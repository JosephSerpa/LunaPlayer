package com.melodia.app.ui.options

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.melodia.app.data.server.ServerConfig
import com.melodia.app.data.server.ServerSettings
import com.melodia.app.data.server.SubsonicClient
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.library.LibraryState
import com.melodia.app.ui.theme.ink
import kotlinx.coroutines.launch

private val QUALITIES = listOf(0, 320, 256, 192, 128)
private val OkGreen = Color(0xFF4ADE80)

/** Ajustes → Servidor Subsonic / Navidrome. */
@Composable
fun ServerScreen(
    config: ServerConfig,
    library: LibraryState,
    settings: ServerSettings,
    onSync: () -> Unit,
    onBack: () -> Unit
) {
    val accent = accentColor
    val scope = rememberCoroutineScope()

    var url by rememberSaveable { mutableStateOf(config.url) }
    var user by rememberSaveable { mutableStateOf(config.user) }
    var password by rememberSaveable { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (ok, texto)

    /** Configuración con lo escrito en pantalla (sin guardarla todavía). */
    fun draft(): ServerConfig {
        val auth = if (password.isNotEmpty()) ServerSettings.makeAuth(password) else config.salt to config.token
        return config.copy(
            url = ServerSettings.normalizeUrl(url), user = user.trim(),
            salt = auth.first, token = auth.second
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = ink.copy(alpha = 0.2f),
        focusedTextColor = ink,
        unfocusedTextColor = ink,
        cursorColor = accent,
        focusedLabelColor = accent,
        unfocusedLabelColor = ink.copy(alpha = 0.6f),
        focusedPlaceholderColor = ink.copy(alpha = 0.35f),
        unfocusedPlaceholderColor = ink.copy(alpha = 0.35f)
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp)
    ) {
        SettingsHeader(title = Str.get(R.string.server), onBack = onBack)

        // --- Conexión ---
        SettingsSectionLabel(
            Str.get(R.string.srv_connection),
            help = Str.get(R.string.srv_connection_help)
        )
        SettingsCard {
            OutlinedTextField(
                value = url, onValueChange = { url = it; message = null },
                label = { Text(Str.get(R.string.srv_address)) },
                placeholder = { Text("http://192.168.1.10:4533") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it; message = null },
                label = { Text(Str.get(R.string.srv_user)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it; message = null },
                label = { Text(Str.get(R.string.srv_password)) },
                placeholder = {
                    Text(if (config.isConfigured) Str.get(R.string.srv_password_saved) else "")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            val canUse = url.isNotBlank() && user.isNotBlank() &&
                    (password.isNotEmpty() || config.token.isNotBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Str.get(R.string.srv_test),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color = ink.copy(alpha = if (canUse) 1f else 0.4f),
                    modifier = Modifier
                        .weight(1f)
                        .pressable {
                            if (!canUse || testing) return@pressable
                            testing = true
                            message = null
                            scope.launch {
                                message = try {
                                    val info = SubsonicClient.ping(draft())
                                    true to listOf(info.name, info.version)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ") + " · API ${info.apiVersion}"
                                } catch (e: Exception) {
                                    false to (e.message ?: Str.get(R.string.err_cant_connect))
                                }
                                testing = false
                            }
                        }
                        .glass(RoundedCornerShape(50), fill = 0.1f, border = 0.18f)
                        .padding(vertical = 13.dp)
                )
                Text(
                    Str.get(R.string.save),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color = accent.onAccent(),
                    modifier = Modifier
                        .weight(1f)
                        .pressable {
                            if (!canUse) return@pressable
                            settings.save(url, user, password)
                            password = ""
                            message = true to Str.get(R.string.srv_saved_syncing)
                        }
                        .background(accent.copy(alpha = if (canUse) 1f else 0.4f), RoundedCornerShape(50))
                        .padding(vertical = 13.dp)
                )
            }
            if (testing || message != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (testing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp, color = accent, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(Str.get(R.string.srv_connecting), style = MaterialTheme.typography.bodyMedium)
                    } else message?.let { (ok, text) ->
                        Text(
                            (if (ok) "✓ " else "✕ ") + text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) OkGreen else ErrorRed
                        )
                    }
                }
            }
        }

        if (config.isConfigured) {
            // --- Biblioteca ---
            SettingsSectionLabel(Str.get(R.string.srv_library))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Str.get(R.string.srv_show_music), style = MaterialTheme.typography.titleMedium)
                        Text(
                            serverStatus(config, library),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (library.serverError != null && config.enabled) ErrorRed
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = settings::setEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = ink.copy(alpha = 0.7f),
                            uncheckedTrackColor = ink.copy(alpha = 0.15f),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
                if (config.enabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (library.serverSyncing) Str.get(R.string.srv_syncing) else Str.get(R.string.srv_sync_now),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable { if (!library.serverSyncing) onSync() }
                            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .padding(vertical = 12.dp)
                    )
                }
            }

            // --- Calidad ---
            SettingsSectionLabel(
                Str.get(R.string.srv_quality),
                help = Str.get(R.string.srv_quality_help)
            )
            SettingsCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUALITIES.forEach { kbps ->
                        val label = if (kbps == 0) Str.get(R.string.srv_original) else kbps.toString()
                        val sel = config.maxBitRate == kbps
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = if (sel) accent.onAccent() else ink.copy(alpha = 0.85f),
                            modifier = Modifier
                                .weight(1f)
                                .pressable { settings.setMaxBitRate(kbps) }
                                .background(
                                    if (sel) accent else ink.copy(alpha = 0.09f),
                                    RoundedCornerShape(50)
                                )
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                Str.get(R.string.srv_disconnect),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = ErrorRed,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .pressable {
                        settings.clear()
                        url = ""; user = ""; password = ""; message = null
                    }
                    .glass(RoundedCornerShape(50), fill = 0.05f, border = 0.12f)
                    .padding(vertical = 13.dp)
            )
        }
    }
}
