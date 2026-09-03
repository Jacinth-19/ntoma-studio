package com.ntoma.studio.ui.screens.fabric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.domain.repository.AnalysisEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val stages: List<Int> = listOf(
        R.string.analysis_stage_preparing,
        R.string.analysis_stage_colors,
        R.string.analysis_stage_pattern,
        R.string.analysis_stage_styles,
        R.string.analysis_stage_finishing,
    ),
    val currentStage: Int = 0,
    val analyzing: Boolean = true,
    val quotaExhausted: Boolean = false,
    val fabric: Fabric? = null,
    val savedId: Long? = null,
    val error: AppError? = null,
    val renameOpen: Boolean = false,
)

class AnalysisViewModel(
    private val container: AppContainer,
    private val uri: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (!container.entitlements.canAnalyze()) {
                _state.value = _state.value.copy(analyzing = false, quotaExhausted = true)
                return@launch
            }
            container.entitlements.consumeAnalysis()
            container.history.record(
                HistoryEvent(kind = HistoryEvent.Kind.SCAN, imageUri = uri, timestamp = System.currentTimeMillis()),
            )
            container.fabricAnalysis.analyze(uri).collect { event ->
                when (event) {
                    is AnalysisEvent.Stage -> {
                        val idx = _state.value.stages.indexOf(event.stageRes)
                        _state.value = _state.value.copy(currentStage = idx.coerceAtLeast(0))
                    }
                    is AnalysisEvent.Completed -> {
                        val id = container.fabricAnalysis.save(event.fabric)
                        _state.value = _state.value.copy(
                            analyzing = false,
                            fabric = event.fabric.copy(id = id),
                            savedId = id,
                        )
                    }
                    is AnalysisEvent.Failed -> {
                        _state.value = _state.value.copy(analyzing = false, error = event.error)
                    }
                }
            }
        }
    }

    fun retry() {
        _state.value = AnalysisUiState()
        viewModelScope.launch {
            if (!container.entitlements.canAnalyze()) {
                _state.value = _state.value.copy(analyzing = false, quotaExhausted = true)
                return@launch
            }
            container.entitlements.consumeAnalysis()
            container.fabricAnalysis.analyze(uri).collect { event ->
                when (event) {
                    is AnalysisEvent.Stage -> {
                        val idx = _state.value.stages.indexOf(event.stageRes)
                        _state.value = _state.value.copy(currentStage = idx.coerceAtLeast(0))
                    }
                    is AnalysisEvent.Completed -> {
                        val id = container.fabricAnalysis.save(event.fabric)
                        _state.value = _state.value.copy(analyzing = false, fabric = event.fabric.copy(id = id), savedId = id)
                    }
                    is AnalysisEvent.Failed -> {
                        _state.value = _state.value.copy(analyzing = false, error = event.error)
                    }
                }
            }
        }
    }

    fun toggleFavorite() {
        val fabric = _state.value.fabric ?: return
        viewModelScope.launch {
            val now = !fabric.isFavorite
            container.fabricAnalysis.setFavorite(fabric.id, now)
            _state.value = _state.value.copy(fabric = fabric.copy(isFavorite = now))
        }
    }

    fun openRename() {
        _state.value = _state.value.copy(renameOpen = true)
    }

    fun closeRename() {
        _state.value = _state.value.copy(renameOpen = false)
    }

    fun rename(name: String) {
        val fabric = _state.value.fabric ?: return
        viewModelScope.launch {
            container.fabricAnalysis.rename(fabric.id, name)
            _state.value = _state.value.copy(fabric = fabric.copy(name = name), renameOpen = false)
        }
    }
}
