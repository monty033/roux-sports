package net.montycasa.roux.domain.model

/**
 * One club in the football teams list.
 *
 * Everything here is what the teams feed states, and null where it stated nothing: a club with no stated nickname is
 * drawn without one, never with a guess built from its full name.
 *
 * @property team The club's identity, league-qualified, so starring it never stars the hockey club that shares its
 *   abbreviation. Its [TeamRef.name] is [fullName] and its [TeamRef.shortName] is [nickname], so the club draws the
 *   same way here as in any other football list.
 * @property fullName The feed's display name, city and nickname together ("Arizona Cardinals").
 * @property location The place alone ("Arizona"). It is not the name a table prints; it is kept because a later page
 *   may need the two halves apart.
 * @property logoUrl The club's crest, only when the feed's first logo is on the artwork host the app is allowed to
 *   reach. Null otherwise, so the row draws the abbreviation instead of asking for a host the client refuses.
 */
data class NflTeam(
    val team: TeamRef,
    val fullName: String?,
    val nickname: String?,
    val location: String?,
    val logoUrl: String?,
)

/**
 * One division's clubs, or the clubs no published division claimed.
 *
 * [division] is the feed's own name ("AFC East") and is null only for the clubs the division tables did not place. The
 * teams feed carries no division field, so a division can only come from a division table; a club it does not name is
 * drawn without a heading rather than dropped from the list or put under a division this app made up.
 */
data class NflTeamGroup(
    val division: String?,
    val teams: List<NflTeam>,
)
