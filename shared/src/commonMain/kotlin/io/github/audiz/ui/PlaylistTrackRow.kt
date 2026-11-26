package io.github.audiz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import io.github.audiz.models.ChartPositionInfo
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.models.isLocalTrackId

@Composable
internal fun LazyListState.rememberVisiblePlaylistTrackIndices(): State<Set<Int>> = remember(this) {
    derivedStateOf { layoutInfo.visibleItemsInfo.mapTo(hashSetOf()) { it.index } }
}

@Composable
internal fun PlaylistTrackRow(
    track: FullTrackInfo,
    artists: String,
    loadThumbnail: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    chartPosition: ChartPositionInfo?,
    narrow: Boolean,
    onActivate: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onQueue: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    extraMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val corner = 14.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) corner else 0.dp,
        topEnd = if (isFirst) corner else 0.dp,
        bottomStart = if (isLast) corner else 0.dp,
        bottomEnd = if (isLast) corner else 0.dp,
    )
    val title = track.title.ifBlank { "Без названия" }
    val album = track.albums.firstOrNull()?.title.orEmpty()

    val playbackModifier = if (onActivate == null) Modifier else Modifier
        .clickable(
            role = Role.Button,
            onClickLabel = if (isPlaying) "Пауза" else "Воспроизвести",
            onClick = onActivate,
        )
        .pointerHoverIcon(PointerIcon.Hand)

    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isActive) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerHigh)
                .then(playbackModifier)
                .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)
                .heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistTrackThumbnail(track, loadThumbnail, displaySize = if (narrow) 44.dp else 52.dp)
            Spacer(Modifier.width(if (narrow) 8.dp else 10.dp))
            Layout(
                modifier = Modifier.weight(1f),
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (chartPosition != null) {
                            ChartPositionBadge(chartPosition)
                        }
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) colors.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = artists.ifBlank { "—" },
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AiTrackBadge(track.id, track.artists)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (narrow) {
                            Icon(
                                Icons.Filled.Album,
                                contentDescription = "Альбом",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        val albumName = album.ifBlank { "—" }
                        Text(
                            text = if (narrow) albumName else "Альбом: $albumName",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (track.durationMs > 0L) {
                            Text(
                                text = formatTime(track.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        PlaylistTrackMenu(onAddToPlaylist, onQueue, onRemove, extraMenuItems)
                    }
                },
            ) { measurables, constraints ->
                val (titleLine, artistLine, albumLine, actions) = measurables
                val width = constraints.maxWidth
                val rowConstraints = constraints.copy(minWidth = 0, minHeight = 0)
                val controls = actions.measure(rowConstraints)
                val textWidth = (width - controls.width - 8.dp.roundToPx()).coerceAtLeast(0)
                val wideTextConstraints = rowConstraints.copy(maxWidth = width - 4.dp.roundToPx().coerceAtMost(width))
                val artistConstraints = rowConstraints.copy(maxWidth = textWidth)
                val titlePlaceable = titleLine.measure(if (narrow) wideTextConstraints else artistConstraints)
                val artistPlaceable = artistLine.measure(artistConstraints)
                val albumPlaceable = albumLine.measure(if (narrow) wideTextConstraints else artistConstraints)
                val metadataHeight = titlePlaceable.height + artistPlaceable.height + albumPlaceable.height
                val height = constraints.constrainHeight(maxOf(metadataHeight, controls.height))
                val textTop = (height - metadataHeight) / 2
                val artistTop = textTop + titlePlaceable.height
                val controlsCenter = if (narrow) artistTop + artistPlaceable.height / 2 else height / 2
                val controlsTop = (controlsCenter - controls.height / 2).coerceIn(0, (height - controls.height).coerceAtLeast(0))

                layout(width, height) {
                    titlePlaceable.placeRelative(0, textTop)
                    artistPlaceable.placeRelative(0, artistTop)
                    albumPlaceable.placeRelative(0, artistTop + artistPlaceable.height)
                    controls.placeRelative(width - controls.width, controlsTop)
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = colors.outlineVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun PlaylistTrackThumbnail(track: FullTrackInfo, loadThumbnail: Boolean, displaySize: Dp) {
    val thumbnailSize = if (with(LocalDensity.current) { displaySize.roundToPx() } <= 120) 120 else 200
    val coverUri = track.coverUri?.takeIf { it.isNotBlank() }
        ?: track.realId?.takeIf(::isLocalTrackId)
        ?: track.id.takeIf(::isLocalTrackId)
    var bitmap by remember(coverUri, thumbnailSize) {
        mutableStateOf(CoverImageLoader.peek(coverUri, thumbnailSize))
    }
    LaunchedEffect(coverUri, thumbnailSize, loadThumbnail) {
        if (loadThumbnail && bitmap == null && coverUri != null) {
            bitmap = CoverImageLoader.loadTrackThumbnail(coverUri, thumbnailSize)
        }
    }
    Box(
        modifier = Modifier
            .size(displaySize)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val artwork = bitmap
        if (artwork != null) {
            Image(
                painter = remember(artwork) { BitmapPainter(artwork, filterQuality = FilterQuality.Medium) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            ContentTypeIcon(ContentType.ALBUM, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PlaylistTrackMenu(
    onAddToPlaylist: (() -> Unit)?,
    onQueue: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    extraMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)?,
) {
    if (onAddToPlaylist == null && onQueue == null && onRemove == null && extraMenuItems == null) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(44.dp).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Действия с треком", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            extraMenuItems?.invoke { expanded = false }
            if (onAddToPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Добавить в плейлист", maxLines = 1) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = { expanded = false; onAddToPlaylist() },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
            if (onQueue != null) {
                DropdownMenuItem(
                    text = { Text("В очередь", maxLines = 1) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    onClick = { expanded = false; onQueue() },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
            if (onRemove != null) {
                if (onAddToPlaylist != null || onQueue != null) HorizontalDivider()
                val error = MaterialTheme.colorScheme.error
                DropdownMenuItem(
                    text = { Text("Удалить трек…", color = error, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = error) },
                    onClick = { expanded = false; onRemove() },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
private fun ChartPositionBadge(chart: ChartPositionInfo) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "#${chart.position}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        val trend = when (chart.progress?.lowercase()) {
            "up" -> (if (chart.shift > 0) "↑${chart.shift}" else "↑") to
                if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
            "down" -> (if (chart.shift > 0) "↓${chart.shift}" else "↓") to MaterialTheme.colorScheme.error
            "new" -> "NEW" to MaterialTheme.colorScheme.primary
            else -> null
        }
        if (trend != null) {
            Text(
                text = trend.first,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = trend.second,
                maxLines = 1
            )
        }
    }
}
