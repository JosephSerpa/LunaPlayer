package com.melodia.app.ui.library

import com.melodia.app.Str
import com.melodia.app.R

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodia.app.MelodiaApp
import com.melodia.app.data.Album
import com.melodia.app.data.Artist
import com.melodia.app.data.Folder
import com.melodia.app.data.Song
import com.melodia.app.data.LibraryCache
import com.melodia.app.data.SearchKeys
import com.melodia.app.data.ServerDb
import com.melodia.app.data.server.ServerConfig
import com.melodia.app.data.server.ServerSettings
import com.melodia.app.data.server.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    /** Canciones que vienen del servidor Subsonic/Navidrome. */
    val serverSongs: Int = 0,
    val serverSyncing: Boolean = false,
    val serverError: String? = null,
    /** Claves de búsqueda precalculadas (minúsculas, sin acentos). */
    val songKeys: Map<Long, String> = emptyMap(),
    val albumKeys: Map<Long, String> = emptyMap(),
    val artistKeys: Map<String, String> = emptyMap(),
    val folderKeys: Map<Int, String> = emptyMap()
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val melodia = app as MelodiaApp
    private val repo = melodia.repository
    private val server = melodia.serverSettings
    private val cache = LibraryCache(app)
    private val serverDb = ServerDb.get(app)
    private var lastForegroundCheck = 0L

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var job: Job? = null
    private var local: List<Song> = emptyList()
    private var remote: List<Song> = emptyList()

    init {
        // Si cambia la conexión al servidor, se vuelve a sincronizar.
        viewModelScope.launch {
            server.config.drop(1).distinctUntilChanged().collect { reload(forceServer = true) }
        }
    }

    /** Huella de la conexión: si cambia, la copia guardada del servidor ya no sirve. */
    private fun serverTag(c: ServerConfig) =
        ServerSettings.md5("${c.url}|${c.user}|${c.token}|${c.maxBitRate}")

    fun load() {
        if (_state.value.loaded || _state.value.loading) return
        reload(forceServer = false)
    }

    /** Sincronizar ahora (botón en Ajustes → Servidor). */
    fun syncServer() = reload(forceServer = true)

    fun reload(forceServer: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val config = server.config.value
            val tag = serverTag(config)

            // 1) Lo guardado: aparece al instante.
            if (!_state.value.loaded) {
                _state.value = _state.value.copy(loading = true)
                val cached = withContext(Dispatchers.IO) {
                    cache.read(LOCAL) to (if (config.isActive) serverDb.read(tag) else null)
                }
                local = cached.first?.songs.orEmpty()
                remote = cached.second.orEmpty()
                if (cached.first != null || remote.isNotEmpty()) publish(syncing = config.isActive)
            }

            // 2) El teléfono, actualizado (rápido).
            val fresh = runCatching { repo.loadSongs() }.getOrNull()
            if (fresh != null) {
                val changed = fresh != local
                if (changed) local = fresh
                if (changed) withContext(Dispatchers.IO) { cache.write(LOCAL, fresh) }
                if (changed || !_state.value.loaded) publish(syncing = config.isActive)
            } else if (!_state.value.loaded) {
                publish(syncing = config.isActive)
            }

            // 3) El servidor: solo si cambió algo (o si se pidió), en segundo plano.
            if (!config.isActive) {
                if (remote.isNotEmpty()) { remote = emptyList(); publish(syncing = false) }
                withContext(Dispatchers.IO) { serverDb.clear() }
                return@launch
            }
            val info = withContext(Dispatchers.IO) { serverDb.info() }?.takeIf { it.tag == tag }
            if (!forceServer && info != null) {
                val age = System.currentTimeMillis() - info.checkedAt
                if (age < CHECK_MIN_MS) { publish(syncing = false); return@launch }
                publish(syncing = true)
                val fp = SubsonicClient.fingerprint(config)
                if (fp != null && fp == info.fingerprint) {
                    withContext(Dispatchers.IO) { serverDb.touch() }
                    publish(syncing = false)
                    return@launch
                }
                // Sin huella (servidor que no la ofrece): se renueva solo pasadas 6 horas.
                if (fp == null && age < SERVER_MAX_AGE_MS) { publish(syncing = false); return@launch }
            }
            publish(syncing = true)
            // La huella se toma ANTES de bajar la lista: si el servidor cambia mientras tanto,
            // la próxima revisión lo detecta. Además dice cuántas canciones hay (menos pedidos).
            val fp = SubsonicClient.fingerprint(config).orEmpty()
            val expected = fp.substringBefore('|').toIntOrNull() ?: -1
            val result = runCatching { SubsonicClient.fetchSongs(config, expected) }
            val list = result.getOrNull()
            if (list != null) {
                withContext(Dispatchers.IO) { serverDb.sync(list, tag, fp) }
                remote = list
                publish(syncing = false)
            } else {
                // Sin conexión: se sigue mostrando la copia guardada.
                publish(syncing = false, error = result.exceptionOrNull()?.message ?: Str.get(R.string.err_cant_connect))
            }
        }
    }

    /**
     * Al volver a la app: se revisa la música del teléfono (canciones descargadas mientras tanto) y, si
     * el servidor cambió, también la del servidor (esa revisión tiene su propio mínimo de 2 minutos).
     */
    fun onForeground() {
        if (!_state.value.loaded) return
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastForegroundCheck < LOCAL_CHECK_MS) return
        lastForegroundCheck = now
        reload(forceServer = false)
    }

    // Con qué listas se calculó lo publicado: si no cambiaron, solo se actualiza el aviso de sincronización.
    private var publishedLocal: List<Song>? = null
    private var publishedRemote: List<Song>? = null

    private suspend fun publish(syncing: Boolean, error: String? = null) {
        val prev = _state.value
        if (prev.loaded && publishedLocal === local && publishedRemote === remote) {
            _state.value = prev.copy(loading = false, serverSyncing = syncing, serverError = error, serverSongs = remote.size)
            return
        }
        val l = local
        val r = remote
        // Ordenar miles de canciones se hace fuera del hilo de la interfaz, con la clave calculada una vez.
        val all = withContext(Dispatchers.Default) {
            (l + r).map { it.title.lowercase() to it }.sortedBy { it.first }.map { it.second }
        }
        val grouped = withContext(Dispatchers.Default) {
            Triple(repo.groupAlbums(all), repo.groupArtists(all), repo.groupFolders(all))
        }
        val keys = withContext(Dispatchers.Default) {
            arrayOf(
                all.associate { it.id to SearchKeys.song(it) },
                grouped.first.associate { it.id to SearchKeys.norm("${it.title} ${it.artist}") },
                grouped.second.associate { it.name to SearchKeys.norm(it.name) },
                grouped.third.associate { it.id to SearchKeys.norm("${it.name} ${it.displayPath}") }
            )
        }
        @Suppress("UNCHECKED_CAST")
        _state.value = LibraryState(
            loading = false, loaded = true,
            songs = all, albums = grouped.first, artists = grouped.second, folders = grouped.third,
            serverSongs = remote.size, serverSyncing = syncing, serverError = error,
            songKeys = keys[0] as Map<Long, String>, albumKeys = keys[1] as Map<Long, String>,
            artistKeys = keys[2] as Map<String, String>, folderKeys = keys[3] as Map<Int, String>
        )
        publishedLocal = l
        publishedRemote = r
    }

    private companion object {
        const val LOCAL = "local"
        /** Mínimo entre comprobaciones del servidor (al volver a la app). */
        const val CHECK_MIN_MS = 2L * 60 * 1000
        /** Mínimo entre revisiones de la música del teléfono al volver a la app. */
        const val LOCAL_CHECK_MS = 20L * 1000
        /** La copia del servidor se renueva sola como mucho cada 6 horas. */
        const val SERVER_MAX_AGE_MS = 6L * 60 * 60 * 1000
    }
}
