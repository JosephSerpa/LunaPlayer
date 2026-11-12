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
        // Credenciales del servidor listas antes de la primera carátula o canción (widget, servicio…).
        serverSettings.config.value
        // Modo DAP listo antes de que el servicio arme la cadena de audio.
        dap.enabled.value
    }

    val repository: MusicRepository by lazy { MusicRepository(contentResolver) }
    val playerController: PlayerController by lazy { PlayerController(this) }

    /** Ecualizador compartido entre la interfaz y el servicio de reproducción. */
    val equalizer: EqualizerEngine by lazy { EqualizerEngine(this) }

    /** Listas, me gusta y estrellas iguales en todos los dispositivos (vía el servidor). */
    val sync: com.melodia.app.data.server.ServerSync by lazy {
        com.melodia.app.data.server.ServerSync(this, serverSettings, favorites, playlists)
    }

    /** Last.fm: scrobbles, me gusta → Loved y estadísticas. */
    val lastfm: com.melodia.app.data.lastfm.LastFm by lazy { com.melodia.app.data.lastfm.LastFm(this, favorites) }

    /** Modo DAP / bit-perfect: la app no toca el audio. */
    val dap: com.melodia.app.audio.DapMode by lazy { com.melodia.app.audio.DapMode(this) }

    /** Slow + reverb + tono. */
    val effects: EffectsEngine by lazy { EffectsEngine(this) }

    /** Servidor Subsonic / Navidrome. */
    val serverSettings: ServerSettings by lazy { ServerSettings(this) }

    /** Listas de reproducción. */
    val playlists: PlaylistStore by lazy { PlaylistStore(this) }

    /** Cuántas veces se escuchó cada canción (para "Quick picks" y lo más escuchado). */
    val stats: com.melodia.app.data.PlayStats by lazy { com.melodia.app.data.PlayStats(this) }

    /** Preferencias de pantalla (pantalla dividida en horizontal). */
    val ui: com.melodia.app.data.UiPrefs by lazy { com.melodia.app.data.UiPrefs(this) }

    /** Distribución de la pantalla de inicio. */
    val home: com.melodia.app.data.HomeLayoutStore by lazy { com.melodia.app.data.HomeLayoutStore(this) }

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
            // Las direcciones del servidor se guardan sin credenciales: se firman aquí, al descargar.
            .components { add(com.melodia.app.data.server.ServerAuthInterceptor()) }
            .crossfade(true)
            .build()
}
