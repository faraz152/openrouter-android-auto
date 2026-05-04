package io.openrouter.android.auto.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.openrouter.android.auto.ChatMessage
import io.openrouter.android.auto.ChatRequest
import io.openrouter.android.auto.ORAError
import io.openrouter.android.auto.ORAErrorCode
import io.openrouter.android.auto.compose.ErrorDisplay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    modelId: String
) {
    val sdk = viewModel.getClient() ?: return
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<ORAError?>(null) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, streamingText) {
        val count = messages.size + if (streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = modelId,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val text = (msg.content as? JsonPrimitive)?.content
                    ?: msg.content?.jsonPrimitive?.content ?: ""
                MessageBubble(text = text, isUser = msg.role == "user")
            }
            if (streamingText.isNotEmpty()) {
                item {
                    MessageBubble(text = streamingText, isUser = false)
                }
            }
        }

        ErrorDisplay(
            error = localError,
            onDismiss = { localError = null }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type a message…") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val userText = input.trim()
                    if (userText.isBlank()) return@Button
                    input = ""
                    messages.add(ChatMessage(role = "user", content = JsonPrimitive(userText)))
                    isStreaming = true
                    streamingText = ""
                    scope.launch {
                        try {
                            val req = ChatRequest(
                                model = modelId,
                                messages = messages.toList()
                            )
                            sdk.streamChat(req).collect { chunk ->
                                val delta = chunk.choices?.firstOrNull()?.delta?.content
                                if (delta is JsonPrimitive) streamingText += delta.content
                            }
                            messages.add(
                                ChatMessage(role = "assistant", content = JsonPrimitive(streamingText))
                            )
                        } catch (e: ORAError) {
                            localError = e
                        } catch (e: Exception) {
                            localError = ORAError(ORAErrorCode.NETWORK_ERROR, e.message ?: "Unknown error", retryable = true)
                        } finally {
                            isStreaming = false
                            streamingText = ""
                        }
                    }
                },
                enabled = !isStreaming && input.isNotBlank()
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean) {
    val bgColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
