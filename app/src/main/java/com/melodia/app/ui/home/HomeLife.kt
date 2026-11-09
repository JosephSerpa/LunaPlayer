package com.melodia.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.melodia.app.data.TileKind
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * "Vida" de la pantalla de Inicio: de vez en cuando, un cuadro hace un gesto sutil (un brillo que
 * lo cruza, una respiración, una leve inclinación, un borde que se ilumina, un pequeño salto) y los
 * que se deslizan avanzan solos. Cada cuadro tiene su propio gesto y su propio reloj al azar, y
 * nunca se mueven dos a la vez. Se puede apagar en Ajustes para ahorrar batería; tampoco corre
 * mientras se edita Inicio ni con la app en segundo plano.
 */
val LocalHomeLife = compositionLocalOf { false }

private object HomeLife {
    private var lastAt = 0L

    /** Solo un gesto cada pocos segundos en toda la pantalla. */
    @Synchronized
    fun claim(gapMs: Long = 6_000L): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAt < gapMs) return false
        lastAt = now
        return true
    }
}

private enum class LifeFx(val durationMs: Int) { SHIMMER(1600), BREATH(1800), TILT(2000), GLOW(1700), FLOAT(1600) }

/** Gesto de cada tipo de cuadro (los fijados varían según lo que tengan). */
private fun fxFor(kind: TileKind, key: String): LifeFx = when (kind) {
    TileKind.CLOCK, TileKind.STREAK, TileKind.LASTFM -> LifeFx.GLOW
    TileKind.SHUFFLE, TileKind.DISCOVER, TileKind.ALBUM_MONTH -> LifeFx.SHIMMER
    TileKind.TOP5, TileKind.STARS, TileKind.SHELF -> LifeFx.BREATH
    TileKind.ALBUMS, TileKind.ARTISTS, TileKind.GENRES, TileKind.COLORS -> LifeFx.TILT
    TileKind.CONTINUE, TileKind.BEST_PARTS, TileKind.RECENT -> LifeFx.FLOAT
    else -> LifeFx.entries[(key.hashCode() and 0x7fffffff) % LifeFx.entries.size]
}

/** Aplica el gesto ocasional del cuadro [key]. Sin "vida" activa no hace nada. */
@Composable
fun Modifier.tileLife(key: String, kind: TileKind): Modifier {
    val on = LocalHomeLife.current
    if (!on) return this
    val fx = remember(key) { fxFor(kind, key) }
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        val rnd = Random(key.hashCode() * 31 + System.nanoTime().toInt())
        delay(6_000L + rnd.nextLong(40_000L))
        while (true) {
            if (HomeLife.claim()) {
                p.snapTo(0f)
                p.animateTo(1f, tween(fx.durationMs, easing = LinearEasing))
                p.snapTo(0f)
            }
            delay(35_000L + rnd.nextLong(70_000L))
        }
    }
    return this
        .graphicsLayer {
            val t = p.value
            if (t <= 0f) return@graphicsLayer
            val bell = sin(t * PI).toFloat() // 0 → 1 → 0, suave en ambos extremos
            when (fx) {
                LifeFx.BREATH -> { val s = 1f + 0.018f * bell; scaleX = s; scaleY = s }
                LifeFx.TILT -> { rotationY = 4f * sin(t * 2f * PI).toFloat() * (1f - t * 0.3f); cameraDistance = 14f * density }
                LifeFx.FLOAT -> translationY = -3.dp.toPx() * bell
                else -> Unit
            }
        }
        .drawWithContent {
            drawContent()
            val t = p.value
            if (t <= 0f) return@drawWithContent
            val r = 18.dp.toPx()
            when (fx) {
                LifeFx.SHIMMER -> {
                    // Un brillo diagonal que cruza el cuadro de lado a lado.
                    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                    clipPath(clip) {
                        val band = size.width * 0.45f
                        val x = -band + (size.width + band * 2f) * t
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent),
                                start = Offset(x - band / 2f, 0f), end = Offset(x + band / 2f, size.height)
                            )
                        )
                    }
                }
                LifeFx.GLOW -> {
                    val a = 0.28f * sin(t * PI).toFloat()
                    drawRoundRect(
                        Color.White.copy(alpha = a), cornerRadius = CornerRadius(r),
                        style = Stroke(1.5.dp.toPx())
                    )
                }
                else -> Unit
            }
        }
}

/** Una tira deslizable que, de vez en cuando, avanza sola un elemento (y vuelve al principio al final). */
@Composable
fun rememberDriftingRow(): LazyListState {
    val state = rememberLazyListState()
    val on = LocalHomeLife.current
    LaunchedEffect(state, on) {
        if (!on) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        delay(15_000L + rnd.nextLong(30_000L))
        while (true) {
            val info = state.layoutInfo
            if (!state.isScrollInProgress && info.totalItemsCount > 1 && HomeLife.claim()) {
                if (state.canScrollForward) {
                    val step = (info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing
                    if (step > 0) state.animateScrollBy(step.toFloat(), tween(1100, easing = FastOutSlowInEasing))
                } else {
                    state.animateScrollToItem(0)
                }
            }
            delay(45_000L + rnd.nextLong(60_000L))
        }
    }
    return state
}

/** Lo mismo para las páginas de un cuadro (pasa a la siguiente y vuelve a la primera). */
@Composable
fun DriftPager(pager: PagerState) {
    val on = LocalHomeLife.current
    LaunchedEffect(pager, on) {
        if (!on) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        delay(20_000L + rnd.nextLong(30_000L))
        while (true) {
            if (!pager.isScrollInProgress && pager.pageCount > 1 && HomeLife.claim()) {
                val next = (pager.currentPage + 1) % pager.pageCount
                pager.animateScrollToPage(next, animationSpec = tween(1100, easing = FastOutSlowInEasing))
            }
            delay(45_000L + rnd.nextLong(60_000L))
        }
    }
}
