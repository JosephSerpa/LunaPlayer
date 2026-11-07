package com.melodia.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Qué estalla: corazones al dar "me gusta", destellos en las estrellas, corazón roto al quitarlo. */
enum class BurstKind { HEART, STAR, BROKEN }

/**
 * Estallido corto de partículas (0,65 s) alrededor de un botón. Se dibuja por encima del botón y
 * puede salirse de sus bordes; cuando termina no dibuja nada ni pide más cuadros.
 */
class BurstState {
    internal val progress = Animatable(1f)
    internal var kind by mutableStateOf(BurstKind.HEART)
    internal var seed by mutableIntStateOf(0)

    suspend fun play(kind: BurstKind) {
        this.kind = kind
        seed += 1
        progress.snapTo(0f)
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    companion object {
        const val DURATION_MS = 650
    }
}

@Composable
fun rememberBurst(): BurstState = remember { BurstState() }

/**
 * Dibuja el estallido de [state] centrado en este elemento. Conviene ponerlo antes de cualquier
 * recorte (clip/glass) en la cadena de modificadores, para que las partículas se vean fuera del botón.
 */
fun Modifier.burst(state: BurstState, color: Color, brokenColor: Color = Color(0xFF9A9AA6), reach: Dp = 30.dp): Modifier =
    drawWithContent {
        drawContent()
        val t = state.progress.value
        if (t >= 1f) return@drawWithContent
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = reach.toPx()
        when (state.kind) {
            BurstKind.HEART -> drawHearts(c, r, t, state.seed, color)
            BurstKind.STAR -> drawSparks(c, r * 0.85f, t, state.seed, color)
            BurstKind.BROKEN -> drawBroken(c, r, t, state.seed, brokenColor)
        }
    }

// --- Dibujo ------------------------------------------------------------------

/** Aleatorio fijo por estallido y partícula (siempre igual en cada cuadro del mismo estallido). */
private fun rnd(seed: Int, i: Int, k: Int): Float {
    val x = sin(seed * 12.9898 + i * 78.233 + k * 37.719) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + 0.42f * s)
    cubicTo(cx - 1.05f * s, cy - 0.25f * s, cx - 0.5f * s, cy - 1.05f * s, cx, cy - 0.45f * s)
    cubicTo(cx + 0.5f * s, cy - 1.05f * s, cx + 1.05f * s, cy - 0.25f * s, cx, cy + 0.42f * s)
    close()
}

private fun sparkPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    // Estrella de cuatro puntas.
    val inner = s * 0.32f
    for (k in 0 until 8) {
        val a = (k * PI / 4.0 - PI / 2.0).toFloat()
        val rr = if (k % 2 == 0) s else inner
        val x = cx + cos(a) * rr
        val y = cy + sin(a) * rr
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawHearts(c: Offset, r: Float, t: Float, seed: Int, color: Color) {
    val e = easeOut(t)
    val fade = (1f - t * t).coerceIn(0f, 1f)
    // Anillo que se abre.
    drawCircle(
        color.copy(alpha = 0.55f * (1f - e)), radius = r * (0.45f + 0.7f * e), center = c,
        style = Stroke(width = (3.dp.toPx() * (1f - e)).coerceAtLeast(0.5f))
    )
    val n = 9
    for (i in 0 until n) {
        val ang = (i * 2.0 * PI / n + rnd(seed, i, 0) * 0.5).toFloat()
        val dist = r * (0.35f + (0.75f + 0.45f * rnd(seed, i, 1)) * e)
        val p = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist - 6.dp.toPx() * e)
        if (i % 3 == 0) {
            drawCircle(color.copy(alpha = fade), radius = 2.2.dp.toPx() * (1f - 0.6f * t), center = p)
        } else {
            val s = (3.2f + 2.2f * rnd(seed, i, 2)).dp.toPx() * (1f - 0.45f * t)
            drawPath(heartPath(p.x, p.y, s), color.copy(alpha = fade))
        }
    }
}

private fun DrawScope.drawSparks(c: Offset, r: Float, t: Float, seed: Int, color: Color) {
    val e = easeOut(t)
    val fade = (1f - t).coerceIn(0f, 1f)
    val n = 7
    for (i in 0 until n) {
        val ang = (i * 2.0 * PI / n + rnd(seed, i, 0) * 0.6).toFloat()
        val dist = r * (0.3f + (0.7f + 0.4f * rnd(seed, i, 1)) * e)
        val p = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist)
        if (i % 2 == 0) {
            val s = (3.5f + 2f * rnd(seed, i, 2)).dp.toPx() * (1f - 0.5f * t)
            rotate(t * 90f, pivot = p) { drawPath(sparkPath(p.x, p.y, s), color.copy(alpha = fade)) }
        } else {
            drawCircle(color.copy(alpha = fade * 0.9f), radius = 1.8.dp.toPx() * (1f - 0.5f * t), center = p)
        }
    }
    // Destello en el centro, muy breve.
    if (t < 0.35f) {
        val k = 1f - t / 0.35f
        drawPath(sparkPath(c.x, c.y, r * 0.55f * (0.6f + 0.4f * (1f - k))), color.copy(alpha = 0.35f * k))
    }
}

private fun DrawScope.drawBroken(c: Offset, r: Float, t: Float, seed: Int, color: Color) {
    val e = easeOut(t)
    val fade = (1f - t * t).coerceIn(0f, 1f)
    val s = r * 0.42f
    val drop = r * 0.55f * t * t
    val apart = r * 0.28f * e
    // Las dos mitades del corazón se separan y caen.
    for (side in listOf(-1f, 1f)) {
        val off = Offset(c.x + side * apart, c.y + drop)
        rotate(side * 22f * e, pivot = Offset(c.x, c.y + s * 0.4f)) {
            // Cada mitad se recorta por el centro de su propio corazón (que ya se movió de lado).
            val big = size.width + r * 4f
            clipRect(
                left = if (side < 0) off.x - big else off.x, right = if (side < 0) off.x else off.x + big,
                top = -big, bottom = size.height + big
            ) {
                drawPath(heartPath(off.x, off.y, s), color.copy(alpha = fade))
            }
        }
    }
    // Pedacitos que caen.
    for (i in 0 until 6) {
        val ang = (PI * (0.15 + 0.7 * rnd(seed, i, 0))).toFloat() // hacia abajo
        val dist = r * (0.25f + 0.6f * e * (0.6f + 0.4f * rnd(seed, i, 1)))
        val p = Offset(c.x + cos(ang) * dist * (if (i % 2 == 0) 1f else -1f), c.y + sin(ang) * dist + drop)
        drawCircle(color.copy(alpha = fade * 0.8f), radius = (1.4f + rnd(seed, i, 2)).dp.toPx(), center = p)
    }
}
