package com.ntoma.studio.domain.model

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: LanguageOption = LanguageOption.ENGLISH,
    val notifications: NotificationPreferences = NotificationPreferences(),
    val displayName: String = "",
    val stylePreference: StylePreference = StylePreference.ALL,
    /** Days to retain history; 0 = keep forever. */
    val keepHistoryDays: Int = 0,
    val premiumEnabled: Boolean = false,
    /** Lower image quality + thumbnails to save mobile data. */
    val dataSaver: Boolean = false,
    /** Ids of demo/real tailors the user bookmarked. */
    val favoriteTailorIds: Set<String> = emptySet(),
    val onboardingCompleted: Boolean = false,
    /** Analysis engine choice; CLOUD only takes effect when [cloudBaseUrl] is set. */
    val analysisEngine: AnalysisEngine = AnalysisEngine.ON_DEVICE_DEMO,
    /** Operator-configured analysis backend; blank = fully on-device. */
    val cloudBaseUrl: String = "",
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class LanguageOption(val tag: String, val labelRes: Int) {
    ENGLISH("en", com.ntoma.studio.R.string.settings_language_english),
    TWI("tw", com.ntoma.studio.R.string.settings_language_twi),
    GA("gaa", com.ntoma.studio.R.string.settings_language_ga),
    EWE("ee", com.ntoma.studio.R.string.settings_language_ewe),
}

data class NotificationPreferences(
    val processing: Boolean = true,
    val recommendations: Boolean = false,
    val updates: Boolean = true,
    val inspiration: Boolean = false,
)

enum class StylePreference(val labelRes: Int) {
    ALL(com.ntoma.studio.R.string.profile_preference_all),
    WOMEN(com.ntoma.studio.R.string.profile_preference_womenswear),
    MEN(com.ntoma.studio.R.string.profile_preference_menswear),
}
