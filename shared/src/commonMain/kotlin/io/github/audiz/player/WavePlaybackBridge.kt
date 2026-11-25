package io.github.audiz.player

import io.github.audiz.TrackPlaySource
import io.github.audiz.models.FullTrackInfo

interface WavePlaybackBridge {
    val isWaveMode: Boolean

    val waveTracks: List<FullTrackInfo>

    val waveCurrentIndex: Int

    fun playWaveTrack(index: Int = 0, source: TrackPlaySource = TrackPlaySource.PLAY, crossfadeMs: Long = 0L)

    fun playNextWaveTrack(crossfadeMs: Long = 0L)

    fun playPrevWaveTrack()

    fun onWaveTrackFinished(crossfadeMs: Long = 0L)

    /** Учёт завершения текущего трека Волны без перехода (следующим играет трек из очереди «Далее») */
    fun reportWaveTrackFinished(trackId: String?) {}

    fun loadInitialWave(autoPlay: Boolean = false, source: TrackPlaySource = TrackPlaySource.PLAY)

    /** Возобновление или запуск текущей/тематической Волны без сброса стиля */
    fun resumeOrLoadWave(autoPlay: Boolean = true, source: TrackPlaySource = TrackPlaySource.PLAY)

    fun getNextWaveTrackCandidate(): FullTrackInfo?

    fun isRecentWaveRepeat(track: FullTrackInfo): Boolean = false
}
