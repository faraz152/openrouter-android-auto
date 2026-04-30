package io.openrouter.android.auto

// ==================== Event Types ====================

/**
 * All event types the SDK can emit.
 * Mirrors OpenRouterEventType from the TypeScript reference (sdk.ts).
 */
enum class ORAEventType {
    MODELS_UPDATED,
    MODEL_ADDED,
    MODEL_REMOVED,
    MODEL_TESTED,
    CONFIG_CHANGED,
    ERROR
}

/**
 * A single event emitted by the SDK.
 */
data class ORAEvent(
    val type: ORAEventType,
    val payload: Any? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Listener callback for SDK events.
 */
typealias EventHandler = (ORAEvent) -> Unit
