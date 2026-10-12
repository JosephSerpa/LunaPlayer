package com.melodia.app.audio

import androidx.annotation.StringRes
import com.melodia.app.R
import com.melodia.app.Str
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Puntos del ecualizador: de 0 a 7 (se empieza con 5). */
const val EQ_BANDS = 7
const val EQ_MIN_BANDS = 0
const val EQ_MIN_DB = -20f
const val EQ_MAX_DB = 20f
const val PREAMP_MIN_DB = -20f
const val PREAMP_MAX_DB = 20f
const val Q_MIN = 0.3f
const val Q_MAX = 8f
const val GRAPH_MIN_HZ = 20.0
const val GRAPH_MAX_HZ = 20000.0

/** Frecuencias con que empiezan los 5 puntos. */
val DEFAULT_FREQS = listOf(60f, 250f, 1000f, 4000f, 12000f)

/** Comportamiento de la curva de un punto. */
enum class FilterType(@StringRes val labelRes: Int, val usesGain: Boolean, val defaultQ: Float) {
    PEAK(R.string.ft_peak, true, 1.0f),
    LOW_SHELF(R.string.ft_low_shelf, true, 0.71f),
    HIGH_SHELF(R.string.ft_high_shelf, true, 0.71f),
    LOW_PASS(R.string.ft_low_pass, false, 0.71f),
    HIGH_PASS(R.string.ft_high_pass, false, 0.71f),
    BAND_PASS(R.string.ft_band_pass, false, 1.0f),
    NOTCH(R.string.ft_notch, false, 4.0f);

    /** Nombre en el idioma del dispositivo. */
    val label: String get() = Str.get(labelRes)
}

/**
 * Los nombres de los perfiles se guardan en español (son la "clave"); aquí se traducen
 * al mostrarlos. Los perfiles propios del usuario se muestran tal cual.
 */
fun presetLabel(key: String): String = when (key) {
    "Plano" -> Str.get(R.string.eqp_flat)
    "Graves potentes" -> Str.get(R.string.eqp_bass)
    "Agudos brillantes" -> Str.get(R.string.eqp_treble)
    "Vocal" -> Str.get(R.string.eqp_vocal)
    "Rock" -> Str.get(R.string.eqp_rock)
    "Electrónica" -> Str.get(R.string.eqp_electronic)
    "Acústico" -> Str.get(R.string.eqp_acoustic)
    "Nocturno" -> Str.get(R.string.eqp_night)
    "Personalizado" -> Str.get(R.string.eqp_custom)
    else -> key
}

/** Un punto del ecualizador: tipo de filtro, frecuencia, ganancia en dB y Q. */
data class EqBand(
    val freqHz: Float,
    val gainDb: Float,
    val q: Float,
    val type: FilterType = FilterType.PEAK
)

data class EqSettings(
    val enabled: Boolean = true,
    val preampDb: Float = 0f,
    val bands: List<EqBand> = defaultBands(),
    val preset: String = "Plano"
)

fun defaultBands(gains: List<Float> = List(DEFAULT_FREQS.size) { 0f }): List<EqBand> =
    DEFAULT_FREQS.mapIndexed { i, f -> EqBand(f, gains.getOrElse(i) { 0f }, 1.0f) }

data class EqPreset(val name: String, val gains: List<Float>)

val EQ_PRESETS = listOf(
    EqPreset("Plano", listOf(0f, 0f, 0f, 0f, 0f)),
    EqPreset("Graves potentes", listOf(8f, 4f, 0f, 0f, 0f)),
    EqPreset("Agudos brillantes", listOf(0f, 0f, 0f, 4f, 8f)),
    EqPreset("Vocal", listOf(-3f, -1f, 4f, 3f, 0f)),
    EqPreset("Rock", listOf(5f, -2f, -1f, 4f, 6f)),
    EqPreset("Electrónica", listOf(7f, 1f, -2f, 3f, 6f)),
    EqPreset("Acústico", listOf(3f, 1f, 1f, 3f, 3f)),
    EqPreset("Nocturno", listOf(2f, -1f, -3f, -4f, -5f))
)

/** Matemática de los filtros; la comparten el procesador de audio y la gráfica. */
object EqMath {

    /** Coeficientes normalizados [b0, b1, b2, a1, a2] del filtro del punto (RBJ cookbook). */
    fun coefficients(band: EqBand, sampleRate: Double): DoubleArray {
        val f = band.freqHz.toDouble().coerceIn(10.0, sampleRate * 0.45)
        val gainDb = if (band.type.usesGain) band.gainDb.toDouble() else 0.0
        val q = band.q.toDouble().coerceAtLeast(0.05)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2.0 * q)
        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        when (band.type) {
            FilterType.PEAK -> {
                b0 = 1.0 + alpha * a; b1 = -2.0 * cosW; b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a; a1 = -2.0 * cosW; a2 = 1.0 - alpha / a
            }
            FilterType.LOW_PASS -> {
                b0 = (1.0 - cosW) / 2.0; b1 = 1.0 - cosW; b2 = (1.0 - cosW) / 2.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosW; a2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1.0 + cosW) / 2.0; b1 = -(1.0 + cosW); b2 = (1.0 + cosW) / 2.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosW; a2 = 1.0 - alpha
            }
            FilterType.BAND_PASS -> {
                b0 = alpha; b1 = 0.0; b2 = -alpha
                a0 = 1.0 + alpha; a1 = -2.0 * cosW; a2 = 1.0 - alpha
            }
            FilterType.NOTCH -> {
                b0 = 1.0; b1 = -2.0 * cosW; b2 = 1.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosW; a2 = 1.0 - alpha
            }
            FilterType.LOW_SHELF -> {
                val beta = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1.0) - (a - 1.0) * cosW + beta)
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
                b2 = a * ((a + 1.0) - (a - 1.0) * cosW - beta)
                a0 = (a + 1.0) + (a - 1.0) * cosW + beta
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
                a2 = (a + 1.0) + (a - 1.0) * cosW - beta
            }
            FilterType.HIGH_SHELF -> {
                val beta = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1.0) + (a - 1.0) * cosW + beta)
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)
                b2 = a * ((a + 1.0) + (a - 1.0) * cosW - beta)
                a0 = (a + 1.0) - (a - 1.0) * cosW + beta
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW)
                a2 = (a + 1.0) - (a - 1.0) * cosW - beta
            }
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** ¿Este punto cambia el sonido? (una campana o tono a 0 dB no hace nada). */
    fun isActive(band: EqBand): Boolean =
        !band.type.usesGain || kotlin.math.abs(band.gainDb) >= 0.05f

    /** Respuesta en frecuencia (dB) de un biquad en la frecuencia dada. */
    fun magnitudeDb(c: DoubleArray, sampleRate: Double, freq: Double): Double {
        val w = 2.0 * PI * freq / sampleRate
        val cw = cos(w)
        val sw = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)
        val nr = c[0] + c[1] * cw + c[2] * c2
        val ni = -(c[1] * sw + c[2] * s2)
        val dr = 1.0 + c[3] * cw + c[4] * c2
        val di = -(c[3] * sw + c[4] * s2)
        val mag2 = (nr * nr + ni * ni) / (dr * dr + di * di)
        return 10.0 * log10(mag2.coerceAtLeast(1e-12))
    }

    /** Frecuencias espaciadas en escala logarítmica para dibujar la curva. */
    fun logFrequencies(count: Int): DoubleArray {
        val lo = log10(GRAPH_MIN_HZ)
        val hi = log10(GRAPH_MAX_HZ)
        return DoubleArray(count) { 10.0.pow(lo + (hi - lo) * it / (count - 1)) }
    }

    /** Respuesta de un solo punto. */
    fun bandCurve(band: EqBand, freqs: DoubleArray, sampleRate: Double = 48000.0): DoubleArray {
        val c = coefficients(band, sampleRate)
        return DoubleArray(freqs.size) { magnitudeDb(c, sampleRate, freqs[it]) }
    }

    /** Respuesta total del ecualizador (todos los puntos + ganancia previa). */
    fun totalCurve(settings: EqSettings, freqs: DoubleArray, sampleRate: Double = 48000.0): DoubleArray {
        val out = DoubleArray(freqs.size) { settings.preampDb.toDouble() }
        for (band in settings.bands) {
            if (!isActive(band)) continue
            val c = coefficients(band, sampleRate)
            for (i in freqs.indices) out[i] += magnitudeDb(c, sampleRate, freqs[i])
        }
        return out
    }
}
