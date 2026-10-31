package net.montycasa.roux

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import net.montycasa.roux.notify.Notifier
import net.montycasa.roux.ui.nav.GameRequest
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.theme.RouxTheme

class MainActivity : ComponentActivity() {
    /**
     * The game a notification was tapped for, if any.
     *
     * State rather than a one-shot read, because this activity is reused: with `FLAG_ACTIVITY_SINGLE_TOP` a
     * second tap arrives at [onNewIntent] and never reaches [onCreate], so a value read once at start would send
     * the user to the first game they ever tapped.
     */
    private val requestedGame = mutableStateOf<GameRequest?>(null)
    private var requestCount = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request(intent?.getStringExtra(Notifier.EXTRA_GAME_ID))
        setContent {
            // Fixed palettes from the signed-off design; dynamic colour stays off. The theme also
            // carries the orange roles Material has no slot for.
            //
            // No inset padding here: the shell's Scaffold owns the insets, so the navigation bar's own
            // background reaches the bottom edge instead of stopping short of it.
            RouxTheme {
                RouxNavHost(
                    requestedGame = requestedGame.value,
                    // Cleared once acted on: holding it would mean the same tap, or any recomposition, navigates
                    // again, and the screen could never be left.
                    onRequestHandled = { requestedGame.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent so this activity's own `intent` is the newest one, not the one it was created with.
        setIntent(intent)
        request(intent.getStringExtra(Notifier.EXTRA_GAME_ID))
    }

    /**
     * A tap becomes a request with a fresh nonce. Tapping the same game twice is two requests: an id compared by
     * value would be unchanged the second time and the navigation would not happen.
     */
    private fun request(gameId: String?) {
        if (gameId != null) requestedGame.value = GameRequest(gameId, ++requestCount)
    }
}
