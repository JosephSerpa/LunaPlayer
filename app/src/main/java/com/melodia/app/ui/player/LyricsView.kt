package com.melodia.app.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.data.Lyrics
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Icono de letras: una ola arriba y dos renglones debajo, como texto. */
val LyricsIcon: ImageVector = ImageVector.Builder(
    name = "Luna.Lyrics", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    val stroke = SolidColor(Color.Black)
    path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        // Ola
        moveTo(3f, 7.2f)
        quadToRelative(1.5f, -2.8f, 3f, 0f)
        reflectiveQuadToRelative(3f, 0f)
        reflectiveQuadToRelative(3f, 0f)
        reflectiveQuadToRelative(3f, 0f)
        reflectiveQuadToRelative(3f, 0f)
        reflectiveQuadToRelative(3f, 0f)
    }
    path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round) {
        moveTo(3f, 13f); lineTo(18f, 13f)
        moveTo(3f, 18.5f); lineTo(13f, 18.5f)
    }
}.build()

/** Estado de la letra de la canción actual. */
sealed interface LyricsUi {
    data object Off : LyricsUi
    data object Loading : LyricsUi
    /** Sin letra. [canPickFolder]: ofrecer autorizar la carpeta de música (Android 11+). */
    data class None(val canPickFolder: Boolean) : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
}

/**
 * Letras sobre la carátula (o a pantalla completa con [expand] = 1).
 * [rect]: dónde dibujarse, en píxeles dentro del reproductor.
 */
@Composable
fun LyricsPanel(
    ui: LyricsUi,
    position: StateFlow<Long>,
    isPlaying: Boolean,
    rect: Rect,
    alpha: Float,
    expand: Float,
    shiftMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    onToggleExpand: () -> Unit,
    onShift: (Long) -> Unit,
    onPickFolder: () -> Unit
) {
    val density = LocalDensity.current
    val w = with(density) { rect.width.toDp() }
    val h = with(density) { rect.height.toDp() }
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(w, h)
            .graphicsLayer { this.alpha = alpha }
            // Sombra suave sin bordes: un óvalo oscuro que se desvanece más allá de la carátula,
            // así la letra parece flotar sobre todo el reproductor.
            .drawBehind {
                val sx = size.width / size.height
                val r = size.height * (0.78f + 0.12f * expand)
                val core = 0.50f + 0.12f * expand
                scale(scaleX = sx, scaleY = 1f, pivot = center) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = core),
                            0.55f to Color.Black.copy(alpha = core * 0.82f),
                            0.8f to Color.Black.copy(alpha = core * 0.35f),
                            1f to Color.Transparent,
                            center = center, radius = r
                        ),
                        radius = r, center = center
                    )
                }
            }
    ) {
        AnimatedContent(
            targetState = ui,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
            label = "lyricsState",
            modifier = Modifier.fillMaxSize()
        ) { st ->
            when (st) {
                is LyricsUi.Ready -> LyricsList(st.lyrics, position, isPlaying, expand, shiftMs, accent, onSeek)
                LyricsUi.Loading -> Message(Str.get(R.string.lyrics_loading))
                is LyricsUi.None -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(LyricsIcon, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        Str.get(R.string.lyrics_none), color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                    )
                    if (st.canPickFolder) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Str.get(R.string.lyrics_folder_hint), color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            Str.get(R.string.lyrics_folder_btn),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .pressable(onPickFolder)
                                .background(accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
                LyricsUi.Off -> Box(Modifier.fillMaxSize())
            }
        }

        // Ver la letra completa / volver a la carátula.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(36.dp)
                .pressable(onToggleExpand)
                .glass(CircleShape, fill = 0.14f, border = 0.16f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expand > 0.5f) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                Str.get(if (expand > 0.5f) R.string.lyrics_collapse else R.string.lyrics_expand),
                tint = Color.White, modifier = Modifier.size(18.dp)
            )
        }

        // Ajuste fino (solo a pantalla completa y con letra sincronizada).
        val synced = (ui as? LyricsUi.Ready)?.lyrics?.synced == true
        if (synced && expand > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .graphicsLayer { this.alpha = expand }
                    .glass(RoundedCornerShape(50), fill = 0.12f, border = 0.14f)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShiftButton("−0,5 s") { onShift(shiftMs - 500) }
                Text(
                    if (shiftMs == 0L) Str.get(R.string.lyrics_in_sync)
                    else (if (shiftMs > 0) "+" else "") + "%.1f s".format(shiftMs / 1000f),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.pressable { onShift(0) }.padding(horizontal = 10.dp, vertical = 6.dp)
                )
                ShiftButton("+0,5 s") { onShift(shiftMs + 500) }
            }
        }
    }
}

@Composable
private fun ShiftButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.pressable(onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Lista de líneas: sigue a la canción, la línea actual crece mientras suena. */
@Composable
private fun LyricsList(
    lyrics: Lyrics,
    position: StateFlow<Long>,
    isPlaying: Boolean,
    expand: Float,
    shiftMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit
) {
    val lines = lyrics.lines
    val synced = lyrics.synced
    val seekCb by rememberUpdatedState(onSeek)

    // Reloj suave: la posición llega cada 250 ms; entre medias se avanza con cada cuadro.
    val anchor = remember { longArrayOf(position.value, SystemClock.uptimeMillis()) }
    var clock by remember { mutableLongStateOf(position.value) }
    LaunchedEffect(position) {
        position.collect { p ->
            anchor[0] = p; anchor[1] = SystemClock.uptimeMillis()
            // Saltos (o en pausa): la posición manda.
            if (!isPlaying || kotlin.math.abs(p - clock) > 900) clock = p
        }
    }
    LaunchedEffect(isPlaying, synced) {
        if (!isPlaying || !synced) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                val est = anchor[0] + (SystemClock.uptimeMillis() - anchor[1]).coerceAtMost(1_000)
                // Nunca hacia atrás por pequeñas diferencias (evita temblores).
                if (est > clock || clock - est > 900) clock = est
            }
        }
    }
    val time = { clock - shiftMs }

    val current by remember(lines, synced) {
        derivedStateOf {
            if (!synced) -1 else {
                val t = time()
                var lo = 0; var hi = lines.size - 1; var ans = -1
                while (lo <= hi) { val mid = (lo + hi) ushr 1; if (lines[mid].timeMs <= t) { ans = mid; lo = mid + 1 } else hi = mid - 1 }
                ans
            }
        }
    }

    val list = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var auto by remember { mutableStateOf(false) }
    var userAt by remember { mutableLongStateOf(0L) }
    // Si el usuario desliza, se deja de seguir 3 s.
    LaunchedEffect(list) {
        var touching = false
        snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !auto) { touching = true; userAt = SystemClock.uptimeMillis() }
            else if (!scrolling && touching) { touching = false; userAt = SystemClock.uptimeMillis() }
        }
    }
    val following = { SystemClock.uptimeMillis() - userAt > 3_000 }

    suspend fun center(index: Int, animate: Boolean) {
        if (index < 0) return
        auto = true
        try {
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                list.scrollToItem(index)
            }
            val i2 = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
            val vp = list.layoutInfo
            val target = vp.viewportStartOffset + (vp.viewportEndOffset - vp.viewportStartOffset) * 0.42f
            val delta = i2.offset + i2.size / 2f - target
            if (animate && item != null) list.animateScrollBy(delta, tween(650, easing = FastOutSlowInEasing))
            else list.scrollBy(delta)
        } finally { auto = false }
    }

    LaunchedEffect(current, expand >= 0.999f || expand <= 0.001f) {
        if (synced && following()) center(current.coerceAtLeast(0), animate = true)
    }
    LaunchedEffect(userAt) {
        if (userAt == 0L) return@LaunchedEffect
        delay(3_100)
        if (synced && following()) {
            center(current.coerceAtLeast(0), animate = true)
            userAt = 0L
        }
    }

    val baseSize = (19f + 7f * expand).sp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = maxHeight * 0.42f
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = if (synced) pad else 56.dp, bottom = if (synced) pad else 56.dp),
            modifier = Modifier
                .fillMaxSize()
                // Bordes que se desvanecen arriba y abajo.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent, 0.22f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val isCur = i == current
                val state = when { !synced -> 0.9f; isCur -> 1f; i < current -> 0.38f; else -> 0.58f }
                val a by animateFloatAsState(state, tween(380), label = "lyricAlpha")
                // La línea actual crece mientras suena; al terminar vuelve suavemente a su tamaño.
                val settle = remember { Animatable(1f) }
                val last = remember { floatArrayOf(1f) }
                LaunchedEffect(isCur) {
                    if (!isCur && last[0] > 1.001f) {
                        settle.snapTo(last[0]); last[0] = 1f
                        settle.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                    }
                }
                val start = line.timeMs
                val end = lines.getOrNull(i + 1)?.timeMs?.takeIf { it > start } ?: (start + 4_000)
                Text(
                    line.text.ifBlank { "♪" },
                    color = if (isCur) Color.White else Color.White,
                    fontSize = baseSize,
                    lineHeight = baseSize * 1.25f,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    // Sombra difusa: se lee sobre cualquier carátula sin necesitar un recuadro.
                    style = androidx.compose.ui.text.TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 18f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = (7f + 3f * expand).dp)
                        .graphicsLayer {
                            alpha = a
                            val s = if (isCur && synced) {
                                val f = ((time() - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                                // Crece rápido al entrar y sigue creciendo despacio hasta el final.
                                val g = 1f + 0.06f * (1f - (1f - f) * (1f - f)) + 0.08f * f
                                last[0] = g; g
                            } else settle.value
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin.Center
                        }
                        .then(
                            if (synced && start >= 0) Modifier.pressable {
                                seekCb((start + shiftMs).coerceAtLeast(0))
                                userAt = 0L
                                scope.launch { center(i, animate = true) }
                            } else Modifier
                        )
                )
            }
        }

        // Volver a la línea actual (cuando el usuario se movió por la letra).
        val away by remember { derivedStateOf { userAt != 0L } }
        if (synced && away) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .pressable {
                        userAt = 0L
                        scope.launch { center(current.coerceAtLeast(0), animate = true) }
                    }
                    .glass(CircleShape, fill = 0.14f, border = 0.16f),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MyLocation, Str.get(R.string.lyrics_follow), tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
