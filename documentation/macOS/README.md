# macOS

The Mac app is the SwiftUI app in `iosApp/`, built for macOS from the same target as iOS (see
AGENTS.md, "macOS is the SwiftUI app"). This page covers how it is distributed. Running it locally
is in [RUNNING_MACOS_GUIDE.md](RUNNING_MACOS_GUIDE.md); the Mac App Store steps are in [MAC_APP_STORE_GUIDE.md](MAC_APP_STORE_GUIDE.md).

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

The workflow has two jobs on two separate runners, so the signing key never shares a machine
with the build:

1. **build** (no secrets): archives `iosApp/iosApp.xcodeproj` for macOS with
   `CODE_SIGNING_ALLOWED=NO`. This is the only place Gradle runs, and with it every Kotlin and
   Android plugin and dependency. The unsigned `.app` is zipped with `ditto` (keeps the framework's
   symlinks) and handed over as the artifact `macos-unsigned-app`, kept for one day and never
   attached to a release.
2. **sign** (environment `release-signing`, fresh runner): runs only Apple's tools and the scripts in
   `packaging/macos/`.
   - imports the Developer ID certificate into a temporary keychain with `-x`, so the private key
     cannot be exported again: a misbehaving step could sign with it during the run, not take it
     along;
   - `sign-app.sh` checks the profile (Developer ID, app id, keychain group, and that it lists the
     signing certificate — without that macOS refuses to launch the app), embeds it, signs
     `Shared.framework` and then the app with the entitlements from
     `iosApp/iosApp/iosApp-macOS.entitlements` (with `$(AppIdentifierPrefix)` expanded and the
     application and team identifier added), hardened runtime and a secure timestamp, and verifies
     the result;
   - notarizes the `.app` (`notarize-submission.sh`), builds and signs the disk image
     (`make-dmg.sh`) and notarizes that too.

What the split protects: the key cannot be stolen by build code, and nothing that runs during the
build can sign anything. What it does not protect: whatever the build job produces gets signed, so
a compromised dependency that changes the app itself still ends up in a signed app. The release
workflow attaches a build provenance attestation to every asset (`gh attestation verify <file>
--repo Joinsider/dhbw`), which shows where an asset came from but not that its code is harmless.

`sign-app.sh` works locally too, against the login keychain, for an archive made with
`CODE_SIGNING_ALLOWED=NO`:

```bash
packaging/macos/sign-app.sh DHBW-Horb.app "Developer ID Application: Johannes Popp (56T6W6Z755)" \
  DHBW_Horb_Developer_ID.provisionprofile iosApp/iosApp/iosApp-macOS.entitlements
```

Signing is manual because automatic signing needs an Apple Development certificate for the
archive step, and with an API key Xcode creates a new one on every CI run. The profile is needed
in any case: `keychain-access-groups` is a restricted entitlement on macOS, and an app that
carries it without an embedded profile is not allowed to launch. If the app gains an entitlement,
it goes into `iosApp-macOS.entitlements` and is picked up by both Xcode and `sign-app.sh`; a nested
bundle (extension, XPC service, login item) makes `sign-app.sh` stop, because it would need its own
entitlements and profile.

## Secrets

Environment secrets of `release-signing` (Settings → Environments). The release workflow passes
them on with `secrets: inherit`; an environment secret wins over a repository secret of the same
name, so the protection only holds once the repository copies are deleted.

The environment should allow only `main` and the release tags, so a workflow started from any
other branch gets no signing secrets. Its jobs: `sign` in `build-macos.yml`, and
`build-android-apk` and `build-android-aab` in `build-release.yml`, which hold the Android upload
keystore (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_PASSWORD`). Adding
yourself as a required reviewer makes every release wait for one approval click.

```bash
gh api -X PUT repos/Joinsider/dhbw/environments/release-signing \
  -F 'deployment_branch_policy[protected_branches]=false' \
  -F 'deployment_branch_policy[custom_branch_policies]=true'
gh api -X POST repos/Joinsider/dhbw/environments/release-signing/deployment-branch-policies \
  -f name=main -f type=branch
gh api -X POST repos/Joinsider/dhbw/environments/release-signing/deployment-branch-policies \
  -f name='v*' -f type=tag
```

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
base64 -i DeveloperID.p12 | gh secret set MACOS_DEVID_P12_BASE64 --env release-signing
gh secret set MACOS_DEVID_P12_PASSWORD --env release-signing
base64 -i DHBW_Horb_Developer_ID.provisionprofile | gh secret set MACOS_DEVID_PROFILE_BASE64 --env release-signing
base64 -i AuthKey_XXXXXXXXXX.p8 | gh secret set ASC_KEY_P8_BASE64 --env release-signing
gh secret set ASC_KEY_ID --env release-signing
gh secret set ASC_ISSUER_ID --env release-signing
```

The first step of the job names every secret that is missing. The release cannot be published
while the macOS job fails: `Publish Release` waits for every build job.
