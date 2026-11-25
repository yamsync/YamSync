package io.github.audiz.player

import io.github.audiz.DispatcherIO
import io.github.audiz.api.MusicRepository
import io.github.audiz.models.DownloadedTrackAudio
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.stream.LocalStreamProxy
import io.github.audiz.ui.CoverImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreloadedAudio(
    val trackId: String,
    val audioData: DownloadedTrackAudio?,
    val quality: String,
    val filePath: String? = null
)

/**
 * ⚡ Фоновая подготовка следующего трека: поиск файла на диске, прогрев начала потока
 * через [LocalStreamProxy] или фоновая загрузка там, где прокси недоступен.
 */
internal class TrackPreloader(
    private val scope: CoroutineScope,
    private val repository: MusicRepository,
    private val localTrackResolver: LocalTrackResolver,
    private val audioWriter: DownloadedAudioWriter,
    private val getSelectedQuality: () -> String,
    private val isRecordToDisk: () -> Boolean,
    private val onTrackSavedToDisk: (() -> Unit)?,
    /** Текущий играющий трек: предзагрузка для него не нужна. */
    private val currentTrackId: () -> String?,
    /** Актуальный запланированный следующий трек: результат сохраняется, только пока он не сменился. */
    private val plannedNextTrackId: () -> String?,
    private val coverUriForTags: () -> String?,
) {
    var preloadedAudio: PreloadedAudio? = null
        private set
    private var preloadJob: Job? = null

    /** Отменить фоновую задачу, не трогая уже готовый результат. */
    fun cancel() {
        preloadJob?.cancel()
    }

    /** Отменить задачу и забыть готовый результат. */
    fun reset() {
        preloadJob?.cancel()
        preloadJob = null
        preloadedAudio = null
    }

    fun clearAudio() {
        preloadedAudio = null
    }

    /** Оставить готовый результат, только если он относится к [trackId]. */
    fun keepOnlyFor(trackId: String) {
        if (preloadedAudio?.trackId != trackId) preloadedAudio = null
    }

    fun isPreloaded(trackId: String?): Boolean =
        trackId != null && preloadedAudio?.trackId == trackId

    /**
     * Следующий трек запустится без сети: файл на диске или аудио уже в памяти.
     * Поиск на диске выполняется, только если [allowDiskLookup] (текущий трек тоже играет с диска).
     */
    fun isLocallyAvailable(next: FullTrackInfo?, allowDiskLookup: Boolean): Boolean {
        val preloaded = preloadedAudio?.takeIf { it.trackId == next?.id }
        return preloaded?.filePath != null || preloaded?.audioData != null ||
            next?.id?.startsWith("local:") == true ||
            (allowDiskLookup && next?.let {
                localTrackResolver.findLocalTrackFile(it.id, it.artists.joinToString { artist -> artist.name }, it.title, getSelectedQuality())
            } != null)
    }

    fun schedule(nextTrack: FullTrackInfo?) {
        preloadJob?.cancel()
        nextTrack ?: return
        // Обложка следующего трека нужна сразу при переключении — загружаем её заранее
        CoverImageLoader.prefetch(nextTrack.coverUri)
        val targetTrackId = nextTrack.id
        val cleanTitle = nextTrack.title.trim()
        val cleanArtist = nextTrack.artists.joinToString { it.name }.trim()
        val selectedQuality = getSelectedQuality()

        if (preloadedAudio?.trackId == targetTrackId && preloadedAudio?.quality == selectedQuality) {
            return
        }

        preloadJob = scope.launch {
            // Задержка 3 секунды для предотвращения лишних скачиваний при быстром перелистывании
            delay(3000L)

            if (currentTrackId() == targetTrackId || plannedNextTrackId() != targetTrackId) {
                return@launch
            }

            try {
                val existingFilePath = withContext(DispatcherIO) {
                    localTrackResolver.findLocalTrackFile(targetTrackId, cleanArtist, cleanTitle, selectedQuality)
                }

                if (existingFilePath != null) {
                    println("⚡ [Предзагрузка] Трек '$cleanTitle' уже есть на диске: $existingFilePath")
                    preloadedAudio = PreloadedAudio(targetTrackId, audioData = null, quality = selectedQuality, filePath = existingFilePath)
                    return@launch
                }

                if (targetTrackId.startsWith("local:")) {
                    return@launch
                }

                // 2. ⚡ Если запущен LocalStreamProxy — предзагружаем только первый чанк, экономя трафик
                if (LocalStreamProxy.isRunning()) {
                    println("⚡ [Предзагрузка] Предзагрузка первого чанка стрима через LocalStreamProxy: $cleanTitle")
                    try {
                        val preloadedUrl = LocalStreamProxy.preloadTrack(targetTrackId, selectedQuality)
                        if (preloadedUrl.isNotEmpty() && plannedNextTrackId() == targetTrackId) {
                            preloadedAudio = PreloadedAudio(targetTrackId, audioData = null, quality = selectedQuality, filePath = null)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("⚡ [Предзагрузка] Ошибка предзагрузки стрима: ${e.message}")
                    }
                    return@launch
                }

                // 3. Скачиваем трек в фоне (fallback для сред без прокси)
                println("⚡ [Предзагрузка] Фоновое скачивание трека: $cleanTitle ($cleanArtist) [Качество: $selectedQuality]")
                val audioData = repository.downloadTrackAudio(targetTrackId, quality = selectedQuality)

                if (plannedNextTrackId() != targetTrackId) {
                    return@launch
                }

                preloadedAudio = if (isRecordToDisk()) {
                    val filePath = audioWriter.save(targetTrackId, cleanTitle, cleanArtist, selectedQuality, audioData, coverUriForTags())
                    localTrackResolver.invalidateCache()
                    onTrackSavedToDisk?.invoke()
                    println("⚡ [Предзагрузка] Трек '$cleanTitle' сохранен на диск: $filePath")
                    PreloadedAudio(targetTrackId, audioData = null, quality = selectedQuality, filePath = filePath)
                } else {
                    println("⚡ [Предзагрузка] Трек '$cleanTitle' сохранен в памяти (${audioData.result.size} байт)")
                    PreloadedAudio(targetTrackId, audioData = audioData, quality = selectedQuality, filePath = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("⚡ [Предзагрузка] Не удалось предзагрузить трек: ${e.message}")
            }
        }
    }
}
