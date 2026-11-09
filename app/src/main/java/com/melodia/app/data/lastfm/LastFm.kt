package com.melodia.app.data.lastfm

import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import android.content.Context
import android.net.Uri
import com.melodia.app.BuildConfig
import com.melodia.app.data.FavoritesStore
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class LastFmException(val code: Int, message: String) : Exception(message)

/** Un elemento de las estadísticas: artista, canción o álbum. */
data class LfmItem(
    val title: String,
    val artist: String = "",
    val plays: Int = 0,
    val image: String? = null,
    val url: String? = null,
    val date: Long = 0L,
    val nowPlaying: Boolean = false
)

data class LfmProfile(
    val name: String,
    val realName: String,
    val playcount: Long,
    val artists: Long,
    val tracks: Long,
    val albums: Long,
    val registered: Long,
    val country: String,
    val image: String?,
    val url: String
)

data class LfmArtistInfo(
    val name: String,
    val listeners: Long,
    val playcount: Long,
    val userPlays: Long,
    val bio: String,
    val tags: List<String>,
    val similar: List<LfmItem>,
    val url: String
)

/** Periodos de las estadísticas de Last.fm. */
enum class LfmPeriod(val api: String) { WEEK("7day"), MONTH("1month"), QUARTER("3month"), HALF("6month"), YEAR("12month"), ALL("overall") }

/**
 * Conexión con Last.fm: inicio de sesión, "escuchando ahora", scrobbles (con cola si no hay internet),
 * me gusta → "Loved tracks" y lectura de tus estadísticas.
 * La contraseña nunca se guarda: solo la clave de sesión que devuelve Last.fm.
 */
@OptIn(FlowPreview::class)
class LastFm(context: Context, private val favorites: FavoritesStore) {

    private val prefs = context.getSharedPreferences("lastfm", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Mutex()

    private val _user = MutableStateFlow(prefs.getString(K_USER, null))
    /** Usuario conectado (null = sin conectar). */
    val user: StateFlow<String?> = _user.asStateFlow()

    private val _scrobbling = MutableStateFlow(prefs.getBoolean(K_SCROBBLE, true))
    val scrobbling: StateFlow<Boolean> = _scrobbling.asStateFlow()

    private val _loveSync = MutableStateFlow(prefs.getBoolean(K_LOVE, true))
    val loveSync: StateFlow<Boolean> = _loveSync.asStateFlow()

    private val _pending = MutableStateFlow(loadQueue().length())
    /** Scrobbles esperando a enviarse (sin conexión). */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    // ------------------------------------------------------------------ clave de API

    /** Clave propia (si el usuario puso una) o la que viene con la app. */
    val apiKey: String get() = prefs.getString(K_KEY, null).orEmpty().ifBlank { BuildConfig.LASTFM_KEY }
    private val secret: String get() = prefs.getString(K_SECRET, null).orEmpty().ifBlank { BuildConfig.LASTFM_SECRET }
    val hasKey: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank()
    val builtInKey: Boolean get() = BuildConfig.LASTFM_KEY.isNotBlank() && BuildConfig.LASTFM_SECRET.isNotBlank()
    fun customKey(): Pair<String, String> = prefs.getString(K_KEY, "").orEmpty() to prefs.getString(K_SECRET, "").orEmpty()

    fun setCustomKey(key: String, secret: String) {
        prefs.edit().putString(K_KEY, key.trim()).putString(K_SECRET, secret.trim()).apply()
    }

    // ------------------------------------------------------------------ sesión

    suspend fun login(username: String, password: String) {
        val r = call(
            "auth.getMobileSession",
            mapOf("username" to username.trim(), "password" to password),
            signed = true, post = true
        )
        val s = r.getJSONObject("session")
        prefs.edit().putString(K_SK, s.getString("key")).putString(K_USER, s.getString("name")).apply()
        // Desde ahora, los me gusta nuevos van a Last.fm (los de antes, solo si se piden con "enviar todos").
        lovedSnap = currentLikes()
        saveLoved()
        _user.value = s.getString("name")
        flush()
    }

    fun logout() {
        prefs.edit().remove(K_SK).remove(K_USER).remove(K_QUEUE).remove(K_LOVED).apply()
        _user.value = null
        _pending.value = 0
    }

    fun setScrobbling(on: Boolean) { _scrobbling.value = on; prefs.edit().putBoolean(K_SCROBBLE, on).apply() }
    fun setLoveSync(on: Boolean) { _loveSync.value = on; prefs.edit().putBoolean(K_LOVE, on).apply() }

    private val sk: String? get() = prefs.getString(K_SK, null)
    private val active: Boolean get() = sk != null && hasKey

    // ------------------------------------------------------------------ escuchando / scrobbles

    fun nowPlaying(artist: String, title: String, album: String, durationMs: Long) {
        if (!active || !_scrobbling.value || artist.isBlank() || title.isBlank()) return
        scope.launch {
            runCatching {
                call("track.updateNowPlaying", buildMap {
                    put("artist", artist); put("track", title)
                    if (album.isNotBlank()) put("album", album)
                    if (durationMs > 0) put("duration", (durationMs / 1000).toString())
                }, signed = true, post = true)
            }
        }
    }

    /** Guarda un scrobble en la cola y la envía (si no hay internet, se reintenta luego). */
    fun scrobble(artist: String, title: String, album: String, durationMs: Long, startedAt: Long) {
        if (!active || !_scrobbling.value || artist.isBlank() || title.isBlank()) return
        scope.launch {
            queueLock.withLock {
                val q = loadQueue()
                q.put(JSONObject().put("a", artist).put("t", title).put("al", album).put("d", durationMs / 1000).put("ts", startedAt / 1000))
                // Last.fm no acepta scrobbles de hace más de 14 días: la cola no crece sin fin.
                while (q.length() > 2000) q.remove(0)
                saveQueue(q)
            }
            flush()
        }
    }

    /** Envía los scrobbles pendientes, de 50 en 50. */
    fun flush() {
        if (!active) return
        scope.launch {
            queueLock.withLock {
                var q = loadQueue()
                while (q.length() > 0) {
                    val n = minOf(50, q.length())
                    val params = HashMap<String, String>()
                    for (i in 0 until n) {
                        val o = q.getJSONObject(i)
                        params["artist[$i]"] = o.getString("a")
                        params["track[$i]"] = o.getString("t")
                        params["timestamp[$i]"] = o.getLong("ts").toString()
                        o.optString("al").takeIf { it.isNotBlank() }?.let { params["album[$i]"] = it }
                        o.optLong("d").takeIf { it > 0 }?.let { params["duration[$i]"] = it.toString() }
                    }
                    val ok = runCatching { call("track.scrobble", params, signed = true, post = true) }
                    if (ok.isFailure) {
                        val e = ok.exceptionOrNull()
                        // Sesión inválida: se deja de intentar hasta que se vuelva a conectar.
                        if (e is LastFmException && (e.code == 9 || e.code == 4)) logout()
                        // Datos rechazados (p. ej. demasiado antiguos): se descartan esos y se sigue.
                        if (e is LastFmException && e.code == 6) { q = drop(q, n); saveQueue(q); continue }
                        break
                    }
                    q = drop(q, n)
                    saveQueue(q)
                }
            }
        }
    }

    private fun drop(q: JSONArray, n: Int): JSONArray {
        val out = JSONArray()
        for (i in n until q.length()) out.put(q.get(i))
        return out
    }

    private fun loadQueue(): JSONArray = runCatching { JSONArray(prefs.getString(K_QUEUE, null) ?: "[]") }.getOrElse { JSONArray() }
    private fun saveQueue(q: JSONArray) {
        prefs.edit().putString(K_QUEUE, q.toString()).apply()
        _pending.value = q.length()
    }

    // ------------------------------------------------------------------ me gusta → Loved

    @Volatile private var songs: Map<Long, Song> = emptyMap()
    private var lovedSnap: Set<Long> = runCatching {
        val a = JSONArray(prefs.getString(K_LOVED, null) ?: "[]"); List(a.length()) { a.getLong(it) }.toSet()
    }.getOrDefault(emptySet())
    private var watching = false

    fun setLibrary(list: List<Song>) {
        songs = list.associateBy { it.id }
        if (watching) return
        watching = true
        scope.launch {
            favorites.marks.debounce(1200).collect { pushLoves() }
        }
    }

    private fun currentLikes(): Set<Long> = favorites.marks.value.filter { it.value.liked }.keys

    private suspend fun pushLoves() {
        if (!active || !_loveSync.value || songs.isEmpty()) return
        val now = currentLikes()
        for (id in now - lovedSnap) {
            val s = songs[id] ?: continue
            if (runCatching { call("track.love", mapOf("artist" to s.artist, "track" to s.title), signed = true, post = true) }.isFailure) break
            lovedSnap = lovedSnap + id
        }
        for (id in lovedSnap - now) {
            val s = songs[id] ?: run { lovedSnap = lovedSnap - id; null } ?: continue
            if (runCatching { call("track.unlove", mapOf("artist" to s.artist, "track" to s.title), signed = true, post = true) }.isFailure) break
            lovedSnap = lovedSnap - id
        }
        saveLoved()
    }

    /** Marca en Last.fm todas las canciones con me gusta (las que ya tenías antes de conectar). */
    suspend fun sendAllLikes(): Int {
        lovedSnap = emptySet()
        pushLoves()
        return lovedSnap.size
    }

    private fun saveLoved() { prefs.edit().putString(K_LOVED, JSONArray(lovedSnap.toList()).toString()).apply() }

    // ------------------------------------------------------------------ estadísticas

    suspend fun profile(): LfmProfile {
        val u = call("user.getInfo", mapOf("user" to (_user.value ?: ""))).getJSONObject("user")
        return LfmProfile(
            name = u.optString("name"),
            realName = u.optString("realname"),
            playcount = u.optString("playcount").toLongOrNull() ?: 0,
            artists = u.optString("artist_count").toLongOrNull() ?: 0,
            tracks = u.optString("track_count").toLongOrNull() ?: 0,
            albums = u.optString("album_count").toLongOrNull() ?: 0,
            registered = u.optJSONObject("registered")?.optString("unixtime")?.toLongOrNull()?.times(1000) ?: 0,
            country = u.optString("country").takeIf { it != "None" }.orEmpty(),
            image = image(u.optJSONArray("image")),
            url = u.optString("url")
        )
    }

    suspend fun topArtists(period: LfmPeriod, limit: Int = 50): List<LfmItem> =
        list(call("user.getTopArtists", userParams(period, limit)).optJSONObject("topartists"), "artist").map {
            LfmItem(it.optString("name"), plays = it.optInt("playcount"), image = image(it.optJSONArray("image")), url = it.optString("url"))
        }

    suspend fun topTracks(period: LfmPeriod, limit: Int = 50): List<LfmItem> =
        list(call("user.getTopTracks", userParams(period, limit)).optJSONObject("toptracks"), "track").map {
            LfmItem(it.optString("name"), artistName(it), it.optInt("playcount"), image(it.optJSONArray("image")), it.optString("url"))
        }

    suspend fun topAlbums(period: LfmPeriod, limit: Int = 50): List<LfmItem> =
        list(call("user.getTopAlbums", userParams(period, limit)).optJSONObject("topalbums"), "album").map {
            LfmItem(it.optString("name"), artistName(it), it.optInt("playcount"), image(it.optJSONArray("image")), it.optString("url"))
        }

    suspend fun recent(limit: Int = 50): List<LfmItem> =
        list(call("user.getRecentTracks", mapOf("user" to (_user.value ?: ""), "limit" to limit.toString())).optJSONObject("recenttracks"), "track").map {
            LfmItem(
                it.optString("name"), artistName(it), 0, image(it.optJSONArray("image")), it.optString("url"),
                date = it.optJSONObject("date")?.optString("uts")?.toLongOrNull()?.times(1000) ?: 0,
                nowPlaying = it.optJSONObject("@attr")?.optString("nowplaying") == "true"
            )
        }

    /**
     * Escuchas por día de los últimos [days] días (el último es hoy).
     *
     * En vez de bajar todas las escuchas de la semana (varias páginas grandes, una tras otra), se pide
     * a Last.fm solo el total de cada día (una consulta mínima por día, varias a la vez).
     */
    suspend fun scrobblesPerDay(days: Int = 7): IntArray = coroutineScope {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val starts = LongArray(days + 1)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        for (i in 0..days) {
            starts[i] = cal.timeInMillis / 1000
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val gate = kotlinx.coroutines.sync.Semaphore(4) // Last.fm pide no más de unas pocas a la vez
        (0 until days).map { i ->
            async {
                gate.withPermit {
                    val root = call(
                        "user.getRecentTracks",
                        mapOf(
                            "user" to (_user.value ?: ""), "limit" to "1",
                            "from" to starts[i].toString(), "to" to (starts[i + 1] - 1).toString()
                        )
                    ).optJSONObject("recenttracks")
                    root?.optJSONObject("@attr")?.optString("total")?.toIntOrNull() ?: 0
                }
            }
        }.awaitAll().toIntArray()
    }

    suspend fun loved(limit: Int = 50): List<LfmItem> =
        list(call("user.getLovedTracks", mapOf("user" to (_user.value ?: ""), "limit" to limit.toString())).optJSONObject("lovedtracks"), "track").map {
            LfmItem(
                it.optString("name"), artistName(it), 0, image(it.optJSONArray("image")), it.optString("url"),
                date = it.optJSONObject("date")?.optString("uts")?.toLongOrNull()?.times(1000) ?: 0
            )
        }

    suspend fun artistInfo(name: String, lang: String): LfmArtistInfo {
        val a = call("artist.getInfo", buildMap {
            put("artist", name); put("autocorrect", "1"); put("lang", lang)
            _user.value?.let { put("username", it) }
        }).getJSONObject("artist")
        val st = a.optJSONObject("stats")
        val similar = runCatching {
            list(call("artist.getSimilar", mapOf("artist" to name, "limit" to "12", "autocorrect" to "1")).optJSONObject("similarartists"), "artist")
                .map { LfmItem(it.optString("name"), image = image(it.optJSONArray("image")), url = it.optString("url")) }
        }.getOrDefault(emptyList())
        return LfmArtistInfo(
            name = a.optString("name"),
            listeners = st?.optString("listeners")?.toLongOrNull() ?: 0,
            playcount = st?.optString("playcount")?.toLongOrNull() ?: 0,
            userPlays = st?.optString("userplaycount")?.toLongOrNull() ?: 0,
            bio = cleanBio(a.optJSONObject("bio")?.optString("summary").orEmpty()),
            tags = list(a.optJSONObject("tags"), "tag").map { it.optString("name") },
            similar = similar,
            url = a.optString("url")
        )
    }

    private fun userParams(period: LfmPeriod, limit: Int) =
        mapOf("user" to (_user.value ?: ""), "period" to period.api, "limit" to limit.toString())

    private fun artistName(o: JSONObject): String =
        o.optJSONObject("artist")?.let { it.optString("name").ifBlank { it.optString("#text") } } ?: o.optString("artist")

    /** Last.fm devuelve un objeto suelto (en vez de lista) cuando hay un solo resultado. */
    private fun list(parent: JSONObject?, key: String): List<JSONObject> {
        parent ?: return emptyList()
        parent.optJSONArray(key)?.let { a -> return List(a.length()) { a.getJSONObject(it) } }
        return parent.optJSONObject(key)?.let { listOf(it) } ?: emptyList()
    }

    private fun image(a: JSONArray?): String? {
        a ?: return null
        for (size in listOf("extralarge", "large", "medium")) {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val u = o.optString("#text")
                // La estrella gris es la imagen "vacía" de Last.fm: mejor no mostrarla.
                if (o.optString("size") == size && u.isNotBlank() && !u.contains("2a96cbd8b46e442fc41c2b86b821562f")) return u
            }
        }
        return null
    }

    private fun cleanBio(raw: String): String =
        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("Read more on Last\\.fm.*$", RegexOption.DOT_MATCHES_ALL), "")
            .trim()

    // ------------------------------------------------------------------ llamadas

    private suspend fun call(method: String, extra: Map<String, String>, signed: Boolean = false, post: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            if (!hasKey) throw LastFmException(-1, "no key")
            val params = HashMap(extra)
            params["method"] = method
            params["api_key"] = apiKey
            if (signed) {
                if (method != "auth.getMobileSession") params["sk"] = sk ?: throw LastFmException(9, "no session")
                params["api_sig"] = sign(params)
            }
            params["format"] = "json"
            val body = params.entries.joinToString("&") { (k, v) -> Uri.encode(k) + "=" + Uri.encode(v) }
            val conn = (if (post) URL(BASE) else URL("$BASE?$body")).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", "LunaPlayer/${BuildConfig.VERSION_NAME}")
                if (post) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream)
                    .bufferedReader().use { it.readText() }
                val json = runCatching { JSONObject(text) }.getOrElse { throw LastFmException(code, "HTTP $code") }
                if (json.has("error")) throw LastFmException(json.optInt("error"), json.optString("message"))
                json
            } finally {
                conn.disconnect()
            }
        }

    private fun sign(params: Map<String, String>): String {
        val sb = StringBuilder()
        for (k in params.keys.sorted()) sb.append(k).append(params[k])
        sb.append(secret)
        return MessageDigest.getInstance("MD5").digest(sb.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BASE = "https://ws.audioscrobbler.com/2.0/"
        const val K_KEY = "api_key"
        const val K_SECRET = "api_secret"
        const val K_SK = "session"
        const val K_USER = "user"
        const val K_SCROBBLE = "scrobble"
        const val K_LOVE = "love"
        const val K_QUEUE = "queue"
        const val K_LOVED = "loved"
    }
}
