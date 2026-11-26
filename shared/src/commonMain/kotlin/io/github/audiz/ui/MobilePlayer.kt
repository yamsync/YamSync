package io.github.audiz.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import io.github.audiz.player.PlayerActions
import io.github.audiz.player.PlayerUiState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    BackHandler(enabled = backHandlerEnabled, onBack = onDismiss)

    val coroutineScope = rememberCoroutineScope()
    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY = remember { Animatable(0f) }

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
                            FullPlayerContextLabel(
                                state = state,
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
                            FullPlayerSettingsIcon()
                        }
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }

            AnimatedPlayerCover(
                state = state,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 14.dp)
                    .aspectRatio(1f)
                    .fillMaxWidth(0.80f)
                    .then(dismissDragModifier),
                onCoverClick = actions.onPlayPause,
                onOpenEqualizer = actions.onOpenEqualizer,
                spectrumModifier = if (actions.onOpenEqualizer != null) controlModifier else Modifier,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FullPlayerTrackInfo(
                    state = state,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    onOpenArtist = actions.onOpenArtist,
                    controlModifier = controlModifier,
                    animateTitle = true,
                )

                actions.onToggleFavorite?.let { toggleFavorite ->
                    FavoriteButton(
                        isFavorite = state.isFavorite,
                        modifier = controlModifier,
                        size = 48.dp,
                        onClick = toggleFavorite,
                    )
                }
            }

            FullPlayerTimeline(
                state = state,
                onSeek = actions.onSeek,
                modifier = Modifier.padding(top = 10.dp),
            )

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
                    PlayerShuffleButton(
                        state = state,
                        onToggle = actions.onToggleShuffle,
                        modifier = controlModifier,
                        size = sideSlotButtonSize,
                        iconSize = if (isNarrow) 20.dp else 24.dp,
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
