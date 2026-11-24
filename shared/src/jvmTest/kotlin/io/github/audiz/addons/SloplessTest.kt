package io.github.audiz.addons

import androidx.compose.runtime.snapshotFlow
import io.github.audiz.TrackPlaySource
import io.github.audiz.api.MusicRepository
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.wave.WaveManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SloplessTest {
    @Test
    fun wavePlaysWithoutWaitingForSlowLookupAndSkipsOnlyFromCache() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<SloplessAssessment?>()
        val service = Slopless(this, isEnabled = { true }) { _, id ->
            assertEquals("202", id)
            started.complete(Unit)
            response.await()
        }
        try {
            val wave = WaveManager(
                scope = this,
                repository = MusicRepository(),
                getAccessToken = { "" },
                shouldSkipRecommendedTrack = { service.isAiTrack(it.id) }
            )
            wave.installInitialTracks(listOf("101", "202", "303").map { FullTrackInfo(id = it) })
            wave.playWaveTrack(0, TrackPlaySource.PLAY)
            service.request(SloplessEntity.TRACK, "202")
            withTimeout(2_000) { started.await() }

            assertFalse(service.isAiTrack("202"))
            assertEquals("202", wave.getNextWaveTrackCandidate()?.id)
            wave.onWaveTrackFinished(0L)
            assertEquals("202", wave.currentWaveTrack?.id)

            response.complete(parseSloplessAssessment(SloplessEntity.TRACK, "202", """{"id":202,"score":-1,"scoreYandex":1}"""))
            withTimeout(2_000) { snapshotFlow { service.isAiTrack("202") }.first { it } }
            assertEquals("202", wave.currentWaveTrack?.id, "A late result must not interrupt the current track")

            wave.playWaveTrack(0, TrackPlaySource.PLAY)
            assertEquals("303", wave.getNextWaveTrackCandidate()?.id)
            wave.onWaveTrackFinished(0L)
            assertEquals("303", wave.currentWaveTrack?.id)
        } finally {
            service.close()
        }
    }

    @Test
    fun concurrentRequestsAreLimitedAndDuplicateTrackIdsAreCoalesced() = runBlocking<Unit> {
        val started = Channel<Pair<String, CompletableDeferred<Double>>>(Channel.UNLIMITED)
        val service = Slopless(this, isEnabled = { true }) { _, id ->
            val response = CompletableDeferred<Double>()
            started.send(id to response)
            SloplessAssessment(classifierScore = response.await())
        }
        try {
            listOf("101", "101:55", "202", "303").forEach { service.request(SloplessEntity.TRACK, it) }
            val first = withTimeout(1_000) { started.receive() }
            val second = withTimeout(1_000) { started.receive() }
            assertEquals(setOf("101", "202"), setOf(first.first, second.first))
            assertTrue(started.tryReceive().isFailure)

            first.second.complete(0.1)
            val third = withTimeout(1_000) { started.receive() }
            assertEquals("303", third.first)
            second.second.complete(0.1)
            third.second.complete(0.1)
        } finally {
            service.close()
        }
    }

    @Test
    fun failedLookupLeavesWavePlayable() = runBlocking {
        val requested = CompletableDeferred<Unit>()
        val service = Slopless(this, isEnabled = { true }) { _, _ ->
            requested.complete(Unit)
            error("Service unavailable")
        }
        try {
            service.request(SloplessEntity.TRACK, "202")
            withTimeout(2_000) { requested.await() }
            assertFalse(service.isAiTrack("202"))
            val wave = WaveManager(this, MusicRepository(), { "" }, shouldSkipRecommendedTrack = { service.isAiTrack(it.id) })
            wave.installInitialTracks(listOf(FullTrackInfo(id = "101"), FullTrackInfo(id = "202")))
            wave.playWaveTrack(0, TrackPlaySource.PLAY)
            wave.onWaveTrackFinished(0L)
            assertEquals("202", wave.currentWaveTrack?.id)
        } finally {
            service.close()
        }
    }

    @Test
    fun parserSeparatesArtistShareFromTrackScoreAndIgnoresUnknownScores() {
        val artist = parseSloplessAssessment(SloplessEntity.ARTIST, "1", """{"id":1,"aiTracks":12,"totalTracks":100}""")
        val track = parseSloplessAssessment(SloplessEntity.TRACK, "2", """{"id":2,"score":0.85,"scoreYandex":null}""")
        assertTrue(artist?.isAiArtist(0.12) == true)
        assertFalse(artist?.isAiArtist(0.13) == true)
        assertTrue(track?.isAiTrack == true)
        assertNull(parseSloplessAssessment(SloplessEntity.TRACK, "2", """{"id":2,"score":-1}"""))
        assertNull(parseSloplessAssessment(SloplessEntity.TRACK, "2", """{"id":2,"score":null}"""))
        assertNull(parseSloplessAssessment(SloplessEntity.TRACK, "2", """{"id":3,"score":0.9,"scoreYandex":1}"""))
    }

    @Test
    fun yandexTrackStatusesCanMarkTracksWithoutAClassifierScore() {
        for (status in 0..4) {
            val track = parseSloplessAssessment(SloplessEntity.TRACK, "2", """{"id":2,"score":-1,"scoreYandex":$status}""")
            assertEquals(status in 1..3, track?.isAiTrack == true, "Yandex status $status")
        }
    }

    @Test
    fun yandexArtistShareUsesItsOwnCheckedTrackCount() {
        val artist = parseSloplessAssessment(
            SloplessEntity.ARTIST, "1", """{"id":1,"aiTracks":0,"totalTracks":100,"yandexAiTracks":2,"yandexCheckedTracks":10}"""
        )
        assertTrue(artist?.isAiArtist(0.20) == true)
        assertFalse(artist?.isAiArtist(0.21) == true)
        val unchecked = parseSloplessAssessment(
            SloplessEntity.ARTIST, "1", """{"id":1,"yandexAiTracks":2,"yandexCheckedTracks":0}"""
        )
        assertNull(unchecked)
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
