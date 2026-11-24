package io.github.audiz.player

import io.github.audiz.loadAppConfig
import io.github.audiz.saveAppConfig

private const val HISTORY_KEY = "recent_playback_history"

internal actual fun readRecentPlaybackHistory(): String? = loadAppConfig(HISTORY_KEY)

internal actual fun writeRecentPlaybackHistory(value: String) = saveAppConfig(HISTORY_KEY, value)
