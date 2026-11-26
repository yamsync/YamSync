package io.github.audiz.player

import io.github.audiz.models.FullArtistInfo

data class PlayerUiState(
    val trackTitle: String = "Выберите трек",
    val artistName: String = "",
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bitrate: Int? = null,
    val volume: Float = 1f,
    val isShuffle: Boolean = false,
    val isNextLoading: Boolean = false,
    val isPrevLoading: Boolean = false,
    val isPlayLoading: Boolean = false,
    val isSavedToDisk: Boolean = false,
    val canSaveTrack: Boolean = false,
    val isSavingToDisk: Boolean = false,
    val isSharing: Boolean = false,
    val isFavorite: Boolean = false,
    val isDisliked: Boolean = false,
    val playlistCount: Int = 0,
    val isConfigActive: Boolean = false,
    val isEqualizerActive: Boolean = false,
    val hasTrackCandidate: Boolean = false,
    val isWave: Boolean = false,
    val waveTitle: String? = null,
    val waveCompactTitle: String? = null,
    val waveTags: List<String> = emptyList(),
    val coverUri: String? = null,
    val isMobile: Boolean = false,
    val isQueueOpen: Boolean = false,
    val upNextCount: Int = 0,
    val isLocalTrack: Boolean = false,
    val trackId: String? = null,
    val trackArtists: List<FullArtistInfo> = emptyList(),
) {
    val isActivelyPlaying: Boolean
        get() = isPlaying && !isPaused

    val isInPlaylist: Boolean
        get() = playlistCount > 0

    val isThematicWave: Boolean
        get() = isWave && !waveTitle.isNullOrBlank() && !waveTitle.equals("Моя Волна", ignoreCase = true)

    val contextLabel: String
        get() = when {
            isThematicWave -> waveCompactTitle?.takeIf { it.isNotBlank() } ?: waveTitle.orEmpty()
            isWave -> "Моя Волна"
            else -> "Сейчас играет"
        }

    val fullTrackTitle: String
        get() = if (artistName.isBlank()) trackTitle else "$artistName — $trackTitle"

    val progress: Float
        get() = if (durationMs > 0) {
            (currentPositionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        } else 0f

    fun positionAt(fraction: Float): Long =
        (fraction.coerceIn(0f, 1f).toDouble() * durationMs.coerceAtLeast(0L)).toLong()
}

data class PlayerActions(
    val onVolumeChange: (Float) -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onToggleShuffle: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
    val onPrev: (() -> Unit)? = null,
    val onConfigClick: (() -> Unit)? = null,
    val onSaveOrManageTrack: (() -> Unit)? = null,
    val onShareTrack: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
    val onToggleFavorite: (() -> Unit)? = null,
    val onManageTrackRemoval: (() -> Unit)? = null,
    val onOpenEqualizer: (() -> Unit)? = null,
    val onOpenQueue: (() -> Unit)? = null,
    val onOpenArtist: (() -> Unit)? = null,
    val onExpandMobilePlayer: (() -> Unit)? = null,
    val onResetWave: (() -> Unit)? = null,
    val onOpenWaveThemes: (() -> Unit)? = null,
    val onMobilePlayerDrag: (Float?) -> Unit = {},
    val onMobilePlayerDragEnd: (Boolean) -> Unit = {},
    val onOpenPlayingPlaylist: (() -> Unit)? = null,
)
