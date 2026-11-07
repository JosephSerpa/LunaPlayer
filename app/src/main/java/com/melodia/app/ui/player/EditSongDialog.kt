package com.melodia.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.Song
import com.melodia.app.data.UNKNOWN_ALBUM
import com.melodia.app.data.UNKNOWN_ARTIST
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink

/** Editar título, artista, álbum y género de una canción del teléfono. */
@Composable
fun EditSongDialog(
    song: Song,
    onSave: (title: String, artist: String, album: String, genre: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val accent = accentColor
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(if (song.artist == UNKNOWN_ARTIST) "" else song.artist) }
    var album by remember { mutableStateOf(if (song.album == UNKNOWN_ALBUM) "" else song.album) }
    var genre by remember { mutableStateOf(song.genre) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = ink, unfocusedTextColor = ink,
        focusedBorderColor = accent, unfocusedBorderColor = ink.copy(alpha = 0.25f),
        focusedLabelColor = accent, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = accent
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.bg.copy(alpha = 0.97f), RoundedCornerShape(26.dp))
                .glass(RoundedCornerShape(26.dp), fill = 0.06f, border = 0.16f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(Str.get(R.string.edit_song), style = MaterialTheme.typography.titleLarge, color = ink)
                com.melodia.app.ui.components.HelpBadge(Str.get(R.string.edit_note), Modifier.padding(start = 2.dp), Str.get(R.string.edit_song))
            }
            OutlinedTextField(title, { title = it }, singleLine = true, colors = colors,
                label = { Text(Str.get(R.string.edit_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artist, { artist = it }, singleLine = true, colors = colors,
                label = { Text(Str.get(R.string.edit_artist)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(album, { album = it }, singleLine = true, colors = colors,
                label = { Text(Str.get(R.string.edit_album)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(genre, { genre = it }, singleLine = true, colors = colors,
                label = { Text(Str.get(R.string.edit_genre)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    Str.get(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium, color = ink,
                    modifier = Modifier.pressable(onDismiss).padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    Str.get(R.string.save),
                    style = MaterialTheme.typography.titleMedium, color = accent.onAccent(),
                    modifier = Modifier
                        .pressable { if (title.isNotBlank()) onSave(title, artist, album, genre) }
                        .background(accent, RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
