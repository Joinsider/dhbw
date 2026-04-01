# Testing Patterns

**Analysis Date:** 2025-05-22

## Test Framework

**Runner:**
- JUnit (v4.13.2)
- Gradle: `./gradlew test` (or `./gradlew :composeApp:testDebugUnitTest`)

**Assertion Library:**
- `kotlin.test` (bundled with Kotlin Multiplatform)

**Run Commands:**
```bash
./gradlew test         # Run all tests
./gradlew koverHtml    # Generate coverage report
./gradlew koverXml     # Generate XML coverage report (for SonarCloud)
```

## Test File Organization

**Location:**
- Co-located in the respective source sets:
  - `composeApp/src/commonTest/kotlin/`: Platform-agnostic tests.
  - `composeApp/src/androidUnitTest/kotlin/`: Android-specific tests.
  - `composeApp/src/desktopTest/kotlin/`: JVM-specific tests.

**Naming:**
- Files: `*Test.kt` (e.g., `LoginFormViewModelTest.kt`).
- Methods: `camelCase_with_scenario` (e.g., `initialState_isEmpty()`).

**Structure:**
```
composeApp/src/commonTest/kotlin/
└── de/fampopprol/dhbwhorb/
    ├── data/
    │   ├── database/
    │   │   ├── dao/
    │   │   └── entities/
    │   └── storage/
    └── ui/
        ├── auth/
        └── pages/
```

## Test Structure

**Suite Organization:**
```kotlin
class SomeViewModelTest {
    @Test
    fun someAction_expectedResult() {
        // Arrange
        val viewModel = SomeViewModel()
        
        // Act
        viewModel.performAction()
        
        // Assert
        assertEquals(expected, viewModel.uiState.result)
    }
}
```

**Patterns:**
- **Arrange-Act-Assert (AAA):** Commonly used with comments.
- **Given-When-Then:** Used in DAO and more complex tests.
- **Abstract Base Classes:** Used for platform-specific tests (like Room DAOs) to ensure consistent test logic across platforms.

## Mocking

**Framework:**
- `ktor-client-mock`: Used for mocking HTTP network requests (e.g., in `AuthenticationService` tests).
- Manual Mocks/Stubs: Creating mock implementations of interfaces.

**Patterns:**
```kotlin
// Example from Ktor mock pattern (conceptual based on dependencies)
val mockEngine = MockEngine { request ->
    respond(
        content = ByteReadChannel("""{"status":"success"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
val client = HttpClient(mockEngine)
```

**What to Mock:**
- Network APIs
- External services (e.g., `AuthenticationService`)
- File system access

**What NOT to Mock:**
- ViewModels (tested as unit tests)
- Data entities/models
- UI state objects

## Fixtures and Factories

**Test Data:**
```kotlin
val grade = GradesEntity(
    name = "Mathematics", gradeValue = 1.7, semesterName = "WS2023"
)
```

**Location:**
- Typically created inline within the test functions or as private helper methods in the test class.

## Coverage

**Requirements:**
- Not strictly enforced, but tracked via `kover`.
- Exclusions defined for generated code, Room implementations, and resources in `composeApp/build.gradle.kts`.

**View Coverage:**
```bash
./gradlew koverHtml
```

## Test Types

**Unit Tests:**
- ViewModels (`LoginFormViewModelTest.kt`)
- Parsers (`HtmlParserTest.kt`, `AuthParserTest.kt`)
- Converters (`DateTimeConvertersTest.kt`)

**Integration Tests:**
- Room DAOs (`GradesDaoTest.kt`, `SemesterDaoTest.kt`)
- Database setup (`DatabaseFactoryTest.kt`)

**E2E Tests / UI Tests:**
- `compose.uiTest` is used for testing Composables (`LoginFormTest.kt`, `BottomNavigationBarTest.kt`).
- `testTag` modifiers are used to identify elements in the UI.

## Common Patterns

**Async Testing:**
- Using `runTest` from `kotlinx-coroutines-test` for suspend functions.
```kotlin
@Test
fun someAsyncTest() = runTest {
    // call suspend function
}
```

**Error Testing:**
- Verifying error states in ViewModels after invalid input.
- Verifying exception handling or failure result types.

---

*Testing analysis: 2025-05-22*
