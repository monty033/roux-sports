package net.montycasa.roux.domain.model

/**
 * A football club's current roster.
 *
 * The feed states one flat list of players and a team-level `groups` object with no per-player membership, so the club
 * and its players are all this type carries. [players] is in the order the roster tab reads: position abbreviation,
 * then jersey number, then name. The adapter is what puts it in that order; an unordered list here would be a screen
 * describing its own ordering rule rather than rendering the roster.
 */
data class NflClubRoster(
    val team: TeamRef,
    val players: List<NflRosterPlayer> = emptyList(),
)

/** One player, limited to what the roster tab shows. */
data class NflRosterPlayer(
    /** The feed's player id, used to keep a row stable while the list refreshes. */
    val id: String,
    val name: String,
    /**
     * The feed's jersey text, exactly as it wrote it. Null means the feed stated no number, which is not zero: the
     * roster tab leaves the column blank rather than inventing one.
     */
    val jersey: String? = null,
    /** The feed's position code (`QB`, `CB`, `WR`), not a group label. */
    val positionAbbreviation: String,
    /** The position's full wording (`Quarterback`), from the same player-level position object. */
    val positionName: String,
    /** The feed's own height wording (`6' 5"`). */
    val heightText: String? = null,
    /** The feed's own weight wording (`237 lbs`). */
    val weightText: String? = null,
    val age: Int? = null,
    val headshotUrl: String? = null,
    /**
     * What the feed says about availability. Never a nullable boolean: [NflPlayerStatus.NotStated] is its own answer
     * and must not be read as "active".
     */
    val status: NflPlayerStatus = NflPlayerStatus.NotStated,
)

/**
 * The three availability answers the roster feed can give.
 *
 * The distinction is deliberately not a nullable string: twelve of Buffalo's seventy-two players carry no status
 * object at all, and seventeen are `active: false` - two different statements that a nullable "active" flag or a
 * missing status word would collapse into the same silence.
 */
sealed interface NflPlayerStatus {
    /** The feed states the player is active, with its own wording for it. */
    data class Active(
        val wording: String,
    ) : NflPlayerStatus

    /** The feed states some other status (`Day-To-Day`, `News`, …), with its own wording. */
    data class Other(
        val wording: String,
    ) : NflPlayerStatus

    /** The feed states no status at all. It says nothing about whether the player is available. */
    data object NotStated : NflPlayerStatus
}
