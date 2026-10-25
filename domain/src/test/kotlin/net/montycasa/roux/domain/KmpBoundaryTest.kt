package net.montycasa.roux.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KmpBoundaryTest {
    @Test
    fun scanSourceAndroidImportHasSizeOne() {
        assertEquals(1, scanSource("import android.os.Bundle").size)
    }

    @Test
    fun scanSourceFullyQualifiedAndroidReferenceHasSizeOne() {
        assertEquals(1, scanSource("val b = android.os.Bundle()").size)
    }

    @Test
    fun scanSourceJavaImportHasSizeOne() {
        assertEquals(1, scanSource("import java.time.Instant").size)
    }

    @Test
    fun scanSourceAllowedDatastoreImportHasSizeZero() {
        assertEquals(0, scanSource("import androidx.datastore.core.DataStore").size)
    }

    @Test
    fun scanTreeMissingOrEmptyDirsIsEmpty() {
        val root = File(System.getProperty("roux.rootDir"))
        assertTrue(
            scanTree(
                File(root, "domain/src/main"),
                File(root, "data/src/main"),
            ).isEmpty(),
        )
    }

    @Test
    fun scanSourceAllowedAnnotationImportHasSizeZero() {
        assertEquals(0, scanSource("import androidx.annotation.Keep").size)
    }

    @Test
    fun scanSourceNonAllowedAndroidxImportHasSizeOne() {
        assertEquals(1, scanSource("import androidx.compose.runtime.Composable").size)
    }

    @Test
    fun scanSourcePlainSourceHasSizeZero() {
        assertEquals(0, scanSource("val x = 1").size)
    }
}

private val allowedAndroidxPrefixes =
    listOf(
        "androidx.datastore.core",
        "androidx.datastore.preferences.core",
        "androidx.annotation",
    )

fun scanSource(text: String): List<String> {
    val violations = mutableListOf<String>()
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("import ")) {
            val path =
                trimmed
                    .removePrefix("import ")
                    .trim()
                    .substringBefore(" as ")
                    .trim()
                    .removeSuffix(".*")
                    .trim()
            val isForbiddenAndroidx =
                path.startsWith("androidx.") &&
                    allowedAndroidxPrefixes.none { path.startsWith(it) }
            if (
                path.startsWith("android.") ||
                path.startsWith("java.") ||
                path.startsWith("javax.") ||
                isForbiddenAndroidx
            ) {
                violations.add("forbidden import: $path")
            }
        } else {
            val regex = Regex("""\bandroid\.[A-Za-z_][A-Za-z0-9_.]*""")
            for (match in regex.findAll(line)) {
                violations.add("forbidden android reference: ${match.value}")
            }
        }
    }
    return violations
}

fun scanTree(vararg dirs: File): List<String> {
    val violations = mutableListOf<String>()
    for (dir in dirs) {
        if (!dir.exists() || !dir.isDirectory) {
            continue
        }
        for (file in dir.walkTopDown()) {
            if (!file.isFile || file.extension != "kt") {
                continue
            }
            for (violation in scanSource(file.readText())) {
                violations.add("${file.path}: $violation")
            }
        }
    }
    return violations
}
