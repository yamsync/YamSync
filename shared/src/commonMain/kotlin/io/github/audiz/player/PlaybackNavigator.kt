package io.github.audiz.player

import io.github.audiz.models.FullTrackInfo

/** Куда перейти по ⏭ / ⏮ или после окончания трека. */
internal sealed interface NavigationTarget {
    /** Трек из очереди «Далее» (или возврат к исходному списку после неё). */
    data class UpNext(val track: FullTrackInfo) : NavigationTarget

    /** Обычный трек списка или поиска. */
    data class Track(val track: FullTrackInfo, val albumId: Long?) : NavigationTarget

    /** Последний трек списка: сначала догрузить следующую страницу после [currentIndex]. */
    data class LoadMoreAfter(val currentIndex: Int) : NavigationTarget

    /** Активен режим Волны: переход делегируется ей. */
    data object Wave : NavigationTarget

    /** Нет текущего трека, но есть треки Волны. */
    data object WaveFallback : NavigationTarget

    /** Переходить некуда. */
    data object None : NavigationTarget
}

/**
 * 🧭 Выбор следующего и предыдущего трека с учётом приоритетов:
 * очередь «Далее» → Волна → история → текущий список → результаты поиска.
 *
 * Изменяет только состояние очередей ([UpNextQueue], [PlaybackQueueManager]);
 * запуск воспроизведения выполняет [PlaybackManager].
 */
internal class PlaybackNavigator(
    private val upNextQueue: UpNextQueue,
    private val queueManager: PlaybackQueueManager,
    private val queueSource: PlaybackQueueSource,
    private val waveBridge: WavePlaybackBridge?,
    private val findTrackInfo: (String) -> FullTrackInfo?,
) {
    private val isWaveMode: Boolean get() = waveBridge?.isWaveMode == true

    /** Есть незавершённый переход, которым управляет очередь «Далее». */
    val hasUpNextTransition: Boolean get() = upNextQueue.state.let {
        it.pending.isNotEmpty() || it.forward.isNotEmpty() || it.suspended || it.current?.inserted == true
    }

    val isPlayingUpNextTrack: Boolean get() = upNextQueue.state.current?.inserted == true

    fun resolveNext(currentId: String?): NavigationTarget {
        val resumeAnchor = upNextQueue.state.let {
            it.anchorId.takeIf { _ -> it.suspended && it.pending.isEmpty() && it.forward.isEmpty() }
        }
        val currentInfo = currentId?.let(findTrackInfo)
        val queued = upNextQueue.next(
            current = currentInfo,
            resume = {
                queueManager.plannedNextTrack = null
                calculateResumeTrack(currentId)
            },
            shuffleCycle = { queueManager.playedInCycleTrackIds },
            past = historyBeforeUpNext(),
            canResumeTrack = ::canResumeRecommendedTrack,
        )
        if (queued != null) return NavigationTarget.UpNext(queued)
        if (isWaveMode) return NavigationTarget.Wave

        val activeId = resumeAnchor ?: currentId
        val loadedTracks = queueSource.getLoadedTracks()
        val searchTracks = queueSource.getSearchTracks()

        // 1. История перемотки вперед
        val fromForward = if (!upNextQueue.state.started) queueManager.popNextFromForwardHistory(activeId, loadedTracks, searchTracks) else null
        if (fromForward != null) return fromForward.asTarget()

        if (activeId != null && !upNextQueue.state.started) {
            queueManager.recordPlayed(activeId)
        }

        // 2. Текущий плейлист (loadedTracks)
        if (loadedTracks.isNotEmpty()) {
            if (queueManager.isShuffleEnabled) {
                queueManager.getNextFromList(activeId, loadedTracks)?.let { return it.asTarget() }
            } else {
                val currentIndex = if (activeId != null) loadedTracks.indexOfFirst { PlaybackQueueManager.isSameTrack(it, activeId) } else -1
                return when {
                    currentIndex != -1 && currentIndex < loadedTracks.lastIndex -> loadedTracks[currentIndex + 1].asTarget()
                    currentIndex == loadedTracks.lastIndex -> NavigationTarget.LoadMoreAfter(currentIndex)
                    else -> loadedTracks.first().asTarget()
                }
            }
        }

        // 3. Результаты поиска
        if (searchTracks.isNotEmpty()) {
            queueManager.getNextFromList(activeId, searchTracks)?.let { return NavigationTarget.Track(it, albumId = null) }
        }

        // 4. Волна
        return if (activeId == null && waveBridge?.waveTracks.orEmpty().isNotEmpty()) NavigationTarget.WaveFallback
        else NavigationTarget.None
    }

    fun resolvePrevious(activeId: String?): NavigationTarget {
        upNextQueue.previous()?.let { return NavigationTarget.UpNext(it) }
        if (upNextQueue.state.started) return NavigationTarget.None
        if (isWaveMode) return NavigationTarget.Wave

        val loadedTracks = queueSource.getLoadedTracks()
        val searchTracks = queueSource.getSearchTracks()

        // 1. Реальная история прослушивания
        queueManager.popPrevFromPlaybackHistory(activeId, loadedTracks, searchTracks)?.let { return it.asTarget() }

        // 2. Текущий плейлист (loadedTracks)
        if (loadedTracks.isNotEmpty()) {
            if (activeId != null) queueManager.recordForward(activeId)
            queueManager.getPrevFromList(activeId, loadedTracks)?.let { return it.asTarget() }
        }

        // 3. Результаты поиска
        if (searchTracks.isNotEmpty()) {
            if (activeId != null) queueManager.recordForward(activeId)
            queueManager.getPrevFromList(activeId, searchTracks)?.let { return NavigationTarget.Track(it, albumId = null) }
        }

        return if (activeId == null && waveBridge?.waveTracks.orEmpty().isNotEmpty()) NavigationTarget.WaveFallback
        else NavigationTarget.None
    }

    /**
     * ⏭ Кандидат на следующий трек: очередь «Далее», затем Волна, история перемотки вперёд,
     * текущий список (по порядку или Shuffle) и результаты поиска.
     */
    fun calculateNextTrackCandidate(trackId: String?): FullTrackInfo? {
        upNextQueue.peek(::canResumeRecommendedTrack)?.let { return it }
        val candidate = calculateBaseNextTrackCandidate(if (upNextQueue.state.suspended) upNextQueue.state.anchorId else trackId)
        if (upNextQueue.state.current?.inserted != true) {
            upNextQueue.captureShuffleCycle(queueManager.playedInCycleTrackIds)
        }
        return candidate
    }

    /** Трек, к которому вернётся воспроизведение после очереди «Далее». */
    fun calculateResumeTrack(activeId: String?): FullTrackInfo? {
        val originalTracks = queueSource.getLoadedTracks()
        return if (!isWaveMode && !queueManager.isShuffleEnabled &&
            originalTracks.lastOrNull()?.let { PlaybackQueueManager.isSameTrack(it, activeId) } == true) {
            null
        } else calculateBaseNextTrackCandidate(activeId)
    }

    private fun calculateBaseNextTrackCandidate(activeId: String?): FullTrackInfo? =
        queueManager.calculateNextCandidate(
            activeId = activeId,
            isWaveMode = isWaveMode,
            nextWaveTrack = waveBridge?.getNextWaveTrackCandidate(),
            loadedTracks = queueSource.getLoadedTracks(),
            searchTracks = queueSource.getSearchTracks()
        )

    private fun historyBeforeUpNext(): List<FullTrackInfo> {
        if (upNextQueue.state.started || upNextQueue.state.pending.isEmpty()) return emptyList()
        val index = buildMap<String, FullTrackInfo> {
            val tracks = queueSource.getLoadedTracks() + queueSource.getSearchTracks() + waveBridge?.waveTracks.orEmpty()
            for (track in tracks) {
                put(track.id.removePrefix("local:"), track)
                track.realId?.let { put(it.removePrefix("local:"), track) }
            }
        }
        return queueManager.playbackHistory.mapNotNull { index[it.removePrefix("local:")] }
    }

    private fun canResumeRecommendedTrack(track: FullTrackInfo): Boolean =
        waveBridge?.let { !it.isWaveMode || !it.isRecentWaveRepeat(track) } ?: true

    private fun FullTrackInfo.asTarget() = NavigationTarget.Track(this, albums.firstOrNull()?.id)
}
