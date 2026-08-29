# Phase 13: Deploy Pipeline - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-10  
**Phase:** 13-fix-deploy-pipeline-version-bumping-across-all-kmp-targets-and-add-manual-workflow-trigger-with-multilingual-release-notes  
**Areas discussed:** Multilingual Release Notes, Version Bumping, Workflow Consolidation, Build Output Versioning, Release Notes Timing, Template Design, Desktop Metadata

---

## Deploy Pipeline Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Multilingual release notes | Auto-generate or integrate release notes in multiple languages | ✓ |
| Version bumping across KMP targets | Ensure all desktop builds have consistent version tracking | ✓ |
| Workflow consolidation | Merge changelog and release workflows into one cohesive pipeline | ✓ |
| Build output versioning | Embed version numbers into generated packages (DEB, DMG, MSI) | ✓ |

**User's choice:** All four aspects (complete pipeline overhaul)

---

## Multilingual Release Notes

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-translate from English | Generate English automatically, translate to German/French via API. Minimal manual work. | |
| Manual per-language notes during release | Provide notes in each language during workflow. More control, slower workflow. | ✓ |
| English auto-generated, German manual via separate workflow | Keep current approach but integrate German into main build-release workflow. | |

**User's choice:** Manual per-language notes during release

**Notes:** User prioritizes control and quality over automation. Release notes are user-facing and should be carefully crafted per language.

---

## Language Support Template

| Option | Description | Selected |
|--------|-------------|----------|
| English + German only | Keep current DHBW focus. Matches existing fastlane and user base. | |
| English + German + French | Expand to French in addition to English and German. | |
| Make it template-based | Design workflow to support any language. Users can add more as needed. Extensible for future. | ✓ |

**User's choice:** Make it template-based

**Notes:** User wants flexibility to add languages in future without code changes. Fixed English + German initially, but architecture allows easy expansion.

---

## Version Bumping Targets

| Option | Description | Selected |
|--------|-------------|----------|
| iOS (currently via Config.xcconfig) | Keep iOS version bumping as-is in the pipeline | ✓ |
| macOS (currently embedded in Gradle) | Add explicit macOS version bumping for clarity | ✓ |
| Windows/MSI (currently embedded in Gradle) | Add explicit Windows version bumping for clarity | ✓ |
| Linux/DEB (currently embedded in Gradle) | Add explicit Linux version bumping for clarity | ✓ |
| Android (existing) | Android already has explicit bumping | ✓ (noted by user) |

**User's choice:** All KMP targets (iOS, Android, macOS, Windows, Linux)

**Notes:** User wants consistent version handling across all platforms. Desktop targets currently use compiled values from Gradle; Phase 13 should make this explicit.

---

## Workflow Organization

| Option | Description | Selected |
|--------|-------------|----------|
| Single consolidated workflow | Merge changelog.yml into build-release.yml. One workflow to manage. | ✓ |
| Keep separate, but coordinate | Two workflows that are coordinated. build-release triggers changelog.yml. | |
| Three-step pipeline | Separate workflows: bump → release → changelog. More granular. | |

**User's choice:** Single consolidated workflow (Recommended)

**Notes:** User prefers simplicity. Single workflow means one trigger point, predictable sequence, fewer things to debug.

---

## Build Output Naming

| Option | Description | Selected |
|--------|-------------|----------|
| Include semver in filename | dhbw-student-app-v2.0.3.deb, .dmg, .msi. Match APK/AAB pattern. | ✓ |
| Include versionCode for desktop too | Add both semver and build number: v2.0.3-build.23 | |
| Keep current auto-naming | Don't rename outputs; use tool-generated names | |
| Add metadata to packages | Embed version in DEB control, DMG metadata, MSI properties | |

**User's choice:** Include semver in filename (Recommended)

**Notes:** User selected consistent naming approach across platforms.

---

## Release Notes Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Before bump (ask for notes first, then bump) | User provides notes as inputs at trigger time. Workflow: inputs → bump → build → publish with notes | ✓ |
| After bump (release, then ask for notes) | Workflow bumps and builds first. Then ask user for notes to add to published release. | |

**User's choice:** Before bump (ask for notes first, then bump)

**Notes:** User prefers single-trigger experience. Notes are provided upfront, everything completes in one workflow run.

---

## Template-Based Language Design

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed English + German inputs, extend later | Workflow has changelog_en, changelog_de. Documentation explains how to add more. | ✓ |
| JSON/structured format for all languages | User provides JSON object with language keys. Workflow parses and distributes. | |
| Comment-based approach | User posts comments with language codes after release. Workflow reads and updates. | |

**User's choice:** Fixed English + German inputs, user can add more later

**Notes:** User wants simple interface for now (two inputs) with documented extensibility for future languages.

---

## Desktop Metadata Versioning

| Option | Description | Selected |
|--------|-------------|----------|
| Filename versioning only | Include version in DEB/DMG/MSI filenames. Internal metadata stays as-is. | |
| Metadata + filename | Update both filenames AND internal package metadata (DMG volume name, MSI properties, DEB control file). | ✓ |
| Auto-detect from build config | Workflow reads version from gradle at build time; all outputs have it automatically. | |

**User's choice:** Metadata + filename (with note: "The number must match anywhere so embed into metadata as well")

**Notes:** User is explicit: version consistency is critical. Both filenames and internal metadata must match. No exceptions.

---

## Claude's Discretion

No areas deferred to Claude. All decisions locked by user input.

---

## Deferred Ideas

### Additional Languages Beyond English + German
- Idea: Support French, Italian, Spanish, etc.
- Noted: User's preference is template-based design; languages can be added as needed in future phases

### Automatic Changelog Generation
- Idea: Parse commit messages (feat:, fix:) to auto-generate changelog
- Noted: Manual control preferred for quality assurance

---

*Discussion completed: 2026-04-10*
*Ready for: /gsd:plan-phase 13 to create detailed implementation plan*
