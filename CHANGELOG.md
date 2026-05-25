# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [1.0.0] — 2026-05-25

### Added

#### `:core` module (`io.openrouter.android:auto-core:1.0.0`)

- **Types** — full `@Serializable` data model mirroring `openrouter-auto` TypeScript SDK: `OpenRouterModel`, `ChatRequest`, `ChatResponse`, `ChatMessage`, `StreamChunk`, `ModelPricing`, `ModelFilter`, `CostBreakdown`, `UserPreferences`, `ModelConfig`, `ParameterDef`, `ORAError`, `ORAErrorCode`
- **`OpenRouterAuto`** — main SDK client with Builder pattern:
  - `initialize()` — fetch model list, load cost/error/parameter registries
  - `chat(request)` — one-shot chat completion (`suspend`)
  - `streamChat(request)` — SSE streaming via `Flow<StreamChunk>`
  - `calculateCost(modelId, promptTokens, completionTokens)` — cost from live pricing registry
  - `estimateTokens(text)` — rough token count
  - `fetchModels()` / `getModels()` / `getModel(id)` / `filterModels(filter)` — model discovery
  - `getBestFreeModel()` — selects optimal free model
  - `testModel(modelId)` / `testAllModels()` — availability testing
  - `addModel()` / `removeModel()` — dynamic model registry
  - `updateModelParameters()` / `getModelParameters()` — per-model parameter management
  - `savePreferences()` / `getPreferences()` — persistent user preferences
  - `on(eventType, handler)` — event subscription (returns unsubscribe function)
  - `dispose()` — clean shutdown
- **Storage layer** — pluggable `StorageAdapter` interface with three implementations:
  - `InMemoryStorage` — ephemeral, zero configuration
  - `SharedPrefsStorage` — persists across app restarts via `SharedPreferences`
  - `FileStorage` — full JSON persistence to a file
- **Cost engine** — `calculateCost()` using `BigDecimal` arithmetic from bundled `cost.json` registry
- **Error handling** — typed `ORAError` with `ORAErrorCode` enum and `retryable` flag; `errors.json` registry maps HTTP status codes to SDK error codes
- **Parameter validation** — `validateParameters()` checks model-specific constraints from `parameters.json` and `platform_params.json` registries
- **SSE streaming** — `StreamParser` and `StreamAccumulator` handle `[DONE]` sentinel, empty lines, malformed chunks, reasoning content, and tool calls
- **Event system** — `ORAEventType` enum with `INITIALIZED`, `MODEL_ADDED`, `MODEL_REMOVED`, `CHAT_START`, `CHAT_END`, `STREAM_START`, `STREAM_END`, `ERROR` events

#### `:compose-ui` module (`io.openrouter.android:auto-compose:1.0.0`)

- **`ModelSelector`** — Compose component for browsing and selecting OpenRouter models with live search
- **`CostEstimator`** — Compose component showing per-call cost breakdown with optional text input

#### `:cli` module

- JVM command-line tool with `models`, `chat`, `stream`, `cost` subcommands

#### Sample Apps

- **`sample-compose`** — full Jetpack Compose demo app: API key screen, model browser, streaming chat, cost estimator
- **`sample-xml`** — full XML Views demo app: API key activity, RecyclerView model list with search, streaming chat activity

#### CI/CD

- GitHub Actions CI workflow: build + test on every push/PR to `main`
- GitHub Actions publish workflow: publish to Maven Central + GitHub Packages on release

### Technical

- Kotlin 1.9, coroutines 1.8, Ktor 2.3 (CIO engine)
- kotlinx.serialization 1.7 — zero reflection, compile-time codegen
- Min SDK 24 (Android 7.0), compile SDK 35, JVM target 17
- 206 unit tests across 6 test classes, 0 failures
- ProGuard consumer rules for both published AARs

---

[Unreleased]: https://github.com/faraz152/openrouter-android-auto/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/faraz152/openrouter-android-auto/releases/tag/v1.0.0
