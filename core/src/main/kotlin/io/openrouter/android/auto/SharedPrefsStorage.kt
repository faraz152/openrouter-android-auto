package io.openrouter.android.auto

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Android SharedPreferences-backed storage.
 *
 * Security: strips any JSON fields matching API key patterns before persisting.
 * Requires an Android [Context].
 */
class SharedPrefsStorage(
    context: Context,
    preferencesName: String = "openrouter_auto_storage"
) : StorageAdapter {

    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        val safeValue = stripSensitiveJson(value)
        prefs.edit().putString(key, safeValue).apply()
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    companion object {
        /**
         * Strip keys matching `apiKey`, `api_key`, `Authorization`, etc.
         * from a JSON object string before persisting to SharedPreferences.
         * Returns the original string unchanged if it is not a JSON object.
         */
        internal fun stripSensitiveJson(value: String): String {
            val trimmed = value.trim()
            if (!trimmed.startsWith("{")) return value

            try {
                val obj = Json.parseToJsonElement(trimmed).jsonObject
                val filtered = obj.filterKeys { key ->
                    !SENSITIVE_KEY_PATTERN.matches(key)
                }
                return Json.encodeToString(JsonObject.serializer(), JsonObject(filtered))
            } catch (_: Exception) {
                // Not valid JSON — return as-is
                return value
            }
        }
    }
}
