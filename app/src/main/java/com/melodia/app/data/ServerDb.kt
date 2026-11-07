package com.melodia.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

/**
 * Copia local (SQLite) de la biblioteca del servidor Subsonic/Navidrome.
 * Se lee al instante al abrir la app y solo se reescribe lo que cambió en el servidor.
 */
class ServerDb private constructor(context: Context) : SQLiteOpenHelper(context, "server_library.db", null, VERSION) {

    data class Info(val tag: String, val fingerprint: String, val savedAt: Long, val checkedAt: Long, val count: Int)
    data class Diff(val added: Int, val removed: Int, val changed: Int) {
        val any get() = added + removed + changed > 0
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE songs(
                id INTEGER PRIMARY KEY, remote_id TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL,
                album TEXT NOT NULL, album_id INTEGER NOT NULL, duration_ms INTEGER NOT NULL,
                uri TEXT NOT NULL, art_uri TEXT NOT NULL, path TEXT NOT NULL, size INTEGER NOT NULL,
                sort_key TEXT NOT NULL, hash INTEGER NOT NULL, genre TEXT NOT NULL DEFAULT '', year INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX songs_sort ON songs(sort_key)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 3 && newVersion == 4) {
            // Versión 4: las direcciones se guardan sin credenciales. Se limpian las que había,
            // sin volver a bajar nada (la cola y la biblioteca siguen igual).
            val rows = ArrayList<Triple<Long, String, String>>()
            db.rawQuery("SELECT id,uri,art_uri FROM songs", null).use { c ->
                while (c.moveToNext()) rows += Triple(c.getLong(0), c.getString(1), c.getString(2))
            }
            for ((id, uri, art) in rows) {
                db.execSQL(
                    "UPDATE songs SET uri=?, art_uri=?, hash=0 WHERE id=?",
                    arrayOf<Any>(com.melodia.app.data.server.ServerAuth.strip(uri), com.melodia.app.data.server.ServerAuth.strip(art), id)
                )
            }
            return
        }
        // La copia solo es una caché: se descarta y se vuelve a bajar del servidor.
        db.execSQL("DROP TABLE IF EXISTS songs")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    private fun meta(db: SQLiteDatabase, k: String): String? =
        db.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun putMeta(db: SQLiteDatabase, k: String, v: String) {
        db.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)", arrayOf(k, v))
    }

    fun info(): Info? = runCatching {
        val db = readableDatabase
        val tag = meta(db, "tag") ?: return null
        val count = db.rawQuery("SELECT COUNT(*) FROM songs", null).use { it.moveToFirst(); it.getInt(0) }
        Info(
            tag, meta(db, "fingerprint").orEmpty(),
            meta(db, "saved_at")?.toLongOrNull() ?: 0L,
            meta(db, "checked_at")?.toLongOrNull() ?: 0L,
            count
        )
    }.getOrNull()

    /** Canciones guardadas, solo si pertenecen a esta conexión (tag). */
    fun read(tag: String): List<Song>? = runCatching {
        val db = readableDatabase
        if (meta(db, "tag") != tag) return null
        val out = ArrayList<Song>()
        db.rawQuery(
            "SELECT id,remote_id,title,artist,album,album_id,duration_ms,uri,art_uri,path,size,genre,year FROM songs", null
        ).use { c ->
            while (c.moveToNext()) {
                out += Song(
                    id = c.getLong(0), remoteId = c.getString(1), title = c.getString(2),
                    artist = c.getString(3), album = c.getString(4), albumId = c.getLong(5),
                    durationMs = c.getLong(6), uri = Uri.parse(c.getString(7)),
                    artUri = Uri.parse(c.getString(8)), path = c.getString(9), sizeBytes = c.getLong(10),
                    genre = c.getString(11).orEmpty(), year = c.getInt(12)
                )
            }
        }
        out
    }.getOrNull()

    private fun hashOf(s: Song) = "${s.title}|${s.artist}|${s.album}|${s.albumId}|${s.durationMs}|${s.uri}|${s.artUri}|${s.path}|${s.sizeBytes}|${s.genre}|${s.year}".hashCode().toLong()

    /** Aplica solo las diferencias con lo que ya había (añadidas, quitadas y modificadas). */
    fun sync(songs: List<Song>, tag: String, fingerprint: String): Diff {
        val db = writableDatabase
        var added = 0; var removed = 0; var changed = 0
        db.beginTransaction()
        try {
            if (meta(db, "tag") != tag) {
                db.execSQL("DELETE FROM songs")
            }
            val old = HashMap<Long, Long>()
            db.rawQuery("SELECT id,hash FROM songs", null).use { while (it.moveToNext()) old[it.getLong(0)] = it.getLong(1) }
            val seen = HashSet<Long>(songs.size * 2)
            val cv = ContentValues()
            for (s in songs) {
                seen += s.id
                val h = hashOf(s)
                val prev = old[s.id]
                if (prev == h) continue
                cv.clear()
                cv.put("id", s.id); cv.put("remote_id", s.remoteId.orEmpty()); cv.put("title", s.title)
                cv.put("artist", s.artist); cv.put("album", s.album); cv.put("album_id", s.albumId)
                cv.put("duration_ms", s.durationMs); cv.put("uri", s.uri.toString())
                cv.put("art_uri", s.artUri.toString()); cv.put("path", s.path); cv.put("size", s.sizeBytes)
                cv.put("sort_key", SearchKeys.norm(s.title)); cv.put("hash", h); cv.put("genre", s.genre); cv.put("year", s.year)
                db.insertWithOnConflict("songs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (prev == null) added++ else changed++
            }
            for (id in old.keys) if (id !in seen) {
                db.delete("songs", "id=?", arrayOf(id.toString())); removed++
            }
            val now = System.currentTimeMillis().toString()
            putMeta(db, "tag", tag); putMeta(db, "fingerprint", fingerprint)
            putMeta(db, "saved_at", now); putMeta(db, "checked_at", now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return Diff(added, removed, changed)
    }

    /** El servidor se revisó y no había cambios. */
    fun touch() {
        runCatching {
            val db = writableDatabase
            putMeta(db, "checked_at", System.currentTimeMillis().toString())
        }
    }

    fun clear() {
        runCatching {
            val db = writableDatabase
            db.execSQL("DELETE FROM songs"); db.execSQL("DELETE FROM meta")
        }
    }

    companion object {
        private const val VERSION = 4

        @Volatile private var instance: ServerDb? = null

        /** Una sola conexión a la base de datos para toda la app. */
        fun get(context: Context): ServerDb =
            instance ?: synchronized(this) {
                instance ?: ServerDb(context.applicationContext).also { instance = it }
            }
    }
}
