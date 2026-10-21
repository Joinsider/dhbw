# Running the Mac app locally

The Mac app is built and run from Xcode like the iOS app. The Compose desktop build
(`:composeApp:jvmRun`) still runs on a Mac for developers, but it is no longer a shipped product.

## Run from Xcode

1. Open `iosApp/iosApp.xcodeproj`, select the `iosApp` scheme and the **My Mac** destination.
2. Run (⌘R). The Kotlin framework is built by the same Gradle phase as for iOS. If Xcode reports
   a missing `Shared.framework`, build it first with `./gradlew :shared:linkDebugFrameworkMacosArm64`.

From the command line:

```bash
cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' -derivedDataPath /tmp/dhbwderived-mac build
```

## Things that differ from iOS

- No widget and no App Group. The database is in the app's sandbox container.
- Background checks run only while the app is open; there is no background task scheduler on macOS.
- Lecture reminders are calendar notifications, subject to the same 64-pending-request limit as iOS.

## Tests

```bash
./gradlew desktopTest          # module suites plus the Compose UI tests (JVM)
./gradlew testDebugUnitTest    # Android unit tests
```

Shared logic is tested in the modules' own suites; the SwiftUI layer has no automated tests.

## Troubleshooting

- **"Library not loaded: Shared.framework"**: the "Embed Shared.framework (macOS)" phase did not run
  or the framework was not built. Build the framework again and clean the derived data.
- **"App is damaged and can't be opened"** on a locally built app: the quarantine attribute is set
  on a downloaded copy. `xattr -dr com.apple.quarantine <path-to-app>` removes it.
- **Linker errors about `sqlite3_*` symbols**: the macOS framework was linked statically. It must
  stay dynamic on macOS (see AGENTS.md).
