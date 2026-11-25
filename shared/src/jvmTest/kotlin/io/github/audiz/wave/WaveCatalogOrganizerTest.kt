package io.github.audiz.wave

import io.github.audiz.models.WaveThemeOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaveCatalogOrganizerTest {

    private fun genre(tag: String, title: String) = WaveThemeOption(
        id = "genre:$tag",
        title = title,
        seeds = listOf("genre:$tag"),
        categoryId = "genre"
    )

    @Test
    fun `matchesQuery supports bilingual aliases and multi-token queries`() {
        val hipHop = genre("rap", "Хип-хоп")
        val loFi = genre("lofi", "Lo-Fi")
        val rusRock = genre("rusrock", "Русский рок")
        val dnb = genre("dnb", "Drum & Bass")
        val rnb = genre("rnb", "R&B")

        assertTrue(WaveCatalogOrganizer.matchesQuery(hipHop, "рэп"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(hipHop, "hip-hop"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(loFi, "лоуфай"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(loFi, "lofi"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(rusRock, "рус рок"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(rusRock, "rock"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(dnb, "драм"))
        assertTrue(WaveCatalogOrganizer.matchesQuery(rnb, "рнб"))
        assertFalse(WaveCatalogOrganizer.matchesQuery(loFi, "метал"))
    }

    @Test
    fun `matchScore ranks exact and prefix matches above secondary matches`() {
        val exact = genre("rock", "Рок")
        val prefix = genre("rocknroll", "Рок-н-ролл")
        val suffix = genre("indierock", "Инди-рок")

        val exactScore = WaveCatalogOrganizer.matchScore(exact, "рок")
        val prefixScore = WaveCatalogOrganizer.matchScore(prefix, "рок")
        val suffixScore = WaveCatalogOrganizer.matchScore(suffix, "рок")

        assertTrue(exactScore > prefixScore)
        assertTrue(prefixScore > suffixScore)
        assertTrue(suffixScore > 0)
    }

    @Test
    fun `prioritizeGenres lifts personal affinity then core presets then remaining subgenres`() {
        val items = listOf(
            genre("chamber", "Камерная музыка"),
            genre("synthwave", "Синтвейв"),
            genre("pop", "Поп"),
            genre("rock", "Рок"),
            genre("accordions", "Баян")
        )
        val sorted = WaveCatalogOrganizer.prioritizeGenres(
            items = items,
            affinityWeights = mapOf("synthwave" to 8, "rusrock" to 4),
            corePresetSeeds = listOf("genre:rock", "genre:pop")
        )

        assertEquals(
            listOf("synthwave", "rock", "pop", "chamber", "accordions"),
            sorted.map { it.seeds.first().substringAfter(':') }
        )
    }

    @Test
    fun `buildCollapsedGenres always includes selected genre within limit`() {
        val items = (1..20).map { i -> genre("g$i", "Жанр $i") }
        val selected = items.last()

        val collapsed = WaveCatalogOrganizer.buildCollapsedGenres(
            prioritizedItems = items,
            selectedOption = selected,
            limit = 14
        )

        assertEquals(14, collapsed.size)
        assertTrue(collapsed.any { it.id == selected.id })
        assertEquals(selected.id, collapsed.first().id)
    }

    @Test
    fun `groupGenresByFamily classifies genres accurately and preserves all items`() {
        val items = listOf(
            genre("poprock", "Поп-рок"),
            genre("baroque", "Барокко"),
            genre("synthpop", "Синти-поп"),
            genre("lofi", "Lo-Fi"),
            genre("phonk", "Фонк"),
            genre("soundtrack", "Саундтреки"),
            genre("folkmetal", "Фолк-метал")
        )

        val groups = WaveCatalogOrganizer.groupGenresByFamily(items).associateBy { it.id }

        assertEquals(
            listOf("Поп-рок", "Фолк-метал"),
            groups["rock_metal"]?.items?.map { it.title }
        )
        assertEquals(listOf("Барокко"), groups["jazz_classical"]?.items?.map { it.title })
        assertEquals(listOf("Синти-поп"), groups["pop_dance"]?.items?.map { it.title })
        assertEquals(listOf("Lo-Fi"), groups["electronic"]?.items?.map { it.title })
        assertEquals(listOf("Фонк"), groups["hiphop_rnb"]?.items?.map { it.title })
        assertEquals(listOf("Саундтреки"), groups["other"]?.items?.map { it.title })
        assertEquals(items.size, groups.values.sumOf { it.items.size })
    }
}
