package com.melodia.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.melodia.app.MainActivity
import com.melodia.app.MelodiaApp

/**
 * Servicio en primer plano: mantiene la música sonando con la app cerrada
 * y Media3 se encarga de la notificación, la pantalla de bloqueo y Bluetooth.
 * El audio pasa por el ecualizador de 7 bandas y la reverberación antes de llegar al altavoz;
 * velocidad y tono los maneja el propio ExoPlayer (Sonic).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MelodiaApp
        val equalizer = app.equalizer
        val effects = app.effects

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(
                        // Orden: quitar voz → ecualizador → reverberación.
                        arrayOf<AudioProcessor>(effects.vocal, equalizer.processor, effects.reverb)
                    )
                    .build()
        }

        // Permite saltar dentro de MP3/AAC en streaming aunque no traigan tabla de búsqueda
        // (por ejemplo, el MP3 que el servidor genera al vuelo con una calidad limitada).
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)

        // Empieza a sonar con muy poco cargado (0,5 s) y sigue llenando por detrás; lo que baja del
        // servidor se guarda en disco (StreamCache) para repetirlo al instante.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(StreamCache.playerFactory(this), extractors))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_NETWORK) // también mantiene el Wi-Fi para el streaming
            .build()
        // Al tocar la notificación (o la pantalla de bloqueo) se abre la app en el reproductor.
        val openPlayer = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_PLAYER
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Wi-Fi activo solo con música de la nube; y si la conexión falla, se reintenta sola.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateWakeMode(player)
            override fun onPlayerError(error: PlaybackException) = recover(player, error)
        })
        // Widget de la pantalla principal: se actualiza al cambiar la canción o al pausar.
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED
                    )
                ) com.melodia.app.widget.PlayerWidget.push(this@PlaybackService, p)
            }
        })
        // Carátulas de la notificación: las direcciones del servidor se firman al descargarlas.
        val bitmaps = androidx.media3.session.CacheBitmapLoader(
            androidx.media3.datasource.DataSourceBitmapLoader(
                com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
                    java.util.concurrent.Executors.newSingleThreadExecutor()
                ),
                StreamCache.artFactory(this)
            )
        )
        updateWakeMode(player)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayer)
            .setBitmapLoader(bitmaps)
            .build()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retriedUid: String? = null
    private var retriedAt = 0L

    private fun isRemote(item: MediaItem?): Boolean {
        val scheme = item?.localConfiguration?.uri?.scheme
        return scheme == "http" || scheme == "https"
    }

    /** El bloqueo de Wi-Fi solo hace falta con música de la nube (la del teléfono no usa la red). */
    private fun updateWakeMode(p: ExoPlayer) {
        p.setWakeMode(if (isRemote(p.currentMediaItem)) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
    }

    /**
     * Fallo de red con una canción de la nube: antes el reproductor se quedaba parado hasta cambiar de
     * canción. Ahora, cerca del final (el servidor anunció un poco más de lo que había) pasa a la siguiente;
     * si no, reintenta una vez desde el mismo punto.
     */
    private fun recover(p: ExoPlayer, error: PlaybackException) {
        val item = p.currentMediaItem ?: return
        if (!isRemote(item) || error.errorCode !in 2000..2999) return
        val uid = item.mediaMetadata.extras?.getString("melodia.uid") ?: item.mediaId
        val pos = p.currentPosition
        val dur = item.mediaMetadata.durationMs?.takeIf { it > 0 } ?: p.duration
        val now = android.os.SystemClock.elapsedRealtime()
        if (dur > 0 && pos >= dur - 5_000) {
            if (p.repeatMode == Player.REPEAT_MODE_ONE) {
                p.seekTo(0)
                p.prepare()
            } else if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
                p.prepare()
            }
            return
        }
        if (uid == retriedUid && now - retriedAt < 30_000) return // ya se reintentó: no entra en bucle
        retriedUid = uid
        retriedAt = now
        handler.postDelayed({
            if (p.playbackState == Player.STATE_IDLE && p.currentMediaItem?.mediaId == item.mediaId) {
                p.seekTo(pos)
                p.prepare()
            }
        }, 1_500)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        com.melodia.app.widget.PlayerWidget.stopped(this)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
