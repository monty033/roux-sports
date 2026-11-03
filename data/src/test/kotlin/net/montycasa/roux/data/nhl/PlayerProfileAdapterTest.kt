package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerRecentGame
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.model.newestSeasonRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player captures were taken with the club pages on 3 November 2026. */
private const val PLAYER_CAPTURE = "2026-11-03"

class PlayerProfileAdapterTest {
    private val skaterJson = Fixtures.text(PLAYER_CAPTURE, "player-8480037-landing.json")
    private val goalieJson = Fixtures.text(PLAYER_CAPTURE, "player-8476316-landing.json")

    @Test
    fun theSkaterFixtureBecomesACompleteSkaterProfile() {
        val profile = parsed(skaterJson)

        assertEquals(8480037L, profile.playerId)
        assertEquals("Aleksi", profile.firstName)
        assertEquals("Heponiemi", profile.lastName)
        assertEquals("C", profile.position)
        assertEquals(20, profile.sweaterNumber)
        assertEquals(70, profile.heightInches)
        assertEquals(155, profile.weightPounds)
        assertEquals("1999-01-09", profile.birthDate)
        assertEquals("Tampere", profile.birthCity)
        assertEquals("FIN", profile.birthCountry)
        assertEquals("L", profile.shootsCatches)
        assertEquals(2017, profile.draft?.year)
        assertEquals("FLA", profile.draft?.teamAbbrev)
        assertEquals(40, profile.draft?.overallPick)
        assertNull(profile.career.playoffs)

        val regular = profile.career.regularSeason as PlayerTotals.Skater
        assertEquals(25, regular.gamesPlayed)
        assertEquals(2, regular.goals)
        assertEquals(6, regular.points)
        assertEquals(0.0714, regular.shootingPctg!!, 0.0001)
        assertEquals(672L, regular.avgTimeOnIceSeconds) // 11:12
    }

    @Test
    fun theGoalieFixtureBranchesOnPositionAndReadsTheGoalieSet() {
        val profile = parsed(goalieJson)

        assertEquals("G", profile.position)
        assertEquals(31, profile.sweaterNumber)

        val regular = profile.career.regularSeason as PlayerTotals.Goalie
        assertEquals(142, regular.gamesPlayed)
        assertEquals(65, regular.wins)
        assertEquals(343, regular.goalsAgainst)
        assertEquals(2.66693, regular.goalsAgainstAverage!!, 0.00001)
        assertEquals(0.90981, regular.savePercentage!!, 0.00001)
        assertEquals(463004L, regular.timeOnIceSeconds) // 7716:44

        val playoffs = profile.career.playoffs as PlayerTotals.Goalie
        assertEquals(10, playoffs.gamesPlayed)
    }

    @Test
    fun aStatTheFeedOmitsIsNullAndARealZeroStaysZero() {
        val profile = parsed(withoutRegularSeasonKey(skaterJson, "shots"))

        val regular = profile.career.regularSeason as PlayerTotals.Skater
        assertNull(regular.shots)
        assertEquals(0, regular.shorthandedGoals)
    }

    @Test
    fun theNewestSeasonPutsTheNhlRegularSeasonRowFirstAndKeepsEveryRowOfThatSeason() {
        val rows = parsed(goalieJson).seasons.newestSeasonRows()

        assertEquals(listOf("NHL", "AHL"), rows.map { it.leagueAbbrev })
        assertEquals(listOf(1, 85177), rows.map { it.sequence })
        assertEquals(20262027, rows.first().season)
    }

    @Test
    fun aSkatersNewestSeasonIsItsShlRowBecauseNoNhlRowIsCurrent() {
        val rows = parsed(skaterJson).seasons.newestSeasonRows()

        assertEquals(listOf("SHL"), rows.map { it.leagueAbbrev })
    }

    @Test
    fun theLastFiveRowsTakeTheShapeOfThePlayersPosition() {
        val goalieGame = parsed(goalieJson).lastFive.single() as PlayerRecentGame.Goalie
        assertEquals("W", goalieGame.decision)
        assertEquals(3, goalieGame.goalsAgainst)
        assertEquals(true, goalieGame.isHome)
        assertEquals(3600L, goalieGame.timeOnIceSeconds)

        val skaterGame = parsed(skaterJson).lastFive.first() as PlayerRecentGame.Skater
        assertEquals("BOS", skaterGame.opponentAbbrev)
        assertEquals(false, skaterGame.isHome)
        assertEquals(481L, skaterGame.timeOnIceSeconds) // 8:01
    }

    @Test
    fun iceTimeIsWholeSecondsAndAnythingMalformedIsAbsent() {
        assertEquals(672L, PlayerProfileAdapter.parseIceTime("11:12"))
        assertEquals(463004L, PlayerProfileAdapter.parseIceTime("7716:44"))
        assertNull(PlayerProfileAdapter.parseIceTime("11:60"))
        assertNull(PlayerProfileAdapter.parseIceTime("11"))
        assertNull(PlayerProfileAdapter.parseIceTime(null))
    }

    @Test
    fun aPayloadWithoutAPlayerIdFailsRatherThanInventOne() {
        val result = PlayerProfileAdapter.parse(withoutTopLevelKey(skaterJson, "playerId"))

        assertTrue(result is ParseResult.Failure)
    }

    private fun parsed(json: String): PlayerProfile =
        (PlayerProfileAdapter.parse(json) as? ParseResult.Success)?.value
            ?: throw AssertionError("expected a parsed profile")

    private fun withoutRegularSeasonKey(
        json: String,
        key: String,
    ): String {
        val root = Json.parseToJsonElement(json).jsonObject
        val career = root.getValue("careerTotals").jsonObject
        val regular = career.getValue("regularSeason").jsonObject.filterKeys { it != key }
        val edited = career + ("regularSeason" to JsonObject(regular))
        return JsonObject(root + ("careerTotals" to JsonObject(edited))).toString()
    }

    private fun withoutTopLevelKey(
        json: String,
        key: String,
    ): String = JsonObject(Json.parseToJsonElement(json).jsonObject.filterKeys { it != key }).toString()
}
