package io.github.audiz.player

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LocalTrackResolverTest {
    @Test
    fun doesNotUseAnotherArtistsDownloadWithTheSameTitle() {
        val directory = createTempDirectory().toFile()
        try {
            val file = directory.resolve("LQ/Roger Sanchez/Roger Sanchez — Lost.m4a")
            file.parentFile.mkdirs()
            file.createNewFile()
            val resolver = LocalTrackResolver { directory.absolutePath }

            assertNull(resolver.findLocalTrackFile("52334482", "The Cranberries", "Lost"))
            assertFalse(resolver.isTrackDownloaded("The Cranberries", "Lost"))
            assertNull(resolver.getDownloadedTrackPath("", "Lost"))
            assertEquals(file.absolutePath, resolver.getDownloadedTrackPath("Roger Sanchez", "Lost"))
            assertEquals(file.absolutePath, resolver.findLocalTrackFile("local:${file.absolutePath}", "", ""))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun matchesNamesAsWrittenByTheDownloader() {
        val directory = createTempDirectory().toFile()
        try {
            val file = directory.resolve("HQ/Artist _ Band, Guest/Artist _ Band, Guest — Why_.flac")
            file.parentFile.mkdirs()
            file.createNewFile()
            val resolver = LocalTrackResolver { directory.absolutePath }

            assertEquals(file.absolutePath, resolver.getDownloadedTrackPath("Artist / Band, Guest", "Why?"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
