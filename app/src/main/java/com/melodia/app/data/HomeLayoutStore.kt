package com.melodia.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Algo que se puede fijar en Inicio desde su menú. */
data class HomePin(val kind: TileKind, val ref: String)

/** Qué muestra un cuadro de la pantalla de inicio. */
enum class TileKind(val builtIn: Boolean) {
    QUICK(true), ALBUMS(true), ARTISTS(true), GENRES(true), LIKES(true),
    SONG(false), ALBUM(false), ARTIST(false),
    /** Toda la música en orden aleatorio, álbum sorpresa, canciones para redescubrir y un reloj. */
    SHUFFLE(true), DISCOVER(true), FORGOTTEN(true), CLOCK(true),
    /** Widgets de "lo que más quieres": top de la semana, álbum del mes, racha, estrellas, momento del día… */
    TOP5(true), ALBUM_MONTH(true), STREAK(true), SHELF(true), STARS(true), MOMENT(true), BEST_PARTS(true),
    COLORS(true), TIMELINE(true), CONTINUE(true), RECENT(true), SERVER_NEW(true), LASTFM(true),
    /** Una lista de reproducción fijada ([HomeTile.ref] = id de la lista). */
    PLAYLIST(false)
}

/**
 * Un cuadro en la cuadrícula de inicio (4 × 6 celdas).
 * [ref] identifica lo fijado: id de la canción o del álbum, o el nombre del artista.
 */
data class HomeTile(
    val kind: TileKind,
    val ref: String = "",
    val col: Int,
    val row: Int,
    val w: Int,
    val h: Int
) {
    val key: String get() = "${kind.name}:$ref"
    val minW: Int get() = when (kind) {
        TileKind.QUICK, TileKind.LIKES, TileKind.FORGOTTEN, TileKind.TOP5, TileKind.SHELF, TileKind.MOMENT,
        TileKind.COLORS, TileKind.TIMELINE, TileKind.CONTINUE, TileKind.RECENT, TileKind.SERVER_NEW,
        TileKind.LASTFM, TileKind.STARS -> 2
        else -> 1
    }
    fun overlaps(o: HomeTile) =
        col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h
}

/**
 * Distribución de la pantalla de inicio, como los iconos de un móvil: el orden de la lista manda
 * y los cuadros se acomodan solos (de izquierda a derecha y de arriba abajo, aprovechando huecos).
 * Se pueden mover, cambiar de tamaño, quitar y fijar cosas nuevas.
 */
class HomeLayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    /** Columnas de la cuadrícula: 4 en celulares; más en pantallas anchas (tablet en horizontal). */
    var cols = COLS
        private set
    /** Filas de la cuadrícula según las columnas: 4 × 12, 6 × 8, 8 × 8. */
    val rows: Int get() = rowsFor(cols)

    private val _tiles = MutableStateFlow(load())
    val tiles: StateFlow<List<HomeTile>> = _tiles.asStateFlow()

    /** Cambia el número de columnas (según el ancho de la pantalla) y reacomoda en el mismo orden. */
    fun setCols(c: Int) {
        if (c == cols) return
        cols = c
        val list = _tiles.value.map { it.copy(w = it.w.coerceAtMost(c)) }
        // Si no cabe en las filas de siempre, se deja crecer hacia abajo: nunca se pierde un cuadro.
        _tiles.value = pack(list, c, rowsFor(c)) ?: pack(list, c, 100) ?: list
    }

    fun isPinned(kind: TileKind, ref: String) = _tiles.value.any { it.kind == kind && it.ref == ref }

    /** Fija una canción, álbum o artista (2 × 2) en el primer hueco libre. Falso si no cabe. */
    fun pin(kind: TileKind, ref: String): Boolean {
        if (isPinned(kind, ref)) return true
        return commit(_tiles.value + HomeTile(kind, ref, 0, 0, 2, 2))
    }

    fun unpin(kind: TileKind, ref: String) {
        commit(_tiles.value.filterNot { it.kind == kind && it.ref == ref })
    }

    fun remove(key: String) {
        commit(_tiles.value.filterNot { it.key == key })
    }

    /** Cuadros fijos (Quick picks, álbumes…) que se quitaron y se pueden volver a poner. */
    fun hiddenBuiltIns(): List<TileKind> =
        DEFAULT.map { it.kind }.filter { k -> _tiles.value.none { it.kind == k } }

    /** Añade un cuadro de Quick picks, álbumes, artistas o géneros (puede haber varios del mismo tipo). */
    fun addBuiltIn(kind: TileKind): Boolean {
        val def = defaultTile(kind) ?: return false
        val ref = if (_tiles.value.none { it.kind == kind && it.ref.isEmpty() }) "" else System.currentTimeMillis().toString()
        // Tamaño normal; si no cabe, uno más pequeño.
        val sizes = listOf(def.w to def.h, 2 to 2, 2 to 1, 1 to 1).distinct().filter { it.first >= def.minW && it.first <= cols }
        for ((w, h) in sizes) {
            if (commit(_tiles.value + def.copy(ref = ref, w = w, h = h))) return true
        }
        return false
    }

    fun reset() {
        commit(DEFAULT)
    }

    /**
     * Mueve un cuadro hacia la celda indicada. Los demás se reacomodan para dejarle sitio:
     * queda antes que todo lo que empieza después de esa celda. Devuelve si cambió algo.
     */
    fun move(key: String, col: Int, row: Int): Boolean {
        val all = _tiles.value
        val packed = arrange(all, key, col, row, cols, rows) ?: return false
        if (packed == all) return false
        store(packed)
        return true
    }

    /** Cambia el tamaño de un cuadro; los demás se reacomodan. Falso si no cabe todo. */
    fun resize(key: String, w: Int, h: Int): Boolean {
        val all = _tiles.value
        val t = all.firstOrNull { it.key == key } ?: return false
        val nw = w.coerceIn(t.minW, cols)
        val nh = h.coerceIn(1, rows)
        if (nw == t.w && nh == t.h) return false
        return commit(all.map { if (it.key == key) it.copy(w = nw, h = nh) else it })
    }

    /** Acomoda y guarda. Falso (sin cambiar nada) si algún cuadro se quedaría sin sitio. */
    private fun commit(list: List<HomeTile>): Boolean {
        val packed = pack(list, cols, rows) ?: return false
        store(packed)
        return true
    }

    private fun store(list: List<HomeTile>) {
        _tiles.value = list
        prefs.edit().putString(KEY, encode(list)).putBoolean(KEY_SET, true).putInt(KEY_GRID, GRID_VERSION).apply()
    }

    // Se guarda en el orden de la lista (el que manda), no por posición.
    private fun encode(list: List<HomeTile>) = list.joinToString("\n") {
        listOf(it.kind.name, Uri.encode(it.ref), it.col, it.row, it.w, it.h).joinToString("|")
    }

    private fun load(): List<HomeTile> {
        if (!prefs.getBoolean(KEY_SET, false)) return pack(DEFAULT) ?: DEFAULT
        val version = prefs.getInt(KEY_GRID, 1)
        val raw = parse(prefs.getString(KEY, "").orEmpty())
            .map { it.copy(w = it.w.coerceIn(1, 8), h = it.h.coerceIn(1, 12)) }
        // Antes de la versión 3 el orden se deducía de la posición; desde la 3 se guarda tal cual.
        val parsed = if (version < 3) raw.sortedWith(compareBy({ it.row }, { it.col })) else raw
        // Cuadrícula anterior (8 × 8): los tamaños pasan a los de ahora y todo se acomoda de nuevo.
        val old = version < 2
        val src = if (old) parsed.map { t ->
            val def = DEFAULT.firstOrNull { it.kind == t.kind }
            t.copy(w = def?.w ?: 2, h = def?.h ?: 2)
        } else parsed
        // Si no cabe en 4 × 6 (se guardó desde una pantalla ancha), crece hacia abajo: no se pierde nada.
        val placed = pack(src, COLS, ROWS) ?: pack(src, COLS, 100) ?: src
        if (version < GRID_VERSION) prefs.edit().putString(KEY, encode(placed)).putInt(KEY_GRID, GRID_VERSION).apply()
        return placed
    }

    private fun parse(raw: String): List<HomeTile> {
        return raw.lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 6) return@mapNotNull null
            val kind = runCatching { TileKind.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
            HomeTile(
                kind, Uri.decode(p[1]),
                p[2].toIntOrNull() ?: return@mapNotNull null, p[3].toIntOrNull() ?: return@mapNotNull null,
                p[4].toIntOrNull() ?: return@mapNotNull null, p[5].toIntOrNull() ?: return@mapNotNull null
            )
        }
    }

    companion object {
        const val COLS = 4
        /** Filas en celulares (4 columnas): el doble que antes; Inicio se desliza hacia abajo para ver el resto. */
        const val ROWS = 20
        private const val GRID_VERSION = 3
        private const val KEY_GRID = "grid"
        private const val KEY = "tiles"
        private const val KEY_SET = "custom"

        /** Arriba: Quick picks (4 × 2); debajo, álbumes y artistas (2 × 2) y, luego, géneros (2 × 2). */
        val DEFAULT = listOf(
            HomeTile(TileKind.QUICK, col = 0, row = 0, w = 4, h = 2),
            HomeTile(TileKind.ALBUMS, col = 0, row = 2, w = 2, h = 2),
            HomeTile(TileKind.ARTISTS, col = 2, row = 2, w = 2, h = 2),
            HomeTile(TileKind.GENRES, col = 0, row = 4, w = 2, h = 2)
        )

        /** "Me gusta": 4 de ancho × 2 de alto; no está en la distribución inicial, se añade con el "+". */
        val LIKES_DEFAULT = HomeTile(TileKind.LIKES, col = 0, row = 0, w = 4, h = 2)

        /** Filas por columnas: 4 × 12 y 6 × 8 (48 casillas); 8 × 8 en pantallas anchas (dos "páginas" de 8 × 4). */
        fun rowsFor(cols: Int) = when {
            cols <= COLS -> ROWS
            cols <= 6 -> 8
            else -> 8
        }

        /** Tamaño con que se añade cada cuadro fijo. */
        fun defaultTile(kind: TileKind): HomeTile? = when (kind) {
            TileKind.LIKES -> LIKES_DEFAULT
            TileKind.SHUFFLE -> HomeTile(kind, col = 0, row = 0, w = 2, h = 1)
            TileKind.DISCOVER -> HomeTile(kind, col = 0, row = 0, w = 2, h = 2)
            TileKind.FORGOTTEN -> HomeTile(kind, col = 0, row = 0, w = 4, h = 2)
            TileKind.CLOCK -> HomeTile(kind, col = 0, row = 0, w = 4, h = 1)
            TileKind.TOP5, TileKind.SHELF, TileKind.MOMENT, TileKind.COLORS, TileKind.TIMELINE,
            TileKind.RECENT, TileKind.SERVER_NEW, TileKind.LASTFM -> HomeTile(kind, col = 0, row = 0, w = 4, h = 2)
            TileKind.STARS, TileKind.CONTINUE -> HomeTile(kind, col = 0, row = 0, w = 4, h = 1)
            TileKind.ALBUM_MONTH, TileKind.STREAK, TileKind.BEST_PARTS -> HomeTile(kind, col = 0, row = 0, w = 2, h = 2)
            else -> DEFAULT.firstOrNull { it.kind == kind }
        }

        /**
         * Cómo quedaría todo si el cuadro [key] se suelta en la celda (col, row), sin guardar nada.
         * Sirve para mostrar el reacomodo mientras se arrastra, y es lo mismo que hace [move].
         * Null si no cabe.
         */
        fun arrange(all: List<HomeTile>, key: String, col: Int, row: Int, cols: Int, rows: Int): List<HomeTile>? {
            val t = all.firstOrNull { it.key == key } ?: return null
            val c = col.coerceIn(0, (cols - t.w).coerceAtLeast(0))
            val r = row.coerceIn(0, (maxOf(rows, all.maxOf { it.row + it.h }) - t.h).coerceAtLeast(0))
            val others = all.filter { it.key != key }
            val idx = others.count { it.row < r || (it.row == r && it.col < c) }
            val list = others.toMutableList().apply { add(idx, t) }
            if (list.map { it.key } == all.map { it.key }) return all
            return pack(list, cols, rows)
        }

        /** Coloca los cuadros en el orden de la lista, cada uno en el primer hueco libre. */
        fun pack(list: List<HomeTile>, cols: Int = COLS, rows: Int = ROWS): List<HomeTile>? {
            val placed = mutableListOf<HomeTile>()
            for (t in list) {
                val tw = t.w.coerceAtMost(cols)
                val spot = freeSpot(placed, tw, t.h, cols, rows) ?: return null
                placed += t.copy(col = spot.first, row = spot.second, w = tw)
            }
            return placed
        }

        fun freeSpot(tiles: List<HomeTile>, w: Int, h: Int, cols: Int = COLS, rows: Int = ROWS): Pair<Int, Int>? {
            for (r in 0..rows - h) for (c in 0..cols - w) {
                val probe = HomeTile(TileKind.SONG, "", c, r, w, h)
                if (tiles.none { it.overlaps(probe) }) return c to r
            }
            return null
        }
    }
}
