package net.montycasa.roux.domain.model

import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/**
 * The game page's two rules: which sections exist, and how old the page is.
 *
 * Both are pure functions of the three payloads, so both are pinned here rather than discovered on a device.
 */
class GameDetailTest {
    private val fullBoxscore =
        GameBoxscore(
            periodScoring = listOf(PeriodScoring(period = 1, awayGoals = 1, homeGoals = 0)),
            shots = ShotTotals(awayShots = 9, homeShots = 11),
            powerPlay = PowerPlaySummary(awayGoals = 0, awayChances = 2, homeGoals = 1, homeChances = 3),
            goalies = listOf(GoalieLine(PlayerRef("A. Goaltender", "MTL"), saves = 28, shotsAgainst = 30)),
        )

    private fun landing(stars: Int = 3) = GameLanding((1..stars).map { ThreeStar(it, PlayerRef("Player $it", "TOR")) })

    @Test
    fun sectionsComeBackInPageOrderWhateverOrderTheDataArrivesIn() {
        val detail =
            GameDetail(
                landing = Loadable(landing(), Freshness.Fresh, null),
                boxscore = Loadable(fullBoxscore, Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            )

        assertEquals(
            listOf(
                GameSection.PeriodScoring,
                GameSection.Shots,
                GameSection.PowerPlay,
                GameSection.Goalies,
                GameSection.ThreeStars,
            ),
            detail.sections(),
        )
    }

    @Test
    fun aFailedBoxscoreStillYieldsTheLandingSections() {
        // The failure this prevents: one timed-out endpoint blanking the whole page.
        val detail =
            GameDetail(
                landing = Loadable(landing(), Freshness.Fresh, null),
                boxscore = Loadable(null, Freshness.Missing, LoadError.Http(500)),
                rightRail = Loadable(null, Freshness.Missing, LoadError.Http(500)),
            )

        assertEquals(listOf(GameSection.ThreeStars), detail.sections())
    }

    @Test
    fun missingSectionsDoNotShiftTheOnesThatRemain() {
        // Only period scoring exists in this payload, so the page shows it first and nothing else - not an empty
        // Shots block sitting where the goals used to be.
        val detail =
            GameDetail(
                landing = Loadable(GameLanding(), Freshness.Fresh, null),
                boxscore = Loadable(fullBoxscore.copy(shots = null, powerPlay = null, goalies = emptyList()), Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            )

        assertEquals(listOf(GameSection.PeriodScoring), detail.sections())
    }

    @Test
    fun staleSinceIsTheOldestSectionNotTheNewest() {
        val oldest = Instant.parse("2026-10-26T18:00:00Z")
        val newest = Instant.parse("2026-10-26T23:30:00Z")
        val detail =
            GameDetail(
                landing = Loadable(landing(), Freshness.Stale(oldest), null),
                boxscore = Loadable(fullBoxscore, Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Stale(newest), null),
            )

        assertEquals(oldest, detail.staleSince())
    }

    @Test
    fun nothingStaleMeansNoTimestamp() {
        val detail =
            GameDetail(
                landing = Loadable(landing(), Freshness.Fresh, null),
                boxscore = Loadable(fullBoxscore, Freshness.Fresh, null),
                rightRail = Loadable(GameRightRail(), Freshness.Fresh, null),
            )

        assertNull("a fresh page has no stale timestamp to report", detail.staleSince())
    }
}
