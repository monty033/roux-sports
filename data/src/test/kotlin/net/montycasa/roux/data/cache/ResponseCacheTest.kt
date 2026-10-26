package net.montycasa.roux.data.cache

import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.ForwardingSink
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ResponseCacheTest {
    @Test
    fun roundTripPreservesRawBytesAndFetchedAt() =
        withCache { fs, cache, _ ->
            cache.put(KEY, RAW, FETCHED_AT)

            val response = cache.get(KEY)!!
            assertArrayEquals(RAW, response.bytes)
            assertEquals(FETCHED_AT, response.fetchedAt)
            val envelope = fs.read(entry(KEY)) { readByteArray() }
            assertArrayEquals("ROUXCACHE1\n${FETCHED_AT.toEpochMilliseconds()}\n".encodeToByteArray() + RAW, envelope)
        }

    @Test
    fun thirtyFirstScoreEvictsOldestAndKeepsNewest() =
        withCache { fs, cache, clock ->
            repeat(31) { index ->
                cache.put(CacheKey(CacheKind.Score, "$index"), RAW, FETCHED_AT)
                assertEquals(
                    clock.now().toEpochMilliseconds(),
                    fs.metadata(entry(CacheKey(CacheKind.Score, "$index"))).lastModifiedAtMillis,
                )
                clock.advance()
            }

            assertNull(cache.get(CacheKey(CacheKind.Score, "0")))
            assertArrayEquals(RAW, cache.get(CacheKey(CacheKind.Score, "30"))!!.bytes)
            assertEquals(30, entriesIn(fs).size)
        }

    @Test
    fun standingsKeepsLatestThree() = assertCap(CacheKind.Standings, 3)

    @Test
    fun detailKeepsLatestTwenty() = assertCap(CacheKind.Detail, 20)

    @Test
    fun seasonsKeepsLatestOne() = assertCap(CacheKind.Seasons, 1)

    @Test
    fun scoreEvictionDoesNotEvictStandings() =
        withCache { fs, cache, clock ->
            val standings = CacheKey(CacheKind.Standings, "older")
            cache.put(standings, RAW, FETCHED_AT)
            repeat(31) { index ->
                clock.advance()
                cache.put(CacheKey(CacheKind.Score, "$index"), RAW, FETCHED_AT)
            }

            assertArrayEquals(RAW, cache.get(standings)!!.bytes)
            assertEquals(31, entriesIn(fs).size)
        }

    @Test
    fun equalWriteTimesEvictByFileName() =
        withCache { _, cache, _ ->
            listOf("c", "a", "b", "d").forEach {
                cache.put(CacheKey(CacheKind.Standings, it), RAW, FETCHED_AT)
            }

            assertNull(cache.get(CacheKey(CacheKind.Standings, "a")))
            listOf("b", "c", "d").forEach { assertNotNull(cache.get(CacheKey(CacheKind.Standings, it))) }
        }

    /**
     * Write order deliberately opposes file-name order. Without this, the other eviction tests
     * pass under a name-only sort: their ids ascend in the same order they are written, so an
     * implementation that ignores modification times entirely still evicts the "right" entry.
     * This is the only test that distinguishes write-time recency from alphabet.
     */
    @Test
    fun evictionFollowsWriteOrderNotFileName() =
        withCache { _, cache, clock ->
            for (id in listOf("c", "b", "a")) {
                cache.put(CacheKey(CacheKind.Standings, id), RAW, FETCHED_AT)
                clock.advance()
            }
            cache.put(CacheKey(CacheKind.Standings, "d"), RAW, FETCHED_AT)

            assertNull(
                "the least recently written entry must go, not the first by name",
                cache.get(CacheKey(CacheKind.Standings, "c")),
            )
            listOf("a", "b", "d").forEach { assertNotNull(cache.get(CacheKey(CacheKind.Standings, it))) }
        }

    /**
     * Reading keeps an entry alive. This is what separates least-recently-USED from
     * least-recently-written: "a" is the oldest write but the newest read, so "b" must go instead.
     */
    @Test
    fun readingRefreshesRecency() =
        withCache { _, cache, clock ->
            for (id in listOf("a", "b", "c")) {
                cache.put(CacheKey(CacheKind.Standings, id), RAW, FETCHED_AT)
                clock.advance()
            }
            assertNotNull(cache.get(CacheKey(CacheKind.Standings, "a")))
            clock.advance()

            cache.put(CacheKey(CacheKind.Standings, "d"), RAW, FETCHED_AT)

            assertNull("the least recently USED entry goes", cache.get(CacheKey(CacheKind.Standings, "b")))
            listOf("a", "c", "d").forEach { assertNotNull(cache.get(CacheKey(CacheKind.Standings, it))) }
        }

    /**
     * Replacing the oldest entry protects it, which the previous version of this test could not
     * show: it replaced an entry that was not the eviction candidate, so it passed even if a
     * replacement kept the old recency.
     */
    @Test
    fun replacingTheOldestEntryProtectsIt() =
        withCache { _, cache, clock ->
            for (id in listOf("a", "b", "c")) {
                cache.put(CacheKey(CacheKind.Standings, id), RAW, FETCHED_AT)
                clock.advance()
            }

            cache.put(CacheKey(CacheKind.Standings, "a"), RAW, FETCHED_AT)
            clock.advance()
            cache.put(CacheKey(CacheKind.Standings, "d"), RAW, FETCHED_AT)

            assertNull("b is now the oldest", cache.get(CacheKey(CacheKind.Standings, "b")))
            listOf("a", "c", "d").forEach { assertNotNull(cache.get(CacheKey(CacheKind.Standings, it))) }
        }

    /**
     * A failed touch must not leave the previous timestamp behind. lastUsedAt prefers the access
     * record over the entry's mtime, so a stale record after a successful replacement would make
     * the freshly written entry look older than entries nobody has touched.
     */
    @Test
    fun failedTouchAfterReplacementDoesNotLeaveAStaleRecord() =
        withCache { fs, cache, clock ->
            var failAccessWrites = false
            val guarded = ResponseCache(flakyAccessWrites(fs) { failAccessWrites }, DIRECTORY, clock)

            guarded.put(CacheKey(CacheKind.Standings, "a"), RAW, FETCHED_AT)
            clock.advance()
            guarded.put(CacheKey(CacheKind.Standings, "b"), RAW, FETCHED_AT)
            clock.advance()

            // Replace "a": its entry is rewritten, but the recency record cannot be updated.
            failAccessWrites = true
            guarded.put(CacheKey(CacheKind.Standings, "a"), RAW, FETCHED_AT)
            failAccessWrites = false
            clock.advance()

            guarded.put(CacheKey(CacheKind.Standings, "c"), RAW, FETCHED_AT)
            clock.advance()
            guarded.put(CacheKey(CacheKind.Standings, "d"), RAW, FETCHED_AT)

            assertNotNull("the replaced entry is the newest write, not the oldest", guarded.get(CacheKey(CacheKind.Standings, "a")))
            assertNull("b is the least recently used", guarded.get(CacheKey(CacheKind.Standings, "b")))
        }

    /**
     * The other direction of the same reconciliation: a read whose stamp cannot be written is still
     * a read, so an earlier record must survive rather than be discarded in favour of a stale mtime.
     */
    @Test
    fun failedTouchOnReadKeepsTheRecencyTheEntryAlreadyHad() =
        withCache { fs, cache, clock ->
            var failAccessWrites = false
            val guarded = ResponseCache(flakyAccessWrites(fs) { failAccessWrites }, DIRECTORY, clock)

            guarded.put(CacheKey(CacheKind.Standings, "a"), RAW, FETCHED_AT)
            clock.advance()
            guarded.put(CacheKey(CacheKind.Standings, "b"), RAW, FETCHED_AT)
            clock.advance()
            guarded.put(CacheKey(CacheKind.Standings, "c"), RAW, FETCHED_AT)
            clock.advance()

            // A read that lands: "a" is now the most recently used entry.
            guarded.get(CacheKey(CacheKind.Standings, "a"))
            clock.advance()

            // A second read that cannot be stamped. Dropping the record here would push "a" back to
            // its write mtime and make it the eviction candidate despite two reads.
            failAccessWrites = true
            assertNotNull(guarded.get(CacheKey(CacheKind.Standings, "a")))
            failAccessWrites = false
            clock.advance()

            guarded.put(CacheKey(CacheKind.Standings, "d"), RAW, FETCHED_AT)

            assertNotNull("read twice, so not the oldest", guarded.get(CacheKey(CacheKind.Standings, "a")))
            assertNull("b was only written once", guarded.get(CacheKey(CacheKind.Standings, "b")))
        }

    /**
     * A read that fails at the filesystem level is not evidence of corruption. Deleting on an
     * unreadable-but-intact entry turns a transient I/O error into permanent cache loss, and if
     * the network is also down the user loses stale data that was still perfectly good.
     */
    @Test
    fun unreadableEntryIsKeptRatherThanDeleted() =
        withCache { fs, cache, clock ->
            cache.put(KEY, RAW, FETCHED_AT)

            val unreadable =
                object : ForwardingFileSystem(fs) {
                    override fun source(file: Path): okio.Source = throw IOException("volume temporarily unavailable")
                }
            val failing = ResponseCache(unreadable, DIRECTORY, clock)

            assertNull("an unreadable entry is a miss", failing.get(KEY))
            assertTrue("but it must not be deleted", fs.exists(entry(KEY)))
            assertArrayEquals("and it is still readable later", RAW, cache.get(KEY)!!.bytes)
        }

    @Test
    fun absentFileIsAMiss() =
        withCache { fs, cache, _ ->
            assertNull(cache.get(KEY))
            assertFalse(fs.exists(entry(KEY)))
            assertFalse(fs.exists(DIRECTORY))
        }

    @Test
    fun emptyFileIsAMissAndDeleted() = assertCorruption(byteArrayOf())

    @Test
    fun wrongMagicIsAMissAndDeleted() = assertCorruption("ROUXCACHE2\n123\nbody".encodeToByteArray())

    @Test
    fun truncatedHeaderIsAMissAndDeleted() = assertCorruption("ROUXCACHE1\n123".encodeToByteArray())

    @Test
    fun nonNumericTimestampIsAMissAndDeleted() = assertCorruption("ROUXCACHE1\nnot-a-number\nbody".encodeToByteArray())

    @Test
    fun overflowingTimestampIsAMissAndDeleted() = assertCorruption("ROUXCACHE1\n9223372036854775808\nbody".encodeToByteArray())

    @Test
    fun extremeEpochMillisRoundTrip() =
        withCache { _, cache, _ ->
            for (millis in listOf(Long.MIN_VALUE, Long.MAX_VALUE)) {
                val instant = Instant.fromEpochMilliseconds(millis)
                cache.put(KEY, RAW, instant)
                assertEquals(instant, cache.get(KEY)!!.fetchedAt)
            }
        }

    @Test
    fun carriageReturnInHeaderIsAMissAndDeleted() = assertCorruption("ROUXCACHE1\r\n123\nbody".encodeToByteArray())

    @Test
    fun emptyBodyIsValid() =
        withCache { _, cache, _ ->
            cache.put(KEY, byteArrayOf(), FETCHED_AT)
            assertArrayEquals(byteArrayOf(), cache.get(KEY)!!.bytes)
        }

    @Test
    fun failedAtomicMovePreservesPreviousEntryAndCleansTemp() =
        withCache { fs, cache, clock ->
            cache.put(KEY, RAW, FETCHED_AT)
            val failing =
                object : ForwardingFileSystem(fs) {
                    override fun atomicMove(
                        source: Path,
                        target: Path,
                    ) {
                        assertEquals(target.parent, source.parent)
                        assertArrayEquals(RAW, cacheBytes(fs, target))
                        throw IOException("injected move failure")
                    }
                }

            assertThrows(IOException::class.java) {
                runBlocking { ResponseCache(failing, DIRECTORY, clock).put(KEY, byteArrayOf(42), Instant.DISTANT_PAST) }
            }

            assertArrayEquals(RAW, cache.get(KEY)!!.bytes)
            assertEquals(FETCHED_AT, cache.get(KEY)!!.fetchedAt)
            assertEquals(listOf(entry(KEY)), entriesIn(fs))
        }

    @Test
    fun failedWritePreservesPreviousEntryAndCleansTemp() =
        withCache { fs, cache, clock ->
            cache.put(KEY, RAW, FETCHED_AT)
            val failing =
                object : ForwardingFileSystem(fs) {
                    override fun sink(
                        file: Path,
                        mustCreate: Boolean,
                    ): Sink =
                        object : ForwardingSink(super.sink(file, mustCreate)) {
                            override fun write(
                                source: Buffer,
                                byteCount: Long,
                            ) {
                                super.write(source, 1)
                                throw IOException("injected partial write failure")
                            }
                        }
                }

            assertThrows(IOException::class.java) {
                runBlocking { ResponseCache(failing, DIRECTORY, clock).put(KEY, byteArrayOf(42), FETCHED_AT) }
            }

            assertArrayEquals(RAW, cache.get(KEY)!!.bytes)
            assertEquals(FETCHED_AT, cache.get(KEY)!!.fetchedAt)
            assertEquals(listOf(entry(KEY)), entriesIn(fs))
        }

    @Test
    fun successfulPutLeavesNoTempFiles() =
        withCache { fs, cache, _ ->
            cache.put(KEY, RAW, FETCHED_AT)
            cache.put(KEY, byteArrayOf(42), FETCHED_AT)

            assertArrayEquals(byteArrayOf(42), cache.get(KEY)!!.bytes)
            assertEquals(listOf(entry(KEY)), entriesIn(fs))
        }

    @Test
    fun invalidIdsAreRejectedBeforeAnyFilesystemAccess() =
        withCache { fs, _, clock ->
            var accesses = 0
            val guarded =
                object : ForwardingFileSystem(fs) {
                    override fun onPathParameter(
                        path: Path,
                        functionName: String,
                        parameterName: String,
                    ): Path {
                        accesses++
                        return super.onPathParameter(path, functionName, parameterName)
                    }
                }
            val cache = ResponseCache(guarded, DIRECTORY, clock)
            val before = fs.allPaths
            for (id in listOf("../escape", "a/b", "a".repeat(65), "", "a\\b", "é")) {
                val key = CacheKey(CacheKind.Score, id)
                assertThrows(IllegalArgumentException::class.java) { runBlocking { cache.put(key, RAW, FETCHED_AT) } }
                assertThrows(IllegalArgumentException::class.java) { runBlocking { cache.get(key) } }
            }

            assertEquals(0, accesses)
            assertEquals(before, fs.allPaths)
            assertFalse(fs.exists("/escape".toPath()))
            assertFalse(fs.exists(DIRECTORY))
        }

    @Test
    fun allowedIdCharactersAndMaximumLengthRoundTrip() =
        withCache { _, cache, _ ->
            val key = CacheKey(CacheKind.Score, "Az09._-" + "x".repeat(57))
            cache.put(key, RAW, FETCHED_AT)
            assertArrayEquals(RAW, cache.get(key)!!.bytes)
        }

    @Test
    fun clearRemovesEveryKindAndCanBeRepeated() =
        withCache { fs, cache, _ ->
            cache.clear()
            CacheKind.entries.forEach { cache.put(CacheKey(it, "1"), RAW, FETCHED_AT) }
            cache.clear()
            cache.clear()

            assertTrue(fs.list(DIRECTORY).isEmpty())
            CacheKind.entries.forEach { assertNull(cache.get(CacheKey(it, "1"))) }
        }

    private fun assertCap(
        kind: CacheKind,
        cap: Int,
    ) = withCache { fs, cache, clock ->
        repeat(cap + 1) { index ->
            cache.put(CacheKey(kind, "$index"), RAW, FETCHED_AT)
            clock.advance()
        }
        assertNull(cache.get(CacheKey(kind, "0")))
        assertArrayEquals(RAW, cache.get(CacheKey(kind, "$cap"))!!.bytes)
        assertEquals(cap, entriesIn(fs).size)
    }

    private fun assertCorruption(envelope: ByteArray) =
        withCache { fs, cache, _ ->
            fs.createDirectories(DIRECTORY)
            fs.write(entry(KEY)) { write(envelope) }

            assertNull(cache.get(KEY))
            assertFalse("Damaged entry must be deleted", fs.exists(entry(KEY)))
            assertNull(cache.get(KEY))
        }

    private fun entriesIn(fakeFileSystem: FakeFileSystem): List<Path> = fakeFileSystem.list(DIRECTORY).filter { it.name.endsWith(".roux") }

    private fun withCache(block: suspend (FakeFileSystem, ResponseCache, MutableClock) -> Unit) =
        runBlocking {
            val clock = MutableClock()
            FakeFileSystem(clock).use { fs ->
                block(fs, ResponseCache(fs, DIRECTORY, clock), clock)
                fs.checkNoOpenFiles()
            }
        }

    private class MutableClock : Clock {
        private var instant = FETCHED_AT

        override fun now(): Instant = instant

        fun advance() {
            instant += 1.seconds
        }
    }

    private fun flakyAccessWrites(
        fs: FileSystem,
        shouldFail: () -> Boolean,
    ): FileSystem =
        object : ForwardingFileSystem(fs) {
            override fun sink(
                file: Path,
                mustCreate: Boolean,
            ): Sink {
                if (shouldFail() && file.name.endsWith(".access")) {
                    throw IOException("access record write failed")
                }
                return super.sink(file, mustCreate)
            }
        }

    private companion object {
        val DIRECTORY = "/cache".toPath()
        val KEY = CacheKey(CacheKind.Score, "2026-10-26")
        val FETCHED_AT = Instant.parse("2026-10-26T12:34:56.789Z")
        val RAW = byteArrayOf(0x00, 0x1B, 0xFF.toByte(), 0x0A, 0x0D, 0x80.toByte())

        fun entry(key: CacheKey): Path = DIRECTORY / "${key.kind.prefix}-${key.id}.roux"

        fun cacheBytes(
            fs: FileSystem,
            path: Path,
        ): ByteArray =
            fs.read(path) {
                readUtf8LineStrict()
                readUtf8LineStrict()
                readByteArray()
            }
    }
}
