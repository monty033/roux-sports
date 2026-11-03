package net.montycasa.roux.data.cache

import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

enum class CacheKind(
    val cap: Int,
    val prefix: String,
) {
    Score(30, "score"),
    Standings(3, "standings"),
    Seasons(1, "seasons"),

    // One entry per club and season. The payload covers a whole season in one response, so a club
    // fan is unlikely to move between many of them; 32 covers every club's current season without
    // letting an old season crowd out the one being read.
    ClubSchedule(32, "club-schedule"),

    // One current roster per club. A roster is small and addressed by club alone, so 32 entries
    // covers every club without letting an old capture displace the one being read.
    ClubRoster(32, "club-roster"),

    // One club-stats entry per club and season, so the Stats tab has its own cache rather than
    // sharing (or evicting) the roster or schedule entry.
    ClubStats(32, "club-stats"),

    // One entry per player. A club's roster has at most a few dozen players, and a fan who opens several profiles in a
    // session should not evict another club's stats, so the player kind gets its own cap.
    PlayerProfile(32, "player-profile"),

    // One kind per detail endpoint, so a burst of landing pages for a busy night cannot evict the boxscore a
    // fan is reading. The caps are per kind; a shared Detail kind would have made the busiest endpoint the
    // one that decides what survives.
    DetailLanding(20, "detail-landing"),
    DetailBoxscore(20, "detail-boxscore"),
    DetailRightRail(20, "detail-right-rail"),
}

data class CacheKey(
    val kind: CacheKind,
    val id: String,
)

// Not a data class: ByteArray equality is identity rather than byte content equality.
class CachedResponse(
    val bytes: ByteArray,
    val fetchedAt: Instant,
    /**
     * A caller-supplied label for the state the body was fetched under, or null when none was
     * recorded. The cache treats it as opaque; a caller that needs a state boundary compares it.
     */
    val state: String? = null,
)

/**
 * A bounded disk cache of raw responses with atomic replacement of complete envelopes.
 *
 * Entries hold the server's bytes unchanged, so an adapter change never invalidates the cache.
 *
 * Eviction is least-recently-USED. Okio's FileSystem exposes no way to set modification times, so
 * recency cannot live in an entry's own mtime; each entry instead has a tiny sibling record holding
 * the clock reading of its last read or write. A read therefore costs one small write, which is
 * cheaper than rewriting the response body and is what stops a frequently viewed, never refreshed
 * entry - a final game detail - from being evicted ahead of one nobody opens.
 */
class ResponseCache(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val directory: Path,
    private val clock: Clock = Clock.System,
) {
    suspend fun get(key: CacheKey): CachedResponse? {
        val target = pathFor(key)

        val raw =
            try {
                fileSystem.read(target) { readByteArray() }
            } catch (_: IOException) {
                // NOT corruption: the entry could not be read at all - transient I/O, permissions,
                // an unavailable volume. Deleting here would turn a recoverable read failure into
                // permanent loss of an entry that is very likely still intact.
                return null
            }

        val response = decode(raw)
        if (response == null) {
            // A positive corruption verdict: the bytes were read and are not a valid envelope.
            // Remove them so the same damage cannot poison every later read.
            deleteQuietly(target)
            deleteQuietly(accessRecordFor(target))
        } else {
            touch(target)
        }
        return response
    }

    suspend fun put(
        key: CacheKey,
        bytes: ByteArray,
        fetchedAt: Instant,
        state: String? = null,
    ) {
        val target = pathFor(key)
        fileSystem.createDirectories(directory)
        // A sibling is essential: atomicMove cannot guarantee atomicity across filesystems.
        val temp = directory / "${target.name}.${clock.now().toEpochMilliseconds()}.${Random.nextLong()}.tmp"
        try {
            val stateBytes = state?.encodeUtf8()
            fileSystem.write(temp, mustCreate = true) {
                write(MAGIC.toByteArray())
                writeUtf8(fetchedAt.toEpochMilliseconds().toString())
                writeByte('\n'.code)
                // A length prefix rather than a newline-delimited field: the state is opaque to
                // the cache and must not be able to make the envelope itself unparseable.
                writeUtf8((stateBytes?.size ?: 0).toString())
                writeByte('\n'.code)
                if (stateBytes != null) write(stateBytes)
                // Preserve the raw body without decoding or re-encoding: the whole point is
                // that an adapter change never invalidates the cache.
                write(bytes)
            }
            fileSystem.atomicMove(temp, target)
        } finally {
            deleteQuietly(temp)
        }
        touch(target)
        evict(key.kind)
    }

    suspend fun clear() {
        for (path in fileSystem.listOrNull(directory).orEmpty()) {
            fileSystem.delete(path)
        }
    }

    private fun pathFor(key: CacheKey): Path {
        require(ID.matches(key.id)) { "Invalid cache id: '${key.id}'" }
        return directory / "${key.kind.prefix}-${key.id}$ENTRY_SUFFIX"
    }

    private fun accessRecordFor(target: Path): Path = target.parent!! / "${target.name}.access"

    /**
     * Parses a complete envelope, returning null for anything malformed.
     *
     * The body is opaque: it may contain newlines, digits, even the magic string. Only the fixed
     * magic, the newline-terminated timestamp, the newline-terminated state length, and the bytes
     * that follow are interpreted.
     */
    private fun decode(raw: ByteArray): CachedResponse? {
        val magic = MAGIC.toByteArray()
        if (raw.size < magic.size) return null
        for (index in magic.indices) {
            if (raw[index] != magic[index]) return null
        }

        // A Long timestamp needs at most 20 ASCII characters including a sign; the newline must
        // fall inside that window or this is not an envelope this cache wrote.
        val magicEnd = magic.size
        val newlineLimit = minOf(magicEnd + TIMESTAMP_MAX_CHARS + 1, raw.size)
        var newline = magicEnd
        while (newline < newlineLimit && raw[newline] != NEWLINE) newline++
        if (newline >= newlineLimit) return null

        val timestamp = raw.decodeToString(magicEnd, newline)
        if (!TIMESTAMP.matches(timestamp)) return null
        val epochMillis = timestamp.toLongOrNull() ?: return null

        val stateStart = newline + 1
        val stateLengthLimit = minOf(stateStart + STATE_LENGTH_MAX_CHARS + 1, raw.size)
        var stateLengthEnd = stateStart
        while (stateLengthEnd < stateLengthLimit && raw[stateLengthEnd] != NEWLINE) stateLengthEnd++
        if (stateLengthEnd >= stateLengthLimit) return null

        val stateLengthText = raw.decodeToString(stateStart, stateLengthEnd)
        if (!STATE_LENGTH.matches(stateLengthText)) return null
        val stateLength = stateLengthText.toIntOrNull() ?: return null
        val stateStartOffset = stateLengthEnd + 1
        val stateEnd = stateStartOffset + stateLength
        if (stateEnd > raw.size) return null

        return CachedResponse(
            bytes = raw.copyOfRange(stateEnd, raw.size),
            fetchedAt = Instant.fromEpochMilliseconds(epochMillis),
            state = raw.decodeToString(stateStartOffset, stateEnd).ifEmpty { null },
        )
    }

    /**
     * Records "this entry was used now". Best effort: recency must never fail a cache hit, and a
     * failed write must not damage the evidence already there.
     *
     * The stamp is written to a sibling and moved into place, never truncated in place, so a write
     * that fails partway leaves the previous stamp intact. A stamp is also only ever moved forward:
     * a clock that steps backwards must not replace newer evidence with older.
     */
    private fun touch(target: Path) {
        val record = accessRecordFor(target)
        val stamp = clock.now().toEpochMilliseconds()
        try {
            fileSystem.createDirectories(directory)
            val existing = readStamp(record)
            if (existing != null && existing >= stamp) return
            val temp = directory / "${record.name}.$stamp.${Random.nextLong()}.tmp"
            try {
                fileSystem.write(temp, mustCreate = true) { writeUtf8(stamp.toString()) }
                fileSystem.atomicMove(temp, record)
            } finally {
                deleteQuietly(temp)
            }
        } catch (_: IOException) {
            // The previous stamp stands; it was only ever replaced by a move, not by a truncation.
        }
    }

    private fun readStamp(path: Path): Long? =
        try {
            fileSystem.read(path) { readUtf8().toLongOrNull() }
        } catch (_: IOException) {
            null
        }

    /**
     * The later of the access record and the entry's own mtime.
     *
     * Neither timestamp alone is trustworthy: the record can predate a replacement, and the mtime
     * cannot capture a hit because Okio cannot set it. Taking the newer is correct in both
     * directions - an entry is never evicted while a newer piece of evidence says it was used.
     */
    private fun lastUsedAt(target: Path): Long {
        val recorded = readStamp(accessRecordFor(target))
        val modified = fileSystem.metadataOrNull(target)?.lastModifiedAtMillis
        return when {
            recorded != null && modified != null -> maxOf(recorded, modified)
            recorded != null -> recorded
            modified != null -> modified
            else -> Long.MIN_VALUE
        }
    }

    private fun evict(kind: CacheKind) {
        val entries =
            fileSystem
                .list(directory)
                .filter { it.name.startsWith("${kind.prefix}-") && it.name.endsWith(ENTRY_SUFFIX) }
                .map { path -> path to lastUsedAt(path) }
                .sortedWith(compareBy({ it.second }, { it.first.name }))

        for ((path, _) in entries.take((entries.size - kind.cap).coerceAtLeast(0))) {
            fileSystem.delete(path)
            deleteQuietly(accessRecordFor(path))
        }
    }

    private fun deleteQuietly(path: Path) {
        try {
            fileSystem.delete(path)
        } catch (_: IOException) {
            // Cleanup must not turn a miss into a fetch failure or mask the original write error.
        }
    }

    private companion object {
        const val ENTRY_SUFFIX = ".roux"
        const val TIMESTAMP_MAX_CHARS = 20
        const val STATE_LENGTH_MAX_CHARS = 10
        const val NEWLINE = '\n'.code.toByte()
        val MAGIC = "ROUXCACHE2\n".encodeUtf8()
        val ID = Regex("[A-Za-z0-9._-]{1,64}")
        val TIMESTAMP = Regex("-?[0-9]+")
        val STATE_LENGTH = Regex("[0-9]{1,$STATE_LENGTH_MAX_CHARS}")
    }
}
