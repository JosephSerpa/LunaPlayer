package com.melodia.app.ui.player

import com.melodia.app.Str
import com.melodia.app.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MicOff
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
import com.melodia.app.ui.components.BackdropImage
import com.melodia.app.ui.components.StableFade
import com.melodia.app.ui.components.rememberLoadedUri
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.onAccent
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.components.pressableWithLongClick
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink
import com.melodia.app.ui.theme.inverse
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reproductor a pantalla completa.
 * [progress] va de 0 (cerrado) a 1 (abierto): con él la carátula "vuela" desde
 * su miniatura ([origin]) hasta el centro, y el resto aparece suavemente.
 */
@Composable
fun PlayerScreen(
    state: PlayerState,
    /** Posición actual (flujo propio: solo se redibuja la línea de tiempo). */
    position: StateFlow<Long>,
    progress: Float,
    closing: Boolean,
    /** true si la carátula y los botones deben ir/venir del mini-reproductor. */
    collapseToMini: Boolean,
    /** Arrastre hacia abajo: cambio de progreso (negativo = cerrando). */
    onDragProgress: (Float) -> Unit,
    /** Se soltó el dedo, con la velocidad en "progreso por segundo". */
    onDragRelease: (Float) -> Unit,
    origin: CoverOrigin,
    mark: SongMark,
    fx: FxSettings,
    effects: EffectsEngine,
    /** Modo luna: cada canción nueva empieza en su mejor parte. */
    moonMode: Boolean,
    onToggleMoon: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRate: (Int) -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    /** Canción anterior siempre (para el gesto de deslizar). */
    onPreviousTrack: () -> Unit,
    /** Marca / borra "la mejor parte" de la canción. */
    onSetBest: (Long) -> Unit,
    onClearBest: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenOptions: () -> Unit,
    /** Cierra el reproductor y abre la pantalla del artista / álbum de la canción. */
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit
) {
    val current = state.current ?: return
    val accent = accentColor
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    // Panel flotante abierto: 0 = ninguno, 1 = Slow + Reverb, 2 = Off vocal.
    var panel by rememberSaveable { mutableStateOf(0) }
    // Deslizar la portada a los lados: anterior / siguiente.
    val swipeX = remember { Animatable(0f) }

    // Espectro real de la canción (se analiza una vez y queda guardado en memoria).
    val spectrum by produceState(initialValue = null as Spectrum?, current.uri) {
        val uri = current.uri
        val cached = uri?.let { SpectrumExtractor.peek(it) }
        if (cached != null) {
            value = cached
        } else {
            value = null
            // Espera un momento: si se salta de canción rápido, no se analiza ninguna de paso.
            kotlinx.coroutines.delay(700)
            val result = uri?.let { SpectrumExtractor.get(context, it, current.durationMs) }
                ?: Spectrum.FAILED
            // Si ya se cambió de canción, este resultado no vale: no pisar el de la nueva.
            coroutineContext.ensureActive()
            value = result
        }
    }

    val close: () -> Unit = onClose
    BackHandler(enabled = !closing) { close() }
    BackHandler(enabled = panel != 0) { panel = 0 }

    // Fondo: el color principal del álbum (ajustado para que el texto se lea).
    // La transición de color se lee solo al dibujar (drawBehind): no recompone nada por cuadro.
    val bgTarget = LocalArt.current.dominant.forBackground(palette.dark)
    val bgAnim = animateColorAsState(bgTarget, tween(700), label = "playerBgColor")
    val darkTheme = palette.dark

    val contentAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val holder = remember { CoordsHolder() }
    var slot by remember { mutableStateOf<Rect?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val p = progress.coerceIn(0f, 1f)

    // La "hoja" del reproductor se encoge hasta convertirse en la barra inferior (y al revés).
    val pill = origin.miniPill
    val sheet: Rect? = if (collapseToMini && pill != null && rootSize.width > 0) {
        lerpRect(pill, Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat()), p)
    } else null
    val sheetRadius = with(density) { (28.dp.toPx() * (1f - p)).toDp() }
    val sheetModifier = if (sheet != null) {
        Modifier
            .offset { IntOffset(sheet.left.roundToInt(), sheet.top.roundToInt()) }
            .size(with(density) { sheet.width.toDp() }, with(density) { sheet.height.toDp() })
    } else Modifier.fillMaxSize()

    // Los botones viajan entre el reproductor y la barra mientras dure la transición.
    val morphButtons = collapseToMini && p < 0.999f &&
            origin.miniPlay != null && holder.play != null

    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        // Fondo completo: la portada, ampliada y muy difuminada, teñida con su color principal.
        Box(
            sheetModifier
                .graphicsLayer {
                    alpha = if (sheet != null) 1f else p
                    shape = RoundedCornerShape(sheetRadius)
                    clip = true
                }
                .drawBehind { drawRect(bgAnim.value) }
        ) {
            StableFade(
                target = rememberLoadedUri(current.artUri, 40),
                durationMs = 700,
                modifier = Modifier.fillMaxSize()
            ) { uri ->
                // Fondo difuminado ya calculado (no hay desenfoque en tiempo real).
                BackdropImage(uri, Modifier.fillMaxSize().graphicsLayer { alpha = 0.85f })
            }
            // Velo del color principal: unifica todo y mantiene el texto legible.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val c = bgAnim.value
                        val bottom = if (darkTheme) lerpColor(c, Color.Black, 0.55f)
                        else lerpColor(c, Color.White, 0.4f)
                        drawRect(
                            Brush.verticalGradient(
                                listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.45f), bottom.copy(alpha = 0.85f))
                            )
                        )
                    }
            )
            // Al llegar a la barra toma el mismo tono que el mini-reproductor.
            if (sheet != null && p < 0.5f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.bg.copy(alpha = 0.78f * (1f - p * 2f)))
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { holder.box = it }
                // Consume los toques para que no lleguen a la biblioteca que queda detrás.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                // Arrastrar hacia abajo encoge el reproductor siguiendo el dedo.
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy ->
                        onDragProgress(-dy / rootSize.height.coerceAtLeast(1) * 1.4f)
                    },
                    onDragStopped = { v -> onDragRelease(-v / rootSize.height.coerceAtLeast(1)) }
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Todo lo que no es la carátula aparece cuando ella ya casi llegó.
                val appear = Modifier.graphicsLayer {
                    alpha = contentAlpha
                    translationY = (1f - contentAlpha) * 40.dp.toPx()
                }

                // --- Barra superior ---
                Row(
                    appear.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .pressable(close)
                            .glass(CircleShape, fill = 0.12f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, Str.get(R.string.close), tint = ink, modifier = Modifier.size(30.dp))
                    }
                    // Botones de 44 dp a cada lado: el texto queda centrado en la pantalla.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            current.album.ifBlank { current.artist },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .pressable(if (current.album.isBlank()) onOpenArtist else onOpenAlbum)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .pressable(onOpenOptions)
                            .glass(CircleShape, fill = 0.12f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Equalizer, Str.get(R.string.equalizer), tint = ink)
                    }
                }

                // Deslizar a los lados cambia de canción: ← siguiente · → anterior.
                // El área que responde es toda la zona de la carátula y el título (no solo la carátula).
                val swipeDrag = Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dx ->
                    scope.launch { swipeX.snapTo(swipeX.value + dx) }
                },
                onDragStopped = { v ->
                    val width = rootSize.width.toFloat().coerceAtLeast(1f)
                    val x = swipeX.value
                    val goNext = (x < -width * 0.25f || v < -1400f) && state.hasNext
                    val goPrev = x > width * 0.25f || v > 1400f
                    if (goNext || goPrev) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val dir = if (goNext) -1f else 1f
                        swipeX.animateTo(dir * width, tween(170))
                        if (goNext) onNext() else onPreviousTrack()
                        // La nueva portada entra desde el lado contrario.
                        swipeX.snapTo(-dir * width * 0.6f)
                        swipeX.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 320f))
                    } else {
                        swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                    }
                }
            )

                // --- Hueco de la carátula: se adapta al alto disponible ---
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(swipeDrag)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .onGloballyPositioned { c ->
                                holder.box?.let { slot = it.localBoundingBoxOf(c) }
                            }
                    )
                }

                Column(appear.fillMaxWidth().then(swipeDrag), horizontalAlignment = Alignment.CenterHorizontally) {
                    // --- Título, artista y formato del audio ---
                    AnimatedContent(
                        targetState = current.title to current.artist,
                        transitionSpec = {
                            // Primero sale el texto viejo y luego entra el nuevo: nunca se ven superpuestos.
                            (fadeIn(tween(260, delayMillis = 140)) + slideInHorizontally(tween(300, delayMillis = 140)) { it / 8 })
                                .togetherWith(fadeOut(tween(140)))
                        },
                        label = "titleSwap"
                    ) { (title, artist) ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                title,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = accent,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .pressable(onOpenArtist)
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val audioLabel = current.audio.label
                    if (audioLabel.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            audioLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .glass(RoundedCornerShape(50), fill = 0.07f, border = 0.12f)
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // --- Me gusta, estrellas y efectos ---
                    MarkRow(
                        mark = mark,
                        fxActive = !fx.isNeutral,
                        moonOn = moonMode,
                        onToggleMoon = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleMoon()
                        },
                        onToggleLike = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleLike()
                        },
                        onRate = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRate(it)
                        },
                        onOpenFx = { panel = 1 },
                        onAddToPlaylist = onAddToPlaylist
                    )

                    Spacer(Modifier.height(10.dp))

                    Timeline(
                        spectrum = spectrum,
                        position = position,
                        durationMs = current.durationMs,
                        accent = accent,
                        onSeek = onSeek,
                        bestMs = mark.bestMs,
                        onSetBest = onSetBest,
                        onClearBest = onClearBest
                    )

                    Spacer(Modifier.height(14.dp))

                    // --- Controles en un panel de vidrio ---
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(44.dp), fill = 0.08f, border = 0.14f)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val inactive = ink.copy(alpha = 0.5f)
                        Box(
                            Modifier.size(46.dp).pressable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShuffle()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle, Str.get(R.string.shuffle),
                                tint = if (state.shuffle) accent else inactive
                            )
                        }
                        Box(Modifier.size(54.dp).pressable(onPrevious), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SkipPrevious, Str.get(R.string.previous), tint = ink, modifier = Modifier.size(36.dp))
                        }
                        PlayPauseButton(
                            modifier = Modifier
                                .onGloballyPositioned { c ->
                                    holder.box?.let { holder.play = it.localBoundingBoxOf(c) }
                                }
                                .graphicsLayer { alpha = if (morphButtons) 0f else 1f },
                            isPlaying = state.isPlaying,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayPause()
                            }
                        )
                        Box(
                            Modifier
                                .size(54.dp)
                                .onGloballyPositioned { c ->
                                    holder.box?.let { holder.next = it.localBoundingBoxOf(c) }
                                }
                                .graphicsLayer { alpha = if (morphButtons) 0f else 1f }
                                .pressable { if (state.hasNext) onNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext, Str.get(R.string.next),
                                tint = ink.copy(alpha = if (state.hasNext) 1f else 0.35f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Box(Modifier.size(46.dp).pressable(onRepeat), contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                                else Icons.Rounded.Repeat,
                                Str.get(R.string.repeat),
                                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else inactive
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                }
            }

            // --- La carátula: vuela desde la miniatura y se funde con el fondo ---
            val target = slot
            if (target != null) {
                val boxPos = holder.box?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
                val from = (if (collapseToMini) origin.miniRect ?: origin.rect else origin.rect)
                    ?.translate(-boxPos.x, -boxPos.y)
                    ?: Rect(target.center, 0f).inflate(with(density) { 24.dp.toPx() })
                FlyingCover(
                    artUri = current.artUri,
                    // Ocupa de borde a borde: el difuminado se funde con el fondo sin cortes.
                    rect = lerpRect(
                        from,
                        // +10 % de tamaño respecto al hueco (5 % por lado) y el margen de difuminado.
                        target.inflate(with(density) { 26.dp.toPx() } + target.width * 0.05f),
                        progress.coerceIn(0f, 1f)
                    ),
                    progress = progress.coerceIn(0f, 1f),
                    startRadiusPx = origin.radius,
                    isPlaying = state.isPlaying,
                    slideX = swipeX.value,
                    slideWidth = rootSize.width.toFloat()
                )
            }

            // --- Off vocal: justo debajo del botón del ecualizador ---
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp + 44.dp + 8.dp, end = 26.dp)
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = (1f - contentAlpha) * 40.dp.toPx()
                    }
                    .size(44.dp)
                    .pressable { panel = 2 }
                    .then(
                        if (fx.vocalOff) Modifier.background(accent, CircleShape)
                        else Modifier.glass(CircleShape, fill = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MicOff, Str.get(R.string.off_vocal),
                    tint = if (fx.vocalOff) accent.onAccent() else ink
                )
            }

            // --- Play y Siguiente viajan entre el reproductor y la barra ---
            if (morphButtons) {
                val playTo = holder.play!!
                MorphPlayButton(
                    rect = lerpRect(origin.miniPlay!!, playTo, p),
                    progress = p,
                    isPlaying = state.isPlaying
                )
                val nextFrom = origin.miniNext
                val nextTo = holder.next
                if (nextFrom != null && nextTo != null) {
                    MorphIcon(
                        rect = lerpRect(nextFrom, nextTo, p),
                        icon = Icons.Rounded.SkipNext,
                        iconSize = 28.dp + (36.dp - 28.dp) * p,
                        alpha = if (state.hasNext) 1f else 0.35f
                    )
                }
            }
        }

        // --- Paneles flotantes (Slow + Reverb, Off vocal) ---
        AnimatedVisibility(
            visible = panel != 0,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { panel = 0 }
            )
        }
        val panelModifier = Modifier
            .navigationBarsPadding()
            .padding(12.dp)
            // Que los toques dentro del panel no lo cierren.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
        AnimatedVisibility(
            visible = panel == 1,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } +
                    fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200))
        ) {
            FxPanel(fx = fx, effects = effects, onClose = { panel = 0 }, modifier = panelModifier)
        }
        AnimatedVisibility(
            visible = panel == 2,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } +
                    fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200))
        ) {
            VocalPanel(fx = fx, effects = effects, onClose = { panel = 0 }, modifier = panelModifier)
        }
    }
}

// ---------------------------------------------------------------------------
// Botón principal con degradado y resplandor
// ---------------------------------------------------------------------------

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = accentColor
    val scale by animateFloatAsState(
        if (isPlaying) 1f else 0.93f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "ppScale"
    )
    Box(
        modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(20.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .pressable(onClick)
            .background(
                Brush.linearGradient(listOf(ink, accent.copy(alpha = 0.85f))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(200), 0.5f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(120), 0.5f))
            },
            label = "ppIcon"
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) Str.get(R.string.pause) else Str.get(R.string.play),
                tint = inverse,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

/** Espectro + tiempos + "mejor parte": lo único que se redibuja con cada avance de la canción. */
@Composable
private fun Timeline(
    spectrum: Spectrum?,
    position: StateFlow<Long>,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    bestMs: Long,
    onSetBest: (Long) -> Unit,
    onClearBest: () -> Unit
) {
    val positionMs by position.collectAsState()
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth()) {
        SpectrumSeekBar(
            spectrum = spectrum,
            positionMs = positionMs,
            durationMs = durationMs,
            accent = accent,
            onSeek = onSeek,
            bestFrac = if (bestMs >= 0 && durationMs > 0) (bestMs.toFloat() / durationMs).coerceIn(0f, 1f) else -1f
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val style = MaterialTheme.typography.labelMedium
            val c = MaterialTheme.colorScheme.onSurfaceVariant
            // Mitades de igual ancho: al cambiar los números del tiempo, la luna no se mueve.
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(positionMs.formatTime(), style = style, color = c, maxLines = 1)
            }
            // "La mejor parte": solo un icono de luna. Tocar salta ahí (o la marca si no hay);
            // mantener presionado la mueve al punto actual; la "x" pequeña la borra.
            val hasBest = bestMs >= 0
            Box {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 42.dp)
                        .pressableWithLongClick(onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSetBest(positionMs)
                        }) {
                            if (hasBest) onSeek(bestMs)
                            else {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetBest(positionMs)
                            }
                        }
                        .then(
                            if (hasBest) Modifier.background(accent.copy(alpha = 0.18f), RoundedCornerShape(50))
                            else Modifier.glass(RoundedCornerShape(50), fill = 0.07f, border = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        MoonIcon,
                        if (hasBest) Str.get(R.string.best_go) else Str.get(R.string.best_mark),
                        tint = if (hasBest) accent else ink.copy(alpha = 0.75f),
                        modifier = Modifier.size(30.dp)
                    )
                }
                if (hasBest) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .pressable(onClearBest)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, Str.get(R.string.best_remove), tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(durationMs.formatTime(), style = style, color = c, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Línea de tiempo = espectro real de la canción
// ---------------------------------------------------------------------------

/**
 * Cada barra es un tramo de la canción: su altura es el volumen real y se divide
 * en tres capas (graves en el centro, medios y agudos hacia afuera).
 * Toca o arrastra para saltar a cualquier punto.
 */
@Composable
private fun SpectrumSeekBar(
    spectrum: Spectrum?,
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    /** Posición de "la mejor parte" (0..1), o -1 si no hay. */
    bestFrac: Float = -1f
) {
    val haptics = LocalHapticFeedback.current
    val inkC = ink
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf(0f) }
    val duration by rememberUpdatedState(durationMs.coerceAtLeast(1L))
    val seekCallback by rememberUpdatedState(onSeek)
    val frac = if (dragging) dragFrac else (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    val ready = spectrum != null && spectrum.ok && spectrum.size > 0
    // Mientras se analiza, una ola suave recorre las barras.
    val wave = rememberInfiniteTransition(label = "specWait")
    val phase by wave.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "specPhase"
    )

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    seekCallback((o.x / size.width * duration).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dragging = true
                        dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        seekCallback((dragFrac * duration).toLong())
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val n = if (ready) spectrum!!.size else SpectrumExtractor.BUCKETS
        val slot = size.width / n
        val barW = slot * 0.62f
        val corner = CornerRadius(barW / 2f)
        val cy = size.height / 2f

        fun layer(i: Int, h: Float, color: Color) {
            val hh = h.coerceIn(barW, size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barW) / 2f, cy - hh / 2f),
                size = Size(barW, hh),
                cornerRadius = corner
            )
        }

        for (i in 0 until n) {
            val pos = (i + 0.5f) / n
            val played = pos <= frac
            val base = if (played) accent else inkC
            // Las barras cercanas a la posición actual "se inflan" un poco.
            val near = 1f - (abs(pos - frac) * 10f).coerceIn(0f, 1f)

            if (ready) {
                val s = spectrum!!
                val h = size.height * (0.10f + 0.90f * s.level[i]) * (1f + 0.12f * near)
                // Agudos (capa exterior), medios, y graves (núcleo).
                layer(i, h, base.copy(alpha = if (played) 0.35f else 0.14f))
                layer(i, h * (s.low[i] + s.mid[i]), base.copy(alpha = if (played) 0.65f else 0.26f))
                layer(i, h * s.low[i], base.copy(alpha = if (played) 1f else 0.45f))
            } else {
                val w = sin((pos * 6f - phase * 2f * PI.toFloat())).let { (it + 1f) / 2f }
                val h = size.height * (0.12f + 0.10f * w)
                layer(i, h, base.copy(alpha = if (spectrum?.ok == false) 0.2f else 0.15f + 0.2f * w))
            }
        }

        // Marca de "la mejor parte": línea fina con un punto arriba.
        if (bestFrac >= 0f) {
            val bx = bestFrac * size.width
            drawRoundRect(
                accent.copy(alpha = 0.8f), Offset(bx - 0.75.dp.toPx(), 4.dp.toPx()),
                Size(1.5.dp.toPx(), size.height - 4.dp.toPx()), CornerRadius(1.dp.toPx())
            )
            drawCircle(accent, 4.dp.toPx(), Offset(bx, 4.dp.toPx()))
        }

        // Marcador de la posición actual.
        val x = frac * size.width
        drawRoundRect(
            inkC, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), CornerRadius(1.dp.toPx())
        )
    }
}
