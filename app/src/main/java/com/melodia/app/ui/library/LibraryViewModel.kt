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
    val serverError: String? = null
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val melodia = app as MelodiaApp
    private val repo = melodia.repository
    private val server = melodia.serverSettings
    private val cache = LibraryCache(app)

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
                    cache.read(LOCAL) to cache.read(REMOTE)?.takeIf { it.tag == tag }
                }
                local = cached.first?.songs.orEmpty()
                remote = if (config.isActive) cached.second?.songs.orEmpty() else emptyList()
                if (cached.first != null || remote.isNotEmpty()) publish(syncing = config.isActive)
            }

            // 2) El teléfono, actualizado (rápido).
            val fresh = runCatching { repo.loadSongs() }.getOrNull()
            if (fresh != null) {
                val changed = fresh != local
                local = fresh
                if (changed) withContext(Dispatchers.IO) { cache.write(LOCAL, fresh) }
                if (changed || !_state.value.loaded) publish(syncing = config.isActive)
            } else if (!_state.value.loaded) {
                publish(syncing = config.isActive)
            }

            // 3) El servidor: solo si hace falta (o si se pidió), en segundo plano.
            if (!config.isActive) {
                if (remote.isNotEmpty()) { remote = emptyList(); publish(syncing = false) }
                withContext(Dispatchers.IO) { cache.delete(REMOTE) }
                return@launch
            }
            val snap = withContext(Dispatchers.IO) { cache.read(REMOTE) }
            val fresh2 = snap != null && snap.tag == tag &&
                    System.currentTimeMillis() - snap.savedAt < SERVER_MAX_AGE_MS
            if (!forceServer && fresh2) {
                publish(syncing = false)
                return@launch
            }
            publish(syncing = true)
            val result = runCatching { SubsonicClient.fetchSongs(config) }
            val list = result.getOrNull()
            if (list != null) {
                remote = list
                withContext(Dispatchers.IO) { cache.write(REMOTE, list, tag) }
                publish(syncing = false)
            } else {
                // Sin conexión: se sigue mostrando la copia guardada.
                publish(syncing = false, error = result.exceptionOrNull()?.message ?: Str.get(R.string.err_cant_connect))
            }
        }
    }

    private suspend fun publish(syncing: Boolean, error: String? = null) {
        val all = (local + remote).sortedBy { it.title.lowercase() }
        val grouped = withContext(Dispatchers.Default) {
            Triple(repo.groupAlbums(all), repo.groupArtists(all), repo.groupFolders(all))
        }
        _state.value = LibraryState(
            loading = false, loaded = true,
            songs = all, albums = grouped.first, artists = grouped.second, folders = grouped.third,
            serverSongs = remote.size, serverSyncing = syncing, serverError = error
        )
    }

    private companion object {
        const val LOCAL = "local"
        const val REMOTE = "server"
        /** La copia del servidor se renueva sola como mucho cada 6 horas. */
        const val SERVER_MAX_AGE_MS = 6L * 60 * 60 * 1000
    }
}
