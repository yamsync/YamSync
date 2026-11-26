package io.github.audiz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.audiz.player.PlayerUiState

@Composable
internal fun FullPlayerContextLabel(state: PlayerUiState, modifier: Modifier = Modifier) {
    Text(
        text = state.contextLabel.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun FullPlayerSettingsIcon() {
    Icon(
        imageVector = Icons.Filled.Settings,
        contentDescription = "Настройки",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
internal fun FullPlayerCover(
    state: PlayerUiState,
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    coverCornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
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
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ).pointerHoverIcon(PointerIcon.Hand) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
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

        overlay()
    }
}

@Composable
internal fun FullPlayerTrackInfo(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onOpenArtist: (() -> Unit)? = null,
    controlModifier: Modifier = Modifier,
    animateTitle: Boolean = false,
    showArtistBadge: Boolean = false,
    titleSize: TextUnit = 22.sp,
    artistSize: TextUnit = 15.sp,
) {
    Column(modifier) {
        val titleStyle = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = titleSize
        )
        if (animateTitle) {
            BouncingMarqueeText(
                text = state.trackTitle,
                style = titleStyle,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = state.trackTitle,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = state.artistName.ifBlank { "Яндекс Музыка" },
                modifier = Modifier.weight(1f, fill = false)
                    .then(
                        if (onOpenArtist != null) Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .then(controlModifier)
                            .clickable(onClick = onOpenArtist)
                        else Modifier
                    ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = artistSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showArtistBadge) AiArtistBadge(state.trackArtists)
        }
    }
}

@Composable
internal fun FullPlayerTimeline(
    state: PlayerUiState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    touchTargetHeight: Dp = 36.dp,
    thumbSize: Dp = 0.dp,
) {
    var seekPreview by remember(state.trackId) { mutableStateOf<Float?>(null) }
    val currentMs = seekPreview?.let(state::positionAt) ?: state.currentPositionMs

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        PlayerSeekBar(
            state = state,
            preview = seekPreview,
            onPreviewChange = { seekPreview = it },
            onSeek = onSeek,
            barHeight = barHeight,
            touchTargetHeight = touchTargetHeight,
            thumbSize = thumbSize,
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
}
