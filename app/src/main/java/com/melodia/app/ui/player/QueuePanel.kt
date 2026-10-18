package com.melodia.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.melodia.app.R
import com.melodia.app.Str
import com.melodia.app.player.PlayerState
import com.melodia.app.player.QueueItem
import com.melodia.app.ui.components.Artwork
import com.melodia.app.ui.components.accentColor
import com.melodia.app.ui.components.glass
import com.melodia.app.ui.components.pressable
import com.melodia.app.ui.theme.LocalPalette
import com.melodia.app.ui.theme.ink
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val RowHeight = 64.dp

/**
 * Cola de reproducción a pantalla completa. Aparece al deslizar el reproductor hacia abajo:
 * el reproductor queda arriba en miniatura y debajo va la lista. [progress] (0..1) sigue al dedo.
 *
 * Reordenar: arrastra ≡. Durante el arrastre la lista no cambia: las demás filas se apartan con un
 * resorte y el cambio real se hace una sola vez al soltar (así no hay saltos ni filas encimadas).
 */
@Composable
fun QueueOverlay(
    progress: State<Float>,
    queue: List<QueueItem>,
    state: PlayerState,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    /** Arrastre vertical en píxeles (positivo = hacia abajo = cerrando la cola). */
    onProgressDelta: (Float) -> Unit,
    /** Se soltó el arrastre, con la velocidad vertical en px/s (positiva = hacia abajo). */
    onRelease: (Float) -> Unit
) {
    val palette = LocalPalette.current
    val accent = accentColor
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val rowPx = with(density) { RowHeight.toPx() }
    val scope = rememberCoroutineScope()
    var height by remember { mutableFloatStateOf(1f) }
    val current = state.current
    val currentIndex = state.queueIndex
    // Copia local: al soltar una fila el orden cambia al instante (sin esperar al reproductor).
    var items by remember { mutableStateOf(queue) }
    LaunchedEffect(queue) { items = queue }
    val currentUid = queue.getOrNull(currentIndex)?.uid

    // Arrastre de una fila: cuál, cuánto se movió y a qué posición caería.
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dropSlot = remember { Animatable(0f) }
    var settling by remember { mutableIntStateOf(-1) }
    val target = if (dragFrom < 0) -1
    else (dragFrom + (dragOffset / rowPx).roundToInt()).coerceIn(0, (items.size - 1).coerceAtLeast(0))

    // Tirar de la lista hacia abajo estando arriba del todo cierra la cola, siguiendo al dedo.
    var intercepting by remember { mutableStateOf(false) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (!intercepting && dy > 0f && atTop && dragFrom < 0 && source == NestedScrollSource.Drag) intercepting = true
                if (intercepting) {
                    if (dy < 0f && progress.value >= 0.999f) { intercepting = false; onRelease(0f); return Offset.Zero }
                    onProgressDelta(dy)
                    return Offset(0f, dy)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!intercepting) return Velocity.Zero
                intercepting = false
                onRelease(available.y)
                return available
            }
        }
    }

    LaunchedEffect(Unit) {
        if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .drawBehind { drawRect(palette.bg.copy(alpha = 0.92f * progress.value.coerceIn(0f, 1f))) }
            // Consume los toques para que no lleguen al reproductor que queda debajo.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // --- Reproductor en miniatura (arriba). Arrastrarlo hacia arriba cierra la cola. ---
            Row(
                Modifier
                    .graphicsLayer {
                        val q = progress.value.coerceIn(0f, 1f)
                        val k = ((q - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        alpha = k
                        translationY = -(1f - q) * 36.dp.toPx()
                        scaleX = 0.9f + 0.1f * k; scaleY = 0.9f + 0.1f * k
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dy -> onProgressDelta(dy) },
                        onDragStopped = { v -> onRelease(v) }
                    )
                    .glass(RoundedCornerShape(30.dp), fill = 0.12f, border = 0.16f)
                    .pressable(onClose)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(current?.artUri, 50.dp, cornerRadius = 16.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        current?.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier.size(44.dp).pressable(onPlayPause).glass(RoundedCornerShape(50), fill = 0.14f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, tint = ink
                    )
                }
                Box(Modifier.size(44.dp).pressable { if (state.hasNext) onNext() }, contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.SkipNext, null,
                        tint = ink.copy(alpha = if (state.hasNext) 1f else 0.35f)
                    )
                }
            }

            Row(
                Modifier
                    .graphicsLayer {
                        val q = progress.value.coerceIn(0f, 1f)
                        translationY = (1f - q) * height * 0.45f
                        alpha = ((q - 0.15f) / 0.6f).coerceIn(0f, 1f)
                    }
                    .fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Str.get(R.string.queue), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${items.size} · " + Str.get(R.string.queue_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    Modifier.size(40.dp).pressable(onClose).glass(RoundedCornerShape(50), fill = 0.12f),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, Str.get(R.string.close), tint = ink) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(nested)
                    .graphicsLayer {
                        val q = progress.value.coerceIn(0f, 1f)
                        translationY = (1f - q) * height * 0.7f
                        alpha = ((q - 0.1f) / 0.5f).coerceIn(0f, 1f)
                    }
            ) {
                itemsIndexed(items, key = { _, q -> q.uid }) { index, item ->
                    val indexNow by rememberUpdatedState(index)
                    val isCurrent = item.uid == currentUid
                    val dragging = index == dragFrom
                    // Las filas entre el origen y el destino se apartan una fila (con resorte).
                    val goal = when {
                        dragFrom < 0 -> 0f
                        index in (dragFrom + 1)..target -> -rowPx
                        index in target until dragFrom -> rowPx
                        else -> 0f
                    }
                    val animatedShift by animateFloatAsState(
                        goal, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "shift"
                    )
                    // Al terminar, la lista ya está reordenada: no debe quedar ningún desplazamiento.
                    val shift = if (dragFrom < 0) 0f else animatedShift
                    Row(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) {
                                    if (settling == index) dropSlot.value else dragOffset
                                } else shift
                                if (dragging) { scaleX = 1.02f; scaleY = 1.02f }
                            }
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                            .fillMaxWidth()
                            .height(RowHeight - 6.dp)
                            .glass(
                                RoundedCornerShape(20.dp),
                                fill = if (dragging) 0.24f else if (isCurrent) 0.16f else 0.06f,
                                border = if (isCurrent) 0.28f else 0.08f
                            )
                            .pressable { if (dragFrom < 0) onJump(index) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(item.artUri, 42.dp, cornerRadius = 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (isCurrent) Str.get(R.string.queue_now) else item.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (items.size > 1) {
                            Box(
                                Modifier.size(38.dp).pressable { if (dragFrom < 0) onRemove(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Close, Str.get(R.string.queue_remove),
                                    tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        // Asa para arrastrar.
                        Box(
                            Modifier
                                .size(44.dp)
                                .pointerInput(item.uid) {
                                    detectDragGestures(
                                        onDragStart = {
                                            if (dragFrom < 0 && settling < 0) { dragFrom = indexNow; dragOffset = 0f }
                                        },
                                        onDragEnd = {
                                            val from = dragFrom
                                            if (from >= 0) {
                                                val to = (from + (dragOffset / rowPx).roundToInt())
                                                            .coerceIn(0, (items.size - 1).coerceAtLeast(0))
                                                scope.launch {
                                                    // La fila se asienta en su hueco y recién entonces cambia la cola.
                                                    settling = from
                                                    dropSlot.snapTo(dragOffset)
                                                    dropSlot.animateTo(
                                                        (to - from) * rowPx,
                                                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                                                    )
                                                    if (to != from) {
                                                        // Orden nuevo al instante en la copia local y luego en el reproductor.
                                                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                                                        onMove(from, to)
                                                    }
                                                    dragFrom = -1; dragOffset = 0f; settling = -1
                                                }
                                            }
                                        },
                                        onDragCancel = { dragFrom = -1; dragOffset = 0f; settling = -1 },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (settling < 0 && dragFrom == indexNow) dragOffset += amount.y
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.DragHandle, null, tint = ink.copy(alpha = 0.6f))
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
