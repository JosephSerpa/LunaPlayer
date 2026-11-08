package com.melodia.app.ui.options

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import com.melodia.app.audio.BOOST_MAX_DB
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.melodia.app.ui.library.PlaylistNameDialog
import com.melodia.app.ui.theme.LocalPalette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.melodia.app.audio.EQ_BANDS
import com.melodia.app.audio.EQ_MIN_BANDS
import com.melodia.app.audio.EqBand
import com.melodia.app.audio.EqMath
import com.melodia.app.audio.FilterType
import com.melodia.app.audio.GRAPH_MAX_HZ
import com.melodia.app.audio.GRAPH_MIN_HZ
import com.melodia.app.audio.EQ_MAX_DB
import com.melodia.app.audio.EQ_MIN_DB
import com.melodia.app.audio.EqSettings
import com.melodia.app.audio.presetLabel
import com.melodia.app.audio.EqualizerEngine
import com.melodia.app.audio.PREAMP_MAX_DB
import com.melodia.app.audio.PREAMP_MIN_DB
import com.melodia.app.audio.Q_MAX
import com.melodia.app.audio.Q_MIN
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.HelpBadge
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.ink
import com.melodia.app.ui.theme.inverse
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Ajustes → Ecualizador de 0 a 7 puntos. */
@Composable
fun EqualizerScreen(
    settings: EqSettings,
    eq: EqualizerEngine,
    onBack: () -> Unit
) {
    val accent = accentColor
    val onAccent = accent.onAccent()
    var selectedRaw by rememberSaveable { mutableStateOf(2) }
    // Con 0 puntos no hay ninguno seleccionado.
    val selected = if (settings.bands.isEmpty()) -1 else selectedRaw.coerceIn(0, settings.bands.lastIndex)
    val band = settings.bands.getOrNull(selected)
    // Punto más alto de la curva total (con la ganancia general): si es muy alto, la barra se pone roja.
    val peakDb = remember(settings) {
        (EqMath.totalCurve(settings, EqMath.logFrequencies(120)).maxOrNull()?.toFloat() ?: 0f) + settings.boostDb
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp)
    ) {
        SettingsHeader(title = Str.get(R.string.equalizer), onBack = onBack)

        Spacer(Modifier.height(22.dp))

        // --- Ecualizador ---
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(32.dp), fill = 0.07f)
                .padding(18.dp)
        ) {
            // Orden: perfil (arriba), ecualizador global y luego los botones de puntos con el interruptor.
            Column(Modifier.alpha(if (settings.enabled) 1f else 0.4f)) {
                // Perfiles: predefinidos y propios, en un desplegable.
                EqProfilePicker(settings = settings, eq = eq)

                // Autoguardado: los cambios se guardan solos en el perfil propio en uso.
                val autoSave by eq.autoSave.collectAsState()
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoSave,
                        onCheckedChange = { eq.setAutoSave(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accent,
                            checkmarkColor = onAccent,
                            uncheckedColor = ink.copy(alpha = 0.5f)
                        )
                    )
                    Text(
                        Str.get(R.string.eq_autosave),
                        style = MaterialTheme.typography.labelLarge,
                        color = ink.copy(alpha = 0.85f)
                    )
                    HelpBadge(Str.get(R.string.eq_autosave_hint), Modifier.padding(start = 2.dp))
                }

            }

            Spacer(Modifier.height(10.dp))

            GlobalEqRow(eq)

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Agregar / quitar curvas: solo iconos (de 0 a 7).
                val canAdd = settings.bands.size < EQ_BANDS
                val canRemove = settings.bands.size > EQ_MIN_BANDS
                PointButton(
                    icon = Icons.Rounded.Remove, description = Str.get(R.string.eq_remove_curve),
                    enabled = canRemove, filled = false,
                    onClick = { if (selected >= 0) eq.removeBand(selected) }
                )
                Spacer(Modifier.width(8.dp))
                PointButton(
                    icon = Icons.Rounded.Add, description = Str.get(R.string.eq_add_curve),
                    enabled = canAdd, filled = true,
                    onClick = { val i = eq.addBand(); if (i >= 0) selectedRaw = i }
                )
                HelpBadge(
                    Str.get(R.string.eq_help),
                    Modifier.padding(start = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                // Estado del ecualizador junto a su interruptor.
                Text(
                    Str.get(if (settings.enabled) R.string.eq_state_on else R.string.eq_state_off),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (settings.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { eq.setEnabled(it) },
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


            Column(Modifier.alpha(if (settings.enabled) 1f else 0.4f)) {
                Spacer(Modifier.height(16.dp))

                // Gráfica de la curva + barra vertical de la ganancia general (derecha)
                val scroll = rememberScrollState()
                Row(Modifier.fillMaxWidth().height(280.dp)) {
                    // Ganancia general: barra vertical a la izquierda, con el mismo alto que la gráfica.
                    GainBar(
                        gainDb = settings.preampDb,
                        peakDb = peakDb,
                        onGain = { db -> eq.setPreamp(db) },
                        modifier = Modifier.width(38.dp).fillMaxHeight()
                    )
                    Spacer(Modifier.width(8.dp))
                    // La gráfica mide el doble que el espacio visible: se desliza de lado para tener más control.
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Recorta la cuadrícula a las esquinas redondeadas: nada sobresale.
                            .clip(RoundedCornerShape(22.dp))
                            .background(ink.copy(alpha = 0.07f))
                    ) {
                        val viewport = maxWidth
                        val density = LocalDensity.current
                        val viewportPx = with(density) { viewport.toPx() }
                        val margin = with(density) { 44.dp.toPx() }

                        // Si el punto elegido queda fuera de la vista, se desliza hasta centrarlo.
                        LaunchedEffect(selected, settings.bands.size, viewportPx) {
                            val b = settings.bands.getOrNull(selected) ?: return@LaunchedEffect
                            snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
                            val lo = kotlin.math.log10(GRAPH_MIN_HZ)
                            val hi = kotlin.math.log10(GRAPH_MAX_HZ)
                            val x = ((kotlin.math.log10(b.freqHz.toDouble()) - lo) / (hi - lo) * viewportPx * 2f).toFloat()
                            val v = scroll.value
                            if (x < v + margin || x > v + viewportPx - margin) {
                                scroll.animateScrollTo((x - viewportPx / 2f).toInt().coerceIn(0, scroll.maxValue))
                            }
                        }

                        Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                            EqGraph(
                                settings = settings,
                                selected = selected,
                                onSelect = { selectedRaw = it },
                                onBandDrag = { i, hz, db -> eq.setFrequencyAndGain(i, hz, db) },
                                modifier = Modifier.width(viewport * 2).fillMaxHeight(),
                                scrollX = { scroll.value }
                            )
                        }

                    }
                }

                // Vista general de la curva completa, justo debajo de la gráfica.
                Spacer(Modifier.height(10.dp))
                val maxV = scroll.maxValue
                val ready = maxV in 1 until Int.MAX_VALUE
                EqOverview(
                    settings = settings,
                    windowStart = if (ready) scroll.value.toFloat() / maxV * 0.5f else 0f,
                    windowSize = 0.5f,
                    onCenter = { c ->
                        if (ready) {
                            val target = ((c - 0.25f).coerceIn(0f, 0.5f) / 0.5f * maxV)
                            scroll.dispatchRawDelta(target - scroll.value)
                        }
                    },
                    // Todo el ancho de la tarjeta: mismo margen a ambos lados.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(OVERVIEW_RADIUS))
                        .background(ink.copy(alpha = 0.07f))
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Ajuste fino de la banda seleccionada ---
        if (band == null) {
            Text(
                Str.get(R.string.eq_no_curves),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .alpha(if (settings.enabled) 1f else 0.4f)
                    .glass(RoundedCornerShape(32.dp), fill = 0.07f)
                    .padding(22.dp)
            )
        } else Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .alpha(if (settings.enabled) 1f else 0.4f)
                .glass(RoundedCornerShape(32.dp), fill = 0.07f)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Str.get(R.string.eq_curve_n, selected + 1), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${fmtHz(band.freqHz)} Hz" + if (band.type.usesGain) " · ${fmtDb(band.gainDb)} dB" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    Str.get(R.string.reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .pressable { eq.resetBand(selected) }
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Comportamiento de la curva de este punto.
            FilterTypePicker(
                selected = band.type,
                onSelect = { eq.setType(selected, it) }
            )

            Spacer(Modifier.height(10.dp))

            if (band.type.usesGain) LabeledSlider(
                label = Str.get(R.string.eq_gain),
                valueText = fmtDb(band.gainDb) + " dB",
                fraction = (band.gainDb - EQ_MIN_DB) / (EQ_MAX_DB - EQ_MIN_DB),
                onFraction = { f ->
                    val g = (f * (EQ_MAX_DB - EQ_MIN_DB) * 10f).roundToInt() / 10f + EQ_MIN_DB
                    eq.setGain(selected, g)
                },
                accent = accent,
                hint = Str.get(R.string.eq_gain_hint)
            )

            LabeledSlider(
                label = Str.get(R.string.eq_q),
                valueText = String.format(Locale.US, "Q %.2f", band.q),
                fraction = logFraction(band.q, Q_MIN, Q_MAX),
                onFraction = { f ->
                    val q = fromLogFraction(f, Q_MIN, Q_MAX)
                    eq.setQ(selected, (q * 20f).roundToInt() / 20f)
                },
                accent = accent,
                hint = Str.get(R.string.eq_q_hint)
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- Ganancia previa y restablecer ---
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .alpha(if (settings.enabled) 1f else 0.4f)
                .glass(RoundedCornerShape(32.dp), fill = 0.07f)
                .padding(18.dp)
        ) {
            // Volumen extra manual: puede saturar, lo decide quien usa la app.
            LabeledSlider(
                label = Str.get(R.string.eq_boost),
                valueText = (if (settings.boostDb < 0.05f) "0" else "+" + fmtDb(settings.boostDb).removePrefix("+")) + " dB",
                fraction = settings.boostDb / BOOST_MAX_DB,
                onFraction = { f -> eq.setBoost((f * BOOST_MAX_DB * 2f).roundToInt() / 2f) },
                accent = overloadTint(accent, peakDb),
                hint = Str.get(R.string.eq_boost_hint)
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Str.get(R.string.eq_limiter), style = MaterialTheme.typography.titleMedium, color = ink)
                    Text(Str.get(R.string.eq_limiter_sub), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f))
                }
                Switch(
                    checked = settings.limiter,
                    onCheckedChange = { eq.setLimiter(it) },
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

            Text(
                Str.get(R.string.eq_reset_all),
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { eq.resetAll() }
                    .glass(RoundedCornerShape(50), fill = 0.1f, border = 0.2f)
                    .padding(vertical = 13.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Controles
// ---------------------------------------------------------------------------

/** Botón redondo con un solo icono (+ / −) para agregar o quitar puntos. */
@Composable
private fun PointButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit
) {
    val accent = accentColor
    Box(
        Modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pressable { if (enabled) onClick() }
            .then(
                if (filled) Modifier.background(accent, CircleShape)
                else Modifier.glass(CircleShape, fill = 0.1f, border = 0.2f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (filled) accent.onAccent() else ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    fraction: Float,
    onFraction: (Float) -> Unit,
    accent: Color,
    hint: String
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            HelpBadge(hint, Modifier.padding(start = 2.dp))
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = accent)
        }
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onFraction,
            colors = SliderDefaults.colors(
                thumbColor = ink,
                activeTrackColor = accent,
                inactiveTrackColor = ink.copy(alpha = 0.18f)
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Formato y escalas
// ---------------------------------------------------------------------------

private fun fmtDb(db: Float): String {
    if (abs(db) < 0.05f) return "0"
    return String.format(Locale.US, "%+.1f", db).removeSuffix(".0")
}

private fun fmtHz(hz: Float): String =
    if (hz >= 1000f) String.format(Locale.US, "%.1fk", hz / 1000f).replace(".0k", "k")
    else hz.roundToInt().toString()

private fun logFraction(v: Float, lo: Float, hi: Float): Float =
    (ln(v / lo) / ln(hi / lo)).coerceIn(0f, 1f)

private fun fromLogFraction(f: Float, lo: Float, hi: Float): Float =
    lo * exp(f.coerceIn(0f, 1f) * ln(hi / lo))

// ---------------------------------------------------------------------------
// Desplegable de perfiles
// ---------------------------------------------------------------------------

@Composable
private fun EqProfilePicker(settings: EqSettings, eq: EqualizerEngine) {
    val accent = accentColor
    val profiles by eq.profiles.collectAsState()
    val assignments by eq.assignments.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val palette = LocalPalette.current

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable { menuOpen = true }
                .glass(RoundedCornerShape(18.dp), fill = 0.08f, border = 0.14f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Str.get(R.string.eq_profile), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.6f))
            Spacer(Modifier.width(12.dp))
            Text(
                presetLabel(settings.preset),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ExpandMore, null, tint = ink)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(palette.bg.copy(alpha = 0.97f))
        ) {
            if (profiles.isNotEmpty()) {
                MenuLabel(Str.get(R.string.eq_my_profiles))
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                ProfileText(p.preset, settings.preset == p.preset)
                                assignments.values.firstOrNull { it.profile == p.preset }?.let {
                                    Text(
                                        Str.get(R.string.eq_assigned_to, it.deviceName),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ink.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        },
                        onClick = { eq.applyProfile(p); menuOpen = false },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline, Str.get(R.string.eq_delete_profile),
                                tint = ink.copy(alpha = 0.5f),
                                modifier = Modifier.pressable { eq.deleteProfile(p.preset) }
                            )
                        }
                    )
                }
            }
            if (profiles.isEmpty()) MenuLabel(Str.get(R.string.eq_no_profiles))
            HorizontalDivider(color = ink.copy(alpha = 0.1f))
            DropdownMenuItem(
                text = { Text(Str.get(R.string.eq_save_current), color = accent) },
                leadingIcon = { Icon(Icons.Rounded.Add, null, tint = accent) },
                onClick = { menuOpen = false; naming = true }
            )
        }
    }

    if (naming) {
        SaveProfileDialog(
            eq = eq,
            initial = if (profiles.none { it.preset == settings.preset }) "" else settings.preset,
            onDismiss = { naming = false }
        )
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun ProfileText(name: String, selected: Boolean) {
    Text(
        (if (selected) "✓  " else "") + presetLabel(name),
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) accentColor else ink
    )
}

// ---------------------------------------------------------------------------
// Tipo de filtro del punto, con su mini gráfica
// ---------------------------------------------------------------------------

private val MINI_FREQS = EqMath.logFrequencies(48)

/** Dibujo diminuto de cómo se comporta este tipo de filtro. */
@Composable
private fun FilterMiniGraph(type: FilterType, modifier: Modifier = Modifier) {
    val accent = accentColor
    val inkC = ink
    val curve = remember(type) {
        EqMath.bandCurve(EqBand(1000f, 10f, type.defaultQ, type), MINI_FREQS)
    }
    Canvas(modifier.size(width = 46.dp, height = 28.dp)) {
        val w = size.width
        val h = size.height
        val zeroY = h / 2f
        drawLine(inkC.copy(alpha = 0.2f), Offset(0f, zeroY), Offset(w, zeroY), strokeWidth = 1.dp.toPx())
        val path = androidx.compose.ui.graphics.Path()
        for (k in curve.indices) {
            val x = k / (curve.size - 1f) * w
            val db = curve[k].toFloat().coerceIn(-14f, 14f)
            val y = zeroY - db / 14f * (h / 2f - 2.dp.toPx())
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path, accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun FilterTypePicker(selected: FilterType, onSelect: (FilterType) -> Unit) {
    val accent = accentColor
    val palette = LocalPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable { menuOpen = true }
                .glass(RoundedCornerShape(18.dp), fill = 0.08f, border = 0.14f)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Str.get(R.string.eq_type), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.6f))
            Spacer(Modifier.width(12.dp))
            Text(
                selected.label,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            FilterMiniGraph(selected)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ExpandMore, null, tint = ink)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(palette.bg.copy(alpha = 0.97f))
        ) {
            FilterType.values().forEach { type ->
                DropdownMenuItem(
                    text = { ProfileText(type.label, type == selected) },
                    trailingIcon = { FilterMiniGraph(type) },
                    onClick = { onSelect(type); menuOpen = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ecualizador global y perfiles por dispositivo
// ---------------------------------------------------------------------------

@Composable
private fun GlobalEqRow(eq: EqualizerEngine) {
    val accent = accentColor
    val on by eq.global.collectAsState()
    var warn by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(Str.get(R.string.eq_global_title), style = MaterialTheme.typography.titleMedium, color = ink)
            Text(Str.get(R.string.eq_global_sub), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f))
        }
        HelpBadge(Str.get(R.string.eq_global_help), Modifier.padding(horizontal = 6.dp))
        Switch(
            checked = on,
            onCheckedChange = { want ->
                if (want) warn = true else eq.setGlobal(false)
            },
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
    if (warn) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { warn = false },
            containerColor = LocalPalette.current.bg,
            title = { Text(Str.get(R.string.eq_global_warn_title), color = ink) },
            text = { Text(Str.get(R.string.eq_global_warn), color = ink.copy(alpha = 0.85f)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    warn = false
                    if (!eq.setGlobal(true)) {
                        android.widget.Toast.makeText(context, Str.get(R.string.eq_global_fail), android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text(Str.get(R.string.eq_global_activate), color = accent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { warn = false }) {
                    Text(Str.get(R.string.cancel), color = ink)
                }
            }
        )
    }
}

/** Guardar perfil: nombre (por defecto el del dispositivo elegido) y, opcionalmente, a qué dispositivo asignarlo. */
@Composable
private fun SaveProfileDialog(eq: EqualizerEngine, initial: String, onDismiss: () -> Unit) {
    val accent = accentColor
    val palette = LocalPalette.current
    val devices by eq.devices.collectAsState()
    var name by remember { mutableStateOf(initial) }
    var picked by remember { mutableStateOf<com.melodia.app.audio.OutputDevice?>(null) }
    // Mientras el nombre sea el automático (o esté vacío), cambia con el dispositivo; si se edita, se respeta.
    var auto by remember { mutableStateOf(initial.isBlank()) }
    val shape = RoundedCornerShape(28.dp)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.bg.copy(alpha = 0.97f), shape)
                .glass(shape, fill = 0.05f, border = 0.14f)
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Text(Str.get(R.string.eq_save_profile), style = MaterialTheme.typography.titleLarge, color = ink)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it; auto = false },
                placeholder = { Text(Str.get(R.string.eq_profile_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = ink.copy(alpha = 0.2f),
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = accent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Str.get(R.string.eq_assign_device), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                HelpBadge(Str.get(R.string.eq_assign_hint), Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            val options = listOf<com.melodia.app.audio.OutputDevice?>(null) + devices
            options.forEach { d ->
                val sel = picked?.key == d?.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .pressable {
                            picked = d
                            if (auto || name.isBlank()) { name = d?.name.orEmpty(); auto = true }
                        }
                        .glass(RoundedCornerShape(16.dp), fill = if (sel) 0.16f else 0.06f, border = if (sel) 0.4f else 0.12f)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        d?.name ?: Str.get(R.string.eq_assign_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (sel) accent else ink,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (devices.isEmpty()) {
                Text(Str.get(R.string.eq_assign_no_devices), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Str.get(R.string.cancel),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = ink,
                    modifier = Modifier.weight(1f).pressable(onDismiss)
                        .glass(RoundedCornerShape(50), fill = 0.08f, border = 0.16f).padding(vertical = 12.dp)
                )
                Text(
                    Str.get(R.string.save),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = accent.onAccent(),
                    modifier = Modifier.weight(1f)
                        .pressable { if (name.isNotBlank()) { eq.saveProfile(name, picked); onDismiss() } }
                        .background(accent.copy(alpha = if (name.isNotBlank()) 1f else 0.4f), RoundedCornerShape(50))
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}
