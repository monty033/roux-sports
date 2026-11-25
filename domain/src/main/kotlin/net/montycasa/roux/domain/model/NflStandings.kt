package net.montycasa.roux.domain.model

/** The two conferences. Which feed group answers for each is the data layer's business, not the domain's. */
enum class NflConference {
    AFC,
    NFC,
}

/**
 * One table the football feed publishes.
 *
 * The feed states its tables the same way at every level: a conference table holds sixteen clubs, a division table
 * holds four. [name] is the feed's own ("AFC East", "National Football Conference") because that is what the screen
 * has to print, and a name this app does not recognise still belongs on the screen.
 */
data class NflStandingTable(
    val name: String,
    val rows: List<NflStandingRow>,
)

/**
 * A club's line in a football table.
 *
 * Every number here is what the feed stated, and null where it stated nothing: a missing points total is drawn as a
 * blank, never as zero, because a club that has not scored and a club whose total the feed omitted are different
 * facts. The record string is the exception - it is composed from wins, losses and ties, because the feed writes it in
 * more than one place and the three counts are the ones it states numerically.
 *
 * @property winPercent The feed's own display (".889"), kept verbatim: football states a percentage to three places
 *   without a leading zero, and reformatting it here would invent a rule the feed already applies.
 * @property streak The feed's streak display ("W5"), not a signed number.
 * @property divisionRecord The feed's division record ("1-1"), which is the display string rather than the three
 *   counts the feed also carries.
 * @property conferenceRecord The feed's conference record, from the `vs. Conf.` stat.
 */
data class NflStandingRow(
    val team: TeamRef,
    val wins: Int? = null,
    val losses: Int? = null,
    val ties: Int? = null,
    val winPercent: String? = null,
    val pointsFor: Int? = null,
    val pointsAgainst: Int? = null,
    val differential: Int? = null,
    val streak: String? = null,
    val divisionRecord: String? = null,
    val conferenceRecord: String? = null,
    val playoffSeed: Int? = null,
) {
    /**
     * W-L-T, the record a football table states.
     *
     * The tie is always printed, even when it is zero: a football record without it is ambiguous late in a season,
     * where a club's tie is exactly what separates it from the club above.
     */
    val record: String?
        get() =
            if (wins != null && losses != null && ties != null) {
                "$wins-$losses-$ties"
            } else {
                null
            }
}
