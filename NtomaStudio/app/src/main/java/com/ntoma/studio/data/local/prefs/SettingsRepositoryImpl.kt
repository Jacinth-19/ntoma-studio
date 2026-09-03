package com.ntoma.studio.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ntoma.studio.domain.model.LanguageOption
import com.ntoma.studio.domain.model.NotificationPreferences
import com.ntoma.studio.domain.model.StylePreference
import com.ntoma.studio.domain.model.ThemeMode
import com.ntoma.studio.domain.model.UserPreferences
import com.ntoma.studio.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "ntoma_settings")

/**
 * Persists user preferences. First-run tooltip completion flags live in a separate store so
 * resetting tooltips never touches real settings.
 */
class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val store = context.settingsStore

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIF_PROCESSING = booleanPreferencesKey("notif_processing")
        val NOTIF_RECOMMENDATIONS = booleanPreferencesKey("notif_recommendations")
        val NOTIF_UPDATES = booleanPreferencesKey("notif_updates")
        val NOTIF_INSPIRATION = booleanPreferencesKey("notif_inspiration")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val STYLE_PREF = stringPreferencesKey("style_pref")
        val KEEP_HISTORY_DAYS = intPreferencesKey("keep_history_days")
        val PREMIUM = booleanPreferencesKey("premium_demo")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val FAVORITE_TAILORS = stringPreferencesKey("favorite_tailors")
        val ENGINE = stringPreferencesKey("analysis_engine")
        val CLOUD_URL = stringPreferencesKey("cloud_base_url")
    }

    override val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            themeMode = ThemeMode.values().firstOrNull { it.name == p[Keys.THEME] } ?: ThemeMode.SYSTEM,
            language = LanguageOption.values().firstOrNull { it.name == p[Keys.LANGUAGE] } ?: LanguageOption.ENGLISH,
            notifications = NotificationPreferences(
                processing = p[Keys.NOTIF_PROCESSING] ?: true,
                recommendations = p[Keys.NOTIF_RECOMMENDATIONS] ?: false,
                updates = p[Keys.NOTIF_UPDATES] ?: true,
                inspiration = p[Keys.NOTIF_INSPIRATION] ?: false,
            ),
            displayName = p[Keys.DISPLAY_NAME].orEmpty(),
            stylePreference = StylePreference.values().firstOrNull { it.name == p[Keys.STYLE_PREF] } ?: StylePreference.ALL,
            keepHistoryDays = p[Keys.KEEP_HISTORY_DAYS] ?: 0,
            premiumEnabled = p[Keys.PREMIUM] ?: false,
            onboardingCompleted = p[Keys.ONBOARDED] ?: false,
            dataSaver = p[Keys.DATA_SAVER] ?: false,
            favoriteTailorIds = p[Keys.FAVORITE_TAILORS].orEmpty()
                .split(",").filter { it.isNotBlank() }.toSet(),
            analysisEngine = com.ntoma.studio.domain.model.AnalysisEngine.values()
                .firstOrNull { it.name == p[Keys.ENGINE] }
                ?: com.ntoma.studio.domain.model.AnalysisEngine.ON_DEVICE_DEMO,
            cloudBaseUrl = p[Keys.CLOUD_URL].orEmpty(),
        )
    }

    override suspend fun current(): UserPreferences = preferences.first()

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        val next = transform(current())
        store.edit { p ->
            p[Keys.THEME] = next.themeMode.name
            p[Keys.LANGUAGE] = next.language.name
            p[Keys.NOTIF_PROCESSING] = next.notifications.processing
            p[Keys.NOTIF_RECOMMENDATIONS] = next.notifications.recommendations
            p[Keys.NOTIF_UPDATES] = next.notifications.updates
            p[Keys.NOTIF_INSPIRATION] = next.notifications.inspiration
            p[Keys.DISPLAY_NAME] = next.displayName
            p[Keys.STYLE_PREF] = next.stylePreference.name
            p[Keys.KEEP_HISTORY_DAYS] = next.keepHistoryDays
            p[Keys.PREMIUM] = next.premiumEnabled
            p[Keys.ONBOARDED] = next.onboardingCompleted
            p[Keys.DATA_SAVER] = next.dataSaver
            p[Keys.FAVORITE_TAILORS] = next.favoriteTailorIds.joinToString(",")
            p[Keys.ENGINE] = next.analysisEngine.name
            p[Keys.CLOUD_URL] = next.cloudBaseUrl
        }
    }
}

private val Context.tooltipStore by preferencesDataStore(name = "ntoma_tooltips")

/** First-run tooltip completion flags, resettable from Settings. */
class TooltipStore(context: Context) {
    private val store = context.tooltipStore

    companion object {
        val CREATE = booleanPreferencesKey("tooltip_create")
        val FAVORITES = booleanPreferencesKey("tooltip_favorites")
        val TRYON = booleanPreferencesKey("tooltip_tryon")

        suspend fun reset(context: Context) {
            context.tooltipStore.edit { it.clear() }
        }
    }

    fun seen(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>): Flow<Boolean> =
        store.data.map { it[key] ?: false }

    suspend fun markSeen(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>) {
        store.edit { it[key] = true }
    }
}
