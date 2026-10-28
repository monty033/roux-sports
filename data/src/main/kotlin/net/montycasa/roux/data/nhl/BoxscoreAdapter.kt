package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.montycasa.roux.data.nhl.dto.BoxscoreDto
import net.montycasa.roux.data.nhl.dto.BoxscoreGoalieDto
import net.montycasa.roux.data.nhl.dto.BoxscoreTeamPlayersDto
import net.montycasa.roux.domain.model.GameBoxscore
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PlayerRef

/**
 * Turns the boxscore payload into the one section it owns: the goalie lines.
 *
 * The boxscore carries the full player table for both teams - skaters, goalies, ice time, faceoff splits - and the page
 * shows none of it except the goalies. That is worth stating because it is the natural place to look for scoring, and it
 * has no scoring at all.
 */
internal object BoxscoreAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun parse(json: String): ParseResult<GameBoxscore> {
        val boxscore =
            try {
                feedJson.decodeFromString<BoxscoreDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL boxscore decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL boxscore decoding failed: ${exception.message}")
            }

        boxscore.id ?: return ParseResult.Failure("NHL boxscore mapping failed: missing id")

        // A game that has not started omits playerByGameStats entirely. That is the ordinary pre-game state, so it
        // yields no goalie lines and no section - not a failure the page has to apologise for.
        val players = boxscore.playerByGameStats ?: return ParseResult.Success(GameBoxscore())

        val goalies =
            players.awayTeam.goalieLines(boxscore.awayTeam?.abbrev) +
                players.homeTeam.goalieLines(boxscore.homeTeam?.abbrev)

        return ParseResult.Success(GameBoxscore(goalies = goalies))
    }

    /**
     * The goalies who played, away side first.
     *
     * A boxscore lists both dressed goaltenders per team, and the backup appears with a `00:00` line and no decision -
     * so the whole list is not the answer. `starter` decides it. When a payload omits that flag, ice time is the
     * fallback: a goalie who did not play has none.
     */
    private fun BoxscoreTeamPlayersDto?.goalieLines(abbrev: String?): List<GoalieLine> {
        val dressed = this?.goalies.orEmpty().mapNotNull { it }
        val starters = dressed.filter { it.starter == true }
        val played = starters.ifEmpty { dressed.filter { it.playedOn() } }
        return played.distinctBy { it.playerId }.mapNotNull { it.toLine(abbrev) }
    }

    private fun BoxscoreGoalieDto.playedOn(): Boolean {
        val timeOnIce = toi?.trim().orEmpty()
        return timeOnIce.isNotEmpty() && timeOnIce.any { it.isDigit() } && timeOnIce.toSeconds() > 0
    }

    /** "62:22" or "00:00"; anything unparseable counts as no time, which keeps a backup out of the section. */
    private fun String.toSeconds(): Long {
        val parts = split(':')
        if (parts.size != 2) return 0
        val minutes = parts[0].toLongOrNull() ?: return 0
        val seconds = parts[1].toLongOrNull() ?: return 0
        return minutes * 60 + seconds
    }

    private fun BoxscoreGoalieDto.toLine(abbrev: String?): GoalieLine? {
        val goalieName = name?.default?.takeIf { it.isNotBlank() } ?: return null
        return GoalieLine(
            goalie = PlayerRef(name = goalieName, teamAbbrev = abbrev.orEmpty()),
            saves = saves ?: 0,
            shotsAgainst = shotsAgainst ?: 0,
        )
    }
}
