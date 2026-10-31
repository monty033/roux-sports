package net.montycasa.roux.notify

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.MainActivity
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.repo.TeamAlertPreferences
import net.montycasa.roux.ui.scores.ScoreFixture
import net.montycasa.roux.ui.scores.ScoresIntegrationApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Toronto's game in the captured slate, and the only one these tests move. */
private const val TORONTO_GAME = "2026020176"

private val TORONTO = TeamKey("TOR")

/**
 * The capture is one finished evening, so every game in it is Final and nothing in it is ever watched. The
 * start-up case is about a game that has *not* started, so this keeps the real slate - same teams, same ids,
 * same start times - and moves one game's state back to before the puck drop. Inventing a feed here would
 * test the fixture rather than the app.
 */
private fun slateWithAGameStillToCome(): ByteArray {
    val body = Json.parseToJsonElement(ScoreFixture.bytes.decodeToString()).jsonObject
    val games =
        body.getValue("games").let { it as JsonArray }.map { element ->
            val game = element.jsonObject
            if (game.getValue("id").jsonPrimitive.content == TORONTO_GAME) {
                JsonObject(game.toMutableMap().apply { put("gameState", JsonPrimitive("FUT")) })
            } else {
                game
            }
        }
    val amended = JsonObject(body.toMutableMap().apply { put("games", JsonArray(games)) })
    return Json.encodeToString(JsonElement.serializer(), amended).encodeToByteArray()
}

/**
 * Stage 8's wiring, end to end: the app opening is what schedules a watch, and the tap that arrives from a
 * notification is what opens a game.
 *
 * These run against the real `RouxApp`, the real container and the real worker factory; only the engine and
 * the clock are substituted, which is exactly what `ScoresIntegrationApp` exists for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
class NotificationsIntegrationTest {
    private val app: ScoresIntegrationApp
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startWorkManager() {
        app.scoreDay = slateWithAGameStillToCome()
        // The app's own factory, so the workers this test finds are built the way production builds them.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration
                .Builder()
                .setWorkerFactory(RouxWorkerFactory(app.container))
                .setExecutor(SynchronousExecutor())
                .build(),
        )
    }

    @Test
    fun theStartUpPassWatchesAFavouriteWhoseGameHasNotStarted() =
        runBlocking {
            follow(start = true, goals = false)

            app.reconcileWatches()

            assertEquals(listOf(WorkManagerGameWatchScheduler.watchName(TORONTO_GAME)), watchNames())
        }

    @Test
    fun aFavouriteWithBothAlertsOffGetsNoWatch() =
        runBlocking {
            follow(start = false, goals = false)

            app.reconcileWatches()

            // The periodic reconcile is scheduled either way - it is what notices a start time the feed published
            // late - so the question is about the per-game watches, and there must be none.
            assertTrue(watchNames().isEmpty())
        }

    @Test
    fun theAppAsksForThatPassWhenItOpens() {
        assertTrue(
            "the application never began a start-up reconcile",
            app.startUpPasses > 0,
        )
    }

    @Test
    fun theAppHasNoUrlEntryPoint() {
        // The tap arrives as an explicit intent carrying a game id. A scheme would be a second way in, reachable
        // by any app on the device, so the promise is that nothing resolves a roux:// link to this app.
        val resolved =
            app.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse("roux://game/$TORONTO_GAME")),
                0,
            )
        assertTrue("roux:// resolves to ${resolved.map { it.activityInfo.name }}", resolved.isEmpty())
    }

    private suspend fun follow(
        start: Boolean,
        goals: Boolean,
    ) {
        app.container.preferences.setFavourites(setOf(TORONTO))
        app.container.preferences.setTeamAlertPreferences(
            TORONTO,
            TeamAlertPreferences(start = start, goals = goals),
        )
    }

    private fun watchNames(): List<String> =
        WorkManager
            .getInstance(app)
            .getWorkInfosByTag(WorkManagerGameWatchScheduler.WATCH_TAG)
            .get()
            .flatMap { info -> info.tags.filter { it.startsWith(WorkManagerGameWatchScheduler.WATCH_PREFIX) } }
            .distinct()
}

/**
 * The tap side of stage 8, on the real activity: a notification carries a game id, the activity is reused, and
 * the shell has to open that game without turning into a second way into the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScoresIntegrationApp::class, qualifiers = "w411dp-h923dp-420dpi")
class NotificationTapIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aTappedGameOpensItsDetail() {
        tap(TORONTO_GAME)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("detail.back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun anIdThatMatchesNoGameLeavesTheAppOnScores() {
        tap("abc")

        composeRule.waitForIdle()

        // A tap we cannot read is a dead end, not a crash and not a guess: the user stays where they were.
        composeRule.onNode(hasText("Scores") and isSelectable()).assertIsSelected()
    }

    private fun tap(gameId: String) {
        // `onNewIntent` is protected, so this drives the one place both platform entry points funnel into. The
        // intent extra itself is `getStringExtra`, which is the framework's, not this app's.
        composeRule.activityRule.scenario.onActivity { activity -> activity.request(gameId) }
    }
}
