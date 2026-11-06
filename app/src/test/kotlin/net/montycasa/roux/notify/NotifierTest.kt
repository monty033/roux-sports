package net.montycasa.roux.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import net.montycasa.roux.MainActivity
import net.montycasa.roux.domain.model.FinalKind
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

    private fun intermission(period: Int = 1) =
        Alert.Intermission(
            gameId = GameId(gameId),
            period = period,
            awayAbbrev = "TOR",
            homeAbbrev = "MTL",
            awayScore = 2,
            homeScore = 1,
        )

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
    fun aPermissionRevokedBetweenThePreflightAndThePostPostsNothingAndDoesNotThrow() {
        // Simulate the race denyPermissions cannot: the preflight check sees the grant, then the check
        // immediately before notify sees it revoked. The second call is the one this guard exists for.
        var permissionChecks = 0
        val revokingContext =
            object : ContextWrapper(application) {
                override fun checkSelfPermission(permission: String): Int {
                    if (permission == Manifest.permission.POST_NOTIFICATIONS && ++permissionChecks > 1) {
                        return PackageManager.PERMISSION_DENIED
                    }
                    return super.checkSelfPermission(permission)
                }
            }
        val racingNotifier = Notifier(revokingContext)

        val outcome = racingNotifier.post(goal(), game)

        assertEquals(NotificationOutcome.NotPosted(NotificationOutcome.Reason.PermissionMissing), outcome)
        assertEquals("a revoked grant must not be claimed as a post", emptyList<Notification>(), posted())
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

    private val shortNamed =
        game.copy(
            state = GameState.Final,
            home = TeamRef(TeamKey("MTL"), name = "Montréal Canadiens", shortName = "Canadiens"),
            away = TeamRef(TeamKey("TOR"), name = "Toronto Maple Leafs", shortName = "Maple Leafs"),
        )

    private fun final(kind: FinalKind?) = Alert.Final(GameId(gameId), awayScore = 3, homeScore = 2, finalKind = kind)

    private fun titleOf(notification: Notification): String? = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Test
    fun theFinalChannelIsCalledFinalScoresAndIsDefaultImportance() {
        val channel = manager.getNotificationChannel("final_scores")

        assertNotNull("a final-scores channel, so finals can be silenced on their own", channel)
        assertEquals("final_scores", Notifier.FINAL_CHANNEL)
        assertEquals("Final scores", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("When a game you follow ends", channel.description)
    }

    @Test
    fun eachAlertTypeLandsOnItsOwnChannel() {
        notifier.post(Alert.GameStart(GameId(gameId), goalBaseline = 0), shortNamed)
        notifier.post(goal(), shortNamed)
        notifier.post(intermission(), shortNamed)
        notifier.post(final(FinalKind.REG), shortNamed)

        val channelByTitle = posted().associate { titleOf(it) to it.channelId }
        assertEquals(
            mapOf(
                "TOR at MTL" to "game_start",
                "TOR 2 – 1 MTL" to "goals",
                "1st Intermission · TOR 2 – 1 MTL" to "intermissions",
                "Final: Maple Leafs 3, Canadiens 2" to "final_scores",
            ),
            channelByTitle,
        )
    }

    @Test
    fun theIntermissionChannelIsItsOwnPromise() {
        val channel = manager.getNotificationChannel("intermissions")

        assertNotNull("an intermissions channel, so breaks can be silenced on their own", channel)
        assertEquals("intermissions", Notifier.INTERMISSION_CHANNEL)
        assertEquals("Intermissions", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("When a game you follow reaches an intermission.", channel.description)
    }

    @Test
    fun anIntermissionStatesThePeriodAndScoreOnItsOwnChannel() {
        notifier.post(intermission(period = 1), game)

        val notification = posted().single()
        assertEquals("1st Intermission · TOR 2 – 1 MTL", titleOf(notification))
        assertEquals("End of the 1st period.", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notifier.INTERMISSION_CHANNEL, notification.channelId)
    }

    @Test
    fun theNextIntermissionReplacesTheLastOneForTheSameGame() {
        val first = notifier.post(intermission(period = 1), game) as NotificationOutcome.Posted
        val second = notifier.post(intermission(period = 2), game) as NotificationOutcome.Posted

        assertEquals("the break is the news, not the history of breaks", first.id, second.id)
        assertEquals("2nd Intermission · TOR 2 – 1 MTL", titleOf(posted().single()))
        assertTrue(notifier.canPost(intermission(period = 3)))
    }

    @Test
    fun aFinalCarriesTheScoreAndTheKindWhenTheKindIsKnown() {
        notifier.post(final(FinalKind.OT), shortNamed)
        assertEquals("Final/OT: Maple Leafs 3, Canadiens 2", titleOf(posted().single()))
    }

    @Test
    fun aShootoutFinalSaysSoAndARegulationFinalSaysNeither() {
        notifier.post(final(FinalKind.SO), shortNamed)
        assertEquals("Final/SO: Maple Leafs 3, Canadiens 2", titleOf(posted().single()))

        // The same id, so the second post replaces the first: what remains is the regulation wording alone.
        notifier.post(final(FinalKind.REG), shortNamed)
        assertEquals("Final: Maple Leafs 3, Canadiens 2", titleOf(posted().single()))
    }

    @Test
    fun aFinalWithNoKindIsAPlainFinalAndInventsNone() {
        notifier.post(final(null), shortNamed)

        assertEquals("Final: Maple Leafs 3, Canadiens 2", titleOf(posted().single()))
    }

    @Test
    fun aFinalFallsBackToTheFullNameThenTheAbbreviationWhenThereIsNoShortName() {
        notifier.post(final(null), game)
        assertEquals("Final: Toronto Maple Leafs 3, Montréal Canadiens 2", titleOf(posted().single()))

        val bare = game.copy(home = TeamRef(TeamKey("MTL")), away = TeamRef(TeamKey("TOR")))
        notifier.post(final(null), bare)
        assertEquals("Final: TOR 3, MTL 2", titleOf(posted().single()))
    }

    @Test
    fun aFinalReplacesNeitherAGoalNorAStartNotification() {
        val start = notifier.post(Alert.GameStart(GameId(gameId), goalBaseline = 0), shortNamed) as NotificationOutcome.Posted
        val goal = notifier.post(goal(), shortNamed) as NotificationOutcome.Posted
        val final = notifier.post(final(FinalKind.OT), shortNamed) as NotificationOutcome.Posted

        assertEquals("three different pieces of news", 3, posted().size)
        assertEquals("and three different ids", 3, setOf(start.id, goal.id, final.id).size)
        assertEquals(
            setOf("TOR at MTL", "TOR 2 – 1 MTL", "Final/OT: Maple Leafs 3, Canadiens 2"),
            posted().map { titleOf(it) }.toSet(),
        )
    }

    @Test
    fun aSilencedFinalChannelIsNotPostableAndDoesNotSilenceTheOthers() {
        manager.deleteNotificationChannel(Notifier.FINAL_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(Notifier.FINAL_CHANNEL, "Final scores", NotificationManager.IMPORTANCE_NONE),
        )

        assertEquals(false, notifier.canPost(final(null)))
        assertEquals(true, notifier.canPost(goal()))
        assertEquals(
            NotificationOutcome.NotPosted(NotificationOutcome.Reason.NotificationsOff),
            notifier.post(final(null), shortNamed),
        )
        assertEquals(emptyList<Notification>(), posted())
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
