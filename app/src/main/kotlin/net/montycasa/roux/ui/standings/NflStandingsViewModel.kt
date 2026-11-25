package net.montycasa.roux.ui.standings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.domain.model.NflConference
import net.montycasa.roux.domain.model.NflStandingRow
import net.montycasa.roux.domain.model.NflStandingTable
import net.montycasa.roux.domain.repo.NflStandingsRepository

/** The three questions the football table answers. The conference switcher applies to the first two. */
enum class NflStandingsTab {
    Division,
    Conference,
    League,
}

/** One read of the feed, kept with the freshness and error that came with it so a tab can show its own. */
data class NflStandingsLoad(
    val tables: List<NflStandingTable>,
    val stale: Freshness.Stale? = null,
    val error: LoadError? = null,
)

data class NflStandingsUiState(
    /** What the selected tab draws: four divisions, one conference, or all thirty-two clubs under one blank title. */
    val tables: List<NflStandingTable> = emptyList(),
    val tab: NflStandingsTab = NflStandingsTab.Division,
    val conference: NflConference = NflConference.AFC,
    /** True from the first request until the selected tab has either rows or an answer. */
    val loading: Boolean = true,
    val error: LoadError? = null,
    val stale: Freshness.Stale? = null,
    val isRefreshing: Boolean = false,
    // What has been read so far. Switching tab or conference reads from here, so it never costs a request: the
    // conference tables arrive once, and each conference's divisions the first time that conference is looked at.
    val conferenceLoad: NflStandingsLoad? = null,
    val divisionLoads: Map<NflConference, NflStandingsLoad> = emptyMap(),
)

/**
 * The football standings page's state.
 *
 * Shaped like the hockey [StandingsViewModel] where the two overlap (`loading`, `error`, `stale`, a table list and the
 * selection), and different where football is: the feed publishes the conference level and the division level as
 * separate payloads, so the divisions are read lazily per conference rather than all at once.
 *
 * Whether a game is live is not known here, for the same reason it is not in the hockey model: no live-game signal
 * reaches the standings page, so every read passes `anyGameLive = false` and the repository applies its idle window.
 */
class NflStandingsViewModel(
    private val standings: NflStandingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NflStandingsUiState())
    val uiState: StateFlow<NflStandingsUiState> = mutableState.asStateFlow()

    // Declared before the init block that starts the first read: a dispatcher that runs the launch inline would
    // otherwise reach this set before it exists.
    private val inFlight = mutableSetOf<Source>()

    init {
        refresh()
    }

    fun selectTab(tab: NflStandingsTab) {
        mutableState.update { shown(it.copy(tab = tab)) }
        loadMissing()
    }

    fun selectConference(conference: NflConference) {
        mutableState.update { shown(it.copy(conference = conference)) }
        loadMissing()
    }

    /**
     * Reads the conference tables and, when the tab needs them, the selected conference's divisions. A repeat reads
     * every table this page already holds again - the repository answers from its cache while that is fresh - so a
     * retry cannot leave one tab newer than another.
     */
    fun refresh() {
        if (mutableState.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val held = mutableState.value
            // The source the page is showing goes first, so what is on screen is the first thing to arrive.
            val sources =
                buildList {
                    add(if (held.tab == NflStandingsTab.Division) Source.Divisions(held.conference) else Source.Conferences)
                    add(Source.Conferences)
                    held.divisionLoads.keys.forEach { add(Source.Divisions(it)) }
                }.distinct()
            try {
                sources.forEach { read(it) }
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** A tab or conference the page has not read yet: ask for exactly that one source, once. */
    private fun loadMissing() {
        val state = mutableState.value
        val missing =
            when (state.tab) {
                NflStandingsTab.Division -> Source.Divisions(state.conference).takeIf { it.conference !in state.divisionLoads }
                else -> Source.Conferences.takeIf { state.conferenceLoad == null }
            } ?: return
        if (missing in inFlight) return
        viewModelScope.launch { read(missing) }
    }

    private suspend fun read(source: Source) {
        if (!inFlight.add(source)) return
        try {
            val loaded =
                try {
                    when (source) {
                        Source.Conferences -> standings.conferences(anyGameLive = false)
                        is Source.Divisions -> standings.divisions(source.conference, anyGameLive = false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A cache that cannot be written is not a network failure, and it must not escape the scope and
                    // take the app down. Whatever was already on screen stays: the table did not stop being the
                    // table because this read failed.
                    mutableState.update { state ->
                        val previous = loadOf(state, source)
                        shown(store(state, source, NflStandingsLoad(previous?.tables.orEmpty(), previous?.stale, LoadError.Local)))
                    }
                    return
                }
            mutableState.update { state -> shown(store(state, source, loaded.toLoad())) }
        } finally {
            inFlight.remove(source)
        }
    }

    private fun Loadable<List<NflStandingTable>>.toLoad() = NflStandingsLoad(data.orEmpty(), freshness as? Freshness.Stale, error)

    private fun loadOf(
        state: NflStandingsUiState,
        source: Source,
    ): NflStandingsLoad? =
        when (source) {
            Source.Conferences -> state.conferenceLoad
            is Source.Divisions -> state.divisionLoads[source.conference]
        }

    private fun store(
        state: NflStandingsUiState,
        source: Source,
        load: NflStandingsLoad,
    ): NflStandingsUiState =
        when (source) {
            Source.Conferences -> state.copy(conferenceLoad = load)
            is Source.Divisions -> state.copy(divisionLoads = state.divisionLoads + (source.conference to load))
        }

    /** Derives what the selected tab draws from what has been read. */
    private fun shown(state: NflStandingsUiState): NflStandingsUiState {
        val load =
            when (state.tab) {
                NflStandingsTab.Division -> state.divisionLoads[state.conference]
                else -> state.conferenceLoad
            }
        val tables =
            when (state.tab) {
                NflStandingsTab.Division -> {
                    load?.tables.orEmpty()
                }

                NflStandingsTab.Conference -> {
                    load
                        ?.tables
                        ?.let { conferenceTable(it, state.conference) }
                        ?.let { listOf(it.copy(rows = seedOrder(it.rows))) }
                        .orEmpty()
                }

                NflStandingsTab.League -> {
                    load
                        ?.tables
                        ?.flatMap { it.rows }
                        ?.let { listOf(NflStandingTable("", leagueOrder(it))) }
                        .orEmpty()
                }
            }
        return state.copy(tables = tables, loading = load == null, error = load?.error, stale = load?.stale)
    }

    private sealed interface Source {
        data object Conferences : Source

        data class Divisions(
            val conference: NflConference,
        ) : Source
    }
}

/**
 * The table the feed published for [conference].
 *
 * The conference payload names its two tables ("American Football Conference") but carries no conference id the domain
 * keeps, so the name is the only statement of which is which. The match is on the name's own words; when the feed words
 * it differently, the feed's order (AFC first) is used rather than showing nothing.
 */
internal fun conferenceTable(
    tables: List<NflStandingTable>,
    conference: NflConference,
): NflStandingTable? {
    val words =
        when (conference) {
            NflConference.AFC -> listOf("american", "afc")
            NflConference.NFC -> listOf("national", "nfc")
        }
    return tables.firstOrNull { table -> words.any { it in table.name.lowercase() } } ?: tables.getOrNull(conference.ordinal)
}

/**
 * Orders one conference by playoff seed: the seeded clubs 1, 2, 3 ... in order, then any club the feed gave no seed,
 * in the feed's own order.
 *
 * The feed's conference table is ranked by record, not by seed, and the two disagree whenever a division leader has a
 * worse record than a wild card: in the captured NFC table the third and fourth seeds sit below the eighth and ninth.
 * The playoff line is drawn after the seventh seed, so a table in record order would put two playoff clubs below it.
 * The seeds are the feed's own figures, so ordering by them states nothing the feed did not.
 */
internal fun seedOrder(rows: List<NflStandingRow>): List<NflStandingRow> = rows.sortedWith(compareBy(nullsLast()) { it.playoffSeed })

/**
 * The league page's order: win percentage, then point differential, then points for, then abbreviation.
 *
 * The app sorts here although the feed's own tables are already ranked, because they are ranked *within* a conference:
 * combining the two conferences into one list needs a rule, and this is football's. Win percentage is read as a number
 * from the feed's display string (".889"), higher first, so the order does not depend on how the text compares. A club
 * with a missing figure sorts after every club that has one, and the abbreviation settles whatever is still level, so
 * the same feed always produces the same page.
 */
internal fun leagueOrder(rows: List<NflStandingRow>): List<NflStandingRow> =
    rows.sortedWith(
        compareByDescending<NflStandingRow, Double?>(nullsFirst()) { it.winPercent?.toDoubleOrNull() }
            .thenByDescending(nullsFirst()) { it.differential }
            .thenByDescending(nullsFirst()) { it.pointsFor }
            .thenBy { it.team.abbrev },
    )
