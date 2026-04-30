package io.openrouter.android.auto

import app.cash.turbine.test
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.openrouter.android.auto.internal.StreamAccumulator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for OpenRouterAuto — SDK client with Ktor MockEngine + Turbine.
 * Covers: initialize, fetchModels, filterModels, addModel, removeModel,
 * updateModelParameters, testModel, chat, streamChat, calculateCost,
 * estimateTokens, savePreferences, getPreferences, events, dispose, Builder.
 */
class OpenRouterAutoTest {

    private lateinit var sdk: OpenRouterAuto

    @BeforeEach
    fun setUp() {
        TestFactory.initRegistries()
    }

    // ─── Builder & SSRF Prevention ───────────────────────────────────────────

    @Nested
    inner class Builder {

        @Test
        fun `builds successfully with valid config`() {
            val engine = TestFactory.makeMockEngine()
            val s = TestFactory.buildSdk(engine)
            assertNotNull(s)
            s.dispose()
        }

        @Test
        fun `invalid baseUrl scheme throws`() {
            val loader = TestFactory::class.java.classLoader!!
            assertThrows(IllegalArgumentException::class.java) {
                OpenRouterAuto.Builder(
                    apiKey = "key",
                    errorsJson = loader.getResourceAsStream("errors.json")!!.bufferedReader().readText(),
                    parametersJson = loader.getResourceAsStream("parameters.json")!!.bufferedReader().readText(),
                    platformParamsJson = loader.getResourceAsStream("platform_params.json")!!.bufferedReader().readText(),
                    costJson = loader.getResourceAsStream("cost.json")!!.bufferedReader().readText()
                ).baseUrl("ftp://evil.example.com").build()
            }
        }
    }

    // ─── fetchModels ─────────────────────────────────────────────────────────

    @Nested
    inner class FetchModels {

        @Test
        fun `fetchModels returns list from API`() = runTest {
            val model = TestFactory.makeModel(id = "openai/gpt-4o")
            val mockEngine = TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(model))
            )
            sdk = TestFactory.buildSdk(mockEngine)
            val result = sdk.fetchModels()
            assertEquals(1, result.size)
            assertEquals("openai/gpt-4o", result[0].id)
        }

        @Test
        fun `fetchModels caches models in storage`() = runTest {
            val model = TestFactory.makeModel()
            val mockEngine = TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(model))
            )
            sdk = TestFactory.buildSdk(mockEngine)
            sdk.fetchModels()
            assertEquals(1, sdk.getModels().size)
        }

        @Test
        fun `fetchModels throws on HTTP error`() = runTest {
            val mockEngine = TestFactory.makeMockEngine(errorStatus = HttpStatusCode.Unauthorized)
            sdk = TestFactory.buildSdk(mockEngine)
            val e = runCatching { sdk.fetchModels() }.exceptionOrNull()
            assertNotNull(e)
            assertTrue(e is ORAError)
        }

        @Test
        fun `getModel returns model by id`() = runTest {
            val model = TestFactory.makeModel(id = "x/y")
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(model))
            ))
            sdk.fetchModels()
            assertNotNull(sdk.getModel("x/y"))
            assertNull(sdk.getModel("nonexistent"))
        }
    }

    // ─── filterModels ─────────────────────────────────────────────────────────

    @Nested
    inner class FilterModels {

        @BeforeEach
        fun loadModels() {
            runBlocking {
            val models = listOf(
                TestFactory.makeModel(id = "openai/gpt-4", promptPrice = "0.03", completionPrice = "0.06"),
                TestFactory.makeFreeModel(id = "meta/llama:free"),
                TestFactory.makeModel(id = "anthropic/claude", promptPrice = "0.001", completionPrice = "0.002",
                    contextLength = 200_000)
            )
                sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                    modelsJson = TestFactory.makeModelsApiResponse(models)
                ))
                sdk.fetchModels()
            }
        }

        @Test
        fun `freeOnly filter returns only free models`() {
            val result = sdk.filterModels(ModelFilter(freeOnly = true))
            assertTrue(result.all { isFreeModel(it) })
            assertEquals(1, result.size)
        }

        @Test
        fun `provider filter returns correct models`() {
            val result = sdk.filterModels(ModelFilter(provider = "openai"))
            assertEquals(1, result.size)
            assertEquals("openai/gpt-4", result[0].id)
        }

        @Test
        fun `search filter matches by id`() {
            val result = sdk.filterModels(ModelFilter(search = "claude"))
            assertEquals(1, result.size)
            assertTrue(result[0].id.contains("claude"))
        }

        @Test
        fun `minContextLength filter excludes short context models`() {
            val result = sdk.filterModels(ModelFilter(minContextLength = 100_000))
            assertEquals(1, result.size)
            assertEquals("anthropic/claude", result[0].id)
        }

        @Test
        fun `excludeModels filter removes specified models`() {
            val result = sdk.filterModels(ModelFilter(excludeModels = listOf("openai/gpt-4")))
            assertFalse(result.any { it.id == "openai/gpt-4" })
        }

        @Test
        fun `no filter returns all models`() {
            assertEquals(3, sdk.filterModels().size)
        }
    }

    // ─── getBestFreeModel ─────────────────────────────────────────────────────

    @Nested
    inner class GetBestFreeModel {

        @Test
        fun `returns free model with largest context`() = runTest {
            val smallFree = TestFactory.makeFreeModel("small").copy(contextLength = 2048)
            val largeFree = TestFactory.makeFreeModel("large").copy(contextLength = 8192)
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(smallFree, largeFree))
            ))
            sdk.fetchModels()
            assertEquals("large", sdk.getBestFreeModel()!!.id)
        }

        @Test
        fun `returns null when no models loaded`() {
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine())
            assertNull(sdk.getBestFreeModel())
        }
    }

    // ─── addModel / removeModel ───────────────────────────────────────────────

    @Nested
    inner class ModelConfig {

        @BeforeEach
        fun loadModel() {
            runBlocking {
                sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                    modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel()))
                ))
                sdk.fetchModels()
            }
        }

        @Test
        fun `addModel stores config`() = runTest {
            val config = sdk.addModel("test/model")
            assertNotNull(config)
            assertEquals("test/model", config.modelId)
            assertNotNull(sdk.getModelConfig("test/model"))
        }

        @Test
        fun `addModel with invalid params throws`() = runTest {
            val result = runCatching { sdk.addModel("test/model", mapOf("temperature" to 99.0)) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ORAError)
        }

        @Test
        fun `addModel unknown model throws MODEL_NOT_FOUND`() = runTest {
            val e = runCatching { sdk.addModel("nonexistent/model") }.exceptionOrNull() as? ORAError
            assertNotNull(e)
            assertEquals(ORAErrorCode.MODEL_NOT_FOUND, e!!.code)
        }

        @Test
        fun `removeModel deletes config`() = runTest {
            sdk.addModel("test/model")
            sdk.removeModel("test/model")
            assertNull(sdk.getModelConfig("test/model"))
        }

        @Test
        fun `getAllModelConfigs returns all added configs`() = runTest {
            sdk.addModel("test/model")
            assertEquals(1, sdk.getAllModelConfigs().size)
        }
    }

    // ─── chat ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Chat {

        @Test
        fun `chat returns ChatResponse with correct content`() = runTest {
            val chatJson = TestFactory.json.encodeToString(
                io.openrouter.android.auto.ChatResponse.serializer(),
                TestFactory.makeChatResponse(content = "Hello from AI")
            )
            val mockEngine = TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel())),
                chatJson = chatJson
            )
            sdk = TestFactory.buildSdk(mockEngine)
            sdk.fetchModels()

            val req = ChatRequest(
                model = "test/model",
                messages = listOf(ChatMessage(role = "user",
                    content = JsonPrimitive("Hi")))
            )
            val response = sdk.chat(req)
            val content = response.choices[0].message.content
            assertTrue(content.toString().contains("Hello from AI"))
        }

        @Test
        fun `chat with 401 response throws ORAError INVALID_API_KEY`() = runTest {
            val mockEngine = TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel())),
                chatErrorStatus = HttpStatusCode.Unauthorized
            )
            sdk = TestFactory.buildSdk(mockEngine)
            sdk.fetchModels()
            val e = runCatching {
                sdk.chat(ChatRequest(model = "test/model",
                    messages = listOf(ChatMessage(role = "user",
                        content = JsonPrimitive("Hi")))))
            }.exceptionOrNull() as? ORAError
            assertNotNull(e)
            assertEquals(ORAErrorCode.INVALID_API_KEY, e!!.code)
        }

        @Test
        fun `chat with unknown model throws MODEL_NOT_FOUND`() = runTest {
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine())
            val e = runCatching {
                sdk.chat(ChatRequest(model = "ghost/model",
                    messages = listOf(ChatMessage(role = "user",
                        content = JsonPrimitive("Hi")))))
            }.exceptionOrNull() as? ORAError
            assertNotNull(e)
            assertEquals(ORAErrorCode.MODEL_NOT_FOUND, e!!.code)
        }
    }

    // ─── streamChat ──────────────────────────────────────────────────────────

    @Nested
    inner class StreamChat {

        @Test
        fun `streamChat emits chunks and completes`() = runTest {
            val chunk1 = TestFactory.makeStreamChunk(content = "Hel")
            val chunk2 = TestFactory.makeStreamChunk(content = "lo!")
            val chunk3 = TestFactory.makeStreamChunk(finishReason = "stop")

            val sseBody = TestFactory.makeSseBody(
                TestFactory.json.encodeToString(StreamChunk.serializer(), chunk1),
                TestFactory.json.encodeToString(StreamChunk.serializer(), chunk2),
                TestFactory.json.encodeToString(StreamChunk.serializer(), chunk3)
            )

            val mockEngine = TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel())),
                sseBody = sseBody
            )
            sdk = TestFactory.buildSdk(mockEngine)
            sdk.fetchModels()

            val request = ChatRequest(
                model = "test/model",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                stream = true
            )

            sdk.streamChat(request).test {
                val c1 = awaitItem()
                assertEquals("Hel", c1.choices?.firstOrNull()?.delta?.content
                    ?.let { (it as? JsonPrimitive)?.content })
                val c2 = awaitItem()
                assertEquals("lo!", c2.choices?.firstOrNull()?.delta?.content
                    ?.let { (it as? JsonPrimitive)?.content })
                awaitItem() // finish chunk
                awaitComplete()
            }
        }

        @Test
        fun `streamChat accumulates to correct final response`() = runTest {
            val chunks = listOf(
                TestFactory.makeStreamChunk(content = "Hello"),
                TestFactory.makeStreamChunk(content = " World"),
                TestFactory.makeStreamChunk(finishReason = "stop")
            )
            val sseBody = TestFactory.makeSseBody(
                *chunks.map { TestFactory.json.encodeToString(StreamChunk.serializer(), it) }.toTypedArray()
            )

            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel())),
                sseBody = sseBody
            ))
            sdk.fetchModels()

            val acc = StreamAccumulator()
            val request = ChatRequest(
                model = "test/model",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                stream = true
            )

            sdk.streamChat(request).collect { acc.push(it) }
            val response = acc.toResponse()
            val text = (response.choices[0].message.content as? JsonPrimitive)?.content
            assertEquals("Hello World", text)
        }
    }

    // ─── StreamAccumulator ───────────────────────────────────────────────────

    @Nested
    inner class StreamAccumulatorTest {

        @Test
        fun `accumulates multiple content deltas`() {
            val acc = StreamAccumulator()
            acc.push(TestFactory.makeStreamChunk(content = "foo"))
            acc.push(TestFactory.makeStreamChunk(content = "bar"))
            val text = (acc.toResponse().choices[0].message.content as? JsonPrimitive)?.content
            assertEquals("foobar", text)
        }

        @Test
        fun `captures finishReason from last chunk`() {
            val acc = StreamAccumulator()
            acc.push(TestFactory.makeStreamChunk(content = "hi"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))
            assertEquals("stop", acc.toResponse().choices[0].finishReason)
        }

        @Test
        fun `captures reasoning content`() {
            val acc = StreamAccumulator()
            acc.push(TestFactory.makeStreamChunk(reasoning = "thinking..."))
            assertNotNull(acc.toResponse().choices[0].message.reasoning)
        }

        @Test
        fun `empty stream produces empty content`() {
            val acc = StreamAccumulator()
            acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))
            assertNull(acc.toResponse().choices[0].message.content)
        }
    }

    // ─── calculateCost / estimateTokens ──────────────────────────────────────

    @Nested
    inner class CostMethods {

        @BeforeEach
        fun loadModel() {
            runBlocking {
                sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                    modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel()))
                ))
                sdk.fetchModels()
            }
        }

        @Test
        fun `calculateCost returns non-zero estimate`() {
            val estimate = sdk.calculateCost("test/model", 1000, 500)
            assertTrue(estimate.totalCost > 0.0)
        }

        @Test
        fun `estimateTokens returns positive count for non-empty text`() {
            assertTrue(sdk.estimateTokens("Hello world") > 0)
        }

        @Test
        fun `calculateCost unknown model throws`() {
            assertThrows(ORAError::class.java) {
                sdk.calculateCost("ghost/model", 100, 100)
            }
        }
    }

    // ─── Preferences ─────────────────────────────────────────────────────────

    @Nested
    inner class Preferences {

        @BeforeEach
        fun setUp() {
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine())
        }

        @Test
        fun `savePreferences then getPreferences round-trips`() = runTest {
            val prefs = UserPreferences(defaultModel = "openai/gpt-4")
            sdk.savePreferences(prefs)
            val loaded = sdk.getPreferences()
            assertEquals("openai/gpt-4", loaded?.defaultModel)
        }

        @Test
        fun `getPreferences returns null when nothing saved`() = runTest {
            assertNull(sdk.getPreferences())
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    @Nested
    inner class Events {

        @Test
        fun `models updated event fires after fetchModels`() = runTest {
            val events = mutableListOf<ORAEvent>()
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel()))
            ))
            sdk.on(ORAEventType.MODELS_UPDATED) { events.add(it) }
            sdk.fetchModels()
            assertEquals(1, events.size)
            assertEquals(ORAEventType.MODELS_UPDATED, events[0].type)
        }

        @Test
        fun `model added event fires after addModel`() = runTest {
            val events = mutableListOf<ORAEvent>()
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel()))
            ))
            sdk.fetchModels()
            sdk.on(ORAEventType.MODEL_ADDED) { events.add(it) }
            sdk.addModel("test/model")
            assertEquals(1, events.size)
        }

        @Test
        fun `unsubscribe stops receiving events`() = runTest {
            val events = mutableListOf<ORAEvent>()
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel()))
            ))
            val unsub = sdk.on(ORAEventType.MODELS_UPDATED) { events.add(it) }
            sdk.fetchModels()
            unsub()
            sdk.fetchModels()
            assertEquals(1, events.size, "Should receive only one event before unsubscribe")
        }
    }

    // ─── dispose ──────────────────────────────────────────────────────────────

    @Nested
    inner class Dispose {

        @Test
        fun `dispose clears event handlers`() = runTest {
            sdk = TestFactory.buildSdk(TestFactory.makeMockEngine(
                modelsJson = TestFactory.makeModelsApiResponse(listOf(TestFactory.makeModel()))
            ))
            var count = 0
            sdk.on(ORAEventType.MODELS_UPDATED) { count++ }
            sdk.fetchModels()
            assertEquals(1, count, "Handler should fire before dispose")
            sdk.dispose()
            // fetchModels after dispose may throw (closed HTTP client); either way, handlers are cleared
            runCatching { sdk.fetchModels() }
            assertEquals(1, count, "No events should fire after dispose")
        }
    }
}
