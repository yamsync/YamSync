package io.github.audiz.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class YandexError(
    val name: String? = null,
    val message: String? = null
)

@Serializable
data class YandexMusicResponse(
    val invocationInfo: InvocationInfo? = null,
    val error: YandexError? = null,
    val result: SearchResult? = null
)

@Serializable
data class SearchResult(
    val searchRequestId: String = "",
    val text: String = "",
    val results: List<TypedResult> = emptyList(),
    val bestResults: List<JsonElement> = emptyList()
)

@Serializable
data class TypedResult(
    val type: String,
    val playlist: PlaylistInfo? = null,
    val artist: ArtistInfo? = null,
    val album: AlbumInfo? = null,
    val track: TrackInfo? = null,
    val wave: WaveSearchResultInfo? = null
)

@Serializable
data class WaveSearchResultId(
    val tag: String? = null,
    val type: String? = null
)

@Serializable
data class WaveSearchResultInfo(
    val id: WaveSearchResultId? = null,
    val seeds: List<String> = emptyList(),
    val title: String? = null,
    val subTitle: String? = null,
    val color: String? = null,
    val header: String? = null
)

@Serializable
data class PlaylistInfo(
    val uid: Long,
    val title: String,
    val description: String? = null,
    val trackCount: Int,
    val kind: Long? = null,
    val playlistUuid: String? = null,
    val coverUri: String? = null,
    val revision: Int? = null
)

@Serializable
data class YandexPlaylistDetails(
    val uid: Long,
    val kind: Long,
    val title: String,
    val revision: Int,
    val trackCount: Int,
    val tracks: List<YandexPlaylistTrackItem> = emptyList()
)

@Serializable
data class YandexPlaylistTrackItem(
    val id: String,
    val albumId: Long? = null,
    val title: String = "",
    val artist: String = ""
)

@Serializable
data class ArtistInfo(
    val id: String,
    val name: String,
    val likesCount: Int? = null,
    val genres: List<String> = emptyList(),
    val counts: ArtistCounts? = null
)

@Serializable
data class ArtistCounts(
    val tracks: Int? = null,
    val directAlbums: Int? = null
)

@Serializable
data class AlbumInfo(
    val id: Long,
    val title: String,
    val year: Int? = null,
    val trackCount: Int
)

@Serializable
data class TrackInfo(
    val id: String,
    val title: String,
    val durationMs: Long,
    // 🔥 ИСПРАВЛЕНИЕ: Используем TrackArtistInfo вместо ArtistInfo, чтобы избежать конфликта типов (Long vs String)
    val artists: List<TrackArtistInfo> = emptyList(),
    val coverUri: String? = null
) {
    fun toFullTrackInfo(): FullTrackInfo = FullTrackInfo(
        id = id,
        title = title,
        durationMs = durationMs,
        artists = artists.map { FullArtistInfo(id = it.id, name = it.name) },
        coverUri = coverUri
    )
}

// 🔥 ДОБАВЛЕНО: Специальная модель для артистов внутри быстрого поиска треков
@Serializable
data class TrackArtistInfo(
    val id: Long, // 🔥 Здесь строго Long, чтобы парсинг не падал на числах без кавычек
    val name: String,
    val likesCount: Int? = null
)

@Serializable
data class YandexArtistTrackIdsResponse(
    val invocationInfo: InvocationInfo? = null,
    val error: YandexError? = null,
    val result: List<String> = emptyList() // Массив ID треков
)

@Serializable
data class InvocationInfo(
    val reqId: String? = null,
    val hostname: String? = null,
    val execDurationMillis: Int? = null
)

@Serializable
data class YandexTracksDetailsResponse(
    val invocationInfo: InvocationInfo? = null,
    val error: YandexError? = null,
    val result: List<FullTrackInfo> = emptyList()
)

@Serializable
data class FullTrackInfo(
    val id: String = "",
    val realId: String? = null,
    val title: String = "",
    val version: String? = null,
    val available: Boolean = false,
    val durationMs: Long = 0,
    val artists: List<FullArtistInfo> = emptyList(),
    val albums: List<FullAlbumInfo> = emptyList(),
    val coverUri: String? = null
)

/**
 * Проверяет, является ли идентификатор трека ссылкой на локальный файл на устройстве
 * (а не облачным треком Яндекс Музыки).
 */
fun isLocalTrackId(id: String?): Boolean {
    if (id.isNullOrBlank()) return false
    val trimmed = id.trim()
    if (trimmed.startsWith("local:") || trimmed.startsWith("file:") || trimmed.startsWith("/") || trimmed.startsWith("~")) {
        return true
    }
    // Windows absolute path with drive letter (например, C:\... или D:/...)
    if (trimmed.length >= 3 && trimmed[0].isLetter() && trimmed[1] == ':' && (trimmed[2] == '\\' || trimmed[2] == '/')) {
        return true
    }
    return false
}

val FullTrackInfo.isLocal: Boolean
    get() = isLocalTrackId(id) || (realId != null && isLocalTrackId(realId))

fun FullTrackInfo.toTrackInfo(): TrackInfo = TrackInfo(
    id = id,
    title = title,
    durationMs = durationMs,
    artists = artists.map { TrackArtistInfo(id = it.id, name = it.name) },
    coverUri = coverUri
)

@Serializable
data class FullArtistInfo(
    val id: Long = 0,
    val name: String = "",
    val available: Boolean = false
)

@Serializable
data class FullAlbumInfo(
    val id: Long = 0,
    val title: String = "",
    val year: Int? = null,
    val trackCount: Int = 0,
    val genre: String? = null
)

@Serializable
data class YandexTrackDownloadInfoResponse(
    val downloadInfo: DownloadInfo
)

@Serializable
data class DownloadInfo(
    val trackId: String,
    val quality: String,
    val codec: String,
    val bitrate: Int,
    val transport: String,
    val key: String,
    val gain: Boolean,
    val urls: List<String>,
    val url: String,
    val realId: String
)

// 🔥 Модель ответа от landing-blocks/collection/* (содержит playlistUuid и список треков)
@Serializable
data class YandexLandingPlaylistResponse(
    val invocationInfo: InvocationInfo? = null,
    val result: LandingPlaylistResult
)

@Serializable
data class LandingPlaylistResult(
    val playlistUuid: String,
    val title: String = "",
    val trackCount: Int = 0,
    val tracks: List<LandingTrackShort> = emptyList()
)

@Serializable
data class LandingTrackShort(
    val id: Long,
    val albumId: Long? = null
)

// 🔥 Модель ответа от /playlist/{uuid} (содержит полный список треков)
@Serializable
data class YandexPlaylistByUuidResponse(
    val invocationInfo: InvocationInfo? = null,
    val result: PlaylistByUuidResult
)

@Serializable
data class PlaylistByUuidResult(
    val playlistUuid: String = "",
    val title: String = "",
    val trackCount: Int = 0,
    val tracks: List<LandingTrackShort> = emptyList()
)

// 🌊 Модели ответов Моей волны (Rotor Radio)
@Serializable
data class RotorSequenceItem(
    val liked: Boolean = false,
    val track: FullTrackInfo? = null
)

@Serializable
data class RotorSessionResponse(
    val radioSessionId: String? = null,
    val batchId: String? = null,
    val sequence: List<RotorSequenceItem> = emptyList(),
    val pumpkin: Boolean = false,
    val terminated: Boolean = false
)

// 🎵 Персональные плейлисты (landing-blocks/personal-playlists)
@Serializable
data class PersonalPlaylistsResponse(
    val items: List<PersonalPlaylistItemWrapper> = emptyList()
)

@Serializable
data class PersonalPlaylistItemWrapper(
    val type: String = "",
    val data: PersonalPlaylistItemData? = null
)

@Serializable
data class PersonalPlaylistItemData(
    val playlist: PersonalPlaylistInfo? = null,
    val playlistType: String = "",
    val description: String? = null,
    val notify: Boolean = false,
    val idForFrom: String? = null
) {
    val displayTitle: String
        get() {
            val raw = playlist?.title?.trim().orEmpty()
            val localizedByType = localizedPersonalTitle(playlistType)
            if (raw.isBlank() || (localizedByType != null && raw.lowercase() in ENGLISH_SYSTEM_TITLES)) {
                return localizedByType ?: "Подборка"
            }
            return raw
        }
}

private val ENGLISH_SYSTEM_TITLES = setOf(
    "playlist of the day",
    "daily playlist",
    "premiere",
    "deja vu",
    "déjà vu",
    "missed likes",
    "stash",
    "podcasts of the week",
    "origin",
    "playlist with alice"
)

private fun localizedPersonalTitle(type: String): String? = when (type) {
    "playlistOfTheDay", "daily" -> "Плейлист дня"
    "recentTracks" -> "Премьера"
    "neverHeard" -> "Дежавю"
    "missedLikes" -> "Тайник"
    "podcasts" -> "Подкасты недели"
    "origin" -> "Плейлист с Алисой"
    else -> null
}

@Serializable
data class PersonalPlaylistInfo(
    val uid: Long = 0L,
    val playlistUuid: String = "",
    val kind: Long = 0L,
    val title: String = "",
    val cover: PersonalPlaylistCover? = null
) {
    val canOpen: Boolean get() = playlistUuid.isNotBlank() || (uid > 0L && kind > 0L)
    val stableKey: String get() = playlistUuid.ifBlank { "${uid}:${kind}" }
}

@Serializable
data class PersonalPlaylistCover(
    val uri: String? = null
)

@Serializable
data class ChartPositionInfo(
    val position: Int,
    val progress: String? = null,
    val shift: Int = 0,
    val listeners: Long? = null
)

@Serializable
data class ChartTrackEntry(
    val track: FullTrackInfo,
    val chart: ChartPositionInfo? = null
)

@Serializable
data class ChartMenuOption(
    val title: String,
    val chartId: String,
    val selected: Boolean = false
)

@Serializable
data class ChartCollection(
    val chartId: String,
    val title: String,
    val description: String? = null,
    val coverUri: String? = null,
    val uid: Long = 0L,
    val kind: Long = 0L,
    val playlistUuid: String = "",
    val trackCount: Int = 0,
    val tracks: List<ChartTrackEntry> = emptyList(),
    val menu: List<ChartMenuOption> = emptyList()
)

@Serializable
data class NewReleaseAlbum(
    val id: Long,
    val title: String,
    val artists: List<String> = emptyList(),
    val coverUri: String? = null,
    val year: Int? = null,
    val trackCount: Int = 0,
    val type: String? = null
)

@Serializable
data class EditorialShowcaseData(
    val charts: List<ChartCollection> = emptyList(),
    val newPlaylists: List<PlaylistInfo> = emptyList(),
    val newReleases: List<NewReleaseAlbum> = emptyList()
) {
    val isEmpty: Boolean
        get() = charts.isEmpty() && newPlaylists.isEmpty() && newReleases.isEmpty()
}

@Serializable
data class WaveThemeOption(
    val id: String,
    val title: String,
    val seeds: List<String>,
    val categoryId: String = "",
    val subtitle: String? = null,
    val imageUri: String? = null,
    val isPersonal: Boolean = false
)

@Serializable
data class WaveThemeCategory(
    val id: String,
    val title: String,
    val allowMultiSelect: Boolean = false,
    val items: List<WaveThemeOption> = emptyList()
)

@Serializable
data class WavePersonalCatalog(
    val personalStations: List<WaveThemeOption> = emptyList(),
    val categories: List<WaveThemeCategory> = emptyList()
) {
    val isEmpty: Boolean
        get() = personalStations.isEmpty() && categories.isEmpty()
}

/**
 * Метаданные прямого аудиопотока для потокового воспроизведения и чанковой загрузки
 */
data class TrackStreamMeta(
    val directUrl: String,
    val aesKey: ByteArray,
    val codec: String,
    val bitrate: Int?,
    val totalSizeBytes: Long
)

