package com.melodia.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Espectro real de una canción a lo largo del tiempo.
 *
 * Cada una de las [size] porciones guarda su volumen ([level], 0..1) y cómo se
 * reparte la energía entre graves ([low]), medios ([mid]) y agudos ([high]);
 * los tres suman 1. Si [ok] es falso no se pudo analizar el archivo.
 */
class Spectrum(
    val level: FloatArray,
    val low: FloatArray,
    val mid: FloatArray,
    val high: FloatArray,
    val ok: Boolean = true
) {
    val size: Int get() = level.size

    companion object {
        val FAILED = Spectrum(FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0), ok = false)
    }
}

/**
 * Decodifica la canción con MediaCodec y mide, por porciones de tiempo, cuánta
 * energía hay en graves (< 250 Hz), medios (250 Hz – 2,5 kHz) y agudos.
 * El resultado se guarda en memoria para no repetir el análisis.
 */
object SpectrumExtractor {

    const val BUCKETS = 96

    private const val LOW_HZ = 250.0
    private const val MID_HZ = 2500.0

    private val cache = LruCache<String, Spectrum>(24)

    /** Hilo propio y de baja prioridad: el análisis no compite con la interfaz ni se acumula. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "spectrum").apply { priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()

    /** Resultado ya guardado (sin analizar), o null. */
    fun peek(uri: Uri): Spectrum? = cache.get(uri.toString())

    suspend fun get(context: Context, uri: Uri, durationMsHint: Long): Spectrum {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        val result = withContext(worker) {
            runCatching { analyze(context.applicationContext, uri, durationMsHint) }
                .getOrNull() ?: Spectrum.FAILED
        }
        if (result.ok) cache.put(key, result)
        return result
    }

    private suspend fun analyze(context: Context, uri: Uri, durationMsHint: Long): Spectrum =
        withContext(worker) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                var track = -1
                var trackFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        track = i; trackFormat = f; break
                    }
                }
                if (track < 0 || trackFormat == null) return@withContext Spectrum.FAILED
                extractor.selectTrack(track)

                val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
                val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION))
                    trackFormat.getLong(MediaFormat.KEY_DURATION) else durationMsHint * 1000L
                if (durationUs <= 0L) return@withContext Spectrum.FAILED

                var sampleRate = intOr(trackFormat, MediaFormat.KEY_SAMPLE_RATE, 44100)
                var channels = intOr(trackFormat, MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
                var floatPcm = false

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(trackFormat, null, null, 0)
                codec.start()

                val n = BUCKETS
                val sumX = DoubleArray(n)
                val sumLow = DoubleArray(n)
                val sumMid = DoubleArray(n)
                val sumHigh = DoubleArray(n)
                val count = LongArray(n)

                // Filtros pasa-bajos de dos polos: a = graves, b = graves + medios.
                var aLow = coef(LOW_HZ, sampleRate)
                var aMid = coef(MID_HZ, sampleRate)
                var a1 = 0.0
                var a2 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                var frame = 0L
                var totalFrames = durationUs / 1_000_000.0 * sampleRate

                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var idleLoops = 0

                while (!outputDone) {
                    ensureActive()

                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                    when {
                        outIdx >= 0 -> {
                            idleLoops = 0
                            if (info.size > 0) {
                                val out = codec.getOutputBuffer(outIdx)!!
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                out.order(ByteOrder.nativeOrder())
                                val frames = info.size / ((if (floatPcm) 4 else 2) * channels)
                                val shorts = if (floatPcm) null else out.asShortBuffer()
                                val floats = if (floatPcm) out.asFloatBuffer() else null
                                for (k in 0 until frames) {
                                    var x = 0.0
                                    for (c in 0 until channels) {
                                        x += if (floats != null) floats.get().toDouble()
                                        else shorts!!.get() / 32768.0
                                    }
                                    x /= channels

                                    a1 += aLow * (x - a1)
                                    a2 += aLow * (a1 - a2)
                                    b1 += aMid * (x - b1)
                                    b2 += aMid * (b1 - b2)
                                    val lo = a2
                                    val mi = b2 - a2
                                    val hi = x - b2

                                    val b = ((frame / totalFrames) * n).toInt().coerceIn(0, n - 1)
                                    sumX[b] += x * x
                                    sumLow[b] += lo * lo
                                    sumMid[b] += mi * mi
                                    sumHigh[b] += hi * hi
                                    count[b]++
                                    frame++
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            sampleRate = intOr(f, MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = intOr(f, MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                            floatPcm = intOr(
                                f, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT
                            ) == AudioFormat.ENCODING_PCM_FLOAT
                            aLow = coef(LOW_HZ, sampleRate)
                            aMid = coef(MID_HZ, sampleRate)
                            totalFrames = durationUs / 1_000_000.0 * sampleRate
                        }
                        else -> {
                            // Sin salida disponible: si ya no hay entrada, evita quedarse esperando para siempre.
                            if (inputDone && ++idleLoops > 400) outputDone = true
                        }
                    }
                }

                buildSpectrum(sumX, sumLow, sumMid, sumHigh, count)
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor.release() }
            }
        }

    private fun intOr(f: MediaFormat, key: String, default: Int): Int =
        if (f.containsKey(key)) f.getInteger(key) else default

    private fun coef(hz: Double, sampleRate: Int): Double =
        1.0 - exp(-2.0 * Math.PI * hz / sampleRate)

    private fun buildSpectrum(
        sumX: DoubleArray, sumLow: DoubleArray, sumMid: DoubleArray, sumHigh: DoubleArray,
        count: LongArray
    ): Spectrum {
        val n = sumX.size
        val rms = DoubleArray(n)
        val low = FloatArray(n)
        val mid = FloatArray(n)
        val high = FloatArray(n)
        for (i in 0 until n) {
            if (count[i] == 0L) continue
            val c = count[i].toDouble()
            rms[i] = sqrt(sumX[i] / c)
            // Reparto por energía (potencia) entre las tres zonas.
            val l = sumLow[i] / c
            val m = sumMid[i] / c
            val h = sumHigh[i] / c
            val total = l + m + h
            if (total > 1e-12) {
                low[i] = (l / total).toFloat()
                mid[i] = (m / total).toFloat()
                high[i] = (h / total).toFloat()
            } else {
                low[i] = 1f
            }
        }
        // Normaliza con el percentil ~95 para que un golpe aislado no aplaste al resto.
        val sorted = rms.sortedArray()
        val ref = sorted[((n - 1) * 0.95).toInt()].coerceAtLeast(1e-6)
        val level = FloatArray(n) { i ->
            ((rms[i] / ref).coerceIn(0.0, 1.0)).pow(0.75).toFloat()
        }
        return Spectrum(level, low, mid, high)
    }
}
