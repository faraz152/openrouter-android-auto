package io.openrouter.android.auto

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.openrouter.android.auto.internal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
private const val DEFAULT_REFERER = "https://github.com/faraz152/openrouter-android-auto"
private const val DEFAULT_SITE_NAME = "openrouter-android-auto"
private const val DEFAULT_TEST_PROMPT = "Say \"Hello! This is a test message.\" and nothing else."
private const val CACHE_DURATION_MS = 3_600_000L  // 1 hour

private val storageJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * Main SDK entry point.
 * Mirrors [OpenRouterAuto](packages/core/src/sdk.ts) from the TypeScript reference.
 *
 * Usage:
 * ```kotlin
 * val sdk = OpenRouterAuto.Builder(
 *     apiKey = "sk-or-...",
 *     errorsJson = <errors.json string>,
 *     parametersJson = <parameters.json string>,
 *     platformParamsJson = <platform_params.json string>,
 *     costJson = <cost.json string>
 * ).build()
 *
 * sdk.initialize()
 * val response = sdk.chat(ChatRequest(...))
 * ```
 *
 * Security: the [apiKey] is set per-request as an `Authorization` header only —
 * never stored in default headers or persisted to storage.
 */
class OpenRouterAuto private constructor(
    private val apiKey: String,
    private val baseUrl: String,
    private val siteUrl: String,
    private val siteName: String,
    private val storage: StorageAdapter,
    private val httpEngine: HttpEngine,
    val testPrompt: String,
    val enableTesting: Boolean
) {

    private val models = mutableListOf<OpenRouterModel>()
    private val modelConfigs = mutableMapOf<String, ModelConfig>()
    private val eventHandlers = mutableMapOf<ORAEventType, MutableSet<EventHandler>>()

    // ==================== Builder ====================

    class Builder(
        private val apiKey: String,
        private val errorsJson: String,
        private val parametersJson: String,
        private val platformParamsJson: String,
        private val costJson: String
    ) {
        private var baseUrl: String = DEFAULT_BASE_URL
        private var siteUrl: String = DEFAULT_REFERER
        private var siteName: String = DEFAULT_SITE_NAME
        private var storage: StorageAdapter = MemoryStorage()
        private var httpClientEngine: HttpClientEngine? = null
        private var testPrompt: String = DEFAULT_TEST_PROMPT
        private var enableTesting: Boolean = true

        fun baseUrl(url: String) = apply {
            val scheme = url.substringBefore("://")
            require(scheme == "https" || scheme == "http") {
                "Unsupported baseUrl scheme: '$scheme'. Only https:// and http:// are allowed."
            }
            baseUrl = url
        }

        fun siteUrl(url: String) = apply { siteUrl = url }
        fun siteName(name: String) = apply { siteName = name }
        fun storage(adapter: StorageAdapter) = apply { storage = adapter }
        fun testPrompt(prompt: String) = apply { testPrompt = prompt }
        fun enableTesting(enabled: Boolean) = apply { enableTesting = enabled }

        /** Inject a custom Ktor engine — use [MockEngine] in tests. */
        fun httpClientEngine(engine: HttpClientEngine) = apply { httpClientEngine = engine }

        fun build(): OpenRouterAuto {
            // Initialize all registries once
            ErrorRegistry.init(errorsJson)
            ParameterRegistry.init(parametersJson, platformParamsJson)
            CostRegistry.init(costJson)

            val engine = if (httpClientEngine != null)
                HttpEngine(httpClientEngine!!)
            else
                HttpEngine()

            return OpenRouterAuto(
                apiKey = apiKey,
                baseUrl = baseUrl.trimEnd('/'),
                siteUrl = siteUrl,
                siteName = siteName,
                storage = storage,
                httpEngine = engine,
                testPrompt = testPrompt,
                enableTesting = enableTesting
            )
        }
    }

    // ==================== Request Helper ====================

    private fun HttpRequestBuilder.applyAuth() {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        header("HTTP-Referer", siteUrl)
        header("X-Title", siteName)
    }

    // ==================== Initialization ====================

    /**
     * Initialize the SDK: load cached models + configs, fetch fresh models if stale.
     * Call this once on startup before making any requests.
     */
    suspend fun initialize() {
        // Load cached models
        storage.get(StorageKeys.MODELS)?.let { json ->
            runCatching {
                storageJson.decodeFromString<List<OpenRouterModel>>(json)
            }.getOrNull()?.let { models.addAll(it) }
        }

        // Load model configs
        storage.get(StorageKeys.MODEL_CONFIGS)?.let { json ->
            runCatching {
                storageJson.decodeFromString<Map<String, ModelConfig>>(json)
            }.getOrNull()?.let { modelConfigs.putAll(it) }
        }

        // Fetch fresh models if cache is stale or empty
        val lastFetch = storage.get(StorageKeys.LAST_FETCH)?.toLongOrNull() ?: 0L
        val stale = System.currentTimeMillis() - lastFetch > CACHE_DURATION_MS
        if (stale || models.isEmpty()) {
            runCatching { fetchModels() }
        }
    }

    // ==================== Model Fetching ====================

    /** Fetch all models from the OpenRouter /models endpoint and cache them. */
    suspend fun fetchModels(): List<OpenRouterModel> {
        val response = httpEngine.client.get("$baseUrl/models") {
            applyAuth()
        }
        if (!response.status.isSuccess()) {
            throw mapHttpError(response.status.value)
        }
        val result = response.body<ModelsResponse>()
        models.clear()
        models.addAll(result.data)
        storage.set(StorageKeys.MODELS, storageJson.encodeToString(result.data))
        storage.set(StorageKeys.LAST_FETCH, System.currentTimeMillis().toString())
        emit(ORAEventType.MODELS_UPDATED, mapOf("count" to models.size))
        return models.toList()
    }

    fun getModels(): List<OpenRouterModel> = models.toList()

    fun getModel(modelId: String): OpenRouterModel? = models.find { it.id == modelId }

    /**
     * Filter models using a [ModelFilter]. All criteria are optional and ANDed together.
     * Mirrors filterModels() in sdk.ts.
     */
    fun filterModels(filter: ModelFilter = ModelFilter()): List<OpenRouterModel> =
        models.filter { model ->
            val arch = model.architecture

            if (filter.modality != null && arch?.modality != filter.modality) return@filter false

            if (filter.inputModalities != null) {
                val inputs = arch?.inputModalities ?: emptyList()
                if (!inputs.containsAll(filter.inputModalities)) return@filter false
            }

            if (filter.outputModalities != null) {
                val outputs = arch?.outputModalities ?: emptyList()
                if (!outputs.containsAll(filter.outputModalities)) return@filter false
            }

            if (filter.maxPrice != null) {
                val prompt = model.pricing.prompt.toDoubleOrNull() ?: 0.0
                val completion = model.pricing.completion.toDoubleOrNull() ?: 0.0
                if (prompt > filter.maxPrice || completion > filter.maxPrice) return@filter false
            }

            if (filter.minContextLength != null && model.contextLength < filter.minContextLength)
                return@filter false

            if (filter.maxContextLength != null && model.contextLength > filter.maxContextLength)
                return@filter false

            if (filter.provider != null && model.id.substringBefore("/") != filter.provider)
                return@filter false

            if (filter.search != null) {
                val q = filter.search.lowercase()
                val match = model.id.lowercase().contains(q) ||
                        model.name.lowercase().contains(q) ||
                        model.description?.lowercase()?.contains(q) == true
                if (!match) return@filter false
            }

            if (filter.supportedParameters != null) {
                val supported = model.supportedParameters ?: emptyList()
                if (!supported.containsAll(filter.supportedParameters)) return@filter false
            }

            if (filter.excludeModels != null && model.id in filter.excludeModels) return@filter false

            if (filter.freeOnly && !isFreeModel(model)) return@filter false

            if (filter.priceTier != null && getPriceTier(model) != filter.priceTier)
                return@filter false

            true
        }

    /** Return the best free text model (largest context), or null if none available. */
    fun getBestFreeModel(): OpenRouterModel? = getBestFreeModel(models)

    // ==================== Model Configuration ====================

    /**
     * Add and configure a model.
     * @param skipTest Pass `true` to bypass the availability test (useful for free models).
     */
    suspend fun addModel(
        modelId: String,
        parameters: Map<String, Any?> = emptyMap(),
        skipTest: Boolean = false
    ): ModelConfig {
        val model = getModel(modelId)
            ?: throw ORAError(
                ORAErrorCode.MODEL_NOT_FOUND,
                "Model '$modelId' not found. Fetch models first.",
                retryable = false
            )

        val validation = validateParameters(model, parameters)
        if (!validation.valid) {
            val msg = validation.errors.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            throw ORAError(ORAErrorCode.INVALID_PARAMETERS, "Invalid parameters: $msg", retryable = false)
        }

        val merged = mergeWithDefaults(model, parameters)
        val config = ModelConfig(
            modelId = modelId,
            enabled = true,
            addedAt = System.currentTimeMillis()
        )

        if (enableTesting && !skipTest) {
            val result = testModel(modelId, merged)
            if (!result.success) {
                throw ORAError(
                    ORAErrorCode.MODEL_UNAVAILABLE,
                    "Model '$modelId' is currently unavailable: ${result.error ?: "test failed"}. " +
                            "Pass skipTest=true to addModel() to bypass this check.",
                    retryable = true
                )
            }
        }

        modelConfigs[modelId] = config
        persistConfigs()
        emit(ORAEventType.MODEL_ADDED, mapOf("modelId" to modelId))
        return config
    }

    /** Remove a model configuration. */
    suspend fun removeModel(modelId: String) {
        modelConfigs.remove(modelId)
        persistConfigs()
        emit(ORAEventType.MODEL_REMOVED, mapOf("modelId" to modelId))
    }

    fun getModelConfig(modelId: String): ModelConfig? = modelConfigs[modelId]

    fun getAllModelConfigs(): Map<String, ModelConfig> = modelConfigs.toMap()

    /** Update parameters for a configured model. */
    suspend fun updateModelParameters(
        modelId: String,
        parameters: Map<String, Any?>
    ): ModelConfig {
        val config = modelConfigs[modelId]
            ?: throw ORAError(
                ORAErrorCode.MODEL_NOT_FOUND,
                "Model '$modelId' is not configured. Add it first.",
                retryable = false
            )

        val model = getModel(modelId)!!
        val validation = validateParameters(model, parameters)
        if (!validation.valid) {
            throw ORAError(
                ORAErrorCode.INVALID_PARAMETERS,
                "Invalid parameters: ${validation.errors}",
                retryable = false
            )
        }

        val updated = config.copy()
        modelConfigs[modelId] = updated
        persistConfigs()
        emit(ORAEventType.CONFIG_CHANGED, mapOf("modelId" to modelId))
        return updated
    }

    // ==================== Model Testing ====================

    /** Test a model with a minimal chat call. Returns success/failure with timing. */
    suspend fun testModel(
        modelId: String,
        parameters: Map<String, Any?> = emptyMap()
    ): ModelTestResult {
        val start = System.currentTimeMillis()
        return try {
            val request = ChatRequest(
                model = modelId,
                messages = listOf(ChatMessage(role = "user",
                    content = kotlinx.serialization.json.JsonPrimitive(testPrompt))),
                stream = null
            )
            val response = httpEngine.client.post("$baseUrl/chat/completions") {
                applyAuth()
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                val elapsed = System.currentTimeMillis() - start
                val body = response.bodyAsText()
                return ModelTestResult(
                    success = false,
                    model = modelId,
                    error = mapHttpError(response.status.value, body).message,
                    responseTime = elapsed
                )
            }
            ModelTestResult(
                success = true,
                model = modelId,
                responseTime = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            ModelTestResult(
                success = false,
                model = modelId,
                error = e.message,
                responseTime = System.currentTimeMillis() - start
            )
        }
    }

    /** Test all currently configured models and return results. */
    suspend fun testAllModels(): List<ModelTestResult> {
        val results = mutableListOf<ModelTestResult>()
        for (modelId in modelConfigs.keys.toList()) {
            val result = testModel(modelId)
            results += result
            emit(ORAEventType.MODEL_TESTED, mapOf("modelId" to modelId, "result" to result))
        }
        return results
    }

    /** Quick availability probe without adding the model. */
    suspend fun checkModelAvailability(modelId: String): Triple<Boolean, String?, Long> {
        val result = testModel(modelId)
        return Triple(result.success, result.error, result.responseTime ?: 0L)
    }

    // ==================== Chat ====================

    /**
     * Send a non-streaming chat completion request.
     * Merges any stored model config parameters with the request parameters.
     */
    suspend fun chat(request: ChatRequest): ChatResponse {
        val model = getModel(request.model)
            ?: throw ORAError(
                ORAErrorCode.MODEL_NOT_FOUND,
                "Model '${request.model}' not found",
                retryable = false
            )

        val response = httpEngine.client.post("$baseUrl/chat/completions") {
            applyAuth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw mapHttpError(response.status.value, body)
        }

        return response.body<ChatResponse>()
    }

    /**
     * Stream a chat completion as a [Flow] of [StreamChunk].
     * Automatically sets `stream = true` on the request.
     * Mirrors streamChat() in sdk.ts.
     */
    fun streamChat(request: ChatRequest): Flow<StreamChunk> = flow {
        val streamRequest = request.copy(stream = true)

        httpEngine.client.preparePost("$baseUrl/chat/completions") {
            applyAuth()
            contentType(ContentType.Application.Json)
            setBody(streamRequest)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                throw mapHttpError(response.status.value, body)
            }
            parseSSEStream(response.bodyAsChannel()).collect { chunk ->
                emit(chunk)
            }
        }
    }.flowOn(Dispatchers.IO)

    // ==================== Cost ====================

    fun calculateCost(
        modelId: String,
        promptTokens: Int,
        completionTokens: Int = 0
    ): CostEstimate {
        val model = getModel(modelId)
            ?: throw ORAError(ORAErrorCode.MODEL_NOT_FOUND, "Model '$modelId' not found", retryable = false)
        return calculateCost(model, promptTokens, completionTokens)
    }

    fun estimateTokens(text: String): Int = io.openrouter.android.auto.estimateTokens(text)

    // ==================== Preferences ====================

    suspend fun savePreferences(preferences: UserPreferences) {
        // Never persist the API key
        storage.set(
            StorageKeys.USER_PREFERENCES,
            storageJson.encodeToString(preferences)
        )
    }

    suspend fun getPreferences(): UserPreferences? =
        storage.get(StorageKeys.USER_PREFERENCES)?.let { json ->
            runCatching { storageJson.decodeFromString<UserPreferences>(json) }.getOrNull()
        }

    // ==================== Parameter Helpers ====================

    fun getModelParameters(modelId: String): List<ParameterDef> {
        val model = getModel(modelId) ?: return emptyList()
        return getModelParameters(model)
    }

    // ==================== Events ====================

    /**
     * Subscribe to SDK events. Returns an unsubscribe lambda.
     * Mirrors on() in sdk.ts.
     */
    fun on(type: ORAEventType, handler: EventHandler): () -> Unit {
        eventHandlers.getOrPut(type) { mutableSetOf() }.add(handler)
        return { eventHandlers[type]?.remove(handler) }
    }

    private fun emit(type: ORAEventType, payload: Any? = null) {
        val event = ORAEvent(type = type, payload = payload)
        eventHandlers[type]?.forEach { handler ->
            runCatching { handler(event) }
        }
    }

    // ==================== Lifecycle ====================

    /** Release all resources (Ktor HttpClient, event handlers). */
    fun dispose() {
        httpEngine.close()
        eventHandlers.clear()
    }

    // ==================== Private Helpers ====================

    private suspend fun persistConfigs() {
        storage.set(StorageKeys.MODEL_CONFIGS, storageJson.encodeToString(modelConfigs.toMap()))
    }
}
