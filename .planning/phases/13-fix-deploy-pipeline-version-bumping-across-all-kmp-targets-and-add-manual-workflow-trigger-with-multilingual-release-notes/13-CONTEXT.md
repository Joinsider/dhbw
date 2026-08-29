# Phase 13: Fix Deploy Pipeline Version Bumping Across All KMP Targets & Add Manual Workflow Trigger with Multilingual Release Notes - Context

**Gathered:** 2026-04-10  
**Status:** Ready for planning

---

## Phase Boundary

Phase 13 overhauls the deploy pipeline to ensure:
- Version bumping is consistent across ALL KMP targets (Android, iOS, macOS, Windows, Linux)
- Release notes are provided in multiple languages during the release workflow
- Build outputs (DEB, DMG, MSI) are named and versioned consistently
- Changelog and release workflows are consolidated into a single workflow
- Users can control release notes content during the build-release workflow trigger

Success means:
- All KMP targets have the same semantic version at release time
- Release notes in GitHub and fastlane are in English + German (extensible)
- Package filenames include semver (e.g., dhbw-student-app-v2.0.3.dmg)
- Internal package metadata also includes versioning (DMG volume name, MSI properties, DEB control file)
- Single consolidated workflow manages bump → build → release → notes

---

## Implementation Decisions

### Release Notes Integration

**D-01: Manual Per-Language Release Notes During Workflow**
- Users provide release notes for each language when triggering build-release workflow
- NOT auto-translated; users write native notes for each language (full control)
- Rationale: Ensures high-quality, culturally-appropriate messaging for each language

**D-02: Fixed English + German, Template-Based for Future Languages**
- Workflow input fields: changelog_en, changelog_de (matching current fastlane support)
- Design is extensible: documentation explains how to add new languages without code changes
- Rationale: Matches current DHBW focus (German + English). Low complexity now, flexible for future.

**D-03: Release Notes Provided Before Version Bump**
- User supplies all release notes as workflow inputs BEFORE bump is triggered
- Workflow sequence: Collect inputs → Bump version → Build packages → Publish release with notes
- Rationale: Workflow completes in one pass; user gets predictable, single-trigger experience

### Consolidated Workflow Architecture

**D-04: Merge changelog.yml into build-release.yml**
- Single workflow manages: bump → build → release → notes
- Remove separate changelog.yml workflow (functionality moved into build-release.yml)
- Build-release.yml inputs now include: bump type, changelog_en, changelog_de
- Rationale: One workflow to manage, consistent trigger experience, notes automatically written with release

**D-05: Release Notes Written to Fastlane + GitHub Release**
- English changelog → fastlane/metadata/android/en-US/changelogs/{versionCode}.txt
- German changelog → fastlane/metadata/android/de-DE/changelogs/{versionCode}.txt
- Both also added to GitHub Release notes in the same workflow step
- Rationale: Fastlane notes used by Play Store and app stores; GitHub Release is canonical source

### Version Bumping Across All KMP Targets

**D-06: Explicit Version Bumping for All KMP Targets**
- Android: versionCode (incremented), versionName (semver) in build.gradle.kts ✓ (already exists)
- iOS: MARKETING_VERSION, CURRENT_PROJECT_VERSION in Config.xcconfig ✓ (already exists)
- macOS: Add explicit version config or update build.gradle.kts desktop section
- Windows: Add explicit version config or update build.gradle.kts desktop section
- Linux: Add explicit version config or update build.gradle.kts desktop section
- All targets must have the same semantic version at release time
- Rationale: Consistency across platforms; prevents version mismatches in production

**D-07: Single Source of Truth for Version**
- Read current version once from build.gradle.kts (existing pattern)
- Compute next semver in bash (existing pattern)
- Write to ALL version locations in a single "Write all versions" step
- Sequence: Read current → Compute new → Write Android & iOS & Desktop → Verify all updated → Commit
- Rationale: Prevents partial updates; easier to debug version drift

### Build Output Versioning

**D-08: Semver in Filename for All Desktop Outputs**
- DEB: dhbw-student-app-v{semver}.deb (currently: generic name from build)
- DMG: dhbw-student-app-v{semver}.dmg (currently: DHBW-Horb-{version}.dmg or similar)
- MSI: dhbw-student-app-v{semver}.msi (currently: generic name from build)
- APK: dhbw-student-app-release-v{semver}.apk ✓ (already exists)
- AAB: dhbw-student-app-release-v{semver}.aab ✓ (already exists)
- Rationale: Consistent naming across platforms; easier to identify versions in downloads

**D-09: Embed Version Metadata Inside Desktop Packages**
- DMG: Update volume name / metadata to include version
- MSI: Update MSI properties (ProductVersion, ProductCode if needed) to include version
- DEB: Version is already in control file via Gradle; ensure it's correctly propagated
- Rationale: Users inspecting package metadata see consistent version; package managers can verify versions

**D-10: Version Bumping for Desktop Happens in Same Step as Android/iOS**
- Desktop versionCode or build number should increment alongside Android versionCode
- Example: Android versionCode 24 → Desktop build number 24 (or similar scheme)
- Rationale: Build numbers stay synchronized; easier to track which build generated which output

### Error Handling & Validation

**D-11: Validate All Versions After Write**
- After writing versions to all files, grep/parse them to confirm all match
- If mismatch detected, fail workflow with detailed error message
- Rationale: Catches version drift early before build starts

**D-12: Fail Fast if Any Platform Missing Version Config**
- If iOS xcconfig, Android gradle, or Desktop gradle version fields not found, fail with error
- Error message suggests which file to check
- Rationale: Prevents silent incomplete version bumps

---

## Claude's Discretion

The following areas where user deferred to Claude:

1. **Desktop version bumping mechanism:** Whether to add separate config files for macOS/Windows or extend build.gradle.kts. Researcher will investigate and planner will decide.
2. **Exact DMG/MSI metadata format:** Which properties to update and how to rename volume/installer. Researcher will look at tooling capabilities.
3. **Build number sequence for desktop:** Whether to match Android versionCode exactly or use a separate sequence. Planner will decide based on build system constraints.

---

## Canonical References

Downstream agents MUST read these before planning or implementing:

### Phase Requirements & Goals
- `.planning/ROADMAP.md` §Phase 13 — Phase goal, dependencies on Phase 12
- `.planning/REQUIREMENTS.md` — No explicit requirements yet (TBD in Phase 13)

### Prior Decisions & Architecture
- `.planning/phases/08-critical-stability/08-CONTEXT.md` — Initialization patterns, ViewModel lifecycle (context for understanding service structure)
- `.planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md` — Manual DI, clear service ownership patterns

### Current CI/CD Implementation
- `.github/workflows/build-release.yml` — Current release workflow; needs consolidation with changelog
- `.github/workflows/changelog.yml` — Current fastlane changelog workflow; to be merged into build-release.yml
- `composeApp/build.gradle.kts` — Version definitions for Android and Desktop (lines 126-127, 199, 302)
- `iosApp/Configuration/Config.xcconfig` — iOS version definitions (MARKETING_VERSION, CURRENT_PROJECT_VERSION)

### Gradle Build System
- Gradle Compose Multiplatform documentation (for Desktop, macOS, Windows, Linux build targets)
- Gradle packaging tasks: `packageDeb`, `packageDmg`, `packageMsi` (to understand how versions are embedded)

### Build Tool Documentation
- DEB packaging specification (for embedding version in control file)
- DMG creation tools (for volume naming, metadata)
- MSI/WiX toolset documentation (for version properties in MSI packages)

---

## Existing Code Insights

### Reusable Assets
- **Semver bash arithmetic** (build-release.yml lines 76-104): Already implemented; reuse for computing new version
- **Multi-file sed updates** (build-release.yml lines 119-147): Pattern for updating versions in multiple files; extend to cover all targets
- **Git commit & push pattern** (build-release.yml lines 149-162): Already handles version commit; enhance to include changelog files
- **Artifact upload & naming** (build-release.yml lines 296-415): Already renames APK/AAB with version; extend pattern to DEB/DMG/MSI

### Established Patterns
- **Workflow conditional outputs** (build-release.yml needs blocks): Use to signal workflow completion stages
- **GitHub API script actions** (build-release.yml lines 571-592): Pattern for programmatic release operations; reuse for adding notes to releases
- **Fastlane changelog structure** (changelog.yml lines 40-58): Pattern for writing language-specific changelogs; integrate into main workflow

### Integration Points
- **build-release.yml workflow**: Primary target for consolidation; add release note inputs and fastlane write steps
- **Gradle build process**: Desktop targets (DEB, DMG, MSI) in build.gradle.kts; version propagation needs verification
- **iOS xcconfig**: Already handles MARKETING_VERSION and CURRENT_PROJECT_VERSION; ensure workflow updates both
- **Git commit step**: Already commits version bumps; extend to commit changelog files in same step

---

## Specific Ideas

### Workflow Input Design
When consolidating changelog.yml into build-release.yml, structure inputs like:
```yaml
workflow_dispatch:
  inputs:
    bump:
      description: 'Version bump type'
      options: [major, minor, patch, prepatch, preminor, premajor, none]
    changelog_en:
      description: 'Release notes (English)'
      required: true
    changelog_de:
      description: 'Release notes (Deutsch)'
      required: true
```

### Multi-File Version Update Pattern
Consolidate version writes into a single bash script that:
1. Reads current version once from build.gradle.kts
2. Computes new semver
3. Updates build.gradle.kts (versionName, versionCode, packageVersion, archiveVersion)
4. Updates Config.xcconfig (MARKETING_VERSION, CURRENT_PROJECT_VERSION)
5. Updates any new Desktop config files (if created)
6. Verifies all files contain the new version
7. Returns final version for use in subsequent steps

### Changelog File Naming
Ensure changelog files use versionCode (matching Android):
```
fastlane/metadata/android/en-US/changelogs/{versionCode}.txt
fastlane/metadata/android/de-DE/changelogs/{versionCode}.txt
```

### Desktop Package Naming Convention
Standardize to: `dhbw-student-app-v{semver}.{ext}`
- `dhbw-student-app-v2.0.4.deb`
- `dhbw-student-app-v2.0.4.dmg`
- `dhbw-student-app-v2.0.4.msi`

---

## Deferred Ideas

### Additional Languages Beyond English + German
- **Idea:** Support French, Italian, Spanish, or other languages
- **Decision:** Deferred to v3.1 or beyond
- **Reason:** Phase 13 targets English + German (matching DHBW scope). Template-based approach makes future additions easy.

### Automatic Changelog Generation from Commit Messages
- **Idea:** Parse commit messages (e.g., "feat:", "fix:") and auto-generate changelog summaries
- **Decision:** Deferred to Phase 14 or v3.1
- **Reason:** Phase 13 focuses on delivery mechanism, not generation. Manual notes ensure quality control.

### CI/CD for Release Notes Translation
- **Idea:** Use translation API (DeepL, Google Translate) to generate one language from another
- **Decision:** Deferred
- **Reason:** Manual control preferred for user-facing messaging. Translations are inexpensive to maintain manually.

### Signed/Notarized Desktop Packages
- **Idea:** Add code signing and notarization for DMG (macOS) and MSI (Windows)
- **Decision:** Out of scope for Phase 13
- **Reason:** Focus is on versioning and release notes. Signing is a separate infrastructure concern (certificates, signing keys, etc.).

---

*Phase: 13-fix-deploy-pipeline-version-bumping-across-all-kmp-targets-and-add-manual-workflow-trigger-with-multilingual-release-notes*  
*Context gathered: 2026-04-10*
