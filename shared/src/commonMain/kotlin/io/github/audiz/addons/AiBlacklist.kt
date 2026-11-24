package io.github.audiz.addons

import io.github.audiz.DispatcherIO
import io.github.audiz.models.FullArtistInfo
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import yandexmusicdownloader.shared.generated.resources.Res

enum class AiFilterMode(val title: String) {
    OFF("Выключено"),
    LABELS("Только метки"),
    SKIP_IN_WAVE("Пропускать в Моей волне")
}

internal enum class AiMatch { TRACK, ARTIST }

@Serializable
internal class AiBlacklist(
    val version: Int,
    private val artists: Set<Long>,
    private val tracks: Set<Long>,
) {
    fun containsArtist(artistId: Long): Boolean = artistId in artists

    fun containsTrack(trackId: String?): Boolean {
        val id = trackId?.substringBefore(':')?.toLongOrNull() ?: return false
        return id in tracks
    }

    fun matchTrack(trackId: String?, trackArtists: List<FullArtistInfo>): AiMatch? = when {
        containsTrack(trackId) -> AiMatch.TRACK
        trackArtists.any { containsArtist(it.id) } -> AiMatch.ARTIST
        else -> null
    }
}

internal suspend fun loadBundledAiBlacklist(): AiBlacklist = withContext(DispatcherIO) {
    Json.decodeFromString<AiBlacklist>(Res.readBytes("files/ai_blacklist.json").decodeToString())
}
