# Technology Stack

**Analysis Date:** 2025-02-12

## Languages

**Primary:**
- Kotlin 2.3.10 - Used across the entire codebase for common logic, UI (Compose), and platform-specific implementations (KMP).

**Secondary:**
- Swift - Used in `iosApp/` and `macosApp/` for platform-specific entry points and UI wrappers.
- Ruby - Used in `Gemfile` for Fastlane and mobile automation scripts.

## Runtime

**Environment:**
- JVM - Desktop and Android runtimes.
- Darwin - iOS and macOS native runtimes (via Kotlin/Native).

**Package Manager:**
- Gradle 8.x (Kotlin DSL)
- Lockfile: `gradle/libs.versions.toml` used for dependency management.

## Frameworks

**Core:**
- Compose Multiplatform 1.10.3 - UI framework for Android, Desktop, iOS, and macOS.
- Ktor 3.4.2 - Networking client used for API requests and HTML scraping.
- Room 2.8.4 - Database abstraction layer for local storage (SQLite).

**Testing:**
- JUnit 4.13.2 - Primary unit testing framework.
- Robolectric 4.16.1 - Android-specific unit testing.
- Kotlin Test - Multiplatform testing utilities.

**Build/Dev:**
- Android Gradle Plugin (AGP) 9.1.0 - Android build system.
- Kotlin Symbol Processing (KSP) 2.3.2 - Code generation (used by Room).
- Kover 0.9.8 - Kotlin code coverage tool.

## Key Dependencies

**Critical:**
- `kotlinx-coroutines` 1.10.2 - Asynchronous programming and concurrency.
- `kotlinx-serialization` 1.10.0 - JSON and data serialization.
- `kotlinx-datetime` 0.7.1 - Modern date and time handling.
- `napier` 2.7.1 - Multiplatform logging.

**Infrastructure:**
- `material-kolor` 4.1.1 - Dynamic color palette generation from seed colors.
- `glance-appwidget` 1.1.1 - Android home screen widgets.
- `dnsjava` 3.6.4 - DNS library (likely for specific networking needs).

## Configuration

**Environment:**
- Local properties: `local.properties` (ignored)
- Gradle properties: `gradle.properties`
- Config files: `iosApp/Configuration/Config.xcconfig` for iOS builds.

**Build:**
- Root `build.gradle.kts` and `settings.gradle.kts`.
- Module-specific `composeApp/build.gradle.kts`.

## Platform Requirements

**Development:**
- JDK 17+
- Android Studio / IntelliJ IDEA
- Xcode (for iOS/macOS builds)

**Production:**
- Android (minSdk 24)
- iOS
- macOS
- Windows/Linux (via JVM Desktop)

---

*Stack analysis: 2025-02-12*
