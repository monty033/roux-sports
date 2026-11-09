package net.montycasa.roux.ui

import kotlinx.datetime.LocalDate
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.ClubGame
import net.montycasa.roux.domain.model.ClubRoster
import net.montycasa.roux.domain.model.ClubSchedule
import net.montycasa.roux.domain.model.ClubStats
import net.montycasa.roux.domain.model.Conference
import net.montycasa.roux.domain.model.Division
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.GoalieStat
import net.montycasa.roux.domain.model.LeagueClub
import net.montycasa.roux.domain.model.LeagueSummary
import net.montycasa.roux.domain.model.PlayerCareer
import net.montycasa.roux.domain.model.PlayerDraft
import net.montycasa.roux.domain.model.PlayerGame
import net.montycasa.roux.domain.model.PlayerProfile
import net.montycasa.roux.domain.model.PlayerSeasonLine
import net.montycasa.roux.domain.model.PlayerTotals
import net.montycasa.roux.domain.model.RosterPlayer
import net.montycasa.roux.domain.model.SkaterStat
import net.montycasa.roux.domain.model.StandingRow
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.player.PlayerDetailUiState
import net.montycasa.roux.ui.team.TeamDetailUiState
import kotlin.time.Instant

/**
 * Domain-built fixtures for the two newest pages, shared by the audit matrix, the reach matrix, the tab
 * screenshots and the TalkBack tests so all four describe the same two pages.
 *
 * Every image URL is deliberately null. A real crest or headshot URL pulls Coil into the JVM and changes what other
 * screenshot goldens capture later in the same worker; the pages draw their fallbacks instead.
 */
internal object DetailPageFixtures {
    const val CURRENT_SEASON = 20262027

    /** The date the player page counts the Info tab's age to. */
    val TODAY = LocalDate(2026, 11, 5)

    /** A fixed clock for the club schedule, so the "next" marker does not move with the wall clock. */
    val NOW = Instant.parse("2026-11-05T17:00:00Z")

    const val NEWEST_GAME_ID = 2026020201L

    // ----- Player ----------------------------------------------------------------------------------------------------

    private fun totals(
        gamesPlayed: Int,
        goals: Int,
        assists: Int,
        plusMinus: Int,
        penaltyMinutes: Int,
        shots: Int,
        powerPlayGoals: Int,
        shorthandedGoals: Int,
        faceoffWinningPctg: Double,
        avgTimeOnIceSeconds: Long,
    ) = PlayerTotals.Skater(
        gamesPlayed = gamesPlayed,
        goals = goals,
        assists = assists,
        points = goals + assists,
        plusMinus = plusMinus,
        penaltyMinutes = penaltyMinutes,
        powerPlayGoals = powerPlayGoals,
        powerPlayPoints = powerPlayGoals,
        shorthandedGoals = shorthandedGoals,
        shorthandedPoints = shorthandedGoals,
        gameWinningGoals = 1,
        overtimeGoals = 0,
        shots = shots,
        shootingPctg = goals.toDouble() / shots,
        faceoffWinningPctg = faceoffWinningPctg,
        avgTimeOnIceSeconds = avgTimeOnIceSeconds,
    )

    private fun season(
        season: Int,
        team: String,
        abbreviation: String,
        sequence: Int,
        totals: PlayerTotals.Skater,
        gameType: Int = 2,
    ) = PlayerSeasonLine(
        season = season,
        gameTypeId = gameType,
        leagueAbbrev = "NHL",
        teamName = team,
        sequence = sequence,
        totals = totals,
        teamAbbrev = abbreviation,
    )

    private val careerSeasons =
        listOf(
            season(
                20242025,
                "Carolina Hurricanes",
                "CAR",
                1,
                totals(
                    gamesPlayed = 60,
                    goals = 10,
                    assists = 15,
                    plusMinus = 2,
                    penaltyMinutes = 18,
                    shots = 100,
                    powerPlayGoals = 3,
                    shorthandedGoals = 0,
                    faceoffWinningPctg = 0.48,
                    avgTimeOnIceSeconds = 660,
                ),
            ),
            // The trade: one season, two clubs, one row each.
            season(
                20252026,
                "Carolina Hurricanes",
                "CAR",
                1,
                totals(
                    gamesPlayed = 41,
                    goals = 8,
                    assists = 13,
                    plusMinus = 4,
                    penaltyMinutes = 12,
                    shots = 76,
                    powerPlayGoals = 2,
                    shorthandedGoals = 1,
                    faceoffWinningPctg = 0.51,
                    avgTimeOnIceSeconds = 702,
                ),
            ),
            season(
                20252026,
                "New Jersey Devils",
                "NJD",
                2,
                totals(
                    gamesPlayed = 29,
                    goals = 4,
                    assists = 7,
                    plusMinus = -2,
                    penaltyMinutes = 8,
                    shots = 44,
                    powerPlayGoals = 1,
                    shorthandedGoals = 0,
                    faceoffWinningPctg = 0.49,
                    avgTimeOnIceSeconds = 688,
                ),
            ),
            // The playoff row gets its own card at the foot of Career.
            season(
                20252026,
                "New Jersey Devils",
                "NJD",
                1,
                totals(
                    gamesPlayed = 9,
                    goals = 2,
                    assists = 3,
                    plusMinus = 1,
                    penaltyMinutes = 4,
                    shots = 16,
                    powerPlayGoals = 1,
                    shorthandedGoals = 0,
                    faceoffWinningPctg = 0.47,
                    avgTimeOnIceSeconds = 711,
                ),
                gameType = 3,
            ),
            season(
                CURRENT_SEASON,
                "Carolina Hurricanes",
                "CAR",
                1,
                totals(
                    gamesPlayed = 13,
                    goals = 2,
                    assists = 3,
                    plusMinus = 1,
                    penaltyMinutes = 6,
                    shots = 30,
                    powerPlayGoals = 1,
                    shorthandedGoals = 0,
                    faceoffWinningPctg = 0.52,
                    avgTimeOnIceSeconds = 672,
                ),
            ),
        )

    val playerProfile =
        PlayerProfile(
            playerId = 8480037L,
            firstName = "Aleksi",
            lastName = "Heponiemi",
            position = "C",
            sweaterNumber = 20,
            headshotUrl = null,
            heightInches = 70,
            weightPounds = 155,
            birthDate = "1999-01-09",
            birthCity = "Tampere",
            birthCountry = "FIN",
            shoots = "L",
            draft = PlayerDraft(year = 2017, teamAbbrev = "FLA", round = 2, pickInRound = 9, overallPick = 40),
            career =
                PlayerCareer(
                    regularSeason =
                        totals(
                            gamesPlayed = 143,
                            goals = 24,
                            assists = 38,
                            plusMinus = 5,
                            penaltyMinutes = 44,
                            shots = 250,
                            powerPlayGoals = 7,
                            shorthandedGoals = 1,
                            faceoffWinningPctg = 0.50,
                            avgTimeOnIceSeconds = 682,
                        ),
                    playoffs =
                        totals(
                            gamesPlayed = 9,
                            goals = 2,
                            assists = 3,
                            plusMinus = 1,
                            penaltyMinutes = 4,
                            shots = 16,
                            powerPlayGoals = 1,
                            shorthandedGoals = 0,
                            faceoffWinningPctg = 0.47,
                            avgTimeOnIceSeconds = 711,
                        ),
                ),
            seasons = careerSeasons,
        )

    val playerGameLog =
        listOf(
            PlayerGame.Skater(
                gameId = NEWEST_GAME_ID,
                gameDate = "2026-11-01",
                opponentAbbrev = "OTT",
                isHome = true,
                teamAbbrev = "CAR",
                goals = 1,
                assists = 2,
                points = 3,
                plusMinus = 1,
                shots = 4,
                timeOnIceSeconds = 754,
            ),
            PlayerGame.Skater(
                gameId = 2026020198L,
                gameDate = "2026-10-30",
                opponentAbbrev = "BOS",
                isHome = false,
                teamAbbrev = "CAR",
                goals = 0,
                assists = 1,
                points = 1,
                plusMinus = -1,
                shots = 2,
                timeOnIceSeconds = 688,
            ),
            PlayerGame.Skater(
                gameId = 2026020186L,
                gameDate = "2026-10-28",
                opponentAbbrev = "MTL",
                isHome = true,
                teamAbbrev = "CAR",
                goals = 1,
                assists = 1,
                points = 2,
                plusMinus = 2,
                shots = 3,
                timeOnIceSeconds = 701,
            ),
            PlayerGame.Skater(
                gameId = 2026020171L,
                gameDate = "2026-10-25",
                opponentAbbrev = "NYR",
                isHome = false,
                teamAbbrev = "CAR",
                goals = 0,
                assists = 0,
                points = 0,
                plusMinus = 0,
                shots = 1,
                timeOnIceSeconds = 640,
            ),
        )

    val playerState =
        PlayerDetailUiState(
            isLoading = false,
            profile = Loadable(playerProfile, Freshness.Fresh, null),
            gameLog = Loadable(playerGameLog, Freshness.Fresh, null),
        )

    // ----- Club ------------------------------------------------------------------------------------------------------

    private val mapleLeafs = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs")

    private val clubRow =
        StandingRow(
            team = mapleLeafs,
            gp = 12,
            w = 9,
            l = 2,
            otl = 1,
            pts = 19,
            leagueSequence = 4,
            conferenceSequence = 2,
            divisionSequence = 1,
            wildcardSequence = 0,
            conference = Conference.Eastern,
            division = Division.Atlantic,
        )

    private fun game(
        id: String,
        date: String,
        opponent: TeamRef,
        home: Boolean,
        state: GameState,
        homeScore: Int? = null,
        awayScore: Int? = null,
    ): ClubGame {
        val toronto = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs")
        return ClubGame(
            id = GameId(id),
            date = date,
            startTimeUtc = Instant.parse("${date}T23:00:00Z"),
            venue = "Scotiabank Arena",
            home = if (home) toronto else opponent,
            away = if (home) opponent else toronto,
            state = state,
            finished = state == GameState.Final,
            homeScore = homeScore,
            awayScore = awayScore,
        )
    }

    val clubSchedule =
        listOf(
            game(
                "2026020101",
                "2026-10-12",
                TeamRef(TeamKey("MTL"), name = "Montréal Canadiens", shortName = "Canadiens"),
                home = true,
                state = GameState.Final,
                homeScore = 4,
                awayScore = 2,
            ),
            game(
                "2026020114",
                "2026-10-15",
                TeamRef(TeamKey("BOS"), name = "Boston Bruins", shortName = "Bruins"),
                home = false,
                state = GameState.Final,
                homeScore = 3,
                awayScore = 1,
            ),
            game(
                "2026020138",
                "2026-10-20",
                TeamRef(TeamKey("OTT"), name = "Ottawa Senators", shortName = "Senators"),
                home = true,
                state = GameState.Final,
                homeScore = 2,
                awayScore = 5,
            ),
            game(
                "2026020162",
                "2026-10-28",
                TeamRef(TeamKey("NYR"), name = "New York Rangers", shortName = "Rangers"),
                home = false,
                state = GameState.Final,
                homeScore = 3,
                awayScore = 2,
            ),
            game(
                "2026020189",
                "2026-11-02",
                TeamRef(TeamKey("TBL"), name = "Tampa Bay Lightning", shortName = "Lightning"),
                home = true,
                state = GameState.Final,
                homeScore = 5,
                awayScore = 4,
            ),
            game(
                "2026020214",
                "2026-11-07",
                TeamRef(TeamKey("DET"), name = "Detroit Red Wings", shortName = "Red Wings"),
                home = false,
                state = GameState.Scheduled,
            ),
        )

    val clubRoster =
        ClubRoster(
            forwards =
                listOf(
                    RosterPlayer(id = 8479318L, name = "Auston Matthews", sweaterNumber = 34, positionCode = "C"),
                    RosterPlayer(id = 8478402L, name = "Mitchell Marner", sweaterNumber = 16, positionCode = "R"),
                    RosterPlayer(id = 8477939L, name = "William Nylander", sweaterNumber = 88, positionCode = "R"),
                    RosterPlayer(id = 8478483L, name = "John Tavares", sweaterNumber = 91, positionCode = "C"),
                ),
            defense =
                listOf(
                    RosterPlayer(id = 8476853L, name = "Morgan Rielly", sweaterNumber = 44, positionCode = "D"),
                    RosterPlayer(id = 8481624L, name = "Jake McCabe", sweaterNumber = 22, positionCode = "D"),
                    RosterPlayer(id = 8479041L, name = "Chris Tanev", sweaterNumber = 8, positionCode = "D"),
                ),
            goalies =
                listOf(
                    RosterPlayer(id = 8479361L, name = "Joseph Woll", sweaterNumber = 60, positionCode = "G"),
                    RosterPlayer(id = 8480313L, name = "Anthony Stolarz", sweaterNumber = 41, positionCode = "G"),
                ),
        )

    val clubStats =
        ClubStats(
            season = CLUB_SEASON,
            skaters =
                listOf(
                    SkaterStat(
                        playerId = 8479318L,
                        name = "Auston Matthews",
                        positionCode = "C",
                        gamesPlayed = 12,
                        goals = 10,
                        assists = 9,
                        points = 19,
                        plusMinus = 7,
                        penaltyMinutes = 4,
                        shots = 44,
                        shootingPctg = 0.227,
                        powerPlayGoals = 4,
                        shorthandedGoals = 0,
                        gameWinningGoals = 2,
                        overtimeGoals = 1,
                        faceoffWinPctg = 0.54,
                        avgTimeOnIcePerGame = 1_188.0,
                        avgShiftsPerGame = 21.4,
                    ),
                    SkaterStat(
                        playerId = 8477939L,
                        name = "William Nylander",
                        positionCode = "R",
                        gamesPlayed = 12,
                        goals = 8,
                        assists = 11,
                        points = 19,
                        plusMinus = 5,
                        penaltyMinutes = 2,
                        shots = 39,
                        shootingPctg = 0.205,
                        powerPlayGoals = 3,
                        shorthandedGoals = 0,
                        gameWinningGoals = 1,
                        overtimeGoals = 0,
                        faceoffWinPctg = 0.45,
                        avgTimeOnIcePerGame = 1_152.0,
                        avgShiftsPerGame = 20.1,
                    ),
                ),
            goalies =
                listOf(
                    GoalieStat(
                        playerId = 8479361L,
                        name = "Joseph Woll",
                        gamesPlayed = 8,
                        gamesStarted = 8,
                        wins = 6,
                        losses = 1,
                        overtimeLosses = 1,
                        goalsAgainst = 18,
                        goalsAgainstAverage = 2.25,
                        shotsAgainst = 231,
                        saves = 213,
                        savePercentage = 0.922,
                        shutouts = 1,
                        timeOnIce = 28_800,
                    ),
                ),
        )

    private val leagueClubNames =
        listOf(
            "Toronto Maple Leafs",
            "Boston Bruins",
            "Montréal Canadiens",
            "Ottawa Senators",
            "Tampa Bay Lightning",
            "Detroit Red Wings",
            "New York Rangers",
            "Buffalo Sabres",
            "Carolina Hurricanes",
            "New Jersey Devils",
            "Philadelphia Flyers",
            "Pittsburgh Penguins",
            "Washington Capitals",
            "New York Islanders",
            "Columbus Blue Jackets",
            "Florida Panthers",
            "Colorado Avalanche",
            "Dallas Stars",
            "Minnesota Wild",
            "Nashville Predators",
            "St. Louis Blues",
            "Winnipeg Jets",
            "Chicago Blackhawks",
            "Utah Mammoth",
            "Vegas Golden Knights",
            "Los Angeles Kings",
            "San Jose Sharks",
            "Seattle Kraken",
            "Calgary Flames",
            "Edmonton Oilers",
            "Vancouver Canucks",
            "Anaheim Ducks",
        )

    val leagueSummary =
        LeagueSummary(
            season = CLUB_SEASON,
            clubs =
                leagueClubNames.mapIndexed { index, name ->
                    LeagueClub(
                        teamName = name,
                        teamId = index + 1,
                        gamesPlayed = 12,
                        wins = 9 - index / 4,
                        losses = index / 4,
                        otLosses = index % 3,
                        ties = 0,
                        points = 19 - index / 3,
                        pointPct = 0.62 - index * 0.008,
                        goalsForPerGame = 3.5 - index * 0.04,
                        goalsAgainstPerGame = 2.2 + index * 0.05,
                        shotsForPerGame = 32.4 - index * 0.18,
                        shotsAgainstPerGame = 27.8 + index * 0.16,
                        faceoffWinPct = 0.54 - index * 0.003,
                        powerPlayPct = 0.27 - index * 0.004,
                        penaltyKillPct = 0.84 - index * 0.003,
                        teamShutouts = index % 4,
                        penaltyMinutes = 80 + index,
                    )
                },
        )

    val teamState =
        TeamDetailUiState(
            teamKey = TeamKey("TOR"),
            row = clubRow,
            isLoading = false,
            schedule = clubSeason(clubSchedule),
            roster = Loadable(clubRoster, Freshness.Fresh, null),
            stats = Loadable(clubStats, Freshness.Fresh, null),
            leagueStats = Loadable(leagueSummary, Freshness.Fresh, null),
        )

    /**
     * The club page on a pre-season day: the schedule and totals are last season's, and each one says so. The games are
     * the same captured fixtures as [teamState], so only the season label differs.
     */
    val teamStateLastSeason =
        teamState.copy(
            schedule = Loadable(ClubSchedule(season = LAST_SEASON, games = clubSchedule), Freshness.Fresh, null),
            stats = Loadable(clubStats.copy(season = LAST_SEASON), Freshness.Fresh, null),
            leagueStats = Loadable(leagueSummary.copy(season = LAST_SEASON), Freshness.Fresh, null),
        )

    /**
     * The reach audit's club state: the same page with a two-game schedule. The full season fills the viewport at
     * doubled type, so its last row would sit 4 dp from the tab row - a finding about a dense list, not about the
     * tab row and back control this audit declares. Two games across two months keep the list short while still
     * rendering the Schedule tab's own rows.
     */
    val teamAuditState: TeamDetailUiState
        get() = teamState.copy(schedule = clubSeason(listOf(clubSchedule[3], clubSchedule[5])))

    /** The fixtures' club is the 2026-27 season, the one the page asks for on the fixed clock. */
    const val CLUB_SEASON = "20262027"

    /** The season before [CLUB_SEASON], which the page falls back to while the current one has no games yet. */
    const val LAST_SEASON = "20252026"

    private fun clubSeason(games: List<ClubGame>) = Loadable(ClubSchedule(season = CLUB_SEASON, games = games), Freshness.Fresh, null)
}
