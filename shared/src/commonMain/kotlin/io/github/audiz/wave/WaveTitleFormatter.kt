package io.github.audiz.wave

import io.github.audiz.models.WaveThemeCategory
import io.github.audiz.models.WaveThemeOption

/**
 * Результат форматирования названия «Моей волны» для широких и узких экранов.
 */
data class WaveDisplayInfo(
    val fullTitle: String,
    val compactTitle: String,
    val tags: List<String>
)

/**
 * Сортирует выбранные фильтры волны по смысловому приоритету (жанр/артист важнее настроения
 * и вторичных модификаторов) и сокращает длинные комбинации для компактных поверхностей.
 */
object WaveTitleFormatter {

    const val MAX_TWO_TAGS_COMPACT_LENGTH = 22

    private val CATEGORY_PRIORITY = mapOf(
        "genre" to 0,
        "artist" to 1,
        "track" to 2,
        "mood" to 3,
        "activity" to 4,
        "epoch" to 5,
        "language" to 6,
        "diversity" to 7
    )

    fun categoryPriority(categoryId: String): Int =
        CATEGORY_PRIORITY[categoryId.trim().lowercase()] ?: 8

    fun sortOptionsSemantically(options: List<WaveThemeOption>): List<WaveThemeOption> {
        if (options.size <= 1) return options
        return options.withIndex()
            .sortedWith(
                compareBy<IndexedValue<WaveThemeOption>> { (_, opt) -> categoryPriority(opt.categoryId) }
                    .thenBy { (idx, _) -> idx }
            )
            .map { it.value }
    }

    fun format(
        title: String?,
        seeds: List<String>,
        categories: List<WaveThemeCategory> = emptyList()
    ): WaveDisplayInfo {
        val rawTitle = title?.trim().orEmpty()
        val rawSegments = if (rawTitle.contains('•') || rawTitle.contains('·')) {
            rawTitle.split('•', '·').map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        // Именованная станция (например, «Энергия дня»), одиночный жанр, артист или трек не дробятся на теги.
        val isSingleNamedWave = seeds.size <= 1 || (rawTitle.isNotBlank() && rawSegments.size <= 1)
        if (isSingleNamedWave) {
            val single = rawTitle.ifBlank { resolveOptionForSeed(seeds.firstOrNull(), categories)?.title.orEmpty() }
            val tags = listOfNotNull(single.takeIf { it.isNotBlank() })
            return WaveDisplayInfo(
                fullTitle = single,
                compactTitle = single,
                tags = tags
            )
        }

        val resolvedTags = resolveMultiFilterTags(rawSegments, seeds, categories)
        if (resolvedTags.isEmpty()) {
            return WaveDisplayInfo(fullTitle = rawTitle, compactTitle = rawTitle, tags = emptyList())
        }

        val fullTitle = resolvedTags.joinToString(" • ")
        return WaveDisplayInfo(
            fullTitle = fullTitle,
            compactTitle = abbreviateTags(resolvedTags),
            tags = resolvedTags
        )
    }

    fun abbreviateTags(tags: List<String>): String = when {
        tags.isEmpty() -> ""
        tags.size == 1 -> tags.first()
        tags.size == 2 -> {
            val joined = "${tags[0]} • ${tags[1]}"
            if (joined.length <= MAX_TWO_TAGS_COMPACT_LENGTH) joined else "${tags[0]} +1"
        }
        else -> "${tags.first()} +${tags.size - 1}"
    }

    private fun resolveMultiFilterTags(
        rawSegments: List<String>,
        seeds: List<String>,
        categories: List<WaveThemeCategory>
    ): List<String> {
        val prioritized = mutableListOf<Pair<Int, String>>()
        val seenLower = mutableSetOf<String>()

        fun addTag(label: String, priority: Int) {
            val clean = label.trim()
            if (clean.isBlank()) return
            if (seenLower.add(clean.lowercase())) {
                prioritized.add(priority to clean)
            }
        }

        val optionBySeed = buildSeedLookup(categories)
        for (seed in seeds) {
            val opt = optionBySeed[seed.trim().lowercase()] ?: continue
            addTag(opt.title, categoryPriority(opt.categoryId))
        }

        val optionByTitle = buildTitleLookup(categories)
        for (segment in rawSegments) {
            val opt = optionByTitle[segment.trim().lowercase()]
            val priority = opt?.let { categoryPriority(it.categoryId) } ?: priorityFromSeedPrefix(segment)
            addTag(opt?.title ?: segment, priority)
        }

        return prioritized.withIndex()
            .sortedWith(compareBy<IndexedValue<Pair<Int, String>>> { it.value.first }.thenBy { it.index })
            .map { it.value.second }
    }

    private fun buildSeedLookup(categories: List<WaveThemeCategory>): Map<String, WaveThemeOption> {
        if (categories.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, WaveThemeOption>()
        for (cat in categories) {
            for (opt in cat.items) {
                for (seed in opt.seeds) {
                    map.getOrPut(seed.trim().lowercase()) { opt }
                }
            }
        }
        return map
    }

    private fun buildTitleLookup(categories: List<WaveThemeCategory>): Map<String, WaveThemeOption> {
        if (categories.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, WaveThemeOption>()
        for (cat in categories) {
            for (opt in cat.items) {
                map.getOrPut(opt.title.trim().lowercase()) { opt }
            }
        }
        return map
    }

    private fun resolveOptionForSeed(seed: String?, categories: List<WaveThemeCategory>): WaveThemeOption? {
        val clean = seed?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return categories.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { opt -> opt.seeds.any { it.trim().lowercase() == clean } }
    }

    private fun priorityFromSeedPrefix(raw: String): Int =
        if (raw.contains(':')) categoryPriority(raw.substringBefore(':')) else 8
}
