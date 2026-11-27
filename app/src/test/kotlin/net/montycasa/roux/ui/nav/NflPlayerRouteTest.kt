package net.montycasa.roux.ui.nav

import net.montycasa.roux.domain.model.NflPlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Football's player route is its own destination, not hockey's with a different id. The two leagues' athlete ids come
 * from different feeds, so the same digits must produce two different routes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NflPlayerRouteTest {
    @Test
    fun aFootballPlayerHasItsOwnLeagueQualifiedRoute() {
        val football = nflPlayerRoute(NflPlayerId("3046779"))
        val hockey = playerRoute(3046779L)

        assertEquals("nfl-player/3046779", football)
        assertEquals("player/3046779", hockey)
        assertTrue("the two leagues' players must not share a route", football != hockey)
    }
}
