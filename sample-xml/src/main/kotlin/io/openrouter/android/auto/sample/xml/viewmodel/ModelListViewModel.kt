package io.openrouter.android.auto.sample.xml.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.openrouter.android.auto.ModelFilter
import io.openrouter.android.auto.OpenRouterAuto
import io.openrouter.android.auto.OpenRouterModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ModelListViewModel(private val sdk: OpenRouterAuto) : ViewModel() {

    private val _search = MutableStateFlow("")
    private val _allModels = MutableStateFlow(sdk.getModels())

    val models: StateFlow<List<OpenRouterModel>> = combine(_allModels, _search) { all, query ->
        if (query.isBlank()) all
        else sdk.filterModels(ModelFilter(search = query))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sdk.getModels())

    fun setSearch(query: String) {
        _search.value = query
    }

    class Factory(private val sdk: OpenRouterAuto) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelListViewModel(sdk) as T
    }
}
