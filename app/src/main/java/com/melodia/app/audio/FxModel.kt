package com.melodia.app.audio

import com.melodia.app.R
import com.melodia.app.Str
import kotlin.math.pow

const val SPEED_MIN = 0.5f
const val SPEED_MAX = 1.5f
const val PITCH_MIN_ST = -12f
const val PITCH_MAX_ST = 12f

/**
 * Efectos "slow + reverb" en tiempo real.
 * [speed]: velocidad (1 = normal). [pitchSt]: tono en semitonos.
 * [reverbMix]: cuánta reverberación (0..1). [roomSize]: tamaño de la sala (0..1).
 * [damping]: cuánto se apagan los agudos de la cola (0..1).
 */
data class FxSettings(
    val speed: Float = 1f,
    val pitchSt: Float = 0f,
    val reverbMix: Float = 0f,
    val roomSize: Float = 0.6f,
    val damping: Float = 0.5f,
    val preset: String = "Normal",
    /** Off vocal: quitar la voz en tiempo real y con qué intensidad (0..1). */
    val vocalOff: Boolean = false,
    val vocalAmount: Float = 0.8f
) {
    /** Factor de tono para Media3 (2^(semitonos/12)). */
    val pitchFactor: Float get() = 2f.pow(pitchSt / 12f)
    val isNeutral: Boolean
        get() = speed == 1f && pitchSt == 0f && reverbMix == 0f
}

data class FxPreset(val name: String, val settings: FxSettings)

val FX_PRESETS = listOf(
    FxPreset("Normal", FxSettings()),
    // Lento y más grave, con mucha sala: el clásico "slowed + reverb".
    FxPreset("Slowed + Reverb", FxSettings(0.85f, -2.8f, 0.45f, 0.82f, 0.45f, "Slowed + Reverb")),
    FxPreset("Muy lento", FxSettings(0.75f, -5f, 0.55f, 0.9f, 0.55f, "Muy lento")),
    FxPreset("Sala", FxSettings(1f, 0f, 0.3f, 0.6f, 0.4f, "Sala")),
    FxPreset("Nightcore", FxSettings(1.25f, 3.9f, 0.12f, 0.45f, 0.3f, "Nightcore"))
)

/** Los nombres de los ajustes se guardan en español (son la "clave"); aquí se traducen al mostrarlos. */
fun fxPresetLabel(key: String): String = when (key) {
    "Normal" -> Str.get(R.string.fxp_normal)
    "Slowed + Reverb" -> Str.get(R.string.fxp_slowed)
    "Muy lento" -> Str.get(R.string.fxp_very_slow)
    "Sala" -> Str.get(R.string.fxp_room)
    "Nightcore" -> Str.get(R.string.fxp_nightcore)
    "Personalizado" -> Str.get(R.string.eqp_custom)
    else -> key
}
