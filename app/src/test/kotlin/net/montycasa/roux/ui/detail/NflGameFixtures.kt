package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.League
import net.montycasa.roux.domain.model.NflGameClub
import net.montycasa.roux.domain.model.NflGameDetail
import net.montycasa.roux.domain.model.NflGameStatus
import net.montycasa.roux.domain.model.NflLeader
import net.montycasa.roux.domain.model.NflLeaderCategory
import net.montycasa.roux.domain.model.NflScoringPlay
import net.montycasa.roux.domain.model.NflScoringQuarter
import net.montycasa.roux.domain.model.NflTeamLeaders
import net.montycasa.roux.domain.model.NflTeamStatComparison
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import kotlin.time.Instant

/**
 * Football game-page fixtures.
 *
 * The final is built from the shape of the captured DET 31 - NO 30 overtime summary. The live and scheduled values are
 * **crafted**, not captured: no NFL game was in progress or scheduled when the 25 November corpus was taken, so those
 * two states are synthetic and are labelled as such wherever they appear in a test.
 */
internal object NflGameFixtures {
    private val awayKey = TeamKey(League.NFL, "NO")
    private val homeKey = TeamKey(League.NFL, "DET")

    fun finalGame(): Game =
        Game(
            id = GameId(League.NFL, "401872923"),
            date = "2026-09-13",
            state = GameState.Final,
            away = TeamRef(awayKey, name = "New Orleans Saints"),
            home = TeamRef(homeKey, name = "Detroit Lions"),
            awayScore = 30,
            homeScore = 31,
            startTimeUtc = Instant.parse("2026-09-13T17:00:00Z"),
        )

    fun liveGame(): Game =
        finalGame().copy(
            id = GameId(League.NFL, "crafted-live"),
            state = GameState.Live(),
            away = TeamRef(TeamKey(League.NFL, "GB"), name = "Green Bay Packers"),
            home = TeamRef(TeamKey(League.NFL, "LAR"), name = "Los Angeles Rams"),
            awayScore = 10,
            homeScore = 17,
            startTimeUtc = Instant.parse("2026-11-20T01:15:00Z"),
        )

    fun scheduledGame(): Game =
        finalGame().copy(
            id = GameId(League.NFL, "crafted-scheduled"),
            state = GameState.Scheduled,
            away = TeamRef(TeamKey(League.NFL, "GB"), name = "Green Bay Packers"),
            home = TeamRef(TeamKey(League.NFL, "LAR"), name = "Los Angeles Rams"),
            awayScore = null,
            homeScore = null,
            startTimeUtc = Instant.parse("2026-11-20T01:15:00Z"),
        )

    fun finalDetail(): NflGameDetail =
        NflGameDetail(
            status = NflGameStatus.Final("Final/OT"),
            away = NflGameClub(TeamRef(awayKey, name = "New Orleans Saints"), score = 30, record = "0-1"),
            home = NflGameClub(TeamRef(homeKey, name = "Detroit Lions"), score = 31, record = "1-0"),
            scoring = scoring(),
            teamStats = teamStats(),
            leaders = leaders(),
        )

    /** Crafted live state, mirroring the adapter's synthetic live payload. */
    fun craftedLiveDetail(): NflGameDetail =
        NflGameDetail(
            status = NflGameStatus.Live("14:12 - 3rd Quarter", period = 3, clock = "14:12"),
            away = NflGameClub(TeamRef(TeamKey(League.NFL, "GB"), name = "Green Bay Packers"), score = 10),
            home = NflGameClub(TeamRef(TeamKey(League.NFL, "LAR"), name = "Los Angeles Rams"), score = 17),
            scoring =
                listOf(
                    NflScoringQuarter(
                        number = 3,
                        plays =
                            listOf(
                                NflScoringPlay(
                                    clock = "14:12",
                                    teamAbbrev = "LAR",
                                    isHome = true,
                                    text = "Kyren Williams 7 Yd Rush (Brandon McManus Kick)",
                                    awayScore = 10,
                                    homeScore = 17,
                                ),
                            ),
                    ),
                ),
        )

    /** Crafted scheduled state, mirroring the adapter's synthetic pre-kickoff payload. */
    fun craftedScheduledDetail(): NflGameDetail =
        NflGameDetail(
            status = NflGameStatus.Scheduled("Thu, November 19th at 8:15 PM EST", startTimeUtc = Instant.parse("2026-11-20T01:15:00Z")),
            away = NflGameClub(TeamRef(TeamKey(League.NFL, "GB"), name = "Green Bay Packers")),
            home = NflGameClub(TeamRef(TeamKey(League.NFL, "LAR"), name = "Los Angeles Rams")),
        )

    private fun scoring(): List<NflScoringQuarter> =
        listOf(
            NflScoringQuarter(
                number = 1,
                plays =
                    listOf(
                        NflScoringPlay(
                            "3:39",
                            "DET",
                            isHome = true,
                            text = "Jahmyr Gibbs 1 Yd Rush (Jake Bates Kick)",
                            awayScore = 0,
                            homeScore = 7,
                        ),
                    ),
            ),
            NflScoringQuarter(
                number = 3,
                plays =
                    listOf(
                        NflScoringPlay(
                            "11:14",
                            "DET",
                            isHome = true,
                            text = "Jahmyr Gibbs 1 Yd Rush (Jake Bates Kick)",
                            awayScore = 0,
                            homeScore = 14,
                        ),
                        NflScoringPlay(
                            "4:27",
                            "NO",
                            isHome = false,
                            text = "Kendre Miller 8 Yd Rush (Daniel Carlson Kick)",
                            awayScore = 7,
                            homeScore = 21,
                        ),
                    ),
            ),
            NflScoringQuarter(
                number = 4,
                plays =
                    listOf(
                        NflScoringPlay(
                            "7:41",
                            "NO",
                            isHome = false,
                            text = "Daniel Carlson 28 Yd Field Goal",
                            awayScore = 17,
                            homeScore = 21,
                        ),
                    ),
            ),
            NflScoringQuarter(
                number = 5,
                plays =
                    listOf(
                        NflScoringPlay(
                            "5:04",
                            "DET",
                            isHome = true,
                            text = "Amon-Ra St. Brown 4 Yd pass from Jared Goff (Jake Bates Kick)",
                            awayScore = 24,
                            homeScore = 31,
                        ),
                    ),
            ),
        )

    private fun teamStats(): List<NflTeamStatComparison> =
        listOf(
            NflTeamStatComparison("firstDowns", away = "29", home = "26"),
            NflTeamStatComparison("totalYards", away = "469", home = "369"),
            NflTeamStatComparison("netPassingYards", away = "378", home = "204"),
            NflTeamStatComparison("rushingYards", away = "91", home = "165"),
            NflTeamStatComparison("turnovers", away = "3", home = "1"),
            NflTeamStatComparison("thirdDownEff", away = "8-17", home = "5-13"),
            NflTeamStatComparison("possessionTime", away = "31:35", home = "36:51"),
        )

    private fun leaders(): List<NflTeamLeaders> =
        listOf(
            NflTeamLeaders(
                teamAbbrev = "DET",
                categories =
                    listOf(
                        category("passingYards", "Passing Yards", "Jared Goff", "26/39, 206 YDS, 2 TD"),
                        category("rushingYards", "Rushing Yards", "Jahmyr Gibbs", "29 CAR, 156 YDS, 2 TD"),
                        category("receivingYards", "Receiving Yards", "Amon-Ra St. Brown", "10 REC, 67 YDS, 2 TD"),
                        category("sacks", "Sacks", "Aidan Hutchinson", "2"),
                        category("totalTackles", "Tackles", "D.J. Reed", "10"),
                    ),
            ),
            NflTeamLeaders(
                teamAbbrev = "NO",
                categories =
                    listOf(
                        category("passingYards", "Passing Yards", "Tyler Shough", "35/56, 410 YDS, 3 TD, 2 INT"),
                        category("rushingYards", "Rushing Yards", "Travis Etienne Jr.", "9 CAR, 46 YDS"),
                        category("receivingYards", "Receiving Yards", "Chris Olave", "10 REC, 182 YDS"),
                        category("sacks", "Sacks", "Carl Granderson", "1"),
                        category("totalTackles", "Tackles", "Jonas Sanker", "10"),
                    ),
            ),
        )

    private fun category(
        name: String,
        displayName: String,
        athlete: String,
        value: String,
    ): NflLeaderCategory =
        NflLeaderCategory(
            name = name,
            displayName = displayName,
            leaders = listOf(NflLeader(athlete, value)),
        )
}
