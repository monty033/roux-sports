package net.montycasa.roux.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.montycasa.roux.MainActivity
import net.montycasa.roux.domain.model.Game
import net.montycasa.roux.domain.model.GameId
import net.montycasa.roux.domain.model.GameState
import net.montycasa.roux.domain.model.TeamKey
import net.montycasa.roux.domain.model.TeamRef
import net.montycasa.roux.domain.policy.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Assertions are made against what the platform holds, not against the builder: a notification that was
 * constructed and never handed to the manager would pass a test of the builder and never appear on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifierTest {
    private lateinit var application: Application
    private lateinit var manager: NotificationManager
    private lateinit var notifier: Notifier

    private val gameId = "2025020123"

    private val game =
        Game(
            id = GameId(gameId),
            date = "2025-02-01",
            state = GameState.Live(),
            home = TeamRef(TeamKey("MTL"), name = "Montréal Canadiens"),
            away = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs"),
            awayScore = 2,
            homeScore = 1,
        )

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // The permission is a runtime grant on API 33+, so a test that does not grant it is testing the refusal.
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = application.getSystemService(NotificationManager::class.java)
        notifier = Notifier(application)
        notifier.ensureChannels()
    }

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun goal(
        away: Int = 2,
        home: Int = 1,
    ) = Alert.Goal(GameId(gameId), away + home, away, home)

    @Test
    fun bothChannelsExistAfterSetup() {
        val start = manager.getNotificationChannel(Notifier.GAME_START_CHANNEL)
        val goals = manager.getNotificationChannel(Notifier.GOALS_CHANNEL)

        assertNotNull("a start channel, so starts can be silenced on their own", start)
        assertNotNull("a goals channel, so goals can be silenced on their own", goals)
        assertEquals("Game starts", start.name)
        assertEquals("Goals", goals.name)
    }

    @Test
    fun aGoalArrivesOnTheGoalsChannelWithTheScoreAsItsTitle() {
        val outcome = notifier.post(goal(away = 2, home = 1), game)

        assertTrue("the goal was posted", outcome is NotificationOutcome.Posted)
        val notification = posted().single()
        assertEquals("TOR 2 – 1 MTL", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Notifier.GOALS_CHANNEL, notification.channelId)
    }

    @Test
    fun aStartArrivesOnTheStartChannel() {
        notifier.post(Alert.GameStart(GameId(gameId), goalBaseline = 0), game)

        val notification = posted().single()
        assertEquals("TOR at MTL", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Notifier.GAME_START_CHANNEL, notification.channelId)
    }

    @Test
    fun theTapTargetIsExplicitAndCarriesTheGameIdAsAnExtra() {
        notifier.post(goal(), game)

        val saved = shadowOf(posted().single().contentIntent).savedIntent
        assertEquals(
            "tapping opens this app's screen, not whatever claims a scheme",
            MainActivity::class.java.name,
            saved.component?.className,
        )
        assertEquals(gameId, saved.getStringExtra(Notifier.EXTRA_GAME_ID))
        assertNull("no data URI: the id travels as an extra this app wrote", saved.data)
    }

    @Test
    fun theSameGoalTwiceReusesOneNotification() {
        val first = notifier.post(goal(away = 1, home = 0), game)
        val second = notifier.post(goal(away = 2, home = 0), game)

        assertEquals((first as NotificationOutcome.Posted).id, (second as NotificationOutcome.Posted).id)
        assertEquals("a score update replaces the line, it does not stack a second notification", 1, posted().size)
        assertEquals("TOR 2 – 0 MTL", posted().single().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun aStartAndAGoalForTheSameGameAreSeparateNotifications() {
        notifier.post(Alert.GameStart(GameId(gameId), goalBaseline = 0), game)
        notifier.post(goal(), game)

        assertEquals("the start and the goal are different news", 2, posted().size)
        assertEquals(
            "and they land on different channels",
            setOf(Notifier.GAME_START_CHANNEL, Notifier.GOALS_CHANNEL),
            posted().map { it.channelId }.toSet(),
        )
    }

    @Test
    fun notificationsTurnedOffPostNothingAndDoNotThrow() {
        shadowOf(manager).setNotificationsEnabled(false)

        val outcome = notifier.post(goal(), game)

        assertEquals(NotificationOutcome.NotPosted(NotificationOutcome.Reason.NotificationsOff), outcome)
        assertEquals(emptyList<Notification>(), posted())
    }

    @Test
    fun theRuntimePermissionMissingPostsNothingAndDoesNotThrow() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val outcome = notifier.post(goal(), game)

        assertEquals(NotificationOutcome.NotPosted(NotificationOutcome.Reason.PermissionMissing), outcome)
        assertEquals(emptyList<Notification>(), posted())
    }

    @Test
    fun aBaselineIsNotPostedEvenWithoutPermission() {
        // The answer to "is there news?" does not depend on whether the user allowed notifications.
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(NotificationOutcome.NothingToPost, notifier.post(Alert.Baseline(GameId(gameId), 3), game))
        assertEquals(emptyList<Notification>(), posted())
    }

    @Test
    fun aGoalWithNoScoreDoesNotInventOne() {
        notifier.post(Alert.Goal(GameId(gameId), goalCount = 1, awayScore = null, homeScore = null), game)

        val title =
            posted()
                .single()
                .extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
        assertEquals("TOR at MTL", title)
        assertTrue("no digits, so no score the feed did not give: $title", title!!.none { it.isDigit() })
    }

    @Test
    fun aChannelTheUserSilencedIsReportedRatherThanClaimedAsPosted() {
        // A silenced channel is importance NONE, which the app cannot set on a live channel - so it is modelled
        // the way the user does it: the channel exists at NONE before the post.
        manager.deleteNotificationChannel(Notifier.GOALS_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(Notifier.GOALS_CHANNEL, "Goals", NotificationManager.IMPORTANCE_NONE),
        )

        val outcome = notifier.post(goal(), game)

        assertEquals(NotificationOutcome.NotPosted(NotificationOutcome.Reason.NotificationsOff), outcome)
        assertEquals("nothing reaches the tray through a silenced channel", emptyList<Notification>(), posted())
        // And the other channel is untouched by it.
        assertTrue(notifier.post(Alert.GameStart(GameId(gameId), 0), game) is NotificationOutcome.Posted)
    }

    @Test
    fun postingCreatesItsOwnChannels() {
        // Nothing else in the process has run: the notifier must not depend on a caller having set up first.
        manager.deleteNotificationChannel(Notifier.GOALS_CHANNEL)
        manager.deleteNotificationChannel(Notifier.GAME_START_CHANNEL)

        val outcome = notifier.post(goal(), game)

        assertTrue("posted, not dropped for a channel that did not exist", outcome is NotificationOutcome.Posted)
        assertNotNull("the channel exists afterwards", manager.getNotificationChannel(Notifier.GOALS_CHANNEL))
    }
}
