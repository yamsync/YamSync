package io.github.audiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.audiz.player.PlayerActions
import io.github.audiz.player.PlayerUiState

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
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    GhostIconButton(
        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
        contentDescription = "Текущий плейлист",
        modifier = modifier,
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
