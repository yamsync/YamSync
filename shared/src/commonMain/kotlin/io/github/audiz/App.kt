package io.github.audiz

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.text.font.FontWeight
import io.github.audiz.ui.AppDialog
import io.github.audiz.ui.rememberAppNavigationState
import io.github.audiz.ui.SearchInputField
import io.github.audiz.ui.SearchResultsList
import io.github.audiz.ui.ArtistTracksList
import io.github.audiz.ui.HomeHub
import io.github.audiz.ui.CollectionsShowcaseScreen
import io.github.audiz.ui.PlaylistsDialog
import io.github.audiz.ui.UpNextDialog
import io.github.audiz.ui.AddToPlaylistDialog
import io.github.audiz.ui.LogsDialog
import io.github.audiz.addons.AiFilterMode
import io.github.audiz.ui.LocalAiBlacklist
import io.github.audiz.ui.PlayerBar
import io.github.audiz.player.PlayerActions
import io.github.audiz.ui.SettingsDialog
import io.github.audiz.ui.SettingsState
import io.github.audiz.ui.SettingsActions
import io.github.audiz.ui.AppAccentColor
import io.github.audiz.ui.buildAppColorScheme
import io.github.audiz.ui.EqualizerDialog
import io.github.audiz.ui.CarPlayerLayout
import io.github.audiz.ui.MobileFullPlayerSheet
import io.github.audiz.ui.clipAboveMiniPlayer
import io.github.audiz.ui.PlayerTrackRemovalDialog
import io.github.audiz.ui.WaveThemesDialog
import io.github.audiz.ui.BackHandler
import io.github.audiz.dsp.EqualizerEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App() {
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel() }

    val messageHost = remember { SnackbarHostState() }
    LaunchedEffect(searchViewModel) {
        searchViewModel.messages.collectLatest { message ->
            messageHost.currentSnackbarData?.dismiss()
            messageHost.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    val supportScope = rememberCoroutineScope()
    val starPromptManager = remember { io.github.audiz.support.StarPromptManager() }

    // 🔄 Фоновая проверка новой версии (ошибки сети игнорируются)
    val updateManager = remember { io.github.audiz.update.UpdateManager() }
    LaunchedEffect(updateManager.nextCheckAt) {
        updateManager.nextCheckAt?.let { scheduledAt ->
            delay((scheduledAt - currentTimeMillis()).coerceAtLeast(0L))
        }
        updateManager.checkForUpdates()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        supportScope.launch { updateManager.checkForUpdates() }
    }

    LaunchedEffect(Unit) {
        DeepLinkHandler.pendingTrackId.collect { trackId ->
            if (trackId != null) {
                DeepLinkHandler.consumeTrackId()
                searchViewModel.playTrackByExternalId(trackId)
            }
        }
    }
    
    val isDarkTheme = when (searchViewModel.appTheme) {
        "Light" -> false
        "Dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    val selectedAccent = remember(searchViewModel.accentColor) {
        AppAccentColor.fromKey(searchViewModel.accentColor)
    }

    val colors = remember(isDarkTheme, selectedAccent) {
        buildAppColorScheme(isDarkTheme, selectedAccent)
    }

    CompositionLocalProvider(
        LocalAiBlacklist provides searchViewModel.aiBlacklist.takeIf {
            searchViewModel.aiFilterMode != AiFilterMode.OFF
        }
    ) {
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {


            // Открытый для просмотра список остаётся видимым, даже если в нём нет треков.
            val showTracksScreen = searchViewModel.isTracksListVisible &&
                (searchViewModel.isPreviewingTrackList || searchViewModel.loadedTracks.isNotEmpty() || searchViewModel.isLoading)
            val isSearchVisible = searchViewModel.searchResult != null || searchViewModel.activeSearchQuery != null
            val nav = rememberAppNavigationState()
            var appTopInRoot by remember { mutableFloatStateOf(0f) }
            var miniPlayerTopInRoot by remember { mutableStateOf<Float?>(null) }
            val homeScrollState = rememberScrollState()
            val collectionsGridState = rememberLazyGridState()

            var dialog by remember { mutableStateOf<AppDialog?>(null) }
            // Не входит в [dialog]: открывается и по входящей ссылке поверх любого открытого окна.
            var showYamSyncDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                DeepLinkHandler.pendingYamSyncUri.collect { uri ->
                    if (uri != null) {
                        DeepLinkHandler.consumeYamSyncUri()
                        searchViewModel.yamSyncManager.connectToPeer(uri)
                        showYamSyncDialog = true
                    }
                }
            }
            val eqState by EqualizerEngine.state.collectAsState()
            val isCarUi = searchViewModel.uiMode == "car"
            val isMobileUi = searchViewModel.isMobileUi
            var isCarPanelOpen by remember(isCarUi) { mutableStateOf(false) }

            val playerState = searchViewModel.playerUiState(showWavePreview = !showTracksScreen).copy(
                isConfigActive = nav.settingsExpanded,
                isEqualizerActive = eqState.isEnabled,
                isQueueOpen = dialog is AppDialog.UpNext || (showTracksScreen && !searchViewModel.isPreviewingTrackList),
            )

            val openPlayingPlaylist: () -> Unit = {
                val upNext = dialog as? AppDialog.UpNext
                val fromPlayer = upNext?.openedFromPlayer ?: nav.isMobilePlayerExpanded
                if (upNext != null) dialog = null
                if (isCarUi) {
                    searchViewModel.openQueueView()
                    isCarPanelOpen = true
                } else if (isMobileUi && fromPlayer) {
                    searchViewModel.openQueueView(TracksListOrigin.MOBILE_PLAYER)
                    nav.updateMobilePlayerExpanded(false)
                } else {
                    searchViewModel.openQueueView(TracksListOrigin.HOME)
                }
            }

            val playerActions = PlayerActions(
                onVolumeChange = { searchViewModel.changeVolume(it) },
                onPlayPause = { searchViewModel.togglePlayPause() },
                onSeek = { searchViewModel.seekPlayer(it) },
                onToggleShuffle = { searchViewModel.toggleShuffle() },
                onNext = { searchViewModel.playNextTrack() },
                onPrev = { searchViewModel.playPrevTrack() },
                onConfigClick = { nav.settingsExpanded = !nav.settingsExpanded },
                onSaveOrManageTrack = {
                    searchViewModel.saveOrManagePlayerTrack { track ->
                        dialog = AppDialog.PlayerTrackRemoval(track)
                    }
                },
                onShareTrack = searchViewModel::shareCurrentTrack,
                onAddToPlaylist = {
                    searchViewModel.playerActionTrack()?.let { dialog = AppDialog.AddToPlaylist(it) }
                },
                onToggleFavorite = if (playerState.hasTrackCandidate && !playerState.isLocalTrack) {
                    searchViewModel::toggleLikeCurrentTrack
                } else null,
                onManageTrackRemoval = {
                    searchViewModel.playerActionTrack()?.let { dialog = AppDialog.PlayerTrackRemoval(it) }
                },
                onOpenEqualizer = {
                    dialog = AppDialog.Equalizer(initialTab = 0)
                },
                onOpenPlayingPlaylist = openPlayingPlaylist.takeIf { searchViewModel.hasPlaybackQueue },
                onOpenQueue = {
                    if (searchViewModel.upNextState.pending.isEmpty()) {
                        openPlayingPlaylist()
                    } else {
                        dialog = AppDialog.UpNext(openedFromPlayer = isMobileUi && nav.isMobilePlayerExpanded)
                    }
                },
                onOpenArtist = if (playerState.artistName.isNotBlank()) {
                    {
                        searchViewModel.openCurrentTrackArtist {
                            nav.updateMobilePlayerExpanded(false)
                        }
                    }
                } else null,
                onExpandMobilePlayer = { nav.updateMobilePlayerExpanded(true) },
                onResetWave = { searchViewModel.resetToDefaultWave() },
                onOpenWaveThemes = { dialog = AppDialog.WaveThemes },
                onMobilePlayerDrag = { top ->
                    nav.isDraggingMobilePlayerOpen = true
                    nav.mobilePlayerDragTop = top?.minus(appTopInRoot)
                },
                onMobilePlayerDragEnd = { open ->
                    if (open) nav.updateMobilePlayerExpanded(true)
                    nav.isDraggingMobilePlayerOpen = false
                }
            )

            val mainContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = if (showTracksScreen) 8.dp else 16.dp)
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!showTracksScreen) {
                            BackHandler(enabled = isSearchVisible || nav.showCollections) {
                                if (isSearchVisible) searchViewModel.clearSearch() else nav.showCollections = false
                            }
                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SearchInputField(
                                            query = searchViewModel.searchQuery,
                                            onQueryChange = {
                                                searchViewModel.searchQuery = it
                                                if (it.isBlank()) {
                                                    searchViewModel.clearSearch()
                                                }
                                            },
                                            onSearchClick = { searchViewModel.performSearch() },
                                            onClear = { searchViewModel.clearSearch() },
                                            isLoading = searchViewModel.activeSearchQuery == searchViewModel.searchQuery.trim(),
                                            onSettingsClick = { nav.settingsExpanded = true },
                                            loadSuggestions = searchViewModel::getSearchSuggestions,
                                            onSuggestionClick = searchViewModel::selectSearchSuggestion
                                        )
                                    }

                                    io.github.audiz.ui.sync.YamSyncPill(
                                        syncManager = searchViewModel.yamSyncManager,
                                        isCompact = isMobileUi,
                                        onOpenSyncDialog = { showYamSyncDialog = true }
                                    )
                                }

                            AnimatedVisibility(
                                visible = !isSearchVisible,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(Modifier.padding(top = 12.dp)) {
                                    if (nav.showCollections) {
                                        CollectionsShowcaseScreen(
                                            personalPlaylists = searchViewModel.personalPlaylists,
                                            charts = searchViewModel.editorialCharts,
                                            editorialPlaylists = searchViewModel.editorialPlaylists,
                                            newReleases = searchViewModel.editorialNewReleases,
                                            isLoading = searchViewModel.isPersonalPlaylistsLoading || searchViewModel.isEditorialShowcaseLoading,
                                            gridState = collectionsGridState,
                                            onBack = { nav.showCollections = false },
                                            onRefresh = {
                                                searchViewModel.loadPersonalPlaylists()
                                                searchViewModel.loadEditorialShowcase(forceRefresh = true)
                                            },
                                            onSelectPersonal = { playlist, title ->
                                                if (playlist.playlistUuid.isNotBlank()) {
                                                    searchViewModel.loadPlaylistByUuid(playlist.playlistUuid, title, origin = TracksListOrigin.COLLECTIONS)
                                                } else {
                                                    searchViewModel.loadPlaylistTracks(playlist.uid, playlist.kind, title, origin = TracksListOrigin.COLLECTIONS)
                                                }
                                            },
                                            onSelectChart = { chart ->
                                                searchViewModel.loadChart(chart, origin = TracksListOrigin.COLLECTIONS)
                                            },
                                            onSelectPlaylist = { playlist ->
                                                searchViewModel.loadPlaylist(playlist, origin = TracksListOrigin.COLLECTIONS)
                                            },
                                            onSelectRelease = { release ->
                                                searchViewModel.loadAlbumTracks(release.id, release.title, origin = TracksListOrigin.COLLECTIONS)
                                            },
                                        )
                                    } else {
                                        updateManager.availableUpdate?.let { update ->
                                            io.github.audiz.ui.UpdateBanner(
                                                update = update,
                                                onDismiss = { updateManager.dismiss() },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        HomeHub(
                                            viewModel = searchViewModel,
                                            scrollState = homeScrollState,
                                            onOpenMediaLibrary = {
                                                searchViewModel.resetBrowsedFolder()
                                                dialog = AppDialog.MediaLibrary
                                            },
                                            onOpenDevices = { showYamSyncDialog = true },
                                            onOpenCollections = {
                                                nav.showCollections = true
                                                if (searchViewModel.editorialCharts.isEmpty() &&
                                                    searchViewModel.editorialPlaylists.isEmpty() &&
                                                    searchViewModel.editorialNewReleases.isEmpty()
                                                ) {
                                                    searchViewModel.loadEditorialShowcase()
                                                }
                                                if (searchViewModel.personalPlaylists.isEmpty() &&
                                                    searchViewModel.currentAccessToken.isNotBlank() &&
                                                    !searchViewModel.isPersonalPlaylistsLoading
                                                ) {
                                                    searchViewModel.loadPersonalPlaylists()
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            searchViewModel.errorMessage?.let { error ->
                                val displayText = if (error.startsWith("⚠️")) error else "⚠️ Ошибка: $error"
                                val isMissingFilesError = error.contains("YamSync", ignoreCase = true) ||
                                    error.contains("не найдены на устройстве", ignoreCase = true) ||
                                    error.contains("не скачаны", ignoreCase = true)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = displayText,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f).clickable { dialog = AppDialog.Logs }
                                            )
                                            IconButton(
                                                onClick = { searchViewModel.errorMessage = null },
                                                modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Закрыть",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📋 Нажмите на текст, чтобы открыть логи",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                modifier = Modifier.clickable { dialog = AppDialog.Logs }
                                            )
                                            if (isMissingFilesError) {
                                                TextButton(
                                                    onClick = {
                                                        searchViewModel.errorMessage = null
                                                        showYamSyncDialog = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("🔄 Открыть YamSync", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            searchViewModel.searchResult?.let { response ->
                                SearchResultsList(
                                    result = response,
                                    onArtistClick = { artistId ->
                                        val artistName = response.result?.results?.firstOrNull { it.artist?.id == artistId }?.artist?.name
                                        searchViewModel.loadArtistTracks(artistId, artistName)
                                    },
                                    onPlaylistClick = { playlist ->
                                        searchViewModel.loadPlaylist(playlist)
                                    },
                                    onAlbumClick = { albumId, albumTitle ->
                                        searchViewModel.loadAlbumTracks(albumId, albumTitle)
                                    },
                                    isTrackDownloading = searchViewModel.isTrackDownloading,
                                    downloadingTrackId = searchViewModel.downloadingTrackId,
                                    onDownloadTrack = { trackId, trackTitle, artistName ->
                                        searchViewModel.downloadTrack(trackId, trackTitle, artistName)
                                    },
                                    onPlayTrack = { trackId, trackTitle, artistName ->
                                        searchViewModel.playTrack(trackId, trackTitle, artistName)
                                    },
                                    onWaveClick = { wave ->
                                        searchViewModel.startThematicWave(wave.title, wave.seeds)
                                    },
                                    playingTrackId = searchViewModel.playerTrackId,
                                    isPlaying = searchViewModel.playerIsPlaying,
                                    isPaused = searchViewModel.playerIsPaused,
                                    playbackPositionMs = searchViewModel.playerPositionMs,
                                    playbackDurationMs = searchViewModel.playerDurationMs,
                                    onTogglePlayPause = { searchViewModel.togglePlayPause() },
                                    onStopPlayback = { searchViewModel.stopPlayback() },
                                    onSeek = { searchViewModel.seekPlayer(it) },
                                    isTrackDownloaded = { artist, title -> searchViewModel.isTrackDownloaded(artist, title) },
                                    isTrackInPlaylist = { searchViewModel.isTrackInAnyPlaylist(it) },
                                    onDeleteTrack = { trackId, trackTitle, artistName ->
                                        dialog = AppDialog.DeleteTrack(trackId, trackTitle, artistName)
                                    },
                                    onAddToPlaylist = { track -> dialog = AppDialog.AddToPlaylist(track) }
                                )
                            }
                        }
                    } else {
                        val isBackGoingToFolderBrowser = searchViewModel.tracksListOrigin == TracksListOrigin.FOLDER_BROWSER && searchViewModel.activeBrowsedFolderSource != null
                        val isBackGoingToMobilePlayer = isMobileUi && searchViewModel.tracksListOrigin == TracksListOrigin.MOBILE_PLAYER

                        val isBackGoingToMediaLibrary = searchViewModel.tracksListOrigin == TracksListOrigin.MEDIA_LIBRARY

                        val goBackFromTracks: () -> Unit = {
                            when {
                                isBackGoingToFolderBrowser -> {
                                    val targetSource = searchViewModel.activeBrowsedFolderSource
                                    val targetPath = searchViewModel.activeBrowsedFolderPath
                                    searchViewModel.closeArtistTracks()
                                    searchViewModel.activeBrowsedFolderSource = targetSource
                                    searchViewModel.activeBrowsedFolderPath = targetPath
                                    dialog = AppDialog.MediaLibrary
                                }
                                isBackGoingToMediaLibrary -> {
                                    searchViewModel.closeArtistTracks()
                                    dialog = AppDialog.MediaLibrary
                                }
                                isBackGoingToMobilePlayer -> {
                                    searchViewModel.closeArtistTracks()
                                    nav.updateMobilePlayerExpanded(true)
                                }
                                else -> searchViewModel.closeArtistTracks()
                            }
                        }
                        BackHandler(enabled = showTracksScreen, onBack = goBackFromTracks)

                        ArtistTracksList(
                            onQueuePlaylistTrack = { track -> searchViewModel.addToUpNext(track) },
                            onManagePlaylistTrackRemoval = { track -> dialog = AppDialog.PlayerTrackRemoval(track) },
                            isPlaybackQueue = !searchViewModel.isPreviewingTrackList,
                            chartPositions = searchViewModel.chartTrackPositions,
                            scrollToTrackRequest = searchViewModel.queueScrollRequest,
                            onScrollToTrackHandled = searchViewModel::consumeQueueScrollRequest,
                            highlightedTrackId = searchViewModel.highlightedTrackId,
                            tracks = searchViewModel.loadedTracks,
                            canLoadMore = searchViewModel.canLoadMore,
                            onLoadMore = { searchViewModel.loadNextPage() },
                            onBackClick = goBackFromTracks,
                            onPlayTrack = { trackId, trackTitle, artistName ->
                                searchViewModel.playTrackFromDisplayedList(trackId, trackTitle, artistName)
                            },
                            playingTrackId = searchViewModel.playerTrackId,
                            isPlaying = searchViewModel.playerIsPlaying,
                            isPaused = searchViewModel.playerIsPaused,
                            onTogglePlayPause = { searchViewModel.togglePlayPause() },
                            totalTracksCount = searchViewModel.totalTracksCount,
                            isLoading = searchViewModel.isLoading,
                            isLoadingAllPages = searchViewModel.isLoadingAllPages,
                            onLoadAllClick = { searchViewModel.loadAllRemainingPages() },
                            title = searchViewModel.currentScreenTitle,
                            onRefresh = if (searchViewModel.isDownloadedTracksScreen) {
                                {
                                    val currentLocal = searchViewModel.localPlaylists.firstOrNull { it.title == searchViewModel.currentScreenTitle }
                                    if (currentLocal != null) {
                                        searchViewModel.openLocalPlaylist(currentLocal)
                                    } else if (searchViewModel.activeQueueFolderPath != null) {
                                        val session = searchViewModel.playbackSessionManager.currentSession
                                        val isRec = session?.isRecursive ?: true
                                        searchViewModel.openFolderPlaylist(
                                            folderPath = searchViewModel.activeQueueFolderPath!!,
                                            folderName = searchViewModel.activeQueueFolderName ?: searchViewModel.currentScreenTitle,
                                            source = searchViewModel.activeQueueSource,
                                            origin = searchViewModel.tracksListOrigin,
                                            isRecursive = isRec
                                        )
                                    } else {
                                        searchViewModel.loadDownloadedTracksPlaylist()
                                    }
                                }
                            } else null,
                            onAddToPlaylist = { track -> dialog = AppDialog.AddToPlaylist(track) },
                            onArtistClick = { artistId, artistName ->
                                searchViewModel.loadArtistTracks(artistId, artistName)
                            },
                            onStartArtistWave = { artistId, artistName ->
                                searchViewModel.startArtistWave(artistId, artistName)
                            }
                        )
                    }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { appTopInRoot = it.positionInRoot().y }) {
            Scaffold(
                snackbarHost = { SnackbarHost(messageHost) },
                bottomBar = {
                    if (!isCarUi) Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isMobileUi) 10.dp else 16.dp)
                            .padding(bottom = if (isMobileUi) 6.dp else 16.dp)
                    ) {
                        io.github.audiz.support.StarSupportPrompt(
                            manager = starPromptManager,
                            // Окна «Далее» и удаления трека из плеера подсказку не скрывают.
                            canShow = !nav.settingsExpanded &&
                                (dialog == null || dialog is AppDialog.UpNext || dialog is AppDialog.PlayerTrackRemoval) &&
                                !showYamSyncDialog && !(isMobileUi && nav.isMobilePlayerExpanded) &&
                                searchViewModel.errorMessage == null && !searchViewModel.isLoading &&
                                !searchViewModel.isPlayTrackLoading,
                            isPlaying = searchViewModel.playerIsPlaying && !searchViewModel.playerIsPaused &&
                                !searchViewModel.isPlayTrackLoading,
                            trackId = searchViewModel.playerTrackId,
                            isLiked = searchViewModel.isCurrentTrackLiked,
                            isSaved = playerState.isLocalTrack || playerState.isSavedToDisk
                        )
                        PlayerBar(
                            state = playerState.copy(currentPositionMs = searchViewModel.playerPositionMs),
                            actions = playerActions,
                            modifier = if (isMobileUi) {
                                Modifier.onGloballyPositioned { miniPlayerTopInRoot = it.positionInRoot().y }
                            } else Modifier,
                        )
                    }
                }
            ) { paddingValues ->
                if (isCarUi) {
                    CarPlayerLayout(
                        state = playerState.copy(currentPositionMs = searchViewModel.playerPositionMs),
                        actions = playerActions,
                        isPanelOpen = isCarPanelOpen,
                        onPanelOpenChange = { isCarPanelOpen = it },
                        modifier = Modifier.padding(paddingValues),
                        content = mainContent,
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(paddingValues)) { mainContent() }
                }
            }

            if (isMobileUi && !isCarUi) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                    val openingProgress = remember {
                        Animatable(if (nav.isMobilePlayerExpanded) 1f else 0f)
                    }

                    LaunchedEffect(nav.isMobilePlayerExpanded, nav.isDraggingMobilePlayerOpen, heightPx) {
                        if (!nav.isDraggingMobilePlayerOpen) {
                            val draggedTop = nav.mobilePlayerDragTop
                            if (draggedTop != null && heightPx > 0f) {
                                openingProgress.snapTo((1f - draggedTop / heightPx).coerceIn(0f, 1f))
                                nav.mobilePlayerDragTop = null
                            }
                            openingProgress.animateTo(
                                targetValue = if (nav.isMobilePlayerExpanded) 1f else 0f,
                                animationSpec = if (nav.isMobilePlayerExpanded) {
                                    tween(280, easing = FastOutSlowInEasing)
                                } else {
                                    tween(250, easing = FastOutLinearInEasing)
                                }
                            )
                        }
                    }

                    val showFullPlayer by remember {
                        derivedStateOf {
                            nav.isMobilePlayerExpanded || nav.mobilePlayerDragTop != null || openingProgress.value > 0f
                        }
                    }
                    if (showFullPlayer) {
                        val miniPlayerTop = miniPlayerTopInRoot?.minus(appTopInRoot)
                        val overlayModifier = if (!nav.isMobilePlayerExpanded &&
                            !nav.isDraggingMobilePlayerOpen && miniPlayerTop != null
                        ) Modifier.clipAboveMiniPlayer(miniPlayerTop) else Modifier
                        Box(Modifier.fillMaxSize().then(overlayModifier)) {
                            MobileFullPlayerSheet(
                                state = playerState.copy(currentPositionMs = searchViewModel.playerPositionMs),
                                actions = playerActions,
                                onDismiss = { nav.updateMobilePlayerExpanded(false) },
                                modifier = Modifier.offset {
                                    val top = nav.mobilePlayerDragTop ?: if (nav.isDraggingMobilePlayerOpen) {
                                        heightPx
                                    } else {
                                        (1f - openingProgress.value) * heightPx
                                    }
                                    IntOffset(0, top.coerceIn(0f, heightPx).roundToInt())
                                },
                                backHandlerEnabled = nav.isMobilePlayerExpanded
                            )
                        }
                    }
                }
            }

            SettingsDialog(
                visible = nav.settingsExpanded,
                state = SettingsState(
                    isRecordToDiskActive = searchViewModel.isRecordToDiskActive,
                    selectedQuality = searchViewModel.selectedQuality,
                    appTheme = searchViewModel.appTheme,
                    accentColor = searchViewModel.accentColor,
                    musicStoragePath = searchViewModel.musicStoragePath,
                    currentAccessToken = searchViewModel.currentAccessToken,
                    authStatusMessage = searchViewModel.authStatusMessage,
                    isFetchingYnison = searchViewModel.isFetchingYnison,
                    ynisonWaveSessionId = searchViewModel.ynisonWaveSessionId,
                    ynisonHost = searchViewModel.ynisonHost,
                    ynisonError = searchViewModel.ynisonError,
                    storageStatusMessage = searchViewModel.storageStatusMessage,
                    uiMode = searchViewModel.uiMode,
                    crossfadeSeconds = searchViewModel.playerCrossfadeSeconds,
                    isUiWatchdogActive = searchViewModel.isUiWatchdogActive,
                    hasPlusSubscription = searchViewModel.hasPlusSubscription,
                    aiFilterMode = searchViewModel.aiFilterMode,
                    aiBlacklistVersion = searchViewModel.aiBlacklist?.version,
                    waveLoopProtectionEnabled = searchViewModel.waveLoopProtectionEnabled,
                    waveLoopHistoryLimit = searchViewModel.waveLoopHistoryLimit,
                ),
                actions = SettingsActions(
                    onClose = { nav.settingsExpanded = false },
                    onToggleRecordToDisk = { searchViewModel.toggleRecordToDisk(it) },
                    onSaveQuality = { searchViewModel.saveQuality(it) },
                    onSaveTheme = { searchViewModel.saveTheme(it) },
                    onSaveAccentColor = { searchViewModel.saveAccentColor(it) },
                    onSaveMusicPath = { searchViewModel.saveMusicPath(it) },
                    onSaveToken = { token, onComplete -> searchViewModel.saveNewToken(token, onComplete) },
                    onClearAuthStatus = { searchViewModel.authStatusMessage = null },
                    onFetchYnisonSession = { searchViewModel.fetchYnisonSession() },
                    onClearPlaylistsCache = { searchViewModel.clearPlaylistsCache() },
                    onClearAllDownloadedMusic = { searchViewModel.clearAllDownloadedMusic() },
                    onOpenEqualizer = { tab ->
                        nav.settingsExpanded = false
                        dialog = AppDialog.Equalizer(initialTab = tab)
                    },
                    onOpenLogs = {
                        nav.settingsExpanded = false
                        dialog = AppDialog.Logs
                    },
                    onSaveUiMode = { searchViewModel.saveUiMode(it) },
                    onSaveCrossfade = { searchViewModel.changeCrossfade(it) },
                    onToggleUiWatchdog = { searchViewModel.toggleUiWatchdog(it) },
                    onRefreshPlusSubscription = { searchViewModel.refreshPlusSubscription() },
                    onProjectOpened = { supportScope.launch { starPromptManager.complete() } },
                    onSaveAiFilterMode = searchViewModel::saveAiFilterMode,
                    onToggleWaveLoopProtection = searchViewModel::setWaveLoopProtection,
                    onSaveWaveLoopHistoryLimit = searchViewModel::saveWaveLoopHistoryLimit,
                )
            )

            when (val current = dialog) {
                null -> Unit

                is AppDialog.Equalizer -> EqualizerDialog(
                    initialTab = current.initialTab,
                    onDismissRequest = { dialog = null }
                )

                is AppDialog.UpNext -> UpNextDialog(
                    viewModel = searchViewModel,
                    onDismiss = {
                        dialog = null
                        if (current.openedFromPlayer) nav.updateMobilePlayerExpanded(true)
                    },
                    onOpenPlaylist = openPlayingPlaylist
                )

                AppDialog.MediaLibrary -> PlaylistsDialog(
                    viewModel = searchViewModel,
                    onDismiss = { dialog = null },
                    selectedTab = nav.mediaLibraryTab,
                    onTabSelected = { nav.mediaLibraryTab = it },
                    onOpenSync = {
                        dialog = null
                        showYamSyncDialog = true
                    }
                )

                AppDialog.Logs -> LogsDialog(
                    onDismiss = { dialog = null }
                )

                AppDialog.WaveThemes -> WaveThemesDialog(
                    viewModel = searchViewModel,
                    onDismiss = { dialog = null }
                )

                is AppDialog.AddToPlaylist -> AddToPlaylistDialog(
                    track = current.track,
                    viewModel = searchViewModel,
                    onDismiss = { dialog = null }
                )

                is AppDialog.PlayerTrackRemoval -> PlayerTrackRemovalDialog(
                    track = current.track,
                    loadOptions = { searchViewModel.preparePlayerTrackRemoval(current.track) },
                    onDismiss = { dialog = null },
                    onConfirm = { target, selection ->
                        dialog = null
                        searchViewModel.applyPlayerTrackRemoval(target, selection)
                    }
                )

                is AppDialog.DeleteTrack -> DeleteTrackConfirmDialog(
                    target = current,
                    onDismiss = { dialog = null },
                    onConfirm = {
                        dialog = null
                        searchViewModel.deleteTrack(
                            trackId = current.trackId,
                            trackTitle = current.title,
                            artistName = current.artist
                        )
                    }
                )
            }

            if (showYamSyncDialog) {
                io.github.audiz.ui.sync.YamSyncDialog(
                    syncManager = searchViewModel.yamSyncManager,
                    onDismiss = { showYamSyncDialog = false }
                )
            }
            }
        }
    }
}
}

@Composable
private fun DeleteTrackConfirmDialog(
    target: AppDialog.DeleteTrack,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val trackDesc = buildString {
        append("«${target.title.ifBlank { "Без названия" }}»")
        if (target.artist.isNotBlank()) {
            append(" (${target.artist})")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Удаление файла с диска",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Вы действительно хотите удалить аудиофайл $trackDesc из памяти устройства? Это действие нельзя отменить.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
