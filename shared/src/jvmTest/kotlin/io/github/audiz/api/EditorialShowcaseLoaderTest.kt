package io.github.audiz.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EditorialShowcaseLoaderTest {

    private val primaryChartJson = """
        {
          "result": {
            "menu": {
              "items": [
                {"title": "Россия", "url": "russia", "selected": true},
                {"title": "Мир", "url": "/chart/world", "selected": false}
              ]
            },
            "chart": {
              "id": "russia",
              "title": "Чарт",
              "description": "Популярные треки прямо сейчас",
              "uid": 414787002,
              "kind": 1002,
              "playlistUuid": "chart-ru-uuid",
              "trackCount": 2,
              "tracks": [
                {
                  "id": "101",
                  "track": {
                    "id": "101",
                    "title": "Хит один",
                    "durationMs": 180000,
                    "ogImage": "avatars.yandex.net/track101/%%",
                    "artists": [{"id": 1, "name": "Артист А"}]
                  },
                  "chart": {"position": 1, "progress": "up", "shift": 2, "listeners": 50000}
                },
                {
                  "id": "102",
                  "track": {
                    "id": "102",
                    "title": "Хит два",
                    "durationMs": 200000,
                    "coverUri": "avatars.yandex.net/track102/%%",
                    "artists": [{"id": 2, "name": "Артист Б"}]
                  },
                  "chart": {"position": 2, "progress": "new", "shift": 0}
                }
              ]
            }
          }
        }
    """.trimIndent()

    private val worldChartJson = """
        {
          "result": {
            "chart": {
              "id": "world",
              "title": "Мировой чарт",
              "cover": {"uri": "avatars.yandex.net/world/%%"},
              "tracks": [
                {
                  "id": "201",
                  "track": {
                    "id": "201",
                    "title": "Global Hit",
                    "durationMs": 190000,
                    "artists": [{"id": 3, "name": "World Star"}]
                  },
                  "chart": {"position": 1, "progress": "same", "shift": 0}
                }
              ]
            }
          }
        }
    """.trimIndent()

    private val playlistsLandingJson = """
        {
          "result": {
            "blocks": [
              {
                "id": "new-playlists",
                "type": "new-playlists",
                "entities": [
                  {
                    "type": "playlist",
                    "data": {
                      "uid": 9001,
                      "kind": 11,
                      "playlistUuid": "editorial-11",
                      "title": "Громкие новинки",
                      "description": "Свежие релизы недели",
                      "trackCount": 40,
                      "cover": {"itemsUri": ["avatars.yandex.net/pl11/%%"]}
                    }
                  },
                  {
                    "type": "playlist",
                    "data": {
                      "uid": 9002,
                      "kind": 12,
                      "title": "Пустой",
                      "trackCount": 0
                    }
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private val releasesLandingJson = """
        {
          "result": {
            "blocks": [
              {
                "id": "new-releases",
                "type": "new-releases",
                "entities": [
                  {
                    "type": "album",
                    "data": {
                      "id": 555,
                      "title": "Новый альбом",
                      "metaType": "single",
                      "year": 2026,
                      "tracksCount": 3,
                      "coverUri": "avatars.yandex.net/album555/%%",
                      "artists": [{"id": 10, "name": "Певец"}]
                    }
                  },
                  {
                    "type": "album",
                    "data": {
                      "id": 556,
                      "title": "Недоступный",
                      "available": false
                    }
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun loadsPrimaryAndMenuChartsPlusLandingReleasesAndPlaylists() = runBlocking {
        val requested = mutableListOf<String>()
        val loader = EditorialShowcaseLoader { url ->
            requested.add(url)
            when (url) {
                ApiUrls.LANDING3_CHART_URL -> primaryChartJson
                "${ApiUrls.LANDING3_CHART_URL}/world" -> worldChartJson
                "${ApiUrls.LANDING3_URL}?blocks=new-playlists,chart" -> playlistsLandingJson
                "${ApiUrls.LANDING3_URL}?blocks=new-releases" -> releasesLandingJson
                else -> null
            }
        }

        val showcase = loader.load()

        assertEquals(listOf("russia", "world"), showcase.charts.map { it.chartId })
        val ruChart = showcase.charts.first()
        assertEquals("Чарт • Россия", ruChart.title)
        assertEquals("avatars.yandex.net/track101/%%", ruChart.coverUri)
        assertEquals(2, ruChart.tracks.size)
        assertEquals("up", ruChart.tracks.first().chart?.progress)
        assertEquals(2, ruChart.tracks.first().chart?.shift)

        val worldChart = showcase.charts[1]
        assertEquals("Мировой чарт", worldChart.title)
        assertEquals("avatars.yandex.net/world/%%", worldChart.coverUri)

        assertEquals(1, showcase.newReleases.size)
        val release = showcase.newReleases.single()
        assertEquals(555L, release.id)
        assertEquals("single", release.type)
        assertEquals(3, release.trackCount)
        assertEquals(listOf("Певец"), release.artists)

        assertEquals(1, showcase.newPlaylists.size)
        val playlist = showcase.newPlaylists.single()
        assertEquals("Громкие новинки", playlist.title)
        assertEquals("avatars.yandex.net/pl11/%%", playlist.coverUri)
        assertEquals("editorial-11", playlist.playlistUuid)
        assertEquals(4, requested.size)
    }

    @Test
    fun fallsBackToLandingChartAndDedicatedEndpointsWhenPrimaryOrBlocksMissing() = runBlocking {
        val landingWithChartOnly = """
            {
              "result": {
                "blocks": [
                  {
                    "type": "chart",
                    "title": "Топ из лендинга",
                    "entities": [
                      {
                        "type": "chart-item",
                        "data": {
                          "track": {"id": "301", "title": "Landing Hit", "durationMs": 150000},
                          "chart": {"position": 1, "progress": "up", "shift": 1}
                        }
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
        val releasesFallback = """{"result":{"newReleases":[{"id":777,"title":"Релиз из фолбэка","artists":[{"name":"Артист"}]}]}}"""
        val playlistsFallback = """{"result":{"newPlaylists":[{"uid":5,"kind":8,"title":"Плейлист фолбэк","trackCount":12}]}}"""

        val loader = EditorialShowcaseLoader { url ->
            when (url) {
                ApiUrls.LANDING3_CHART_URL -> error("chart endpoint down")
                "${ApiUrls.LANDING3_URL}?blocks=new-playlists,chart" -> landingWithChartOnly
                ApiUrls.LANDING3_NEW_RELEASES_URL -> releasesFallback
                ApiUrls.LANDING3_NEW_PLAYLISTS_URL -> playlistsFallback
                else -> null
            }
        }

        val showcase = loader.load()
        assertEquals(1, showcase.charts.size)
        assertEquals("Топ из лендинга", showcase.charts.single().title)
        assertEquals("301", showcase.charts.single().tracks.single().track.id)
        assertEquals(777L, showcase.newReleases.single().id)
        assertEquals("Плейлист фолбэк", showcase.newPlaylists.single().title)
    }

    @Test
    fun localizesEnglishChartMetadataWhileKeepingTrackMetadata() {
        val englishChartJson = """
            {
              "result": {
                "menu": {
                  "items": [
                    {"title": "Russia", "url": "russia", "selected": true},
                    {"title": "World", "url": "world", "selected": false}
                  ]
                },
                "chart": {
                  "id": "russia",
                  "title": "Chart",
                  "description": "Tracks popular on Yandex Music right now",
                  "tracks": [
                    {"id": "1", "track": {"id": "1", "title": "YOASOBI - Idol", "durationMs": 180000}}
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = EditorialShowcaseCatalog.parseChart(englishChartJson)
        assertNotNull(parsed)
        assertEquals("Чарт • Россия", parsed.title)
        assertEquals(listOf("Россия", "Мир"), parsed.menu.map { it.title })
        assertEquals("Россия", parsed.description)
        assertEquals("YOASOBI - Idol", parsed.tracks.single().track.title)
    }

    @Test
    fun usesRussianAcceptLanguageForEditorialPlaylistsAndEnglishForTracksAndCharts() {
        assertEquals(
            ApiSessionContext.ACCEPT_LANGUAGE_RU,
            EditorialShowcaseCatalog.acceptLanguageForUrl("${ApiUrls.LANDING3_URL}?blocks=new-playlists,chart")
        )
        assertEquals(
            ApiSessionContext.ACCEPT_LANGUAGE_RU,
            EditorialShowcaseCatalog.acceptLanguageForUrl(ApiUrls.LANDING3_NEW_PLAYLISTS_URL)
        )
        assertEquals(
            ApiSessionContext.ACCEPT_LANGUAGE_TRACK_METADATA,
            EditorialShowcaseCatalog.acceptLanguageForUrl(ApiUrls.LANDING3_CHART_URL)
        )
        assertEquals(
            ApiSessionContext.ACCEPT_LANGUAGE_TRACK_METADATA,
            EditorialShowcaseCatalog.acceptLanguageForUrl("${ApiUrls.LANDING3_URL}?blocks=new-releases")
        )

        val ctx = ApiSessionContext()
        val defaultReq = HttpRequestBuilder().apply { with(ctx) { applyAuthHeaders() } }
        assertEquals(ApiSessionContext.ACCEPT_LANGUAGE_TRACK_METADATA, defaultReq.headers[HttpHeaders.AcceptLanguage])

        val ruReq = HttpRequestBuilder().apply { with(ctx) { applyAuthHeaders(ApiSessionContext.ACCEPT_LANGUAGE_RU) } }
        assertEquals(ApiSessionContext.ACCEPT_LANGUAGE_RU, ruReq.headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun loadChartUsesDirectAndDefaultFallback() = runBlocking {
        val loader = EditorialShowcaseLoader { url ->
            when (url) {
                "${ApiUrls.LANDING3_CHART_URL}/world" -> worldChartJson
                ApiUrls.LANDING3_CHART_URL -> primaryChartJson
                else -> null
            }
        }

        val world = loader.loadChart("/chart/world")
        assertNotNull(world)
        assertEquals("world", world.chartId)
        assertTrue(world.tracks.isNotEmpty())

        val russia = loader.loadChart("russia")
        assertNotNull(russia)
        assertEquals("russia", russia.chartId)
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        assertFailsWith<CancellationException> {
            EditorialShowcaseLoader { throw CancellationException("cancelled") }.load()
        }
        Unit
    }
}
