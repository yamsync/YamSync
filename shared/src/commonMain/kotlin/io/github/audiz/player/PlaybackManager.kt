package io.github.audiz.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.audiz.AudioPlayer
import io.github.audiz.SystemMediaControls
import io.github.audiz.TrackPlaySource
import io.github.audiz.acquirePlaybackWakeLock
import io.github.audiz.currentTimeMillis
import io.github.audiz.getFileSize
import io.github.audiz.loadAppConfig
import io.github.audiz.localFileExists
import io.github.audiz.releasePlaybackWakeLock
import io.github.audiz.saveAppConfig
import io.github.audiz.AppConfigKeys
import io.github.audiz.stream.LocalStreamProxy
import io.github.audiz.api.MusicRepository
import io.github.audiz.models.DownloadedTrackAudio
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.DispatcherIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * 🎵 Выделенный менеджер воспроизведения музыки и управления очередью треков.
 * Инкапсулирует:
 * - Стейт-машину аудиоплеера (play, pause, seek, volume, прогресс, битрейт).
 * - Аппаратную интеграцию (AudioPlayer, SystemMediaControls, PlatformWakeLock).
 * - Навигацию по истории и очереди треков (Next, Prev, Shuffle, loop).
 *
 * Выбор следующего/предыдущего трека — [PlaybackNavigator], предзагрузка — [TrackPreloader],
 * сохранение очереди «Далее» — [PlaybackQueuePersistence], запись аудио на диск — [DownloadedAudioWriter].
 */
class PlaybackManager(
    private val scope: CoroutineScope,
    private val repository: MusicRepository,
    val localTrackResolver: LocalTrackResolver,
    val queueSource: PlaybackQueueSource,
    val queueManager: PlaybackQueueManager = PlaybackQueueManager(),
    val waveBridge: WavePlaybackBridge? = null,
    private val getSelectedQuality: () -> String,
    private val isRecordToDisk: () -> Boolean,
    private val onTrackSavedToDisk: (() -> Unit)? = null,
    private val onTrackStarted: ((trackId: String, title: String, artist: String, coverUri: String?, isWave: Boolean) -> Unit)? = null,
    private val onError: (String) -> Unit = {},
    private val onTrackMetadataChanged: ((FullTrackInfo) -> Unit)? = null,
    private val onPlaybackConfirmed: ((String) -> Unit)? = null,
) {
    val upNextQueue = UpNextQueue()
    private var restoringUpNext = false
    private val queuePersistence = PlaybackQueuePersistence(scope)
    private val navigator = PlaybackNavigator(upNextQueue, queueManager, queueSource, waveBridge, ::findTrackInfo)
    private val audioWriter = DownloadedAudioWriter(repository, localTrackResolver, ::findTrackInfo)
    private val preloader = TrackPreloader(
        scope = scope,
        repository = repository,
        localTrackResolver = localTrackResolver,
        audioWriter = audioWriter,
        getSelectedQuality = getSelectedQuality,
        isRecordToDisk = isRecordToDisk,
        onTrackSavedToDisk = onTrackSavedToDisk,
        currentTrackId = { trackId },
        plannedNextTrackId = { plannedNextTrack?.id },
        coverUriForTags = { coverUri },
    )

    suspend fun restoreUpNextQueue() {
        val saved = queuePersistence.load()
        if (saved != null && upNextQueue.state == UpNextState()) {
            upNextQueue.restore(saved)
            queueManager.playedInCycleTrackIds.addAll(saved.shuffleCycle)
            restoringUpNext = saved.current?.inserted == true
            if (restoringUpNext) saved.current?.track?.let { setInitialTrack(it) }
        }
        queuePersistence.startAutoSave(upNextQueue)
    }

    fun addToUpNext(track: FullTrackInfo, first: Boolean = false) {
        val state = upNextQueue.state
        if (state.pending.isEmpty() && !state.suspended && state.forward.isEmpty()) {
            val resume = navigator.calculateResumeTrack(trackId)
            upNextQueue.prepareResume(resume, trackId, queueManager.playedInCycleTrackIds)
        }
        upNextQueue.add(track, first)
        refreshUpNextPreload()
    }

    fun refreshUpNextPreload() {
        preloader.cancel()
        preloader.clearAudio()
        plannedNextTrack = null
        plannedNextTrack = calculateNextTrackCandidate()
        if (isPlaying) schedulePreload()
    }

    fun clearUpNextForSelection() {
        restoringUpNext = false
        upNextQueue.reset()
        plannedNextTrack = null
    }

    private fun playUpNextTrack(track: FullTrackInfo, source: TrackPlaySource, crossfadeMs: Long = 0L) {
        val waveIndex = waveBridge?.waveTracks?.indexOfFirst { PlaybackQueueManager.isSameTrack(it, track.id) } ?: -1
        if (waveBridge?.isWaveMode == true && upNextQueue.state.current?.inserted == false && waveIndex >= 0) {
            waveBridge.playWaveTrack(waveIndex, source, crossfadeMs)
        } else {
            playTrack(track.id, track.title, track.artists.joinToString { it.name },
                targetAlbumId = track.albums.firstOrNull()?.id, targetCoverUri = track.coverUri,
                isManualSelection = false, source = source, crossfadeMs = crossfadeMs, queueSelection = true)
        }
    }

    /** Сейчас играет трек из очереди «Далее», а не из исходного списка или Волны. */
    val isPlayingUpNextTrack: Boolean get() = navigator.isPlayingUpNextTrack

    /**
     * Единая точка автоперехода после окончания трека: очередь «Далее» всегда имеет приоритет над Волной.
     * [finished] = false означает, что трек не доигран (ошибка), и о завершении Волне не сообщаем.
     */
    internal fun advanceAfterTrackEnd(prevId: String? = trackId, crossfadeMs: Long, finished: Boolean = true) {
        val bridge = waveBridge
        if (bridge?.isWaveMode == true && !navigator.hasUpNextTransition) {
            bridge.onWaveTrackFinished(crossfadeMs)
            return
        }
        if (finished && bridge?.isWaveMode == true && !isPlayingUpNextTrack) {
            bridge.reportWaveTrackFinished(prevId)
        }
        playNextTrack(prevId, source = TrackPlaySource.PLAY, crossfadeMs = crossfadeMs)
    }

    private fun skipFailedUpNext(id: String) {
        if (upNextQueue.state.current?.inserted == true && upNextQueue.state.current?.track?.id == id) {
            upNextQueue.discardFailedCurrent()
            scope.launch { playNextTrack(id, TrackPlaySource.PLAY) }
        }
    }

    /**
     * Вторичный конструктор для 100% обратной совместимости с тестами и существующим кодом.
     */
    constructor(
        scope: CoroutineScope,
        repository: MusicRepository,
        getMusicStoragePath: () -> String,
        getSelectedQuality: () -> String,
        isRecordToDisk: () -> Boolean,
        onTrackSavedToDisk: (() -> Unit)? = null,
        isTrackDownloaded: (artist: String, title: String) -> Boolean = { _, _ -> false },
        getLoadedTracks: () -> List<FullTrackInfo>,
        getSearchTracks: () -> List<FullTrackInfo> = { emptyList() },
        onLoadMoreTracks: (suspend () -> Boolean)? = null,
        isWaveMode: () -> Boolean = { false },
        onPlayWaveByIndex: ((Int, TrackPlaySource) -> Unit)? = null,
        onPlayNextWave: (() -> Unit)? = null,
        onPlayPrevWave: (() -> Unit)? = null,
        onWaveTrackFinished: ((crossfadeMs: Long) -> Unit)? = null,
        onInitialWave: ((autoPlay: Boolean, source: TrackPlaySource) -> Unit)? = null,
        getWaveTracks: () -> List<FullTrackInfo> = { emptyList() },
        getWaveCurrentIndex: () -> Int = { 0 },
        getNextWaveTrack: (() -> FullTrackInfo?)? = null,
        onWaveTrackFinishedReported: ((trackId: String?) -> Unit)? = null,
        onError: (String) -> Unit = {}
    ) : this(
        scope = scope,
        repository = repository,
        localTrackResolver = LocalTrackResolver(getMusicStoragePath),
        queueSource = object : PlaybackQueueSource {
            override fun getLoadedTracks(): List<FullTrackInfo> = getLoadedTracks()
            override fun getSearchTracks(): List<FullTrackInfo> = getSearchTracks()
            override suspend fun loadMoreTracks(): Boolean = onLoadMoreTracks?.invoke() ?: false
        },
        queueManager = PlaybackQueueManager(),
        waveBridge = object : WavePlaybackBridge {
            override val isWaveMode: Boolean get() = isWaveMode()
            override val waveTracks: List<FullTrackInfo> get() = getWaveTracks()
            override val waveCurrentIndex: Int get() = getWaveCurrentIndex()
            override fun playWaveTrack(index: Int, source: TrackPlaySource, crossfadeMs: Long) {
                onPlayWaveByIndex?.invoke(index, source)
            }
            override fun playNextWaveTrack(crossfadeMs: Long) { onPlayNextWave?.invoke() }
            override fun playPrevWaveTrack() { onPlayPrevWave?.invoke() }
            override fun onWaveTrackFinished(crossfadeMs: Long) { onWaveTrackFinished?.invoke(crossfadeMs) }
            override fun loadInitialWave(autoPlay: Boolean, source: TrackPlaySource) { onInitialWave?.invoke(autoPlay, source) }
            override fun resumeOrLoadWave(autoPlay: Boolean, source: TrackPlaySource) {
                if (autoPlay && waveTracks.isNotEmpty()) playWaveTrack(waveCurrentIndex, source)
                else onInitialWave?.invoke(autoPlay, source)
            }
            override fun getNextWaveTrackCandidate(): FullTrackInfo? = getNextWaveTrack?.invoke()
            override fun reportWaveTrackFinished(trackId: String?) { onWaveTrackFinishedReported?.invoke(trackId) }
        },
        getSelectedQuality = getSelectedQuality,
        isRecordToDisk = isRecordToDisk,
        onTrackSavedToDisk = onTrackSavedToDisk,
        onError = onError
    )

    companion object {
        const val PREV_RESTART_THRESHOLD_MS = 5000L // 5 секунд: если трек играет дольше, ⏮ начинает его сначала
        const val PREV_DOUBLE_CLICK_WINDOW_MS = 2000L // 2 секунды: окно для повторного нажатия ⏮ для перехода на предыдущий
    }

    val audioPlayer = AudioPlayer()
    val systemMediaControls = SystemMediaControls()
    var isTrackLiked: ((String?) -> Boolean)? = null

    var isPlaying by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var trackId by mutableStateOf<String?>(null)
        private set
    var albumId by mutableStateOf<Long?>(null)
        private set
    var trackTitle by mutableStateOf("")
        private set
    var artistName by mutableStateOf("")
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var bitrate by mutableStateOf<Int?>(null)
        private set
    var coverUri by mutableStateOf<String?>(null)
        private set
    var volume by mutableStateOf(loadAppConfig(AppConfigKeys.PLAYER_VOLUME)?.toFloatOrNull() ?: 1f)
        private set

    // 📻 Плавный переход между треками (Кроссфейд в секундах, 0 = Выкл / Умный стык)
    var crossfadeSeconds by mutableStateOf(loadAppConfig(AppConfigKeys.PLAYER_CROSSFADE_SECONDS)?.toIntOrNull() ?: 3)

    val isShuffleEnabled: Boolean get() = queueManager.isShuffleEnabled

    var isNextLoading by mutableStateOf(false)
        private set
    var isPrevLoading by mutableStateOf(false)
        private set
    var isPlayLoading by mutableStateOf(false)
        private set

    var currentPlayingFilePath: String? = null
        private set
    var currentPlayingSizeBytes: Long? = null
        private set
    var isCurrentTrackHQ: Boolean = false
        private set

    // ⚡ Предзагрузка следующего трека
    var plannedNextTrack: FullTrackInfo?
        get() = queueManager.plannedNextTrack
        set(value) { queueManager.plannedNextTrack = value }
    val preloadedAudio: PreloadedAudio? get() = preloader.preloadedAudio

    // ⏱ Запас на запуск следующего трека перед кроссфейдом по фактическим замерам
    private val transitionLead = TransitionLeadEstimator { currentTimeMillis() }

    private var progressJob: Job? = null
    private var playTrackJob: Job? = null
    @Volatile private var isSeeking: Boolean = false
    @Volatile private var hasTriggeredAutoAdvance: Boolean = false
    @Volatile private var lastSeekTimestamp: Long = 0L
    @Volatile private var lastPrevClickTimestamp: Long = 0L
    private var trackStartSystemTime: Long = 0L

    // 📜 История воспроизведения для точного возврата назад (⏮) и перехода вперед (⏭)
    val playbackHistory: MutableList<String> get() = queueManager.playbackHistory
    val forwardHistory: MutableList<String> get() = queueManager.forwardHistory

    var wasInterruptedBySystem: Boolean = false
        private set

    init {
        audioPlayer.setVolume(volume)

        // 🐧 Кросс-платформенная интеграция с системным медиаплеером / MPRIS / SMTC
        systemMediaControls.initialize(
            onPlay = { if (isPaused) togglePlayPause() },
            onPause = { if (isPlaying && !isPaused) togglePlayPause() },
            onTogglePlayPause = { togglePlayPause() },
            onNext = { playNextTrack() },
            onPrev = { playPrevTrack() },
            onSeek = { seekTo(it) }
        )

        // 🎵 Аппаратный коллбэк завершения трека
        audioPlayer.setOnCompletionListener {
            if (!hasTriggeredAutoAdvance) {
                handleTrackFinished(isNaturalCompletion = true)
            }
        }

        // 📻 Нативный аппаратный тайм-обсервер (надежно срабатывает в фоне / при выключенном экране на iOS)
        audioPlayer.setOnNearEndListener {
            val totalDur = if (durationMs > 0) durationMs else audioPlayer.getDurationMs()
            val effectiveCrossfadeMs = effectiveCrossfadeMs(totalDur)

            if (!hasTriggeredAutoAdvance && !isSeeking && isPlaying && !isPaused && totalDur > 6000L) {
                hasTriggeredAutoAdvance = true
                if (effectiveCrossfadeMs > 0L) {
                    transitionLead.onTransitionRequested(isNextTrackLocal(), isNextTrackPreloaded())
                }
                println("PlaybackManager: 📻 Аппаратный TimeObserver: автопереход к следующему треку (кроссфейд: ${effectiveCrossfadeMs}мс)")
                advanceAfterTrackEnd(crossfadeMs = effectiveCrossfadeMs)
            }
        }

        audioPlayer.setOnPlaybackStartedListener {
            val startedId = trackId
            scope.launch {
                startedId?.let { onPlaybackConfirmed?.invoke(it) }
                transitionLead.onPlaybackStarted()
                isNextLoading = false
                isPrevLoading = false
                isPlayLoading = false
                isPlaying = true
                isPaused = false
                releasePlaybackWakeLock()
                systemMediaControls.updatePlaybackState(true, false, positionMs)
                startProgressPolling()
            }
        }

        audioPlayer.setOnErrorListener { errorMsg ->
            scope.launch {
                handlePlaybackError(errorMsg)
            }
        }

        // 🎧 Обработка системных прерываний (звонки, будильники) и отключений (Bluetooth/наушники)
        audioPlayer.setOnInterruptionListener { shouldPause, canResume ->
            scope.launch {
                if (shouldPause) {
                    if (isPlaying && !isPaused) {
                        positionMs = audioPlayer.getCurrentPositionMs()
                        audioPlayer.pause()
                        isPaused = true
                        wasInterruptedBySystem = canResume
                        systemMediaControls.updatePlaybackState(isPlaying = false, isPaused = true, positionMs)
                    }
                } else {
                    // Прерывание завершилось (звонок окончен)
                    if (wasInterruptedBySystem && isPaused && canResume) {
                        wasInterruptedBySystem = false
                        audioPlayer.resume()
                        isPaused = false
                        systemMediaControls.updatePlaybackState(isPlaying = true, isPaused = false, positionMs)
                        startProgressPolling()
                    }
                }
            }
        }

        // 🌐 Запуск локального HTTP стриминг-прокси (Zero-Latency Audio Streaming)
        try {
            LocalStreamProxy.start(
                repository = repository,
                getMusicStoragePath = localTrackResolver.getMusicStoragePath,
                isRecordToDisk = isRecordToDisk,
                onTrackSaved = { savedTrackId, filePath ->
                    if (trackId == savedTrackId) {
                        currentPlayingFilePath = filePath
                        currentPlayingSizeBytes = getFileSize(filePath).takeIf { it > 0 }
                    }
                    onTrackSavedToDisk?.invoke()
                }
            )
        } catch (e: Throwable) {
            println("PlaybackManager: Ошибка запуска LocalStreamProxy: ${e.message}")
        }
    }

    fun changeVolume(newVolume: Float) {
        volume = newVolume
        audioPlayer.setVolume(newVolume)
        saveAppConfig(AppConfigKeys.PLAYER_VOLUME, newVolume.toString())
    }

    fun changeCrossfade(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        crossfadeSeconds = clamped
        saveAppConfig(AppConfigKeys.PLAYER_CROSSFADE_SECONDS, clamped.toString())
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
        upNextQueue.captureShuffleCycle(queueManager.playedInCycleTrackIds)
        preloader.cancel()
        preloader.clearAudio()
        queueManager.plannedNextTrack = null
        queueManager.plannedNextTrack = calculateNextTrackCandidate()
        schedulePreload()
    }

    fun setLoadingFlags(next: Boolean, prev: Boolean, play: Boolean) {
        isNextLoading = next
        isPrevLoading = prev
        isPlayLoading = play
    }

    private fun setLoadingFor(source: TrackPlaySource) = setLoadingFlags(
        next = source == TrackPlaySource.NEXT,
        prev = source == TrackPlaySource.PREV,
        play = source == TrackPlaySource.PLAY,
    )

    fun resetLoadingFlags() {
        isNextLoading = false
        isPrevLoading = false
        isPlayLoading = false
    }

    fun updateCurrentPlayingFilePath(path: String?) {
        currentPlayingFilePath = path
    }

    fun stopPlayback() {
        wasInterruptedBySystem = false
        hasTriggeredAutoAdvance = false
        lastPrevClickTimestamp = 0L
        playTrackJob?.cancel()
        playTrackJob = null
        isNextLoading = false
        isPrevLoading = false
        isPlayLoading = false
        progressJob?.cancel()
        audioPlayer.stop()
        releasePlaybackWakeLock()
        isPlaying = false
        isPaused = false
        trackId = null
        albumId = null
        positionMs = 0L
        durationMs = 0L
        trackTitle = ""
        artistName = ""
        bitrate = null
        currentPlayingFilePath = null
        currentPlayingSizeBytes = null
        isCurrentTrackHQ = false
        preloader.reset()
        queueManager.clearAll()
        systemMediaControls.clear()
    }

    fun seekTo(position: Long) {
        val safePos = position.coerceAtLeast(0L)
        lastSeekTimestamp = currentTimeMillis()
        isSeeking = true
        hasTriggeredAutoAdvance = false
        try {
            io.github.audiz.dsp.AudioVisualizer.resetTrackAudibility()
        } catch (_: Throwable) {}
        try {
            audioPlayer.seekTo(safePos)
            positionMs = safePos
            systemMediaControls.updatePlaybackState(isPlaying, isPaused, safePos)
        } catch (t: Throwable) {
            println("PlaybackManager: Ошибка seekTo: ${t.message}")
        } finally {
            scope.launch {
                delay(700)
                if (currentTimeMillis() - lastSeekTimestamp >= 600) {
                    isSeeking = false
                }
            }
        }
    }

    fun togglePlayPause() {
        wasInterruptedBySystem = false
        if (isPlaying && !isPaused) {
            positionMs = audioPlayer.getCurrentPositionMs()
            audioPlayer.pause()
            isPaused = true
            systemMediaControls.updatePlaybackState(false, true, positionMs)
        } else if (isPaused) {
            audioPlayer.resume()
            isPaused = false
            systemMediaControls.updatePlaybackState(true, false, positionMs)
            startProgressPolling()
        } else if (trackId == null) {
            if (upNextQueue.state.pending.isNotEmpty()) {
                playNextTrack(source = TrackPlaySource.PLAY)
                return
            }
            isPlayLoading = true
            waveBridge?.resumeOrLoadWave(true, TrackPlaySource.PLAY)
        } else if (waveBridge?.isWaveMode == true && upNextQueue.state.current?.inserted != true &&
            waveBridge.waveTracks.getOrNull(waveBridge.waveCurrentIndex)?.id == trackId) {
            waveBridge.playWaveTrack(waveBridge.waveCurrentIndex, TrackPlaySource.PLAY)
        } else {
            isPlayLoading = true
            playTrack(
                targetTrackId = trackId!!,
                targetTrackTitle = trackTitle,
                targetArtistName = artistName,
                targetAlbumId = albumId,
                isManualSelection = false,
                source = TrackPlaySource.PLAY,
                queueSelection = upNextQueue.state.current?.inserted == true
            )
        }
    }

    /**
     * 🎵 Установить начальный трек (для восстановления сессии при рестарте).
     * Выставляет метаданные трека в плеере в состоянии готовности к запуску.
     */
    fun setInitialTrack(initial: FullTrackInfo) {
        val track = if (restoringUpNext) upNextQueue.state.current?.track ?: initial else initial
        if (!restoringUpNext) upNextQueue.restoreOriginalTrack(initial)
        trackId = track.id
        trackTitle = track.title
        artistName = track.artists.joinToString { it.name }.ifBlank { "Unknown Artist" }
        albumId = track.albums.firstOrNull()?.id
        durationMs = track.durationMs
        positionMs = 0L
        coverUri = track.coverUri
        isPlaying = false
        isPaused = false
    }

    internal fun refreshRestoredTrackMetadata(track: FullTrackInfo) {
        if (trackId != track.id) return
        upNextQueue.restoreOriginalTrack(track)
        if (track.durationMs > 0L) durationMs = track.durationMs
        if (track.title.isNotBlank()) trackTitle = track.title
        if (track.artists.isNotEmpty()) artistName = track.artists.joinToString { it.name }
        albumId = track.albums.firstOrNull()?.id ?: albumId
        coverUri = track.coverUri ?: coverUri
        onTrackMetadataChanged?.invoke(track)
        if (isPlaying || isPaused) {
            systemMediaControls.updateMetadata(trackTitle, artistName, "", durationMs, trackId, coverUri,
                isLiked = isTrackLiked?.invoke(trackId) == true)
        }
    }

    fun sanitizeKeepSpaces(input: String): String = LocalTrackResolver.sanitizeKeepSpaces(input)

    fun findTrackInfo(targetTrackId: String): FullTrackInfo? {
        val cleanTarget = targetTrackId.removePrefix("local:")
        fun matches(it: FullTrackInfo): Boolean {
            return it.id == targetTrackId || it.realId == targetTrackId ||
                   it.id.removePrefix("local:") == cleanTarget ||
                   it.realId?.removePrefix("local:") == cleanTarget
        }
        return upNextQueue.findTrack(targetTrackId)
            ?: queueSource.getLoadedTracks().firstOrNull(::matches)
            ?: waveBridge?.waveTracks?.firstOrNull(::matches)
            ?: queueSource.getSearchTracks().firstOrNull(::matches)
    }

    /**
     * Воспроизвести трек: если файл есть на диске — играть с диска; иначе скачать по сети.
     */
    fun playTrack(
        targetTrackId: String,
        targetTrackTitle: String,
        targetArtistName: String,
        targetAlbumId: Long? = null,
        targetCoverUri: String? = null,
        isManualSelection: Boolean = true,
        isWave: Boolean = false,
        source: TrackPlaySource = TrackPlaySource.PLAY,
        crossfadeMs: Long = 0L,
        queueSelection: Boolean = false
    ) {
        if (isManualSelection) clearUpNextForSelection()
        restoringUpNext = false
        if (!isManualSelection && !queueSelection) {
            val info = findTrackInfo(targetTrackId) ?: FullTrackInfo(id = targetTrackId, title = targetTrackTitle)
            upNextQueue.normalTrackSelected(info)
        }
        if (crossfadeMs <= 0L) {
            hasTriggeredAutoAdvance = false
            transitionLead.cancel()
        }
        isFallbackDownloading = false
        wasInterruptedBySystem = false
        progressJob?.cancel()
        val effectiveCrossfade = if (crossfadeMs > 0L) {
            crossfadeMs
        } else {
            0L
        }
        lastPrevClickTimestamp = 0L
        coverUri = targetCoverUri ?: findTrackInfo(targetTrackId)?.coverUri
        if (isManualSelection) {
            val currentId = trackId
            if (currentId != null && currentId != targetTrackId) {
                queueManager.recordPlayed(currentId)
                queueManager.clearForwardHistory()
            }
        }

        playTrackJob?.cancel()
        preloader.cancel()
        io.github.audiz.dsp.AudioVisualizer.resetTrackAudibility()
        preloader.keepOnlyFor(targetTrackId)
        setLoadingFor(source)

        var thisJob: Job? = null
        thisJob = scope.launch {
            val cleanArtist = targetArtistName.trim()
            val cleanTitle = targetTrackTitle.trim()
            val musicStoragePath = localTrackResolver.getMusicStoragePath()
            val selectedQuality = getSelectedQuality()
            var isStreamingStarted = false
            try {
                val resolvedAlbumId = targetAlbumId ?: findTrackInfo(targetTrackId)?.albums?.firstOrNull()?.id

                // 🔍 1. Проверяем, есть ли файл на диске
                val preloaded = preloadedAudio
                val existingFilePath = withContext(DispatcherIO) {
                    preloaded?.filePath?.takeIf {
                        preloaded.trackId == targetTrackId && preloaded.quality == selectedQuality && localFileExists(it)
                    } ?: localTrackResolver.findLocalTrackFile(targetTrackId, cleanArtist, cleanTitle, selectedQuality)
                }

                // Если файл найден — играем с диска без скачивания
                if (existingFilePath != null) {
                    println("▶️ Играем с диска: $existingFilePath")
                    isNextLoading = false
                    isPrevLoading = false
                    isPlayLoading = false
                    trackId = targetTrackId
                    albumId = resolvedAlbumId
                    trackTitle = cleanTitle
                    artistName = cleanArtist
                    currentPlayingFilePath = existingFilePath
                    currentPlayingSizeBytes = withContext(DispatcherIO) { getFileSize(existingFilePath).takeIf { it > 0 } }
                    isCurrentTrackHQ = (selectedQuality == "2") && existingFilePath.contains("HQ", ignoreCase = true)
                    val knownDuration = findTrackInfo(targetTrackId)?.durationMs?.takeIf { it > 0 } ?: 0L
                    positionMs = 0L
                    durationMs = knownDuration
                    audioPlayer.playFromFile(existingFilePath, effectiveCrossfade)
                    if (durationMs <= 0L) {
                        val playerDur = audioPlayer.getDurationMs()
                        if (playerDur > 0L) {
                            durationMs = playerDur
                        } else {
                            try {
                                val localInfo = withContext(DispatcherIO) {
                                    io.github.audiz.getTracksFromLocalPaths(listOf(existingFilePath)).firstOrNull()
                                }
                                if (localInfo != null && localInfo.durationMs > 0L) {
                                    durationMs = localInfo.durationMs
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                        }
                    }
                    isPlaying = true
                    isPaused = false
                    releasePlaybackWakeLock()
                    systemMediaControls.updateMetadata(
                        cleanTitle,
                        cleanArtist,
                        "",
                        durationMs,
                        targetTrackId,
                        coverUri,
                        isLiked = isTrackLiked?.invoke(targetTrackId) == true
                    )
                    systemMediaControls.updatePlaybackState(true, false, 0L)
                    onTrackStarted?.invoke(targetTrackId, cleanTitle, cleanArtist, coverUri, isWave)
                    preloader.clearAudio()
                    startProgressPolling()
                    planNextAndPreload()
                    return@launch
                }

                // 2. Если трек локальный, но файла нет — не запрашиваем сеть
                if (targetTrackId.startsWith("local:")) {
                    onError("Локальный файл не найден на диске: ${targetTrackId.removePrefix("local:")}")
                    skipFailedUpNext(targetTrackId)
                    releasePlaybackWakeLock()
                    isNextLoading = false
                    isPrevLoading = false
                    isPlayLoading = false
                    return@launch
                }

                // 3. 📥 Файла нет на диске и трек сетевой
                val isPreloadedInMemory = preloaded?.trackId == targetTrackId && preloaded.quality == selectedQuality && preloaded.audioData != null

                // 🌐 Если нет готового аудио в памяти и работает локальный стриминг-прокси — запускаем воспроизведение мгновенно!
                if (!isPreloadedInMemory && LocalStreamProxy.isRunning()) {
                    trackId = targetTrackId
                    albumId = resolvedAlbumId
                    trackTitle = cleanTitle
                    artistName = cleanArtist
                    val knownDuration = findTrackInfo(targetTrackId)?.durationMs?.takeIf { it > 0 } ?: 0L
                    positionMs = 0L
                    durationMs = knownDuration
                    bitrate = DownloadedAudioWriter.defaultBitrate(selectedQuality)
                    isCurrentTrackHQ = (selectedQuality == "2")
                    currentPlayingFilePath = null
                    currentPlayingSizeBytes = null

                    val streamUrl = LocalStreamProxy.getStreamUrl(
                        trackId = targetTrackId,
                        quality = selectedQuality,
                        title = cleanTitle,
                        artist = cleanArtist
                    )
                    println("⚡ [Стриминг] Мгновенный запуск потока через LocalStreamProxy: $streamUrl")
                    isStreamingStarted = true
                    audioPlayer.playFromUrl(streamUrl, effectiveCrossfade)

                    isPlaying = true
                    isPaused = false
                    isNextLoading = false
                    isPrevLoading = false
                    isPlayLoading = false
                    releasePlaybackWakeLock()
                    systemMediaControls.updateMetadata(
                        cleanTitle,
                        cleanArtist,
                        "",
                        durationMs,
                        targetTrackId,
                        coverUri,
                        isLiked = isTrackLiked?.invoke(targetTrackId) == true
                    )
                    systemMediaControls.updatePlaybackState(true, false, 0L)
                    onTrackStarted?.invoke(targetTrackId, cleanTitle, cleanArtist, coverUri, isWave)
                    startProgressPolling()
                    preloader.clearAudio()
                    planNextAndPreload()
                    return@launch
                }

                val audioData = if (isPreloadedInMemory) {
                    println("⚡ [Предзагрузка] Используем готовое аудио из памяти для: $cleanTitle")
                    preloaded.audioData
                } else {
                    repository.downloadTrackAudio(targetTrackId, quality = selectedQuality)
                }
                preloader.clearAudio()

                trackId = targetTrackId
                albumId = resolvedAlbumId
                trackTitle = cleanTitle
                artistName = cleanArtist
                applyDownloadedAudioInfo(audioData, targetTrackId, selectedQuality)

                if (isRecordToDisk() && musicStoragePath.isNotBlank()) {
                    val savedPath = audioWriter.save(targetTrackId, cleanTitle, cleanArtist, selectedQuality, audioData, coverUri)
                    localTrackResolver.invalidateCache()
                    onTrackSavedToDisk?.invoke()
                    val resolvedFile = localTrackResolver.findLocalTrackFile(
                        trackId = targetTrackId,
                        artist = cleanArtist,
                        title = cleanTitle,
                        selectedQuality = selectedQuality
                    ) ?: savedPath
                    currentPlayingFilePath = resolvedFile
                    audioPlayer.playFromFile(resolvedFile, effectiveCrossfade)
                } else {
                    println("💾 Record to disk выключен: воспроизводим трек без сохранения в библиотеку")
                    currentPlayingFilePath = null
                    audioPlayer.playFromBytes(audioData.result, effectiveCrossfade)
                }

                isPlaying = true
                isPaused = false
                isNextLoading = false
                isPrevLoading = false
                isPlayLoading = false
                releasePlaybackWakeLock()
                systemMediaControls.updateMetadata(
                    cleanTitle,
                    cleanArtist,
                    "",
                    durationMs,
                    targetTrackId,
                    coverUri,
                    isLiked = isTrackLiked?.invoke(targetTrackId) == true
                )
                systemMediaControls.updatePlaybackState(true, false, 0L)
                onTrackStarted?.invoke(targetTrackId, cleanTitle, cleanArtist, coverUri, isWave)
                startProgressPolling()
                planNextAndPreload()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                releasePlaybackWakeLock()
                isNextLoading = false
                isPrevLoading = false
                isPlayLoading = false
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Неизвестная ошибка"
                io.github.audiz.util.AppLogger.e("PlaybackManager", "Ошибка воспроизведения '$cleanTitle': $errorMsg", e)
                onError("Ошибка воспроизведения: $errorMsg")
                skipFailedUpNext(targetTrackId)
            } finally {
                if (!isStreamingStarted) {
                    val currentJob = coroutineContext[Job]
                    if (currentJob == null || !currentJob.isCancelled || playTrackJob === currentJob || playTrackJob === thisJob) {
                        isNextLoading = false
                        isPrevLoading = false
                        isPlayLoading = false
                    }
                }
            }
        }
        playTrackJob = thisJob
    }

    /**
     * Fallback: принудительно скачать и воспроизвести в LQ
     */
    fun playTrackForceLQ(
        targetTrackId: String,
        targetTrackTitle: String,
        targetArtistName: String,
        source: TrackPlaySource = TrackPlaySource.PLAY
    ) {
        wasInterruptedBySystem = false
        playTrackJob?.cancel()
        setLoadingFor(source)
        var thisJob: Job? = null
        thisJob = scope.launch {
            val cleanArtist = targetArtistName.trim()
            val cleanTitle = targetTrackTitle.trim()
            val musicStoragePath = localTrackResolver.getMusicStoragePath()
            try {
                val existingFilePath = localTrackResolver.findLocalTrackFile(
                    trackId = targetTrackId,
                    artist = cleanArtist,
                    title = cleanTitle,
                    selectedQuality = "1"
                )
                val knownDuration = findTrackInfo(targetTrackId)?.durationMs?.takeIf { it > 0 } ?: 0L

                if (existingFilePath != null) {
                    println("▶️ Играем LQ с диска: $existingFilePath")
                    isNextLoading = false
                    isPrevLoading = false
                    isPlayLoading = false
                    trackId = targetTrackId
                    trackTitle = cleanTitle
                    artistName = cleanArtist
                    currentPlayingFilePath = existingFilePath
                    positionMs = 0L
                    durationMs = knownDuration
                    currentPlayingSizeBytes = getFileSize(existingFilePath).takeIf { it > 0 }
                    bitrate = DownloadedAudioWriter.bitrateFromSize(currentPlayingSizeBytes, durationMs) ?: 192
                    audioPlayer.playFromFile(existingFilePath)
                    isPlaying = true
                    isPaused = false
                    releasePlaybackWakeLock()
                    startProgressPolling()
                    planNextAndPreload()
                    return@launch
                }

                if (LocalStreamProxy.isRunning()) {
                    trackId = targetTrackId
                    trackTitle = cleanTitle
                    artistName = cleanArtist
                    positionMs = 0L
                    durationMs = knownDuration
                    bitrate = 192
                    isCurrentTrackHQ = false
                    currentPlayingFilePath = null
                    currentPlayingSizeBytes = null

                    val streamUrl = LocalStreamProxy.getStreamUrl(
                        trackId = targetTrackId,
                        quality = "1",
                        title = cleanTitle,
                        artist = cleanArtist
                    )
                    println("⚡ [Стриминг LQ] Запуск потока через LocalStreamProxy: $streamUrl")
                    audioPlayer.playFromUrl(streamUrl)

                    val resolvedCoverUri = findTrackInfo(targetTrackId)?.coverUri
                    coverUri = resolvedCoverUri
                    val isWave = waveBridge?.isWaveMode == true

                    isPlaying = true
                    isPaused = false
                    isNextLoading = false
                    isPrevLoading = false
                    isPlayLoading = false
                    releasePlaybackWakeLock()
                    systemMediaControls.updateMetadata(
                        cleanTitle,
                        cleanArtist,
                        "",
                        durationMs,
                        targetTrackId,
                        resolvedCoverUri,
                        isLiked = isTrackLiked?.invoke(targetTrackId) == true
                    )
                    systemMediaControls.updatePlaybackState(true, false, 0L)
                    onTrackStarted?.invoke(targetTrackId, cleanTitle, cleanArtist, resolvedCoverUri, isWave)
                    startProgressPolling()
                    preloader.clearAudio()
                    planNextAndPreload()
                    return@launch
                }

                println("📥 Скачиваем LQ трек...")
                val audioData = repository.downloadTrackAudio(targetTrackId, quality = "1")

                trackId = targetTrackId
                trackTitle = cleanTitle
                artistName = cleanArtist
                applyDownloadedAudioInfo(audioData, targetTrackId, quality = "1")

                if (isRecordToDisk() && musicStoragePath.isNotBlank()) {
                    val savedPath = audioWriter.save(targetTrackId, cleanTitle, cleanArtist, "1", audioData, coverUri)
                    onTrackSavedToDisk?.invoke()
                    val resolvedFile = localTrackResolver.findLocalTrackFile(
                        trackId = targetTrackId,
                        artist = cleanArtist,
                        title = cleanTitle,
                        selectedQuality = "1"
                    ) ?: savedPath
                    currentPlayingFilePath = resolvedFile
                    audioPlayer.playFromFile(resolvedFile)
                } else {
                    currentPlayingFilePath = null
                    audioPlayer.playFromBytes(audioData.result)
                }

                isPlaying = true
                isPaused = false
                isNextLoading = false
                isPrevLoading = false
                isPlayLoading = false
                releasePlaybackWakeLock()
                startProgressPolling()
                planNextAndPreload()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                releasePlaybackWakeLock()
                isNextLoading = false
                isPrevLoading = false
                isPlayLoading = false
                io.github.audiz.util.AppLogger.e("PlaybackManager", "Ошибка LQ fallback: ${e.message}", e)
                onError("Ошибка LQ fallback: ${e.message}")
            } finally {
                val currentJob = coroutineContext[Job]
                if (currentJob == null || !currentJob.isCancelled || playTrackJob === currentJob || playTrackJob === thisJob) {
                    isNextLoading = false
                    isPrevLoading = false
                    isPlayLoading = false
                }
            }
        }
        playTrackJob = thisJob
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        trackStartSystemTime = currentTimeMillis()
        progressJob = scope.launch {
            var hasStartedPlaying = false
            var checksWithoutPlaying = 0

            while (true) {
                delay(250)
                val isAudioPlaying = audioPlayer.isPlaying()

                if (isAudioPlaying) {
                    hasStartedPlaying = true
                    checksWithoutPlaying = 0
                    positionMs = audioPlayer.getCurrentPositionMs()
                    if (positionMs > 0L) transitionLead.onAudioAdvancing()
                    val audioDur = audioPlayer.getDurationMs()
                    if (audioDur > 0 && durationMs != audioDur) {
                        durationMs = audioDur
                        trackId?.let { id ->
                            findTrackInfo(id)?.copy(durationMs = audioDur)?.let { onTrackMetadataChanged?.invoke(it) }
                        }
                        systemMediaControls.updateMetadata(
                            title = trackTitle,
                            artist = artistName,
                            album = "",
                            durationMs = durationMs,
                            trackId = trackId,
                            coverUri = coverUri,
                            isLiked = isTrackLiked?.invoke(trackId) == true
                        )
                    }

                    systemMediaControls.updatePlaybackState(true, false, positionMs)

                    val totalDur = if (durationMs > 0) durationMs else audioDur
                    val playedSinceStartMs = currentTimeMillis() - trackStartSystemTime

                    // Сбрасываем флаг автоперехода только когда новый трек уверенно заиграл (> 2.5 сек)
                    if (hasTriggeredAutoAdvance && playedSinceStartMs > 1500L && (positionMs > 2500L || (totalDur in 1..5000L && positionMs > totalDur / 2))) {
                        hasTriggeredAutoAdvance = false
                    }

                    if ((bitrate == null || bitrate == 0) && durationMs > 0) {
                        val sizeBytes = currentPlayingSizeBytes
                            ?: currentPlayingFilePath?.let { getFileSize(it) }?.takeIf { it > 0 }
                        if (sizeBytes != null && sizeBytes > 0) {
                            val calculated = ((sizeBytes * 8) / durationMs).toInt()
                            if (calculated > 0) {
                                bitrate = calculated
                            }
                        }
                        if (bitrate == null || bitrate == 0) {
                            bitrate = if (isCurrentTrackHQ) 320 else 192
                        }
                    }

                    // 📻 Радио-стык / Кроссфейд: автопереход к следующему треку заранее
                    val crossfadeMs = crossfadeSeconds * 1000L
                    val maxAllowedCrossfade = (totalDur * 0.35f).toLong()
                    val effectiveCrossfadeMs = effectiveCrossfadeMs(totalDur)
                    val isNextLocal = isNextTrackLocal()
                    val isNextPreloaded = isNextTrackPreloaded()

                    val leadHeadroomMs = transitionLead.leadMs(
                        hasCrossfade = crossfadeMs > 0L,
                        isNextLocal = isNextLocal,
                        isNextPreloaded = isNextPreloaded
                    )
                    val triggerThresholdMs = if (crossfadeMs > 0L) {
                        (effectiveCrossfadeMs + leadHeadroomMs).coerceAtMost(maxAllowedCrossfade + leadHeadroomMs).coerceAtMost((totalDur * 0.45f).toLong())
                    } else {
                        leadHeadroomMs
                    }
                    audioPlayer.updateNearEndThreshold(triggerThresholdMs, totalDur)

                    // Обнаружение тишины в хвосте трека (активно как при радио-кроссфейде, так и без него)
                    val isSilenceAtTail = io.github.audiz.dsp.AudioVisualizer.isSilenceDetectedInTail(positionMs, totalDur)
                    val isNearTrackEnd = if (crossfadeMs > 0L) {
                        positionMs >= (totalDur - triggerThresholdMs)
                    } else {
                        false
                    }

                    if (!hasTriggeredAutoAdvance && !isSeeking && totalDur > 6000L && playedSinceStartMs > 3000L && (isNearTrackEnd || isSilenceAtTail)) {
                        hasTriggeredAutoAdvance = true
                        if (effectiveCrossfadeMs > 0L) {
                            transitionLead.onTransitionRequested(isNextLocal, isNextPreloaded)
                        }
                        println("PlaybackManager: 📻 Автопереключение на следующий трек (кроссфейд: ${effectiveCrossfadeMs}мс, позиция: $positionMs / $totalDur мс, тишина: $isSilenceAtTail)")
                        advanceAfterTrackEnd(crossfadeMs = effectiveCrossfadeMs)
                    }
                } else if (!isPaused && isPlaying) {
                    if (isSeeking) {
                        continue
                    }
                    if (!hasStartedPlaying) {
                        checksWithoutPlaying++
                        if (checksWithoutPlaying < 48) { // 12 секунд на сетевой запуск и буферизацию
                            continue
                        }
                        println("PlaybackManager: Трек не запустился за 12 секунд -> ошибка запуска")
                        handleTrackFinished(isNaturalCompletion = false)
                        break
                    }

                    // Трек уже играл, но временно не играет (буферизация, системная пауза или естественный конец)
                    val currentPos = audioPlayer.getCurrentPositionMs()
                    val totalDur = if (durationMs > 0) durationMs else audioPlayer.getDurationMs()
                    val nearEndThreshold = if (crossfadeSeconds > 0) ((crossfadeSeconds * 1000L) + 3000L).coerceAtMost((totalDur * 0.45f).toLong()) else 3500L
                    val isNearEnd = totalDur > 0 && (currentPos >= (totalDur - nearEndThreshold) || (totalDur > 10000L && currentPos >= totalDur * 0.95f))

                    val timeSinceStart = currentTimeMillis() - trackStartSystemTime
                    if (isNearEnd && timeSinceStart > 3000L) {
                        println("PlaybackManager: Трек завершился около конца ($currentPos / $totalDur мс)")
                        if (!hasTriggeredAutoAdvance) {
                            handleTrackFinished(isNaturalCompletion = true)
                        }
                        break
                    }

                    checksWithoutPlaying++
                    if (checksWithoutPlaying < 12) { // даем 3 секунды на буферизацию
                        continue
                    }

                    // Если через 3 секунды плеер все еще не играет и пауза не была нажата пользователем:
                    // Синхронизируем стейт с реальным состоянием аудиоустройства (звонок, отключение BT)
                    if (!audioPlayer.isPlaying()) {
                        println("PlaybackManager: Воспроизведение остановлено в середине трека -> синхронизируем паузу")
                        positionMs = currentPos
                        isPaused = true
                        systemMediaControls.updatePlaybackState(isPlaying = false, isPaused = true, positionMs)
                        break
                    }
                }
            }
        }
    }

    private var isFallbackDownloading = false

    private suspend fun handlePlaybackError(errorMsg: String) {
        println("PlaybackManager: ⚠️ Ошибка воспроизведения: $errorMsg (trackId=$trackId, currentPlayingFilePath=$currentPlayingFilePath)")
        val currentId = trackId
        val currentTitle = trackTitle
        val currentArtist = artistName

        if (currentId != null && !currentId.startsWith("local:") && currentPlayingFilePath == null && !isFallbackDownloading) {
            println("PlaybackManager: 🔄 Потоковое воспроизведение не удалось. Переключаемся на прямое скачивание для трека $currentTitle ($currentId)...")
            isFallbackDownloading = true
            try {
                val selectedQuality = getSelectedQuality()
                val audioData = repository.downloadTrackAudio(currentId, quality = selectedQuality)

                if (trackId != currentId) {
                    println("PlaybackManager: Трек сменился во время фоллбэк-скачивания ($currentId -> $trackId), отменяем воспроизведение")
                    return
                }

                val cleanTitle = currentTitle.trim()
                val cleanArtist = currentArtist.trim()
                applyDownloadedAudioInfo(audioData, currentId, selectedQuality)

                if (isRecordToDisk()) {
                    val filePath = audioWriter.save(currentId, cleanTitle, cleanArtist, selectedQuality, audioData, coverUri)
                    onTrackSavedToDisk?.invoke()
                    currentPlayingFilePath = filePath
                    audioPlayer.playFromFile(filePath, 0L)
                } else {
                    currentPlayingFilePath = null
                    audioPlayer.playFromBytes(audioData.result, 0L)
                }

                isPlaying = true
                isPaused = false
                isNextLoading = false
                isPrevLoading = false
                isPlayLoading = false
                releasePlaybackWakeLock()
                systemMediaControls.updateMetadata(
                    cleanTitle,
                    cleanArtist,
                    "",
                    durationMs,
                    currentId,
                    coverUri,
                    isLiked = isTrackLiked?.invoke(currentId) == true
                )
                systemMediaControls.updatePlaybackState(true, false, 0L)
                startProgressPolling()
                planNextAndPreload()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("PlaybackManager: ❌ Прямое скачивание после ошибки стриминга завершилось ошибкой: ${e.message}")
            } finally {
                isFallbackDownloading = false
            }
        }

        isNextLoading = false
        isPrevLoading = false
        isPlayLoading = false
        isPlaying = false
        isPaused = false
        releasePlaybackWakeLock()
        val fullError = "Ошибка воспроизведения: $errorMsg"
        io.github.audiz.util.AppLogger.e("PlaybackManager", fullError)
        onError(fullError)
        trackId?.let { skipFailedUpNext(it) }
    }

    fun handleTrackFinished(isNaturalCompletion: Boolean = false) {
        scope.launch {
            if (!isPlaying && !isNaturalCompletion) return@launch
            if (isSeeking) return@launch

            val wasAutoTriggered = hasTriggeredAutoAdvance
            hasTriggeredAutoAdvance = true
            acquirePlaybackWakeLock()

            progressJob?.cancel()
            val prevId = trackId
            val prevTitle = trackTitle
            val prevArtist = artistName
            isPlaying = false
            trackId = null
            positionMs = durationMs

            if (prevId != null) {
                if (isNaturalCompletion) {
                    if (LocalStreamProxy.isRunning()) {
                        LocalStreamProxy.onTrackCompleted(prevId, if (isCurrentTrackHQ) "2" else "1")
                    }
                    val naturalCrossfadeMs = if (wasAutoTriggered) {
                        (crossfadeSeconds * 1000L).takeIf { it > 0L } ?: 0L
                    } else {
                        0L // Если трек физически уже завершился (EOF) до автоперехода — играем следующий сразу на 100% громкости
                    }
                    advanceAfterTrackEnd(prevId, naturalCrossfadeMs)
                } else {
                    if (upNextQueue.state.current?.inserted == true) {
                        onError("Не удалось воспроизвести трек очереди: $prevTitle")
                        skipFailedUpNext(prevId)
                        return@launch
                    }
                    println("⚠️ Трек упал или не смог запуститься.")
                    if (prevId.startsWith("local:")) {
                        onError("Ошибка: не удалось воспроизвести локальный файл.")
                        playNextTrack(prevId, source = TrackPlaySource.PLAY, crossfadeMs = 0L)
                    } else if (isCurrentTrackHQ) {
                        println("♻️ Fallback: Пытаемся скачать и воспроизвести LQ версию...")
                        isCurrentTrackHQ = false
                        playTrackForceLQ(prevId, prevTitle, prevArtist, source = TrackPlaySource.PLAY)
                    } else {
                        onError("Ошибка: не удалось воспроизвести трек даже в LQ.")
                        advanceAfterTrackEnd(prevId, 0L, finished = false)
                    }
                }
            }
        }
    }

    /**
     * Переход к следующему треку в очереди
     */
    fun playNextTrack(currentId: String? = trackId, source: TrackPlaySource = TrackPlaySource.NEXT, crossfadeMs: Long = 0L) {
        lastPrevClickTimestamp = 0L
        if (source == TrackPlaySource.NEXT) setLoadingFor(source)
        when (val target = navigator.resolveNext(currentId ?: trackId)) {
            is NavigationTarget.UpNext -> playUpNextTrack(target.track, source, crossfadeMs)
            NavigationTarget.Wave -> waveBridge?.playNextWaveTrack(crossfadeMs)
            is NavigationTarget.Track -> playNavigationTrack(target, source, crossfadeMs)
            is NavigationTarget.LoadMoreAfter -> loadMoreAndPlayNext(target.currentIndex, source, crossfadeMs)
            NavigationTarget.WaveFallback -> waveBridge?.playNextWaveTrack()
            NavigationTarget.None -> isNextLoading = false
        }
    }

    /** Последний трек списка: догружаем следующую страницу, иначе начинаем список заново. */
    private fun loadMoreAndPlayNext(currentIndex: Int, source: TrackPlaySource, crossfadeMs: Long) {
        if (source == TrackPlaySource.NEXT) isNextLoading = true
        scope.launch {
            try {
                val loaded = queueSource.loadMoreTracks()
                val updatedTracks = queueSource.getLoadedTracks()
                if (loaded && currentIndex + 1 < updatedTracks.size) {
                    playNavigationTrack(updatedTracks[currentIndex + 1], source, crossfadeMs)
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("PlaybackManager: Ошибка догрузки следующей страницы: ${e.message}")
                if (source == TrackPlaySource.NEXT) isNextLoading = false
            }
            val firstTrack = queueSource.getLoadedTracks().firstOrNull()
            if (firstTrack != null) {
                playNavigationTrack(firstTrack, source, crossfadeMs)
            } else if (source == TrackPlaySource.NEXT) {
                isNextLoading = false
            }
        }
    }

    private fun playNavigationTrack(track: FullTrackInfo, source: TrackPlaySource, crossfadeMs: Long) =
        playNavigationTrack(NavigationTarget.Track(track, track.albums.firstOrNull()?.id), source, crossfadeMs)

    private fun playNavigationTrack(target: NavigationTarget.Track, source: TrackPlaySource, crossfadeMs: Long = 0L) {
        playTrack(
            targetTrackId = target.track.id,
            targetTrackTitle = target.track.title,
            targetArtistName = target.track.artists.joinToString { it.name },
            targetAlbumId = target.albumId,
            isManualSelection = false,
            source = source,
            crossfadeMs = crossfadeMs
        )
    }

    /**
     * Переход к предыдущему треку в очереди (или перезапуск текущего, если прошло > 5 сек)
     */
    fun playPrevTrack(currentId: String? = trackId, source: TrackPlaySource = TrackPlaySource.PREV) {
        try {
            val now = currentTimeMillis()
            val currentPos = if (positionMs > 0L) positionMs else audioPlayer.getCurrentPositionMs()
            val timeSinceLastPrev = now - lastPrevClickTimestamp

            // 🔄 Если трек уже играет больше 5 секунд и это НЕ быстрый повторный клик (прошло > 2 сек):
            // перематываем текущий трек на начало и запускаем воспроизведение
            if (trackId != null && currentPos > PREV_RESTART_THRESHOLD_MS && timeSinceLastPrev > PREV_DOUBLE_CLICK_WINDOW_MS) {
                println("PlaybackManager: ⏮ Трек играет $currentPos мс (> 5 сек) -> перезапуск с начала")
                lastPrevClickTimestamp = now
                seekTo(0L)
                if (isPaused) {
                    audioPlayer.resume()
                    isPaused = false
                    systemMediaControls.updatePlaybackState(isPlaying = true, isPaused = false, 0L)
                    startProgressPolling()
                } else if (!isPlaying) {
                    togglePlayPause()
                }
                return
            }

            // Если сработал переход к предыдущему треку — сбрасываем таймер быстрого клика
            lastPrevClickTimestamp = 0L

            if (source == TrackPlaySource.PREV) setLoadingFor(source)
            when (val target = navigator.resolvePrevious(currentId ?: trackId)) {
                is NavigationTarget.UpNext -> playUpNextTrack(target.track, source)
                NavigationTarget.Wave, NavigationTarget.WaveFallback -> waveBridge?.playPrevWaveTrack()
                is NavigationTarget.Track -> playNavigationTrack(target, source)
                is NavigationTarget.LoadMoreAfter, NavigationTarget.None -> isPrevLoading = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            io.github.audiz.util.AppLogger.e("PlaybackManager", "Ошибка playPrevTrack: ${t.message}", t)
            isPrevLoading = false
        }
    }

    /**
     * ⏭ Вычислить кандидата на следующий трек в очереди с учетом приоритетов:
     * 1. Очередь «Далее»
     * 2. Моя Волна (getNextWaveTrack)
     * 3. История перемотки вперед (forwardHistory)
     * 4. Текущий список треков (loadedTracks) со случайным (Shuffle) или последовательным порядком
     * 5. Результаты поиска (searchTracks)
     */
    fun calculateNextTrackCandidate(): FullTrackInfo? = navigator.calculateNextTrackCandidate(trackId)

    /** Следующий трек запустится без сети: файл на диске или аудио уже в памяти. */
    private fun isNextTrackLocal(): Boolean =
        preloader.isLocallyAvailable(queueManager.plannedNextTrack, allowDiskLookup = currentPlayingFilePath != null)

    /** Для следующего трека уже подготовлено начало потока. */
    private fun isNextTrackPreloaded(): Boolean = preloader.isPreloaded(queueManager.plannedNextTrack?.id)

    /**
     * ⚡ Запуск фоновой предзагрузки запланированного следующего трека
     */
    fun schedulePreload() = preloader.schedule(plannedNextTrack)

    /** Пересчитать следующий трек после запуска текущего и начать его предзагрузку. */
    private fun planNextAndPreload() {
        queueManager.plannedNextTrack = calculateNextTrackCandidate()
        schedulePreload()
    }

    /** Применить длительность, размер и битрейт полученного по сети аудио к текущему треку. */
    private fun applyDownloadedAudioInfo(audio: DownloadedTrackAudio, forTrackId: String, quality: String) {
        positionMs = 0L
        durationMs = findTrackInfo(forTrackId)?.durationMs?.takeIf { it > 0 } ?: 0L
        currentPlayingSizeBytes = audio.result.size.toLong()
        bitrate = DownloadedAudioWriter.resolveBitrate(audio, durationMs) ?: DownloadedAudioWriter.defaultBitrate(quality)
        isCurrentTrackHQ = (quality == "2")
    }

    /** Длительность кроссфейда с учётом ограничения в 35% длины трека. */
    private fun effectiveCrossfadeMs(totalDurationMs: Long): Long {
        val crossfadeMs = crossfadeSeconds * 1000L
        return if (crossfadeMs > 0L) minOf(crossfadeMs, (totalDurationMs * 0.35f).toLong()) else 0L
    }

    /**
     * 🌊 Уведомление об изменении очереди (например, дозагрузка треков в Моей Волне)
     */
    fun onQueueUpdated() {
        val candidate = calculateNextTrackCandidate()
        if (candidate?.id != plannedNextTrack?.id) {
            plannedNextTrack = candidate
            schedulePreload()
        }
    }

    fun release() {
        preloader.reset()
        plannedNextTrack = null
        playTrackJob?.cancel()
        progressJob?.cancel()
        audioPlayer.release()
        systemMediaControls.release()
        LocalStreamProxy.stop()
    }
}
