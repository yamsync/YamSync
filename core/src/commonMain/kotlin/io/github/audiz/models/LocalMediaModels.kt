package io.github.audiz.models

import kotlinx.serialization.Serializable

@Serializable
enum class LocalSourceType {
    FOLDER,
    SINGLE_FILE,
    PLAYLIST_FILE  // Внешний файл плейлиста (.m3u / .m3u8)
}

@Serializable
data class CustomMediaSource(
    val id: String,
    val type: LocalSourceType,
    val name: String,
    val path: String,
    val trackCount: Int = 1,
    val addedAt: Long = 0L          // Timestamp добавления (ms)
)

@Serializable
data class FolderItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean = true,
    val trackCount: Int = 0
)

@Serializable
data class FolderListing(
    val currentPath: String,
    val parentPath: String? = null,
    val rootPath: String? = null,
    val subfolders: List<FolderItem> = emptyList(),
    val tracks: List<FullTrackInfo> = emptyList()
)
