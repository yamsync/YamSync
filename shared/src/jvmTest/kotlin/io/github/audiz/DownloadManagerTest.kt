package io.github.audiz

import io.github.audiz.api.MusicRepository
import io.github.audiz.download.DownloadManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadManagerTest {

    @Test
    fun deletingTheSelectedFileCanKeepPlaylistEntriesAndDoesNotFallBackToAnotherFile() = runBlocking {
        val directory = Files.createTempDirectory("exact_track_delete").toFile()
        try {
            val selected = File(directory, "selected.flac").apply { writeText("selected") }
            val otherCopy = File(directory, "HQ/Artist/Artist — Track.flac").apply {
                parentFile.mkdirs()
                writeText("other")
            }
            val deleteNotifications = mutableListOf<Boolean>()
            var finished = CompletableDeferred<Unit>()
            val manager = DownloadManager(
                scope = this,
                repository = MusicRepository(),
                getMusicStoragePath = { directory.absolutePath },
                getSelectedQuality = { "2" },
                getPlayingTrackId = { null },
                getPlayingTrackTitle = { "" },
                getPlayingArtistName = { "" },
                getPlayingFilePath = { null },
                onTrackDeleted = { _, _, removePlaylistEntries -> deleteNotifications.add(removePlaylistEntries) },
                onError = { finished.complete(Unit) }
            )

            manager.deleteTrack("123", "Track", "Artist", selected.absolutePath, removePlaylistEntries = false)
            withTimeout(5_000) { finished.await() }
            assertFalse(selected.exists())
            assertTrue(otherCopy.exists())
            assertEquals(listOf(false), deleteNotifications)

            finished = CompletableDeferred()
            manager.deleteTrack("123", "Track", "Artist", selected.absolutePath, removePlaylistEntries = false)
            withTimeout(5_000) { finished.await() }
            assertTrue(otherCopy.exists(), "The confirmed file no longer exists; no other copy should be deleted")
            assertEquals(listOf(false), deleteNotifications)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createDownloadManager(storagePath: String = "/tmp"): DownloadManager {
        return DownloadManager(
            scope = CoroutineScope(Dispatchers.Unconfined),
            repository = MusicRepository(),
            getMusicStoragePath = { storagePath },
            getSelectedQuality = { "1" },
            getPlayingTrackId = { null },
            getPlayingTrackTitle = { "" },
            getPlayingArtistName = { "" },
            getPlayingFilePath = { null }
        )
    }

    @Test
    fun testSanitizeKeepSpaces() {
        val manager = createDownloadManager()
        val original = "AC/DC - Highway to Hell: Live*?\"<>|"
        val sanitized = manager.sanitizeKeepSpaces(original)
        assertEquals("AC_DC - Highway to Hell_ Live______", sanitized)
        // Spaces are preserved
        assertTrue(sanitized.contains(" - Highway to Hell_ Live"))
    }

    @Test
    fun testInitialStateAndIncrementVersion() {
        val manager = createDownloadManager()
        assertFalse(manager.isTrackDownloading)
        assertNull(manager.downloadingTrackId)
        assertEquals(0, manager.downloadVersion)

        manager.incrementDownloadVersion()
        assertEquals(1, manager.downloadVersion)
    }

    @Test
    fun testIsTrackDownloadedWithTempDir() {
        val tempDir = Files.createTempDirectory("download_manager_test").toFile()
        try {
            val manager = createDownloadManager(tempDir.absolutePath)
            val artist = "Queen"
            val title = "Bohemian Rhapsody"

            assertFalse(manager.isTrackDownloaded(artist, title))

            // Save file in HQ folder
            val hqFolder = java.io.File(tempDir, "HQ")
            hqFolder.mkdirs()
            val fileName = "$artist — $title.m4a"
            saveTrackFile(hqFolder.absolutePath, artist, fileName, "dummy_audio".toByteArray())

            assertTrue(manager.isTrackDownloaded(artist, title))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
