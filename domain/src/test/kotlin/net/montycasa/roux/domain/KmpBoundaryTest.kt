package net.montycasa.roux.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

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

    @Test
    fun scanSourceDatastoreCoreDataNearMissHasSizeOne() {
        assertEquals(1, scanSource("import androidx.datastore.coreData.DataStore").size)
    }

    @Test
    fun scanSourceDatastoreCorelessNearMissHasSizeOne() {
        assertEquals(1, scanSource("import androidx.datastore.coreless.Foo").size)
    }

    @Test
    fun scanSourceAnnotationaryNearMissHasSizeOne() {
        assertEquals(1, scanSource("import androidx.annotationary.Keep").size)
    }

    @Test
    fun scanSourceWildcardAndroidImportHasSizeOne() {
        assertEquals(1, scanSource("import android.*").size)
    }

    @Test
    fun scanSourceWildcardJavaImportHasSizeOne() {
        assertEquals(1, scanSource("import java.*").size)
    }

    @Test
    fun scanSourceWildcardJavaxImportHasSizeOne() {
        assertEquals(1, scanSource("import javax.*").size)
    }

    @Test
    fun scanSourceMultipleImportsOnOneLineHasSizeOne() {
        val source = "import androidx.datastore.core.DataStore; import java.time.Instant"
        assertEquals(1, scanSource(source).size)
    }

    @Test
    fun scanSourceLineCommentedImportHasSizeZero() {
        assertEquals(0, scanSource("// import android.os.Bundle").size)
    }

    @Test
    fun scanSourceBlockCommentedImportHasSizeZero() {
        assertEquals(0, scanSource("/*\nimport android.os.Bundle\n*/").size)
    }

    @Test
    fun scanSourceImportInsideTripleQuotedStringHasSizeZero() {
        val source = "val s = \"\"\"import androidx.annotation.Keep\"\"\""
        assertEquals(0, scanSource(source).size)
    }

    @Test
    fun scanSourceForbiddenImportInsideTripleQuotedStringHasSizeZero() {
        val source = "val s = \"\"\"import android.os.Bundle\"\"\""
        assertEquals(0, scanSource(source).size)
    }

    @Test
    fun scanSourceImportWithTrailingCommentHasSizeOne() {
        assertEquals(1, scanSource("import android.os.Bundle // Android-only").size)
    }

    @Test
    fun scanSourceStringLiteralWithEscapedQuotesHasSizeZero() {
        val source = "val s = \"import android.os.Bundle \\\"quoted\\\"\""
        assertEquals(0, scanSource(source).size)
    }

    @Test
    fun scanTreeFindsForbiddenImportInNestedKotlinFile() {
        val root = Files.createTempDirectory("kmp-boundary-scan").toFile()
        try {
            val nestedDir = File(root, "src/main/kotlin/deep").apply { mkdirs() }
            val nestedFile = File(nestedDir, "Nested.kt")
            nestedFile.writeText("package nested\n\nimport android.os.Bundle\n")
            val violations = scanTree(root)
            assertEquals(1, violations.size)
            assertTrue(violations.single().contains(nestedFile.path))
            assertTrue(violations.single().contains("forbidden import: android.os.Bundle"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun scanTreeReportsUnreadableKotlinFile() {
        val root = Files.createTempDirectory("kmp-boundary-unreadable").toFile()
        val unreadable = File(root, "Unreadable.kt")
        try {
            unreadable.writeText("package unreadable\n")
            assertTrue(unreadable.setReadable(false, false))
            assumeFalse("cannot simulate an unreadable file while running as root", unreadable.canRead())
            val violations = scanTree(root)
            assertEquals(1, violations.size)
            assertTrue(violations.single().contains(unreadable.path))
            assertTrue(violations.single().contains("unable to read file"))
        } finally {
            unreadable.setReadable(true, false)
            root.deleteRecursively()
        }
    }
}

private val allowedAndroidxPaths =
    listOf(
        "androidx.datastore.core",
        "androidx.datastore.preferences.core",
        "androidx.annotation",
    )

private val importPattern = Regex("""\bimport\s+([^\s;]+)""")

private val androidReferencePattern = Regex("""\bandroid\.[A-Za-z_][A-Za-z0-9_.]*""")

fun scanSource(text: String): List<String> {
    val cleaned = stripCommentsAndStrings(text)
    val violations = mutableListOf<String>()
    val withoutImports = StringBuilder(cleaned)
    for (match in importPattern.findAll(cleaned)) {
        val importPath = match.groupValues[1]
        if (isForbiddenImport(importPath)) {
            val line = lineNumberAt(cleaned, match.range.first)
            violations.add("line $line: forbidden import: $importPath")
        }
        for (index in match.range) {
            if (withoutImports[index] != '\n') {
                withoutImports.setCharAt(index, ' ')
            }
        }
    }
    for (match in androidReferencePattern.findAll(withoutImports)) {
        val line = lineNumberAt(withoutImports, match.range.first)
        violations.add("line $line: forbidden android reference: ${match.value}")
    }
    return violations
}

fun scanTree(vararg dirs: File): List<String> {
    val violations = mutableListOf<String>()
    for (dir in dirs) {
        val isDirectory =
            try {
                dir.exists() && dir.isDirectory
            } catch (exception: Exception) {
                violations.add("${dir.path}: unable to inspect path: ${reason(exception)}")
                false
            }
        if (!isDirectory) {
            continue
        }
        val files =
            dir.walkTopDown().onFail { file, exception ->
                violations.add("${file.path}: unable to list directory: ${reason(exception)}")
            }
        try {
            for (file in files) {
                if (!file.isFile || file.extension != "kt") {
                    continue
                }
                val source =
                    try {
                        file.readText()
                    } catch (exception: Exception) {
                        violations.add("${file.path}: unable to read file: ${reason(exception)}")
                        continue
                    }
                for (violation in scanSource(source)) {
                    violations.add("${file.path}: $violation")
                }
            }
        } catch (exception: Exception) {
            violations.add("${dir.path}: scan aborted: ${reason(exception)}")
        }
    }
    return violations
}

private fun isForbiddenImport(importPath: String): Boolean {
    val forbiddenAndroid = importPath == "android" || importPath.startsWith("android.")
    val forbiddenJava = importPath == "java" || importPath.startsWith("java.")
    val forbiddenJavax = importPath == "javax" || importPath.startsWith("javax.")
    val isAndroidx = importPath == "androidx" || importPath.startsWith("androidx.")
    val forbiddenAndroidx = isAndroidx && !isAllowedAndroidx(importPath)
    return forbiddenAndroid || forbiddenJava || forbiddenJavax || forbiddenAndroidx
}

private fun isAllowedAndroidx(importPath: String): Boolean =
    allowedAndroidxPaths.any { allowed ->
        importPath == allowed || importPath.startsWith("$allowed.")
    }

private fun lineNumberAt(
    text: CharSequence,
    offset: Int,
): Int {
    var line = 1
    for (index in 0 until offset) {
        if (text[index] == '\n') {
            line++
        }
    }
    return line
}

private fun reason(exception: Throwable): String = "${exception.javaClass.simpleName}: ${exception.message ?: "no message"}"

private fun stripCommentsAndStrings(text: String): String {
    val result = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("//", index) -> {
                while (index < text.length && text[index] != '\n') {
                    index++
                }
            }

            text.startsWith("/*", index) -> {
                var depth = 1
                index += 2
                while (index < text.length && depth > 0) {
                    when {
                        text.startsWith("/*", index) -> {
                            depth++
                            index += 2
                        }

                        text.startsWith("*/", index) -> {
                            depth--
                            index += 2
                        }

                        else -> {
                            if (text[index] == '\n') {
                                result.append('\n')
                            }
                            index++
                        }
                    }
                }
            }

            text.startsWith("\"\"\"", index) -> {
                index += 3
                while (index < text.length && !text.startsWith("\"\"\"", index)) {
                    if (text[index] == '\n') {
                        result.append('\n')
                    }
                    index++
                }
                if (index < text.length) {
                    index += 3
                }
            }

            text[index] == '"' -> {
                index++
                while (index < text.length) {
                    when {
                        text[index] == '\\' -> {
                            if (index + 1 < text.length && text[index + 1] == '\n') {
                                result.append('\n')
                            }
                            index += 2
                        }

                        text[index] == '"' -> {
                            index++
                            break
                        }

                        else -> {
                            if (text[index] == '\n') {
                                result.append('\n')
                            }
                            index++
                        }
                    }
                }
            }

            text[index] == '\'' -> {
                index++
                while (index < text.length) {
                    when {
                        text[index] == '\\' -> {
                            index += 2
                        }

                        text[index] == '\'' -> {
                            index++
                            break
                        }

                        else -> {
                            if (text[index] == '\n') {
                                result.append('\n')
                            }
                            index++
                        }
                    }
                }
            }

            else -> {
                result.append(text[index])
                index++
            }
        }
    }
    return result.toString()
}
