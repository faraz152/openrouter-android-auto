package io.openrouter.android.auto.cli

import kotlinx.cli.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

private val CONFIG_DIR = File(System.getProperty("user.home"), ".openrouter-auto")
private val CONFIG_FILE = File(CONFIG_DIR, "config.json")

fun main(args: Array<String>) {
    val parser = ArgParser("openrouter-auto")
    parser.subcommands(
        SetupCommand(),
        ModelsCommand(),
        AddCommand(),
        TestCommand(),
        ChatCommand()
    )
    parser.parse(args)
}

// ==================== Helpers ====================

private fun loadApiKey(): String {
    if (!CONFIG_FILE.exists()) {
        System.err.println("Error: Not configured. Run 'setup --api-key YOUR_KEY' first.")
        System.exit(1)
    }
    val config = cliJson.decodeFromString(CliConfig.serializer(), CONFIG_FILE.readText())
    return config.apiKey
}

private fun buildClient(): CliClient = CliClient(loadApiKey())

internal fun formatPrice(price: String): String {
    val bd = price.toBigDecimalOrNull() ?: return price
    return if (bd.signum() == 0) "Free" else "$$bd/1K"
}

internal fun formatContextLength(length: Int?): String = when {
    length == null -> "—"
    length >= 1_000_000 -> "${"%.1f".format(length / 1_000_000.0)}M"
    length >= 1_000 -> "${length / 1_000}K"
    else -> "$length"
}

// ==================== Commands ====================

class SetupCommand : Subcommand("setup", "Configure API key") {
    private val apiKey by option(ArgType.String, shortName = "k", fullName = "api-key",
        description = "OpenRouter API key").required()

    override fun execute() = runBlocking {
        // Create config dir with owner-only perms
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs()
            try {
                Files.setPosixFilePermissions(
                    CONFIG_DIR.toPath(),
                    PosixFilePermissions.fromString("rwx------")
                )
            } catch (_: UnsupportedOperationException) { /* Windows */ }
        }

        // Test connection
        print("Testing connection... ")
        val client = CliClient(apiKey)
        try {
            val models = client.fetchModels()
            println("✓ Connected. ${models.size} models available.")
        } catch (e: Exception) {
            println("✗ Failed: ${e.message}")
            client.close()
            return@runBlocking
        }
        client.close()

        // Save config
        val configJson = cliJson.encodeToString(CliConfig.serializer(), CliConfig(apiKey = apiKey))
        CONFIG_FILE.writeText(configJson)
        try {
            Files.setPosixFilePermissions(
                CONFIG_FILE.toPath(),
                PosixFilePermissions.fromString("rw-------")
            )
        } catch (_: UnsupportedOperationException) { /* Windows */ }

        println("Config saved to ${CONFIG_FILE.absolutePath}")
    }
}

class ModelsCommand : Subcommand("models", "List available models") {
    private val free by option(ArgType.Boolean, shortName = "f", fullName = "free",
        description = "Show only free models").default(false)
    private val provider by option(ArgType.String, shortName = "p", fullName = "provider",
        description = "Filter by provider")
    private val search by option(ArgType.String, shortName = "s", fullName = "search",
        description = "Search by model name or ID")
    private val limit by option(ArgType.Int, shortName = "n", fullName = "limit",
        description = "Max number of results").default(20)

    override fun execute() = runBlocking {
        val client = buildClient()
        try {
            var models = client.fetchModels()

            // Apply filters
            if (free) {
                models = models.filter { m ->
                    val p = m.pricing
                    p != null && (p.prompt.toBigDecimalOrNull()?.signum() ?: 1) == 0
                            && (p.completion.toBigDecimalOrNull()?.signum() ?: 1) == 0
                }
            }
            provider?.let { prov ->
                models = models.filter { it.id.startsWith("$prov/") }
            }
            search?.let { q ->
                models = models.filter { m ->
                    m.id.contains(q, ignoreCase = true) || m.name.contains(q, ignoreCase = true)
                }
            }
            models = models.take(limit)

            if (models.isEmpty()) {
                println("No models found matching filters.")
                return@runBlocking
            }

            // Table header
            val fmt = "%-45s  %-15s  %-15s  %s"
            println(fmt.format("MODEL", "PROMPT PRICE", "COMP PRICE", "CTX"))
            println("-".repeat(90))

            for (m in models) {
                println(fmt.format(
                    m.id.take(45),
                    formatPrice(m.pricing?.prompt ?: "0"),
                    formatPrice(m.pricing?.completion ?: "0"),
                    formatContextLength(m.contextLength)
                ))
            }

            println("\n${models.size} model(s) shown.")
        } finally {
            client.close()
        }
    }
}

class AddCommand : Subcommand("add", "Add and configure a model") {
    private val modelId by argument(ArgType.String, description = "Model ID (e.g. openai/gpt-4o)")
    private val temperature by option(ArgType.Double, shortName = "t", fullName = "temperature",
        description = "Temperature (0-2)")
    private val maxTokens by option(ArgType.Int, fullName = "max-tokens",
        description = "Max output tokens")
    private val skipTest by option(ArgType.Boolean, fullName = "skip-test",
        description = "Skip model test").default(false)

    override fun execute() = runBlocking {
        val client = buildClient()
        try {
            // Verify model exists
            val models = client.fetchModels()
            val model = models.find { it.id == modelId }
            if (model == null) {
                println("Error: Model '$modelId' not found.")
                return@runBlocking
            }
            println("Found: ${model.name} (${formatContextLength(model.contextLength)})")

            // Test model
            if (!skipTest) {
                print("Testing model... ")
                try {
                    val response = client.chat(ChatRequest(
                        model = modelId,
                        messages = listOf(ChatMessage("user", JsonPrimitive("Say 'OK' in one word."))),
                        temperature = temperature,
                        maxTokens = maxTokens ?: 5
                    ))
                    val content = response.choices.firstOrNull()?.message?.content
                    println("✓ Response: ${(content as? JsonPrimitive)?.content ?: content}")
                } catch (e: Exception) {
                    println("✗ Test failed: ${e.message}")
                    return@runBlocking
                }
            }

            println("Model '$modelId' configured successfully.")
            temperature?.let { println("  temperature: $it") }
            maxTokens?.let { println("  max_tokens: $it") }
        } finally {
            client.close()
        }
    }
}

class TestCommand : Subcommand("test", "Test a model with a probe request") {
    private val modelId by argument(ArgType.String, description = "Model ID to test")

    override fun execute() = runBlocking {
        val client = buildClient()
        try {
            print("Testing '$modelId'... ")
            val start = System.currentTimeMillis()
            val response = client.chat(ChatRequest(
                model = modelId,
                messages = listOf(ChatMessage("user", JsonPrimitive("Respond with 'OK' only."))),
                maxTokens = 5
            ))
            val elapsed = System.currentTimeMillis() - start
            val content = response.choices.firstOrNull()?.message?.content
            println("✓ (${elapsed}ms)")
            println("Response: ${(content as? JsonPrimitive)?.content ?: content}")
        } catch (e: Exception) {
            println("✗ Failed: ${e.message}")
        } finally {
            client.close()
        }
    }
}

class ChatCommand : Subcommand("chat", "Send a chat completion") {
    private val modelId by argument(ArgType.String, description = "Model ID")
    private val prompt by argument(ArgType.String, description = "User message")
    private val stream by option(ArgType.Boolean, fullName = "stream",
        description = "Stream the response").default(false)
    private val system by option(ArgType.String, fullName = "system",
        description = "System message")
    private val temperature by option(ArgType.Double, shortName = "t", fullName = "temperature",
        description = "Temperature (0-2)")
    private val maxTokens by option(ArgType.Int, fullName = "max-tokens",
        description = "Max output tokens")

    override fun execute() = runBlocking {
        val client = buildClient()
        try {
            val messages = mutableListOf<ChatMessage>()
            system?.let { messages.add(ChatMessage("system", JsonPrimitive(it))) }
            messages.add(ChatMessage("user", JsonPrimitive(prompt)))

            val request = ChatRequest(
                model = modelId,
                messages = messages,
                stream = stream,
                temperature = temperature,
                maxTokens = maxTokens
            )

            if (stream) {
                client.streamChat(request).collect { token ->
                    print(token)
                    System.out.flush()
                }
                println()
            } else {
                val response = client.chat(request)
                val content = response.choices.firstOrNull()?.message?.content
                println((content as? JsonPrimitive)?.content ?: content.toString())
            }
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
        } finally {
            client.close()
        }
    }
}
