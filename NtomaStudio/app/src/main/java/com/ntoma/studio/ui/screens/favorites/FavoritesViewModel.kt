package com.ntoma.studio.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GeneratedLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val fabrics: List<Fabric> = emptyList(),
    val designs: List<DressStyle> = emptyList(),
    val looks: List<GeneratedLook> = emptyList(),
)

class FavoritesViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.favorites.observeFavoriteFabrics().collect { list ->
                _state.value = _state.value.copy(fabrics = list)
            }
        }
        viewModelScope.launch {
            container.favorites.observeFavoriteDesigns().collect { list ->
                _state.value = _state.value.copy(designs = list)
            }
        }
        viewModelScope.launch {
            container.favorites.observeFavoriteLooks().collect { list ->
                _state.value = _state.value.copy(looks = list)
            }
        }
    }

    fun unfavoriteFabric(id: Long) {
        viewModelScope.launch { container.fabricAnalysis.setFavorite(id, false) }
    }

    /** One-shot re-read of every favorites list; used by pull-to-refresh. */
    fun refresh(onComplete: () -> Unit) {
        viewModelScope.launch {
            _state.value = FavoritesUiState(
                fabrics = container.favorites.observeFavoriteFabrics().first(),
                designs = container.favorites.observeFavoriteDesigns().first(),
                looks = container.favorites.observeFavoriteLooks().first(),
            )
            onComplete()
        }
    }

    fun unfavoriteDesign(id: String) {
        viewModelScope.launch { container.favorites.setDesignFavorite(id, false) }
    }

    fun unfavoriteLook(id: Long) {
        viewModelScope.launch { container.tryOn.setFavorite(id, false) }
    }
}
