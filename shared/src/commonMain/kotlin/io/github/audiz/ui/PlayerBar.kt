package io.github.audiz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.audiz.player.PlayerActions
import io.github.audiz.player.PlayerUiState
import kotlinx.coroutines.delay

@Composable
fun PlayerBar(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier
) {
    if (state.isMobile) {
        MobileMiniPlayerBar(
            state = state,
            actions = actions,
            onExpand = { actions.onExpandMobilePlayer?.invoke() },
            modifier = modifier
        )
    } else {
        DesktopPlayerBar(
            state = state,
            actions = actions,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopPlayerBar(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier
) {
    var seekPreview by remember(state.trackId) { mutableStateOf<Float?>(null) }
    var justCopiedShare by remember { mutableStateOf(false) }
    val displayPosition = seekPreview?.let(state::positionAt) ?: state.currentPositionMs
    val coverBitmap = rememberCoverBitmap(state.coverUri, 120)

    LaunchedEffect(justCopiedShare) {
        if (justCopiedShare) {
            delay(1500)
            justCopiedShare = false
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            val buttonSize = 28.dp
            val controlsInOneRow = maxWidth >= 248.dp
            val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            val primary = MaterialTheme.colorScheme.primary

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f).widthIn(min = 96.dp).heightIn(min = buttonSize),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (coverBitmap != null) {
                                Image(
                                    bitmap = coverBitmap,
                                    contentDescription = "Обложка трека",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(16.dp), tint = tint)
                        }
                        NowPlayingIndicator(
                            isPlaying = state.isActivelyPlaying,
                            barWidth = 2.dp,
                            barSpacing = 1.dp,
                            maxHeight = 14.dp
                        )
                        val waveRowModifier = if (state.isWave && actions.onOpenWaveThemes != null) {
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = { actions.onOpenWaveThemes.invoke() })
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 2.dp, vertical = 1.dp)
                        } else {
                            Modifier.weight(1f)
                        }
                        Row(
                            modifier = waveRowModifier,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (state.isThematicWave) {
                                Icon(
                                    imageVector = Icons.Filled.Waves,
                                    contentDescription = "Моя Волна",
                                    tint = primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = state.contextLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isWave) primary else tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.isThematicWave) {
                            CompactPlayerAction(
                                icon = Icons.Filled.Close,
                                description = "Сбросить к персональной Волне",
                                size = 20.dp,
                                onClick = actions.onResetWave
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${formatTime(displayPosition)} / ${formatTime(state.durationMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum", fontSize = 10.sp),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.bitrate?.takeIf { it > 0 }?.let { bitrate ->
                            val bitrateText = if (bitrate >= 1000) "${bitrate / 1000}M" else "${bitrate}k"
                            Text(
                                text = "${if (bitrate >= 256) "HQ" else "LQ"} · $bitrateText",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DesktopVolumeControl(volume = state.volume, onVolumeChange = actions.onVolumeChange, buttonSize = buttonSize)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BouncingMarqueeText(
                        text = state.fullTrackTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                        modifier = Modifier.weight(1f)
                    )
                    AiTrackBadge(state.trackId, state.trackArtists)
                }

                PlayerSeekBar(
                    state = state,
                    preview = seekPreview,
                    onPreviewChange = { seekPreview = it },
                    onSeek = actions.onSeek,
                    barHeight = 5.dp,
                    touchTargetHeight = 14.dp,
                )

                val leftActions: @Composable () -> Unit = {
                    FlowRow(
                        modifier = Modifier.widthIn(max = buttonSize * 2 + 4.dp),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PlayerRemovalButton(state, actions.onManageTrackRemoval, buttonSize, iconSize = 17.dp)
                        CompactPlayerAction(
                            Icons.Filled.Shuffle, "Случайный порядок", actions.onToggleShuffle,
                            active = state.isShuffle
                        )
                        CompactPlayerAction(
                            Icons.Filled.Tune, "Эквалайзер и срезы частот", actions.onOpenEqualizer,
                            active = state.isEqualizerActive
                        )
                        BadgedBox(badge = {
                            if (state.upNextCount > 0) Badge { Text(state.upNextCount.toString()) }
                        }) {
                            CompactPlayerAction(
                                Icons.AutoMirrored.Filled.FormatListBulleted, "Очередь «Далее»", actions.onOpenQueue,
                                active = state.isQueueOpen
                            )
                        }
                    }
                }
                val rightActions: @Composable () -> Unit = {
                    FlowRow(
                        modifier = Modifier.widthIn(max = buttonSize * 2 + 4.dp),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        CompactPlayerAction(
                            if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (state.isFavorite) "Убрать из любимых" else "Добавить в любимые",
                            actions.onToggleFavorite,
                            active = state.isFavorite
                        )
                        PlayerSaveButton(state, actions.onSaveOrManageTrack, buttonSize, iconSize = 17.dp)
                        PlayerPlaylistButton(state, actions.onAddToPlaylist, buttonSize, iconSize = 17.dp)
                        CompactPlayerAction(
                            if (justCopiedShare) Icons.Filled.Check else Icons.Filled.Share,
                            if (justCopiedShare) "Ссылка скопирована" else "Поделиться треком",
                            actions.onShareTrack?.let { share -> { share(); justCopiedShare = true } },
                            active = justCopiedShare,
                            isLoading = state.isSharing
                        )
                    }
                }
                val transport: @Composable () -> Unit = {
                    PlayerTransportControls(state, actions, skipSize = buttonSize, playSize = 44.dp, spacing = 6.dp)
                }

                if (controlsInOneRow) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { leftActions() }
                        transport()
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { rightActions() }
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { transport() }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        leftActions()
                        rightActions()
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPlayerAction(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)?,
    size: Dp = 28.dp,
    active: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp))
            .clickable(
                enabled = enabled && onClick != null && !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() }
            )
            .pointerHoverIcon(if (enabled && onClick != null && !isLoading) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 1.5.dp)
        } else {
            Icon(
                icon, contentDescription = description,
                tint = if (enabled && onClick != null) tint else tint.copy(alpha = 0.3f),
                modifier = Modifier.size(minOf(17.dp, size * 0.65f))
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
