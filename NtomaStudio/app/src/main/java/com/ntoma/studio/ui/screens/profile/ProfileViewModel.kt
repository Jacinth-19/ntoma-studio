package com.ntoma.studio.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val prefs: UserPreferences = UserPreferences(),
    val fabricCount: Int = 0,
    val designCount: Int = 0,
    val lookCount: Int = 0,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(prefs = prefs)
            }
        }
        viewModelScope.launch {
            container.fabricAnalysis.observeAll().collect { list ->
                _state.value = _state.value.copy(fabricCount = list.size)
            }
        }
        viewModelScope.launch {
            container.dressStyles.observeAll().collect { list ->
                _state.value = _state.value.copy(designCount = list.size)
            }
        }
        viewModelScope.launch {
            container.tryOn.observeLooks().collect { list ->
                _state.value = _state.value.copy(lookCount = list.size)
            }
        }
    }

    fun setName(name: String) = viewModelScope.launch {
        container.settings.update { it.copy(displayName = name.trim()) }
    }
}
