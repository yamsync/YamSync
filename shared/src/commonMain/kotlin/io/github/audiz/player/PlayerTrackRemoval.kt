package io.github.audiz.player

import io.github.audiz.models.FullTrackInfo
import io.github.audiz.models.PlaylistInfo

internal data class PlayerTrackRemoval(
    val track: FullTrackInfo,
    val filePath: String?,
    val localTrackPaths: Set<String>,
    val localPlaylistIds: Set<String>,
    val yandexPlaylists: List<PlaylistInfo>,
    val recommendationTrackId: String?,
    val isHiddenFromRecommendations: Boolean
) {
    val playlistCount: Int get() = localPlaylistIds.size + yandexPlaylists.size

    fun defaultSelection(): TrackRemovalSelection {
        val hasPlaylists = playlistCount > 0
        return TrackRemovalSelection(
            removeFromPlaylists = hasPlaylists,
            deleteFile = filePath != null && (hasPlaylists || recommendationTrackId == null),
            changeRecommendations = recommendationTrackId != null && !hasPlaylists
        )
    }
}


internal data class TrackRemovalSelection(
    val removeFromPlaylists: Boolean = false,
    val deleteFile: Boolean = false,
    val changeRecommendations: Boolean = false
) {
    val hasActions: Boolean get() = removeFromPlaylists || deleteFile || changeRecommendations
}
