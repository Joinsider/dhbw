# Phase 13: Fix Deploy Pipeline - Executable Plan

**Created:** 2026-04-10  
**Status:** Ready for execution  
**Target Completion:** 2026-04-24

---

## Phase Boundary & Success Criteria

**Phase Goal:** Overhaul CI/CD deploy pipeline for version bumping consistency across ALL KMP targets (Android, iOS, macOS, Windows, Linux), consolidate changelog workflow, and support multilingual release notes.

**Success Definition:**
1. All KMP targets have same semantic version at release time
2. Release notes available in English + German in GitHub and fastlane
3. Package filenames include semver: `dhbw-student-app-v2.0.3.{deb,dmg,msi}`
4. Version metadata embedded in packages (DMG volume, MSI properties, DEB control)
5. Single consolidated workflow: bump → build → release → notes
6. Version validation fails fast if any platform missing or mismatched

---

## Implementation Plan (4 Waves)

### Wave 1: Workflow Consolidation & Inputs

**Files:** `.github/workflows/build-release.yml`

**Changes:**

1. Add workflow_dispatch inputs (to `on.workflow_dispatch.inputs`):
   ```yaml
   bump:
     description: 'Version bump type'
     required: true
     default: 'patch'
     type: choice
     options: [major, minor, patch, prepatch, preminor, premajor, none]
   changelog_en:
     description: 'Release notes (English)'
     required: true
     type: string
   changelog_de:
     description: 'Release notes (Deutsch)'
     required: true
     type: string
   ```

2. Create new job `bump-and-notes` (before existing `build-android-release` job):
   - Runs on: `ubuntu-latest`
   - Outputs: `new_version`, `new_code` (passed to downstream build jobs)
   
3. **Steps in bump-and-notes job:**
   - Checkout code
   - Read current version from build.gradle.kts (grep versionName)
   - Compute new semver (use existing bash arithmetic from build-release.yml lines 76-99)
   - Increment versionCode: `NEW_CODE=$((CURRENT_CODE + 1))`
   - **→ Wave 2: Write all versions (see Wave 2 below)**
   - **→ Wave 2: Validate versions (see Wave 2 below)**
   - Write fastlane changelogs (see below)
   - Commit changelog + version files
   - **Push to main**
   - **→ Create GitHub Release with release notes** (NEW for D-05):
     ```bash
     gh release create "v$NEW_VERSION" \
       --title "v$NEW_VERSION" \
       --notes "## English\n$CHANGELOG_EN\n\n## Deutsch\n$CHANGELOG_DE" \
       --target main \
       composeApp/build/outputs/apk/*release*.apk \
       composeApp/build/outputs/bundle/*release*.aab
     ```

4. **Write fastlane changelogs step** (after version validation):
   ```bash
   VERSION_CODE="${{ steps.versioncode.outputs.new_code }}"
   CHANGELOG_EN="${{ github.event.inputs.changelog_en }}"
   CHANGELOG_DE="${{ github.event.inputs.changelog_de }}"
   
   EN_FILE="fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt"
   mkdir -p "$(dirname "$EN_FILE")"
   printf '%s\n' "$CHANGELOG_EN" > "$EN_FILE"
   
   DE_FILE="fastlane/metadata/android/de-DE/changelogs/$VERSION_CODE.txt"
   mkdir -p "$(dirname "$DE_FILE")"
   printf '%s\n' "$CHANGELOG_DE" > "$DE_FILE"
   ```

5. **Commit and push step** (after changelogs):
   ```bash
   git config user.name "github-actions[bot]"
   git config user.email "github-actions[bot]@users.noreply.github.com"
   
   git add composeApp/build.gradle.kts iosApp/Configuration/Config.xcconfig \
     "fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt" \
     "fastlane/metadata/android/de-DE/changelogs/$VERSION_CODE.txt"
   
   git commit -m "chore: bump to v${{ steps.semver.outputs.new_version }} with release notes"
   git push origin HEAD:main
   ```

6. Make downstream jobs depend on bump-and-notes:
   - Add `needs: bump-and-notes` to build-android-release, build-ios-release, build-desktop-release

**Validation:** Trigger workflow with bump=none → verify workflow executes and outputs success message "✓ Workflow completed"

**Scope:** Low

---

### Wave 2: Version Bumping & Validation

**Files:**
- `.github/workflows/build-release.yml` (version write + validation steps)
- `composeApp/build.gradle.kts` (verify/add platform-specific properties)
- `iosApp/Configuration/Config.xcconfig` (verify version fields exist)

**Changes:**

1. **Audit current version definitions** in build.gradle.kts:
   - Android: `versionCode = 23` (line 126) ✓
   - Android: `versionName = "v2.0.3"` (line 127) ✓
   - Desktop: `packageVersion = "2.0.3"` (line 199) ✓
   - Desktop: `archiveVersion.set("2.0.3")` (line 302) ✓

2. **Audit iOS version definitions** in iosApp/Configuration/Config.xcconfig:
   - `MARKETING_VERSION = 2.0.3` (exists) ✓
   - `CURRENT_PROJECT_VERSION = 23` (exists) ✓

3. **Decision: Desktop Version Properties (Discretion Area 1 - RESOLVED)**
   
   **Chosen: Option C (Gradle native properties per RESEARCH.md recommendation)**
   
   Add platform-specific version properties to build.gradle.kts (in compose.desktop.application.nativeDistributions block, ~line 200):
   ```kotlin
   dmgPackageVersion = "2.0.3"    // DMG format requirement
   msiPackageVersion = "2.0.3"    // MSI format requirement
   debPackageVersion = "2.0.3"    // DEB format requirement
   ```
   
   These properties will be used in the sed update patterns below.

4. **Extend version write step** in workflow (replace existing sed commands with expanded version):
   ```bash
   NEW="2.0.4"  # Computed in previous step
   NEW_CODE="24"  # Computed in previous step
   
   # Android
   sed -i -E "s/(versionName\s*=\s*)\"v?[^\"]+\"/\1\"v$NEW\"/" composeApp/build.gradle.kts
   sed -i -E "s/(versionCode\s*=\s*)[0-9]+/\1$NEW_CODE/" composeApp/build.gradle.kts
   
   # Desktop (global + format-specific)
   sed -i -E "s/(packageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
   sed -i -E "s/(archiveVersion\.set\()\"[^\"]+\"\)/\1\"$NEW\")/" composeApp/build.gradle.kts
   sed -i -E "s/(dmgPackageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
   sed -i -E "s/(msiPackageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
   sed -i -E "s/(debPackageVersion\s*=\s*)\"[^\"]+\"/\1\"$NEW\"/" composeApp/build.gradle.kts
   
   # iOS
   sed -i -E "s/(MARKETING_VERSION\s*=\s*).*/\1$NEW/" iosApp/Configuration/Config.xcconfig
   sed -i -E "s/(CURRENT_PROJECT_VERSION\s*=\s*).*/\1$NEW_CODE/" iosApp/Configuration/Config.xcconfig
   ```

5. **Add comprehensive version validation step** (D-11, D-12 - NEW):
   ```bash
   echo "Verifying all versions..."
   ERRORS=0
   
   # Android (2 fields)
   if ! grep -q "versionName.*\"v$NEW\"" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Android versionName not updated to v$NEW"
     ERRORS=$((ERRORS + 1))
   fi
   if ! grep -q "versionCode.*$NEW_CODE" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Android versionCode not updated to $NEW_CODE"
     ERRORS=$((ERRORS + 1))
   fi
   
   # Desktop (5 fields: packageVersion, archiveVersion, dmg, msi, deb)
   if ! grep -q "packageVersion.*\"$NEW\"" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Desktop packageVersion not updated to $NEW"
     ERRORS=$((ERRORS + 1))
   fi
   if ! grep -q "archiveVersion\.set(\"$NEW\")" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Desktop archiveVersion not updated to $NEW"
     ERRORS=$((ERRORS + 1))
   fi
   if ! grep -q "dmgPackageVersion.*\"$NEW\"" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Desktop dmgPackageVersion not updated to $NEW"
     ERRORS=$((ERRORS + 1))
   fi
   if ! grep -q "msiPackageVersion.*\"$NEW\"" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Desktop msiPackageVersion not updated to $NEW"
     ERRORS=$((ERRORS + 1))
   fi
   if ! grep -q "debPackageVersion.*\"$NEW\"" composeApp/build.gradle.kts; then
     echo "❌ ERROR: Desktop debPackageVersion not updated to $NEW"
     ERRORS=$((ERRORS + 1))
   fi
   
   # iOS (2 fields)
   if ! grep -q "MARKETING_VERSION.*$NEW" iosApp/Configuration/Config.xcconfig; then
     echo "❌ ERROR: iOS MARKETING_VERSION not updated to $NEW"
     ERRORS=$((ERRORS + 1))
   fi
   if ! grep -q "CURRENT_PROJECT_VERSION.*$NEW_CODE" iosApp/Configuration/Config.xcconfig; then
     echo "❌ ERROR: iOS CURRENT_PROJECT_VERSION not updated to $NEW_CODE"
     ERRORS=$((ERRORS + 1))
   fi
   
   if [ $ERRORS -gt 0 ]; then
     echo "❌ Version validation FAILED with $ERRORS errors"
     exit 1
   fi
   
   echo "✅ All 9 version fields verified successfully"
   ```

**Validation:**
- Run workflow with bump=patch
- Check workflow logs for: `✅ All 9 version fields verified successfully`
- Verify git log: `git log --oneline -1` should show version bump commit
- Run: `git diff HEAD~1 HEAD | grep -E "versionName|versionCode|packageVersion|dmgPackageVersion|msiPackageVersion|debPackageVersion|MARKETING_VERSION|CURRENT_PROJECT_VERSION"` → should show all 9 updates

**Scope:** Medium

---

### Wave 3: Desktop Package Naming & Metadata

**Files:** `.github/workflows/build-release.yml`

**Changes:**

1. **Rename desktop artifacts** in artifact upload step (after builds complete):
   ```bash
   # DEB (Linux, from build-desktop-release on linux runner)
   if [ -f composeApp/build/compose/binaries/main/deb/dhbw-*.deb ]; then
     cd composeApp/build/compose/binaries/main/deb/
     mv dhbw-*.deb "dhbw-student-app-v${{ env.NEW_VERSION }}.deb" 2>/dev/null || true
   fi
   
   # DMG (macOS, from build-desktop-release on macos-latest runner)
   if [ -f "composeApp/build/compose/binaries/main/dmg/DHBW-Horb-*.dmg" ]; then
     cd composeApp/build/compose/binaries/main/dmg/
     mv "DHBW-Horb-*.dmg" "dhbw-student-app-v${{ env.NEW_VERSION }}.dmg" 2>/dev/null || true
   fi
   
   # MSI (Windows, from build-desktop-release on windows-latest runner)
   if [ -f composeApp/build/compose/binaries/main/msi/*.msi ]; then
     cd composeApp/build/compose/binaries/main/msi/
     mv *.msi "dhbw-student-app-v${{ env.NEW_VERSION }}.msi" 2>/dev/null || true
   fi
   ```

2. **Post-build verification step** (after artifact uploads, to verify metadata):
   
   **DMG volume name verification:**
   ```bash
   # Check if DMG was created with correct volume name (Gradle Compose may auto-set)
   # If volume name is wrong, post-build rename required (see Blockers section)
   DMG_FILE="composeApp/build/compose/binaries/main/dmg/dhbw-student-app-v${{ env.NEW_VERSION }}.dmg"
   if [ -f "$DMG_FILE" ]; then
     hdiutil imageinfo "$DMG_FILE" | grep -i "Format:" || echo "Warning: Could not inspect DMG volume name"
   fi
   ```
   
   **MSI ProductVersion verification:**
   ```bash
   # MSI ProductVersion should be set by Gradle Compose automatically
   # Post-build verification: Inspect MSI metadata (Windows runner only)
   # Command: wmic datafile where name="..." get Version
   # Deferred to UAT (manual test on Windows)
   ```
   
   **DEB control file verification:**
   ```bash
   DEB_FILE="composeApp/build/compose/binaries/main/deb/dhbw-student-app-v${{ env.NEW_VERSION }}.deb"
   if [ -f "$DEB_FILE" ]; then
     echo "DEB Control Info:"
     dpkg-deb -I "$DEB_FILE" | grep -E "Package:|Version:|Architecture:"
   fi
   ```

**Validation:**
- Build locally: `./gradlew packageDeb packageDmg packageMsi`
- Check filenames: `ls -la composeApp/build/compose/binaries/main/{deb,dmg,msi}/`
- Should show: `dhbw-student-app-v2.0.4.deb`, `dhbw-student-app-v2.0.4.dmg`, `dhbw-student-app-v2.0.4.msi`
- Run DEB verification: `dpkg-deb -I composeApp/build/compose/binaries/main/deb/dhbw-student-app-v*.deb`
- Mount DMG locally: `hdiutil attach composeApp/build/compose/binaries/main/dmg/dhbw-student-app-v*.dmg` → check mounted volume name

**Scope:** Medium

---

### Wave 4: Integration Testing

**Manual Test Scenario:**

1. **Setup:**
   - Ensure project on main branch
   - Current version: versionCode=23, versionName=v2.0.3
   - All version files exist (build.gradle.kts, Config.xcconfig, fastlane paths)

2. **Trigger Workflow:**
   - GitHub Actions → build-release.yml → "Run workflow"
   - bump: `patch`
   - changelog_en: `"Bug fixes and performance improvements"`
   - changelog_de: `"Fehlerbehebungen und Leistungsverbesserungen"`
   - Click "Run workflow"

3. **Verify Results:**

   **UAT Checklist:**
   - [ ] **Git Commit:** `git log --oneline -1` shows `chore: bump to v2.0.4 with release notes`
   - [ ] **All Versions Updated:** `git diff HEAD~1 HEAD | grep -E "versionName|versionCode|packageVersion|MARKETING_VERSION|CURRENT_PROJECT_VERSION"` → confirms all 9 fields updated
   - [ ] **Workflow Logs - Validation Pass:** Grep logs for `✅ All 9 version fields verified successfully` → must exist
   - [ ] **Fastlane Changelogs - English:** `cat fastlane/metadata/android/en-US/changelogs/24.txt` → shows "Bug fixes and performance improvements"
   - [ ] **Fastlane Changelogs - German:** `cat fastlane/metadata/android/de-DE/changelogs/24.txt` → shows "Fehlerbehebungen und Leistungsverbesserungen"
   - [ ] **Desktop Package Names:** 
     - DEB: `ls composeApp/build/compose/binaries/main/deb/ | grep dhbw-student-app-v2.0.4.deb`
     - DMG: `ls composeApp/build/compose/binaries/main/dmg/ | grep dhbw-student-app-v2.0.4.dmg`
     - MSI: `ls composeApp/build/compose/binaries/main/msi/ | grep dhbw-student-app-v2.0.4.msi`
   - [ ] **GitHub Release Created:** `gh release view v2.0.4` → must return release info (or check GitHub UI)
   - [ ] **GitHub Release Notes:** `gh release view v2.0.4 --json body` → must contain "Bug fixes and performance improvements" AND "Fehlerbehebungen und Leistungsverbesserungen"

   **Edge Case - Skip Bump Test:**
   - Trigger workflow: bump=none
   - [ ] No version bump occurs (git log unchanged)
   - [ ] No changelog files created
   - [ ] No GitHub Release created
   - [ ] Workflow completes successfully

**Scope:** Manual verification (~30-45 min per test scenario)

---

## Execution Sequence

```
Wave 1 Workflow Consolidation
  ├─ Add workflow inputs
  ├─ Create bump-and-notes job
  ├─ Add GitHub Release step
  └─ Test: Trigger with bump=none → Verify workflow structure

Wave 2 Version Bumping
  ├─ Audit all version config files (build.gradle.kts, Config.xcconfig)
  ├─ Add Gradle platform-specific properties (dmg, msi, deb)
  ├─ Extend version write steps (9 fields total)
  ├─ Add comprehensive validation logic
  └─ Test: Trigger with bump=patch → Verify all 9 fields + validation logs

Wave 3 Package Naming
  ├─ Add artifact rename steps (deb, dmg, msi)
  ├─ Add post-build verification steps
  └─ Test: Local build → Verify filenames + metadata

Wave 4 Integration Testing
  ├─ Full workflow trigger (patch bump + English + German notes)
  ├─ Run UAT checklist
  ├─ Edge case: Test skip bump (bump=none)
  └─ Documentation: Record results
```

**Critical Path:** Wave 1 → Wave 2 → Wave 3 → Wave 4 (sequential)  
**No parallelization:** Each wave validates previous; gate before proceeding

---

## Risk Mitigation

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Version drift (missing platform) | **HIGH** | Comprehensive grep validation (9 fields); fail workflow if any missing |
| GitHub Release not created | **MEDIUM** | Use `gh release create` command in bump-and-notes job; test in Wave 1 |
| DMG volume name wrong | **MEDIUM** | Post-build hdiutil verification (Wave 3); document if additional tooling needed |
| iOS config file missed | **MEDIUM** | Explicitly listed in Wave 2 with sed patterns; audit before and after |
| Validation commands not runnable | **MEDIUM** | All bash patterns provided inline; test locally before workflow deployment |

---

## Metadata

| Field | Value |
|-------|-------|
| Phase | 13 |
| Milestone | v3.0 |
| Type | CI/CD Configuration |
| Effort | 8-12 hours |
| Start | 2026-04-10 |
| Target End | 2026-04-24 |
| Depends On | Phase 12 complete |
| Blocks | v3.0 release |

*Plan created: 2026-04-10*
*Plan verified: 2026-04-10*
