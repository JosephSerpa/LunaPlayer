package com.melodia.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.melodia.app.audio.EffectsEngine
import com.melodia.app.audio.EqualizerEngine
import com.melodia.app.data.FavoritesStore
import com.melodia.app.data.MusicRepository
import com.melodia.app.data.PlaylistStore
import com.melodia.app.data.server.ServerSettings
import com.melodia.app.player.PlayerController

class MelodiaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Str.init(LocaleManager.wrap(this))
    }

    val repository: MusicRepository by lazy { MusicRepository(contentResolver) }
    val playerController: PlayerController by lazy { PlayerController(this) }

    /** Ecualizador compartido entre la interfaz y el servicio de reproducción. */
    val equalizer: EqualizerEngine by lazy { EqualizerEngine(this) }

    /** Slow + reverb + tono. */
    val effects: EffectsEngine by lazy { EffectsEngine(this) }

    /** Servidor Subsonic / Navidrome. */
    val serverSettings: ServerSettings by lazy { ServerSettings(this) }

    /** Listas de reproducción. */
    val playlists: PlaylistStore by lazy { PlaylistStore(this) }

    /** Me gusta y estrellas. */
    val favorites: FavoritesStore by lazy { FavoritesStore(this) }


    /**
     * Cargador de imágenes compartido: guarda las carátulas en memoria y en disco
     * (también las del servidor), así al volver a abrir la app aparecen al instante.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            // Navidrome suele pedir no guardar en caché: aquí sí conviene guardar.
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
