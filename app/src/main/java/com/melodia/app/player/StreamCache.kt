package com.melodia.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.melodia.app.data.server.ServerAuth
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.Executors

/**
 * Caché de disco para la música del servidor.
 *
 *  - Todo lo que se reproduce en streaming se guarda (hasta [MAX_BYTES], borrando lo más viejo):
 *    volver a oír una canción, o saltar hacia atrás, es instantáneo y no gasta datos.
 *  - Las canciones que vienen después en la cola se precargan (solo el comienzo), así al pasar
 *    de una a otra no hay espera.
 */
@OptIn(UnstableApi::class)
object StreamCache {

    private const val MAX_BYTES = 1024L * 1024 * 1024

    /** Comienzo de la siguiente canción que se descarga antes de que haga falta. */
    private const val PREFETCH_NEXT = 6L * 1024 * 1024
    private const val PREFETCH_AFTER = 2L * 1024 * 1024

    private var cache: SimpleCache? = null

    @Synchronized
    fun cache(context: Context): SimpleCache =
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "stream"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }

    /**
     * Clave estable de una canción del servidor: no depende del token ni de otros parámetros,
     * solo de qué canción es y en qué calidad se pide.
     */
    fun keyFor(uri: Uri): String {
        val id = uri.getQueryParameter("id")
        if (id != null && uri.path?.endsWith("/rest/stream") == true) {
            return "nv:${uri.authority}:$id:${uri.getQueryParameter("maxBitRate").orEmpty()}:${uri.getQueryParameter("format").orEmpty()}"
        }
        return uri.toString()
    }

    /**
     * Descargas por internet. Las direcciones del servidor llegan sin credenciales y se firman aquí
     * ([ServerAuth]). 8 s sin recibir datos ya cuenta como conexión caída: el reproductor reintenta antes.
     */
    private fun http(): DataSource.Factory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory()
            .setUserAgent("LunaPlayer")
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)
    ) { spec -> spec.withUri(ServerAuth.sign(spec.uri)) }

    /** Para las carátulas de la notificación y la pantalla de bloqueo (también firmadas). */
    fun artFactory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(context.applicationContext, http())

    private fun cached(context: Context, flags: Int = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(http())
            .setCacheKeyFactory { spec -> keyFor(spec.uri) }
            .setFlags(flags)

    /** Para el reproductor: lo de internet pasa por la caché; archivos del teléfono, directo. */
    fun playerFactory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(context.applicationContext, cached(context))

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "prefetch").apply { priority = Thread.MIN_PRIORITY }
    }

    @Volatile
    private var writer: CacheWriter? = null
    @Volatile
    private var generation = 0

    /** Precarga el comienzo de las próximas canciones del servidor (la más cercana primero). */
    fun prefetch(context: Context, upcoming: List<Uri>) {
        val remote = upcoming.filter { it.scheme == "http" || it.scheme == "https" }.take(2)
        val gen = ++generation
        writer?.cancel()
        if (remote.isEmpty()) return
        val app = context.applicationContext
        worker.execute {
            remote.forEachIndexed { i, uri ->
                if (gen != generation) return@execute
                val bytes = if (i == 0) PREFETCH_NEXT else PREFETCH_AFTER
                runCatching {
                    val source = cached(app, CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                        .createDataSourceForDownloading()
                    val spec = DataSpec.Builder().setUri(uri).setPosition(0).setLength(bytes)
                        .setKey(keyFor(uri)).build()
                    val w = CacheWriter(source, spec, null, null)
                    writer = w
                    if (gen == generation) w.cache()
                }
            }
        }
    }
}
