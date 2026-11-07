package com.melodia.app.ui.options

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.pressable

private data class Credit(val name: String, val by: String, val license: String)

private val LIBS = listOf(
    Credit("Jetpack Compose, AndroidX", "Google / The Android Open Source Project", "Apache 2.0"),
    Credit("Media3 (ExoPlayer)", "Google / The Android Open Source Project", "Apache 2.0"),
    Credit("Coil", "Coil Contributors", "Apache 2.0"),
    Credit("Guava", "Google", "Apache 2.0"),
    Credit("Kotlin, kotlinx.coroutines", "JetBrains", "Apache 2.0")
)

private val FONTS = listOf(
    Credit("Outfit", "The Outfit Project Authors", "SIL OFL 1.1"),
    Credit("Sora", "The Sora Project Authors", "SIL OFL 1.1"),
    Credit("Playfair Display", "The Playfair Display Project Authors", "SIL OFL 1.1")
)

/** Licencias de código abierto y créditos de la app. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val accent = accentColor
    // Texto completo de cada licencia: solo se lee al desplegarla.
    var open by remember { mutableStateOf<Int?>(null) }
    fun raw(id: Int) = runCatching {
        ctx.resources.openRawResource(id).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp)
    ) {
        SettingsHeader(title = Str.get(R.string.licenses_title), onBack = onBack)

        SettingsSectionLabel(Str.get(R.string.licenses_libs))
        SettingsCard {
            LIBS.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${c.by} · ${c.license}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LicenseToggle(accent, Str.get(R.string.licenses_full_text) + " — Apache 2.0", open == 0) {
            open = if (open == 0) null else 0
        }
        if (open == 0) LicenseText(raw(R.raw.license_apache2))

        SettingsSectionLabel(Str.get(R.string.licenses_fonts))
        SettingsCard {
            FONTS.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${c.by} · ${c.license}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val fontTexts = listOf(
            "Outfit" to R.raw.license_ofl_outfit,
            "Sora" to R.raw.license_ofl_sora,
            "Playfair Display" to R.raw.license_ofl_playfair
        )
        fontTexts.forEachIndexed { i, (name, res) ->
            val idx = i + 1
            LicenseToggle(accent, Str.get(R.string.licenses_full_text) + " — $name", open == idx) {
                open = if (open == idx) null else idx
            }
            if (open == idx) LicenseText(raw(res))
        }

        Spacer(Modifier.height(18.dp))
        SettingsCard {
            Text(
                Str.get(R.string.licenses_legal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LicenseToggle(accent: androidx.compose.ui.graphics.Color, label: String, expanded: Boolean, onClick: () -> Unit) {
    Text(
        (if (expanded) "▾ " else "▸ ") + label,
        style = MaterialTheme.typography.labelLarge,
        color = accent,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp)
    )
}

@Composable
private fun LicenseText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}
