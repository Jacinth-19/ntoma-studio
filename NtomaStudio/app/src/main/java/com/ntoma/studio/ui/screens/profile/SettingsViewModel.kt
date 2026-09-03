package com.ntoma.studio.ui.screens.profile

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.LanguageOption
import com.ntoma.studio.domain.model.NotificationPreferences
import com.ntoma.studio.domain.model.ThemeMode
import com.ntoma.studio.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val version: String = "",
)

class SettingsViewModel(private val container: AppContainer, context: Context) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(prefs = prefs)
            }
        }
        _state.value = _state.value.copy(
            version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            } catch (e: PackageManager.NameNotFoundException) {
                "1.0"
            },
        )
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        container.settings.update { it.copy(themeMode = mode) }
    }

    fun setLanguage(lang: LanguageOption) = viewModelScope.launch {
        container.settings.update { it.copy(language = lang) }
    }

    fun setNotifications(transform: (NotificationPreferences) -> NotificationPreferences) = viewModelScope.launch {
        container.settings.update { it.copy(notifications = transform(it.notifications)) }
    }

    fun setAllNotifications(enabled: Boolean) = viewModelScope.launch {
        container.settings.update {
            it.copy(
                notifications = it.notifications.copy(
                    processing = enabled,
                    recommendations = enabled && it.notifications.recommendations,
                    updates = enabled,
                ),
            )
        }
    }

    fun replayOnboarding() = viewModelScope.launch {
        container.settings.update { it.copy(onboardingCompleted = false) }
    }

    fun setDataSaver(enabled: Boolean) = viewModelScope.launch {
        container.settings.update { it.copy(dataSaver = enabled) }
    }

    fun resetPersonalization() = viewModelScope.launch {
        container.feedback.resetAll()
    }
}
