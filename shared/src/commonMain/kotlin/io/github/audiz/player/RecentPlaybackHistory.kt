package io.github.audiz.player

import io.github.audiz.DispatcherIO
import io.github.audiz.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal expect fun readRecentPlaybackHistory(): String?
internal expect fun writeRecentPlaybackHistory(value: String)

/** Keeps the last 1,000 playback starts in order, including multiple plays of the same track. */
internal class RecentPlaybackHistory(
    scope: CoroutineScope,
    private val read: () -> String? = ::readRecentPlaybackHistory,
    private val write: (String) -> Unit = ::writeRecentPlaybackHistory,
) {
    private val plays = ArrayDeque<String>()
    private val counts = mutableMapOf<String, Int>()
    private val loaded = CompletableDeferred<Unit>()
    private val pendingWrites = Channel<Unit>(Channel.CONFLATED)

    init {
        // The caller and the in-memory history share the same scope dispatcher.
        scope.launch {
            val saved = withContext(DispatcherIO) {
                try {
                    read()?.let { Json.decodeFromString<List<String>>(it) }.orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("RecentPlaybackHistory", "Не удалось открыть историю воспроизведения", e)
                    emptyList()
                }
            }
            if (saved.isNotEmpty()) {
                val earlyPlays = plays.toList()
                plays.clear()
                counts.clear()
                saved.takeLast(LIMIT).forEach(::append)
                earlyPlays.forEach(::append)
            }
            loaded.complete(Unit)
            for (signal in pendingWrites) {
                val snapshot = plays.toList()
                withContext(DispatcherIO) {
                    try {
                        write(Json.encodeToString(snapshot))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("RecentPlaybackHistory", "Не удалось сохранить историю воспроизведения", e)
                    }
                }
            }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    fun record(trackId: String) {
        val id = canonicalId(trackId)
        if (id.isBlank()) return
        append(id)
        pendingWrites.trySend(Unit)
    }

    operator fun contains(trackId: String): Boolean = canonicalId(trackId) in counts

    private fun append(id: String) {
        if (plays.size == LIMIT) {
            val oldest = plays.removeFirst()
            val previousCount = counts.getValue(oldest)
            if (previousCount == 1) counts.remove(oldest) else counts[oldest] = previousCount - 1
        }
        plays.addLast(id)
        counts[id] = (counts[id] ?: 0) + 1
    }

    private fun canonicalId(trackId: String): String {
        val id = trackId.trim()
        val catalogId = id.removePrefix("local:").substringBefore(':').toLongOrNull()
        return catalogId?.takeIf { it > 0 }?.toString() ?: id
    }

    private companion object {
        const val LIMIT = 1_000
    }
}
