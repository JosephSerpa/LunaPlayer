package com.melodia.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlin.math.cos
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.melodia.app.ui.components.BurstKind
import com.melodia.app.ui.components.burst
import com.melodia.app.ui.components.rememberBurst
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
import androidx.compose.foundation.layout.widthIn
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
import com.melodia.app.ui.components.BlackHoleIcon
import com.melodia.app.ui.components.pressableWithLongClick
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
    var title: Rect? = null
    var artist: Rect? = null
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
    slideWidth: Float = 1f,
    /** 0 = vertical · 1 = horizontal. En los dos casos el borde se funde con el fondo. */
    land: () -> Float = { 0f }
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
                        // En horizontal también se funde con el fondo, igual que en vertical.
                        val fl = feather
                        val fx = featherStops(fl, fl)
                        val fy = featherStops(fl, fl)
                        drawRect(Brush.horizontalGradient(*fx), blendMode = BlendMode.DstIn)
                        drawRect(Brush.verticalGradient(*fy), blendMode = BlendMode.DstIn)
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
private fun featherStops(start: Float, end: Float): Array<Pair<Float, Color>> {
    val steps = 10
    val list = ArrayList<Pair<Float, Color>>()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val a = if (start < 0.001f) 1f else t * t * (3f - 2f * t)
        list.add(start * t to Color.Black.copy(alpha = a))
    }
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        val a = if (end < 0.001f) 1f else t * t * (3f - 2f * t)
        list.add((1f - end * t) to Color.Black.copy(alpha = a))
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
    onToggleFx: () -> Unit,
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
        val heartBurst = rememberBurst()
        val broken = ink.copy(alpha = 0.55f)
        Box(
            Modifier
                .size(44.dp)
                // Partículas al marcar; corazón roto al quitarlo (antes del recorte del botón, para que se salgan).
                .burst(heartBurst, accent, broken)
                .pressable {
                    val wasLiked = mark.liked
                    onToggleLike()
                    scope.launch {
                        pop.snapTo(0.7f)
                        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                    scope.launch { heartBurst.play(if (wasLiked) BurstKind.BROKEN else BurstKind.HEART) }
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
        val starBursts = List(5) { rememberBurst() }
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
                        // Las 5 estrellas siempre del mismo tamaño, aunque la pantalla sea angosta.
                        .weight(1f, fill = false)
                        .widthIn(max = 30.dp)
                        .aspectRatio(1f)
                        .burst(starBursts[i - 1], accent)
                        .pressable {
                            val clearing = mark.rating == i
                            onRate(i)
                            if (!clearing) scope.launch { starBursts[i - 1].play(BurstKind.STAR) }
                        }
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

        // Agujero negro: tocar enciende/apaga Slow + Reverb; mantener presionado y soltar abre su menú.
        FxHoleButton(fxActive = fxActive, accent = accent, onToggle = onToggleFx, onOpenMenu = onOpenFx)
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


/**
 * Botón del agujero negro. Un toque enciende o apaga el efecto. Al mantenerlo presionado aparece,
 * sobre el dedo, un agujero negro que crece y tiembla con un anillo que se va llenando: avisa que,
 * si se suelta con el anillo completo, se abre el menú. El menú se abre al SOLTAR, no al presionar;
 * si se desliza el dedo fuera del botón, no pasa nada.
 */
@Composable
private fun FxHoleButton(fxActive: Boolean, accent: Color, onToggle: () -> Unit, onOpenMenu: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val charge = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val toggleCb by rememberUpdatedState(onToggle)
    val openCb by rememberUpdatedState(onOpenMenu)
    val pressScale by animateFloatAsState(
        if (holding) 0.92f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "holePress"
    )
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    holding = true
                    armed = false
                    // Un toque rápido no muestra nada: el aviso empieza tras un instante.
                    val charging = scope.launch {
                        kotlinx.coroutines.delay(130)
                        charge.snapTo(0f)
                        charge.animateTo(1f, tween(480, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                        armed = true
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                    val up = waitForUpOrCancellation()
                    charging.cancel()
                    val open = armed
                    holding = false
                    armed = false
                    scope.launch { charge.animateTo(0f, tween(180)) }
                    if (up != null) {
                        up.consume()
                        if (open) openCb() else toggleCb()
                    }
                }
            }
            .semantics {
                onClick { toggleCb(); true }
                onLongClick { openCb(); true }
            }
            .then(
                if (fxActive) Modifier.background(accent, CircleShape)
                else Modifier.glass(CircleShape, fill = 0.08f)
            ),
        contentAlignment = Alignment.Center
    ) {
        BlackHoleIcon(
            active = fxActive,
            tint = if (fxActive) accent.onAccent() else ink,
            size = 28.dp
        )
        if (charge.value > 0.001f) {
            val lift = with(density) { (HOLE_POP + 10.dp).roundToPx() }
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -lift),
                properties = androidx.compose.ui.window.PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                HoleCharge(progress = charge.value, armed = armed, accent = accent)
            }
        }
    }
}

private val HOLE_POP = 96.dp

/** El agujero que crece y tiembla sobre el dedo; el anillo se llena hasta que se puede soltar. */
@Composable
private fun HoleCharge(progress: Float, armed: Boolean, accent: Color) {
    val t = rememberInfiniteTransition(label = "holeShake")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "holeTime")
    val pulse by animateFloatAsState(if (armed) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "holeArmed")
    // Crece con un pequeño rebote al final.
    val grow = 1f - (1f - progress) * (1f - progress)
    val scale = 0.35f + 0.65f * grow + 0.08f * pulse
    Box(
        Modifier
            .size(HOLE_POP)
            .graphicsLayer {
                val amp = (0.6f + 2.4f * progress + 0.8f * pulse) * density
                val k = time * 2f * PI.toFloat()
                // Temblor: varias frecuencias mezcladas para que no parezca un vaivén regular.
                translationX = (sin(k * 23f) * 0.7f + sin(k * 37f) * 0.3f) * amp
                translationY = (cos(k * 29f) * 0.7f + sin(k * 41f) * 0.3f) * amp
                scaleX = scale
                scaleY = scale
                alpha = (progress * 2.5f).coerceAtMost(1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            // Halo del color del tema y fondo oscuro, para que se vea sobre cualquier carátula.
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.45f * progress), accent.copy(alpha = 0.12f * progress), Color.Transparent),
                    center = c, radius = r
                ),
                radius = r, center = c
            )
            drawCircle(Color.Black.copy(alpha = 0.55f), radius = r * 0.62f, center = c)
            // Anillo de carga.
            val sw = 3.dp.toPx()
            val rr = r * 0.78f
            drawCircle(Color.White.copy(alpha = 0.16f), radius = rr, center = c, style = Stroke(sw))
            drawArc(
                accent, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        // El agujero gira más rápido a medida que se carga.
        BlackHoleIcon(
            active = true,
            tint = accent,
            size = 52.dp,
            modifier = Modifier.graphicsLayer { rotationZ = 220f * progress + 360f * time * progress }
        )
    }
}
