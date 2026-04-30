package io.openrouter.android.auto.internal

import io.openrouter.android.auto.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reassembles a sequence of [StreamChunk] deltas into a complete [ChatResponse].
 *
 * Usage:
 * ```kotlin
 * val acc = StreamAccumulator()
 * flow.collect { acc.push(it) }
 * val response: ChatResponse = acc.toResponse()
 * ```
 *
 * Handles:
 * - Text content accumulation
 * - Reasoning / reasoning_content accumulation
 * - Tool call streaming (index-keyed partial assembly)
 * - Usage capture (present on final chunk from OpenRouter)
 * - Empty delta objects (skipped without appending)
 */
internal class StreamAccumulator {

    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private val toolCallPartials = mutableMapOf<Int, MutableToolCall>()

    private var finishReason: String? = null
    private var id: String? = null
    private var model: String? = null
    private var created: Long? = null
    private var usage: Usage? = null

    fun push(chunk: StreamChunk) {
        chunk.id?.let { id = it }
        chunk.model?.let { model = it }
        chunk.created?.let { created = it }
        chunk.usage?.let { usage = it }

        val choices = chunk.choices ?: return
        if (choices.isEmpty()) return

        val choice = choices[0]
        choice.finishReason?.let { finishReason = it }

        val delta = choice.delta ?: return

        // Accumulate text content — extract string from JsonElement
        delta.content?.let { elem ->
            try {
                val text = elem.jsonPrimitive.content
                if (text.isNotEmpty()) content.append(text)
            } catch (_: Exception) {
                // Non-primitive content (e.g. null literal) — skip
            }
        }

        // Accumulate reasoning
        delta.reasoning?.let { if (it.isNotEmpty()) reasoning.append(it) }
        delta.reasoningContent?.let { if (it.isNotEmpty()) reasoning.append(it) }

        // Accumulate tool calls — keyed by index for multi-call streaming
        delta.toolCalls?.forEach { tc ->
            val idx = tc.index ?: 0
            val partial = toolCallPartials.getOrPut(idx) { MutableToolCall() }
            tc.id?.let { partial.id = it }
            tc.type?.let { partial.type = it }
            tc.function?.name?.let { partial.name += it }
            tc.function?.arguments?.let { partial.arguments += it }
        }
    }

    /**
     * Assemble accumulated state into a [ChatResponse].
     * Safe to call multiple times (idempotent read).
     */
    fun toResponse(): ChatResponse {
        val toolCalls = toolCallPartials.entries
            .sortedBy { it.key }
            .map { (_, p) ->
                ToolCall(
                    id = p.id,
                    type = p.type ?: "function",
                    function = FunctionCall(name = p.name, arguments = p.arguments)
                )
            }
            .takeIf { it.isNotEmpty() }

        val message = ChatMessage(
            role = "assistant",
            content = if (content.isNotEmpty()) JsonPrimitive(content.toString()) else null,
            toolCalls = toolCalls,
            reasoning = reasoning.toString().takeIf { it.isNotEmpty() }
        )

        return ChatResponse(
            id = id ?: "",
            model = model,
            created = created,
            choices = listOf(Choice(index = 0, message = message, finishReason = finishReason)),
            usage = usage
        )
    }

    private class MutableToolCall(
        var id: String? = null,
        var type: String? = null,
        var name: String = "",
        var arguments: String = ""
    )
}
