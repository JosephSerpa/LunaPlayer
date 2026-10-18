package com.melodia.app.ui.library

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.melodia.app.data.Playlist
import com.melodia.app.data.Song
import com.melodia.app.data.SongMark
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.CollectionTarget
import com.melodia.app.ui.components.StaggeredItem
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.pressableWithLongClick
import com.melodia.app.ui.components.LocalCollectionMenu
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink

// ---------------------------------------------------------------------------
// Pestaña "Listas"
// ---------------------------------------------------------------------------

@Composable
fun PlaylistList(
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val accent = accentColor
    val collectionMenu = LocalCollectionMenu.current
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        item(key = "new") {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .pressable(onCreate)
                    .background(accent, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Add, null, tint = accent.onAccent())
                Spacer(Modifier.width(8.dp))
                Text(Str.get(R.string.new_playlist), style = MaterialTheme.typography.titleMedium, color = accent.onAccent())
            }
        }
        itemsIndexed(playlists, key = { _, p -> p.id }) { index, p ->
            val songs = p.songIds.mapNotNull { songsById[it] }
            StaggeredItem(index) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .pressableWithLongClick(
                            collectionMenu?.let { m ->
                                { m(CollectionTarget(p.name, Str.plural(R.plurals.songs_count, songs.size), songs.firstOrNull()?.artUri, songs)) }
                            }
                        ) { onOpen(p.id) }
                        .glass(RoundedCornerShape(22.dp), fill = 0.07f, border = 0.08f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistCover(songs, 54.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            Str.plural(R.plurals.songs_count, songs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (songs.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .pressable { onPlay(songs) }
                                .glass(CircleShape, fill = 0.12f, border = 0.16f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PlayArrow, Str.get(R.string.play_playlist), tint = ink)
                        }
                    }
                }
            }
        }
    }
}

/** Portada de la lista: la de su primera canción, o un icono si está vacía. */
@Composable
fun PlaylistCover(songs: List<Song>, size: androidx.compose.ui.unit.Dp) {
    if (songs.isNotEmpty()) {
        Artwork(songs.first().artUri, size, cornerRadius = 16.dp)
    } else {
        Box(
            Modifier.size(size).glass(RoundedCornerShape(16.dp), fill = 0.1f, border = 0.1f),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = accentColor)
        }
    }
}

// ---------------------------------------------------------------------------
// Menú de una canción (mantener presionada o botón del reproductor)
// ---------------------------------------------------------------------------

@Composable
fun SongMenuDialog(
    song: Song,
    playlists: List<Playlist>,
    /** Si se abrió desde una lista, se ofrece quitarla de ella. */
    inPlaylist: Playlist?,
    mark: SongMark,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onRate: (Int) -> Unit,
    onAddTo: (Long) -> Unit,
    onCreateWith: (String) -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(
            title = Str.get(R.string.new_playlist),
            initial = "",
            confirm = Str.get(R.string.create_and_add),
            onConfirm = { onCreateWith(it); onDismiss() },
            onDismiss = { creating = false }
        )
        return
    }
    DialogCard(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.artUri, 48.dp, cornerRadius = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.padding(top = 10.dp))

        MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, Str.get(R.string.play_next)) { onPlayNext(); onDismiss() }
        MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, Str.get(R.string.add_to_queue)) { onAddToQueue(); onDismiss() }
        MenuRow(
            if (mark.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            Str.get(if (mark.liked) R.string.menu_unlike else R.string.menu_like)
        ) { onToggleLike(); onDismiss() }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Str.get(R.string.menu_rate),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            for (i in 1..5) {
                val on = i <= mark.rating
                Icon(
                    if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    Str.plural(R.plurals.stars_count, i),
                    tint = if (on) accentColor else ink.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp).pressable { onRate(i) }.padding(5.dp)
                )
            }
        }

        if (inPlaylist != null) {
            MenuRow(Icons.Rounded.RemoveCircleOutline, Str.get(R.string.remove_from, inPlaylist.name)) {
                onRemoveFromPlaylist(); onDismiss()
            }
        }
        MenuRow(Icons.Rounded.Add, Str.get(R.string.new_playlist_with_song)) { creating = true }

        val others = playlists.filter { it.id != inPlaylist?.id }
        if (others.isNotEmpty()) {
            Text(
                Str.get(R.string.add_to),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
            )
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                for (p in others) {
                    val already = song.id in p.songIds
                    MenuRow(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        if (already) "${p.name}  ✓" else p.name,
                        enabled = !already
                    ) { onAddTo(p.id); onDismiss() }
                }
            }
        }
    }
}

@Composable
fun CollectionMenuDialog(
    target: CollectionTarget,
    playlists: List<Playlist>,
    onPlay: (shuffle: Boolean) -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddTo: (Long) -> Unit,
    onCreateWith: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(
            title = Str.get(R.string.new_playlist),
            initial = target.title,
            confirm = Str.get(R.string.create_and_add),
            onConfirm = { onCreateWith(it); onDismiss() },
            onDismiss = { creating = false }
        )
        return
    }
    DialogCard(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(target.artUri, 48.dp, cornerRadius = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(target.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    target.subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.padding(top = 10.dp))
        MenuRow(Icons.Rounded.PlayArrow, Str.get(R.string.menu_play)) { onPlay(false); onDismiss() }
        MenuRow(Icons.Rounded.Shuffle, Str.get(R.string.menu_shuffle)) { onPlay(true); onDismiss() }
        MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, Str.get(R.string.play_next)) { onPlayNext(); onDismiss() }
        MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, Str.get(R.string.add_to_queue)) { onAddToQueue(); onDismiss() }
        MenuRow(Icons.Rounded.Add, Str.get(R.string.new_playlist)) { creating = true }
        if (playlists.isNotEmpty()) {
            Text(
                Str.get(R.string.menu_add_all_to).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
            )
            Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                for (p in playlists) {
                    MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, p.name) { onAddTo(p.id); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable { if (enabled) onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) accentColor else ink.copy(alpha = 0.35f))
        Spacer(Modifier.width(14.dp))
        Text(
            text, style = MaterialTheme.typography.bodyLarge,
            color = ink.copy(alpha = if (enabled) 1f else 0.45f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Pide el nombre de una lista (crear o renombrar). */
@Composable
fun PlaylistNameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = accentColor
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DialogCard(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(Str.get(R.string.playlist_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = ink.copy(alpha = 0.2f),
                focusedTextColor = ink,
                unfocusedTextColor = ink,
                cursorColor = accent
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Spacer(Modifier.padding(top = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Str.get(R.string.cancel),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .pressable(onDismiss)
                    .glass(RoundedCornerShape(50), fill = 0.08f, border = 0.16f)
                    .padding(vertical = 12.dp)
            )
            Text(
                confirm,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = accent.onAccent(),
                modifier = Modifier
                    .weight(1f)
                    .pressable { if (name.isNotBlank()) { onConfirm(name); onDismiss() } }
                    .background(accent.copy(alpha = if (name.isNotBlank()) 1f else 0.4f), RoundedCornerShape(50))
                    .padding(vertical = 12.dp)
            )
        }
    }
}

/** Tarjeta flotante de vidrio para los diálogos. */
@Composable
private fun DialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(24.dp, shape)
                .background(palette.bg.copy(alpha = 0.96f), shape)
                .glass(shape, fill = 0.05f, border = 0.14f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) { content() }
    }
}

/** Botones bajo el título de una lista: renombrar y eliminar (con confirmación). */
@Composable
fun PlaylistActions(onRename: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            Str.get(R.string.rename),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .pressable(onRename)
                .glass(RoundedCornerShape(50), fill = 0.08f, border = 0.14f)
                .padding(horizontal = 18.dp, vertical = 9.dp)
        )
        Text(
            if (confirmDelete) Str.get(R.string.confirm_delete) else Str.get(R.string.delete),
            style = MaterialTheme.typography.labelLarge,
            color = if (confirmDelete) com.melodia.app.ui.options.ErrorRed else ink,
            modifier = Modifier
                .pressable { if (confirmDelete) onDelete() else confirmDelete = true }
                .glass(RoundedCornerShape(50), fill = 0.08f, border = 0.14f)
                .padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}
