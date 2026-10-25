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
    fun scanSourceAndroidReferenceInStringInterpolationHasSizeOne() {
        assertEquals(1, scanSource("val s = \"\${android.os.Build.MODEL}\"").size)
    }

    @Test
    fun scanSourceAndroidReferenceInInterpolationWithNestedBracesHasSizeOne() {
        val source = "val s = \"\${if (true) { android.os.Build.MODEL } else { \"\" }}\""
        assertEquals(1, scanSource(source).size)
    }

    @Test
    fun scanSourceAndroidReferenceAfterNestedStringInInterpolationHasSizeOne() {
        val source = "val s = \"\${foo(\"bar\") + android.os.Build.MODEL}\""
        assertEquals(1, scanSource(source).size)
    }

    @Test
    fun scanSourceAndroidReferenceInTripleQuotedInterpolationHasSizeOne() {
        val source = "val s = \"\"\"x\${android.os.Build.MODEL}\"\"\""
        assertEquals(1, scanSource(source).size)
    }

    @Test
    fun scanSourceEscapedDollarBeforeBraceIsNotInterpolationHasSizeZero() {
        // The `\$` escape makes `${...}` literal text inside the string.
        val source = "val s = \"\\\${android.os.Build.MODEL}\""
        assertEquals(0, scanSource(source).size)
    }

    @Test
    fun scanSourceForbiddenImportTextInNestedStringInsideInterpolationHasSizeZero() {
        // The nested string is a literal, not executable code, so the import-like
        // text inside it stays stripped: the expected violation count is zero.
        val source = "val s = \"\${listOf(\"import android.os.Bundle\")}\""
        assertEquals(0, scanSource(source).size)
    }

    @Test
    fun scanSourceEscapedIdentifierAfterAndroidDotHasSizeOne() {
        assertEquals(1, scanSource("val x = android.`when`.Foo").size)
    }

    @Test
    fun scanSourceForbiddenImportTextInPlainStringHasSizeZero() {
        assertEquals(0, scanSource("val s = \"import android.os.Bundle\"").size)
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

// Kotlin allows backtick-quoted identifiers after `android.` (e.g. android.`when`.Foo),
// so accept either form at every segment position.
private val androidSegmentPattern = """(?:`[^`\n]+`|[A-Za-z_][A-Za-z0-9_]*)"""

private val androidReferencePattern = Regex("""\bandroid\.$androidSegmentPattern(?:\.$androidSegmentPattern)*""")

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
    stripCode(text, 0, result)
    return result.toString()
}

/**
 * Copies executable code from [text], starting at [start], into [result]. Comments
 * and the literal text of string/char literals are dropped, but the code inside
 * `${...}` string templates is kept because Kotlin evaluates it as an expression.
 * Newlines are preserved everywhere so reported line numbers still match the
 * original source.
 *
 * When [stopAtClosingBrace] is true this scans the inside of an interpolation and
 * returns the index just past the `}` that closes it. Braces are matched by
 * counting, and strings, chars and comments inside the expression are consumed by
 * the same scanner, so `${ if (x) { foo("bar") } else { "" } }` is handled.
 *
 * Limitation: this is a lexical scanner, not a Kotlin parser. Only the `${...}`
 * template form is scanned. A bare `$name` template is skipped along with the
 * literal text because it cannot contain a dotted `android.*` reference of its
 * own (`"$android.os"` evaluates `android` and treats `.os` as literal text).
 */
private fun stripCode(
    text: String,
    start: Int,
    result: StringBuilder,
    stopAtClosingBrace: Boolean = false,
): Int {
    var index = start
    var braceDepth = if (stopAtClosingBrace) 1 else 0
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

            text.startsWith("\"\"\"", index) || text[index] == '"' -> {
                index = stripStringLiteral(text, index, result)
            }

            text[index] == '\'' -> {
                index = stripCharLiteral(text, index, result)
            }

            stopAtClosingBrace && text[index] == '{' -> {
                braceDepth++
                result.append('{')
                index++
            }

            stopAtClosingBrace && text[index] == '}' -> {
                braceDepth--
                index++
                if (braceDepth == 0) {
                    return index
                }
                result.append('}')
            }

            else -> {
                result.append(text[index])
                index++
            }
        }
    }
    return index
}

private fun stripStringLiteral(
    text: String,
    start: Int,
    result: StringBuilder,
): Int {
    val triple = text.startsWith("\"\"\"", start)
    var index = start + if (triple) 3 else 1
    while (index < text.length) {
        when {
            triple && text.startsWith("\"\"\"", index) -> {
                return index + 3
            }

            !triple && text[index] == '\\' -> {
                if (index + 1 < text.length && text[index + 1] == '\n') {
                    result.append('\n')
                }
                index += 2
            }

            !triple && text[index] == '"' -> {
                return index + 1
            }

            text[index] == '$' && index + 1 < text.length && text[index + 1] == '{' -> {
                index = stripCode(text, index + 2, result, stopAtClosingBrace = true)
            }

            else -> {
                if (text[index] == '\n') {
                    result.append('\n')
                }
                index++
            }
        }
    }
    return index
}

private fun stripCharLiteral(
    text: String,
    start: Int,
    result: StringBuilder,
): Int {
    var index = start + 1
    while (index < text.length) {
        when {
            text[index] == '\\' -> {
                index += 2
            }

            text[index] == '\'' -> {
                return index + 1
            }

            else -> {
                if (text[index] == '\n') {
                    result.append('\n')
                }
                index++
            }
        }
    }
    return index
}
