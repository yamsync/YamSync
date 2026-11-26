package io.github.audiz.ui

data class TrackItemState(
    val isDownloading: Boolean = false,
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isDownloaded: Boolean = false,
    val isInPlaylist: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
)

data class TrackItemActions(
    val onPlay: () -> Unit,
    val onTogglePlayPause: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onDownload: () -> Unit,
    val onDelete: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null,
)
