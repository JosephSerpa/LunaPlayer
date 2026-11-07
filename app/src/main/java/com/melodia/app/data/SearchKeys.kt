package com.melodia.app.data

import java.text.Normalizer

/** Claves de búsqueda precalculadas: minúsculas y sin acentos ("Canción" coincide con "cancion"). */
object SearchKeys {
    private val MARKS = Regex("\\p{Mn}+")

    fun norm(s: String): String {
        if (s.isEmpty()) return s
        val lower = s.lowercase()
        // Atajo: texto ASCII sin nada que quitar.
        if (lower.all { it.code < 128 }) return lower
        return MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
    }

    fun song(s: Song) = norm("${s.title} ${s.artist} ${s.album}")
}
