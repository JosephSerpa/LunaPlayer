package com.melodia.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.melodia.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Colores sacados de la carátula
// ---------------------------------------------------------------------------

data class ArtColors(
    val vibrant: Color = Color(0xFFA78BFA),
    val muted: Color = Color(0xFF5B3FA0),
    val dark: Color = Color(0xFF1A1233),
    /** Color principal de la carátula (para el fondo del reproductor). */
    val dominant: Color = Color(0xFF2A2250)
)

val LocalArt = compositionLocalOf { ArtColors() }

/** Colores de una carátula ya cargada (se usa una versión pequeña: es suficiente y muy rápido). */
private fun colorsFrom(bitmap: Bitmap): ArtColors? {
    val p = Palette.from(bitmap).maximumColorCount(16).generate()
    val fallback = ArtColors()
    val vib = p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(0)))
    val mut = p.getMutedColor(p.getDominantColor(0))
    val drk = p.getDarkMutedColor(p.getDarkVibrantColor(0))
    val dom = p.getDominantColor(0)
    if (vib == 0 && mut == 0 && drk == 0 && dom == 0) return null
    return ArtColors(
        vibrant = if (vib == 0) fallback.vibrant else Color(brighten(vib)),
        muted = if (mut == 0) fallback.muted else Color(mut),
        dark = if (drk == 0) fallback.dark else Color(drk),
        dominant = if (dom == 0) fallback.dominant else Color(dom)
    )
}

/**
 * Fondos difuminados ya calculados. Se desenfocan UNA vez (en un hilo de fondo, sobre una imagen
 * de 40 px) y luego solo se dibujan: así no hay desenfoques en tiempo real en la GPU a cada cuadro.
 */
object BackdropCache {
    private val cache = android.util.LruCache<String, ImageBitmap>(16)
    fun get(uri: Uri): ImageBitmap? = cache.get(uri.toString())
    fun put(uri: Uri, image: ImageBitmap) { cache.put(uri.toString(), image) }
}

private const val BACKDROP_SIZE = 40

private fun makeBackdrop(src: Bitmap): ImageBitmap {
    val n = BACKDROP_SIZE
    val small = Bitmap.createScaledBitmap(src, n, n, true).copy(Bitmap.Config.ARGB_8888, true)
    val px = IntArray(n * n)
    small.getPixels(px, 0, n, 0, 0, n, n)
    val tmp = IntArray(n * n)
    repeat(3) {
        blurPass(px, tmp, n, horizontal = true)
        blurPass(tmp, px, n, horizontal = false)
    }
    small.setPixels(px, 0, n, 0, 0, n, n)
    return small.asImageBitmap()
}

/** Una pasada de desenfoque de caja (radio 4) en una dirección. */
private fun blurPass(src: IntArray, dst: IntArray, n: Int, horizontal: Boolean) {
    val r = 4
    val count = 2 * r + 1
    for (line in 0 until n) {
        for (i in 0 until n) {
            var red = 0
            var green = 0
            var blue = 0
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, n - 1)
                val c = if (horizontal) src[line * n + j] else src[j * n + line]
                red += (c shr 16) and 255
                green += (c shr 8) and 255
                blue += c and 255
            }
            val out = (0xFF shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            if (horizontal) dst[line * n + i] = out else dst[i * n + line] = out
        }
    }
}

/**
 * Prepara todo lo de una carátula ANTES de mostrarla: la descarga/decodifica a los tamaños que se
 * usan, calcula sus colores y su fondo difuminado. Devuelve los colores (o null si no se pudieron).
 */
suspend fun prepareArt(context: Context, uri: Uri, sizes: List<Int>): ArtColors? {
    // Primero el tamaño grande (si es de red, se descarga aquí); los demás salen ya de la caché.
    for (px in sizes) {
        runCatching {
            context.imageLoader.execute(ImageRequest.Builder(context).data(uri).size(px).build())
        }
    }
    val bitmap = runCatching {
        val request = ImageRequest.Builder(context).data(uri).allowHardware(false).size(96).build()
        (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }.getOrNull() ?: return null
    return withContext(Dispatchers.Default) {
        runCatching { BackdropCache.put(uri, makeBackdrop(bitmap)) }
        runCatching { colorsFrom(bitmap) }.getOrNull()
    }
}

/** Dibuja el fondo difuminado ya calculado de una carátula (no hace nada si no está en la caché). */
@Composable
fun BackdropImage(uri: Uri?, modifier: Modifier = Modifier) {
    val image = remember(uri) { uri?.let { BackdropCache.get(it) } }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier
        )
    }
}

/** Color de acento sacado de la carátula, ajustado para leerse en tema claro u oscuro. */
val accentColor: Color
    @Composable get() {
        val v = LocalArt.current.vibrant
        return if (LocalPalette.current.dark) v
        else Color(ColorUtils.blendARGB(v.toArgb(), android.graphics.Color.BLACK, 0.45f))
    }

/**
 * Versión de este color apta como fondo: en tema oscuro se oscurece y en claro se aclara
 * lo justo para que el texto siga leyéndose, sin perder el tono de la carátula.
 */
fun Color.forBackground(dark: Boolean): Color {
    var argb = toArgb()
    repeat(12) {
        val lum = ColorUtils.calculateLuminance(argb)
        if (dark && lum <= 0.10) return Color(argb)
        if (!dark && lum >= 0.72) return Color(argb)
        argb = ColorUtils.blendARGB(
            argb,
            if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            0.18f
        )
    }
    return Color(argb)
}

/** Texto negro o blanco, el que mejor se lea sobre este color. */
fun Color.onAccent(): Color =
    if (ColorUtils.calculateLuminance(toArgb()) > 0.45) Color(0xFF0A0A0F) else Color.White

/** Asegura que el color de acento sea lo bastante claro para leerse sobre fondo oscuro. */
private fun brighten(argb: Int): Int =
    if (ColorUtils.calculateLuminance(argb) < 0.35)
        ColorUtils.blendARGB(argb, android.graphics.Color.WHITE, 0.4f)
    else argb

// ---------------------------------------------------------------------------
// Utilidades de estilo
// ---------------------------------------------------------------------------

/**
 * Efecto "vidrio": fondo translúcido y borde fino (se adapta al tema claro/oscuro).
 * Es una función @Composable normal (no "composed"): es bastante más barata en las listas largas.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Float = 0.09f,
    border: Float = 0.12f
): Modifier {
    val tint = LocalPalette.current.ink
    return this
        .background(tint.copy(alpha = fill), shape)
        .border(1.dp, tint.copy(alpha = border), shape)
}

/** Se encoge un poco al pulsar, con rebote. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier = pressableWithLongClick(null, onClick)

/** Como [pressable], y además [onLongClick] al mantener presionado. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressableWithLongClick(onLongClick: (() -> Unit)?, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick
        )
}

/** Ángulo que avanza solo mientras suena y se queda quieto al pausar. */
@Composable
fun rememberSpinAngle(playing: Boolean, periodMs: Int = 14000): State<Float> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                val start = angle.value % 360f
                angle.snapTo(start)
                val remaining = ((360f - start) / 360f * periodMs).toInt().coerceAtLeast(1)
                angle.animateTo(360f, tween(remaining, easing = LinearEasing))
            }
        }
    }
    return angle.asState()
}

/** Barritas de ecualizador animadas. */
@Composable
fun EqualizerBars(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier.size(18.dp),
    bars: Int = 4
) {
    val t = rememberInfiniteTransition(label = "eq")
    val durations = listOf(520, 700, 610, 480, 760, 560)
    val values = List(bars) { i ->
        t.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durations[i % durations.size], easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }
    val level by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "eqLevel")
    Canvas(modifier) {
        val barW = size.width / (bars * 2f - 1f)
        for (i in 0 until bars) {
            val f = 0.18f + (values[i].value - 0.25f) / 0.75f * 0.82f * level
            val h = size.height * f
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2f * barW, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

/**
 * Carátula "lista para mostrarse". La decide un único punto (MainActivity): solo cambia
 * cuando la nueva imagen ya está descargada en todos los tamaños y sus colores calculados,
 * y entonces cambian juntos fondo, portada, barra inferior y colores (sin desfases).
 */
val LocalShownArt = compositionLocalOf<Uri?> { null }

/** Devuelve la carátula ya lista (ver [LocalShownArt]); los parámetros se conservan por compatibilidad. */
@Composable
fun rememberLoadedUri(@Suppress("UNUSED_PARAMETER") uri: Uri?, @Suppress("UNUSED_PARAMETER") sizePx: Int): Uri? =
    LocalShownArt.current

private class Held<T>(val value: T)

/**
 * Cambio de imagen SIN parpadeo: la anterior se queda opaca debajo y la nueva aparece
 * encima con un fundido. (Un Crossfade normal baja la opacidad de las dos a la vez y,
 * a mitad del cambio, deja ver el fondo: ese era el parpadeo.)
 */
@Composable
fun <T> StableFade(
    target: T,
    durationMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    var current by remember { mutableStateOf(Held(target)) }
    var previous by remember { mutableStateOf<Held<T>?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target != current.value) {
            previous = current
            current = Held(target)
            fade.snapTo(0f)
            fade.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
            previous = null
        }
    }
    Box(modifier) {
        previous?.let { old -> Box(Modifier.fillMaxSize()) { content(old.value) } }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) { content(current.value) }
    }
}

// ---------------------------------------------------------------------------
// Fondo ambiental: la carátula, muy difuminada, detrás de toda la app
// ---------------------------------------------------------------------------

@Composable
fun AmbientBackground(
    artUri: Uri?,
    colors: ArtColors,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    Box(modifier.fillMaxSize().background(palette.bg)) {
        StableFade(
            target = rememberLoadedUri(artUri, 48),
            durationMs = 1000,
            modifier = Modifier.fillMaxSize()
        ) { uri ->
            BackdropImage(uri, Modifier.fillMaxSize())
        }
        if (artUri != null) {
            // Velo ligero: deja ver el álbum pero mantiene el texto legible.
            Box(
                Modifier.fillMaxSize().background(
                    palette.bg.copy(alpha = if (palette.dark) 0.45f else 0.5f)
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            palette.bg.copy(alpha = 0.3f),
                            Color.Transparent,
                            palette.bg.copy(alpha = 0.5f)
                        )
                    )
                )
            )
        }
    }
}
