package io.openrouter.android.auto.internal

import io.openrouter.android.auto.ParameterDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Internal singleton holding parsed parameters.json + platform_params.json registry data.
 * Must be initialized once via [init] before any parameter-validation functions are called.
 * In production, called from OpenRouterAuto.Builder; in tests, call directly.
 */
internal object ParameterRegistry {

    @Volatile private var _paramDefs: Map<String, ParameterDef> = emptyMap()
    @Volatile private var _platformParams: Set<String> = emptySet()
    @Volatile private var initialized: Boolean = false

    val paramDefs: Map<String, ParameterDef> get() = _paramDefs
    val platformParams: Set<String> get() = _platformParams

    fun isInitialized(): Boolean = initialized

    fun init(paramsJson: String, platformParamsJson: String) {
        val root = Json.parseToJsonElement(paramsJson).jsonObject

        _paramDefs = root.entries.associate { (name, defElem) ->
            val defObj = defElem.jsonObject
            name to ParameterDef(
                name = name,
                type = defObj["type"]!!.jsonPrimitive.content,
                description = defObj["description"]?.jsonPrimitive?.content,
                default = defObj["default"],
                min = defObj["min"]?.jsonPrimitive?.doubleOrNull,
                max = defObj["max"]?.jsonPrimitive?.doubleOrNull,
                enum = defObj["enum"]?.jsonArray?.toList(),
                required = defObj["required"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }

        val platformArray = Json.parseToJsonElement(platformParamsJson).jsonArray
        _platformParams = platformArray.map { it.jsonPrimitive.content }.toSet()

        initialized = true
    }

    /** Reset for testing only. */
    internal fun reset() {
        _paramDefs = emptyMap()
        _platformParams = emptySet()
        initialized = false
    }
}
