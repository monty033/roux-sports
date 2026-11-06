package net.montycasa.roux.domain.model

/** One day in a weekly schedule feed, with every game type the feed listed for that day. */
data class ScheduleDay(
    val date: String,
    val gameTypes: List<Int>,
)

/**
 * One weekly schedule response: its days and the start date of the next block.
 *
 * The feed pages by week, so a walk reads one block and follows [nextStartDate] instead of guessing
 * the next date. A missing next date means the walk cannot continue.
 */
data class ScheduleBlock(
    val days: List<ScheduleDay> = emptyList(),
    val nextStartDate: String? = null,
)
