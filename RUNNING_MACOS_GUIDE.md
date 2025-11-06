# Running and Testing on macOS

## Quick Start - Run the App

### Option 1: Run directly from Gradle (Recommended for Development)
```bash
./gradlew :composeApp:run
```
This will compile and launch the desktop app immediately.

### Option 2: Run with hot reload for development
```bash
./gradlew :composeApp:runHot
```
This enables hot reload - changes to your code will be reflected without full restart.

### Option 3: Build and run the packaged app
```bash
# Build the app
./gradlew :composeApp:createDistributable

# Run it
open composeApp/build/compose/binaries/main/app/DHBW\ Horb\ Studenten\ App.app
```

## Testing

### Run all tests
```bash
./gradlew :composeApp:test
```

### Run specific test suites
```bash
# Desktop tests only
./gradlew :composeApp:desktopTest

# Android unit tests
./gradlew :composeApp:testDebugUnitTest

# iOS tests (requires macOS with Xcode)
./gradlew :composeApp:iosSimulatorArm64Test
```

### Run tests with coverage
```bash
./gradlew :composeApp:test --info
```

## Building Distributable Packages

### Create DMG (macOS installer)
```bash
./gradlew :composeApp:packageDmg
```
Output: `composeApp/build/compose/binaries/main/dmg/*.dmg`

### Create distributable app
```bash
./gradlew :composeApp:createDistributable
```
Output: `composeApp/build/compose/binaries/main/app/`

### Create unsigned distributable for testing
```bash
./gradlew :composeApp:createDistributable
```
Then test by opening: `composeApp/build/compose/binaries/main/app/DHBW Horb Studenten App.app`

## Development Workflow

### 1. Standard Development Cycle
```bash
# Start the app with hot reload
./gradlew :composeApp:runHot

# Make changes to your Kotlin/Compose code
# The app will auto-reload
```

### 2. Quick Test Cycle
```bash
# Run tests in watch mode
./gradlew :composeApp:test --continuous

# In another terminal, make code changes
# Tests will automatically re-run
```

### 3. Build and Test DMG
```bash
# Build DMG
./gradlew :composeApp:packageDmg

# Open the DMG to test installation
open composeApp/build/compose/binaries/main/dmg/*.dmg
```

## Debugging

### Run with debug logging
```bash
./gradlew :composeApp:run --info
```

### Run with Kotlin compiler debugging
```bash
./gradlew :composeApp:run --debug
```

### Attach debugger from IntelliJ IDEA
1. Open the project in IntelliJ IDEA or Android Studio
2. Set breakpoints in your Kotlin code
3. Click "Run" → "Debug 'Main'" or use the debug icon
4. The app will launch with debugger attached

### Check for issues
```bash
# Check for compilation errors
./gradlew :composeApp:compileKotlinDesktop

# Check all targets compile
./gradlew :composeApp:compileKotlin
```

## Platform-Specific Testing

### Test Desktop (JVM) Target
```bash
# Compile
./gradlew :composeApp:compileKotlinDesktop

# Test
./gradlew :composeApp:desktopTest

# Run
./gradlew :composeApp:run
```

### Test macOS Native Targets
```bash
# Compile ARM64
./gradlew :composeApp:compileKotlinMacosArm64

# Compile X64
./gradlew :composeApp:compileKotlinMacosX64

# Note: Native macOS targets don't have a direct "run" command yet
# They're used for framework embedding in Swift apps
```

### Test iOS Targets (requires Xcode)
```bash
# Compile
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Test
./gradlew :composeApp:iosSimulatorArm64Test

# Run in simulator
cd iosApp
xcodebuild -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15' build
open iosApp.xcodeproj
# Then run from Xcode
```

### Test Android Target
```bash
# Compile
./gradlew :composeApp:compileDebugKotlinAndroid

# Test
./gradlew :composeApp:testDebugUnitTest

# Build APK
./gradlew :composeApp:assembleDebug

# Install on connected device
./gradlew :composeApp:installDebug
```

## Troubleshooting

### Issue: "Cannot find Java runtime"
```bash
# Check Java version
java -version

# Should be Java 11 or higher
# If not, install from https://adoptium.net/
```

### Issue: App won't launch - "App is damaged"
```bash
# Remove quarantine attribute
xattr -cr "composeApp/build/compose/binaries/main/app/DHBW Horb Studenten App.app"

# Then try to open again
open "composeApp/build/compose/binaries/main/app/DHBW Horb Studenten App.app"
```

### Issue: Gradle daemon issues
```bash
# Stop all Gradle daemons
./gradlew --stop

# Clear Gradle cache
rm -rf ~/.gradle/caches/

# Clean and rebuild
./gradlew clean build
```

### Issue: Configuration cache problems
```bash
# Disable configuration cache temporarily
./gradlew :composeApp:run --no-configuration-cache
```

### Issue: Native library errors
```bash
# Clear build directory
./gradlew :composeApp:clean

# Rebuild everything
./gradlew :composeApp:build
```

## Performance Testing

### Profile app startup
```bash
./gradlew :composeApp:run --profile
```
Results: `build/reports/profile/`

### Memory profiling
Use Xcode Instruments or JProfiler with:
```bash
./gradlew :composeApp:run -Dcom.sun.management.jmxremote
```

## Continuous Integration

### GitHub Actions example workflow
```yaml
- name: Test macOS Desktop App
  run: ./gradlew :composeApp:desktopTest

- name: Build DMG
  run: ./gradlew :composeApp:packageDmg
  
- name: Upload DMG
  uses: actions/upload-artifact@v3
  with:
    name: macos-dmg
    path: composeApp/build/compose/binaries/main/dmg/*.dmg
```

## IDE Integration

### IntelliJ IDEA / Android Studio
1. Open project root folder
2. Wait for Gradle sync
3. Find "Main.kt" in `desktopMain`
4. Click the green play button next to `fun main()`
5. Or use Run Configurations:
   - Run → Edit Configurations
   - Add new "Kotlin" configuration
   - Main class: `de.joinside.dhbw.MainKt`
   - Module: `composeApp.desktopMain`

### VS Code with Kotlin plugin
1. Install Kotlin extension
2. Open terminal
3. Run: `./gradlew :composeApp:run`

## Best Practices

1. **Development**: Use `./gradlew :composeApp:runHot` for fastest iteration
2. **Testing**: Run `./gradlew :composeApp:test` before commits
3. **Distribution**: Test DMG installation on a clean Mac before releasing
4. **Debugging**: Use IDE debugger for best experience
5. **CI/CD**: Automate builds and tests in your pipeline

## Next Steps

- For App Store submission, see `MAC_APP_STORE_GUIDE.md`
- For signing and notarization, see the guide for required certificates
- For production builds, use release mode with proper signing

