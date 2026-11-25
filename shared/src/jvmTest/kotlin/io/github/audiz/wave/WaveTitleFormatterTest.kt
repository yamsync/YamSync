package io.github.audiz.wave

import io.github.audiz.models.WaveThemeCategory
import io.github.audiz.models.WaveThemeOption
import kotlin.test.Test
import kotlin.test.assertEquals

class WaveTitleFormatterTest {

    private fun sampleCategories() = listOf(
        WaveThemeCategory(
            id = "diversity",
            title = "Характер",
            items = listOf(WaveThemeOption("diversity:discover", "Незнакомое", listOf("diversity:discover"), "diversity"))
        ),
        WaveThemeCategory(
            id = "mood",
            title = "Настроение",
            items = listOf(WaveThemeOption("mood:active", "Бодрое", listOf("mood:active"), "mood"))
        ),
        WaveThemeCategory(
            id = "language",
            title = "Язык",
            items = listOf(WaveThemeOption("language:russian", "На русском", listOf("language:russian"), "language"))
        ),
        WaveThemeCategory(
            id = "genre",
            title = "Жанры",
            items = listOf(
                WaveThemeOption("genre:poprock", "Поп-рок", listOf("genre:pop-rock"), "genre"),
                WaveThemeOption("genre:rock", "Рок", listOf("genre:rock"), "genre"),
                WaveThemeOption("genre:alt", "Альтернативный рок", listOf("genre:alternative"), "genre")
            )
        )
    )

    @Test
    fun `sortOptionsSemantically puts genre and mood ahead of diversity and language`() {
        val cats = sampleCategories()
        val unsorted = listOf(
            cats[0].items.first(), // diversity
            cats[2].items.first(), // language
            cats[1].items.first(), // mood
            cats[3].items.first()  // genre
        )

        val sorted = WaveTitleFormatter.sortOptionsSemantically(unsorted)

        assertEquals(listOf("Поп-рок", "Бодрое", "На русском", "Незнакомое"), sorted.map { it.title })
    }

    @Test
    fun `format prioritizes multi-filter tags and abbreviates to primary plus count`() {
        val display = WaveTitleFormatter.format(
            title = "Незнакомое • Бодрое • На русском • Поп-рок",
            seeds = listOf("diversity:discover", "mood:active", "language:russian", "genre:pop-rock"),
            categories = sampleCategories()
        )

        assertEquals(listOf("Поп-рок", "Бодрое", "На русском", "Незнакомое"), display.tags)
        assertEquals("Поп-рок • Бодрое • На русском • Незнакомое", display.fullTitle)
        assertEquals("Поп-рок +3", display.compactTitle)
    }

    @Test
    fun `format keeps two short tags together and abbreviates two long tags`() {
        val shortTwo = WaveTitleFormatter.format(
            title = "Бодрое • Рок",
            seeds = listOf("mood:active", "genre:rock"),
            categories = sampleCategories()
        )
        assertEquals("Рок • Бодрое", shortTwo.compactTitle)

        val longTwo = WaveTitleFormatter.format(
            title = "Альтернативный рок • Тренировка",
            seeds = listOf("genre:alternative", "activity:workout"),
            categories = sampleCategories()
        )
        assertEquals("Альтернативный рок +1", longTwo.compactTitle)
    }

    @Test
    fun `format preserves named multi-seed stations and single track titles with bullet`() {
        val station = WaveTitleFormatter.format(
            title = "Энергия дня",
            seeds = listOf("mood:active", "genre:rock"),
            categories = sampleCategories()
        )
        assertEquals("Энергия дня", station.compactTitle)
        assertEquals(listOf("Энергия дня"), station.tags)

        val trackWave = WaveTitleFormatter.format(
            title = "Похожее на «Track • Live»",
            seeds = listOf("track:12345"),
            categories = sampleCategories()
        )
        assertEquals("Похожее на «Track • Live»", trackWave.compactTitle)
        assertEquals(listOf("Похожее на «Track • Live»"), trackWave.tags)
    }
}
