# AGENTS.md – DHBW Horb Student App

Kotlin Multiplatform (KMP) app targeting **Android, Desktop (JVM), iOS, macOS** using Compose Multiplatform. Scrapes the DHBW Dualis web portal for timetable and grades.

## Architecture Overview

Multi-module KMP build. Package root stays `de.fampopprol.dhbwhorb` in every module — module
boundaries do not change package names, so imports are identical across the split.

```
:core:common     Outcome/AppError, platform detection,
                 appCoroutineScope                               (no dependencies)
:domain          Dualis models, domain models, repository
                 interfaces, use cases, TimeHelper               -> :core:common
:data            Ktor client, HTML parsers, Dualis services,
                 Room database + DAOs, SecureStorage, prefs,
                 repository implementations                      -> :domain
:services        Notifications, widget use cases, FileViewer     -> :data
:presentation    MVI stores: State / Intent / Msg / Effect,
                 reducers                                       -> :services
:shared          Umbrella; exports the five modules above as
                 `Shared.framework` for Apple targets — no Compose
:composeApp      Compose UI, navigation, platform entry points,
                 Android Glance widget                           -> all of the above
```

Everything above `:data` talks to the six repository interfaces in `:domain`
(`AuthRepository`, `SessionRepository`, `TimetableRepository`, `GradeRepository`,
`DocumentRepository`, `PreferencesRepository`) and to the use cases built on them. The
`DualisXService` classes are `:data` internals — nothing outside `:data` should resolve one.

`:presentation` is part of `Shared.framework` since P4: the stores expose `StateFlow` and the
module has no Compose plugin or dependency at all. Adding one would put the Compose runtime back
into the framework and break the Swift build in P7 — the check below is what catches that.

Verify the framework stays Compose-free after changes:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
nm -gU shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared | grep -c androidx.compose   # must be 0
```

Three service locators exist as bridges and are meant to disappear once the classes behind them
become interfaces with per-platform implementations: `AndroidAppContext` (`:core:common`) hands the
Android Context to `:data`, `WidgetRefreshTrigger` (`:services`) lets background work request a
Glance refresh that only `:composeApp` can perform, and `NotificationDispatcher` (`:services`)
holds its Android Context statically because `expect class` forbids a platform-specific
constructor parameter. All three are initialised from `DualisApplication.onCreate()`; forgetting
one is not a compile error, and dropping the dispatcher's call in P2 crashed the settings screen
until P4.

Platform entry points: `composeApp/androidMain/MainActivity.kt`, `composeApp/desktopMain/main.kt`,
`composeApp/iosMain/MainViewController.kt`.

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

### Expect/Actual
Used for `getDatabaseBuilder()`, `NotificationDispatcher`, `getPlatform(): PlatformType`, and the
two DI entry points `dataPlatformModule()` / `servicesPlatformModule()`.

Secure storage is **not** expect/actual any more: `SecureStorageInterface` has one implementation
per platform (`AndroidSecureStorage`, `DesktopSecureStorage`, `IosSecureStorage`,
`MacosSecureStorage`), bound in `dataPlatformModule()`. An expect class cannot take a
platform-specific constructor parameter, which is what Android needs for its Context.
- Android → `EncryptedSharedPreferences`, Context injected via `androidContext()`
- Desktop → `java-keyring`, falling back to `Preferences.userNodeForPackage`
- Android DB path → `Context.getDatabasePath("grades_database.db")`
- Desktop DB path → per-user data directory (`~/Library/Application Support/…`, `%APPDATA%`,
  `$XDG_DATA_HOME`); it used to be `java.io.tmpdir`, where the OS may delete it
- iOS DB path → the app group container `group.de.fampopprol.dhbwhorb`, so the widget extension
  can read it; falls back to `NSDocumentDirectory` with a logged error if the entitlement is absent
- macOS DB path → `NSApplicationSupportDirectory` (no widget extension, so no app group)

### Dependency Injection (Koin)
One composition root: `initKoin()` in `:shared`, called from `DualisApplication.onCreate()`,
`desktopMain/main.kt` and `MainViewController()`. Modules: `coreModule`, `dataModule` +
`dataPlatformModule()`, `servicesModule` + `servicesPlatformModule()`, `presentationModule`.
`extraModules` is what a platform adds on top — the Glance refresher on Android, the widget writer
on iOS.

**On iOS, Koin has to start before the first composition.** `App()` resolves its dependencies
during composition while a `LaunchedEffect` only runs afterwards — starting Koin there crashes the
first frame with "KoinApplication has not been started".

Classes the framework instantiates (WorkManager workers, schedulers) implement `KoinComponent` and
resolve themselves; `Application.onCreate()` always runs first, so the graph is ready.

`App()` and the screens take no service parameters. Tests override the graph instead:
`WithTestKoin { … }` for Compose tests, `testKoin()` for plain ones — both in `testutil/TestKoin.kt`.
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

```bash
# Android unit tests (Compose UI tests are excluded on Android – run on Desktop instead)
./gradlew :composeApp:testDebugUnitTest

# Desktop (JVM) tests – includes Compose UI tests
./gradlew :composeApp:desktopTest

# All tests + coverage report (Kover)
./gradlew :composeApp:koverXmlReportKmpCoverage
./gradlew :composeApp:koverHtmlReportKmpCoverage

# Desktop run with Compose Hot Reload
./gradlew :composeApp:jvmRun -t

# Android release APK (requires env vars SIGNING_KEYSTORE_PATH, SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD)
./gradlew :composeApp:assembleRelease

# Desktop native packages (Dmg/Msi/Deb)
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
| `presentation/…/presentation/store/BaseStore.kt` | The store contract every feature inherits |
| `composeApp/…/ui/store/StoreCompose.kt` | The only Compose-aware part of the store plumbing |
| `…/data/storage/database/AppDatabase.kt` | Room DB definition; `clearAllData()` for logout |
| `…/data/storage/database/AppDatabaseMigrations.kt` | Schema version, migration list, destructive-fallback allowlist |
| `data/schemas/` | Room schema exports (auto-generated, do not edit) |
| `gradle/libs.versions.toml` | All dependency versions and plugin aliases |

