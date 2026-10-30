package net.montycasa.roux.data.nhl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import net.montycasa.roux.data.nhl.dto.PeriodShotsDto
import net.montycasa.roux.data.nhl.dto.RightRailDto
import net.montycasa.roux.data.nhl.dto.TeamStatRowDto
import net.montycasa.roux.domain.model.GameRightRail
import net.montycasa.roux.domain.model.PowerPlaySummary
import net.montycasa.roux.domain.model.ShotTotals
import net.montycasa.roux.domain.model.TeamStat

/**
 * Turns the right rail into the two sections it owns: shots and power play.
 *
 * Both are stated twice in the payload and neither statement is guaranteed to agree with the other. Shots appear as a
 * `sog` row in the comparison table and again as a per-period array; power play appears as `"0/1"` and again as
 * `powerPlayPctg`. The table is the one the page shows, so it decides - and the per-period array is only a fallback for
 * a payload that drops the row, because a sum of periods and a printed total can disagree by a shot and the page has no
 * way to explain the difference.
 */
internal object RightRailAdapter {
    private val feedJson =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private const val SOG = "sog"
    private const val POWER_PLAY = "powerPlay"

    fun parse(json: String): ParseResult<GameRightRail> {
        val rail =
            try {
                feedJson.decodeFromString<RightRailDto>(json)
            } catch (exception: SerializationException) {
                return ParseResult.Failure("NHL right rail decoding failed: ${exception.message}")
            } catch (exception: IllegalArgumentException) {
                return ParseResult.Failure("NHL right rail decoding failed: ${exception.message}")
            }

        val rows = rail.teamGameStats.orEmpty().mapNotNull { it }
        return ParseResult.Success(
            GameRightRail(
                shots = rows.shots(rail.shotsByPeriod.orEmpty().mapNotNull { it }),
                powerPlay = rows.powerPlay(),
                // Every remaining row is passed through in the notation the league used, so the page can decide how to
                // draw it. Dropping the rows the page has no typed field for is how eight of ten stats went missing.
                teamStats =
                    rows.mapNotNull { row ->
                        val category = row.category?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        val away = row.awayValue.asText() ?: return@mapNotNull null
                        val home = row.homeValue.asText() ?: return@mapNotNull null
                        TeamStat(category, away, home)
                    },
            ),
        )
    }

    private fun List<TeamStatRowDto>.row(category: String): TeamStatRowDto? = firstOrNull { it.category == category }

    private fun List<TeamStatRowDto>.shots(periods: List<PeriodShotsDto>): ShotTotals? {
        row(SOG)?.let { sog ->
            val away = sog.awayValue.asInt()
            val home = sog.homeValue.asInt()
            if (away != null && home != null) return ShotTotals(away, home)
        }
        // A partial or missing sog row falls back to counting the periods, but only if there is something to count.
        if (periods.isEmpty()) return null
        val away = periods.sumOf { it.away ?: 0 }
        val home = periods.sumOf { it.home ?: 0 }
        return ShotTotals(away, home)
    }

    /**
     * `"0/1"` is goals over chances, and both sides have to parse: half a power-play line is not a statistic the page
     * can print, so it is dropped rather than shown with a zero invented for the missing half.
     */
    private fun List<TeamStatRowDto>.powerPlay(): PowerPlaySummary? {
        val row = row(POWER_PLAY) ?: return null
        val away = row.awayValue.asFraction() ?: return null
        val home = row.homeValue.asFraction() ?: return null
        return PowerPlaySummary(
            awayGoals = away.first,
            awayChances = away.second,
            homeGoals = home.first,
            homeChances = home.second,
        )
    }

    /** The value exactly as the feed wrote it: a percentage stays `0.56`, a fraction stays `2/5`. */
    private fun JsonElement?.asText(): String? =
        when (val element = this) {
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            else -> null
        }

    /** Accepts a JSON number or a numeric string; anything else is absent. */
    private fun JsonElement?.asInt(): Int? =
        when (val element = this) {
            is JsonPrimitive -> element.contentOrNull?.trim()?.toIntOrNull()
            else -> null
        }

    /** `"2/5"` to `2 to 5`. Rejects anything that is not two non-negative integers. */
    private fun JsonElement?.asFraction(): Pair<Int, Int>? {
        val text = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        val parts = text.split('/')
        if (parts.size != 2) return null
        val goals = parts[0].toIntOrNull() ?: return null
        val chances = parts[1].toIntOrNull() ?: return null
        if (goals < 0 || chances < 0) return null
        return goals to chances
    }
}
