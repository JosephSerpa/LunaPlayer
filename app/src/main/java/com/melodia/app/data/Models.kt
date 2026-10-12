package com.melodia.app.data

import android.net.Uri
import com.melodia.app.R
import com.melodia.app.Str

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val artUri: Uri,
    val path: String = "",
    val sizeBytes: Long = 0L,
    /** Id en el servidor Subsonic/Navidrome; null si la canción está en el teléfono. */
    val remoteId: String? = null
) {
    val isRemote: Boolean get() = remoteId != null
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri,
    val songs: List<Song>
)

data class Artist(
    val name: String,
    val artUri: Uri?,
    val albums: List<Album>,
    val songs: List<Song>
)

data class Folder(
    val id: Int,
    val path: String,
    val name: String,
    val displayPath: String,
    val artUri: Uri?,
    val songs: List<Song>
)

val UNKNOWN_ARTIST: String get() = Str.get(R.string.unknown_artist)
val UNKNOWN_ALBUM: String get() = Str.get(R.string.unknown_album)

fun Long.formatTime(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
