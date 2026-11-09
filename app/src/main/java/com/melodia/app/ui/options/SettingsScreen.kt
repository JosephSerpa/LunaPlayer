package com.melodia.app.ui.options

import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Download
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.melodia.app.audio.EqSettings
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.KofiColor
import com.melodia.app.ui.components.KofiIcon
import com.melodia.app.ui.components.openKofi
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
    onOpenLicenses: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLastFm: () -> Unit,
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

        // --- Copia de seguridad ---
        BackupSection(library)

        // --- Last.fm ---
        SettingsSectionLabel(Str.get(R.string.lfm_title), help = Str.get(R.string.lfm_help))
        run {
            val lfm = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp).lastfm
            val user by lfm.user.collectAsState()
            SettingsLink(
                icon = Icons.Rounded.GraphicEq,
                title = "Last.fm",
                subtitle = user?.let { Str.get(R.string.lfm_connected_as, it) } ?: Str.get(R.string.lfm_not_connected),
                onClick = onOpenLastFm
            )
        }

        // --- Apoyar el proyecto ---
        SettingsSectionLabel(Str.get(R.string.support_title))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        SettingsLink(
            icon = KofiIcon,
            title = Str.get(R.string.kofi_title),
            subtitle = "ko-fi.com/raizzon",
            onClick = { openKofi(ctx) },
            iconTint = KofiColor
        )

        // --- Reproductor horizontal ---
        run {
            val ui = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.melodia.app.MelodiaApp).ui
            val mode by ui.split.collectAsState()
            val dc = com.melodia.app.ui.deviceClassOf(androidx.compose.ui.platform.LocalConfiguration.current)
            val on = when (mode) { 1 -> true; 2 -> false; else -> dc == com.melodia.app.ui.DeviceClass.TABLET }
            SettingsSectionLabel(Str.get(R.string.screen_title), help = Str.get(R.string.split_help))
            SettingsCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(Str.get(R.string.split_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = on, onCheckedChange = { ui.setSplit(if (it) 1 else 2) })
                }
                // Animaciones sutiles en los cuadros de Inicio (apagarlas ahorra batería).
                val life by ui.homeLife.collectAsState()
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(Str.get(R.string.home_life_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    com.melodia.app.ui.components.HelpBadge(Str.get(R.string.home_life_help))
                    androidx.compose.material3.Switch(checked = life, onCheckedChange = { ui.setHomeLife(it) })
                }
            }
        }

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
            Text("Luna player 2.7.2", style = MaterialTheme.typography.titleMedium)
            Text(
                Str.plural(R.plurals.about_phone_songs, library.songs.size - library.serverSongs) +
                        if (library.serverSongs > 0) Str.get(R.string.about_server_part, library.serverSongs) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsLink(
            icon = Icons.Rounded.NewReleases,
            title = Str.get(R.string.changelog_title),
            subtitle = Str.get(R.string.changelog_sub),
            onClick = onOpenChangelog
        )
        SettingsLink(
            icon = Icons.Rounded.Description,
            title = Str.get(R.string.licenses_title),
            subtitle = Str.get(R.string.licenses_sub),
            onClick = onOpenLicenses
        )
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

/** Exportar / importar todo (me gusta, listas, ecualizador, widgets, ajustes) a un archivo. */
@Composable
private fun BackupSection(library: LibraryState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var message by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var pending by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.melodia.app.data.Backup.export(context, library.songs, uri) }
                Str.get(R.string.backup_exported)
            }.getOrElse { Str.get(R.string.backup_error, it.message ?: "") }
            busy = false
        }
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pending = uri }

    SettingsSectionLabel(Str.get(R.string.backup_title), help = Str.get(R.string.backup_help))
    SettingsLink(
        icon = Icons.Rounded.Upload,
        title = Str.get(R.string.backup_export),
        subtitle = Str.get(R.string.backup_export_sub),
        onClick = {
            if (!busy) {
                val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                exporter.launch("LunaPlayer-backup-$day.json")
            }
        }
    )
    SettingsLink(
        icon = Icons.Rounded.Download,
        title = Str.get(R.string.backup_import),
        subtitle = message ?: Str.get(R.string.backup_import_sub),
        onClick = { if (!busy) importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
    )

    pending?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = com.melodia.app.ui.theme.LocalPalette.current.bg,
            title = { Text(Str.get(R.string.backup_import_q), color = ink) },
            text = { Text(Str.get(R.string.backup_import_warn), color = ink.copy(alpha = 0.85f)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = true
                        val r = runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.melodia.app.data.Backup.import(context, library.songs, uri)
                            }
                        }
                        busy = false
                        r.onSuccess {
                            android.widget.Toast.makeText(
                                context, Str.get(R.string.backup_imported, it.matched, it.songs), android.widget.Toast.LENGTH_LONG
                            ).show()
                            kotlinx.coroutines.delay(1200)
                            com.melodia.app.data.Backup.restart(context)
                        }.onFailure { message = Str.get(R.string.backup_bad_file) }
                    }
                }) { Text(Str.get(R.string.backup_import_go), color = com.melodia.app.ui.components.accentColor) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pending = null }) { Text(Str.get(R.string.cancel), color = ink) }
            }
        )
    }
}
