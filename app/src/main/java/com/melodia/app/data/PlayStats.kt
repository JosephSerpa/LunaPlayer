package com.melodia.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Una canción escuchada: cuántas veces y cuándo fue la última. */
data class PlayCount(val count: Int, val lastPlayed: Long)

/** Una escucha en el historial: qué canción y cuándo. */
data class PlayEvent(val songId: Long, val at: Long)

/**
 * Cuántas veces se escuchó cada canción (cuenta a los 30 s, o a la mitad si es corta).
 * Alimenta "Quick picks" y lo más escuchado de la pantalla de inicio. Se guarda en el teléfono.
 */
class PlayStats(context: Context) {

    private val file = File(context.filesDir, "play_stats.bin")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _counts = MutableStateFlow<Map<Long, PlayCount>>(emptyMap())
    val counts: StateFlow<Map<Long, PlayCount>> = _counts.asStateFlow()

    private var saveJob: Job? = null

    /** Historial de escuchas (las más recientes al final), para "esta semana", rachas, recientes… */
    private val logFile = File(context.filesDir, "play_log.bin")
    private val _log = MutableStateFlow<List<PlayEvent>>(emptyList())
    val log: StateFlow<List<PlayEvent>> = _log.asStateFlow()

    init {
        scope.launch {
            val events = readLog()
            synchronized(lock) { _log.value = events + _log.value }
        }
        scope.launch {
            val loaded = read()
            synchronized(lock) {
                // Si ya se contó algo mientras se leía el archivo, se suma a lo guardado.
                val merged = HashMap(loaded)
                for ((id, c) in _counts.value) {
                    val old = merged[id]
                    merged[id] = if (old == null) c else PlayCount(old.count + c.count, maxOf(old.lastPlayed, c.lastPlayed))
                }
                _counts.value = merged
            }
        }
    }

    fun record(songId: Long) {
        synchronized(lock) {
            val m = HashMap(_counts.value)
            val old = m[songId]
            m[songId] = PlayCount((old?.count ?: 0) + 1, System.currentTimeMillis())
            _counts.value = m
            _log.value = (_log.value + PlayEvent(songId, System.currentTimeMillis())).takeLast(LOG_MAX)
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            write(_counts.value)
            writeLog(_log.value)
        }
    }

    private fun readLog(): List<PlayEvent> = runCatching {
        if (!logFile.exists()) return emptyList()
        DataInputStream(logFile.inputStream().buffered()).use { i ->
            if (i.readInt() != VERSION) return emptyList()
            val n = i.readInt()
            List(n) { PlayEvent(i.readLong(), i.readLong()) }
        }
    }.getOrDefault(emptyList())

    private fun writeLog(list: List<PlayEvent>) {
        runCatching {
            val tmp = File(logFile.parentFile, logFile.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { o ->
                o.writeInt(VERSION)
                o.writeInt(list.size)
                for (e in list) { o.writeLong(e.songId); o.writeLong(e.at) }
            }
            tmp.renameTo(logFile)
        }
    }

    private fun read(): Map<Long, PlayCount> = runCatching {
        if (!file.exists()) return emptyMap()
        DataInputStream(file.inputStream().buffered()).use { i ->
            if (i.readInt() != VERSION) return emptyMap()
            val n = i.readInt()
            val m = HashMap<Long, PlayCount>(n * 2)
            repeat(n) { m[i.readLong()] = PlayCount(i.readInt(), i.readLong()) }
            m
        }
    }.getOrDefault(emptyMap())

    private fun write(m: Map<Long, PlayCount>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { o ->
                o.writeInt(VERSION)
                o.writeInt(m.size)
                for ((id, c) in m) { o.writeLong(id); o.writeInt(c.count); o.writeLong(c.lastPlayed) }
            }
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val VERSION = 1
        /** Escuchas que se guardan en el historial (las más viejas se descartan). */
        const val LOG_MAX = 30_000
    }
}
