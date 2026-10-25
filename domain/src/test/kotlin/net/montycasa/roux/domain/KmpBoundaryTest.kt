package net.montycasa.roux.domain

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
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
    fun scanSourceEscapedAndroidRootHasSizeOne() {
        assertEquals(1, scanSource("val x = `android`.os.Build.MODEL").size)
    }

    @Test
    fun scanSourceWhitespaceSeparatedAndroidReferenceHasSizeOne() {
        assertEquals(1, scanSource("val y = android . os . Build.MODEL").size)
    }

    @Test
    fun scanSourceCommentSplicedJavaImportHasSizeOne() {
        assertEquals(1, scanSource("import/*c*/java.time.Instant").size)
    }

    @Test
    fun scanSourceEscapedAndroidImportRootHasSizeOne() {
        assertEquals(1, scanSource("import `android`.os.Bundle").size)
    }

    @Test
    fun scanSourceLocalAndroidVariableInInterpolationHasSizeZero() {
        val source = "val android = Any(); val r = \"\${android}\".toString()"
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

private enum class TokenKind {
    IDENTIFIER,
    DOT,
    MUL,
    SEMICOLON,
    IMPORT,
    OTHER,
}

private data class Token(
    val kind: TokenKind,
    val text: String,
    val line: Int,
)

private data class ParsedImport(
    val path: String,
    val nextIndex: Int,
)

/**
 * Tokenizes [text] with the Kotlin compiler's own lexer and keeps only the
 * significant tokens. Comments, whitespace and the literal parts of strings and
 * char literals are dropped as tokens in their own right, so text that merely
 * looks like an import or a reference can never be mistaken for code.
 */
private fun tokenize(text: String): List<Token> {
    val lexer = KotlinLexer()
    lexer.start(text, 0, text.length, 0)
    val tokens = mutableListOf<Token>()
    var line = 1
    while (true) {
        val type = lexer.tokenType ?: break
        val tokenText = text.substring(lexer.tokenStart, lexer.tokenEnd)
        val kind =
            when (type) {
                KtTokens.WHITE_SPACE,
                KtTokens.EOL_COMMENT,
                KtTokens.BLOCK_COMMENT,
                KtTokens.DOC_COMMENT,
                KtTokens.SHEBANG_COMMENT,
                -> null

                KtTokens.IMPORT_KEYWORD -> TokenKind.IMPORT

                KtTokens.IDENTIFIER -> TokenKind.IDENTIFIER

                KtTokens.DOT -> TokenKind.DOT

                KtTokens.MUL -> TokenKind.MUL

                KtTokens.SEMICOLON, KtTokens.EOL_OR_SEMICOLON -> TokenKind.SEMICOLON

                else -> TokenKind.OTHER
            }
        if (kind != null) {
            tokens.add(Token(kind, tokenText, line))
        }
        line += tokenText.count { it == '\n' }
        lexer.advance()
    }
    return tokens
}

fun scanSource(text: String): List<String> {
    val tokens = tokenize(text)
    val violations = mutableListOf<String>()
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        if (isImportKeyword(tokens, index)) {
            val parsed = parseImportPath(tokens, index + 1)
            if (parsed.path.isNotEmpty() && isForbiddenImport(parsed.path)) {
                violations.add("line ${token.line}: forbidden import: ${parsed.path}")
            }
            index = parsed.nextIndex
            continue
        }
        if (isAndroidReference(tokens, index)) {
            violations.add("line ${token.line}: forbidden android reference: ${referencePath(tokens, index)}")
        }
        index++
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

/**
 * Kotlin 2.4's lexer does not emit [KtTokens.IMPORT_KEYWORD] for `import`; it
 * comes back as an ordinary IDENTIFIER. Treat an unquoted `import` that starts a
 * statement as the import keyword, so both token shapes are handled.
 */
private fun isImportKeyword(
    tokens: List<Token>,
    index: Int,
): Boolean {
    val token = tokens[index]
    if (token.kind == TokenKind.IMPORT) {
        return true
    }
    if (token.kind != TokenKind.IDENTIFIER || token.text != "import") {
        return false
    }
    val previous = tokens.getOrNull(index - 1) ?: return true
    return previous.kind == TokenKind.SEMICOLON || previous.line < token.line
}

private fun parseImportPath(
    tokens: List<Token>,
    startIndex: Int,
): ParsedImport {
    val parts = mutableListOf<String>()
    var index = startIndex
    while (index < tokens.size) {
        val part = importPart(tokens[index]) ?: break
        parts.add(part)
        index++
        if (tokens.getOrNull(index)?.kind != TokenKind.DOT) {
            break
        }
        index++
    }
    return ParsedImport(parts.joinToString("."), index)
}

private fun importPart(token: Token): String? =
    when (token.kind) {
        TokenKind.IDENTIFIER -> unquote(token.text)
        TokenKind.MUL -> "*"
        else -> null
    }

private fun isAndroidReference(
    tokens: List<Token>,
    index: Int,
): Boolean {
    val token = tokens[index]
    if (token.kind != TokenKind.IDENTIFIER || unquote(token.text) != "android") {
        return false
    }
    if (tokens.getOrNull(index - 1)?.kind == TokenKind.DOT) {
        return false
    }
    return tokens.getOrNull(index + 1)?.kind == TokenKind.DOT
}

private fun referencePath(
    tokens: List<Token>,
    startIndex: Int,
): String {
    val parts = mutableListOf(unquote(tokens[startIndex].text))
    var index = startIndex
    while (true) {
        if (tokens.getOrNull(index + 1)?.kind != TokenKind.DOT) {
            break
        }
        val segment = tokens.getOrNull(index + 2) ?: break
        if (segment.kind != TokenKind.IDENTIFIER) {
            break
        }
        parts.add(unquote(segment.text))
        index += 2
    }
    return parts.joinToString(".")
}

private fun unquote(text: String): String =
    if (text.length >= 2 && text.startsWith('`') && text.endsWith('`')) {
        text.substring(1, text.length - 1)
    } else {
        text
    }

private fun isForbiddenImport(importPath: String): Boolean {
    val root = importPath.substringBefore('.')
    if (root == "android" || root == "java" || root == "javax") {
        return true
    }
    val isAndroidx = root == "androidx"
    return isAndroidx && !isAllowedAndroidx(importPath)
}

private fun isAllowedAndroidx(importPath: String): Boolean =
    allowedAndroidxPaths.any { allowed ->
        importPath == allowed || importPath.startsWith("$allowed.")
    }

private fun reason(exception: Throwable): String = "${exception.javaClass.simpleName}: ${exception.message ?: "no message"}"
