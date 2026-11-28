package io.github.audiz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.audiz.player.PlayerActions
import io.github.audiz.player.PlayerUiState
import kotlinx.coroutines.delay

@Composable
internal fun PlayerTransportControls(
    state: PlayerUiState,
    actions: PlayerActions,
    skipSize: Dp,
    playSize: Dp,
    spacing: Dp,
    controlModifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        OutlineIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Предыдущий трек",
            modifier = controlModifier,
            size = skipSize,
            isLoading = state.isPrevLoading,
            onClick = { actions.onPrev?.invoke() },
        )
        FilledIconButton(
            icon = if (state.isActivelyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (state.isActivelyPlaying) "Пауза" else "Воспроизвести",
            modifier = controlModifier,
            size = playSize,
            isLoading = state.isPlayLoading && !state.isActivelyPlaying,
            onClick = actions.onPlayPause,
        )
        OutlineIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Следующий трек",
            modifier = controlModifier,
            size = skipSize,
            isLoading = state.isNextLoading,
            onClick = { actions.onNext?.invoke() },
        )
    }
}

@Composable
internal fun PlayerShuffleButton(
    state: PlayerUiState,
    onToggle: (() -> Unit)?,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    GhostIconButton(
        icon = Icons.Filled.Shuffle,
        contentDescription = "Случайный порядок",
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        contentColor = if (state.isShuffle) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        onClick = { onToggle?.invoke() },
    )
}

@Composable
internal fun PlayingPlaylistButton(
    onOpen: () -> Unit,
    size: Dp = 44.dp,
) {
    GhostIconButton(
        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
        contentDescription = "Текущий плейлист",
        size = size,
        iconSize = 22.dp,
        onClick = onOpen,
    )
}

@Composable
internal fun PlayerSeekBar(
    state: PlayerUiState,
    preview: Float?,
    onPreviewChange: (Float?) -> Unit,
    onSeek: (Long) -> Unit,
    barHeight: Dp,
    touchTargetHeight: Dp,
    thumbSize: Dp = 0.dp,
) {
    ThinSlider(
        value = preview ?: state.progress,
        onValueChange = { if (state.durationMs > 0) onPreviewChange(it) },
        onValueChangeFinished = { fraction ->
            if (state.durationMs > 0) onSeek(state.positionAt(fraction))
            onPreviewChange(null)
        },
        modifier = Modifier.fillMaxWidth(),
        barHeight = barHeight,
        touchTargetHeight = touchTargetHeight,
        thumbSize = thumbSize,
    )
}

@Composable
internal fun PlayerRemovalButton(
    state: PlayerUiState,
    onRemove: (() -> Unit)?,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    GhostIconButton(
        icon = Icons.Filled.Delete,
        contentDescription = "Удалить трек",
        size = size,
        iconSize = iconSize,
        modifier = modifier,
        enabled = state.hasTrackCandidate && onRemove != null,
        contentColor = if (state.isDisliked || state.isLocalTrack || state.isSavedToDisk) {
            MaterialTheme.colorScheme.error
        } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        onClick = { onRemove?.invoke() },
    )
}

@Composable
internal fun PlayerSaveButton(
    state: PlayerUiState,
    onSave: (() -> Unit)?,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    if (state.isSavingToDisk) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(iconSize), strokeWidth = 1.8.dp)
        }
    } else {
        GhostIconButton(
            icon = Icons.Filled.Save,
            contentDescription = if (state.isSavedToDisk) "Управлять сохранённым треком" else "Сохранить на диск",
            size = size,
            iconSize = iconSize,
            modifier = modifier,
            enabled = state.canSaveTrack && onSave != null,
            contentColor = if (state.isSavedToDisk) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            onClick = { onSave?.invoke() },
        )
    }
}

@Composable
internal fun PlayerPlaylistButton(
    state: PlayerUiState,
    onAdd: (() -> Unit)?,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    GhostIconButton(
        icon = if (state.isInPlaylist) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
        contentDescription = when (state.playlistCount) {
            0 -> "Добавить в плейлист"
            1 -> "В плейлисте"
            else -> "В плейлистах (${state.playlistCount})"
        },
        size = size,
        iconSize = iconSize,
        modifier = modifier,
        enabled = state.hasTrackCandidate && onAdd != null,
        contentColor = if (state.isInPlaylist) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        onClick = { onAdd?.invoke() },
    )
}

@Composable
internal fun FullPlayerAuxiliaryControls(
    state: PlayerUiState,
    actions: PlayerActions,
    isNarrow: Boolean,
    controlModifier: Modifier = Modifier,
    onQueueOpened: () -> Unit = {},
) {
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
                        onQueueOpened()
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
