@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.audiz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.audiz.SearchViewModel
import io.github.audiz.TracksListOrigin
import io.github.audiz.isPlatformPickerSupported
import io.github.audiz.launchDirectoryPicker
import io.github.audiz.models.LastPlaybackType
import io.github.audiz.models.LocalSourceType
import io.github.audiz.models.PersonalPlaylistInfo
import io.github.audiz.models.PersonalPlaylistItemData
import io.github.audiz.player.PlaybackQueueManager
import io.github.audiz.sync.YamSyncConnectionState

@Composable
internal fun HomeHub(
    viewModel: SearchViewModel,
    scrollState: ScrollState,
    onOpenMediaLibrary: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenCollections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(scrollState)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeWaveSection(viewModel)
            QuickAccessSection(viewModel, onOpenMediaLibrary, onOpenDevices)

            val lastSession = viewModel.playbackSessionManager.currentSession
            if (lastSession != null && lastSession.type != LastPlaybackType.WAVE && !viewModel.playerIsPlaying) {
                HomeListSurface {
                    HomeNavigationRow(
                        title = "Продолжить: ${lastSession.title ?: "Последний плейлист"}",
                        subtitle = lastSession.lastTrackTitle?.takeIf { it.isNotBlank() },
                        icon = Icons.Filled.History,
                        onClick = { viewModel.resumeLastSession(lastSession) },
                    )
                }
            }

            HomeListSurface {
                HomeNavigationRow(
                    "Подборки",
                    Icons.Filled.Layers,
                    iconTint = homeIconTint(Color(0xFFB9A6CA)),
                    onClick = onOpenCollections,
                )
            }

            if (viewModel.localPlaylists.isNotEmpty() || viewModel.customMediaSources.isNotEmpty() || isPlatformPickerSupported) {
                LocalLibrarySection(viewModel)
            }
        }
    }
}

@Composable
private fun HomeWaveSection(viewModel: SearchViewModel) {
    var showThemes by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accent = lerp(colors.primary, colors.onSurfaceVariant, 0.24f)
    val isCurrentTrackFromWave = viewModel.isWaveMode && !viewModel.playbackManager.isPlayingUpNextTrack &&
        viewModel.currentWaveTrack?.let { PlaybackQueueManager.isSameTrack(it, viewModel.playerTrackId) } == true
    val isWavePlaying = isCurrentTrackFromWave && viewModel.playerIsPlaying && !viewModel.playerIsPaused
    val isWavePaused = isCurrentTrackFromWave && viewModel.playerIsPaused
    val themeTitle = viewModel.currentWaveTitle?.takeIf { viewModel.currentWaveSeeds.isNotEmpty() && it.isNotBlank() }
        ?: "Персональная"
    val status = when {
        isWavePlaying -> "Сейчас играет"
        isWavePaused -> "На паузе"
        viewModel.isWaveLoading -> "Загрузка…"
        else -> "Тема волны"
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Waves, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                "Моя волна",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { showThemes = true }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Все темы", color = accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }

        HomeListSurface {
            Box {
                if (isWavePlaying || isWavePaused) {
                    Box(Modifier.matchParentSize()) {
                        Box(Modifier.fillMaxHeight().width(4.dp).background(accent.copy(alpha = if (isWavePlaying) 1f else 0.5f)))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                        Text(
                            themeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isWavePlaying) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    } else if (viewModel.isWaveLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                    }
                }
            }
        }

        val recentThemes = viewModel.recentThematicWaves.take(3)
        if (recentThemes.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                recentThemes.forEach { theme ->
                    val selected = viewModel.isWaveMode && viewModel.currentWaveSeeds == theme.seeds
                    Surface(
                        onClick = { viewModel.selectWaveTheme(theme) },
                        enabled = !viewModel.isWaveLoading,
                        modifier = Modifier.heightIn(min = 38.dp).pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) lerp(colors.surfaceContainerLow, accent, 0.12f) else colors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                theme.title,
                                color = if (selected) accent else colors.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showThemes) WaveThemesDialog(viewModel, onDismiss = { showThemes = false })
}

@Composable
private fun QuickAccessSection(
    viewModel: SearchViewModel,
    onOpenMediaLibrary: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val deviceStateColor = when (viewModel.yamSyncManager.connectionState) {
        is YamSyncConnectionState.Connected, is YamSyncConnectionState.Syncing -> colors.primary
        is YamSyncConnectionState.Hosting, is YamSyncConnectionState.Connecting -> colors.tertiary
        is YamSyncConnectionState.Disconnected -> colors.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Column {
        HomeSectionHeading("Быстрый доступ")
        HomeListSurface {
            HomeNavigationRow(
                "Скачанное", Icons.Filled.Download, iconTint = homeIconTint(Color(0xFF8DB9A5)),
                onClick = { viewModel.loadDownloadedTracksPlaylist() },
            )
            HomeListDivider()
            HomeNavigationRow(
                "Любимые", Icons.Filled.Favorite, iconTint = homeIconTint(Color(0xFFD2A0B0)),
                onClick = { viewModel.loadLikesPlaylist() },
            )
            HomeListDivider()
            HomeNavigationRow(
                "Медиатека", Icons.Filled.LibraryMusic, iconTint = homeIconTint(Color(0xFFACADD0)),
                onClick = onOpenMediaLibrary,
            )
            HomeListDivider()
            HomeNavigationRow(
                "История", Icons.Filled.History, iconTint = homeIconTint(Color(0xFFC4AC88)),
                onClick = { viewModel.loadHistory() },
            )
            HomeListDivider()
            HomeNavigationRow(
                "Устройства", Icons.Filled.Devices, iconTint = homeIconTint(Color(0xFF8EB7BA)),
                statusColor = deviceStateColor, onClick = onOpenDevices,
            )
        }
    }
}

@Composable
private fun homeIconTint(color: Color): Color {
    val colors = MaterialTheme.colorScheme
    val neutralWeight = if (colors.surface.luminance() < 0.5f) 0.22f else 0.68f
    return lerp(color, colors.onSurfaceVariant, neutralWeight)
}

@Composable
private fun LocalLibrarySection(viewModel: SearchViewModel) {
    val colors = MaterialTheme.colorScheme
    val folderTint = lerp(colors.onSurfaceVariant, colors.primary, 0.42f)
    val folderBackground = lerp(colors.surfaceContainerLow, colors.primary, 0.065f)
    Column {
        HomeSectionHeading("Папки и плейлисты")
        HomeListSurface {
            viewModel.customMediaSources.forEachIndexed { index, source ->
                key(source.type, source.id) {
                    if (index > 0) HomeListDivider()
                    val isFolder = source.type == LocalSourceType.FOLDER
                    HomeNavigationRow(
                        title = source.name,
                        icon = when (source.type) {
                            LocalSourceType.FOLDER -> Icons.Filled.Folder
                            LocalSourceType.PLAYLIST_FILE -> Icons.AutoMirrored.Filled.QueueMusic
                            LocalSourceType.SINGLE_FILE -> Icons.Filled.MusicNote
                        },
                        count = source.trackCount.takeIf { source.type == LocalSourceType.PLAYLIST_FILE },
                        iconTint = if (isFolder) folderTint else colors.onSurfaceVariant,
                        background = if (isFolder) folderBackground else Color.Transparent,
                        onClick = {
                            if (isFolder) {
                                viewModel.openFolderPlaylist(
                                    source.path, source.name, source, isRecursive = true, origin = TracksListOrigin.HOME,
                                )
                            } else {
                                viewModel.openCustomSource(source)
                            }
                        },
                    )
                }
            }
            viewModel.localPlaylists.forEachIndexed { index, playlist ->
                key(playlist.id) {
                    if (index > 0 || viewModel.customMediaSources.isNotEmpty()) HomeListDivider()
                    HomeNavigationRow(
                        title = playlist.title,
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        count = playlist.trackCount,
                        onClick = { viewModel.openLocalPlaylist(playlist) },
                    )
                }
            }
            if (isPlatformPickerSupported) {
                if (viewModel.customMediaSources.isNotEmpty() || viewModel.localPlaylists.isNotEmpty()) HomeListDivider()
                HomeNavigationRow(
                    title = "Добавить папку",
                    icon = Icons.Filled.Add,
                    onClick = {
                        launchDirectoryPicker { path ->
                            if (!path.isNullOrBlank()) viewModel.addCustomSource(path)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HomeListSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(content = content)
    }
}

@Composable
private fun HomeListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 39.dp, end = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
    )
}

@Composable
private fun HomeNavigationRow(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    count: Int? = null,
    statusColor: Color? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .heightIn(min = 39.dp)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        count?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        statusColor?.let { Box(Modifier.size(6.dp).background(it, CircleShape)) }
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun PersonalCollectionsScreen(
    items: List<PersonalPlaylistItemData>,
    isLoading: Boolean,
    gridState: LazyGridState,
    onBack: () -> Unit,
    onSelect: (PersonalPlaylistInfo, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collections = items.mapNotNull { item ->
        item.playlist?.takeIf { it.canOpen }?.let { it to item.displayTitle }
    }.distinctBy { it.first.stableKey }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val columns = if (maxWidth < 540.dp) GridCells.Fixed(2) else GridCells.Adaptive(150.dp)
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text("Подборки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            if (collections.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Text("Подборок пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(collections, key = { it.first.stableKey }) { (playlist, title) ->
                        PersonalCollectionCard(title, playlist.cover?.uri) { onSelect(playlist, title) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalCollectionCard(title: String, coverUri: String?, onClick: () -> Unit) {
    // Playlist cover URLs accept named dimensions such as 200x200.
    val cover = rememberCoverBitmap(coverUri, size = 200)
    Column(
        Modifier.clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp))
            }
        }
        Text(title, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
