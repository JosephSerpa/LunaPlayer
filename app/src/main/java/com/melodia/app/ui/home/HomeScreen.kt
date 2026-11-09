package com.melodia.app.ui.home

import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.horizontalScroll
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.geometry.Size as GSize
import com.melodia.app.ui.theme.LocalPalette
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset as GOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.melodia.app.data.SearchKeys
import com.melodia.app.ui.components.SongRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.zIndex
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.Album
import com.melodia.app.data.Artist
import com.melodia.app.data.HomeLayoutStore
import com.melodia.app.data.HomeTile
import com.melodia.app.data.PlayCount
import com.melodia.app.data.Song
import com.melodia.app.data.TileKind
import com.melodia.app.data.UNKNOWN_ARTIST
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.LocalMarks
import com.melodia.app.ui.components.HelpBadge
import com.melodia.app.ui.components.LocalSongMenu
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.pressableWithLongClick
import com.melodia.app.ui.library.LibraryState
import com.melodia.app.ui.theme.ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Un género con su carátula representativa y sus canciones. */
data class GenreItem(val name: String, val artUri: Uri?, val songs: List<Song>)

/** Lo más escuchado, calculado en segundo plano cada vez que cambian la biblioteca o las escuchas. */
private class HomeData(
    val quick: List<Song>,
    val likes: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<GenreItem>,
    /** Canciones que hace mucho no suenan (o nunca): para redescubrir. */
    val forgotten: List<Song>,
    /** Álbumes en orden aleatorio del día: el "álbum sorpresa" va pasando por ellos. */
    val discover: List<Album>,
    val allSongs: List<Song>,
    val songsById: Map<Long, Song>,
    val albumsById: Map<Long, Album>,
    val artistsByName: Map<String, Artist>
)

private const val QUICK_MAX = 20
private const val TILE_ITEMS = 24

/** Tamaño fijo con que se piden las carátulas de los cuadros: al redimensionar no se vuelven a cargar. */
private const val ART_PX = 300

private fun computeHome(
    lib: LibraryState, stats: Map<Long, PlayCount>, liked: Set<Long>, all: Boolean = false
): HomeData {
    val quickMax = if (all) 60 else QUICK_MAX
    val top = if (all) Int.MAX_VALUE else TILE_ITEMS
    val byId = lib.songs.associateBy { it.id }
    fun plays(s: Song) = stats[s.id]?.count ?: 0

    // Quick picks: lo que más escuchas; si aún hay pocas escuchas, se completa con tus me gusta
    // y algunas al azar (cambian una vez al día, no cada vez que se abre la app).
    val day = System.currentTimeMillis() / 86_400_000L
    val quick = LinkedHashSet<Song>()
    stats.entries.asSequence()
        .filter { it.key in byId }
        .sortedWith(compareByDescending<Map.Entry<Long, PlayCount>> { it.value.count }.thenByDescending { it.value.lastPlayed })
        .take(quickMax)
        .forEach { quick += byId.getValue(it.key) }
    if (quick.size < quickMax) {
        lib.songs.filter { it.id in liked }.shuffled(Random(day)).forEach { if (quick.size < quickMax) quick += it }
    }
    if (quick.size < quickMax) {
        lib.songs.shuffled(Random(day + 1)).take(quickMax * 2).forEach { if (quick.size < quickMax) quick += it }
    }

    val albumPlays = HashMap<Long, Int>()
    val artistPlays = HashMap<String, Int>()
    val genrePlays = HashMap<String, Int>()
    for ((id, c) in stats) {
        val s = byId[id] ?: continue
        albumPlays[s.albumId] = (albumPlays[s.albumId] ?: 0) + c.count
        artistPlays[s.artist] = (artistPlays[s.artist] ?: 0) + c.count
        if (s.genre.isNotBlank()) genrePlays[s.genre] = (genrePlays[s.genre] ?: 0) + c.count
    }
    val albums = lib.albums
        .sortedWith(compareByDescending<Album> { albumPlays[it.id] ?: 0 }.thenByDescending { it.songs.size })
        .take(top)
    val artists = lib.artists.filter { it.name != UNKNOWN_ARTIST }
        .sortedWith(compareByDescending<Artist> { artistPlays[it.name] ?: 0 }.thenByDescending { it.songs.size })
        .take(top)
    val genres = lib.songs.filter { it.genre.isNotBlank() }.groupBy { it.genre }
        .map { (name, songs) ->
            val top = songs.maxByOrNull { plays(it) } ?: songs.first()
            GenreItem(name, top.artUri, songs.sortedByDescending { plays(it) })
        }
        .sortedWith(compareByDescending<GenreItem> { genrePlays[it.name] ?: 0 }.thenByDescending { it.songs.size })
        .take(top)

    val likes = lib.songs.filter { it.id in liked }
        .sortedWith(compareByDescending<Song> { plays(it) }.thenBy { it.title })
        .take(if (all) Int.MAX_VALUE else 40)

    // Redescubrir: nunca escuchadas o sin sonar desde hace 45 días; cambian una vez al día.
    val old = System.currentTimeMillis() - 45L * 86_400_000L
    val forgotten = lib.songs
        .filter { s -> stats[s.id]?.let { it.lastPlayed < old } ?: true }
        .shuffled(Random(day + 7))
        .take(if (all) 200 else 40)
    val discover = lib.albums.shuffled(Random(day + 3)).take(if (all) Int.MAX_VALUE else 60)

    return HomeData(
        quick = quick.toList(), likes = likes, albums = albums, artists = artists, genres = genres,
        forgotten = forgotten, discover = discover, allSongs = lib.songs,
        songsById = byId,
        albumsById = lib.albums.associateBy { it.id },
        artistsByName = lib.artists.associateBy { it.name }
    )
}

/**
 * Pantalla principal (como YouTube Music o Spotify): una cuadrícula de 4 × 6 con lo que más
 * escuchas. El botón de editar (arriba a la derecha) hace que los cuadros tiemblen: se pueden
 * arrastrar a otro lugar o quitar. Canciones, álbumes y artistas se fijan desde su menú.
 */
@Composable
fun HomeScreen(
    library: LibraryState,
    tiles: List<HomeTile>,
    stats: Map<Long, PlayCount>,
    hiddenBuiltIns: List<TileKind>,
    onMove: (key: String, col: Int, row: Int) -> Unit,
    onResize: (key: String, w: Int, h: Int) -> Unit,
    onOpenList: (TileKind) -> Unit,
    onRemove: (key: String) -> Unit,
    onAddBuiltIn: (TileKind) -> Unit,
    onReset: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenOptions: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onGenre: (String) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    /** Estado de edición compartido con la cabecera común (páginas de inicio). */
    editingState: androidx.compose.runtime.MutableState<Boolean>? = null,
    /** Dentro de las páginas de inicio: la cabecera la pone [MainPages]. */
    embedded: Boolean = false,
    /** Columnas que caben a lo ancho (la tienda reacomoda los cuadros). */
    onCols: (Int) -> Unit = {},
    /** Fija una lista de reproducción en Inicio. */
    onPinPlaylist: (Long) -> Unit = {}
) {
    val ownEditing = rememberSaveable { mutableStateOf(false) }
    var editing by (editingState ?: ownEditing)
    var searching by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }
    if (searching) {
        BackHandler { searching = false }
        SearchSongs(library, onBack = { searching = false }, onPlaySong = onPlaySong)
        return
    }
    val marks = LocalMarks.current
    val liked = remember(marks) { marks.filterValues { it.liked }.keys }
    val data by produceState<HomeData?>(null, library.songs, library.albums, stats, liked) {
        value = withContext(Dispatchers.Default) { computeHome(library, stats, liked) }
    }
    // Datos de los widgets (top de la semana, rachas, estrellas…): se recalculan al cambiar algo.
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp
    val log by app.stats.log.collectAsStateWithLifecycle()
    val albumMarks = com.melodia.app.ui.components.LocalAlbumMarks.current
    val slotNow = slotOf(System.currentTimeMillis())
    val widgets by produceState<WidgetData?>(null, library.songs, library.albums, stats, log, marks, albumMarks, slotNow) {
        value = withContext(Dispatchers.Default) { computeWidgets(library, stats, log, marks, albumMarks) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 150.dp)
    ) {
        // --- Cabecera: título y, a la derecha, biblioteca, ajustes y editar ---
        if (!embedded) Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Str.get(R.string.home_title), style = MaterialTheme.typography.displayLarge, color = ink)
                    if (editing) HelpBadge(Str.get(R.string.home_edit_hint), Modifier.padding(start = 6.dp), Str.get(R.string.home_edit))
                }
            }
            if (!editing) {
                HeaderButton(Icons.Rounded.Search, Str.get(R.string.search_songs)) { searching = true }
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.LibraryMusic, Str.get(R.string.home_library), onClick = onOpenLibrary)
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.Settings, Str.get(R.string.options), onClick = onOpenOptions)
                Spacer(Modifier.width(8.dp))
            }
            HeaderButton(
                if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                Str.get(if (editing) R.string.home_done else R.string.home_edit),
                highlighted = editing
            ) { editing = !editing }
        }

        val d = data
        if (d == null || !library.loaded) {
            Spacer(Modifier.height(40.dp))
            return@Column
        }

        HomeGrid(
            tiles = tiles,
            data = d,
            wd = widgets,
            library = library,
            editing = editing,
            onMove = onMove,
            onResize = onResize,
            onOpenList = onOpenList,
            onAddTile = { adding = true },
            onRemove = onRemove,
            onPlaySong = onPlaySong,
            onAlbum = onAlbum,
            onArtist = onArtist,
            onGenre = onGenre,
            onCols = onCols
        )

        if (adding) {
            AddTileDialog(
                data = d,
                wd = widgets,
                library = library,
                onPinPlaylist = { adding = false; onPinPlaylist(it) },
                onPick = { adding = false; onAddBuiltIn(it) },
                onReset = { adding = false; onReset() },
                onDismiss = { adding = false }
            )
        }
        if (!editing && tiles.isEmpty()) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Str.get(R.string.home_empty_short),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HelpBadge(Str.get(R.string.home_empty), Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** Dónde se busca el texto escrito. */
private enum class SearchField { ALL, TITLE, ARTIST, ALBUM, GENRE, FOLDER }

/** Cómo se ordenan los resultados. */
private enum class SearchSort { RELEVANCE, STARS, LIKES }

/** Nombre de la carpeta de una canción (la última parte de su ruta). */
private fun folderName(s: Song) = s.path.substringBeforeLast('/', "").substringAfterLast('/')

/**
 * Buscador de canciones: se abre con la lupa de Inicio. Con resultados en pantalla se puede elegir
 * dónde buscar (todo, canción, artista, álbum, género o carpeta) y ordenarlos por estrellas o me gusta.
 */
@Composable
internal fun SearchSongs(library: LibraryState, onBack: () -> Unit, onPlaySong: (List<Song>, Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var fieldRaw by rememberSaveable { mutableStateOf(SearchField.ALL.ordinal) }
    var sortRaw by rememberSaveable { mutableStateOf(SearchSort.RELEVANCE.ordinal) }
    val field = SearchField.entries[fieldRaw]
    val sort = SearchSort.entries[sortRaw]
    val marks = LocalMarks.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = remember(query) { SearchKeys.norm(query.trim()) }
    // Se filtra y ordena fuera del hilo de la interfaz: con miles de canciones no traba al escribir.
    val results by produceState(emptyList<Song>(), q, field, sort, library.songs, library.songKeys, marks) {
        value = if (q.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            val words = q.split(' ').filter { it.isNotEmpty() }
            fun key(s: Song): String = when (field) {
                SearchField.ALL -> library.songKeys[s.id] ?: SearchKeys.song(s)
                SearchField.TITLE -> SearchKeys.norm(s.title)
                SearchField.ARTIST -> SearchKeys.norm(s.artist)
                SearchField.ALBUM -> SearchKeys.norm(s.album)
                SearchField.GENRE -> SearchKeys.norm(s.genre)
                SearchField.FOLDER -> SearchKeys.norm(folderName(s))
            }
            val found = library.songs.filter { s -> val k = key(s); words.all { it in k } }
            // "Relevancia" agrupa por lo que se busca (p. ej. por artista y luego por álbum).
            val grouped = when (field) {
                SearchField.ARTIST -> found.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }))
                SearchField.ALBUM -> found.sortedWith(compareBy({ it.album.lowercase() }, { it.artist.lowercase() }))
                SearchField.GENRE -> found.sortedWith(compareBy({ it.genre.lowercase() }, { it.artist.lowercase() }))
                SearchField.FOLDER -> found.sortedWith(compareBy({ folderName(it).lowercase() }, { it.title.lowercase() }))
                else -> found
            }
            when (sort) {
                SearchSort.RELEVANCE -> grouped
                SearchSort.STARS -> grouped.sortedByDescending { marks[it.id]?.rating ?: 0 }
                SearchSort.LIKES -> grouped.sortedWith(
                    compareByDescending<Song> { marks[it.id]?.liked == true }.thenByDescending { marks[it.id]?.rating ?: 0 }
                )
            }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderButton(Icons.AutoMirrored.Rounded.ArrowBack, Str.get(R.string.home_title), onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).glass(RoundedCornerShape(50), fill = 0.08f).padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            Str.get(R.string.search_songs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
                        cursorBrush = SolidColor(ink),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, Str.get(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).pressable { query = "" }
                    )
                }
            }
        }
        // Filtros: aparecen cuando hay algo escrito.
        if (q.isNotEmpty()) SearchFilters(field, sort, results.size, onField = { fieldRaw = it.ordinal }, onSort = { sortRaw = it.ordinal })
        if (q.isNotEmpty() && results.isEmpty()) {
            Text(
                Str.get(R.string.no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            items(results, key = { it.id }) { song ->
                SongRow(song = song, onClick = { onPlaySong(results, results.indexOf(song)) })
            }
        }
    }
}

@Composable
private fun SearchFilters(
    field: SearchField,
    sort: SearchSort,
    count: Int,
    onField: (SearchField) -> Unit,
    onSort: (SearchSort) -> Unit
) {
    val accent = accentColor
    val chipInk = ink
    val chipInverse = com.melodia.app.ui.theme.inverse
    @Composable
    fun chip(label: String, on: Boolean, onColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
        val fg = if (on) (if (onColor == accent) accent.onAccent() else chipInverse) else chipInk.copy(alpha = 0.8f)
        Row(
            Modifier
                .pressable(onClick)
                .background(if (on) onColor else chipInk.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (on) fg else accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fields = listOf(
            SearchField.ALL to Str.get(R.string.search_in_all),
            SearchField.TITLE to Str.get(R.string.tab_songs),
            SearchField.ARTIST to Str.get(R.string.tab_artists),
            SearchField.ALBUM to Str.get(R.string.tab_albums),
            SearchField.GENRE to Str.get(R.string.search_in_genre),
            SearchField.FOLDER to Str.get(R.string.tab_folders)
        )
        for ((f, label) in fields) chip(label, field == f, chipInk) { onField(f) }
        Box(Modifier.width(1.dp).height(20.dp).background(chipInk.copy(alpha = 0.15f)))
        chip(Str.get(R.string.search_sort_relevance), sort == SearchSort.RELEVANCE, accent) { onSort(SearchSort.RELEVANCE) }
        chip(Str.get(R.string.search_sort_stars), sort == SearchSort.STARS, accent, Icons.Rounded.Star) { onSort(SearchSort.STARS) }
        chip(Str.get(R.string.search_sort_likes), sort == SearchSort.LIKES, accent, Icons.Rounded.Favorite) { onSort(SearchSort.LIKES) }
        HelpBadge(Str.get(R.string.search_filters_help))
    }
    Text(
        Str.plural(R.plurals.songs_count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)
    )
}

@Composable
private fun tileLabel(k: TileKind): String = when (k) {
    TileKind.QUICK -> Str.get(R.string.home_quick_picks)
    TileKind.ALBUMS -> Str.get(R.string.home_top_albums)
    TileKind.ARTISTS -> Str.get(R.string.home_top_artists)
    TileKind.GENRES -> Str.get(R.string.home_top_genres)
    TileKind.LIKES -> Str.get(R.string.home_likes)
    TileKind.SHUFFLE -> Str.get(R.string.home_shuffle)
    TileKind.DISCOVER -> Str.get(R.string.home_discover)
    TileKind.FORGOTTEN -> Str.get(R.string.home_forgotten)
    TileKind.CLOCK -> Str.get(R.string.home_clock)
    TileKind.TOP5 -> Str.get(R.string.w_top5)
    TileKind.ALBUM_MONTH -> Str.get(R.string.w_album_month)
    TileKind.STREAK -> Str.get(R.string.w_streak)
    TileKind.SHELF -> Str.get(R.string.w_shelf)
    TileKind.STARS -> Str.get(R.string.w_stars)
    TileKind.MOMENT -> Str.get(R.string.w_moment)
    TileKind.BEST_PARTS -> Str.get(R.string.w_best)
    TileKind.COLORS -> Str.get(R.string.w_colors)
    TileKind.TIMELINE -> Str.get(R.string.w_timeline)
    TileKind.CONTINUE -> Str.get(R.string.w_continue)
    TileKind.RECENT -> Str.get(R.string.w_recent)
    TileKind.SERVER_NEW -> Str.get(R.string.w_server_new)
    TileKind.LASTFM -> "Last.fm"
    else -> ""
}

@Composable
internal fun HeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val accent = accentColor
    Box(
        Modifier
            .size(46.dp)
            .pressable(onClick)
            .then(
                if (highlighted) Modifier.background(accent, CircleShape)
                else Modifier.glass(CircleShape, fill = 0.12f, border = 0.2f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (highlighted) accent.onAccent() else ink)
    }
}

/** Elegir qué cuadro añadir en Inicio: cada uno se ve tal como quedará antes de añadirlo. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddTileDialog(
    data: HomeData, wd: WidgetData?, library: LibraryState, onPinPlaylist: (Long) -> Unit,
    onPick: (TileKind) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp
    val playlists by app.playlists.playlists.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(palette.bg.copy(alpha = 0.97f), RoundedCornerShape(26.dp))
                .glass(RoundedCornerShape(26.dp), fill = 0.06f, border = 0.16f)
                .padding(vertical = 14.dp)
        ) {
            Text(
                Str.get(R.string.home_add),
                style = MaterialTheme.typography.titleLarge, color = ink,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )
            BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                // Escala común: un cuadro de 4 de ancho ocupa la mitad del diálogo menos el margen.
                val full = 84.dp
                val gap = 8.dp
                val scale = ((maxWidth - 16.dp * 2 - 12.dp) / 2f) / (full * 4 + gap * 3)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (k in ADDABLE) {
                        val def = HomeLayoutStore.defaultTile(k) ?: continue
                        Column(
                            Modifier.pressable { onPick(k) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val w = full * def.w + gap * (def.w - 1)
                            val h = full * 1.05f * def.h + gap * (def.h - 1)
                            // Vista previa real del cuadro, dibujada a tamaño normal y reducida.
                            Box(
                                Modifier
                                    .size(w * scale, h * scale)
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                            ) {
                                Box(
                                    Modifier
                                        .requiredSize(w, h)
                                        .graphicsLayer {
                                            scaleX = scale; scaleY = scale
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                        }
                                ) {
                                    TileContent(def, data, wd, library, editing = true, onPlaySong = { _, _ -> }, onAlbum = {}, onArtist = {}, onGenre = {}, onOpenList = {})
                                    // Tapa los toques: en la vista previa nada responde, solo se elige.
                                    // No los consume, así deslizar encima de un cuadro sigue desplazando la lista.
                                    Box(Modifier.matchParentSize().pointerInput(Unit) {
                                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                                    })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tileLabel(k), style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
                            }
                        }
                    }
                    // Listas de reproducción: se fijan como un cuadro con su collage.
                    if (playlists.isNotEmpty()) {
                        Text(
                            Str.get(R.string.w_pin_playlist),
                            style = MaterialTheme.typography.titleSmall, color = ink,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                        for (p in playlists) {
                            Chip(Icons.Rounded.Add, p.name) { onPinPlaylist(p.id) }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().pressable(onReset).padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(Str.get(R.string.home_reset), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Cuadros que se pueden añadir con el "+" (en este orden). */
private val ADDABLE = listOf(
    TileKind.TOP5, TileKind.CONTINUE, TileKind.ALBUM_MONTH, TileKind.STREAK, TileKind.SHELF, TileKind.STARS,
    TileKind.MOMENT, TileKind.BEST_PARTS, TileKind.RECENT, TileKind.COLORS, TileKind.TIMELINE,
    TileKind.SERVER_NEW, TileKind.LASTFM,
    TileKind.SHUFFLE, TileKind.CLOCK, TileKind.QUICK, TileKind.LIKES, TileKind.DISCOVER,
    TileKind.FORGOTTEN, TileKind.ALBUMS, TileKind.ARTISTS, TileKind.GENRES
)

/** Aleatorio: toda la música mezclada, de un toque. Fondo con carátulas del día. */
@Composable
private fun ShuffleTile(data: HomeData, tile: HomeTile, onClick: () -> Unit) {
    val accent = accentColor
    val arts = remember(data.discover) { data.discover.take(8).map { it.artUri } }
    Box(
        Modifier
            .fillMaxSize()
            .pressable(onClick)
            .clip(RoundedCornerShape(18.dp))
            .glass(RoundedCornerShape(18.dp), fill = 0.08f, border = 0.12f)
    ) {
        // Tira de carátulas, muy tenue, detrás.
        Row(Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f }) {
            val n = (tile.w * 2).coerceIn(2, 8)
            for (i in 0 until n) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val u = arts.getOrNull(i)
                    if (u != null) BoxWithConstraints(Modifier.fillMaxSize()) {
                        Artwork(u, maxOf(maxWidth, maxHeight), cornerRadius = 0.dp, requestPx = ART_PX,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.15f)))
            )
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shuffle, null, tint = accent.onAccent(), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Str.get(R.string.home_shuffle), style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Str.plural(R.plurals.songs_count, data.allSongs.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

/** Álbum sorpresa: uno distinto cada día; el dado salta a otro. */
@Composable
private fun DiscoverTile(data: HomeData, editing: Boolean, onAlbum: (Long) -> Unit, onPlaySong: (List<Song>, Int) -> Unit) {
    var idx by rememberSaveable { mutableStateOf(0) }
    val album = data.discover.getOrNull(idx % data.discover.size.coerceAtLeast(1))
    TileSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TileLabel(Str.get(R.string.home_discover)) }
            Box(
                Modifier.size(26.dp).pressable { if (!editing) idx++ },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Casino, Str.get(R.string.home_discover_next), tint = accentColor, modifier = Modifier.size(18.dp)) }
        }
        if (album == null) return@TileSurface
        BoxWithConstraints(Modifier.fillMaxSize().pressable { onAlbum(album.id) }) {
            val side = minOf(maxWidth, maxHeight)
            Box(Modifier.size(maxWidth, maxHeight)) {
                Crossfade(album.id, label = "discover") { _ ->
                    Artwork(album.artUri, side, cornerRadius = 12.dp, requestPx = ART_PX, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                    )
                )
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(album.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(32.dp).background(Color.White, CircleShape).pressable { if (!editing) onPlaySong(album.songs, 0) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

/** Reloj minimalista: hora grande y fina, y la fecha debajo. Se ajusta al tamaño del cuadro. */
@Composable
private fun ClockTile() {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            // Despierta justo al cambiar de minuto.
            kotlinx.coroutines.delay(60_000L - value % 60_000L + 50L)
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = remember(now, is24) { java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm", locale).format(java.util.Date(now)) }
    val date = remember(now, locale) {
        java.text.SimpleDateFormat("EEEE, d MMMM", locale).format(java.util.Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    Box(
        Modifier.fillMaxSize().glass(RoundedCornerShape(18.dp), fill = 0.05f, border = 0.10f),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val wide = maxWidth > maxHeight * 1.6f
            val lineH = maxHeight * 0.5f
            // Tamaño de la hora según el espacio: llena el cuadro sin cortarse.
            val byH = maxHeight * (if (wide) 0.62f else 0.42f)
            val byW = maxWidth / (if (wide) 3.4f else 2.8f) * 1.0f
            val size = with(density) { minOf(byH, byW).toSp() }
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time, fontSize = size, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraLight, color = ink, maxLines = 1, letterSpacing = (-1).sp)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.width(1.dp).height(lineH).background(ink.copy(alpha = 0.25f)))
                    Spacer(Modifier.width(14.dp))
                    Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(time, fontSize = size, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraLight, color = ink, maxLines = 1, letterSpacing = (-1).sp)
                    Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick)
            .glass(RoundedCornerShape(50), fill = 0.10f, border = 0.16f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Cuadrícula 8 × 8
// ---------------------------------------------------------------------------

@Composable
private fun HomeGrid(
    tiles: List<HomeTile>,
    data: HomeData,
    wd: WidgetData?,
    library: LibraryState,
    editing: Boolean,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onOpenList: (TileKind) -> Unit,
    onAddTile: () -> Unit,
    onRemove: (String) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onGenre: (String) -> Unit,
    onCols: (Int) -> Unit
) {
    // Temblor del modo edición: un solo reloj para todos; cada cuadro con su propio desfase.
    // Solo existe mientras se edita: fuera de ese modo la pantalla no dibuja cuadros de más.
    val still = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val phase: androidx.compose.runtime.State<Float> = if (editing) {
        val wiggle = rememberInfiniteTransition(label = "wiggle")
        wiggle.animateFloat(
            0f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing)), label = "wigglePhase"
        )
    } else still
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    BoxWithConstraints(Modifier.widthIn(max = 1600.dp).fillMaxWidth().padding(horizontal = 14.dp)) {
        // Más columnas en pantallas anchas (tablet, sobre todo en horizontal): caben más cuadros a lo ancho.
        val cols = when {
            maxWidth >= 900.dp -> 8
            maxWidth >= 600.dp -> 6
            else -> HomeLayoutStore.COLS
        }
        SideEffect { onCols(cols) }
        // Mientras se arrastra un cuadro, los demás ya se ven donde quedarán al soltarlo.
        var hover by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
        val shown = remember(tiles, hover, cols) {
            hover?.let { (k, c, r) -> HomeLayoutStore.arrange(tiles, k, c, r, cols, HomeLayoutStore.rowsFor(cols)) } ?: tiles
        }
        val rows = maxOf(HomeLayoutStore.rowsFor(cols), shown.maxOfOrNull { it.row + it.h } ?: 0, tiles.maxOfOrNull { it.row + it.h } ?: 0)
        val gap = 8.dp
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        // Celdas algo más altas que anchas: deja sitio al texto de cada cuadro.
        val cellH = cellW * 1.05f
        val usedRows = if (editing) rows else (tiles.maxOfOrNull { it.row + it.h } ?: 0)
        val gridH = if (usedRows == 0) 0.dp else cellH * usedRows + gap * (usedRows - 1)
        val density = LocalDensity.current
        val stepX = with(density) { (cellW + gap).toPx() }
        val stepY = with(density) { (cellH + gap).toPx() }

        Box(Modifier.fillMaxWidth().height(gridH)) {
            // En edición se ven todas las celdas, para saber dónde se puede soltar.
            if (editing) {
                val cellColor = ink.copy(alpha = 0.05f)
                for (r in 0 until rows) for (c in 0 until cols) {
                    if (shown.any { c >= it.col && c < it.col + it.w && r >= it.row && r < it.row + it.h }) continue
                    Box(
                        Modifier
                            .offset((cellW + gap) * c, (cellH + gap) * r)
                            .size(cellW, cellH)
                            .background(cellColor, RoundedCornerShape(18.dp))
                    )
                }
                // Cuadro "+" en el primer hueco libre: añade cuadros nuevos o los que se quitaron.
                if (hover == null) HomeLayoutStore.freeSpot(tiles, 1, 1, cols, rows)?.let { (c, r) ->
                    Box(
                        Modifier
                            .offset((cellW + gap) * c, (cellH + gap) * r)
                            .size(cellW, cellH)
                            .zIndex(3f)
                            .pressable(onAddTile)
                            .glass(RoundedCornerShape(18.dp), fill = 0.10f, border = 0.22f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, Str.get(R.string.home_add), tint = ink, modifier = Modifier.size(32.dp))
                    }
                }
            }
            // Hueco donde caerá el cuadro que se arrastra.
            hover?.let { (k, _, _) ->
                shown.firstOrNull { it.key == k }?.let { g ->
                    val gx by animateDpAsState((cellW + gap) * g.col, tween(200), label = "ghostX")
                    val gy by animateDpAsState((cellH + gap) * g.row, tween(200), label = "ghostY")
                    Box(
                        Modifier
                            .offset { IntOffset(gx.roundToPx(), gy.roundToPx()) }
                            .size(cellW * g.w + gap * (g.w - 1), cellH * g.h + gap * (g.h - 1))
                            .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                            .border(2.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    )
                }
            }
            val lifePref by (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp)
                .ui.homeLife.collectAsStateWithLifecycle()
            tiles.forEachIndexed { index, t ->
                key(t.key) {
                    // El que se arrastra sigue al dedo desde su sitio; los demás muestran el reacomodo.
                    val view = if (hover?.first == t.key) t else shown.firstOrNull { it.key == t.key } ?: t
                    TileHost(
                        tile = view, index = index, cellW = cellW, cellH = cellH, gap = gap, gridCols = cols, gridRows = rows,
                        stepX = stepX, stepY = stepY, editing = editing, phase = phase,
                        onHover = { k, c, r ->
                            val h = Triple(k, c, r)
                            if (hover != h) hover = h
                        },
                        onDrop = { k, c, r -> onMove(k, c, r); hover = null },
                        onDragCancel = { hover = null },
                        onResize = onResize, onRemove = onRemove
                    ) {
                        // Vida de Inicio: apagada mientras se edita o si el usuario la desactivó.
                        androidx.compose.runtime.CompositionLocalProvider(LocalHomeLife provides (lifePref && !editing)) {
                            Box(Modifier.fillMaxSize().tileLife(t.key, t.kind)) {
                                TileContent(t, data, wd, library, editing, onPlaySong, onAlbum, onArtist, onGenre, onOpenList)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TileHost(
    tile: HomeTile,
    index: Int,
    cellW: Dp,
    cellH: Dp,
    gap: Dp,
    gridCols: Int,
    gridRows: Int,
    stepX: Float,
    stepY: Float,
    editing: Boolean,
    phase: State<Float>,
    onHover: (String, Int, Int) -> Unit,
    onDrop: (String, Int, Int) -> Unit,
    onDragCancel: () -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val hoverNow by rememberUpdatedState(onHover)
    val dropNow by rememberUpdatedState(onDrop)
    val cancelNow by rememberUpdatedState(onDragCancel)
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var rs by remember { mutableStateOf(Offset.Zero) } // arrastre de la esquina para cambiar de tamaño
    var resizing by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(tile)
    val density = LocalDensity.current
    val baseW = cellW * tile.w + gap * (tile.w - 1)
    val baseH = cellH * tile.h + gap * (tile.h - 1)
    val maxW = (cellW + gap) * (gridCols - tile.col) - gap
    val maxH = (cellH + gap) * (gridRows - tile.row) - gap
    val minW = cellW * tile.minW + gap * (tile.minW - 1)
    val scope = rememberCoroutineScope()
    val tx = (cellW + gap) * tile.col
    val ty = (cellH + gap) * tile.row
    val ox = remember { Animatable(tx, Dp.VectorConverter) }
    val oy = remember { Animatable(ty, Dp.VectorConverter) }
    var dropped by remember { mutableStateOf(0) }
    LaunchedEffect(tx, ty, dropped) {
        launch { ox.animateTo(tx, tween(280)) }
        launch { oy.animateTo(ty, tween(280)) }
    }
    // Tamaño animado; mientras se arrastra la esquina sigue al dedo sin animación.
    val wAnim = remember { Animatable(baseW, Dp.VectorConverter) }
    val hAnim = remember { Animatable(baseH, Dp.VectorConverter) }
    var settle by remember { mutableStateOf(0) }
    LaunchedEffect(baseW, baseH, settle) {
        launch { wAnim.animateTo(baseW, tween(280)) }
        launch { hAnim.animateTo(baseH, tween(280)) }
    }
    val liveW = { (baseW + with(density) { rs.x.toDp() }).coerceIn(minW, maxW) }
    val liveH = { (baseH + with(density) { rs.y.toDp() }).coerceIn(cellH, maxH) }
    val liveWNow by rememberUpdatedState(liveW)
    val liveHNow by rememberUpdatedState(liveH)
    Box(
        Modifier
            .offset { IntOffset(ox.value.roundToPx(), oy.value.roundToPx()) }
            .zIndex(if (dragging || resizing) 2f else 1f)
            .layout { measurable, _ ->
                val wp = (if (resizing) liveW() else wAnim.value).roundToPx()
                val hp = (if (resizing) liveH() else hAnim.value).roundToPx()
                val p = measurable.measure(Constraints.fixed(wp, hp))
                layout(wp, hp) { p.place(0, 0) }
            }
            .graphicsLayer {
                translationX = drag.x
                translationY = drag.y
                if (editing) {
                    // Cada cuadro se mece un poco, con su propio ritmo: indica que se puede mover.
                    val a = sin(((phase.value + index * 0.29f) * 2f * PI).toFloat())
                    rotationZ = if (dragging || resizing) 0f else 1.1f * a
                    val s = if (dragging) 1.06f else if (resizing) 1f else 0.97f
                    scaleX = s; scaleY = s
                }
            }
            .then(
                if (editing) Modifier.pointerInput(tile.key) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val t = current
                            val col = ((t.col * stepX + drag.x) / stepX).roundToInt()
                            val row = ((t.row * stepY + drag.y) / stepY).roundToInt()
                            // Se suelta desde donde está el dedo: el cuadro se desliza a su sitio, sin saltar atrás.
                            val fx = ox.value + with(density) { drag.x.toDp() }
                            val fy = oy.value + with(density) { drag.y.toDp() }
                            scope.launch {
                                ox.snapTo(fx); oy.snapTo(fy)
                                drag = Offset.Zero
                                dragging = false
                                dropped++
                                dropNow(t.key, col, row)
                            }
                        },
                        onDragCancel = { drag = Offset.Zero; dragging = false; cancelNow() }
                    ) { change, delta ->
                        change.consume()
                        drag += delta
                        val t = current
                        hoverNow(
                            t.key,
                            ((t.col * stepX + drag.x) / stepX).roundToInt(),
                            ((t.row * stepY + drag.y) / stepY).roundToInt()
                        )
                    }
                } else Modifier
            )
    ) {
        content()
        if (editing) {
            // Capa que tapa el contenido: en edición los toques mueven el cuadro, no lo abren.
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            })
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
                    .pressable { onRemove(tile.key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, Str.get(R.string.home_remove), tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Esquinas marcadas: indican que el cuadro se puede agrandar o reducir.
            val accent = accentColor
            Canvas(Modifier.matchParentSize()) {
                // Arcos que siguen la curva de cada esquina redondeada, con un tramo recto corto.
                val sw = 2.5.dp.toPx(); val m = sw / 2f
                val rad = 18.dp.toPx() - m; val ext = 10.dp.toPx()
                val st = Stroke(sw, cap = StrokeCap.Round)
                val c = accent.copy(alpha = 0.9f)
                val R = size.width - m; val B = size.height - m
                val d = GSize(rad * 2, rad * 2)
                drawArc(c, 180f, 90f, false, GOffset(m, m), d, style = st)
                drawArc(c, 270f, 90f, false, GOffset(R - 2 * rad, m), d, style = st)
                drawArc(c, 90f, 90f, false, GOffset(m, B - 2 * rad), d, style = st)
                drawArc(c, 0f, 90f, false, GOffset(R - 2 * rad, B - 2 * rad), d, style = st)
                fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                    drawLine(c, GOffset(x1, y1), GOffset(x2, y2), sw, StrokeCap.Round)
                line(m + rad, m, m + rad + ext, m); line(m, m + rad, m, m + rad + ext)
                line(R - rad, m, R - rad - ext, m); line(R, m + rad, R, m + rad + ext)
                line(m + rad, B, m + rad + ext, B); line(m, B - rad, m, B - rad - ext)
                line(R - rad, B, R - rad - ext, B); line(R, B - rad, R, B - rad - ext)
            }
            // Asa de la esquina inferior derecha: arrastrarla cambia el tamaño.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp)
                    .pointerInput(tile.key, stepX, stepY) {
                        detectDragGestures(
                            onDragStart = { resizing = true },
                            onDragEnd = {
                                val t = current
                                val nw = t.w + (rs.x / stepX).roundToInt()
                                val nh = t.h + (rs.y / stepY).roundToInt()
                                // Se parte del tamaño que se veía: sin saltos al soltar.
                                val fw = liveWNow(); val fh = liveHNow()
                                scope.launch {
                                    wAnim.snapTo(fw); hAnim.snapTo(fh)
                                    rs = Offset.Zero
                                    resizing = false
                                    settle++
                                    onResize(t.key, nw, nh)
                                }
                            },
                            onDragCancel = { rs = Offset.Zero; resizing = false }
                        ) { change, delta ->
                            change.consume()
                            rs += delta
                        }
                    },
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    Modifier.padding(4.dp).size(22.dp).background(accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.OpenInFull, null, tint = accent.onAccent(), modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun TileContent(
    t: HomeTile,
    data: HomeData,
    wd: WidgetData?,
    library: LibraryState,
    editing: Boolean,
    onPlaySong: (List<Song>, Int) -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onGenre: (String) -> Unit,
    onOpenList: (TileKind) -> Unit
) {
    val menu = LocalSongMenu.current
    when (t.kind) {
        TileKind.QUICK -> QuickPicksTile(Str.get(R.string.home_quick_picks), Str.get(R.string.home_quick_short), Str.get(R.string.home_quick_empty), data.quick, t.h, editing, onPlaySong) { onOpenList(TileKind.QUICK) }
        TileKind.LIKES -> QuickPicksTile(Str.get(R.string.home_likes), Str.get(R.string.home_likes_short), Str.get(R.string.home_likes_empty), data.likes, t.h, editing, onPlaySong) { onOpenList(TileKind.LIKES) }
        TileKind.ALBUMS -> CollageTile(
            Str.get(R.string.home_top_albums),
            data.albums.map { it.artUri to null },
            circle = false, tile = t, onOpenList = { onOpenList(TileKind.ALBUMS) }
        ) { i -> onAlbum(data.albums[i].id) }
        TileKind.ARTISTS -> CollageTile(
            Str.get(R.string.home_top_artists),
            data.artists.map { it.artUri to null },
            circle = true, tile = t, onOpenList = { onOpenList(TileKind.ARTISTS) }
        ) { i -> onArtist(data.artists[i].name) }
        TileKind.GENRES -> CollageTile(
            Str.get(R.string.home_top_genres),
            data.genres.map { it.artUri to it.name },
            circle = false, tile = t, onOpenList = { onOpenList(TileKind.GENRES) }
        ) { i -> onGenre(data.genres[i].name) }
        TileKind.SHUFFLE -> ShuffleTile(data, t) {
            if (data.allSongs.isNotEmpty()) onPlaySong(data.allSongs.shuffled(), 0)
        }
        TileKind.DISCOVER -> DiscoverTile(data, editing, onAlbum, onPlaySong)
        TileKind.FORGOTTEN -> QuickPicksTile(Str.get(R.string.home_forgotten), Str.get(R.string.home_quick_short), Str.get(R.string.home_quick_empty), data.forgotten, t.h, editing, onPlaySong) { onOpenList(TileKind.FORGOTTEN) }
        TileKind.CLOCK -> ClockTile()
        TileKind.SONG -> {
            val song = t.ref.toLongOrNull()?.let { data.songsById[it] }
            val longClick: (() -> Unit)? = if (song != null && menu != null) ({ menu(song) }) else null
            PinnedTile(song?.artUri, song?.title, song?.artist, circle = false, onLongClick = longClick) {
                if (song != null) onPlaySong(listOf(song), 0)
            }
        }
        TileKind.ALBUM -> {
            val album = t.ref.toLongOrNull()?.let { data.albumsById[it] }
            PinnedTile(album?.artUri, album?.title, album?.artist, circle = false) { album?.let { onAlbum(it.id) } }
        }
        TileKind.ARTIST -> {
            val artist = data.artistsByName[t.ref]
            PinnedTile(artist?.artUri, artist?.name, null, circle = true) {
                artist?.let { onArtist(it.name) }
            }
        }
        TileKind.PLAYLIST -> {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp
            val playlists by app.playlists.playlists.collectAsStateWithLifecycle()
            val p = playlists.firstOrNull { it.id.toString() == t.ref }
            val songs = remember(p, data.songsById) { p?.songIds?.mapNotNull { data.songsById[it] } ?: emptyList() }
            val actions = LocalHomeActions.current
            PlaylistWidget(p, songs, onOpen = { p?.let { actions.onPlaylist(it.id) } }, onPlaySong = onPlaySong)
        }
        TileKind.CONTINUE -> ContinueWidget()
        else -> {
            val w = wd
            if (w == null) { TileSurface {}; return }
            when (t.kind) {
                TileKind.TOP5 -> Top5Widget(w, onPlaySong)
                TileKind.ALBUM_MONTH -> AlbumMonthWidget(w, onAlbum, onPlaySong)
                TileKind.STREAK -> StreakWidget(w, onArtist, onPlaySong)
                TileKind.SHELF -> ShelfWidget(w, t.h, editing, onAlbum, onPlaySong)
                TileKind.STARS -> StarsWidget(w, onPlaySong)
                TileKind.MOMENT -> MomentWidget(w, editing, onPlaySong)
                TileKind.BEST_PARTS -> {
                    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp
                    BestPartsWidget(w) { app.playerController.playClips(it) }
                }
                TileKind.COLORS -> ColorsWidget(library, w, onPlaySong)
                TileKind.TIMELINE -> TimelineWidget(w, editing, onPlaySong)
                TileKind.RECENT -> RecentWidget(w, t.w, t.h, onPlaySong)
                TileKind.SERVER_NEW -> ServerNewWidget(library, w, editing, onAlbum)
                TileKind.LASTFM -> LastFmWidget(w, library, onArtist)
                else -> TileSurface {}
            }
        }
    }
}

/** Fondo de vidrio común a todos los cuadros. */
@Composable
private fun TileSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .glass(RoundedCornerShape(18.dp), fill = 0.08f, border = 0.12f)
            .padding(8.dp),
        content = content
    )
}

@Composable
private fun TileLabel(text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = accentColor, modifier = Modifier.size(16.dp))
    }
}

/** Quick picks: 2 canciones por página; se desliza a la izquierda para ver más. */
@Composable
private fun QuickPicksTile(
    label: String,
    emptyText: String,
    emptyHelp: String,
    songs: List<Song>, rowsPerPage: Int, editing: Boolean,
    onPlaySong: (List<Song>, Int) -> Unit, onOpenList: () -> Unit
) {
    val menu = LocalSongMenu.current
    val per = rowsPerPage.coerceIn(1, 6)
    TileSurface {
        TileLabel(label, onOpenList)
        if (songs.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HelpBadge(emptyHelp)
            }
            return@TileSurface
        }
        val pages = (songs.size + per - 1) / per
        val pager = rememberPagerState { pages }
        DriftPager(pager)
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            // Se asoma la página siguiente: invita a deslizar.
            contentPadding = PaddingValues(end = 28.dp),
            pageSpacing = 8.dp,
            userScrollEnabled = !editing
        ) { page ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until per) {
                    val i = page * per + k
                    if (i >= songs.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val song = songs[i]
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val art = maxHeight
                        Row(
                            Modifier
                                .fillMaxSize()
                                .pressableWithLongClick(menu?.let { m -> { m(song) } }) { onPlaySong(songs, i) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Artwork(song.artUri, art, cornerRadius = 8.dp, requestPx = ART_PX)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title, style = MaterialTheme.typography.labelLarge, color = ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cuatro carátulas en 2 × 2 (álbumes, artistas o géneros más escuchados). */
@Composable
private fun CollageTile(
    label: String,
    items: List<Pair<Uri?, String?>>,
    circle: Boolean,
    tile: HomeTile,
    onOpenList: () -> Unit,
    onClick: (Int) -> Unit
) {
    TileSurface {
        TileLabel(label, onOpenList)
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Cuántas carátulas caben según el tamaño del cuadro.
            // Una carátula por celda: al agrandar el cuadro, se ven más.
            val nc = tile.w.coerceIn(1, 8)
            val nr = tile.h.coerceIn(1, 12)
            val side = minOf((maxWidth - 4.dp * (nc - 1)) / nc, (maxHeight - 4.dp * (nr - 1)) / nr)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until nr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until nc) {
                            val i = r * nc + c
                            val item = items.getOrNull(i)
                            if (item == null) {
                                Spacer(Modifier.size(side))
                            } else {
                                Box(Modifier.size(side).pressable { onClick(i) }) {
                                    Artwork(item.first, side, cornerRadius = 10.dp, circle = circle, requestPx = ART_PX)
                                    val caption = item.second
                                    if (caption != null) {
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        0.45f to Color.Transparent,
                                                        1f to Color.Black.copy(alpha = 0.75f)
                                                    )
                                                )
                                        )
                                        Text(
                                            caption,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Algo fijado desde un menú: canción, álbum o artista. */
@Composable
private fun PinnedTile(
    artUri: Uri?,
    title: String?,
    subtitle: String?,
    circle: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    TileSurface {
        BoxWithConstraints(
            Modifier.fillMaxSize().pressableWithLongClick(onLongClick, onClick),
            contentAlignment = Alignment.TopCenter
        ) {
            val textH = if (subtitle != null) 34.dp else 20.dp
            val side = minOf(maxWidth, maxHeight - textH).coerceAtLeast(24.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(artUri, side, cornerRadius = 12.dp, circle = circle, requestPx = ART_PX)
                Spacer(Modifier.height(3.dp))
                Text(
                    title ?: Str.get(R.string.home_unavailable),
                    style = MaterialTheme.typography.labelMedium, color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Lista completa detrás de un cuadro de Inicio (toca su título): todos los álbumes, artistas o
 * géneros ordenados por lo que más escuchas, o las canciones de Quick picks.
 */
@Composable
fun HomeListScreen(
    kind: TileKind,
    library: LibraryState,
    stats: Map<Long, PlayCount>,
    onBack: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onGenre: (String) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit
) {
    val marks = LocalMarks.current
    val liked = remember(marks) { marks.filterValues { it.liked }.keys }
    val data by produceState<HomeData?>(null, library.songs, library.albums, stats, liked) {
        value = withContext(Dispatchers.Default) { computeHome(library, stats, liked, all = true) }
    }
    val title = tileLabel(kind)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderButton(Icons.AutoMirrored.Rounded.ArrowBack, Str.get(R.string.home_title), onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = ink)
        }
        val d = data ?: return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            when (kind) {
                TileKind.ALBUMS -> items(d.albums, key = { it.id }) { a ->
                    ListEntry(a.artUri, a.title, a.artist, false) { onAlbum(a.id) }
                }
                TileKind.ARTISTS -> items(d.artists, key = { it.name }) { a ->
                    ListEntry(a.artUri, a.name, Str.plural(R.plurals.songs_count, a.songs.size), true) { onArtist(a.name) }
                }
                TileKind.GENRES -> items(d.genres, key = { it.name }) { g ->
                    ListEntry(g.artUri, g.name, Str.plural(R.plurals.songs_count, g.songs.size), false) { onGenre(g.name) }
                }
                TileKind.LIKES -> items(d.likes, key = { it.id }) { s ->
                    SongRow(song = s, onClick = { onPlaySong(d.likes, d.likes.indexOf(s)) })
                }
                TileKind.FORGOTTEN -> items(d.forgotten, key = { it.id }) { s ->
                    SongRow(song = s, onClick = { onPlaySong(d.forgotten, d.forgotten.indexOf(s)) })
                }
                else -> items(d.quick, key = { it.id }) { s ->
                    SongRow(song = s, onClick = { onPlaySong(d.quick, d.quick.indexOf(s)) })
                }
            }
        }
    }
}

@Composable
private fun ListEntry(art: Uri?, title: String, subtitle: String, circle: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(art, 54.dp, cornerRadius = 14.dp, circle = circle)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * Inicio y la biblioteca clásica (álbumes, artistas, canciones, listas) son una sola pantalla:
 * se pasa de una a otra deslizando a los lados. Arriba, una cabecera común con Ko-fi, búsqueda,
 * "me gusta", ajustes y (solo en Inicio) editar.
 */
@Composable
fun MainPages(
    library: LibraryState,
    onPlayLikes: (() -> Unit)?,
    onOpenOptions: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    home: @Composable (editing: androidx.compose.runtime.MutableState<Boolean>) -> Unit,
    libraryPage: @Composable () -> Unit
) {
    val editingState = rememberSaveable { mutableStateOf(false) }
    var editing by editingState
    var searching by rememberSaveable { mutableStateOf(false) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val accent = accentColor
    // Atrás en la biblioteca: vuelve a Inicio.
    BackHandler(enabled = pager.currentPage == 1 && !searching) {
        scope.launch { pager.animateScrollToPage(0) }
    }
    BackHandler(enabled = editing) { editing = false }
    if (searching) {
        BackHandler { searching = false }
        SearchSongs(library, onBack = { searching = false }, onPlaySong = onPlaySong)
        return
    }
    // 0 = Inicio · 1 = biblioteca (con los valores intermedios mientras se desliza). Se lee al dibujar.
    val pos = { (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f) }
    val editVisible by remember { androidx.compose.runtime.derivedStateOf { editing || pos() < 0.97f } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // --- Ko-fi y puntos de página ---
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .pressable { com.melodia.app.ui.components.openKofi(ctx) }
                    .glass(RoundedCornerShape(50), fill = 0.10f, border = 0.16f)
                    .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    com.melodia.app.ui.components.KofiIcon, Str.get(R.string.kofi_cd),
                    tint = com.melodia.app.ui.components.KofiColor, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Ko-fi", style = MaterialTheme.typography.labelLarge, color = ink)
            }
            Spacer(Modifier.weight(1f))
            // Dos puntos: en qué página estás (también se pueden tocar).
            for (i in 0..1) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = 22.dp, height = 22.dp)
                        .pressable { scope.launch { pager.animateScrollToPage(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                val near = 1f - kotlin.math.abs(pos() - i).coerceIn(0f, 1f)
                                alpha = 0.3f + 0.7f * near
                                scaleX = 1f + 0.35f * near; scaleY = scaleX
                            }
                            .background(ink, CircleShape)
                    )
                }
            }
        }

        // --- Título y botones ---
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Text(
                            Str.get(R.string.home_title),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                            color = ink, maxLines = 1, softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                val o = pos()
                                alpha = 1f - o
                                translationX = -o * 40.dp.toPx()
                            }
                        )
                        Text(
                            Str.get(R.string.your_music),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                            color = ink, maxLines = 1, softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                val o = pos()
                                alpha = o
                                translationX = (1f - o) * 40.dp.toPx()
                            }
                        )
                    }
                    if (editing) HelpBadge(Str.get(R.string.home_edit_hint), Modifier.padding(start = 6.dp), Str.get(R.string.home_edit))
                }
                if (library.loaded) {
                    Text(
                        Str.plural(R.plurals.songs_count, library.songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (library.serverSyncing) {
                    Text(Str.get(R.string.syncing_cloud), style = MaterialTheme.typography.labelMedium, color = accent)
                } else if (library.serverError != null) {
                    Text(
                        Str.get(R.string.server_error_cloud, library.serverError),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            if (!editing) {
                HeaderButton(Icons.Rounded.Search, Str.get(R.string.search_songs)) { searching = true }
                Spacer(Modifier.width(8.dp))
                // Me gusta: corazón con "play" en el centro.
                Box(
                    Modifier
                        .size(46.dp)
                        .graphicsLayer { alpha = if (onPlayLikes == null) 0.4f else 1f }
                        .pressable { onPlayLikes?.invoke() }
                        .glass(CircleShape, fill = 0.12f, border = 0.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Favorite, Str.get(R.string.play_my_likes),
                        tint = accent, modifier = Modifier.size(28.dp)
                    )
                    Icon(
                        Icons.Rounded.PlayArrow, null,
                        tint = accent.onAccent(), modifier = Modifier.size(14.dp).padding(start = 1.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.Settings, Str.get(R.string.options), onClick = onOpenOptions)
            }
            // Editar: solo en Inicio; se encoge y desaparece al pasar a la biblioteca.
            Box(
                Modifier
                    .layout { m, c ->
                        val pl = m.measure(c)
                        val k = if (editing) 1f else 1f - pos()
                        val w = ((pl.width + 8.dp.toPx()) * k).roundToInt()
                        layout(w, pl.height) { pl.placeRelative(w - pl.width, 0) }
                    }
                    .graphicsLayer { alpha = if (editing) 1f else 1f - pos() }
                    .clipToBounds()
            ) {
                // Escondido (en la biblioteca) no se dibuja: así no tapa el botón de ajustes.
                if (editVisible) HeaderButton(
                    if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                    Str.get(if (editing) R.string.home_done else R.string.home_edit),
                    highlighted = editing
                ) { if (editing || pager.currentPage == 0) editing = !editing }
            }
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Mientras se editan los cuadros, deslizar mueve cuadros, no páginas.
            userScrollEnabled = !editing,
            beyondViewportPageCount = 1
        ) { page ->
            if (page == 0) home(editingState) else libraryPage()
        }
    }
}
