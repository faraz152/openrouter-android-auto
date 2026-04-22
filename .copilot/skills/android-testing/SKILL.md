# Skill: Android SDK Testing

> Specialized skill for testing the OpenRouter Android SDK using JUnit 5, MockK, Turbine, Ktor MockEngine, and kotlinx-coroutines-test.

## When This Skill Applies

- Writing unit tests for any module in `core/src/test/`
- Testing streaming/Flow behavior
- Mocking HTTP responses
- Testing coroutine-based code
- Setting up test fixtures and factories
- Debugging test failures

## Test Framework Stack

| Library         | Version | Purpose                                                 |
| --------------- | ------- | ------------------------------------------------------- |
| JUnit 5         | 5.10.3  | `@Test`, `@Nested`, `@BeforeEach`, `@ParameterizedTest` |
| MockK           | 1.13.12 | `mockk<T>()`, `coEvery`, `coVerify`, `slot`, `spyk`     |
| Ktor MockEngine | 2.3.12  | HTTP mocking without network                            |
| Turbine         | 1.1.0   | `Flow.test { awaitItem(), awaitComplete() }`            |
| coroutines-test | 1.8.1   | `runTest`, `TestDispatcher`                             |

## Test File Structure

```
core/src/test/kotlin/io/openrouter/android/auto/
├── CostTest.kt              ← 8+ tests
├── ErrorsTest.kt            ← 8+ tests
├── ParametersTest.kt        ← 10+ tests
├── StorageTest.kt           ← 6+ tests
├── StreamAccumulatorTest.kt ← 5+ tests
├── OpenRouterAutoTest.kt    ← 15+ tests
└── TestFactory.kt           ← Shared test data builders
```

## TestFactory.kt — Test Data Builders

```kotlin
object TestFactory {

    fun makeModel(
        id: String = "test/model",
        name: String = "Test Model",
        promptPrice: String = "0.001",
        completionPrice: String = "0.002",
        contextLength: Int = 4096,
        supportedParameters: List<String> = listOf("temperature", "top_p", "max_tokens"),
        maxCompletionTokens: Int? = null,
        inputModalities: List<String> = listOf("text"),
        outputModalities: List<String> = listOf("text"),
        modality: String = "text->text"
    ): OpenRouterModel = OpenRouterModel(
        id = id,
        name = name,
        pricing = ModelPricing(prompt = promptPrice, completion = completionPrice),
        contextLength = contextLength,
        supportedParameters = supportedParameters,
        architecture = ModelArchitecture(
            modality = modality,
            inputModalities = inputModalities,
            outputModalities = outputModalities
        ),
        topProvider = TopProvider(
            maxCompletionTokens = maxCompletionTokens,
            isModerated = false
        )
    )

    fun makeFreeModel(id: String = "test/free-model"): OpenRouterModel =
        makeModel(id = id, promptPrice = "0", completionPrice = "0")

    fun makeChatResponse(
        content: String = "Hello!",
        model: String = "test/model",
        finishReason: String = "stop",
        promptTokens: Int = 10,
        completionTokens: Int = 5
    ): ChatResponse = ChatResponse(
        id = "gen-test-123",
        model = model,
        created = 1700000000L,
        choices = listOf(
            Choice(
                index = 0,
                message = ChatMessage(role = "assistant", content = JsonPrimitive(content)),
                finishReason = finishReason
            )
        ),
        usage = Usage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = promptTokens + completionTokens
        )
    )

    fun makeStreamChunk(
        content: String? = null,
        reasoning: String? = null,
        finishReason: String? = null,
        index: Int = 0,
        id: String = "gen-test-123",
        model: String = "test/model"
    ): StreamChunk = StreamChunk(
        id = id,
        model = model,
        created = 1700000000L,
        choices = listOf(
            StreamChoice(
                index = index,
                delta = ChatMessage(
                    role = if (content == null && reasoning == null) null else "assistant",
                    content = content?.let { JsonPrimitive(it) },
                    reasoning = reasoning
                ),
                finishReason = finishReason
            )
        )
    )

    fun makeModelsApiResponse(models: List<OpenRouterModel>): String {
        return Json.encodeToString(ModelsResponse(data = models))
    }

    fun makeMockEngine(
        modelsResponse: String? = null,
        chatResponse: String? = null,
        sseResponse: String? = null,
        errorStatus: HttpStatusCode? = null
    ): MockEngine = MockEngine { request ->
        when {
            errorStatus != null ->
                respondError(errorStatus)
            request.url.encodedPath.endsWith("/models") ->
                respond(
                    modelsResponse ?: makeModelsApiResponse(listOf(makeModel())),
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json")
                )
            request.url.encodedPath.endsWith("/chat/completions") -> {
                val body = request.body.toByteArray().decodeToString()
                if (body.contains("\"stream\":true") && sseResponse != null) {
                    respond(sseResponse, HttpStatusCode.OK,
                        headersOf("Content-Type", "text/event-stream"))
                } else {
                    respond(
                        chatResponse ?: Json.encodeToString(makeChatResponse()),
                        HttpStatusCode.OK,
                        headersOf("Content-Type", "application/json")
                    )
                }
            }
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    fun makeSSEBody(chunks: List<StreamChunk>): String {
        val lines = chunks.map { "data: ${Json.encodeToString(it)}" }
        return (lines + "data: [DONE]").joinToString("\n\n") + "\n\n"
    }
}
```

## Test Patterns

### Cost Tests

```kotlin
class CostTest {
    @Test
    fun `calculateCost with known pricing`() {
        val model = TestFactory.makeModel(promptPrice = "0.001", completionPrice = "0.002")
        val cost = calculateCost(model, promptTokens = 1000, completionTokens = 500)
        assertEquals(1.0, cost.promptCost, 0.0001)
        assertEquals(1.0, cost.completionCost, 0.0001)
        assertEquals(2.0, cost.totalCost, 0.0001)
    }

    @Test
    fun `isFreeModel with zero pricing`() {
        assertTrue(isFreeModel(TestFactory.makeFreeModel()))
    }

    @Test
    fun `isFreeModel with colon free suffix`() {
        assertTrue(isFreeModel(TestFactory.makeModel(id = "meta/llama:free")))
    }

    @Test
    fun `estimateTokens ceil division`() {
        assertEquals(3, estimateTokens("Hello World!"))  // 12 chars / 4 = 3
        assertEquals(1, estimateTokens("Hi"))             // 2 chars / 4 = ceil(0.5) = 1
    }
}
```

### Error Tests

```kotlin
class ErrorsTest {
    @Test
    fun `mapHttpError 429 to RATE_LIMITED`() {
        val error = mapHttpError(429, null)
        assertEquals(ORAErrorCode.RATE_LIMITED, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun `retry delay exponential backoff capped`() {
        assertEquals(1000L, getRetryDelay(0))
        assertEquals(2000L, getRetryDelay(1))
        assertEquals(4000L, getRetryDelay(2))
        assertEquals(30000L, getRetryDelay(10))  // capped
    }

    @Test
    fun `body with credit keyword maps to INSUFFICIENT_CREDITS`() {
        val error = mapHttpError(400, """{"error":"Not enough credits"}""")
        assertEquals(ORAErrorCode.INSUFFICIENT_CREDITS, error.code)
    }
}
```

### Stream Accumulator Tests

```kotlin
class StreamAccumulatorTest {
    @Test
    fun `accumulates content from multiple chunks`() {
        val acc = StreamAccumulator()
        acc.push(TestFactory.makeStreamChunk(content = "Hel"))
        acc.push(TestFactory.makeStreamChunk(content = "lo"))
        acc.push(TestFactory.makeStreamChunk(content = "!"))
        acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))

        val response = acc.toResponse()
        assertEquals("Hello!", extractText(response.choices[0].message.content))
        assertEquals("stop", response.choices[0].finishReason)
    }

    @Test
    fun `accumulates reasoning separately`() {
        val acc = StreamAccumulator()
        acc.push(TestFactory.makeStreamChunk(reasoning = "Let me think..."))
        acc.push(TestFactory.makeStreamChunk(content = "The answer is 42."))

        val response = acc.toResponse()
        assertEquals("Let me think...", response.choices[0].message.reasoning)
        assertEquals("The answer is 42.", extractText(response.choices[0].message.content))
    }
}
```

### OpenRouterAuto Integration Tests

```kotlin
class OpenRouterAutoTest {
    @Test
    fun `builder rejects blank API key`() {
        assertThrows<IllegalArgumentException> {
            OpenRouterAuto.Builder(apiKey = "").build()
        }
    }

    @Test
    fun `builder rejects non-http baseUrl`() {
        assertThrows<IllegalArgumentException> {
            OpenRouterAuto.Builder(apiKey = "sk-test")
                .baseUrl("ftp://evil.com")
                .build()
        }
    }

    @Test
    fun `initialize fetches models`() = runTest {
        val engine = TestFactory.makeMockEngine()
        val sdk = createSdkWithEngine(engine)
        sdk.initialize()
        assertTrue(sdk.getModels().isNotEmpty())
    }

    @Test
    fun `filterModels by freeOnly`() = runTest {
        val sdk = createSdkWithModels(listOf(
            TestFactory.makeModel(id = "paid/model", promptPrice = "0.001"),
            TestFactory.makeFreeModel(id = "free/model")
        ))
        val free = sdk.filterModels(ModelFilter(freeOnly = true))
        assertEquals(1, free.size)
        assertEquals("free/model", free[0].id)
    }

    @Test
    fun `streamChat emits chunks via Flow`() = runTest {
        val chunks = listOf(
            TestFactory.makeStreamChunk(content = "Hel"),
            TestFactory.makeStreamChunk(content = "lo!"),
            TestFactory.makeStreamChunk(finishReason = "stop")
        )
        val engine = TestFactory.makeMockEngine(sseResponse = TestFactory.makeSSEBody(chunks))
        val sdk = createSdkWithEngine(engine)

        sdk.streamChat(chatRequest).test {
            assertEquals("Hel", extractText(awaitItem().choices!![0].delta!!.content))
            assertEquals("lo!", extractText(awaitItem().choices!![0].delta!!.content))
            awaitItem() // finish_reason chunk
            awaitComplete()
        }
    }
}
```

## Reference Tests

Always check the TypeScript test files for test scenarios to mirror:

1. Read `.copilot/reference-path.local`
2. Check `packages/core/__tests__/*.test.ts`
3. Port each test scenario to Kotlin/JUnit 5

## Common Testing Mistakes

1. **Don't use `runBlocking` in tests** — use `runTest` from coroutines-test.
2. **Don't forget `awaitComplete()`** in Turbine — tests will hang.
3. **Don't forget `useJUnitPlatform()`** in `build.gradle.kts` — JUnit 5 won't run.
4. **Don't mock final classes without MockK** — use `mockk<T>()` not Mockito.
5. **Don't assert floating point with `assertEquals`** — use `assertEquals(expected, actual, delta)`.
