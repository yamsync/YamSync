package io.github.audiz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.models.isLocalTrackId

@Composable
internal fun PlaylistTrackRow(
    track: FullTrackInfo,
    artists: String,
    loadThumbnail: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onActivate: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onQueue: (() -> Unit)?,
    onRemove: (() -> Unit)?,
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

    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isActive) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerHigh)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (isPlaying) "Пауза" else "Воспроизвести",
                    onClick = onActivate,
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)
                .heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistTrackThumbnail(track, loadThumbnail)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    AiArtistBadge(track.artists)
                }
                Text(
                    text = if (album.isBlank()) "Альбом: —" else "Альбом: $album",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (track.durationMs > 0L) {
                Text(
                    text = formatTime(track.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlaylistTrackMenu(onAddToPlaylist, onQueue, onRemove)
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
private fun PlaylistTrackThumbnail(track: FullTrackInfo, loadThumbnail: Boolean) {
    val coverUri = track.coverUri?.takeIf { it.isNotBlank() }
        ?: track.realId?.takeIf(::isLocalTrackId)
        ?: track.id.takeIf(::isLocalTrackId)
    var bitmap by remember(coverUri) {
        mutableStateOf(CoverImageLoader.peek(coverUri, CoverImageLoader.TRACK_THUMBNAIL_SIZE))
    }
    LaunchedEffect(coverUri, loadThumbnail) {
        if (loadThumbnail && bitmap == null && coverUri != null) {
            bitmap = CoverImageLoader.loadTrackThumbnail(coverUri)
        }
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val artwork = bitmap
        if (artwork != null) {
            Image(artwork, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
) {
    if (onAddToPlaylist == null && onQueue == null && onRemove == null) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(44.dp).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Действия с треком", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
