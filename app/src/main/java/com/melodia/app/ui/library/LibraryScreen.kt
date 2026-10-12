package com.melodia.app.ui.library

import com.melodia.app.Str
import com.melodia.app.R

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.melodia.app.data.Album
import com.melodia.app.data.Artist
import com.melodia.app.data.Folder
import com.melodia.app.data.Playlist
import com.melodia.app.data.Song
import com.melodia.app.player.NowPlaying
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.LocalMarks
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.SongRow
import com.melodia.app.ui.components.StaggeredItem
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.ink
import com.melodia.app.ui.theme.inverse
import kotlinx.coroutines.launch
import java.util.Calendar

private fun tabs() = listOf(Str.get(R.string.tab_albums), Str.get(R.string.tab_artists), Str.get(R.string.tab_songs), Str.get(R.string.tab_playlists), Str.get(R.string.tab_liked), Str.get(R.string.tab_folders))
private val listBottomPadding = 130.dp


@Composable
fun LibraryScreen(
    state: LibraryState,
    query: String,
    onQueryChange: (String) -> Unit,
    current: NowPlaying?,
    isPlaying: Boolean,
    onArtist: (String) -> Unit,
    onAlbum: (Long) -> Unit,
    onFolder: (Int) -> Unit,
    onOpenOptions: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    /** Tocar una canción: suena y se abre el reproductor desde su carátula. */
    onPlaySong: (List<Song>, Int) -> Unit,
    playlists: List<Playlist>,
    onPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val accent = accentColor
    val songsById = remember(state.songs) { state.songs.associateBy { it.id } }
    val marks = LocalMarks.current
    val q = query.trim().lowercase()
    val artists = remember(state.artists, q) {
        if (q.isEmpty()) state.artists else state.artists.filter { it.name.lowercase().contains(q) }
    }
    val albums = remember(state.albums, q) {
        if (q.isEmpty()) state.albums else state.albums.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }
    val folders = remember(state.folders, q) {
        if (q.isEmpty()) state.folders else state.folders.filter {
            it.name.lowercase().contains(q) || it.displayPath.lowercase().contains(q)
        }
    }
    // Filtro por estrellas: 0 = todas, 1..5 = solo las canciones con esa calificación.
    var starFilter by rememberSaveable { mutableStateOf(0) }
    val allLiked = remember(state.songs, marks) {
        state.songs
            .filter { marks[it.id]?.liked == true }
            .sortedByDescending { marks[it.id]?.rating ?: 0 }
    }
    val liked = remember(state.songs, marks, q, starFilter) {
        state.songs
            .filter { marks[it.id]?.liked == true }
            .filter { starFilter == 0 || (marks[it.id]?.rating ?: 0) == starFilter }
            .filter {
                q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
            // Primero las de más estrellas.
            .sortedByDescending { marks[it.id]?.rating ?: 0 }
    }
    val songs = remember(state.songs, q, starFilter, marks) {
        state.songs
            .filter { starFilter == 0 || (marks[it.id]?.rating ?: 0) == starFilter }
            .filter {
                q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
    }

    val tabInk = ink
    val tabInverse = inverse
    val pagerState = rememberPagerState(initialPage = 0) { tabs().size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(start = 24.dp, end = 20.dp, top = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    Str.get(R.string.your_music),
                    style = MaterialTheme.typography.displayLarge,
                    color = ink
                )
                if (state.loaded) {
                    Text(
                        Str.plural(R.plurals.songs_count, state.songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.serverSyncing) {
                    Text(
                        Str.get(R.string.syncing_cloud),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                } else if (state.serverError != null) {
                    Text(
                        Str.get(R.string.server_error_cloud, state.serverError),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 6.dp, end = 10.dp)
                    .size(46.dp)
                    .graphicsLayer { alpha = if (allLiked.isEmpty()) 0.4f else 1f }
                    .pressable { if (allLiked.isNotEmpty()) onPlay(allLiked, 0, false) }
                    .glass(CircleShape, fill = 0.12f, border = 0.2f),
                contentAlignment = Alignment.Center
            ) {
                // Corazón con un triángulo de "play" en el centro.
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Favorite, Str.get(R.string.play_my_likes),
                        tint = accent, modifier = Modifier.size(28.dp)
                    )
                    Icon(
                        Icons.Rounded.PlayArrow, null,
                        tint = accent.onAccent(),
                        modifier = Modifier.size(14.dp).padding(start = 1.dp)
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(46.dp)
                    .pressable(onOpenOptions)
                    .glass(CircleShape, fill = 0.12f, border = 0.2f),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Settings, Str.get(R.string.options), tint = ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        SearchField(query, onQueryChange)
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs().forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                val bg by animateColorAsState(
                    if (selected) tabInk else tabInk.copy(alpha = 0.08f),
                    tween(300), label = "tabBg"
                )
                val fg by animateColorAsState(
                    if (selected) tabInverse else tabInk.copy(alpha = 0.8f),
                    tween(300), label = "tabFg"
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    modifier = Modifier
                        .pressable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .background(bg, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (n in 1..5) {
                val on = starFilter == n
                Row(
                    Modifier
                        .pressable { starFilter = if (on) 0 else n }
                        .background(
                            if (on) accent else tabInk.copy(alpha = 0.08f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star, null,
                        tint = if (on) accent.onAccent() else accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$n",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) accent.onAccent() else tabInk.copy(alpha = 0.8f)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = accent)
            }
            return@Column
        }

        if (state.songs.isEmpty()) {
            EmptyMessage(Str.get(R.string.empty_no_music))
            return@Column
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                2 -> if (songs.isEmpty() && starFilter != 0) {
                    EmptyMessage(Str.plural(R.plurals.no_songs_stars, starFilter))
                } else {
                    SongList(songs, current, isPlaying, onPlay, onPlaySong)
                }
                0 -> AlbumGrid(albums, onAlbum, onPlay)
                1 -> ArtistGrid(artists, onArtist)
                3 -> PlaylistList(
                    playlists = playlists,
                    songsById = songsById,
                    onOpen = onPlaylist,
                    onCreate = onCreatePlaylist,
                    onPlay = { onPlay(it, 0, false) },
                    bottomPadding = listBottomPadding
                )
                4 -> if (liked.isEmpty()) {
                    EmptyMessage(
                        if (starFilter != 0) Str.plural(R.plurals.no_liked_stars, starFilter)
                        else Str.get(R.string.empty_likes_hint)
                    )
                } else {
                    SongList(liked, current, isPlaying, onPlay, onPlaySong)
                }
                else -> FolderList(folders, onFolder, onPlay)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(50), fill = 0.08f)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    Str.get(R.string.search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
                cursorBrush = SolidColor(ink),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, Str.get(R.string.clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).pressable { onQueryChange("") }
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

/** Carátula que se ajusta al ancho disponible de su celda. */
@Composable
private fun FitArtwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    circle: Boolean = false,
    fraction: Float = 1f,
    glow: Color = Color.Transparent
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = maxWidth * fraction
        Artwork(
            uri = uri,
            size = side,
            cornerRadius = cornerRadius,
            circle = circle
        )
    }
}

@Composable
private fun ArtistGrid(artists: List<Artist>, onArtist: (String) -> Unit) {
    if (artists.isEmpty()) return EmptyMessage(Str.get(R.string.no_results))
    val accent = accentColor
    val ringFade = ink.copy(alpha = 0.06f)
    val ringStrong = ink.copy(alpha = 0.35f)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = listBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(artists, key = { _, a -> a.name }) { index, artist ->
            StaggeredItem(index) {
                Column(
                    Modifier.fillMaxWidth().pressable { onArtist(artist.name) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .border(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(ringStrong, ringFade, ringStrong)
                                ),
                                CircleShape
                            )
                            .padding(5.dp)
                    ) {
                        FitArtwork(artist.artUri, circle = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Str.plural(R.plurals.songs_count, artist.songs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    onAlbum: (Long) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    if (albums.isEmpty()) return EmptyMessage(Str.get(R.string.no_results))
    val accent = accentColor
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = listBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(albums, key = { _, a -> a.id }) { index, album ->
            StaggeredItem(index) {
                // La segunda columna baja un poco: la cuadrícula "flota" en vez de verse rígida.
                Column(
                    Modifier
                        .padding(top = if (index % 2 == 1) 26.dp else 0.dp)
                        .pressable { onAlbum(album.id) }
                ) {
                    Box {
                        FitArtwork(album.artUri, cornerRadius = 26.dp)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(40.dp)
                                .pressable { onPlay(album.songs, 0, false) }
                                .glass(CircleShape, fill = 0.28f, border = 0.3f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PlayArrow, Str.get(R.string.play_album), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        album.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    current: NowPlaying?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit
) {
    if (songs.isEmpty()) return EmptyMessage(Str.get(R.string.no_results))
    val accent = accentColor
    val onAccent = accent.onAccent()
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding)) {
        item {
          Column {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .pressable { onPlay(songs, 0, true) }
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f))
                        ),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Shuffle, null, tint = onAccent)
                Spacer(Modifier.width(10.dp))
                Text(
                    Str.get(R.string.shuffle_all),
                    style = MaterialTheme.typography.titleMedium,
                    color = onAccent
                )
            }
            Spacer(Modifier.height(4.dp))
          }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            StaggeredItem(index) {
                SongRow(
                    song = song,
                    isCurrent = current?.mediaId == song.id.toString(),
                    isPlaying = isPlaying,
                    onClick = { onPlaySong(songs, index) }
                )
            }
        }
    }
}

@Composable
private fun FolderList(
    folders: List<Folder>,
    onFolder: (Int) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    if (folders.isEmpty()) return EmptyMessage(Str.get(R.string.no_results))
    val accent = accentColor
    val shape = RoundedCornerShape(22.dp)
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding)) {
        itemsIndexed(folders, key = { _, f -> f.id }) { index, folder ->
            StaggeredItem(index) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .pressable { onFolder(folder.id) }
                        .glass(shape, fill = 0.07f, border = 0.08f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Artwork(folder.artUri, 54.dp, cornerRadius = 16.dp)
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Folder, null,
                                tint = accent.onAccent(), modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${Str.plural(R.plurals.songs_count, folder.songs.size)} · ${folder.displayPath}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(40.dp)
                            .pressable { onPlay(folder.songs, 0, false) }
                            .glass(CircleShape, fill = 0.12f, border = 0.16f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, Str.get(R.string.play_folder), tint = ink)
                    }
                }
            }
        }
    }
}
