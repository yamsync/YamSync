package io.github.audiz.player

import androidx.compose.runtime.snapshotFlow
import io.github.audiz.AppConfigKeys
import io.github.audiz.DispatcherIO
import io.github.audiz.loadAppConfig
import io.github.audiz.saveAppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 💾 Сохранение и восстановление очереди «Далее» между запусками приложения.
 */
internal class PlaybackQueuePersistence(
    private val scope: CoroutineScope,
) {
    private val queueJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var autoSaveJob: Job? = null

    /** Сохранённое состояние очереди или null, если его нет или оно повреждено. */
    suspend fun load(): UpNextState? = withContext(DispatcherIO) {
        try {
            loadAppConfig(AppConfigKeys.PLAYER_UP_NEXT)?.let { queueJson.decodeFromString(UpNextState.serializer(), it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Сохранять каждое изменение [queue] в конфиг приложения. */
    fun startAutoSave(queue: UpNextQueue) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            snapshotFlow { queue.state }.collect { state ->
                withContext(DispatcherIO) {
                    saveAppConfig(AppConfigKeys.PLAYER_UP_NEXT, queueJson.encodeToString(UpNextState.serializer(), state))
                }
            }
        }
    }
}
