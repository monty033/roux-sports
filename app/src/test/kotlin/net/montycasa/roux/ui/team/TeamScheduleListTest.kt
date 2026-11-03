package net.montycasa.roux.ui.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.repo.clubScheduleRepository
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.nav.detailRoute
import net.montycasa.roux.ui.theme.RouxTheme
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.TimeZone as JavaTimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
class TeamScheduleListTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = this@TeamScheduleListTest.now
        }
    private lateinit var originalTimeZone: JavaTimeZone

    @Before
    fun setTorontoTimeZone() {
        originalTimeZone = JavaTimeZone.getDefault()
        // The right-hand time and the date text have to be fixed for the row assertions.
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/Toronto"))
    }

    @After
    fun restoreTimeZone() {
        JavaTimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun theCapturedSeasonHasEveryGameInAscendingStartOrder() {
        val directory = Files.createTempDirectory("roux-club-schedule").toFile()
        try {
            val payload =
                requireNotNull(javaClass.getResourceAsStream("/fx/2026-11-03/club-schedule-ana-20262027.json")) {
                    "captured ANA club schedule is missing"
                }.use { it.readBytes() }
            val engine =
                MockEngine {
                    respond(
                        payload,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = NhlApi(NhlHttpClientFactory.create(engine, "test-version"))
            val cache = ResponseCache(directory = directory.absolutePath.toPath(), clock = clock)
            val repository =
                clubScheduleRepository(
                    api = api,
                    cache = cache,
                    clock = clock,
                    zone = TimeZone.of("America/Toronto"),
                    ioDispatcher = Dispatchers.Unconfined,
                )
            val games = requireNotNull(runBlocking { repository.schedule(TeamKey("ANA")).data })
            val rows =
                scheduleListModel(games, Locale.US)
                    .entries
                    .filterIsInstance<ScheduleListEntry.Game>()
                    .map { it.game }

            assertEquals("the captured ANA season has 88 games", 88, rows.size)
            assertEquals(rows.sortedBy { it.startTimeUtc }, rows)
            assertEquals(games.sortedBy { it.startTimeUtc }.map { it.id }, rows.map { it.id })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun monthHeadersAscendAndEachGameSitsUnderItsMonth() {
        val games =
            listOf(
                game("dec", "2026-12-05T20:00:00Z", GameState.Scheduled),
                game("oct", "2026-10-12T20:00:00Z", GameState.Final, finished = true),
                game("nov", "2026-11-04T20:00:00Z", GameState.Scheduled),
            )

        val model = scheduleListModel(games, Locale.US)
        val months = model.entries.filterIsInstance<ScheduleListEntry.Month>().map { it.label }
        assertEquals(listOf("October", "November", "December"), months)

        val monthForGame = mutableMapOf<String, String>()
        var currentMonth: String? = null
        model.entries.forEach { entry ->
            when (entry) {
                is ScheduleListEntry.Month -> currentMonth = entry.label
                is ScheduleListEntry.Game -> monthForGame[entry.game.id.value] = requireNotNull(currentMonth)
            }
        }
        assertEquals(mapOf("oct" to "October", "nov" to "November", "dec" to "December"), monthForGame)
    }

    @Test
    fun theNextGameOpensCentredInTheViewport() {
        val games = seasonAroundNext()
        showList(games)

        val nextTag = "team.schedule.row.next"
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag(nextTag).fetchSemanticsNode() }.isSuccess
        }
        val list = rule.onNodeWithTag("team.schedule.list").fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag(nextTag).fetchSemanticsNode().boundsInRoot
        val listCentre = list.top + list.height / 2
        val rowCentre = row.top + row.height / 2

        assertTrue(
            "next row centre $rowCentre should be within a row of viewport centre $listCentre",
            abs(rowCentre - listCentre) <= row.height,
        )
    }

    @Test
    fun scrollingUpAndDownFromTheAnchorRevealsEarlierAndLaterGames() {
        val games = seasonAroundNext()
        showList(games)
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("team.schedule.row.next").fetchSemanticsNode() }.isSuccess
        }

        val older = "team.schedule.row.old-5"
        val newer = "team.schedule.row.new-5"
        rule.onNodeWithTag(older).assertDoesNotExist()

        rule.onNodeWithTag("team.schedule.list").performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onNodeWithTag(older).assertExists()

        rule.onNodeWithTag("team.schedule.list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("team.schedule.list").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag(newer).assertExists()
    }

    @Test
    fun theNextGameCarriesATextMarker() {
        showList(seasonAroundNext())
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("team.schedule.row.next.next", useUnmergedTree = true).fetchSemanticsNode() }
                .isSuccess
        }

        rule.onNodeWithTag("team.schedule.row.next.next", useUnmergedTree = true).assertTextEquals("Next")
    }

    @Test
    fun aHomeGameReadsDateCrestThenVersusTheNicknameOnly() {
        val home = game("home", "2026-11-05T20:00:00Z", GameState.Scheduled, home = TeamKey("TOR"), away = TeamKey("BOS"))
        showList(listOf(home))
        rule.waitForIdle()

        val tag = "team.schedule.row.home"
        rule.onNodeWithTag("$tag.name", useUnmergedTree = true).assertTextEquals("vs Bruins")
        rule.onNodeWithTag("$tag.day", useUnmergedTree = true).assertTextEquals("Thu, Nov 5")
        val date = rule.onNodeWithTag("$tag.day", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val crest = rule.onNodeWithTag("$tag.crest", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the date must sit left of the crest", date.right <= crest.left)
        assertTrue(
            "the city must not appear in the row",
            rule.onAllNodesWithText("Boston", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun anAwayGameReadsAtTheNickname() {
        val away = game("away", "2026-11-05T20:00:00Z", GameState.Scheduled, home = TeamKey("BOS"), away = TeamKey("TOR"))
        showList(listOf(away))
        rule.waitForIdle()

        rule.onNodeWithTag("team.schedule.row.away.name", useUnmergedTree = true).assertTextEquals("at Bruins")
    }

    @Test
    fun theRightCellShowsTimeLiveOrTheResultFromThisClubsSide() {
        val future = game("future", "2026-11-05T20:00:00Z", GameState.Scheduled)
        val live = game("live", "2026-11-03T11:00:00Z", GameState.Live())
        val win = game("win", "2026-11-01T20:00:00Z", GameState.Final, finished = true, homeScore = 5, awayScore = 3)
        showList(listOf(future, live, win))
        rule.waitForIdle()

        rule.onNodeWithTag("team.schedule.row.future.state", useUnmergedTree = true).assertTextEquals("3:00 PM")
        rule.onNodeWithTag("team.schedule.row.live.state", useUnmergedTree = true).assertTextEquals("Live")
        rule.onNodeWithTag("team.schedule.row.win.state", useUnmergedTree = true).assertTextEquals("W 5-3")
    }

    @Test
    fun anAwayWinStillPrintsTheClubsScoreFirstInTheRow() {
        val awayWin =
            game(
                "away-win",
                "2026-11-01T20:00:00Z",
                GameState.Final,
                finished = true,
                home = TeamKey("BOS"),
                away = TeamKey("TOR"),
                homeScore = 3,
                awayScore = 5,
            )
        showList(listOf(awayWin))
        rule.waitForIdle()

        rule.onNodeWithTag("team.schedule.row.away-win.state", useUnmergedTree = true).assertTextEquals("W 5-3")
    }

    @Test
    fun aFinishedGameWithNoScoreStillSaysFinal() {
        val noScore = game("no-score", "2026-11-01T20:00:00Z", GameState.Final, finished = true)
        showList(listOf(noScore))
        rule.waitForIdle()

        rule.onNodeWithTag("team.schedule.row.no-score.state", useUnmergedTree = true).assertTextEquals("Final")
    }

    @Test
    fun theTabsShowScheduleSelectedAndAllThreeTabsAvailable() {
        showScreen(schedule = Loadable(listOf(game("next", "2026-11-05T20:00:00Z", GameState.Scheduled)), Freshness.Fresh, null))

        rule.onNodeWithTag("team.tab.Schedule").assertIsSelected().assertIsEnabled()
        rule
            .onNodeWithTag("team.tab.Roster")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertIsNotSelected()
        rule
            .onNodeWithTag("team.tab.Stats")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertIsNotSelected()
    }

    @Test
    fun theTabsSitAtTheBottomOfThePageUnderTheList() {
        showScreen(schedule = Loadable(seasonAroundNext(), Freshness.Fresh, null))

        val list = rule.onNodeWithTag("team.schedule.list").fetchSemanticsNode().boundsInRoot
        val tabs = rule.onNodeWithTag("team.tab.Schedule").fetchSemanticsNode().boundsInRoot
        val page = rule.onNodeWithTag("team.page.TOR").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "the tab row belongs below the list, at ${tabs.top} against the list's ${list.bottom}",
            tabs.top >= list.bottom - 1f,
        )
        assertTrue(
            "the tab row belongs at the bottom of the page, ${tabs.bottom} against ${page.bottom}",
            page.bottom - tabs.bottom <= 32f,
        )
    }

    @Test
    fun aScheduleFailureLeavesTheHeaderAndTabsOnScreen() {
        showScreen(
            schedule = Loadable(data = null, freshness = Freshness.Missing, error = LoadError.Network),
        )

        rule.onNodeWithTag("team.name").assertIsDisplayed()
        rule.onNodeWithTag("team.tab.Schedule").assertIsDisplayed()
        rule.onNodeWithTag("team.schedule.error").assertIsDisplayed()
        rule.onNodeWithText("Schedule could not be loaded.").assertIsDisplayed()
        rule.onNodeWithTag("team.schedule.retry").assertExists()
    }

    @Test
    fun tappingARowRoutesToThatGamesDetailPage() {
        var route: String? = null
        val games = seasonAroundNext()
        showScreen(
            schedule = Loadable(games, Freshness.Fresh, null),
            onGameClick = { game -> route = detailRoute(game) },
        )
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("team.schedule.row.next").fetchSemanticsNode() }.isSuccess
        }

        rule.onNodeWithTag("team.schedule.row.next").performClick()
        rule.waitForIdle()

        assertEquals(detailRoute(games.first { it.id.value == "next" }), route)
        assertTrue(route.orEmpty().startsWith("detail/next"))
    }

    private fun showList(games: List<ClubGame>) {
        rule.setContent {
            RouxTheme {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    TeamScheduleList(
                        teamKey = TeamKey("TOR"),
                        schedule = Loadable(games, Freshness.Fresh, null),
                        isRefreshing = false,
                        onRetry = {},
                        onGameClick = {},
                        now = now,
                    )
                }
            }
        }
    }

    private fun showScreen(
        schedule: Loadable<List<ClubGame>>,
        onGameClick: (ClubGame) -> Unit = {},
    ) {
        rule.setContent {
            RouxTheme {
                TeamDetailScreen(
                    state =
                        TeamDetailUiState(
                            teamKey = TeamKey("TOR"),
                            row = toronto,
                            isLoading = false,
                            schedule = schedule,
                        ),
                    onBack = {},
                    onGameClick = onGameClick,
                    now = now,
                )
            }
        }
    }

    /**
     * Ten finished games before the anchor, then the next game, then enough future games that the
     * list has something to scroll towards in both directions.
     */
    private fun seasonAroundNext(): List<ClubGame> {
        val older =
            (1..10).map { index ->
                game("old-$index", "2026-10-${index.toString().padStart(2, '0')}T20:00:00Z", GameState.Final, finished = true)
            }
        val next = game("next", "2026-11-05T20:00:00Z", GameState.Scheduled)
        val newer =
            (1..10).map { index ->
                game("new-$index", "2026-11-${(index + 5).toString().padStart(2, '0')}T20:00:00Z", GameState.Scheduled)
            }
        return listOf(older[2], next, older[5], newer[4], older[8], newer[8]) +
            older.filterIndexed { index, _ -> index !in setOf(2, 5, 8) } +
            newer.filterIndexed { index, _ -> index !in setOf(4, 8) }
    }

    private fun game(
        id: String,
        start: String,
        state: GameState,
        finished: Boolean = false,
        home: TeamKey = TeamKey("TOR"),
        away: TeamKey = TeamKey("BOS"),
        homeScore: Int? = null,
        awayScore: Int? = null,
    ): ClubGame {
        val instant = Instant.parse(start)
        return ClubGame(
            id = GameId(id),
            date = start.substring(0, 10),
            startTimeUtc = instant,
            home = teamRef(home),
            away = teamRef(away),
            state = state,
            finished = finished,
            homeScore = homeScore,
            awayScore = awayScore,
        )
    }

    private fun teamRef(team: TeamKey): TeamRef =
        when (team.abbrev) {
            "TOR" -> TeamRef(team, name = "Toronto Maple Leafs", shortName = "Maple Leafs")
            "BOS" -> TeamRef(team, name = "Boston Bruins", shortName = "Bruins")
            else -> TeamRef(team)
        }

    private val toronto =
        StandingRow(
            team = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs"),
            gp = 12,
            w = 9,
            l = 2,
            otl = 1,
            pts = 19,
            leagueSequence = 7,
            conferenceSequence = 3,
            divisionSequence = 2,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Atlantic,
        )
}
