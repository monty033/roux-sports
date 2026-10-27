package net.montycasa.roux.domain.policy

import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesOrderingTest {
    private val tor = TeamKey("TOR")
    private val mtl = TeamKey("MTL")
    private val games =
        listOf(
            game("bos-nyi", "BOS", "NYI"),
            game("tor-buf", "TOR", "BUF"),
            game("edm-van", "EDM", "VAN"),
            game("mtl-ott", "MTL", "OTT"),
            game("wpg-tor", "WPG", "TOR"),
            game("ana-cgy", "ANA", "CGY"),
        )

    @Test
    fun favoritesOnlyKeepsTorontoHomeAndAwayGamesInFeedOrder() {
        val result = FavoritesOrdering.filter(games, setOf(tor), favoritesOnly = true)
        assertEquals(listOf(games[1], games[4]), result.rows)
        assertNull(result.hint)
    }

    @Test
    fun emptyFavoritesKeepAllGamesAndInviteTeamSelection() {
        val result = FavoritesOrdering.filter(games, emptySet(), favoritesOnly = true)
        assertEquals(games, result.rows)
        assertEquals(FavoritesHint.PickTeams, result.hint)
    }

    @Test
    fun selectedFavoritesAbsentFromDayYieldNoRowsAndNoFavouriteGamesHint() {
        val result = FavoritesOrdering.filter(games, setOf(TeamKey("SEA")), favoritesOnly = true)
        assertEquals(emptyList<Game>(), result.rows)
        assertEquals(FavoritesHint.NoFavouriteGames, result.hint)
    }

    @Test
    fun pinPreservesFeedOrderWithinBothFavoriteAndOtherGroups() {
        val rows = FavoritesOrdering.pin(games, setOf(tor, mtl))
        assertEquals(
            listOf("tor-buf", "mtl-ott", "wpg-tor", "bos-nyi", "edm-van", "ana-cgy"),
            rows.map { it.id.value },
        )
    }

    @Test
    fun awayFavoriteAloneQualifiesForFilteringAndPinning() {
        val awayGame = game("bos-tor", "BOS", "TOR")
        val rows = listOf(games[0], awayGame)
        val result = FavoritesOrdering.filter(rows, setOf(tor), favoritesOnly = true)
        assertEquals(listOf(awayGame), result.rows)
        assertNull(result.hint)
        assertEquals(listOf(awayGame, games[0]), FavoritesOrdering.pin(rows, setOf(tor)))
    }

    @Test
    fun favoritesOnlyOffKeepsAllGamesInFeedOrderWithSelectedFavorites() {
        val result = FavoritesOrdering.filter(games, setOf(tor), favoritesOnly = false)
        assertEquals(games, result.rows)
        assertNull(result.hint)
    }

    @Test
    fun emptyDayWithSelectedFavoritesYieldsNoRowsAndNoFavouriteGamesHint() {
        val result = FavoritesOrdering.filter(emptyList(), setOf(tor), favoritesOnly = true)
        assertEquals(emptyList<Game>(), result.rows)
        assertEquals(FavoritesHint.NoFavouriteGames, result.hint)
    }

    @Test
    fun pinOnAlreadyPinnedRowsIsIdempotent() {
        val ordered = listOf(games[1], games[3], games[4], games[0], games[2], games[5])
        val pinned = FavoritesOrdering.pin(ordered, setOf(tor, mtl))
        assertEquals(ordered, pinned)
        assertEquals(pinned, FavoritesOrdering.pin(pinned, setOf(tor, mtl)))
    }

    @Test
    fun emptyFavoritesStillInviteTeamSelectionWhenFavoritesOnlyIsOff() {
        val result = FavoritesOrdering.filter(games, emptySet(), favoritesOnly = false)
        assertEquals(games, result.rows)
        assertEquals(FavoritesHint.PickTeams, result.hint)
    }

    @Test
    fun emptyDayWithoutFavoritesPrioritizesTeamSelectionHint() {
        val result = FavoritesOrdering.filter(emptyList(), emptySet(), favoritesOnly = true)
        assertEquals(emptyList<Game>(), result.rows)
        assertEquals(FavoritesHint.PickTeams, result.hint)
    }

    @Test
    fun favoritesOnlyOffDoesNotReportAbsentFavorites() {
        val result = FavoritesOrdering.filter(games, setOf(TeamKey("SEA")), favoritesOnly = false)
        assertEquals(games, result.rows)
        assertNull(result.hint)
    }

    @Test
    fun emptyDayWithFilteringOffAndSelectedFavoritesNeedsNoHint() {
        val result = FavoritesOrdering.filter(emptyList(), setOf(tor), favoritesOnly = false)
        assertEquals(emptyList<Game>(), result.rows)
        assertNull(result.hint)
    }

    @Test
    fun pinWithEmptyFavoritesPreservesEntireFeedOrder() {
        assertEquals(games, FavoritesOrdering.pin(games, emptySet()))
    }

    @Test
    fun pinWithEmptyDayReturnsNoRows() {
        assertEquals(emptyList<Game>(), FavoritesOrdering.pin(emptyList(), setOf(tor)))
    }

    @Test
    fun gameBetweenTwoFavoritesIsPinnedOnce() {
        val matchup = game("tor-mtl", "TOR", "MTL")
        assertEquals(
            listOf(matchup, games[0]),
            FavoritesOrdering.pin(listOf(games[0], matchup), setOf(tor, mtl)),
        )
    }

    @Test
    fun pinAfterFavoritesOnlyFilteringLeavesRowsUnchanged() {
        val result = FavoritesOrdering.filter(games, setOf(tor, mtl), favoritesOnly = true)
        assertEquals(listOf(games[1], games[3], games[4]), result.rows)
        assertEquals(result.rows, FavoritesOrdering.pin(result.rows, setOf(tor, mtl)))
    }

    private fun game(
        id: String,
        home: String,
        away: String,
    ): Game =
        Game(
            id = GameId(id),
            date = "2026-10-26",
            state = GameState.Scheduled,
            home = TeamRef(TeamKey(home)),
            away = TeamRef(TeamKey(away)),
        )
}
