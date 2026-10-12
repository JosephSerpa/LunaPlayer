package com.melodia.app.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Ecualizador paramétrico de hasta 7 puntos que trabaja directamente sobre el audio PCM
 * antes de que salga al altavoz. Cada punto es un filtro (biquad) de un tipo
 * (campana, tonos, pasos, muesca) con frecuencia, ganancia (±20 dB) y Q propios.
 */
@OptIn(UnstableApi::class)
class EqualizerAudioProcessor : BaseAudioProcessor() {

    @Volatile private var settings = EqSettings()
    @Volatile private var version = 0
    private var appliedVersion = -1

    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID

    private val coeffs = Array(EQ_BANDS) { DoubleArray(5) }
    private val bandActive = BooleanArray(EQ_BANDS)
    private var preamp = 1.0

    /** Memoria de cada filtro: [canal][banda][2]. */
    private var state = DoubleArray(0)

    /** Se llama desde la interfaz; el cambio se aplica en el siguiente bloque de audio. */
    fun setSettings(newSettings: EqSettings) {
        settings = newSettings
        version += 1
    }

    @Throws(AudioProcessor.UnhandledAudioFormatException::class)
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        state = DoubleArray(channels * EQ_BANDS * 2)
        appliedVersion = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        // Al saltar en la canción se limpia la memoria de los filtros.
        Arrays.fill(state, 0.0)
        appliedVersion = -1
    }

    override fun onReset() {
        state = DoubleArray(0)
        sampleRate = 0
        channels = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)

        val v = version
        val s = settings
        if (!s.enabled || sampleRate == 0 || channels == 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (appliedVersion != v) {
            updateCoefficients(s)
            appliedVersion = v
        }

        var ch = 0
        if (encoding == C.ENCODING_PCM_16BIT) {
            val scale = preamp / 32768.0
            while (inputBuffer.remaining() >= 2) {
                val x = softLimit(filter(ch, inputBuffer.short * scale))
                output.putShort((x * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
                ch += 1
                if (ch == channels) ch = 0
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                val x = softLimit(filter(ch, inputBuffer.float * preamp))
                output.putFloat(x.toFloat())
                ch += 1
                if (ch == channels) ch = 0
            }
        }
        output.flip()
    }

    private fun updateCoefficients(s: EqSettings) {
        preamp = 10.0.pow(s.preampDb / 20.0)
        for (b in 0 until EQ_BANDS) {
            val band = s.bands.getOrNull(b)
            if (band == null || !EqMath.isActive(band)) {
                bandActive[b] = false
            } else {
                bandActive[b] = true
                val c = EqMath.coefficients(band, sampleRate.toDouble())
                System.arraycopy(c, 0, coeffs[b], 0, 5)
            }
        }
    }

    /** Pasa una muestra por las 7 bandas en cascada (forma transpuesta II). */
    private fun filter(channel: Int, input: Double): Double {
        var x = input
        var base = channel * EQ_BANDS * 2
        for (b in 0 until EQ_BANDS) {
            if (bandActive[b]) {
                val k = coeffs[b]
                val z1 = state[base]
                val z2 = state[base + 1]
                val y = k[0] * x + z1
                state[base] = k[1] * x - k[3] * y + z2
                state[base + 1] = k[2] * x - k[4] * y
                x = y
            }
            base += 2
        }
        return x
    }

    /** Limitador suave: evita el "crujido" cuando las subidas de volumen pasan el máximo. */
    private fun softLimit(x: Double): Double {
        val a = abs(x)
        if (a <= 0.8) return x
        val limited = 0.8 + 0.2 * tanh((a - 0.8) / 0.2)
        return if (x < 0) -limited else limited
    }
}
