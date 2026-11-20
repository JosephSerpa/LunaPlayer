package com.melodia.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

/** Una línea de la letra. [timeMs] = -1 si la letra no tiene tiempos. */
data class LyricLine(val timeMs: Long, val text: String)

/** Letra de una canción. [synced]: tiene tiempos (se resalta la línea actual). */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String)

/**
 * Busca la letra de una canción, en este orden:
 * 1. archivo .lrc con el mismo nombre, junto a la canción (directo o con la carpeta que autorizó el usuario);
 * 2. letra dentro del archivo (MP3 USLT, FLAC LYRICS, M4A ©lyr);
 * 3. Navidrome / OpenSubsonic, si la canción viene del servidor.
 *
 * Solo se busca cuando se abren las letras, y el resultado queda en memoria.
 */
object LyricsRepository {

    private const val PREFS = "lyrics"
    private const val KEY_TREE = "tree"

    /** null = buscada y sin letra. */
    private val cache = LruCache<Long, Optional>(40)
    private class Optional(val value: Lyrics?)

    fun cached(songId: Long): Lyrics? = cache.get(songId)?.value
    fun isCached(songId: Long): Boolean = cache.get(songId) != null

    suspend fun load(context: Context, song: Song): Lyrics? {
        cache.get(song.id)?.let { return it.value }
        val result = withContext(Dispatchers.IO) {
            runCatching { find(context.applicationContext, song) }.getOrNull()
        }
        cache.put(song.id, Optional(result))
        return result
    }

    private suspend fun find(context: Context, song: Song): Lyrics? {
        if (!song.isRemote) {
            readSidecar(context, song.path)?.let { return parse(it, "lrc") }
            runCatching {
                context.contentResolver.openInputStream(song.uri)?.use { EmbeddedLyrics.read(it, song.path) }
            }.getOrNull()?.let { return parse(it, "tag") }
            return null
        }
        val cfg = (context as? com.melodia.app.MelodiaApp)?.serverSettings?.config?.value ?: return null
        if (!cfg.isConfigured) return null
        return com.melodia.app.data.server.SubsonicClient.lyrics(cfg, song.remoteId!!, song.artist, song.title)
    }

    // ------------------------------------------------------------------ .lrc junto a la canción

    /** Carpeta de música autorizada (Android 11+: los .lrc no son audio y no se pueden leer sin ella). */
    fun tree(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE, null) ?: return null
        val uri = Uri.parse(raw)
        val ok = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (ok) uri else null
    }

    fun setTree(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TREE, uri.toString()).apply()
        cache.evictAll()
    }

    /** ¿Conviene ofrecer autorizar una carpeta? (canción del teléfono, Android 11+ y sin carpeta aún). */
    fun needsTree(context: Context, song: Song): Boolean =
        !song.isRemote && android.os.Build.VERSION.SDK_INT >= 30 && tree(context) == null

    private fun readSidecar(context: Context, path: String): String? {
        if (path.isBlank() || !path.contains('.')) return null
        val base = path.substringBeforeLast('.')
        for (ext in listOf(".lrc", ".LRC")) {
            val f = File(base + ext)
            val text = runCatching { if (f.canRead()) f.readBytes().let(::decodeText) else null }.getOrNull()
            if (text != null) return text
        }
        val tree = tree(context) ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val volume = treeId.substringBefore(':')
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        val p = path.replace(Regex("^/sdcard/"), "/storage/emulated/0/")
        if (!p.startsWith("$root/")) return null
        val rel = p.removePrefix("$root/").substringBeforeLast('.')
        val treeBase = treeId.substringAfter(':', "")
        if (treeBase.isNotEmpty() && rel != treeBase && !rel.startsWith("$treeBase/")) return null
        for (ext in listOf(".lrc", ".LRC")) {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, "$volume:$rel$ext")
            val text = runCatching {
                context.contentResolver.openInputStream(doc)?.use { decodeText(it.readBytes()) }
            }.getOrNull()
            if (text != null) return text
        }
        return null
    }

    /** UTF-8 (con o sin BOM), UTF-16 con BOM; si no es UTF-8 válido, Latin-1. */
    private fun decodeText(b: ByteArray): String {
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        val start = if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) 3 else 0
        val dec = Charsets.UTF_8.newDecoder()
        return runCatching { dec.decode(java.nio.ByteBuffer.wrap(b, start, b.size - start)).toString() }
            .getOrElse { String(b, Charsets.ISO_8859_1) }
    }

    // ------------------------------------------------------------------ ajuste fino (por canción)

    fun offset(context: Context, song: Song): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("off:" + Backup.songKey(song), 0L)

    fun setOffset(context: Context, song: Song, ms: Long) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val k = "off:" + Backup.songKey(song)
        if (ms == 0L) e.remove(k) else e.putLong(k, ms)
        e.apply()
    }

    // ------------------------------------------------------------------ LRC

    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIME = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val META = Regex("""^\[(ar|ti|al|au|by|re|ve|length|id|#)\s*:.*]$""", RegexOption.IGNORE_CASE)
    private val OFFSET = Regex("""^\[offset\s*:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)

    /** Convierte texto (LRC o letra simple) en líneas. null si está vacía. */
    fun parse(raw: String, source: String): Lyrics? {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        var offset = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (line0 in text.split('\n')) {
            val line = line0.trim()
            OFFSET.find(line)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L; return@let }
            if (OFFSET.matches(line) || META.matches(line)) continue
            val tags = TIME.findAll(line).toList()
            if (tags.isNotEmpty() && line.startsWith("[")) {
                // Los tiempos van al principio: "[00:12.30][01:40.00]texto".
                var end = 0
                for (t in tags) { if (t.range.first == end) end = t.range.last + 1 else break }
                val body = line.substring(end).replace(WORD_TIME, "").trim()
                for (t in tags) {
                    if (t.range.last >= end) break
                    val m = t.groupValues[1].toLong(); val s = t.groupValues[2].toLong()
                    val fr = t.groupValues[3]
                    val ms = when (fr.length) { 0 -> 0L; 1 -> fr.toLong() * 100; 2 -> fr.toLong() * 10; else -> fr.take(3).toLong() }
                    timed += LyricLine((m * 60_000 + s * 1000 + ms - offset).coerceAtLeast(0), body)
                }
            } else {
                plain += line.replace(WORD_TIME, "")
            }
        }
        if (timed.isNotEmpty()) {
            timed.sortBy { it.timeMs }
            // Quita líneas vacías repetidas y las del final.
            val out = ArrayList<LyricLine>()
            for (l in timed) if (!(l.text.isEmpty() && out.lastOrNull()?.text?.isEmpty() == true)) out += l
            while (out.lastOrNull()?.text?.isEmpty() == true && out.size > 1) out.removeAt(out.size - 1)
            return Lyrics(out, true, source)
        }
        val lines = plain.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        if (lines.none { it.isNotBlank() }) return null
        return Lyrics(lines.map { LyricLine(-1L, it) }, false, source)
    }
}

/** Lee la letra guardada dentro del archivo de audio, sin librerías (solo cabeceras, no el audio). */
internal object EmbeddedLyrics {

    private const val MAX_BLOCK = 40 * 1024 * 1024

    fun read(input: InputStream, path: String): String? {
        val inp = DataInputStream(input.buffered(64 * 1024))
        inp.mark(16)
        val head = ByteArray(12)
        val n = inp.read(head)
        inp.reset()
        if (n < 8) return null
        return when {
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() -> id3(inp)
            head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() && head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> flac(inp)
            String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(inp)
            else -> null
        }
    }

    // --- MP3 (ID3v2.3 / 2.4): marco USLT ---
    private fun id3(inp: DataInputStream): String? {
        val h = ByteArray(10); inp.readFully(h)
        val major = h[3].toInt()
        if (major < 3) return null
        val flags = h[5].toInt()
        var remaining = syncsafe(h, 6).toLong()
        if (flags and 0x40 != 0) {
            val eh = ByteArray(4); inp.readFully(eh)
            val size = if (major == 4) syncsafe(eh, 0) - 4 else be32(eh, 0)
            skip(inp, size.toLong()); remaining -= 4 + size
        }
        val fh = ByteArray(10)
        while (remaining > 10) {
            inp.readFully(fh)
            remaining -= 10
            if (fh[0].toInt() == 0) return null
            val id = String(fh, 0, 4, Charsets.ISO_8859_1)
            val size = if (major == 4) syncsafe(fh, 4) else be32(fh, 4)
            if (size <= 0 || size > remaining) return null
            remaining -= size
            if (id == "USLT" && size < MAX_BLOCK) {
                val b = ByteArray(size); inp.readFully(b)
                return uslt(b)
            }
            skip(inp, size.toLong())
        }
        return null
    }

    private fun uslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        var i = 4 // codificación + idioma (3)
        val wide = enc == 1 || enc == 2
        // Salta la descripción (termina en 0 o en 00).
        if (wide) { while (i + 1 < b.size && !(b[i].toInt() == 0 && b[i + 1].toInt() == 0)) i += 2; i += 2 }
        else { while (i < b.size && b[i].toInt() != 0) i++; i++ }
        if (i >= b.size) return null
        val cs: Charset = when (enc) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
        return String(b, i, b.size - i, cs).trimEnd('\u0000').takeIf { it.isNotBlank() }
    }

    // --- FLAC: comentarios Vorbis LYRICS / UNSYNCEDLYRICS ---
    private fun flac(inp: DataInputStream): String? {
        skip(inp, 4)
        while (true) {
            val hdr = inp.readUnsignedByte()
            val last = hdr and 0x80 != 0
            val type = hdr and 0x7F
            val len = (inp.readUnsignedByte() shl 16) or (inp.readUnsignedByte() shl 8) or inp.readUnsignedByte()
            if (type == 4 && len < MAX_BLOCK) {
                val b = ByteArray(len); inp.readFully(b)
                return vorbis(b)
            }
            skip(inp, len.toLong())
            if (last) return null
        }
    }

    private fun vorbis(b: ByteArray): String? {
        var i = 0
        fun le32(): Int { val v = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24); i += 4; return v }
        val vendor = le32(); i += vendor
        val count = le32()
        var unsynced: String? = null
        repeat(count) {
            if (i + 4 > b.size) return unsynced
            val l = le32()
            if (l < 0 || i + l > b.size) return unsynced
            val c = String(b, i, l, Charsets.UTF_8); i += l
            val key = c.substringBefore('=').uppercase()
            val v = c.substringAfter('=', "")
            if (v.isNotBlank()) when (key) {
                "LYRICS", "SYNCEDLYRICS" -> if (v.contains('[')) return v else if (unsynced == null) unsynced = v
                "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (unsynced == null) unsynced = v
            }
        }
        return unsynced
    }

    // --- M4A / MP4: moov › udta › meta › ilst › ©lyr › data ---
    private fun mp4(inp: DataInputStream): String? {
        while (true) {
            val size = inp.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { inp.readFully(it) }.let { String(it, Charsets.ISO_8859_1) }
            var body = size - 8
            if (size == 1L) { body = inp.readLong() - 16 }
            if (size == 0L) return null
            if (type == "moov") {
                if (body > MAX_BLOCK) return null
                val b = ByteArray(body.toInt()); inp.readFully(b)
                return findLyr(b, 0, b.size, 0)
            }
            skip(inp, body)
        }
    }

    private fun findLyr(b: ByteArray, from: Int, to: Int, depth: Int): String? {
        var i = from
        while (i + 8 <= to) {
            val size = be32(b, i)
            if (size < 8 || i + size > to) return null
            val type = String(b, i + 4, 4, Charsets.ISO_8859_1)
            when (type) {
                "udta", "ilst" -> findLyr(b, i + 8, i + size, depth + 1)?.let { return it }
                "meta" -> findLyr(b, i + 12, i + size, depth + 1)?.let { return it }
                "©lyr" -> {
                    var j = i + 8
                    while (j + 16 <= i + size) {
                        val ds = be32(b, j)
                        if (ds < 16 || j + ds > i + size) break
                        if (String(b, j + 4, 4, Charsets.ISO_8859_1) == "data")
                            return String(b, j + 16, ds - 16, Charsets.UTF_8).takeIf { it.isNotBlank() }
                        j += ds
                    }
                }
            }
            i += size
        }
        return null
    }

    // --- utilidades ---
    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun skip(inp: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val s = inp.skip(left)
            if (s <= 0) { if (inp.read() < 0) throw java.io.EOFException(); left-- } else left -= s
        }
    }
}
