package io.openrouter.android.auto.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.OpenRouterModel
import io.openrouter.android.auto.PriceTier
import io.openrouter.android.auto.compose.ErrorDisplay
import io.openrouter.android.auto.compose.PriceTierBadge
import io.openrouter.android.auto.formatPricePer1K
import io.openrouter.android.auto.getPriceTier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelBrowserScreen(
    viewModel: AppViewModel,
    onChatClick: (String) -> Unit,
    onCostClick: (String) -> Unit
) {
    val models by viewModel.models.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedModel by remember { mutableStateOf<OpenRouterModel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTier by remember { mutableStateOf<PriceTier?>(null) }

    val filteredModels by remember(models, searchQuery, activeTier) {
        derivedStateOf {
            models.filter { model ->
                val matchesSearch = searchQuery.isBlank() ||
                        model.id.contains(searchQuery, ignoreCase = true) ||
                        model.name.contains(searchQuery, ignoreCase = true)
                val matchesTier = activeTier == null || getPriceTier(model) == activeTier
                matchesSearch && matchesTier
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Models (${filteredModels.size}/${models.size})",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search models…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )

        // Filter chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    label = { Text(tier.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        // Action buttons for selected model
        if (selectedModel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedModel!!.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { onChatClick(selectedModel!!.id) }) {
                    Text("Chat")
                }
                OutlinedButton(onClick = { onCostClick(selectedModel!!.id) }) {
                    Text("Cost")
                }
            }
        }

        ErrorDisplay(
            error = error,
            onDismiss = { viewModel.clearError() }
        )

        // Always-visible model list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredModels, key = { it.id }) { model ->
                ModelRow(
                    model = model,
                    isSelected = model.id == selectedModel?.id,
                    onClick = {
                        selectedModel = if (selectedModel?.id == model.id) null else model
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: OpenRouterModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tier = getPriceTier(model)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PriceTierBadge(tier)
            if ((model.contextLength ?: 0) > 0) {
                val ctx = model.contextLength ?: 0
                val label = if (ctx >= 1_000_000) "${ctx / 1_000_000}M"
                            else if (ctx >= 1_000) "${ctx / 1_000}K"
                            else "$ctx"
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
