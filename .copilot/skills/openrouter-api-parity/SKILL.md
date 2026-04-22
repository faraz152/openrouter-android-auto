# Skill: OpenRouter API Feature Parity

> Ensures every feature from the TypeScript `openrouter-auto` SDK is faithfully ported to the Android Kotlin SDK.

## When This Skill Applies

- Adding any new SDK feature or method
- Implementing model filtering, cost estimation, error handling, or parameter validation
- Porting logic from the TypeScript reference
- Reviewing completeness of the Android SDK
- Any PR or code review involving SDK functionality

## Reference Resolution

**CRITICAL**: Every feature implementation MUST start by consulting the reference project.

1. Read `.copilot/reference-path.local` for the absolute path.
2. Navigate to the corresponding source file in the reference project.
3. Implement the Kotlin equivalent with identical behavior.

## Feature Parity Checklist

### Model Discovery

- [ ] `fetchModels()` — GET `/api/v1/models`, parse `data` array, cache, emit event
- [ ] `getModels()` — Return cached list (defensive copy)
- [ ] `getModel(id)` — Single lookup by ID
- [ ] `filterModels(filter)` — All filter criteria: modality, inputModalities, outputModalities, maxPrice, minContextLength, maxContextLength, provider, search, supportedParameters, excludeModels, freeOnly, priceTier
- [ ] `getBestFreeModel()` — Free text model with largest contextLength

### Model Configuration

- [ ] `addModel(modelId, params, skipTest)` — Validate + optionally test + store
- [ ] `removeModel(modelId)` — Remove from storage
- [ ] `getModelConfig(modelId)` — Retrieve stored config
- [ ] `getAllModelConfigs()` — All stored configs
- [ ] `updateModelParameters(modelId, params)` — Validate + update
- [ ] `checkModelAvailability(modelId)` — Quick probe
- [ ] `testModel(modelId, params?)` — Full test probe
- [ ] `testAllModels()` — Bulk test all configs

### Chat

- [ ] `chat(request)` — Non-streaming POST, validate params, merge config
- [ ] `streamChat(request)` — SSE streaming via Flow, handle `[DONE]`, malformed chunks

### Cost

- [ ] `calculateCost(model, promptTokens, completionTokens, reasoningTokens)` — BigDecimal math
- [ ] `estimateTokens(text)` — `ceil(text.length / 4)`
- [ ] `calculateChatCost(model, messages, expectedResponseTokens)` — Add 4 overhead per message
- [ ] `formatCost(amount)` — "Free", "< $0.000001", scaled formatting
- [ ] `formatPricePer1K(price)` — "$0.000150/1K tokens"
- [ ] `getPriceTier(model)` — FREE/CHEAP/MODERATE/EXPENSIVE from registry thresholds
- [ ] `isFreeModel(model)` — Price "0" OR id ends with `:free`
- [ ] `compareModelCosts(models, tokens)` — Sorted ascending
- [ ] `getCheapestModel(models, tokens)` — First from sorted
- [ ] `calculateMonthlyEstimate(model, daily, avgPrompt, avgCompletion)` — daily × 30
- [ ] `getBestFreeModel(models)` — Free text model, largest context

### Error Handling

- [ ] `mapHttpError(statusCode, body, networkCode)` — Registry-driven mapping
- [ ] Body pattern matching: "credit"/"balance" → INSUFFICIENT_CREDITS
- [ ] `getRetryDelay(attempt, baseDelay)` — min(1000 × 2^attempt, 30000)
- [ ] `isRetryable(code)` — Check registry retryable list
- [ ] `stripSensitiveData(details)` — Remove apiKey, api_key, Authorization
- [ ] `ORAError.format()` — "ERROR [CODE]: message. Tip: ..."
- [ ] Error tips from `errors.json` registry

### Parameter Validation

- [ ] `validateParameters(model, params)` — 13 params, type + range checks
- [ ] Platform params bypass — 23 always-allowed params from `platform-params.json`
- [ ] `mergeWithDefaults(model, userParams)` — Registry defaults ← user overrides
- [ ] `sanitizeParameters(params)` — Strip null/JsonNull
- [ ] `getParameterDefinitions()` — All 13 definitions
- [ ] `getModelParameters(model)` — Filtered to model's supported_parameters
- [ ] Dynamic max_tokens limit from `topProvider.maxCompletionTokens`

### Storage

- [ ] `StorageAdapter` interface: get, set, remove, clear
- [ ] `MemoryStorage` — ConcurrentHashMap, process-lifetime
- [ ] `SharedPrefsStorage` — API key stripping before write
- [ ] `FileStorage` — Path traversal prevention, 0600 permissions
- [ ] Custom adapter injection via Builder

### Events

- [ ] `on(eventType, handler)` → returns unsubscribe lambda
- [ ] Event types: ModelsUpdated, ModelAdded, ModelRemoved, ModelTested, ConfigChanged, Error
- [ ] Global `onError` callback from Builder
- [ ] Global `onEvent` callback from Builder

### Web Search

- [ ] `createWebSearchTool(params?)` — Returns openrouter:web_search tool
- [ ] `enableWebSearch(request, params?)` — Copy request with tool appended

### Auto-fetch

- [ ] Configurable `fetchInterval` (seconds)
- [ ] Configurable `cacheDuration` (seconds)
- [ ] Auto-fetch coroutine starts on `initialize()`
- [ ] `dispose()` cancels auto-fetch and closes HttpClient

## TypeScript → Kotlin Translation Guide

| TypeScript                       | Kotlin                                              |
| -------------------------------- | --------------------------------------------------- |
| `interface Foo { bar?: string }` | `data class Foo(val bar: String? = null)`           |
| `Record<string, any>`            | `Map<String, JsonElement>`                          |
| `Promise<T>`                     | `suspend fun(): T`                                  |
| `AsyncGenerator<T>`              | `Flow<T>`                                           |
| `Map<K, V>`                      | `MutableMap<K, V>` (internal), `Map<K, V>` (public) |
| `Set<T>`                         | `Set<T>`                                            |
| `null \| undefined`              | `null` (Kotlin has no `undefined`)                  |
| `Date`                           | `Long` (epoch millis)                               |
| `setTimeout/setInterval`         | `delay()` / `while(isActive) { delay() }`           |
| `class extends Error`            | `class : Exception()`                               |
| `JSON.parse()`                   | `Json.decodeFromString<T>()`                        |
| `JSON.stringify()`               | `Json.encodeToString(serializer, value)`            |
| `axios.create()`                 | `HttpClient(CIO) { ... }`                           |
| `axios.get/post()`               | `httpClient.get/post { ... }`                       |
| `require('fs')`                  | `java.io.File` / `java.nio.file.Files`              |

## Behavioral Differences to Account For

1. **Axios interceptors** → Ktor request pipeline / per-request headers
2. **localStorage** → SharedPreferences (Android) / FileStorage (JVM CLI)
3. **require('path')** → `java.nio.file.Path` / `java.io.File`
4. **process.cwd()** → `System.getProperty("user.dir")`
5. **`[DONE]` sentinel** → Same handling in SSE parser
6. **Event handlers (Map of Sets)** → `MutableSharedFlow` or similar pattern
7. **NodeJS.Timeout** → Coroutine `Job` with cancellation
