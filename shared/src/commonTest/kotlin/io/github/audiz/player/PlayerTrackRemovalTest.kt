package io.github.audiz.player

import io.github.audiz.models.FullTrackInfo
import io.github.audiz.models.PlaylistInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerTrackRemovalTest {
    @Test
    fun downloadedPlaylistTrackDefaultsToRemovingEntriesAndFile() {
        val target = target(
            filePath = "/music/track.flac",
            yandexPlaylists = listOf(PlaylistInfo(uid = 1, title = "Mix", trackCount = 1))
        )
        assertEquals(TrackRemovalSelection(removeFromPlaylists = true, deleteFile = true), target.defaultSelection())
    }

    @Test
    fun trackOutsidePlaylistsDefaultsToRecommendationsOnlyEvenWithAFile() {
        val target = target(filePath = "/music/track.flac")
        assertEquals(TrackRemovalSelection(changeRecommendations = true), target.defaultSelection())
        assertEquals(TrackRemovalSelection(changeRecommendations = true), target.copy(filePath = null).defaultSelection())
    }

    @Test
    fun playlistTrackWithoutAFileDefaultsToRemovingOnlyPlaylistEntries() {
        val target = target().copy(localPlaylistIds = setOf("offline"))
        assertEquals(TrackRemovalSelection(removeFromPlaylists = true), target.defaultSelection())
    }

    @Test
    fun localFileWithoutAYandexIdDefaultsToDeletingTheFile() {
        val target = target(filePath = "/music/local.flac").copy(recommendationTrackId = null)
        assertEquals(TrackRemovalSelection(deleteFile = true), target.defaultSelection())
    }

    private fun target(filePath: String? = null, yandexPlaylists: List<PlaylistInfo> = emptyList()) =
        PlayerTrackRemoval(
            track = FullTrackInfo(id = "123"),
            filePath = filePath,
            localTrackPaths = emptySet(),
            localPlaylistIds = emptySet(),
            yandexPlaylists = yandexPlaylists,
            recommendationTrackId = "123",
            isHiddenFromRecommendations = false
        )
}
