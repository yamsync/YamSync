package io.github.audiz.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.audiz.DispatcherIO
import io.github.audiz.SearchViewModel
import io.github.audiz.localFileExists
import io.github.audiz.models.CustomMediaSource
import io.github.audiz.models.LocalPlaylist
import io.github.audiz.models.LocalSourceType
import io.github.audiz.resolveLocalPath
import kotlinx.coroutines.CancellationException

/**
 * 💾 Вкладка 1: Локальные источники (папки, файлы, M3U) и оффлайн-плейлисты
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalPlaylistsTab(
    viewModel: SearchViewModel,
    onAddSourceClick: () -> Unit,
    onOpenSource: (CustomMediaSource) -> Unit,
    onBrowseFolder: (CustomMediaSource) -> Unit,
    onCreateClick: () -> Unit,
    onRenameClick: (LocalPlaylist) -> Unit,
    onDeleteClick: (LocalPlaylist) -> Unit,
    onOpenPlaylist: (LocalPlaylist) -> Unit,
    onOpenSync: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==========================================
        // 📁 СЕКЦИЯ 1: Папки и файлы устройства
        // ==========================================
        item(key = "custom_sources_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Папки и файлы устройства",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${viewModel.customMediaSources.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Button(
                    onClick = onAddSourceClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.wrapContentWidth().pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }
        }

        if (viewModel.customMediaSources.isEmpty()) {
            item(key = "custom_sources_empty") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Своя музыка с устройства",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Добавьте папки, отдельные треки или плейлисты M3U для оффлайн-прослушивания в плеере.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(viewModel.customMediaSources, key = { index, source -> "source_${source.id}_$index" }) { index, source ->
                CustomSourceCard(
                    source = source,
                    onOpen = { onOpenSource(source) },
                    onBrowse = if (source.type == LocalSourceType.FOLDER) { { onBrowseFolder(source) } } else null,
                    onDelete = { viewModel.removeCustomSource(source.id) },
                    compactActions = !viewModel.isMobileUi
                )
            }
        }

        // ==========================================
        // 💾 СЕКЦИЯ 2: Оффлайн-плейлисты
        // ==========================================
        item(key = "local_playlists_header") {
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Оффлайн-плейлисты",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${viewModel.localPlaylists.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Создать", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
            }
        }

        if (viewModel.localPlaylists.isEmpty()) {
            item(key = "local_playlists_empty") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Нет созданных оффлайн-плейлистов",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Создайте плейлист и добавляйте в него скачанные треки.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(viewModel.localPlaylists, key = { index, playlist -> "playlist_${playlist.id}_$index" }) { index, playlist ->
                // Проверка наличия файлов и путей к обложкам — вне UI-потока
                val availability = produceState(initialValue = null as Int?, playlist.trackPaths) {
                    value = withContext(DispatcherIO) {
                        try {
                            playlist.trackPaths.count { path ->
                                localFileExists(resolveLocalPath(path)) || run {
                                    val fn = path.substringAfterLast('/').substringAfterLast('\\')
                                    fn.isNotBlank() && localFileExists(resolveLocalPath(fn))
                                }
                            }
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            0
                        }
                    }
                }.value
                val availableCount = availability
                val isFullyAvailable = availableCount != null && availableCount == playlist.trackCount
                val isNoneAvailable = availableCount == 0 && playlist.trackCount > 0

                PlaylistCardSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Открыть плейлист") { onOpenPlaylist(playlist) }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Верхняя строка: Название и бейджи слева + вертикально отцентрированные действия справа
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

                                    PlaylistSourceBadge(isOffline = true)

                                    if (availableCount != null && !isFullyAvailable) {
                                        val missingText = if (isNoneAvailable) "нет файлов" else "$availableCount/${playlist.trackCount}"
                                        Text(
                                            text = "• $missingText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Правый ряд действий: [YamSync (если нужен)] [⋮ Меню]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (availableCount != null && !isFullyAvailable && onOpenSync != null) {
                                    IconButton(
                                        onClick = {
                                            onDismiss()
                                            onOpenSync()
                                        },
                                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = "Синхронизировать YamSync",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                PlaylistMoreMenu(
                                    onRename = { onRenameClick(playlist) },
                                    onExportM3u = { viewModel.exportLocalPlaylist(playlist.id) },
                                    onDelete = { onDeleteClick(playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
