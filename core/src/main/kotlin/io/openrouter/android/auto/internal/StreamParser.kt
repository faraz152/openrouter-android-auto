package io.openrouter.android.auto.internal

import io.ktor.utils.io.*
import io.openrouter.android.auto.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

private val sseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Parse a Ktor [ByteReadChannel] SSE stream into a [Flow] of [StreamChunk].
 *
 * Protocol rules (all enforced here):
 * 1. Each event line is prefixed with `data: `
 * 2. Empty / blank lines are separators — skip them
 * 3. Lines not starting with `data: ` (e.g. `event:`, `id:`, `retry:`) are ignored
 * 4. `data: [DONE]` is the end-of-stream sentinel — terminate cleanly
 * 5. Malformed JSON in a `data:` line is silently skipped (never crashes the stream)
 * 6. Extra whitespace after the prefix is trimmed
 */
internal fun parseSSEStream(channel: ByteReadChannel): Flow<StreamChunk> = flow {
    while (!channel.isClosedForRead) {
        val line = channel.readUTF8Line() ?: break

        if (line.isBlank()) continue
        if (!line.startsWith("data:")) continue

        // Trim after "data:" — handles both "data: {...}" and "data:{...}"
        val data = line.removePrefix("data:").trim()

        if (data == "[DONE]") break

        try {
            val chunk = sseJson.decodeFromString<StreamChunk>(data)
            emit(chunk)
        } catch (_: Exception) {
            // Silently skip malformed chunks — common at stream boundaries
        }
    }
}
