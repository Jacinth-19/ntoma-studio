package com.ntoma.studio.ui.screens.discover

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.GeneratedLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val styles: List<DressStyle> = emptyList(),
    val looks: List<GeneratedLook> = emptyList(),
    val loaded: Boolean = false,
    val offline: Boolean = false,
    val query: String = "",
)

class DiscoverViewModel(container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.dressStyles.observeAll().collect { styles ->
                _state.value = _state.value.copy(styles = styles, loaded = true)
            }
        }
        viewModelScope.launch {
            container.tryOn.observeLooks().collect { looks ->
                _state.value = _state.value.copy(looks = looks)
            }
        }
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }
}

fun Context.isOnline(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
