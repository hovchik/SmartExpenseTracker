package com.smartexpense.tracker.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Helper for managing the app locale independently of the system locale.
 *
 * Stores the user's language preference in SharedPreferences (fast startup access)
 * and applies it by wrapping the base context in [wrapContext].
 */
object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    /** Special value meaning "follow the system locale". */
    const val SYSTEM = "system"

    /** All language codes supported by the app. */
    val SUPPORTED_LANGUAGES = listOf(SYSTEM, "en", "ru", "hy", "zh", "es")

    /** Read the persisted language code (or [SYSTEM]). */
    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM
    }

    /** Persist a new language code and return a context wrapped in that locale. */
    fun setLanguage(context: Context, languageCode: String): Context {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
        return wrapContext(context, languageCode)
    }

    /**
     * Wrap [context] so its resources resolve to the given locale.
     * Call from `attachBaseContext` **and** when the user changes the language at runtime.
     */
    fun wrapContext(context: Context, languageCode: String = getLanguage(context)): Context {
        val locale = if (languageCode == SYSTEM) {
            // Use the first system locale
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList.getDefault()[0]
            } else {
                @Suppress("DEPRECATION")
                Locale.getDefault()
            }
        } else {
            Locale.forLanguageTag(languageCode)
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))

        return context.createConfigurationContext(config)
    }
}
