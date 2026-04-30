package io.openrouter.android.auto.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Internal singleton holding parsed cost.json registry data.
 * Must be initialized once via [init] before any cost functions are called.
 * In production, called from OpenRouterAuto.Builder; in tests, call directly.
 */
internal object CostRegistry {

    @Volatile
    private var _tierFreeMax: Double? = null

    @Volatile
    private var _tierCheapMax: Double? = null

    @Volatile
    private var _tierModerateMax: Double? = null

    @Volatile
    private var _tierExpensiveMax: Double? = null

    @Volatile
    private var _charsPerToken: Int = 4

    @Volatile
    private var _messageOverheadTokens: Int = 4

    @Volatile
    private var initialized: Boolean = false

    val tierFreeMax: Double? get() = _tierFreeMax
    val tierCheapMax: Double? get() = _tierCheapMax
    val tierModerateMax: Double? get() = _tierModerateMax
    val tierExpensiveMax: Double? get() = _tierExpensiveMax
    val charsPerToken: Int get() = _charsPerToken
    val messageOverheadTokens: Int get() = _messageOverheadTokens

    fun isInitialized(): Boolean = initialized

    fun init(jsonString: String) {
        val root = Json.parseToJsonElement(jsonString).jsonObject

        val tiers = root["price_tiers"]!!.jsonObject
        _tierFreeMax = tiers["free"]!!.jsonObject["max_avg_price"]?.jsonPrimitive?.doubleOrNull
        _tierCheapMax = tiers["cheap"]!!.jsonObject["max_avg_price"]?.jsonPrimitive?.doubleOrNull
        _tierModerateMax = tiers["moderate"]!!.jsonObject["max_avg_price"]?.jsonPrimitive?.doubleOrNull
        _tierExpensiveMax = tiers["expensive"]!!.jsonObject["max_avg_price"]?.jsonPrimitive?.doubleOrNull

        _charsPerToken = root["token_estimate_chars_per_token"]?.jsonPrimitive?.intOrNull ?: 4
        _messageOverheadTokens = root["message_overhead_tokens"]?.jsonPrimitive?.intOrNull ?: 4

        initialized = true
    }

    /** Reset for testing only. */
    internal fun reset() {
        _tierFreeMax = null
        _tierCheapMax = null
        _tierModerateMax = null
        _tierExpensiveMax = null
        _charsPerToken = 4
        _messageOverheadTokens = 4
        initialized = false
    }
}
