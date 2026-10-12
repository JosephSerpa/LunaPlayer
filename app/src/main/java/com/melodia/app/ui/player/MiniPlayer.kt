package com.melodia.app.ui.player

import com.melodia.app.Str
import com.melodia.app.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.melodia.app.player.PlayerState
import kotlinx.coroutines.flow.StateFlow
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.LocalCoverOrigin
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.rememberLoadedUri
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink
import com.melodia.app.ui.theme.inverse

/** Píldora flotante de vidrio: carátula cuadrada, título y una fina línea de progreso. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    position: StateFlow<Long>,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = state.current ?: return
    val accent = accentColor
    val palette = LocalPalette.current
    val track = ink.copy(alpha = 0.14f)
    val shape = RoundedCornerShape(28.dp)
    val origin = LocalCoverOrigin.current
    val density = LocalDensity.current
    val artBounds = remember { arrayOfNulls<Rect>(1) }

    Column(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .onGloballyPositioned { origin.miniPill = it.boundsInRoot() }
            .shadow(20.dp, shape, ambientColor = accent, spotColor = accent)
            .pressable {
                // La carátula del reproductor "sale" desde la del mini-reproductor.
                origin.rect = artBounds[0]
                origin.openedFromMini = true
                origin.radius = with(density) { 15.dp.toPx() }
                onExpand()
            }
            .background(palette.bg.copy(alpha = 0.78f), shape)
            .glass(shape, fill = 0.06f, border = 0.16f)
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val artPx = with(density) { 50.dp.roundToPx() }
            Artwork(
                rememberLoadedUri(current.artUri, artPx), 50.dp, cornerRadius = 15.dp,
                modifier = Modifier.onGloballyPositioned {
                    artBounds[0] = it.boundsInRoot()
                    origin.miniRect = artBounds[0]
                }
            )
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = current.title to current.artist,
                transitionSpec = {
                    (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320)))
                        .togetherWith(slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "miniTrack"
            ) { (title, artist) ->
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                Modifier
                    .size(46.dp)
                    .onGloballyPositioned { origin.miniPlay = it.boundsInRoot() }
                    .pressable(onPlayPause)
                    .background(ink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(tween(180), 0.5f))
                            .togetherWith(fadeOut(tween(120)) + scaleOut(tween(120), 0.5f))
                    },
                    label = "miniPlayPause"
                ) { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) Str.get(R.string.pause) else Str.get(R.string.play),
                        tint = inverse,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .onGloballyPositioned { origin.miniNext = it.boundsInRoot() }
                    .pressable { if (state.hasNext) onNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SkipNext, Str.get(R.string.next),
                    tint = ink.copy(alpha = if (state.hasNext) 1f else 0.35f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Línea de progreso muy fina (se redibuja sola, sin tocar el resto de la barra).
        MiniProgress(position, current.durationMs, accent, track)
    }
}

@Composable
private fun MiniProgress(
    position: StateFlow<Long>,
    durationMs: Long,
    accent: androidx.compose.ui.graphics.Color,
    track: androidx.compose.ui.graphics.Color
) {
    val pos by position.collectAsState()
    val raw = if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(raw, tween(260), label = "miniProgress")
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(3.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, Offset.Zero, size, r)
        if (progress > 0f) {
            drawRoundRect(accent, Offset.Zero, Size(size.width * progress, size.height), r)
        }
    }
}
