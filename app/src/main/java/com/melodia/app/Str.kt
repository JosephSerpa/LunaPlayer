package com.melodia.app

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Acceso a los textos traducidos (español base + inglés) desde cualquier parte de la app,
 * incluido código que no es @Composable. Los textos siguen el idioma del dispositivo.
 */
object Str {
    private lateinit var app: Context

    /** Recibe el contexto ya con el idioma elegido (ver [LocaleManager.wrap]). */
    fun init(context: Context) {
        app = context
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) app.getString(id) else app.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        app.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))
}
