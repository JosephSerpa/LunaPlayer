package com.melodia.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Marcas del usuario sobre una canción: "me gusta", de 0 a 5 estrellas y
 * [bestMs], el punto marcado como "la mejor parte" (-1 = sin marcar).
 */
data class SongMark(val liked: Boolean = false, val rating: Int = 0, val bestMs: Long = -1L) {
    val hasBest: Boolean get() = bestMs >= 0
}

/** Guarda en el teléfono los "me gusta" y las estrellas de cada canción. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _marks = MutableStateFlow(load())
    val marks: StateFlow<Map<Long, SongMark>> = _marks.asStateFlow()

    /** Modo luna: al cambiar de canción, empieza en su "mejor parte" si tiene una marcada. */
    private val _moon = MutableStateFlow(prefs.getBoolean(KEY_MOON, false))
    val moonMode: StateFlow<Boolean> = _moon.asStateFlow()

    fun setMoonMode(on: Boolean) {
        _moon.value = on
        prefs.edit().putBoolean(KEY_MOON, on).apply()
    }

    /** Dónde debe empezar [songId] con el modo luna: su mejor parte, o null si no aplica. */
    fun moonStartMs(songId: Long): Long? =
        if (_moon.value) _marks.value[songId]?.bestMs?.takeIf { it > 0 } else null

    fun toggleLike(songId: Long) = update(songId) { it.copy(liked = !it.liked) }

    /** Pone [stars] estrellas; si se toca la misma cantidad otra vez, se quitan. */
    fun setRating(songId: Long, stars: Int) = update(songId) {
        val s = stars.coerceIn(0, 5)
        it.copy(rating = if (it.rating == s) 0 else s)
    }

    /** Marca (o mueve) "la mejor parte" de la canción. */
    fun setBest(songId: Long, positionMs: Long) = update(songId) { it.copy(bestMs = positionMs.coerceAtLeast(0)) }

    fun clearBest(songId: Long) = update(songId) { it.copy(bestMs = -1L) }

    private fun update(songId: Long, change: (SongMark) -> SongMark) {
        val current = _marks.value[songId] ?: SongMark()
        val next = change(current)
        val map = _marks.value.toMutableMap()
        if (!next.liked && next.rating == 0 && !next.hasBest) map.remove(songId) else map[songId] = next
        _marks.value = map
        save(map)
    }

    private fun save(map: Map<Long, SongMark>) {
        val json = JSONObject()
        for ((id, m) in map) {
            json.put(id.toString(), JSONObject().put("l", if (m.liked) 1 else 0).put("r", m.rating).put("b", m.bestMs))
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): Map<Long, SongMark> = runCatching {
        val json = JSONObject(prefs.getString(KEY, null) ?: return@runCatching emptyMap())
        buildMap {
            for (key in json.keys()) {
                val o = json.getJSONObject(key)
                put(key.toLong(), SongMark(o.optInt("l") == 1, o.optInt("r").coerceIn(0, 5), o.optLong("b", -1L)))
            }
        }
    }.getOrDefault(emptyMap())

    private companion object {
        const val KEY = "marks_v1"
        const val KEY_MOON = "moon_mode"
    }
}
