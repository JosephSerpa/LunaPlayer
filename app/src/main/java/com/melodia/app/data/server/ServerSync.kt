package com.melodia.app.data.server

import android.content.Context
import com.melodia.app.data.FavoritesStore
import com.melodia.app.data.Playlist
import com.melodia.app.data.PlaylistStore
import com.melodia.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mantiene iguales en todos tus dispositivos las listas de reproducción, los "me gusta" y las
 * estrellas de la música del servidor Subsonic / Navidrome. El servidor es la copia común:
 * lo que cambias aquí se envía, y al abrir la app se baja lo que cambió en otros dispositivos.
 * Solo afecta a canciones del servidor; la música local de cada dispositivo se queda en él.
 */
@OptIn(FlowPreview::class)
class ServerSync(
    context: Context,
    private val settings: ServerSettings,
    private val favorites: FavoritesStore,
    private val playlists: PlaylistStore
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Último error de sincronización (null si todo bien). */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> _error.value = e.message ?: e.javaClass.simpleName }
    )

    /** Resultado de la última sincronización completa (para mostrarlo en Ajustes → Servidor). */
    data class Status(val at: Long, val likes: Int, val playlists: Int)
    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) syncNow()
    }

    // id local ↔ id del servidor de las canciones que hay ahora en la biblioteca
    @Volatile private var toRemote: Map<Long, String> = emptyMap()
    @Volatile private var toLocal: Map<String, Long> = emptyMap()
    @Volatile private var albumIds: Set<Long> = emptySet()
    private var lastRun = 0L
    /** Cuándo se pidió "sincronizar ahora": solo valen estrellas vistas después de eso. */
    @Volatile private var fullAt = 0L
    /** Última vez que se envió una calificación al servidor desde aquí. */
    @Volatile private var lastRatingPush = 0L
    private var watching = false

    // Lo último que se sabe que está en el servidor (para enviar solo diferencias)
    private var likesSnap: Set<Long> = loadLongs("likes")
    private var ratingsSnap: Map<Long, Int> = loadRatings()
    private var plSnap: Map<Long, Snap> = loadSnaps()

    // Álbumes: id local ↔ id del servidor (solo los que tienen canciones del servidor en la biblioteca)
    @Volatile private var albumToRemote: Map<Long, String> = emptyMap()
    @Volatile private var albumToLocal: Map<String, Long> = emptyMap()
    private var albumLikesSnap: Set<Long> = loadLongs("albumLikes")
    private var albumRatingsSnap: Map<Long, Int> = loadRatings("albumRatings")

    /** [changed]: fecha de modificación que dio el servidor (vacía si no se sabe: entonces se vuelve a pedir). */
    private data class Snap(val sid: String, val name: String, val songs: List<String>, val changed: String = "")

    /** La biblioteca se cargó (o cambió): se aprende qué canciones son del servidor y se sincroniza. */
    fun onLibrary(songs: List<Song>) {
        val remote = songs.filter { it.remoteId != null }
        albumIds = remote.map { it.albumId }.toSet()
        toRemote = remote.associate { it.id to it.remoteId!! }
        toLocal = remote.associate { it.remoteId!! to it.id }
        startWatching()
        syncNow(minGapMs = 20_000)
    }

    /** Sincroniza ya (al abrir la app o al volver a ella). */
    fun syncNow(minGapMs: Long = 0, full: Boolean = false) {
        if (full) {
            prefs.edit().putLong("ratedAt", 0L).apply()
            fullAt = System.currentTimeMillis()
        }
        if (!active()) return
        val now = System.currentTimeMillis()
        if (now - lastRun < minGapMs) return
        lastRun = now
        scope.launch { guarded { fullSync() } }
    }

    private fun active() = _enabled.value && settings.config.value.isActive && toRemote.isNotEmpty()

    private fun startWatching() {
        if (watching) return
        watching = true
        scope.launch { favorites.marks.debounce(1500).collect { guarded { pushMarks() } } }
        scope.launch { favorites.albumMarks.debounce(1500).collect { guarded { pushMarks() } } }
        scope.launch { playlists.playlists.debounce(1500).collect { guarded { pushPlaylists() } } }
    }

    // No se llama run: dentro de launch {} Kotlin usaria el run de la libreria estandar y se saltaria esto.
    private suspend fun guarded(block: suspend () -> Unit) {
        if (!active()) return
        mutex.withLock {
            try {
                block()
                _error.value = null
            } catch (e: Exception) {
                // Sin conexión o servidor caído: queda pendiente y se reintenta en la próxima sincronización.
                _error.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private suspend fun fullSync() {
        val c = settings.config.value
        pushMarks()
        pullMarks(c)
        pushMarks()
        pushPlaylists()
        pullPlaylists(c)
        _status.value = Status(System.currentTimeMillis(), likesSnap.size, plSnap.size)
    }

    // ---------------------------------------------------------------- me gusta y estrellas

    /** Aprende qué álbum del servidor es cuál en la biblioteca (una vez por hora, o si aún no se sabe). */
    private suspend fun ensureAlbumMap(c: ServerConfig, force: Boolean = false): List<SubsonicClient.RemoteAlbum>? {
        val now = System.currentTimeMillis()
        if (!force && albumToRemote.isNotEmpty() && now - prefs.getLong("albumsAt", 0L) < 3_600_000L) return null
        val list = SubsonicClient.albums(c)
        val pairs = list.mapNotNull { a ->
            val lid = SubsonicClient.albumLocalId(c, a.id)
            if (lid in albumIds) lid to a.id else null
        }
        albumToRemote = pairs.toMap()
        albumToLocal = pairs.associate { it.second to it.first }
        prefs.edit().putLong("albumsAt", now).apply()
        return list
    }

    private suspend fun pushAlbumMarks(c: ServerConfig) {
        if (albumToRemote.isEmpty()) return
        val marks = favorites.albumMarks.value
        val ids = albumToRemote
        val curLikes = marks.filter { it.value.liked && it.key in ids }.keys
        val curRatings = marks.filter { it.value.rating > 0 && it.key in ids }.mapValues { it.value.rating }
        try {
            for (chunk in (curLikes - albumLikesSnap).chunked(BATCH)) {
                SubsonicClient.starMany(c, chunk.map { ids.getValue(it) }, albums = true); albumLikesSnap = albumLikesSnap + chunk
            }
            for (chunk in albumLikesSnap.filter { it in ids && it !in curLikes }.chunked(BATCH)) {
                SubsonicClient.unstarMany(c, chunk.map { ids.getValue(it) }, albums = true); albumLikesSnap = albumLikesSnap - chunk.toSet()
            }
            for ((id, r) in curRatings) if (albumRatingsSnap[id] != r) {
                SubsonicClient.setRating(c, ids.getValue(id), r); albumRatingsSnap = albumRatingsSnap + (id to r)
            }
            for (id in albumRatingsSnap.keys.filter { it in ids && it !in curRatings }) {
                SubsonicClient.setRating(c, ids.getValue(id), 0); albumRatingsSnap = albumRatingsSnap - id
            }
        } finally {
            prefs.edit()
                .putString("albumLikes", JSONArray(albumLikesSnap.toList()).toString())
                .putString("albumRatings", JSONObject(albumRatingsSnap.mapKeys { it.key.toString() }).toString())
                .apply()
        }
    }

    private suspend fun pullAlbumMarks(c: ServerConfig, fullList: List<SubsonicClient.RemoteAlbum>?) {
        if (albumToLocal.isEmpty()) return
        val liked = HashSet<Long>()
        val ratings = HashMap<Long, Int>()
        for ((rid, r) in SubsonicClient.starredAlbums(c)) {
            val id = albumToLocal[rid] ?: continue
            liked += id
            if (r > 0) ratings[id] = r
        }
        var all = false
        if (fullList != null) {
            for (a in fullList) {
                val id = albumToLocal[a.id] ?: continue
                if (a.starred) liked += id
                if (a.rating > 0) ratings[id] = a.rating
            }
            all = true
        }
        favorites.applyRemoteAlbums(albumToRemote.keys, liked, ratings, all)
        albumLikesSnap = liked
        albumRatingsSnap = ratings
        prefs.edit()
            .putString("albumLikes", JSONArray(albumLikesSnap.toList()).toString())
            .putString("albumRatings", JSONObject(albumRatingsSnap.mapKeys { it.key.toString() }).toString())
            .apply()
    }

    private suspend fun pushMarks() {
        val c = settings.config.value
        runCatching { ensureAlbumMap(c) }
        pushAlbumMarks(c)
        val marks = favorites.marks.value
        val ids = toRemote
        val curLikes = marks.filter { it.value.liked && it.key in ids }.keys
        val curRatings = marks.filter { it.value.rating > 0 && it.key in ids }.mapValues { it.value.rating }
        try {
            // En lotes de 100: cientos de "me gusta" ya no son cientos de pedidos.
            for (chunk in (curLikes - likesSnap).chunked(BATCH)) {
                SubsonicClient.starMany(c, chunk.map { ids.getValue(it) }); likesSnap = likesSnap + chunk
            }
            for (chunk in likesSnap.filter { it in ids && it !in curLikes }.chunked(BATCH)) {
                SubsonicClient.unstarMany(c, chunk.map { ids.getValue(it) }); likesSnap = likesSnap - chunk.toSet()
            }
            for ((id, r) in curRatings) if (ratingsSnap[id] != r) {
                lastRatingPush = System.currentTimeMillis()
                SubsonicClient.setRating(c, ids.getValue(id), r); ratingsSnap = ratingsSnap + (id to r)
            }
            for (id in ratingsSnap.keys.filter { it in ids && it !in curRatings }) {
                lastRatingPush = System.currentTimeMillis()
                SubsonicClient.setRating(c, ids.getValue(id), 0); ratingsSnap = ratingsSnap - id
            }
        } finally {
            saveMarksSnap()
        }
    }

    private suspend fun pullMarks(c: ServerConfig) {
        val starred = SubsonicClient.starred(c)
        val liked = HashSet<Long>()
        val ratings = HashMap<Long, Int>()
        for ((rid, r) in starred) {
            val id = toLocal[rid] ?: continue
            liked += id
            if (r > 0) ratings[id] = r
        }
        // Las estrellas de canciones sin "me gusta" solo salen recorriendo el catálogo. Si la biblioteca se
        // acaba de bajar, ya se vieron ahí (no se pide nada más); si no, se recorre como mucho cada 3 horas.
        val now = System.currentTimeMillis()
        val ratedAt = prefs.getLong("ratedAt", 0L)
        var all = false
        val catalog = SubsonicClient.catalogRatings(c)
        // Solo vale si es más nueva que las estrellas que se enviaron desde aquí (si no, pisaría un cambio reciente).
        if (catalog != null && catalog.at > ratedAt && catalog.at >= fullAt && catalog.at > lastRatingPush &&
            now - catalog.at < FULL_SCAN_MS
        ) {
            // Las de canciones con "me gusta" ya llegaron frescas arriba: no se tocan.
            for ((rid, r) in catalog.ratings) toLocal[rid]?.let { if (it !in liked) ratings[it] = r }
            prefs.edit().putLong("ratedAt", catalog.at).apply()
            all = true
        } else if (now - ratedAt > FULL_SCAN_MS) {
            for ((rid, r) in SubsonicClient.rated(c)) toLocal[rid]?.let { ratings[it] = r }
            prefs.edit().putLong("ratedAt", now).apply()
            all = true
        }
        favorites.applyRemote(toRemote.keys, liked, ratings, all)
        // Álbumes: lo mismo (con el catálogo completo de álbumes una vez por hora).
        val albumList = ensureAlbumMap(c, force = all)
        pullAlbumMarks(c, albumList)
        likesSnap = liked
        ratingsSnap = ratings
        saveMarksSnap()
    }

    // ---------------------------------------------------------------- listas de reproducción

    private fun remoteOf(p: Playlist): List<String> = p.songIds.mapNotNull { toRemote[it] }

    private suspend fun pushPlaylists() {
        val c = settings.config.value
        val server = SubsonicClient.playlists(c)
        val serverIds = server.map { it.id }.toSet()
        val snap = plSnap.toMutableMap()
        val linked = playlists.playlists.value.mapNotNull { it.serverId }.toMutableSet()
        var local = playlists.playlists.value
        try {
            // 1) Listas locales sin vincular que ya existen en el servidor con el mismo nombre: se juntan.
            local = local.map { l ->
                if (l.serverId != null) return@map l
                val m = server.firstOrNull { it.name == l.name && it.id !in linked } ?: return@map l
                linked += m.id
                val ss = SubsonicClient.playlistSongs(c, m.id)
                snap[l.id] = Snap(m.id, m.name, ss, m.changed)
                val merged = ss.mapNotNull { toLocal[it] } + l.songIds.filter { it !in ss.mapNotNull { r -> toLocal[r] } }
                l.copy(serverId = m.id, songIds = merged.distinct())
            }
            // 2) Enviar lo nuevo y lo cambiado.
            local = local.map { l ->
                val rs = remoteOf(l)
                val s = snap[l.id]
                val sid = l.serverId
                when {
                    sid == null || sid !in serverIds -> {
                        // Si la borraron en el servidor sin tocarla aquí, se quita luego (pullPlaylists).
                        val removedRemotely = sid != null && s != null && s.name == l.name && same(s.songs, rs)
                        if (removedRemotely) l else {
                            val nid = SubsonicClient.createPlaylist(c, l.name, rs)
                            snap[l.id] = Snap(nid, l.name, rs)
                            l.copy(serverId = nid)
                        }
                    }
                    s == null || s.name != l.name || !same(s.songs, rs) -> {
                        var songsNow = s?.songs ?: SubsonicClient.playlistSongs(c, sid)
                        if (s == null || !same(s.songs, rs)) {
                            // Lo que el servidor tenga y esta biblioteca no conozca, se conserva.
                            val unknown = songsNow.filter { it !in toLocal && it !in rs }
                            val target = rs + unknown
                            SubsonicClient.replacePlaylist(c, sid, target, songsNow.size)
                            songsNow = target
                        }
                        if (s == null || s.name != l.name) SubsonicClient.renamePlaylist(c, sid, l.name)
                        snap[l.id] = Snap(sid, l.name, songsNow)
                        l
                    }
                    else -> l
                }
            }
            // 3) Las que se borraron aquí se borran en el servidor.
            val localIds = local.map { it.id }.toSet()
            for ((lid, s) in snap.toMap()) if (lid !in localIds) {
                if (s.sid in serverIds) SubsonicClient.deletePlaylist(c, s.sid)
                snap.remove(lid)
            }
        } finally {
            plSnap = snap
            savePlSnap()
            if (local != playlists.playlists.value) playlists.replaceAll(local)
        }
    }

    /** Baja las listas del servidor: nuevas, cambiadas en otro dispositivo o borradas allí. */
    private suspend fun pullPlaylists(c: ServerConfig) {
        val server = SubsonicClient.playlists(c)
        // Solo se piden las canciones de las listas que cambiaron desde la última vez.
        val known = plSnap.values.associateBy { it.sid }
        val serverSongs = HashMap<String, List<String>>()
        for (sp in server) {
            val s = known[sp.id]
            serverSongs[sp.id] =
                if (s != null && sp.changed.isNotBlank() && s.changed == sp.changed) s.songs
                else SubsonicClient.playlistSongs(c, sp.id)
        }
        val byId = server.associateBy { it.id }
        val snap = plSnap.toMutableMap()
        val out = mutableListOf<Playlist>()
        var seq = System.currentTimeMillis()
        for (l in playlists.playlists.value) {
            val sid = l.serverId
            if (sid == null) { out += l; continue }
            val sp = byId[sid]
            if (sp == null) continue // borrada en el servidor
            val s = snap[l.id]
            val unchanged = s != null && s.name == l.name && same(s.songs, remoteOf(l))
            if (unchanged) {
                val ss = serverSongs.getValue(sid)
                val localOnly = l.songIds.filter { it > 0 }
                out += l.copy(name = sp.name, songIds = (ss.mapNotNull { toLocal[it] } + localOnly).distinct())
                snap[l.id] = Snap(sid, sp.name, ss, sp.changed)
            } else out += l
        }
        val have = out.mapNotNull { it.serverId }.toSet()
        for (sp in server) if (sp.id !in have) {
            val ss = serverSongs.getValue(sp.id)
            val p = Playlist(seq++, sp.name, ss.mapNotNull { toLocal[it] }, sp.id)
            out += p
            snap[p.id] = Snap(sp.id, sp.name, ss, sp.changed)
        }
        val ids = out.map { it.id }.toSet()
        snap.keys.retainAll(ids)
        plSnap = snap
        savePlSnap()
        playlists.replaceAll(out)
    }

    /** Compara dos listas de canciones del servidor solo en las que esta biblioteca conoce. */
    private fun same(a: List<String>, b: List<String>) =
        a.filter { it in toLocal } == b.filter { it in toLocal }

    // ---------------------------------------------------------------- guardado

    private fun saveMarksSnap() {
        prefs.edit()
            .putString("likes", JSONArray(likesSnap.toList()).toString())
            .putString("ratings", JSONObject(ratingsSnap.mapKeys { it.key.toString() }).toString())
            .apply()
    }

    private fun savePlSnap() {
        val arr = JSONArray()
        for ((id, s) in plSnap) {
            arr.put(JSONObject().put("id", id).put("sid", s.sid).put("name", s.name).put("songs", JSONArray(s.songs)).put("changed", s.changed))
        }
        prefs.edit().putString("pl", arr.toString()).apply()
    }

    private fun loadLongs(key: String): Set<Long> = runCatching {
        val a = JSONArray(prefs.getString(key, null) ?: return@runCatching emptySet())
        List(a.length()) { a.getLong(it) }.toSet()
    }.getOrDefault(emptySet())

    private fun loadRatings(key: String = "ratings"): Map<Long, Int> = runCatching {
        val o = JSONObject(prefs.getString(key, null) ?: return@runCatching emptyMap())
        o.keys().asSequence().associate { it.toLong() to o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun loadSnaps(): Map<Long, Snap> = runCatching {
        val a = JSONArray(prefs.getString("pl", null) ?: return@runCatching emptyMap())
        buildMap {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val sg = o.getJSONArray("songs")
                put(o.getLong("id"), Snap(o.getString("sid"), o.getString("name"), List(sg.length()) { sg.getString(it) }, o.optString("changed")))
            }
        }
    }.getOrDefault(emptyMap())

    private companion object {
        const val KEY_ENABLED = "enabled"
        /** Pedidos de "me gusta" agrupados. */
        const val BATCH = 100
        /** Cada cuánto se recorre el catálogo completo (estrellas sin "me gusta", álbumes). */
        const val FULL_SCAN_MS = 3L * 3_600_000L
    }
}
