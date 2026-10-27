package net.montycasa.roux.ui.scores

import android.content.Context
import coil3.ImageLoader
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.montycasa.roux.AppContainer
import net.montycasa.roux.RouxApp
import okio.Path.Companion.toPath
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Instant

/** Replaces only the container inputs; the real application's singleton factory still builds Coil. */
class ScoresIntegrationApp : RouxApp() {
    /** Each test must observe its own requests, including URLs another test answered with a 404. */
    override fun newImageLoader(context: Context): ImageLoader =
        super
            .newImageLoader(context)
            .newBuilder()
            .diskCache(null)
            .build()

    var offline = false
    var serveLogos = false
    val requests = CopyOnWriteArrayList<Url>()

    private var activeContainer: AppContainer? = null

    override val container: AppContainer
        get() = activeContainer ?: newContainer().also { activeContainer = it }

    /** A restart keeps only disk state; callers dispose the old host before using the new container. */
    suspend fun restart(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
        container.close()
        activeContainer = openAfterClose(scope)
    }

    /**
     * Opens a store over the files the closed container was using.
     *
     * `close()` waits for the old store's coroutines; it cannot wait for DataStore to drop its claim on the
     * file, and the refusal is raised at first use rather than at construction - `AppContainer`'s own KDoc and
     * `aSecondContainerOverTheSameFilesDirIsRefused` both pin that. So the claim is made here, explicitly, and
     * retried across the window. Production opens one container per process and never reopens, so only tests
     * meet this; if the window ever outlasts the retries, that is a failure, not something to paper over.
     */
    private suspend fun openAfterClose(scope: CoroutineScope): AppContainer {
        repeat(50) {
            val candidate = newContainer(scope)
            try {
                candidate.preferences.getFavourites()
                return candidate
            } catch (refusal: IllegalStateException) {
                if (!refusal.isClaimRefusal()) throw refusal
                runCatching { candidate.close() }
                delay(10)
            }
        }
        error("the previous container still claimed the preference store after 500 ms")
    }

    /** DataStore's wording for one live instance per file, so an unrelated failure is never retried. */
    private fun IllegalStateException.isClaimRefusal(): Boolean = message?.contains("multiple DataStores active", ignoreCase = true) == true

    private fun newContainer(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): AppContainer = buildContainer(scope)

    private fun buildContainer(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): AppContainer =
        AppContainer(
            scope = scope,
            filesDir = filesDir.absolutePath.toPath(),
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("${ScoreFixture.date}T20:00:00Z")
                },
            engine =
                MockEngine { request ->
                    requests += request.url
                    if (offline) throw IOException("offline cold start")
                    when (request.url.host) {
                        "api-web.nhle.com" -> {
                            respond(
                                when (request.url.encodedPath) {
                                    "/v1/score/${ScoreFixture.date}" -> ScoreFixture.bytes
                                    "/v1/standings/${ScoreFixture.date}" -> ScoreFixture.bytes("standings.json")
                                    "/v1/standings-season" -> ScoreFixture.bytes("standings-season.json")
                                    else -> error("Unexpected API path: ${request.url.encodedPath}")
                                },
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "assets.nhle.com" -> {
                            respond(
                                """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><path fill="red" d="M0 0h32v32H0z"/></svg>""",
                                if (serveLogos) HttpStatusCode.OK else HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, "image/svg+xml"),
                            )
                        }

                        else -> {
                            error("Unexpected host: ${request.url.host}")
                        }
                    }
                },
        )
}

/** The existing data corpus is a test resource in :app too; no duplicate capture or rewritten date. */
internal object ScoreFixture {
    fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/fx/2026-10-26/$name")) {
            "Missing captured fixture: $name"
        }.use { it.readBytes() }

    val bytes = bytes("score-gameday.json")
    private val body = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    val date = body.getValue("currentDate").jsonPrimitive.content
    val games = body.getValue("games").jsonArray.map { it.jsonObject }
}
