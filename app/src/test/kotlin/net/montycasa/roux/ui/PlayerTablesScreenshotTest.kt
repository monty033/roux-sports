package net.montycasa.roux.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.domain.model.GoalieLine
import net.montycasa.roux.domain.model.PlayerRef
import net.montycasa.roux.domain.model.SkaterLine
import net.montycasa.roux.ui.detail.PlayerTables
import net.montycasa.roux.ui.theme.RouxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player tables live only on a club destination, which the page-level matrix never opens, so the Forwards /
 * Defensemen split has no golden anywhere else. This records the whole block on its own at phone width, in both
 * themes, so the three sub-tables and their repeated column header rows are reviewable as an image.
 *
 * The data is the home side of the real UTA @ PHI boxscore fixture, so the rows and their ordering are the feed's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTablesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun playerTablesInBothThemes() {
        val club = clubFromFixture("homeTeam")
        val dark = mutableStateOf(false)
        rule.setContent {
            RouxTheme(dark = dark.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        PlayerTables(goalies = club.goalies, forwards = club.forwards, defense = club.defense)
                    }
                }
            }
        }
        listOf(false, true).forEach { isDark ->
            val theme = if (isDark) "dark" else "light"
            rule.runOnIdle { dark.value = isDark }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/matrix/player-tables-$theme.png")
        }
    }

    private fun clubFromFixture(side: String): ClubTables {
        val root = Json.parseToJsonElement(fixtureText()).jsonObject
        val players = root["playerByGameStats"]!!.jsonObject
        val team = players[side]!!.jsonObject
        val abbrev = root[side]!!.jsonObject["abbrev"]!!.jsonPrimitive.content
        val dressed =
            team["goalies"]
                ?.jsonArray
                .orEmpty()
                .map { it.jsonObject }
        val played =
            dressed
                .filter { it["starter"]?.jsonPrimitive?.booleanOrNull == true }
                .ifEmpty { dressed.filter { it.text("toi").toSeconds() > 0 } }
        return ClubTables(
            forwards = team.lines("forwards", abbrev),
            defense = team.lines("defense", abbrev),
            goalies = played.map { it.toGoalieLine(abbrev) },
        )
    }

    private fun JsonObject.lines(
        key: String,
        abbrev: String,
    ): List<SkaterLine> =
        this[key]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val line = element.jsonObject
                val name = line.text("name", "default")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SkaterLine(
                    skater = PlayerRef(name = name, teamAbbrev = abbrev),
                    position = line.text("position").orEmpty(),
                    goals = line.int("goals"),
                    assists = line.int("assists"),
                    points = line.int("points"),
                    plusMinus = line.int("plusMinus"),
                    shotsOnGoal = line.int("sog"),
                    penaltyMinutes = line.int("pim"),
                    toiSeconds = line.text("toi").toSeconds(),
                    faceoffFraction = line["faceoffWinningPctg"]?.jsonPrimitive?.doubleOrNull,
                )
            }

    private fun JsonObject.toGoalieLine(abbrev: String): GoalieLine {
        val name = text("name", "default").orEmpty()
        return GoalieLine(
            goalie = PlayerRef(name = name, teamAbbrev = abbrev),
            saves = int("saves"),
            shotsAgainst = int("shotsAgainst"),
            goalsAgainst = int("goalsAgainst"),
            toiSeconds = text("toi").toSeconds(),
        )
    }

    private fun JsonObject.text(vararg keys: String): String? {
        var value = this
        keys.dropLast(1).forEach { key -> value = value[key]?.jsonObject ?: return null }
        return value[keys.last()]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun String?.toSeconds(): Long {
        val parts = this?.trim().orEmpty().split(':')
        if (parts.size != 2) return 0
        val minutes = parts[0].toLongOrNull() ?: return 0
        val seconds = parts[1].toLongOrNull() ?: return 0
        return minutes * 60 + seconds
    }

    private fun fixtureText(): String =
        checkNotNull(javaClass.classLoader?.getResource("fx/2026-11-01/gc-uta-phi-boxscore.json"))
            .readText()

    private data class ClubTables(
        val forwards: List<SkaterLine>,
        val defense: List<SkaterLine>,
        val goalies: List<GoalieLine>,
    )
}
