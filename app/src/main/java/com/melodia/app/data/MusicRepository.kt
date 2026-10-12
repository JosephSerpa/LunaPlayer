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
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )
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
                    sizeBytes = c.getLong(sizeCol)
                )
            }
        }
        songs
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
                    songs = list
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
                    songs = list.sortedBy { it.title.lowercase() }
                )
            }
            .sortedBy { it.name.lowercase() }

    private fun prettyPath(path: String): String =
        path.replace(Regex("^/storage/emulated/\\d+/?"), "")
            .replace(Regex("^/sdcard/?"), "")
            .ifBlank { Str.get(R.string.internal_storage) }
}
