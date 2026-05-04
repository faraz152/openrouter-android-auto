package io.openrouter.android.auto.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.compose.CostEstimator

@Composable
fun CostScreen(
    viewModel: AppViewModel,
    modelId: String
) {
    val models by viewModel.models.collectAsState()
    val model = models.find { it.id == modelId } ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Cost Estimation",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(Modifier.height(8.dp))

        CostEstimator(
            model = model,
            modifier = Modifier.padding(horizontal = 16.dp),
            showTextInput = true
        )
    }
}
