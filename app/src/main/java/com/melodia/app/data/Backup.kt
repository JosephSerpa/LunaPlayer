package com.melodia.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.Normalizer

/**
 * Copia de seguridad "todo en uno" en un archivo .json: me gusta, estrellas, mejor parte, listas,
 * reproducciones, ecualizador (con perfiles y dispositivos), efectos, widgets de inicio y ajustes.
 *
 * Las canciones del teléfono tienen un número distinto en cada dispositivo (y tras reinstalar), así que
 * en el archivo cada canción va con una clave estable: título + artista + álbum + duración. Al importar
 * se busca la misma canción en la biblioteca de ese dispositivo. Las del servidor van por su id del servidor.
 *
 * No se guardan los datos de conexión del servidor ni de Last.fm.
 */
object Backup {

    private const val FORMAT = "lunaplayer-backup"
    private const val VERSION = 1

    /** Archivos de ajustes que se copian tal cual. */
    private val PLAIN_PREFS = listOf("equalizer", "effects", "ui", "language")

    data class Result(val songs: Int, val matched: Int, val playlists: Int)

    // ------------------------------------------------------------------ claves estables

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")

    /** Clave estable de una canción (la misma en cualquier dispositivo). */
    fun songKey(s: Song): String =
        if (s.remoteId != null) "r:" + s.remoteId
        else "l:" + norm(s.title) + "|" + norm(s.artist) + "|" + norm(s.album) + "|" + (s.durationMs / 1000)

    /** Clave más suelta (sin álbum ni duración) para cuando la exacta no aparece. */
    private fun looseKey(s: Song): String = "l:" + norm(s.title) + "|" + norm(s.artist)
    private fun looseOf(key: String): String? =
        if (!key.startsWith("l:")) null else key.split('|').let { if (it.size >= 2) it[0] + "|" + it[1] else null }

    private fun albumKey(title: String, artist: String) = norm(title) + "|" + norm(artist)

    // ------------------------------------------------------------------ exportar

    fun export(context: Context, songs: List<Song>, out: Uri) {
        val byId = songs.associateBy { it.id }
        val root = JSONObject()
            .put("format", FORMAT).put("version", VERSION)
            .put("created", System.currentTimeMillis())

        val songMap = JSONObject()
        fun ref(id: Long): String? {
            val s = byId[id] ?: return null
            val k = songKey(s)
            songMap.put(id.toString(), k)
            return k
        }

        // Me gusta, estrellas y mejor parte
        val favPrefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val marks = JSONArray()
        runCatching {
            val m = JSONObject(favPrefs.getString("marks_v1", null) ?: "{}")
            for (id in m.keys()) {
                val k = ref(id.toLong()) ?: continue
                marks.put(m.getJSONObject(id).put("k", k))
            }
        }
        root.put("marks", marks)
        // Álbumes marcados (clave: título|artista normalizados)
        val albumOut = JSONArray()
        runCatching {
            val am = JSONObject(favPrefs.getString("album_marks_v1", null) ?: "{}")
            val albumsById = songs.groupBy { it.albumId }
            for (id in am.keys()) {
                val first = albumsById[id.toLong()]?.firstOrNull() ?: continue
                albumOut.put(am.getJSONObject(id).put("k", norm(first.album) + "|" + norm(first.artist)))
            }
        }
        root.put("albumMarks", albumOut)
        root.put("moon", favPrefs.getBoolean("moon_mode", false))

        // Listas
        val lists = JSONArray()
        runCatching {
            val arr = JSONArray(context.getSharedPreferences("playlists", Context.MODE_PRIVATE).getString("playlists_v1", null) ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("songs") ?: JSONArray()
                val keys = JSONArray()
                for (k in 0 until ids.length()) ref(ids.getLong(k))?.let { keys.put(it) }
                lists.put(JSONObject().put("name", o.optString("name")).put("songs", keys))
            }
        }
        root.put("playlists", lists)

        // Reproducciones
        val stats = JSONArray()
        for ((id, c) in readStats(context)) {
            val k = ref(id) ?: continue
            stats.put(JSONObject().put("k", k).put("n", c.first).put("t", c.second))
        }
        root.put("stats", stats)

        // Widgets de inicio (las canciones y álbumes fijados van por clave)
        val albums = songs.groupBy { it.albumId }.mapValues { (_, l) -> albumKey(l.first().album, l.first().artist) }
        val home = context.getSharedPreferences("home", Context.MODE_PRIVATE)
        val tiles = home.getString("tiles", "").orEmpty().split('\n').filter { it.isNotBlank() }.map { line ->
            val p = line.split('|').toMutableList()
            if (p.size >= 2) {
                val refRaw = Uri.decode(p[1])
                when (p[0]) {
                    "SONG" -> refRaw.toLongOrNull()?.let { ref(it) }?.let { p[1] = Uri.encode("@" + it) }
                    "ALBUM" -> refRaw.toLongOrNull()?.let { albums[it] }?.let { p[1] = Uri.encode("@" + it) }
                }
            }
            p.joinToString("|")
        }
        root.put("home", JSONObject().put("tiles", JSONArray(tiles)).put("prefs", dump(home)))

        // Ajustes
        val prefs = JSONObject()
        for (name in PLAIN_PREFS) prefs.put(name, dump(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        root.put("prefs", prefs)
        root.put("songsIndex", songMap.length())

        context.contentResolver.openOutputStream(out, "wt")!!.use { it.write(root.toString(1).toByteArray(Charsets.UTF_8)) }
    }

    // ------------------------------------------------------------------ importar

    /**
     * Junta la copia con lo que ya hay: los me gusta, estrellas y listas del archivo se suman (si una lista
     * se llama igual, se juntan sus canciones) y los ajustes se reemplazan. Después hay que reiniciar la app.
     */
    fun import(context: Context, songs: List<Song>, input: Uri): Result {
        val text = context.contentResolver.openInputStream(input)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "format" }

        val exact = HashMap<String, Long>()
        val loose = HashMap<String, Long>()
        for (s in songs) {
            exact.putIfAbsent(songKey(s), s.id)
            if (s.remoteId == null) loose.putIfAbsent(looseKey(s), s.id)
        }
        var seen = 0; var matched = 0
        val cache = HashMap<String, Long?>()
        fun find(k: String): Long? = cache.getOrPut(k) {
            seen++
            (exact[k] ?: looseOf(k)?.let { loose[it] })?.also { matched++ }
        }

        // Me gusta, estrellas y mejor parte: lo del archivo manda sobre la misma canción.
        val favPrefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val cur = runCatching { JSONObject(favPrefs.getString("marks_v1", null) ?: "{}") }.getOrElse { JSONObject() }
        val marks = root.optJSONArray("marks") ?: JSONArray()
        for (i in 0 until marks.length()) {
            val o = marks.getJSONObject(i)
            val id = find(o.optString("k")) ?: continue
            val prev = cur.optJSONObject(id.toString())
            val liked = o.optInt("l") == 1 || prev?.optInt("l") == 1
            val rating = o.optInt("r").takeIf { it > 0 } ?: prev?.optInt("r") ?: 0
            val best = o.optLong("b", -1L).takeIf { it >= 0 } ?: prev?.optLong("b", -1L) ?: -1L
            cur.put(id.toString(), JSONObject().put("l", if (liked) 1 else 0).put("r", rating).put("b", best))
        }
        val curAl = runCatching { JSONObject(favPrefs.getString("album_marks_v1", null) ?: "{}") }.getOrElse { JSONObject() }
        val alKeys = HashMap<String, Long>()
        for (sg in songs) alKeys[norm(sg.album) + "|" + norm(sg.artist)] = sg.albumId
        val alIn = root.optJSONArray("albumMarks") ?: JSONArray()
        for (i in 0 until alIn.length()) {
            val o = alIn.getJSONObject(i)
            val id = alKeys[o.optString("k")] ?: continue
            val prev = curAl.optJSONObject(id.toString())
            val liked = o.optInt("l") == 1 || prev?.optInt("l") == 1
            val rating = o.optInt("r").takeIf { it > 0 } ?: prev?.optInt("r") ?: 0
            curAl.put(id.toString(), JSONObject().put("l", if (liked) 1 else 0).put("r", rating))
        }
        favPrefs.edit().putString("album_marks_v1", curAl.toString()).putString("marks_v1", cur.toString())
            .putBoolean("moon_mode", root.optBoolean("moon", favPrefs.getBoolean("moon_mode", false))).commit()

        // Listas: las de mismo nombre se juntan; las nuevas se agregan.
        val plPrefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)
        val curLists = runCatching { JSONArray(plPrefs.getString("playlists_v1", null) ?: "[]") }.getOrElse { JSONArray() }
        val byName = HashMap<String, JSONObject>()
        for (i in 0 until curLists.length()) curLists.getJSONObject(i).let { byName[it.optString("name")] = it }
        val lists = root.optJSONArray("playlists") ?: JSONArray()
        var nextId = System.currentTimeMillis()
        for (i in 0 until lists.length()) {
            val o = lists.getJSONObject(i)
            val name = o.optString("name")
            if (name.isBlank()) continue
            val keys = o.optJSONArray("songs") ?: JSONArray()
            val ids = (0 until keys.length()).mapNotNull { find(keys.getString(it)) }
            val target = byName[name]
            if (target != null) {
                val have = target.optJSONArray("songs") ?: JSONArray()
                val list = (0 until have.length()).map { have.getLong(it) }.toMutableList()
                for (id in ids) if (id !in list) list += id
                target.put("songs", JSONArray(list))
            } else {
                val n = JSONObject().put("id", nextId++).put("name", name).put("songs", JSONArray(ids.distinct()))
                curLists.put(n); byName[name] = n
            }
        }
        plPrefs.edit().putString("playlists_v1", curLists.toString()).commit()

        // Reproducciones: se queda el mayor número de cada canción (importar dos veces no las duplica).
        val stats = readStats(context).toMutableMap()
        val st = root.optJSONArray("stats") ?: JSONArray()
        for (i in 0 until st.length()) {
            val o = st.getJSONObject(i)
            val id = find(o.optString("k")) ?: continue
            val old = stats[id]
            stats[id] = maxOf(old?.first ?: 0, o.optInt("n")) to maxOf(old?.second ?: 0L, o.optLong("t"))
        }
        writeStats(context, stats)

        // Widgets de inicio
        root.optJSONObject("home")?.let { h ->
            val albumIds = songs.groupBy { it.albumId }.entries.associate { (id, l) -> albumKey(l.first().album, l.first().artist) to id }
            val arr = h.optJSONArray("tiles") ?: JSONArray()
            val tiles = (0 until arr.length()).mapNotNull { idx ->
                val p = arr.getString(idx).split('|').toMutableList()
                if (p.size >= 2) {
                    val r = Uri.decode(p[1])
                    if (r.startsWith("@")) {
                        val k = r.substring(1)
                        val id = when (p[0]) { "SONG" -> find(k); "ALBUM" -> albumIds[k]; else -> null } ?: return@mapNotNull null
                        p[1] = Uri.encode(id.toString())
                    }
                }
                p.joinToString("|")
            }
            val home = context.getSharedPreferences("home", Context.MODE_PRIVATE)
            restore(home, h.optJSONObject("prefs"))
            home.edit().putString("tiles", tiles.joinToString("\n")).putBoolean("custom", true).commit()
        }

        // Ajustes
        root.optJSONObject("prefs")?.let { p ->
            for (name in PLAIN_PREFS) restore(context.getSharedPreferences(name, Context.MODE_PRIVATE), p.optJSONObject(name))
        }
        return Result(seen, matched, lists.length())
    }

    /** Reinicia la app para que todo lo importado se cargue de nuevo. */
    fun restart(context: Context) {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    // ------------------------------------------------------------------ utilidades

    private fun dump(p: android.content.SharedPreferences): JSONObject {
        val o = JSONObject()
        for ((k, v) in p.all) {
            val e = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            } ?: continue
            o.put(k, e)
        }
        return o
    }

    private fun restore(p: android.content.SharedPreferences, o: JSONObject?) {
        o ?: return
        val ed = p.edit()
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "s" -> ed.putString(k, e.optString("v"))
                "b" -> ed.putBoolean(k, e.optBoolean("v"))
                "i" -> ed.putInt(k, e.optInt("v"))
                "l" -> ed.putLong(k, e.optLong("v"))
                "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
                "set" -> e.optJSONArray("v")?.let { a -> ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        ed.commit()
    }

    // Mismo formato que PlayStats (play_stats.bin).
    private fun readStats(context: Context): Map<Long, Pair<Int, Long>> = runCatching {
        val f = File(context.filesDir, "play_stats.bin")
        if (!f.exists()) return emptyMap()
        DataInputStream(f.inputStream().buffered()).use { i ->
            if (i.readInt() != 1) return emptyMap()
            val n = i.readInt()
            buildMap { repeat(n) { put(i.readLong(), i.readInt() to i.readLong()) } }
        }
    }.getOrDefault(emptyMap())

    private fun writeStats(context: Context, m: Map<Long, Pair<Int, Long>>) {
        val f = File(context.filesDir, "play_stats.bin")
        val tmp = File(context.filesDir, "play_stats.bin.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { o ->
            o.writeInt(1); o.writeInt(m.size)
            for ((id, c) in m) { o.writeLong(id); o.writeInt(c.first); o.writeLong(c.second) }
        }
        tmp.renameTo(f)
    }
}
