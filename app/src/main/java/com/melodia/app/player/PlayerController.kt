package com.melodia.app.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.melodia.app.MelodiaApp
import com.melodia.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Formato del audio que suena: p. ej. "MP3", 320 kbps, 44,1 kHz. */
data class AudioInfo(
    val format: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0
) {
    /** "MP3 · 320 kbps · 44.1 kHz" (omite lo que no se conoce). */
    val label: String
        get() = listOfNotNull(
            format.takeIf { it.isNotBlank() },
            bitrateKbps.takeIf { it > 0 }?.let { "$it kbps" },
            sampleRateHz.takeIf { it > 0 }?.let {
                val khz = it / 1000.0
                (if (it % 1000 == 0) "%.0f".format(khz) else "%.1f".format(khz)) + " kHz"
            }
        ).joinToString(" · ")
}

data class NowPlaying(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUri: Uri? = null,
    val durationMs: Long = 0L,
    /** Archivo de audio (para analizar el espectro). */
    val uri: Uri? = null,
    val audio: AudioInfo = AudioInfo()
)

data class PlayerState(
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

/** Puente entre la UI y el [PlaybackService] mediante un MediaController. */
@OptIn(UnstableApi::class)
class PlayerController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private val favorites get() = (appContext as MelodiaApp).favorites
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /**
     * Posición de la canción, aparte del resto del estado: cambia 4 veces por segundo
     * y así solo se redibuja lo que la muestra (no toda la app).
     */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    /** Última cola reproducida: se guarda para retomarla al abrir la app. */
    private val session = appContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    private var savedIndex = -1
    private var savedShuffle: Boolean? = null

    /** Acciones pedidas antes de que el controlador termine de conectarse. */
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Modo luna: al pasar a otra canción, empieza en su mejor parte (si la tiene).
            // Al cargar una cola nueva (PLAYLIST_CHANGED) ya se pide en play(); en repetir no aplica.
            if (mediaItem == null ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) return
            val c = controller ?: return
            val start = mediaItem.mediaId.toLongOrNull()?.let { favorites.moonStartMs(it) } ?: return
            val duration = c.duration
            if (duration > 0 && start >= duration - 1000) return
            c.seekTo(start)
            _position.value = start
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startProgressLoop() else {
                progressJob?.cancel()
                controller?.let { savePosition(it.currentPosition) }
            }
        }
    }

    init {
        // Velocidad y tono (slow) se aplican al reproductor cada vez que cambian.
        val effects = (appContext as MelodiaApp).effects
        scope.launch {
            effects.settings
                .map { it.speed to it.pitchFactor }
                .distinctUntilChanged()
                .collect { (speed, pitch) ->
                    withController { it.playbackParameters = PlaybackParameters(speed, pitch) }
                }
        }
        val token = SessionToken(
            appContext, ComponentName(appContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync(c)
            if (c.isPlaying) startProgressLoop()
            pending.forEach { it(c) }
            pending.clear()
        }, MoreExecutors.directExecutor())
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val meta = item?.mediaMetadata
        val current = if (item == null) null else {
            val duration = p.duration.takeIf { it > 0 } ?: meta?.durationMs ?: 0L
            NowPlaying(
                mediaId = item.mediaId,
                title = meta?.title?.toString().orEmpty(),
                artist = meta?.artist?.toString().orEmpty(),
                album = meta?.albumTitle?.toString().orEmpty(),
                artUri = meta?.artworkUri,
                durationMs = duration,
                uri = meta?.extras?.getString(EXTRA_URI)?.let { Uri.parse(it) }
                    ?: item.mediaId.toLongOrNull()?.takeIf { it > 0 }?.let {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
                    },
                audio = audioInfo(p, meta?.extras, duration)
            )
        }
        _position.value = p.currentPosition.coerceAtLeast(0)
        if (item != null && (p.currentMediaItemIndex != savedIndex || p.shuffleModeEnabled != savedShuffle)) {
            savedIndex = p.currentMediaItemIndex
            savedShuffle = p.shuffleModeEnabled
            session.edit()
                .putInt(KEY_INDEX, savedIndex)
                .putBoolean(KEY_SHUFFLE, p.shuffleModeEnabled)
                .apply()
        }
        _state.update {
            it.copy(
                current = current,
                // Mientras carga (streaming) y debe sonar, se sigue mostrando "reproduciendo":
                // si no, el botón y la portada hacen un salto de pausa en cada cambio de canción.
                isPlaying = p.isPlaying ||
                        (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING),
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
                hasNext = p.hasNextMediaItem(),
                hasPrevious = p.hasPreviousMediaItem()
            )
        }
    }

    /** Formato, tasa de bits y frecuencia de muestreo de la pista que suena. */
    private fun audioInfo(p: Player, extras: Bundle?, durationMs: Long): AudioInfo {
        val path = extras?.getString(EXTRA_PATH).orEmpty()
        val size = extras?.getLong(EXTRA_SIZE) ?: 0L

        val selected = p.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        val fmt = selected?.let { g ->
            (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
        }

        val byExt = when (path.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "MP3"
            "m4a", "aac", "mp4" -> "AAC"
            "flac" -> "FLAC"
            "wav" -> "WAV"
            "ogg", "oga" -> "OGG"
            "opus" -> "OPUS"
            "wma" -> "WMA"
            "amr" -> "AMR"
            else -> ""
        }
        val byMime = when (fmt?.sampleMimeType) {
            MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_VORBIS -> "OGG"
            MimeTypes.AUDIO_OPUS -> "OPUS"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_RAW -> "WAV"
            MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3 -> "AC3"
            else -> ""
        }

        var kbps = fmt?.let { if (it.averageBitrate > 0) it.averageBitrate else it.bitrate }
            ?.takeIf { it > 0 }?.let { (it + 500) / 1000 } ?: 0
        // Sin dato del decodificador: se estima con tamaño del archivo / duración.
        if (kbps == 0 && size > 0 && durationMs > 0) {
            kbps = (size * 8.0 / durationMs).toInt()   // bits por ms = kbps
        }
        val rate = fmt?.sampleRate?.takeIf { it > 0 } ?: 0
        return AudioInfo(byExt.ifBlank { byMime }, kbps, rate)
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var ticks = 0
            while (isActive) {
                controller?.let { c ->
                    val pos = c.currentPosition.coerceAtLeast(0)
                    _position.value = pos
                    if (++ticks % 20 == 0) savePosition(pos) // cada ~5 s
                }
                delay(250)
            }
        }
    }

    private fun savePosition(pos: Long) {
        session.edit().putLong(KEY_POSITION, pos.coerceAtLeast(0)).apply()
    }

    /**
     * Dirección que se reproduce. En streaming se pide al servidor que estime el tamaño del archivo
     * (estimateContentLength): sin eso, al convertir a MP3 al vuelo el reproductor no sabe cuánto dura
     * ni puede saltar a otro punto hasta que la canción se vuelve a abrir.
     */
    private fun Song.playbackUri(): Uri =
        if (isRemote && uri.getQueryParameter("estimateContentLength") == null)
            uri.buildUpon().appendQueryParameter("estimateContentLength", "true").build()
        else uri

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(playbackUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artUri)
                    .setDurationMs(durationMs)
                    .setExtras(Bundle().apply {
                        putString(EXTRA_PATH, path)
                        putLong(EXTRA_SIZE, sizeBytes)
                        putString(EXTRA_URI, uri.toString())
                    })
                    .build()
            )
            .build()

    // ---- Acciones públicas -------------------------------------------------

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        // Con bibliotecas enormes no se manda toda la lista al reproductor (4 000+ canciones
        // tardan y hacen que la app se trabe al elegir una): se usa una ventana alrededor de la elegida.
        val (queue, index) = limitQueue(songs, startIndex.coerceIn(0, songs.lastIndex), shuffle)
        // Modo luna: la canción elegida también empieza en su mejor parte.
        val startMs = favorites.moonStartMs(queue[index].id) ?: 0L
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(queue.map { it.toMediaItem() }, index, startMs)
            c.prepare()
            c.play()
        }
        session.edit()
            .putString(KEY_QUEUE, queue.joinToString(",") { it.id.toString() })
            .putInt(KEY_INDEX, index)
            .putLong(KEY_POSITION, startMs)
            .putBoolean(KEY_SHUFFLE, shuffle)
            .apply()
    }

    private fun limitQueue(songs: List<Song>, startIndex: Int, shuffle: Boolean): Pair<List<Song>, Int> {
        if (songs.size <= MAX_QUEUE) return songs to startIndex
        if (shuffle) {
            // Al azar: la elegida primero y el resto de una muestra aleatoria.
            val others = songs.filterIndexed { i, _ -> i != startIndex }.shuffled().take(MAX_QUEUE - 1)
            return (listOf(songs[startIndex]) + others) to 0
        }
        val from = (startIndex - MAX_QUEUE / 4).coerceAtLeast(0)
        val to = (from + MAX_QUEUE).coerceAtMost(songs.size)
        return songs.subList(from, to) to (startIndex - from)
    }

    /**
     * Al abrir la app: vuelve a cargar la última cola en pausa, en la misma canción y segundo.
     * No hace nada si ya hay algo sonando (el servicio siguió vivo).
     */
    fun restoreLastSession(library: List<Song>) {
        val ids = session.getString(KEY_QUEUE, null)?.split(',')?.mapNotNull { it.toLongOrNull() }
        if (ids.isNullOrEmpty() || library.isEmpty()) return
        val byId = library.associateBy { it.id }
        val savedIds = ids
        val queue = savedIds.mapNotNull { byId[it] }
        if (queue.isEmpty()) return
        // Si alguna canción ya no existe, se busca la que estaba sonando dentro de la nueva cola.
        val wantedId = savedIds.getOrNull(session.getInt(KEY_INDEX, 0))
        val index = queue.indexOfFirst { it.id == wantedId }.takeIf { it >= 0 } ?: 0
        val position = if (index >= 0 && queue[index].id == wantedId) session.getLong(KEY_POSITION, 0L) else 0L
        val shuffle = session.getBoolean(KEY_SHUFFLE, false)
        withController { c ->
            if (c.mediaItemCount > 0) return@withController
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(queue.map { it.toMediaItem() }, index, position)
            c.prepare()
            _position.value = position
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { c ->
        // Como en cualquier reproductor: tras 3 s reinicia la canción actual.
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0)
        else c.seekToPrevious()
    }

    /** Siempre a la canción anterior (o al inicio si es la primera). */
    fun previousTrack() = withController { c ->
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
    }

    fun seekTo(positionMs: Long) = withController { c ->
        c.seekTo(positionMs)
        _position.value = positionMs
        savePosition(positionMs)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private companion object {
        const val EXTRA_PATH = "melodia.path"
        const val EXTRA_SIZE = "melodia.size"
        const val EXTRA_URI = "melodia.uri"
        /** Máximo de canciones que se cargan en la cola a la vez. */
        const val MAX_QUEUE = 400
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
    }
}
