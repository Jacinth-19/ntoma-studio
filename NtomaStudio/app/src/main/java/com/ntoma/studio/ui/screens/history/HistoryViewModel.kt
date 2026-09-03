package com.ntoma.studio.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.HistoryEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HistoryUiState(val events: List<HistoryEvent> = emptyList())

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.history.observeAll().collect { events ->
                _state.value = HistoryUiState(events)
            }
        }
    }

    fun delete(id: Long) = viewModelScope.launch { container.history.delete(id) }
    fun clear() = viewModelScope.launch { container.history.clear() }

    /** One-shot re-read of the history list; used by pull-to-refresh. */
    fun refresh(onComplete: () -> Unit) {
        viewModelScope.launch {
            _state.value = HistoryUiState(container.history.observeAll().first())
            onComplete()
        }
    }
}
