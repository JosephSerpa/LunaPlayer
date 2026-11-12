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
    val sampleRateHz: Int = 0,
    /** Lo que entrega el decodificador (para la ficha técnica). */
    val channels: Int = 0,
    val mime: String = "",
    val codecs: String = "",
    /** Bits por muestra de lo decodificado (solo si el formato lo indica, p. ej. WAV). */
    val pcmBits: Int = 0,
    val path: String = "",
    val sizeBytes: Long = 0L
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

/** Una canción de la cola de reproducción. [uid] es único aunque la misma canción esté dos veces. */
data class QueueItem(
    val uid: String,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artUri: Uri?
)

data class PlayerState(
    val current: NowPlaying? = null,
    /** Posición de la canción actual dentro de la cola. */
    val queueIndex: Int = -1,
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

    /**
     * Aleatorio propio: la cola se mezcla aquí y el orden que se ve es el que suena. (El aleatorio del
     * reproductor mete las canciones nuevas en posiciones al azar: "a continuación" fallaba con él activo.)
     */
    private val shuffleOn = MutableStateFlow(false)
    /** Orden original (ids) de la cola antes de mezclarla, para poder desactivar el aleatorio. */
    private var originalIds: List<String>? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /**
     * Posición de la canción, aparte del resto del estado: cambia 4 veces por segundo
     * y así solo se redibuja lo que la muestra (no toda la app).
     */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    /** Hasta dónde está descargada la canción (ms): para la barra de carga de la música en la nube. */
    private val _buffered = MutableStateFlow(0L)
    val buffered: StateFlow<Long> = _buffered.asStateFlow()

    /** Cola de reproducción actual (en el orden de la lista). */
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    /** Última cola reproducida: se guarda para retomarla al abrir la app. */
    private val session = appContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    private var savedIndex = -1
    private var savedShuffle: Boolean? = null

    init {
        shuffleOn.value = session.getBoolean(KEY_SHUFFLE, false)
        originalIds = session.getString(KEY_ORIGINAL, null)?.split(',')?.filter { it.isNotBlank() }
    }

    /** Acciones pedidas antes de que el controlador termine de conectarse. */
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                updateQueue(player)
                prefetchUpcoming()
            }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            prefetchUpcoming()
            // Modo luna: al pasar a otra canción, empieza en su mejor parte (si la tiene).
            // Al cargar una cola nueva (PLAYLIST_CHANGED) ya se pide en play(); en repetir no aplica.
            if (mediaItem == null ||
                mediaItem.mediaMetadata.extras?.getBoolean(EXTRA_CLIP) == true ||
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
        val dap = (appContext as MelodiaApp).dap
        scope.launch {
            // En modo DAP la velocidad y el tono quedan en 1× / 0 st: nada pasa por el procesador de tempo.
            kotlinx.coroutines.flow.combine(effects.settings, dap.enabled) { fx, on ->
                if (on) 1f to 1f else fx.effSpeed to fx.pitchFactor
            }
                .distinctUntilChanged()
                .collect { (speed, pitch) ->
                    withController { it.playbackParameters = PlaybackParameters(speed, pitch) }
                }
        }
        scope.launch {
            // Al encender o apagar el modo DAP se vuelve a armar la ruta de audio (misma canción y posición).
            var first = true
            dap.enabled.collect {
                if (first) { first = false; return@collect }
                reapplyAudioPath()
            }
        }
        val token = SessionToken(
            appContext, ComponentName(appContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            // El aleatorio del reproductor ya no se usa (ver [shuffleOn]).
            if (c.shuffleModeEnabled) { c.shuffleModeEnabled = false; shuffleOn.value = true }
            c.addListener(listener)
            sync(c)
            updateQueue(c)
            if (c.isPlaying) startProgressLoop()
            pending.forEach { it(c) }
            pending.clear()
        }, MoreExecutors.directExecutor())
    }

    /**
     * Vuelve a armar la ruta de audio (por ejemplo al cambiar el modo DAP): recarga la cola en la misma
     * canción y posición, sin cambiar si estaba sonando o en pausa. Puede notarse un corte breve.
     */
    private fun reapplyAudioPath() = withController { c ->
        runCatching {
            val n = c.mediaItemCount
            if (n == 0) return@runCatching
            val index = c.currentMediaItemIndex
            val position = c.currentPosition.coerceAtLeast(0L)
            val items = List(n) { c.getMediaItemAt(it) }
            val play = c.playWhenReady
            c.setMediaItems(items, index, position)
            c.prepare()
            c.playWhenReady = play
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val meta = item?.mediaMetadata
        val current = if (item == null) null else {
            val duration = durationOf(p)
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
        _buffered.value = p.bufferedPosition.coerceAtLeast(0)
        if (item != null && (p.currentMediaItemIndex != savedIndex || shuffleOn.value != savedShuffle)) {
            savedIndex = p.currentMediaItemIndex
            savedShuffle = shuffleOn.value
            session.edit()
                .putInt(KEY_INDEX, savedIndex)
                .putBoolean(KEY_SHUFFLE, shuffleOn.value)
                .apply()
        }
        _state.update {
            it.copy(
                current = current,
                queueIndex = if (item == null) -1 else p.currentMediaItemIndex,
                // Mientras carga (streaming) y debe sonar, se sigue mostrando "reproduciendo":
                // si no, el botón y la portada hacen un salto de pausa en cada cambio de canción.
                isPlaying = p.isPlaying ||
                        (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING),
                shuffle = shuffleOn.value,
                repeatMode = p.repeatMode,
                hasNext = p.hasNextMediaItem(),
                hasPrevious = p.hasPreviousMediaItem()
            )
        }
    }

    /** ¿La canción viene del servidor? (las del teléfono no tienen dirección http). */
    private fun isRemote(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getString(EXTRA_URI)?.startsWith("http") == true

    /**
     * Duración de lo que suena. En la nube manda la que informa el servidor: el MP3 convertido al vuelo
     * anuncia un tamaño algo mayor que el real y el reproductor calcularía unos segundos de más.
     * En las "mejores partes" manda la del fragmento.
     */
    private fun durationOf(p: Player): Long {
        val item = p.currentMediaItem
        val meta = item?.mediaMetadata
        val fromMeta = meta?.durationMs?.takeIf { it > 0 } ?: 0L
        val clip = meta?.extras?.getBoolean(EXTRA_CLIP) == true
        if (!clip && fromMeta > 0 && isRemote(item)) return fromMeta
        return p.duration.takeIf { it > 0 } ?: fromMeta
    }

    private fun updateQueue(p: Player) {
        _queue.value = List(p.mediaItemCount) { i ->
            val item = p.getMediaItemAt(i)
            val m = item.mediaMetadata
            QueueItem(
                uid = m.extras?.getString(EXTRA_UID) ?: "${item.mediaId}#$i",
                mediaId = item.mediaId,
                title = m.title?.toString().orEmpty(),
                artist = m.artist?.toString().orEmpty(),
                artUri = m.artworkUri
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
        val bits = when (fmt?.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
        return AudioInfo(
            byExt.ifBlank { byMime }, kbps, rate,
            channels = fmt?.channelCount?.takeIf { it > 0 } ?: 0,
            mime = fmt?.sampleMimeType.orEmpty(),
            codecs = fmt?.codecs.orEmpty(),
            pcmBits = bits,
            path = path,
            sizeBytes = size
        )
    }

    /** Si la app está a la vista. Sin nadie mirando, el avance se revisa 1 vez por segundo (no 4). */
    @Volatile private var uiVisible = true

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        // Al volver, la barra salta enseguida a la posición real.
        if (visible) controller?.let { c ->
            _position.value = c.currentPosition.coerceAtLeast(0)
            _buffered.value = c.bufferedPosition.coerceAtLeast(0)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastSave = System.currentTimeMillis()
            while (isActive) {
                controller?.let { c ->
                    val pos = c.currentPosition.coerceAtLeast(0)
                    _position.value = pos
                    _buffered.value = c.bufferedPosition.coerceAtLeast(0)
                    val now = System.currentTimeMillis()
                    if (now - lastSave >= 5_000) { savePosition(pos); lastSave = now } // cada ~5 s
                    countPlay(c, pos)
                    trackLastFm(c, pos)
                }
                delay(if (uiVisible) 250 else 1_000)
            }
        }
    }

    /** Última reproducción contada (por item de la cola): una canción cuenta una vez por vez que suena. */
    private var countedUid: String? = null

    /** Cuenta una escucha cuando la canción lleva 30 s (o la mitad, si es corta). */
    private fun countPlay(c: MediaController, pos: Long) {
        val item = c.currentMediaItem ?: return
        val uid = item.mediaMetadata.extras?.getString(EXTRA_UID) ?: item.mediaId
        if (uid == countedUid) return
        val dur = durationOf(c).takeIf { it > 0 } ?: return
        if (pos < minOf(30_000L, dur / 2)) return
        countedUid = uid
        item.mediaId.toLongOrNull()?.let { (appContext as MelodiaApp).stats.record(it) }
    }

    // --- Last.fm: "escuchando ahora" al empezar y scrobble a la mitad (o a los 4 min) ---
    private var lfmUid: String? = null
    private var lfmStart = 0L
    private var lfmDone = false

    private fun trackLastFm(c: MediaController, pos: Long) {
        val item = c.currentMediaItem ?: return
        val uid = item.mediaMetadata.extras?.getString(EXTRA_UID) ?: item.mediaId
        val m = item.mediaMetadata
        val artist = m.artist?.toString().orEmpty()
        val title = m.title?.toString().orEmpty()
        val album = m.albumTitle?.toString().orEmpty()
        val dur = durationOf(c).takeIf { it > 0 } ?: 0L
        val lfm = (appContext as MelodiaApp).lastfm
        if (uid != lfmUid) {
            if (!c.isPlaying) return
            lfmUid = uid
            lfmStart = System.currentTimeMillis() - pos
            lfmDone = false
            lfm.nowPlaying(artist, title, album, dur)
            return
        }
        // Si se vuelve al principio (repetir), cuenta como otra escucha.
        if (lfmDone && pos < 3_000 && c.isPlaying) {
            lfmStart = System.currentTimeMillis() - pos
            lfmDone = false
            lfm.nowPlaying(artist, title, album, dur)
        }
        if (lfmDone || dur < 30_000) return
        if (pos >= minOf(240_000L, dur / 2)) {
            lfmDone = true
            lfm.scrobble(artist, title, album, dur, lfmStart)
        }
    }

    private var prefetchJob: Job? = null

    /**
     * Descarga por adelantado el comienzo de las siguientes canciones del servidor, pero solo cuando la
     * que suena ya tiene su colchón: al empezar una canción, ella tiene toda la conexión para sí.
     */
    private fun prefetchUpcoming() {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (cur < 0) return
        val next = (1..2).mapNotNull { k ->
            val i = cur + k
            if (i < c.mediaItemCount) c.getMediaItemAt(i).localConfiguration?.uri else null
        }
        prefetchJob?.cancel()
        // Corta enseguida la precarga anterior (era para otra posición de la cola).
        StreamCache.prefetch(appContext, emptyList())
        if (next.isEmpty()) return
        prefetchJob = scope.launch {
            awaitHeadroom(15_000)
            StreamCache.prefetch(appContext, next)
        }
    }

    /**
     * Espera (como mucho [maxWaitMs]) a que la canción de la nube que suena tenga ~15 s descargados
     * por delante o esté completa. Con música del teléfono no espera.
     */
    suspend fun awaitHeadroom(maxWaitMs: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val c = controller ?: return
            if (!isRemote(c.currentMediaItem)) return
            val buffered = c.bufferedPosition
            val dur = durationOf(c)
            if (buffered - c.currentPosition >= 15_000 || (dur > 0 && buffered >= dur - 1_000)) return
            delay(500)
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
                        putString(EXTRA_UID, java.util.UUID.randomUUID().toString())
                    })
                    .build()
            )
            .build()

    // ---- Acciones públicas -------------------------------------------------

    /**
     * Reproduce [songs]. Con [shuffle] = true (botones "aleatorio") empieza en una canción AL AZAR de
     * todas las que hay y sigue con el resto mezclado; [startIndex] solo cuenta sin aleatorio.
     */
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val queue: List<Song>
        val index: Int
        if (shuffle) {
            val pool = if (songs.size > MAX_QUEUE) songs.shuffled().take(MAX_QUEUE) else songs
            val first = pool.random()
            queue = listOf(first) + pool.filter { it !== first }.shuffled()
            index = 0
            originalIds = pool.map { it.id.toString() }
        } else {
            // Con bibliotecas enormes no se manda toda la lista al reproductor (4 000+ canciones
            // tardan y hacen que la app se trabe al elegir una): se usa una ventana alrededor de la elegida.
            val (q, i) = limitQueue(songs, startIndex.coerceIn(0, songs.lastIndex))
            queue = q; index = i
            originalIds = null
        }
        shuffleOn.value = shuffle
        // Modo luna: la canción elegida también empieza en su mejor parte.
        val startMs = favorites.moonStartMs(queue[index].id) ?: 0L
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(queue.map { it.toMediaItem() }, index, startMs)
            c.prepare()
            c.play()
        }
        session.edit()
            .putString(KEY_QUEUE, queue.joinToString(",") { it.id.toString() })
            .putString(KEY_ORIGINAL, originalIds?.joinToString(","))
            .putInt(KEY_INDEX, index)
            .putLong(KEY_POSITION, startMs)
            .putBoolean(KEY_SHUFFLE, shuffle)
            .apply()
    }

    /**
     * "Mejores partes": reproduce seguidas solo las mejores partes de [clips] (canción y
     * milisegundo donde empieza su mejor parte), [lengthMs] de cada una.
     */
    fun playClips(clips: List<Pair<Song, Long>>, lengthMs: Long = 30_000L) {
        if (clips.isEmpty()) return
        val items = clips.take(MAX_QUEUE).map { (song, start) ->
            val base = song.toMediaItem()
            val extras = Bundle(base.mediaMetadata.extras ?: Bundle()).apply { putBoolean(EXTRA_CLIP, true) }
            base.buildUpon()
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start.coerceAtLeast(0))
                        .setEndPositionMs(start.coerceAtLeast(0) + lengthMs)
                        .build()
                )
                .setMediaMetadata(base.mediaMetadata.buildUpon().setExtras(extras).build())
                .build()
        }
        shuffleOn.value = false
        originalIds = null
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items, 0, 0L)
            c.prepare()
            c.play()
        }
    }

    // ---- Cola: editar lo que sigue ------------------------------------------

    /** Pone [songs] justo después de la canción que suena (arriba de la cola). */
    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) { play(songs); return@withController }
            val at = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
            c.addMediaItems(at, songs.take(MAX_QUEUE).map { it.toMediaItem() })
            saveQueue(c)
        }
    }

    /** Añade [songs] al final de la cola. */
    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) { play(songs); return@withController }
            c.addMediaItems(songs.take(MAX_QUEUE).map { it.toMediaItem() })
            saveQueue(c)
        }
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) {
            c.moveMediaItem(from, to)
            saveQueue(c)
        }
    }

    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount && c.mediaItemCount > 1) {
            c.removeMediaItem(index)
            saveQueue(c)
        }
    }

    /** Salta a la canción [index] de la cola. */
    fun skipToQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    /** Guarda la cola editada para retomarla al abrir la app. */
    private fun saveQueue(c: Player) {
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        session.edit()
            .putString(KEY_QUEUE, ids.joinToString(","))
            .putString(KEY_ORIGINAL, originalIds?.joinToString(","))
            .putBoolean(KEY_SHUFFLE, shuffleOn.value)
            .putInt(KEY_INDEX, c.currentMediaItemIndex)
            .apply()
    }

    private fun limitQueue(songs: List<Song>, startIndex: Int): Pair<List<Song>, Int> {
        if (songs.size <= MAX_QUEUE) return songs to startIndex
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
            c.shuffleModeEnabled = false
            shuffleOn.value = shuffle
            c.setMediaItems(queue.map { it.toMediaItem() }, index, position)
            c.prepare()
            _position.value = position
        }
    }

    /** Reanuda lo que había en la cola (o lo pausa si ya suena). */
    fun resume() = withController { c -> if (c.mediaItemCount > 0) { c.prepare(); c.play() } }

    /**
     * Para el widget: pausa o sigue. Si la app estaba cerrada y la cola está vacía, la vuelve a
     * cargar desde la última sesión ([load] da la biblioteca guardada) y empieza a sonar.
     */
    fun playPauseOrRestore(load: suspend () -> List<Song>) = withController { c ->
        if (c.mediaItemCount > 0) {
            if (c.playWhenReady) c.pause() else { c.prepare(); c.play() }
        } else {
            // Leer la biblioteca guardada toma un momento: se hace fuera del hilo principal.
            scope.launch {
                val library = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) }
                restoreLastSession(library)
                withController { if (it.mediaItemCount > 0) it.play() }
            }
        }
    }

    /**
     * Mira la intención (playWhenReady), no si ya suena: mientras una canción de la nube carga, "isPlaying"
     * es falso y antes el botón no hacía nada. Si el reproductor quedó detenido por un error, lo reanuda.
     */
    fun togglePlayPause() = withController { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_IDLE && c.playbackState != Player.STATE_ENDED) c.pause()
        else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
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
        // En la nube no se salta más allá del final real (el servidor respondería con error).
        val dur = durationOf(c)
        val target = if (isRemote(c.currentMediaItem) && dur > 2_000) positionMs.coerceAtMost(dur - 1_000) else positionMs
        c.seekTo(target)
        _position.value = target
        savePosition(target)
    }

    /**
     * Activa el aleatorio mezclando solo lo que viene después de la canción actual; al desactivarlo,
     * la cola vuelve a su orden original (las canciones añadidas después quedan a continuación).
     */
    fun toggleShuffle() = withController { c ->
        if (!shuffleOn.value) {
            val n = c.mediaItemCount
            originalIds = List(n) { c.getMediaItemAt(it).mediaId }
            shuffleOn.value = true
            val cur = c.currentMediaItemIndex
            if (n - cur - 1 > 1) {
                val after = List(n - cur - 1) { c.getMediaItemAt(cur + 1 + it) }.shuffled()
                c.removeMediaItems(cur + 1, n)
                c.addMediaItems(after)
            }
        } else {
            shuffleOn.value = false
            restoreOriginalOrder(c)
            originalIds = null
        }
        saveQueue(c)
        sync(c)
    }

    private fun restoreOriginalOrder(c: MediaController) {
        val ids = originalIds ?: return
        val n = c.mediaItemCount
        if (n < 2) return
        val cur = c.currentMediaItemIndex
        val rank = HashMap<String, Int>()
        ids.forEachIndexed { i, id -> rank.putIfAbsent(id, i) }
        val items = List(n) { c.getMediaItemAt(it) }
        val curRank = rank[items[cur].mediaId] ?: Int.MAX_VALUE
        val before = ArrayList<MediaItem>()
        val unknown = ArrayList<MediaItem>()
        val after = ArrayList<MediaItem>()
        for (i in items.indices) {
            if (i == cur) continue
            val r = rank[items[i].mediaId]
            when {
                r == null -> unknown.add(items[i])
                r < curRank -> before.add(items[i])
                else -> after.add(items[i])
            }
        }
        before.sortBy { rank[it.mediaId] }
        after.sortBy { rank[it.mediaId] }
        c.removeMediaItems(cur + 1, n)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (before.isNotEmpty()) c.addMediaItems(0, before)
        c.addMediaItems(unknown + after)
    }

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
        const val EXTRA_UID = "melodia.uid"
        const val EXTRA_CLIP = "melodia.clip"
        /** Máximo de canciones que se cargan en la cola a la vez. */
        const val MAX_QUEUE = 400
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_ORIGINAL = "original_order"
    }
}
