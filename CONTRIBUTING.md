# Contributing to openrouter-android-auto

Thank you for your interest in contributing! This document explains how to set up your development environment and submit changes.

---

## Development Setup

### Prerequisites

- **Android Studio** Iguana (2023.2) or newer
- **JDK 17** (Temurin recommended)
- **Android SDK** — compile SDK 35, min SDK 24
- **Git**

### Clone and Open

```bash
git clone https://github.com/faraz152/openrouter-android-auto.git
cd openrouter-android-auto
```

Open the root `build.gradle.kts` in Android Studio. Let Gradle sync complete.

### Reference Project (maintainers only)

This SDK mirrors the TypeScript [`openrouter-auto`](https://github.com/faraz152/openrouter-auto) package. If you have access to that project locally, create:

```
.copilot/reference-path.local
```

containing the absolute path to the `openrouter-auto` directory. This file is git-ignored and is used by maintainers to verify feature parity.

---

## Module Structure

| Module            | Description                                                      | Dependencies            |
| ----------------- | ---------------------------------------------------------------- | ----------------------- |
| `:core`           | Main SDK — types, HTTP, streaming, cost, errors, params, storage | Zero Android UI deps    |
| `:compose-ui`     | Optional Jetpack Compose components                              | `:core` + Compose       |
| `:cli`            | JVM command-line tool                                            | `:core` (JVM only)      |
| `:sample-compose` | Compose demo app                                                 | `:core` + `:compose-ui` |
| `:sample-xml`     | XML Views demo app                                               | `:core`                 |

**Module boundary rule:** Never add Android UI imports to `:core`. Never add business logic to `:compose-ui`.

---

## Running Tests

```bash
# Run all :core unit tests
./gradlew :core:test

# Run with test report
./gradlew :core:test && open core/build/reports/tests/test/index.html

# Run a specific test class
./gradlew :core:test --tests "io.openrouter.android.auto.CostTest"
```

Tests use **JUnit 5**, **MockK**, **Turbine**, and **Ktor MockEngine**. No real HTTP calls are made in any test.

---

## Code Style

| Rule          | Detail                                                                                              |
| ------------- | --------------------------------------------------------------------------------------------------- |
| Language      | Kotlin only — no Java files                                                                         |
| Formatting    | Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)             |
| Naming        | `camelCase` for functions/variables, `PascalCase` for classes, `SCREAMING_SNAKE_CASE` for constants |
| Serialization | `@SerialName("snake_case")` — never `@JsonProperty` or Gson                                         |
| Async         | `suspend fun` for one-shot, `Flow<T>` for streaming                                                 |
| Errors        | Throw `ORAError(ORAErrorCode.X, ...)` — never raw `IllegalStateException`                           |
| Preconditions | Use `require()` / `check()` — not `if (...) throw`                                                  |
| HTTP client   | Never create `HttpClient` per-request — reuse the single instance                                   |

---

## Writing Tests

Every public API function must have a corresponding unit test.

```kotlin
// DO: Use Ktor MockEngine
val mockEngine = MockEngine { request ->
    respond(
        content = """{"id":"chatcmpl-123","choices":[...]}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
val sdk = buildTestSdk(engine = mockEngine)

// DO: Use Turbine for Flow tests
sdk.streamChat(request).test {
    val first = awaitItem()
    assertEquals("Hello", first.choices?.first()?.delta?.content)
    awaitComplete()
}

// DON'T: Real HTTP in tests
// DON'T: Thread.sleep() or runBlocking{delay(...)}
```

---

## Adding a New Feature

1. **Check the reference** — open the corresponding TypeScript file in `openrouter-auto/packages/core/src/` to understand intended behavior
2. **Add types** — extend `Types.kt` with any new `@Serializable` data classes
3. **Implement** — add the function to `OpenRouterAuto.kt` or a focused file
4. **Test** — add tests in `core/src/test/kotlin/`
5. **Verify** — `./gradlew :core:test` must pass with 0 failures

---

## Commit Messages

All commits follow this format:

```
[TYPE] Summary :emoji:
```

| Type       | Emoji | When                                 |
| ---------- | ----- | ------------------------------------ |
| `FEAT`     | ✨    | New feature                          |
| `FIX`      | 🐛    | Bug fix                              |
| `DOCS`     | 📚    | Documentation only                   |
| `TEST`     | 🚨    | Tests only                           |
| `REFACTOR` | 📦    | Code restructure, no behavior change |
| `BUILD`    | 🛠️    | Gradle, CI, publishing               |
| `CHORE`    | ♻️    | Maintenance, dependency bumps        |

Examples:

```
[FEAT] Add image generation support ✨
[FIX] Handle empty SSE chunks in StreamParser 🐛
[TEST] Add edge cases for CostTest 🚨
```

---

## Pull Request Guidelines

1. **Branch from `main`** — `git checkout -b feat/your-feature`
2. **One concern per PR** — don't mix features with refactors
3. **Tests required** — PRs without tests for new public API will not be merged
4. **CI must pass** — the CI workflow runs tests + assembles release AARs
5. **Update CHANGELOG.md** — add your change under `[Unreleased]`

---

## Reporting Bugs

Open an issue with:

- Android version and device/emulator
- SDK version (`auto-core` version)
- Minimal reproducible code snippet
- Full stack trace if available

---

## Security Issues

Do **not** open public issues for security vulnerabilities. Email the maintainer directly or use GitHub's private security advisory feature.
