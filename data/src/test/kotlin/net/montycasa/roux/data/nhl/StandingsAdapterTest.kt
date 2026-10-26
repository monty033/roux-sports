package net.montycasa.roux.data.nhl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.data.Fixtures
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.TeamKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real fixtures and whole-table comparisons pin standings identity and completeness. */
class StandingsAdapterTest {
    @Test
    fun untouchedFixtureSucceedsWith32Rows() {
        val result = NhlAdapter.parseStandings(fixture)

        assertTrue("The untouched fixture must parse: $result", result is ParseResult.Success)
        assertEquals(32, (result as ParseResult.Success).value.size)
    }

    @Test
    fun removingLastRowWithContiguousSequencesFailsExpectedSize() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val rows = root.getValue("standings").jsonArray.dropLast(1)
        assertEquals(
            (1..31).toList(),
            rows.map {
                it.jsonObject
                    .getValue("leagueSequence")
                    .jsonPrimitive.int
            },
        )
        val json = JsonObject(root + ("standings" to JsonArray(rows)))
        val result = NhlAdapter.parseStandings(json.toString())

        assertTrue("A table missing its last team must fail: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("expected 32"))
        assertTrue(reason, reason.contains("actual 31"))
        assertTrue(reason, reason.contains("league shape may have changed"))
    }

    @Test
    fun removingMiddleRowAndRenumberingFailsExpectedSize() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val rows =
            root
                .getValue("standings")
                .jsonArray
                .filterIndexed { index, _ -> index != 15 }
                .mapIndexed { index, row -> JsonObject(row.jsonObject + ("leagueSequence" to JsonPrimitive(index + 1))) }
        assertEquals((1..31).toList(), rows.map { it.getValue("leagueSequence").jsonPrimitive.int })
        val json = JsonObject(root + ("standings" to JsonArray(rows)))
        val result = NhlAdapter.parseStandings(json.toString())

        assertTrue("A short table with clean numbering must fail: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("expected 32"))
        assertTrue(reason, reason.contains("actual 31"))
        assertTrue(reason, reason.contains("league shape may have changed"))
    }

    @Test
    fun duplicateTeamAbbreviationFailsWithDuplicatedKey() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val duplicateAbbreviation =
            root
                .getValue("standings")
                .jsonArray[1]
                .jsonObject
                .getValue("teamAbbrev")
                .jsonObject
                .getValue("default")
        val json =
            mutateRow { row ->
                val teamAbbrev = row.getValue("teamAbbrev").jsonObject
                JsonObject(row + ("teamAbbrev" to JsonObject(teamAbbrev + ("default" to duplicateAbbreviation))))
            }
        val result = NhlAdapter.parseStandings(json)

        assertTrue("Duplicate team keys must fail even with clean numbering: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("duplicate team keys"))
        assertTrue(reason, reason.contains(duplicateAbbreviation.jsonPrimitive.content))
    }

    @Test
    fun missingStandingsKeyFails() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val result = NhlAdapter.parseStandings(JsonObject(root - "standings").toString())

        assertTrue("A missing standings key must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("missing standings field"))
    }

    @Test
    fun fabricatedExtraRowWithContiguousSequencesFailsWithDuplicatedKey() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val originalRows = root.getValue("standings").jsonArray
        val duplicateAbbreviation =
            originalRows
                .first()
                .jsonObject
                .getValue(
                    "teamAbbrev",
                ).jsonObject
                .getValue("default")
                .jsonPrimitive.content
        val rows =
            (originalRows + originalRows.first()).mapIndexed { index, row ->
                JsonObject(row.jsonObject + ("leagueSequence" to JsonPrimitive(index + 1)))
            }
        assertEquals((1..33).toList(), rows.map { it.getValue("leagueSequence").jsonPrimitive.int })
        val json = JsonObject(root + ("standings" to JsonArray(rows)))
        val result = NhlAdapter.parseStandings(json.toString())

        assertTrue("A fabricated extra row must fail: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("duplicate team keys"))
        assertTrue(reason, reason.contains(duplicateAbbreviation))
    }

    @Test
    fun removingAnEntireRowFailsTheWholeTable() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val rows = root.getValue("standings").jsonArray
        val json = JsonObject(root + ("standings" to JsonArray(rows.drop(1))))
        val result = NhlAdapter.parseStandings(json.toString())

        assertTrue("A table missing an entire valid row must fail: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("leagueSequence"))
        assertTrue(reason, reason.contains("missing=[${rows.first().jsonObject.getValue("leagueSequence").jsonPrimitive.int}]"))
    }

    @Test
    fun emptyTableFails() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val json = JsonObject(root + ("standings" to JsonArray(emptyList())))
        val result = NhlAdapter.parseStandings(json.toString())

        assertTrue("An empty league table must fail: $result", result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("empty"))
    }

    @Test
    fun duplicateLeagueSequenceFailsWithActionableReason() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val duplicateSequence =
            root
                .getValue("standings")
                .jsonArray[1]
                .jsonObject
                .getValue("leagueSequence")
        val json = mutateRow { JsonObject(it + ("leagueSequence" to duplicateSequence)) }
        val result = NhlAdapter.parseStandings(json)

        assertTrue("Duplicate league sequences must fail: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("leagueSequence"))
        assertTrue(reason, reason.contains("duplicates=[$duplicateSequence]"))
    }

    @Test
    fun completeTableHasEveryConferenceAndDivision() {
        assertEquals(32, baseline.size)
        assertEquals(mapOf(Conference.Eastern to 16, Conference.Western to 16), baseline.groupingBy { it.conference }.eachCount())
        assertEquals(
            mapOf(Division.Atlantic to 8, Division.Central to 8, Division.Metropolitan to 8, Division.Pacific to 8),
            baseline.groupingBy { it.division }.eachCount(),
        )
        assertEquals((1..32).toSet(), baseline.map { it.leagueSequence }.toSet())
    }

    @Test
    fun zeroWildcardSequenceKeepsDivisionQualifiersUnranked() {
        assertTrue(baseline.all { it.divisionSequence in 1..8 })
        assertTrue(baseline.all { it.wildcardSequence in 0..10 })
        assertEquals(12, baseline.count { it.wildcardRank == null })
        assertEquals(12, baseline.count { it.wildcardSequence == 0 })
    }

    @Test
    fun all24DistinctScoreTeamKeysArePresentInStandings() {
        val score = (NhlAdapter.parseScore(Fixtures.text("score-gameday.json")) as ParseResult.Success).value
        val scoreKeys = score.games.flatMap { listOf(it.home.key, it.away.key) }.toSet()
        val standingsKeys = baseline.map { it.team.key }.toSet()

        assertEquals("The score fixture contains 24 distinct team keys", 24, scoreKeys.size)
        assertTrue("Missing standings keys: ${scoreKeys - standingsKeys}", standingsKeys.containsAll(scoreKeys))
    }

    @Test
    fun missingOvertimeLossesKeepsEveryRowAndOnlyClearsThatValue() {
        val json = mutateRow { JsonObject(it - "otLosses") }
        val rows = (NhlAdapter.parseStandings(json) as ParseResult.Success).value

        assertNotNull(baseline.first().otl)
        assertEquals(32, rows.size)
        assertNull(rows.first().otl)
        assertEquals(listOf(baseline.first().copy(otl = null)) + baseline.drop(1), rows)
    }

    @Test
    fun missingTeamAbbreviationFailsTheWholeTableWithRowIndex() {
        val result = NhlAdapter.parseStandings(mutateRow { JsonObject(it - "teamAbbrev") })

        assertTrue("An incomplete table must fail: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("row 0"))
        assertTrue(reason, reason.contains("teamAbbrev.default"))
    }

    @Test
    fun blankTeamAbbreviationsFailWithRowIndexAndFieldWhileValidAbbreviationParses() {
        listOf("CAR", "", " ").forEach { abbreviation ->
            val json =
                mutateRow { row ->
                    val teamAbbrev = row.getValue("teamAbbrev").jsonObject
                    JsonObject(row + ("teamAbbrev" to JsonObject(teamAbbrev + ("default" to JsonPrimitive(abbreviation)))))
                }
            val result = NhlAdapter.parseStandings(json)

            if (abbreviation == "CAR") {
                assertTrue("A valid abbreviation must parse: $result", result is ParseResult.Success)
                val rows = (result as ParseResult.Success).value
                assertEquals(32, rows.size)
                assertEquals(TeamKey("CAR"), rows.first().team.key)
            } else {
                assertTrue("Blank abbreviation '$abbreviation' must fail the whole table: $result", result is ParseResult.Failure)
                val reason = (result as ParseResult.Failure).reason
                assertTrue(reason, reason.contains("row 0"))
                assertTrue(reason, reason.contains("teamAbbrev.default"))
            }
        }
    }

    @Test
    fun blankConferenceNameFailsWithRowIndexAndField() {
        val result = NhlAdapter.parseStandings(mutateRow { JsonObject(it + ("conferenceName" to JsonPrimitive(""))) })

        assertTrue("A blank conference name must fail the whole table: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("row 0"))
        assertTrue(reason, reason.contains("conferenceName"))
    }

    @Test
    fun blankDivisionNameFailsWithRowIndexAndField() {
        val result = NhlAdapter.parseStandings(mutateRow { JsonObject(it + ("divisionName" to JsonPrimitive(" "))) })

        assertTrue("A blank division name must fail the whole table: $result", result is ParseResult.Failure)
        val reason = (result as ParseResult.Failure).reason
        assertTrue(reason, reason.contains("row 0"))
        assertTrue(reason, reason.contains("divisionName"))
    }

    @Test
    fun unrecognisedNonBlankConferenceNameKeepsAll32Rows() {
        val result = NhlAdapter.parseStandings(mutateRow { JsonObject(it + ("conferenceName" to JsonPrimitive("Northern"))) })

        assertTrue("An unrecognised non-blank conference name must parse: $result", result is ParseResult.Success)
        val rows = (result as ParseResult.Success).value
        assertEquals(32, rows.size)
        assertEquals(Conference.Unknown("Northern"), rows.first().conference)
        assertEquals(listOf(baseline.first().copy(conference = Conference.Unknown("Northern"))) + baseline.drop(1), rows)
    }

    @Test
    fun missingRequiredFieldsFailWithTheirFieldAndRowIndex() {
        val fields =
            listOf(
                "gamesPlayed",
                "wins",
                "losses",
                "points",
                "leagueSequence",
                "conferenceSequence",
                "divisionSequence",
                "wildcardSequence",
                "conferenceName",
                "divisionName",
            )
        fields.forEach { field ->
            val result = NhlAdapter.parseStandings(mutateRow { JsonObject(it - field) })
            assertTrue("Missing $field must fail: $result", result is ParseResult.Failure)
            val reason = (result as ParseResult.Failure).reason
            assertTrue(reason, reason.contains("row 0"))
            assertTrue(reason, reason.contains(field))
        }
        val result = NhlAdapter.parseStandings(mutateRow { JsonObject(it + ("teamAbbrev" to JsonObject(emptyMap()))) })
        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("row 0 missing teamAbbrev.default"))
    }

    @Test
    fun explicitNullRowFailsWithItsIndex() {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val rows = root.getValue("standings").jsonArray
        val json = JsonObject(root + ("standings" to JsonArray(rows.mapIndexed { index, row -> if (index == 7) JsonNull else row })))
        val result = NhlAdapter.parseStandings(json.toString())

        assertTrue(result is ParseResult.Failure)
        assertTrue((result as ParseResult.Failure).reason.contains("row 7"))
    }

    @Test
    fun unknownGroupNamesKeepEveryRowAndPreserveRawNames() {
        val json =
            mutateRow {
                JsonObject(
                    it + mapOf("conferenceName" to JsonPrimitive("Future conference"), "divisionName" to JsonPrimitive("Future division")),
                )
            }
        val rows = (NhlAdapter.parseStandings(json) as ParseResult.Success).value
        val expected =
            baseline.first().copy(
                conference = Conference.Unknown("Future conference"),
                division = Division.Unknown("Future division"),
            )

        assertEquals(listOf(expected) + baseline.drop(1), rows)
    }

    @Test
    fun localizedIdentityAndBothLogoUrlsMapFromTheirWireFields() {
        val first =
            Json
                .parseToJsonElement(fixture)
                .jsonObject
                .getValue("standings")
                .jsonArray
                .first()
                .jsonObject
        val team = baseline.first().team

        assertEquals(
            TeamKey(
                first
                    .getValue("teamAbbrev")
                    .jsonObject
                    .getValue("default")
                    .jsonPrimitive.content,
            ),
            team.key,
        )
        assertEquals(
            first
                .getValue("teamName")
                .jsonObject
                .getValue("default")
                .jsonPrimitive.content,
            team.name,
        )
        assertEquals(first.getValue("teamLogo").jsonPrimitive.content, team.logoUrl)
        assertEquals(first.getValue("teamLogoDark").jsonPrimitive.content, team.darkLogoUrl)
    }

    @Test
    fun truncatedDocumentsReturnFailureForBothFunctionsWithoutThrowing() {
        listOf(fixture to "standings", seasonsFixture to "seasons").forEach { (input, label) ->
            listOf(input.dropLast(1), input.take(input.length * 40 / 100)).forEach { truncated ->
                val result = if (label == "standings") NhlAdapter.parseStandings(truncated) else NhlAdapter.parseSeasons(truncated)
                assertTrue("Truncated $label must fail: $result", result is ParseResult.Failure)
                assertTrue((result as ParseResult.Failure).reason.contains("$label decoding failed"))
            }
        }
    }

    @Test
    fun seasonsKeepAll109EntriesInAscendingFeedOrder() {
        val seasons = (NhlAdapter.parseSeasons(seasonsFixture) as ParseResult.Success).value
        val feedIds =
            Json.parseToJsonElement(seasonsFixture).jsonObject.getValue("seasons").jsonArray.map {
                it.jsonObject
                    .getValue("id")
                    .jsonPrimitive.int
            }

        assertEquals(109, seasons.size)
        assertEquals(feedIds, seasons.map { it.id })
        assertTrue(seasons.zipWithNext().all { (first, second) -> first.id < second.id })
    }

    @Test
    fun latestSeasonHasTheCapturedStandingsDates() {
        val latest = (NhlAdapter.parseSeasons(seasonsFixture) as ParseResult.Success).value.maxBy { it.id }

        assertNotNull(latest.start)
        assertNotNull(latest.end)
        assertEquals(20262027, latest.id)
        assertEquals("2026-09-29", latest.start)
        assertEquals("2026-10-25", latest.end)
    }

    private fun mutateRow(transform: (JsonObject) -> JsonObject): String {
        val root = Json.parseToJsonElement(fixture).jsonObject
        val rows = root.getValue("standings").jsonArray
        return JsonObject(root + ("standings" to JsonArray(listOf(transform(rows.first().jsonObject)) + rows.drop(1)))).toString()
    }

    private companion object {
        val fixture = Fixtures.text("standings.json")
        val seasonsFixture = Fixtures.text("standings-season.json")
        val baseline by lazy { (NhlAdapter.parseStandings(fixture) as ParseResult.Success).value }
    }
}
