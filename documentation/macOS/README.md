# macOS

The Mac app is the SwiftUI app in `iosApp/`, built for macOS from the same target as iOS (see
AGENTS.md, "macOS is the SwiftUI app"). This page covers how it is distributed. Running it locally
is in `RUNNING_MACOS_GUIDE.md`; the Mac App Store steps are in `MAC_APP_STORE_GUIDE.md`.

## Channels

- **Mac App Store**: the macOS platform of the existing iOS app record, so the same purchase covers
  both. Steps: `MAC_APP_STORE_GUIDE.md`.
- **GitHub release and Homebrew**: a Developer ID signed, notarized disk image built by the
  `build-dmg-macos` job in `.github/workflows/build-release.yml`. The asset is
  `dhbw-student-app-v<version>.dmg`; the app inside is `DHBW-Horb.app`. Because the image is
  notarized, the self-signing workaround that earlier releases required is no longer needed.
  The Homebrew cask lives outside this repository and is not verified here.

## Release job

`build-dmg-macos` runs these steps, each with a script or plist in `packaging/macos/`:

1. Archive `iosApp/iosApp.xcodeproj` for macOS.
2. Export the archive with Developer ID (`ExportOptions-DeveloperID.plist`).
3. Notarize the `.app` with the App Store Connect API key (`notarize-submission.sh`).
4. Package the stapled `.app` into the disk image (`make-dmg.sh`), sign it, and notarize it.

Secrets the job reads (set in the repository settings):

| Secret | Holds |
|---|---|
| `MACOS_DEVID_P12_BASE64` | Developer ID Application certificate, exported as `.p12`, base64 |
| `MACOS_DEVID_P12_PASSWORD` | Password of that `.p12` |
| `ASC_KEY_P8_BASE64` | App Store Connect API key (`AuthKey_<id>.p8`), base64 |
| `ASC_KEY_ID` | Key id of that API key |
| `ASC_ISSUER_ID` | Issuer id of the App Store Connect team |

The same API key authenticates `xcodebuild -allowProvisioningUpdates`, so the job needs no other
Apple credential.
