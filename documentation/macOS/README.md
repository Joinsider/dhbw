# macOS

The Mac app is the SwiftUI app in `iosApp/`, built for macOS from the same target as iOS (see
AGENTS.md, "macOS is the SwiftUI app"). This page covers how it is distributed. Running it locally
is in `RUNNING_MACOS_GUIDE.md`; the Mac App Store steps are in `MAC_APP_STORE_GUIDE.md`.

## Channels

- **Mac App Store**: the macOS platform of the existing iOS app record, so the same purchase covers
  both. Steps: `MAC_APP_STORE_GUIDE.md`.
- **GitHub release and Homebrew**: a Developer ID signed, notarized disk image built by
  `.github/workflows/build-macos.yml`. The release workflow calls it as its `build-dmg-macos` job;
  the asset is `dhbw-student-app-v<version>.dmg` and the app inside is `DHBW-Horb.app`. Because the
  image is notarized, the self-signing workaround that earlier releases required is no longer needed.
  The Homebrew cask lives outside this repository and is not verified here.

## Build job

`build-macos.yml` runs on its own as well: Actions → "Build macOS App" → Run workflow builds the
selected branch with the version in `iosApp/Configuration/Config.xcconfig` and keeps the DMG as
the run artifact `dmg-macos` for 14 days, named `dhbw-student-app-v<version>-<commit>.dmg`. Nothing
is released. Called from the release workflow it builds the tag, fails if `Config.xcconfig` does
not carry the release version, and hands the DMG to the release.

Steps, each with a script or plist in `packaging/macos/`:

1. Install the Developer ID certificate into a temporary keychain and the Developer ID profile
   into Xcode's profile directory; the profile is checked for app id, type and keychain group.
2. Archive `iosApp/iosApp.xcodeproj` for macOS with manual signing.
3. Export the archive with Developer ID (`ExportOptions-DeveloperID.plist`, the profile is added
   at runtime) and check signature, hardened runtime, embedded profile and entitlements.
4. Notarize the `.app` with the App Store Connect API key (`notarize-submission.sh`).
5. Package the stapled `.app` into the disk image (`make-dmg.sh`), sign it, and notarize it.

Signing is manual because automatic signing needs an Apple Development certificate for the
archive step, and with an API key Xcode creates a new one on every CI run. The profile is needed
in any case: `keychain-access-groups` is a restricted entitlement on macOS, and an app that
carries it without an embedded profile is not allowed to launch.

## Secrets

Repository secrets (Settings → Secrets and variables → Actions). The release workflow passes them
on with `secrets: inherit`.

| Secret | Holds |
|---|---|
| `MACOS_DEVID_P12_BASE64` | Developer ID Application certificate with its private key, exported as `.p12`, base64 |
| `MACOS_DEVID_P12_PASSWORD` | Password of that `.p12` |
| `MACOS_DEVID_PROFILE_BASE64` | Developer ID provisioning profile for `de.fampopprol.dhbw`, base64 |
| `ASC_KEY_P8_BASE64` | App Store Connect API key (`AuthKey_<id>.p8`), base64 |
| `ASC_KEY_ID` | Key id of that API key |
| `ASC_ISSUER_ID` | Issuer id of the App Store Connect team |

How to get each one:

- **Certificate**: Keychain Access → My Certificates → "Developer ID Application: … (56T6W6Z755)"
  → Export as `.p12` with a password. Expand the entry first so the private key is included.
- **Profile**: developer.apple.com → Certificates, Identifiers & Profiles → Profiles → `+` →
  Distribution → **Developer ID**, platform macOS, App ID `de.fampopprol.dhbw`, the Developer ID
  Application certificate above. The job looks the profile up by its UUID, so its name is free.
  A new profile is needed when the certificate is renewed or the App ID's capabilities change.
- **API key**: App Store Connect → Users and Access → Integrations → App Store Connect API →
  Team Keys → `+` with the role *Developer* (enough for notarization). The `.p8` can be
  downloaded once; key id and issuer id are shown on the same page.

Encoding and storing them with the GitHub CLI:

```bash
base64 -i DeveloperID.p12 | gh secret set MACOS_DEVID_P12_BASE64
gh secret set MACOS_DEVID_P12_PASSWORD
base64 -i DHBW_Horb_Developer_ID.provisionprofile | gh secret set MACOS_DEVID_PROFILE_BASE64
base64 -i AuthKey_XXXXXXXXXX.p8 | gh secret set ASC_KEY_P8_BASE64
gh secret set ASC_KEY_ID
gh secret set ASC_ISSUER_ID
```

The first step of the job names every secret that is missing. The release cannot be published
while the macOS job fails: `Publish Release` waits for every build job.
