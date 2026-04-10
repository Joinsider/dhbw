---
phase: 13-fix-deploy-pipeline-version-bumping-across-all-kmp-targets-and-add-manual-workflow-trigger-with-multilingual-release-notes
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - .github/workflows/build-release.yml
  - composeApp/build.gradle.kts
autonomous: true
requirements: []
must_haves:
  truths:
    - "User can trigger build-release workflow with changelog_en and changelog_de inputs"
    - "Version bump updates versionCode/versionName (Android), MARKETING_VERSION/CURRENT_PROJECT_VERSION (iOS), and packageVersion (Desktop) atomically"
    - "All platforms report the same semantic version after bump completes"
    - "Fastlane changelogs are written to fastlane/metadata/android/{en-US,de-DE}/changelogs/{versionCode}.txt"
    - "GitHub Release notes include both English and German changelog text"
    - "Desktop packages (DEB, DMG, MSI) are named with semver: dhbw-student-app-v{semver}.{ext}"
    - "Version validation step fails workflow if any platform config is missing or versions don't match"
  artifacts:
    - path: ".github/workflows/build-release.yml"
      provides: "Consolidated build, release, and changelog workflow with user inputs"
      must_contain: ["changelog_en", "changelog_de", "bump-and-notes"]
    - path: "composeApp/build.gradle.kts"
      provides: "Version definitions for all platforms (versionCode, versionName, packageVersion, archiveVersion)"
      version_fields: ["versionCode = 23", "versionName = \"v2.0.3\"", "packageVersion = \"2.0.3\"", "archiveVersion.set(\"2.0.3\")"]
    - path: "iosApp/Configuration/Config.xcconfig"
      provides: "iOS version definitions (MARKETING_VERSION, CURRENT_PROJECT_VERSION)"
      version_fields: ["CURRENT_PROJECT_VERSION=23", "MARKETING_VERSION=2.0.3"]
  key_links:
    - from: "build-release.yml workflow_dispatch inputs"
      to: "bump-and-notes job"
      via: "github.event.inputs.bump, changelog_en, changelog_de"
      pattern: "workflow_dispatch.*inputs"
    - from: "bump-and-notes job"
      to: "composeApp/build.gradle.kts + iosApp/Configuration/Config.xcconfig"
      via: "sed version writes, git commit"
      pattern: "sed.*versionName|sed.*versionCode|sed.*MARKETING_VERSION"
    - from: "bump-and-notes job"
      to: "fastlane/metadata/android/{en-US,de-DE}/changelogs"
      via: "mkdir + printf changelog files"
      pattern: "mkdir.*changelogs|printf.*changelog"
    - from: "create-release job"
      to: "GitHub Release"
      via: "gh release create with fastlane changelog content"
      pattern: "softprops/action-gh-release|body.*changelog"
    - from: "build-*-* jobs"
      to: "artifact naming"
      via: "semver-based rename patterns"
      pattern: "dhbw-student-app-v|rename|artifact"
---

<objective>
Consolidate the deployment pipeline to ensure consistent version bumping across all KMP targets (Android, iOS, macOS, Windows, Linux) and integrate multilingual release notes into a single workflow trigger.

Purpose:
- Eliminate manual, separate changelog workflow steps (currently changelog.yml runs after build-release.yml)
- Ensure all platforms maintain the same semantic version at release time
- Provide users with a single workflow trigger that accepts release notes in multiple languages
- Enforce version validation to catch mismatches before release

Output:
- Updated build-release.yml with consolidated workflow, new inputs, and validation steps
- Updated composeApp/build.gradle.kts with platform-specific version properties (if needed)
- Verified version write/read patterns for Android, iOS, and Desktop targets
- Named desktop packages with semantic versioning (v{semver})
- Fastlane changelogs written atomically with version bump
- GitHub Release notes populated with user-provided multilingual content
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/STATE.md
@13-CONTEXT.md
@13-RESEARCH.md

### Current Implementation Details

**Android versionCode/versionName (build.gradle.kts lines 126-127):**
```kotlin
versionCode = 23
versionName = "v2.0.3"
```

**Desktop packageVersion (build.gradle.kts lines 199, 302):**
```kotlin
packageVersion = "2.0.3"
archiveVersion.set("2.0.3")
```

**iOS versions (iosApp/Configuration/Config.xcconfig lines 6-7):**
```
CURRENT_PROJECT_VERSION=23
MARKETING_VERSION=2.0.3
```

**Current version bump pattern (build-release.yml lines 76-147):**
- Reads versionName from gradle
- Computes new semver in pure bash
- Uses sed to update build.gradle.kts and Config.xcconfig
- Commits version changes
- Triggers build jobs

**Current changelog workflow (changelog.yml lines 1-73):**
- Separate workflow_dispatch trigger
- Reads versionCode from gradle
- Writes to fastlane/metadata/android/{en-US,de-DE}/changelogs/{versionCode}.txt
- Commits changelog files
- Runs AFTER release is published (manual step)

**Fastlane structure (verified):**
- fastlane/metadata/android/en-US/changelogs/ exists with files for versionCodes 6-22
- fastlane/metadata/android/de-DE/changelogs/ exists with corresponding German files
- Both directories ready to accept new {versionCode}.txt files

**Artifact naming (build-release.yml lines 399-415, 472-487, 523-543):**
- APK: dhbw-student-app-release-v{version}.apk ✓
- AAB: dhbw-student-app-release-v{version}.aab ✓
- DEB: Currently tool-generated, needs renaming to dhbw-student-app-v{version}.deb
- DMG: Currently tool-generated, needs renaming to dhbw-student-app-v{version}.dmg
- MSI: dhbw-student-app-release-v{version}.msi ✓ (already semver in name)

### Key Workflow Sections to Modify

1. **workflow_dispatch inputs** (lines 12-26): Add changelog_en and changelog_de inputs
2. **bump-and-notes job** (new): Insert after bump-version, before create-release
   - Outputs new_version and new_code for downstream jobs
   - Writes fastlane changelog files
   - Validates all versions match
3. **Version write and validate** (lines 119-162): Extend to include validation step per D-11/D-12
4. **Artifact upload steps** (lines 296-352 for DEB, lines 336-352 for DMG, lines 523-543 for MSI): Rename with semver pattern
5. **Release notes** (lines 571-593): Populate GitHub Release body with changelog_en + changelog_de
</context>

<tasks>

<task type="auto">
  <name>Task 1: Extend build-release.yml workflow_dispatch inputs with changelog fields</name>
  <files>.github/workflows/build-release.yml</files>
  <action>
Update the workflow_dispatch.inputs section (lines 12-26) to add two new required input fields:

**Add after line 26 (after "none" option):**

```yaml
      changelog_en:
        description: 'Release notes (English)'
        required: true
        type: string
      changelog_de:
        description: 'Release notes (Deutsch)'
        required: true
        type: string
```

This allows users to provide English and German release notes when manually triggering the workflow via "Run workflow" dialog in GitHub Actions UI.

**Verify:** The workflow_dispatch.inputs section should have 3 inputs: bump, changelog_en, changelog_de. These are required=true to ensure users provide notes before triggering.

**Rationale:** Per D-01 (manual per-language notes), D-02 (fixed English + German), D-03 (notes provided before bump). Users see input fields in UI, reducing friction vs. needing to update separate workflows.
  </action>
  <verify>
    <automated>grep -A 30 "workflow_dispatch:" .github/workflows/build-release.yml | grep -E "changelog_en|changelog_de|required: true"</automated>
  </verify>
  <done>workflow_dispatch inputs include changelog_en and changelog_de fields with required: true, description text, and type: string. Both appear in GitHub Actions UI when triggering workflow manually.</done>
</task>

<task type="auto">
  <name>Task 2: Create bump-and-notes job that consolidates version bump + changelog write + validation</name>
  <files>.github/workflows/build-release.yml</files>
  <action>
Insert new job after the current bump-version job (after line 162) and before the create-release job (line 164).

The new bump-and-notes job should:

1. **Depend on:** bump-version job (needs its outputs: new_version, new_code)
2. **Outputs:** Pass new_version and new_code to downstream jobs
3. **Steps:**
   a. **Checkout** with SSH key for commit/push
   b. **Write fastlane changelogs** using the versionCode from bump-version outputs:
      - Read github.event.inputs.changelog_en and changelog_de
      - Create fastlane/metadata/android/en-US/changelogs/{versionCode}.txt with English content
      - Create fastlane/metadata/android/de-DE/changelogs/{versionCode}.txt with German content
      - Use mkdir -p to ensure directories exist
      - Use printf (not echo) to avoid trailing newlines
   c. **Validate all versions match** (D-11, D-12):
      - Grep each version file (build.gradle.kts, Config.xcconfig) to extract current values
      - Verify versionName, versionCode, packageVersion all contain expected values
      - Fail with detailed error if any missing or mismatched
      - Log grep output for debugging
   d. **Commit changelogs** alongside version bump:
      - git add composeApp/build.gradle.kts iosApp/Configuration/Config.xcconfig fastlane/metadata/android/{en-US,de-DE}/changelogs/{versionCode}.txt
      - git commit -m "chore: bump to v{NEW_VERSION} with release notes"
      - git push origin HEAD:main

**Code template:**

```yaml
  bump-and-notes:
    name: Bump version and write release notes
    runs-on: ubuntu-latest
    needs: [bump-version]
    if: needs.bump-version.outputs.skipped != 'true'
    permissions:
      contents: write
    outputs:
      new_version: ${{ needs.bump-version.outputs.new_version }}
      new_code: ${{ steps.version-code.outputs.new_code }}
    steps:
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0
          ssh-key: ${{ secrets.DEPLOY_KEY }}

      - name: Read versionCode
        id: version-code
        run: |
          CURRENT_CODE=$(grep -m1 'versionCode' composeApp/build.gradle.kts \
            | sed -E 's/.*versionCode\s*=\s*([0-9]+).*/\1/')
          echo "new_code=$CURRENT_CODE" >> "$GITHUB_OUTPUT"

      - name: Write fastlane changelogs
        env:
          CHANGELOG_EN: ${{ github.event.inputs.changelog_en }}
          CHANGELOG_DE: ${{ github.event.inputs.changelog_de }}
        run: |
          VERSION_CODE="${{ steps.version-code.outputs.new_code }}"
          
          # English
          EN_FILE="fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt"
          mkdir -p "$(dirname "$EN_FILE")"
          printf '%s\n' "$CHANGELOG_EN" > "$EN_FILE"
          echo "Written: $EN_FILE"
          cat "$EN_FILE"
          
          # German
          DE_FILE="fastlane/metadata/android/de-DE/changelogs/$VERSION_CODE.txt"
          mkdir -p "$(dirname "$DE_FILE")"
          printf '%s\n' "$CHANGELOG_DE" > "$DE_FILE"
          echo "Written: $DE_FILE"
          cat "$DE_FILE"

      - name: Validate all versions (D-11, D-12)
        run: |
          NEW_VERSION="${{ needs.bump-version.outputs.new_version }}"
          NEW_CODE="${{ steps.version-code.outputs.new_code }}"
          ERRORS=0

          echo "=== Version Validation ==="
          echo "Expected: v$NEW_VERSION (code: $NEW_CODE)"
          echo ""

          # Check Android versionName
          if grep -q "versionName.*\"v$NEW_VERSION\"" composeApp/build.gradle.kts; then
            echo "✓ Android versionName: v$NEW_VERSION"
          else
            echo "✗ Android versionName NOT found or mismatched"
            ERRORS=$((ERRORS + 1))
          fi

          # Check Android versionCode
          if grep -q "versionCode.*$NEW_CODE" composeApp/build.gradle.kts; then
            echo "✓ Android versionCode: $NEW_CODE"
          else
            echo "✗ Android versionCode NOT found or mismatched"
            ERRORS=$((ERRORS + 1))
          fi

          # Check Desktop packageVersion
          if grep -q "packageVersion.*\"$NEW_VERSION\"" composeApp/build.gradle.kts; then
            echo "✓ Desktop packageVersion: $NEW_VERSION"
          else
            echo "✗ Desktop packageVersion NOT found or mismatched"
            ERRORS=$((ERRORS + 1))
          fi

          # Check iOS MARKETING_VERSION
          if [ -f "iosApp/Configuration/Config.xcconfig" ]; then
            if grep -q "MARKETING_VERSION.*$NEW_VERSION" iosApp/Configuration/Config.xcconfig; then
              echo "✓ iOS MARKETING_VERSION: $NEW_VERSION"
            else
              echo "✗ iOS MARKETING_VERSION NOT found or mismatched"
              ERRORS=$((ERRORS + 1))
            fi

            # Check iOS CURRENT_PROJECT_VERSION
            if grep -q "CURRENT_PROJECT_VERSION.*$NEW_CODE" iosApp/Configuration/Config.xcconfig; then
              echo "✓ iOS CURRENT_PROJECT_VERSION: $NEW_CODE"
            else
              echo "✗ iOS CURRENT_PROJECT_VERSION NOT found or mismatched"
              ERRORS=$((ERRORS + 1))
            fi
          else
            echo "✗ iOS Config.xcconfig NOT found"
            ERRORS=$((ERRORS + 1))
          fi

          if [ $ERRORS -gt 0 ]; then
            echo ""
            echo "Version validation failed with $ERRORS error(s)"
            exit 1
          fi

          echo ""
          echo "All versions validated successfully"

      - name: Commit changelogs with version bump
        run: |
          VERSION_CODE="${{ steps.version-code.outputs.new_code }}"
          NEW_VERSION="${{ needs.bump-version.outputs.new_version }}"
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"

          git add composeApp/build.gradle.kts iosApp/Configuration/Config.xcconfig \
            "fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt" \
            "fastlane/metadata/android/de-DE/changelogs/$VERSION_CODE.txt"

          if git diff --cached --quiet; then
            echo "Changelogs already up to date"
          else
            git commit -m "chore: bump to v$NEW_VERSION with release notes (en + de)"
            git push origin HEAD:main
          fi
```

**Rationale:** 
- Per D-04: Consolidates changelog.yml logic into build-release.yml, eliminating separate manual workflow
- Per D-05: Writes to both fastlane paths and will be used in GitHub Release body
- Per D-07: Single transaction write (version files + changelog files in one git commit)
- Per D-11/D-12: Validates all versions before continuing; fails workflow if any missing
  </action>
  <verify>
    <automated>grep -A 80 "bump-and-notes:" .github/workflows/build-release.yml | grep -E "changelog_en|changelog_de|Validate all versions|versionCode.*new_code"</automated>
  </verify>
  <done>bump-and-notes job exists between bump-version and create-release, writes fastlane changelogs from workflow inputs, validates all versions match, and commits both version files and changelog files in a single transaction.</done>
</task>

<task type="auto">
  <name>Task 3: Update create-release job to use bump-and-notes outputs and add release notes to GitHub Release</name>
  <files>.github/workflows/build-release.yml</files>
  <action>
Modify the create-release job (starting line 164) to:

1. **Update job dependencies:** Change `needs: [ bump-version ]` to `needs: [ bump-and-notes ]` (line 167)

2. **Add body parameter to GitHub Release** in the "Create Release" step (lines 256-266):

After the existing `generate_release_notes: true`, add:

```yaml
          body: |
            ## Release Notes
            
            ### English
            ${{ github.event.inputs.changelog_en }}
            
            ### Deutsch
            ${{ github.event.inputs.changelog_de }}
```

This populates the GitHub Release body with both English and German notes provided by the user (per D-05).

3. **Verify:** The release created should have:
   - Name: v{version}
   - Body: English + German release notes in separate sections
   - Tag: v{version}
   - Draft: false (after clean-release publishes it)

**Rationale:**
- Per D-05: Release notes written to GitHub Release from workflow inputs
- Ensures GitHub Release has user-provided content, not auto-generated summaries
- Users see multilingual notes when viewing release on GitHub
  </action>
  <verify>
    <automated>grep -A 20 "Create Release" .github/workflows/build-release.yml | grep -E "body:|changelog_en|changelog_de"</automated>
  </verify>
  <done>create-release job depends on bump-and-notes, and GitHub Release body includes English and German release notes from workflow inputs.</done>
</task>

<task type="auto">
  <name>Task 4: Rename DEB and DMG artifacts with semantic versioning in filenames</name>
  <files>.github/workflows/build-release.yml</files>
  <action>
Update the artifact naming steps to match the naming convention already used for APK/AAB/MSI. Per D-08, desktop packages must be named as dhbw-student-app-v{semver}.{ext}.

**For DEB (build-deb-amd64 job, lines 296-308):**

Replace lines 297-302 with:

```bash
      - name: Find and rename DEB file
        id: find-deb
        run: |
          DEB_FILE=$(find composeApp/build/compose/binaries/main/deb -name "*.deb" -type f | head -n 1)
          if [ -z "$DEB_FILE" ]; then
            echo "Error: No DEB file found"
            exit 1
          fi
          VERSION="${{ needs.create-release.outputs.version }}"
          DEB_DIR=$(dirname "$DEB_FILE")
          RENAMED_DEB="$DEB_DIR/dhbw-student-app-v$VERSION.deb"
          cp "$DEB_FILE" "$RENAMED_DEB"
          echo "deb_file=$RENAMED_DEB" >> $GITHUB_OUTPUT
          echo "deb_name=dhbw-student-app-v$VERSION.deb" >> $GITHUB_OUTPUT
```

**For DMG (build-dmg-macos job, lines 336-346):**

Replace lines 337-346 with:

```bash
      - name: Find and rename DMG file
        id: find-dmg
        run: |
          DMG_FILE=$(find composeApp/build/compose/binaries/main/dmg -name "*.dmg" -type f | head -n 1)
          if [ -z "$DMG_FILE" ]; then
            echo "Error: No DMG file found"
            exit 1
          fi
          VERSION="${{ needs.create-release.outputs.version }}"
          DMG_DIR=$(dirname "$DMG_FILE")
          RENAMED_DMG="$DMG_DIR/dhbw-student-app-v$VERSION.dmg"
          cp "$DMG_FILE" "$RENAMED_DMG"
          echo "dmg_file=$RENAMED_DMG" >> $GITHUB_OUTPUT
          echo "dmg_name=dhbw-student-app-v$VERSION.dmg" >> $GITHUB_OUTPUT
```

**Verify:** After build completes:
- DEB artifact should be named `dhbw-student-app-v2.0.4.deb` (example with v2.0.4)
- DMG artifact should be named `dhbw-student-app-v2.0.4.dmg`
- Both artifacts uploaded to release with correct names

**Rationale:** Per D-08, consistent naming across all desktop packages. Users downloading releases can identify versions from filenames alone.
  </action>
  <verify>
    <automated>grep -A 15 "Find and rename DEB" .github/workflows/build-release.yml | grep "dhbw-student-app-v" && grep -A 15 "Find and rename DMG" .github/workflows/build-release.yml | grep "dhbw-student-app-v"</automated>
  </verify>
  <done>DEB and DMG artifacts renamed with pattern dhbw-student-app-v{version}.{ext}. Both updated in build-release.yml steps.</done>
</task>

<task type="auto">
  <name>Task 5: Remove changelog.yml workflow (replaced by consolidated build-release.yml)</name>
  <files>.github/workflows/changelog.yml</files>
  <action>
Delete the separate changelog.yml workflow file. This workflow is now superseded by the consolidated build-release.yml workflow.

Per D-04, the changelog functionality has been moved into build-release.yml as:
- Workflow inputs: changelog_en, changelog_de
- Job: bump-and-notes (writes fastlane changelogs)
- Release body: Populated with user-provided notes

Action:
- Delete .github/workflows/changelog.yml
- This prevents users from triggering the old separate workflow
- Users now provide release notes in the build-release.yml manual trigger instead

Rationale: Single source of truth. Consolidated workflow eliminates confusion about when/how to update changelogs. Version bump and changelog write happen atomically.
  </action>
  <verify>
    <automated>test ! -f .github/workflows/changelog.yml && echo "changelog.yml removed" || exit 1</automated>
  </verify>
  <done>changelog.yml workflow file deleted. Users can no longer trigger separate changelog workflow.</done>
</task>

<task type="auto">
  <name>Task 6: Verify version property coverage for all KMP targets in build.gradle.kts</name>
  <files>composeApp/build.gradle.kts</files>
  <action>
Audit composeApp/build.gradle.kts to ensure version properties are correctly defined for all targets. Per D-06 (all KMP targets have same semver at release) and D-10 (desktop versionCode synced with Android).

Current implementation (verified at plan time):
- Android: versionCode = 23, versionName = "v2.0.3" (lines 126-127) ✓
- Desktop: packageVersion = "2.0.3" (line 199) ✓
- Desktop: archiveVersion.set("2.0.3") (line 302) ✓

Action: Verify no additional platform-specific properties are needed. The research recommends using `nativeDistributions.<os>.<format>PackageVersion` only if Gradle doesn't automatically propagate packageVersion to all formats.

Steps:
1. Grep for all version-related lines in composeApp/build.gradle.kts
2. Verify Android versionCode and versionName are present and will be updated by workflow sed patterns
3. Verify Desktop packageVersion and archiveVersion are present
4. Confirm iOS versions are in separate file (Config.xcconfig) which workflow also updates
5. If any platform-specific nativeDistributions properties are needed (e.g., dmgPackageVersion), add them now

Current grep results (from lines 126-127, 199, 302):
```
versionCode = 23
versionName = "v2.0.3"
packageVersion = "2.0.3"
archiveVersion.set("2.0.3")
```

If Gradle Compose natively propagates packageVersion to all desktop formats (DEB, DMG, MSI), no additional properties are needed. The workflow's sed patterns for packageVersion will update all formats.

Recommendation: No changes needed to build.gradle.kts at this time. The existing properties are sufficient, and Gradle Compose handles platform-specific format requirements automatically.

Rationale: Per D-07 (single source of truth), existing approach works. If desktop format-specific properties become necessary in future, they can be added in Phase 14 or beyond.
  </action>
  <verify>
    <automated>grep -E "versionCode|versionName|packageVersion|archiveVersion" composeApp/build.gradle.kts | head -10</automated>
  </verify>
  <done>Version properties verified for all targets (Android, Desktop, iOS). No additional Gradle properties needed. Workflow sed patterns will update all version locations correctly.</done>
</task>

<task type="auto">
  <name>Task 7: Validate workflow structure and test a manual workflow trigger (dry-run)</name>
  <files>.github/workflows/build-release.yml</files>
  <action>
Perform final validation of the updated build-release.yml workflow to ensure:

1. **Syntax validation:** Check YAML syntax is valid
2. **Job dependencies:** Verify job chain: bump-version → bump-and-notes → create-release → build-* jobs → clean-release
3. **Output propagation:** Confirm outputs flow correctly between jobs:
   - bump-version.outputs.new_version → bump-and-notes (used in commit message)
   - bump-and-notes.outputs.new_version → create-release (used for tag, version)
   - create-release.outputs.version, tag → build-* jobs (used for artifact naming, checkout)
4. **Input validation:** Confirm workflow_dispatch inputs are required and have descriptions
5. **Conditional logic:** Verify `if` conditions guard jobs correctly (e.g., don't build if skipped=true)

Action (manual verification steps, executors can document results):
1. Run: `yamllint .github/workflows/build-release.yml` (if yamllint available)
2. Or manually inspect YAML structure
3. Trace through job dependencies with eyes: bump-version → bump-and-notes → create-release
4. Verify github.event.inputs.changelog_en and changelog_de are referenced in bump-and-notes
5. Verify GitHub Release creation includes both changelog inputs in body

Optional test (if CI system allows manual dispatch testing):
- Trigger build-release.yml with manual inputs (bump=patch, changelog_en=test, changelog_de=test)
- Verify workflow completes without errors
- Check workflow run logs for "All versions validated successfully"
- Verify GitHub Release was created with test changelog text in body

Rationale: Catch workflow syntax or logic errors before actual release. Phase 13 is final phase of v3.0 milestone; workflow must be reliable.
  </action>
  <verify>
    <automated>grep -c "jobs:" .github/workflows/build-release.yml && grep -c "needs:" .github/workflows/build-release.yml && echo "Workflow structure validated"</automated>
  </verify>
  <done>build-release.yml workflow structure is valid. Job dependencies, outputs, and inputs verified. Ready for production use.</done>
</task>

</tasks>

<verification>
After all tasks complete:

1. **Workflow Consolidation (D-04):**
   - ✓ build-release.yml includes changelog_en and changelog_de inputs (Task 1)
   - ✓ bump-and-notes job writes fastlane changelogs (Task 2)
   - ✓ changelog.yml workflow file deleted (Task 5)
   - ✗ Users can no longer trigger separate changelog workflow

2. **Version Bumping Across All Targets (D-06, D-07):**
   - ✓ Workflow updates versionCode, versionName (Android)
   - ✓ Workflow updates MARKETING_VERSION, CURRENT_PROJECT_VERSION (iOS)
   - ✓ Workflow updates packageVersion, archiveVersion (Desktop)
   - ✓ All updates happen in single transaction (Task 2, step 4: single git commit)

3. **Release Notes Integration (D-01, D-02, D-03, D-05):**
   - ✓ Users provide changelog_en and changelog_de as workflow inputs
   - ✓ Notes written to fastlane/metadata/android/{en-US,de-DE}/changelogs/{versionCode}.txt (Task 2)
   - ✓ GitHub Release body populated with English + German notes (Task 3)

4. **Artifact Naming with Semver (D-08):**
   - ✓ DEB renamed to dhbw-student-app-v{version}.deb (Task 4)
   - ✓ DMG renamed to dhbw-student-app-v{version}.dmg (Task 4)
   - ✓ MSI already named dhbw-student-app-release-v{version}.msi (verified in workflow)
   - ✓ APK, AAB already named with version (verified in workflow)

5. **Version Validation (D-11, D-12):**
   - ✓ bump-and-notes job validates all version locations (Task 2, step 3)
   - ✓ Workflow fails if any platform config missing or versions mismatch
   - ✓ Detailed error output for debugging (Task 2)

6. **No Breaking Changes:**
   - ✓ Existing version write patterns preserved (sed-based updates)
   - ✓ Existing artifact build jobs unchanged (only renaming added)
   - ✓ Existing git commit pattern extended (version + changelog in one commit)
</verification>

<success_criteria>
Phase 13 is complete when:

1. **Workflow Manual Trigger Works:**
   - User can visit GitHub Actions → build-release.yml → "Run workflow"
   - Two new input fields visible: changelog_en (required), changelog_de (required)
   - Workflow trigger shows these inputs in UI

2. **Version Bumping Verified (end-to-end):**
   - Manual workflow trigger with bump=patch, changelog_en="Test en", changelog_de="Test de"
   - Workflow completes successfully
   - All version locations updated: versionCode incremented, versionName bumped, MARKETING_VERSION bumped, packageVersion bumped
   - Grep verification passes: "All versions validated successfully" in logs
   - Single git commit created with message "chore: bump to v{new_version} with release notes"

3. **Fastlane Changelogs Created:**
   - Files exist: fastlane/metadata/android/en-US/changelogs/{newVersionCode}.txt
   - Files exist: fastlane/metadata/android/de-DE/changelogs/{newVersionCode}.txt
   - Both files contain user-provided changelog text (from inputs)

4. **GitHub Release Populated:**
   - GitHub Release created with tag v{version}
   - Release body includes "### English" section with changelog_en content
   - Release body includes "### Deutsch" section with changelog_de content

5. **Desktop Packages Named Correctly:**
   - DEB artifact uploaded to release as dhbw-student-app-v{version}.deb
   - DMG artifact uploaded to release as dhbw-student-app-v{version}.dmg
   - MSI artifact uploaded to release as dhbw-student-app-release-v{version}.msi
   - APK artifact uploaded to release as dhbw-student-app-release-v{version}.apk
   - AAB artifact uploaded to release as dhbw-student-app-release-v{version}.aab

6. **No Separate Changelog Workflow:**
   - changelog.yml is deleted and no longer appears in GitHub Actions workflows
   - Users receive clear error if they try to trigger it manually
   - All changelog functionality integrated into build-release.yml

7. **Backward Compatibility:**
   - PR merge into main (without manual trigger) still works, bumps version with default patch
   - Version validation passes for all targets
   - No breaking changes to existing release process for automated merges
</success_criteria>

<output>
After execution, create `.planning/phases/13-fix-deploy-pipeline-version-bumping-across-all-kmp-targets-and-add-manual-workflow-trigger-with-multilingual-release-notes/{phase}-{plan}-SUMMARY.md` with:

1. **Execution Summary**
   - All 7 tasks completed
   - Workflow file updated with changelog inputs and bump-and-notes job
   - changelog.yml deleted
   - Version properties verified in build.gradle.kts and Config.xcconfig
   - Artifact naming patterns updated for DEB/DMG

2. **Validation Results**
   - Version write/validate patterns tested (output: "All versions validated successfully")
   - Workflow syntax validated
   - Job dependency chain verified: bump-version → bump-and-notes → create-release → build-* → clean-release
   - Fastlane changelog paths confirmed ready

3. **Changes Summary**
   - Files modified: .github/workflows/build-release.yml
   - Files deleted: .github/workflows/changelog.yml
   - Files verified: composeApp/build.gradle.kts, iosApp/Configuration/Config.xcconfig

4. **Decisions Made**
   - No additional Gradle properties needed for desktop versions (packageVersion auto-propagates)
   - Artifact naming uses pattern dhbw-student-app-v{version}.{ext} for consistency
   - GitHub Release body includes both EN + DE notes in "### English" and "### Deutsch" sections

5. **Next Phase**
   - Phase 13 is final phase of v3.0 milestone
   - No Phase 14 planned
   - Ready for v3.0 release: execute build-release.yml workflow with desired version bump and release notes
</output>
