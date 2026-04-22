package io.openrouter.android.auto

import io.openrouter.android.auto.internal.ParameterRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Result of a full parameter validation pass.
 * @property valid True when no errors were found.
 * @property errors Map of parameter name → error message.
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: Map<String, String>
)

// ==================== Per-Model Parameter Definitions ====================

/**
 * Return [ParameterDef] list for the parameters supported by [model].
 * Adjusts `max` for max_tokens / max_completion_tokens from the model's topProvider.
 * Mirrors getModelParameters() in parameters.ts.
 */
fun getModelParameters(model: OpenRouterModel): List<ParameterDef> {
    val supported = model.supportedParameters ?: return emptyList()
    return supported.mapNotNull { name ->
        val def = ParameterRegistry.paramDefs[name] ?: return@mapNotNull null
        when {
            (name == "max_tokens" || name == "max_completion_tokens") &&
                    model.topProvider?.maxCompletionTokens != null ->
                def.copy(max = model.topProvider.maxCompletionTokens.toDouble())

            name == "max_tokens" && model.contextLength > 0 ->
                def.copy(max = model.contextLength.toDouble())

            else -> def
        }
    }
}

// ==================== Single-Parameter Validation ====================

/**
 * Validate a single parameter value against its [ParameterDef].
 * Returns a pair of (isValid, errorMessage?).
 * Mirrors validateParameter() in parameters.ts.
 */
fun validateParameter(name: String, value: Any?, def: ParameterDef): Pair<Boolean, String?> {
    if (value == null) return true to null

    return when (def.type) {
        "number" -> {
            val num = (value as? Number)?.toDouble()
                ?: return false to "$name must be a number"
            when {
                def.min != null && num < def.min -> false to "$name must be at least ${def.min}"
                def.max != null && num > def.max -> false to "$name must be at most ${def.max}"
                else -> true to null
            }
        }
        "integer" -> {
            val num: Long = when (value) {
                is Int -> value.toLong()
                is Long -> value
                is Double -> if (value == kotlin.math.floor(value)) value.toLong()
                    else return false to "$name must be an integer"
                else -> return false to "$name must be an integer"
            }
            when {
                def.min != null && num < def.min -> false to "$name must be at least ${def.min}"
                def.max != null && num > def.max -> false to "$name must be at most ${def.max}"
                else -> true to null
            }
        }
        "boolean" ->
            if (value is Boolean) true to null
            else false to "$name must be a boolean"
        "string" ->
            if (value is String) true to null
            else false to "$name must be a string"
        "array" ->
            if (value is List<*>) true to null
            else false to "$name must be an array"
        else -> true to null
    }
}

// ==================== Full Request Validation ====================

/**
 * Validate all [params] against the model's supported parameter set.
 * Platform-level params (model, messages, stream, etc.) are always allowed.
 * Mirrors validateParameters() in parameters.ts.
 */
fun validateParameters(
    model: OpenRouterModel,
    params: Map<String, Any?>
): ValidationResult {
    val errors = mutableMapOf<String, String>()
    val supported = model.supportedParameters ?: emptyList()

    // Flag unknown parameters (not supported by model and not a platform param)
    for (key in params.keys) {
        if (key !in supported && key !in ParameterRegistry.platformParams) {
            errors[key] = "Parameter '$key' is not supported by this model"
        }
    }

    // Validate value ranges / types for supported params
    val defs = getModelParameters(model)
    for (def in defs) {
        val defName = def.name ?: continue
        val value = params[defName] ?: continue
        val (valid, error) = validateParameter(defName, value, def)
        if (!valid && error != null) errors[defName] = error
    }

    return ValidationResult(errors.isEmpty(), errors)
}

// ==================== Defaults & Sanitization ====================

/**
 * Return a map of default parameter values for [model]'s supported params.
 * Mirrors getDefaultParameters() in parameters.ts.
 */
fun getDefaultParameters(model: OpenRouterModel): Map<String, Any> {
    val defaults = mutableMapOf<String, Any>()
    for (def in getModelParameters(model)) {
        val defName = def.name ?: continue
        val defaultElem = def.default ?: continue
        if (defaultElem !is JsonPrimitive) continue
        val coerced: Any = when {
            defaultElem.isString -> defaultElem.content
            defaultElem.booleanOrNull != null -> defaultElem.booleanOrNull!!
            defaultElem.intOrNull != null -> defaultElem.intOrNull!!
            defaultElem.doubleOrNull != null -> defaultElem.doubleOrNull!!
            else -> defaultElem.content
        }
        defaults[defName] = coerced
    }
    return defaults
}

/**
 * Merge [userParams] on top of defaults for [model].
 * User-supplied values always win. Mirrors mergeWithDefaults() in parameters.ts.
 */
fun mergeWithDefaults(
    model: OpenRouterModel,
    userParams: Map<String, Any?>
): Map<String, Any?> = getDefaultParameters(model) + userParams

/**
 * Strip null / null-equivalent entries from [params].
 * Mirrors sanitizeParameters() in parameters.ts.
 */
fun sanitizeParameters(params: Map<String, Any?>): Map<String, Any> =
    params.entries
        .filter { it.value != null }
        .associate { it.key to it.value!! }
