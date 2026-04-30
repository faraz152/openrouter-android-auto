package io.openrouter.android.auto.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.ModelFilter
import io.openrouter.android.auto.OpenRouterModel
import io.openrouter.android.auto.PriceTier
import io.openrouter.android.auto.formatPricePer1K
import io.openrouter.android.auto.getPriceTier

/**
 * Searchable model selector with price tier badges, context length display,
 * and filter chips for FREE / CHEAP / MODERATE / EXPENSIVE.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelSelector(
    models: List<OpenRouterModel>,
    selectedModel: OpenRouterModel?,
    onSelect: (OpenRouterModel) -> Unit,
    modifier: Modifier = Modifier,
    showPricing: Boolean = true,
    showContextLength: Boolean = true,
    filters: ModelFilter? = null,
    placeholder: String = "Select a model…"
) {
    var searchQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var activeTier by remember { mutableStateOf<PriceTier?>(null) }

    val filteredModels by remember(models, searchQuery, activeTier, filters) {
        derivedStateOf {
            models.filter { model ->
                val matchesSearch = searchQuery.isBlank() ||
                        model.id.contains(searchQuery, ignoreCase = true) ||
                        model.name.contains(searchQuery, ignoreCase = true)
                val matchesTier = activeTier == null || getPriceTier(model) == activeTier
                val matchesFilter = filters?.let { f ->
                    (f.provider == null || model.id.startsWith("${f.provider}/")) &&
                            (f.minContextLength == null || (model.contextLength ?: 0) >= f.minContextLength!!) &&
                            (f.excludeModels == null || model.id !in f.excludeModels!!)
                } ?: true
                matchesSearch && matchesTier && matchesFilter
            }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = selectedModel?.name ?: searchQuery,
            onValueChange = { query ->
                searchQuery = query
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true
        )

        // Filter chips row
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeTier == null,
                onClick = { activeTier = null },
                label = { Text("All") }
            )
            PriceTier.entries.forEach { tier ->
                FilterChip(
                    selected = activeTier == tier,
                    onClick = { activeTier = if (activeTier == tier) null else tier },
                    label = { Text(tier.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tier.color().copy(alpha = 0.15f),
                        selectedLabelColor = tier.color()
                    )
                )
            }
        }

        DropdownMenu(
            expanded = expanded && filteredModels.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 400.dp)
        ) {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(filteredModels, key = { it.id }) { model ->
                    ModelItem(
                        model = model,
                        showPricing = showPricing,
                        showContextLength = showContextLength,
                        onClick = {
                            onSelect(model)
                            searchQuery = ""
                            expanded = false
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: OpenRouterModel,
    showPricing: Boolean,
    showContextLength: Boolean,
    onClick: () -> Unit
) {
    val tier = getPriceTier(model)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = model.id,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showPricing) {
                PriceTierBadge(tier)
            }
            if (showContextLength) {
                Text(
                    text = formatContextLength(model.contextLength ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PriceTierBadge(tier: PriceTier) {
    val label = when (tier) {
        PriceTier.FREE -> "Free"
        PriceTier.CHEAP -> "Cheap"
        PriceTier.MODERATE -> "Mid"
        PriceTier.EXPENSIVE -> "$$"
    }
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = tier.color().copy(alpha = 0.15f),
            selectedLabelColor = tier.color()
        )
    )
}

/** Format context length: 1_000_000 → "1.0M", 128_000 → "128K", 4096 → "4K" */
fun formatContextLength(contextLength: Int): String = when {
    contextLength >= 1_000_000 -> "${"%.1f".format(contextLength / 1_000_000.0)}M"
    contextLength >= 1_000 -> "${contextLength / 1_000}K"
    else -> "$contextLength"
}
