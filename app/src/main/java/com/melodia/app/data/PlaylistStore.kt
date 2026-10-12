package com.melodia.app.data

import com.melodia.app.Str
import com.melodia.app.R

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Lista de reproducción creada por el usuario. */
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList()
)

/** Guarda las listas de reproducción en el teléfono. */
class PlaylistStore(context: Context) {

    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** Crea una lista (opcionalmente con canciones) y devuelve su id. */
    fun create(name: String, songIds: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        val clean = name.trim().ifBlank { Str.get(R.string.new_playlist) }
        update { it + Playlist(id, clean, songIds.distinct()) }
        return id
    }

    fun rename(id: Long, name: String) = edit(id) { it.copy(name = name.trim().ifBlank { it.name }) }

    fun delete(id: Long) = update { list -> list.filterNot { it.id == id } }

    /** Añade canciones al final (sin repetir las que ya estaban). */
    fun add(id: Long, songIds: List<Long>) = edit(id) { p ->
        p.copy(songIds = p.songIds + songIds.filterNot { it in p.songIds })
    }

    fun remove(id: Long, songId: Long) = edit(id) { p -> p.copy(songIds = p.songIds - songId) }

    private fun edit(id: Long, change: (Playlist) -> Playlist) =
        update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun update(change: (List<Playlist>) -> List<Playlist>) {
        val next = change(_playlists.value)
        _playlists.value = next
        val json = JSONArray()
        for (p in next) {
            json.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", JSONArray(p.songIds))
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<Playlist> = runCatching {
        val json = JSONArray(prefs.getString(KEY, null) ?: return@runCatching emptyList())
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            val songs = o.optJSONArray("songs") ?: JSONArray()
            Playlist(
                id = o.getLong("id"),
                name = o.optString("name"),
                songIds = List(songs.length()) { k -> songs.getLong(k) }
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "playlists_v1"
    }
}
