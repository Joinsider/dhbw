# AGENTS.md – DHBW Horb Student App

Kotlin Multiplatform (KMP) app targeting **Android, Desktop (JVM: Linux and Windows), iOS and
macOS**. Android and Desktop use Compose Multiplatform. **iOS and macOS are one native SwiftUI
app** (iOS since P7, macOS since the SwiftUI target became multiplatform), and both share everything
below the UI through `Shared.framework`. Scrapes the DHBW Dualis web portal for timetable and grades.

## Architecture Overview

Multi-module KMP build. Package root stays `de.fampopprol.dhbwhorb` in every module — module
boundaries do not change package names, so imports are identical across the split.

```
:core:common     Outcome/AppError, platform detection,
                 appCoroutineScope                               (no dependencies)
:core:testing    Repository fakes, in-memory DAOs, the test Koin
                 graph — depended on only from a `commonTest`    -> :presentation
:domain          Dualis models, domain models, repository
                 interfaces, use cases, TimeHelper               -> :core:common
:data            Ktor client, HTML parsers, Dualis services,
                 Room database + DAOs, SecureStorage, prefs,
                 repository implementations                      -> :domain
:services        Notifications, widget use cases, FileViewer     -> :data
:presentation    MVI stores: State / Intent / Msg / Effect,
                 reducers                                       -> :services
:shared          Umbrella; exports the five production modules above as
                 `Shared.framework` for Apple targets (iOS and macOS) — no Compose,
                 plus the Swift bridge in `shared/appleMain/…/ios/` (WidgetSnapshot
                 stays in `shared/iosMain`)
:composeApp      Compose UI, navigation, platform entry points,
                 Android Glance widget                           -> all of the above
                 (Android and JVM desktop only — no Apple target)
iosApp/          The SwiftUI app for iOS and macOS: `RootView` + five screens,
                 links `Shared.framework` (see "macOS is the SwiftUI app")
```

Everything above `:data` talks to the six repository interfaces in `:domain`
(`AuthRepository`, `SessionRepository`, `TimetableRepository`, `GradeRepository`,
`DocumentRepository`, `PreferencesRepository`) and to the use cases built on them. The
`DualisXService` classes are `:data` internals — nothing outside `:data` should resolve one.

`:presentation` is part of `Shared.framework` since P4: the stores expose `StateFlow` and the
module has no Compose plugin or dependency at all. Adding one would put the Compose runtime back
into the framework and break the SwiftUI app — the check below is what catches that.

Verify the framework stays Compose-free after changes:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
nm -gU shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared | grep -c androidx.compose   # must be 0

./gradlew :shared:linkDebugFrameworkMacosArm64
nm -gU shared/build/bin/macosArm64/debugFramework/Shared.framework/Shared | grep -c androidx.compose   # must be 0
```

There are no service locators left. Three used to exist as bridges, each initialised from
`DualisApplication.onCreate()`, and each one a call that was not a compile error to forget —
dropping the notification dispatcher's in P2 crashed the settings screen until P4. All three are
now interfaces with a per-platform implementation, with Koin supplying the Android Context. The
last of them, `NotificationDispatcher`, went in P9; it had held its Context statically because
`expect class` forbids a platform-specific constructor parameter. **When a platform class needs a
Context, give it a constructor parameter and bind it in `servicesPlatformModule()` /
`dataPlatformModule()` — do not add a static field back.**

**Background monitoring runs at two speeds.** `LectureChangeMonitor` checks the current week in
full on every run — the grid plus one request per lecture, because lecturers and rooms only exist
on a lecture's own page — and sweeps the next four weeks with the weekly grid alone (one request
each) at most every four hours. A future week whose grid moved is then fetched in full, so the
detailed comparison still happens, for one week instead of five. A future week the app has never
loaded has nothing to compare against, so it is fetched in full once and stored *without*
notifying — otherwise the whole week would arrive as new lectures, and skipping it instead (which
is what used to happen) left week +3 unwatched until somebody paged to it. Nothing before now is compared on
either side, and `saveLecturesToDatabase` drops anything that ended more than 60 days ago, so the
cache no longer keeps every week it has ever seen.

Pairing cached against fetched lectures happens per subject in two passes — exact slots first, then
nearest survivors. That order is the whole trick: it is what lets a moved lecture be recognised as
moved (the old key contained the start time, so a move could only ever look like a cancellation
plus a new lecture) *without* turning "the Monday slot was cancelled" into "Monday moved to
Wednesday". `LectureChangeMonitorTest` holds both halves.

Notification text is the one thing users read that comes from neither resource system:
`LectureNotificationTexts` in `:services` holds it in German and English, picked by
`currentLanguage()`. A background worker has no Compose context and no bundle to ask, and copying
the formatting into the four platform dispatchers would put one behaviour in four places.

**There are two kinds of notification, and only one of them is a background check.** The lecture
*reminder* is not polled: `LectureReminderPlanner` reads the cached timetable and hands finished
notifications to the operating system, which holds them and shows them at a wall-clock minute
whether or not the app ever runs again. Every replan is a `replaceAll`, so a cancelled lecture
stops reminding by simply not being planned. Replanned at launch, whenever the lead time changes,
after every hourly check, and — on Android — after a reboot, which clears every pending alarm.

Both platforms need something for it. On iOS a `UNCalendarNotificationTrigger` is enough and the
existing notification permission covers it, but iOS keeps at most **64 pending requests** per app
and drops the rest silently, which is why `MAX_REMINDERS` is 40. On Android it is an `AlarmManager`
alarm plus `LectureReminderReceiver`; exact alarms need `SCHEDULE_EXACT_ALARM`, which from
Android 14 the user grants on a system screen. Without it the alarms are set with a five-minute
window instead of not at all, `firesExactly()` returns false, and the settings screen says so and
offers a button to that screen. `:services` carries its own `AndroidManifest.xml` so the two
permissions and the two receivers sit next to the code that needs them.

**Deleting the app takes the account with it.** That is what a user deleting an app expects, and
it is what app data does on both platforms — except the Keychain, which outlives an uninstall by
design. `CredentialsInstallGuard` closes that: it keeps the identity of the installation that wrote
the credentials *next to* them, and `initKoin()` clears them when it no longer matches. The identity
comes from `currentInstallStamp()` — on iOS the App Group container's creation date (the App Group,
not the app's own home directory, because the widget extension runs the same code from a different
one), and `null` everywhere else, which disables the guard because there the credential store is
already inside the app data. A store with no stamp yet is adopted rather than wiped, so the update
that introduces this does not log anyone out. Android's second door is Auto Backup, closed by
excluding `dualis_secure_prefs.xml` in `res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml`.

Platform entry points: `composeApp/androidMain/MainActivity.kt`, `composeApp/desktopMain/main.kt`,
and `iosApp/iosApp/iOSApp.swift` (which calls `SharedApp.start()`; the same file serves macOS).

**On iOS there is a second process.** Since P8 the widget extension links `Shared.framework` too
and starts its own Koin graph through `WidgetSnapshotProvider`; the two processes share the
database file in the App Group container, nothing else. That means:

* The widget has no Swift copy of any model. It renders `WidgetLecture`/`WidgetDay` straight out
  of `WidgetSnapshot.kt`. Adding a field there is enough — there is no second place to change.
* Only the app can tell WidgetKit that something moved, so `AppModel` reloads the timelines from
  `SharedApp.observeTimetableChanges`.
* The `TimetableWidgetExtension` target runs `:shared:embedAndSignAppleFrameworkForXcode` in its
  own build phase and is pinned to `ARCHS = arm64`, because the Kotlin framework has no x86_64
  slice and the extension would otherwise be asked to build one.
* Background monitoring is real on iOS now: `LectureMonitorScheduler.ios.kt` drives
  `BGTaskScheduler` from Kotlin. `iOSApp.init()` registers the launch handler (iOS rejects a
  registration that arrives after launch has finished), and `AppModel` submits or cancels the
  request as the two notification switches change — the same job `MainActivity` does on Android.
  The identifier lives in `Info.plist` under `BGTaskSchedulerPermittedIdentifiers`; if it is
  missing, registration fails and the first `submit` terminates the process.
* App and extension see the same keychain items because both entitlement files list
  `$(AppIdentifierPrefix)de.fampopprol.dhbw` first. No access group is named in code — see
  `AppleSecureStorage`.

### macOS is the SwiftUI app, built from the same target

The Mac app is not a second Compose app. It is the SwiftUI target in `iosApp/`, which is one
multiplatform target built for iOS 26 and macOS 26 with the same bundle id (`de.fampopprol.dhbw`).
On the Mac App Store that makes macOS a platform of the existing iOS app record, so Universal
Purchase covers it and it replaces the "Designed for iPad" build. The reason for one target rather
than a separate Mac project is that the Swift code, the bridges and the store plumbing are shared;
the differences are `#if os(macOS)` branches inside the same files: the timetable uses a toolbar
(previous / Today / next, ⌘← / ⌘→) instead of the page TabView, documents use Quick Look, NSSavePanel
and NSSharingServicePicker, the root uses `.sidebarAdaptable`, and the default window is 1040×700.
Apple Silicon only: the shared modules build `macosArm64`, and the `macosX64` target was removed
everywhere.

The framework is **dynamic on macOS** (`isStatic = appleTarget.name != "macosArm64"` in
`shared/build.gradle.kts`). Linked statically, Xcode's linker pulled in androidx.sqlite's
sqlite-framework cinterop member, which references six SQLite symbols that the macOS SDK's
`libsqlite3` does not export, and the link failed. Kotlin's `embedAndSign` task skips dynamic
frameworks, so the app target has its own run script phase, "Embed Shared.framework (macOS)", that
copies and signs it into `Contents/Frameworks`. Do not switch the macOS framework back to static
without linking the app again.

What differs from iOS at runtime:
- **No widget and no App Group.** The widget extension is iOS-only (`platformFilter = ios`), so the
  macOS database lives in the sandbox container, and `setWidgetReload` is a no-op there
  (`installWidgetReload` is the expect/actual).
- **Hourly check on `NSBackgroundActivityScheduler`.** There is no `BGTaskScheduler` on macOS.
  `LectureMonitorScheduler` in `services/src/macosMain` creates one `NSBackgroundActivityScheduler`
  (identifier `de.fampopprol.dhbwhorb.lecture-monitor`, hourly, ten minutes' tolerance, utility QoS).
  The system picks the moment, defers under load and does not fire during sleep. The JVM desktop keeps
  its own coroutine loop in `desktopMain`. `registerTaskHandler()` is a no-op on both. Every run goes
  through `NotificationManager.checkAndNotify()`, which replans the reminders first, as on iOS. The
  check runs only while the process runs, so on its own it stops when the app quits. Each run that finishes is recorded by `NotificationManager.checkAndNotify()` as a `LectureCheck` (time and
  success, offline or failed) in PlatformSettings, and shown as "Letzte Prüfung" in Settings → Background and in the menu bar.
- **Background mode: a login item that starts hidden (`iosApp/iosApp/Mac/`).** The
  Settings switch "Beim Anmelden im Hintergrund starten" registers `SMAppService.mainApp`. Its
  status is the only state: nothing stores a second flag, and `BackgroundMode.shared` re-reads it on
  every app activation, because the user can remove the item in System Settings. Turning it on
  while the status is `.requiresApproval` shows a button to Login Items.
  - *Login launch detection* (`LaunchContext` in `Mac/BackgroundMode.swift`): either the launching
    Apple event carries `keyAELaunchedAsLogInItem`, or the system has been up for less than five
    minutes. The second signal is what makes it work after a reboot; logout and login without a reboot
    is only recognised by the first, which SwiftUI does not guarantee to be current.
  - *Starting hidden* (only when the launch is a login launch and the item is enabled): the activation
    policy is `.accessory` before the first window exists, the `Window` scene uses
    `defaultLaunchBehavior(.suppressed)` with `restorationBehavior(.disabled)`, and
    `applicationDidFinishLaunching` closes any window that appeared anyway.
  - *Window and Dock:* a key window switches the app to `.regular`; the last user window closing
    switches it back to `.accessory` (menu bar only, no Dock icon). `applicationShouldTerminateAfterLastWindowClosed`
    returns `false` in both modes, which is AppKit's default and today's behaviour. The window is a
    single `Window` scene so the menu's "Stundenplan öffnen" brings it forward rather than duplicating it.
  - *Menu bar:* `MenuBarExtra` with `isInserted:` bound to the login-item status, so it exists only in
    background mode. It shows the next lecture today or tomorrow from the timetable store, "Stundenplan
    öffnen", "Jetzt aktualisieren" (timetable refresh for the current week) and "Beenden".
  - *Stores start at launch:* `AppModel` is created by `@State` on the `App`, so the stores, the monitor
    schedule and the reminder replan run on a hidden launch too.
  - *What does not run:* nothing before the user logs in; nothing after ⌘Q or after the login item is
    switched off. Launching
    the app again from Finder while it is hidden does not open the window: use the menu bar item.
- **Reminders use the iOS mechanism.** `AppleLectureReminderScheduler` (`services/src/appleMain`) is
  bound on both Apple platforms and plans `UNCalendarNotificationTrigger` requests, so the 64-pending
  limit and `MAX_REMINDERS` apply on macOS too.
- **Sandbox entitlements** live in `iosApp/iosApp/iosApp-macOS.entitlements`: app sandbox, outgoing
  network, user-selected files read/write (for the save panel) and the keychain access group. The
  app is built with the hardened runtime.
- **Credentials use the data protection keychain, like iOS.** `AppleSecureStorage` (`data/src/appleMain`)
  is bound on both Apple platforms and sets `kSecUseDataProtectionKeychain` on every query. The
  older macOS implementation used the legacy file-based login keychain, where each item's access
  list is bound to the code signature of the app that wrote it: every rebuild or re-signing
  prompted for the Mac password once per item, about eight times at launch. The data protection
  keychain authorises by the entitlement instead and does not prompt. Consequences: the legacy
  items are neither read nor deleted (reading them is what prompts), so macOS users log in once
  more after this change. An unsigned or ad-hoc build cannot write to the data protection keychain
  (`errSecMissingEntitlement`, -34018) and cannot log in; that is expected.
- **Uninstall does not clear credentials on macOS.** The install guard is disabled there
  (`currentInstallStamp()` is `null`): the sandbox container survives deleting the app, so its
  creation date cannot tell a reinstall from an update, and the bundle date changes on every
  update. The keychain items therefore survive an uninstall on macOS, as they did before. Closing
  that needs a marker the installer writes, not a timestamp.

Distribution is covered in `documentation/macOS/README.md`: the App Store upload, and the Developer
ID DMG that `.github/workflows/build-macos.yml` archives, notarizes and packages — called by the
release workflow, and startable on its own. It signs manually with a Developer ID profile, because
the keychain group entitlement is restricted on macOS and the app does not launch without one.

**The Apple version lives in `iosApp/Configuration/Config.xcconfig` and nowhere else.** No target in
`project.pbxproj` sets `MARKETING_VERSION` or `CURRENT_PROJECT_VERSION`; a value there would win
over the file, and the release bump would silently stop reaching Xcode builds — which is what
happened until the targets were cleaned (the bump wrote 3.0.3 (29) while Xcode kept 3.0.4 (34)).
Xcode's General tab writes into the target when a version is edited there, so edit the file. The
version follows the Android `versionName`; the build number is Apple's own counter, one up per
release, because App Store Connect rejects a number it has already seen. The bump job's validation
fails if a target sets either value again.

## Critical Patterns

### Shared HttpClient (cookie sharing)
`AuthenticationService` and `DualisApiClient` **must share the same `HttpClient` instance** so
session cookies persist across all requests. It is a `single` in `dataModule` — declared exactly
once, so this cannot drift per platform. `KoinGraphTest.graph_actuallyBuilds` asserts the identity.

`HttpClientFactory.create { }` (expect/actual in `data/…/net/`) builds it. The factory returns the
finished client, not just an engine, because engine configuration is typed to the engine and the
shared config is star-projected — everything platform-independent still comes from `dataModule`.

### Desktop TLS — the bundled HARICA roots
Dualis chains to HARICA's 2021 TLS roots, which are **not** in the JDK's `cacerts` (Zulu 25 ships
only the 2015 ones). Every desktop request failed with `PKIX path building failed`; Android and
Apple were fine because they use platform trust stores. `DesktopTrustStore.kt` therefore extends
the JDK's default anchors with two roots bundled in `data/src/desktopMain/resources/certs/`, and
`HttpClientFactory.desktop.kt` hands the resulting trust manager to OkHttp.

It only ever **adds** anchors — chain, hostname and expiry checks stay with the JDK.
`DesktopTrustStoreTest` pins both fingerprints, checks that no default anchor is lost, and fails
once a JDK baseline ships the roots itself so the workaround does not outlive the problem. Read
`resources/certs/README.md` before touching any of it.

### Error handling — `Outcome` / `AppError`
Anything that can fail returns `Outcome<T>` (`Ok` / `Err(AppError)`) from `:core:common`, never
`null`, `emptyList()` or `kotlin.Result`. `AppError` distinguishes `Offline`, `SessionExpired`,
`InvalidCredentials`, `NoCredentials`, `Http(code)`, `Parse(source, hint)`, `Storage(hint)`,
`Unsupported(hint)` and `Unexpected(hint)`; a sealed hierarchy because SKIE turns it into a Swift
enum in P7.

Exceptions become errors in exactly two places: `Throwable.toAppError()` in
`data/error/NetworkErrors.kt`, and the `catch` blocks around database access in the repositories.
A `catch (e: Exception)` anywhere else in the Dualis data path is a bug. The parsers are the
deliberate exception: they swallow a malformed row and keep the rest, and the page-level
validation in `DualisPageGateway` catches a real break.

`AppError.toUserMessage()` (`composeApp/.../ui/error/AppErrorMessage.kt`) is the only place an
error turns into words, so the message is localised instead of being an English exception string.

### Session handling
`DualisPageGateway` performs every authenticated page fetch: build URL, validate the page,
re-authenticate once, classify what is left. The three Dualis services used to carry their own
copy of that loop.

Re-authentication goes through `ReAuthenticator`, which is single-flight: a `Mutex` guards one
`CompletableDeferred`, so concurrent callers share a single login instead of racing. Never add a
second login path — `SessionManager` no longer has an `isReAuthenticating` flag, and the reason is
that it let one caller through and rejected the rest.

`ReAuthenticator` is also the *only automatic* login, so `AutoLoginGuard` sits there: after two
`InvalidCredentials` in a row it answers `InvalidCredentials` without a request, so a changed
password is not tried against Dualis every hour until the account locks. Offline and 5xx do not
count. The count lives in secure storage next to the credentials — it survives restarts, is seen
by the iOS widget process, and goes on logout or reinstall. **Only a manual login
(`AuthRepositoryImpl.login`) opens it again**; while it is closed `SessionRepository` reports no
session, and `AppStore` watches `autoLoginBlocked` so a running app switches to the login screen
(`AppState.reLoginRequired` says why). If a *different* account logs in there, the cache is wiped
first, as on logout.

### Expect/Actual
Used for `getDatabaseBuilder()`, `getPlatform(): PlatformType`, and the two DI entry points
`dataPlatformModule()` / `servicesPlatformModule()`. Not for anything that needs a constructor
parameter on one platform — that is what the interfaces above are for.

### Two storages, on purpose
`SecureStorageInterface` holds **secrets** (credentials, session, cookie). `PlatformSettings` holds
**settings** (theme, colour, notification toggles), through the `SettingsStorage` wrapper that also
performs the one-time move of pre-split installations out of the secure store.

They used to be one interface, and the price was paid only on desktop: macOS checks its Keychain
access list per *entry*, so reading the colour scheme opened a permission dialog — eight of them on
a cold start. Android and iOS never prompt, which is why it survived so long. **Whether a value is
a secret is a property of the value, not of the platform**; put settings in `PlatformSettings`.

Backends: Android `SharedPreferences` (`dualis_settings`), desktop `java.util.prefs`, Apple
`NSUserDefaults`. Logging out clears credentials and no longer takes the user's theme with it.

### One keyring entry on desktop
`DesktopSecureStorage` keeps everything in a single keyring entry (`DualisApp` / `credentials`)
and holds it in memory for the run: one dialog per start instead of one per value, and a repeated
read cannot ask twice. Pre-bundle installations are migrated on first access via the `_stored_keys`
index; the old entries are deleted only after the bundle is verifiably written, because failing
halfway would log the user out for good.

`KoinGraphTest.graph_actuallyBuilds` constructs the real secure storage, so running the desktop
suite touches — and migrates — the developer's own keyring.

Secure storage is **not** expect/actual any more: `SecureStorageInterface` has one implementation
per platform (`AndroidSecureStorage`, `DesktopSecureStorage`, and `AppleSecureStorage` shared by
iOS and macOS), bound in `dataPlatformModule()`. An expect class cannot take a platform-specific
constructor parameter, which is what Android needs for its Context.
- Android → `EncryptedSharedPreferences`, Context injected via `androidContext()`
- Desktop → `java-keyring`, falling back to `Preferences.userNodeForPackage`
- iOS and macOS → `AppleSecureStorage`, the Keychain on the data protection keychain (see
  "macOS is the SwiftUI app"); the item layout (service `DualisApp`, one item per key, `_stored_keys`
  index) is the same on both, and iOS items are unaffected by the flag
- Android DB path → `Context.getDatabasePath("grades_database.db")`
- Desktop DB path → per-user data directory (`~/Library/Application Support/…`, `%APPDATA%`,
  `$XDG_DATA_HOME`); it used to be `java.io.tmpdir`, where the OS may delete it
- iOS DB path → the app group container `group.de.fampopprol.dhbwhorb`, so the widget extension
  can read it; falls back to `NSDocumentDirectory` with a logged error if the entitlement is absent
- macOS DB path → `NSApplicationSupportDirectory`, inside the sandbox container (no widget
  extension, so no app group)

### Dependency Injection (Koin)
One composition root: `initKoin()` in `:shared`, called from `DualisApplication.onCreate()`,
`desktopMain/main.kt` and `SharedApp.start()`. Modules: `coreModule`, `dataModule` +
`dataPlatformModule()`, `servicesModule` + `servicesPlatformModule()`, `presentationModule`.
`extraModules` is what a platform adds on top — the Glance refresher on Android, the widget writer
on iOS.

**On iOS, Koin has to start before the first view is built.** `AppModel.init()` resolves the six
stores, and it runs while `iOSApp` builds its `@State`; starting Koin from a `.task` instead would
run after that first frame and crash with "KoinApplication has not been started". `SharedApp.start()`
is idempotent because SwiftUI may build the root more than once.

Classes the framework instantiates (WorkManager workers, schedulers) implement `KoinComponent` and
resolve themselves; `Application.onCreate()` always runs first, so the graph is ready.

`App()` and the screens take no service parameters. Tests override the graph instead:
`testKoin()` for plain tests (`:core:testing`, `testutil/TestKoin.kt`) and `WithTestKoin { … }` for
Compose tests (`:composeApp` commonTest — it is a composable, and `:core:testing` has no Compose
because `:presentation`'s tests depend on it).
`KoinGraphTest` verifies every module and builds the real graph, so a missing binding fails in CI
rather than when a user opens the screen.

### Navigation
`androidx.navigation:navigation-compose` in the JetBrains Multiplatform variant
(`org.jetbrains.androidx.navigation`). Navigation 3 was still alpha and Android-only when P5 ran,
so the plan's rule picked this one.

Destinations are typed (`ui/navigation/Routes.kt`), so a route carries its arguments:
`Route.Grades(semesterId)` either has one or does not compile. `DhbwNavHost` is the logged-in
graph; the root composable only chooses between *restoring*, *login* and *the graph*.

`NavController.switchTab()` is the single way to change tab — `popUpTo(startDestination)` plus
`launchSingleTop` keeps the stack from growing one entry per tap, and `saveState`/`restoreState`
keep each tab's scroll position. Back from a tab returns to the timetable; back from the timetable
leaves the app.

Deep links: `dhbw://timetable?week=-1`, `dhbw://grades/{semesterId}`, `dhbw://documents`,
`dhbw://settings`. The Android manifest declares the scheme; the graph resolves the path, so a
cold start opens the right screen instead of the timetable and then jumping.

Navigation tests drive the controller through `runOnUiThread`, not by tapping the bar:
`NavBackStackEntry` moves its own `LifecycleRegistry` and that refuses to run off the main thread,
which a click dispatched from the Compose test's thread trips.

### MVI stores
One store per feature in `:presentation`: `AppStore`, `AuthStore`, `TimetableStore`, `GradesStore`,
`DocumentsStore`, `SettingsStore`. Each is `State` / `Intent` / `Msg` / `Effect` over `BaseStore`.

```kotlin
interface Store<S : Any, I : Any, E : Any> {
    val state: StateFlow<S>      // always has a value
    val effects: Flow<E>         // one-shot, never replayed
    fun dispatch(intent: I)
    fun close()
}
```

Two rules make the difference:

* **The reducer is a top-level function** — `reduceTimetable(state, msg)`, `reduceGrades(...)` —
  not a method. It cannot reach a repository, a scope or a clock, so its purity is structural.
  Its tests call it directly: no `runTest`, no dispatcher, no fakes.
* **The effect handler cannot touch the state.** `EffectScope` offers exactly `emit(msg)` and
  `send(effect)`. There is no window between reading state and writing it, which is why the old
  race conditions cannot be expressed any more.

`dedupeKey(intent)` is where "refresh while a refresh runs" is decided — a key, not a boolean two
coroutines can both read as false.

**A screen re-enters the composition on every tab switch**, so pages dispatch `EnsureLoaded`, not
`Load`. `Load` is the retry action and always fetches. Getting this wrong is invisible in tests and
shows up only as network traffic when walking the tabs — check it with
`adb logcat -d --pid=… | grep -c "Executing GET request"`, which must read 0.

Stores are Koin singles on `appCoroutineScope`, so switching tabs costs nothing and a store keeps
what it has already loaded for the whole session.

Compose sees a store through two helpers in `composeApp/.../ui/store/StoreCompose.kt`:
`store.collectState()` and `store.HandleEffects { … }`. Nothing else about a store is Compose-aware.

Swift sees the same store through `StoreBox` (`iosApp/iosApp/Bridge/`), which observes the Kotlin
bridges in `shared/iosMain/…/ios/StoreBridges.kt` — one per store, non-generic so Swift keeps the
real intent and state types. The flows are collected inside Kotlin (`FlowObserver`) on
`Dispatchers.Main` and call a Swift closure. **SKIE is not used**: its newest release supports
Kotlin 2.2.0 and this project is on 2.3.20; the consequence is that sealed hierarchies reach Swift
as classes matched with `is`, not as exhaustive Swift enums.

### Room Database
Schema version 4, `exportSchema = true`, schemas in `data/schemas/`.

`getDatabaseBuilder()` per platform decides **only where the file lives**. How it is opened —
migrations and the destructive-fallback policy — lives once in `createRoomDatabase()`
(`data/…/database/DatabaseFactory.kt`), so the four platforms cannot drift apart on it.

Migration rules, all in `data/…/database/AppDatabaseMigrations.kt`:
- `APP_DATABASE_VERSION` is the single source of the schema version; `@Database` reads it.
- Raising it obliges you to add the `Migration` to `APP_DATABASE_MIGRATIONS` **and** to commit the
  schema export Room writes. `AppDatabaseMigrationTest` (desktopTest) fails otherwise — it is the
  gate, and it does bite: bumping the version without a migration fails four of its five tests.
- Only the pre-release schemas in `DESTRUCTIBLE_SCHEMA_VERSIONS` (1, 2, 3) may be dropped. Every
  released build shipped schema 4, so no installation in the wild is affected.
- There is **no** blanket `fallbackToDestructiveMigration` any more. An unmigratable version gap now
  fails loudly at open time instead of silently deleting cached grades and timetable.

The export path in `data/build.gradle.kts` is `schemaDirectory("$projectDir/schemas")` — plain
interpolation, **no** escaped dollar. Escaping it makes the plugin write into a directory literally
named `$projectDir` while `data/schemas/` quietly freezes, which is what happened from P1 to P6.

KSP processors declared per target in `dependencies {}`:
```kotlin
add("kspAndroid", libs.androidx.room.compiler)
add("kspDesktop", libs.androidx.room.compiler)
// …
```

### HTML Parsing
Dualis responses are HTML-scraped using **regex** (no DOM library). Parsers (`GradeParser`, `TimetableParser`, `AuthParser`) operate purely on raw HTML strings returned by `DualisApiClient.get()`. Keep parsing isolated from network code.

### Demo Mode
Login with `demo@hb.dhbw-stuttgart.de` / `demo123` uses `DemoDataProvider` static data, bypassing all network calls. Credentials defined in `SessionManager.DEMO_EMAIL` / `DEMO_PASSWORD`.

### Logging
Use Napier everywhere: `Napier.d("…", tag = TAG)`. Each class defines `private const val TAG = "ClassName"`.

### License Headers
Files must include SPDX headers:
```kotlin
// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later
```

## Build & Test Commands

Tests live in the module they test, since P9. The task names are unqualified on purpose — that
runs every module's own suite, and a qualified `:composeApp:desktopTest` now covers only the
Compose UI.

```bash
# Android unit tests (Compose UI tests are excluded on Android – run on Desktop instead)
./gradlew testDebugUnitTest

# Desktop (JVM) tests – includes Compose UI tests
./gradlew desktopTest

# All tests + coverage report (Kover)
./gradlew :composeApp:koverXmlReportKmpCoverage
./gradlew :composeApp:koverHtmlReportKmpCoverage

# Desktop run with Compose Hot Reload
./gradlew :composeApp:jvmRun -t

# Android release APK (requires env vars SIGNING_KEYSTORE_PATH, SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD)
./gradlew :composeApp:assembleRelease

# iOS app (Debug, simulator). Leave CODE_SIGNING_ALLOWED=NO out when checking anything that
# hangs off an entitlement — without it the app runs without App Group and Keychain group.
cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/dhbwderived build

# macOS app (Debug, Apple Silicon). Built from the SwiftUI target; see "macOS is the SwiftUI app"
cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' -derivedDataPath /tmp/dhbwderived-mac build

# Desktop native packages (Msi on Windows, Deb on Linux; run on that OS only)
./gradlew :composeApp:packageDistributionForCurrentOS

# Fat JAR for Linux distribution
./gradlew :composeApp:packageFatJar
```

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/kotlin/…/App.kt` | Root composable: theme, and restoring / login / graph |
| `shared/src/commonMain/kotlin/…/shared/Koin.kt` | `initKoin()` — the single composition root |
| `…/data/dualis/remote/services/AuthenticationService.kt` | Login + redirect chain + re-auth logic |
| `…/data/dualis/remote/DualisApiClient.kt` | Raw HTTP GET; no parsing |
| `…/data/repository/TimetableRepositoryImpl.kt` | Cache-first strategy (3-day threshold), single-flight per week |
| `…/data/dualis/remote/services/DualisPageGateway.kt` | Authenticated page fetch with one re-auth retry |
| `…/data/dualis/remote/session/ReAuthenticator.kt` | Single-flight re-login |
| `…/core/error/Outcome.kt`, `…/core/error/AppError.kt` | The error channel |
| `…/data/storage/credentials/CredentialsInstallGuard.kt` | Drops credentials that outlived the installation that stored them |
| `services/…/notifications/LectureChangeMonitor.kt` | What changed in the timetable: two speeds, and the pairing that recognises a move |
| `services/…/notifications/LectureNotificationTexts.kt` | The notification wording, de/en — the only user-facing text outside the two UI resource systems |
| `services/…/reminders/LectureReminderPlanner.kt` | Turns the cached timetable into the alarms the system holds |
| `services/src/androidMain/AndroidManifest.xml` | The alarm permissions and the two reminder receivers |
| `presentation/…/presentation/store/BaseStore.kt` | The store contract every feature inherits |
| `composeApp/…/ui/store/StoreCompose.kt` | The only Compose-aware part of the store plumbing |
| `shared/src/iosMain/…/ios/SharedApp.kt` | The one door Swift uses: starts Koin, hands out the six bridges |
| `shared/src/iosMain/…/ios/StoreBridges.kt` | One typed bridge per store, for Swift |
| `iosApp/iosApp/Bridge/StoreBox.swift` | `@Observable` wrapper — the Swift half of the store plumbing |
| `iosApp/iosApp/RootView.swift` | `AppModel` (stores, widget reload, background schedule) plus login gate and tab bar |
| `shared/src/iosMain/…/ios/WidgetSnapshot.kt` | The widget's data, defined once; the extension's only Kotlin entry point |
| `services/…/notifications/LectureMonitorScheduler.ios.kt` | `BGTaskScheduler` registration, submission and the background check |
| `services/src/macosMain/…/notifications/LectureMonitorScheduler.kt` | `NSBackgroundActivityScheduler` for macOS; the JVM desktop loop is in `desktopMain` |
| `iosApp/iosApp/Mac/BackgroundMode.swift` | macOS background mode: login item, login-launch detection, `MacAppDelegate` (activation policy, last window) |
| `iosApp/iosApp/Mac/MenuBarMenu.swift` | The menu bar item shown in background mode |
| `iosApp/TimetableWidgetExtension.entitlements` | App Group + keychain group for the extension (the app's are in `iosApp/iosApp/iosApp.entitlements`) |
| `iosApp/iosApp/Localizable.xcstrings` | iOS strings (en/de); the Compose UI has its own in `composeResources` |
| `iosApp/TimetableWidget/Localizable.xcstrings` | The widget extension's strings (en/de) — a bundle of its own, not the app's |
| `iosApp/iosApp/iosApp-macOS.entitlements` | Sandbox, network, user-selected files and keychain group for the macOS app |
| `shared/build.gradle.kts` | Framework linkage per Apple target — dynamic on macOS, see "macOS is the SwiftUI app" |
| `packaging/macos/make-dmg.sh` | Builds the Developer ID DMG shipped by the release workflow |
| `core/testing/src/commonMain/…/testutil/` | The fakes every module's tests build on |
| `…/data/storage/database/AppDatabase.kt` | Room DB definition; `clearAllData()` for logout |
| `…/data/storage/database/AppDatabaseMigrations.kt` | Schema version, migration list, destructive-fallback allowlist |
| `data/schemas/` | Room schema exports (auto-generated, do not edit) |
| `gradle/libs.versions.toml` | All dependency versions and plugin aliases |

