package io.openrouter.android.auto

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for Cost.kt — cost calculation, token estimation, price formatting,
 * tier classification, model comparison, and monthly projections.
 * Covers all public functions: calculateCost, estimateTokens, calculateChatCost,
 * formatCost, formatPricePer1K, getPriceTier, isFreeModel, compareModelCosts,
 * getCheapestModel, calculateMonthlyEstimate, getBestFreeModel.
 */
class CostTest {

    private lateinit var model: OpenRouterModel

    @BeforeEach
    fun setUp() {
        TestFactory.initRegistries()
        model = TestFactory.makeModel()
    }

    // ─── calculateCost ────────────────────────────────────────────────────────

    @Nested
    inner class CalculateCost {

        @Test
        fun `prompt cost is correct for known pricing`() {
            // 0.001 per 1K tokens → 1000 tokens = 0.001
            val cost = calculateCost(model, promptTokens = 1000, completionTokens = 0)
            assertEquals(0.001, cost.promptCost, 0.000_0001, "promptCost should be 0.001")
        }

        @Test
        fun `completion cost is correct for known pricing`() {
            // 0.002 per 1K tokens → 500 tokens = 0.001
            val cost = calculateCost(model, promptTokens = 0, completionTokens = 500)
            assertEquals(0.001, cost.completionCost, 0.000_0001, "completionCost should be 0.001")
        }

        @Test
        fun `reasoning tokens billed at completion rate`() {
            // 0.002 per 1K → 200 tokens = 0.0004
            val cost = calculateCost(model, promptTokens = 0, completionTokens = 0, reasoningTokens = 200)
            assertEquals(0.0004, cost.reasoningCost, 0.000_0001, "reasoningCost should be 0.0004")
        }

        @Test
        fun `total cost sums all components`() {
            val cost = calculateCost(model, promptTokens = 1000, completionTokens = 500, reasoningTokens = 100)
            val expected = 0.001 + 0.001 + 0.0002  // prompt + completion + reasoning
            assertEquals(expected, cost.totalCost, 0.000_0001)
        }

        @Test
        fun `zero tokens returns zero cost`() {
            val cost = calculateCost(model, promptTokens = 0, completionTokens = 0)
            assertEquals(0.0, cost.totalCost, 0.0)
        }

        @Test
        fun `free model returns zero cost`() {
            val free = TestFactory.makeFreeModel()
            val cost = calculateCost(free, promptTokens = 1000, completionTokens = 500)
            assertEquals(0.0, cost.totalCost, 0.0)
        }

        @Test
        fun `cost estimate currency defaults to USD`() {
            val cost = calculateCost(model, promptTokens = 100, completionTokens = 100)
            assertEquals("USD", cost.currency)
        }
    }

    // ─── estimateTokens ─────────────────────────────────────────────────────

    @Nested
    inner class EstimateTokens {

        @Test
        fun `empty string returns 0 tokens`() {
            assertEquals(0, estimateTokens(""))
        }

        @Test
        fun `short text returns ceil division`() {
            // cost.json has chars_per_token = 4
            // "Hello world" = 11 chars → ceil(11/4) = 3
            assertEquals(3, estimateTokens("Hello world"))
        }

        @Test
        fun `exact multiple of chars per token`() {
            assertEquals(1, estimateTokens("abcd"))   // 4 chars / 4 = 1
            assertEquals(2, estimateTokens("abcde"))  // 5 chars / 4 = ceil(1.25) = 2
        }
    }

    // ─── calculateChatCost ────────────────────────────────────────────────────

    @Nested
    inner class CalculateChatCost {

        @Test
        fun `single message adds overhead tokens`() {
            val messages = listOf(
                ChatMessage(role = "user", content = JsonPrimitive("Hello"))
            )
            val cost = calculateChatCost(model, messages, expectedResponseTokens = 100)
            // 5 chars → ceil(5/4) = 2 tokens + 4 overhead = 6 prompt tokens
            // Response = 100 tokens
            // promptCost = 6 * 0.001 / 1000, completionCost = 100 * 0.002 / 1000
            assertTrue(cost.promptCost > 0.0)
            assertTrue(cost.completionCost > 0.0)
        }

        @Test
        fun `multiple messages accumulate overhead`() {
            val messages = listOf(
                ChatMessage(role = "system", content = JsonPrimitive("You are helpful")),
                ChatMessage(role = "user", content = JsonPrimitive("Hi"))
            )
            val cost = calculateChatCost(model, messages, expectedResponseTokens = 50)
            // Overhead = 2 messages × 4 = 8 tokens
            assertTrue(cost.promptCost > 0.0)
            assertTrue(cost.completionCost > 0.0)
        }
    }

    // ─── formatCost ──────────────────────────────────────────────────────────

    @Nested
    inner class FormatCost {

        @Test
        fun `zero prints Free`() {
            assertEquals("Free", formatCost(0.0))
        }

        @Test
        fun `tiny amount prints less than`() {
            assertEquals("< \$0.000001", formatCost(0.0000001))
        }

        @Test
        fun `normal amount prints dollar amount`() {
            assertTrue(formatCost(0.005).startsWith("\$"))
            assertTrue(formatCost(0.005).contains("0.005"))
        }
    }

    // ─── formatPricePer1K ────────────────────────────────────────────────────

    @Nested
    inner class FormatPricePer1K {

        @Test
        fun `formats price with slash and tokens`() {
            assertEquals("\$0.001/1K tokens", formatPricePer1K("0.001"))
        }

        @Test
        fun `zero prints Free per 1K`() {
            assertEquals("Free/1K tokens", formatPricePer1K("0"))
        }
    }

    // ─── getPriceTier ───────────────────────────────────────────────────────

    @Nested
    inner class GetPriceTier {

        @Test
        fun `free model tier is FREE`() {
            assertEquals(PriceTier.FREE, getPriceTier(TestFactory.makeFreeModel()))
        }

        @Test
        fun `known priced model is not FREE`() {
            assertNotEquals(PriceTier.FREE, getPriceTier(model))
        }

        @Test
        fun `model with zero prices but no free suffix is still FREE`() {
            val m = TestFactory.makeModel(promptPrice = "0", completionPrice = "0")
            assertEquals(PriceTier.FREE, getPriceTier(m))
        }
    }

    // ─── isFreeModel ──────────────────────────────────────────────────────────

    @Nested
    inner class IsFreeModel {

        @Test
        fun `model with zero prices is free`() {
            val m = TestFactory.makeModel(promptPrice = "0", completionPrice = "0")
            assertTrue(isFreeModel(m))
        }

        @Test
        fun `model with non-zero prices is not free`() {
            assertFalse(isFreeModel(model))
        }

        @Test
        fun `model with free suffix is free`() {
            val m = TestFactory.makeModel(id = "meta/llama:free")
            assertTrue(isFreeModel(m))
        }

        @Test
        fun `free suffixed model with prices still free`() {
            val m = TestFactory.makeModel(id = "x/y:free", promptPrice = "0.001", completionPrice = "0.002")
            assertTrue(isFreeModel(m))
        }
    }

    // ─── compareModelCosts ────────────────────────────────────────────────────

    @Nested
    inner class CompareModelCosts {

        @Test
        fun `sorted ascending by total cost`() {
            val cheap = TestFactory.makeModel(id = "cheap", promptPrice = "0.0001", completionPrice = "0.0001")
            val expensive = TestFactory.makeModel(id = "expensive", promptPrice = "0.01", completionPrice = "0.01")
            val result = compareModelCosts(listOf(expensive, cheap), 1000, 1000)
            assertEquals("cheap", result[0].first.id)
            assertEquals("expensive", result[1].first.id)
        }

        @Test
        fun `free model sorts first`() {
            val free = TestFactory.makeFreeModel(id = "free")
            val paid = TestFactory.makeModel(id = "paid")
            val result = compareModelCosts(listOf(paid, free), 1000, 1000)
            assertEquals("free", result[0].first.id)
        }

        @Test
        fun `empty list returns empty`() {
            assertTrue(compareModelCosts(emptyList(), 1000, 1000).isEmpty())
        }
    }

    // ─── getCheapestModel ─────────────────────────────────────────────────────

    @Nested
    inner class GetCheapestModel {

        @Test
        fun `returns cheapest model`() {
            val cheap = TestFactory.makeModel(id = "a", promptPrice = "0.0001", completionPrice = "0.0001")
            val expensive = TestFactory.makeModel(id = "b", promptPrice = "0.01", completionPrice = "0.01")
            val result = getCheapestModel(listOf(expensive, cheap), 1000, 1000)
            assertEquals("a", result!!.id)
        }

        @Test
        fun `returns null for empty list`() {
            assertNull(getCheapestModel(emptyList(), 1000, 1000))
        }
    }

    // ─── calculateMonthlyEstimate ────────────────────────────────────────────

    @Nested
    inner class CalculateMonthlyEstimate {

        @Test
        fun `monthly is daily times 30`() {
            val daily = calculateCost(model, 1000, 500).totalCost
            val monthly = calculateMonthlyEstimate(model, dailyRequests = 1, avgPromptTokens = 1000, avgCompletionTokens = 500)
            assertEquals(daily * 30, monthly.totalCost, 0.000_0001)
        }

        @Test
        fun `multiple daily requests multiply correctly`() {
            val monthly = calculateMonthlyEstimate(model, dailyRequests = 10, avgPromptTokens = 1000, avgCompletionTokens = 500)
            val single = calculateMonthlyEstimate(model, dailyRequests = 1, avgPromptTokens = 1000, avgCompletionTokens = 500)
            assertEquals(single.totalCost * 10, monthly.totalCost, 0.000_0001)
        }

        @Test
        fun `zero daily requests returns zero`() {
            val monthly = calculateMonthlyEstimate(model, dailyRequests = 0, avgPromptTokens = 1000, avgCompletionTokens = 500)
            assertEquals(0.0, monthly.totalCost, 0.0)
        }
    }

    // ─── getBestFreeModel ────────────────────────────────────────────────────

    @Nested
    inner class GetBestFreeModel {

        @Test
        fun `returns free model with largest context`() {
            val small = TestFactory.makeFreeModel(id = "small").copy(contextLength = 2048)
            val large = TestFactory.makeFreeModel(id = "large").copy(contextLength = 8192)
            assertEquals("large", getBestFreeModel(listOf(small, large))!!.id)
        }

        @Test
        fun `returns null when no free models`() {
            assertNull(getBestFreeModel(listOf(model)))
        }

        @Test
        fun `returns null for empty list`() {
            assertNull(getBestFreeModel(emptyList()))
        }
    }
}
