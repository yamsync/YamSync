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
 * Set of positive Int IDs on a primitive open-addressing table: O(1) lookups,
 * 4 bytes per slot (load factor <= 0.75), no boxing and no per-entry objects.
 */
internal class IntIdSet private constructor(private val table: IntArray, val size: Int) {
    private val mask = table.size - 1

    operator fun contains(id: Long): Boolean {
        if (id <= 0L || id > Int.MAX_VALUE) return false
        val key = id.toInt()
        var i = mix(key) and mask
        while (true) {
            val v = table[i]
            if (v == key) return true
            if (v == 0) return false
            i = (i + 1) and mask
        }
    }

    companion object {
        val EMPTY: IntIdSet = of(IntArray(0))

        private fun mix(x: Int): Int {
            val h = x * -0x61c88647 // Fibonacci hashing
            return h xor (h ushr 16)
        }

        fun of(ids: IntArray, count: Int = ids.size): IntIdSet {
            var cap = 16
            while (cap.toLong() * 3 < count.toLong() * 4) cap = cap shl 1
            val table = IntArray(cap)
            val mask = cap - 1
            var size = 0
            for (k in 0 until count) {
                val id = ids[k]
                if (id <= 0) continue
                var i = mix(id) and mask
                while (table[i] != 0 && table[i] != id) i = (i + 1) and mask
                if (table[i] == 0) {
                    table[i] = id
                    size++
                }
            }
            return IntIdSet(table, size)
        }

        fun of(ids: Collection<Long>): IntIdSet =
            of(ids.filter { it in 1..Int.MAX_VALUE.toLong() }.map { it.toInt() }.toIntArray())
    }
}

internal class AiBlacklist(
    val version: Int,
    private val artists: IntIdSet,
    private val tracks: IntIdSet,
    private val suspectArtists: IntIdSet = IntIdSet.EMPTY,
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

    fun matchTrack(trackId: String?, trackArtists: List<FullArtistInfo>): AiMatch? = when {
        containsTrack(trackId) -> AiMatch.TRACK
        trackArtists.any { containsArtist(it.id) } -> AiMatch.ARTIST
        trackArtists.any { isSuspectArtist(it.id) } -> AiMatch.SUSPECT_ARTIST
        else -> null
    }

    fun shouldSkipInWave(track: FullTrackInfo, likedArtistIds: Set<Long>?, isTrackLiked: () -> Boolean): Boolean =
        likedArtistIds != null &&
            containsTrack(track.id) &&
            track.artists.none { it.id in likedArtistIds } &&
            !isTrackLiked()

    companion object {
        fun of(version: Int, artists: Set<Long>, tracks: Set<Long>, suspectArtists: Set<Long> = emptySet()) =
            AiBlacklist(version, IntIdSet.of(artists), IntIdSet.of(tracks), IntIdSet.of(suspectArtists))

        /**
         * Parses the bundle produced by yam-ai-blacklist `export-client` (schema 3):
         * {"version":36,"schema":3,"encoding":"delta","artists":[...],"tracks":[...],"suspectArtists":[...]}
         * Each list is sorted and delta-encoded: the first number is an ID, every next one is the
         * difference to the previous ID. Parsed in one pass straight into IntArrays, without a JSON
         * tree or boxed numbers. Plain (non-delta) lists are accepted as well.
         */
        fun parse(bytes: ByteArray): AiBlacklist {
            val delta = indexOf(bytes, "\"encoding\":\"delta\"") >= 0
            return AiBlacklist(
                version = readNumber(bytes, "version") ?: 0,
                artists = IntIdSet.of(readIdArray(bytes, "artists", delta)),
                tracks = IntIdSet.of(readIdArray(bytes, "tracks", delta)),
                suspectArtists = IntIdSet.of(readIdArray(bytes, "suspectArtists", delta)),
            )
        }

        private fun indexOf(bytes: ByteArray, text: String): Int {
            val pattern = text.encodeToByteArray()
            outer@ for (i in 0..bytes.size - pattern.size) {
                for (j in pattern.indices) if (bytes[i + j] != pattern[j]) continue@outer
                return i
            }
            return -1
        }

        private fun readNumber(bytes: ByteArray, key: String): Int? {
            var i = indexOf(bytes, "\"$key\":")
            if (i < 0) return null
            i += key.length + 3
            var value = 0
            while (i < bytes.size && bytes[i] in DIGIT_0..DIGIT_9) value = value * 10 + (bytes[i++] - DIGIT_0)
            return value
        }

        private fun readIdArray(bytes: ByteArray, key: String, delta: Boolean): IntArray {
            var start = indexOf(bytes, "\"$key\":[")
            if (start < 0) return IntArray(0)
            start += key.length + 4
            // First pass: count numbers to allocate the exact array size
            var count = 0
            var inNumber = false
            var i = start
            while (i < bytes.size && bytes[i] != CLOSE) {
                val digit = bytes[i] in DIGIT_0..DIGIT_9
                if (digit && !inNumber) count++
                inNumber = digit
                i++
            }
            // Second pass: parse (and accumulate deltas) into the array
            val out = IntArray(count)
            var n = 0
            var acc = 0L
            var cur = 0L
            inNumber = false
            i = start
            while (n < count && i <= bytes.size) {
                val b = if (i < bytes.size) bytes[i] else CLOSE
                if (b in DIGIT_0..DIGIT_9) {
                    cur = cur * 10 + (b - DIGIT_0)
                    inNumber = true
                } else if (inNumber) {
                    acc = if (delta) acc + cur else cur
                    out[n++] = acc.toInt()
                    cur = 0L
                    inNumber = false
                }
                i++
            }
            return out
        }

        private const val DIGIT_0: Byte = 48 // '0'
        private const val DIGIT_9: Byte = 57 // '9'
        private const val CLOSE: Byte = 93 // ']'
    }
}

internal suspend fun loadBundledAiBlacklist(): AiBlacklist = withContext(DispatcherIO) {
    AiBlacklist.parse(Res.readBytes("files/ai_blacklist.json"))
}
