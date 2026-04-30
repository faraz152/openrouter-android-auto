package io.openrouter.android.auto

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.openrouter.android.auto.internal.CostRegistry
import io.openrouter.android.auto.internal.ErrorRegistry
import io.openrouter.android.auto.internal.ParameterRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Central factory for test fixtures shared across all test suites.
 * Call [initRegistries] in @BeforeEach to ensure ErrorRegistry + ParameterRegistry
 * are loaded from classpath resources before each test.
 */
object TestFactory {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // ─── Registry Init ──────────────────────────────────────────────────────

    /**
     * Load ErrorRegistry, ParameterRegistry, and CostRegistry from src/test/resources/.
     * Must be called before any test that exercises SDK functions.
     */
    fun initRegistries() {
        val loader = TestFactory::class.java.classLoader!!
        val errorsJson = loader.getResourceAsStream("errors.json")!!
            .bufferedReader().readText()
        val paramsJson = loader.getResourceAsStream("parameters.json")!!
            .bufferedReader().readText()
        val platformJson = loader.getResourceAsStream("platform_params.json")!!
            .bufferedReader().readText()
        val costJson = loader.getResourceAsStream("cost.json")!!
            .bufferedReader().readText()

        ErrorRegistry.init(errorsJson)
        ParameterRegistry.init(paramsJson, platformJson)
        CostRegistry.init(costJson)
    }

    // ─── Model Builders ─────────────────────────────────────────────────────

    fun makeModel(
        id: String = "test/model",
        name: String = "Test Model",
        promptPrice: String = "0.001",
        completionPrice: String = "0.002",
        contextLength: Int = 4096,
        supportedParameters: List<String> = listOf(
            "temperature", "top_p", "top_k", "max_tokens",
            "frequency_penalty", "presence_penalty", "seed", "stop"
        ),
        maxCompletionTokens: Int? = 2048,
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

    fun makeModelNoParams(id: String = "test/no-params"): OpenRouterModel =
        makeModel(id = id, supportedParameters = emptyList())

    fun makeModelWithParams(vararg params: String): OpenRouterModel =
        makeModel(supportedParameters = params.toList())

    // ─── Chat Builders ───────────────────────────────────────────────────────

    fun makeChatResponse(
        content: String = "Hello!",
        model: String = "test/model",
        finishReason: String = "stop",
        promptTokens: Int = 10,
        completionTokens: Int = 5
    ): ChatResponse = ChatResponse(
        id = "gen-test-123",
        model = model,
        created = 1_700_000_000L,
        choices = listOf(
            Choice(
                index = 0,
                message = ChatMessage(
                    role = "assistant",
                    content = JsonPrimitive(content)
                ),
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
        created = 1_700_000_000L,
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

    fun makeModelsApiResponse(models: List<OpenRouterModel>): String =
        json.encodeToString(ModelsResponse(data = models))

    // ─── Error Builders ──────────────────────────────────────────────────────

    fun makeORAError(
        code: ORAErrorCode = ORAErrorCode.UNKNOWN,
        message: String = "Test error",
        retryable: Boolean = false,
        details: Any? = null
    ): ORAError = ORAError(
        code = code,
        message = message,
        retryable = retryable,
        details = details
    )

    // ─── SSE Helpers ─────────────────────────────────────────────────────────

    /** Build a well-formed SSE body from a list of JSON chunk strings + [DONE]. */
    fun makeSseBody(vararg chunkJsons: String): String =
        chunkJsons.joinToString("\n\n") { "data: $it" } + "\n\ndata: [DONE]\n\n"

    // ─── MockEngine Builders ─────────────────────────────────────────────────

    fun makeMockEngine(
        modelsJson: String? = null,
        chatJson: String? = null,
        sseBody: String? = null,
        errorStatus: HttpStatusCode? = null,
        chatErrorStatus: HttpStatusCode? = null
    ): MockEngine = MockEngine { request ->
        when {
            errorStatus != null -> respondError(errorStatus)

            request.url.encodedPath.endsWith("/models") -> respond(
                content = modelsJson ?: makeModelsApiResponse(listOf(makeModel())),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            request.url.encodedPath.endsWith("/chat/completions") -> {
                if (chatErrorStatus != null) {
                    respondError(chatErrorStatus)
                } else {
                    val body = request.body.toByteArray().decodeToString()
                    if (body.contains("\"stream\":true") && sseBody != null) {
                        respond(
                            content = sseBody,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
                        )
                    } else {
                        respond(
                            content = chatJson ?: json.encodeToString(makeChatResponse()),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            }

            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    /** Build a fully configured SDK with a [MockEngine] and in-memory storage. */
    fun buildSdk(mockEngine: MockEngine): OpenRouterAuto {
        val loader = TestFactory::class.java.classLoader!!
        return OpenRouterAuto.Builder(
            apiKey = "test-sk-key",
            errorsJson = loader.getResourceAsStream("errors.json")!!.bufferedReader().readText(),
            parametersJson = loader.getResourceAsStream("parameters.json")!!.bufferedReader().readText(),
            platformParamsJson = loader.getResourceAsStream("platform_params.json")!!.bufferedReader().readText(),
            costJson = loader.getResourceAsStream("cost.json")!!.bufferedReader().readText()
        )
            .httpClientEngine(mockEngine)
            .storage(MemoryStorage())
            .enableTesting(false)
            .build()
    }
}

