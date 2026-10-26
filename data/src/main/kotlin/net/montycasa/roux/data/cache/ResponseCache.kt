package net.montycasa.roux.data.cache

import okio.BufferedSource
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
 * Eviction is least-recently-WRITTEN, rather than least-recently-USED: reads do not update
 * modification times, and Okio's FileSystem has no utimes API to implement true LRU.
 * [clock] supplies the time component of sibling temporary file names; eviction uses filesystem
 * metadata, independently of the response's fetchedAt timestamp.
 */
class ResponseCache(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val directory: Path,
    private val clock: Clock = Clock.System,
) {
    suspend fun get(key: CacheKey): CachedResponse? {
        val target = pathFor(key)
        val response =
            try {
                fileSystem.read(target) { readResponse() }
            } catch (_: IOException) {
                null
            }
        if (response == null) {
            // Delete damaged entries so a corrupt envelope cannot poison later reads. A cache
            // is only an optimisation: even an unreadable or undeletable entry is just a miss.
            deleteQuietly(target)
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
                write(MAGIC)
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
        evict(key.kind)
    }

    suspend fun clear() {
        for (path in fileSystem.listOrNull(directory).orEmpty()) {
            fileSystem.delete(path)
        }
    }

    private fun pathFor(key: CacheKey): Path {
        require(ID.matches(key.id)) { "Invalid cache id: '${key.id}'" }
        return directory / "${key.kind.prefix}-${key.id}.roux"
    }

    private fun BufferedSource.readResponse(): CachedResponse? {
        if (readByteString(MAGIC.size.toLong()) != MAGIC) return null
        // A Long timestamp needs at most 20 ASCII bytes, including a possible minus sign.
        val newline = indexOf('\n'.code.toByte(), 0, 21)
        if (newline == -1L) return null
        val timestamp = readUtf8(newline)
        skip(1)
        if (!TIMESTAMP.matches(timestamp)) return null
        val epochMillis = timestamp.toLongOrNull() ?: return null
        val fetchedAt = Instant.fromEpochMilliseconds(epochMillis)
        return CachedResponse(readByteArray(), fetchedAt)
    }

    private fun evict(kind: CacheKind) {
        val entries =
            fileSystem
                .list(directory)
                .filter { it.name.startsWith("${kind.prefix}-") && it.name.endsWith(".roux") }
                .mapNotNull { path ->
                    fileSystem.metadataOrNull(path)?.takeIf { it.isRegularFile }?.let { metadata ->
                        path to (metadata.lastModifiedAtMillis ?: Long.MIN_VALUE)
                    }
                }.sortedWith(compareBy({ it.second }, { it.first.name }))
        for ((path, _) in entries.take((entries.size - kind.cap).coerceAtLeast(0))) {
            fileSystem.delete(path)
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
        val MAGIC = "ROUXCACHE1\n".encodeUtf8()
        val ID = Regex("[A-Za-z0-9._-]{1,64}")
        val TIMESTAMP = Regex("-?[0-9]+")
    }
}
