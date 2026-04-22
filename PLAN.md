# openrouter-android-auto-connect — Complete Project Plan

> **Pure-Kotlin Android SDK** mirroring every feature of `openrouter-auto` — model discovery, chat completions, SSE streaming, cost estimation, parameter validation, error handling, pluggable storage, and event system.

---

## Table of Contents

- [Tech Stack & Decisions](#tech-stack--decisions)
- [Repository Structure](#repository-structure)
- [Phase 1: Project Scaffolding & Build System](#phase-1-project-scaffolding--build-system)
- [Phase 2: Types & Data Models](#phase-2-types--data-models)
- [Phase 3: Error Handling](#phase-3-error-handling)
- [Phase 4: Parameter Validation](#phase-4-parameter-validation)
- [Phase 5: Cost Estimation](#phase-5-cost-estimation)
- [Phase 6: Storage Layer](#phase-6-storage-layer)
- [Phase 7: Core SDK Client](#phase-7-core-sdk-client)
- [Phase 8: Compose UI Module](#phase-8-compose-ui-module)
- [Phase 9: CLI Module](#phase-9-cli-module)
- [Phase 10: Sample Apps](#phase-10-sample-apps)
- [Phase 11: Testing](#phase-11-testing)
- [Phase 12: CI/CD & Publishing](#phase-12-cicd--publishing)
- [Phase 13: Documentation](#phase-13-documentation)
- [Dependency Graph](#dependency-graph)
- [Feature Parity Matrix](#feature-parity-matrix)
- [Reference Files](#reference-files)
- [Scope Boundaries](#scope-boundaries)

---

## Tech Stack & Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Language | **Kotlin** (no Java) | Modern, concise, coroutine-native |
| Min SDK | **API 24** (Android 7.0) | Good device coverage (~95%), modern APIs |
| HTTP Client | **Ktor** (CIO default) + **OkHttp engine optional** | Ktor is KMP-compatible & coroutine-native; OkHttp for teams already using it |
| Serialization | **kotlinx.serialization** | Compile-time codegen (fastest), KMP-ready, no reflection |
| Async | **Kotlin Coroutines + Flow** | `suspend fun` for one-shot calls, `Flow<T>` for streaming |
| KMP Scope | **Android-only now**, KMP-ready structure | `commonMain` / `androidMain` split — iOS/Desktop targets future work |
| DI Strategy | **DI-agnostic** (Builder pattern) | No Hilt/Koin dependency; users wire their own |
| Compose UI | **Optional `:compose-ui` module** | Separate artifact, not forced on SDK consumers |
| Storage | **Pluggable**: Memory, SharedPrefs, File | 3 adapters, custom adapter injection supported |
| CLI | **Kotlin JVM** (kotlinx-cli) | Matching Python CLI commands |
| Publishing | **Maven Central + JitPack + GitHub Packages** | Maximum distribution reach |
| Sample Apps | **Both XML + Compose** | Demonstrate both Android UI paradigms |

---

## Repository Structure

```
openrouter-android-auto-connect/
├── build.gradle.kts                          ← root build config (plugins, allprojects)
├── settings.gradle.kts                       ← module includes
├── gradle.properties                         ← project-wide props, Maven signing config
├── gradle/
│   └── libs.versions.toml                    ← Gradle version catalog (single source of truth for deps)
│
├── registry/                                 ← copied from openrouter-auto (reference copy)
│   ├── cost.json
│   ├── errors.json
│   ├── parameters.json
│   └── platform-params.json
│
├── core/                                     ← :core — Android Library (AAR)
│   ├── build.gradle.kts
│   ├── consumer-rules.pro                    ← ProGuard rules shipped to consumers
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            ← INTERNET permission
│       │   ├── res/raw/                       ← registry JSONs embedded as raw resources
│       │   │   ├── cost.json
│       │   │   ├── errors.json
│       │   │   ├── parameters.json
│       │   │   └── platform_params.json
│       │   └── kotlin/io/openrouter/android/auto/
│       │       ├── OpenRouterAuto.kt           ← main SDK client + Builder
│       │       ├── Types.kt                     ← all @Serializable data classes
│       │       ├── Cost.kt                      ← cost estimation functions
│       │       ├── Errors.kt                    ← error mapping, ORAError class
│       │       ├── Parameters.kt                ← validation, defaults, sanitize
│       │       ├── Storage.kt                   ← StorageAdapter interface + 3 implementations
│       │       ├── Events.kt                    ← event types + EventBus (MutableSharedFlow)
│       │       └── internal/
│       │           ├── HttpEngine.kt            ← Ktor HttpClient factory
│       │           ├── StreamParser.kt          ← SSE line parser → Flow<StreamChunk>
│       │           └── StreamAccumulator.kt     ← chunk reassembly → ChatResponse
│       └── test/kotlin/io/openrouter/android/auto/
│           ├── OpenRouterAutoTest.kt
│           ├── CostTest.kt
│           ├── ErrorsTest.kt
│           ├── ParametersTest.kt
│           ├── StorageTest.kt
│           ├── StreamAccumulatorTest.kt
│           └── TestFactory.kt                  ← makeModel(), makeChatResponse(), etc.
│
├── compose-ui/                               ← :compose-ui — Android Library (AAR)
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/openrouter/android/auto/compose/
│       ├── ModelSelector.kt                   ← searchable dropdown, price tier badges
│       ├── ModelConfigPanel.kt                ← dynamic parameter form, live validation
│       ├── CostEstimator.kt                   ← live cost breakdown with text input
│       ├── ErrorDisplay.kt                    ← error + tip banner, auto-dismiss
│       └── theme/
│           └── OpenRouterTheme.kt             ← Material3 color tokens
│
├── cli/                                      ← :cli — JVM Application
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/openrouter/android/auto/cli/
│       └── Main.kt                            ← setup, models, add, test, chat commands
│
├── sample-compose/                           ← :sample-compose — Android App
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/io/openrouter/android/auto/sample/compose/
│           ├── MainActivity.kt
│           ├── ApiKeyScreen.kt
│           ├── ModelBrowserScreen.kt
│           ├── ChatScreen.kt
│           └── CostScreen.kt
│
├── sample-xml/                               ← :sample-xml — Android App
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/layout/
│       │   ├── activity_main.xml
│       │   ├── activity_chat.xml
│       │   └── item_model.xml
│       └── kotlin/io/openrouter/android/auto/sample/xml/
│           ├── App.kt                         ← Application class, SDK init
│           ├── ApiKeyActivity.kt
│           ├── ModelListActivity.kt
│           ├── ChatActivity.kt
│           └── viewmodel/
│               ├── ModelListViewModel.kt
│               └── ChatViewModel.kt
│
├── .github/
│   └── workflows/
│       ├── ci.yml                            ← build + test on PR
│       └── publish.yml                       ← publish on release tag
│
├── .gitignore
├── LICENSE                                   ← MIT
├── README.md
├── QUICKSTART.md
├── CONTRIBUTING.md
└── CHANGELOG.md
```

---

## Phase 1: Project Scaffolding & Build System

**Goal:** Working Gradle multi-module project that compiles with zero source code.

### Tasks

| # | Task | Details |
|---|---|---|
| 1.1 | Create root directory | `openrouter-android-auto-connect/` alongside `openrouter-auto/` |
| 1.2 | `settings.gradle.kts` | `rootProject.name = "openrouter-android-auto-connect"`, include `:core`, `:compose-ui`, `:cli`, `:sample-compose`, `:sample-xml` |
| 1.3 | `gradle/libs.versions.toml` | Version catalog — see [Dependencies Table](#13-version-catalog) below |
| 1.4 | Root `build.gradle.kts` | Apply plugins at project level (AGP, Kotlin, Serialization) with `apply false` |
| 1.5 | `gradle.properties` | `android.useAndroidX=true`, `kotlin.code.style=official`, `android.nonTransitiveRClass=true`, Maven signing placeholders |
| 1.6 | `:core/build.gradle.kts` | `com.android.library` + `kotlinx-serialization`, minSdk=24, compileSdk=35, embed registry JSONs |
| 1.7 | `:compose-ui/build.gradle.kts` | `com.android.library` + Compose compiler, depends on `:core` |
| 1.8 | `:cli/build.gradle.kts` | `application` plugin (pure JVM), depends on shared types, kotlinx-cli |
| 1.9 | `:sample-compose/build.gradle.kts` | `com.android.application`, depends on `:core` + `:compose-ui` |
| 1.10 | `:sample-xml/build.gradle.kts` | `com.android.application`, depends on `:core` |
| 1.11 | Copy registry JSONs | `openrouter-auto/packages/registry/*.json` → `core/src/main/res/raw/` (rename hyphens to underscores for Android resource naming) |
| 1.12 | `AndroidManifest.xml` (core) | `<uses-permission android:name="android.permission.INTERNET"/>` |
| 1.13 | ProGuard rules | Keep rules for `@Serializable` classes, Ktor engine classes |
| 1.14 | `.gitignore` | Standard Android + Gradle ignores |
| 1.15 | Gradle wrapper | `gradle/wrapper/` with Gradle 8.9+ |

### 1.3: Version Catalog

```toml
[versions]
kotlin = "2.0.21"
agp = "8.5.2"
coroutines = "1.8.1"
ktor = "2.3.12"
serialization = "1.7.3"
compose-bom = "2024.12.01"
material3 = "1.3.1"
junit5 = "5.10.3"
mockk = "1.13.12"
turbine = "1.1.0"
kotlinx-cli = "0.3.6"

[libraries]
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }

junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
kotlinx-cli = { module = "org.jetbrains.kotlinx:kotlinx-cli", version.ref = "kotlinx-cli" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### Verification

```bash
./gradlew :core:assembleDebug
# Must compile successfully with zero Kotlin source files
```

---

## Phase 2: Types & Data Models

**Goal:** All `@Serializable` data classes matching the original `types.ts` / `types.py` / `types.go`.

**Parallel with:** Phases 3, 4, 5 (no dependency between them)

### File: `core/src/main/kotlin/io/openrouter/android/auto/Types.kt`

### 2.1: Model Types

```kotlin
@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val pricing: ModelPricing,
    @SerialName("context_length") val contextLength: Int,
    val architecture: ModelArchitecture? = null,
    @SerialName("top_provider") val topProvider: TopProvider? = null,
    @SerialName("per_request_limits") val perRequestLimits: PerRequestLimits? = null,
    @SerialName("supported_parameters") val supportedParameters: List<String>? = null
)

@Serializable
data class ModelPricing(
    val prompt: String,          // price per token as string (e.g., "0.00015")
    val completion: String,
    val image: String? = null,
    val request: String? = null
)

@Serializable
data class ModelArchitecture(
    val modality: String? = null,         // "text->text", "text+image->text", etc.
    val tokenizer: String? = null,
    @SerialName("instruct_type") val instructType: String? = null
)

@Serializable
data class TopProvider(
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated") val isModerated: Boolean? = null
)

@Serializable
data class PerRequestLimits(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null
)
```

### 2.2: Chat Types

```kotlin
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Double? = null,
    @SerialName("min_p") val minP: Double? = null,
    @SerialName("top_a") val topA: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,  // "auto"|"none"|object
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    val reasoning: ReasoningConfig? = null,
    val include: List<String>? = null,
    @SerialName("response_format") val responseFormat: JsonElement? = null,
    val provider: ProviderPreferences? = null,
    val models: List<String>? = null,
    val route: String? = null,
    val plugins: JsonElement? = null,
    val metadata: JsonElement? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val user: String? = null,
    val modalities: List<String>? = null,
    val logprobs: Boolean? = null,
    @SerialName("top_logprobs") val topLogprobs: Int? = null,
    @SerialName("cache_control") val cacheControl: JsonElement? = null,
    @SerialName("service_tier") val serviceTier: String? = null,
    @SerialName("stream_options") val streamOptions: JsonElement? = null,
    val trace: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,                                   // "system", "user", "assistant", "tool"
    val content: JsonElement? = null,                   // String or List<ContentPart>
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val refusal: String? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val annotations: List<JsonElement>? = null
)

@Serializable
sealed class ContentPart {
    @Serializable @SerialName("text")
    data class Text(val text: String) : ContentPart()

    @Serializable @SerialName("image_url")
    data class ImageUrl(@SerialName("image_url") val imageUrl: ImageUrlData) : ContentPart()

    @Serializable @SerialName("input_audio")
    data class InputAudio(@SerialName("input_audio") val inputAudio: InputAudioData) : ContentPart()
}

@Serializable data class ImageUrlData(val url: String, val detail: String? = null)
@Serializable data class InputAudioData(val data: String, val format: String)
```

### 2.3: Tool Types

```kotlin
@Serializable
data class ToolDefinition(
    val type: String,                                     // "function" or "openrouter:web_search"
    val function: FunctionDefinition? = null,
    val parameters: JsonElement? = null                    // for web search tool
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null                    // JSON Schema
)

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null,
    val index: Int? = null                                 // for streaming assembly
)

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
)
```

### 2.4: Response Types

```kotlin
@Serializable
data class ChatResponse(
    val id: String,
    val model: String? = null,
    val created: Long? = null,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("reasoning_tokens") val reasoningTokens: Int? = null
)

@Serializable
data class StreamChunk(
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val choices: List<StreamChoice>? = null,
    val usage: Usage? = null
)

@Serializable
data class StreamChoice(
    val index: Int,
    val delta: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)
```

### 2.5: Config & Filter Types

```kotlin
@Serializable
data class ModelConfig(
    @SerialName("model_id") val modelId: String,
    val parameters: Map<String, JsonElement> = emptyMap(),
    @SerialName("added_at") val addedAt: Long = System.currentTimeMillis(),
    @SerialName("last_tested_at") val lastTestedAt: Long? = null,
    @SerialName("test_result") val testResult: ModelTestResult? = null
)

@Serializable
data class ModelTestResult(
    val success: Boolean,
    @SerialName("response_time") val responseTime: Long? = null,
    val error: String? = null,
    val response: String? = null
)

data class ModelFilter(
    val freeOnly: Boolean = false,
    val maxPrice: Double? = null,
    val priceTier: PriceTier? = null,
    val provider: String? = null,
    val search: String? = null,
    val minContextLength: Int? = null,
    val maxContextLength: Int? = null,
    val modality: String? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
    val supportedParameters: List<String>? = null,
    val excludeModels: List<String>? = null
)

enum class PriceTier { FREE, CHEAP, MODERATE, EXPENSIVE }

@Serializable
data class ProviderPreferences(
    val order: List<String>? = null,
    @SerialName("allow_fallbacks") val allowFallbacks: Boolean? = null,
    @SerialName("require_parameters") val requireParameters: Boolean? = null,
    @SerialName("data_collection") val dataCollection: String? = null,
    val sort: String? = null,
    val quantizations: List<String>? = null,
    val ignore: List<String>? = null
)

@Serializable
data class ReasoningConfig(
    val effort: String,        // "low", "medium", "high"
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class CostEstimate(
    @SerialName("prompt_cost") val promptCost: Double,
    @SerialName("completion_cost") val completionCost: Double,
    @SerialName("reasoning_cost") val reasoningCost: Double = 0.0,
    @SerialName("total_cost") val totalCost: Double,
    val currency: String = "USD"
)
```

### Verification

```kotlin
// Unit test: deserialize actual OpenRouter API response
@Test fun `deserialize models API response`() {
    val json = """{"data":[{"id":"openai/gpt-4o","name":"GPT-4o",...}]}"""
    val models = Json.decodeFromString<ModelsResponse>(json)
    assertEquals("openai/gpt-4o", models.data[0].id)
}
```

---

## Phase 3: Error Handling

**Goal:** Registry-driven error mapping identical to all other SDKs.

**Parallel with:** Phases 2, 4, 5

### File: `core/src/main/kotlin/io/openrouter/android/auto/Errors.kt`

### 3.1: Error Code Enum

```kotlin
enum class ORAErrorCode {
    INVALID_API_KEY,
    RATE_LIMITED,
    MODEL_NOT_FOUND,
    MODEL_UNAVAILABLE,
    INVALID_PARAMETERS,
    INSUFFICIENT_CREDITS,
    PROVIDER_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    UNKNOWN
}
```

### 3.2: Error Class

```kotlin
class ORAError(
    val code: ORAErrorCode,
    message: String,
    val retryable: Boolean,
    val details: Map<String, Any>? = null,
    val tip: String? = null
) : Exception(message) {
    fun format(): String  // "ERROR [RATE_LIMITED]: Too many requests. Tip: Wait and retry..."
}
```

### 3.3: Functions to Implement

| Function | Signature | Description |
|---|---|---|
| `mapHttpError` | `(statusCode: Int, body: String?, networkCode: String?) → ORAError` | Maps HTTP status / network error to typed ORAError using `errors.json` `code_map` |
| `getRetryDelay` | `(attempt: Int, baseDelay: Long = 1000) → Long` | $\min(1000 \times 2^{attempt},\ 30000)$ ms |
| `isRetryable` | `(code: ORAErrorCode) → Boolean` | Check against `errors.json` `retryable` list |
| `stripSensitiveData` | `(details: Map<String, Any>) → Map<String, Any>` | Remove `apiKey`, `api_key`, `Authorization` from error details |

### 3.4: Registry Loading

- Load `R.raw.errors` at initialization
- Parse into internal maps: `codeMap`, `messages`, `tips`, `retryableCodes`
- Body pattern matching: if response body contains "credit" or "balance" → `INSUFFICIENT_CREDITS`

### Verification

```kotlin
@Test fun `map 429 to RATE_LIMITED`() {
    val error = mapHttpError(429, null)
    assertEquals(ORAErrorCode.RATE_LIMITED, error.code)
    assertTrue(error.retryable)
}

@Test fun `retry delay exponential backoff capped at 30s`() {
    assertEquals(1000, getRetryDelay(0))
    assertEquals(2000, getRetryDelay(1))
    assertEquals(4000, getRetryDelay(2))
    assertEquals(30000, getRetryDelay(10))  // capped
}
```

---

## Phase 4: Parameter Validation

**Goal:** Registry-driven parameter validation, defaults, and sanitization.

**Parallel with:** Phases 2, 3, 5

### File: `core/src/main/kotlin/io/openrouter/android/auto/Parameters.kt`

### 4.1: Registry Data

- Load `R.raw.parameters` → 13 parameter definitions with type, min, max, default
- Load `R.raw.platform_params` → 23 always-allowed parameter names

### 4.2: Functions to Implement

| Function | Signature | Description |
|---|---|---|
| `validateParameters` | `(model: OpenRouterModel, params: Map<String, JsonElement>) → List<String>` | Returns list of validation errors (empty = valid) |
| `mergeWithDefaults` | `(model: OpenRouterModel, userParams: Map<String, JsonElement>) → Map<String, JsonElement>` | Registry defaults ← user overrides |
| `sanitizeParameters` | `(params: Map<String, JsonElement>) → Map<String, JsonElement>` | Strip null/JsonNull values |
| `getParameterDefinitions` | `() → Map<String, ParameterDef>` | All 13 parameter definitions |
| `getModelParameters` | `(model: OpenRouterModel) → Map<String, ParameterDef>` | Filtered to model's `supported_parameters` |

### 4.3: Validation Rules

```
For each param in user params:
  1. If param NOT in model.supportedParameters AND NOT in PLATFORM_PARAMS → error
  2. If param has registry definition:
     a. Type check: "number" → must be numeric, "integer" → must be whole, "boolean", "array"
     b. Range check: value < min or value > max → error
     c. Special: max_tokens max = model.topProvider.maxCompletionTokens (dynamic)
```

### Verification

```kotlin
@Test fun `reject temperature above 2`() {
    val errors = validateParameters(model, mapOf("temperature" to JsonPrimitive(3.0)))
    assertTrue(errors.any { "temperature" in it })
}

@Test fun `allow platform params without model support`() {
    val errors = validateParameters(model, mapOf("session_id" to JsonPrimitive("abc")))
    assertTrue(errors.isEmpty())
}
```

---

## Phase 5: Cost Estimation

**Goal:** Full cost calculation module matching all other SDKs.

**Parallel with:** Phases 2, 3, 4

### File: `core/src/main/kotlin/io/openrouter/android/auto/Cost.kt`

### 5.1: Core Formula

$$
\text{promptCost} = \frac{\text{promptTokens}}{1000} \times \text{pricePerKPrompt}
$$

$$
\text{completionCost} = \frac{\text{completionTokens}}{1000} \times \text{pricePerKCompletion}
$$

$$
\text{reasoningCost} = \frac{\text{reasoningTokens}}{1000} \times \text{pricePerKCompletion} \quad \text{(billed at completion rate)}
$$

$$
\text{totalCost} = \text{promptCost} + \text{completionCost} + \text{reasoningCost}
$$

> Use `toBigDecimal()` for parsing price strings to avoid floating-point precision loss.

### 5.2: Functions to Implement

| Function | Signature | Description |
|---|---|---|
| `calculateCost` | `(model, promptTokens, completionTokens, reasoningTokens) → CostEstimate` | Core cost calculation |
| `estimateTokens` | `(text: String) → Int` | `⌈text.length / 4⌉` (from `cost.json` config) |
| `calculateChatCost` | `(model, messages, expectedResponseTokens) → CostEstimate` | Adds 4 overhead tokens per message |
| `formatCost` | `(amount: Double) → String` | `"Free"`, `"< $0.000001"`, or scaled `"$0.001234"` |
| `formatPricePer1K` | `(price: String) → String` | `"$0.000150/1K tokens"` |
| `getPriceTier` | `(model) → PriceTier` | From `cost.json` thresholds: free→0, cheap→0.0001, moderate→0.01, expensive→∞ |
| `isFreeModel` | `(model) → Boolean` | Price strings are "0" OR model ID ends with `:free` |
| `compareModelCosts` | `(models, promptTokens, completionTokens) → List<Pair<Model, Cost>>` | Sorted ascending by totalCost |
| `getCheapestModel` | `(models, promptTokens, completionTokens) → OpenRouterModel?` | First from sorted |
| `calculateMonthlyEstimate` | `(model, dailyRequests, avgPrompt, avgCompletion) → CostEstimate` | `daily × 30` |
| `getBestFreeModel` | `(models) → OpenRouterModel?` | Free text model with largest `contextLength` |

### Verification

```kotlin
@Test fun `calculate cost for known pricing`() {
    val model = makeModel(promptPrice = "0.001", completionPrice = "0.002")
    val cost = calculateCost(model, promptTokens = 1000, completionTokens = 500)
    assertEquals(1.0, cost.promptCost, 0.0001)
    assertEquals(1.0, cost.completionCost, 0.0001)
}

@Test fun `free model detection`() {
    assertTrue(isFreeModel(makeModel(promptPrice = "0", completionPrice = "0")))
    assertTrue(isFreeModel(makeModel(id = "meta/llama:free")))
}
```

---

## Phase 6: Storage Layer

**Goal:** Pluggable storage with 3 adapters.

**Depends on:** Phase 2 (needs Types for serialization)

### File: `core/src/main/kotlin/io/openrouter/android/auto/Storage.kt`

### 6.1: Interface

```kotlin
interface StorageAdapter {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

object StorageKeys {
    const val MODELS = "models"
    const val MODEL_CONFIGS = "model_configs"
    const val USER_PREFS = "user_preferences"
    const val LAST_FETCH = "last_fetch"
}
```

### 6.2: Adapters

| Adapter | Context Required | Persistence | Use Case |
|---|---|---|---|
| `MemoryStorage` | No | Process lifetime | Default, testing, CLI |
| `SharedPrefsStorage` | Yes (`android.content.Context`) | Disk (XML) | Android apps |
| `FileStorage` | No | JSON file | CLI, JVM tools |

### 6.3: Security Rules

- **SharedPrefsStorage:** Before writing any value, scan for and strip `apiKey` / `api_key` fields from JSON strings (never persist secrets to SharedPreferences)
- **FileStorage:** Resolve the canonical path and assert it falls under `user.home` or `user.dir` — reject path traversal attempts (e.g., `../../etc/passwd`)
- **FileStorage:** Create file with owner-only permissions (`PosixFilePermissions.asFileAttribute("rw-------")`)

### Verification

```kotlin
@Test fun `memory storage CRUD`() { ... }
@Test fun `shared prefs strips api key`() { ... }
@Test fun `file storage rejects path traversal`() { ... }
@Test fun `concurrent memory storage access`() { ... }
```

---

## Phase 7: Core SDK Client

**Goal:** Main `OpenRouterAuto` client with full API surface matching all other SDKs.

**Depends on:** Phases 2–6

### Files

- `core/.../OpenRouterAuto.kt` — public API
- `core/.../internal/HttpEngine.kt` — Ktor client factory
- `core/.../internal/StreamParser.kt` — SSE parser
- `core/.../internal/StreamAccumulator.kt` — chunk reassembly
- `core/.../Events.kt` — event types + bus

### 7.1: Builder Pattern

```kotlin
val client = OpenRouterAuto.Builder(apiKey = "sk-or-...")
    .baseUrl("https://openrouter.ai/api/v1")   // default
    .autoFetch(true)                             // default
    .fetchInterval(3600L)                        // seconds, default
    .cacheDuration(3600L)                        // seconds, default
    .enableTesting(true)                         // default
    .testPrompt("Say \"Hello!\" and nothing else.")
    .storageAdapter(SharedPrefsStorage(context)) // or MemoryStorage()
    .siteUrl("https://github.com/your/app")
    .siteName("My App")
    .onError { error: ORAError -> Log.e("ORA", error.format()) }
    .onEvent { event: ORAEvent -> Log.d("ORA", "$event") }
    .build()
```

### 7.2: Public API Surface

#### Lifecycle

| Method | Signature | Description |
|---|---|---|
| `initialize` | `suspend fun initialize()` | Load cache → fetch if expired → start auto-fetch coroutine |
| `dispose` | `fun dispose()` | Cancel auto-fetch, close HttpClient |

#### Model Discovery

| Method | Signature | Description |
|---|---|---|
| `fetchModels` | `suspend fun fetchModels(): List<OpenRouterModel>` | GET `/models`, cache, emit `models:updated` |
| `getModels` | `fun getModels(): List<OpenRouterModel>` | Return cached list |
| `getModel` | `fun getModel(id: String): OpenRouterModel?` | Single lookup |
| `filterModels` | `fun filterModels(filter: ModelFilter): List<OpenRouterModel>` | Full filter criteria |
| `getBestFreeModel` | `fun getBestFreeModel(): OpenRouterModel?` | Free text model, largest context |

#### Model Configuration

| Method | Signature | Description |
|---|---|---|
| `addModel` | `suspend fun addModel(modelId, params, skipTest): ModelConfig` | Validate + optionally test + store |
| `removeModel` | `suspend fun removeModel(modelId: String)` | Remove from storage |
| `getModelConfig` | `fun getModelConfig(modelId: String): ModelConfig?` | Retrieve stored config |
| `getAllModelConfigs` | `fun getAllModelConfigs(): Map<String, ModelConfig>` | All stored configs |
| `updateModelParameters` | `suspend fun updateModelParameters(modelId, params)` | Validate + update |
| `checkModelAvailability` | `suspend fun checkModelAvailability(modelId): ModelTestResult` | Quick probe |
| `testModel` | `suspend fun testModel(modelId, params?): ModelTestResult` | Full test probe |
| `testAllModels` | `suspend fun testAllModels(): Map<String, ModelTestResult>` | Bulk test |

#### Chat

| Method | Signature | Description |
|---|---|---|
| `chat` | `suspend fun chat(request: ChatRequest): ChatResponse` | Non-streaming POST |
| `streamChat` | `fun streamChat(request: ChatRequest): Flow<StreamChunk>` | SSE streaming, cold Flow |

#### Cost

| Method | Signature | Description |
|---|---|---|
| `calculateCost` | `fun calculateCost(modelId, promptTokens, completionTokens): CostEstimate` | Cost from cached model |

#### Web Search

| Method | Signature | Description |
|---|---|---|
| `createWebSearchTool` | `fun createWebSearchTool(params?): ToolDefinition` | Returns `openrouter:web_search` tool |
| `enableWebSearch` | `fun enableWebSearch(request, params?): ChatRequest` | Copy of request with web search tool appended |

#### Events

| Method | Signature | Description |
|---|---|---|
| `on` | `fun on(eventType: String, handler: (ORAEvent) → Unit): () → Unit` | Subscribe; returns unsubscribe lambda |

### 7.3: Internal — HttpEngine

```kotlin
// internal/HttpEngine.kt
internal fun createHttpClient(config: OpenRouterAutoConfig): HttpClient {
    // 1. Validate baseUrl scheme (https:// or http:// only — SSRF prevention)
    // 2. Create Ktor HttpClient with CIO engine (or OkHttp if configured)
    // 3. Install ContentNegotiation with kotlinx.serialization JSON
    // 4. Install request interceptor:
    //    - Authorization: Bearer ${apiKey}   (per-request, NOT default header)
    //    - HTTP-Referer: ${siteUrl}
    //    - X-Title: ${siteName}
}
```

### 7.4: Internal — StreamParser

```kotlin
// internal/StreamParser.kt
internal fun parseSSEStream(channel: ByteReadChannel): Flow<StreamChunk> = flow {
    // Read line by line
    // Skip empty lines and lines not starting with "data: "
    // Skip "[DONE]" sentinel
    // JSON-parse each data payload into StreamChunk
    // Emit via flow
}
```

### 7.5: Internal — StreamAccumulator

```kotlin
// internal/StreamAccumulator.kt
class StreamAccumulator {
    private var content = StringBuilder()
    private var reasoning = StringBuilder()
    private val toolCalls = mutableMapOf<Int, ToolCall>()  // index-keyed
    private var finishReason: String? = null
    private var id: String? = null
    private var model: String? = null
    private var created: Long? = null
    private var usage: Usage? = null

    fun push(chunk: StreamChunk) { /* accumulate deltas */ }
    fun toResponse(): ChatResponse { /* reconstruct full response */ }
}
```

### 7.6: Events

```kotlin
sealed class ORAEvent {
    data class ModelsUpdated(val count: Int) : ORAEvent()
    data class ModelAdded(val modelId: String, val config: ModelConfig) : ORAEvent()
    data class ModelRemoved(val modelId: String) : ORAEvent()
    data class ModelTested(val modelId: String, val result: ModelTestResult) : ORAEvent()
    data class ConfigChanged(val modelId: String, val config: ModelConfig) : ORAEvent()
    data class Error(val error: ORAError) : ORAEvent()
}
```

Internal bus: `MutableSharedFlow<ORAEvent>` with `replay = 0`, collected in subscriber coroutines.

### Verification

```kotlin
// Ktor MockEngine replaces axios mock adapter
val mockEngine = MockEngine { request ->
    when {
        request.url.encodedPath == "/models" -> respond(modelsJson, HttpStatusCode.OK)
        request.url.encodedPath == "/chat/completions" -> respond(chatJson, HttpStatusCode.OK)
        else -> respondError(HttpStatusCode.NotFound)
    }
}

@Test fun `initialize fetches models`() { ... }
@Test fun `chat sends correct request`() { ... }
@Test fun `streamChat emits chunks`() { ... }
@Test fun `addModel validates and stores`() { ... }
@Test fun `filterModels by price tier`() { ... }
@Test fun `events emitted on model add`() { ... }
@Test fun `builder rejects missing apiKey`() { ... }
```

---

## Phase 8: Compose UI Module

**Goal:** Optional Material3 Compose components.

**Depends on:** Phase 7

### 8.1: ModelSelector

```kotlin
@Composable
fun ModelSelector(
    models: List<OpenRouterModel>,
    selectedModel: OpenRouterModel?,
    onSelect: (OpenRouterModel) -> Unit,
    showPricing: Boolean = true,
    showContextLength: Boolean = true,
    filters: ModelFilter? = null,
    placeholder: String = "Select a model..."
)
```

- `ExposedDropdownMenuBox` with search `TextField`
- Each item shows: model name, provider, price tier badge (colored `FilterChip`), context length
- Optional filter bar: row of `FilterChip` for FREE / CHEAP / MODERATE / EXPENSIVE

### 8.2: ModelConfigPanel

```kotlin
@Composable
fun ModelConfigPanel(
    modelId: String,
    parameters: Map<String, JsonElement>,
    parameterDefs: Map<String, ParameterDef>,
    onSave: (Map<String, JsonElement>) -> Unit,
    onTest: (() -> Unit)? = null
)
```

- Dynamic form from parameter definitions
- `Slider` for number params (temperature 0–2, top_p 0–1, etc.)
- `OutlinedTextField` for integer params
- `Switch` for boolean params
- Inline validation error `Text` (red, below each field)
- `Button`s: Save, Test (optional)

### 8.3: CostEstimator

```kotlin
@Composable
fun CostEstimator(
    model: OpenRouterModel,
    defaultPromptTokens: Int = 1000,
    defaultCompletionTokens: Int = 500,
    showTextInput: Boolean = false
)
```

- Two `OutlinedTextField` for token counts
- Optional `TextField` multiline (auto-runs `estimateTokens()` on text change)
- Cost breakdown `Card`: Prompt / Completion / Reasoning / **Total**

### 8.4: ErrorDisplay

```kotlin
@Composable
fun ErrorDisplay(
    error: ORAError?,
    onDismiss: (() -> Unit)? = null,
    autoHideMs: Long = 5000
)
```

- Material3 `Card` with error icon + message + tip
- Auto-dismiss via `LaunchedEffect(error) { delay(autoHideMs); onDismiss?.invoke() }`

### 8.5: Theme

```kotlin
object OpenRouterTheme {
    val FreeTierColor = Color(0xFF4CAF50)      // Green
    val CheapTierColor = Color(0xFF2196F3)     // Blue
    val ModerateTierColor = Color(0xFFFF9800)  // Orange
    val ExpensiveTierColor = Color(0xFFF44336) // Red
}
```

### Verification

- `@Preview` for each component with sample data
- Manual test in `:sample-compose`

---

## Phase 9: CLI Module

**Goal:** JVM CLI matching the Python SDK's CLI commands.

**Depends on:** Phase 7

### 9.1: KMP Restructure Required

Since `:core` is an Android library (AAR), the CLI can't depend on it directly. We restructure:

```
core/
├── src/
│   ├── commonMain/kotlin/    ← Types, Cost, Errors, Params, Storage interface,
│   │                            MemoryStorage, FileStorage, HttpEngine, SDK client
│   ├── androidMain/kotlin/   ← SharedPrefsStorage, R.raw resource loading
│   └── jvmMain/kotlin/       ← JVM-specific overrides (if any)
```

This makes `:cli` depend on `:core` (JVM target) while Android apps use the Android target.

### 9.2: Commands

| Command | Flags | Description |
|---|---|---|
| `setup` | `--api-key KEY` | Store API key in `~/.openrouter-auto/config.json` (0600 perms), test connection |
| `models` | `--free`, `--provider P`, `--search S`, `--limit N` | List/filter models, tabular output |
| `add <model>` | `--temperature N`, `--max-tokens N`, `--skip-test` | Validate + optionally test + store config |
| `test <model>` | — | Run test probe, print result |
| `chat <model> "prompt"` | `--stream`, `--system MSG` | Send chat, print response or stream |

### 9.3: Implementation

```kotlin
// cli/Main.kt
fun main(args: Array<String>) {
    val parser = ArgParser("openrouter-auto")
    parser.subcommands(SetupCommand(), ModelsCommand(), AddCommand(), TestCommand(), ChatCommand())
    parser.parse(args)
}
```

### Verification

```bash
./gradlew :cli:run --args="models --free --limit 5"
./gradlew :cli:run --args="chat openai/gpt-4o 'Hello world' --stream"
```

---

## Phase 10: Sample Apps

**Goal:** Working demo apps showing SDK usage with both XML and Compose.

**Depends on:** Phases 7 + 8

### 10.1: sample-compose (Single Activity, Compose Navigation)

| Screen | Components Used | SDK Methods |
|---|---|---|
| API Key Input | `TextField`, `Button` | `Builder.build()` |
| Model Browser | `ModelSelector`, filter chips | `getModels()`, `filterModels()` |
| Chat | `LazyColumn`, streaming text | `chat()`, `streamChat()` (collect Flow) |
| Cost Estimator | `CostEstimator` component | `calculateCost()` |

```kotlin
// No DI framework — direct Builder usage
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val client = remember { OpenRouterAuto.Builder(apiKey = savedKey)
                .storageAdapter(SharedPrefsStorage(applicationContext))
                .build() }
            LaunchedEffect(Unit) { client.initialize() }
            // Navigation...
        }
    }
}
```

### 10.2: sample-xml (Multi-Activity, ViewModels)

| Activity | Layout | SDK Methods |
|---|---|---|
| ApiKeyActivity | `EditText` + `Button` | `Builder.build()` |
| ModelListActivity | `RecyclerView` | `getModels()`, `filterModels()` |
| ChatActivity | `EditText` + `RecyclerView` | `chat()`, `streamChat()` → `asLiveData()` |

```kotlin
// Application class for SDK lifecycle
class App : Application() {
    lateinit var openRouter: OpenRouterAuto
    fun initSdk(apiKey: String) {
        openRouter = OpenRouterAuto.Builder(apiKey)
            .storageAdapter(SharedPrefsStorage(this))
            .build()
    }
}

// ViewModel converts Flow to LiveData
class ChatViewModel(private val sdk: OpenRouterAuto) : ViewModel() {
    val streamedText = MutableLiveData<String>()
    fun sendMessage(model: String, prompt: String) {
        viewModelScope.launch {
            sdk.streamChat(ChatRequest(model, listOf(ChatMessage("user", JsonPrimitive(prompt)))))
                .collect { chunk -> /* update streamedText */ }
        }
    }
}
```

### Verification

```bash
./gradlew :sample-compose:installDebug
./gradlew :sample-xml:installDebug
# Both apps launch and display model list
```

---

## Phase 11: Testing

**Goal:** 50+ unit tests matching the original project's test coverage.

**Ongoing from:** Phase 2

### 11.1: Test Framework

| Dependency | Purpose |
|---|---|
| JUnit 5 | Test runner, assertions |
| MockK | Kotlin-first mocking |
| Ktor MockEngine | HTTP request/response mocking |
| Turbine | `Flow` testing (assert emissions) |
| kotlinx-coroutines-test | `runTest`, `TestDispatcher` |

### 11.2: Test Files

| Test File | Coverage | Target Tests |
|---|---|---|
| `CostTest.kt` | calculateCost, estimateTokens, formatCost, getPriceTier, isFreeModel, compareModelCosts, monthlyEstimate | 8+ |
| `ErrorsTest.kt` | mapHttpError (all status codes), retryable flag, format(), retry delay | 8+ |
| `ParametersTest.kt` | validate all 13 params, range violations, unknown params, platform params, mergeWithDefaults, sanitize | 10+ |
| `StorageTest.kt` | MemoryStorage CRUD, API key stripping, FileStorage path traversal, concurrent access | 6+ |
| `StreamAccumulatorTest.kt` | push chunks, accumulate content/reasoning/tool_calls, toResponse() | 5+ |
| `OpenRouterAutoTest.kt` | Builder validation, initialize, fetchModels, chat, streamChat, addModel, removeModel, filterModels, events | 15+ |
| **Total** | | **50+** |

### 11.3: Test Utilities

```kotlin
// TestFactory.kt
object TestFactory {
    fun makeModel(
        id: String = "test/model",
        promptPrice: String = "0.001",
        completionPrice: String = "0.002",
        contextLength: Int = 4096,
        supportedParameters: List<String> = listOf("temperature", "top_p", "max_tokens")
    ): OpenRouterModel

    fun makeChatResponse(content: String = "Hello!"): ChatResponse

    fun makeStreamChunk(content: String = "Hel", index: Int = 0): StreamChunk

    fun makeMockEngine(handlers: MockRequestHandler): MockEngine
}
```

### 11.4: Instrumented Tests (Phase 11b — optional)

- `SharedPrefsStorageInstrumentedTest` — run on real Android device/emulator
- `LiveE2ETest` — requires real API key from CI secret, validates full flow

### Verification

```bash
./gradlew :core:test
# All 50+ tests pass
```

---

## Phase 12: CI/CD & Publishing

**Goal:** Automated build, test, and publish pipeline.

### 12.1: CI Workflow (`.github/workflows/ci.yml`)

```yaml
name: CI
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :core:test
      - run: ./gradlew :core:assembleRelease
      - run: ./gradlew :compose-ui:assembleRelease
      - uses: actions/upload-artifact@v4
        with: { name: test-reports, path: '**/build/reports/tests/' }
```

### 12.2: Publish Workflow (`.github/workflows/publish.yml`)

```yaml
name: Publish
on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: ./gradlew :core:test
      - run: ./gradlew :core:publishReleasePublicationToMavenCentralRepository
        env:
          MAVEN_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.GPG_SIGNING_PASSWORD }}
      - run: ./gradlew :compose-ui:publishReleasePublicationToMavenCentralRepository
      - run: ./gradlew :core:publishReleasePublicationToGitHubPackagesRepository
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 12.3: Maven Coordinates

| Module | Group ID | Artifact ID | 
|---|---|---|
| Core SDK | `io.openrouter.android` | `auto-core` |
| Compose UI | `io.openrouter.android` | `auto-compose` |
| CLI | `io.openrouter.android` | `auto-cli` |

### 12.4: Consumer Gradle

```kotlin
// build.gradle.kts (consumer app)
dependencies {
    implementation("io.openrouter.android:auto-core:1.0.0")

    // Optional: Compose UI components
    implementation("io.openrouter.android:auto-compose:1.0.0")
}
```

### Verification

```bash
./gradlew publishToMavenLocal
# Artifacts appear in ~/.m2/repository/io/openrouter/android/
```

---

## Phase 13: Documentation

**Goal:** Comprehensive docs for developer adoption.

### 13.1: Files

| File | Content |
|---|---|
| `README.md` | Badges, installation (3 sources), quick start, feature list, API overview, samples link |
| `QUICKSTART.md` | Step-by-step: setup → initialize → chat → stream → cost, with XML and Compose snippets |
| `CONTRIBUTING.md` | Dev setup, module structure, testing, code style, PR guidelines |
| `CHANGELOG.md` | v1.0.0 initial release notes |
| `LICENSE` | MIT (matching original project) |

### 13.2: README Quick Start Preview

```kotlin
// 1. Add dependency
implementation("io.openrouter.android:auto-core:1.0.0")

// 2. Initialize
val client = OpenRouterAuto.Builder(apiKey = "sk-or-...")
    .storageAdapter(SharedPrefsStorage(context))
    .build()
client.initialize()

// 3. Chat
val response = client.chat(ChatRequest(
    model = "openai/gpt-4o",
    messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hello!")))
))
println(response.choices[0].message.content)

// 4. Stream
client.streamChat(request).collect { chunk ->
    print(chunk.choices?.firstOrNull()?.delta?.content ?: "")
}

// 5. Cost
val cost = client.calculateCost("openai/gpt-4o", promptTokens = 1000, completionTokens = 500)
println("Total: ${formatCost(cost.totalCost)}")
```

---

## Dependency Graph

```
Phase 1 (Scaffolding)
  │
  ├──── Phase 2 (Types) ──────────┐
  ├──── Phase 3 (Errors)          │
  ├──── Phase 4 (Params)          ├──── Phase 6 (Storage) ────┐
  └──── Phase 5 (Cost) ───────────┘                           │
                                                               │
                                              Phase 7 (Core SDK) ──────┐
                                                │                      │
                                      ┌─────────┴─────────┐           │
                                      │                    │           │
                               Phase 8 (Compose)    Phase 9 (CLI)     │
                                      │                    │           │
                                      └────────┬───────────┘           │
                                               │                      │
                                        Phase 10 (Samples)            │
                                               │                      │
                                    ┌──────────┼──────────┐            │
                                    │          │          │            │
                             Phase 11    Phase 12    Phase 13          │
                             (Tests)     (CI/CD)     (Docs)           │
                                                                      │
                                              ← Phase 11 runs ongoing ┘
```

**Parallelism:**
- Phases 2, 3, 4, 5 → all **parallel** after Phase 1
- Phase 6 → after Phase 2 (needs Types)
- Phase 7 → after Phases 2–6
- Phases 8, 9 → **parallel** after Phase 7
- Phase 10 → after Phases 7 + 8
- Phases 11, 12, 13 → **parallel** after Phase 10
- Phase 11 (testing) runs continuously from Phase 2 onward

---

## Feature Parity Matrix

Every feature from `openrouter-auto` mapped to the Android implementation:

| Feature | TS/Python/Go/Rust | Android (Kotlin) | Notes |
|---|---|---|---|
| Model discovery (345+ models) | ✅ | ✅ `fetchModels()` | GET `/api/v1/models` |
| Model filtering (price, provider, modality, context) | ✅ | ✅ `filterModels(ModelFilter)` | All criteria supported |
| Chat completions | ✅ | ✅ `suspend fun chat()` | Coroutine-based |
| SSE streaming | ✅ | ✅ `Flow<StreamChunk>` | Cold Flow, Ktor SSE |
| Stream accumulator | ✅ | ✅ `StreamAccumulator` | Content + reasoning + tool calls |
| Cost estimation | ✅ | ✅ `calculateCost()` | BigDecimal precision |
| Token estimation | ✅ | ✅ `estimateTokens()` | ⌈chars/4⌉ |
| Price tier classification | ✅ | ✅ `getPriceTier()` | Free/Cheap/Moderate/Expensive |
| Monthly cost projection | ✅ | ✅ `calculateMonthlyEstimate()` | daily × 30 |
| Model comparison | ✅ | ✅ `compareModelCosts()` | Sorted by cost |
| Parameter validation | ✅ | ✅ `validateParameters()` | 13 params, type+range checks |
| Parameter defaults | ✅ | ✅ `mergeWithDefaults()` | Registry-driven |
| Platform params bypass | ✅ | ✅ `PLATFORM_PARAMS` set | 23 always-allowed |
| Error mapping (HTTP → typed) | ✅ | ✅ `mapHttpError()` | Registry-driven |
| Retry delay (exponential backoff) | ✅ | ✅ `getRetryDelay()` | Capped at 30s |
| Retryable error detection | ✅ | ✅ `isRetryable()` | 5 retryable codes |
| Error tips from registry | ✅ | ✅ `ORAError.tip` | Actionable messages |
| Model add/remove/update | ✅ | ✅ Full CRUD | With validation + test |
| Model testing (probe) | ✅ | ✅ `testModel()` | Returns success + responseTime |
| Bulk model testing | ✅ | ✅ `testAllModels()` | Parallel test all configs |
| Model availability check | ✅ | ✅ `checkModelAvailability()` | Quick probe |
| Auto-fetch models | ✅ | ✅ Coroutine timer | Configurable interval |
| Cache with TTL | ✅ | ✅ `cacheDuration` | Pluggable storage |
| Memory storage | ✅ | ✅ `MemoryStorage` | ConcurrentHashMap |
| File storage | ✅ | ✅ `FileStorage` | Path traversal prevention |
| Browser storage | ✅ (localStorage) | ✅ `SharedPrefsStorage` | Android equivalent |
| Custom storage adapter | ✅ | ✅ `StorageAdapter` interface | Inject your own |
| API key security | ✅ | ✅ Per-request header | Never in default headers |
| Storage key stripping | ✅ | ✅ SharedPrefs adapter | Never persist secrets |
| SSRF prevention | ✅ | ✅ URL scheme validation | https/http only |
| Event system | ✅ | ✅ `on()` + `ORAEvent` | SharedFlow-based |
| Global error callback | ✅ | ✅ `onError` | Builder config |
| Global event callback | ✅ | ✅ `onEvent` | Builder config |
| Tool calling | ✅ | ✅ `ToolDefinition` | Full support |
| Web search | ✅ | ✅ `createWebSearchTool()` | Helper functions |
| Reasoning models | ✅ | ✅ `ReasoningConfig` | R1-style reasoning |
| Vision / multimodal | ✅ | ✅ `ContentPart` sealed class | Image + audio |
| Provider routing | ✅ | ✅ `ProviderPreferences` | Order, sort, fallbacks |
| CLI tool | ✅ (Python) | ✅ (Kotlin JVM) | setup/models/add/test/chat |
| UI components | ✅ (React) | ✅ (Compose, optional) | ModelSelector, CostEstimator, etc. |

---

## Reference Files

Source files from `openrouter-auto/` to reference during implementation:

| Source File | Maps To | Purpose |
|---|---|---|
| `packages/registry/cost.json` | `core/src/main/res/raw/cost.json` | Copy verbatim |
| `packages/registry/errors.json` | `core/src/main/res/raw/errors.json` | Copy verbatim |
| `packages/registry/parameters.json` | `core/src/main/res/raw/parameters.json` | Copy verbatim |
| `packages/registry/platform-params.json` | `core/src/main/res/raw/platform_params.json` | Copy verbatim (rename hyphen) |
| `packages/core/src/types.ts` | `Types.kt` | Data model shapes |
| `packages/core/src/sdk.ts` | `OpenRouterAuto.kt` | Public API surface |
| `packages/core/src/cost.ts` | `Cost.kt` | Cost functions |
| `packages/core/src/errors.ts` | `Errors.kt` | Error mapping |
| `packages/core/src/parameters.ts` | `Parameters.kt` | Validation logic |
| `packages/core/src/storage.ts` | `Storage.kt` | Adapter pattern |
| `packages/python/openrouter_auto/cli.py` | `cli/Main.kt` | CLI commands |
| `packages/react/src/components/*.tsx` | `compose-ui/*.kt` | UI components |
| `packages/core/__tests__/*.test.ts` | `core/test/*.kt` | Test patterns |

---

## Scope Boundaries

### In Scope

- All features from the original `openrouter-auto` project (see Feature Parity Matrix)
- Pure Kotlin — **no Java** anywhere
- Android API 24+ with KMP-ready project structure
- Ktor HTTP client (CIO default, OkHttp engine optional)
- kotlinx.serialization (compile-time, no reflection)
- Kotlin Coroutines + Flow (suspend for one-shot, Flow for streaming)
- Pluggable storage: Memory + SharedPrefs + File
- Optional Compose UI module (`:compose-ui`)
- CLI tool (`:cli`, Kotlin JVM)
- Sample apps for both XML and Compose
- Publishing to Maven Central + JitPack + GitHub Packages
- CI/CD with GitHub Actions
- MIT License

### Explicitly Out of Scope

| Exclusion | Rationale |
|---|---|
| iOS / Desktop KMP targets | Future work — project structure supports it |
| Java API / callback wrappers | Pure Kotlin decision — no Java consumers |
| Hilt / Koin integration modules | DI-agnostic — users wire their own |
| Dokka API documentation site | Future enhancement |
| Live E2E tests in CI | Requires API key — manual only |
| DataStore migration | SharedPrefs sufficient for v1, DataStore can be added as 4th adapter |
