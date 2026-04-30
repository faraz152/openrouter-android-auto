package io.openrouter.android.auto.compose

import androidx.compose.ui.graphics.Color
import io.openrouter.android.auto.PriceTier

/** Material3 color tokens for price tier badges and SDK UI. */
object OpenRouterTheme {
    val FreeTierColor = Color(0xFF4CAF50)
    val CheapTierColor = Color(0xFF2196F3)
    val ModerateTierColor = Color(0xFFFF9800)
    val ExpensiveTierColor = Color(0xFFF44336)
    val ErrorColor = Color(0xFFD32F2F)
    val SuccessColor = Color(0xFF388E3C)
}

/** Map a [PriceTier] to its theme color. */
fun PriceTier.color(): Color = when (this) {
    PriceTier.FREE -> OpenRouterTheme.FreeTierColor
    PriceTier.CHEAP -> OpenRouterTheme.CheapTierColor
    PriceTier.MODERATE -> OpenRouterTheme.ModerateTierColor
    PriceTier.EXPENSIVE -> OpenRouterTheme.ExpensiveTierColor
}
