# openrouter-android-auto

[![CI](https://github.com/faraz152/openrouter-android-auto/actions/workflows/ci.yml/badge.svg)](https://github.com/faraz152/openrouter-android-auto/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.openrouter.android/auto-core)](https://central.sonatype.com/artifact/io.openrouter.android/auto-core)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-24-brightgreen)](https://developer.android.com/about/versions/nougat)

> Pure-Kotlin Android SDK for [OpenRouter](https://openrouter.ai) — model discovery, chat completions, SSE streaming, cost estimation, parameter validation, error handling, and pluggable storage. Feature-complete mirror of the [`openrouter-auto`](https://github.com/faraz152/openrouter-auto) TypeScript SDK.

---

## Features

- **Model Discovery** — fetch, filter, and search all OpenRouter models
- **Chat Completions** — one-shot `suspend fun chat()` with full response
- **SSE Streaming** — `Flow<StreamChunk>` with real-time token delivery
- **Cost Estimation** — accurate per-call cost from live pricing registry
- **Parameter Validation** — model-specific parameter checking before requests
- **Error Handling** — typed `ORAError` with retry hints and error codes
- **Pluggable Storage** — `InMemory`, `SharedPrefs`, `File` adapters (or bring your own)
- **Event System** — subscribe to SDK lifecycle events
- **Optional Compose UI** — `ModelSelector`, `CostEstimator` components
- **CLI Tool** — JVM command-line tool for scripting and testing

---

## Installation

### Maven Central (recommended)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("io.openrouter.android:auto-core:1.0.0")

    // Optional: Jetpack Compose UI components
    implementation("io.openrouter.android:auto-compose:1.0.0")
}
```

### GitHub Packages

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/faraz152/openrouter-android-auto")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

---

## Quick Start

```kotlin
import io.openrouter.android.auto.OpenRouterAuto
import io.openrouter.android.auto.SharedPrefsStorage
import io.openrouter.android.auto.Types.*
import kotlinx.serialization.json.JsonPrimitive

// 1. Build the client (once per app session)
val client = OpenRouterAuto.Builder(apiKey = "sk-or-v1-...")
    .storage(SharedPrefsStorage(context))
    .siteUrl("https://myapp.example.com")
    .siteName("My App")
    .build()

// 2. Initialize (fetches model list, loads registry)
client.initialize()

// 3. One-shot chat
val response = client.chat(
    ChatRequest(
        model = "openai/gpt-4o",
        messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hello!")))
    )
)
println(response.choices[0].message.content)

// 4. Streaming chat
client.streamChat(request).collect { chunk ->
    print(chunk.choices?.firstOrNull()?.delta?.content ?: "")
}

// 5. Cost estimation
val cost = client.calculateCost(
    modelId = "openai/gpt-4o",
    promptTokens = 1000,
    completionTokens = 500
)
println("Cost: $${cost.totalCost}")

// 6. Clean up
client.dispose()
```

See [QUICKSTART.md](QUICKSTART.md) for the full step-by-step guide with XML and Compose examples.

---

## API Reference

### `OpenRouterAuto.Builder`

| Method | Description |
|---|---|
| `Builder(apiKey)` | Required. Your OpenRouter API key |
| `.baseUrl(url)` | Override API base URL (default: `https://openrouter.ai/api/v1`) |
| `.siteUrl(url)` | Your app's URL (sent as `HTTP-Referer`) |
| `.siteName(name)` | Your app's name (sent as `X-Title`) |
| `.storage(adapter)` | Pluggable storage adapter |
| `.httpClientEngine(engine)` | Override Ktor engine (e.g. OkHttp for testing) |
| `.build()` | Returns `OpenRouterAuto` instance |

### Core Methods

| Method | Returns | Description |
|---|---|---|
| `initialize()` | `Unit` | Fetch models + load registries. Call once on startup |
| `fetchModels()` | `List<OpenRouterModel>` | Re-fetch models from API |
| `getModels()` | `List<OpenRouterModel>` | Return cached model list |
| `getModel(id)` | `OpenRouterModel?` | Find model by ID |
| `filterModels(filter)` | `List<OpenRouterModel>` | Filter by capability, pricing, context |
| `getBestFreeModel()` | `OpenRouterModel?` | Best available free model |
| `chat(request)` | `ChatResponse` | One-shot completion |
| `streamChat(request)` | `Flow<StreamChunk>` | SSE streaming completion |
| `calculateCost(...)` | `CostBreakdown` | Token cost from pricing registry |
| `estimateTokens(text)` | `Int` | Rough token count estimate |
| `testModel(modelId)` | `ModelTestResult` | Ping a model with a test prompt |
| `testAllModels()` | `List<ModelTestResult>` | Test all known models |
| `savePreferences(prefs)` | `Unit` | Persist user preferences |
| `getPreferences()` | `UserPreferences?` | Load persisted preferences |
| `on(type, handler)` | `() -> Unit` | Subscribe to SDK events (returns unsubscribe fn) |
| `dispose()` | `Unit` | Close HTTP client and release resources |

### Storage Adapters

```kotlin
// In-memory (default, cleared on process death)
InMemoryStorage()

// SharedPreferences (survives app restarts)
SharedPrefsStorage(context, name = "openrouter_prefs")

// File-based (full JSON persistence)
FileStorage(file = File(context.filesDir, "openrouter.json"))
```

### Compose UI Components

```kotlin
// Model picker with search + filter
ModelSelector(
    models = models,
    onModelSelected = { model -> /* ... */ }
)

// Cost breakdown widget
CostEstimator(
    model = selectedModel,
    showTextInput = true
)
```

---

## Module Structure

```
openrouter-android-auto/
├── core/           — :core AAR  — main SDK (no UI dependencies)
├── compose-ui/     — :compose-ui AAR  — optional Compose components
├── cli/            — JVM CLI tool
├── sample-compose/ — Jetpack Compose demo app
└── sample-xml/     — XML Views demo app
```

---

## Requirements

- Android **API 24+** (Android 7.0)
- Kotlin **1.9+**
- Coroutines **1.8+**

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

---

## License

[MIT](LICENSE) © Faraz Ahmed
