package io.openrouter.android.auto.sample.xml

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import io.openrouter.android.auto.sample.xml.viewmodel.ChatViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val sdk = (application as App).openRouter ?: run {
            finish()
            return
        }

        val modelId = intent.getStringExtra("model_id") ?: run {
            finish()
            return
        }
        val modelName = intent.getStringExtra("model_name") ?: modelId

        viewModel = ViewModelProvider(
            this,
            ChatViewModel.Factory(sdk)
        )[ChatViewModel::class.java]

        val titleText = findViewById<TextView>(R.id.textModelTitle)
        val editInput = findViewById<EditText>(R.id.editMessage)
        val buttonSend = findViewById<Button>(R.id.buttonSend)
        messagesContainer = findViewById(R.id.messagesContainer)
        scrollView = findViewById(R.id.messagesScrollView)

        titleText.text = modelName

        // Observe messages
        viewModel.messages.observe(this) { messages ->
            messagesContainer.removeAllViews()
            messages.forEach { msg ->
                val role = msg.role ?: "unknown"
                val text = (msg.content as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: msg.content?.toString() ?: ""
                appendMessage(role, text)
            }
        }

        // Observe streaming text
        viewModel.streamedText.observe(this) { text ->
            if (text.isNotEmpty()) {
                // Replace or append streaming indicator
                val lastChild = messagesContainer.getChildAt(messagesContainer.childCount - 1)
                val isStreamingView = lastChild?.tag == "streaming"
                if (isStreamingView) {
                    (lastChild as? TextView)?.text = "assistant: $text"
                } else {
                    val tv = createMessageView("assistant", text)
                    tv.tag = "streaming"
                    messagesContainer.addView(tv)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }

        // Observe streaming state
        viewModel.isStreaming.observe(this) { streaming ->
            buttonSend.isEnabled = !streaming
            buttonSend.text = if (streaming) "…" else "Send"
        }

        // Observe errors
        viewModel.error.observe(this) { error ->
            if (error != null) {
                appendMessage("error", error.message ?: "Unknown error")
            }
        }

        buttonSend.setOnClickListener {
            val text = editInput.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            editInput.text.clear()
            viewModel.sendMessage(modelId, text)
        }
    }

    private fun appendMessage(role: String, text: String) {
        messagesContainer.addView(createMessageView(role, text))
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun createMessageView(role: String, text: String): TextView =
        TextView(this).apply {
            this.text = "$role: $text"
            setPadding(24, 12, 24, 12)
            textSize = 14f
            setTextColor(
                if (role == "user")
                    resources.getColor(android.R.color.holo_blue_dark, theme)
                else
                    resources.getColor(android.R.color.black, theme)
            )
        }
}
