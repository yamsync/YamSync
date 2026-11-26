package io.github.audiz.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.PI
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import io.github.audiz.dsp.AudioVisualizer
import io.github.audiz.player.PlayerActions
import io.github.audiz.player.PlayerUiState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MobileMiniPlayerBar(
    state: PlayerUiState,
    actions: PlayerActions,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentProgress = state.progress

    val currentActions by rememberUpdatedState(actions)
    var miniPlayerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val swipeGuard = remember { PlayerSwipeGuard() }
    val expandDragModifier = Modifier.pointerInput(swipeGuard) {
        val openThreshold = 96.dp.toPx()
        var distance = 0f
        var dragging = false

        fun finish(open: Boolean) {
            if (dragging) {
                dragging = false
                currentActions.onMobilePlayerDragEnd(open)
            }
        }

        try {
            detectPlayerDrag(
                guard = swipeGuard,
                orientation = Orientation.Vertical,
                onDragStart = {
                    dragging = true
                    distance = 0f
                },
                onDrag = { delta ->
                    distance = (distance - delta).coerceAtLeast(0f)
                    val top = miniPlayerCoordinates
                        ?.takeIf { it.isAttached && distance > 0f }
                        ?.positionInRoot()?.y?.minus(distance)
                    currentActions.onMobilePlayerDrag(top)
                },
                onDragEnd = { finish(distance >= openThreshold) },
                onDragCancel = { finish(false) }
            )
        } finally {
            finish(false)
        }
    }

    val coverBitmap = rememberCoverBitmap(state.coverUri, 200)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { miniPlayerCoordinates = it }
            .then(expandDragModifier)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpand
                    )
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp, bottom = 2.dp)
                        .size(width = 36.dp, height = 3.5.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            CircleShape
                        )
                )
            }

            MiniPlayerProgressBar(
                progress = currentProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .padding(top = 1.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExpand
                        )
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap,
                                contentDescription = "Обложка трека",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (state.isActivelyPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.28f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NowPlayingIndicator(
                                        isPlaying = true,
                                        color = Color.White,
                                        maxHeight = 14.dp
                                    )
                                }
                            }
                        } else if (state.isActivelyPlaying) {
                            NowPlayingIndicator(
                                isPlaying = true,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxHeight = 18.dp
                            )
                        } else if (state.isPaused) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Пауза",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Музыка",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BouncingMarqueeText(
                                text = state.fullTrackTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f),
                                enableCopy = false
                            )
                            AiTrackBadge(state.trackId, state.trackArtists)
                        }
                        if (state.isWave) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isThematicWave) {
                                    Icon(
                                        imageVector = Icons.Filled.Waves,
                                        contentDescription = "Моя Волна",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = state.contextLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (state.isThematicWave && actions.onResetWave != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { actions.onResetWave.invoke() }
                                            )
                                            .pointerHoverIcon(PointerIcon.Hand),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Сбросить к обычной волне",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PlayerTransportControls(
                    state, actions, skipSize = 42.dp, playSize = 48.dp, spacing = 4.dp,
                    controlModifier = swipeGuard.controlModifier,
                )
            }
        }
    }
}

internal fun Modifier.clipAboveMiniPlayer(top: Float): Modifier = clip(GenericShape { size, _ ->
    addRect(Rect(0f, 0f, size.width, top.coerceIn(0f, size.height)))
})

/**
 * ⏱ Неинтерактивная шкала прогресса для мини-плеера (только индикация).
 * Не перехватывает клики и жесты, позволяя свободно нажимать и свайпать мини-плеер вверх.
 */
@Composable
private fun MiniPlayerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.90f))
        )
    }
}

private class PlayerSwipeGuard {
    val controlPointers = mutableSetOf<PointerId>()

    val controlModifier: Modifier = Modifier.pointerInput(this) {
        awaitEachGesture {
            // Controls receive the Main pass before the enclosing player.
            val down = awaitFirstDown(requireUnconsumed = false)
            controlPointers.add(down.id)
            try {
                // Use touch tolerance for mouse clicks too. Only a deliberate drag cancels a tap.
                awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            } finally {
                controlPointers.remove(down.id)
            }
        }
    }
}

private suspend fun PointerInputScope.detectPlayerDrag(
    guard: PlayerSwipeGuard,
    orientation: Orientation,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    val horizontal = orientation == Orientation.Horizontal
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.id in guard.controlPointers) return@awaitEachGesture

        var overSlop = 0f
        val acceptDrag: (PointerInputChange, Float) -> Unit = { change, distance ->
            change.consume()
            overSlop = distance
        }
        val firstDrag = if (horizontal) {
            awaitHorizontalPointerSlopOrCancellation(down.id, down.type, acceptDrag)
        } else {
            awaitVerticalPointerSlopOrCancellation(down.id, down.type, acceptDrag)
        } ?: return@awaitEachGesture

        onDragStart()
        onDrag(overSlop)
        val move: (PointerInputChange) -> Unit = { change ->
            val delta = change.positionChange()
            onDrag(if (horizontal) delta.x else delta.y)
            change.consume()
        }
        val completed = if (horizontal) horizontalDrag(firstDrag.id, move) else verticalDrag(firstDrag.id, move)
        if (completed) onDragEnd() else onDragCancel()
    }
}

@Composable
fun MobileFullPlayerSheet(
    state: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true
) {
    val coverCornerRadius = 24.dp

    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val coroutineScope = rememberCoroutineScope()
    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY = remember { Animatable(0f) }

    var seekPreview by remember(state.trackId) { mutableStateOf<Float?>(null) }
    val currentMs = seekPreview?.let(state::positionAt) ?: state.currentPositionMs

    val coverBitmap = rememberCoverBitmap(state.coverUri, 600)
    // Палитра считается по пикселям обложки — вне UI-потока
    val ambilightPalette by produceState<AmbilightPalette?>(null, coverBitmap) {
        value = coverBitmap?.let { bitmap ->
            withContext(Dispatchers.Default) { extractAmbilightPalette(bitmap) }
        }
    }

    // «Дыхание» подсветки: только во время воспроизведения и на общей сетке ~30 fps
    val isActivelyPlaying = state.isActivelyPlaying
    val breathingAlphaState = remember { mutableFloatStateOf(0.50f) }
    LaunchedEffect(isActivelyPlaying) {
        if (!isActivelyPlaying) {
            breathingAlphaState.floatValue = 0.50f
            return@LaunchedEffect
        }
        val ticker = DecorFrameTicker()
        var startNs = 0L
        while (isActive) {
            ticker.awaitFrame { frameNs, _ ->
                if (startNs == 0L) startNs = frameNs
                val halfCycles = (frameNs - startNs) / 3_200_000_000.0
                val phase = (halfCycles % 2.0).toFloat()
                val progress = if (phase < 1f) phase else 2f - phase
                breathingAlphaState.floatValue = 0.65f + 0.25f * FastOutSlowInEasing.transform(progress)
            }
        }
    }
    val breathingAlpha by breathingAlphaState

    val currentActions by rememberUpdatedState(actions)
    val currentState by rememberUpdatedState(state)
    val swipeGuard = remember { PlayerSwipeGuard() }
    val controlModifier = swipeGuard.controlModifier
    val trackDragOffsetX = remember { mutableFloatStateOf(0f) }
    val trackSwipeModifier = Modifier.pointerInput(swipeGuard) {
        val swipeThreshold = 64.dp.toPx()
        var dragDistance = 0f
        var startingOffset = 0f
        var settleJob: Job? = null

        fun settle() {
            settleJob = coroutineScope.launch {
                animate(trackDragOffsetX.floatValue, 0f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { value, _ ->
                    trackDragOffsetX.floatValue = value
                }
            }
        }

        detectPlayerDrag(
            guard = swipeGuard,
            orientation = Orientation.Horizontal,
            onDragStart = {
                settleJob?.cancel()
                startingOffset = trackDragOffsetX.floatValue
                dragDistance = 0f
            },
            onDrag = { dragAmount ->
                dragDistance += dragAmount
                trackDragOffsetX.floatValue = startingOffset + dragDistance
            },
            onDragEnd = {
                when {
                    dragDistance <= -swipeThreshold && !currentState.isNextLoading ->
                        currentActions.onNext?.invoke()
                    dragDistance >= swipeThreshold && !currentState.isPrevLoading ->
                        currentActions.onPrev?.invoke()
                }
                settle()
            },
            onDragCancel = { settle() }
        )
    }

    val dismissDragModifier = Modifier.pointerInput(swipeGuard) {
        detectPlayerDrag(
            guard = swipeGuard,
            orientation = Orientation.Vertical,
            onDrag = { dragAmount ->
                if (dragAmount > 0 || dragOffsetY > 0) {
                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                }
            },
            onDragEnd = {
                if (dragOffsetY > 120f) {
                    onDismiss()
                } else {
                    coroutineScope.launch {
                        animatedOffsetY.snapTo(dragOffsetY)
                        dragOffsetY = 0f
                        animatedOffsetY.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
                    }
                }
            },
            onDragCancel = {
                dragOffsetY = 0f
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .then(trackSwipeModifier)
            .offset { IntOffset(0, (dragOffsetY + animatedOffsetY.value).roundToInt()) },
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = trackDragOffsetX.floatValue
            }
        ) {
            val isNarrow = maxWidth < 360.dp
            val isVeryNarrow = maxWidth < 300.dp
            val horizontalPadding = when {
                isVeryNarrow -> 10.dp
                isNarrow -> 16.dp
                else -> 24.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dismissDragModifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 12.dp)
                        .size(width = 40.dp, height = 4.5.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            CircleShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp).then(controlModifier)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Свернуть плеер",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        val labelModifier = if (state.isWave && actions.onOpenWaveThemes != null) {
                            Modifier.weight(1f, fill = false)
                                .clip(RoundedCornerShape(8.dp))
                                .then(controlModifier)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { actions.onOpenWaveThemes.invoke() }
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        } else {
                            Modifier.weight(1f, fill = false)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = labelModifier
                        ) {
                            if (state.isThematicWave) {
                                Icon(
                                    imageVector = Icons.Filled.Waves,
                                    contentDescription = "Моя Волна",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = state.contextLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (state.isThematicWave && actions.onResetWave != null) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                    .then(controlModifier)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { actions.onResetWave.invoke() }
                                    )
                                    .pointerHoverIcon(PointerIcon.Hand),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Сбросить к обычной волне",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    if (actions.onConfigClick != null) {
                        IconButton(
                            onClick = { actions.onConfigClick.invoke() },
                            modifier = Modifier.size(44.dp).then(controlModifier)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Настройки",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 14.dp)
                    .aspectRatio(1f)
                    .fillMaxWidth(0.80f)
                    .then(dismissDragModifier),
                contentAlignment = Alignment.Center
            ) {
                val glowLeft = ambilightPalette?.left ?: MaterialTheme.colorScheme.primary
                val glowRight = ambilightPalette?.right ?: MaterialTheme.colorScheme.secondary

                // Ореол из 24 градиентных слоёв растеризуется один раз в bitmap (при смене размера
                // или цветов), а каждый кадр выводится одной текстурой с «дышащей» прозрачностью.
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val maxDistance = 46.dp.toPx()
                            val glowImage = if (w > 0f && h > 0f) {
                                renderAmbilightGlow(
                                    density = this,
                                    width = w,
                                    height = h,
                                    maxDistance = maxDistance,
                                    baseCornerRadius = coverCornerRadius.toPx(),
                                    glowLeft = glowLeft,
                                    glowRight = glowRight
                                )
                            } else null
                            onDrawBehind {
                                if (glowImage != null) {
                                    drawImage(
                                        image = glowImage,
                                        topLeft = Offset(-maxDistance, -maxDistance),
                                        alpha = breathingAlpha.coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(coverCornerRadius))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(coverCornerRadius)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = actions.onPlayPause
                        )
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap,
                            contentDescription = "Обложка альбома",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (state.isPaused) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "Пауза",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(64.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = "Музыка",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(72.dp)
                                )
                            }
                        }
                    }

                    CoverSpectrumVisualizer(
                        isPlaying = state.isActivelyPlaying,
                        glowLeft = glowLeft,
                        glowRight = glowRight,
                        onOpenEqualizer = actions.onOpenEqualizer,
                        coverCornerRadius = coverCornerRadius,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .then(if (actions.onOpenEqualizer != null) controlModifier else Modifier)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                ) {
                    BouncingMarqueeText(
                        text = state.trackTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.artistName.ifBlank { "Яндекс Музыка" },
                            modifier = Modifier.weight(1f, fill = false)
                                .then(
                                    if (actions.onOpenArtist != null) Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .then(controlModifier)
                                        .clickable(onClick = actions.onOpenArtist)
                                    else Modifier
                                ),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        AiArtistBadge(state.trackArtists)
                    }
                }

                actions.onToggleFavorite?.let { toggleFavorite ->
                    FavoriteButton(
                        isFavorite = state.isFavorite,
                        modifier = controlModifier,
                        size = 48.dp,
                        onClick = toggleFavorite,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                PlayerSeekBar(
                    state = state,
                    preview = seekPreview,
                    onPreviewChange = { seekPreview = it },
                    onSeek = actions.onSeek,
                    barHeight = 6.dp,
                    touchTargetHeight = 36.dp,
                )

                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = formatTime(currentMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    if (state.bitrate != null && state.bitrate > 0) {
                        val bitrateText = if (state.bitrate >= 1000) "${state.bitrate / 1000}M" else "${state.bitrate}k"
                        Text(
                            text = bitrateText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Text(
                        text = formatTime(state.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }

            val playButtonSize = if (isNarrow) 58.dp else 68.dp
            val skipButtonSize = if (isNarrow) 44.dp else 52.dp
            val sideSlotButtonSize = if (isNarrow) 38.dp else 44.dp
            val controlsSpacing = if (isNarrow) 8.dp else 16.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isNarrow) 4.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Левый слот (Перемешать) - симметричен правому слоту
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GhostIconButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = "Случайный порядок",
                        modifier = controlModifier,
                        size = sideSlotButtonSize,
                        iconSize = if (isNarrow) 20.dp else 24.dp,
                        contentColor = if (state.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        onClick = { actions.onToggleShuffle?.invoke() }
                    )
                }

                // 2. ЦЕНТРАЛЬНЫЙ БЛОК: Предыдущий + Play/Pause + Следующий (ВСЕГДА СТРОГО ПО ЦЕНТРУ ЭКРАНА)
                PlayerTransportControls(
                    state, actions, skipButtonSize, playButtonSize, controlsSpacing, controlModifier,
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    DesktopVolumeControl(
                        volume = state.volume,
                        onVolumeChange = actions.onVolumeChange,
                        modifier = controlModifier,
                        buttonSize = sideSlotButtonSize,
                        bottomPlayerPadding = 0.dp
                    )
                }
            }

            var justCopiedShare by remember { mutableStateOf(false) }

            LaunchedEffect(justCopiedShare) {
                if (justCopiedShare) {
                    delay(1500)
                    justCopiedShare = false
                }
            }

            val actionButtonSize = if (isNarrow) 34.dp else 40.dp
            val actionIconSize = if (isNarrow) 20.dp else 22.dp
            val defaultActionTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerRemovalButton(state, actions.onManageTrackRemoval, actionButtonSize, actionIconSize, controlModifier)
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    BadgedBox(badge = {
                        if (state.upNextCount > 0) Badge { Text(state.upNextCount.toString()) }
                    }) {
                        GhostIconButton(
                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "Очередь воспроизведения",
                            modifier = controlModifier,
                            size = actionButtonSize,
                            iconSize = actionIconSize,
                            contentColor = if (state.isQueueOpen) MaterialTheme.colorScheme.primary else defaultActionTint,
                            onClick = {
                                actions.onOpenQueue?.invoke()
                                onDismiss()
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GhostIconButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "Эквалайзер",
                        modifier = controlModifier,
                        size = actionButtonSize,
                        iconSize = actionIconSize,
                        contentColor = if (state.isEqualizerActive) MaterialTheme.colorScheme.primary else defaultActionTint,
                        onClick = { actions.onOpenEqualizer?.invoke() }
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerSaveButton(state, actions.onSaveOrManageTrack, actionButtonSize, actionIconSize, controlModifier)
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerPlaylistButton(state, actions.onAddToPlaylist, actionButtonSize, actionIconSize, controlModifier)
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isSharing) {
                        Box(
                            modifier = Modifier.size(actionButtonSize).then(controlModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(actionIconSize),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        val shareIcon = if (justCopiedShare) Icons.Filled.Check else Icons.Filled.Share
                        val shareTint = if (justCopiedShare) Color(0xFF1D9E75) else defaultActionTint
                        GhostIconButton(
                            icon = shareIcon,
                            contentDescription = if (justCopiedShare) "Ссылка скопирована" else "Поделиться треком",
                            modifier = controlModifier,
                            size = actionButtonSize,
                            iconSize = actionIconSize,
                            contentColor = shareTint,
                            onClick = {
                                justCopiedShare = true
                                actions.onShareTrack?.invoke()
                            }
                        )
                    }
                }
            }

        }
    }
}
}

/**
 * Растеризует Ambilight-ореол (24 градиентных слоя с гауссовым спадом) в bitmap
 * размером обложки плюс [maxDistance] с каждой стороны.
 */
private fun renderAmbilightGlow(
    density: Density,
    width: Float,
    height: Float,
    maxDistance: Float,
    baseCornerRadius: Float,
    glowLeft: Color,
    glowRight: Color
): ImageBitmap {
    val imageW = (width + 2 * maxDistance).roundToInt().coerceAtLeast(1)
    val imageH = (height + 2 * maxDistance).roundToInt().coerceAtLeast(1)
    val image = ImageBitmap(imageW, imageH)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(imageW.toFloat(), imageH.toFloat())) {
        val steps = 24
        for (i in steps downTo 1) {
            val d = (i.toFloat() / steps) * maxDistance
            val factor = d / maxDistance
            val alpha = (0.13f * exp(-2.2f * factor * factor)).coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(glowLeft.copy(alpha = alpha), glowRight.copy(alpha = alpha)),
                    startX = maxDistance - d,
                    endX = maxDistance + width + d
                ),
                topLeft = Offset(maxDistance - d, maxDistance - d),
                size = Size(width + 2 * d, height + 2 * d),
                cornerRadius = CornerRadius(baseCornerRadius + d, baseCornerRadius + d)
            )
        }
    }
    return image
}

data class AmbilightPalette(
    val left: Color,
    val right: Color,
    val center: Color
)

/**
 * 🎨 Безопасное извлечение насыщенных (vibrant) цветов из обложки альбома.
 * Игнорирует белые/черные/серые рамки и находит истинные акцентные цвета арта.
 */
fun extractAmbilightPalette(bitmap: androidx.compose.ui.graphics.ImageBitmap?): AmbilightPalette? {
    if (bitmap == null) return null
    return runCatching {
        val pixelMap = bitmap.toPixelMap()
        val w = pixelMap.width
        val h = pixelMap.height
        if (w <= 0 || h <= 0) return null

        fun findVibrantInRegion(minX: Int, maxX: Int): Color {
            var bestScore = 0f
            var bestColor = Color.Transparent
            val stepX = ((maxX - minX) / 12).coerceAtLeast(1)
            val stepY = (h / 12).coerceAtLeast(1)

            for (x in minX until maxX step stepX) {
                for (y in 0 until h step stepY) {
                    val c = pixelMap[x, y]
                    val rf = c.red
                    val gf = c.green
                    val bf = c.blue

                    val maxVal = maxOf(rf, gf, bf)
                    val minVal = minOf(rf, gf, bf)
                    val delta = maxVal - minVal
                    val saturation = if (maxVal > 0.05f) delta / maxVal else 0f
                    val brightness = maxVal

                    if (brightness in 0.15f..0.98f && saturation > 0.20f) {
                        val score = saturation * brightness
                        if (score > bestScore) {
                            bestScore = score
                            bestColor = c
                        }
                    }
                }
            }
            return if (bestScore > 0f) bestColor else pixelMap[minX + (maxX - minX) / 2, h / 2]
        }

        val leftColor = findVibrantInRegion(0, w / 2)
        val rightColor = findVibrantInRegion(w / 2, w)
        val centerColor = pixelMap[w / 2, h / 2]

        AmbilightPalette(
            left = leftColor,
            right = rightColor,
            center = centerColor
        )
    }.getOrNull()
}

/**
 * Спектр вдоль нижнего края обложки с плавным уменьшением высоты и прозрачности по краям.
 * Цвета следуют палитре обложки; клик открывает эквалайзер.
 */
@Composable
fun CoverSpectrumVisualizer(
    isPlaying: Boolean,
    glowLeft: Color,
    glowRight: Color,
    onOpenEqualizer: (() -> Unit)?,
    coverCornerRadius: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    var frame by remember {
        mutableStateOf(AudioVisualizer.advanceSpectrumFrame(0.016f, isPlaying))
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            frame = AudioVisualizer.advanceSpectrumFrame(0.016f, false)
        } else {
            val ticker = DecorFrameTicker()
            while (isActive) {
                ticker.awaitFrame { _, dt ->
                    frame = AudioVisualizer.advanceSpectrumFrame(dt.coerceIn(0.005f, 0.05f), true)
                }
            }
        }
    }

    val numBars = AudioVisualizer.SPECTRUM_BAND_COUNT
    val heightEnvelope = remember(numBars) {
        FloatArray(numBars) { index ->
            sin(PI * (index + 0.5) / numBars).toFloat().pow(0.8f)
        }
    }
    val edgeOpacity = remember(numBars) {
        FloatArray(numBars) { index ->
            val position = (index + 0.5f) / numBars
            val fade = (minOf(position, 1f - position) / 0.14f).coerceIn(0f, 1f)
            fade * fade * (3f - 2f * fade)
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / 0.28f)
            .clip(RoundedCornerShape(bottomStart = coverCornerRadius, bottomEnd = coverCornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x08000000),
                        Color(0x33000000),
                        Color(0x80000000)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onOpenEqualizer != null,
                onClick = { onOpenEqualizer?.invoke() }
            )
            .pointerHoverIcon(if (onOpenEqualizer != null) PointerIcon.Hand else PointerIcon.Default)
    ) {
        val bottomInset = maxOf(maxWidth * 0.045f, coverCornerRadius * 0.45f)
        val spectrumHeight = maxWidth * 0.20f
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(spectrumHeight + bottomInset)
                .align(Alignment.BottomCenter)
        ) {
            val totalW = size.width
            val maxH = size.height - bottomInset.toPx()
            if (totalW <= 0f || maxH <= 0f) return@Canvas
            val reflectionTop = maxH + 1.dp.toPx()
            val reflectionScale = ((size.height - reflectionTop) / maxH).coerceIn(0f, 0.30f)

            // Кадр спектра читается только здесь: обновление каждый кадр перерисовывает
            // Canvas, не вызывая рекомпозицию BoxWithConstraints.
            val barLevels = frame.levels
            val capLevels = frame.peakCaps

            val sideInset = maxOf(totalW * 0.035f, coverCornerRadius.toPx() / 3f)
            val availableW = totalW - 2f * sideInset
            if (availableW <= 0f) return@Canvas
            val preferredSpacing = if (numBars >= 25) 2.dp.toPx() else 4.dp.toPx()
            val spacing = minOf(preferredSpacing, availableW / (numBars * 2f))
            val barW = (availableW - (numBars - 1) * spacing) / numBars
            val totalBarsW = availableW
            val startX = sideInset
            val peakH = 2.dp.toPx()

            val barBrush = Brush.horizontalGradient(
                colors = listOf(
                    lerp(glowLeft, Color(0xFFFFF3D4), 0.55f).copy(alpha = 0.98f),
                    lerp(glowRight, Color(0xFFFFF3D4), 0.55f).copy(alpha = 0.98f)
                ),
                startX = startX,
                endX = startX + totalBarsW
            )

            for (i in 0 until numBars) {
                val level = if (i < barLevels.size) barLevels[i] else AudioVisualizer.RESTING_LEVEL
                val envelope = heightEnvelope[i]
                val opacity = edgeOpacity[i]
                val h = (maxH * level.coerceIn(0.08f, 1f)).coerceAtLeast(minOf(barW, maxH)) * envelope
                val x = startX + i * (barW + spacing)
                val y = maxH - h
                val barRadius = CornerRadius(minOf(1.dp.toPx(), barW * 0.2f))

                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = barRadius,
                    alpha = opacity
                )

                val capLevel = if (i < capLevels.size) capLevels[i].coerceAtLeast(level) else level
                val taperedPeakH = peakH * envelope
                val targetPeakY = (maxH - (maxH * capLevel.coerceIn(0.08f, 1f) * envelope))
                    .coerceIn(0f, (maxH - taperedPeakH).coerceAtLeast(0f))
                val peakY = targetPeakY.coerceAtMost(y - 2.dp.toPx() * envelope).coerceAtLeast(0f)
                drawRect(
                    color = Color.White.copy(alpha = 0.94f * opacity),
                    topLeft = Offset(x, peakY),
                    size = Size(barW, taperedPeakH)
                )

                if (reflectionScale > 0f) {
                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(x, reflectionTop),
                        size = Size(barW, h * reflectionScale),
                        cornerRadius = barRadius,
                        alpha = opacity * 0.32f
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.22f * opacity),
                        topLeft = Offset(x, reflectionTop + (maxH - peakY - taperedPeakH) * reflectionScale),
                        size = Size(barW, taperedPeakH * reflectionScale)
                    )
                }
            }
        }
    }
}



