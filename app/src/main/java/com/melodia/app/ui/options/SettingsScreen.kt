package com.melodia.app.ui.options

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.melodia.app.audio.EqSettings
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.inverse
import com.melodia.app.ui.theme.ink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import com.melodia.app.audio.presetLabel
import com.melodia.app.data.server.ServerConfig
import com.melodia.app.ui.library.LibraryState

/** Pantalla principal de Ajustes: todo agrupado por secciones. */
@Composable
fun SettingsScreen(
    language: String,
    onLanguage: (String) -> Unit,
    eq: EqSettings,
    server: ServerConfig,
    library: LibraryState,
    onOpenEqualizer: () -> Unit,
    onOpenServer: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp)
    ) {
        SettingsHeader(title = Str.get(R.string.settings), onBack = onBack)


        SettingsSectionLabel(Str.get(R.string.sound))
        SettingsLink(
            icon = Icons.Rounded.GraphicEq,
            title = Str.get(R.string.equalizer),
            subtitle = if (eq.enabled) Str.get(R.string.eq_on_status, presetLabel(eq.preset)) else Str.get(R.string.eq_off_status),
            onClick = onOpenEqualizer
        )

        // --- Fuentes de música ---
        SettingsSectionLabel(Str.get(R.string.music_sources))
        SettingsLink(
            icon = Icons.Rounded.Cloud,
            title = Str.get(R.string.server_title),
            subtitle = serverStatus(server, library),
            statusColor = if (server.isActive && library.serverError != null) ErrorRed else null,
            onClick = onOpenServer
        )

        // --- Idioma ---
        SettingsSectionLabel(Str.get(R.string.lang_title), help = Str.get(R.string.lang_help))
        SettingsCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.melodia.app.LocaleManager.SYSTEM to Str.get(R.string.lang_system),
                    "es" to Str.get(R.string.lang_es),
                    "en" to Str.get(R.string.lang_en)
                ).forEach { (code, label) ->
                    val sel = code == language
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) inverse else ink.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .pressable { if (!sel) onLanguage(code) }
                            .background(
                                if (sel) ink else ink.copy(alpha = 0.09f),
                                RoundedCornerShape(50)
                            )
                            .padding(vertical = 11.dp)
                    )
                }
            }
        }

        // --- Acerca de ---
        SettingsSectionLabel(Str.get(R.string.about))
        SettingsCard {
            Text("Luna player 1.9.4", style = MaterialTheme.typography.titleMedium)
            Text(
                Str.plural(R.plurals.about_phone_songs, library.songs.size - library.serverSongs) +
                        if (library.serverSongs > 0) Str.get(R.string.about_server_part, library.serverSongs) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

val ErrorRed = Color(0xFFFF6B6B)

fun serverStatus(server: ServerConfig, library: LibraryState): String = when {
    !server.isConfigured -> Str.get(R.string.srv_not_configured)
    !server.enabled -> Str.get(R.string.srv_hidden)
    library.serverSyncing -> Str.get(R.string.srv_syncing)
    library.serverError != null -> Str.get(R.string.srv_error, library.serverError)
    else -> Str.get(R.string.srv_songs_url, Str.plural(R.plurals.songs_count, library.serverSongs), server.url.substringAfter("://"))
}
