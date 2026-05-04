package io.openrouter.android.auto.sample.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.OpenRouterModel
import io.openrouter.android.auto.compose.ErrorDisplay
import io.openrouter.android.auto.compose.ModelSelector

@Composable
fun ModelBrowserScreen(
    viewModel: AppViewModel,
    onChatClick: (String) -> Unit,
    onCostClick: (String) -> Unit
) {
    val models by viewModel.models.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedModel by remember { mutableStateOf<OpenRouterModel?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Models (${models.size})",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        ModelSelector(
            models = models,
            selectedModel = selectedModel,
            onSelect = { selectedModel = it },
            modifier = Modifier.padding(horizontal = 16.dp),
            showPricing = true,
            showContextLength = true
        )

        if (selectedModel != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onChatClick(selectedModel!!.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Chat")
                }
                OutlinedButton(
                    onClick = { onCostClick(selectedModel!!.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Estimate Cost")
                }
            }
        }

        ErrorDisplay(
            error = error,
            onDismiss = { viewModel.clearError() }
        )
    }
}
