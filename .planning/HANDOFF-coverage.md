# Handoff: Test coverage improvement (SonarQube-driven)

## Context

`Joinsider_dhbw` (branch `main`) fails its SonarQube quality gate: `new_coverage` is 70% against an 80% required threshold. Overall project coverage is 48.5% (7,406 lines to cover, 3,760 uncovered). Full diagnostic ran via the `sonarqube` MCP server (`mcp__sonarqube__*` tools — see "SonarQube access" below).

A 5-phase plan was defined with the user and phase 1 is **in progress, uncommitted, and NOT YET BUILT/TESTED**. Verify it compiles and passes before doing anything else.

## The 5-phase plan

1. **Close gaps in commonMain data-layer files that already have tests** — `HtmlParser.kt`, `AuthenticationService.kt`, `TimetableParser.kt`. Pure logic, existing test files, no new tooling. *(in progress — this handoff)*
2. **Near-miss Compose screens with existing test scaffolding** — `LoginForm.kt`, `HelpSelectionCard.kt`, `DocumentsPage.kt`, `DesignSelectionCard.kt`, `TimetablePage.kt`, `GradesPage.kt`. Compose UI testing is already set up and used (`androidx.compose.ui.test.runComposeUiTest` — see e.g. `composeApp/src/commonTest/kotlin/de/fampopprol/dhbwhorb/ui/auth/LoginFormTest.kt`), so this is just extending existing `*Test.kt` files to cover more states/branches. *(not started)*
3. **Zero-coverage commonMain logic** — DI modules (`data/src/commonMain/.../data/di/DataModule.kt`), storage/cache, `NotificationPreferencesInteractor`, `LectureChange`, remaining Dualis service gaps (`DualisPageGateway`, `DualisDocumentService`). New test files following the existing pattern. *(not started)*
4. **Platform-specific code** — secure storage on Android/iOS/macOS/desktop, notification dispatchers, `CustomDnsResolver`. Lower ROI, thin platform wrappers. Decide per-platform whether unit tests or manual/integration testing makes more sense. *(not started — no iOS/macOS test source sets currently have any tests; `androidUnitTest` has 3 files, `desktopTest` has 4)*
5. **Glance widgets** (`composeApp/src/androidMain/.../widget/layouts/*.kt`) — hardest, lowest ROI. Revisit last. *(not started)*

## Phase 1 status

### Done (uncommitted)

- **`data/src/commonMain/.../parser/HtmlParser.kt`** — tests added to `HtmlParserTest.kt`. `isValidModuleDetailsPage()` had zero direct tests (now covered: pop-up markers, missing table, non-pop-up, redirect). Also added: `extractUserFullName` alt-pattern fallbacks (unclosed `<h1>`, no `<h1>` at all), `isErrorPage` single-quoted `access_denied` class, `isErrorPage` hitting the error-pattern-list loop (`abgelaufen`), `isValidGradePage` happy path with only the semester dropdown present.
- **`data/src/commonMain/.../parser/TimetableParser.kt`** — tests added to `TimetableParserTest.kt`. Added: invalid month in header (`Month(13)` throws → must be caught, not crash), Saturday/Sunday header resolution (only Mo/Mi/Fr were exercised before), the lenient fallback header pattern (triggers when the strict pattern doesn't match due to extra markup), and the two untested branches of the link-building `if/else if/else` in `parseLectureCell` (absolute `http` link kept as-is; relative link *without* a leading slash gets `"$BASE_URL/$linkPath"`).
- Two lines were judged **likely unreachable defensive code** and deliberately left uncovered — flag this to the user rather than chasing them blindly:
  - `TimetableParser.kt:90` — the outer catch in `parseWeeklyView`'s per-cell loop; `parseLectureCell` already swallows all its own exceptions internally and returns `null`, so nothing should ever reach this catch.
  - `TimetableParser.kt:287-288` — the catch in `parseIndividualPage`; no obvious input causes the regex/string operations in that function to throw.
  - `TimetableParser.kt:96` (the *other* uncovered line, the header exception path) **is** reachable and **is** covered by the new invalid-month test — don't confuse it with line 90.

### Not done — pick up here

**`data/src/commonMain/.../services/AuthenticationService.kt`** (81.1% line coverage, 47.8% branch coverage) has **no test at all for a fully successful login**. Every existing test in `AuthenticationServiceTest.kt` fails before reaching the redirect-following / session-storage code (invalid creds, server error, offline, missing redirect header, or the demo-user short-circuit). This is the single most valuable gap in the file — a happy-path test exercising `followRedirects` → `isMainPage` → session storage would cover most of the remaining uncovered lines in one shot. Specifically:

| Lines | What's uncovered | How to trigger it |
|---|---|---|
| 96 | `bodyAsText()` throwing inside the response-body try/catch | Mock engine that returns a response whose body read throws (or returns a broken content stream) |
| 121 | `authParser.extractRedirectUrlFromHeader` returning null → `parseFailure(...)` | Redirect header present but in a format `extractRedirectUrlFromHeader` can't parse |
| 128, 133, 140 | Happy path: `followRedirects` succeeding, `userFullName` null-vs-non-null, `sessionId` empty-vs-not | **Full successful login test** — mock engine returning a redirect header, then a page satisfying `htmlParser.isMainPage`. Write it once with a name found, once without (covers 133 both ways) |
| 159 | `depth >= MAX_REDIRECT_DEPTH` in `followRedirects` | Mock engine that always returns another redirect page, forcing 10+ recursions |
| 165-166 | `client.get(startUrl)` throwing inside `followRedirects` | Mock engine throws after the initial login POST succeeds (two-stage mock) |
| 172-175 | The `isRedirectPage` branch of `followRedirects`'s `when` — following one interstitial redirect page before reaching the main page | Mock engine: login POST → redirect page (not yet main) → main page, and a variant where the redirect page has no follow-up URL (`extractRedirectUrlFromHtml` returns null) |
| 182-184 | The `else` branch — a 200 response that's neither the main page nor a redirect page | Mock engine returns some unrelated HTML after the redirect chain |
| 191-192 | `extractSessionId()` finding a `JSESSIONID`/`cnsc` cookie | Happy-path login test where the auth token is null but a session cookie is set (forces the `authToken ?: extractSessionId()` fallback) |

The existing test file already has the pattern to copy: `createAuthenticationServiceWithMockEngine(mockEngine)` plus `MockEngine { respond(...) }`. For a multi-request flow (login POST + one or more `client.get` redirect follows), `MockEngine` needs a handler that inspects the request URL/count and responds differently per call — look at how `MockEngine { }`'s lambda receives `request:` if a multi-step mock isn't already established elsewhere in the test suite; check `DualisDocumentServiceTest.kt` or `DualisPageGateway`-related tests for an existing multi-call mock pattern before inventing one.

### After AuthenticationServiceTest.kt

1. **Run the tests.** Nothing in this session has been compiled or executed yet. Use the module's test task, e.g.:
   ```bash
   ./gradlew :data:testDebugUnitTest :data:jvmTest
   ```
   (check `data/build.gradle.kts` for the exact KMP test task names — this is a Kotlin Multiplatform module, so `commonTest` runs under each configured target's test task, not a single `test` task).
2. Fix any compilation/assertion failures — the new tests were written by reading the source and reasoning about regex/branch behavior, not by running them.
3. Re-check coverage via SonarQube (requires a fresh analysis run — MCP coverage data reflects the last analyzed commit, not uncommitted local changes) or just trust the local line-by-line reasoning above.
4. Move to phase 2.

## SonarQube access

- MCP server: `sonarqube` (tools appear as `mcp__sonarqube__*`; some are deferred — use `ToolSearch` with `select:<name>` if a call fails with "not loaded").
- Project key: `Joinsider_dhbw` (note the capital J — there's also a lowercase `joinsider_dhbw` and a `Joinsider_dhbw-next`; don't mix them up). Branch: `main` (only long-lived branch analyzed).
- Useful calls:
  - `mcp__sonarqube__get_project_quality_gate_status` (projectKey, branch) — pass/fail per condition.
  - `mcp__sonarqube__get_component_measures` (metricKeys: `coverage`, `line_coverage`, `branch_coverage`, `uncovered_lines`, `lines_to_cover`) — project-wide numbers.
  - `mcp__sonarqube__search_files_by_coverage` (branch, pageSize up to 500) — worst-covered files first. **Result is large (299 files) — paginate with `pageIndex`, and if a single call exceeds the tool's token cap it gets written to a file on disk with a path in the error message; use `jq` via Bash on that file rather than re-reading it with the Read tool.**
  - `mcp__sonarqube__get_file_coverage_details` (key: `Joinsider_dhbw:<path>`, branch) — exact uncovered line numbers and partially-covered branch conditions for one file. This is what drove all the test-writing above.
- There's also a `sonarqube:sonar-coverage` **skill** that wraps the same flow if the MCP tools aren't available for some reason — it has its own CLI fallback (`sonar api get ...`).

## Codebase notes worth knowing before writing more tests

- **No mocking framework.** Only `kotlin-test`, JUnit, Robolectric (Android only), and `kotlinx-coroutines-test` are in `libs.versions.toml`. Network mocking uses Ktor's `MockEngine` (see `AuthenticationServiceTest.kt`), not MockK. Things wired through Koin DI need hand-written fakes (e.g. `FakeSecureStorage` in `data/src/commonTest/.../storage/credentials/`) rather than mocks.
- **Compose UI testing works fine** via `androidx.compose.ui.test.runComposeUiTest` (`ExperimentalTestApi`) — contrary to an earlier assumption in this session, no new dependency is needed for phase 2.
- **Test file layout mirrors main source 1:1** — e.g. `data/src/commonMain/kotlin/.../parser/TimetableParser.kt` → `data/src/commonTest/kotlin/.../parser/TimetableParserTest.kt`. Follow this when creating new test files in phases 2-5.
- Fixtures for Dualis HTML parsing live in `data/src/commonTest/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/parser/fixtures/DualisFixtures.kt` — prefer deriving variants of these with `.replace(...)` over hand-rolling unrelated HTML, to stay consistent with the "derived from documented real captures" provenance note at the top of that file.
