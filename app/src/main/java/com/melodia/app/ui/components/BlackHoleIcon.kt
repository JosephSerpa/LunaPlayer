package com.melodia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Agujero negro minimalista: núcleo oscuro, anillo de luz y tres brazos de disco que giran muy
 * despacio mientras [active] (quieto cuando no). Solo anima cuando está activo.
 */
@Composable
fun BlackHoleIcon(
    active: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val angle by rememberSpinAngle(playing = active, periodMs = 9000)
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f
        val sw = (this.size.minDimension * 0.07f).coerceAtLeast(1.2f)
        val base = if (active) angle else 28f

        // Halo suave alrededor.
        drawCircle(
            Brush.radialGradient(
                listOf(tint.copy(alpha = 0.0f), tint.copy(alpha = 0.30f), tint.copy(alpha = 0.0f)),
                center = c, radius = r
            ),
            radius = r, center = c
        )
        // Brazos del disco (giran).
        fun arm(radiusF: Float, from: Float, sweep: Float, alpha: Float, width: Float) {
            val rr = r * radiusF
            drawArc(
                color = tint.copy(alpha = alpha),
                startAngle = base + from, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
        }
        arm(0.80f, 0f, 120f, 0.95f, sw)
        arm(0.66f, 140f, 100f, 0.75f, sw * 0.9f)
        arm(0.88f, 250f, 70f, 0.55f, sw * 0.8f)
        // Anillo de luz y núcleo.
        drawCircle(tint.copy(alpha = 0.9f), radius = r * 0.44f, center = c, style = Stroke(width = sw * 0.9f))
        drawCircle(Color.Black, radius = r * 0.38f, center = c)
    }
}
