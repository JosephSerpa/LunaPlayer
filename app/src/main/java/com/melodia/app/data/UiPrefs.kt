package com.melodia.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias de pantalla. [split]: pantalla dividida en horizontal (navegador a la izquierda,
 * reproductor a la derecha). 0 = automático (solo tablets), 1 = activada, 2 = desactivada.
 */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val _split = MutableStateFlow(prefs.getInt("split", 0))
    val split: StateFlow<Int> = _split.asStateFlow()

    private val _homeLife = MutableStateFlow(prefs.getBoolean("home_life", true))
    /** Pequeñas animaciones ocasionales en los cuadros de Inicio (se puede apagar para ahorrar batería). */
    val homeLife: StateFlow<Boolean> = _homeLife.asStateFlow()

    fun setHomeLife(on: Boolean) {
        _homeLife.value = on
        prefs.edit().putBoolean("home_life", on).apply()
    }

    fun setSplit(mode: Int) {
        _split.value = mode
        prefs.edit().putInt("split", mode).apply()
    }
}
