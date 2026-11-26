package io.github.audiz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.audiz.models.ChartPositionInfo
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.player.PlaybackQueueManager
import io.github.audiz.tracklist.TrackListScrollRequest

@Composable
private fun LazyListState.OnBottomReached(buffer: Int = 2, onLoadMore: () -> Unit) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            totalItemsNumber > 1 && lastVisibleItemIndex >= (totalItemsNumber - buffer)
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onLoadMore()
    }
}

private fun playlistTrackCountLabel(count: Int): String {
    val noun = when {
        count % 100 in 11..14 -> "треков"
        count % 10 == 1 -> "трек"
        count % 10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$count $noun"
}

@Composable
fun ArtistTracksList(
    tracks: List<FullTrackInfo>,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onBackClick: () -> Unit,
    onPlayTrack: (trackId: String, trackTitle: String, artistName: String) -> Unit,
    playingTrackId: String? = null,
    isPlaying: Boolean = false,
    isPaused: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    totalTracksCount: Int = tracks.size,
    isLoading: Boolean = false,
    isLoadingAllPages: Boolean = false,
    onLoadAllClick: () -> Unit = {},
    title: String = "Загружено треков",
    onRefresh: (() -> Unit)? = null,
    onAddToPlaylist: ((FullTrackInfo) -> Unit)? = null,
    onArtistClick: ((artistId: String, artistName: String) -> Unit)? = null,
    onStartArtistWave: ((artistId: String, artistName: String) -> Unit)? = null,
    scrollToTrackRequest: TrackListScrollRequest? = null,
    onScrollToTrackHandled: (TrackListScrollRequest) -> Unit = {},
    highlightedTrackId: String? = null,
    isPlaybackQueue: Boolean = true,
    chartPositions: Map<String, ChartPositionInfo> = emptyMap(),
    modifier: Modifier = Modifier,
    onQueuePlaylistTrack: ((FullTrackInfo) -> Unit)? = null,
    onManagePlaylistTrackRemoval: ((FullTrackInfo) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
) {
    val listState = rememberLazyListState()
    val visibleTrackIndices by listState.rememberVisiblePlaylistTrackIndices()
    listState.OnBottomReached { if (canLoadMore) onLoadMore() }

    val scrollTargetIndex = scrollToTrackRequest?.let { request ->
        tracks.indexOfFirst { PlaybackQueueManager.isSameTrack(it, request.trackId) }
    } ?: -1
    LaunchedEffect(scrollToTrackRequest, scrollTargetIndex, tracks.size, isLoading, canLoadMore) {
        val request = scrollToTrackRequest ?: return@LaunchedEffect
        when {
            scrollTargetIndex >= 0 -> {
                listState.scrollToItem(scrollTargetIndex)
                onScrollToTrackHandled(request)
            }
            isLoading -> Unit
            canLoadMore -> onLoadMore()
            else -> onScrollToTrackHandled(request)
        }
    }

    val commonArtist = remember(tracks, title) {
        if (tracks.isNotEmpty() && !title.startsWith("Треки:")) {
            val firstArtist = tracks.firstOrNull()?.artists?.firstOrNull()
            if (firstArtist != null && tracks.all { t -> t.artists.any { it.name.equals(firstArtist.name, ignoreCase = true) } }) {
                firstArtist
            } else null
        } else null
    }
    val waveArtist = remember(tracks, title, commonArtist) {
        commonArtist ?: if (title.startsWith("Треки:")) {
            tracks.firstOrNull()?.artists?.firstOrNull { it.id > 0L && it.name.isNotBlank() }
        } else null
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 8.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                size = 40.dp,
                outlined = true,
                onClick = onBackClick,
            )
            Spacer(modifier = Modifier.width(16.dp))
            val total = if (totalTracksCount > tracks.size) totalTracksCount else tracks.size
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isLoading && tracks.isEmpty() -> "Загрузка треков..."
                        isLoading && tracks.isNotEmpty() -> "Показано: ${tracks.size} (обновление...)"
                        tracks.isEmpty() -> "Нет треков"
                        total > tracks.size -> "Показано: ${tracks.size} из $total"
                        else -> playlistTrackCountLabel(total)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (commonArtist != null && onArtistClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TopBarIconButton(
                    icon = Icons.Filled.Person,
                    contentDescription = "Все треки: ${commonArtist.name}",
                    outlined = true,
                    onClick = { onArtistClick(commonArtist.id.toString(), commonArtist.name) }
                )
            }

            if (waveArtist != null && waveArtist.id > 0L && onStartArtistWave != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TopBarIconButton(
                    icon = Icons.Filled.Waves,
                    contentDescription = "Волна по артисту: ${waveArtist.name}",
                    outlined = true,
                    onClick = { onStartArtistWave(waveArtist.id.toString(), waveArtist.name) }
                )
            }

            if (onRefresh != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TopBarIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Обновить список",
                    outlined = true,
                    onClick = onRefresh
                )
            }

            if (canLoadMore && total > tracks.size) {
                Spacer(modifier = Modifier.width(8.dp))
                TopBarIconButton(
                    icon = Icons.Filled.UnfoldMore,
                    contentDescription = "Прогрузить весь список треков",
                    outlined = true,
                    isLoading = isLoadingAllPages,
                    onClick = onLoadAllClick
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val narrowRows = maxWidth < 360.dp
            if (tracks.isEmpty()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Загрузка треков...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Треков не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        val artistsString = track.artists.joinToString { it.name }
                        val isCurrentTrackActive = (isPlaying || isPaused) &&
                            PlaybackQueueManager.isSameTrack(track, playingTrackId)
                        val isCurrentTrackPlaying = isCurrentTrackActive && isPlaying && !isPaused && isPlaybackQueue
                        val isHighlighted = highlightedTrackId != null && PlaybackQueueManager.isSameTrack(track, highlightedTrackId)
                        val chartPos = chartPositions[track.id] ?: chartPositions[track.id.substringBefore(":")]
                        val loadThumbnail by remember(index, listState) {
                            derivedStateOf { index in visibleTrackIndices }
                        }
                        PlaylistTrackRow(
                            track = track,
                            artists = artistsString,
                            loadThumbnail = loadThumbnail,
                            isFirst = index == 0,
                            isLast = index == tracks.lastIndex,
                            isActive = isCurrentTrackActive || isHighlighted,
                            isPlaying = isCurrentTrackPlaying,
                            chartPosition = chartPos,
                            narrow = narrowRows,
                            onActivate = {
                                if (isCurrentTrackActive && isPlaybackQueue) onTogglePlayPause()
                                else onPlayTrack(track.id, track.title, artistsString)
                            },
                            onAddToPlaylist = onAddToPlaylist?.let { add -> { add(track) } },
                            onQueue = onQueuePlaylistTrack?.let { enqueue -> { enqueue(track) } },
                            onRemove = onManagePlaylistTrackRemoval?.let { remove -> { remove(track) } },
                        )
                    }
                    if (canLoadMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                PlatformScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    state = listState
                )
            }
        }
    }
}

