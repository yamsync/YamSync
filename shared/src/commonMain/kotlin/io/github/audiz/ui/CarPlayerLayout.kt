package io.github.audiz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.audiz.player.PlayerActions
import io.github.audiz.player.PlayerUiState

@Composable
internal fun CarPlayerLayout(
    state: PlayerUiState,
    actions: PlayerActions,
    isPanelOpen: Boolean,
    onPanelOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val openPanel = { onPanelOpenChange(true) }
    val closePanel = { onPanelOpenChange(false) }
    val panelState = rememberSaveableStateHolder()
    val panelContent: @Composable () -> Unit = {
        panelState.SaveableStateProvider("carBrowser", content)
    }
    BackHandler(enabled = isPanelOpen, onBack = closePanel)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val canSplitHorizontally = maxWidth >= 640.dp || (maxWidth >= 480.dp && maxWidth > maxHeight)
        when {
            !isPanelOpen -> Row(Modifier.fillMaxSize()) {
                CompactFullPlayer(state, actions, Modifier.weight(1f).fillMaxHeight())
                CarPanelHandle(openPanel, Modifier.fillMaxHeight())
            }
            canSplitHorizontally -> SplitPlayerLayout(state, actions) {
                CarBrowserPanel(closePanel, Modifier.fillMaxSize(), panelContent)
            }
            else -> Column(Modifier.fillMaxSize()) {
                CompactFullPlayer(state, actions, Modifier.weight(1f).fillMaxWidth(), isSplit = true)
                HorizontalDivider()
                CarBrowserPanel(closePanel, Modifier.weight(1f).fillMaxWidth(), panelContent)
            }
        }
    }
}

@Composable
internal fun SplitPlayerLayout(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    mobileControls: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        CompactFullPlayer(
            state = state,
            actions = actions,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            isSplit = true,
            mobileControls = mobileControls,
        )
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) { content() }
    }
}

@Composable
private fun CarPanelHandle(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    val dragDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1 else 1
    Column(
        modifier = modifier.width(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .pointerInput(dragDirection) {
                val openThreshold = 24.dp.toPx()
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragEnd = { if (distance * dragDirection >= openThreshold) currentOnOpen() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        distance += dragAmount
                    },
                )
            }
            .clickable(role = Role.Button, onClickLabel = "Открыть медиатеку", onClick = onOpen)
            .pointerHoverIcon(PointerIcon.Hand),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(32.dp))
        Icon(
            Icons.AutoMirrored.Filled.FormatListBulleted,
            contentDescription = "Медиатека",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun CarBrowserPanel(
    onCollapse: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.clipToBounds()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
        OutlinedButton(
            onClick = onCollapse,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .pointerHoverIcon(PointerIcon.Hand),
            contentPadding = PaddingValues(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Свернуть панель", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactFullPlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier,
    isSplit: Boolean = false,
    mobileControls: Boolean = false,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val short = maxHeight < 440.dp
        val isNarrow = maxWidth < 360.dp
        val sideBySide = (!isSplit && maxWidth >= 540.dp) || (short && maxWidth >= 300.dp)
        val inset = if (maxWidth >= 540.dp) 24.dp else 12.dp
        val controlWidth = (maxWidth - inset * 2).coerceAtLeast(0.dp)
        val largeTargets = !short && controlWidth >= 420.dp
        val skipSize = minOf(if (largeTargets) 76.dp else 56.dp, controlWidth / 6)
        val playSize = skipSize + if (largeTargets) 12.dp else 8.dp
        val sideButtonSize = minOf(skipSize, 64.dp)
        val sideIconSize = minOf(sideButtonSize, 24.dp)
        val sideGap = minOf(12.dp, controlWidth / 24)
        val gap = ((controlWidth - sideButtonSize * 2 - skipSize * 2 - playSize - sideGap * 2) / 2)
            .coerceIn(0.dp, 100.dp)
        val headerButtonSize = if (short) 44.dp else 54.dp
        val sectionGap = if (short) 4.dp else 12.dp
        val trackInfo: @Composable (Modifier) -> Unit = { infoModifier ->
            Row(infoModifier, verticalAlignment = Alignment.CenterVertically) {
                FullPlayerTrackInfo(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onOpenArtist = actions.onOpenArtist.takeIf { mobileControls },
                    titleSize = if (sideBySide) {
                        if (short) 20.sp else 32.sp
                    } else 22.sp,
                    artistSize = if (sideBySide) {
                        if (short) 14.sp else 20.sp
                    } else 15.sp,
                )
                if (mobileControls) {
                    actions.onToggleFavorite?.let { toggleFavorite ->
                        Spacer(Modifier.width(8.dp))
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            size = if (short) 40.dp else 48.dp,
                            onClick = toggleFavorite,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = inset)
                .padding(top = sectionGap, bottom = sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(headerButtonSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(headerButtonSize)) {
                    actions.onOpenPlayingPlaylist?.let {
                        PlayingPlaylistButton(it, size = headerButtonSize)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    FullPlayerContextLabel(state)
                }
                IconButton(
                    onClick = { actions.onConfigClick?.invoke() },
                    enabled = actions.onConfigClick != null,
                    modifier = Modifier.size(headerButtonSize).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    FullPlayerSettingsIcon()
                }
            }

            if (sideBySide) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        AnimatedPlayerCover(
                            state, Modifier.size(minOf(maxWidth, maxHeight)),
                            onCoverClick = actions.onPlayPause.takeIf { mobileControls },
                            onOpenEqualizer = actions.onOpenEqualizer.takeIf { mobileControls },
                        )
                    }
                    trackInfo(Modifier.weight(1f))
                }
            } else {
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedPlayerCover(
                        state, Modifier.size(minOf(maxWidth, maxHeight)),
                        onCoverClick = actions.onPlayPause.takeIf { mobileControls },
                        onOpenEqualizer = actions.onOpenEqualizer.takeIf { mobileControls },
                    )
                }
                trackInfo(Modifier.fillMaxWidth())
            }

            FullPlayerTimeline(
                state = state,
                onSeek = actions.onSeek,
                barHeight = 7.dp,
                touchTargetHeight = if (short) 30.dp else 44.dp,
                thumbSize = 18.dp,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PlayerShuffleButton(state, actions.onToggleShuffle, sideButtonSize, sideIconSize)
                }
                Spacer(Modifier.width(sideGap))
                PlayerTransportControls(state, actions, skipSize, playSize, gap)
                Spacer(Modifier.width(sideGap))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DesktopVolumeControl(
                        volume = state.volume,
                        onVolumeChange = actions.onVolumeChange,
                        buttonSize = sideButtonSize,
                        shuffleIconSize = sideIconSize.takeUnless { mobileControls },
                    )
                }
            }
            if (mobileControls) {
                FullPlayerAuxiliaryControls(
                    state = state,
                    actions = actions,
                    isNarrow = isNarrow,
                )
            }
        }
    }
}
