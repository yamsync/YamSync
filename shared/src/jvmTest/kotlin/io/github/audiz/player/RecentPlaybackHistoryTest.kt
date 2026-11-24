package io.github.audiz.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentPlaybackHistoryTest {
    @Test
    fun restoresAndPersistsOnlyTheLastThousandPlaybackStarts() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val written = CompletableDeferred<String>()
        val saved = (1..999).map(Int::toString) + "1"
        val history = RecentPlaybackHistory(
            scope,
            read = { Json.encodeToString(saved) },
            write = { written.complete(it) },
        )

        try {
            // Both starts arrive before the asynchronous restore finishes.
            history.record("local:1000:42")
            history.record("/music/my track.flac")
            withTimeout(5_000) { history.awaitLoaded() }
            assertTrue("1" in history)
            assertFalse("2" in history)
            assertTrue("1000:55" in history)
            assertTrue("/music/my track.flac" in history)

            val persisted = Json.decodeFromString<List<String>>(withTimeout(5_000) { written.await() })
            assertEquals(1_000, persisted.size)
            assertEquals("3", persisted.first())
            assertEquals(listOf("1", "1000", "/music/my track.flac"), persisted.takeLast(3))
        } finally {
            scope.cancel()
        }
    }
}
