package net.montycasa.roux.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The request token has a fixed shape, and the shape is the fix.
 *
 * ESPN refuses a User-Agent that carries `git.montycasa.net` with 403, which is why every football screen was empty on
 * a device while the suite stayed green: the tests run against a mock engine, and a mock engine never rejects a
 * header. A URL in the token also told two third parties where the app is developed, which the app has no reason to
 * do. These assertions fail on either mistake.
 */
class UserAgentTest {
    @Test
    fun theTokenNamesTheAppAndItsVersion() {
        assertEquals("RouxSports/9.9.9 (Android)", UserAgent.of("9.9.9"))
    }

    @Test
    fun theTokenCarriesNoUrl() {
        val token = UserAgent.of("9.9.9")
        assertFalse("a URL in the token is what ESPN refuses and what leaks the host", token.contains("://"))
        assertFalse(token.contains("+http"))
    }

    @Test
    fun theTokenNamesNoInternalHost() {
        val token = UserAgent.of("9.9.9")
        assertFalse("the request token must not name where the app is developed", token.contains("montycasa"))
    }

    @Test
    fun aVersionIsCarriedVerbatim() {
        // A version with build metadata must not be trimmed: the token is what support reads back.
        assertEquals("RouxSports/1.2.3-debug (Android)", UserAgent.of("1.2.3-debug"))
    }
}
