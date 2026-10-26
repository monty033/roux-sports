package net.montycasa.roux.data

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** G5 keeps NHL wire DTOs inside the adapter, away from repository code. */
class AdapterIsolationTest {
    @Test
    fun repositoryDtoImportHasSizeOne() {
        assertEquals(1, sample("import net.montycasa.roux.data.nhl.dto.ScoreDto").size)
    }

    @Test
    fun adapterDtoImportHasSizeZero() {
        val sources =
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/nhl/Y.kt" to
                    "import net.montycasa.roux.data.nhl.dto.ScoreDto",
            )
        assertEquals(0, scanAdapterSources(sources).size)
    }

    @Test
    fun repositoryDomainImportHasSizeZero() {
        assertEquals(0, sample("import net.montycasa.roux.domain.Score").size)
    }

    @Test
    fun repositoryWildcardDtoImportHasSizeOne() {
        assertEquals(1, sample("import net.montycasa.roux.data.nhl.dto.*").size)
    }

    @Test
    fun repositoryLineCommentedDtoImportHasSizeZero() {
        assertEquals(0, sample("// import net.montycasa.roux.data.nhl.dto.ScoreDto").size)
    }

    @Test
    fun repositoryBlockCommentedDtoImportHasSizeZero() {
        assertEquals(0, sample("/*\nimport net.montycasa.roux.data.nhl.dto.ScoreDto\n*/").size)
    }

    @Test
    fun repositoryNearMissPackageHasSizeZero() {
        assertEquals(0, sample("import net.montycasa.roux.data.nhl.dtos.ScoreDto").size)
    }

    @Test
    fun repositoryExactDtoPackageImportHasSizeOne() {
        assertEquals(1, sample("import net.montycasa.roux.data.nhl.dto").size)
    }

    @Test
    fun repositoryDtoSubpackageImportHasSizeOne() {
        assertEquals(1, sample("import net.montycasa.roux.data.nhl.dto.scores.ScoreDto").size)
    }

    @Test
    fun repositoryBacktickQuotedDtoImportHasSizeOne() {
        assertEquals(1, sample("import `net`.montycasa.roux.data.nhl.`dto`.ScoreDto").size)
    }

    @Test
    fun repositoryCommentSplicedDtoImportHasSizeOne() {
        assertEquals(1, sample("import/*c*/net.montycasa.roux.data.nhl.dto.ScoreDto").size)
    }

    @Test
    fun repositoryMultipleImportsOnOneLineHasSizeTwo() {
        val source =
            "import net.montycasa.roux.domain.Score; " +
                "import net.montycasa.roux.data.nhl.dto.ScoreDto; " +
                "import net.montycasa.roux.data.nhl.dto.TeamDto as WireTeam"
        assertEquals(2, sample(source).size)
    }

    @Test
    fun repositoryDtoImportInStringHasSizeZero() {
        assertEquals(0, sample("val s = \"import net.montycasa.roux.data.nhl.dto.ScoreDto\"").size)
    }

    @Test
    fun repositoryDtoImportInTripleQuotedStringHasSizeZero() {
        assertEquals(0, sample("val s = \"\"\"import net.montycasa.roux.data.nhl.dto.ScoreDto\"\"\"").size)
    }

    @Test
    fun repositoryDtoImportInNestedStringHasSizeZero() {
        val source = "val s = \"\${listOf(\"import net.montycasa.roux.data.nhl.dto.ScoreDto\")}\""
        assertEquals(0, sample(source).size)
    }

    @Test
    fun repositorySiblingDirectoryHasSizeZero() {
        val sources =
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/repository/X.kt" to
                    "import net.montycasa.roux.data.nhl.dto.ScoreDto",
            )
        assertEquals(0, scanAdapterSources(sources).size)
    }

    @Test
    fun realMainTreeHasSizeZero() {
        val violations = scanAdapterTree(repositoryRoot())
        assertEquals(violations.joinToString("\n"), 0, violations.size)
    }

    @Test
    fun scanTreeFindsDtoImportInNestedRepositoryFile() {
        val root = temporaryRoot("adapter-isolation-scan")
        try {
            val nestedFile = File(root, "${REPOSITORY_PATH}nested/deep/Nested.kt")
            assertTrue(nestedFile.parentFile.mkdirs())
            nestedFile.writeText("package nested\n\nimport net.montycasa.roux.data.nhl.dto.ScoreDto\n")
            val violations = scanAdapterTree(root)
            assertEquals(1, violations.size)
            assertTrue(violations.single().contains(nestedFile.path))
            assertTrue(violations.single().contains("line 3: forbidden import: net.montycasa.roux.data.nhl.dto.ScoreDto"))
        } finally {
            assertTrue(root.deleteRecursively())
        }
    }

    @Test
    fun scanTreeReportsUnreadableKotlinFile() {
        val root = temporaryRoot("adapter-isolation-unreadable")
        val unreadable = File(root, "${REPOSITORY_PATH}Unreadable.kt")
        try {
            assertTrue(unreadable.parentFile.mkdirs())
            unreadable.writeText("package unreadable\n")
            assertTrue(unreadable.setReadable(false, false))
            assumeFalse("cannot simulate an unreadable file while running as root", unreadable.canRead())
            val violations = scanAdapterTree(root)
            assertEquals(1, violations.size)
            assertTrue(violations.single().contains(unreadable.path))
            assertTrue(violations.single().contains("unable to read file"))
        } finally {
            unreadable.setReadable(true, false)
            assertTrue(root.deleteRecursively())
        }
    }

    private fun sample(source: String): List<String> = scanAdapterSources(listOf("${REPOSITORY_PATH}X.kt" to source))

    private fun repositoryRoot(): File = File(System.getProperty("roux.rootDir"))

    private fun temporaryRoot(prefix: String): File {
        val parent = File(repositoryRoot(), "data/build/tmp").apply { mkdirs() }
        return Files.createTempDirectory(parent.toPath(), prefix).toFile()
    }
}

private const val REPOSITORY_PATH = "data/src/main/kotlin/net/montycasa/roux/data/repo/"
private const val DTO_PACKAGE = "net.montycasa.roux.data.nhl.dto"

/** Paths are relative to the repository root, with forward slash separators. */
internal fun scanAdapterSources(sources: Iterable<Pair<String, String>>): List<String> =
    sources.flatMap { (path, source) ->
        if (path.startsWith(REPOSITORY_PATH)) {
            scanSource(source).map { "$path: $it" }
        } else {
            emptyList()
        }
    }

/** Scans the real main tree relative to [root], also used by the planted-tree test. */
internal fun scanAdapterTree(root: File): List<String> {
    val dir = File(root, "data/src/main")
    val violations = mutableListOf<String>()
    val isDirectory =
        try {
            dir.exists() && dir.isDirectory
        } catch (exception: Exception) {
            violations.add("${dir.path}: unable to inspect path: ${reason(exception)}")
            false
        }
    if (!isDirectory) {
        return violations
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
            val relativePath = file.relativeTo(root).invariantSeparatorsPath
            for (violation in scanAdapterSources(listOf(relativePath to source))) {
                violations.add("${root.path}/$violation")
            }
        }
    } catch (exception: Exception) {
        violations.add("${dir.path}: scan aborted: ${reason(exception)}")
    }
    return violations
}

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

/** Comments and string contents stay lexer tokens, never raw import-like text. */
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

private fun scanSource(text: String): List<String> {
    val tokens = tokenize(text)
    val violations = mutableListOf<String>()
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        if (isImportKeyword(tokens, index)) {
            val parsed = parseImportPath(tokens, index + 1)
            if (parsed.path == DTO_PACKAGE || parsed.path.startsWith("$DTO_PACKAGE.")) {
                violations.add("line ${token.line}: forbidden import: ${parsed.path}")
            }
            index = parsed.nextIndex
            continue
        }
        index++
    }
    return violations
}

/** Kotlin 2.4 can lex an unquoted statement-starting `import` as an identifier. */
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
        TokenKind.IDENTIFIER -> token.text.removeSurrounding("`")
        TokenKind.MUL -> "*"
        else -> null
    }

private fun reason(exception: Throwable): String = "${exception.javaClass.simpleName}: ${exception.message ?: "no message"}"
