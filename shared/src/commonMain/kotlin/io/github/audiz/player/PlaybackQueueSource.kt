package io.github.audiz.player

import io.github.audiz.models.FullTrackInfo

interface PlaybackQueueSource {
    fun getLoadedTracks(): List<FullTrackInfo>

    fun getSearchTracks(): List<FullTrackInfo> = emptyList()

    /** Асинхронная дозагрузка следующей страницы плейлиста. Возвращает true, если загружены новые треки */
    suspend fun loadMoreTracks(): Boolean = false
}
