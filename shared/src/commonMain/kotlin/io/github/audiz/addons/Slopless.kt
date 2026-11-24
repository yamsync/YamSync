package io.github.audiz.addons

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import io.github.audiz.DispatcherIO
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class SloplessMode(val title: String) {
    OFF("Выключено"),
    LABELS("Только метки"),
    SKIP_IN_WAVE("Пропускать в Моей волне")
}

internal enum class SloplessEntity(val path: String) { TRACK("track"), ARTIST("artist") }

/** Запросы ставятся в очередь из UI, а воспроизведение читает только готовый кеш. */
internal class Slopless(
    scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val fetchOverride: (suspend (SloplessEntity, String) -> Double?)? = null
) {
    private data class Key(val entity: SloplessEntity, val id: String)
    private data class Entry(val score: Double?, val expiresAt: TimeMark)

    private val cache = mutableStateMapOf<Key, Entry>()
    private val pending = mutableSetOf<Key>()
    private val requests = Channel<Key>(24)
    private var retryAfter: TimeMark? = null
    private val client = lazy {
        HttpClient {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = 1_500
                connectTimeoutMillis = 1_000
                socketTimeoutMillis = 1_500
            }
        }
    }
    private val workerJob = scope.launch {
        repeat(2) { launch { processRequests() } }
    }

    init {
        workerJob.invokeOnCompletion { if (client.isInitialized()) client.value.close() }
    }

    private suspend fun processRequests() {
        for (key in requests) {
            try {
                if (!isEnabled() || isCoolingDown()) continue
                val score = withTimeoutOrNull(1_500) {
                    withContext(DispatcherIO) {
                        val lookup = fetchOverride ?: ::fetchScore
                        LookupResult(lookup(key.entity, key.id))
                    }
                } ?: error("Slopless lookup timed out")
                val lifetime = if (score.value == null) 5.minutes else 60.minutes
                Snapshot.withMutableSnapshot {
                    if (cache.size >= 512 && key !in cache) {
                        val oldest = cache.minByOrNull { -it.value.expiresAt.elapsedNow() }
                        oldest?.let { cache.remove(it.key) }
                    }
                    cache[key] = Entry(score.value, TimeSource.Monotonic.markNow() + lifetime)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                retryAfter = TimeSource.Monotonic.markNow() + 30.seconds
            } finally {
                pending.remove(key)
            }
        }
    }

    private data class LookupResult(val value: Double?)

    fun request(entity: SloplessEntity, id: String?) {
        val key = key(entity, id) ?: return
        if (!isEnabled() || isCoolingDown() || cache[key]?.expiresAt?.hasNotPassedNow() == true) return
        if (pending.add(key) && !requests.trySend(key).isSuccess) pending.remove(key)
    }

    fun score(entity: SloplessEntity, id: String?): Double? {
        val key = key(entity, id) ?: return null
        return cache[key]?.takeIf { it.expiresAt.hasNotPassedNow() }?.score
    }

    fun isAiTrack(id: String?): Boolean = (score(SloplessEntity.TRACK, id) ?: 0.0) > 0.5

    fun close() {
        requests.cancel()
        workerJob.cancel()
    }

    private fun key(entity: SloplessEntity, id: String?): Key? {
        val numericId = id?.substringBefore(':')?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Key(entity, numericId.toString())
    }

    private fun isCoolingDown(): Boolean = retryAfter?.hasNotPassedNow() == true

    private suspend fun fetchScore(entity: SloplessEntity, id: String): Double? {
        val response = client.value.get("https://slopless.art/api/${entity.path}/$id")
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status == HttpStatusCode.OK)
        return parseSloplessScore(entity, id, response.bodyAsText())
    }
}

internal fun parseSloplessScore(entity: SloplessEntity, id: String, body: String): Double? {
    val fields = Json.parseToJsonElement(body).jsonObject
    if (fields["id"]?.jsonPrimitive?.longOrNull != id.toLongOrNull()) return null
    return when (entity) {
        SloplessEntity.TRACK -> fields["score"]?.jsonPrimitive?.doubleOrNull?.takeIf { it in 0.0..1.0 }
        SloplessEntity.ARTIST -> {
            val total = fields["totalTracks"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
            val ai = fields["aiTracks"]?.jsonPrimitive?.longOrNull?.takeIf { it in 0..total } ?: return null
            ai.toDouble() / total
        }
    }
}
