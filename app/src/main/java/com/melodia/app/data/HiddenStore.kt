package com.melodia.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Qué se puede ocultar de la biblioteca. */
enum class HiddenType { SONG, ALBUM, ARTIST, FOLDER }

/**
 * Algo oculto. La clave es estable (no depende del número que Android da a cada canción), así que
 * sigue oculto tras reinstalar y viaja en la copia de seguridad.
 */
data class HiddenItem(
    val type: HiddenType,
    val key: String,
    val title: String,
    val subtitle: String,
    val at: Long
)

/**
 * Canciones, álbumes, artistas y carpetas que el usuario ocultó. No se borra nada: solo dejan de
 * aparecer en la biblioteca, Inicio, búsquedas y mezclas. Se vuelven a mostrar desde Ajustes → Ocultos.
 */
class HiddenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(decode(prefs.getString(KEY, null)))
    val items: StateFlow<List<HiddenItem>> = _items.asStateFlow()

    fun isHidden(type: HiddenType, key: String) = _items.value.any { it.type == type && it.key == key }

    fun hide(item: HiddenItem) {
        if (isHidden(item.type, item.key)) return
        save(_items.value + item)
    }

    fun unhide(item: HiddenItem) = save(_items.value.filterNot { it.type == item.type && it.key == item.key })

    fun unhideAll() = save(emptyList())

    private fun save(list: List<HiddenItem>) {
        _items.value = list
        prefs.edit().putString(KEY, encode(list)).apply()
    }

    /** Quita de la lista todo lo oculto (las carpetas incluyen sus subcarpetas). */
    fun apply(all: List<Song>): List<Song> = filter(all, _items.value)

    companion object {
        const val PREFS = "hidden"
        const val KEY = "items_v1"

        fun songItem(s: Song) = HiddenItem(HiddenType.SONG, Backup.songKey(s), s.title, s.artist, System.currentTimeMillis())
        fun albumKey(title: String, artist: String) = Backup.norm(title) + "|" + Backup.norm(artist)
        fun albumItem(a: Album) = HiddenItem(HiddenType.ALBUM, albumKey(a.title, a.artist), a.title, a.artist, System.currentTimeMillis())
        fun artistItem(name: String) = HiddenItem(HiddenType.ARTIST, Backup.norm(name), name, "", System.currentTimeMillis())
        fun folderItem(f: Folder) = HiddenItem(HiddenType.FOLDER, f.path, f.name, f.displayPath, System.currentTimeMillis())

        fun filter(all: List<Song>, hidden: List<HiddenItem>): List<Song> {
            if (hidden.isEmpty()) return all
            val songs = HashSet<String>(); val artists = HashSet<String>()
            val albums = HashSet<String>(); val folders = ArrayList<String>()
            for (h in hidden) when (h.type) {
                HiddenType.SONG -> songs += h.key
                HiddenType.ARTIST -> artists += h.key
                HiddenType.ALBUM -> albums += h.key
                HiddenType.FOLDER -> folders += h.key
            }
            fun inHiddenFolder(path: String): Boolean {
                if (folders.isEmpty()) return false
                val dir = path.substringBeforeLast('/', "")
                return folders.any { dir == it || dir.startsWith("$it/") }
            }
            var out = all.filter { s ->
                !(songs.isNotEmpty() && Backup.songKey(s) in songs) &&
                    !(artists.isNotEmpty() && Backup.norm(s.artist) in artists) &&
                    !inHiddenFolder(s.path)
            }
            if (albums.isNotEmpty()) {
                // El artista del álbum es el que más se repite en sus canciones (igual que en la biblioteca).
                val gone = out.groupBy { it.albumId }.filter { (_, l) ->
                    val artist = l.groupingBy { it.artist }.eachCount().maxBy { it.value }.key
                    albumKey(l.first().album, artist) in albums
                }.keys
                if (gone.isNotEmpty()) out = out.filter { it.albumId !in gone }
            }
            return out
        }

        fun encode(list: List<HiddenItem>): String = JSONArray().apply {
            for (h in list) put(
                JSONObject().put("t", h.type.name).put("k", h.key).put("n", h.title).put("s", h.subtitle).put("at", h.at)
            )
        }.toString()

        fun decode(raw: String?): List<HiddenItem> = runCatching {
            val a = JSONArray(raw ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val type = runCatching { HiddenType.valueOf(o.optString("t")) }.getOrNull() ?: return@mapNotNull null
                val key = o.optString("k").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                HiddenItem(type, key, o.optString("n"), o.optString("s"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}
