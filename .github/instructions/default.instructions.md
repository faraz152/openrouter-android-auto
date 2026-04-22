---
applyTo: "**"
---

# Default Instructions — OpenRouter Android Auto SDK

## Reference Project Access

Before implementing or modifying any SDK functionality, read the reference path from `.copilot/reference-path.local` and consult the corresponding source file in the `openrouter-auto` TypeScript project to ensure feature parity.

**NEVER** hardcode the reference project path in any committed file. The path is developer-local.

## Module Boundaries

- `:core` — Zero Android UI dependencies. Pure SDK logic: types, HTTP, streaming, cost, errors, params, storage.
- `:compose-ui` — Depends on `:core`. Material3 Compose components only. No business logic.
- `:cli` — Depends on `:core` (JVM target). Pure JVM, no Android APIs.
- `:sample-compose` — Depends on `:core` + `:compose-ui`. Demo app only.
- `:sample-xml` — Depends on `:core`. Demo app only.

## Code Quality Rules

1. Every public API function must have a corresponding unit test.
2. All `@Serializable` data classes must round-trip correctly (serialize → deserialize → equal).
3. Registry JSON parsing must happen at SDK initialization, not per-call.
4. Ktor `HttpClient` must be created once and reused — never per-request.
5. `Flow` emissions must happen on `Dispatchers.IO` for network operations.
6. All suspend functions must be cancellation-safe (respect `isActive`).
7. Use `require()` / `check()` for preconditions, not if-throw.

## Error Handling Pattern

```kotlin
// DO: Use typed errors
throw ORAError(ORAErrorCode.MODEL_NOT_FOUND, "Model '$modelId' not found", retryable = false)

// DON'T: Throw generic exceptions
throw IllegalStateException("Model not found")
```

## Serialization Pattern

```kotlin
// DO: Use @SerialName for API field mapping
@Serializable
data class ModelPricing(
    val prompt: String,
    val completion: String,
    val image: String? = null,
    val request: String? = null
)

// DON'T: Use @JsonProperty, @Json, or other non-kotlinx annotations
```

## Testing Pattern

```kotlin
// DO: Use Ktor MockEngine for HTTP tests
val mockEngine = MockEngine { request ->
    respond(content = jsonResponse, status = HttpStatusCode.OK)
}

// DO: Use Turbine for Flow tests
sdk.streamChat(request).test {
    val first = awaitItem()
    assertEquals("Hello", first.choices?.first()?.delta?.content)
    awaitComplete()
}

// DON'T: Use real HTTP calls in unit tests
```
