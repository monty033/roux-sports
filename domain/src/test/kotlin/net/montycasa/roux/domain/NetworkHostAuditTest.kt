package net.montycasa.roux.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app reaches the three NHL hosts and, only from the ESPN client's paths, the two ESPN hosts authorized on
 * 2026-11-18: `site.api.espn.com` for data and `a.espncdn.com` for images. `site.web.api.espn.com` is not authorized.
 * This test reads the `src/main` tree of every module and fails on any other host, so a new network call cannot arrive
 * without the host decision being reviewed next to it.
 *
 * The split is asserted rather than merged because one shared set would let a bug in one league's adapter bill the
 * other league's host.
 *
 * `api.nhle.com` is the league statistics host, authorized on 2026-11-03 for the team-statistics tab.
 */
class NetworkHostAuditTest {
    private val nhlHosts = setOf("api-web.nhle.com", "assets.nhle.com", "api.nhle.com")
    private val espnHosts = setOf("site.api.espn.com", "a.espncdn.com")

    /** An ESPN host may be named only here: the ESPN client's own files, the NFL data tree, and the shared crest file. */
    private val espnHostPaths =
        listOf(
            "data/src/main/kotlin/net/montycasa/roux/data/net/Espn",
            "data/src/main/kotlin/net/montycasa/roux/data/nfl/",
            "app/src/main/kotlin/net/montycasa/roux/ui/common/LeagueCrest.kt",
        )

    /** An NHL host may never be named inside the ESPN client or the NFL data tree. */
    private val nhlHostForbiddenPaths =
        listOf(
            "data/src/main/kotlin/net/montycasa/roux/data/net/Espn",
            "data/src/main/kotlin/net/montycasa/roux/data/nfl/",
        )

    /**
     * Hosts outside the NHL and ESPN sets that one file may name, and why. Both are references rather than
     * destinations: the About section hands its link to a browser, and the User-Agent only names the project in a
     * header it sends.
     *
     * The About link is the public GitHub mirror, not the self-hosted Forgejo instance development happens on, because
     * that instance is not reachable from the internet and the link is for readers.
     */
    private val documentedHostReferences =
        mapOf(
            "app/src/main/kotlin/net/montycasa/roux/ui/settings/AboutSection.kt" to setOf("github.com"),
            "data/src/main/kotlin/net/montycasa/roux/data/net/NhlHttpClientFactory.kt" to setOf("git.montycasa.net"),
            // The ESPN client's User-Agent names the project, as the NHL one does. Its ESPN hosts need no entry: the path rule covers them.
            "data/src/main/kotlin/net/montycasa/roux/data/net/EspnHttpClientFactory.kt" to setOf("git.montycasa.net"),
        )

    @Test
    fun anUnlistedUrlHostIsFlagged() {
        val violations = violationsIn(mapOf("data/src/main/kotlin/Foo.kt" to "val url = \"https://example.com/x\""))

        assertEquals(listOf("data/src/main/kotlin/Foo.kt: unlisted host example.com"), violations)
    }

    @Test
    fun theNhlHostsAreAccepted() {
        val violations =
            violationsIn(
                mapOf(
                    "data/src/main/kotlin/Foo.kt" to
                        """
                        val standings = "https://api-web.nhle.com/v1/standings/now"
                        val schedule = "https://api-web.nhle.com/v1/schedule/2026-09-19"
                        val crest = "https://assets.nhle.com/logos/nhl/svg/ANA_light.svg"
                        val stats = "https://api.nhle.com/stats/rest/en/team/summary"
                        """.trimIndent(),
                ),
            )

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun theSourceLinkIsAllowedOnlyWhereItIsDocumented() {
        val link = "text = \"https://github.com/monty033/roux-sports\""

        assertEquals(emptyList<String>(), violationsIn(mapOf(SOURCE_LINK_ALLOWED_PATH to link)))
        assertEquals(
            listOf("app/src/main/kotlin/Other.kt: unlisted host github.com"),
            violationsIn(mapOf("app/src/main/kotlin/Other.kt" to link)),
        )
    }

    @Test
    fun theProjectHostInTheUserAgentIsAllowedOnlyInTheClient() {
        val agent = "headers.append(HttpHeaders.UserAgent, \"RouxSports/1.0 (+https://git.montycasa.net/patrick/roux-sports)\")"

        assertEquals(emptyList<String>(), violationsIn(mapOf(USER_AGENT_PATH to agent)))
        assertEquals(
            listOf("app/src/main/kotlin/Other.kt: unlisted host git.montycasa.net"),
            violationsIn(mapOf("app/src/main/kotlin/Other.kt" to agent)),
        )
    }

    @Test
    fun theEspnLogoHostIsNamedOnlyInItsClient() {
        val logo = "val logo = \"https://a.espncdn.com/i/teamlogos/nfl/500/phi.png\""

        assertEquals(
            listOf("app/src/main/kotlin/Other.kt: unlisted host a.espncdn.com"),
            violationsIn(
                mapOf("app/src/main/kotlin/Other.kt" to logo),
            ),
        )
        assertEquals(
            emptyList<String>(),
            violationsIn(mapOf("data/src/main/kotlin/net/montycasa/roux/data/net/EspnHttpClientFactory.kt" to logo)),
        )
    }

    @Test
    fun anEspnDataHostIsAcceptedOnlyForTheEspnClientAndTheNflTree() {
        val data = "val url = \"https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard\""
        val paths =
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnApi.kt",
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnHttpClientFactory.kt",
                "data/src/main/kotlin/net/montycasa/roux/data/nfl/NflScoreboardAdapter.kt",
                "app/src/main/kotlin/net/montycasa/roux/ui/common/LeagueCrest.kt",
                "data/src/main/kotlin/net/montycasa/roux/data/net/NhlApi.kt",
                "data/src/main/kotlin/net/montycasa/roux/data/repo/NflScoresRepositoryImpl.kt",
                "domain/src/main/kotlin/net/montycasa/roux/domain/model/PlayerMugshot.kt",
            )

        assertEquals(
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/net/NhlApi.kt: unlisted host site.api.espn.com",
                "data/src/main/kotlin/net/montycasa/roux/data/repo/NflScoresRepositoryImpl.kt: unlisted host site.api.espn.com",
                "domain/src/main/kotlin/net/montycasa/roux/domain/model/PlayerMugshot.kt: unlisted host site.api.espn.com",
            ),
            violationsIn(paths.associateWith { data }),
        )
    }

    @Test
    fun anNhlHostIsRefusedInsideTheEspnClientAndTheNflTree() {
        val nhl = "val url = \"https://api-web.nhle.com/v1/score/2026-11-22\""
        val paths =
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnApi.kt",
                "data/src/main/kotlin/net/montycasa/roux/data/nfl/NflScoreboardAdapter.kt",
                "domain/src/main/kotlin/net/montycasa/roux/domain/model/PlayerMugshot.kt",
                "app/src/main/kotlin/net/montycasa/roux/ui/common/LeagueCrest.kt",
            )

        assertEquals(
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnApi.kt: unlisted host api-web.nhle.com",
                "data/src/main/kotlin/net/montycasa/roux/data/nfl/NflScoreboardAdapter.kt: unlisted host api-web.nhle.com",
            ),
            violationsIn(paths.associateWith { nhl }),
        )
    }

    @Test
    fun theUnauthorizedEspnSiblingAndItsLookalikesAreFlaggedEverywhere() {
        val files =
            mapOf(
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnApi.kt" to
                    "val a = \"https://site.web.api.espn.com/x\"\nval b = \"https://site.api.espn.com.evil.test/x\"",
                "data/src/main/kotlin/net/montycasa/roux/data/nfl/X.kt" to
                    "val a = \"https://site.web.api.espn.com/x\"\nval b = \"https://site.api.espn.com.evil.test/x\"",
            )

        assertEquals(
            listOf(
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnApi.kt: unlisted host site.api.espn.com.evil.test",
                "data/src/main/kotlin/net/montycasa/roux/data/net/EspnApi.kt: unlisted host site.web.api.espn.com",
                "data/src/main/kotlin/net/montycasa/roux/data/nfl/X.kt: unlisted host site.api.espn.com.evil.test",
                "data/src/main/kotlin/net/montycasa/roux/data/nfl/X.kt: unlisted host site.web.api.espn.com",
            ),
            violationsIn(files),
        )
    }

    @Test
    fun aBareEspnHostWithoutASchemeIsAlsoAudited() {
        // Hosts reach the client as constants too, so a scheme-less ESPN host must not slip past.
        val host = "val host = \"a.espncdn.com\""

        assertEquals(
            listOf("data/src/main/kotlin/net/montycasa/roux/data/repo/X.kt: unlisted host a.espncdn.com"),
            violationsIn(mapOf("data/src/main/kotlin/net/montycasa/roux/data/repo/X.kt" to host)),
        )
        assertEquals(
            emptyList<String>(),
            violationsIn(mapOf("data/src/main/kotlin/net/montycasa/roux/data/net/EspnHttpClientFactory.kt" to host)),
        )
    }

    @Test
    fun aHostThatOnlyLooksAllowedIsFlagged() {
        val lookalikes =
            mapOf(
                "data/src/main/kotlin/A.kt" to "val a = \"https://notapi-web.nhle.com/x\"",
                "data/src/main/kotlin/B.kt" to "val b = \"https://api-web.nhle.com.evil.test/x\"",
                "data/src/main/kotlin/C.kt" to "val c = \"https://api.nhle.com.evil.test/x\"",
            )

        assertEquals(
            listOf(
                "data/src/main/kotlin/A.kt: unlisted host notapi-web.nhle.com",
                "data/src/main/kotlin/B.kt: unlisted host api-web.nhle.com.evil.test",
                "data/src/main/kotlin/C.kt: unlisted host api.nhle.com.evil.test",
            ),
            violationsIn(lookalikes),
        )
    }

    @Test
    fun aBareNhlDomainWithoutASchemeIsAlsoAudited() {
        // Hosts reach the client as constants and string templates too, so a scheme-less host must not slip past.
        val violations = violationsIn(mapOf("data/src/main/kotlin/Foo.kt" to "val host = \"stats.nhle.com\""))

        assertEquals(listOf("data/src/main/kotlin/Foo.kt: unlisted host stats.nhle.com"), violations)
    }

    @Test
    fun aUrlInACommentIsNotARequest() {
        // Source citations are not connections; only a Kotlin string literal can reach the network.
        val citation = "// the algorithm is described at https://howardhinnant.github.io/date_algorithms.html"
        val violations = violationsIn(mapOf("domain/src/main/kotlin/DateStrip.kt" to citation))

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun anXmlNamespaceIsNotARequest() {
        val manifest = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" />"""

        assertEquals(emptyList<String>(), violationsIn(mapOf("app/src/main/AndroidManifest.xml" to manifest)))
    }

    @Test
    fun theRealSourceTreeReachesOnlyTheAllowedHosts() {
        val root = repositoryRoot()
        val files = mainSources(root)

        // A test that scans nothing passes for the wrong reason; the app has far more than this.
        assertTrue("scanned only ${files.size} files under src/main", files.size >= 50)
        assertEquals(emptyList<String>(), violationsIn(files))
    }

    private fun violationsIn(files: Map<String, String>): List<String> =
        files
            .flatMap { (path, content) -> hostsIn(path, content).map { host -> path to host } }
            .filter { (path, host) -> !isAllowed(path, host) }
            .map { (path, host) -> "$path: unlisted host $host" }
            .sorted()

    private fun isAllowed(
        path: String,
        host: String,
    ): Boolean =
        when {
            host in espnHosts -> espnHostPaths.any(path::startsWith)
            host in nhlHosts -> nhlHostForbiddenPaths.none(path::startsWith)
            else -> host in documentedHostReferences[path].orEmpty()
        }

    /**
     * The hosts a file can send a request to. A Kotlin file is read through its string literals, because comments and
     * Kotlin type names are not connections. An XML file keeps its attributes but drops namespace declarations, which
     * are identifiers rather than destinations. The bare-domain pattern covers hosts built without a scheme, limited to
     * the domains this app uses so ordinary identifiers are not mistaken for hosts.
     */
    private fun hostsIn(
        path: String,
        content: String,
    ): List<String> {
        val searchable =
            when {
                path.endsWith(".kt") -> stringLiteralsIn(content)
                path.endsWith(".xml") -> content.replace(XML_NAMESPACE, "")
                else -> content
            }

        return (URL_HOST.findAll(searchable).map { it.groupValues[1] } + BARE_HOST.findAll(searchable).map { it.value })
            .map { it.substringBefore(':').lowercase().trimEnd('.') }
            .distinct()
            .sorted()
            .toList()
    }

    private fun stringLiteralsIn(content: String): String =
        (RAW_STRING.findAll(content).map { it.value } + STRING.findAll(content).map { it.value }).joinToString("\n")

    /** Every file under a module's `src/main` tree, keyed by its repository-relative path. */
    private fun mainSources(root: File): Map<String, String> =
        root
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name !in SKIPPED_DIRECTORIES }
            .flatMap { module -> File(module, "src/main").takeIf { it.isDirectory }?.sourceFiles().orEmpty() }
            .associate { file -> file.relativeTo(root).path.replace(File.separatorChar, '/') to file.readText() }

    private fun File.sourceFiles(): List<File> =
        walkTopDown()
            .onEnter { it.name !in SKIPPED_DIRECTORIES }
            .filter { it.isFile }
            .toList()

    private fun repositoryRoot(): File {
        val configured = System.getProperty("roux.rootDir")?.let(::File)
        val start = configured ?: File(System.getProperty("user.dir"))
        val root =
            generateSequence(start) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }

        assertTrue("could not find settings.gradle.kts above $start", root != null)

        return root!!
    }

    private companion object {
        const val SOURCE_LINK_ALLOWED_PATH = "app/src/main/kotlin/net/montycasa/roux/ui/settings/AboutSection.kt"
        const val USER_AGENT_PATH = "data/src/main/kotlin/net/montycasa/roux/data/net/NhlHttpClientFactory.kt"

        val URL_HOST = Regex("""https?://([A-Za-z0-9._-]+)""")
        val BARE_HOST =
            Regex("""(?<![\w.-])[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*(?:\.nhle\.com|\.montycasa\.net|\.espn\.com|\.espncdn\.com)(?![\w.-])""")
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:[^\"\\\\\\n]|\\\\.)*\"")
        val XML_NAMESPACE = Regex("""xmlns:[A-Za-z0-9-]+="[^"]*"""")
        val SKIPPED_DIRECTORIES = setOf("build", ".gradle", ".git", ".worktrees")
    }
}
