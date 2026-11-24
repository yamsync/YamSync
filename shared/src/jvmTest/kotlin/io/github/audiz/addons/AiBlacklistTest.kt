package io.github.audiz.addons

import io.github.audiz.TrackPlaySource
import io.github.audiz.api.MusicRepository
import io.github.audiz.models.FullArtistInfo
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.wave.WaveManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiBlacklistTest {
    @Test
    fun waveKeepsTracksByLikedArtistsAndUsesTheFilterOnceLikesAreAvailable() {
        val blacklist = AiBlacklist(version = 1, artists = setOf(12), tracks = setOf(202))
        val track = FullTrackInfo(id = "202", artists = listOf(FullArtistInfo(id = 11), FullArtistInfo(id = 12)))

        assertFalse(blacklist.shouldSkipInWave(track, likedArtistIds = null) { false })
        assertFalse(blacklist.shouldSkipInWave(track, likedArtistIds = setOf(12)) { false })
        assertFalse(blacklist.shouldSkipInWave(track, likedArtistIds = emptySet()) { true })
        assertTrue(blacklist.shouldSkipInWave(track, likedArtistIds = emptySet()) { false })
        assertFalse(blacklist.shouldSkipInWave(track.copy(id = "303"), likedArtistIds = emptySet()) { false })
    }

    @Test
    fun bundledSnapshotLoadsFromComposeResources() = runBlocking {
        val blacklist = loadBundledAiBlacklist()

        assertEquals(18, blacklist.version)
        assertTrue(blacklist.containsArtist(4429238))
        assertTrue(blacklist.containsTrack("48580139:42"))
        assertFalse(blacklist.containsTrack("local:48580139"))
        assertFalse(blacklist.containsTrack("/music/48580139.mp3"))
        assertFalse(blacklist.containsTrack("4429238"))
        assertFalse(blacklist.containsArtist(0))
    }

    @Test
    fun trackMatchTakesPriorityAndConsidersAllCreditedArtists() {
        val blacklist = AiBlacklist(version = 1, artists = setOf(12), tracks = setOf(202))
        val artists = listOf(FullArtistInfo(id = 11), FullArtistInfo(id = 12))

        assertEquals(AiMatch.TRACK, blacklist.matchTrack("202", artists))
        assertEquals(AiMatch.ARTIST, blacklist.matchTrack("303", artists))
        assertNull(blacklist.matchTrack("303", artists.take(1)))
    }

    @Test
    fun waveContinuesWithoutWaitingForTheDatabaseAndOnlySkipsListedTracks() = runBlocking {
        var blacklist: AiBlacklist? = null
        val tracks = listOf(
            FullTrackInfo(id = "101"),
            FullTrackInfo(id = "202"),
            FullTrackInfo(id = "303", artists = listOf(FullArtistInfo(id = 12)))
        )
        val wave = WaveManager(
            scope = this,
            repository = MusicRepository(),
            getAccessToken = { "" },
            shouldSkipRecommendedTrack = { blacklist?.containsTrack(it.id) == true }
        )
        wave.installInitialTracks(tracks)
        wave.playWaveTrack(0, TrackPlaySource.PLAY)
        assertEquals("202", wave.getNextWaveTrackCandidate()?.id)

        wave.onWaveTrackFinished(0L)
        assertEquals("202", wave.currentWaveTrack?.id)
        blacklist = AiBlacklist(version = 1, artists = setOf(12), tracks = setOf(202))
        assertEquals("202", wave.currentWaveTrack?.id)

        wave.playWaveTrack(0, TrackPlaySource.PLAY)
        assertEquals("303", wave.getNextWaveTrackCandidate()?.id)
        wave.onWaveTrackFinished(0L)
        assertEquals("303", wave.currentWaveTrack?.id)
    }

    @Test
    fun waveFallsBackWhenEveryBufferedTrackIsMarked() = runBlocking {
        val wave = WaveManager(this, MusicRepository(), { "" }, shouldSkipRecommendedTrack = { true })
        wave.installInitialTracks(listOf(FullTrackInfo(id = "101"), FullTrackInfo(id = "202")))
        wave.playWaveTrack(0, TrackPlaySource.PLAY)
        assertEquals("202", wave.getNextWaveTrackCandidate()?.id)
        wave.onWaveTrackFinished(0L)
        assertEquals("202", wave.currentWaveTrack?.id)
        assertTrue(wave.isWaveMode)
    }
}
