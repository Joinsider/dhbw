# Mac App Store

The Mac app is not a separate product. It is the macOS platform of the existing iOS app record
(bundle id `de.fampopprol.dhbw`), built from the SwiftUI target in `iosApp/`. Adding the platform to
the record makes the purchase universal, and the Mac build replaces the "Designed for iPad" app.
See AGENTS.md, "macOS is the SwiftUI app", for why the target is built this way.

## Steps

1. **App Store Connect**: open the existing app, add the **macOS** platform, and create a macOS
   version with the same metadata. Release notes are maintained in `metadata/` and fastlane; they
   are not changed by this guide.
2. **Build settings** (already set in the project): macOS 26, Apple Silicon only, sandbox entitlements
   in `iosApp/iosApp/iosApp-macOS.entitlements`, hardened runtime on. Xcode's automatic signing needs
   an Apple Distribution certificate and a Mac App Store provisioning profile for the team.
3. **Archive**:
   ```bash
   cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release \
     -destination 'generic/platform=macOS' -archivePath /tmp/dhbw-mas.xcarchive \
     -allowProvisioningUpdates archive
   ```
   The embed script phase "Embed Shared.framework (macOS)" runs during the archive, because the
   macOS framework is dynamic and Kotlin's own embed task skips it.
4. **Upload**: in Xcode Organizer choose *Distribute App → App Store Connect*, or export the archive
   with `xcodebuild -exportArchive` (method `app-store-connect`) and upload the resulting `.pkg` with
   Transporter, or with `xcrun altool --upload-app -t macos -f <file>.pkg` and the API key.
5. **Submit**: select the uploaded build on the macOS version in App Store Connect and submit it for
   review.

## Troubleshooting

- **Sandbox violation or missing file access**: the app may only write inside its container and to
  folders the user picks in a save panel. Check `iosApp-macOS.entitlements` first.
- **Network failures**: the `network.client` entitlement is required for outgoing requests in the sandbox.
