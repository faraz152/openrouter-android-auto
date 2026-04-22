package io.openrouter.android.auto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for Errors.kt — registry-driven error mapping, retry helpers, display formatting.
 * Covers every public function: mapHttpError, mapNetworkError, isRetryable,
 * getRetryDelay, formatErrorForDisplay, and ORAError construction.
 */
class ErrorsTest {

    @BeforeEach
    fun setUp() {
        TestFactory.initRegistries()
    }

    // ─── mapHttpError — HTTP status code mapping ─────────────────────────────

    @Nested
    inner class MapHttpError {

        @Test
        fun `401 maps to INVALID_API_KEY`() {
            val error = mapHttpError(401)
            assertEquals(ORAErrorCode.INVALID_API_KEY, error.code)
        }

        @Test
        fun `403 maps to INVALID_API_KEY`() {
            val error = mapHttpError(403)
            assertEquals(ORAErrorCode.INVALID_API_KEY, error.code)
        }

        @Test
        fun `429 maps to RATE_LIMITED`() {
            val error = mapHttpError(429)
            assertEquals(ORAErrorCode.RATE_LIMITED, error.code)
        }

        @Test
        fun `404 maps to MODEL_NOT_FOUND`() {
            val error = mapHttpError(404)
            assertEquals(ORAErrorCode.MODEL_NOT_FOUND, error.code)
        }

        @Test
        fun `400 maps to INVALID_PARAMETERS`() {
            val error = mapHttpError(400)
            assertEquals(ORAErrorCode.INVALID_PARAMETERS, error.code)
        }

        @Test
        fun `402 maps to INSUFFICIENT_CREDITS`() {
            val error = mapHttpError(402)
            assertEquals(ORAErrorCode.INSUFFICIENT_CREDITS, error.code)
        }

        @ParameterizedTest
        @ValueSource(ints = [500, 502, 503, 504])
        fun `5xx maps to PROVIDER_ERROR`(statusCode: Int) {
            val error = mapHttpError(statusCode)
            assertEquals(ORAErrorCode.PROVIDER_ERROR, error.code)
        }

        @Test
        fun `unknown status code maps to UNKNOWN`() {
            val error = mapHttpError(418)
            assertEquals(ORAErrorCode.UNKNOWN, error.code)
        }

        @Test
        fun `message contains credit overrides code to INSUFFICIENT_CREDITS`() {
            val error = mapHttpError(400, mapOf("error" to mapOf("message" to "Insufficient credit balance")))
            assertEquals(ORAErrorCode.INSUFFICIENT_CREDITS, error.code)
        }

        @Test
        fun `message contains model not found overrides code`() {
            val error = mapHttpError(400, mapOf("message" to "The model 'x/y' was not found"))
            assertEquals(ORAErrorCode.MODEL_NOT_FOUND, error.code)
        }

        @Test
        fun `message contains rate limit overrides code`() {
            val error = mapHttpError(400, mapOf("message" to "Rate limit exceeded"))
            assertEquals(ORAErrorCode.RATE_LIMITED, error.code)
        }

        @Test
        fun `message contains unauthorized overrides code`() {
            val error = mapHttpError(400, mapOf("message" to "Invalid key — unauthorized"))
            assertEquals(ORAErrorCode.INVALID_API_KEY, error.code)
        }

        @Test
        fun `error message is populated from registry`() {
            val error = mapHttpError(401)
            assertTrue(error.message!!.isNotBlank(), "Error message should be non-blank")
            assertTrue(error.message!!.contains("API key", ignoreCase = true))
        }

        @Test
        fun `UNKNOWN error appends body message`() {
            val error = mapHttpError(418, mapOf("message" to "I'm a teapot"))
            assertTrue(error.message!!.contains("teapot", ignoreCase = true))
        }

        @Test
        fun `retryable flag is set correctly for retryable codes`() {
            val error = mapHttpError(429)
            assertTrue(error.retryable)
        }

        @Test
        fun `retryable flag is false for non-retryable code`() {
            val error = mapHttpError(401)
            assertFalse(error.retryable)
        }
    }

    // ─── mapNetworkError ─────────────────────────────────────────────────────

    @Nested
    inner class MapNetworkError {

        @Test
        fun `timeout exception maps to TIMEOUT`() {
            val error = mapNetworkError(RuntimeException("Request timeout"))
            assertEquals(ORAErrorCode.TIMEOUT, error.code)
        }

        @Test
        fun `etimedout maps to TIMEOUT`() {
            val error = mapNetworkError(RuntimeException("etimedout: connection timed out"))
            assertEquals(ORAErrorCode.TIMEOUT, error.code)
        }

        @Test
        fun `connection refused maps to NETWORK_ERROR`() {
            val error = mapNetworkError(RuntimeException("ECONNREFUSED: connection refused"))
            assertEquals(ORAErrorCode.NETWORK_ERROR, error.code)
        }

        @Test
        fun `connection reset maps to NETWORK_ERROR`() {
            val error = mapNetworkError(RuntimeException("ECONNRESET: connection reset by peer"))
            assertEquals(ORAErrorCode.NETWORK_ERROR, error.code)
        }

        @Test
        fun `generic network exception maps to NETWORK_ERROR`() {
            val error = mapNetworkError(RuntimeException("Unknown socket error"))
            assertEquals(ORAErrorCode.NETWORK_ERROR, error.code)
        }

        @Test
        fun `timeout is retryable`() {
            val error = mapNetworkError(RuntimeException("timeout"))
            assertTrue(error.retryable)
        }

        @Test
        fun `NETWORK_ERROR is retryable`() {
            val error = mapNetworkError(RuntimeException("connection refused"))
            assertTrue(error.retryable)
        }
    }

    // ─── isRetryable ─────────────────────────────────────────────────────────

    @Nested
    inner class IsRetryable {

        @ParameterizedTest
        @CsvSource(
            "RATE_LIMITED, true",
            "PROVIDER_ERROR, true",
            "NETWORK_ERROR, true",
            "TIMEOUT, true",
            "MODEL_UNAVAILABLE, true",
            "INVALID_API_KEY, false",
            "INSUFFICIENT_CREDITS, false",
            "INVALID_PARAMETERS, false",
            "MODEL_NOT_FOUND, false",
            "UNKNOWN, false"
        )
        fun `retryable status matches registry`(codeStr: String, expectedRetryable: Boolean) {
            val code = ORAErrorCode.valueOf(codeStr)
            assertEquals(expectedRetryable, isRetryable(code))
        }
    }

    // ─── getRetryDelay ───────────────────────────────────────────────────────

    @Nested
    inner class GetRetryDelay {

        @Test
        fun `attempt 0 returns baseDelay`() {
            assertEquals(1_000L, getRetryDelay(0, 1_000L))
        }

        @Test
        fun `attempt 1 doubles base`() {
            assertEquals(2_000L, getRetryDelay(1, 1_000L))
        }

        @Test
        fun `attempt 2 quadruples base`() {
            assertEquals(4_000L, getRetryDelay(2, 1_000L))
        }

        @Test
        fun `delay is capped at 30 seconds`() {
            val delay = getRetryDelay(20, 1_000L)
            assertEquals(30_000L, delay)
        }

        @Test
        fun `negative attempt throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                getRetryDelay(-1)
            }
        }
    }

    // ─── formatErrorForDisplay ───────────────────────────────────────────────

    @Nested
    inner class FormatErrorForDisplay {

        @Test
        fun `output starts with cross emoji and message`() {
            val error = ORAError(ORAErrorCode.UNKNOWN, "Test message", retryable = false)
            val display = formatErrorForDisplay(error)
            assertTrue(display.startsWith("❌"))
            assertTrue(display.contains("Test message"))
        }

        @Test
        fun `tip is included for codes with registered tips`() {
            val error = ORAError(ORAErrorCode.INVALID_API_KEY, "bad key", retryable = false)
            val display = formatErrorForDisplay(error)
            assertTrue(display.contains("💡"), "Should include tip")
            assertTrue(display.contains("openrouter.ai/keys", ignoreCase = true))
        }

        @Test
        fun `retryable error includes retry hint`() {
            val error = ORAError(ORAErrorCode.RATE_LIMITED, "rate limited", retryable = true)
            val display = formatErrorForDisplay(error)
            assertTrue(display.contains("🔄"))
        }

        @Test
        fun `non-retryable error has no retry hint`() {
            val error = ORAError(ORAErrorCode.INVALID_API_KEY, "bad key", retryable = false)
            val display = formatErrorForDisplay(error)
            assertFalse(display.contains("🔄"))
        }
    }

    // ─── ORAError class ───────────────────────────────────────────────────────

    @Nested
    inner class ORAErrorClass {

        @Test
        fun `can be thrown and caught as Exception`() {
            val caught = runCatching<Unit> { throw ORAError(ORAErrorCode.UNKNOWN, "oops") }
            assertTrue(caught.exceptionOrNull() is ORAError)
        }

        @Test
        fun `message is accessible via exception getMessage`() {
            val error = ORAError(ORAErrorCode.TIMEOUT, "timed out")
            assertEquals("timed out", error.message)
        }

        @Test
        fun `code is stored correctly`() {
            val error = ORAError(ORAErrorCode.MODEL_NOT_FOUND, "not found")
            assertEquals(ORAErrorCode.MODEL_NOT_FOUND, error.code)
        }

        @Test
        fun `details are stored without modification`() {
            val details = mapOf("status" to 404, "body" to "not found")
            val error = ORAError(ORAErrorCode.MODEL_NOT_FOUND, "not found", details = details)
            assertEquals(details, error.details)
        }

        @Test
        fun `timestamp is set on construction`() {
            val before = System.currentTimeMillis()
            val error = ORAError(ORAErrorCode.UNKNOWN, "err")
            val after = System.currentTimeMillis()
            assertTrue(error.timestamp in before..after)
        }
    }
}
