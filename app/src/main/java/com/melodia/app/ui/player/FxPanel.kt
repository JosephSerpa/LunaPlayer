package com.melodia.app.ui.player

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.melodia.app.audio.EffectsEngine
import com.melodia.app.audio.FxProfile
import com.melodia.app.audio.FxSettings
import com.melodia.app.audio.PITCH_MAX_ST
import com.melodia.app.audio.PITCH_MIN_ST
import com.melodia.app.audio.SPEED_MAX
import com.melodia.app.audio.SPEED_MIN
import com.melodia.app.ui.components.HelpBadge
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.pressableWithLongClick
import com.melodia.app.ui.library.PlaylistNameDialog
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Panel flotante "Slow + Reverb": velocidad, tono y reverberación.
 * Cada cambio se escucha al instante.
 */
@Composable
fun FxPanel(
    fx: FxSettings,
    effects: EffectsEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentColor
    val onAccent = accent.onAccent()
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(32.dp)

    Column(
        modifier
            .fillMaxWidth()
            .shadow(30.dp, shape, ambientColor = accent, spotColor = accent)
            .background(palette.bg.copy(alpha = 0.9f), shape)
            .glass(shape, fill = 0.06f, border = 0.16f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Slow + Reverb", style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
            HelpBadge(
                Str.get(R.string.fx_help),
                Modifier.padding(start = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                Str.get(R.string.reset),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier
                    .pressable { effects.reset() }
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                maxLines = 1, softWrap = false
            )
            Spacer(Modifier.width(10.dp))
            PanelCloseButton(onClose)
        }

        Spacer(Modifier.height(14.dp))

        // Mis ajustes: solo los que el usuario guarda. Tocar aplica; mantener presionado borra.
        val profiles by effects.profiles.collectAsState()
        var naming by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf<FxProfile?>(null) }
        Text(
            Str.get(R.string.fx_my_settings).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.name }) { profile ->
                val active = fx.enabled && fx.preset == profile.name
                Text(
                    profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) onAccent else ink.copy(alpha = 0.85f),
                    maxLines = 1,
                    modifier = Modifier
                        .pressableWithLongClick({ deleting = profile }) { effects.applyProfile(profile) }
                        .background(
                            if (active) accent else ink.copy(alpha = 0.09f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item(key = "save") {
                Row(
                    Modifier
                        .pressable { naming = true }
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Str.get(R.string.fx_save), style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1)
                }
            }
        }
        if (profiles.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                Str.get(R.string.fx_no_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (naming) {
            PlaylistNameDialog(
                title = Str.get(R.string.fx_save_title),
                initial = if (fx.preset == "Normal" || fx.preset == "Personalizado") "" else fx.preset,
                confirm = Str.get(R.string.save),
                onConfirm = { effects.saveProfile(it) },
                onDismiss = { naming = false }
            )
        }
        deleting?.let { p ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(p.name) },
                text = { Text(Str.get(R.string.fx_delete_q)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { effects.deleteProfile(p.name); deleting = null }) {
                        Text(Str.get(R.string.delete), color = accent)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { deleting = null }) {
                        Text(Str.get(R.string.cancel))
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        FxSlider(
            label = Str.get(R.string.fx_speed),
            value = fx.speed,
            range = SPEED_MIN..SPEED_MAX,
            valueText = String.format(Locale.US, "%.2f×", fx.speed),
            accent = accent,
            onChange = effects::setSpeed
        )
        FxSlider(
            label = Str.get(R.string.fx_pitch),
            value = fx.pitchSt,
            range = PITCH_MIN_ST..PITCH_MAX_ST,
            valueText = when {
                fx.pitchSt == 0f -> "0 st"
                else -> String.format(Locale.US, "%+.1f st", fx.pitchSt)
            },
            accent = accent,
            onChange = effects::setPitch
        )
        FxSlider(
            label = "Reverb",
            value = fx.reverbMix,
            range = 0f..1f,
            valueText = "${(fx.reverbMix * 100).roundToInt()} %",
            accent = accent,
            onChange = effects::setReverbMix
        )
        FxSlider(
            label = Str.get(R.string.fx_room),
            value = fx.roomSize,
            range = 0f..1f,
            valueText = "${(fx.roomSize * 100).roundToInt()} %",
            accent = accent,
            enabled = fx.reverbMix > 0f,
            onChange = effects::setRoomSize
        )
        FxSlider(
            label = Str.get(R.string.fx_tail),
            value = fx.damping,
            range = 0f..1f,
            valueText = "${(fx.damping * 100).roundToInt()} %",
            accent = accent,
            enabled = fx.reverbMix > 0f,
            onChange = effects::setDamping
        )
    }
}

@Composable
private fun FxSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    accent: Color,
    onChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.weight(1f)
            )
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = ink,
                activeTrackColor = accent,
                inactiveTrackColor = ink.copy(alpha = 0.18f),
                disabledThumbColor = ink.copy(alpha = 0.3f),
                disabledActiveTrackColor = ink.copy(alpha = 0.2f),
                disabledInactiveTrackColor = ink.copy(alpha = 0.1f)
            )
        )
    }
}

/** Panel flotante "Off vocal": quita la voz en tiempo real, con intensidad ajustable. */
@Composable
fun VocalPanel(
    fx: FxSettings,
    effects: EffectsEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentColor
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(32.dp)

    Column(
        modifier
            .fillMaxWidth()
            .shadow(30.dp, shape, ambientColor = accent, spotColor = accent)
            .background(palette.bg.copy(alpha = 0.9f), shape)
            .glass(shape, fill = 0.06f, border = 0.16f)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Str.get(R.string.off_vocal), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
            HelpBadge(
                Str.get(R.string.vocal_help),
                Modifier.padding(start = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = fx.vocalOff,
                onCheckedChange = effects::setVocalOff,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = ink.copy(alpha = 0.7f),
                    uncheckedTrackColor = ink.copy(alpha = 0.15f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            PanelCloseButton(onClose)
        }
        Spacer(Modifier.height(6.dp))
        FxSlider(
            label = Str.get(R.string.fx_intensity),
            value = fx.vocalAmount,
            range = 0f..1f,
            valueText = "${(fx.vocalAmount * 100).roundToInt()} %",
            accent = accent,
            enabled = fx.vocalOff,
            onChange = effects::setVocalAmount
        )
    }
}

/** Botón redondo de cerrar los paneles flotantes: tamaño fijo, nunca se aplasta. */
@Composable
private fun PanelCloseButton(onClose: () -> Unit) {
    Box(
        Modifier
            .requiredSize(40.dp)
            .pressable(onClose)
            .glass(CircleShape, fill = 0.14f, border = 0.22f),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Close, Str.get(R.string.close), tint = ink, modifier = Modifier.size(20.dp))
    }
}
