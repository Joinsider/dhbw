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
1. Add workflow_dispatch inputs: `bump`, `changelog_en`, `changelog_de`
2. Create new job `bump-and-notes` (before build jobs):
   - Read current version from build.gradle.kts
   - Compute new semver + increment versionCode
   - Write all versions (see Wave 2)
   - Validate versions (see Wave 2)
   - Write fastlane changelogs to fastlane/metadata/android/{en-US,de-DE}/changelogs/
   - Commit version bump + changelogs
   - Push to main
3. Make build jobs depend on bump-and-notes: `needs: bump-and-notes`

**Validation:** Test trigger with bump=none → verify job executes

**Scope:** Low

---

### Wave 2: Version Bumping & Validation

**Files:** `.github/workflows/build-release.yml`, `composeApp/build.gradle.kts`

**Changes:**
1. Audit build.gradle.kts for existing version definitions:
   - Android: versionCode (126), versionName (127) ✓
   - Desktop: packageVersion (199) ✓
   - Desktop: archiveVersion.set() (302) ✓

2. Add platform-specific properties to build.gradle.kts (if missing):
   ```kotlin
   dmgPackageVersion = "2.0.3"
   msiPackageVersion = "2.0.3"
   debPackageVersion = "2.0.3"
   ```

3. Extend version write step in workflow (add to existing sed updates):
   - Android: versionName, versionCode (existing)
   - Desktop: packageVersion, archiveVersion, dmgPackageVersion, msiPackageVersion, debPackageVersion (extend)
   - iOS: MARKETING_VERSION, CURRENT_PROJECT_VERSION (existing)

4. Add comprehensive version validation step (NEW):
   ```bash
   # After all writes, grep each file for expected versions
   # Fail with clear error if any field missing or wrong
   # Covers: 2x Android, 5x Desktop, 2x iOS = 8 fields
   ```

**Validation:** Test trigger with bump=patch → verify all 8 version fields updated + validated

**Scope:** Medium

---

### Wave 3: Desktop Package Naming & Metadata

**Files:** `.github/workflows/build-release.yml`

**Changes:**
1. Rename desktop artifacts after build:
   - DEB: dhbw-*.deb → dhbw-student-app-v{NEW_VERSION}.deb
   - DMG: *.dmg → dhbw-student-app-v{NEW_VERSION}.dmg
   - MSI: *.msi → dhbw-student-app-v{NEW_VERSION}.msi

2. Post-build verification steps:
   - DMG: Verify volume name matches version (may need hdiutil post-build step)
   - MSI: Verify ProductVersion in metadata
   - DEB: Verify control file has correct version

**Validation:** Build locally → check filenames + inspect package metadata

**Scope:** Medium

---

### Wave 4: Integration Testing

**Manual Test:**
1. Trigger workflow: bump=patch, changelog_en="Bug fixes", changelog_de="Fehlerbehebungen"
2. Verify results:
   - [ ] Git commit: version bump + changelogs
   - [ ] Fastlane changelogs: both en-US and de-DE exist
   - [ ] Desktop packages: DEB/DMG/MSI named with semver
   - [ ] GitHub Release: created with English + German notes
   - [ ] Workflow logs: version validation passed (all 8 fields)

**Scope:** Manual (~30 min)

---

## Execution Sequence

```
Wave 1 → Wave 2 → Wave 3 → Wave 4 (sequential)

Each wave dependent on previous:
- Wave 1: Create workflow structure
- Wave 2: Extend version logic
- Wave 3: Package naming
- Wave 4: Verify end-to-end
```

**Critical Path:** All 4 waves sequential (each gate depends on previous)

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Version drift (missing platform) | Comprehensive grep validation; fail fast |
| DMG volume name outdated | Post-build hdiutil verification |
| MSI upgrade broken | Gradle auto-generates ProductCode; manual test |

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

*Plan created: 2026-04-10*
