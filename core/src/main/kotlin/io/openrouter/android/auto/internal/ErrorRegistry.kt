package io.openrouter.android.auto.internal

import io.openrouter.android.auto.ORAErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Internal singleton holding parsed errors.json registry data.
 * Must be initialized once via [init] before any error-mapping functions are called.
 * In production, called from OpenRouterAuto.Builder; in tests, call directly.
 */
internal object ErrorRegistry {

    @Volatile private var _codeMap: Map<String, ORAErrorCode> = emptyMap()
    @Volatile private var _messages: Map<ORAErrorCode, String> = emptyMap()
    @Volatile private var _tips: Map<String, String> = emptyMap()
    @Volatile private var _retryableCodes: Set<ORAErrorCode> = emptySet()
    @Volatile private var initialized: Boolean = false

    val codeMap: Map<String, ORAErrorCode> get() = _codeMap
    val messages: Map<ORAErrorCode, String> get() = _messages
    val tips: Map<String, String> get() = _tips
    val retryableCodes: Set<ORAErrorCode> get() = _retryableCodes

    fun isInitialized(): Boolean = initialized

    fun init(jsonString: String) {
        val root = Json.parseToJsonElement(jsonString).jsonObject

        _codeMap = root["code_map"]!!.jsonObject.entries.mapNotNull { (k, v) ->
            val code = ORAErrorCode.entries.find { it.name == v.jsonPrimitive.content }
            if (code != null) k to code else null
        }.toMap()

        _messages = root["messages"]!!.jsonObject.entries.mapNotNull { (k, v) ->
            val code = ORAErrorCode.entries.find { it.name == k }
            if (code != null) code to v.jsonPrimitive.content else null
        }.toMap()

        _tips = root["tips"]!!.jsonObject.entries.associate { (k, v) ->
            k to v.jsonPrimitive.content
        }

        _retryableCodes = root["retryable"]!!.jsonArray.mapNotNull { elem ->
            ORAErrorCode.entries.find { it.name == elem.jsonPrimitive.content }
        }.toSet()

        initialized = true
    }

    /** Reset for testing only. */
    internal fun reset() {
        _codeMap = emptyMap()
        _messages = emptyMap()
        _tips = emptyMap()
        _retryableCodes = emptySet()
        initialized = false
    }
}
