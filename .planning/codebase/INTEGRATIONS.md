# External Integrations

**Analysis Date:** 2025-02-12

## APIs & External Services

**Education Portal (Primary):**
- DHBW Dualis - Central source for grades and timetable.
  - Service: `https://dualis.dhbw.de`
  - Client: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/DualisApiClient.kt` (custom Ktor-based)
  - Auth: Handled via `AuthenticationService.kt` using form submission and session cookies.

**Other:**
- Demo Data: `DemoDataProvider.kt` provides mock data when in demo mode.

## Data Storage

**Databases:**
- SQLite (via Android Room - KMP version)
  - Connection: Local persistent file on each platform.
  - Client: `AppDatabase.kt` and several DAOs (e.g., `LectureEventDao.kt`, `GradeDao.kt`).
  - Schema Location: `composeApp/schemas/` (Room exported schemas).

**File Storage:**
- Local filesystem only - Used for storing database file and application preferences.

**Caching:**
- Database acts as a primary cache for Dualis data.
- Stale data detection logic in `LectureService.kt` (3-day threshold).

## Authentication & Identity

**Auth Provider:**
- Custom DHBW Dualis - Integrated directly with the CampusNet login system.
  - Implementation: `AuthenticationService.kt` handles login, redirects, and session persistence.
  - Credentials Storage: `SessionManager.kt` uses platform-specific secure storage (e.g., `EncryptedSharedPreferences` on Android via KMP wrappers).

## Monitoring & Observability

**Error Tracking:**
- None detected (uses `Napier` for local logging).

**Logs:**
- `io.github.aakira:napier` (Napier) - Multiplatform logging to system consoles.

## CI/CD & Deployment

**Hosting:**
- Android Play Store (deployment scripts in `fastlane/`).
- GitHub Pages (deployment via `.github/workflows/deploy-pages.yml`).
- AUR (Arch Linux) / apt-repo (Debian/Ubuntu) - Package distribution.

**CI Pipeline:**
- GitHub Actions - Multiple workflows in `.github/workflows/` (CI, Build-Release, Changelog).

## Environment Configuration

**Required env vars:**
- None detected in code; build-time configurations are in `gradle.properties` and `.xcconfig` files.

**Secrets location:**
- Not stored in codebase (handled via CI secrets or local environment).

## Webhooks & Callbacks

**Incoming:**
- None detected.

**Outgoing:**
- None detected.

---

*Integration audit: 2025-02-12*
