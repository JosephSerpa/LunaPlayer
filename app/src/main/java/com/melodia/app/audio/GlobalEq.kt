package com.melodia.app.audio

import android.media.audiofx.Equalizer

/**
 * Ecualizador del sistema: se engancha a la mezcla de salida (sesión 0) y afecta a todo lo que suena
 * en el dispositivo, no solo a esta app. Usa el ecualizador por bandas del propio Android: su curva
 * es una aproximación de la nuestra (se mide la curva en el centro de cada una de sus bandas).
 * Algunos fabricantes o apps lo ignoran; por eso [start] devuelve si funcionó.
 */
class GlobalEq {

    private var eq: Equalizer? = null

    val active: Boolean get() = eq != null

    @Suppress("DEPRECATION")
    fun start(settings: EqSettings): Boolean {
        stop()
        return try {
            val e = Equalizer(1000, 0)
            eq = e
            e.enabled = true
            apply(settings)
            true
        } catch (_: Throwable) {
            eq = null
            false
        }
    }

    fun apply(settings: EqSettings) {
        val e = eq ?: return
        try {
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange // milibelios
            val freqs = DoubleArray(n) { (e.getCenterFreq(it.toShort()) / 1000.0).coerceAtLeast(20.0) }
            val curve = EqMath.totalCurve(settings, freqs)
            for (i in 0 until n) {
                val mb = ((curve[i] + settings.boostDb) * 100.0).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(i.toShort(), if (settings.enabled) mb.toShort() else 0)
            }
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        try { eq?.enabled = false; eq?.release() } catch (_: Throwable) {}
        eq = null
    }
}
