package net.montycasa.roux.data

// When :app tests need this corpus, move it to a shared test-fixtures module or Gradle test-fixtures artifact.

/** Real NHL API responses captured by scripts/capture-fixtures.sh. */
object Fixtures {
    const val CURRENT = "2026-10-26"

    fun bytes(name: String): ByteArray {
        val path = "/fx/$CURRENT/$name"
        val stream = Fixtures::class.java.getResourceAsStream(path) ?: error("Fixture resource not found: $path")
        return stream.use { it.readBytes() }
    }

    fun text(name: String): String = bytes(name).toString(Charsets.UTF_8)
}
