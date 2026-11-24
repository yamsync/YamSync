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

internal data class SloplessAssessment(
    val classifierScore: Double? = null,
    val isTrackMarkedByYandex: Boolean = false,
    val yandexArtistShare: Double? = null
) {
    val isAiTrack: Boolean get() = isTrackMarkedByYandex || (classifierScore ?: 0.0) > 0.5

    fun isAiArtist(threshold: Double): Boolean =
        maxOf(classifierScore ?: 0.0, yandexArtistShare ?: 0.0) >= threshold
}

/** Запросы ставятся в очередь из UI, а воспроизведение читает только готовый кеш. */
internal class Slopless(
    scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val fetchOverride: (suspend (SloplessEntity, String) -> SloplessAssessment?)? = null
) {
    private data class Key(val entity: SloplessEntity, val id: String)
    private data class Entry(val assessment: SloplessAssessment?, val expiresAt: TimeMark)

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
                val result = withTimeoutOrNull(1_500) {
                    withContext(DispatcherIO) {
                        val lookup = fetchOverride ?: ::fetchAssessment
                        LookupResult(lookup(key.entity, key.id))
                    }
                } ?: error("Slopless lookup timed out")
                val lifetime = if (result.value == null) 5.minutes else 60.minutes
                Snapshot.withMutableSnapshot {
                    if (cache.size >= 512 && key !in cache) {
                        val oldest = cache.minByOrNull { -it.value.expiresAt.elapsedNow() }
                        oldest?.let { cache.remove(it.key) }
                    }
                    cache[key] = Entry(result.value, TimeSource.Monotonic.markNow() + lifetime)
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

    private data class LookupResult(val value: SloplessAssessment?)

    fun request(entity: SloplessEntity, id: String?) {
        val key = key(entity, id) ?: return
        if (!isEnabled() || isCoolingDown() || cache[key]?.expiresAt?.hasNotPassedNow() == true) return
        if (pending.add(key) && !requests.trySend(key).isSuccess) pending.remove(key)
    }

    fun assessment(entity: SloplessEntity, id: String?): SloplessAssessment? {
        val key = key(entity, id) ?: return null
        return cache[key]?.takeIf { it.expiresAt.hasNotPassedNow() }?.assessment
    }

    fun isAiTrack(id: String?): Boolean = assessment(SloplessEntity.TRACK, id)?.isAiTrack == true

    fun close() {
        requests.cancel()
        workerJob.cancel()
    }

    private fun key(entity: SloplessEntity, id: String?): Key? {
        val numericId = id?.substringBefore(':')?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Key(entity, numericId.toString())
    }

    private fun isCoolingDown(): Boolean = retryAfter?.hasNotPassedNow() == true

    private suspend fun fetchAssessment(entity: SloplessEntity, id: String): SloplessAssessment? {
        val response = client.value.get("https://slopless.art/api/${entity.path}/$id")
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status == HttpStatusCode.OK)
        return parseSloplessAssessment(entity, id, response.bodyAsText())
    }
}

internal fun parseSloplessAssessment(entity: SloplessEntity, id: String, body: String): SloplessAssessment? {
    val fields = Json.parseToJsonElement(body).jsonObject
    if (fields["id"]?.jsonPrimitive?.longOrNull != id.toLongOrNull()) return null

    fun share(aiKey: String, totalKey: String): Double? {
        val total = fields[totalKey]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
        val ai = fields[aiKey]?.jsonPrimitive?.longOrNull?.takeIf { it in 0..total } ?: return null
        return ai.toDouble() / total
    }

    return when (entity) {
        SloplessEntity.TRACK -> {
            val score = fields["score"]?.jsonPrimitive?.doubleOrNull?.takeIf { it in 0.0..1.0 }
            val yandexStatus = fields["scoreYandex"]?.jsonPrimitive?.longOrNull
            val markedByYandex = yandexStatus != null && yandexStatus in 1L..3L
            if (score == null && !markedByYandex) return null
            SloplessAssessment(classifierScore = score, isTrackMarkedByYandex = markedByYandex)
        }
        SloplessEntity.ARTIST -> {
            val classifierShare = share("aiTracks", "totalTracks")
            val yandexShare = share("yandexAiTracks", "yandexCheckedTracks")
            if (classifierShare == null && yandexShare == null) return null
            SloplessAssessment(classifierScore = classifierShare, yandexArtistShare = yandexShare)
        }
    }
}
