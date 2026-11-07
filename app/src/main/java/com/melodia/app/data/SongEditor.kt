package com.melodia.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Datos nuevos de una canción del teléfono. */
data class SongEdit(
    val song: Song,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String
)

/**
 * Edita la información (título, artista, álbum, género) de una canción del teléfono en la
 * biblioteca de música de Android. Necesita el permiso que pide el sistema para esa canción.
 */
object SongEditor {

    /** Lanza SecurityException si el sistema aún no dio permiso de escritura sobre la canción. */
    fun apply(context: Context, e: SongEdit) {
        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, e.title.trim())
            put(MediaStore.Audio.Media.ARTIST, e.artist.trim())
            put(MediaStore.Audio.Media.ALBUM, e.album.trim())
        }
        resolver.update(e.song.uri, cv, null, null)
        // El género no se puede escribir en todos los teléfonos: se intenta aparte.
        if (Build.VERSION.SDK_INT >= 30 && e.genre.trim() != e.song.genre) {
            runCatching {
                val g = ContentValues().apply { put(MediaStore.Audio.Media.GENRE, e.genre.trim()) }
                resolver.update(e.song.uri, g, null, null)
            }
        }
    }
}
