package io.github.audiz.models

import kotlinx.serialization.Serializable

@Serializable
data class YamSyncDevice(
    val id: String,
    val name: String,
    val platform: String, // "Desktop", "Android", "iOS"
    val appVersion: String = "1.0",
    val ip: String = "",
    val port: Int = 43594
)

/**
 * 🎵 Описание аудиотрека для синхронизации (отвязано от абсолютных файловых путей)
 */
@Serializable
data class YamSyncTrack(
    val fileName: String,
    val artist: String,
    val title: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val checksum: String = ""
) {
    /** Уникальный ключ трека для надежной дедупликации без привязки к наличию файла на диске */
    val matchKey: String
        get() {
            if (checksum.isNotBlank()) return checksum.trim().lowercase()
            val a = artist.trim().lowercase()
            val t = title.trim().lowercase()
            if (a.isNotBlank() && a != "unknown" && a != "unknown artist" && t.isNotBlank()) {
                return "${a}_${t}"
            }
            val baseName = fileName.substringBeforeLast('.').trim().lowercase()
            return baseName.ifBlank { "track" }
        }
}

@Serializable
data class YamSyncPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val tracks: List<YamSyncTrack> = emptyList()
) {
    val trackCount: Int get() = tracks.size
}

@Serializable
data class YamSyncManifest(
    val device: YamSyncDevice,
    val playlists: List<YamSyncPlaylist> = emptyList(),
    val availableFiles: List<YamSyncTrack> = emptyList(),
    val generatedAt: Long = 0L
)

@Serializable
enum class YamSyncResolution {
    KEEP_LOCAL,
    TAKE_REMOTE,
    MERGE_ALL     // Объединить всё (слияние уникальных треков с сохранением порядка)
}

@Serializable
enum class YamSyncDiffState {
    IDENTICAL,
    NEW_LOCAL,
    NEW_REMOTE,
    MODIFIED     // Есть на обоих, но список треков или метаданные различаются
}

@Serializable
data class YamSyncPlaylistDiff(
    val playlistId: String,
    val title: String,
    val state: YamSyncDiffState,
    val localPlaylist: YamSyncPlaylist? = null,
    val remotePlaylist: YamSyncPlaylist? = null,
    val localOnlyTracks: List<YamSyncTrack> = emptyList(),
    val remoteOnlyTracks: List<YamSyncTrack> = emptyList(),
    val commonTracks: List<YamSyncTrack> = emptyList(),
    val resolution: YamSyncResolution = YamSyncResolution.MERGE_ALL
)

@Serializable
data class YamSyncTransferProgress(
    val fileName: String,
    val trackTitle: String,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isCompleted: Boolean = false,
    val error: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

@Serializable
data class YamSyncMergePayload(
    val playlists: List<YamSyncPlaylist>
)
