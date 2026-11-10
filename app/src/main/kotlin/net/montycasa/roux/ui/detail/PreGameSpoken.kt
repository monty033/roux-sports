package net.montycasa.roux.ui.detail

import net.montycasa.roux.domain.model.ClubForm
import net.montycasa.roux.domain.model.GoalieSeason
import net.montycasa.roux.domain.model.SeasonRecord
import net.montycasa.roux.domain.policy.PreGameFormat

/**
 * What a screen reader says for the four pre-game cards: one sentence per logical row.
 *
 * The cards are drawn as fragments. A reader hears "6-3-1", "W2", "20.3%", "8th" and a crest, and cannot tell what
 * any of it belongs to. Each sentence here carries the same facts in the order a person would say them, and spells
 * out only the glyphs that are ambiguous when they are heard: a percent sign is "percent", and a record's dashes are
 * "wins, losses, overtime losses". Ordinary integers stay as they are: "13 points" is heard as it is written.
 *
 * These are plain functions over the domain's values, with no Compose types in sight, so each one can be tested on
 * its own and a card's composable stays a call site rather than a wall of string building.
 */
internal object PreGameSpoken {
    /** The dash the card prints for a figure the feed did not send. It is never said aloud. */
    private const val MISSING = "–"

    /**
     * The Last Ten card's row for one club: the club's name, its record in words, then its streak in words.
     *
     * Null when the feed sent no record, because there is then nothing to say and the card prints nothing for that
     * club either.
     */
    fun lastTen(
        club: String,
        form: ClubForm,
    ): String? {
        val record = form.record ?: return null
        val parts = mutableListOf(club, recordInWords(record))
        streakInWords(form)?.let { parts += it }
        return parts.joinToString(", ")
    }

    /**
     * The Season stats card's row: the stat, then the away club's value and rank, then the home club's.
     *
     * A side the feed sent no value for is left out rather than read as a dash, and a side with a value but no rank
     * is read without one.
     */
    fun seasonStat(
        label: String,
        away: StatSide,
        home: StatSide,
    ): String = (listOf(label) + listOfNotNull(away.spoken(), home.spoken())).joinToString(". ") + "."

    /**
     * One club's row on the Season stats card when the card is stacked and each club has a row of its own.
     *
     * Null when that side has nothing to say.
     */
    fun seasonStatSide(
        label: String,
        side: StatSide,
    ): String? = side.spoken()?.let { "$label. $it." }

    /**
     * The Top Performers card's row for one player: their name, then their goals, assists and points.
     *
     * The counts are the ones the card ranks by, in the order it shows them.
     */
    fun topPerformer(
        name: String,
        goals: Int,
        assists: Int,
        points: Int,
    ): String =
        "$name, $goals ${plural(goals, "goal")}, $assists ${plural(assists, "assist")}, " +
            "$points ${plural(points, "point")}"

    /**
     * The Goaltenders card's row for one goaltender: their name, their games, their record, their goals against
     * average and their save percentage.
     *
     * A figure the feed did not send is left out rather than read as a dash.
     */
    fun goaltender(
        name: String,
        goalie: GoalieSeason,
    ): String {
        val parts = mutableListOf(name)
        goalie.gamesPlayed?.let { parts += "$it ${plural(it, "game")}" }
        goalie.record?.let { parts += recordInWords(it) }
        goalie.goalsAgainstAverage?.let { parts += "${PreGameFormat.perGame(it)} goals against" }
        goalie.savePercentage?.let { parts += "${PreGameFormat.percent(it).replace("%", " percent")} saves" }
        return parts.joinToString(", ")
    }

    /**
     * One club's side of a season-stat row: the club, the value as the card prints it, and the rank.
     *
     * A value the feed did not send leaves the side out; a rank it did not send leaves the rank out. The card prints
     * a dash for either, and a dash read aloud is a dash, not a number.
     */
    data class StatSide(
        val club: String,
        val value: String,
        val rank: String,
    ) {
        /** The side as it is said, or null when the side has nothing to say. */
        fun spoken(): String? {
            val value = value.trim()
            if (value.isEmpty() || value == MISSING) return null
            val spoken = value.replace("%", " percent")
            val rank = rank.trim()
            return if (rank.isEmpty() || rank == MISSING) "$club $spoken" else "$club $spoken, $rank"
        }
    }

    /**
     * A count and its noun: "1 win", "6 wins", "1 loss", "4 losses", "2 overtime losses".
     *
     * A noun that already ends in an "s" takes "es", which is what "loss" needs.
     */
    private fun plural(
        count: Int,
        noun: String,
    ): String {
        val many = if (noun.endsWith("s")) "${noun}es" else "${noun}s"
        return if (count == 1) noun else many
    }

    /** A record in words: "6 wins, 3 losses, 1 overtime loss". */
    private fun recordInWords(record: SeasonRecord): String =
        listOf(
            "${record.wins} ${plural(record.wins, "win")}",
            "${record.losses} ${plural(record.losses, "loss")}",
            "${record.overtimeLosses} ${plural(record.overtimeLosses, "overtime loss")}",
        ).joinToString(", ")

    /**
     * The feed's streak code in words: how the club's run of games ended.
     *
     * The codes are the feed's own: `W`, `L`, `OTL` and `SOL`. A code the feed does not use says nothing, rather
     * than reading the bare count on its own.
     */
    private fun streakInWords(form: ClubForm): String? {
        val type = form.streakType?.trim()?.uppercase() ?: return null
        val count = form.streak ?: return null
        return when (type) {
            "W" -> "won the last $count"
            "L" -> "lost the last $count"
            "OTL" -> "lost $count in overtime"
            "SOL" -> "lost $count in a shootout"
            else -> null
        }
    }
}
