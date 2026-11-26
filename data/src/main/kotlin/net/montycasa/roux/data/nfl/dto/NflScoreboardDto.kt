package net.montycasa.roux.data.nfl.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's NFL scoreboard. Only the fields the scores screen reads are described; the feed carries much more, and
 * `ignoreUnknownKeys` lets the rest through.
 */
@Serializable
data class NflScoreboardDto(
    val events: List<NflEventDto>? = null,
    val week: NflWeekDto? = null,
    val season: NflSeasonDto? = null,
    val leagues: List<NflLeagueDto>? = null,
)

/** The week number the scoreboard reports. */
@Serializable
data class NflWeekDto(
    val number: Int? = null,
)

/** The season type: 1 preseason, 2 regular season, 3 postseason. */
@Serializable
data class NflSeasonDto(
    val year: Int? = null,
    val type: Int? = null,
)

/** The league block, kept so a scoreboard that is not a league scoreboard is recognisable. */
@Serializable
data class NflLeagueDto(
    val id: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
)

/** One game on the scoreboard. */
@Serializable
data class NflEventDto(
    val id: String? = null,
    val date: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val competitions: List<NflCompetitionDto>? = null,
)

/** One game within an event. The scoreboard carries exactly one, but the feed models it as a list. */
@Serializable
data class NflCompetitionDto(
    val id: String? = null,
    val competitors: List<NflCompetitorDto>? = null,
    val status: NflStatusDto? = null,
    val situation: NflSituationDto? = null,
    val broadcasts: List<NflBroadcastDto>? = null,
    val notes: List<NflNoteDto>? = null,
    val linescores: List<NflLinescoreDto>? = null,
)

/** The game's status. `status.type.name` is the only discriminator the census found to be safe. */
@Serializable
data class NflStatusDto(
    val type: NflStatusTypeDto? = null,
    val period: Int? = null,
    val displayClock: String? = null,
)

/** The status type: its `name` is what the census observed across every state. */
@Serializable
data class NflStatusTypeDto(
    val name: String? = null,
    val state: String? = null,
    val completed: Boolean? = null,
    val detail: String? = null,
    val description: String? = null,
)

/** One side of a game. */
@Serializable
data class NflCompetitorDto(
    val id: String? = null,
    val homeAway: String? = null,
    val score: String? = null,
    val winner: Boolean? = null,
    val team: NflTeamDto? = null,
    /**
     * The club's records: an overall row (`type == "total"`) plus home or road splits. Present in the summary payload;
     * the scoreboard's competitor may omit it. The overall row is the line the game hero prints.
     */
    val record: List<NflRecordDto>? = null,
)

/** One record line. [displayValue] and [summary] are the same text in the captures, e.g. `4-6`. */
@Serializable
data class NflRecordDto(
    val type: String? = null,
    val summary: String? = null,
    val displayValue: String? = null,
)

/** The team on one side of a game. */
@Serializable
data class NflTeamDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val name: String? = null,
    val location: String? = null,
    val logo: String? = null,
    val logos: List<NflLogoDto>? = null,
)

/** One logo variant. The census found every NFL logo served from `a.espncdn.com`. */
@Serializable
data class NflLogoDto(
    val href: String? = null,
    val rel: List<String>? = null,
    val width: Int? = null,
    val height: Int? = null,
)

/**
 * The live situation. The census found it only on an in-progress event: `possession` is the team id as a string,
 * and `lastPlay` describes the last play rather than necessarily the current possession.
 */
@Serializable
data class NflSituationDto(
    val down: Int? = null,
    val distance: Int? = null,
    val yardLine: Int? = null,
    val possession: String? = null,
    val isRedZone: Boolean? = null,
    val lastPlay: NflLastPlayDto? = null,
)

/** The last play. The census found no `down`, `distance` or `yardLine` key on it. */
@Serializable
data class NflLastPlayDto(
    val id: String? = null,
    val text: String? = null,
    val team: NflTeamDto? = null,
)

/** A game note, such as the overtime marker. */
@Serializable
data class NflNoteDto(
    val headline: String? = null,
    val type: String? = null,
)

/** A broadcast listing. The census found these on the competition, not on the event. */
@Serializable
data class NflBroadcastDto(
    val type: NflBroadcastTypeDto? = null,
    /** The scope, as a plain string: the corpus shows "national". */
    val market: String? = null,
    val media: NflBroadcastMediaDto? = null,
    val names: List<String>? = null,
)

@Serializable
data class NflBroadcastTypeDto(
    val id: String? = null,
    val shortName: String? = null,
)

@Serializable
data class NflBroadcastMediaDto(
    val shortName: String? = null,
)

/** One period's score. The scoreboard carries a numeric value and a period index; the summary carries only text. */
@Serializable
data class NflLinescoreDto(
    val value: Double? = null,
    val displayValue: String? = null,
    val period: Int? = null,
)
