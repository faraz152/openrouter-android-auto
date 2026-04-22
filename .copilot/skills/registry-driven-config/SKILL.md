# Skill: Registry-Driven Configuration

> Specialized skill for working with the JSON registry files that drive cost estimation, error handling, and parameter validation.

## When This Skill Applies

- Updating or syncing registry JSON files from the reference project
- Implementing code that reads registry data (Cost.kt, Errors.kt, Parameters.kt)
- Adding new error codes, parameters, or cost tiers
- Debugging mismatches between Android SDK behavior and reference project behavior

## Registry Files

The registry files are the **source of truth** for SDK behavior. They live in two places:

1. **Reference copy**: `registry/` at project root (for human reference)
2. **Embedded copy**: `core/src/main/res/raw/` (compiled into the AAR)

### File Mapping

| Registry Source                          | Android Resource                             | Purpose                                               |
| ---------------------------------------- | -------------------------------------------- | ----------------------------------------------------- |
| `packages/registry/cost.json`            | `core/src/main/res/raw/cost.json`            | Price tier thresholds, token estimation config        |
| `packages/registry/errors.json`          | `core/src/main/res/raw/errors.json`          | HTTP → error code map, messages, tips, retryable list |
| `packages/registry/parameters.json`      | `core/src/main/res/raw/parameters.json`      | 13 parameter definitions with type/min/max/default    |
| `packages/registry/platform-params.json` | `core/src/main/res/raw/platform_params.json` | 23 always-allowed parameter names                     |

**Important**: Android resource files cannot contain hyphens. Rename `platform-params.json` → `platform_params.json`.

## Syncing from Reference Project

1. Read `.copilot/reference-path.local` for the reference project path.
2. Copy the 4 JSON files from `<ref>/packages/registry/` to both `registry/` and `core/src/main/res/raw/`.
3. Rename `platform-params.json` → `platform_params.json` for Android.
4. Verify JSON validity.

## Registry Schemas

### cost.json

```json
{
  "price_tiers": {
    "free": { "max_avg_price": 0 },
    "cheap": { "max_avg_price": 0.0001 },
    "moderate": { "max_avg_price": 0.01 },
    "expensive": { "max_avg_price": null }
  },
  "token_estimate_chars_per_token": 4,
  "message_overhead_tokens": 4
}
```

**Usage in Cost.kt:**

- `token_estimate_chars_per_token` → divisor for `estimateTokens()`: `ceil(text.length / 4)`
- `message_overhead_tokens` → added per message in `calculateChatCost()`
- `price_tiers` → thresholds for `getPriceTier()`:
  - avg price = 0 → "free"
  - avg price < 0.0001 → "cheap"
  - avg price < 0.01 → "moderate"
  - avg price ≥ 0.01 → "expensive"

### errors.json

```json
{
  "code_map": { "401": "INVALID_API_KEY", "429": "RATE_LIMITED", ... },
  "messages": { "INVALID_API_KEY": "Invalid or missing API key...", ... },
  "tips": { "INVALID_API_KEY": "Double-check your OpenRouter API key...", ... },
  "retryable": ["RATE_LIMITED", "PROVIDER_ERROR", "NETWORK_ERROR", "TIMEOUT", "MODEL_UNAVAILABLE"]
}
```

**Usage in Errors.kt:**

- `code_map` → `mapHttpError(statusCode)` lookup
- `messages` → default message for each error code
- `tips` → actionable hint for `ORAError.tip`
- `retryable` → `isRetryable()` check

### parameters.json

```json
{
  "temperature": { "type": "number", "description": "...", "default": 1.0, "min": 0, "max": 2 },
  "top_p": { "type": "number", "description": "...", "default": 1.0, "min": 0, "max": 1 },
  ...
}
```

**13 parameters defined**: temperature, top_p, top_k, max_tokens, max_completion_tokens, frequency_penalty, presence_penalty, repetition_penalty, min_p, top_a, seed, stop, stream

**Usage in Parameters.kt:**

- Type validation: "number" → Double, "integer" → Int, "boolean" → Boolean, "array" → List
- Range validation: check min/max bounds
- Default values: populate `mergeWithDefaults()`

### platform-params.json

```json
["model", "messages", "stream", "stream_options", "tools", "tool_choice", ...]
```

**23 always-allowed parameters** that bypass model-specific validation.

**Usage in Parameters.kt:**

- If a parameter is in this set, it's always valid regardless of model's `supported_parameters`

## Loading Registry in Kotlin

### Android Context-based Loading

```kotlin
internal class RegistryLoader(private val context: Context) {
    fun loadCostConfig(): CostConfig {
        val json = context.resources.openRawResource(R.raw.cost)
            .bufferedReader().use { it.readText() }
        return Json.decodeFromString(json)
    }

    fun loadErrorsConfig(): ErrorsConfig {
        val json = context.resources.openRawResource(R.raw.errors)
            .bufferedReader().use { it.readText() }
        return Json.decodeFromString(json)
    }
    // ... similar for parameters and platform_params
}
```

### JVM/CLI Loading (no Android Context)

```kotlin
internal object RegistryLoaderJvm {
    fun loadCostConfig(): CostConfig {
        val json = this::class.java.classLoader
            ?.getResourceAsStream("cost.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("cost.json not found in classpath")
        return Json.decodeFromString(json)
    }
}
```

## Registry Data Classes

```kotlin
@Serializable
internal data class CostConfig(
    @SerialName("price_tiers") val priceTiers: Map<String, PriceTierConfig>,
    @SerialName("token_estimate_chars_per_token") val tokenEstimateCharsPerToken: Int,
    @SerialName("message_overhead_tokens") val messageOverheadTokens: Int
)

@Serializable
internal data class PriceTierConfig(
    @SerialName("max_avg_price") val maxAvgPrice: Double?
)

@Serializable
internal data class ErrorsConfig(
    @SerialName("code_map") val codeMap: Map<String, String>,
    val messages: Map<String, String>,
    val tips: Map<String, String>,
    val retryable: List<String>
)

@Serializable
internal data class ParameterDef(
    val type: String,
    val description: String,
    val default: JsonElement? = null,
    val min: Double? = null,
    val max: Double? = null,
    val enum: List<JsonElement>? = null
)
```

## Verification

After syncing registry files, verify:

1. `cost.json` has all 4 price tiers (free, cheap, moderate, expensive)
2. `errors.json` has all 10 error codes in `code_map`
3. `parameters.json` has all 13 parameter definitions
4. `platform_params.json` has all 23 platform parameters
5. Unit tests for each registry-driven function still pass
