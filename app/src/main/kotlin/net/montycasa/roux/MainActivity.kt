package net.montycasa.roux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.montycasa.roux.ui.nav.RouxNavHost
import net.montycasa.roux.ui.theme.RouxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Fixed palettes from the signed-off design; dynamic colour stays off. The theme also
            // carries the orange roles Material has no slot for.
            //
            // No inset padding here: the shell's Scaffold owns the insets, so the navigation bar's own
            // background reaches the bottom edge instead of stopping short of it.
            RouxTheme {
                RouxNavHost()
            }
        }
    }
}
