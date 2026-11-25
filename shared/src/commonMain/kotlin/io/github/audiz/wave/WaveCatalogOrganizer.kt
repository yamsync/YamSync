package io.github.audiz.wave

import io.github.audiz.models.ThematicWavePreset
import io.github.audiz.models.WaveThemeOption

/**
 * Группа жанров внутри раскрытого списка настроек «Моей волны».
 */
data class WaveGenreFamilyGroup(
    val id: String,
    val title: String,
    val items: List<WaveThemeOption>
)

/**
 * Отвечает за поиск (включая билингвальные синонимы), персональную приоритизацию
 * и группировку жанров/тем «Моей волны» на клиенте.
 */
object WaveCatalogOrganizer {

    const val DEFAULT_COLLAPSED_GENRE_LIMIT = 14

    private enum class GenreFamily(val id: String, val title: String) {
        ROCK_METAL("rock_metal", "Рок и метал"),
        POP_DANCE("pop_dance", "Поп и танцевальная"),
        ELECTRONIC("electronic", "Электроника и Lo-Fi"),
        HIPHOP_RNB("hiphop_rnb", "Хип-хоп, R&B и фанк"),
        JAZZ_CLASSICAL("jazz_classical", "Джаз, блюз и классика"),
        OTHER("other", "Фолк, OST и другое")
    }

    private val PUNCTUATION_REGEX = Regex("[-_/.,():\"'«»]+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private val SEARCH_ALIAS_GROUPS: List<List<String>> = listOf(
        listOf("rock", "рок"),
        listOf("pop", "поп"),
        listOf("rap", "hiphop", "рэп", "реп", "хипхоп"),
        listOf("electronic", "electronics", "edm", "электроника", "электро", "дэнс", "dance", "танцевальная"),
        listOf("metal", "метал", "металл"),
        listOf("jazz", "джаз"),
        listOf("blues", "блюз"),
        listOf("classical", "classic", "классика", "классическая"),
        listOf("indie", "инди"),
        listOf("alternative", "alt", "альтернатива"),
        listOf("punk", "панк"),
        listOf("lofi", "лоуфай"),
        listOf("synthpop", "синтипоп"),
        listOf("dnb", "драм", "днб"),
        listOf("rnb", "рнб"),
        listOf("kpop", "кпоп", "кейпоп"),
        listOf("jpop", "джейпоп"),
        listOf("folk", "фолк"),
        listOf("shanson", "chanson", "шансон"),
        listOf("soundtrack", "ost", "films", "саундтрек", "кино"),
        listOf("ambient", "эмбиент", "амбиент"),
        listOf("techno", "техно"),
        listOf("house", "хаус"),
        listOf("trance", "транс"),
        listOf("dubstep", "дабстеп"),
        listOf("reggae", "регги"),
        listOf("funk", "фанк"),
        listOf("soul", "соул"),
        listOf("disco", "диско"),
        listOf("country", "кантри"),
        listOf("phonk", "фонк"),
        listOf("trap", "трэп"),
        listOf("rus", "russian", "рус", "русск", "русский", "русская", "на русском"),
        listOf("foreign", "зарубеж", "иностран", "иностранное"),
        listOf("children", "forchildren", "kids", "детск", "детям"),
        listOf("relax", "chill", "chillout", "calm", "спокойн", "расслаб", "чилл"),
        listOf("sad", "грустн", "печальн"),
        listOf("fun", "happy", "весел", "позитив"),
        listOf("active", "energetic", "workout", "sport", "бодр", "энергич", "спорт", "трениров"),
        listOf("drive", "car", "дорог", "за рулем", "машин"),
        listOf("sleep", "сон", "засыпать"),
        listOf("work", "focus", "работ", "учеб"),
        listOf("party", "вечерин"),
        listOf("discover", "незнаком"),
        listOf("favorite", "любим"),
        listOf("popular", "популярн")
    ).map { group -> group.map(::normalizeSearchText).distinct() }

    private val GENRE_TAG_STEM_FALLBACKS: Map<String, String> = mapOf(
        "electronics" to "electronic",
        "rusrock" to "rock",
        "poprock" to "rock",
        "pop-rock" to "rock",
        "altrock" to "rock",
        "hardrock" to "rock",
        "rusrap" to "rap",
        "foreignrap" to "rap",
        "hiphop" to "rap",
        "hip-hop" to "rap",
        "ruspop" to "pop",
        "europop" to "pop",
        "rusfolk" to "folk"
    )

    fun matchesQuery(option: WaveThemeOption, query: String): Boolean =
        matchScore(option.title, option.subtitle, option.seeds, query) > 0

    fun matchesQuery(preset: ThematicWavePreset, query: String): Boolean =
        matchScore(preset.title, preset.subTitle, preset.seeds, query) > 0

    fun matchScore(option: WaveThemeOption, query: String): Int =
        matchScore(option.title, option.subtitle, option.seeds, query)

    fun matchScore(title: String, subtitle: String?, seeds: List<String>, query: String): Int {
        val queryNorm = normalizeSearchText(query)
        if (queryNorm.isBlank()) return 1

        val titleNorm = normalizeSearchText(title)
        val subtitleNorm = normalizeSearchText(subtitle.orEmpty())
        val seedTagsNorm = seeds
            .filterNot { it.startsWith("artist:", ignoreCase = true) || it.startsWith("track:", ignoreCase = true) }
            .joinToString(" ") { normalizeSearchText(it.substringAfter(':')) }

        val combined = listOf(titleNorm, subtitleNorm, seedTagsNorm)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val combinedCompact = combined.replace(" ", "")
        val queryTokens = queryNorm.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return 1

        var directTitleMatches = 0
        var aliasTitleMatches = 0
        for (token in queryTokens) {
            val variants = expandTokenVariants(token)
            val matchesCombined = variants.any { variant ->
                combined.contains(variant) || combinedCompact.contains(variant.replace(" ", ""))
            }
            if (!matchesCombined) return 0

            val titleCompact = titleNorm.replace(" ", "")
            when {
                titleNorm.contains(token) || titleCompact.contains(token) -> directTitleMatches++
                variants.any { variant ->
                    titleNorm.contains(variant) || titleCompact.contains(variant.replace(" ", ""))
                } -> aliasTitleMatches++
            }
        }

        val firstToken = queryTokens.first()
        val titleWords = titleNorm.split(' ').filter { it.isNotBlank() }
        return when {
            titleNorm == queryNorm -> 1000
            titleNorm.startsWith(queryNorm) -> 800
            titleWords.any { it.startsWith(firstToken) } -> 600
            titleNorm.contains(queryNorm) -> 450
            directTitleMatches == queryTokens.size -> 300
            directTitleMatches + aliasTitleMatches == queryTokens.size -> 200
            else -> 100
        }
    }

    /**
     * Сортирует жанры: сначала персонально релевантные, затем базовые макро-жанры,
     * затем остальные поджанры с сохранением исходного порядка внутри уровня.
     */
    fun prioritizeGenres(
        items: List<WaveThemeOption>,
        affinityWeights: Map<String, Int>,
        corePresetSeeds: List<String>
    ): List<WaveThemeOption> {
        if (items.size <= 1) return items
        val coreOrder = corePresetSeeds
            .mapNotNull { normalizeGenreTag(it) }
            .distinct()
            .withIndex()
            .associate { (idx, tag) -> tag to idx }

        val normalizedWeights = buildNormalizedAffinityWeights(affinityWeights)

        return items.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<WaveThemeOption>> { (_, option) ->
                    scoreGenreOption(option, normalizedWeights)
                }.thenBy { (_, option) ->
                    val tag = optionGenreTag(option)
                    coreOrder[tag] ?: Int.MAX_VALUE
                }.thenBy { (index, _) ->
                    index
                }
            )
            .map { it.value }
    }

    /**
     * Возвращает компактный срез жанров для свёрнутого состояния, гарантируя наличие выбранного жанра.
     */
    fun buildCollapsedGenres(
        prioritizedItems: List<WaveThemeOption>,
        selectedOption: WaveThemeOption?,
        limit: Int = DEFAULT_COLLAPSED_GENRE_LIMIT
    ): List<WaveThemeOption> {
        if (prioritizedItems.size <= limit) return prioritizedItems
        val topSlice = prioritizedItems.take(limit)
        if (selectedOption == null) return topSlice
        val selectedMatchesTop = topSlice.any { it.id == selectedOption.id || it.seeds == selectedOption.seeds }
        if (selectedMatchesTop) return topSlice

        val selectedItem = prioritizedItems.firstOrNull {
            it.id == selectedOption.id || it.seeds == selectedOption.seeds
        } ?: selectedOption
        return (listOf(selectedItem) + topSlice).take(limit)
    }

    /**
     * Группирует жанры по семействам для удобного просмотра при раскрытии полного каталога.
     */
    fun groupGenresByFamily(items: List<WaveThemeOption>): List<WaveGenreFamilyGroup> {
        if (items.isEmpty()) return emptyList()
        val buckets = linkedMapOf<GenreFamily, MutableList<WaveThemeOption>>()
        for (option in items) {
            val family = classifyGenreFamily(option)
            buckets.getOrPut(family) { mutableListOf() }.add(option)
        }
        return GenreFamily.entries.mapNotNull { family ->
            val groupItems = buckets[family].orEmpty()
            if (groupItems.isEmpty()) null else WaveGenreFamilyGroup(
                id = family.id,
                title = family.title,
                items = groupItems
            )
        }
    }

    private fun buildNormalizedAffinityWeights(rawWeights: Map<String, Int>): Map<String, Int> {
        if (rawWeights.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        for ((rawTag, weight) in rawWeights) {
            if (weight <= 0) continue
            val tag = normalizeGenreTag(rawTag) ?: continue
            result[tag] = (result[tag] ?: 0) + weight
            val parentStem = GENRE_TAG_STEM_FALLBACKS[tag]
            if (parentStem != null) {
                val parentBoost = (weight / 2).coerceAtLeast(1)
                result[parentStem] = (result[parentStem] ?: 0) + parentBoost
            }
        }
        return result
    }

    private fun scoreGenreOption(option: WaveThemeOption, normalizedWeights: Map<String, Int>): Int {
        if (normalizedWeights.isEmpty()) return 0
        var best = 0
        for (seed in option.seeds) {
            val tag = normalizeGenreTag(seed) ?: continue
            val direct = normalizedWeights[tag] ?: 0
            val stem = GENRE_TAG_STEM_FALLBACKS[tag]?.let { (normalizedWeights[it] ?: 0) / 2 } ?: 0
            best = maxOf(best, direct, stem)
        }
        return best
    }

    private fun optionGenreTag(option: WaveThemeOption): String? {
        val genreSeed = option.seeds.firstOrNull { it.startsWith("genre:", ignoreCase = true) }
            ?: option.seeds.firstOrNull()
        return normalizeGenreTag(genreSeed)
    }

    private fun normalizeGenreTag(raw: String?): String? {
        val clean = raw?.trim()?.lowercase()?.substringAfter(':')?.takeIf { it.isNotBlank() } ?: return null
        return if (clean == "electronics") "electronic" else clean
    }

    private fun classifyGenreFamily(option: WaveThemeOption): GenreFamily {
        val seedTag = optionGenreTag(option).orEmpty()
        val titleNorm = normalizeSearchText(option.title)
        val tokens = (seedTag.split('-', '_', '/', ' ') + titleNorm.split(' '))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val isJazzClassical = tokens.any { t ->
            t.startsWith("baroque") || t.startsWith("барок") ||
                t.contains("jazz") || t.contains("джаз") ||
                t.contains("blues") || t.contains("блюз") ||
                t.contains("classic") || t.contains("классик") ||
                t.contains("opera") || t.contains("опер") ||
                t.contains("neoclassic") || t.contains("неоклас") ||
                t.contains("symphon") || t.contains("симфон") ||
                t.contains("instrumental") || t.contains("инструмент") ||
                t == "lounge" || t == "лаунж" ||
                t == "chamber" || t == "камерн" ||
                t == "swing" || t == "свинг"
        }
        if (isJazzClassical) return GenreFamily.JAZZ_CLASSICAL

        val isHipHopRnb = tokens.any { t ->
            t == "rap" || t.endsWith("rap") || t == "рэп" || t == "реп" ||
                t.contains("hiphop") || t.contains("хипхоп") || t == "rnb" || t == "рнб" ||
                t == "soul" || t == "соул" || t == "funk" || t == "фанк" ||
                t == "trap" || t == "трэп" || t == "phonk" || t == "фонк" ||
                t == "drill" || t == "дриль" || t == "grime" || t == "грайм"
        }
        if (isHipHopRnb) return GenreFamily.HIPHOP_RNB

        val isRockMetal = tokens.any { t ->
            t == "rock" || t.endsWith("rock") || t.startsWith("rock") ||
                t == "рок" || t.endsWith("рок") || t.startsWith("рок") ||
                t.contains("metal") || t.contains("метал") ||
                t.contains("punk") || t.contains("панк") ||
                t.contains("alt") || t.contains("альтернат") ||
                t == "indie" || t == "инди" ||
                t.contains("grunge") || t.contains("гранж") ||
                t.contains("hardcore") || t.contains("хардкор") ||
                t == "emo" || t == "эмо" ||
                t.contains("shoegaze") || t.contains("шугейз") ||
                t == "britpop" || t == "бритпоп"
        }
        if (isRockMetal) return GenreFamily.ROCK_METAL

        val isPopDance = tokens.any { t ->
            t == "pop" || t.endsWith("pop") || t.startsWith("pop") ||
                t == "поп" || t.endsWith("поп") || t.startsWith("поп") ||
                t.contains("disco") || t.contains("диско") ||
                t.contains("dance") || t.contains("танцев") || t == "дэнс" ||
                t.contains("estrada") || t.contains("эстрад") ||
                t == "club" || t == "клуб" || t == "клубная"
        }
        if (isPopDance) return GenreFamily.POP_DANCE

        val isElectronic = tokens.any { t ->
            t == "edm" ||
                t.contains("electro") || t.contains("электро") ||
                t.contains("lofi") || t.contains("лоу") ||
                t.contains("ambient") || t.contains("эмбиент") || t.contains("амбиент") ||
                t.contains("techno") || t.contains("техно") ||
                t == "house" || t.endsWith("house") || t == "хаус" || t.endsWith("хаус") ||
                t.contains("trance") || t.contains("транс") ||
                t.contains("dubstep") || t.contains("дабстеп") ||
                t == "dnb" || t == "днб" || t.contains("drum") || t.contains("драм") ||
                t.contains("chill") || t.contains("чилл") ||
                t == "idm" || t.contains("synth") || t.contains("синт") ||
                t.contains("retrowave") || t.contains("vaporwave") || t.contains("darkwave") ||
                t.contains("breakbeat") || t.contains("брейкбит") ||
                t.contains("garage") || t.contains("гэридж") ||
                t.contains("downtempo") || t.contains("даунтемпо") ||
                t.contains("hardstyle") || t.contains("хардстайл")
        }
        if (isElectronic) return GenreFamily.ELECTRONIC

        return GenreFamily.OTHER
    }

    private fun expandTokenVariants(token: String): List<String> {
        val variants = linkedSetOf(token)
        if (token.length < 2) return variants.toList()
        for (group in SEARCH_ALIAS_GROUPS) {
            val matchesGroup = group.any { alias ->
                alias == token || alias.startsWith(token) || (alias.length >= 4 && token.startsWith(alias))
            }
            if (matchesGroup) {
                variants.addAll(group)
            }
        }
        return variants.toList()
    }

    private fun normalizeSearchText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.trim()
            .lowercase()
            .replace('ё', 'е')
            .replace("r&b", "rnb")
            .replace("drum & bass", "dnb")
            .replace("drum and bass", "dnb")
            .replace("drumandbass", "dnb")
            .replace("драм-н-бейс", "dnb")
            .replace("драм н бейс", "dnb")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .replace("lo fi", "lofi")
            .replace("лоу фай", "лоуфай")
            .replace("hip hop", "hiphop")
            .replace("хип хоп", "хипхоп")
            .replace("k pop", "kpop")
            .replace("к поп", "кпоп")
            .replace("j pop", "jpop")
            .replace("дж поп", "джейпоп")
            .replace("джей поп", "джейпоп")
            .replace("синти поп", "синтипоп")
            .trim()
    }
}
