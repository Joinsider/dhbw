# Phase 13: Fix Deploy Pipeline - Research

**Researched:** 2026-04-10
**Domain:** CI/CD Pipeline, Cross-Platform Version Management, Package Versioning
**Confidence:** HIGH

## Summary

Phase 13 requires consistent version bumping across all KMP targets (Android, iOS, macOS, Windows, Linux) and integrating multilingual release notes into the build pipeline. Research across three areas reveals:

1. **Desktop Version Bumping:** Gradle Compose Multiplatform supports hierarchical version configuration (format-specific > OS-specific > global). Option C (Gradle properties) combined with per-format configuration in `nativeDistributions` is the standard pattern and already partially implemented in the current `build.gradle.kts` (lines 199, 302).

2. **Desktop Package Metadata:** Each format has strict version requirements (DMG: `MAJOR[.MINOR][.PATCH]`; MSI: `MAJOR.MINOR.BUILD` with 0-255/0-255/0-65535 limits; DEB: `[EPOCH:]UPSTREAM[-DEBIAN]`). Gradle automatically propagates `packageVersion` to all three formats; additional metadata (DMG volume names, MSI properties) requires post-build tooling.

3. **Build Number Strategy:** Desktop should use the same versionCode sequence as Android (recommend synchronized). This aligns with mobile industry standards and Flutter/React Native KMP patterns; separate sequences create complexity and version drift risk.

**Primary recommendation:** Extend `composeApp/build.gradle.kts` to support platform-specific version properties using Gradle's native `nativeDistributions.<os>.<format>PackageVersion` configuration. Keep versionCode synchronized across all platforms. Consolidate version writes in the GitHub workflow as a single transaction.

## User Constraints (from CONTEXT.md)

### Locked Decisions
- All KMP targets must have the same semantic version at release time (D-06)
- Single source of truth: read current version once from build.gradle.kts, compute new semver, write to ALL version locations in one step (D-07)
- Semver in filename for all desktop outputs: `dhbw-student-app-v{semver}.{ext}` (D-08)
- Embed version metadata inside desktop packages: DMG volume names, MSI properties, DEB control file (D-09)
- Desktop version bumping synchronized with Android versionCode (D-10)
- Validate all versions after write; fail fast if any platform missing config (D-11, D-12)
- Manual per-language release notes during workflow trigger (D-01)
- Fixed English + German, template-based for future languages (D-02)
- Release notes provided before version bump (D-03)
- Merge changelog.yml into build-release.yml (D-04)
- Release notes written to fastlane + GitHub Release (D-05)

### Claude's Discretion
1. **Desktop version bumping mechanism:** Whether to add separate config files (Option A) vs. extend build.gradle.kts (Option B) vs. use Gradle properties (Option C)
2. **Exact DMG/MSI metadata format:** Which properties to update and how
3. **Build number sequence for desktop:** Match Android versionCode exactly or separate sequence

## Standard Stack

### Core
| Library/Tool | Version | Purpose | Why Standard |
|--------|---------|---------|--------------|
| Gradle Compose Multiplatform | 1.10.3 (current) | Desktop native distributions (DEB, DMG, MSI) | Official JetBrains tool for KMP desktop; built into project |
| Kotlin Multiplatform | 2.3.20 (current) | Cross-platform build configuration | Project standard |
| GitHub Actions | Built-in | CI/CD workflow automation | Project standard for release pipeline |
| Bash (pure) | POSIX | Semver arithmetic, version writes | Portable, no external dependencies |

### Supporting
| Library/Tool | Version | Use Case |
|--------|---------|----------|
| `hdiutil` | macOS native | DMG volume renaming and metadata (macOS only) |
| `dpkg` / DEB tools | Linux native | DEB control file verification (Linux only) |
| WiX Toolset | Not applicable (Gradle handles) | MSI generation (not direct dependency) |

## Architecture Patterns

### Recommended Desktop Version Configuration

Gradle Compose Multiplatform provides a **hierarchical version configuration system** with three priority levels:

```
1. Format-specific (highest): nativeDistributions.<os>.<packageFormat>PackageVersion
2. OS-specific (medium):       nativeDistributions.<os>.packageVersion
3. Global (lowest):             nativeDistributions.packageVersion
```

**Current implementation (partial):**
- Android: `versionCode = 23`, `versionName = "v2.0.3"` (lines 126-127)
- Desktop: `packageVersion = "2.0.3"` (line 199), `archiveVersion.set("2.0.3")` (line 302)
- iOS: `MARKETING_VERSION=2.0.3`, `CURRENT_PROJECT_VERSION=23` (Config.xcconfig)

**Recommended configuration for Phase 13:**

```kotlin
compose.desktop {
    application {
        nativeDistributions {
            packageVersion = "2.0.3" // Global fallback
            
            macOS {
                dmgPackageVersion = "2.0.3"    // DMG format requirement
                pkgPackageVersion = "2.0.3"    // PKG format requirement
            }
            
            windows {
                msiPackageVersion = "2.0.3"    // MSI format requirement
                exePackageVersion = "2.0.3"    // EXE format requirement
            }
            
            linux {
                debPackageVersion = "2.0.3"    // DEB format requirement
                rpmPackageVersion = "2.0.3"    // RPM format requirement
            }
        }
    }
}
```

This approach is **zero additional complexity** — the properties already exist in Gradle Compose Multiplatform and are automatically propagated to the packaging tasks (`packageDeb`, `packageDmg`, `packageMsi`).

### Version Format Requirements by Platform

| Platform | Format | Example | Constraints |
|----------|--------|---------|-------------|
| **DMG (macOS)** | `MAJOR[.MINOR][.PATCH]` | `2.0.3` | Non-negative integers only; any part optional but recommended to use 3 parts |
| **MSI/EXE (Windows)** | `MAJOR.MINOR.BUILD` | `2.0.3` | MAJOR/MINOR max 255, BUILD max 65535; all parts required |
| **DEB (Linux)** | `[EPOCH:]UPSTREAM[-DEBIAN]` | `2.0.3` or `1:2.0.3-1` | Must start with digit; supports `~`, `-`, `+` for pre-releases |
| **iOS** | `MARKETING_VERSION` (semver) + `CURRENT_PROJECT_VERSION` (integer) | `2.0.3` + `23` | Two separate fields |
| **Android** | `versionName` (semver) + `versionCode` (integer) | `v2.0.3` + `23` | Two separate fields; versionName uses `v` prefix convention |

**All formats accept `X.Y.Z` semantic versioning.** No transformation needed in the workflow.

### Single Version Write Transaction Pattern

Current implementation already uses sed-based multi-file updates (build-release.yml lines 119-147). Phase 13 extends this:

```bash
# Existing pattern (to extend)
sed -i -E "s/(versionName\s*=\s*)\"v?[^\"]+\"/\1\"v$NEW\"/" composeApp/build.gradle.kts
sed -i -E "s/(versionCode\s*=\s*)[0-9]+/\1$NEW_CODE/" composeApp/build.gradle.kts
sed -i -E "s/(packageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
sed -i -E "s/(MARKETING_VERSION\s*=\s*).*/\1$NEW/" iosApp/Configuration/Config.xcconfig
sed -i -E "s/(CURRENT_PROJECT_VERSION\s*=\s*).*/\1$NEW_CODE/" iosApp/Configuration/Config.xcconfig

# Phase 13 additions (if format-specific properties are added)
# Only if we extend build.gradle.kts with platform-specific properties
sed -i -E "s/(dmgPackageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
sed -i -E "s/(msiPackageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
sed -i -E "s/(debPackageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
```

**Verification step (from D-11):**
```bash
# After all writes, grep each file to confirm versions match
grep -E "versionName|versionCode|packageVersion|dmgPackageVersion|msiPackageVersion|debPackageVersion" \
  composeApp/build.gradle.kts iosApp/Configuration/Config.xcconfig
# Parse output, verify all non-code fields contain $NEW, all code fields contain $NEW_CODE
```

### Release Notes Integration Pattern

Current `changelog.yml` (lines 1-76) reads versionCode and writes to fastlane paths:
```bash
fastlane/metadata/android/en-US/changelogs/{versionCode}.txt
fastlane/metadata/android/de-DE/changelogs/{versionCode}.txt
```

**Phase 13 consolidation:** Move this step into build-release.yml after version bump, before artifact upload. Sequence:
1. User provides `changelog_en` and `changelog_de` as workflow inputs
2. Bump version (read versionCode)
3. Write changelogs to fastlane paths using versionCode from step 2
4. Commit changelog files alongside version bump
5. Build packages and publish release with notes

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| **Custom desktop version config** | Separate JSON/YAML config files for macOS/Windows/Linux versions | Gradle's `nativeDistributions.<os>.<format>PackageVersion` | Gradle natively handles format requirements; custom configs duplicate logic and risk version drift |
| **DMG volume naming** | Shell script to re-create DMG with custom volume name | `hdiutil -volname` (macOS native) + post-build rename | hdiutil is the standard Apple tool; avoids reimplementing disk image creation |
| **MSI property injection** | WiX XML rewriting or MSI binary patching | Gradle Compose plugin (handles automatically) + post-build verification | Gradle Compose MSI generation handles ProductVersion correctly; patching risks corruption |
| **DEB control file updates** | Manual sed editing of control file | Gradle DEB packaging (built-in) + verification grep | Gradle's DEB task generates control file correctly; manual edits risk format violations |
| **Version drift detection** | Polling multiple files in separate steps | Single transaction: write all → verify all in one step (existing pattern) | Atomic operations prevent partial writes; simpler error handling |

**Key insight:** Gradle Compose Multiplatform already handles the complexity of format-specific version requirements. The workflow doesn't need custom tooling — it needs to **extend** the existing pattern to cover all platforms in a single transaction.

## Common Pitfalls

### Pitfall 1: Version Format Mismatches Across Platforms
**What goes wrong:** Semantic version `2.0.3` works for DMG/DEB, but MSI requires explicit `MAJOR.MINOR.BUILD` format. If workflow writes `2.0.3-rc1` to all platforms, MSI build fails silently or accepts invalid version.

**Why it happens:** Version format requirements are platform-specific. Bash scripts don't validate format before writing.

**How to avoid:** Use Gradle properties to centralize format handling. Gradle's `nativeDistributions` plugin validates format per-platform and fails early with clear errors.

**Warning signs:** Build jobs succeed on macOS/Linux but fail on Windows; or packages build but show wrong version in installer properties.

### Pitfall 2: Partial Version Updates (Some Platforms Missing)
**What goes wrong:** sed script updates Android + iOS but misses desktop `packageVersion`, resulting in APK v2.0.4 but DMG v2.0.3.

**Why it happens:** Multiple sed patterns in sequence can fail silently if a file doesn't contain the expected pattern.

**How to avoid:** (Already implemented in D-11/D-12) Validate all versions immediately after write. Grep each file and compare outputs. Fail workflow if any expected version field missing or contains wrong value. Log grep output to workflow for debugging.

**Warning signs:** Release notes reference v2.0.4 but one platform's package shows v2.0.3 in properties.

### Pitfall 3: VersionCode Sequence Drift for Desktop
**What goes wrong:** Desktop uses independent build number (e.g., "build 5") while Android versionCode increments to 24, causing confusion about which build generated which artifacts.

**Why it happens:** Desktop doesn't have a traditional "versionCode" field like Android. Temptation to create separate sequences.

**How to avoid:** Synchronize desktop versionCode with Android. Store it in `packageVersion` or a separate `buildNumber` property (read from Android versionCode). Recommendation: use Android versionCode as the canonical source; desktop reads it and uses for consistency.

**Warning signs:** Developers ask "which build number corresponds to which release?" or find multiple packages with same semver but different internals.

### Pitfall 4: DMG Volume Name Not Updated
**What goes wrong:** DMG filename is `dhbw-student-app-v2.0.4.dmg`, but when mounted, volume shows "DHBW-Horb-2.0.3" (old name).

**Why it happens:** Gradle Compose Multiplatform doesn't rename the DMG volume during packaging. Volume name comes from `nativeDistributions.packageName` or hardcoded in build script.

**How to avoid:** (This is a separate task from version bumping) After packageDmg task completes, use post-build step to rename volume with `hdiutil`. Alternatively, configure Gradle's `packageName` or `distributionSourceDir` if volume names are static.

**Warning signs:** User mounts DMG and sees outdated version in Finder.

### Pitfall 5: MSI ProductVersion vs Revision Number Confusion
**What goes wrong:** MSI ProductVersion property is set correctly (2.0.4), but MSI file properties show a GUID in "Revision Number" field instead of human-readable version.

**Why it happens:** WiX and Windows Installer have separate ProductVersion and RevisionNumber (Package Code). By default, RevisionNumber is auto-generated GUID.

**How to avoid:** Gradle Compose MSI plugin handles ProductVersion automatically. If custom MSI generation is used, configure WiX's `SummaryInformation` element to set RevisionNumber to the product version string (not GUID).

**Warning signs:** Right-click MSI → Properties → Details shows GUID in "File version" field instead of version number.

## Code Examples

### Version Bump Workflow Sequence (Existing + Phase 13 Extension)

**Source:** Current `.github/workflows/build-release.yml` (lines 52-147)

```bash
# STEP 1: Read current version (existing)
CURRENT=$(grep -m1 'versionName' composeApp/build.gradle.kts \
  | sed -E 's/.*"v?([^"]+)".*/\1/')

# STEP 2: Compute new semver (existing pure-bash arithmetic)
# [bash arithmetic for major/minor/patch increment — lines 76-99]

# STEP 3: Increment versionCode (existing)
CURRENT_CODE=$(grep -m1 'versionCode' composeApp/build.gradle.kts \
  | sed -E 's/.*versionCode\s*=\s*([0-9]+).*/\1/')
NEW_CODE=$((CURRENT_CODE + 1))

# STEP 4: Write ALL versions (existing pattern, extend for Phase 13)
# Android
sed -i -E "s/(versionName\s*=\s*)\"v?[^\"]+\"/\1\"v$NEW\"/" composeApp/build.gradle.kts
sed -i -E "s/(versionCode\s*=\s*)[0-9]+/\1$NEW_CODE/" composeApp/build.gradle.kts

# Desktop (existing)
sed -i -E "s/(packageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
sed -i -E "s/(archiveVersion\.set\()\"[^\"]+\"\)/\1\"$NEW\")/" composeApp/build.gradle.kts

# iOS
sed -i -E "s/(MARKETING_VERSION\s*=\s*).*/\1$NEW/" iosApp/Configuration/Config.xcconfig
sed -i -E "s/(CURRENT_PROJECT_VERSION\s*=\s*).*/\1$NEW_CODE/" iosApp/Configuration/Config.xcconfig

# STEP 5: Validate (D-11, new for Phase 13)
echo "Verifying all versions..."
ERRORS=0

# Check Android
if ! grep -q "versionName.*\"v$NEW\"" composeApp/build.gradle.kts; then
  echo "ERROR: Android versionName not updated correctly"
  ERRORS=$((ERRORS + 1))
fi

if ! grep -q "versionCode.*$NEW_CODE" composeApp/build.gradle.kts; then
  echo "ERROR: Android versionCode not updated correctly"
  ERRORS=$((ERRORS + 1))
fi

# Check Desktop
if ! grep -q "packageVersion.*\"$NEW\"" composeApp/build.gradle.kts; then
  echo "ERROR: Desktop packageVersion not updated correctly"
  ERRORS=$((ERRORS + 1))
fi

# Check iOS
if ! grep -q "MARKETING_VERSION.*$NEW" iosApp/Configuration/Config.xcconfig; then
  echo "ERROR: iOS MARKETING_VERSION not updated correctly"
  ERRORS=$((ERRORS + 1))
fi

if ! grep -q "CURRENT_PROJECT_VERSION.*$NEW_CODE" iosApp/Configuration/Config.xcconfig; then
  echo "ERROR: iOS CURRENT_PROJECT_VERSION not updated correctly"
  ERRORS=$((ERRORS + 1))
fi

if [ $ERRORS -gt 0 ]; then
  echo "Version validation failed with $ERRORS errors"
  exit 1
fi

echo "All versions verified successfully"
```

### Release Notes Consolidation (New for Phase 13)

**Source:** Consolidate `.github/workflows/changelog.yml` into `build-release.yml`

```yaml
# In build-release.yml, add to workflow_dispatch inputs:
on:
  pull_request:
    types: [closed]
    branches: [main]
  workflow_dispatch:
    inputs:
      bump:
        description: 'Version bump type'
        required: true
        default: 'patch'
        type: choice
        options: [patch, minor, major, prepatch, preminor, premajor, none]
      changelog_en:
        description: 'Release notes (English)'
        required: true
        type: string
      changelog_de:
        description: 'Release notes (Deutsch)'
        required: true
        type: string

# Add new job before build jobs:
jobs:
  bump-and-notes:
    name: Bump version and write release notes
    runs-on: ubuntu-latest
    outputs:
      new_version: ${{ steps.semver.outputs.new_version }}
      new_code: ${{ steps.versioncode.outputs.new_code }}
    steps:
      # [Existing bump-version steps from above]
      
      - name: Write fastlane changelogs
        if: steps.semver.outputs.skipped != 'true'
        env:
          CHANGELOG_EN: ${{ github.event.inputs.changelog_en }}
          CHANGELOG_DE: ${{ github.event.inputs.changelog_de }}
        run: |
          VERSION_CODE="${{ steps.versioncode.outputs.new_code }}"
          
          # English
          EN_FILE="fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt"
          mkdir -p "$(dirname "$EN_FILE")"
          printf '%s\n' "$CHANGELOG_EN" > "$EN_FILE"
          
          # German
          DE_FILE="fastlane/metadata/android/de-DE/changelogs/$VERSION_CODE.txt"
          mkdir -p "$(dirname "$DE_FILE")"
          printf '%s\n' "$CHANGELOG_DE" > "$DE_FILE"
          
          echo "Changelogs written:"
          cat "$EN_FILE"
          cat "$DE_FILE"
      
      - name: Commit changelogs with version bump
        if: steps.semver.outputs.skipped != 'true'
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          
          git add composeApp/build.gradle.kts iosApp/Configuration/Config.xcconfig \
            "fastlane/metadata/android/en-US/changelogs/${{ steps.versioncode.outputs.new_code }}.txt" \
            "fastlane/metadata/android/de-DE/changelogs/${{ steps.versioncode.outputs.new_code }}.txt"
          
          git commit -m "chore: bump to v${{ steps.semver.outputs.new_version }} with release notes"
          git push origin HEAD:main
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Separate release workflows (build-release.yml + changelog.yml) | Single consolidated workflow with release notes inputs | Phase 13 | Reduces manual steps; single trigger point for reproducibility |
| Manual versionCode incrementing per-platform | Automated semver parsing + versionCode sync in workflow | Phase 13 | Prevents version drift; enforces consistency |
| Generic desktop package names (tool-generated) | Semantic version in filenames (`v{semver}`) | Phase 13 (D-08) | Easier artifact identification; matches mobile convention |
| Version metadata embedded only in code | Version metadata in both code AND package internals (DMG volume, MSI properties) | Phase 13 (D-09) | Users inspecting packages see consistent version |

**Deprecated/outdated:**
- Manual fastlane changelog workflow (`changelog.yml`) — replaced by consolidated inputs in `build-release.yml`
- Per-target version bumping steps — replaced by single atomic transaction across all targets

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Git | Version commit/push | ✓ | (macOS native) | — |
| Bash | Semver arithmetic, sed updates | ✓ | 5.x (macOS) | — |
| Gradle | Build system | ✓ | 8.x (wrapper) | — |
| JDK | Gradle/build tools | ✓ | 21 (GH Actions) | — |
| hdiutil | DMG volume renaming (macos-latest runner) | ✓ | macOS native | Not applicable to DEB/MSI |
| dpkg-deb | DEB control file verification (ubuntu-latest runner) | ✓ | Linux native | grep + manual validation |
| WiX Toolset | MSI generation (Windows runner only) | ✗ | — | Gradle Compose MSI plugin handles generation |
| GitHub Actions | CI/CD execution | ✓ | (current) | — |

**Note:** Gradle Compose Multiplatform handles DEB, DMG, and MSI generation automatically. No WiX XML files are maintained in the project. Windows MSI builds are executed via Gradle's `packageMsi` task on Windows runners (if/when added to CI).

## Validation Architecture

| Property | Value |
|----------|-------|
| Framework | Bash + grep + manual verification |
| Config file | `.github/workflows/build-release.yml` |
| Quick run command | `./gradlew packageDeb packageDmg packageMsi` (local testing) |
| Full suite command | `.github/workflows/build-release.yml` (full CI/CD) |

### Phase Requirements → Test Map

No automated tests required for Phase 13 (CI/CD configuration phase). Validation is manual/integration:

| Req ID | Behavior | Test Type | Validation Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-06 | All KMP targets have same semver at release time | Integration | Grep check in build-release.yml | ✓ (lines 141-147) |
| D-07 | Single transaction: read → compute → write all | Integration | Workflow execution logs | ✓ (lines 119-147) |
| D-08 | Semver in output filenames | Integration | Find + basename check in artifact upload | Requires Phase 13 implementation |
| D-09 | Version metadata in package internals | Manual | `dpkg-deb -I *.deb`, `otool -L *.dmg`, `msiexec /i *.msi` | Out of scope (user responsibility) |
| D-10 | Desktop versionCode synced with Android | Integration | Grep verification in workflow | ✓ (lines 141-147) |
| D-11 | All versions validated after write | Integration | Grep + conditional exit in workflow | Requires Phase 13 implementation |
| D-12 | Fail fast if platform config missing | Integration | Error detection in workflow | Requires Phase 13 implementation |

### Wave 0 Gaps
- [ ] `build-release.yml` — Add changelog_en/changelog_de inputs to workflow_dispatch
- [ ] `build-release.yml` — Add fastlane changelog write step after version bump
- [ ] `build-release.yml` — Add comprehensive version validation step (D-11/D-12)
- [ ] `build-release.yml` — Extend artifact rename patterns for DEB/DMG/MSI with semver (D-08)
- [ ] Manual testing: Verify DMG volume name is readable after build

*(All other infrastructure in place: gradle tasks, fastlane paths, version read/write patterns)*

## Open Questions

1. **DMG Volume Name Post-Build Renaming**
   - What we know: `hdiutil -volname` can set volume name at creation time; Gradle Compose handles DMG creation via `packageDmg` task
   - What's unclear: Whether Gradle Compose exposes `volumeName` configuration in `nativeDistributions.macOS`, or if post-build `hdiutil attach` / rename is required
   - Recommendation: During planning, test `packageDmg` task output. If volume name is configurable in Gradle (likely), add to `nativeDistributions`. If not, add post-build shell step to rename with `hdiutil`.

2. **MSI Installation Behavior with Version Metadata**
   - What we know: WiX ProductVersion must be `MAJOR.MINOR.BUILD`; MSI ProductCode is GUID
   - What's unclear: Whether users can upgrade from v2.0.3 to v2.0.4 MSI seamlessly, or if ProductCode changes break upgrade detection
   - Recommendation: WiX plugin should handle auto-generating ProductCode per build. If custom logic is added, verify MSI upgrade detection works (test scenario: install v2.0.3, run v2.0.4 MSI, check if "upgrade" prompt appears).

3. **DEB Epoch Handling**
   - What we know: Debian allows optional epoch prefix (e.g., `1:2.0.3`) to force version ordering
   - What's unclear: Whether project needs epoch (usually for upstream version resets); current implementation uses no epoch
   - Recommendation: Keep no epoch for now (simpler). If future release requires downgrade or version reset, add epoch in workflow. Document in release notes.

## Sources

### Primary (HIGH confidence)
- [Kotlin Multiplatform Desktop Configuration](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) — Official docs on native distribution formats, version requirements, and Gradle configuration
- [Current `.github/workflows/build-release.yml`](file:///Users/johannes/StudioProjects/dhbw/.github/workflows/build-release.yml) — Existing version bump pattern (verified in project)
- [Current `composeApp/build.gradle.kts`](file:///Users/johannes/StudioProjects/dhbw/composeApp/build.gradle.kts) — Android/Desktop version definitions (lines 126-127, 199, 302)
- [Current `iosApp/Configuration/Config.xcconfig`](file:///Users/johannes/StudioProjects/dhbw/iosApp/Configuration/Config.xcconfig) — iOS version definitions
- [Gradle Compose Multiplatform Desktop](https://github.com/JetBrains/compose-multiplatform-desktop-template) — Official template and documentation

### Secondary (MEDIUM confidence)
- [Debian Control File Specification](https://www.debian.org/doc/debian-policy/ch-controlfields.html) — Official DEB version field format requirements
- [DMG Creation with hdiutil](https://ss64.com/mac/hdiutil.html) — macOS native tool documentation
- [WiX Toolset Product Element](https://docs.firegiant.com/wix/schema/wxs/product/) — MSI ProductVersion and ProductCode configuration
- [Flutter Desktop Version Management](https://docs.flutter.dev/platform-integration/desktop) — Community pattern for multi-platform versioning (verification)

### Tertiary (LOW confidence — for reference only)
- Various Gradle DEB/RPM plugins (gradle-fpm-plugin, gradle-ospackage-plugin) — None used in current project; Gradle Compose handles DEB/DMG/MSI natively

## Metadata

**Confidence breakdown:**
- **Standard Stack:** HIGH — Gradle Compose Multiplatform officially documents version configuration; current project already partially implements it
- **Desktop Version Bumping:** HIGH — Kotlin official docs provide exact format requirements and configuration examples
- **Package Metadata:** MEDIUM-HIGH — DEB and MSI formats are well-documented; DMG volume naming requires field testing but is standard macOS tooling
- **Build Number Strategy:** MEDIUM — Kotlin community pattern observed in Flutter; no explicit KMP documentation, but consistent with industry practice

**Research date:** 2026-04-10  
**Valid until:** 2026-05-10 (30 days; Gradle/Kotlin updates are infrequent for stable features)
