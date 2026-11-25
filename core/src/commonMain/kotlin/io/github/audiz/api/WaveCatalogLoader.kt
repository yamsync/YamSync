package io.github.audiz.api

import io.github.audiz.models.WavePersonalCatalog
import io.github.audiz.models.WaveThemeCategory
import io.github.audiz.models.WaveThemeOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Загружает персональные настройки и рекомендованные станции «Моей волны» (Rotor).
 */
class WaveCatalogLoader(private val fetch: suspend (String) -> String?) {

    suspend fun load(): WavePersonalCatalog = coroutineScope {
        val settingsDeferred = async {
            readList(ApiUrls.ROTOR_WAVE_SETTINGS_URL, WaveCatalogParser::parseWaveSettings)
        }
        val dashboardDeferred = async {
            readList(ApiUrls.ROTOR_STATIONS_DASHBOARD_URL, WaveCatalogParser::parseStationsDashboard)
        }

        val settingsCategories = settingsDeferred.await()
        val dashboardStations = dashboardDeferred.await()

        val stationCategories = if (settingsCategories.size < MIN_SETTINGS_CATEGORIES) {
            readList(ApiUrls.ROTOR_STATIONS_LIST_URL, WaveCatalogParser::parseStationsList)
        } else {
            emptyList()
        }

        val mergedCategories = WaveCatalogParser.mergeCategories(settingsCategories + stationCategories)
        WavePersonalCatalog(
            personalStations = dashboardStations,
            categories = mergedCategories
        )
    }

    private suspend fun <T> readList(url: String, parse: (String) -> List<T>): List<T> =
        try {
            fetch(url)?.let(parse).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    private companion object {
        const val MIN_SETTINGS_CATEGORIES = 2
    }
}

/**
 * Разбирает ответы /rotor/wave/settings, /rotor/stations/dashboard и /rotor/stations/list.
 */
object WaveCatalogParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val categoryOrder = listOf("diversity", "mood", "activity", "language", "epoch", "genre")

    fun parseWaveSettings(raw: String): List<WaveThemeCategory> {
        val root = parseJsonObject(raw) ?: return emptyList()
        val result = root.obj("result") ?: root
        val categories = mutableListOf<WaveThemeCategory>()

        val blocks = result.array("blocks")
        for (blockElem in blocks) {
            val block = blockElem as? JsonObject ?: continue
            val blockId = block.string("id")?.trim().orEmpty().ifBlank {
                block.string("type")?.trim().orEmpty()
            }
            val blockTitle = block.string("name")?.trim()?.takeIf { it.isNotBlank() }
                ?: block.string("title")?.trim()?.takeIf { it.isNotBlank() }
                ?: defaultCategoryTitle(blockId)

            val sets = block.array("sets")
            val directItems = block.array("items")
            if (sets.isNotEmpty()) {
                val collected = mutableListOf<WaveThemeOption>()
                for (setElem in sets) {
                    val setObj = setElem as? JsonObject ?: continue
                    val setId = setObj.string("id")?.trim().orEmpty()
                    val setTitle = setObj.string("name")?.trim()?.takeIf { it.isNotBlank() }
                        ?: setObj.string("title")?.trim()?.takeIf { it.isNotBlank() }
                    val setSeeds = parseSeeds(setObj, defaultPrefix = categoryPrefix(blockId), fallbackTag = setId)
                    val setItems = setObj.array("items")
                    if (setItems.isEmpty() && setTitle != null && setSeeds.isNotEmpty()) {
                        collected.add(
                            WaveThemeOption(
                                id = setId.ifBlank { setSeeds.joinToString(",") },
                                title = setTitle,
                                seeds = setSeeds,
                                categoryId = normalizeCategoryId(blockId)
                            )
                        )
                    } else {
                        for (itemElem in setItems) {
                            parseSettingsItem(
                                obj = itemElem as? JsonObject ?: continue,
                                categoryId = normalizeCategoryId(blockId),
                                defaultPrefix = categoryPrefix(blockId)
                            )?.let(collected::add)
                        }
                    }
                }
                if (collected.isNotEmpty()) {
                    categories.add(
                        WaveThemeCategory(
                            id = normalizeCategoryId(blockId).ifBlank { blockTitle.lowercase() },
                            title = blockTitle,
                            items = collected.distinctBy { it.seeds }
                        )
                    )
                }
            } else if (directItems.isNotEmpty()) {
                val catId = normalizeCategoryId(blockId)
                val collected = directItems.mapNotNull { elem ->
                    parseSettingsItem(
                        obj = elem as? JsonObject ?: return@mapNotNull null,
                        categoryId = catId,
                        defaultPrefix = categoryPrefix(catId)
                    )
                }.distinctBy { it.seeds }
                if (collected.isNotEmpty()) {
                    categories.add(
                        WaveThemeCategory(
                            id = catId.ifBlank { blockTitle.lowercase() },
                            title = blockTitle,
                            items = collected
                        )
                    )
                }
            }
        }

        val restrictions = result.obj("restrictions2") ?: result.obj("restrictions")
        if (categories.isEmpty() && restrictions != null) {
            for ((key, value) in restrictions) {
                val obj = value as? JsonObject ?: continue
                val prefix = categoryPrefix(key)
                if (prefix.isBlank()) continue
                val title = obj.string("name")?.takeIf { it.isNotBlank() } ?: defaultCategoryTitle(prefix)
                val items = obj.array("possibleValues").mapNotNull { pvElem ->
                    val pv = pvElem as? JsonObject ?: return@mapNotNull null
                    val rawVal = pv.string("value")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    if (rawVal == "all" || rawVal == "any" || rawVal == "default") return@mapNotNull null
                    val label = pv.string("name")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    WaveThemeOption(
                        id = "$prefix:$rawVal",
                        title = label,
                        seeds = listOf("$prefix:$rawVal"),
                        categoryId = prefix
                    )
                }.distinctBy { it.seeds }
                if (items.isNotEmpty()) {
                    categories.add(WaveThemeCategory(id = prefix, title = title, items = items))
                }
            }
        }

        return mergeCategories(categories)
    }

    fun parseStationsDashboard(raw: String): List<WaveThemeOption> {
        val root = parseJsonObject(raw) ?: return emptyList()
        val result = root.obj("result") ?: root
        val stations = result.array("stations")
        return stations.mapNotNull { elem ->
            parseStationEntry(elem as? JsonObject ?: return@mapNotNull null, isPersonal = true)
        }.distinctBy { it.seeds }
    }

    fun parseStationsList(raw: String): List<WaveThemeCategory> {
        val root = parseJsonObject(raw) ?: return emptyList()
        val array = when (val res = root["result"]) {
            is JsonArray -> res
            is JsonObject -> res.array("stations")
            else -> root.array("stations")
        }
        val grouped = linkedMapOf<String, MutableList<WaveThemeOption>>()
        for (elem in array) {
            val option = parseStationEntry(elem as? JsonObject ?: continue, isPersonal = false) ?: continue
            val catId = option.categoryId.ifBlank { "other" }
            if (catId == "user" || catId == "other") continue
            grouped.getOrPut(catId) { mutableListOf() }.add(option)
        }
        val orderedKeys = (categoryOrder.filter { it in grouped } + grouped.keys.filter { it !in categoryOrder })
        return orderedKeys.mapNotNull { catId ->
            val items = grouped[catId]?.distinctBy { it.seeds }.orEmpty()
            if (items.isEmpty()) null else WaveThemeCategory(
                id = catId,
                title = defaultCategoryTitle(catId),
                items = items
            )
        }
    }

    fun mergeCategories(categories: List<WaveThemeCategory>): List<WaveThemeCategory> {
        val merged = linkedMapOf<String, MutableList<WaveThemeOption>>()
        val titles = mutableMapOf<String, String>()
        for (cat in categories) {
            val normId = normalizeCategoryId(cat.id).ifBlank { cat.title.lowercase() }
            if (cat.items.isEmpty()) continue
            titles.getOrPut(normId) { cat.title.ifBlank { defaultCategoryTitle(normId) } }
            val target = merged.getOrPut(normId) { mutableListOf() }
            target.addAll(cat.items)
        }
        val orderedIds = categoryOrder.filter { it in merged } + merged.keys.filter { it !in categoryOrder }
        return orderedIds.mapNotNull { id ->
            val unique = merged[id]?.distinctBy { it.seeds }.orEmpty()
            if (unique.isEmpty()) null else WaveThemeCategory(
                id = id,
                title = titles[id] ?: defaultCategoryTitle(id),
                items = unique
            )
        }
    }

    private fun parseSettingsItem(
        obj: JsonObject,
        categoryId: String,
        defaultPrefix: String
    ): WaveThemeOption? {
        val title = obj.string("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: obj.string("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val itemId = obj.string("id")?.trim().orEmpty()
        val seeds = parseSeeds(obj, defaultPrefix = defaultPrefix, fallbackTag = itemId)
        if (seeds.isEmpty() || seeds.all { it == "user:onyourwave" }) return null
        return WaveThemeOption(
            id = itemId.ifBlank { seeds.joinToString(",") },
            title = title,
            seeds = seeds,
            categoryId = categoryId,
            subtitle = obj.string("subTitle")?.takeIf { it.isNotBlank() },
            imageUri = extractImageUri(obj)
        )
    }

    private fun parseStationEntry(wrapper: JsonObject, isPersonal: Boolean): WaveThemeOption? {
        val station = wrapper.obj("station") ?: wrapper
        if (station.boolean("enabled") == false) return null
        val idObj = station.obj("id")
        val type = idObj?.string("type")?.trim().orEmpty()
        val tag = idObj?.string("tag")?.trim().orEmpty()
        val idForFrom = station.string("idForFrom")?.trim() ?: wrapper.string("idForFrom")?.trim().orEmpty()

        val rawSeed = when {
            type.isNotBlank() && tag.isNotBlank() -> "$type:$tag"
            idForFrom.contains(':') -> idForFrom
            else -> ""
        }
        val seeds = parseSeeds(station, defaultPrefix = type, fallbackTag = tag)
            .ifEmpty { listOfNotNull(rawSeed.takeIf { it.isNotBlank() }) }

        if (seeds.isEmpty() || seeds.any { it == "user:onyourwave" }) return null
        val title = station.string("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: station.string("title")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null

        val categoryId = normalizeCategoryId(type.ifBlank { seeds.first().substringBefore(':', "") })
        val subtitle = wrapper.string("explanation")?.takeIf { it.isNotBlank() }
            ?: station.string("subTitle")?.takeIf { it.isNotBlank() }
            ?: if (isPersonal) categoryLabelIfHelpful(categoryId) else null

        return WaveThemeOption(
            id = seeds.joinToString(","),
            title = title,
            seeds = seeds,
            categoryId = categoryId,
            subtitle = subtitle,
            imageUri = extractImageUri(station),
            isPersonal = isPersonal
        )
    }

    private fun parseSeeds(obj: JsonObject, defaultPrefix: String, fallbackTag: String): List<String> {
        val fromArray = obj.array("seeds").mapNotNull { elem ->
            (elem as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }.map { normalizeSeedToken(it, defaultPrefix) }
        if (fromArray.isNotEmpty()) return fromArray.distinct()

        val single = obj.string("seed")?.trim()?.takeIf { it.isNotBlank() }
            ?: obj.string("value")?.trim()?.takeIf { it.isNotBlank() }
        if (single != null) return listOf(normalizeSeedToken(single, defaultPrefix))

        if (fallbackTag.contains(':')) return listOf(fallbackTag)
        if (defaultPrefix.isNotBlank() && fallbackTag.isNotBlank()) {
            return listOf("$defaultPrefix:$fallbackTag")
        }
        return emptyList()
    }

    private fun normalizeSeedToken(raw: String, defaultPrefix: String): String =
        if (raw.contains(':') || defaultPrefix.isBlank()) raw else "$defaultPrefix:$raw"

    private fun normalizeCategoryId(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("diversity") || lower.contains("character") -> "diversity"
            lower.contains("mood") -> "mood"
            lower.contains("activity") -> "activity"
            lower.contains("language") -> "language"
            lower.contains("epoch") || lower.contains("era") || lower.contains("decade") -> "epoch"
            lower.contains("genre") || lower.contains("style") -> "genre"
            else -> lower
        }
    }

    private fun categoryPrefix(raw: String): String = when (normalizeCategoryId(raw)) {
        "diversity" -> "diversity"
        "mood" -> "mood"
        "activity" -> "activity"
        "language" -> "language"
        "epoch" -> "epoch"
        "genre" -> "genre"
        else -> ""
    }

    private fun defaultCategoryTitle(categoryId: String): String = when (normalizeCategoryId(categoryId)) {
        "diversity" -> "Характер"
        "mood" -> "Настроение"
        "activity" -> "Занятия"
        "language" -> "Язык"
        "epoch" -> "Эпоха"
        "genre" -> "Жанры"
        else -> "Темы"
    }

    private fun categoryLabelIfHelpful(categoryId: String): String? = when (categoryId) {
        "mood" -> "Настроение"
        "activity" -> "Занятие"
        "epoch" -> "Эпоха"
        "genre" -> "Жанр"
        else -> null
    }

    private fun extractImageUri(obj: JsonObject): String? {
        return obj.string("fullImageUrl")?.takeIf { it.isNotBlank() }
            ?: obj.obj("icon")?.string("imageUrl")?.takeIf { it.isNotBlank() }
            ?: obj.obj("icon")?.string("uri")?.takeIf { it.isNotBlank() }
            ?: obj.string("coverUri")?.takeIf { it.isNotBlank() }
    }

    private fun parseJsonObject(raw: String): JsonObject? = runCatching {
        json.parseToJsonElement(raw) as? JsonObject
    }.getOrNull()

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.boolean(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
}
