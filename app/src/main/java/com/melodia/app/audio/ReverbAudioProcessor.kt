package com.melodia.app.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Reverberación en tiempo real (algoritmo "Freeverb": 8 filtros peine en paralelo
 * y 4 pasa-todo en serie por canal). Si la mezcla es 0 deja pasar el audio intacto.
 */
@OptIn(UnstableApi::class)
class ReverbAudioProcessor : BaseAudioProcessor() {

    @Volatile private var mix = 0f
    @Volatile private var room = 0.6f
    @Volatile private var damp = 0.5f

    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var active = false

    private var combL = emptyArray<Comb>()
    private var combR = emptyArray<Comb>()
    private var apL = emptyArray<AllPass>()
    private var apR = emptyArray<AllPass>()

    fun setParams(mix: Float, roomSize: Float, damping: Float) {
        this.mix = mix.coerceIn(0f, 1f)
        this.room = roomSize.coerceIn(0f, 1f)
        this.damp = damping.coerceIn(0f, 1f)
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
        val k = inputAudioFormat.sampleRate / 44100.0
        fun len(n: Int) = (n * k).roundToInt().coerceAtLeast(1)
        combL = Array(COMBS.size) { Comb(len(COMBS[it])) }
        combR = Array(COMBS.size) { Comb(len(COMBS[it] + SPREAD)) }
        apL = Array(ALLPASS.size) { AllPass(len(ALLPASS[it])) }
        apR = Array(ALLPASS.size) { AllPass(len(ALLPASS[it] + SPREAD)) }
        active = false
        return inputAudioFormat
    }

    override fun onFlush() = clear()

    override fun onReset() {
        combL = emptyArray(); combR = emptyArray(); apL = emptyArray(); apR = emptyArray()
        channels = 0
    }

    private fun clear() {
        combL.forEach { it.clear() }; combR.forEach { it.clear() }
        apL.forEach { it.clear() }; apR.forEach { it.clear() }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val m = mix
        if (m < 0.005f || channels == 0 || combL.isEmpty()) {
            active = false
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (!active) {
            clear() // empieza sin restos de una cola anterior
            active = true
        }

        val feedback = room * 0.28f + 0.7f
        val d1 = damp * 0.4f
        val dry = 1f - 0.5f * m
        val wet = 1.4f * m
        val pcm16 = encoding == C.ENCODING_PCM_16BIT
        val bytes = if (pcm16) 2 else 4
        val frame = FloatArray(channels)

        while (inputBuffer.remaining() >= bytes * channels) {
            for (c in 0 until channels) {
                frame[c] = if (pcm16) inputBuffer.short / 32768f else inputBuffer.float
            }
            val l = frame[0]
            val r = if (channels > 1) frame[1] else l
            val input = (l + r) * GAIN

            var outL = 0f
            var outR = 0f
            for (i in combL.indices) {
                outL += combL[i].process(input, feedback, d1)
                outR += combR[i].process(input, feedback, d1)
            }
            for (i in apL.indices) {
                outL = apL[i].process(outL)
                outR = apR[i].process(outR)
            }
            frame[0] = limit(l * dry + outL * wet)
            if (channels > 1) frame[1] = limit(r * dry + outR * wet)

            for (c in 0 until channels) {
                if (pcm16) {
                    output.putShort((frame[c] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
                } else {
                    output.putFloat(frame[c])
                }
            }
        }
        output.flip()
    }

    private fun limit(x: Float): Float {
        val a = abs(x)
        if (a <= 0.85f) return x
        val y = 0.85f + 0.15f * tanh((a - 0.85f) / 0.15f)
        return if (x < 0) -y else y
    }

    private class Comb(size: Int) {
        private val buf = FloatArray(size)
        private var idx = 0
        private var store = 0f
        fun process(input: Float, feedback: Float, damp1: Float): Float {
            val out = buf[idx]
            store = out * (1f - damp1) + store * damp1
            buf[idx] = input + store * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }
        fun clear() { buf.fill(0f); store = 0f; idx = 0 }
    }

    private class AllPass(size: Int) {
        private val buf = FloatArray(size)
        private var idx = 0
        fun process(input: Float): Float {
            val b = buf[idx]
            buf[idx] = input + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return b - input
        }
        fun clear() { buf.fill(0f); idx = 0 }
    }

    private companion object {
        val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS = intArrayOf(556, 441, 341, 225)
        const val SPREAD = 23
        const val GAIN = 0.015f
    }
}
