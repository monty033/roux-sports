package net.montycasa.roux.data.net

/**
 * The User-Agent every provider request carries.
 *
 * It is a plain product token on purpose: no URL, and nothing that identifies the operator.
 *
 * Two things make that a rule rather than a preference. ESPN's edge answers **403** to a User-Agent carrying
 * `git.montycasa.net`, which is exactly what the app used to send, so every football screen failed on a real device
 * while every test passed against a mock engine that never looks at headers. And the old token advertised a private
 * host to ESPN and to the NHL on every single request, which a public app has no reason to do.
 *
 * [UserAgentTest] holds the shape: change the wording here and it fails.
 */
internal object UserAgent {
    /** The request token for [version], the app's version name. */
    fun of(version: String): String = "RouxSports/$version (Android)"
}
