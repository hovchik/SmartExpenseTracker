package com.smartexpense.tracker.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Manages app-level locale overrides for runtime language switching.
 * Stores the user's chosen language code in SharedPreferences and
 * applies it via [applyLocale] at startup.
 */
object LocaleManager {

    private const val PREFS_NAME = "flowsense_locale"
    private const val KEY_LANGUAGE = "language_code"

    /** All languages the app ships translations for. */
    val SUPPORTED_LANGUAGES: List<LanguageOption> = listOf(
        LanguageOption("system", "System Default", ""),
        LanguageOption("en", "English", "English"),
        LanguageOption("es", "Español", "Spanish"),
        LanguageOption("fr", "Français", "French"),
        LanguageOption("de", "Deutsch", "German"),
        LanguageOption("pt", "Português", "Portuguese"),
        LanguageOption("ru", "Русский", "Russian"),
        LanguageOption("ja", "日本語", "Japanese"),
        LanguageOption("zh", "中文", "Chinese"),
        LanguageOption("ko", "한국어", "Korean"),
        LanguageOption("hi", "हिन्दी", "Hindi"),
        LanguageOption("ar", "العربية", "Arabic"),
        LanguageOption("hy", "Հայերեն", "Armenian"),
        LanguageOption("tr", "Türkçe", "Turkish"),
        LanguageOption("it", "Italiano", "Italian"),
        LanguageOption("nl", "Nederlands", "Dutch")
    )

    data class LanguageOption(
        val code: String,
        val nativeName: String,
        val englishName: String
    ) {
        val displayName: String
            get() = if (englishName.isNotEmpty()) "$nativeName ($englishName)" else nativeName
    }

    /** Returns the persisted language code, or "system" if none. */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "system") ?: "system"
    }

    /** Persists the chosen language code. Call [applyLocale] after this. */
    fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }

    /**
     * Wraps [baseContext] with the user-selected locale.
     * Call from [android.app.Application.attachBaseContext] and
     * [android.app.Activity.attachBaseContext].
     */
    fun applyLocale(baseContext: Context): Context {
        val langCode = getLanguage(baseContext)
        if (langCode == "system" || langCode.isEmpty()) return baseContext

        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }

    /** Returns the currently active locale for the app. */
    fun currentLocale(context: Context): Locale {
        val langCode = getLanguage(context)
        return if (langCode == "system" || langCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(langCode)
        }
    }
}
