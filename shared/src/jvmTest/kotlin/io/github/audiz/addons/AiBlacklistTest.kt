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
        val blacklist = AiBlacklist.of(version = 1, artists = setOf(12), tracks = setOf(202))
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

        assertEquals(36, blacklist.version)
        assertTrue(blacklist.containsArtist(10017896))
        assertFalse(blacklist.containsArtist(8102741))
        assertTrue(blacklist.containsTrack("146254487:42"))
        assertFalse(blacklist.containsTrack("local:146254487"))
        assertFalse(blacklist.containsTrack("/music/146254487.mp3"))
        assertFalse(blacklist.containsTrack("10017896"))
        assertFalse(blacklist.containsTrack("144137423"))
        assertFalse(blacklist.containsArtist(0))
        // Artist 544 has AI tracks but is not blacklisted as a whole
        assertFalse(blacklist.containsArtist(544))
        assertTrue(blacklist.isSuspectArtist(544))
        assertTrue(blacklist.containsTrack("125627531"))
    }

    @Test
    fun parsesDeltaEncodedSchema3Bundle() {
        val json = """{"version":36,"schema":3,"encoding":"delta","artists":[10,5,85],"tracks":[1000,1],"suspectArtists":[7]}"""
        val blacklist = AiBlacklist.parse(json.encodeToByteArray())

        assertEquals(36, blacklist.version)
        assertTrue(blacklist.containsArtist(10))
        assertTrue(blacklist.containsArtist(15))
        assertTrue(blacklist.containsArtist(100))
        assertFalse(blacklist.containsArtist(5))
        assertTrue(blacklist.containsTrack("1001:77"))
        assertFalse(blacklist.containsTrack("1"))
        assertTrue(blacklist.isSuspectArtist(7))
        assertFalse(blacklist.isSuspectArtist(10))
    }

    @Test
    fun parsesPlainListsWithoutDeltaEncoding() {
        val blacklist = AiBlacklist.parse("""{"version":5,"artists":[10,15],"tracks":[]}""".encodeToByteArray())

        assertEquals(5, blacklist.version)
        assertTrue(blacklist.containsArtist(15))
        assertFalse(blacklist.containsTrack("10"))
        assertFalse(blacklist.isSuspectArtist(10))
    }

    @Test
    fun intIdSetHandlesCollisionsAndOutOfRangeIds() {
        val ids = IntArray(100_000) { it * 64 + 1 }
        val set = IntIdSet.of(ids)

        assertEquals(100_000, set.size)
        assertTrue(ids.all { it.toLong() in set })
        assertFalse(2L in set)
        assertFalse(0L in set)
        assertFalse(-1L in set)
        assertFalse((Int.MAX_VALUE.toLong() + 1) in set)
    }

    @Test
    fun suspectArtistIsReportedOnlyWhenNothingStrongerMatches() {
        val blacklist = AiBlacklist.of(version = 1, artists = setOf(12), tracks = setOf(202), suspectArtists = setOf(13))
        val suspect = listOf(FullArtistInfo(id = 13))

        assertEquals(AiMatch.SUSPECT_ARTIST, blacklist.matchTrack("303", suspect))
        assertEquals(AiMatch.TRACK, blacklist.matchTrack("202", suspect))
        assertEquals(AiMatch.ARTIST, blacklist.matchTrack("303", suspect + FullArtistInfo(id = 12)))
        assertEquals(AiMatch.SUSPECT_ARTIST, blacklist.matchArtist(13))
        assertEquals(AiMatch.ARTIST, blacklist.matchArtist(12))
        assertNull(blacklist.matchArtist(14))
        // Wave auto-skip only acts on tracks listed as AI, never on "possibly AI" artists
        assertFalse(blacklist.shouldSkipInWave(FullTrackInfo(id = "303", artists = suspect), emptySet()) { false })
    }

    @Test
    fun trackMatchTakesPriorityAndConsidersAllCreditedArtists() {
        val blacklist = AiBlacklist.of(version = 1, artists = setOf(12), tracks = setOf(202))
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
        blacklist = AiBlacklist.of(version = 1, artists = setOf(12), tracks = setOf(202))
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
