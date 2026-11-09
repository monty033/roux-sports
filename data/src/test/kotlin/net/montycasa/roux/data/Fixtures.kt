package net.montycasa.roux.data

// When :app tests need this corpus, move it to a shared test-fixtures module or Gradle test-fixtures artifact.

/** Real NHL API responses captured by scripts/capture-fixtures.sh. */
object Fixtures {
    const val CURRENT = "2026-10-26"

    /** Club season schedules were captured separately, after the base corpus. */
    const val CLUB_SCHEDULE_CAPTURE = "2026-11-03"

    /** Club statistics arrived in the same later capture as the schedule and roster. */
    const val CLUB_STATS_CAPTURE = CLUB_SCHEDULE_CAPTURE

    /** The league penalties table was captured a day after the club corpus, on 4 November 2026. */
    const val LEAGUE_PENALTIES_CAPTURE = "2026-11-04"

    /** The Toronto season pair that proves the pre-season rollover, captured 9 November 2026. */
    const val ROLLOVER_CAPTURE = "2026-11-09"

    fun bytes(name: String): ByteArray = bytes(CURRENT, name)

    fun bytes(
        capture: String,
        name: String,
    ): ByteArray {
        val path = "/fx/$capture/$name"
        val stream = Fixtures::class.java.getResourceAsStream(path) ?: error("Fixture resource not found: $path")
        return stream.use { it.readBytes() }
    }

    fun text(name: String): String = text(CURRENT, name)

    fun text(
        capture: String,
        name: String,
    ): String = bytes(capture, name).toString(Charsets.UTF_8)

    /** A club schedule captured 3 November 2026 from the live API. */
    fun clubSchedule(name: String): String = text(CLUB_SCHEDULE_CAPTURE, name)

    /** A current roster captured 3 November 2026 from the live API. */
    fun roster(name: String): String = text(CLUB_SCHEDULE_CAPTURE, name)

    /** Club season statistics captured 3 November 2026 from the live API. */
    fun clubStats(name: String): String = text(CLUB_SCHEDULE_CAPTURE, name)

    /** The shorter name, for callers that already have "club" in scope. */
    fun stats(name: String): String = clubStats(name)

    /** A rollover capture from [ROLLOVER_CAPTURE]: the empty upcoming season, or the season that has games. */
    fun rollover(name: String): String = text(ROLLOVER_CAPTURE, name)
}
