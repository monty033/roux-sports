package net.montycasa.roux.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The retired wording stays out of the app. These scan the real sources and resources, so a screen, string or helper
 * that brings a retired word back fails here rather than on a device.
 *
 * The working directory is the app module, so the other modules are reached by their relative paths.
 */
class PlayerWordingTest {
    @Test
    fun noAppSourceOrStringSaysCatchesOrVitals() {
        // Resource text reaches the screen in any case, so it is matched case-insensitively. Kotlin is matched
        // case-sensitively so that a lowercase word in a code comment does not count as wording.
        val hits =
            sourceFiles("src/main")
                .flatMap { file ->
                    hitsIn(file, if (file.extension == "xml") CATCHES_IN_TEXT else CATCHES) + hitsIn(file, VITALS)
                }

        assertEquals("the app still says Catches or Vitals: $hits", emptyList<String>(), hits)
    }

    @Test
    fun theRetiredLastFiveCardLeavesNoTraceInAnyModule() {
        val hits =
            listOf("src/main", "../domain/src/main", "../data/src/main")
                .flatMap { sourceFiles(it) }
                .flatMap { file -> hitsIn(file, LAST_FIVE) }

        assertEquals("the retired last-five card is still referenced: $hits", emptyList<String>(), hits)
    }

    private fun sourceFiles(root: String): List<File> =
        File(root)
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .toList()

    private fun hitsIn(
        file: File,
        pattern: Regex,
    ): List<String> =
        file.readLines().mapIndexedNotNull { index, line ->
            if (pattern.containsMatchIn(line)) "${file.path}:${index + 1}" else null
        }

    private companion object {
        val CATCHES = Regex("Catches")
        val CATCHES_IN_TEXT = Regex("catches", RegexOption.IGNORE_CASE)
        val VITALS = Regex("vitals", RegexOption.IGNORE_CASE)
        val LAST_FIVE = Regex("last ?5|lastfive|last[ _]five", RegexOption.IGNORE_CASE)
    }
}
