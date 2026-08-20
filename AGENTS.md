# AGENTS.md – DHBW Horb Student App

Kotlin Multiplatform (KMP) app targeting **Android, Desktop (JVM), iOS, macOS** using Compose Multiplatform. Scrapes the DHBW Dualis web portal for timetable and grades.

## Architecture Overview

Multi-module KMP build. Package root stays `de.fampopprol.dhbwhorb` in every module — module
boundaries do not change package names, so imports are identical across the split.

```
:core:common     Platform detection, AndroidAppContext           (no dependencies)
:domain          Dualis models, TimeHelper                       -> :core:common
:data            Ktor client, HTML parsers, Dualis services,
                 Room database + DAOs, SecureStorage, prefs      -> :domain
:services        LectureService, notifications, widget use
                 cases, FileViewer                               -> :data
:presentation    ViewModels                                      -> :services
:shared          Umbrella; exports the four modules above as
                 `Shared.framework` for Apple targets — no Compose
:composeApp      Compose UI, navigation, platform entry points,
                 Android Glance widget                           -> all of the above
```

`:presentation` is deliberately NOT part of `Shared.framework`: its ViewModels still hold state in
Compose's `mutableStateOf`. It joins once they expose `StateFlow` instead.

Verify the framework stays Compose-free after changes:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
nm -gU shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared | grep -c androidx.compose   # must be 0
```

Two service locators exist as bridges and are meant to disappear once DI is in place:
`AndroidAppContext` (`:core:common`) hands the Android Context to `:data`, and
`WidgetRefreshTrigger` (`:services`) lets background work request a Glance refresh that only
`:composeApp` can perform.

Platform entry points: `composeApp/androidMain/MainActivity.kt`, `composeApp/desktopMain/main.kt`,
`composeApp/iosMain/MainViewController.kt`.

## Critical Patterns

### Shared HttpClient (cookie sharing)
`AuthenticationService` and `DualisApiClient` **must share the same `HttpClient` instance** so session cookies persist across all requests. This is wired in `App.kt` and both entry points:
```kotlin
val sharedHttpClient = HttpClient { install(HttpCookies); install(HttpTimeout) { … } }
val authenticationService = AuthenticationService(sessionManager, sharedHttpClient)
val apiClient = DualisApiClient(sharedHttpClient)
```

### Expect/Actual
Platform-specific implementations exist for: `SecureStorage`, `getDatabaseBuilder()`, `NotificationDispatcher`, `getPlatform(): PlatformType`, `LectureMonitorScheduler`.
- Android `SecureStorage` → `EncryptedSharedPreferences`
- Desktop `SecureStorage` → `java-keyring`
- Android DB path → `Context.getDatabasePath("grades_database.db")`
- Desktop DB path → `java.io.tmpdir/dhbw.db`

### Dependency Injection / Testability
`SecureStorage` (expect/actual class) is wrapped in `SecureStorageWrapper : SecureStorageInterface`. Services accept `SecureStorageInterface` for mocking. `App()` accepts optional test parameters (`testAuthenticationService`, `testCredentialsProvider`, `database`, …) used in Compose UI tests (`AppTest.kt`).

### ViewModel State
ViewModels use `mutableStateOf` (not StateFlow/LiveData):
```kotlin
var uiState by mutableStateOf(GradesUiState())
    private set
```

### Room Database
Schema version 4, `exportSchema = true`, schemas in `data/schemas/`. Uses `fallbackToDestructiveMigration(dropAllTables = true)` — **no manual migrations**. KSP processors declared per target in `dependencies {}`:
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
| `composeApp/src/commonMain/kotlin/…/App.kt` | Root composable; wires all services; defines `AppScreen` enum |
| `…/data/dualis/remote/services/AuthenticationService.kt` | Login + redirect chain + re-auth logic |
| `…/data/dualis/remote/DualisApiClient.kt` | Raw HTTP GET; no parsing |
| `…/services/LectureService.kt` | Cache-first fetch strategy (3-day threshold) |
| `…/data/storage/database/AppDatabase.kt` | Room DB definition; `clearAllData()` for logout |
| `data/schemas/` | Room schema exports (auto-generated, do not edit) |
| `gradle/libs.versions.toml` | All dependency versions and plugin aliases |

