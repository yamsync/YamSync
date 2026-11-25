package io.github.audiz.addons

import io.github.audiz.DispatcherIO
import io.github.audiz.models.FullArtistInfo
import io.github.audiz.models.FullTrackInfo
import kotlinx.coroutines.withContext
import yandexmusicdownloader.shared.generated.resources.Res

enum class AiFilterMode(val title: String) {
    OFF("Выключено"),
    LABELS("Только метки"),
    SKIP_IN_WAVE("Пропускать в Моей волне")
}

/** TRACK / ARTIST: listed as AI. SUSPECT_ARTIST: the artist has AI tracks, but is not blacklisted as a whole. */
internal enum class AiMatch { TRACK, ARTIST, SUSPECT_ARTIST }

/**
 * Sorted array of positive Int IDs: lookups by binary search, O(log n) (~19 steps for 316k IDs),
 * exactly 4 bytes per ID, no boxing, no per-entry objects and no build step after loading.
 */
internal class SortedIntIds(private val ids: IntArray) {
    val size: Int get() = ids.size

    operator fun contains(id: Long): Boolean {
        if (id <= 0L || id > Int.MAX_VALUE) return false
        val key = id.toInt()
        var lo = 0
        var hi = ids.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = ids[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }

    companion object {
        val EMPTY = SortedIntIds(IntArray(0))

        fun of(ids: Collection<Long>): SortedIntIds =
            SortedIntIds(ids.filter { it in 1..Int.MAX_VALUE.toLong() }.map { it.toInt() }.distinct().sorted().toIntArray())
    }
}

internal class AiBlacklist(
    val version: Int,
    private val artists: SortedIntIds,
    private val tracks: SortedIntIds,
    private val suspectArtists: SortedIntIds = SortedIntIds.EMPTY,
) {
    fun containsArtist(artistId: Long): Boolean = artistId in artists

    /** The artist has at least one AI track but is not blacklisted as a whole ("possibly AI"). */
    fun isSuspectArtist(artistId: Long): Boolean = artistId in suspectArtists

    fun containsTrack(trackId: String?): Boolean {
        val id = trackId?.substringBefore(':')?.toLongOrNull() ?: return false
        return id in tracks
    }

    fun matchArtist(artistId: Long): AiMatch? = when {
        containsArtist(artistId) -> AiMatch.ARTIST
        isSuspectArtist(artistId) -> AiMatch.SUSPECT_ARTIST
        else -> null
    }

    fun matchArtists(artists: List<FullArtistInfo>): AiMatch? = when {
        artists.any { containsArtist(it.id) } -> AiMatch.ARTIST
        artists.any { isSuspectArtist(it.id) } -> AiMatch.SUSPECT_ARTIST
        else -> null
    }

    fun matchTrack(trackId: String?, trackArtists: List<FullArtistInfo>): AiMatch? =
        if (containsTrack(trackId)) AiMatch.TRACK else matchArtists(trackArtists)

    fun shouldSkipInWave(track: FullTrackInfo, likedArtistIds: Set<Long>?, isTrackLiked: () -> Boolean): Boolean =
        likedArtistIds != null &&
            containsTrack(track.id) &&
            track.artists.none { it.id in likedArtistIds } &&
            !isTrackLiked()

    companion object {
        fun of(version: Int, artists: Set<Long>, tracks: Set<Long>, suspectArtists: Set<Long> = emptySet()) =
            AiBlacklist(version, SortedIntIds.of(artists), SortedIntIds.of(tracks), SortedIntIds.of(suspectArtists))

        /**
         * Reads the binary bundle produced by yam-ai-blacklist `export-client` (little-endian):
         *   "YAB" + format version 0x01 | int32 database version |
         *   3 sections: int32 count, then count x int32 sorted IDs (artists, tracks, suspect artists)
         * Loading is a straight copy into IntArrays: no text parsing, no hashing, no sorting.
         */
        fun parse(bytes: ByteArray): AiBlacklist {
            require(bytes.size >= 8 && bytes[0] == 'Y'.code.toByte() && bytes[1] == 'A'.code.toByte() &&
                bytes[2] == 'B'.code.toByte() && bytes[3] == FORMAT_VERSION) { "Unsupported AI blacklist format" }
            var offset = 8
            fun section(): SortedIntIds {
                require(offset + 4 <= bytes.size) { "Truncated AI blacklist" }
                val count = readIntLe(bytes, offset)
                require(count >= 0 && offset + 4 + count.toLong() * 4 <= bytes.size) { "Truncated AI blacklist" }
                offset += 4
                val ids = IntArray(count) { readIntLe(bytes, offset + it * 4) }
                offset += count * 4
                return SortedIntIds(ids)
            }
            return AiBlacklist(
                version = readIntLe(bytes, 4),
                artists = section(),
                tracks = section(),
                suspectArtists = section(),
            )
        }

        private fun readIntLe(b: ByteArray, i: Int): Int =
            (b[i].toInt() and 0xFF) or
                ((b[i + 1].toInt() and 0xFF) shl 8) or
                ((b[i + 2].toInt() and 0xFF) shl 16) or
                (b[i + 3].toInt() shl 24)

        private const val FORMAT_VERSION: Byte = 1
    }
}

internal suspend fun loadBundledAiBlacklist(): AiBlacklist = withContext(DispatcherIO) {
    AiBlacklist.parse(Res.readBytes("files/ai_blacklist.bin"))
}
