package com.ntoma.studio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.domain.model.StylePreference
import com.ntoma.studio.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val prefs: UserPreferences = UserPreferences(),
    val recentFabrics: List<Fabric> = emptyList(),
    val recommended: List<DressStyle> = emptyList(),
    val latestLook: GeneratedLook? = null,
    val favoriteCount: Int = 0,
    val analysesLeft: Int = 3,
)

class HomeViewModel(container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(prefs = prefs)
            }
        }
        viewModelScope.launch {
            container.fabricAnalysis.observeAll().collect { fabrics ->
                _state.value = _state.value.copy(recentFabrics = fabrics.take(3))
            }
        }
        viewModelScope.launch {
            container.dressStyles.observeAll().collect { styles ->
                val pref = _state.value.prefs.stylePreference
                val filtered = styles.filter {
                    pref == StylePreference.ALL ||
                        it.gender.name == pref.name ||
                        it.gender == com.ntoma.studio.domain.model.GenderCategory.UNISEX
                }
                _state.value = _state.value.copy(recommended = (filtered.ifEmpty { styles }).take(4))
            }
        }
        viewModelScope.launch {
            container.tryOn.observeLooks().collect { looks ->
                _state.value = _state.value.copy(latestLook = looks.firstOrNull())
            }
        }
        viewModelScope.launch {
            container.entitlements.remainingAnalysesToday().collect { left ->
                _state.value = _state.value.copy(analysesLeft = left)
            }
        }
        viewModelScope.launch {
            container.favorites.countsFlow().collect { counts ->
                _state.value = _state.value.copy(favoriteCount = counts)
            }
        }
    }
}
