package io.github.audiz.player

import io.github.audiz.api.MusicRepository
import io.github.audiz.models.DownloadedTrackAudio
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.saveTrackFile
import io.github.audiz.ui.CoverImageLoader
import io.github.audiz.util.AudioMetadataTagger

/**
 * 💾 Сохранение полученного аудио трека в музыкальную библиотеку:
 * имя файла, папка качества, встраивание тегов и обложки.
 *
 * Общая логика для обычного запуска, LQ-фоллбэка, повторной попытки после сбоя потока и предзагрузки.
 */
internal class DownloadedAudioWriter(
    private val repository: MusicRepository,
    private val localTrackResolver: LocalTrackResolver,
    private val findTrackInfo: (String) -> FullTrackInfo?,
) {
    fun qualityBasePath(quality: String): String =
        "${localTrackResolver.getMusicStoragePath()}/${qualityFolder(quality)}"

    /**
     * Сохраняет аудио на диск и возвращает ожидаемый путь к файлу.
     * [coverUri] имеет приоритет над обложкой из метаданных трека.
     */
    suspend fun save(
        trackId: String,
        title: String,
        artist: String,
        quality: String,
        audio: DownloadedTrackAudio,
        coverUri: String?,
    ): String {
        val extension = fileExtension(audio)
        val fileName = fileName(artist, title, extension)
        val basePath = qualityBasePath(quality)
        val taggedBytes = tagAudio(trackId, title, artist, extension, audio.result, coverUri)
        saveTrackFile(basePath, artist, fileName, taggedBytes)
        return "$basePath/${LocalTrackResolver.sanitizeKeepSpaces(artist.ifBlank { "Unknown Artist" })}/$fileName"
    }

    private suspend fun tagAudio(
        trackId: String,
        title: String,
        artist: String,
        extension: String,
        rawAudioBytes: ByteArray,
        coverUri: String?,
    ): ByteArray {
        val info = findTrackInfo(trackId)
        val effectiveCoverUri = coverUri ?: info?.coverUri
        val coverBytes = effectiveCoverUri?.let { uri ->
            val url = CoverImageLoader.formatCoverUrl(uri, 600)
            if (url != null) repository.fetchImageBytes(url) else null
        }
        return AudioMetadataTagger.embedMetadata(
            audioBytes = rawAudioBytes,
            extension = extension,
            title = title,
            artist = artist,
            album = info?.albums?.firstOrNull()?.title,
            year = info?.albums?.firstOrNull()?.year?.toString(),
            coverBytes = coverBytes
        )
    }

    companion object {
        fun qualityFolder(quality: String): String = if (quality == "2") "HQ" else "LQ"

        fun defaultBitrate(quality: String): Int = if (quality == "2") 320 else 192

        fun fileExtension(audio: DownloadedTrackAudio): String {
            val raw = audio.type.substringBefore("-")
            return if (raw.equals("aac", ignoreCase = true)) "m4a" else raw
        }

        fun fileName(artist: String, title: String, extension: String): String =
            LocalTrackResolver.sanitizeKeepSpaces(
                if (artist.isNotEmpty()) "$artist — $title.$extension" else "$title.$extension"
            )

        /** Битрейт из ответа сервера, из строки кодека либо по размеру и длительности. */
        fun resolveBitrate(audio: DownloadedTrackAudio, durationMs: Long): Int? {
            val fromType = audio.type.substringAfter("-").substringBefore("-").toIntOrNull()
            return audio.bitrate
                ?: fromType?.takeIf { it in 32..1000 }
                ?: bitrateFromSize(audio.result.size.toLong(), durationMs)
        }

        fun bitrateFromSize(sizeBytes: Long?, durationMs: Long): Int? =
            if (durationMs > 0 && sizeBytes != null && sizeBytes > 0) {
                ((sizeBytes * 8) / durationMs).toInt().takeIf { it > 0 }
            } else null
    }
}
