package com.melodia.app.ui.library

import com.melodia.app.Str
import com.melodia.app.R

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.melodia.app.data.Album
import com.melodia.app.data.Song
import com.melodia.app.player.NowPlaying
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.SongRow
import com.melodia.app.ui.components.StaggeredItem
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.ink

/** Pantalla de detalle reutilizada para artistas y álbumes. */
@Composable
fun DetailScreen(
    title: String,
    subtitle: String,
    artUri: Uri?,
    circleArt: Boolean,
    songs: List<Song>,
    albums: List<Album>,
    current: NowPlaying?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onAlbum: (Long) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    /** Botones extra bajo el título (p. ej. renombrar/eliminar una lista). */
    actions: (@Composable () -> Unit)? = null,
    /** Texto cuando no hay canciones. */
    emptyText: String? = null
) {
    val accent = accentColor
    val onAccent = accent.onAccent()

    // La portada entra con un pequeño rebote.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val heroScale by animateFloatAsState(
        if (shown) 1f else 0.7f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "heroScale"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Box(
                    Modifier
                        .padding(start = 16.dp, top = 12.dp)
                        .size(44.dp)
                        .pressable(onBack)
                        .glass(CircleShape, fill = 0.12f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, Str.get(R.string.back), tint = ink)
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.graphicsLayer { scaleX = heroScale; scaleY = heroScale; alpha = heroScale.coerceIn(0f, 1f) }) {
                        val shape = if (circleArt) CircleShape else RoundedCornerShape(36.dp)
                        Artwork(
                            artUri, 230.dp, cornerRadius = 36.dp, circle = circleArt,
                            modifier = Modifier.shadow(
                                32.dp, shape, ambientColor = accent, spotColor = accent
                            )
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier
                                .pressable { onPlay(songs, 0, false) }
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(accent, accent.copy(alpha = 0.6f))
                                    ),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 26.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = onAccent)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                Str.get(R.string.play),
                                style = MaterialTheme.typography.titleMedium,
                                color = onAccent
                            )
                        }
                        Row(
                            Modifier
                                .pressable { onPlay(songs, 0, true) }
                                .glass(RoundedCornerShape(50), fill = 0.12f, border = 0.2f)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, tint = ink)
                            Spacer(Modifier.width(6.dp))
                            Text(Str.get(R.string.shuffle), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (actions != null) {
                        Spacer(Modifier.height(14.dp))
                        actions()
                    }
                    Spacer(Modifier.height(28.dp))
                    if (songs.isEmpty() && emptyText != null) {
                        Text(
                            emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (albums.size > 1) {
            item {
              Column {
                Text(
                    Str.get(R.string.tab_albums),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        Column(Modifier.width(140.dp).pressable { onAlbum(album.id) }) {
                            Artwork(album.artUri, 140.dp, cornerRadius = 24.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                album.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    Str.get(R.string.tab_songs),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
              }
            }
        }

        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            StaggeredItem(index) {
                SongRow(
                    song = song,
                    showArt = false,
                    number = index + 1,
                    isCurrent = current?.mediaId == song.id.toString(),
                    isPlaying = isPlaying,
                    onClick = { onPlaySong(songs, index) }
                )
            }
        }
    }
}
