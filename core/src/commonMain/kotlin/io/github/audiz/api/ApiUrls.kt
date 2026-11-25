package io.github.audiz.api

object ApiUrls {
    const val SEARCH_URL = "https://api.music.yandex.ru/search/instant/mixed"
    const val TRACKS_URL  = "https://api.music.yandex.ru/tracks"
    const val LANDING_LIKES_URL = "https://api.music.yandex.ru/landing-blocks/collection/playlist-with-likes"
    const val PLAYLIST_BY_UUID_URL = "https://api.music.yandex.ru/playlist" // + /{uuid}?resumeStream=false&richTracks=false
    const val HISTORY_URL = "https://api.music.yandex.ru/music-history"
    const val PERSONAL_FEED_URL = "https://api.music.yandex.ru/feed"
    const val GENERATED_PLAYLIST_BASE_URL = "https://api.music.yandex.ru/playlists/personal"
    const val PERSONAL_PLAYLISTS_URL = "https://api.music.yandex.ru/landing-blocks/personal-playlists"
    const val USER_PLAYLISTS_BASE_URL = "https://api.music.yandex.ru/users"
    const val LANDING3_URL = "https://api.music.yandex.ru/landing3"
    const val LANDING3_CHART_URL = "https://api.music.yandex.ru/landing3/chart"
    const val LANDING3_NEW_RELEASES_URL = "https://api.music.yandex.ru/landing3/new-releases"
    const val LANDING3_NEW_PLAYLISTS_URL = "https://api.music.yandex.ru/landing3/new-playlists"
    const val ROTOR_WAVE_SETTINGS_URL = "https://api.music.yandex.ru/rotor/wave/settings"
    const val ROTOR_STATIONS_DASHBOARD_URL = "https://api.music.yandex.ru/rotor/stations/dashboard"
    const val ROTOR_STATIONS_LIST_URL = "https://api.music.yandex.ru/rotor/stations/list"
}