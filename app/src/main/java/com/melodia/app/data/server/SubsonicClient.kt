package com.melodia.app.data.server

import com.melodia.app.Str
import com.melodia.app.R

import android.net.Uri
import com.melodia.app.data.Song
import com.melodia.app.data.UNKNOWN_ALBUM
import com.melodia.app.data.UNKNOWN_ARTIST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SubsonicException(message: String) : Exception(message)

/** Resultado de "Probar conexión". */
data class ServerInfo(val name: String, val version: String, val apiVersion: String)

/**
 * Cliente mínimo de la API de Subsonic (compatible con Navidrome, Airsonic, Gonic…).
 * Usa autenticación por token y respuestas JSON.
 */
object SubsonicClient {

    const val API_VERSION = "1.16.1"
    const val CLIENT = "Melodia"
    private const val PAGE = 5000

    private fun query(c: ServerConfig, extra: Map<String, String> = emptyMap()): String {
        val params = linkedMapOf(
            "u" to c.user, "t" to c.token, "s" to c.salt,
            "v" to API_VERSION, "c" to CLIENT, "f" to "json"
        )
        params.putAll(extra)
        return params.entries.joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
    }

    fun endpoint(c: ServerConfig, method: String, extra: Map<String, String> = emptyMap()): String =
        "${c.url}/rest/$method?${query(c, extra)}"

    /**
     * Dirección sin credenciales (se guardan y se ven fuera de la app): [ServerAuth] les añade usuario
     * y token solo al descargar.
     */
    private fun publicEndpoint(c: ServerConfig, method: String, extra: Map<String, String>): String =
        "${c.url}/rest/$method?" + extra.entries.joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }

    /** URL para reproducir una canción (respeta la calidad elegida). */
    fun streamUrl(c: ServerConfig, id: String): String {
        val extra = linkedMapOf("id" to id)
        if (c.maxBitRate > 0) {
            extra["maxBitRate"] = c.maxBitRate.toString()
            extra["format"] = "mp3"
        }
        return publicEndpoint(c, "stream", extra)
    }

    fun coverUrl(c: ServerConfig, coverId: String, size: Int = 600): String =
        publicEndpoint(c, "getCoverArt", linkedMapOf("id" to coverId, "size" to size.toString()))

    /** Como [call], pero con parámetros repetidos (varios songId) y por POST, para listas largas. */
    private suspend fun callPairs(c: ServerConfig, method: String, pairs: List<Pair<String, String>>): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL("${c.url}/rest/$method").openConnection() as HttpURLConnection
            try {
                val auth = listOf(
                    "u" to c.user, "t" to c.token, "s" to c.salt,
                    "v" to API_VERSION, "c" to CLIENT, "f" to "json"
                )
                val body = (auth + pairs).joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                readRoot(conn)
            } finally {
                conn.disconnect()
            }
        }

    // --- Favoritos, estrellas y listas (se comparten entre dispositivos) ---

    /** Canciones con "me gusta" en el servidor, con su calificación (0 si no tiene). */
    suspend fun starred(c: ServerConfig): List<Pair<String, Int>> {
        val arr = call(c, "getStarred2").optJSONObject("starred2")?.optJSONArray("song") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            o.getString("id") to o.optInt("userRating", 0).coerceIn(0, 5)
        }
    }

    /** Todas las canciones con estrellas (con o sin "me gusta"). Recorre el catálogo, así que se usa poco. */
    suspend fun rated(c: ServerConfig): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        var offset = 0
        while (true) {
            val songs = call(
                c, "search3",
                mapOf("query" to "", "artistCount" to "0", "albumCount" to "0",
                    "songCount" to PAGE.toString(), "songOffset" to offset.toString())
            ).optJSONObject("searchResult3")?.optJSONArray("song") ?: break
            if (songs.length() == 0) break
            for (i in 0 until songs.length()) {
                val o = songs.getJSONObject(i)
                val r = o.optInt("userRating", 0)
                if (r > 0) out += o.getString("id") to r.coerceIn(0, 5)
            }
            offset += songs.length()
            if (songs.length() < PAGE) break
        }
        return out
    }

    /** Álbumes con "me gusta" en el servidor (con su calificación). */
    suspend fun starredAlbums(c: ServerConfig): List<Pair<String, Int>> {
        val arr = call(c, "getStarred2").optJSONObject("starred2")?.optJSONArray("album") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            o.getString("id") to o.optInt("userRating", 0).coerceIn(0, 5)
        }
    }

    data class RemoteAlbum(val id: String, val rating: Int, val starred: Boolean)

    /** Todos los álbumes del servidor con su calificación y "me gusta". */
    suspend fun albums(c: ServerConfig): List<RemoteAlbum> {
        val out = mutableListOf<RemoteAlbum>()
        var offset = 0
        while (true) {
            val arr = call(c, "getAlbumList2", mapOf("type" to "alphabeticalByName", "size" to "500", "offset" to offset.toString()))
                .optJSONObject("albumList2")?.optJSONArray("album") ?: break
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += RemoteAlbum(o.getString("id"), o.optInt("userRating", 0).coerceIn(0, 5), o.has("starred"))
            }
            if (arr.length() < 500) break
            offset += arr.length()
        }
        return out
    }

    /** Ids (del servidor) de los últimos álbumes añadidos, del más nuevo al más viejo. */
    suspend fun newestAlbums(c: ServerConfig, size: Int = 30): List<String> {
        val arr = call(c, "getAlbumList2", mapOf("type" to "newest", "size" to size.toString()))
            .optJSONObject("albumList2")?.optJSONArray("album") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it).getString("id") }
    }

    /** Id local (el mismo que usa la app en [Song.albumId]) de un álbum del servidor. */
    fun albumLocalId(c: ServerConfig, serverAlbumId: String): Long = remoteId(c, "album:$serverAlbumId")

    suspend fun starAlbum(c: ServerConfig, id: String) { call(c, "star", mapOf("albumId" to id)) }
    suspend fun unstarAlbum(c: ServerConfig, id: String) { call(c, "unstar", mapOf("albumId" to id)) }

    suspend fun star(c: ServerConfig, id: String) { call(c, "star", mapOf("id" to id)) }
    suspend fun unstar(c: ServerConfig, id: String) { call(c, "unstar", mapOf("id" to id)) }

    /**
     * Varios "me gusta" en un solo pedido (la API acepta el parámetro repetido). Si el servidor no
     * admite el pedido agrupado, se envían uno por uno como antes.
     */
    suspend fun starMany(c: ServerConfig, ids: List<String>, albums: Boolean = false) = many(c, "star", ids, albums)

    suspend fun unstarMany(c: ServerConfig, ids: List<String>, albums: Boolean = false) = many(c, "unstar", ids, albums)

    private suspend fun many(c: ServerConfig, method: String, ids: List<String>, albums: Boolean) {
        if (ids.isEmpty()) return
        val key = if (albums) "albumId" else "id"
        if (ids.size == 1) { call(c, method, mapOf(key to ids[0])); return }
        try {
            callPairs(c, method, ids.map { key to it })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            for (id in ids) call(c, method, mapOf(key to id))
        }
    }
    suspend fun setRating(c: ServerConfig, id: String, rating: Int) {
        call(c, "setRating", mapOf("id" to id, "rating" to rating.coerceIn(0, 5).toString()))
    }

    /** [changed]: cuándo se modificó por última vez (vacío si el servidor no lo dice). */
    data class RemotePlaylist(val id: String, val name: String, val changed: String = "")

    suspend fun playlists(c: ServerConfig): List<RemotePlaylist> {
        val arr = call(c, "getPlaylists").optJSONObject("playlists")?.optJSONArray("playlist") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RemotePlaylist(o.getString("id"), o.optString("name"), o.optString("changed"))
        }
    }

    suspend fun playlistSongs(c: ServerConfig, id: String): List<String> {
        val arr = call(c, "getPlaylist", mapOf("id" to id)).optJSONObject("playlist")?.optJSONArray("entry")
            ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it).getString("id") }
    }

    /** Crea una lista en el servidor y devuelve su id. */
    suspend fun createPlaylist(c: ServerConfig, name: String, songs: List<String>): String {
        val r = callPairs(c, "createPlaylist", listOf("name" to name) + songs.map { "songId" to it })
        r.optJSONObject("playlist")?.optString("id")?.takeIf { it.isNotBlank() }?.let { return it }
        return playlists(c).lastOrNull { it.name == name }?.id ?: throw SubsonicException(Str.get(R.string.err_unknown))
    }

    /** Deja la lista del servidor exactamente con estas canciones, en este orden. */
    suspend fun replacePlaylist(c: ServerConfig, id: String, songs: List<String>, currentCount: Int) {
        if (songs.isEmpty()) {
            if (currentCount > 0) {
                callPairs(c, "updatePlaylist", listOf("playlistId" to id) + (0 until currentCount).map { "songIndexToRemove" to it.toString() })
            }
        } else {
            callPairs(c, "createPlaylist", listOf("playlistId" to id) + songs.map { "songId" to it })
        }
    }

    suspend fun renamePlaylist(c: ServerConfig, id: String, name: String) {
        call(c, "updatePlaylist", mapOf("playlistId" to id, "name" to name))
    }

    suspend fun deletePlaylist(c: ServerConfig, id: String) { call(c, "deletePlaylist", mapOf("id" to id)) }

    private fun readRoot(conn: HttpURLConnection): JSONObject {
        conn.connectTimeout = 10_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        if (code !in 200..299) throw SubsonicException(Str.get(R.string.err_http, code))
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val root = runCatching { JSONObject(body).getJSONObject("subsonic-response") }
            .getOrElse { throw SubsonicException(Str.get(R.string.err_not_subsonic)) }
        if (root.optString("status") != "ok") {
            val err = root.optJSONObject("error")
            val msg = when (err?.optInt("code")) {
                40 -> Str.get(R.string.err_bad_login)
                41 -> Str.get(R.string.err_no_token)
                else -> err?.optString("message")?.takeIf { it.isNotBlank() } ?: Str.get(R.string.err_unknown)
            }
            throw SubsonicException(msg)
        }
        return root
    }

    private suspend fun call(c: ServerConfig, method: String, extra: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(endpoint(c, method, extra)).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code !in 200..299) throw SubsonicException(Str.get(R.string.err_http, code))
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = runCatching { JSONObject(body).getJSONObject("subsonic-response") }
                    .getOrElse { throw SubsonicException(Str.get(R.string.err_not_subsonic)) }
                if (root.optString("status") != "ok") {
                    val err = root.optJSONObject("error")
                    val msg = when (err?.optInt("code")) {
                        40 -> Str.get(R.string.err_bad_login)
                        41 -> Str.get(R.string.err_no_token)
                        else -> err?.optString("message")?.takeIf { it.isNotBlank() } ?: Str.get(R.string.err_unknown)
                    }
                    throw SubsonicException(msg)
                }
                root
            } finally {
                conn.disconnect()
            }
        }

    suspend fun ping(c: ServerConfig): ServerInfo {
        val r = call(c, "ping")
        return ServerInfo(
            name = r.optString("type").ifBlank { "Subsonic" }.replaceFirstChar { it.uppercase() },
            version = r.optString("serverVersion"),
            apiVersion = r.optString("version")
        )
    }

    /**
     * Huella barata del estado de la biblioteca del servidor (nº de canciones + último escaneo).
     * Si no cambia, no hace falta volver a descargar nada. Null si el servidor no la ofrece.
     */
    suspend fun fingerprint(c: ServerConfig): String? = runCatching {
        val st = call(c, "getScanStatus").optJSONObject("scanStatus") ?: return null
        if (st.optBoolean("scanning")) return null
        val count = st.optLong("count", -1L)
        val last = st.optString("lastScan")
        if (count < 0 && last.isBlank()) null else "$count|$last"
    }.getOrNull()

    /** Estrellas de todo el catálogo, vistas al bajar la biblioteca (así no hace falta recorrerlo otra vez). */
    class Catalog(val server: String, val at: Long, val ratings: Map<String, Int>)

    @Volatile private var lastCatalog: Catalog? = null

    /** Las estrellas vistas en la última descarga completa de la biblioteca de este servidor, o null. */
    fun catalogRatings(c: ServerConfig): Catalog? = lastCatalog?.takeIf { it.server == "${c.url}|${c.user}" }

    /** Descarga la lista completa de canciones del servidor. */
    suspend fun fetchSongs(c: ServerConfig, expected: Int = -1): List<Song> {
        val out = mutableListOf<Song>()
        val ratings = HashMap<String, Int>()
        var offset = 0
        // Páginas grandes: toda una biblioteca de ~5 000 canciones llega en 1 o 2 pedidos
        // (el servidor comprime la respuesta) en vez de decenas de pedidos pequeños.
        while (true) {
            val r = call(
                c, "search3",
                mapOf(
                    "query" to "", "artistCount" to "0", "albumCount" to "0",
                    "songCount" to PAGE.toString(), "songOffset" to offset.toString()
                )
            )
            val songs = r.optJSONObject("searchResult3")?.optJSONArray("song") ?: JSONArray()
            val got = songs.length()
            for (i in 0 until got) {
                val o = songs.getJSONObject(i)
                out += toSong(c, o)
                val r = o.optInt("userRating", 0)
                if (r > 0) ratings[o.getString("id")] = r.coerceIn(0, 5)
            }
            if (got == 0) break
            offset += got
            // Si ya sabemos cuántas hay (getScanStatus), no hace falta pedir una página vacía.
            if (expected > 0 && offset >= expected) break
            if (got < PAGE && expected <= 0 && got % 500 != 0) break
        }
        if (out.isNotEmpty()) lastCatalog = Catalog("${c.url}|${c.user}", System.currentTimeMillis(), ratings)
        // Algunos servidores no devuelven nada con búsqueda vacía: se recorre por álbumes.
        if (out.isEmpty()) out += fetchByAlbums(c)
        return out.distinctBy { it.id }
    }

    private suspend fun fetchByAlbums(c: ServerConfig): List<Song> {
        val out = mutableListOf<Song>()
        var offset = 0
        while (true) {
            val r = call(
                c, "getAlbumList2",
                mapOf("type" to "alphabeticalByName", "size" to "500", "offset" to offset.toString())
            )
            val albums = r.optJSONObject("albumList2")?.optJSONArray("album") ?: JSONArray()
            for (i in 0 until albums.length()) {
                val id = albums.getJSONObject(i).optString("id")
                if (id.isBlank()) continue
                val a = call(c, "getAlbum", mapOf("id" to id)).optJSONObject("album") ?: continue
                val songs = a.optJSONArray("song") ?: continue
                for (k in 0 until songs.length()) out += toSong(c, songs.getJSONObject(k))
            }
            if (albums.length() < 500) break
            offset += 500
        }
        return out
    }

    private fun toSong(c: ServerConfig, o: JSONObject): Song {
        val id = o.getString("id")
        val title = o.optString("title").ifBlank { Str.get(R.string.untitled) }
        val artist = o.optString("artist").ifBlank { UNKNOWN_ARTIST }
        val album = o.optString("album").ifBlank { UNKNOWN_ALBUM }
        val albumKey = o.optString("albumId").ifBlank { "$artist/$album" }
        val cover = o.optString("coverArt").ifBlank { o.optString("albumId") }
        val suffix = o.optString("suffix").ifBlank { "mp3" }
        val transcoded = c.maxBitRate > 0
        // Ruta "virtual" para la pestaña Carpetas: Servidor/<carpeta del servidor>.
        val rawPath = o.optString("path").ifBlank { "$artist/$album/$title.$suffix" }
        var path = Str.get(R.string.server_folder) + "/" + rawPath.trimStart('/')
        if (transcoded) path = path.substringBeforeLast('.') + ".mp3"
        return Song(
            id = remoteId(c, id),
            title = title,
            artist = artist,
            album = album,
            albumId = remoteId(c, "album:$albumKey"),
            durationMs = o.optLong("duration") * 1000L,
            uri = Uri.parse(streamUrl(c, id)),
            artUri = Uri.parse(if (cover.isNotBlank()) coverUrl(c, cover) else coverUrl(c, id)),
            path = path,
            sizeBytes = if (transcoded) 0L else o.optLong("size"),
            remoteId = id,
            genre = o.optString("genre").trim(),
            year = o.optInt("year").let { y -> if (y in 1000..2999) y else 0 }
        )
    }

    /** Id numérico estable (negativo, para no chocar con los ids del teléfono). */
    private fun remoteId(c: ServerConfig, key: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64 bits
        for (ch in "${c.url}|${c.user}|$key") {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L
        }
        return -((h and Long.MAX_VALUE) or 1L)
    }
}
