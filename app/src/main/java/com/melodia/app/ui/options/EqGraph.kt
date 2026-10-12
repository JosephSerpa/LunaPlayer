package com.melodia.app.ui.options

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melodia.app.audio.EQ_MAX_DB
import com.melodia.app.audio.EQ_MIN_DB
import com.melodia.app.audio.EqMath
import com.melodia.app.audio.EqSettings
import com.melodia.app.audio.GRAPH_MAX_HZ
import com.melodia.app.audio.GRAPH_MIN_HZ
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.theme.ink
import com.melodia.app.ui.theme.inverse
import com.melodia.app.ui.theme.Outfit
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val LOG_MIN = log10(GRAPH_MIN_HZ).toFloat()
private val LOG_MAX = log10(GRAPH_MAX_HZ).toFloat()
private val FREQS = EqMath.logFrequencies(180)

private fun freqToX(f: Float, w: Float): Float =
    (log10(f.toDouble()).toFloat() - LOG_MIN) / (LOG_MAX - LOG_MIN) * w

private fun xToFreq(x: Float, w: Float): Float =
    10.0.pow((LOG_MIN + (x / w) * (LOG_MAX - LOG_MIN)).toDouble()).toFloat()

private fun gainToY(g: Float, h: Float): Float =
    (1f - (g - EQ_MIN_DB) / (EQ_MAX_DB - EQ_MIN_DB)) * h

private fun yToGain(y: Float, h: Float): Float {
    val g = EQ_MIN_DB + (1f - y / h) * (EQ_MAX_DB - EQ_MIN_DB)
    return ((g * 2f).roundToInt() / 2f).coerceIn(EQ_MIN_DB, EQ_MAX_DB)
}

/**
 * Curva de respuesta del ecualizador: se ve en tiempo real cómo queda el sonido.
 * Los puntos (5 a 7) se pueden arrastrar: izquierda/derecha cambia la frecuencia
 * y arriba/abajo la ganancia.
 */
@Composable
fun EqGraph(
    settings: EqSettings,
    selected: Int,
    onSelect: (Int) -> Unit,
    onBandDrag: (index: Int, freqHz: Float, gainDb: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentColor
    val inkC = ink
    val inverseC = inverse
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(inkC) {
        TextStyle(fontFamily = Outfit, fontSize = 10.sp, color = inkC.copy(alpha = 0.45f))
    }
    val nodeStyle = remember(inkC) {
        TextStyle(fontFamily = Outfit, fontSize = 11.sp, color = inkC.copy(alpha = 0.9f))
    }
    val current by rememberUpdatedState(settings)
    val selectCb by rememberUpdatedState(onSelect)
    val dragCb by rememberUpdatedState(onBandDrag)
    var dragging by remember { mutableStateOf(-1) }

    val total = remember(settings) { EqMath.totalCurve(settings, FREQS) }
    val selectedBand = remember(settings, selected) {
        settings.bands.getOrNull(selected)?.let { EqMath.bandCurve(it, FREQS) }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val i = nearestBand(current, o, size.width.toFloat(), size.height.toFloat(), 56.dp.toPx())
                    if (i >= 0) selectCb(i)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val i = nearestBand(current, o, size.width.toFloat(), size.height.toFloat(), 56.dp.toPx())
                        dragging = i
                        if (i >= 0) selectCb(i)
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                    onDrag = { change, _ ->
                        val i = dragging
                        if (i >= 0) {
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val x = change.position.x.coerceIn(0f, w)
                            val y = change.position.y.coerceIn(0f, h)
                            dragCb(i, xToFreq(x, w), yToGain(y, h))
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // --- Cuadrícula ---
        val gridDb = listOf(-20f, -10f, 0f, 10f, 20f)
        for (db in gridDb) {
            val y = gainToY(db, h)
            drawLine(
                inkC.copy(alpha = if (db == 0f) 0.28f else 0.08f),
                Offset(0f, y), Offset(w, y),
                strokeWidth = if (db == 0f) 1.5.dp.toPx() else 1.dp.toPx()
            )
            val text = when {
                db > 0f -> "+${db.toInt()}"
                else -> "${db.toInt()}"
            }
            drawText(
                textMeasurer = measurer, text = text, style = labelStyle,
                topLeft = Offset(6.dp.toPx(), (y + 2.dp.toPx()).coerceAtMost(h - 14.dp.toPx()))
            )
        }
        val gridHz = listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)
        for (f in gridHz) {
            val x = freqToX(f, w)
            val major = f == 100f || f == 1000f || f == 10000f
            drawLine(
                inkC.copy(alpha = if (major) 0.12f else 0.05f),
                Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx()
            )
            if (major) {
                val label = when (f) { 100f -> "100 Hz"; 1000f -> "1 kHz"; else -> "10 kHz" }
                drawText(
                    textMeasurer = measurer, text = label, style = labelStyle,
                    topLeft = Offset(x + 4.dp.toPx(), h - 16.dp.toPx())
                )
            }
        }

        // --- Curva total ---
        val line = Path()
        for (k in FREQS.indices) {
            val x = k / (FREQS.size - 1f) * w
            val y = gainToY(total[k].toFloat().coerceIn(EQ_MIN_DB, EQ_MAX_DB), h)
            if (k == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val zeroY = gainToY(0f, h)
        val fill = Path().apply {
            addPath(line)
            lineTo(w, zeroY)
            lineTo(0f, zeroY)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.03f)),
                startY = 0f, endY = h
            )
        )

        // --- Campana de la banda seleccionada (muestra su ancho / Q) ---
        if (selectedBand != null) {
            val bell = Path()
            for (k in FREQS.indices) {
                val x = k / (FREQS.size - 1f) * w
                val y = gainToY(selectedBand[k].toFloat().coerceIn(EQ_MIN_DB, EQ_MAX_DB), h)
                if (k == 0) bell.moveTo(x, y) else bell.lineTo(x, y)
            }
            drawPath(
                bell, inkC.copy(alpha = 0.55f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawPath(
            line, accent,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // --- Puntos del ecualizador ---
        settings.bands.forEachIndexed { i, b ->
            val p = Offset(freqToX(b.freqHz, w), gainToY(if (b.type.usesGain) b.gainDb else 0f, h))
            val sel = i == selected
            // Si la subida es muy fuerte (puede saturar el sonido), el punto se tiñe de rojo.
            val effective = if (b.type.usesGain) b.gainDb + settings.preampDb else 0f
            val tone = overloadTint(accent, effective)
            if (sel) drawCircle(tone.copy(alpha = 0.28f), 22.dp.toPx(), p)
            drawCircle(if (sel) overloadTint(inkC, effective) else tone, if (sel) 9.dp.toPx() else 6.dp.toPx(), p)
            drawCircle(inverseC, if (sel) 3.5.dp.toPx() else 2.5.dp.toPx(), p)
            val ly = if (p.y < 32.dp.toPx()) p.y + 14.dp.toPx() else p.y - 28.dp.toPx()
            drawText(
                textMeasurer = measurer, text = "${i + 1}", style = nodeStyle,
                topLeft = Offset(p.x - 3.dp.toPx(), ly)
            )
        }
    }
}

private fun nearestBand(s: EqSettings, o: Offset, w: Float, h: Float, maxDist: Float): Int {
    var best = -1
    var bestDist = maxDist
    for (i in s.bands.indices) {
        val b = s.bands[i]
        val dx = freqToX(b.freqHz, w) - o.x
        val dy = gainToY(if (b.type.usesGain) b.gainDb else 0f, h) - o.y
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

// ---------------------------------------------------------------------------
// Aviso de saturación y barra vertical de ganancia
// ---------------------------------------------------------------------------

private val OverloadRed = Color(0xFFFF4545)

/**
 * Tiñe [base] de rojo cuando la subida efectiva ([effectiveDb] = ganancia + ganancia previa)
 * es tan alta que puede saturar el sonido: empieza en +4 dB y es rojo pleno desde +12 dB.
 */
internal fun overloadTint(base: Color, effectiveDb: Float): Color {
    val t = ((effectiveDb - 4f) / 8f).coerceIn(0f, 1f)
    return if (t <= 0f) base else lerp(base, OverloadRed, t)
}

/**
 * Barra vertical de la ganancia general (ganancia previa), de -20 a +20 dB, a la derecha de la gráfica.
 * Se tiñe de rojo cuando la curva total sube tanto que puede saturar ([peakDb] = punto más alto
 * de la curva, ya con la ganancia general incluida).
 */
@Composable
fun GainBar(
    gainDb: Float,
    peakDb: Float,
    onGain: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = accentColor
    val inkC = ink
    val inverseC = inverse
    val gainCb by rememberUpdatedState(onGain)

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { o -> gainCb(yToGain(o.y, size.height.toFloat())) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o -> gainCb(yToGain(o.y, size.height.toFloat())) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val h = size.height.toFloat()
                        gainCb(yToGain(change.position.y.coerceIn(0f, h), h))
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val trackW = 14.dp.toPx()
        val x0 = (w - trackW) / 2f
        val zeroY = gainToY(0f, h)
        val tone = overloadTint(accent, peakDb)

        // Riel
        drawRoundRect(
            inkC.copy(alpha = 0.12f),
            topLeft = Offset(x0, 0f), size = Size(trackW, h),
            cornerRadius = CornerRadius(trackW / 2f)
        )
        // Relleno desde 0 dB hasta el valor
        val y = gainToY(gainDb, h)
        val height = kotlin.math.abs(y - zeroY)
        if (height > 0.5f) {
            drawRoundRect(
                tone,
                topLeft = Offset(x0, minOf(y, zeroY)), size = Size(trackW, height),
                cornerRadius = CornerRadius(trackW / 2f)
            )
        }
        // Marca del 0 dB, dentro del riel (nada sobresale)
        drawLine(
            inkC.copy(alpha = 0.45f),
            Offset(x0 + 2.dp.toPx(), zeroY), Offset(x0 + trackW - 2.dp.toPx(), zeroY),
            strokeWidth = 1.5.dp.toPx()
        )
        // Mango: siempre dentro de la barra
        val r = 10.dp.toPx()
        val c = Offset(w / 2f, y.coerceIn(r + 2.dp.toPx(), h - r - 2.dp.toPx()))
        drawCircle(tone.copy(alpha = 0.25f), r + 4.dp.toPx(), c)
        drawCircle(overloadTint(inkC, peakDb), r, c)
        drawCircle(inverseC, 3.5.dp.toPx(), c)
    }
}
