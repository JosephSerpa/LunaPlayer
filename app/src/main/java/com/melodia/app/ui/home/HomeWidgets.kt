package com.melodia.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.melodia.app.MelodiaApp
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.Album
import com.melodia.app.data.Artist
import com.melodia.app.data.PlayCount
import com.melodia.app.data.PlayEvent
import com.melodia.app.data.Playlist
import com.melodia.app.data.Song
import com.melodia.app.data.SongMark
import com.melodia.app.data.UNKNOWN_ARTIST
import com.melodia.app.data.lastfm.LfmPeriod
import com.melodia.app.data.server.SubsonicClient
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.pressableWithLongClick
import com.melodia.app.ui.library.LibraryState
import com.melodia.app.ui.theme.ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Acciones que los widgets necesitan y que no están en la cuadrícula (navegar a una lista, Last.fm…)
// ---------------------------------------------------------------------------

class HomeActions(
    val onPlaylist: (Long) -> Unit = {},
    val onOpenLastFm: () -> Unit = {},
    val onOpenServer: () -> Unit = {}
)

val LocalHomeActions = staticCompositionLocalOf { HomeActions() }

private const val WART = 300

// ---------------------------------------------------------------------------
// Datos de los widgets: se calculan en segundo plano a partir de la biblioteca y el historial.
// ---------------------------------------------------------------------------

/** Un álbum escuchado hace poco: la canción con que seguir y cuántas sonaron (para la "pila"). */
internal class RecentItem(val album: Album, val song: Song, val count: Int)

/** Algo de 5 estrellas para la estantería. */
internal class ShelfItem(val album: Album?, val song: Song?) {
    val art: Uri? get() = album?.artUri ?: song?.artUri
    val title: String get() = album?.title ?: song?.title.orEmpty()
}

internal class Decade(val start: Int, val albums: List<Album>, val songs: List<Song>)

internal class WidgetData(
    val top5: List<Song>,
    val top5Plays: List<Int>,
    val top5Week: Boolean,
    val monthAlbum: Album?,
    val monthPlays: Int,
    val streakArtist: Artist?,
    val streakDays: Int,
    val shelf: List<ShelfItem>,
    /** Canciones por estrellas: índice 0 = 1 estrella … 4 = 5 estrellas. */
    val byStars: List<List<Song>>,
    val slot: Int,
    val momentSongs: List<Song>,
    val momentReal: Boolean,
    val bestClips: List<Pair<Song, Long>>,
    val likedCount: Int,
    val decades: List<Decade>,
    val recent: List<RecentItem>,
    val albumsById: Map<Long, Album>,
    val songsById: Map<Long, Song>
)

private val tz: TimeZone get() = TimeZone.getDefault()
private fun dayOf(ms: Long) = (ms + tz.getOffset(ms)) / 86_400_000L

/** Franja del día: 0 madrugada, 1 mañana, 2 tarde, 3 noche. */
internal fun slotOf(ms: Long): Int {
    val h = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 0..5 -> 0
        in 6..11 -> 1
        in 12..18 -> 2
        else -> 3
    }
}

internal fun computeWidgets(
    lib: LibraryState,
    stats: Map<Long, PlayCount>,
    log: List<PlayEvent>,
    marks: Map<Long, SongMark>,
    albumMarks: Map<Long, SongMark>,
    now: Long = System.currentTimeMillis()
): WidgetData {
    val byId = lib.songs.associateBy { it.id }
    val albumsById = lib.albums.associateBy { it.id }
    val week = now - 7L * 86_400_000L
    val month = now - 30L * 86_400_000L

    // --- Top 5 de la semana ---
    val weekCounts = HashMap<Long, Int>()
    for (e in log) if (e.at >= week && e.songId in byId) weekCounts[e.songId] = (weekCounts[e.songId] ?: 0) + 1
    val top = LinkedHashMap<Long, Int>()
    weekCounts.entries.sortedByDescending { it.value }.take(5).forEach { top[it.key] = it.value }
    val top5Week = top.isNotEmpty()
    if (top.size < 5) {
        // Sin historial suficiente: lo que sonó esta semana y, si no, lo más escuchado de siempre.
        stats.entries.asSequence().filter { it.key in byId && it.key !in top }
            .sortedWith(compareByDescending<Map.Entry<Long, PlayCount>> { if (it.value.lastPlayed >= week) 1 else 0 }
                .thenByDescending { it.value.count })
            .take(5 - top.size).forEach { top[it.key] = it.value.count }
    }
    val top5 = top.keys.mapNotNull { byId[it] }
    val top5Plays = top.values.toList()

    // --- Álbum del mes ---
    val albumMonth = HashMap<Long, Int>()
    var monthFromLog = false
    for (e in log) if (e.at >= month) byId[e.songId]?.let { albumMonth[it.albumId] = (albumMonth[it.albumId] ?: 0) + 1; monthFromLog = true }
    if (!monthFromLog) for ((id, c) in stats) if (c.lastPlayed >= month) byId[id]?.let { albumMonth[it.albumId] = (albumMonth[it.albumId] ?: 0) + c.count }
    val monthBest = albumMonth.entries.filter { it.key in albumsById }.maxByOrNull { it.value }
    val monthAlbum = monthBest?.let { albumsById[it.key] }

    // --- Racha: días seguidos (hasta hoy o ayer) escuchando al mismo artista ---
    val artistDays = HashMap<String, HashSet<Long>>()
    val artistWeek = HashMap<String, Int>()
    for (e in log) {
        val s = byId[e.songId] ?: continue
        if (s.artist == UNKNOWN_ARTIST) continue
        artistDays.getOrPut(s.artist) { HashSet() } += dayOf(e.at)
        if (e.at >= week) artistWeek[s.artist] = (artistWeek[s.artist] ?: 0) + 1
    }
    val today = dayOf(now)
    var bestArtist: String? = null
    var bestStreak = 0
    for ((name, days) in artistDays) {
        var d = if (today in days) today else if (today - 1 in days) today - 1 else continue
        var n = 0
        while (d in days) { n++; d-- }
        if (n > bestStreak || (n == bestStreak && (artistWeek[name] ?: 0) > (artistWeek[bestArtist] ?: 0))) {
            bestStreak = n; bestArtist = name
        }
    }
    val artistsByName = lib.artists.associateBy { it.name }
    val streakArtist = bestArtist?.let { artistsByName[it] } ?: run {
        val plays = HashMap<String, Int>()
        for ((id, c) in stats) byId[id]?.let { if (it.artist != UNKNOWN_ARTIST) plays[it.artist] = (plays[it.artist] ?: 0) + c.count }
        plays.maxByOrNull { it.value }?.key?.let { artistsByName[it] }
    }

    // --- Estantería de 5 estrellas y canciones por estrellas ---
    val shelf = ArrayList<ShelfItem>()
    lib.albums.filter { albumMarks[it.id]?.rating == 5 }.forEach { shelf += ShelfItem(it, null) }
    lib.songs.filter { marks[it.id]?.rating == 5 }.forEach { shelf += ShelfItem(null, it) }
    val byStars = (1..5).map { n ->
        val set = LinkedHashSet<Song>()
        lib.songs.filterTo(set) { marks[it.id]?.rating == n }
        lib.albums.filter { albumMarks[it.id]?.rating == n }.forEach { set += it.songs }
        set.toList()
    }

    // --- Ahora suena bien: lo que sueles oír a esta hora ---
    val slot = slotOf(now)
    val slotCounts = HashMap<Long, Int>()
    for (e in log) if (e.songId in byId && slotOf(e.at) == slot) slotCounts[e.songId] = (slotCounts[e.songId] ?: 0) + 1
    val momentReal = slotCounts.isNotEmpty()
    val moment = if (momentReal) slotCounts.entries.sortedByDescending { it.value }.take(30).mapNotNull { byId[it.key] }
    else stats.entries.sortedByDescending { it.value.count }.take(30).mapNotNull { byId[it.key] }
        .ifEmpty { lib.songs.shuffled(kotlin.random.Random(today)).take(30) }

    // --- Mejores partes ---
    val bestClips = lib.songs.mapNotNull { s -> marks[s.id]?.takeIf { it.hasBest }?.let { s to it.bestMs } }
        .sortedByDescending { stats[it.first.id]?.count ?: 0 }
    val likedCount = marks.count { it.value.liked }

    // --- Décadas ---
    val decades = lib.albums.mapNotNull { a ->
        val y = a.songs.map { it.year }.filter { it > 0 }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        y?.let { (it / 10 * 10) to a }
    }.groupBy({ it.first }, { it.second }).toSortedMap().map { (start, albums) ->
        Decade(start, albums.sortedByDescending { a -> a.songs.sumOf { stats[it.id]?.count ?: 0 } }, albums.flatMap { it.songs })
    }

    // --- Recientes ---
    val recent = ArrayList<RecentItem>()
    val seen = HashSet<Long>()
    val events = if (log.isNotEmpty()) log.asReversed().asSequence().map { it.songId }
    else stats.entries.sortedByDescending { it.value.lastPlayed }.asSequence().map { it.key }
    val recentWindow = events.take(200).toList()
    for (id in recentWindow) {
        val s = byId[id] ?: continue
        if (!seen.add(s.albumId)) continue
        val a = albumsById[s.albumId] ?: continue
        val count = recentWindow.take(60).mapNotNull { byId[it] }.filter { it.albumId == s.albumId }.map { it.id }.distinct().size
        recent += RecentItem(a, s, count.coerceAtLeast(1))
        if (recent.size >= 12) break
    }

    return WidgetData(
        top5, top5Plays, top5Week, monthAlbum, monthBest?.value ?: 0, streakArtist, bestStreak,
        shelf, byStars, slot, moment, momentReal, bestClips, likedCount, decades, recent, albumsById, byId
    )
}

// ---------------------------------------------------------------------------
// Colores de las carátulas (para el mosaico): se calculan una vez y se guardan.
// ---------------------------------------------------------------------------

object AlbumColors {
    /**
     * Para sacar un color basta una carátula diminuta: a las del servidor se les pide 64 px en vez de
     * 600 (decenas de veces menos datos al calcular cientos de álbumes).
     */
    private fun smallCover(uri: android.net.Uri?): android.net.Uri? {
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) return uri
        if (uri.path?.endsWith("/getCoverArt") != true || uri.getQueryParameter("size") == null) return uri
        val b = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            b.appendQueryParameter(name, if (name == "size") "64" else uri.getQueryParameter(name))
        }
        return b.build()
    }

    private val _colors = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val colors: StateFlow<Map<Long, Int>> = _colors.asStateFlow()
    private var loaded = false
    private var running = false

    /** Calcula (en segundo plano) el color de los álbumes que aún no lo tienen. */
    suspend fun ensure(context: Context, albums: List<Album>) {
        if (running) return
        running = true
        try {
            val prefs = context.getSharedPreferences("album_colors", Context.MODE_PRIVATE)
            if (!loaded) {
                _colors.value = prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Int)?.let { id to it } } }.toMap()
                loaded = true
            }
            val missing = albums.filter { it.id !in _colors.value }.take(600)
            if (missing.isEmpty()) return
            val loader = context.imageLoader
            val batch = HashMap<Long, Int>()
            // Los que fallan (p. ej. sin conexión) se apartan solo por esta vez: se reintentan al volver a abrir.
            val failed = HashMap<Long, Int>()
            for ((i, a) in missing.withIndex()) {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val r = loader.execute(
                            ImageRequest.Builder(context).data(smallCover(a.artUri)).size(48).allowHardware(false).build()
                        )
                        if (r !is SuccessResult) return@runCatching null
                        val bmp = (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        bmp?.let { b ->
                            val p = Palette.from(b).generate()
                            (p.vibrantSwatch ?: p.dominantSwatch)?.rgb
                        } ?: 0
                    }.getOrNull()
                }
                if (outcome == null) failed[a.id] = 0 else batch[a.id] = outcome
                if (batch.size >= 24 || (i == missing.lastIndex && (batch.isNotEmpty() || failed.isNotEmpty()))) {
                    _colors.value = _colors.value + batch + failed
                    prefs.edit().apply { batch.forEach { (k, v) -> putInt(k.toString(), v) } }.apply()
                    batch.clear()
                    failed.clear()
                }
            }
        } finally {
            running = false
        }
    }

    /** Grupo de color: 0 rojo … 7 rosa, 8 blanco y negro, -1 sin carátula. */
    fun bucket(argb: Int): Int {
        if (argb == 0) return -1
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        if (hsv[1] < 0.2f || hsv[2] < 0.18f) return 8
        val h = hsv[0]
        return when {
            h < 15f || h >= 345f -> 0
            h < 45f -> 1
            h < 70f -> 2
            h < 160f -> 3
            h < 200f -> 4
            h < 255f -> 5
            h < 290f -> 6
            else -> 7
        }
    }

    val bucketColors = listOf(
        Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
        Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFF757575)
    )
}

/** Últimos álbumes del servidor y datos de Last.fm: se guardan un rato para no pedirlos en cada vistazo. */
private object WidgetCache {
    var serverNew: Pair<Long, List<Long>>? = null
    var lastfm: Pair<Long, LfmWidget>? = null
}

private class LfmWidget(val artist: String, val plays: Int, val days: IntArray)

// ---------------------------------------------------------------------------
// Piezas comunes
// ---------------------------------------------------------------------------

private val WShape = RoundedCornerShape(18.dp)

/** Fondo propio de cada widget (así se distinguen de un vistazo). */
@Composable
private fun Frame(brush: Brush, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .clip(WShape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.10f), WShape),
        content = content
    )
}

@Composable
private fun Title(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Hint(text: String, color: Color = Color.White.copy(alpha = 0.8f)) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PlayDot(size: Dp = 34.dp, bg: Color = Color.White, fg: Color = Color.Black, onClick: () -> Unit) {
    Box(Modifier.size(size).background(bg, CircleShape).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, null, tint = fg, modifier = Modifier.size(size * 0.6f))
    }
}

/** Número de posición (#1, #2…) sobre una carátula. */
@Composable
private fun RankBadge(n: Int, big: Boolean, modifier: Modifier = Modifier) {
    val gold = Color(0xFFFFC94D)
    Box(
        modifier
            .background(if (n == 1) gold else Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = if (big) 8.dp else 5.dp, vertical = 1.dp)
    ) {
        Text(
            "#$n", fontSize = if (big) 15.sp else 11.sp, fontWeight = FontWeight.Black,
            color = if (n == 1) Color.Black else Color.White, maxLines = 1
        )
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) { Hint(text) }
}

// ---------------------------------------------------------------------------
// Widgets
// ---------------------------------------------------------------------------

/** Top 5 de la semana: la #1 grande y las demás más pequeñas, con su número. */
@Composable
internal fun Top5Widget(wd: WidgetData, onPlaySong: (List<Song>, Int) -> Unit) {
    val songs = wd.top5
    Frame(Brush.linearGradient(listOf(Color(0xFF3A2A06), Color(0xFF15120C)))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Title(Str.get(if (wd.top5Week) R.string.w_top5 else R.string.w_top5_all), Icons.Rounded.EmojiEvents, Color(0xFFFFC94D))
                }
                if (songs.isNotEmpty()) PlayDot(28.dp, Color(0xFFFFC94D)) { onPlaySong(songs, 0) }
            }
            Spacer(Modifier.height(6.dp))
            if (songs.isEmpty()) { Empty(Str.get(R.string.w_need_plays)); return@Column }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val gap = 6.dp
                val mw = maxWidth; val mh = maxHeight
                val wide = mw > mh * 1.25f
                when {
                    wide -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        val big = min(mh.value, mw.value * 0.45f).dp
                        RankCover(songs, 0, big, onPlaySong)
                        val restW = mw - big - gap
                        val rows = if (mh >= 120.dp) 2 else 1
                        val perRow = if (rows == 2) 2 else 4
                        val side = min(((restW - gap * (perRow - 1)) / perRow).value, ((mh - gap * (rows - 1)) / rows).value).dp
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (c in 0 until perRow) {
                                    val i = 1 + r * perRow + c
                                    if (i < songs.size) RankCover(songs, i, side, onPlaySong)
                                }
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                        val big = min(mw.value, mh.value * 0.6f).dp
                        RankCover(songs, 0, big, onPlaySong)
                        val side = min(((mw - gap * 3) / 4).value, (mh - big - gap).value).dp
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (i in 1 until songs.size) RankCover(songs, i, side, onPlaySong)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankCover(songs: List<Song>, i: Int, side: Dp, onPlaySong: (List<Song>, Int) -> Unit, modifier: Modifier = Modifier, ring: Boolean = false) {
    val s = songs[i]
    Box(
        modifier.size(side)
            .then(if (ring) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp)) else Modifier)
            .pressableWithLongClick({ onPlaySong(songs, 0) }) { onPlaySong(songs, i) }
    ) {
        Artwork(s.artUri, side, cornerRadius = 10.dp, requestPx = WART)
        if (i == 0 && side >= 90.dp) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
            )
            Column(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                Text(s.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.artist, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        RankBadge(i + 1, big = side >= 90.dp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
    }
}

/** Álbum del mes: carátula como un disco, con un aro que se llena según las escuchas. */
@Composable
internal fun AlbumMonthWidget(wd: WidgetData, onAlbum: (Long) -> Unit, onPlaySong: (List<Song>, Int) -> Unit) {
    val a = wd.monthAlbum
    val ring = Color(0xFF7CF2C8)
    Frame(Brush.radialGradient(listOf(Color(0xFF173B33), Color(0xFF0B1513)))) {
        if (a == null) { Column(Modifier.padding(10.dp)) { Title(Str.get(R.string.w_album_month), Icons.Rounded.Album, ring); Empty(Str.get(R.string.w_need_plays)) }; return@Frame }
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp).pressable { onAlbum(a.id) }) {
            val wide = maxWidth > maxHeight * 1.3f
            val disc = if (wide) min(maxHeight.value, maxWidth.value * 0.5f).dp else min(maxWidth.value, (maxHeight - 40.dp).value).dp.coerceAtLeast(40.dp)
            val progress = (wd.monthPlays / 30f).coerceIn(0.05f, 1f)
            val info: @Composable () -> Unit = {
                Column {
                    Title(Str.get(R.string.w_album_month), Icons.Rounded.Album, ring)
                    Text(a.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Str.plural(R.plurals.w_times_month, wd.monthPlays), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            }
            val discView: @Composable () -> Unit = {
                Box(Modifier.size(disc), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 5.dp.toPx()
                        drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        drawArc(ring, -90f, 360f * progress, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Artwork(a.artUri, disc - 16.dp, circle = true, requestPx = WART)
                    Box(Modifier.size(disc * 0.12f).background(Color(0xFF0B1513), CircleShape))
                }
            }
            if (wide) Row(verticalAlignment = Alignment.CenterVertically) {
                discView(); Spacer(Modifier.width(10.dp)); Box(Modifier.weight(1f)) { info() }
                PlayDot(30.dp, ring) { onPlaySong(a.songs, 0) }
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box { discView(); Box(Modifier.align(Alignment.BottomEnd)) { PlayDot(28.dp, ring) { onPlaySong(a.songs, 0) } } }
                Spacer(Modifier.height(4.dp))
                Text(a.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Str.plural(R.plurals.w_times_month, wd.monthPlays), style = MaterialTheme.typography.labelSmall, color = ring, maxLines = 1)
            }
        }
    }
}

/** Racha: el artista que escuchas varios días seguidos, con una llamita y los días. */
@Composable
internal fun StreakWidget(wd: WidgetData, onArtist: (String) -> Unit, onPlaySong: (List<Song>, Int) -> Unit) {
    val a = wd.streakArtist
    val flame = Color(0xFFFF7A2F)
    Frame(Brush.verticalGradient(listOf(Color(0xFF4A1A08), Color(0xFF1A0B06)))) {
        if (a == null) { Column(Modifier.padding(10.dp)) { Title(Str.get(R.string.w_streak), Icons.Rounded.LocalFireDepartment, flame); Empty(Str.get(R.string.w_need_plays)) }; return@Frame }
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp).pressable { onPlaySong(a.songs.shuffled(), 0) }) {
            val wide = maxWidth > maxHeight * 1.3f
            val photo = if (wide) min(maxHeight.value, maxWidth.value * 0.45f).dp else min(maxWidth.value, (maxHeight - 38.dp).value).dp.coerceAtLeast(36.dp)
            val badge: @Composable (Modifier) -> Unit = { m ->
                Row(
                    m.background(Brush.horizontalGradient(listOf(Color(0xFFFFB13B), flame)), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    if (wd.streakDays > 0) Text("${wd.streakDays}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
            val photoView: @Composable () -> Unit = {
                Box(Modifier.size(photo)) {
                    Artwork(a.artUri, photo, circle = true, requestPx = WART, modifier = Modifier.border(3.dp, flame, CircleShape))
                    badge(Modifier.align(Alignment.BottomCenter))
                }
            }
            val subtitle = if (wd.streakDays > 0) Str.plural(R.plurals.w_streak_days, wd.streakDays) else Str.get(R.string.w_streak_none)
            if (wide) Row(verticalAlignment = Alignment.CenterVertically) {
                photoView(); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Title(Str.get(R.string.w_streak), Icons.Rounded.LocalFireDepartment, flame)
                    Text(a.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pressable { onArtist(a.name) })
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                photoView()
                Spacer(Modifier.height(4.dp))
                Text(a.name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = flame, maxLines = 1)
            }
        }
    }
}

/** Estantería: lo de 5 estrellas puesto como discos en repisas de madera. */
@Composable
internal fun ShelfWidget(wd: WidgetData, tileH: Int, editing: Boolean, onAlbum: (Long) -> Unit, onPlaySong: (List<Song>, Int) -> Unit) {
    val wood = Brush.verticalGradient(listOf(Color(0xFFB07A45), Color(0xFF6E4524)))
    Frame(Brush.verticalGradient(listOf(Color(0xFF2B1D12), Color(0xFF16100A)))) {
        Column(Modifier.fillMaxSize().padding(top = 10.dp, start = 10.dp, end = 10.dp)) {
            Title(Str.get(R.string.w_shelf), Icons.Rounded.Star, Color(0xFFFFD27A))
            if (wd.shelf.isEmpty()) { Empty(Str.get(R.string.w_shelf_empty)); return@Column }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val shelves = (maxHeight / 90.dp).toInt().coerceIn(1, max(1, tileH))
                val perShelf = (wd.shelf.size + shelves - 1) / shelves
                Column(Modifier.fillMaxSize()) {
                    for (k in 0 until shelves) {
                        val part = wd.shelf.drop(k * perShelf).take(perShelf)
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val side = (maxHeight - 12.dp).coerceAtLeast(24.dp)
                            LazyRow(
                                Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                userScrollEnabled = !editing
                            ) {
                                itemsIndexed(part) { _, item ->
                                    Box(
                                        Modifier.size(side).graphicsLayer { shadowElevation = 8f; shape = RoundedCornerShape(4.dp); clip = true }
                                            .pressable {
                                                item.album?.let { onAlbum(it.id) } ?: item.song?.let { onPlaySong(listOf(it), 0) }
                                            }
                                    ) {
                                        Artwork(item.art, side, cornerRadius = 4.dp, requestPx = WART)
                                        if (item.song != null) Icon(
                                            Icons.Rounded.MusicNote, null, tint = Color.White,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(14.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(2.dp)
                                        )
                                    }
                                }
                            }
                            // La repisa.
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(9.dp).background(wood, RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
    }
}

/** Cinco botones de estrellas: cada uno reproduce (al azar) lo que tiene esa calificación. */
@Composable
internal fun StarsWidget(wd: WidgetData, onPlaySong: (List<Song>, Int) -> Unit) {
    val gold = Color(0xFFFFC94D)
    Frame(Brush.linearGradient(listOf(Color(0xFF241B3A), Color(0xFF120E1E)))) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val ratio = maxWidth / maxHeight
            val cols = when { ratio > 2.6f -> 5; ratio > 0.9f -> 3; else -> 2 }
            val rows = (5 + cols - 1) / cols
            val gap = 6.dp
            val cellW = (maxWidth - gap * (cols - 1)) / cols
            val cellH = (maxHeight - gap * (rows - 1)) / rows
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val n = 5 - (r * cols + c) // de 5 estrellas hacia abajo
                        if (n < 1) { Spacer(Modifier.size(cellW, cellH)); continue }
                        val songs = wd.byStars[n - 1]
                        val strength = 0.15f + 0.17f * n
                        Column(
                            Modifier.size(cellW, cellH)
                                .background(gold.copy(alpha = strength * 0.45f), RoundedCornerShape(14.dp))
                                .border(1.dp, gold.copy(alpha = strength), RoundedCornerShape(14.dp))
                                .pressable { if (songs.isNotEmpty()) onPlaySong(songs.shuffled(), 0) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$n", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (cellH > 60.dp) 22.sp else 16.sp)
                                Icon(Icons.Rounded.Star, null, tint = gold, modifier = Modifier.size(if (cellH > 60.dp) 22.dp else 16.dp))
                            }
                            if (cellH > 40.dp) Text("${songs.size}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private val slotBrushes = listOf(
    listOf(Color(0xFF1B1F4B), Color(0xFF3B2667)),   // madrugada
    listOf(Color(0xFFFFB36B), Color(0xFFE8697B)),   // mañana
    listOf(Color(0xFF3FA9F5), Color(0xFF7B61FF)),   // tarde
    listOf(Color(0xFF0D1B3E), Color(0xFF29115A))    // noche
)

/** Ahora suena bien: lo que sueles escuchar a esta hora; el fondo cambia con el momento del día. */
@Composable
internal fun MomentWidget(wd: WidgetData, editing: Boolean, onPlaySong: (List<Song>, Int) -> Unit) {
    val names = listOf(R.string.w_slot_0, R.string.w_slot_1, R.string.w_slot_2, R.string.w_slot_3)
    val icon = if (wd.slot == 1 || wd.slot == 2) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime
    Frame(Brush.linearGradient(slotBrushes[wd.slot])) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(Str.get(R.string.w_moment), style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        Str.get(names[wd.slot]) + if (wd.momentReal) "" else " · " + Str.get(R.string.w_moment_learning),
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (wd.momentSongs.isNotEmpty()) PlayDot(32.dp) { onPlaySong(wd.momentSongs, 0) }
            }
            Spacer(Modifier.height(8.dp))
            if (wd.momentSongs.isEmpty()) return@Column
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val side = min(maxHeight.value, 120f).dp.coerceAtLeast(28.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = !editing) {
                    itemsIndexed(wd.momentSongs) { i, s ->
                        Box(Modifier.size(side).pressable { onPlaySong(wd.momentSongs, i) }) {
                            Artwork(s.artUri, side, cornerRadius = 12.dp, requestPx = WART)
                        }
                    }
                }
            }
        }
    }
}

/** Mejores partes: una luna que se llena según cuántas canciones tienen su mejor parte marcada. */
@Composable
internal fun BestPartsWidget(wd: WidgetData, onPlayClips: (List<Pair<Song, Long>>) -> Unit) {
    val n = wd.bestClips.size
    val fill = if (wd.likedCount > 0) (n.toFloat() / wd.likedCount).coerceIn(0f, 1f) else if (n > 0) 1f else 0f
    val moon = Color(0xFFF3E9C6)
    Frame(Brush.radialGradient(listOf(Color(0xFF1C2350), Color(0xFF070A1C)))) {
        // Estrellitas de fondo.
        Canvas(Modifier.fillMaxSize()) {
            val r = kotlin.random.Random(7)
            repeat(26) { drawCircle(Color.White.copy(alpha = 0.15f + r.nextFloat() * 0.4f), 0.6.dp.toPx() + r.nextFloat() * 1.2.dp.toPx(), Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp).pressable { if (n > 0) onPlayClips(wd.bestClips.shuffled()) }) {
            val wide = maxWidth > maxHeight * 1.3f
            val side = if (wide) min(maxHeight.value, maxWidth.value * 0.45f).dp else min(maxWidth.value, (maxHeight - 38.dp).value).dp.coerceAtLeast(30.dp)
            val moonView: @Composable () -> Unit = {
                Canvas(Modifier.size(side)) {
                    val rad = size.minDimension / 2f * 0.86f
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(moon.copy(alpha = 0.10f), rad * 1.15f, c)
                    drawCircle(Color(0xFF2A2F55), rad, c)
                    // Parte iluminada: de derecha a izquierda, como las fases de la luna.
                    clipRectFill(this, c, rad, fill, moon)
                }
            }
            val text: @Composable () -> Unit = {
                Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
                    Text(Str.get(R.string.w_best), style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        if (n > 0) Str.plural(R.plurals.w_best_count, n) else Str.get(R.string.w_best_empty),
                        style = MaterialTheme.typography.labelSmall, color = moon.copy(alpha = 0.85f), maxLines = 2
                    )
                }
            }
            if (wide) Row(verticalAlignment = Alignment.CenterVertically) { moonView(); Spacer(Modifier.width(10.dp)); text() }
            else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { moonView(); Spacer(Modifier.height(4.dp)); text() }
        }
    }
}

private fun clipRectFill(scope: androidx.compose.ui.graphics.drawscope.DrawScope, c: Offset, rad: Float, fill: Float, color: Color) {
    with(scope) {
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c.x - rad, c.y - rad, c.x + rad, c.y + rad))
        }
        clipPath(path) {
            val left = c.x + rad - 2 * rad * fill
            drawRect(color, Offset(left, c.y - rad), Size(c.x + rad - left, 2 * rad))
        }
    }
}

/** Mosaico de colores: tus álbumes agrupados por el color de la carátula, como un arcoíris. */
@Composable
internal fun ColorsWidget(library: LibraryState, wd: WidgetData, onPlaySong: (List<Song>, Int) -> Unit) {
    val context = LocalContext.current
    val colors by AlbumColors.colors.collectAsStateWithLifecycle()
    LaunchedEffect(library.albums) { AlbumColors.ensure(context.applicationContext, library.albums) }
    val groups = remember(colors, library.albums) {
        val g = Array(9) { ArrayList<Album>() }
        for (a in library.albums) {
            val b = AlbumColors.bucket(colors[a.id] ?: 0)
            if (b >= 0) g[b] += a
        }
        g.mapIndexed { i, l -> i to l }.filter { it.second.isNotEmpty() }
    }
    Frame(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0E0E0E)))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Title(Str.get(R.string.w_colors), Icons.Rounded.Palette, Color.White)
            Spacer(Modifier.height(6.dp))
            if (groups.isEmpty()) { Empty(Str.get(R.string.w_colors_loading)); return@Column }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val n = groups.size
                val showArt = maxWidth / n >= 34.dp && maxHeight >= 70.dp
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                    for ((bucket, albums) in groups) {
                        val col = AlbumColors.bucketColors[bucket]
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.55f))))
                                .pressable { onPlaySong(albums.flatMap { it.songs }.shuffled(), 0) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (showArt) BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
                                val side = min(maxWidth.value, 64f).dp
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Artwork(albums.first().artUri, side, cornerRadius = 6.dp, requestPx = WART)
                                    Text("${albums.size}", style = MaterialTheme.typography.labelSmall, color = col.onAccent(), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Línea del tiempo: tus álbumes por década, en una tira que se desliza. */
@Composable
internal fun TimelineWidget(wd: WidgetData, editing: Boolean, onPlaySong: (List<Song>, Int) -> Unit) {
    val accent = Color(0xFF9AD0FF)
    Frame(Brush.horizontalGradient(listOf(Color(0xFF0F2132), Color(0xFF14142A)))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Title(Str.get(R.string.w_timeline), Icons.Rounded.History, accent)
            Spacer(Modifier.height(6.dp))
            if (wd.decades.isEmpty()) { Empty(Str.get(R.string.w_timeline_empty)); return@Column }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cardH = maxHeight
                val cardW = (cardH - 10.dp).coerceIn(48.dp, 200.dp)
                // La línea del tiempo que une las décadas.
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 3.dp).height(2.dp).background(accent.copy(alpha = 0.4f)))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = !editing) {
                    itemsIndexed(wd.decades) { _, d ->
                        Box(Modifier.size(cardW, cardH).pressable { onPlaySong(d.songs.shuffled(), 0) }) {
                            Artwork(d.albums.first().artUri, cardW, cornerRadius = 12.dp, requestPx = WART)
                            Box(Modifier.size(cardW, cardW).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
                            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 16.dp)) {
                                Text(decadeLabel(d.start), color = Color.White, fontWeight = FontWeight.Black, fontSize = if (cardH > 110.dp) 26.sp else 18.sp, maxLines = 1)
                                Text(Str.plural(R.plurals.albums_count, d.albums.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                            }
                            Box(Modifier.align(Alignment.BottomCenter).size(10.dp).background(accent, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

private fun decadeLabel(start: Int): String = if (start in 1900..1999) "'${(start % 100).toString().padStart(2, '0')}s" else "${start}s"

/** Lista fijada: collage de sus carátulas, nombre y número de canciones. */
@Composable
internal fun PlaylistWidget(playlist: Playlist?, songs: List<Song>, onOpen: () -> Unit, onPlaySong: (List<Song>, Int) -> Unit) {
    val arts = remember(songs) { songs.map { it.artUri }.distinct().take(4) }
    Frame(Brush.linearGradient(listOf(Color(0xFF2A1238), Color(0xFF120A1A)))) {
        if (playlist == null) { Empty(Str.get(R.string.home_unavailable)); return@Frame }
        BoxWithConstraints(Modifier.fillMaxSize().pressable(onOpen)) {
            val boxW = maxWidth
            // Collage de fondo: 1, 2 o 4 carátulas según cuántas haya.
            Column(Modifier.fillMaxSize()) {
                val rowsN = if (arts.size >= 4) 2 else 1
                for (r in 0 until rowsN) Row(Modifier.weight(1f)) {
                    val per = if (arts.size >= 4) 2 else arts.size.coerceAtLeast(1)
                    for (c in 0 until per) {
                        val u = arts.getOrNull(r * per + c)
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            if (u != null) Artwork(u, max(maxWidth.value, maxHeight.value).dp, cornerRadius = 0.dp, requestPx = WART, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xEE120A1A))))
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp).background(Color(0xFF9B4DFF), CircleShape).padding(3.dp))
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Str.plural(R.plurals.songs_count, songs.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
                if (songs.isNotEmpty() && boxW > 110.dp) PlayDot(30.dp, Color(0xFF9B4DFF), Color.White) { onPlaySong(songs, 0) }
            }
        }
    }
}

/** Continuar: lo último que sonaba, con su barra de progreso; un toque y sigue. */
@Composable
internal fun ContinueWidget() {
    val app = LocalContext.current.applicationContext as MelodiaApp
    val controller = app.playerController
    val state by controller.state.collectAsStateWithLifecycle()
    val pos by controller.position.collectAsStateWithLifecycle()
    val cur = state.current
    val accent = accentColor
    Frame(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), Color(0xFF101014)))) {
        if (cur == null) { Column(Modifier.padding(10.dp)) { Title(Str.get(R.string.w_continue), Icons.Rounded.PlayCircle); Empty(Str.get(R.string.w_continue_empty)) }; return@Frame }
        val progress = if (cur.durationMs > 0) (pos.toFloat() / cur.durationMs).coerceIn(0f, 1f) else 0f
        BoxWithConstraints(Modifier.fillMaxSize().pressable { if (state.isPlaying) controller.togglePlayPause() else controller.resume() }) {
            val tall = maxHeight > maxWidth * 0.9f
            val boxH = maxHeight
            if (tall) {
                // Cuadrado o alto: carátula grande con el progreso como aro.
                val side = min(maxWidth.value, (maxHeight - 34.dp).value).dp.coerceAtLeast(40.dp)
                Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                        Artwork(cur.artUri, side - 10.dp, circle = true, requestPx = WART)
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 4.dp.toPx()
                            drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                            drawArc(accent, -90f, 360f * progress, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White,
                            modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(6.dp))
                    }
                    Text(cur.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(cur.artUri, boxH - 16.dp, cornerRadius = 10.dp, requestPx = WART)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(Str.get(if (state.isPlaying) R.string.w_continue_now else R.string.w_continue), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
                    Text(cur.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cur.artist, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accent, RoundedCornerShape(2.dp)))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = accent.onAccent())
                }
            }
        }
    }
}

/** Recientes: lo último que escuchaste, como pilas de carátulas (más alta = más canciones del disco). */
@Composable
internal fun RecentWidget(wd: WidgetData, tileW: Int, tileH: Int, onPlaySong: (List<Song>, Int) -> Unit) {
    Frame(Brush.verticalGradient(listOf(Color(0xFF1E2A24), Color(0xFF0F1512)))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Title(Str.get(R.string.w_recent), Icons.Rounded.Schedule, Color(0xFFA8E6CF))
            Spacer(Modifier.height(6.dp))
            if (wd.recent.isEmpty()) { Empty(Str.get(R.string.w_need_plays)); return@Column }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cols = tileW.coerceIn(2, 8).let { if (maxWidth / it < 56.dp) max(1, (maxWidth / 56.dp).toInt()) else it }
                val rows = max(1, min(tileH, (maxHeight / 70.dp).toInt()))
                val gap = 10.dp
                val cellW = (maxWidth - gap * (cols - 1)) / cols
                val cellH = (maxHeight - gap * (rows - 1)) / rows
                val side = min(cellW.value, cellH.value).dp - 8.dp
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until cols) {
                            val item = wd.recent.getOrNull(r * cols + c)
                            Box(Modifier.size(cellW, cellH), contentAlignment = Alignment.Center) {
                                if (item != null) Box(Modifier.size(side + 8.dp).pressable {
                                    val idx = item.album.songs.indexOfFirst { it.id == item.song.id }.coerceAtLeast(0)
                                    onPlaySong(item.album.songs, idx)
                                }) {
                                    val depth = item.count.coerceIn(1, 3)
                                    // Hojas de atrás de la pila, giradas.
                                    for (k in depth - 1 downTo 1) {
                                        Box(
                                            Modifier.size(side).offset(x = (4 * k).dp, y = 0.dp)
                                                .graphicsLayer { rotationZ = 5f * k }
                                                .background(Color.White.copy(alpha = 0.10f + 0.05f * k), RoundedCornerShape(8.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Artwork(item.album.artUri, side, cornerRadius = 8.dp, requestPx = WART)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** De Navidrome: lo último que se añadió al servidor, con su etiqueta "Nuevo". */
@Composable
internal fun ServerNewWidget(library: LibraryState, wd: WidgetData, editing: Boolean, onAlbum: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as MelodiaApp
    val config by app.serverSettings.config.collectAsStateWithLifecycle()
    val actions = LocalHomeActions.current
    val ids by produceState(WidgetCache.serverNew?.second ?: emptyList(), config) {
        if (!config.isActive) { value = emptyList(); return@produceState }
        val cached = WidgetCache.serverNew
        if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60_000L) { value = cached.second; return@produceState }
        val got = runCatching { SubsonicClient.newestAlbums(config).map { SubsonicClient.albumLocalId(config, it) } }.getOrNull()
        if (got != null) { WidgetCache.serverNew = System.currentTimeMillis() to got; value = got }
    }
    val albums = remember(ids, wd.albumsById) { ids.mapNotNull { wd.albumsById[it] } }
    val green = Color(0xFF3DDC97)
    Frame(Brush.linearGradient(listOf(Color(0xFF0E2A3A), Color(0xFF0A1218)))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Title(Str.get(R.string.w_server_new), Icons.Rounded.Cloud, Color(0xFF7FD3FF))
            Spacer(Modifier.height(6.dp))
            if (!config.isActive) {
                Box(Modifier.fillMaxSize().pressable(actions.onOpenServer), contentAlignment = Alignment.Center) { Hint(Str.get(R.string.w_server_off)) }
                return@Column
            }
            if (albums.isEmpty()) { Empty(Str.get(R.string.w_loading)); return@Column }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val side = maxHeight.coerceAtMost(160.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = !editing) {
                    itemsIndexed(albums) { i, a ->
                        Box(Modifier.size(side).pressable { onAlbum(a.id) }) {
                            Artwork(a.artUri, side, cornerRadius = 12.dp, requestPx = WART)
                            if (i < 10) Text(
                                Str.get(R.string.w_new), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).background(green, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Last.fm: tu artista del mes y un gráfico con tus escuchas de los últimos 7 días. */
@Composable
internal fun LastFmWidget(wd: WidgetData, library: LibraryState, onArtist: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as MelodiaApp
    val user by app.lastfm.user.collectAsStateWithLifecycle()
    val actions = LocalHomeActions.current
    val red = Color(0xFFE31B23)
    val data by produceState(WidgetCache.lastfm?.second, user) {
        if (user == null) { value = null; return@produceState }
        val cached = WidgetCache.lastfm
        if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60_000L) { value = cached.second; return@produceState }
        val got = runCatching {
            val top = app.lastfm.topArtists(LfmPeriod.MONTH, 1).firstOrNull()
            LfmWidget(top?.title.orEmpty(), top?.plays ?: 0, app.lastfm.scrobblesPerDay(7))
        }.getOrNull()
        if (got != null) { WidgetCache.lastfm = System.currentTimeMillis() to got; value = got }
    }
    Frame(Brush.linearGradient(listOf(Color(0xFF3A0B0E), Color(0xFF150708)))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Title("Last.fm", Icons.Rounded.BarChart, Color(0xFFFF6B6B))
            Spacer(Modifier.height(6.dp))
            if (user == null) {
                Box(Modifier.fillMaxSize().pressable(actions.onOpenLastFm), contentAlignment = Alignment.Center) { Hint(Str.get(R.string.w_lastfm_off)) }
                return@Column
            }
            val d = data
            if (d == null) { Empty(Str.get(R.string.w_loading)); return@Column }
            val local = remember(d.artist, library.artists) { library.artists.firstOrNull { it.name.equals(d.artist, ignoreCase = true) } }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight * 1.2f
                val artist: @Composable () -> Unit = {
                    Row(Modifier.pressable { local?.let { onArtist(it.name) } }, verticalAlignment = Alignment.CenterVertically) {
                        if (local != null) { Artwork(local.artUri, 38.dp, circle = true, requestPx = WART); Spacer(Modifier.width(8.dp)) }
                        Column {
                            Text(Str.get(R.string.w_lastfm_artist), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                            Text(d.artist.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (d.plays > 0) Text(Str.plural(R.plurals.w_scrobbles, d.plays), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6B6B), maxLines = 1)
                        }
                    }
                }
                val chart: @Composable (Modifier) -> Unit = { m ->
                    val maxV = (d.days.maxOrNull() ?: 0).coerceAtLeast(1)
                    Row(m, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        d.days.forEachIndexed { i, v ->
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / maxV).coerceAtLeast(0.04f) * 0.85f)
                                        .background(if (i == d.days.lastIndex) red else red.copy(alpha = 0.55f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                            }
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { artist() }
                    Spacer(Modifier.width(10.dp))
                    chart(Modifier.weight(1f).fillMaxHeight())
                } else Column(Modifier.fillMaxSize()) {
                    artist(); Spacer(Modifier.height(6.dp)); chart(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
