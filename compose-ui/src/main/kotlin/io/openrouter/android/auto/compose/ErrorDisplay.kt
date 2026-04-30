package io.openrouter.android.auto.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.ORAError
import io.openrouter.android.auto.ORAErrorCode
import io.openrouter.android.auto.isRetryable
import kotlinx.coroutines.delay

/** Per-error-code user-facing tips. */
private val ERROR_TIPS = mapOf(
    ORAErrorCode.INVALID_API_KEY to "Check your API key in the settings.",
    ORAErrorCode.RATE_LIMITED to "Wait a moment and try again.",
    ORAErrorCode.INSUFFICIENT_CREDITS to "Add credits at openrouter.ai/credits.",
    ORAErrorCode.MODEL_NOT_FOUND to "The model may have been removed or renamed.",
    ORAErrorCode.MODEL_UNAVAILABLE to "Try a different model.",
    ORAErrorCode.PROVIDER_ERROR to "The upstream provider is experiencing issues.",
    ORAErrorCode.INVALID_PARAMETERS to "Adjust your parameters and try again.",
    ORAErrorCode.NETWORK_ERROR to "Check your internet connection.",
    ORAErrorCode.TIMEOUT to "The request took too long. Try again.",
    ORAErrorCode.UNKNOWN to "An unexpected error occurred."
)

/**
 * Error banner with icon, message, tip, optional retry, and auto-dismiss.
 */
@Composable
fun ErrorDisplay(
    error: ORAError?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    autoHideMs: Long = 5000
) {
    var visible by remember(error) { mutableStateOf(error != null) }

    // Auto-dismiss
    if (error != null && autoHideMs > 0) {
        LaunchedEffect(error) {
            delay(autoHideMs)
            visible = false
            onDismiss?.invoke()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (error != null) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OpenRouterTheme.ErrorColor.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error.code.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = OpenRouterTheme.ErrorColor
                        )
                        if (onDismiss != null) {
                            IconButton(onClick = {
                                visible = false
                                onDismiss()
                            }) {
                                Text("✕")
                            }
                        }
                    }

                    Text(
                        text = error.message ?: "An error occurred",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    ERROR_TIPS[error.code]?.let { tip ->
                        Text(
                            text = "💡 $tip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onRetry != null && isRetryable(error.code)) {
                        TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
