package io.openrouter.android.auto.sample.xml.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.openrouter.android.auto.ChatMessage
import io.openrouter.android.auto.ChatRequest
import io.openrouter.android.auto.ORAError
import io.openrouter.android.auto.ORAErrorCode
import io.openrouter.android.auto.OpenRouterAuto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

class ChatViewModel(private val sdk: OpenRouterAuto) : ViewModel() {

    val messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val streamedText = MutableLiveData("")
    val isStreaming = MutableLiveData(false)
    val error = MutableLiveData<ORAError?>(null)

    private val _messages = mutableListOf<ChatMessage>()

    fun sendMessage(modelId: String, prompt: String) {
        if (prompt.isBlank()) return

        val userMsg = ChatMessage(role = "user", content = JsonPrimitive(prompt))
        _messages.add(userMsg)
        messages.value = _messages.toList()

        viewModelScope.launch {
            isStreaming.value = true
            streamedText.value = ""
            error.value = null

            try {
                val req = ChatRequest(model = modelId, messages = _messages.toList())
                var assembled = ""

                sdk.streamChat(req).collect { chunk ->
                    val delta = chunk.choices?.firstOrNull()?.delta?.content
                    if (delta is JsonPrimitive) {
                        assembled += delta.content
                        streamedText.postValue(assembled)
                    }
                }

                _messages.add(
                    ChatMessage(role = "assistant", content = JsonPrimitive(assembled))
                )
                messages.postValue(_messages.toList())
            } catch (e: ORAError) {
                error.postValue(e)
            } catch (e: Exception) {
                error.postValue(
                    ORAError(ORAErrorCode.NETWORK_ERROR, e.message ?: "Unknown error", retryable = true)
                )
            } finally {
                isStreaming.postValue(false)
                streamedText.postValue("")
            }
        }
    }

    class Factory(private val sdk: OpenRouterAuto) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(sdk) as T
    }
}
