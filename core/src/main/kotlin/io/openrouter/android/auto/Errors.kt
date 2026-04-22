package io.openrouter.android.auto

import io.openrouter.android.auto.internal.ErrorRegistry

// ==================== Error Code Enum ====================

/**
 * Typed error codes for every failure category the SDK can produce.
 * Mirrors OpenRouterErrorCode from the TypeScript reference (errors.ts).
 */
enum class ORAErrorCode {
    INVALID_API_KEY,
    RATE_LIMITED,
    MODEL_NOT_FOUND,
    MODEL_UNAVAILABLE,
    INVALID_PARAMETERS,
    INSUFFICIENT_CREDITS,
    PROVIDER_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    UNKNOWN
}

// ==================== Error Class ====================

/**
 * SDK-specific exception carrying a typed [ORAErrorCode] plus retry metadata.
 */
class ORAError(
    val code: ORAErrorCode,
    message: String,
    val details: Any? = null,
    val retryable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Exception(message) {
    override fun toString(): String = formatErrorForDisplay(this)
}

// ==================== Error Mapping ====================

/**
 * Create an [ORAError] from an HTTP status code and optional response body.
 * Mirrors parseOpenRouterError() / createErrorFromResponse() in errors.ts.
 *
 * @param statusCode HTTP status code (e.g. 401, 429)
 * @param body Raw response body — Map, String, or null
 */
fun mapHttpError(statusCode: Int, body: Any? = null): ORAError {
    val statusStr = statusCode.toString()
    var code = ErrorRegistry.codeMap[statusStr] ?: ORAErrorCode.UNKNOWN

    // Extract message text from response body for pattern-based override
    val bodyMessage: String? = when (body) {
        is String -> body.takeIf { it.isNotBlank() }
        is Map<*, *> -> (body["error"] as? Map<*, *>)?.get("message") as? String
            ?: body["message"] as? String
        else -> null
    }

    // Pattern-based code override — mirrors TypeScript error detection logic
    if (bodyMessage != null) {
        val lower = bodyMessage.lowercase()
        code = when {
            lower.contains("credit") || lower.contains("balance") ->
                ORAErrorCode.INSUFFICIENT_CREDITS
            lower.contains("model") && lower.contains("not found") ->
                ORAErrorCode.MODEL_NOT_FOUND
            lower.contains("rate limit") || lower.contains("too many requests") ->
                ORAErrorCode.RATE_LIMITED
            lower.contains("invalid key") || lower.contains("unauthorized") ->
                ORAErrorCode.INVALID_API_KEY
            else -> code
        }
    }

    val baseMessage = ErrorRegistry.messages[code] ?: "Unknown error"
    val fullMessage = if (code == ORAErrorCode.UNKNOWN && bodyMessage != null) {
        "$baseMessage ($bodyMessage)"
    } else {
        baseMessage
    }

    return ORAError(
        code = code,
        message = fullMessage,
        details = body,
        retryable = isRetryable(code)
    )
}

/**
 * Create an [ORAError] from a network-layer [Throwable].
 * Handles timeouts, connection refused/reset, and generic network failures.
 */
fun mapNetworkError(cause: Throwable): ORAError {
    val msg = cause.message?.lowercase() ?: ""
    val code = when {
        msg.contains("timeout") || msg.contains("etimedout") -> ORAErrorCode.TIMEOUT
        msg.contains("connection refused") || msg.contains("econnrefused") ->
            ORAErrorCode.NETWORK_ERROR
        msg.contains("connection reset") || msg.contains("econnreset") ->
            ORAErrorCode.NETWORK_ERROR
        else -> ORAErrorCode.NETWORK_ERROR
    }
    val message = ErrorRegistry.messages[code] ?: "Network error"
    return ORAError(
        code = code,
        message = message,
        details = cause.message,
        retryable = isRetryable(code)
    )
}

// ==================== Retry Helpers ====================

/**
 * Returns true if the given error code is safe to retry.
 */
fun isRetryable(code: ORAErrorCode): Boolean = ErrorRegistry.retryableCodes.contains(code)

/**
 * Exponential back-off delay in milliseconds, capped at 30 seconds.
 *
 * @param attempt 0-based attempt index
 * @param baseDelayMs Starting delay (default 1 000 ms)
 */
fun getRetryDelay(attempt: Int, baseDelayMs: Long = 1_000L): Long {
    require(attempt >= 0) { "attempt must be >= 0, was $attempt" }
    return minOf(baseDelayMs * (1L shl attempt), 30_000L)
}

// ==================== Display ====================

/**
 * Format a human-readable error string with a tip (if registered) and retry hint.
 * Mirrors formatErrorForDisplay() in errors.ts.
 */
fun formatErrorForDisplay(error: ORAError): String {
    val sb = StringBuilder("❌ ${error.message}")
    val tip = ErrorRegistry.tips[error.code.name]
    if (tip != null) sb.append("\n💡 Tip: $tip")
    if (error.retryable) sb.append("\n🔄 This error is retryable.")
    return sb.toString()
}
