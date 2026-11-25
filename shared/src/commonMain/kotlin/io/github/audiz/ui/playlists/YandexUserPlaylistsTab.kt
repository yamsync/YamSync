package io.github.audiz.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.audiz.SearchViewModel
import io.github.audiz.models.PlaylistInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun YandexUserPlaylistsTab(
    viewModel: SearchViewModel,
    onOpenPlaylist: (PlaylistInfo) -> Unit,
    onRenameClick: (PlaylistInfo) -> Unit = {}
) {
    if (viewModel.currentAccessToken.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "🔑 Для доступа к плейлистам из Яндекс Музыки авторизуйтесь в Настройках ⚙️",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (viewModel.isUserPlaylistsLoading && viewModel.userPlaylists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
        return
    }

    if (viewModel.userPlaylists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "В вашем аккаунте Яндекс Музыки пока нет созданных плейлистов.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(viewModel.userPlaylists, key = { index, playlist -> "yandex_pl_${playlist.kind ?: playlist.uid}_$index" }) { index, playlist ->
            val kind = playlist.kind
            val playlistKey = playlist.kind?.toString() ?: playlist.playlistUuid ?: playlist.title
            val downloadProgress = viewModel.activePlaylistDownloadProgress
            val isThisDownloading = downloadProgress != null && downloadProgress.playlistId == playlistKey
            val isAnyDownloading = downloadProgress != null

            PlaylistCardSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Открыть плейлист") { onOpenPlaylist(playlist) }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlaylistTracksPill(trackCount = playlist.trackCount)

                                PlaylistSourceBadge(isOffline = false)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isThisDownloading) {
                                Box(
                                    modifier = Modifier.size(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.downloadYandexPlaylistToLocal(playlist) },
                                    enabled = !isAnyDownloading,
                                    modifier = Modifier.size(28.dp).then(
                                        if (!isAnyDownloading) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Скачать плейлист в оффлайн",
                                        tint = if (!isAnyDownloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (kind != null) {
                                PlaylistMoreMenu(
                                    onRename = { onRenameClick(playlist) }
                                )
                            }
                        }
                    }

                    if (downloadProgress != null && isThisDownloading) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val progressTitle = if (downloadProgress.totalCount > 0) {
                                        val skippedInfo = if (downloadProgress.skippedCount > 0) " (пропущено ${downloadProgress.skippedCount})" else ""
                                        "Скачивание: ${downloadProgress.downloadedCount}/${downloadProgress.totalCount}$skippedInfo"
                                    } else {
                                        "Подготовка списка треков..."
                                    }

                                    Text(
                                        text = progressTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { viewModel.cancelPlaylistDownload() },
                                        modifier = Modifier.size(24.dp).pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Отменить скачивание",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (downloadProgress.isIndeterminate || downloadProgress.totalCount <= 0) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    val fraction = (downloadProgress.downloadedCount + downloadProgress.skippedCount).toFloat() / downloadProgress.totalCount.toFloat()
                                    LinearProgressIndicator(
                                        progress = { fraction.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                if (downloadProgress.currentTrackTitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = downloadProgress.currentTrackTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
