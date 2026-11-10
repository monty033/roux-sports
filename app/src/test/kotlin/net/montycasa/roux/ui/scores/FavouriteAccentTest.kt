package net.montycasa.roux.ui.scores

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.model.DateIndexEntry
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.ui.theme.RouxDarkAccents
import net.montycasa.roux.ui.theme.RouxLightAccents
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.time.Instant

/**
 * The accent stripe on a favourite card, as the screen actually paints it.
 *
 * The stripe is drawn, not laid out, so the only honest way to prove it is there is to look at the
 * pixels: a card's left edge carries the orange, and a card that is not a favourite's carries the
 * card's own surface. The band is measured in the card's own image, so a stripe painted somewhere
 * else - or painted twice for a matchup between two favourites - fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h923dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavouriteAccentTest {
    @get:Rule
    val rule = createComposeRule()

    /** The width Patrick approved, written out here so the measurement is against the approval. */
    private val approvedAccentWidth = 4.dp

    private val date = "2025-10-25"
    private val index = listOf(DateIndexEntry(date, 4))

    /** The away club is the favourite: the accent has to reach a card whose favourite is on top. */
    private val awayFavourite = game("away", away = "TOR", home = "BOS")

    /** The home club is the favourite: the other end of the card. */
    private val homeFavourite = game("home", away = "BOS", home = "TOR")

    /** Both clubs are favourites, and the card still carries one stripe. */
    private val bothFavourite = game("both", away = "TOR", home = "MTL")

    /** Neither club is a favourite. */
    private val neither = game("neither", away = "BOS", home = "NYR")

    private val suppliedState = mutableStateOf<ScoresUiState?>(null)

    @Test
    fun aFavouriteClubAccentsItsCardAndLeavesTheOthersAlone() {
        launch(favourites = setOf(TeamKey("TOR")))

        assertAccented(tag(awayFavourite))
        assertAccented(tag(homeFavourite))
        assertNotAccented(tag(neither))
    }

    @Test
    fun aMatchupBetweenTwoFavouritesCarriesOneAccent() {
        launch(favourites = setOf(TeamKey("TOR"), TeamKey("MTL")))

        // One stripe, not one per favourite club: the band is 4 dp and it is painted once.
        assertAccented(tag(bothFavourite))
        assertNotAccented(tag(neither))
    }

    @Test
    fun noFavouriteSelectionAccentsNoCard() {
        launch(favourites = emptySet())

        assertNotAccented(tag(awayFavourite))
        assertNotAccented(tag(homeFavourite))
        assertNotAccented(tag(bothFavourite))
        assertNotAccented(tag(neither))
    }

    @Test
    fun theAllGamesListStillAccentsTheFavourites() {
        // The filter is off, so every game is on the list and the favourites are not pinned to the top.
        // The accent follows the teams, not the order: the order also depends on live and final state.
        launch(favourites = setOf(TeamKey("TOR")), favouritesOnly = false)

        assertAccented(tag(awayFavourite))
        assertAccented(tag(homeFavourite))
        assertNotAccented(tag(neither))
    }

    @Test
    fun theOffSeasonFullSlateAccentsOnlyTheGamesInvolvingFavourites() {
        // The off-season shows every game of the last day, favourites or not.
        launch(
            favourites = setOf(TeamKey("TOR")),
            offSeason = OffSeasonUi("2025-10-25", "2026-09-19", "2025–26"),
        )

        assertAccented(tag(awayFavourite))
        assertNotAccented(tag(neither))
    }

    @Test
    fun theAccentIsTheSameOrangeInTheDarkTheme() {
        launch(favourites = setOf(TeamKey("TOR")), dark = true)

        assertAccented(tag(awayFavourite), dark = true)
        assertNotAccented(tag(neither), dark = true)
    }

    private fun launch(
        favourites: Set<TeamKey>,
        favouritesOnly: Boolean = true,
        offSeason: OffSeasonUi? = null,
        dark: Boolean = false,
    ) {
        suppliedState.value =
            ScoresUiState(
                selectedDate = date,
                today = date,
                rows = listOf(awayFavourite, homeFavourite, bothFavourite, neither),
                favouritesOnly = favouritesOnly,
                favouriteTeams = favourites,
                freshness = Freshness.Fresh,
                dateIndex = index,
                offSeason = offSeason,
            )
        rule.setContent {
            RouxTheme(dark = dark) {
                ScoresScreen(
                    suppliedState.value!!,
                    onRefresh = {},
                    onGameClick = {},
                    onSelectDate = {},
                )
            }
        }
    }

    private fun tag(game: Game): String = "scores.game.${game.id.value}"

    /** The card's middle row, in the card's own pixels: the left edge is straight there. */
    private fun middleRow(cardTag: String): List<Color> {
        val pixels = rule.onNodeWithTag(cardTag).captureToImage().toPixelMap()
        val row = pixels.height / 2
        return (0 until pixels.width).map { pixels[it, row] }
    }

    private fun assertAccented(
        cardTag: String,
        dark: Boolean = false,
    ) {
        val accent = if (dark) RouxDarkAccents.fill else RouxLightAccents.fill
        val painted = middleRow(cardTag).withIndex().filter { it.value == accent }.map { it.index }
        assertTrue("the accent is not painted on $cardTag", painted.isNotEmpty())
        // The stripe is flush with the card's left edge; at most one pixel of the edge itself is blended.
        assertTrue("the accent does not start at the card's left edge", painted.first() <= 1)
        // One stripe, not one per favourite club: the paint is a single run across the card.
        assertEquals(
            "the accent is painted in one run, not one per favourite club",
            painted.size,
            painted.last() - painted.first() + 1,
        )
        // Measured against the approved width rather than read from the implementation's constant: four dp,
        // a shade thicker than the mock's three.
        val approved = with(rule.density) { approvedAccentWidth.toPx() }
        assertTrue(
            "the accent is ${painted.size} px wide; the approved width is $approved px",
            abs(painted.size - approved) <= 1,
        )
    }

    private fun assertNotAccented(
        cardTag: String,
        dark: Boolean = false,
    ) {
        val accent = if (dark) RouxDarkAccents.fill else RouxLightAccents.fill
        assertTrue("the accent is painted on $cardTag", middleRow(cardTag).none { it == accent })
    }

    private fun game(
        id: String,
        away: String,
        home: String,
    ): Game =
        Game(
            id = GameId(id),
            date = date,
            state = GameState.Scheduled,
            away = TeamRef(TeamKey(away), name = club(away)),
            home = TeamRef(TeamKey(home), name = club(home)),
            startTimeUtc = Instant.parse("2025-10-25T23:00:00Z"),
        )

    private fun club(key: String): String =
        when (key) {
            "TOR" -> "Toronto Maple Leafs"
            "BOS" -> "Boston Bruins"
            "MTL" -> "Montréal Canadiens"
            "NYR" -> "New York Rangers"
            else -> key
        }
}
