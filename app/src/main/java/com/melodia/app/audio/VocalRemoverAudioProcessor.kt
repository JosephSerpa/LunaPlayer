package com.melodia.app.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Off vocal" en tiempo real. La voz principal casi siempre está grabada en el centro
 * (igual en ambos canales), así que se separa el audio en centro (L+R) y lados (L−R)
 * y se le resta al centro la zona donde vive la voz (≈150 Hz – 7 kHz).
 * Los graves del centro (bombo, bajo) y los lados (guitarras, coros abiertos) se conservan.
 * [amount]: 0 = sin cambio, 1 = máximo.
 */
@OptIn(UnstableApi::class)
class VocalRemoverAudioProcessor : BaseAudioProcessor() {

    @Volatile private var enabled = false
    @Volatile private var amount = 0.8f

    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var sampleRate = 0

    // Paso alto 150 Hz + paso bajo 7 kHz (Butterworth, 2.º orden), aplicados al centro.
    private var hp = DoubleArray(5)
    private var lp = DoubleArray(5)
    private val hpState = DoubleArray(2)
    private val lpState = DoubleArray(2)

    fun setParams(enabled: Boolean, amount: Float) {
        this.enabled = enabled
        this.amount = amount.coerceIn(0f, 1f)
    }

    @Throws(AudioProcessor.UnhandledAudioFormatException::class)
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate
        hp = biquad(sampleRate.toDouble(), 150.0, highPass = true)
        lp = biquad(sampleRate.toDouble(), 7000.0, highPass = false)
        clear()
        return inputAudioFormat
    }

    override fun onFlush() = clear()

    override fun onReset() {
        channels = 0
        sampleRate = 0
    }

    private fun clear() {
        hpState.fill(0.0); lpState.fill(0.0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val k = amount.toDouble()
        if (!enabled || k <= 0.001 || channels == 0 || sampleRate == 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val pcm16 = encoding == C.ENCODING_PCM_16BIT
        val bytes = if (pcm16) 2 else 4
        val frame = DoubleArray(channels)

        while (inputBuffer.remaining() >= bytes * channels) {
            for (c in 0 until channels) {
                frame[c] = if (pcm16) inputBuffer.short / 32768.0 else inputBuffer.float.toDouble()
            }
            if (channels >= 2) {
                val l = frame[0]
                val r = frame[1]
                val mid = (l + r) * 0.5
                val side = (l - r) * 0.5
                val voice = run(lp, lpState, run(hp, hpState, mid))
                val newMid = mid - k * voice
                frame[0] = newMid + side
                frame[1] = newMid - side
            } else {
                // En mono no hay "centro" que separar: solo se atenúa la zona de la voz.
                val voice = run(lp, lpState, run(hp, hpState, frame[0]))
                frame[0] = frame[0] - 0.7 * k * voice
            }
            for (c in 0 until channels) {
                val v = frame[c].coerceIn(-1.0, 1.0)
                if (pcm16) output.putShort((v * 32767.0).roundToInt().toShort())
                else output.putFloat(v.toFloat())
            }
        }
        output.flip()
    }

    /** Filtro biquad en forma transpuesta II. */
    private fun run(c: DoubleArray, z: DoubleArray, x: Double): Double {
        val y = c[0] * x + z[0]
        z[0] = c[1] * x - c[3] * y + z[1]
        z[1] = c[2] * x - c[4] * y
        return y
    }

    /** Coeficientes [b0, b1, b2, a1, a2] de un paso alto / paso bajo Butterworth (RBJ). */
    private fun biquad(fs: Double, freq: Double, highPass: Boolean): DoubleArray {
        val f = freq.coerceAtMost(fs * 0.45)
        val w0 = 2.0 * PI * f / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * 0.7071)
        val a0 = 1.0 + alpha
        val b0: Double
        val b1: Double
        if (highPass) {
            b0 = (1.0 + cw) / 2.0
            b1 = -(1.0 + cw)
        } else {
            b0 = (1.0 - cw) / 2.0
            b1 = 1.0 - cw
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b0 / a0, (-2.0 * cw) / a0, (1.0 - alpha) / a0)
    }
}
