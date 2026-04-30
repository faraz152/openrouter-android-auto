package io.openrouter.android.auto

import io.openrouter.android.auto.internal.CostRegistry
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

// ==================== Core Cost Calculation ====================

/**
 * Calculate the cost of a request given token counts and model pricing.
 * Uses BigDecimal to avoid floating-point precision loss.
 *
 * @param promptTokens Number of prompt tokens
 * @param completionTokens Number of completion tokens
 * @param reasoningTokens Number of reasoning tokens (billed at completion rate)
 */
fun calculateCost(
    model: OpenRouterModel,
    promptTokens: Int,
    completionTokens: Int,
    reasoningTokens: Int = 0
): CostEstimate {
    val promptPrice = model.pricing.prompt.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val completionPrice = model.pricing.completion.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val perThousand = BigDecimal.valueOf(1000)

    val promptCost = promptPrice
        .multiply(BigDecimal.valueOf(promptTokens.toLong()))
        .divide(perThousand, 10, RoundingMode.HALF_UP)

    val completionCost = completionPrice
        .multiply(BigDecimal.valueOf(completionTokens.toLong()))
        .divide(perThousand, 10, RoundingMode.HALF_UP)

    val reasoningCost = completionPrice
        .multiply(BigDecimal.valueOf(reasoningTokens.toLong()))
        .divide(perThousand, 10, RoundingMode.HALF_UP)

    val totalCost = promptCost.add(completionCost).add(reasoningCost)

    return CostEstimate(
        promptCost = promptCost.toDouble(),
        completionCost = completionCost.toDouble(),
        reasoningCost = reasoningCost.toDouble(),
        totalCost = totalCost.toDouble()
    )
}

// ==================== Token Estimation ====================

/**
 * Rough token estimate: ceil(charCount / chars_per_token from cost.json).
 * Mirrors estimateTokens() in cost.ts.
 */
fun estimateTokens(text: String): Int {
    val charsPerToken = CostRegistry.charsPerToken.coerceAtLeast(1)
    return (text.length + charsPerToken - 1) / charsPerToken
}

// ==================== Chat Cost ====================

/**
 * Calculate cost for a chat conversation, adding message overhead tokens.
 * Mirrors calculateChatCost() in cost.ts.
 */
fun calculateChatCost(
    model: OpenRouterModel,
    messages: List<ChatMessage>,
    expectedResponseTokens: Int
): CostEstimate {
    val overhead = CostRegistry.messageOverheadTokens * messages.size
    val contentTokens = messages.sumOf { estimateMessageTokens(it) }
    return calculateCost(model, overhead + contentTokens, expectedResponseTokens)
}

private fun estimateMessageTokens(message: ChatMessage): Int {
    val text = when (val content = message.content) {
        is JsonPrimitive -> content.content
        else -> content?.toString() ?: ""
    }
    return estimateTokens(text)
}

// ==================== Cost Formatting ====================

/**
 * Format a cost amount for human display.
 * Returns "Free", "< $0.000001", or a scaled dollar string.
 * Mirrors formatCost() in cost.ts.
 */
fun formatCost(amount: Double): String = when {
    amount == 0.0 -> "Free"
    amount < 0.000001 -> "< \$0.000001"
    amount < 0.001 -> "\$${String.format("%.6f", amount)}"
    amount < 0.01 -> "\$${String.format("%.4f", amount)}"
    else -> "\$${String.format("%.2f", amount)}"
}

/**
 * Format a per-1K token price string for display.
 * Returns "$0.000150/1K tokens" or "Free/1K tokens".
 */
fun formatPricePer1K(price: String): String {
    val bd = price.toBigDecimalOrNull() ?: return "\$$price/1K tokens"
    val formatted = if (bd == BigDecimal.ZERO) "Free" else "$${bd.stripTrailingZeros().toPlainString()}"
    return "$formatted/1K tokens"
}

// ==================== Price Tier Classification ====================

/**
 * Classify a model into a price tier based on its average per-token price.
 * Mirrors getPriceTier() in cost.ts.
 */
fun getPriceTier(model: OpenRouterModel): PriceTier {
    val avgPrice = calculateAveragePrice(model)
    val freeMax = CostRegistry.tierFreeMax ?: 0.0
    val cheapMax = CostRegistry.tierCheapMax ?: 0.0001
    val moderateMax = CostRegistry.tierModerateMax ?: 0.01

    return when {
        avgPrice <= freeMax -> PriceTier.FREE
        avgPrice <= cheapMax -> PriceTier.CHEAP
        avgPrice <= moderateMax -> PriceTier.MODERATE
        else -> PriceTier.EXPENSIVE
    }
}

/**
 * Returns true when the model is free (zero prices or `:free` suffix).
 * Mirrors isFreeModel() in cost.ts.
 */
fun isFreeModel(model: OpenRouterModel): Boolean {
    val promptZero = model.pricing.prompt == "0" || model.pricing.prompt.toDoubleOrNull() == 0.0
    val completionZero = model.pricing.completion == "0" || model.pricing.completion.toDoubleOrNull() == 0.0
    return (promptZero && completionZero) || model.id.endsWith(":free")
}

// ==================== Model Comparison ====================

/**
 * Compare costs across multiple models for the same token counts.
 * Returns list sorted ascending by total cost.
 * Mirrors compareModelCosts() in cost.ts.
 */
fun compareModelCosts(
    models: List<OpenRouterModel>,
    promptTokens: Int,
    completionTokens: Int
): List<Pair<OpenRouterModel, CostEstimate>> =
    models
        .map { it to calculateCost(it, promptTokens, completionTokens) }
        .sortedBy { it.second.totalCost }

/**
 * Return the cheapest model for given token counts, or null if the list is empty.
 */
fun getCheapestModel(
    models: List<OpenRouterModel>,
    promptTokens: Int,
    completionTokens: Int
): OpenRouterModel? =
    compareModelCosts(models, promptTokens, completionTokens).firstOrNull()?.first

/**
 * Calculate a monthly cost projection (daily requests × 30 days).
 * Mirrors calculateMonthlyEstimate() in cost.ts.
 */
fun calculateMonthlyEstimate(
    model: OpenRouterModel,
    dailyRequests: Int,
    avgPromptTokens: Int,
    avgCompletionTokens: Int
): CostEstimate {
    val daily = calculateCost(model, avgPromptTokens, avgCompletionTokens)
    val days = 30
    val requests = dailyRequests.coerceAtLeast(0)
    return CostEstimate(
        promptCost = daily.promptCost * requests * days,
        completionCost = daily.completionCost * requests * days,
        reasoningCost = daily.reasoningCost * requests * days,
        totalCost = daily.totalCost * requests * days
    )
}

/**
 * Return the best free text model (largest context length).
 * Mirrors getBestFreeModel() in cost.ts.
 */
fun getBestFreeModel(models: List<OpenRouterModel>): OpenRouterModel? =
    models
        .filter { isFreeModel(it) }
        .filter { it.architecture?.outputModalities?.contains("text") != false }
        .maxByOrNull { it.contextLength }

// ==================== Private Helpers ====================

private fun calculateAveragePrice(model: OpenRouterModel): Double {
    val prompt = model.pricing.prompt.toDoubleOrNull() ?: 0.0
    val completion = model.pricing.completion.toDoubleOrNull() ?: 0.0
    return (prompt + completion) / 2.0
}
