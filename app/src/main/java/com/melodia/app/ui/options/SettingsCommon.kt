package com.melodia.app.ui.options

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.melodia.app.ui.components.HelpBadge
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.ink

/** Cabecera común de las pantallas de ajustes: botón atrás + título. */
@Composable
fun SettingsHeader(title: String, onBack: () -> Unit) {
    val accent = accentColor
    Row(
        Modifier.padding(start = 16.dp, end = 24.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .pressable(onBack)
                .glass(CircleShape, fill = 0.12f),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, Str.get(R.string.back), tint = ink)
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

/** Título pequeño que separa grupos de ajustes (con ayuda opcional en un (!)). */
@Composable
fun SettingsSectionLabel(text: String, help: String? = null) {
    Row(
        Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (help != null) HelpBadge(help, Modifier.padding(start = 2.dp))
    }
}

/** Tarjeta de vidrio para agrupar controles. */
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), fill = 0.07f)
            .padding(18.dp),
        content = content
    )
}

/** Fila que abre otra pantalla de ajustes. */
@Composable
fun SettingsLink(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    statusColor: Color? = null
) {
    val accent = accentColor
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .pressable(onClick)
            .glass(RoundedCornerShape(24.dp), fill = 0.07f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).glass(RoundedCornerShape(14.dp), fill = 0.1f, border = 0.1f),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = ink.copy(alpha = 0.5f)
        )
    }
}
