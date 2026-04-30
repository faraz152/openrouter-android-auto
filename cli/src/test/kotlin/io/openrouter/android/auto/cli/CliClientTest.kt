package io.openrouter.android.auto.cli

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CliClientTest {

    private fun makeMockEngine(
        modelsJson: String = """{"data":[{"id":"test/model","name":"Test Model","context_length":4096,"pricing":{"prompt":"0.001","completion":"0.002"}}]}""",
        chatJson: String = """{"id":"gen-123","choices":[{"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}]}""",
        sseBody: String? = null,
        errorStatus: HttpStatusCode? = null
    ): MockEngine = MockEngine { request ->
        when {
            errorStatus != null -> respondError(errorStatus)

            request.url.encodedPath.endsWith("/models") -> respond(
                content = modelsJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )

            request.url.encodedPath.endsWith("/chat/completions") -> {
                val body = request.body.toByteArray().decodeToString()
                if (body.contains("\"stream\":true") && sseBody != null) {
                    respond(
                        content = sseBody,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
                    )
                } else {
                    respond(
                        content = chatJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }

            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    @Nested
    inner class FetchModels {

        @Test
        fun `fetchModels returns list from API`() = runTest {
            val client = CliClient("test-key", engine = makeMockEngine())
            val models = client.fetchModels()
            assertEquals(1, models.size)
            assertEquals("test/model", models[0].id)
            client.close()
        }

        @Test
        fun `fetchModels with multiple models`() = runTest {
            val json = """{"data":[
                {"id":"a/b","name":"AB","context_length":4096,"pricing":{"prompt":"0","completion":"0"}},
                {"id":"c/d","name":"CD","context_length":8192,"pricing":{"prompt":"0.01","completion":"0.02"}}
            ]}""".trimIndent()
            val client = CliClient("test-key", engine = makeMockEngine(modelsJson = json))
            val models = client.fetchModels()
            assertEquals(2, models.size)
            client.close()
        }

        @Test
        fun `fetchModels throws on error`() = runTest {
            val client = CliClient("bad-key", engine = makeMockEngine(errorStatus = HttpStatusCode.Unauthorized))
            assertThrows(RuntimeException::class.java) { runTest { client.fetchModels() } }
            client.close()
        }
    }

    @Nested
    inner class Chat {

        @Test
        fun `chat returns response`() = runTest {
            val client = CliClient("test-key", engine = makeMockEngine())
            val response = client.chat(ChatRequest(
                model = "test/model",
                messages = listOf(ChatMessage("user", JsonPrimitive("Hi")))
            ))
            assertNotNull(response.choices.firstOrNull())
            val content = response.choices[0].message?.content
            assertTrue(content.toString().contains("Hello"))
            client.close()
        }

        @Test
        fun `chat throws on error`() = runTest {
            val client = CliClient("test-key", engine = makeMockEngine(errorStatus = HttpStatusCode.InternalServerError))
            val ex = runCatching {
                client.chat(ChatRequest(
                    model = "test/model",
                    messages = listOf(ChatMessage("user", JsonPrimitive("Hi")))
                ))
            }.exceptionOrNull()
            assertNotNull(ex)
            client.close()
        }
    }

    @Nested
    inner class StreamChat {

        @Test
        fun `streamChat emits content tokens`() = runTest {
            val sseBody = """
                data: {"id":"gen-1","choices":[{"delta":{"content":"Hel"},"finish_reason":null}]}

                data: {"id":"gen-1","choices":[{"delta":{"content":"lo!"},"finish_reason":null}]}

                data: {"id":"gen-1","choices":[{"delta":{"content":""},"finish_reason":"stop"}]}

                data: [DONE]

            """.trimIndent()

            val client = CliClient("test-key", engine = makeMockEngine(sseBody = sseBody))
            val tokens = client.streamChat(ChatRequest(
                model = "test/model",
                messages = listOf(ChatMessage("user", JsonPrimitive("Hi"))),
                stream = true
            )).toList()

            assertEquals(2, tokens.size)
            assertEquals("Hel", tokens[0])
            assertEquals("lo!", tokens[1])
            client.close()
        }

        @Test
        fun `streamChat handles empty body gracefully`() = runTest {
            val client = CliClient("test-key", engine = makeMockEngine(sseBody = "data: [DONE]\n\n"))
            val tokens = client.streamChat(ChatRequest(
                model = "test/model",
                messages = listOf(ChatMessage("user", JsonPrimitive("Hi"))),
                stream = true
            )).toList()
            assertEquals(0, tokens.size)
            client.close()
        }
    }

    @Nested
    inner class HelperFunctions {

        @Test
        fun `formatContextLength formats correctly`() {
            assertEquals("4K", formatContextLength(4096))
            assertEquals("128K", formatContextLength(128_000))
            assertEquals("1.0M", formatContextLength(1_000_000))
            assertEquals("100", formatContextLength(100))
            assertEquals("—", formatContextLength(null))
        }

        @Test
        fun `formatPrice formats correctly`() {
            assertEquals("Free", formatPrice("0"))
            assertEquals("$0.001/1K", formatPrice("0.001"))
        }

        @Test
        fun `cliJson ignores unknown keys`() {
            val json = """{"id":"test","name":"Test","unknown_field":42}"""
            val model = cliJson.decodeFromString(OpenRouterModel.serializer(), json)
            assertEquals("test", model.id)
        }
    }
}
