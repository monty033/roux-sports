package net.montycasa.roux.data.nhl

import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.GoalieDecision
import net.montycasa.roux.domain.model.PlayerGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The game-log captures were taken with the player pages on 4 November 2026. */
private const val GAME_LOG_CAPTURE = "2026-11-04"

class PlayerGameLogAdapterTest {
    private val goalieJson = Fixtures.text(GAME_LOG_CAPTURE, "player-8476316-game-log-20262027.json")
    private val skaterJson = Fixtures.text(GAME_LOG_CAPTURE, "player-8478402-game-log-20262027.json")

    @Test
    fun theGoalieFixtureBecomesGoalieRowsWithDecisionAndSavesNotGoalsOrAssists() {
        val games = parsed(goalieJson, isGoalie = true)

        assertEquals(listOf(2026020246L, 2026020231L), games.map { it.gameId })
        val first = games.first() as PlayerGame.Goalie
        assertEquals("2026-11-03", first.gameDate)
        assertEquals("VAN", first.opponentAbbrev)
        assertEquals(false, first.isHome)
        assertEquals(GoalieDecision.Win, first.decision)
        assertEquals(23, first.shotsAgainst)
        assertEquals(4, first.goalsAgainst)
        assertEquals(0.826087, first.savePercentage!!, 0.000001)
        assertEquals(3571L, first.timeOnIceSeconds) // 59:31

        val second = games.last() as PlayerGame.Goalie
        assertEquals(true, second.isHome)
        assertEquals(3600L, second.timeOnIceSeconds) // 60:00
    }

    @Test
    fun theSkaterFixtureKeepsTheFeedsOrderAndReadsTheSkaterCounts() {
        val games = parsed(skaterJson, isGoalie = false)

        // The feed's own order, which the screen re-sorts. The adapter must not sort it itself.
        assertEquals(listOf(2026020239L, 2026020230L, 2026020220L), games.map { it.gameId })
        val first = games.first() as PlayerGame.Skater
        assertEquals("NJD", first.opponentAbbrev)
        assertEquals(false, first.isHome)
        assertEquals(1, first.assists)
        assertEquals(1, first.points)
        assertEquals(0, first.plusMinus)
        assertEquals(1, first.shots)
        assertEquals(1455L, first.timeOnIceSeconds) // 24:15
    }

    /** The club a row was played for is carried through, so a tapped game can name its two clubs. */
    @Test
    fun eachGameRowKeepsTheClubThePlayerPlayedForInThatGame() {
        val skater = parsed(skaterJson, isGoalie = false)
        val goalie = parsed(goalieJson, isGoalie = true)

        assertEquals(listOf("EDM", "EDM", "EDM"), skater.map { it.teamAbbrev })
        assertEquals(listOf("ANA", "ANA"), goalie.map { it.teamAbbrev })
    }

    @Test
    fun everyFeedDecisionCodeMapsToOneOutcomeAndAnyOtherCodeIsAbsent() {
        val games =
            parsed(
                log(
                    goalieRow(1, "W"),
                    goalieRow(2, "L"),
                    goalieRow(3, "OT"),
                    goalieRow(4, "OTL"),
                    goalieRow(5, "X"),
                    goalieRow(6, null),
                ),
                isGoalie = true,
            )

        assertEquals(
            listOf(GoalieDecision.Win, GoalieDecision.Loss, GoalieDecision.OvertimeLoss, GoalieDecision.OvertimeLoss, null, null),
            games.map { (it as PlayerGame.Goalie).decision },
        )
    }

    @Test
    fun aRowWithNoGameIdIsDroppedRatherThanGuessedAt() {
        val games =
            parsed(
                log(
                    """{"gameDate":"2026-11-01","homeRoadFlag":"H","opponentAbbrev":"OTT","goals":1}""",
                    skaterRow(7),
                ),
                isGoalie = false,
            )

        assertEquals(listOf(7L), games.map { it.gameId })
    }

    @Test
    fun aPayloadWithNoGameLogKeyIsAnEmptyLogNotAFailure() {
        // A rostered player with no games this season is sent without the key at all.
        val result = PlayerGameLogAdapter.parse("""{"seasonId":20262027,"gameTypeId":2}""", isGoalie = false)

        assertEquals(emptyList<PlayerGame>(), (result as ParseResult.Success).value)
    }

    @Test
    fun aPayloadThatIsNotJsonFailsSoTheScreenCanSayTheLogFailed() {
        val result = PlayerGameLogAdapter.parse("<html>upstream error</html>", isGoalie = true)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun aHomeFlagOtherThanHOrRIsNotGuessed() {
        val games = parsed(log("""{"gameId":9,"homeRoadFlag":"N","gameDate":"2026-11-01"}"""), isGoalie = false)

        assertNull((games.single() as PlayerGame.Skater).isHome)
    }

    private fun parsed(
        json: String,
        isGoalie: Boolean,
    ): List<PlayerGame> =
        (PlayerGameLogAdapter.parse(json, isGoalie) as? ParseResult.Success)?.value
            ?: throw AssertionError("expected a parsed game log")

    private fun log(vararg rows: String): String = """{"gameLog":[${rows.joinToString(",")}]}"""

    private fun goalieRow(
        id: Long,
        decision: String?,
    ): String {
        val code = decision?.let { "\"decision\":\"$it\"," }.orEmpty()
        return """{"gameId":$id,"gameDate":"2026-11-0$id","homeRoadFlag":"H",$code"opponentAbbrev":"OTT",""" +
            """"shotsAgainst":20,"goalsAgainst":2,"savePctg":0.9,"toi":"60:00"}"""
    }

    private fun skaterRow(id: Long): String =
        """{"gameId":$id,"gameDate":"2026-11-01","homeRoadFlag":"H","opponentAbbrev":"OTT","goals":1,"toi":"18:00"}"""
}
