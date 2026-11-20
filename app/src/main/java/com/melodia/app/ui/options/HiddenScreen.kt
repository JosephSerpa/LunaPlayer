package com.melodia.app.ui.options

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.HiddenItem
import com.melodia.app.data.HiddenStore
import com.melodia.app.data.HiddenType
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink

/** Ajustes → Ocultos: todo lo que se ocultó de la biblioteca, para volver a mostrarlo. */
@Composable
fun HiddenScreen(store: HiddenStore, onBack: () -> Unit) {
    val items by store.items.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }
    val accent = accentColor
    val sections = remember(items) {
        listOf(HiddenType.FOLDER, HiddenType.ARTIST, HiddenType.ALBUM, HiddenType.SONG)
            .map { t -> t to items.filter { it.type == t }.sortedBy { it.title.lowercase() } }
            .filter { it.second.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SettingsHeader(title = Str.get(R.string.hidden_title), onBack = onBack)
        if (items.isEmpty()) {
            Text(
                Str.get(R.string.hidden_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(28.dp)
            )
            return@Column
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp)) {
            item {
                Text(
                    Str.get(R.string.hidden_show_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent.onAccent(),
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .pressable { confirmAll = true }
                        .background(accent, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
            for ((type, list) in sections) {
                item(key = "h_" + type.name) {
                    SettingsSectionLabel(
                        when (type) {
                            HiddenType.FOLDER -> Str.get(R.string.hidden_folders)
                            HiddenType.ARTIST -> Str.get(R.string.hidden_artists)
                            HiddenType.ALBUM -> Str.get(R.string.hidden_albums)
                            HiddenType.SONG -> Str.get(R.string.hidden_songs)
                        } + " · " + list.size
                    )
                }
                items(list, key = { type.name + it.key }) { h -> HiddenRow(h) { store.unhide(h) } }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            containerColor = LocalPalette.current.bg,
            title = { Text(Str.get(R.string.hidden_show_all_q), color = ink) },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; store.unhideAll() }) {
                    Text(Str.get(R.string.hidden_show_all), color = accent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(Str.get(R.string.cancel), color = ink) } }
        )
    }
}

@Composable
private fun HiddenRow(h: HiddenItem, onShow: () -> Unit) {
    val accent = accentColor
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = 0.07f)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (h.type) {
                HiddenType.FOLDER -> Icons.Rounded.Folder
                HiddenType.ARTIST -> Icons.Rounded.Person
                HiddenType.ALBUM -> Icons.Rounded.Album
                HiddenType.SONG -> Icons.Rounded.MusicNote
            },
            null, tint = ink.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (h.subtitle.isNotBlank()) Text(
                h.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            Str.get(R.string.hidden_show),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier
                .pressable(onShow)
                .glass(RoundedCornerShape(50), fill = 0.10f, border = 0.18f)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
