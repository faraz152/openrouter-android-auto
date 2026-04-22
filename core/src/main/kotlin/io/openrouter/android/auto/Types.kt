package io.openrouter.android.auto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ==================== Model Types ====================

@Serializable
data class ModelArchitecture(
    val modality: String? = null,
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
    @SerialName("instruct_type") val instructType: String? = null,
    val tokenizer: String? = null
)

@Serializable
data class ModelPricing(
    val prompt: String,
    val completion: String,
    val image: String? = null,
    val request: String? = null
)

@Serializable
data class TopProvider(
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated") val isModerated: Boolean? = null
)

@Serializable
data class ModelLinks(
    val details: String? = null
)

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("context_length") val contextLength: Int,
    val created: Long? = null,
    val architecture: ModelArchitecture? = null,
    val pricing: ModelPricing,
    @SerialName("supported_parameters") val supportedParameters: List<String>? = null,
    @SerialName("top_provider") val topProvider: TopProvider? = null,
    val links: ModelLinks? = null,
    @SerialName("canonical_slug") val canonicalSlug: String? = null,
    @SerialName("per_request_limits") val perRequestLimits: PerRequestLimits? = null
)

@Serializable
data class PerRequestLimits(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null
)

@Serializable
data class ModelsResponse(
    val data: List<OpenRouterModel>
)

// ==================== Tool Calling Types ====================

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null
)

@Serializable
data class ToolDefinition(
    val type: String,                        // "function" or "openrouter:web_search"
    val function: FunctionDefinition? = null,
    val parameters: JsonElement? = null      // for web search tool
)

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null,
    val index: Int? = null                   // for streaming chunk assembly
)

// ==================== Reasoning Types ====================

@Serializable
data class ReasoningConfig(
    val effort: String? = null,              // "low", "medium", "high"
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ReasoningDetail(
    val type: String,
    val text: String,
    val format: String? = null,
    val index: Int? = null
)

// ==================== Content Part Types (multimodal) ====================

@Serializable
data class ImageUrlData(
    val url: String,
    val detail: String? = null               // "auto", "low", "high"
)

@Serializable
data class InputAudioData(
    val data: String,
    val format: String                       // "wav" or "mp3"
)

// ==================== Annotation Types ====================

@Serializable
data class UrlCitation(
    val url: String,
    val title: String? = null,
    val content: String? = null,
    @SerialName("start_index") val startIndex: Int? = null,
    @SerialName("end_index") val endIndex: Int? = null
)

@Serializable
data class Annotation(
    val type: String,                        // "url_citation"
    @SerialName("url_citation") val urlCitation: UrlCitation? = null
)

// ==================== Chat Message ====================

@Serializable
data class ChatMessage(
    val role: String? = null,                // "system", "user", "assistant", "tool"
    val content: JsonElement? = null,        // String or List<ContentPart> as JsonElement
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("reasoning_details") val reasoningDetails: List<ReasoningDetail>? = null,
    val refusal: String? = null,
    val annotations: List<Annotation>? = null
)

// ==================== Web Search Types ====================

@Serializable
data class WebSearchUserLocation(
    val type: String = "approximate",
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val timezone: String? = null
)

@Serializable
data class WebSearchParameters(
    val engine: String? = null,              // "auto", "native", "exa", "firecrawl", "parallel"
    @SerialName("max_results") val maxResults: Int? = null,
    @SerialName("max_total_results") val maxTotalResults: Int? = null,
    @SerialName("search_context_size") val searchContextSize: String? = null,
    @SerialName("allowed_domains") val allowedDomains: List<String>? = null,
    @SerialName("excluded_domains") val excludedDomains: List<String>? = null,
    @SerialName("user_location") val userLocation: WebSearchUserLocation? = null
)

// ==================== Provider Routing Types ====================

@Serializable
data class ProviderPreferences(
    val order: List<String>? = null,
    @SerialName("allow_fallbacks") val allowFallbacks: Boolean? = null,
    @SerialName("require_parameters") val requireParameters: Boolean? = null,
    @SerialName("data_collection") val dataCollection: String? = null,
    val sort: String? = null,
    val quantizations: List<String>? = null,
    val ignore: List<String>? = null
)

// ==================== Chat Request ====================

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Double? = null,
    @SerialName("min_p") val minP: Double? = null,
    @SerialName("top_a") val topA: Double? = null,
    val seed: Int? = null,
    val stop: JsonElement? = null,           // String or List<String>
    @SerialName("stream_options") val streamOptions: JsonElement? = null,
    val tools: List<JsonElement>? = null,    // ToolDefinition | WebSearchTool
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    val reasoning: ReasoningConfig? = null,
    val include: List<String>? = null,
    @SerialName("response_format") val responseFormat: JsonElement? = null,
    val provider: ProviderPreferences? = null,
    val models: List<String>? = null,
    val route: String? = null,
    val plugins: JsonElement? = null,
    val metadata: JsonObject? = null,
    val trace: JsonElement? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val user: String? = null,
    val modalities: List<String>? = null,
    val logprobs: Boolean? = null,
    @SerialName("top_logprobs") val topLogprobs: Int? = null,
    @SerialName("cache_control") val cacheControl: JsonElement? = null,
    @SerialName("service_tier") val serviceTier: String? = null
)

// ==================== Response Types ====================

@Serializable
data class CompletionTokensDetails(
    @SerialName("reasoning_tokens") val reasoningTokens: Int? = null,
    @SerialName("accepted_prediction_tokens") val acceptedPredictionTokens: Int? = null,
    @SerialName("rejected_prediction_tokens") val rejectedPredictionTokens: Int? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatResponse(
    val id: String,
    val model: String? = null,
    val created: Long? = null,
    val choices: List<Choice>,
    val usage: Usage? = null
)

// ==================== Streaming Types ====================

@Serializable
data class StreamChoice(
    val index: Int,
    val delta: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamChunk(
    val id: String? = null,
    val `object`: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val choices: List<StreamChoice>? = null,
    val usage: Usage? = null
)

// ==================== Model Configuration Types ====================

@Serializable
data class ModelTestResult(
    val success: Boolean,
    val model: String? = null,
    val error: String? = null,
    @SerialName("response_time") val responseTime: Long? = null,
    @SerialName("tokens_used") val tokensUsed: Int? = null,
    val response: String? = null
)

@Serializable
data class ModelConfig(
    @SerialName("model_id") val modelId: String,
    val parameters: Map<String, JsonElement> = emptyMap(),
    val enabled: Boolean = true,
    @SerialName("test_status") val testStatus: String? = null,  // "pending", "success", "failed"
    @SerialName("test_error") val testError: String? = null,
    @SerialName("added_at") val addedAt: Long = System.currentTimeMillis(),
    @SerialName("last_tested") val lastTested: Long? = null
)

// ==================== Filter & Enum Types ====================

enum class PriceTier { FREE, CHEAP, MODERATE, EXPENSIVE }

data class ModelFilter(
    val freeOnly: Boolean = false,
    val maxPrice: Double? = null,
    val priceTier: PriceTier? = null,
    val provider: String? = null,
    val search: String? = null,
    val minContextLength: Int? = null,
    val maxContextLength: Int? = null,
    val modality: String? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
    val supportedParameters: List<String>? = null,
    val excludeModels: List<String>? = null
)

// ==================== Cost Estimate ====================

@Serializable
data class CostEstimate(
    @SerialName("prompt_cost") val promptCost: Double,
    @SerialName("completion_cost") val completionCost: Double,
    @SerialName("reasoning_cost") val reasoningCost: Double = 0.0,
    @SerialName("total_cost") val totalCost: Double,
    val currency: String = "USD"
)

// ==================== Parameter Definition ====================

@Serializable
data class ParameterDef(
    val name: String? = null,
    val type: String,                        // "number", "integer", "string", "boolean", "array"
    val description: String? = null,
    val default: JsonElement? = null,
    val min: Double? = null,
    val max: Double? = null,
    val enum: List<JsonElement>? = null,
    val required: Boolean = false
)

// ==================== User Preferences ====================

@Serializable
data class UserPreferences(
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("default_parameters") val defaultParameters: Map<String, JsonElement>? = null,
    @SerialName("max_budget") val maxBudget: Double? = null,
    @SerialName("preferred_providers") val preferredProviders: List<String>? = null,
    @SerialName("excluded_models") val excludedModels: List<String>? = null
)
