package com.melodia.app.audio

import com.melodia.app.R
import com.melodia.app.Str
import kotlin.math.pow

const val SPEED_MIN = 0.5f
const val SPEED_MAX = 1.5f
const val PITCH_MIN_ST = -12f
const val PITCH_MAX_ST = 12f

/** Valores con los que empieza la función (y a los que vuelve "Restablecer"). */
const val FX_DEFAULT_SPEED = 0.85f
const val FX_DEFAULT_PITCH = -3.5f

/**
 * Con velocidad y tono unidos: semitonos que cambia el tono por cada 1× de velocidad.
 * Sale de los valores iniciales (0.85× ↔ −3.5 st) y deja ambos controles dentro de su rango.
 */
const val FX_LINK_ST_PER_X = 3.5f / 0.15f

/**
 * Efectos "slow + reverb" en tiempo real.
 * [speed]: velocidad (1 = normal). [pitchSt]: tono en semitonos.
 * [reverbMix]: cuánta reverberación (0..1). [roomSize]: tamaño de la sala (0..1).
 * [damping]: cuánto se apagan los agudos de la cola (0..1).
 */
data class FxSettings(
    val speed: Float = FX_DEFAULT_SPEED,
    val pitchSt: Float = FX_DEFAULT_PITCH,
    val reverbMix: Float = 0f,
    val roomSize: Float = 0.6f,
    val damping: Float = 0.5f,
    val preset: String = "Normal",
    /** Efecto encendido. Apagado conserva los valores pero suena normal (botón agujero negro). */
    val enabled: Boolean = false,
    /** Off vocal: quitar la voz en tiempo real y con qué intensidad (0..1). */
    val vocalOff: Boolean = false,
    val vocalAmount: Float = 0.8f,
    /** Velocidad y tono se mueven juntos (al mover uno, el otro lo acompaña). */
    val linked: Boolean = true,
    /** Punto de partida del vínculo: desde aquí se calcula cuánto acompaña el otro control. */
    val anchorSpeed: Float = FX_DEFAULT_SPEED,
    val anchorPitch: Float = FX_DEFAULT_PITCH
) {
    /** Lo que realmente se aplica: con el efecto apagado, todo neutro. */
    val effSpeed: Float get() = if (enabled) speed else 1f
    /** Factor de tono para Media3 (2^(semitonos/12)). */
    val pitchFactor: Float get() = if (enabled) 2f.pow(pitchSt / 12f) else 1f
    val effReverbMix: Float get() = if (enabled) reverbMix else 0f
    val isNeutral: Boolean
        get() = speed == 1f && pitchSt == 0f && reverbMix == 0f
}

/** Ajuste de slow + reverb guardado por el usuario (solo existen los que él crea). */
data class FxProfile(
    val name: String,
    val speed: Float,
    val pitchSt: Float,
    val reverbMix: Float,
    val roomSize: Float,
    val damping: Float
)

/** Los ajustes del usuario se muestran tal cual; solo "Personalizado" (nombre interno) se traduce. */
fun fxPresetLabel(key: String): String = when (key) {
    "Personalizado" -> Str.get(R.string.eqp_custom)
    else -> key
}
