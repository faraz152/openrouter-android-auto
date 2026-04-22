---
applyTo: "**/*.kt"
---

# Kotlin Source Instructions — OpenRouter Android Auto SDK

## Language & Style

- **Kotlin only** — never create Java files.
- Target **Kotlin 2.0+** language features.
- Use `data class` for value types, `sealed class` for union types, `object` for singletons.
- Prefer `val` over `var`. Mutable state only inside `internal` classes.
- Use `internal` visibility for implementation details that should not leak to SDK consumers.

## Coroutines & Flow

- One-shot operations: `suspend fun`
- Streaming operations: `fun streamX(): Flow<T>` (cold Flow)
- Use `withContext(Dispatchers.IO)` for network/disk operations inside suspend functions.
- Never use `GlobalScope`. Always scope coroutines to a lifecycle (SDK client's `CoroutineScope`).
- Use `flow { }` builder for SSE stream parsing, not `channelFlow` unless fan-out is needed.

## kotlinx.serialization

- Every API data class must be annotated with `@Serializable`.
- Use `@SerialName("snake_case")` for fields that differ from Kotlin camelCase.
- Use `JsonElement` for polymorphic/untyped JSON fields (e.g., `toolChoice`, `responseFormat`).
- Configure Json instance with: `ignoreUnknownKeys = true`, `isLenient = true`, `encodeDefaults = false`.
- For optional fields, use `= null` default, never empty string or empty list unless API guarantees it.

## Ktor HTTP Client

- Create `HttpClient` via factory function in `internal/HttpEngine.kt`.
- Install `ContentNegotiation` with `kotlinx.serialization`.
- Set `Authorization` header per-request via interceptor — never as default header.
- Set `HTTP-Referer` and `X-Title` headers per-request.
- For streaming: use `preparePost` + `execute` to get `HttpResponse` with streaming body.

## File Organization

```
core/src/main/kotlin/io/openrouter/android/auto/
├── OpenRouterAuto.kt       ← Public SDK client + Builder
├── Types.kt                ← All @Serializable data classes
├── Cost.kt                 ← Cost estimation functions
├── Errors.kt               ← Error mapping, ORAError class
├── Parameters.kt           ← Validation, defaults, sanitize
├── Storage.kt              ← StorageAdapter interface + implementations
├── Events.kt               ← Event types + EventBus
└── internal/
    ├── HttpEngine.kt       ← Ktor HttpClient factory
    ├── StreamParser.kt     ← SSE line parser → Flow<StreamChunk>
    └── StreamAccumulator.kt ← Chunk reassembly → ChatResponse
```
