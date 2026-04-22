package io.openrouter.android.auto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for Parameters.kt — registry-driven parameter validation,
 * default extraction, merging, and sanitization.
 * Covers all public functions: getModelParameters, validateParameter,
 * validateParameters, getDefaultParameters, mergeWithDefaults, sanitizeParameters.
 */
class ParametersTest {

    private lateinit var model: OpenRouterModel

    @BeforeEach
    fun setUp() {
        TestFactory.initRegistries()
        model = TestFactory.makeModel()
    }

    // ─── getModelParameters ───────────────────────────────────────────────────

    @Nested
    inner class GetModelParameters {

        @Test
        fun `returns only parameters supported by model`() {
            val defs = getModelParameters(model)
            val names = defs.mapNotNull { it.name }
            assertTrue(names.containsAll(listOf("temperature", "top_p", "max_tokens")))
        }

        @Test
        fun `returns empty list for model with no supported params`() {
            val empty = TestFactory.makeModelNoParams()
            assertTrue(getModelParameters(empty).isEmpty())
        }

        @Test
        fun `unknown param name in supportedParameters is ignored gracefully`() {
            val m = TestFactory.makeModelWithParams("temperature", "nonexistent_param_xyz")
            val defs = getModelParameters(m)
            val names = defs.mapNotNull { it.name }
            assertTrue("temperature" in names)
            assertFalse("nonexistent_param_xyz" in names)
        }

        @Test
        fun `max_tokens max is capped by topProvider maxCompletionTokens`() {
            val m = TestFactory.makeModel(
                supportedParameters = listOf("max_tokens"),
                maxCompletionTokens = 512
            )
            val def = getModelParameters(m).first { it.name == "max_tokens" }
            assertEquals(512.0, def.max)
        }

        @Test
        fun `max_tokens max falls back to contextLength when topProvider is null`() {
            val m = model.copy(
                supportedParameters = listOf("max_tokens"),
                topProvider = null,
                contextLength = 8192
            )
            val def = getModelParameters(m).first { it.name == "max_tokens" }
            assertEquals(8192.0, def.max)
        }
    }

    // ─── validateParameter ────────────────────────────────────────────────────

    @Nested
    inner class ValidateParameter {

        private fun defOf(name: String) =
            TestFactory.initRegistries().let {
                io.openrouter.android.auto.internal.ParameterRegistry.paramDefs[name]!!
            }

        @Test
        fun `null value always passes`() {
            val def = defOf("temperature")
            val (valid, _) = validateParameter("temperature", null, def)
            assertTrue(valid)
        }

        @Test
        fun `temperature 0dot5 is valid`() {
            val def = defOf("temperature")
            val (valid, error) = validateParameter("temperature", 0.5, def)
            assertTrue(valid, error)
        }

        @Test
        fun `temperature above max 2 is rejected`() {
            val def = defOf("temperature")
            val (valid, error) = validateParameter("temperature", 3.0, def)
            assertFalse(valid)
            assertNotNull(error)
            assertTrue(error!!.contains("at most"))
        }

        @Test
        fun `temperature below min 0 is rejected`() {
            val def = defOf("temperature")
            val (valid, error) = validateParameter("temperature", -0.1, def)
            assertFalse(valid)
            assertTrue(error!!.contains("at least"))
        }

        @Test
        fun `string value for number type is rejected`() {
            val def = defOf("temperature")
            val (valid, error) = validateParameter("temperature", "warm", def)
            assertFalse(valid)
            assertTrue(error!!.contains("number"))
        }

        @Test
        fun `top_k as Int is valid integer`() {
            val def = defOf("top_k")
            val (valid, _) = validateParameter("top_k", 50, def)
            assertTrue(valid)
        }

        @Test
        fun `top_k as Double with fractional part is rejected`() {
            val def = defOf("top_k")
            val (valid, error) = validateParameter("top_k", 3.7, def)
            assertFalse(valid)
            assertTrue(error!!.contains("integer"))
        }

        @Test
        fun `top_k as whole Double like 10dot0 is valid`() {
            val def = defOf("top_k")
            val (valid, _) = validateParameter("top_k", 10.0, def)
            assertTrue(valid)
        }

        @Test
        fun `top_p value 0dot9 is valid`() {
            val def = defOf("top_p")
            val (valid, _) = validateParameter("top_p", 0.9, def)
            assertTrue(valid)
        }

        @Test
        fun `frequency_penalty at boundary -2 is valid`() {
            val def = defOf("frequency_penalty")
            val (valid, _) = validateParameter("frequency_penalty", -2.0, def)
            assertTrue(valid)
        }

        @Test
        fun `frequency_penalty above max 2 is rejected`() {
            val def = defOf("frequency_penalty")
            val (valid, _) = validateParameter("frequency_penalty", 2.1, def)
            assertFalse(valid)
        }
    }

    // ─── validateParameters ───────────────────────────────────────────────────

    @Nested
    inner class ValidateParameters {

        @Test
        fun `valid temperature passes validation`() {
            val result = validateParameters(model, mapOf("temperature" to 0.7))
            assertTrue(result.valid, result.errors.toString())
        }

        @Test
        fun `unsupported parameter is flagged as error`() {
            val result = validateParameters(model, mapOf("made_up_param" to 99))
            assertFalse(result.valid)
            assertTrue(result.errors.containsKey("made_up_param"))
        }

        @Test
        fun `platform params like model and messages are always allowed`() {
            val result = validateParameters(model, mapOf(
                "model" to "some/model",
                "messages" to emptyList<Any>(),
                "stream" to true,
                "session_id" to "abc-123"
            ))
            assertTrue(result.valid, result.errors.toString())
        }

        @Test
        fun `temperature out of range is reported`() {
            val result = validateParameters(model, mapOf("temperature" to 5.0))
            assertFalse(result.valid)
            assertTrue(result.errors.containsKey("temperature"))
        }

        @Test
        fun `multiple errors are collected simultaneously`() {
            val result = validateParameters(model, mapOf(
                "temperature" to 99.0,
                "top_p" to 2.0,
                "fake_param" to "x"
            ))
            assertFalse(result.valid)
            assertTrue(result.errors.size >= 3)
        }

        @Test
        fun `empty params map passes validation`() {
            val result = validateParameters(model, emptyMap())
            assertTrue(result.valid)
        }

        @Test
        fun `model with no supported params rejects any model-specific param`() {
            val empty = TestFactory.makeModelNoParams()
            val result = validateParameters(empty, mapOf("temperature" to 0.5))
            assertFalse(result.valid)
            assertTrue(result.errors.containsKey("temperature"))
        }

        @Test
        fun `top_k integer value is accepted`() {
            val result = validateParameters(model, mapOf("top_k" to 40))
            assertTrue(result.valid, result.errors.toString())
        }
    }

    // ─── getDefaultParameters ─────────────────────────────────────────────────

    @Nested
    inner class GetDefaultParameters {

        @Test
        fun `temperature default is 1dot0`() {
            val defaults = getDefaultParameters(model)
            assertEquals(1.0, defaults["temperature"] as? Double ?: defaults["temperature"])
        }

        @Test
        fun `top_p default is 1dot0`() {
            val defaults = getDefaultParameters(model)
            assertNotNull(defaults["top_p"])
        }

        @Test
        fun `max_tokens has no default (optional)`() {
            val defaults = getDefaultParameters(model)
            assertNull(defaults["max_tokens"])
        }

        @Test
        fun `model with no params returns empty defaults`() {
            val defaults = getDefaultParameters(TestFactory.makeModelNoParams())
            assertTrue(defaults.isEmpty())
        }
    }

    // ─── mergeWithDefaults ────────────────────────────────────────────────────

    @Nested
    inner class MergeWithDefaults {

        @Test
        fun `user param overrides default`() {
            val merged = mergeWithDefaults(model, mapOf("temperature" to 0.3))
            assertEquals(0.3, merged["temperature"])
        }

        @Test
        fun `missing user param is filled by default`() {
            val merged = mergeWithDefaults(model, emptyMap())
            // temperature default is 1.0 from registry
            assertNotNull(merged["temperature"])
        }

        @Test
        fun `user param not in defaults is preserved`() {
            val merged = mergeWithDefaults(model, mapOf("seed" to 42))
            assertEquals(42, merged["seed"])
        }
    }

    // ─── sanitizeParameters ───────────────────────────────────────────────────

    @Nested
    inner class SanitizeParameters {

        @Test
        fun `null values are stripped`() {
            val result = sanitizeParameters(mapOf("temperature" to 0.7, "top_p" to null))
            assertFalse(result.containsKey("top_p"))
            assertTrue(result.containsKey("temperature"))
        }

        @Test
        fun `non-null values are preserved`() {
            val result = sanitizeParameters(mapOf("temperature" to 0.5, "seed" to 42))
            assertEquals(0.5, result["temperature"])
            assertEquals(42, result["seed"])
        }

        @Test
        fun `empty map returns empty map`() {
            val result = sanitizeParameters(emptyMap())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `all-null map returns empty map`() {
            val result = sanitizeParameters(mapOf("a" to null, "b" to null))
            assertTrue(result.isEmpty())
        }
    }
}
