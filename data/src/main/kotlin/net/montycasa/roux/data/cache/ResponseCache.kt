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
    Detail(20, "detail"),
}

data class CacheKey(
    val kind: CacheKind,
    val id: String,
)

// Not a data class: ByteArray equality is identity rather than byte content equality.
class CachedResponse(
    val bytes: ByteArray,
    val fetchedAt: Instant,
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
    ) {
        val target = pathFor(key)
        fileSystem.createDirectories(directory)
        // A sibling is essential: atomicMove cannot guarantee atomicity across filesystems.
        val temp = directory / "${target.name}.${clock.now().toEpochMilliseconds()}.${Random.nextLong()}.tmp"
        try {
            fileSystem.write(temp, mustCreate = true) {
                write(MAGIC.toByteArray())
                writeUtf8(fetchedAt.toEpochMilliseconds().toString())
                writeByte('\n'.code)
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
     * magic, the newline-terminated timestamp, and whatever follows that newline are interpreted.
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

        return CachedResponse(
            bytes = raw.copyOfRange(newline + 1, raw.size),
            fetchedAt = Instant.fromEpochMilliseconds(epochMillis),
        )
    }

    /** Records "this entry was used now". Best effort: recency must never fail a cache hit. */
    private fun touch(target: Path) {
        try {
            fileSystem.createDirectories(directory)
            fileSystem.write(accessRecordFor(target)) {
                writeUtf8(clock.now().toEpochMilliseconds().toString())
            }
        } catch (_: IOException) {
            // A stale record is worse than none: lastUsedAt prefers the record over the entry's
            // fresh mtime, so after a replacement whose touch failed, the entry just written would
            // carry its predecessor's old timestamp and be evicted ahead of older entries. Drop the
            // record so eviction falls back to the mtime of the bytes that are actually on disk.
            deleteQuietly(accessRecordFor(target))
        }
    }

    private fun lastUsedAt(target: Path): Long {
        val recorded =
            try {
                fileSystem.read(accessRecordFor(target)) { readUtf8().toLongOrNull() }
            } catch (_: IOException) {
                null
            }
        return recorded
            ?: fileSystem.metadataOrNull(target)?.lastModifiedAtMillis
            ?: Long.MIN_VALUE
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
        const val NEWLINE = '\n'.code.toByte()
        val MAGIC = "ROUXCACHE1\n".encodeUtf8()
        val ID = Regex("[A-Za-z0-9._-]{1,64}")
        val TIMESTAMP = Regex("-?[0-9]+")
    }
}
