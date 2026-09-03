package com.ntoma.studio

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app language without appcompat. The choice is mirrored into plain SharedPreferences so
 * [wrap] can apply it synchronously in attachBaseContext; Compose then resolves every
 * stringResource against the wrapped configuration.
 */
object AppLocale {

    private const val PREFS = "ntoma_locale"
    private const val KEY = "tag"

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "en") ?: "en"

    fun apply(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag == "en") return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
