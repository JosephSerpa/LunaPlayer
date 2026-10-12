package com.melodia.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Idioma de la app: "system" (el del teléfono), "es" o "en".
 * Se guarda aparte y se aplica envolviendo el contexto.
 */
object LocaleManager {
    const val SYSTEM = "system"
    private const val PREFS = "language"
    private const val KEY = "code"

    fun saved(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM)?.takeIf { it == "es" || it == "en" } ?: SYSTEM

    fun save(context: Context, code: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
    }

    /** Devuelve el contexto con el idioma elegido (o el mismo si se sigue al sistema). */
    fun wrap(context: Context): Context {
        val code = saved(context)
        if (code == SYSTEM) return context
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
