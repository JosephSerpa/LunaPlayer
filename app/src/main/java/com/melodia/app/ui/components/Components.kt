package com.melodia.app.ui.components

import com.melodia.app.Str
import com.melodia.app.R

import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.melodia.app.ui.theme.ink
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.melodia.app.data.Song
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.data.SongMark
import com.melodia.app.data.formatTime

/** Dónde estaba en pantalla la carátula que se tocó (para animar su expansión al reproductor). */
class CoverOrigin {
    /** Carátula que se tocó para abrir el reproductor. */
    var rect: Rect? = null
    /** Carátula del mini-reproductor (adonde vuelve al cerrar). */
    var miniRect: Rect? = null
    /** La barra completa del mini-reproductor y sus botones. */
    var miniPill: Rect? = null
    var miniPlay: Rect? = null
    var miniNext: Rect? = null
    /** Carátula del reproductor abierto y carátula de la barra de la cola (para unirlas al abrir la cola). */
    var playerCover: Rect? = null
    var queueArt: Rect? = null
    /** Barra de controles del reproductor y su play / siguiente; y la barra de la cola con los suyos. */
    var playerControls: Rect? = null
    var playerPlay: Rect? = null
    var playerNext: Rect? = null
    var queuePill: Rect? = null
    /** Título y artista del mini-reproductor (viajan hasta los del reproductor). */
    var miniTitle: Rect? = null
    var miniArtist: Rect? = null
    var queuePlay: Rect? = null
    var queueNext: Rect? = null
    /** true si el reproductor se abrió desde la barra (no desde una canción). */
    var openedFromMini: Boolean = false
    var radius: Float = 14f
}

val LocalCoverOrigin = staticCompositionLocalOf { CoverOrigin() }

/** Abre el menú de una canción (añadir a lista…) al mantenerla presionada. */
val LocalSongMenu = staticCompositionLocalOf<((Song) -> Unit)?> { null }

/** Álbum, artista, carpeta o lista: algo que contiene varias canciones. */
data class CollectionTarget(
    val title: String,
    val subtitle: String,
    val artUri: Uri?,
    val songs: List<Song>,
    /** Qué fijar en Inicio desde su menú (álbum o artista); null si no se puede fijar. */
    val pin: com.melodia.app.data.HomePin? = null,
    /** Si es un álbum: su id, para mostrar "me gusta" y estrellas en el menú. */
    val albumId: Long? = null
)

/** Abre el menú de un álbum / artista / carpeta / lista al mantenerlo presionado. */
val LocalCollectionMenu = staticCompositionLocalOf<((CollectionTarget) -> Unit)?> { null }

/** "Me gusta" y estrellas de los álbumes, por id de álbum. */
val LocalAlbumMarks = compositionLocalOf<Map<Long, SongMark>> { emptyMap() }

/** Corazón y cinco estrellas para marcar un álbum. */
@Composable
fun AlbumMarkBar(mark: SongMark?, onLike: () -> Unit, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = accentColor
    val broken = ink.copy(alpha = 0.55f)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val heartBurst = rememberBurst()
    val starBursts = List(5) { rememberBurst() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).burst(heartBurst, accent, broken).pressable {
                val wasLiked = mark?.liked == true
                onLike()
                scope.launch { heartBurst.play(if (wasLiked) BurstKind.BROKEN else BurstKind.HEART) }
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (mark?.liked == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                Str.get(R.string.like),
                tint = if (mark?.liked == true) accent else ink.copy(alpha = 0.7f),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        for (n in 1..5) {
            Box(
                Modifier.size(38.dp).burst(starBursts[n - 1], accent).pressable {
                    // Tocar la misma cantidad la quita: entonces no hay destellos.
                    val clearing = (mark?.rating ?: 0) == n
                    onRate(n)
                    if (!clearing) scope.launch { starBursts[n - 1].play(BurstKind.STAR, n / 5f) }
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if ((mark?.rating ?: 0) >= n) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
                    tint = if ((mark?.rating ?: 0) >= n) accent else ink.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/** "Me gusta" y estrellas de todas las canciones, por id. */
val LocalMarks = compositionLocalOf<Map<Long, SongMark>> { emptyMap() }

/** Carátula con esquinas redondeadas y un placeholder elegante si no hay imagen. */
@Composable
fun Artwork(
    uri: Uri?,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    circle: Boolean = false,
    /** Tamaño fijo de la imagen pedida (para carátulas que cambian de tamaño al animarse). */
    requestPx: Int? = null
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(cornerRadius)
    // Fondo neutro (no depende del color de la canción: así las listas no se redibujan
    // cada vez que cambia la carátula que suena).
    val placeholderBg = LocalPalette.current.ink.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(placeholderBg),
        contentAlignment = Alignment.Center
    ) {
        // El icono queda debajo: se ve mientras carga o si no hay carátula.
        Placeholder(size)
        if (uri != null) {
            val context = LocalContext.current
            val px = requestPx ?: with(LocalDensity.current) { size.roundToPx() }.coerceIn(32, 1200)
            // Se pide la imagen al tamaño en que se dibuja: listas mucho más fluidas.
            val request = remember(uri, px) {
                ImageRequest.Builder(context).data(uri).size(px).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
private fun Placeholder(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = LocalPalette.current.ink.copy(alpha = 0.35f),
            modifier = Modifier.size(size / 2.4f)
        )
    }
}

/** Entrada escalonada: cada elemento aparece con rebote y un pequeño retardo. */
@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    // Solo los primeros elementos se animan; los demás (al hacer scroll) aparecen
    // directamente, sin coste extra.
    if (index >= 12) {
        content()
        return
    }
    // Ya se animó una vez (la lista lo recuerda aunque la fila salga de pantalla): al volver
    // deslizando rápido, aparece al instante y no se ven huecos vacíos.
    var shown by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (shown) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 40L)
        visible = true
        kotlinx.coroutines.delay(700)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "stagger"
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress.coerceIn(0f, 1f)
            translationY = (1f - progress) * 60f
            val s = 0.92f + 0.08f * progress
            scaleX = s; scaleY = s
        }
    ) { content() }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    showArt: Boolean = true,
    number: Int? = null
) {
    val accent = accentColor
    val shape = RoundedCornerShape(22.dp)
    val origin = LocalCoverOrigin.current
    val mark = LocalMarks.current[song.id]
    val menu = LocalSongMenu.current
    val density = LocalDensity.current
    // Posiciones en pantalla (se guardan sin provocar recomposiciones).
    val bounds = remember { arrayOfNulls<Rect>(2) } // 0 = carátula, 1 = fila
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .pressableWithLongClick(menu?.let { m -> { m(song) } }) {
                val side = with(density) { 54.dp.toPx() }
                val art = bounds[0] ?: bounds[1]?.let {
                    val left = it.left + with(density) { 12.dp.toPx() }
                    Rect(left, it.center.y - side / 2f, left + side, it.center.y + side / 2f)
                }
                origin.rect = art
                origin.openedFromMini = false
                origin.radius = with(density) { 16.dp.toPx() }
                onClick()
            }
            .glass(shape, fill = if (isCurrent) 0.16f else 0.07f, border = if (isCurrent) 0.28f else 0.08f)
            .onGloballyPositioned { bounds[1] = it.boundsInRoot() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            Artwork(
                song.artUri, 54.dp, cornerRadius = 16.dp,
                modifier = Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            )
            Spacer(Modifier.width(14.dp))
        } else if (number != null) {
            Text(
                "%02d".format(number),
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Nube = la canción viene del servidor (Subsonic / Navidrome) y se comparte entre dispositivos.
                if (song.isRemote) {
                    Icon(
                        Icons.Rounded.Cloud, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (mark != null) {
            if (mark.rating > 0) {
                Text(
                    "★${mark.rating}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Spacer(Modifier.width(6.dp))
            }
            if (mark.liked) {
                Icon(
                    Icons.Rounded.Favorite, Str.get(R.string.like),
                    tint = accent, modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        if (isCurrent) {
            EqualizerBars(active = isPlaying, color = accent, modifier = Modifier.size(20.dp))
        } else {
            Text(
                song.durationMs.formatTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ayuda: un (!) discreto; al mantenerlo presionado aparece la explicación
// ---------------------------------------------------------------------------

@Composable
fun HelpBadge(text: String, modifier: Modifier = Modifier, title: String? = null) {
    var show by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    // Círculo discreto con "!": al tocarlo se abre una ventana corta que explica para qué sirve.
    Box(
        modifier
            .size(32.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { show = true },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.5.dp, palette.ink.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "!",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = palette.ink.copy(alpha = 0.6f)
            )
        }
    }
    if (show) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { show = false }) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.bg.copy(alpha = 0.97f), shape)
                    .glass(shape, fill = 0.06f, border = 0.16f)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title ?: Str.get(R.string.help_title),
                    style = MaterialTheme.typography.titleLarge, color = palette.ink
                )
                Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
                Text(
                    Str.get(R.string.help_ok),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { show = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Coloca la burbuja encima del (!) — o debajo si no cabe — sin salirse de la pantalla. */
private class AbovePositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, maxX)
        var y = anchorBounds.top - popupContentSize.height - gap
        if (y < 0) y = anchorBounds.bottom + gap
        return IntOffset(x, y)
    }
}
