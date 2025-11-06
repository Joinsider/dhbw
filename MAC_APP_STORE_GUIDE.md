# macOS App Store Deployment Guide

This project supports macOS distribution through the Mac App Store using Compose Multiplatform.

## Build Targets

This project includes two macOS-related targets:

### 1. Desktop (JVM) Target - **RECOMMENDED FOR MAC APP STORE**
- **Purpose**: Production-ready macOS application for App Store distribution
- **Technology**: JVM-based Compose Desktop
- **Status**: Fully supported and production-ready
- **App Store Compatible**: ✅ Yes

### 2. Native macOS Targets (macosArm64, macosX64)
- **Purpose**: Native Kotlin/Native framework (experimental)
- **Technology**: Kotlin/Native with Compose Multiplatform
- **Status**: ⚠️ Experimental - Limited Compose UI support in current version
- **App Store Compatible**: 🚧 Not recommended yet
- **Use Case**: Future-proofing, framework embedding for Swift/SwiftUI apps

## Building for Mac App Store (Desktop/JVM Target)

### Prerequisites
1. macOS development machine
2. Xcode installed
3. Apple Developer account
4. Developer ID Application certificate
5. Mac App Store distribution certificate

### Build Commands

#### Create DMG for distribution outside App Store:
```bash
./gradlew :composeApp:packageDmg
```
Output: `composeApp/build/compose/binaries/main/dmg/`

#### Create PKG for App Store submission:
```bash
./gradlew :composeApp:packagePkg
```
Output: `composeApp/build/compose/binaries/main/pkg/`

#### Create signed and notarized DMG:
```bash
./gradlew :composeApp:packageDmg -Pcompose.desktop.mac.sign=true
```

### Configuration

The macOS app configuration is in `composeApp/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        mainClass = "de.joinside.dhbw.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "DHBW Horb Studenten App"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("icon.icns"))
                bundleID = "de.joinside.dhbw"
                
                // Configure for App Store submission:
                signing {
                    sign.set(true)
                    identity.set("3rd Party Mac Developer Application: Your Name (TEAM_ID)")
                }
                appStore.set(true)
                
                // App category for App Store
                appCategory = "public.app-category.education"
                
                // Entitlements for App Store (create separate .entitlements file)
                entitlementsFile.set(project.file("macOS.entitlements"))
                runtimeEntitlementsFile.set(project.file("macOS-runtime.entitlements"))
            }
        }
    }
}
```

### Required Entitlements

Create `composeApp/macOS.entitlements`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.app-sandbox</key>
    <true/>
    <key>com.apple.security.network.client</key>
    <true/>
    <key>com.apple.security.files.user-selected.read-write</key>
    <true/>
</dict>
</plist>
```

Create `composeApp/macOS-runtime.entitlements`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.app-sandbox</key>
    <true/>
    <key>com.apple.security.network.client</key>
    <true/>
</dict>
</plist>
```

### Signing and Notarization

#### 1. Code Signing
```bash
# Sign the app
codesign --force --deep --sign "3rd Party Mac Developer Application: Your Name (TEAM_ID)" \
  --entitlements composeApp/macOS.entitlements \
  --options runtime \
  "composeApp/build/compose/binaries/main/app/DHBW Horb Studenten App.app"
```

#### 2. Create signed PKG
```bash
productbuild --component "DHBW Horb Studenten App.app" /Applications \
  --sign "3rd Party Mac Developer Installer: Your Name (TEAM_ID)" \
  DHBWApp.pkg
```

#### 3. Notarize (for non-App Store distribution)
```bash
# Submit for notarization
xcrun notarytool submit DHBWApp.dmg \
  --apple-id "your-email@example.com" \
  --team-id "TEAM_ID" \
  --password "app-specific-password" \
  --wait

# Staple the notarization ticket
xcrun stapler staple DHBWApp.dmg
```

#### 4. Upload to App Store Connect
```bash
xcrun altool --upload-app --type macos --file DHBWApp.pkg \
  --username "your-email@example.com" \
  --password "app-specific-password"
```

Or use [Transporter app](https://apps.apple.com/app/transporter/id1450874784) for uploading.

### App Store Submission Checklist

- [ ] Set correct bundle ID in `build.gradle.kts`
- [ ] Configure version numbers (packageVersion)
- [ ] Create app icon set (icon.icns - 512x512, 256x256, 128x128, 64x64, 32x32, 16x16)
- [ ] Add required entitlements
- [ ] Configure signing certificates
- [ ] Test app in sandbox mode
- [ ] Create App Store Connect app listing
- [ ] Prepare screenshots (1280x800, 1440x900, 2560x1600, 2880x1800)
- [ ] Write app description and metadata
- [ ] Build and sign release package
- [ ] Upload via Transporter or altool
- [ ] Submit for review

## Native macOS Framework (Experimental)

The native macOS targets are included for future compatibility but are not yet suitable for App Store distribution.

### Build Native Framework
```bash
# Build for Apple Silicon
./gradlew :composeApp:linkDebugFrameworkMacosArm64

# Build for Intel
./gradlew :composeApp:linkDebugFrameworkMacosX64

# Universal Framework
./gradlew :composeApp:createXCFramework
```

The framework can be embedded in a SwiftUI app (see `macosApp/` directory for example).

## Troubleshooting

### Issue: "App is damaged and can't be opened"
- **Solution**: App needs to be properly signed and notarized

### Issue: Sandbox violations
- **Solution**: Add required entitlements in `macOS.entitlements`

### Issue: Network requests fail
- **Solution**: Add `com.apple.security.network.client` entitlement

### Issue: Can't save files
- **Solution**: Add `com.apple.security.files.user-selected.read-write` entitlement

## References

- [Compose Multiplatform Documentation](https://github.com/JetBrains/compose-multiplatform)
- [Apple App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Apple Code Signing Guide](https://developer.apple.com/library/archive/documentation/Security/Conceptual/CodeSigningGuide/)
- [Gradle Compose Desktop Plugin](https://github.com/JetBrains/compose-jb/tree/master/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop)

## Current Status

✅ **Desktop (JVM) Target**: Ready for Mac App Store distribution
- All platform-specific implementations complete
- Proper keychain integration for secure storage
- Room database support
- Network client configured

⚠️ **Native macOS Targets**: Framework builds successfully but limited UI support
- Platform implementations complete (SecureStorage, Database, etc.)
- Waiting for full Compose UI support in future Compose Multiplatform versions
- Can be used for embedding in Swift/SwiftUI apps

**Recommendation**: Use the Desktop (JVM) target for Mac App Store publishing.

