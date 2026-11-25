package io.github.audiz.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaveCatalogLoaderTest {

    private val settingsJson = """
        {
          "result": {
            "blocks": [
              {
                "id": "diversity",
                "name": "Характер",
                "sets": [
                  {
                    "id": "main",
                    "items": [
                      {"id": "discover", "name": "Незнакомое", "seeds": ["diversity:discover"]},
                      {"id": "favorite", "name": "Любимое", "seeds": ["diversity:favorite"]}
                    ]
                  }
                ]
              },
              {
                "id": "activity",
                "name": "Занятия",
                "items": [
                  {"id": "workout", "name": "Тренировка", "seeds": ["activity:workout"]},
                  {"id": "driving", "name": "В дороге", "seeds": ["activity:driving"]}
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private val dashboardJson = """
        {
          "result": {
            "stations": [
              {
                "station": {
                  "id": {"type": "user", "tag": "onyourwave"},
                  "name": "Моя волна",
                  "enabled": true
                }
              },
              {
                "station": {
                  "id": {"type": "mood", "tag": "active"},
                  "name": "Энергичное",
                  "fullImageUrl": "avatars.yandex.net/mood-active/%%",
                  "enabled": true
                },
                "explanation": "Подходит для утра"
              },
              {
                "station": {
                  "id": {"type": "epoch", "tag": "nineties"},
                  "name": "90-е",
                  "enabled": true
                }
              }
            ]
          }
        }
    """.trimIndent()

    private val stationsListJson = """
        {
          "result": [
            {"station": {"id": {"type": "user", "tag": "onyourwave"}, "name": "Моя волна"}},
            {"station": {"id": {"type": "mood", "tag": "calm"}, "name": "Спокойное"}},
            {"station": {"id": {"type": "activity", "tag": "work"}, "name": "Работа"}},
            {"station": {"id": {"type": "epoch", "tag": "twothousands"}, "name": "2000-е"}}
          ]
        }
    """.trimIndent()

    @Test
    fun loadsWaveSettingsAndPersonalizedDashboardStations() = runBlocking {
        val requested = mutableListOf<String>()
        val loader = WaveCatalogLoader { url ->
            requested.add(url)
            when (url) {
                ApiUrls.ROTOR_WAVE_SETTINGS_URL -> settingsJson
                ApiUrls.ROTOR_STATIONS_DASHBOARD_URL -> dashboardJson
                else -> error("Unexpected fallback request: $url")
            }
        }

        val catalog = loader.load()

        assertEquals(2, requested.size)
        assertEquals(listOf("Энергичное", "90-е"), catalog.personalStations.map { it.title })
        assertEquals("Подходит для утра", catalog.personalStations.first().subtitle)
        assertTrue(catalog.personalStations.all { it.isPersonal })

        assertEquals(listOf("diversity", "activity"), catalog.categories.map { it.id })
        assertEquals(listOf("Незнакомое", "Любимое"), catalog.categories[0].items.map { it.title })
        assertEquals(listOf("activity:workout"), catalog.categories[1].items[0].seeds)
    }

    @Test
    fun fallsBackToStationsListAndRestrictionsWhenWaveSettingsUnavailable() = runBlocking {
        val restrictionsSettingsJson = """
            {
              "result": {
                "restrictions2": {
                  "language": {
                    "name": "Язык",
                    "possibleValues": [
                      {"name": "Любой", "value": "any"},
                      {"name": "На русском", "value": "russian"},
                      {"name": "Иностранное", "value": "not-russian"}
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val loader = WaveCatalogLoader { url ->
            when (url) {
                ApiUrls.ROTOR_WAVE_SETTINGS_URL -> restrictionsSettingsJson
                ApiUrls.ROTOR_STATIONS_DASHBOARD_URL -> dashboardJson
                ApiUrls.ROTOR_STATIONS_LIST_URL -> stationsListJson
                else -> null
            }
        }

        val catalog = loader.load()
        assertEquals(listOf("mood", "activity", "language", "epoch"), catalog.categories.map { it.id })
        assertEquals(
            listOf("На русском", "Иностранное"),
            catalog.categories.first { it.id == "language" }.items.map { it.title }
        )
        assertEquals(
            listOf("Спокойное"),
            catalog.categories.first { it.id == "mood" }.items.map { it.title }
        )
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        assertFailsWith<CancellationException> {
            WaveCatalogLoader { throw CancellationException("cancelled") }.load()
        }
        Unit
    }
}
