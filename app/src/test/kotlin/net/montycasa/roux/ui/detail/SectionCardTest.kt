@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui.detail

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.montycasa.roux.domain.Freshness
import net.montycasa.roux.domain.LoadError
import net.montycasa.roux.domain.Loadable
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A card that declares it exists only to show something must not be born as a placeholder: no header, no "Loading",
 * no frame while its fetch is in flight. A failure is still a card worth drawing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h923dp-xhdpi")
class SectionCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a hide-when-empty card with no data yet draws nothing`() {
        show(Loadable(null, Freshness.Missing, null), hideWhenEmpty = { it.isEmpty() })

        compose.onNodeWithTag(TAG).assertDoesNotExist()
        compose.onAllNodesWithText("Loading").assertCountEquals(0)
    }

    @Test
    fun `a hide-when-empty card whose data is empty draws nothing`() {
        show(Loadable(emptyList(), Freshness.Fresh, null), hideWhenEmpty = { it.isEmpty() })

        compose.onNodeWithTag(TAG).assertDoesNotExist()
        compose.onAllNodesWithText("Loading").assertCountEquals(0)
        compose.onNodeWithText(CONTENT).assertDoesNotExist()
    }

    @Test
    fun `a hide-when-empty card the predicate keeps draws its content`() {
        show(Loadable(listOf(CONTENT), Freshness.Fresh, null), hideWhenEmpty = { it.isEmpty() })

        compose.onNodeWithTag(TAG).assertExists()
        compose.onNodeWithText(CONTENT).assertExists()
    }

    @Test
    fun `a hide-when-empty card that failed still shows the failure and retry`() {
        show(
            Loadable(null, Freshness.Missing, LoadError.Http(503)),
            hideWhenEmpty = { it.isEmpty() },
            onRetry = {},
        )

        compose.onNodeWithText("Test could not be loaded").assertExists()
        compose.onNodeWithTag("$TAG.retry").assertExists()
    }

    @Test
    fun `a card without a predicate keeps the loading placeholder`() {
        show(Loadable(null, Freshness.Missing, null))

        compose.onNodeWithTag(TAG).assertExists()
        compose.onNodeWithText("Loading").assertExists()
    }

    private fun show(
        loadable: Loadable<List<String>>,
        hideWhenEmpty: ((List<String>) -> Boolean)? = null,
        onRetry: (() -> Unit)? = null,
    ) {
        compose.setContent {
            RouxTheme(dark = true) {
                SectionCard(
                    title = "Test",
                    tag = TAG,
                    loadable = loadable,
                    failureLabel = "Test could not be loaded",
                    onRetry = onRetry,
                    hideWhenEmpty = hideWhenEmpty,
                ) { items ->
                    Text(items.joinToString(separator = ""))
                }
            }
        }
    }

    private companion object {
        const val TAG = "detail.test.card"
        const val CONTENT = "Content"
    }
}
