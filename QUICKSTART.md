# Quick Start Guide — openrouter-android-auto

This guide walks you from zero to a working chat app in under 10 minutes.

---

## Step 1: Add the Dependency

```kotlin
// build.gradle.kts (app or feature module)
dependencies {
    implementation("io.openrouter.android:auto-core:1.0.0")
}
```

Sync Gradle. That's the only dependency you need for core functionality.

---

## Step 2: Add Internet Permission

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Step 3: Initialize the SDK

Create the client once per app session — either in your `Application` class or a singleton:

```kotlin
import io.openrouter.android.auto.OpenRouterAuto
import io.openrouter.android.auto.SharedPrefsStorage

class MyApp : Application() {

    lateinit var openRouter: OpenRouterAuto

    override fun onCreate() {
        super.onCreate()
        openRouter = OpenRouterAuto.Builder(apiKey = BuildConfig.OPENROUTER_API_KEY)
            .storage(SharedPrefsStorage(this))
            .siteUrl("https://myapp.example.com")
            .siteName("My App")
            .build()
    }
}
```

> **Never hardcode your API key.** Store it in `local.properties` and read it via `BuildConfig`, or load it from user input at runtime.

Call `initialize()` once after the client is created (loads models and registries):

```kotlin
// In a coroutine — ViewModel.init, Activity.onCreate with lifecycleScope, etc.
lifecycleScope.launch {
    openRouter.initialize()
}
```

---

## Step 4: One-Shot Chat

```kotlin
import io.openrouter.android.auto.Types.ChatRequest
import io.openrouter.android.auto.Types.ChatMessage
import kotlinx.serialization.json.JsonPrimitive

// suspend — call from a coroutine
val response = openRouter.chat(
    ChatRequest(
        model = "openai/gpt-4o",
        messages = listOf(
            ChatMessage(role = "user", content = JsonPrimitive("What is Android?"))
        )
    )
)

val reply = response.choices[0].message.content
println(reply)
```

---

## Step 5: Streaming Chat

Use `streamChat()` to receive tokens as they arrive:

### Jetpack Compose

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val streamedText by viewModel.streamedText.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendMessage("Tell me about Kotlin coroutines")
    }

    Text(text = streamedText)
}

// ViewModel
class ChatViewModel(private val sdk: OpenRouterAuto) : ViewModel() {

    private val _streamedText = MutableStateFlow("")
    val streamedText: StateFlow<String> = _streamedText

    fun sendMessage(userMessage: String) {
        viewModelScope.launch {
            _streamedText.value = ""
            val request = ChatRequest(
                model = "openai/gpt-4o",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive(userMessage)))
            )
            sdk.streamChat(request).collect { chunk ->
                val token = chunk.choices?.firstOrNull()?.delta?.content ?: ""
                _streamedText.value += token
            }
        }
    }
}
```

### XML Views

```kotlin
// Activity / Fragment
private fun startStreaming(userMessage: String) {
    lifecycleScope.launch {
        val request = ChatRequest(
            model = "openai/gpt-4o",
            messages = listOf(ChatMessage(role = "user", content = JsonPrimitive(userMessage)))
        )
        val builder = StringBuilder()
        sdk.streamChat(request).collect { chunk ->
            val token = chunk.choices?.firstOrNull()?.delta?.content ?: ""
            builder.append(token)
            withContext(Dispatchers.Main) {
                binding.responseTextView.text = builder.toString()
            }
        }
    }
}
```

---

## Step 6: Cost Estimation

```kotlin
val cost = openRouter.calculateCost(
    modelId = "openai/gpt-4o",
    promptTokens = 1000,
    completionTokens = 500
)

println("Prompt cost:     $${cost.promptCost}")
println("Completion cost: $${cost.completionCost}")
println("Total:           $${cost.totalCost}")
```

---

## Step 7: Model Discovery

```kotlin
// All models
val models = openRouter.getModels()

// Filter — only models with vision support under $5/M tokens
val visionModels = openRouter.filterModels(
    ModelFilter(
        capabilities = setOf(ModelCapability.VISION),
        maxPromptPrice = 0.000005
    )
)

// Find the best free model
val freeModel = openRouter.getBestFreeModel()
```

---

## Step 8: Compose UI Components (optional)

Add the compose-ui artifact:

```kotlin
implementation("io.openrouter.android:auto-compose:1.0.0")
```

Then use the pre-built components:

```kotlin
import io.openrouter.android.auto.compose.ModelSelector
import io.openrouter.android.auto.compose.CostEstimator

// Model picker with live search
ModelSelector(
    models = models,
    onModelSelected = { model ->
        selectedModel = model
    }
)

// Cost estimator with text input
CostEstimator(
    model = selectedModel,
    showTextInput = true
)
```

---

## Sample Apps

Full working examples are in the repository:

| App            | Path              | Description                             |
| -------------- | ----------------- | --------------------------------------- |
| Compose sample | `sample-compose/` | Full chat app with Jetpack Compose      |
| XML sample     | `sample-xml/`     | Full chat app with Views + RecyclerView |

Clone the repo and open in Android Studio to run them directly.

---

## Handling Errors

```kotlin
import io.openrouter.android.auto.ORAError
import io.openrouter.android.auto.ORAErrorCode

try {
    val response = openRouter.chat(request)
} catch (e: ORAError) {
    when (e.code) {
        ORAErrorCode.AUTH_ERROR -> showError("Invalid API key")
        ORAErrorCode.RATE_LIMITED -> retryAfterDelay(e.retryAfter)
        ORAErrorCode.MODEL_NOT_FOUND -> showError("Model unavailable")
        else -> showError(e.message)
    }
}
```

---

## Storage Options

```kotlin
// In-memory only (default — cleared on process death)
OpenRouterAuto.Builder(apiKey = key)
    .build()

// Persist to SharedPreferences (survives app restarts)
OpenRouterAuto.Builder(apiKey = key)
    .storage(SharedPrefsStorage(context))
    .build()

// Persist to a JSON file
OpenRouterAuto.Builder(apiKey = key)
    .storage(FileStorage(File(context.filesDir, "openrouter.json")))
    .build()
```

---

## Clean Up

Call `dispose()` when the SDK is no longer needed (e.g. in `onDestroy` or when the ViewModel is cleared):

```kotlin
openRouter.dispose()
```
