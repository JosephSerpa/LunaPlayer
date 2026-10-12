package com.melodia.app.ui.player

import com.melodia.app.Str
import com.melodia.app.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.melodia.app.audio.EffectsEngine
import com.melodia.app.audio.FxSettings
import com.melodia.app.audio.Spectrum
import com.melodia.app.audio.SpectrumExtractor
import com.melodia.app.data.SongMark
import com.melodia.app.data.formatTime
import com.melodia.app.player.PlayerState
import com.melodia.app.ui.components.MoonIcon
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.CoverOrigin
import com.melodia.app.ui.components.LocalArt
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.forBackground
import com.melodia.app.ui.components.StableFade
import com.melodia.app.ui.components.rememberLoadedUri
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink
import com.melodia.app.ui.theme.inverse
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Interpola entre dos rectángulos (posición y tamaño). */
internal fun lerpRect(a: Rect, b: Rect, t: Float): Rect {
    return Rect(
        a.left + (b.left - a.left) * t,
        a.top + (b.top - a.top) * t,
        a.right + (b.right - a.right) * t,
        a.bottom + (b.bottom - a.bottom) * t
    )
}

/** Guarda coordenadas de layout sin provocar recomposiciones. */
internal class CoordsHolder {
    var box: LayoutCoordinates? = null
    var play: Rect? = null
    var next: Rect? = null
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color {
    return androidx.compose.ui.graphics.lerp(a, b, t).copy(alpha = 1f)
}

// ---------------------------------------------------------------------------
// Carátula que se expande desde su miniatura y funde sus bordes con el fondo
// ---------------------------------------------------------------------------

@Composable
internal fun FlyingCover(
    artUri: android.net.Uri?,
    rect: Rect,
    progress: Float,
    startRadiusPx: Float,
    isPlaying: Boolean,
    /** Desplazamiento horizontal al deslizar para cambiar de canción. */
    slideX: Float = 0f,
    slideWidth: Float = 1f
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val w = with(density) { rect.width.coerceAtLeast(1f).toDp() }
    val h = with(density) { rect.height.coerceAtLeast(1f).toDp() }
    // Esquinas: de las de la miniatura a casi nada (el borde ya se difumina).
    val radius = with(density) { (startRadiusPx * (1f - progress)).toDp() }
    val feather = 0.32f * progress
    // La carátula mantiene siempre su tamaño: pausar o reproducir no la cambia.
    val scale = 1f

    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(w, h)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = slideX
                // Al alejarse hacia un lado se desvanece y gira apenas.
                val f = (kotlin.math.abs(slideX) / slideWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                alpha = 1f - 0.7f * f
                rotationZ = slideX / slideWidth.coerceAtLeast(1f) * 6f
            }
    ) {
        // Las máscaras de los bordes se aplican una sola vez sobre las dos imágenes
        // (la que sale y la que entra), así el cambio de portada no parpadea.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (feather > 0.001f) {
                        // Borde en degradado suave (curva "smoothstep"): no se nota dónde empieza.
                        val stops = featherStops(feather)
                        drawRect(Brush.horizontalGradient(*stops), blendMode = BlendMode.DstIn)
                        drawRect(Brush.verticalGradient(*stops), blendMode = BlendMode.DstIn)
                    }
                }
                .clip(RoundedCornerShape(radius))
        ) {
            StableFade(
                target = rememberLoadedUri(artUri, 1080),
                durationMs = 450,
                modifier = Modifier.fillMaxSize()
            ) { uri ->
                Artwork(uri, w, cornerRadius = radius, modifier = Modifier.fillMaxSize(), requestPx = 1080)
            }
        }
    }
}

/** Paradas de un degradado de borde: transparente en la orilla y opaco con curva suave hacia dentro. */
private fun featherStops(feather: Float): Array<Pair<Float, Color>> {
    val steps = 10
    val list = ArrayList<Pair<Float, Color>>()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val a = t * t * (3f - 2f * t)
        list.add(feather * t to Color.Black.copy(alpha = a))
    }
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        val a = t * t * (3f - 2f * t)
        list.add((1f - feather * t) to Color.Black.copy(alpha = a))
    }
    return list.toTypedArray()
}

// ---------------------------------------------------------------------------
// Corazón, estrellas y botón de efectos
// ---------------------------------------------------------------------------

@Composable
internal fun MarkRow(
    mark: SongMark,
    fxActive: Boolean,
    moonOn: Boolean,
    onToggleMoon: () -> Unit,
    onToggleLike: () -> Unit,
    onRate: (Int) -> Unit,
    onOpenFx: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val accent = accentColor
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Corazón con "latido" al marcarlo.
        val pop = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .size(44.dp)
                .pressable {
                    onToggleLike()
                    scope.launch {
                        pop.snapTo(0.7f)
                        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                }
                .glass(CircleShape, fill = if (mark.liked) 0.16f else 0.08f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (mark.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (mark.liked) Str.get(R.string.unlike) else Str.get(R.string.like),
                tint = if (mark.liked) accent else ink,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        }

        Spacer(Modifier.width(8.dp))
        // Añadir a una lista.
        Box(
            Modifier
                .size(44.dp)
                .pressable(onAddToPlaylist)
                .glass(CircleShape, fill = 0.08f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistAdd, Str.get(R.string.add_to_playlist),
                tint = ink, modifier = Modifier.size(22.dp)
            )
        }

        // Estrellas (tocar la misma cantidad otra vez las quita).
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..5) {
                val on = i <= mark.rating
                val s by animateFloatAsState(
                    if (on) 1.1f else 1f,
                    spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                    label = "star$i"
                )
                Icon(
                    if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    Str.plural(R.plurals.stars_count, i),
                    tint = if (on) accent else ink.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(28.dp)
                        .pressable { onRate(i) }
                        .padding(3.dp)
                        .graphicsLayer { scaleX = s; scaleY = s }
                )
            }
        }

        // Modo luna: al cambiar de canción empieza en su "mejor parte".
        Box(
            Modifier
                .size(44.dp)
                .pressable(onToggleMoon)
                .then(
                    if (moonOn) Modifier.background(accent, CircleShape)
                    else Modifier.glass(CircleShape, fill = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                MoonIcon, if (moonOn) Str.get(R.string.moon_off) else Str.get(R.string.moon_on),
                tint = if (moonOn) accent.onAccent() else ink,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        // Botón del panel Slow + Reverb.
        Box(
            Modifier
                .size(44.dp)
                .pressable(onOpenFx)
                .then(
                    if (fxActive) Modifier.background(accent, CircleShape)
                    else Modifier.glass(CircleShape, fill = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Tune, "Slow + Reverb",
                tint = if (fxActive) accent.onAccent() else ink,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Botones que viajan entre el reproductor y la barra inferior
// ---------------------------------------------------------------------------

@Composable
internal fun MorphPlayButton(rect: Rect, progress: Float, isPlaying: Boolean) {
    val density = LocalDensity.current
    val accent = accentColor
    val inkC = ink
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .background(
                // En la barra es un círculo liso; en el reproductor, degradado con el acento.
                Brush.linearGradient(listOf(inkC, lerpColor(inkC, accent.copy(alpha = 1f), 0.85f * progress))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = inverse,
            modifier = Modifier.size(28.dp + (42.dp - 28.dp) * progress)
        )
    }
}

@Composable
internal fun MorphIcon(
    rect: Rect,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconSize: androidx.compose.ui.unit.Dp,
    alpha: Float
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = ink.copy(alpha = alpha), modifier = Modifier.size(iconSize))
    }
}
