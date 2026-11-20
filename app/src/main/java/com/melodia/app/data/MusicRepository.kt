package com.melodia.app.data

import com.melodia.app.Str
import com.melodia.app.R

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee la música del dispositivo con MediaStore. */
class MusicRepository(private val resolver: ContentResolver) {

    private val albumArtBase = Uri.parse("content://media/external/audio/albumart")

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val withGenre = android.os.Build.VERSION.SDK_INT >= 30
        val projection = listOfNotNull(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            if (withGenre) MediaStore.Audio.Media.GENRE else null,
            if (withGenre) MediaStore.Audio.Media.DISC_NUMBER else null
        ).toTypedArray()
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sort
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val genreCol = if (withGenre) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            val yearCol = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val discCol = if (withGenre) c.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                songs += Song(
                    id = id,
                    title = c.getString(titleCol)?.trim().orEmpty().ifBlank { Str.get(R.string.untitled) },
                    artist = normalizeArtist(c.getString(artistCol)),
                    album = c.getString(albumCol)?.trim().orEmpty().ifBlank { UNKNOWN_ALBUM },
                    albumId = albumId,
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ),
                    artUri = ContentUris.withAppendedId(albumArtBase, albumId),
                    path = c.getString(dataCol).orEmpty(),
                    sizeBytes = c.getLong(sizeCol),
                    genre = if (genreCol >= 0) c.getString(genreCol)?.trim().orEmpty() else "",
                    year = if (yearCol >= 0) c.getInt(yearCol).let { y -> if (y in 1000..2999) y else 0 } else 0,
                    track = trackOf(
                        if (trackCol >= 0) c.getInt(trackCol) else 0,
                        if (discCol >= 0) c.getString(discCol) else null
                    )
                )
            }
        }
        songs
    }

    /**
     * Android guarda la pista como "disco × 1000 + pista" (1003 = disco 1, pista 3) o solo la pista;
     * en Android 11+ el disco también viene aparte ("2" o "2/3").
     */
    private fun trackOf(raw: Int, discRaw: String?): Int {
        if (raw <= 0) return 0
        if (raw >= 1000) return trackOrder(raw / 1000, raw % 1000)
        val disc = discRaw?.trim()?.substringBefore('/')?.trim()?.toIntOrNull() ?: 1
        return trackOrder(disc, raw)
    }

    private fun normalizeArtist(raw: String?): String {
        val name = raw?.trim().orEmpty()
        // Android devuelve "<unknown>" cuando falta el metadato.
        return if (name.isBlank() || name.equals("<unknown>", ignoreCase = true)) UNKNOWN_ARTIST
        else name
    }

    fun groupAlbums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (albumId, list) ->
                val first = list.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = list.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
                    artUri = first.artUri,
                    songs = list.sortedWith(AlbumOrder)
                )
            }
            .sortedBy { it.title.lowercase() }

    fun groupArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (name, list) ->
                val albums = groupAlbums(list)
                Artist(
                    name = name,
                    artUri = albums.firstOrNull()?.artUri,
                    albums = albums,
                    songs = list.sortedBy { it.title.lowercase() }
                )
            }
            .sortedBy { it.name.lowercase() }

    /** Agrupa las canciones por la carpeta donde están guardadas. */
    fun groupFolders(songs: List<Song>): List<Folder> =
        songs.groupBy { it.path.substringBeforeLast('/', "") }
            .map { (folderPath, list) ->
                Folder(
                    id = folderPath.hashCode(),
                    path = folderPath,
                    name = folderPath.substringAfterLast('/').ifBlank { Str.get(R.string.no_folder) },
                    displayPath = prettyPath(folderPath),
                    artUri = list.firstOrNull()?.artUri,
                    // Dentro de una carpeta: por álbum y en el orden real de cada álbum.
                    songs = list.sortedWith(compareBy<Song> { it.album.lowercase() }.then(AlbumOrder))
                )
            }
            .sortedBy { it.name.lowercase() }

    private fun prettyPath(path: String): String =
        path.replace(Regex("^/storage/emulated/\\d+/?"), "")
            .replace(Regex("^/sdcard/?"), "")
            .ifBlank { Str.get(R.string.internal_storage) }
}
