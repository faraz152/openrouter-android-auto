package io.openrouter.android.auto.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.OpenRouterModel
import io.openrouter.android.auto.calculateCost
import io.openrouter.android.auto.estimateTokens
import io.openrouter.android.auto.formatCost
import io.openrouter.android.auto.formatPricePer1K

/**
 * Live cost estimation component.
 *
 * Shows token inputs, an optional text area that auto-estimates tokens,
 * and a cost breakdown card.
 */
@Composable
fun CostEstimator(
    model: OpenRouterModel,
    modifier: Modifier = Modifier,
    defaultPromptTokens: Int = 1000,
    defaultCompletionTokens: Int = 500,
    showTextInput: Boolean = false
) {
    var promptTokens by remember { mutableIntStateOf(defaultPromptTokens) }
    var completionTokens by remember { mutableIntStateOf(defaultCompletionTokens) }
    var textInput by remember { mutableStateOf("") }

    // Auto-estimate from text
    if (showTextInput && textInput.isNotBlank()) {
        promptTokens = estimateTokens(textInput)
    }

    val estimate by remember(model, promptTokens, completionTokens) {
        derivedStateOf {
            runCatching { calculateCost(model, promptTokens, completionTokens) }.getOrNull()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cost Estimator", style = MaterialTheme.typography.titleMedium)
        Text(
            text = model.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showTextInput) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter text to estimate tokens") },
                minLines = 3,
                maxLines = 6
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = promptTokens.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> promptTokens = v } },
                modifier = Modifier.weight(1f),
                label = { Text("Prompt tokens") },
                singleLine = true
            )
            OutlinedTextField(
                value = completionTokens.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> completionTokens = v } },
                modifier = Modifier.weight(1f),
                label = { Text("Completion tokens") },
                singleLine = true
            )
        }

        estimate?.let { est ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CostRow("Prompt", est.promptCost)
                    CostRow("Completion", est.completionCost)
                    if (est.reasoningCost > 0) {
                        CostRow("Reasoning", est.reasoningCost)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    CostRow("Total", est.totalCost, bold = true)
                }
            }

            // Pricing info
            model.pricing?.let { p ->
                Text(
                    text = "Prompt: ${formatPricePer1K(p.prompt)} / 1K tokens  •  " +
                            "Completion: ${formatPricePer1K(p.completion)} / 1K tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CostRow(label: String, amount: Double, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium
        )
        Text(
            text = formatCost(amount),
            style = if (bold) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium
        )
    }
}
