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
     * Para canciones del servidor no se baja el archivo original (un FLAC pesa 25 MB o más):
     * se pide una copia ligera (MP3 de 64 kbps, unas 25 veces menos datos) que basta para medir
     * graves, medios y agudos. Se descarga y se analiza a la vez.
     */
    private fun lightUrl(uri: Uri): String {
        val b = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name == "maxBitRate" || name == "format" || name == "estimateContentLength") continue
            b.appendQueryParameter(name, uri.getQueryParameter(name))
        }
        return com.melodia.app.data.server.ServerAuth.sign(
            b.appendQueryParameter("maxBitRate", "64").appendQueryParameter("format", "mp3").build()
        ).toString()
    }

    /** Descarga en segundo plano a un archivo temporal y deja leerlo mientras todavía se baja. */
    private class RemoteSource(url: String, dir: File) : MediaDataSource() {
        private val file = File.createTempFile("spec", ".mp3", dir)
        private val raf = RandomAccessFile(file, "r")
        private val lock = Object()
        @Volatile private var written = 0L
        @Volatile private var done = false
        @Volatile private var closed = false
        @Volatile private var error: Throwable? = null
        @Volatile private var conn: HttpURLConnection? = null
        private val thread = Thread({
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                conn = c
                c.connectTimeout = 8_000
                c.readTimeout = 15_000
                val code = c.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (!closed) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            synchronized(lock) { lock.notifyAll() }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (!closed) error = t
            } finally {
                done = true
                synchronized(lock) { lock.notifyAll() }
            }
        }, "spectrum-dl").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            synchronized(lock) {
                while (written <= position && !done && !closed) lock.wait(250)
                if (closed) return -1
                if (written <= position) {
                    error?.let { throw IOException(it) }
                    return -1
                }
                raf.seek(position)
                val n = raf.read(buffer, offset, minOf(size.toLong(), written - position).toInt())
                return if (n <= 0) -1 else n
            }
        }

        override fun getSize(): Long = -1L

        override fun close() {
            closed = true
            runCatching { conn?.disconnect() }
            synchronized(lock) { lock.notifyAll() }
            runCatching { thread.interrupt() }
            runCatching { raf.close() }
            runCatching { file.delete() }
        }
    }

    private suspend fun analyze(
        context: Context, uri: Uri, durationMsHint: Long, onProgress: ((Spectrum) -> Unit)?
    ): Spectrum =
        withContext(worker) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            var source: RemoteSource? = null
            // Si se cancela (otra canción), se corta la descarga: el análisis no se queda bloqueado.
            val watcher = launch(Dispatchers.IO) {
                try { awaitCancellation() } finally { runCatching { source?.close() } }
            }
            try {
                if (isRemote(uri)) {
                    val dir = File(context.cacheDir, "spectra").apply { mkdirs() }
                    source = RemoteSource(lightUrl(uri), dir)
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
                // La duración que ya conocemos de la canción manda (la del MP3 ligero es aproximada).
                val durationUs = if (durationMsHint > 0) durationMsHint * 1000L
                else if (trackFormat.containsKey(MediaFormat.KEY_DURATION))
                    trackFormat.getLong(MediaFormat.KEY_DURATION) else 0L
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
                var lastEmit = 0L
                val remote = isRemote(uri)
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
                        codec?.flush()
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
                    // Tope total: si tarda demasiado se entrega lo que haya (y no se guarda como completo).
                    if (nowNs - startNs > (if (remote) 240L else 150L) * 1_000_000_000L) break
                    if (!remote && nowNs - lastAdvanceNs > 6_000_000_000L) {
                        if (!skipAhead()) break
                    }

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
                            lastAdvanceNs = System.nanoTime()
                            if (info.size > 0) {
                                // La posición real del audio manda: así un tramo saltado no descuadra la barra.
                                if (info.presentationTimeUs > 0L) {
                                    frame = info.presentationTimeUs * sampleRate / 1_000_000L
                                    lastPts = info.presentationTimeUs
                                }
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
                            // Avance visible: la barra se va llenando a medida que se analiza.
                            if (onProgress != null) {
                                val now = System.nanoTime()
                                if (now - lastEmit > 120_000_000L) {
                                    lastEmit = now
                                    onProgress(
                                        buildSpectrum(
                                            sumX, sumLow, sumMid, sumHigh, count,
                                            (frame / totalFrames).toFloat().coerceIn(0f, 0.999f)
                                        )
                                    )
                                }
                            }
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (++errors > 12 || !skipAhead()) break
                    }
                }

                val covered = count.count { it > 0L }
                val spec = buildSpectrum(sumX, sumLow, sumMid, sumHigh, count)
                // Casi nada analizado: fallo. Con huecos: se rellenan y se muestra, sin guardarlo.
                if (covered < n / 4) Spectrum.FAILED
                else if (covered < n * 0.92) Spectrum(spec.level, spec.low, spec.mid, spec.high, true, 1f, exact = false)
                else spec
            } finally {
                watcher.cancel()
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor.release() }
                runCatching { source?.close() }
            }
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
