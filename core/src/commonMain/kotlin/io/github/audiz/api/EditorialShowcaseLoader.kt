package io.github.audiz.api

import io.github.audiz.models.ChartCollection
import io.github.audiz.models.ChartMenuOption
import io.github.audiz.models.ChartPositionInfo
import io.github.audiz.models.ChartTrackEntry
import io.github.audiz.models.EditorialShowcaseData
import io.github.audiz.models.FullTrackInfo
import io.github.audiz.models.NewReleaseAlbum
import io.github.audiz.models.PlaylistInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Загружает редакционную витрину Яндекс Музыки: чарты, новые плейлисты и новые релизы.
 */
class EditorialShowcaseLoader(private val fetch: suspend (String) -> String?) {

    suspend fun load(): EditorialShowcaseData = coroutineScope {
        val primaryChartDeferred = async {
            readNullable(ApiUrls.LANDING3_CHART_URL, EditorialShowcaseCatalog::parseChart)
        }
        val playlistsLandingDeferred = async {
            readNullable("${ApiUrls.LANDING3_URL}?blocks=new-playlists,chart") { raw ->
                EditorialShowcaseCatalog.parseLanding(raw)
            }
        }
        val releasesLandingDeferred = async {
            readNullable("${ApiUrls.LANDING3_URL}?blocks=new-releases") { raw ->
                EditorialShowcaseCatalog.parseLanding(raw)
            }
        }

        val primaryChart = primaryChartDeferred.await()
        val playlistsLanding = playlistsLandingDeferred.await() ?: EditorialShowcaseData()
        val releasesLanding = releasesLandingDeferred.await() ?: EditorialShowcaseData()

        val extraChartIds = buildList {
            primaryChart?.menu
                ?.map { it.chartId }
                ?.filter { it.isNotBlank() && it != primaryChart.chartId }
                ?.forEach { add(it) }
            if (primaryChart?.chartId != "world" && none { it == "world" }) {
                add("world")
            }
        }.distinct().take(MAX_EXTRA_CHARTS)

        val extraCharts = extraChartIds.map { chartId ->
            async {
                readNullable("${ApiUrls.LANDING3_CHART_URL}/$chartId") { raw ->
                    EditorialShowcaseCatalog.parseChart(raw, requestedChartId = chartId)
                }
            }
        }.awaitAll().filterNotNull()

        val fallbackReleasesDeferred = if (releasesLanding.newReleases.isEmpty()) {
            async {
                readList(ApiUrls.LANDING3_NEW_RELEASES_URL, EditorialShowcaseCatalog::parseNewReleasesFallback)
            }
        } else null

        val fallbackPlaylistsDeferred = if (playlistsLanding.newPlaylists.isEmpty()) {
            async {
                readList(ApiUrls.LANDING3_NEW_PLAYLISTS_URL, EditorialShowcaseCatalog::parseNewPlaylistsFallback)
            }
        } else null

        val mergedCharts = EditorialShowcaseCatalog.mergeCharts(
            listOfNotNull(primaryChart) + extraCharts + playlistsLanding.charts
        )
        val newReleases = (releasesLanding.newReleases.ifEmpty { fallbackReleasesDeferred?.await().orEmpty() })
            .distinctBy { it.id }
        val newPlaylists = (playlistsLanding.newPlaylists.ifEmpty { fallbackPlaylistsDeferred?.await().orEmpty() })
            .distinctBy { playlistKey(it) }

        EditorialShowcaseData(
            charts = mergedCharts,
            newPlaylists = newPlaylists,
            newReleases = newReleases
        )
    }

    suspend fun loadChart(chartId: String = "russia"): ChartCollection? {
        val cleanId = EditorialShowcaseCatalog.normalizeChartId(chartId).ifBlank { "russia" }
        return readNullable("${ApiUrls.LANDING3_CHART_URL}/$cleanId") { raw ->
            EditorialShowcaseCatalog.parseChart(raw, requestedChartId = cleanId)
        } ?: if (cleanId == "russia") {
            readNullable(ApiUrls.LANDING3_CHART_URL, EditorialShowcaseCatalog::parseChart)
        } else null
    }

    private suspend fun <T> readNullable(url: String, parse: (String) -> T?): T? =
        try {
            fetch(url)?.let(parse)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun <T> readList(url: String, parse: (String) -> List<T>): List<T> =
        try {
            fetch(url)?.let(parse).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    private fun playlistKey(playlist: PlaylistInfo): String =
        playlist.playlistUuid?.takeIf { it.isNotBlank() } ?: "${playlist.uid}:${playlist.kind ?: 0L}"

    private companion object {
        const val MAX_EXTRA_CHARTS = 4
    }
}

/**
 * Нормализует ответы /landing3 и /landing3/chart в компактные модели витрины.
 */
object EditorialShowcaseCatalog {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun normalizeChartId(rawUrl: String): String {
        val cleaned = rawUrl.trim().substringBefore('?').trim('/')
        return cleaned.substringAfterLast('/').trim()
    }

    fun acceptLanguageForUrl(url: String): String {
        val lower = url.lowercase()
        return if (lower.contains("/landing3/chart") ||
            lower.contains("/landing3/new-releases") ||
            lower.contains("blocks=new-releases")
        ) {
            ApiSessionContext.ACCEPT_LANGUAGE_TRACK_METADATA
        } else {
            ApiSessionContext.ACCEPT_LANGUAGE_RU
        }
    }

    fun parseChart(raw: String, requestedChartId: String = ""): ChartCollection? {
        val result = unwrapResult(raw) ?: return null
        val chartObj = result.obj("chart") ?: result.takeIf { "tracks" in it || "entities" in it } ?: return null
        val menu = result.obj("menu")?.array("items")?.mapNotNull(::parseMenuItem).orEmpty()

        val resolvedId = normalizeChartId(requestedChartId).takeIf { it.isNotBlank() }
            ?: chartObj.string("id")?.let(::normalizeChartId)?.takeIf { it.isNotBlank() && it != "chart" }
            ?: menu.firstOrNull { it.selected }?.chartId
            ?: "russia"

        val entries = parseChartEntries(chartObj)
        if (entries.isEmpty() && chartObj.string("playlistUuid").isNullOrBlank()) {
            return null
        }

        val rawTitle = chartObj.string("title")?.trim().orEmpty()
        val menuTitle = menu.firstOrNull { it.chartId == resolvedId }?.title
        val resolvedTitle = localizeChartTitle(rawTitle, resolvedId, menuTitle)

        val coverUri = extractCoverUri(chartObj) ?: entries.firstOrNull()?.track?.coverUri
        val ownerUid = chartObj.number("uid") ?: chartObj.obj("owner")?.number("uid") ?: 0L
        val kind = chartObj.number("kind") ?: 0L
        val trackCount = chartObj.int("trackCount") ?: chartObj.int("tracksCount") ?: entries.size

        return ChartCollection(
            chartId = resolvedId,
            title = resolvedTitle,
            description = localizeChartDescription(chartObj.string("description"), menuTitle),
            coverUri = coverUri,
            uid = ownerUid,
            kind = kind,
            playlistUuid = chartObj.string("playlistUuid").orEmpty(),
            trackCount = trackCount,
            tracks = entries,
            menu = menu
        )
    }

    fun parseLanding(raw: String): EditorialShowcaseData {
        val result = unwrapResult(raw) ?: return EditorialShowcaseData()
        val blocks = result.array("blocks")
        if (blocks.isEmpty()) return EditorialShowcaseData()

        val charts = mutableListOf<ChartCollection>()
        val newPlaylists = mutableListOf<PlaylistInfo>()
        val newReleases = mutableListOf<NewReleaseAlbum>()

        for (elem in blocks) {
            val block = elem as? JsonObject ?: continue
            val blockType = (block.string("type") ?: block.string("id") ?: "").lowercase()
            val entities = block.array("entities")
            when (blockType) {
                "new-releases" -> {
                    for (entityElem in entities) {
                        val entity = entityElem as? JsonObject ?: continue
                        val data = entity.obj("data") ?: entity
                        val albumObj = data.obj("album") ?: data
                        parseAlbum(albumObj)?.let(newReleases::add)
                    }
                }
                "new-playlists" -> {
                    for (entityElem in entities) {
                        val entity = entityElem as? JsonObject ?: continue
                        val data = entity.obj("data") ?: entity
                        val playlistObj = data.obj("playlist") ?: data
                        parsePlaylist(playlistObj)?.let(newPlaylists::add)
                    }
                }
                "chart" -> {
                    val entries = parseChartEntries(block)
                    if (entries.isNotEmpty()) {
                        charts.add(
                            ChartCollection(
                                chartId = "russia",
                                title = block.string("title")?.takeIf { it.isNotBlank() } ?: "Чарт",
                                description = block.string("description"),
                                coverUri = extractCoverUri(block) ?: entries.firstOrNull()?.track?.coverUri,
                                trackCount = entries.size,
                                tracks = entries
                            )
                        )
                    }
                }
            }
        }

        return EditorialShowcaseData(
            charts = mergeCharts(charts),
            newPlaylists = newPlaylists.distinctBy { it.playlistUuid?.takeIf(String::isNotBlank) ?: "${it.uid}:${it.kind ?: 0L}" },
            newReleases = newReleases.distinctBy { it.id }
        )
    }

    fun parseNewReleasesFallback(raw: String): List<NewReleaseAlbum> {
        val result = unwrapResult(raw) ?: return emptyList()
        val array = result.array("newReleases").ifEmpty { result.array("albums") }
        return array.mapNotNull { elem -> (elem as? JsonObject)?.let(::parseAlbum) }
    }

    fun parseNewPlaylistsFallback(raw: String): List<PlaylistInfo> {
        val result = unwrapResult(raw) ?: return emptyList()
        val array = result.array("newPlaylists").ifEmpty { result.array("playlists") }
        return array.mapNotNull { elem -> (elem as? JsonObject)?.let(::parsePlaylist) }
    }

    fun mergeCharts(charts: List<ChartCollection>): List<ChartCollection> {
        val byId = LinkedHashMap<String, ChartCollection>()
        for (chart in charts) {
            val existing = byId[chart.chartId]
            if (existing == null || chart.tracks.size > existing.tracks.size) {
                byId[chart.chartId] = if (existing == null) chart else chart.copy(
                    menu = chart.menu.ifEmpty { existing.menu },
                    coverUri = chart.coverUri ?: existing.coverUri
                )
            }
        }
        return byId.values.toList()
    }

    private fun parseMenuItem(elem: JsonElement): ChartMenuOption? {
        val obj = elem as? JsonObject ?: return null
        val rawTitle = obj.string("title")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val chartId = obj.string("url")?.let(::normalizeChartId)?.takeIf { it.isNotBlank() } ?: return null
        return ChartMenuOption(
            title = localizeMenuTitle(rawTitle, chartId),
            chartId = chartId,
            selected = obj.boolean("selected") ?: false
        )
    }

    private fun parseChartEntries(container: JsonObject): List<ChartTrackEntry> {
        val rawItems = container.array("tracks").ifEmpty { container.array("entities") }
        val entries = mutableListOf<ChartTrackEntry>()
        for ((index, elem) in rawItems.withIndex()) {
            val wrapper = elem as? JsonObject ?: continue
            val itemObj = wrapper.obj("data") ?: wrapper
            val trackObj = itemObj.obj("track")
                ?: itemObj.takeIf { "title" in it && ("artists" in it || "durationMs" in it) }
                ?: continue
            val decoded = runCatching {
                json.decodeFromJsonElement(FullTrackInfo.serializer(), trackObj)
            }.getOrNull() ?: continue

            val fallbackId = itemObj.string("id") ?: itemObj.number("id")?.toString() ?: ""
            val resolvedId = decoded.id.ifBlank { fallbackId }
            if (resolvedId.isBlank()) continue

            val fallbackCover = decoded.coverUri?.takeIf { it.isNotBlank() }
                ?: trackObj.string("ogImage")
                ?: trackObj.array("albums").firstOrNull()?.let { it as? JsonObject }?.let(::extractCoverUri)

            val track = decoded.copy(
                id = resolvedId,
                coverUri = fallbackCover
            )

            val chartPosObj = itemObj.obj("chart")
            val chartPos = if (chartPosObj != null) {
                ChartPositionInfo(
                    position = chartPosObj.int("position") ?: (index + 1),
                    progress = chartPosObj.string("progress"),
                    shift = chartPosObj.int("shift") ?: 0,
                    listeners = chartPosObj.number("listeners")
                )
            } else {
                ChartPositionInfo(position = index + 1)
            }
            entries.add(ChartTrackEntry(track = track, chart = chartPos))
        }
        return entries
    }

    private fun parseAlbum(obj: JsonObject): NewReleaseAlbum? {
        if (obj.boolean("available") == false) return null
        val id = obj.number("id") ?: return null
        if (id <= 0L) return null
        val title = obj.string("title")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val artists = obj.array("artists").mapNotNull { artistElem ->
            (artistElem as? JsonObject)?.string("name")?.trim()?.takeIf { it.isNotBlank() }
        }
        val rawType = obj.string("type")?.takeIf { it.isNotBlank() && it != "music" }
            ?: obj.string("metaType")?.takeIf { it.isNotBlank() && it != "music" }
        return NewReleaseAlbum(
            id = id,
            title = title,
            artists = artists,
            coverUri = extractCoverUri(obj),
            year = obj.int("year"),
            trackCount = obj.int("trackCount") ?: obj.int("tracksCount") ?: 0,
            type = rawType
        )
    }

    private fun parsePlaylist(obj: JsonObject): PlaylistInfo? {
        if (obj.boolean("available") == false || obj.int("trackCount") == 0) return null
        if (obj["playlistAbsence"] != null && obj["playlistAbsence"] !is JsonNull) return null
        val uid = obj.number("uid") ?: obj.obj("owner")?.number("uid") ?: 0L
        val kind = obj.number("kind")
        val uuid = obj.string("playlistUuid")?.takeIf { it.isNotBlank() }
        if (uuid == null && (uid <= 0L || kind == null || kind <= 0L)) return null
        val title = obj.string("title")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return PlaylistInfo(
            uid = uid,
            title = title,
            description = obj.string("description")?.takeIf { it.isNotBlank() },
            trackCount = obj.int("trackCount") ?: obj.int("tracksCount") ?: 0,
            kind = kind,
            playlistUuid = uuid,
            coverUri = extractCoverUri(obj),
            revision = obj.int("revision")
        )
    }

    private fun extractCoverUri(obj: JsonObject): String? {
        val direct = obj.string("coverUri")?.takeIf { it.isNotBlank() }
        if (direct != null) return direct
        val coverObj = obj.obj("cover")
        val fromCover = coverObj?.string("uri")?.takeIf { it.isNotBlank() }
            ?: coverObj?.array("itemsUri")?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
        if (fromCover != null) return fromCover
        return obj.string("ogImage")?.takeIf { it.isNotBlank() }
            ?: obj.string("bgImageUri")?.takeIf { it.isNotBlank() }
    }

    private fun localizeMenuTitle(rawTitle: String, chartId: String): String = when (rawTitle.lowercase()) {
        "russia" -> "Россия"
        "world", "world chart", "global", "global chart" -> "Мир"
        else -> when (chartId.lowercase()) {
            "russia" -> if (rawTitle.equals("chart", ignoreCase = true)) "Россия" else rawTitle
            "world" -> if (rawTitle.equals("chart", ignoreCase = true)) "Мир" else rawTitle
            else -> rawTitle
        }
    }

    private fun localizeChartTitle(rawTitle: String, chartId: String, menuTitle: String?): String {
        val lower = rawTitle.lowercase()
        val isGenericChart = lower.isBlank() || lower == "чaрт" || lower == "чарт" || lower == "chart"
        val isEnglishKnown = lower in setOf(
            "russia", "russian chart", "chart • russia", "chart russia",
            "world", "world chart", "global chart", "chart • world"
        )
        return when {
            isGenericChart -> if (!menuTitle.isNullOrBlank() && chartId.lowercase() != "world") {
                "Чарт • $menuTitle"
            } else {
                defaultChartTitle(chartId)
            }
            isEnglishKnown -> defaultChartTitle(chartId)
            else -> rawTitle
        }
    }

    private fun localizeChartDescription(rawDescription: String?, menuTitle: String?): String? {
        val trimmed = rawDescription?.trim()?.takeIf { it.isNotBlank() } ?: return menuTitle
        val hasCyrillic = trimmed.any { it in '\u0400'..'\u04FF' }
        return if (hasCyrillic) trimmed else (menuTitle ?: "Популярные треки прямо сейчас")
    }

    private fun defaultChartTitle(chartId: String): String = when (chartId.lowercase()) {
        "world" -> "Мировой чарт"
        "russia" -> "Чарт • Россия"
        else -> "Чарт"
    }

    private fun unwrapResult(raw: String): JsonObject? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        obj.obj("result") ?: obj
    }.getOrNull()

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.boolean(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
}
