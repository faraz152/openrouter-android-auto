# Skill: SSE Streaming with Kotlin Flow

> Specialized skill for implementing Server-Sent Events (SSE) streaming using Ktor and Kotlin Flow, including stream parsing, chunk accumulation, and error recovery.

## When This Skill Applies

- Implementing `streamChat()` in `OpenRouterAuto.kt`
- Working on `internal/StreamParser.kt` (SSE line parser)
- Working on `internal/StreamAccumulator.kt` (chunk reassembly)
- Writing tests for streaming behavior
- Debugging streaming issues

## SSE Protocol

OpenRouter streams responses using the SSE (Server-Sent Events) protocol:

```
data: {"id":"gen-123","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}

data: {"id":"gen-123","choices":[{"index":0,"delta":{"content":"lo"}}]}

data: {"id":"gen-123","choices":[{"index":0,"delta":{"content":"!"}}],"usage":{"prompt_tokens":10,"completion_tokens":3}}

data: [DONE]
```

### Rules

1. Each event is prefixed with `data: `
2. Events are separated by double newlines (`\n\n`)
3. The stream ends with `data: [DONE]`
4. Empty lines between events must be skipped
5. Lines not starting with `data: ` must be ignored (e.g., `event:`, `id:`, `retry:`)
6. Malformed JSON in a `data:` line must be silently skipped (not crash the stream)

## Implementation

### StreamParser.kt

```kotlin
// internal/StreamParser.kt
package io.openrouter.android.auto.internal

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.openrouter.android.auto.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun parseSSEStream(channel: ByteReadChannel): Flow<StreamChunk> = flow {
    while (!channel.isClosedForRead) {
        val line = channel.readUTF8Line() ?: break

        // Skip empty lines (event separators)
        if (line.isBlank()) continue

        // Only process "data: " prefixed lines
        if (!line.startsWith("data: ")) continue

        val data = line.removePrefix("data: ").trim()

        // Check for stream end sentinel
        if (data == "[DONE]") break

        // Parse JSON, skip malformed chunks
        try {
            val chunk = json.decodeFromString<StreamChunk>(data)
            emit(chunk)
        } catch (e: Exception) {
            // Silently skip malformed chunks — common with partial SSE data
        }
    }
}
```

### StreamAccumulator.kt

```kotlin
// internal/StreamAccumulator.kt
package io.openrouter.android.auto.internal

import io.openrouter.android.auto.*

class StreamAccumulator {
    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private val toolCallPartials = mutableMapOf<Int, MutableToolCall>()
    private var finishReason: String? = null
    private var id: String? = null
    private var model: String? = null
    private var created: Long? = null
    private var usage: Usage? = null

    fun push(chunk: StreamChunk) {
        // Update metadata
        chunk.id?.let { id = it }
        chunk.model?.let { model = it }
        chunk.created?.let { created = it }
        chunk.usage?.let { usage = it }

        val choices = chunk.choices ?: return
        if (choices.isEmpty()) return

        val choice = choices[0]
        choice.finishReason?.let { finishReason = it }

        val delta = choice.delta ?: return

        // Accumulate content
        delta.content?.let { c ->
            // Extract text from JsonElement
            val text = extractText(c)
            if (text != null) content.append(text)
        }

        // Accumulate reasoning
        delta.reasoning?.let { reasoning.append(it) }
        delta.reasoningContent?.let { reasoning.append(it) }

        // Accumulate tool calls
        delta.toolCalls?.forEach { tc ->
            val idx = tc.index ?: 0
            val partial = toolCallPartials.getOrPut(idx) {
                MutableToolCall()
            }
            tc.id?.let { partial.id = it }
            tc.type?.let { partial.type = it }
            tc.function?.name?.let { partial.name += it }
            tc.function?.arguments?.let { partial.arguments += it }
        }
    }

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
```

### Integration in OpenRouterAuto.kt

```kotlin
fun streamChat(request: ChatRequest): Flow<StreamChunk> = flow {
    val streamRequest = request.copy(stream = true)
    httpClient.preparePost("${config.baseUrl}/chat/completions") {
        header("Authorization", "Bearer ${config.apiKey}")
        header("HTTP-Referer", config.siteUrl ?: "")
        header("X-Title", config.siteName ?: "")
        contentType(ContentType.Application.Json)
        setBody(streamRequest)
    }.execute { response ->
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw mapHttpError(response.status.value, body)
        }
        val channel = response.bodyAsChannel()
        parseSSEStream(channel).collect { chunk ->
            emit(chunk)
        }
    }
}.flowOn(Dispatchers.IO)
```

## Testing Streaming

### Mock SSE Response

```kotlin
fun createSSEResponse(chunks: List<String>): String {
    return chunks.joinToString("\n\n") { "data: $it" } + "\n\ndata: [DONE]\n\n"
}

val mockEngine = MockEngine { request ->
    if (request.url.encodedPath.endsWith("/chat/completions")) {
        val sseBody = createSSEResponse(listOf(
            """{"id":"gen-1","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}""",
            """{"id":"gen-1","choices":[{"index":0,"delta":{"content":"lo!"}}]}""",
            """{"id":"gen-1","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}"""
        ))
        respond(sseBody, HttpStatusCode.OK, headersOf("Content-Type", "text/event-stream"))
    } else {
        respondError(HttpStatusCode.NotFound)
    }
}
```

### Turbine Flow Test

```kotlin
@Test
fun `streamChat accumulates content correctly`() = runTest {
    val accumulator = StreamAccumulator()
    sdk.streamChat(request).test {
        val chunk1 = awaitItem()
        accumulator.push(chunk1)
        assertEquals("Hel", extractText(chunk1.choices!![0].delta!!.content))

        val chunk2 = awaitItem()
        accumulator.push(chunk2)

        val chunk3 = awaitItem()
        accumulator.push(chunk3)

        awaitComplete()

        val response = accumulator.toResponse()
        assertEquals("Hello!", extractText(response.choices[0].message.content))
        assertEquals("stop", response.choices[0].finishReason)
    }
}
```

## Edge Cases to Handle

1. **Empty content deltas**: `{"delta":{}}` — skip, don't append empty string
2. **Reasoning + content in same stream**: Accumulate both independently
3. **Tool calls across multiple chunks**: Index-keyed assembly
4. **Usage only in final chunk**: Capture if present on any chunk
5. **Network interruption mid-stream**: Flow cancellation propagates cleanly
6. **Malformed JSON**: Skip with try-catch, don't kill the whole stream
7. **Extra whitespace**: `data:  {"id":...}` (double space) — handle with trim
8. **No newline at end**: Some servers don't send final `\n` — handle gracefully
