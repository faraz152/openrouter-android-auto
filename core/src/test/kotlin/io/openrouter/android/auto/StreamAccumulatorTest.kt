package io.openrouter.android.auto

import io.openrouter.android.auto.internal.StreamAccumulator
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Standalone unit tests for [StreamAccumulator].
 * Covers push/accumulate lifecycle, text content, reasoning, tool calls, and toResponse().
 * Mirrors __tests__/stream.test.ts from the TypeScript reference.
 */
class StreamAccumulatorTest {

    private lateinit var acc: StreamAccumulator

    @BeforeEach
    fun setUp() {
        acc = StreamAccumulator()
    }

    // ─── Empty accumulator ────────────────────────────────────────────────────

    @Nested
    inner class EmptyAccumulator {

        @Test
        fun `toResponse returns valid ChatResponse with no content`() {
            acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))
            val response = acc.toResponse()
            assertNotNull(response)
            assertEquals(1, response.choices.size)
            assertNull(response.choices[0].message.content)
        }

        @Test
        fun `finishReason is captured from chunk`() {
            acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))
            assertEquals("stop", acc.toResponse().choices[0].finishReason)
        }

        @Test
        fun `finishReason length is captured from last chunk`() {
            acc.push(TestFactory.makeStreamChunk(content = "hi"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "length"))
            assertEquals("length", acc.toResponse().choices[0].finishReason)
        }

        @Test
        fun `model and id are captured from chunk`() {
            val chunk = TestFactory.makeStreamChunk(content = "x").copy(
                id = "gen-abc",
                model = "openai/gpt-4o"
            )
            acc.push(chunk)
            val response = acc.toResponse()
            assertEquals("gen-abc", response.id)
            assertEquals("openai/gpt-4o", response.model)
        }
    }

    // ─── Text content accumulation ────────────────────────────────────────────

    @Nested
    inner class TextContent {

        @Test
        fun `single chunk content is preserved`() {
            acc.push(TestFactory.makeStreamChunk(content = "Hello!"))
            val text = (acc.toResponse().choices[0].message.content as? JsonPrimitive)?.content
            assertEquals("Hello!", text)
        }

        @Test
        fun `multiple content deltas are concatenated in order`() {
            acc.push(TestFactory.makeStreamChunk(content = "Hel"))
            acc.push(TestFactory.makeStreamChunk(content = "lo"))
            acc.push(TestFactory.makeStreamChunk(content = " World"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))
            val text = (acc.toResponse().choices[0].message.content as? JsonPrimitive)?.content
            assertEquals("Hello World", text)
        }

        @Test
        fun `empty string deltas are skipped`() {
            acc.push(TestFactory.makeStreamChunk(content = ""))
            val content = acc.toResponse().choices[0].message.content
            assertNull(content)  // empty → no content set
        }

        @Test
        fun `toResponse is idempotent — multiple calls return same text`() {
            acc.push(TestFactory.makeStreamChunk(content = "Hello"))
            val r1 = acc.toResponse()
            val r2 = acc.toResponse()
            assertEquals(
                (r1.choices[0].message.content as? JsonPrimitive)?.content,
                (r2.choices[0].message.content as? JsonPrimitive)?.content
            )
        }

        @Test
        fun `whitespace and newlines are preserved`() {
            acc.push(TestFactory.makeStreamChunk(content = "line1\n"))
            acc.push(TestFactory.makeStreamChunk(content = "line2"))
            val text = (acc.toResponse().choices[0].message.content as? JsonPrimitive)?.content
            assertEquals("line1\nline2", text)
        }
    }

    // ─── Reasoning content accumulation ───────────────────────────────────────

    @Nested
    inner class ReasoningContent {

        @Test
        fun `reasoning field is accumulated`() {
            acc.push(TestFactory.makeStreamChunk(reasoning = "Step 1: think"))
            acc.push(TestFactory.makeStreamChunk(reasoning = " Step 2: answer"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "stop"))
            val reasoning = acc.toResponse().choices[0].message.reasoning
            assertNotNull(reasoning)
            assertTrue(reasoning!!.contains("Step 1"))
            assertTrue(reasoning.contains("Step 2"))
        }

        @Test
        fun `reasoning null when no reasoning chunks`() {
            acc.push(TestFactory.makeStreamChunk(content = "plain"))
            assertNull(acc.toResponse().choices[0].message.reasoning)
        }

        @Test
        fun `reasoning and content accumulate independently`() {
            acc.push(TestFactory.makeStreamChunk(content = "Answer: 42", reasoning = "Thinking..."))
            val msg = acc.toResponse().choices[0].message
            assertEquals("Answer: 42", (msg.content as? JsonPrimitive)?.content)
            assertEquals("Thinking...", msg.reasoning)
        }
    }

    // ─── Tool call accumulation ────────────────────────────────────────────────

    @Nested
    inner class ToolCallAccumulation {

        private fun makeToolChunk(
            index: Int = 0,
            id: String? = null,
            name: String? = null,
            arguments: String? = null,
            finishReason: String? = null
        ): StreamChunk = StreamChunk(
            id = "gen-tool",
            model = "test/model",
            created = 1_700_000_000L,
            choices = listOf(
                StreamChoice(
                    index = 0,
                    delta = ChatMessage(
                        role = "assistant",
                        toolCalls = listOf(
                            ToolCall(
                                id = id,
                                type = if (id != null) "function" else null,
                                index = index,
                                function = if (name != null || arguments != null)
                                    FunctionCall(name = name, arguments = arguments)
                                else null
                            )
                        )
                    ),
                    finishReason = finishReason
                )
            )
        )

        @Test
        fun `single tool call is assembled correctly`() {
            acc.push(makeToolChunk(id = "call_abc", name = "get_weather", arguments = "{\""))
            acc.push(makeToolChunk(arguments = "city\":\"NYC\"}"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "tool_calls"))

            val msg = acc.toResponse().choices[0].message
            assertNotNull(msg.toolCalls)
            assertEquals(1, msg.toolCalls!!.size)
            assertEquals("call_abc", msg.toolCalls[0].id)
            assertEquals("get_weather", msg.toolCalls[0].function?.name)
            assertEquals("{\"city\":\"NYC\"}", msg.toolCalls[0].function?.arguments)
        }

        @Test
        fun `multiple tool calls keyed by index`() {
            acc.push(makeToolChunk(index = 0, id = "call_0", name = "fn_a", arguments = "{}"))
            acc.push(makeToolChunk(index = 1, id = "call_1", name = "fn_b", arguments = "{}"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "tool_calls"))

            val toolCalls = acc.toResponse().choices[0].message.toolCalls
            assertNotNull(toolCalls)
            assertEquals(2, toolCalls!!.size)
            assertEquals("fn_a", toolCalls[0].function?.name)
            assertEquals("fn_b", toolCalls[1].function?.name)
        }

        @Test
        fun `no tool calls when none present`() {
            acc.push(TestFactory.makeStreamChunk(content = "Hello"))
            assertNull(acc.toResponse().choices[0].message.toolCalls)
        }

        @Test
        fun `tool call arguments streamed in fragments are joined`() {
            acc.push(makeToolChunk(index = 0, id = "c1", name = "search"))
            acc.push(makeToolChunk(index = 0, arguments = "{\"q\":"))
            acc.push(makeToolChunk(index = 0, arguments = "\"hello\"}"))
            acc.push(TestFactory.makeStreamChunk(finishReason = "tool_calls"))

            val fn = acc.toResponse().choices[0].message.toolCalls!![0].function
            assertEquals("search", fn?.name)
            assertEquals("{\"q\":\"hello\"}", fn?.arguments)
        }
    }

    // ─── Usage capture ────────────────────────────────────────────────────────

    @Nested
    inner class UsageCapture {

        @Test
        fun `usage from final chunk is preserved`() {
            val chunkWithUsage = TestFactory.makeStreamChunk(finishReason = "stop").copy(
                usage = Usage(promptTokens = 10, completionTokens = 20, totalTokens = 30)
            )
            acc.push(chunkWithUsage)
            val usage = acc.toResponse().usage
            assertNotNull(usage)
            assertEquals(10, usage!!.promptTokens)
            assertEquals(20, usage.completionTokens)
        }

        @Test
        fun `usage is null when no usage chunk sent`() {
            acc.push(TestFactory.makeStreamChunk(content = "hello"))
            assertNull(acc.toResponse().usage)
        }
    }

    // ─── Role assignment ──────────────────────────────────────────────────────

    @Nested
    inner class RoleAssignment {

        @Test
        fun `assembled message role is assistant`() {
            acc.push(TestFactory.makeStreamChunk(content = "Hi"))
            assertEquals("assistant", acc.toResponse().choices[0].message.role)
        }
    }
}
