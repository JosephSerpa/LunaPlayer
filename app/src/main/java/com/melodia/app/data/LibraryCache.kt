package com.melodia.app.data

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Copia de la biblioteca en el teléfono (formato binario compacto, se lee en milisegundos).
 * Así las canciones aparecen al instante al abrir la app y luego se actualizan en segundo plano.
 */
class LibraryCache(context: Context) {

    private val dir = File(context.filesDir, "library").apply { mkdirs() }

    data class Snapshot(val songs: List<Song>, val savedAt: Long, val tag: String)

    fun read(name: String): Snapshot? = runCatching {
        val file = File(dir, "$name.bin")
        if (!file.exists()) return null
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { input ->
            if (input.readInt() != VERSION) return null
            val savedAt = input.readLong()
            val tag = input.readUTF()
            val count = input.readInt()
            val songs = ArrayList<Song>(count)
            repeat(count) {
                val id = input.readLong()
                val title = input.readUTF()
                val artist = input.readUTF()
                val album = input.readUTF()
                val albumId = input.readLong()
                val duration = input.readLong()
                val uri = Uri.parse(input.readUTF())
                val artUri = Uri.parse(input.readUTF())
                val path = input.readUTF()
                val size = input.readLong()
                val remote = input.readUTF().ifEmpty { null }
                songs += Song(id, title, artist, album, albumId, duration, uri, artUri, path, size, remote)
            }
            Snapshot(songs, savedAt, tag)
        }
    }.getOrNull()

    fun write(name: String, songs: List<Song>, tag: String = "") {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { out ->
                out.writeInt(VERSION)
                out.writeLong(System.currentTimeMillis())
                out.writeUTF(tag)
                out.writeInt(songs.size)
                for (s in songs) {
                    out.writeLong(s.id)
                    out.writeUTF(s.title.take(MAX))
                    out.writeUTF(s.artist.take(MAX))
                    out.writeUTF(s.album.take(MAX))
                    out.writeLong(s.albumId)
                    out.writeLong(s.durationMs)
                    out.writeUTF(s.uri.toString())
                    out.writeUTF(s.artUri.toString())
                    out.writeUTF(s.path.take(MAX))
                    out.writeLong(s.sizeBytes)
                    out.writeUTF(s.remoteId.orEmpty())
                }
            }
            // Se reemplaza de golpe: nunca queda un archivo a medio escribir.
            tmp.renameTo(File(dir, "$name.bin"))
        }
    }

    fun delete(name: String) {
        File(dir, "$name.bin").delete()
    }

    private companion object {
        const val VERSION = 1
        /** writeUTF admite hasta 64 KB; los textos se recortan por seguridad. */
        const val MAX = 4000
    }
}
