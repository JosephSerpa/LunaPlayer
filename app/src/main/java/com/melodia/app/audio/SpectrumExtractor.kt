package com.melodia.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
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
    val ok: Boolean = true,
    /** Cuánto de la canción ya se analizó (0..1). Mientras sea menor que 1 la barra se va llenando. */
    val progress: Float = 1f,
    /** Falso si hubo tramos que no se pudieron leer y se rellenaron: se muestra, pero no se guarda. */
    val exact: Boolean = true
) {
    val size: Int get() = level.size
    val complete: Boolean get() = ok && progress >= 0.999f

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
    private const val DISK_VERSION = 1

    private val cache = LruCache<String, Spectrum>(24)

    /** Hilo propio y de baja prioridad: el análisis no compite con la interfaz ni se acumula. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "spectrum").apply { priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()

    /**
     * Identidad de la canción para guardar su espectro: en el servidor es "servidor + id"
     * (no cambia con el token ni la calidad); en el teléfono, su dirección.
     */
    fun keyOf(uri: Uri): String {
        val id = uri.getQueryParameter("id")
        return if ((uri.scheme == "http" || uri.scheme == "https") && id != null) "r:${uri.authority}:$id"
        else uri.toString()
    }

    private fun isRemote(uri: Uri) = uri.scheme == "http" || uri.scheme == "https"

    /** Resultado ya guardado en memoria (sin analizar), o null. */
    fun peek(uri: Uri): Spectrum? = cache.get(keyOf(uri))

    // ---- Copia en disco: cada canción se analiza una sola vez, aunque se cierre la app ----

    private fun fileName(uri: Uri, durationMs: Long) =
        java.security.MessageDigest.getInstance("MD5")
            .digest("${keyOf(uri)}|$durationMs".toByteArray()).joinToString("") { "%02x".format(it) } + ".bin"

    /**
     * Se guarda en el almacenamiento propio de la app (no en la caché): el sistema o "borrar caché"
     * no lo eliminan, así cada canción se analiza una sola vez.
     */
    private fun file(context: Context, uri: Uri, durationMs: Long): File {
        val dir = File(context.applicationContext.filesDir, "spectra").apply { mkdirs() }
        return File(dir, fileName(uri, durationMs))
    }

    /** Copias de versiones anteriores (estaban en la caché). */
    private fun legacyFile(context: Context, uri: Uri, durationMs: Long) =
        File(File(context.applicationContext.cacheDir, "spectra"), fileName(uri, durationMs))

    private fun readDisk(f: File): Spectrum? = runCatching {
        if (!f.exists()) return null
        java.io.DataInputStream(f.inputStream().buffered()).use { i ->
            if (i.readInt() != DISK_VERSION) return null
            val n = i.readInt()
            if (n != BUCKETS) return null
            fun arr() = FloatArray(n) { i.readFloat() }
            Spectrum(arr(), arr(), arr(), arr())
        }
    }.getOrNull()

    private fun writeDisk(f: File, s: Spectrum) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            java.io.DataOutputStream(tmp.outputStream().buffered()).use { o ->
                o.writeInt(DISK_VERSION); o.writeInt(s.size)
                for (a in arrayOf(s.level, s.low, s.mid, s.high)) for (v in a) o.writeFloat(v)
            }
            tmp.renameTo(f)
        }
    }

    /** Memoria o disco, sin analizar nada: instantáneo. Null si hay que analizar. */
    suspend fun load(context: Context, uri: Uri, durationMsHint: Long): Spectrum? {
        peek(uri)?.let { return it }
        val s = withContext(Dispatchers.IO) {
            val f = file(context, uri, durationMsHint)
            readDisk(f) ?: readDisk(legacyFile(context, uri, durationMsHint))?.also { writeDisk(f, it) }
        } ?: return null
        cache.put(keyOf(uri), s)
        return s
    }

    /**
     * Analiza la canción. Mientras avanza llama a [onProgress] con lo que lleva (la barra se va
     * llenando); el resultado final se guarda en memoria y en disco.
     */
    suspend fun get(
        context: Context, uri: Uri, durationMsHint: Long,
        onProgress: ((Spectrum) -> Unit)? = null
    ): Spectrum {
        load(context, uri, durationMsHint)?.let { return it }
        val result = withContext(worker) {
            runCatching { analyze(context.applicationContext, uri, durationMsHint, onProgress) }
                .getOrNull() ?: Spectrum.FAILED
        }
        if (result.complete && result.exact) {
            cache.put(keyOf(uri), result)
            withContext(Dispatchers.IO) { writeDisk(file(context, uri, durationMsHint), result) }
        }
        return result
    }

    /**
     * Canciones del servidor: una sola descarga. El análisis lee a través de la misma caché que usa el
     * reproductor, así que lo que ya bajó el reproductor se lee del disco y lo que baja el análisis queda
     * guardado para el reproductor. Ningún dato se descarga dos veces.
     *
     * Mientras la canción que suena vaya justa de colchón ([com.melodia.app.player.StreamGate]), el
     * análisis espera: el audio siempre tiene prioridad (también al saltar a la parte favorita).
     */
    private class CachedSource(context: Context, private val uri: Uri) : MediaDataSource() {
        private val ds = com.melodia.app.player.StreamCache.analysisSource(context)
        private val lock = Any()
        @Volatile private var closed = false
        private var pos = -1L
        private var length = -1L

        init {
            val n = ds.open(androidx.media3.datasource.DataSpec(uri))
            if (n != androidx.media3.common.C.LENGTH_UNSET.toLong()) length = n
            pos = 0L
        }

        /** El servidor permite pedir trozos sueltos (archivo original con tamaño conocido). */
        val seekable: Boolean get() = length > 0

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (length in 1..position) return -1
            while (!closed && !com.melodia.app.player.StreamGate.healthy) Thread.sleep(250)
            synchronized(lock) {
                if (closed) return -1
                try {
                    if (position != pos) {
                        runCatching { ds.close() }
                        ds.open(
                            androidx.media3.datasource.DataSpec.Builder()
                                .setUri(uri).setPosition(position).build()
                        )
                        pos = position
                    }
                    val n = ds.read(buffer, offset, size)
                    if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) return -1
                    pos += n
                    return n
                } catch (t: Throwable) {
                    pos = -1L
                    if (closed) return -1
                    throw IOException(t)
                }
            }
        }

        override fun getSize(): Long = length

        override fun close() {
            closed = true
            runCatching { ds.close() }
        }
    }

    /** Sumas por porción y filtros de graves/medios. */
    private class Bands(val n: Int, sampleRate: Int) {
        val sumX = DoubleArray(n)
        val sumLow = DoubleArray(n)
        val sumMid = DoubleArray(n)
        val sumHigh = DoubleArray(n)
        val count = LongArray(n)
        var sampleRate = sampleRate; private set
        private var aLow = SpectrumExtractor.coef(LOW_HZ, sampleRate)
        private var aMid = SpectrumExtractor.coef(MID_HZ, sampleRate)
        private var a1 = 0.0
        private var a2 = 0.0
        private var b1 = 0.0
        private var b2 = 0.0

        fun setRate(sr: Int) {
            sampleRate = sr
            aLow = SpectrumExtractor.coef(LOW_HZ, sr)
            aMid = SpectrumExtractor.coef(MID_HZ, sr)
        }

        fun resetFilters() { a1 = 0.0; a2 = 0.0; b1 = 0.0; b2 = 0.0 }

        fun add(x: Double, b: Int) {
            a1 += aLow * (x - a1)
            a2 += aLow * (a1 - a2)
            b1 += aMid * (x - b1)
            b2 += aMid * (b1 - b2)
            val lo = a2
            val mi = b2 - a2
            val hi = x - b2
            sumX[b] += x * x
            sumLow[b] += lo * lo
            sumMid[b] += mi * mi
            sumHigh[b] += hi * hi
            count[b]++
        }

        fun spectrum(progress: Float = 1f) = SpectrumExtractor.buildSpectrum(sumX, sumLow, sumMid, sumHigh, count, progress)
    }

    /** Formato del audio decodificado (cambia cuando el decodificador avisa). */
    private class Pcm(var channels: Int, var floatPcm: Boolean = false)

    /** Lee una muestra mono (promedio de canales) del búfer de salida. */
    private inline fun forEachFrame(
        out: java.nio.ByteBuffer, info: MediaCodec.BufferInfo, pcm: Pcm,
        block: (k: Int, x: Double) -> Boolean
    ) {
        out.position(info.offset)
        out.limit(info.offset + info.size)
        out.order(ByteOrder.nativeOrder())
        val ch = pcm.channels
        val frames = info.size / ((if (pcm.floatPcm) 4 else 2) * ch)
        val shorts = if (pcm.floatPcm) null else out.asShortBuffer()
        val floats = if (pcm.floatPcm) out.asFloatBuffer() else null
        for (k in 0 until frames) {
            var x = 0.0
            for (c in 0 until ch) {
                x += if (floats != null) floats.get().toDouble() else shorts!!.get() / 32768.0
            }
            if (!block(k, x / ch)) return
        }
    }

    private suspend fun analyze(
        context: Context, uri: Uri, durationMsHint: Long, onProgress: ((Spectrum) -> Unit)?
    ): Spectrum =
        withContext(worker) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            var source: CachedSource? = null
            // Si se cancela (otra canción), se corta la lectura: el análisis no se queda bloqueado.
            val watcher = launch(Dispatchers.IO) {
                try { awaitCancellation() } finally { runCatching { source?.close() } }
            }
            try {
                val remote = isRemote(uri)
                if (remote) {
                    source = CachedSource(context, uri)
                    extractor.setDataSource(source)
                } else {
                    extractor.setDataSource(context, uri, null)
                }
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
                val durationUs = if (durationMsHint > 0) durationMsHint * 1000L
                else if (trackFormat.containsKey(MediaFormat.KEY_DURATION))
                    trackFormat.getLong(MediaFormat.KEY_DURATION) else 0L
                if (durationUs <= 0L) return@withContext Spectrum.FAILED

                val bands = Bands(BUCKETS, intOr(trackFormat, MediaFormat.KEY_SAMPLE_RATE, 44100))
                val pcm = Pcm(intOr(trackFormat, MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1))

                val c = MediaCodec.createDecoderByType(mime)
                codec = c
                c.configure(trackFormat, null, null, 0)
                c.start()

                // Muestreo: un trozo corto en el centro de cada porción en vez de decodificar todo.
                // Mucho más rápido (sobre todo en DAP); si el formato no permite saltar bien, se
                // analiza la canción completa como antes.
                val canSample = !remote || source?.seekable == true
                val sampled = if (canSample) sampled(extractor, c, bands, pcm, durationUs, onProgress) else null
                val result = sampled ?: run {
                    runCatching { c.flush() }
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val fresh = Bands(BUCKETS, bands.sampleRate)
                    sequential(extractor, c, fresh, pcm, durationUs, remote, onProgress)
                }
                result
            } finally {
                watcher.cancel()
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor.release() }
                runCatching { source?.close() }
            }
        }

    /** Duración del trozo que se decodifica por porción. */
    private const val WINDOW_US = 350_000L

    /**
     * Decodifica solo un trozo de [WINDOW_US] por porción. Devuelve null si el archivo no permite
     * saltar con precisión (entonces se usa el análisis completo).
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.sampled(
        extractor: MediaExtractor, codec: MediaCodec, bands: Bands, pcm: Pcm, durationUs: Long,
        onProgress: ((Spectrum) -> Unit)?
    ): Spectrum? {
        val n = bands.n
        val bucketUs = durationUs / n
        val win = minOf(bucketUs, WINDOW_US)
        val info = MediaCodec.BufferInfo()
        val startNs = System.nanoTime()
        var lastEmit = 0L
        var badSeeks = 0
        for (i in 0 until n) {
            ensureActive()
            // Tope total generoso: si no alcanza, se entrega lo que haya sin guardarlo.
            if (System.nanoTime() - startNs > 90_000_000_000L) break
            val start = (i * bucketUs + (bucketUs - win) / 2).coerceAtLeast(0L)
            val end = start + win
            try {
                codec.flush()
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val landed = extractor.sampleTime
                if (landed < 0) break
                // El salto cayó lejos: este formato no salta bien. Dos fallos seguidos → análisis completo.
                if (i > 0 && kotlin.math.abs(landed - start) > 3_000_000L) {
                    if (++badSeeks >= 2) return null
                    continue
                }
                badSeeks = 0
                bands.resetFilters()
                var inputDone = false
                var segDone = false
                val segStart = System.nanoTime()
                while (!segDone) {
                    ensureActive()
                    if (System.nanoTime() - segStart > 3_000_000_000L) break // tramo atascado: se salta
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0 || extractor.sampleTime > end + 500_000L) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                            if (info.size > 0) {
                                val pts = info.presentationTimeUs
                                val sr = bands.sampleRate
                                forEachFrame(codec.getOutputBuffer(outIdx)!!, info, pcm) { k, x ->
                                    val t = pts + k * 1_000_000L / sr
                                    when {
                                        t < start -> true
                                        t >= end -> { segDone = true; false }
                                        else -> { bands.add(x, i); true }
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) segDone = true
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            bands.setRate(intOr(f, MediaFormat.KEY_SAMPLE_RATE, bands.sampleRate))
                            pcm.channels = intOr(f, MediaFormat.KEY_CHANNEL_COUNT, pcm.channels).coerceAtLeast(1)
                            pcm.floatPcm = intOr(
                                f, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT
                            ) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tramo ilegible: queda vacío y se rellena con el vecino al final.
            }
            if (onProgress != null) {
                val now = System.nanoTime()
                if (now - lastEmit > 120_000_000L) {
                    lastEmit = now
                    onProgress(bands.spectrum(((i + 1f) / n).coerceAtMost(0.999f)))
                }
            }
        }
        return finish(bands)
    }

    /** Resultado final: con muchos huecos se muestra pero no se guarda; casi vacío es fallo. */
    private fun finish(bands: Bands): Spectrum {
        val n = bands.n
        val covered = bands.count.count { it > 0L }
        val spec = bands.spectrum()
        return if (covered < n / 4) Spectrum.FAILED
        else if (covered < n * 0.92) Spectrum(spec.level, spec.low, spec.mid, spec.high, true, 1f, exact = false)
        else spec
    }

    /** Análisis completo, de principio a fin (formatos que no permiten saltar o servidor sin trozos). */
    private suspend fun kotlinx.coroutines.CoroutineScope.sequential(
        extractor: MediaExtractor, codec: MediaCodec, bands: Bands, pcm: Pcm, durationUs: Long,
        remote: Boolean, onProgress: ((Spectrum) -> Unit)?
    ): Spectrum {
        val n = bands.n
        var frame = 0L
        var totalFrames = durationUs / 1_000_000.0 * bands.sampleRate
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var idleLoops = 0
        var lastEmit = 0L
        val startNs = System.nanoTime()
        var lastAdvanceNs = startNs
        var lastPts = 0L
        var skips = 0
        var errors = 0

        // Si el decodificador se atasca o falla en un tramo dañado, salta ~1,5 s y sigue.
        fun skipAhead(): Boolean {
            if (++skips > 40) return false
            val target = lastPts + 1_500_000L
            return runCatching {
                codec.flush()
                extractor.seekTo(target, MediaExtractor.SEEK_TO_NEXT_SYNC)
                inputDone = false
                idleLoops = 0
                lastAdvanceNs = System.nanoTime()
                lastPts = target
                true
            }.getOrDefault(false)
        }

        while (!outputDone) {
            ensureActive()
            try {
                val nowNs = System.nanoTime()
                // En la nube el tope es amplio: la lectura puede esperar a que el audio tenga colchón.
                if (nowNs - startNs > (if (remote) 600L else 150L) * 1_000_000_000L) break
                if (!remote && nowNs - lastAdvanceNs > 6_000_000_000L) {
                    if (!skipAhead()) break
                }
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                        lastAdvanceNs = System.nanoTime()
                        if (info.size > 0) {
                            if (info.presentationTimeUs > 0L) {
                                frame = info.presentationTimeUs * bands.sampleRate / 1_000_000L
                                lastPts = info.presentationTimeUs
                            }
                            forEachFrame(codec.getOutputBuffer(outIdx)!!, info, pcm) { _, x ->
                                val b = ((frame / totalFrames) * n).toInt().coerceIn(0, n - 1)
                                bands.add(x, b)
                                frame++
                                true
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (onProgress != null) {
                            val now = System.nanoTime()
                            if (now - lastEmit > 120_000_000L) {
                                lastEmit = now
                                onProgress(bands.spectrum((frame / totalFrames).toFloat().coerceIn(0f, 0.999f)))
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        bands.setRate(intOr(f, MediaFormat.KEY_SAMPLE_RATE, bands.sampleRate))
                        pcm.channels = intOr(f, MediaFormat.KEY_CHANNEL_COUNT, pcm.channels).coerceAtLeast(1)
                        pcm.floatPcm = intOr(
                            f, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT
                        ) == AudioFormat.ENCODING_PCM_FLOAT
                        totalFrames = durationUs / 1_000_000.0 * bands.sampleRate
                    }
                    else -> {
                        if (inputDone && ++idleLoops > 400) outputDone = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (++errors > 12 || !skipAhead()) break
            }
        }
        return finish(bands)
    }

    private fun intOr(f: MediaFormat, key: String, default: Int): Int =
        if (f.containsKey(key)) f.getInteger(key) else default

    private fun coef(hz: Double, sampleRate: Int): Double =
        1.0 - exp(-2.0 * Math.PI * hz / sampleRate)

    private fun buildSpectrum(
        sumX: DoubleArray, sumLow: DoubleArray, sumMid: DoubleArray, sumHigh: DoubleArray,
        count: LongArray, progress: Float = 1f
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
        // Tramos sin datos (saltados o más allá del final): se rellenan con el vecino más cercano.
        if (progress >= 0.999f) {
            var prev = -1
            for (i in 0 until n) {
                if (count[i] > 0L) { prev = i; continue }
                if (prev >= 0) { rms[i] = rms[prev]; low[i] = low[prev]; mid[i] = mid[prev]; high[i] = high[prev] }
            }
            var next = -1
            for (i in n - 1 downTo 0) {
                if (count[i] > 0L) { next = i; continue }
                if (rms[i] == 0.0 && next >= 0) { rms[i] = rms[next]; low[i] = low[next]; mid[i] = mid[next]; high[i] = high[next] }
            }
        }
        // Normaliza con el percentil ~95 para que un golpe aislado no aplaste al resto.
        val filled = rms.filterIndexed { i, _ -> count[i] > 0L }.sorted()
        val ref = (if (filled.isEmpty()) 1e-6 else filled[((filled.size - 1) * 0.95).toInt()]).coerceAtLeast(1e-6)
        val level = FloatArray(n) { i ->
            ((rms[i] / ref).coerceIn(0.0, 1.0)).pow(0.75).toFloat()
        }
        return Spectrum(level, low, mid, high, ok = true, progress = progress)
    }
}
