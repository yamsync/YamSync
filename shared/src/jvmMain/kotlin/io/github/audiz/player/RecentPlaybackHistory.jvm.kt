package io.github.audiz.player

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private val historyPath: Path
    get() = Path.of(System.getProperty("user.home", "."), ".yamsync", "recent-playback.json")

internal actual fun readRecentPlaybackHistory(): String? =
    try {
        Files.readString(historyPath)
    } catch (_: NoSuchFileException) {
        null
    }

internal actual fun writeRecentPlaybackHistory(value: String) {
    val path = historyPath
    Files.createDirectories(path.parent)
    val temporary = Files.createTempFile(path.parent, "recent-playback-", ".tmp")
    try {
        Files.writeString(temporary, value)
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}
