package com.ntoma.studio.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DataControlsUiState(val prefs: UserPreferences = UserPreferences())

class DataControlsViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DataControlsUiState())
    val state: StateFlow<DataControlsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.preferences.collect { prefs -> _state.value = DataControlsUiState(prefs) }
        }
    }

    fun setRetention(days: Int) = viewModelScope.launch {
        container.settings.update { it.copy(keepHistoryDays = days) }
        if (days > 0) container.history.pruneOlderThan(days)
    }

    fun clearHistory() = viewModelScope.launch { container.history.clear() }

    /** Wipes scans, looks, history, favourites, quotas and stored media from this device. */
    fun deleteAll() = viewModelScope.launch {
        val db = container.database
        db.fabricDao().deleteAll()
        db.lookDao().deleteAll()
        db.historyDao().clear()
        db.dressStyleDao().clearFavorites()
        db.usageDao().clear()
        File(context.filesDir, "media").deleteRecursively()
        File(context.cacheDir, "shared").deleteRecursively()
    }
}
