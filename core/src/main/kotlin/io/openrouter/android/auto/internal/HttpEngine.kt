package io.openrouter.android.auto.internal

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

private val sdkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

/**
 * Internal Ktor [HttpClient] factory.
 * Created once per [OpenRouterAuto] instance and reused for all requests.
 * Pass a custom [engine] (e.g. [MockEngine]) for testing.
 */
internal class HttpEngine(engine: HttpClientEngine = CIO.create()) {

    val client: HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(sdkJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 60_000L
        }
    }

    fun close() = client.close()
}
