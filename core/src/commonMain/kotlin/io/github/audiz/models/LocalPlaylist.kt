package io.github.audiz.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val createdAt: Long = 0L,               // Время создания (timestamp ms)
    val updatedAt: Long = 0L,
    val trackPaths: List<String> = emptyList()
) {
    val trackCount: Int get() = trackPaths.size
}
