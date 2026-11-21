package net.montycasa.roux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoFetcherRouterTest {
    @Test
    fun anEspnCdnLogoRoutesToTheEspnClient() {
        assertTrue(isEspnLogo("https://a.espncdn.com/i/teamlogos/nfl/500/phi.png"))
        assertTrue(isEspnLogo("https://A.ESPNCDN.COM/i/teamlogos/nfl/500/phi.png"))
    }

    @Test
    fun anNhlLogoStaysOnTheNhlClient() {
        assertFalse(isEspnLogo("https://assets.nhle.com/logos/nhl/svg/TOR_light.svg"))
    }

    @Test
    fun aLookalikeOrUnauthorizedEspnHostDoesNotRouteToEspn() {
        assertFalse(isEspnLogo("https://a.espncdn.com.evil.test/x.png"))
        assertFalse(isEspnLogo("https://site.web.api.espn.com/x.png"))
        assertFalse(isEspnLogo("https://site.api.espn.com/x.png"))
    }

    @Test
    fun aNonUrlLogoRoutesToNeitherEspnNorNhlBySpecialCase() {
        assertFalse(isEspnLogo(42))
        assertFalse(isEspnLogo("not a url"))
    }
}
