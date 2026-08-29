# Coding Conventions

**Analysis Date:** 2025-05-22

## Naming Patterns

**Files:**
- `PascalCase`: For classes, interfaces, and `@Composable` files (e.g., `LoginForm.kt`, `AppDatabase.kt`).
- `camelCase`: For other Kotlin files (e.g., `Main.kt`).

**Functions:**
- `camelCase`: For standard functions and methods (e.g., `onUsernameChange()`, `validateFields()`).
- `PascalCase`: For `@Composable` functions (e.g., `LoginForm()`, `App()`).

**Variables:**
- `camelCase`: For properties, parameters, and local variables (e.g., `uiState`, `isLoading`).
- `UPPER_SNAKE_CASE`: For constants (not explicitly seen but standard Kotlin).

**Types:**
- `PascalCase`: For classes, interfaces, objects, and sealed classes/interfaces (e.g., `LoginFormViewModel`, `LoginResult`).

## Code Style

**Formatting:**
- Standard Kotlin style as enforced by IntelliJ IDEA / Android Studio.
- Indentation: 4 spaces.
- Trailing commas: Used in multi-line parameter lists.

**Linting:**
- No explicit linting configuration (like Detekt or Lint) was found in the build scripts, but standard Kotlin compiler checks and IDE inspections are used.

## Import Organization

**Order:**
1. Android/Compose imports
2. Third-party library imports (Ktor, Napier, etc.)
3. Local project imports

**Path Aliases:**
- None detected.

## Error Handling

**Patterns:**
- **Result Wrappers:** Using sealed classes/interfaces to represent success and failure states (e.g., `LoginResult` in `src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/AuthenticationService.kt`).
- **Validation:** ViewModels perform field validation and update UI state with error messages (e.g., `LoginFormViewModel.kt`).

## Logging

**Framework:** `napier` (v2.7.1)

**Patterns:**
- Napier is used for logging across the KMP modules.

## Comments

**When to Comment:**
- Class-level documentation for major components (e.g., `AppDatabase.kt`).
- Function-level documentation for complex logic.
- Inline comments for explaining specific blocks (e.g., "Given-When-Then" in tests).

**JSDoc/TSDoc:**
- KDoc is used for documentation where applicable.

## Function Design

**Size:**
- Generally small and focused on a single responsibility.

**Parameters:**
- Uses default values where appropriate (e.g., in Composables).
- Complex parameter sets in Composables are sometimes passed via a `ViewModel`.

**Return Values:**
- Standard Kotlin types.
- UI state updates are typically handled via `mutableStateOf` instead of return values in UI-related functions.

## Module Design

**Exports:**
- Explicit visibility modifiers (`private`, `internal`, `protected`) are used to control exposure.

**Barrel Files:**
- Not applicable to Kotlin/Gradle structure.

---

*Convention analysis: 2025-05-22*
