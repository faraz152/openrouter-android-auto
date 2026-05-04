package io.openrouter.android.auto.sample.compose

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.openrouter.android.auto.ORAError
import io.openrouter.android.auto.ORAErrorCode
import io.openrouter.android.auto.OpenRouterAuto
import io.openrouter.android.auto.OpenRouterModel
import io.openrouter.android.auto.SharedPrefsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("sample_compose_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKey = _apiKey.asStateFlow()

    private val _models = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val models = _models.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<ORAError?>(null)
    val error = _error.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private var _sdk: OpenRouterAuto? = null

    fun getClient(): OpenRouterAuto? = _sdk

    fun connect(apiKey: String) {
        val context = getApplication<Application>()
        prefs.edit().putString("api_key", apiKey).apply()
        _apiKey.value = apiKey

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val client = OpenRouterAuto.Builder(
                    apiKey = apiKey,
                    errorsJson = context.readRaw(io.openrouter.android.auto.R.raw.errors),
                    parametersJson = context.readRaw(io.openrouter.android.auto.R.raw.parameters),
                    platformParamsJson = context.readRaw(io.openrouter.android.auto.R.raw.platform_params),
                    costJson = context.readRaw(io.openrouter.android.auto.R.raw.cost)
                )
                    .storage(SharedPrefsStorage(context))
                    .build()

                client.initialize()
                _sdk = client
                _models.value = client.getModels()
                _isConnected.value = true
            } catch (e: ORAError) {
                _error.value = e
            } catch (e: Exception) {
                _error.value = ORAError(
                    ORAErrorCode.NETWORK_ERROR,
                    e.message ?: "Unknown error",
                    retryable = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        _sdk?.dispose()
    }
}

internal fun Context.readRaw(id: Int): String =
    resources.openRawResource(id).bufferedReader().use { it.readText() }
