# Skill: Kotlin Android SDK Development

> Specialized skill for building the `:core` Android Library module with Ktor, kotlinx.serialization, and Kotlin Coroutines.

## When This Skill Applies

- Creating or modifying Kotlin source files in `core/src/main/kotlin/`
- Implementing SDK public API methods (`OpenRouterAuto`, Builder)
- Working with `@Serializable` data classes (`Types.kt`)
- Implementing HTTP client logic (`internal/HttpEngine.kt`)
- Any work on the `:core` module

## Reference Project Lookup

**ALWAYS** before implementing any SDK feature:

1. Read `.copilot/reference-path.local` to get the reference project path.
2. Open the corresponding TypeScript file (see mapping below).
3. Ensure your Kotlin implementation mirrors the TypeScript behavior exactly.

| Kotlin Target       | TypeScript Reference              |
| ------------------- | --------------------------------- |
| `Types.kt`          | `packages/core/src/types.ts`      |
| `OpenRouterAuto.kt` | `packages/core/src/sdk.ts`        |
| `Cost.kt`           | `packages/core/src/cost.ts`       |
| `Errors.kt`         | `packages/core/src/errors.ts`     |
| `Parameters.kt`     | `packages/core/src/parameters.ts` |
| `Storage.kt`        | `packages/core/src/storage.ts`    |

## Kotlin-Specific Patterns

### Data Classes with kotlinx.serialization

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
```

**Key rules:**

- Every API-facing field with underscores gets `@SerialName("snake_case")`
- Optional fields default to `null`
- Use `JsonElement` for polymorphic fields (tool_choice, response_format)
- Configure shared `Json` instance:

```kotlin
internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}
```

### Builder Pattern for SDK Client

```kotlin
class OpenRouterAuto private constructor(private val config: Config) {

    class Builder(private val apiKey: String) {
        private var baseUrl = "https://openrouter.ai/api/v1"
        private var storage: StorageAdapter = MemoryStorage()
        // ... other fields with defaults

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun storageAdapter(adapter: StorageAdapter) = apply { this.storage = adapter }

        fun build(): OpenRouterAuto {
            require(apiKey.isNotBlank()) { "API key must not be blank" }
            val parsedUrl = URL(baseUrl)
            require(parsedUrl.protocol in listOf("https", "http")) {
                "Unsupported baseUrl scheme: ${parsedUrl.protocol}"
            }
            return OpenRouterAuto(Config(apiKey, baseUrl, storage, ...))
        }
    }
}
```

### Ktor HTTP Client Setup

```kotlin
internal fun createHttpClient(config: Config): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
}
```

- Authorization header set **per-request**, not in defaultRequest
- HttpClient created once at build time, reused across all calls
- CIO is the default engine; OkHttp is optional

### Coroutines & Flow

```kotlin
// One-shot call
suspend fun chat(request: ChatRequest): ChatResponse =
    withContext(Dispatchers.IO) {
        val response = httpClient.post("/chat/completions") {
            header("Authorization", "Bearer ${config.apiKey}")
            setBody(request)
        }
        response.body<ChatResponse>()
    }

// Streaming call
fun streamChat(request: ChatRequest): Flow<StreamChunk> = flow {
    // Cold flow — only executes when collected
    val streamRequest = request.copy(stream = true)
    httpClient.preparePost("/chat/completions") {
        header("Authorization", "Bearer ${config.apiKey}")
        setBody(streamRequest)
    }.execute { response ->
        val channel = response.bodyAsChannel()
        parseSSEStream(channel).collect { chunk -> emit(chunk) }
    }
}.flowOn(Dispatchers.IO)
```

### BigDecimal for Cost Calculations

```kotlin
fun calculateCost(model: OpenRouterModel, promptTokens: Int, completionTokens: Int): CostEstimate {
    val promptPrice = model.pricing.prompt.toBigDecimal()
    val completionPrice = model.pricing.completion.toBigDecimal()
    val promptCost = promptPrice * promptTokens.toBigDecimal() / 1000.toBigDecimal()
    val completionCost = completionPrice * completionTokens.toBigDecimal() / 1000.toBigDecimal()
    val totalCost = promptCost + completionCost
    return CostEstimate(
        promptCost = promptCost.toDouble(),
        completionCost = completionCost.toDouble(),
        totalCost = totalCost.toDouble()
    )
}
```

## Common Pitfalls

1. **Don't use Gson/Moshi** — only kotlinx.serialization.
2. **Don't use `GlobalScope`** — scope to the SDK client's `CoroutineScope`.
3. **Don't default optional fields to empty string** — use `null`.
4. **Don't store API key in SharedPreferences** — per-request header only.
5. **Don't create HttpClient per request** — reuse a single instance.
6. **Don't block the main thread** — all network/disk ops in `Dispatchers.IO`.
7. **Don't use `runBlocking`** in library code — only in CLI or tests.

## Verification Checklist

- [ ] All data classes match TypeScript interfaces field-by-field
- [ ] JSON round-trip works (serialize → deserialize → equals original)
- [ ] `@SerialName` used for all snake_case API fields
- [ ] Optional fields have `= null` defaults
- [ ] HttpClient created once in Builder.build()
- [ ] Authorization header set per-request
- [ ] All public functions have unit tests
