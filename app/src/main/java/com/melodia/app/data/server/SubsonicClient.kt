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

    private const val API_VERSION = "1.16.1"
    private const val CLIENT = "Melodia"
    private const val PAGE = 500

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

    /** URL para reproducir una canción (respeta la calidad elegida). */
    fun streamUrl(c: ServerConfig, id: String): String {
        val extra = mutableMapOf("id" to id)
        if (c.maxBitRate > 0) {
            extra["maxBitRate"] = c.maxBitRate.toString()
            extra["format"] = "mp3"
        }
        return endpoint(c, "stream", extra)
    }

    fun coverUrl(c: ServerConfig, coverId: String, size: Int = 600): String =
        endpoint(c, "getCoverArt", mapOf("id" to coverId, "size" to size.toString()))

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

    /** Descarga la lista completa de canciones del servidor. */
    suspend fun fetchSongs(c: ServerConfig): List<Song> {
        val out = mutableListOf<Song>()
        var offset = 0
        while (true) {
            val r = call(
                c, "search3",
                mapOf(
                    "query" to "", "artistCount" to "0", "albumCount" to "0",
                    "songCount" to PAGE.toString(), "songOffset" to offset.toString()
                )
            )
            val songs = r.optJSONObject("searchResult3")?.optJSONArray("song") ?: JSONArray()
            for (i in 0 until songs.length()) out += toSong(c, songs.getJSONObject(i))
            if (songs.length() < PAGE) break
            offset += PAGE
        }
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
            remoteId = id
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
