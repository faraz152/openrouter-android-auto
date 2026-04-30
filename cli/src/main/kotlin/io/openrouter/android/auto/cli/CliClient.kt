package io.openrouter.android.auto.cli

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ==================== Minimal types for CLI ====================

@Serializable
data class ModelPricing(
    val prompt: String = "0",
    val completion: String = "0"
)

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String = "",
    @SerialName("context_length") val contextLength: Int? = null,
    val pricing: ModelPricing? = null,
    @SerialName("top_provider") val topProvider: JsonObject? = null
)

@Serializable
data class ModelsResponse(val data: List<OpenRouterModel>)

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChoiceMessage(
    val role: String? = null,
    val content: JsonElement? = null
)

@Serializable
data class Choice(
    val message: ChoiceMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList()
)

@Serializable
data class StreamDelta(
    val content: String? = null
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamChunk(
    val id: String? = null,
    val choices: List<StreamChoice>? = null
)

@Serializable
data class CliConfig(
    @SerialName("api_key") val apiKey: String
)

// ==================== CLI Client ====================

internal val cliJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

class CliClient(
    private val apiKey: String,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
    engine: HttpClientEngine? = null
) {
    private val client = HttpClient(engine ?: CIO.create()) {
        install(ContentNegotiation) { json(cliJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000L
            connectTimeoutMillis = 15_000L
        }
    }

    suspend fun fetchModels(): List<OpenRouterModel> {
        val response = client.get("$baseUrl/models") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to fetch models: HTTP ${response.status.value}")
        }
        val body = response.bodyAsText()
        return cliJson.decodeFromString(ModelsResponse.serializer(), body).data
    }

    suspend fun chat(request: ChatRequest): ChatResponse {
        val response = client.post("$baseUrl/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(cliJson.encodeToString(ChatRequest.serializer(), request.copy(stream = false)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Chat failed: HTTP ${response.status.value} — ${response.bodyAsText()}")
        }
        return cliJson.decodeFromString(ChatResponse.serializer(), response.bodyAsText())
    }

    fun streamChat(request: ChatRequest): Flow<String> = flow {
        val response = client.post("$baseUrl/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(cliJson.encodeToString(ChatRequest.serializer(), request.copy(stream = true)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Stream failed: HTTP ${response.status.value}")
        }
        val text = response.bodyAsText()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == "data: [DONE]") continue
            if (!trimmed.startsWith("data: ")) continue
            val json = trimmed.removePrefix("data: ")
            val chunk = runCatching {
                cliJson.decodeFromString(StreamChunk.serializer(), json)
            }.getOrNull() ?: continue
            val content = chunk.choices?.firstOrNull()?.delta?.content
            if (!content.isNullOrEmpty()) emit(content)
        }
    }

    fun close() = client.close()
}
