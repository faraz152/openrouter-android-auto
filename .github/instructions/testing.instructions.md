---
applyTo: "**/test/**/*.kt"
---

# Testing Instructions — OpenRouter Android Auto SDK

## Test Framework Stack

| Library                 | Purpose                                                         |
| ----------------------- | --------------------------------------------------------------- |
| JUnit 5                 | Test runner, assertions, `@Test`, `@Nested`, `@BeforeEach`      |
| MockK                   | Kotlin-first mocking: `mockk<T>()`, `coEvery`, `coVerify`       |
| Ktor MockEngine         | HTTP request/response stubbing without real network             |
| Turbine                 | `Flow<T>` testing: `flow.test { awaitItem(), awaitComplete() }` |
| kotlinx-coroutines-test | `runTest`, `TestDispatcher`, `advanceUntilIdle()`               |

## Test File Naming

- Test file mirrors source file: `Cost.kt` → `CostTest.kt`
- Use `@Nested` inner classes to group related test scenarios
- Test method naming: `` `descriptive sentence with backticks` ``

## Test Patterns

### HTTP Mocking with Ktor MockEngine

```kotlin
val mockEngine = MockEngine { request ->
    when {
        request.url.encodedPath == "/models" ->
            respond(modelsJsonString, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        request.url.encodedPath == "/chat/completions" ->
            respond(chatResponseJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        else ->
            respondError(HttpStatusCode.NotFound)
    }
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun `streamChat emits chunks then completes`() = runTest {
    sdk.streamChat(request).test {
        val chunk1 = awaitItem()
        assertEquals("Hel", chunk1.choices?.first()?.delta?.content)
        val chunk2 = awaitItem()
        assertEquals("lo!", chunk2.choices?.first()?.delta?.content)
        awaitComplete()
    }
}
```

### Test Factory (TestFactory.kt)

Always use `TestFactory` helpers to create test data:

```kotlin
TestFactory.makeModel(id = "test/model", promptPrice = "0.001")
TestFactory.makeChatResponse(content = "Hello!")
TestFactory.makeStreamChunk(content = "Hel", index = 0)
```

## Coverage Targets

| Module            | Min Tests | Focus Areas                                                          |
| ----------------- | --------- | -------------------------------------------------------------------- |
| Cost              | 8+        | calculateCost, estimateTokens, formatCost, getPriceTier, isFreeModel |
| Errors            | 8+        | mapHttpError for all status codes, retryable detection, format()     |
| Parameters        | 10+       | All 13 params, range violations, platform params bypass              |
| Storage           | 6+        | CRUD, API key stripping, path traversal rejection                    |
| StreamAccumulator | 5+        | Content, reasoning, tool_calls assembly                              |
| OpenRouterAuto    | 15+       | Builder, init, chat, stream, addModel, filterModels, events          |

## Reference Tests

Mirror test scenarios from the TypeScript project. Read the reference path from `.copilot/reference-path.local` and check `packages/core/__tests__/` for the original test files.
