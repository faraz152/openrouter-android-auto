package io.openrouter.android.auto.sample.xml

import android.app.Application
import io.openrouter.android.auto.OpenRouterAuto
import io.openrouter.android.auto.SharedPrefsStorage

class App : Application() {

    var openRouter: OpenRouterAuto? = null
        private set

    fun initSdk(apiKey: String) {
        openRouter?.dispose()
        openRouter = OpenRouterAuto.Builder(
            apiKey = apiKey,
            errorsJson = readRaw(io.openrouter.android.auto.R.raw.errors),
            parametersJson = readRaw(io.openrouter.android.auto.R.raw.parameters),
            platformParamsJson = readRaw(io.openrouter.android.auto.R.raw.platform_params),
            costJson = readRaw(io.openrouter.android.auto.R.raw.cost)
        )
            .storage(SharedPrefsStorage(this))
            .build()
    }

    private fun readRaw(id: Int): String =
        resources.openRawResource(id).bufferedReader().use { it.readText() }
}
