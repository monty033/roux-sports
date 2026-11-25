package net.montycasa.roux.ui.standings

import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflStandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Test

class NflStandingsOrderTest {
    private fun club(
        abbrev: String,
        pct: String? = null,
        diff: Int? = null,
        pointsFor: Int? = null,
        seed: Int? = null,
    ) = NflStandingRow(
        team = TeamRef(TeamKey(League.NFL, abbrev)),
        winPercent = pct,
        differential = diff,
        pointsFor = pointsFor,
        playoffSeed = seed,
    )

    private fun order(vararg rows: NflStandingRow) = leagueOrder(rows.toList()).map { it.team.abbrev }

    @Test
    fun theLeagueIsOrderedByWinPercentageFirst() {
        assertEquals(listOf("AAA", "BBB", "CCC"), order(club("CCC", ".400"), club("AAA", ".889"), club("BBB", ".500")))
    }

    @Test
    fun winPercentageIsComparedAsANumberNotAsText() {
        // As text, "1.000" sorts before ".889" and ".500" before ".1": the feed's own display is read as the number it is.
        assertEquals(listOf("WIN", "MID", "LOW"), order(club("LOW", ".100"), club("MID", ".889"), club("WIN", "1.000")))
    }

    @Test
    fun aTiedPercentageFallsToPointDifferentialThenPointsForThenAbbreviation() {
        val rows =
            arrayOf(
                club("ZZZ", ".778", diff = 50, pointsFor = 200),
                club("AAA", ".778", diff = 50, pointsFor = 200),
                club("MMM", ".778", diff = 50, pointsFor = 240),
                club("BBB", ".778", diff = 125, pointsFor = 100),
                club("TOP", ".889", diff = -10, pointsFor = 1),
            )
        // TOP wins on percentage alone; BBB leads the .778 clubs on differential; MMM beats the level pair on points for;
        // and the last two are level in everything, so the abbreviation settles them, alphabetically.
        assertEquals(listOf("TOP", "BBB", "MMM", "AAA", "ZZZ"), order(*rows))
    }

    @Test
    fun theOrderDoesNotDependOnTheOrderTheClubsArriveIn() {
        val rows = listOf(club("A", ".5", 1, 10), club("B", ".5", 1, 10), club("C", ".5", 2, 5), club("D", ".6", 0, 0))
        assertEquals(order(*rows.toTypedArray()), order(*rows.reversed().toTypedArray()))
    }

    @Test
    fun aClubWithAMissingFigureSortsAfterEveryClubThatHasIt() {
        assertEquals(
            listOf("HAS", "NODIFF", "NOPCT"),
            order(club("NOPCT"), club("NODIFF", ".500"), club("HAS", ".500", diff = -40)),
        )
    }

    @Test
    fun theCapturedLeagueIsThirtyTwoClubsWithTheFeedsLeadersOnTop() {
        val league = leagueOrder(NflStandingsFixtures.conferences.flatMap { it.rows })
        assertEquals(32, league.size)
        // BUF and SF are both .889; BUF's +88 beats SF's +85.
        assertEquals(listOf("BUF", "SF"), league.take(2).map { it.team.abbrev })
        // Three clubs share .111 at the bottom and differential orders them: TEN -72, MIA -76, LAC -95. NYJ, at .200, is
        // the last club above them.
        assertEquals(listOf("NYJ", "TEN", "MIA", "LAC"), league.takeLast(4).map { it.team.abbrev })
    }

    @Test
    fun seedOrderPutsTheSeededClubsInSeedOrderAndTheUnseededLastInFeedOrder() {
        val rows = listOf(club("D", seed = 4), club("U1"), club("A", seed = 1), club("U2"), club("C", seed = 3))
        assertEquals(listOf("A", "C", "D", "U1", "U2"), seedOrder(rows).map { it.team.abbrev })
    }

    @Test
    fun theCapturedNfcSeedOrderPutsNewYorkAndCarolinaAboveTheLine() {
        val nfc = seedOrder(NflStandingsFixtures.nfc.rows)
        assertEquals(listOf("SF", "DET", "NYG", "CAR", "MIN", "CHI", "GB"), nfc.take(7).map { it.team.abbrev })
    }
}
