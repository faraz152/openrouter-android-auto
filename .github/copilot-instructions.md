# OpenRouter Android Auto — Copilot Instructions

> These instructions are automatically loaded by GitHub Copilot for every interaction in this repository.

## Reference Project

This project is a **pure-Kotlin Android SDK** that mirrors every feature of the TypeScript/Python/Go/Rust `openrouter-auto` package. The reference source lives in a local directory specified in `.copilot/reference-path.local`.

### How to Resolve the Reference

1. Read `.copilot/reference-path.local` to get the absolute path to the `openrouter-auto` project.
2. If the file is missing, ask the developer to create it (see `.copilot/reference-path.example`).
3. Use the reference project's source files to ensure **feature parity** when implementing or modifying any module.

### Key Reference File Mappings

| Reference File (openrouter-auto)         | Android File                                 | Purpose                 |
| ---------------------------------------- | -------------------------------------------- | ----------------------- |
| `packages/registry/cost.json`            | `core/src/main/res/raw/cost.json`            | Cost estimation config  |
| `packages/registry/errors.json`          | `core/src/main/res/raw/errors.json`          | Error mapping registry  |
| `packages/registry/parameters.json`      | `core/src/main/res/raw/parameters.json`      | Parameter definitions   |
| `packages/registry/platform-params.json` | `core/src/main/res/raw/platform_params.json` | Platform-allowed params |
| `packages/core/src/types.ts`             | `core/.../Types.kt`                          | Data model shapes       |
| `packages/core/src/sdk.ts`               | `core/.../OpenRouterAuto.kt`                 | Public API surface      |
| `packages/core/src/cost.ts`              | `core/.../Cost.kt`                           | Cost functions          |
| `packages/core/src/errors.ts`            | `core/.../Errors.kt`                         | Error mapping           |
| `packages/core/src/parameters.ts`        | `core/.../Parameters.kt`                     | Validation logic        |
| `packages/core/src/storage.ts`           | `core/.../Storage.kt`                        | Storage adapters        |
| `packages/core/__tests__/*.test.ts`      | `core/test/*.kt`                             | Test patterns           |
| `packages/react/src/components/*.tsx`    | `compose-ui/*.kt`                            | UI components           |

### When to Consult the Reference

- **Adding a new feature**: Always check the corresponding TS file first.
- **Fixing a bug**: Cross-reference the TS implementation for intended behavior.
- **Writing tests**: Mirror the test scenarios from `__tests__/*.test.ts`.
- **Updating registry files**: Copy from `packages/registry/` verbatim (rename hyphens → underscores for Android).

## Project Architecture

```
openrouter-android-auto/
├── core/           ← :core — Android Library (AAR), main SDK
├── compose-ui/     ← :compose-ui — Optional Compose components
├── cli/            ← :cli — JVM CLI tool
├── sample-compose/ ← :sample-compose — Compose demo app
├── sample-xml/     ← :sample-xml — XML Views demo app
└── registry/       ← Reference copy of registry JSONs
```

## Tech Stack Rules

| Aspect          | Rule                                                                        |
| --------------- | --------------------------------------------------------------------------- |
| Language        | **Kotlin only** — no Java files anywhere                                    |
| Min SDK         | **API 24** (Android 7.0)                                                    |
| HTTP            | **Ktor** (CIO engine default)                                               |
| Serialization   | **kotlinx.serialization** — no Gson, no Moshi, no reflection                |
| Async           | **Coroutines + Flow** — `suspend fun` for one-shot, `Flow<T>` for streaming |
| DI              | **None** — Builder pattern, DI-agnostic                                     |
| Testing         | **JUnit 5 + MockK + Turbine + Ktor MockEngine**                             |
| Build           | **Gradle Kotlin DSL** with version catalog (`libs.versions.toml`)           |
| Package Manager | **Gradle** (no manual dependency jars)                                      |

## Coding Conventions

- **Package**: `io.openrouter.android.auto`
- **File Naming**: Dot case — `open.router.auto.kt` (but PascalCase class names: `OpenRouterAuto.kt`)
- **Variables/Functions**: camelCase, no abbreviations
- **Classes/Enums**: PascalCase
- **Constants**: SCREAMING_SNAKE_CASE
- **Serialized field names**: Use `@SerialName("snake_case")` to match API JSON
- **Internal classes**: Mark with `internal` visibility modifier
- **Nullable types**: Use `?` — don't default to empty strings/lists unless the API guarantees non-null

## Commit Convention

All commit messages follow: `[TYPE] Summary :emoji:`

| Type       | Emoji | Description        |
| ---------- | ----- | ------------------ |
| `FEAT`     | ✨    | New feature        |
| `FIX`      | 🐛    | Bug fix            |
| `DOCS`     | 📚    | Documentation      |
| `STYLE`    | 💎    | Formatting         |
| `REFACTOR` | 📦    | Code restructuring |
| `PERF`     | 🚀    | Performance        |
| `TEST`     | 🚨    | Tests              |
| `BUILD`    | 🛠️    | Build system       |
| `CI`       | ⚙️    | CI/CD              |
| `CHORE`    | ♻️    | Maintenance        |

## Security Requirements

1. **API Key**: Per-request `Authorization` header only — never stored in default headers or SharedPrefs.
2. **Storage**: Strip `apiKey`/`api_key` fields before persisting to SharedPreferences.
3. **File Storage**: Validate canonical paths — reject path traversal (`../../`).
4. **SSRF Prevention**: Validate `baseUrl` scheme is `https://` or `http://` only.
5. **ProGuard**: Keep rules for `@Serializable` classes.

## Important Notes

- The PLAN.md at the project root contains the complete implementation plan with all phases, data models, and verification criteria.
- Registry JSON files must be kept in sync with the reference project.
- All `@Serializable` data classes must handle optional fields with `= null` defaults.
- Use `BigDecimal` for cost calculations to avoid floating-point precision loss.
- SSE streaming must handle `[DONE]` sentinel, empty lines, and malformed chunks gracefully.
