# AGENTS.md – DHBW Horb Student App

Kotlin Multiplatform (KMP) app targeting **Android, Desktop (JVM), iOS, macOS** using Compose Multiplatform. Scrapes the DHBW Dualis web portal for timetable and grades.

## Architecture Overview

Single module `composeApp` with KMP source sets. Package root: `de.fampopprol.dhbwhorb`.

```
commonMain/
  data/
    dualis/
      remote/         # HTTP client, HTML parsers, services (AuthenticationService, DualisGradeService, DualisLectureService)
      demo/           # Demo mode static data (DemoDataProvider)
      models/         # Domain models (Course, Exam, Grade, Timetable, …)
    storage/
      credentials/    # SecureStorage (expect/actual), SecureStorageWrapper, SecureStorageInterface
      database/       # Room AppDatabase v4, DatabaseFactory (expect/actual getDatabaseBuilder)
      preferences/    # ThemePreferences, NotificationPreferences (use SecureStorage directly)
  services/
    LectureService.kt                 # Cache-first: returns DB data, background-refreshes if >3 days old
    notifications/                    # NotificationDispatcher (expect/actual), LectureChangeMonitor
  ui/
    pages/            # LoginPage, StartPage, TimetablePage, GradesPage, SettingsPage
    grades/viewModels/GradesViewModel.kt
    schedule/viewModels/TimetableViewModel.kt
    navigation/BottomNavigationBar.kt
    theme/            # Theme.kt (MaterialExpressiveTheme + material-kolor for Material You)
  util/Platform.kt    # PlatformType enum + isMobilePlatform()
```

Platform entry points: `androidMain/MainActivity.kt`, `desktopMain/main.kt`.

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
Schema version 4, `exportSchema = true`, schemas in `composeApp/schemas/`. Uses `fallbackToDestructiveMigration(dropAllTables = true)` — **no manual migrations**. KSP processors declared per target in `dependencies {}`:
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
| `composeApp/schemas/` | Room schema exports (auto-generated, do not edit) |
| `gradle/libs.versions.toml` | All dependency versions and plugin aliases |

