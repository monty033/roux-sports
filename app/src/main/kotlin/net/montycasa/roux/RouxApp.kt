package net.montycasa.roux

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.prefs.DataStorePreferencesRepository
import net.montycasa.roux.data.repo.gameDetailRepository
import net.montycasa.roux.data.repo.scoresRepository
import net.montycasa.roux.data.repo.standingsRepository
import net.montycasa.roux.domain.repo.GameDetailRepository
import net.montycasa.roux.domain.repo.PreferencesRepository
import net.montycasa.roux.domain.repo.ScoresRepository
import net.montycasa.roux.domain.repo.StandingsRepository
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * Application entry point. Owns exactly one [AppContainer] for the process.
 *
 * The container is `by lazy` on purpose: DataStore permits one live instance per file per process and
 * refuses a second one at first use, so construction has to happen once and be shared. Building it eagerly in
 * `onCreate` would also be correct; lazily, a process that never touches prefs or the cache never opens them.
 */
open class RouxApp :
    Application(),
    SingletonImageLoader.Factory {
    /** Overridable so integration tests replace the engine while exercising the production wiring. */
    open val container: AppContainer by lazy {
        AppContainer(
            filesDir = filesDir.absolutePath.toPath(),
            engine = OkHttp.create(),
            version = versionName(),
        )
    }

    /**
     * Coil shares the data client's engine and NHL host boundary. Supplying the fetcher explicitly
     * prevents Coil from choosing a client of its own for team logos.
     */
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            .serviceLoaderEnabled(false)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = container.httpClient))
                add(SvgDecoder.Factory())
            }.build()

    /**
     * The User-Agent carries the app version so an NHL-side complaint can be traced to a build.
     * `getPackageInfo(String, Int)` is deprecated on API 33 in favour of the `PackageInfoFlags`
     * overload, which needs API 33 while minSdk is 31 - so the old call is the correct one here.
     */
    @Suppress("DEPRECATION")
    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: UNKNOWN_VERSION

    private companion object {
        const val UNKNOWN_VERSION = "dev"
    }
}

/**
 * Manual dependency injection for the whole app: one place where the network engine, the clock, the cache
 * and the preference store are chosen, so every other type can take them as parameters.
 *
 * The engine and the clock are constructor parameters rather than defaults read from the platform, because
 * a test that cannot substitute them cannot observe anything except the real NHL and the real time.
 *
 * [preferences] is deliberately a `val`, so this container cannot hand two different stores to two callers:
 * one instance per container is what keeps the single-DataStore rule true in practice. The rule itself is
 * DataStore's - it refuses a second live instance over one file at first use, not at construction, which is
 * what `aSecondContainerOverTheSameFilesDirIsRefused` pins.
 */
class AppContainer(
    filesDir: Path,
    engine: HttpClientEngine,
    val clock: Clock = Clock.System,
    version: String = "dev",
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val httpClient: HttpClient = NhlHttpClientFactory.create(engine, version)

    /** Undocumented NHL API behind the allowlist that [httpClient] enforces. */
    val api: NhlApi = NhlApi(httpClient)

    val cacheDirectory: Path = filesDir / "nhl-cache"

    val cache: ResponseCache =
        ResponseCache(
            directory = cacheDirectory,
            clock = clock,
        )

    val preferences: PreferencesRepository =
        DataStorePreferencesRepository(
            directory = filesDir / "datastore",
            scope = scope,
        )

    val scores: ScoresRepository = scoresRepository(api = api, cache = cache, clock = clock)

    val standings: StandingsRepository = standingsRepository(api = api, cache = cache, clock = clock)

    /** The game page's three endpoints, each cached on its own so a landing-page burst cannot evict them. */
    val gameDetail: GameDetailRepository =
        gameDetailRepository(api = api, cache = cache, clock = clock)

    /**
     * Closes the HTTP client and cancels this container's scope, waiting for the preference store's
     * coroutines to finish. A closed container's repositories and client must not be used again, and the
     * [scope] belongs to this container, so it must not be shared with another one.
     *
     * This waits for the scope, not for DataStore to drop its claim on the file: that happens just after
     * the coroutines complete, so a same-process reopen can still be refused for a moment. The app opens
     * one container per process and never reopens, so only tests meet that window - and they retry, as
     * `DataStorePreferencesRepositoryTest.openWhenReleased` documents.
     *
     * The client is closed in a `finally` because a caller cancelled while the join is suspended would
     * otherwise leave it open.
     */
    suspend fun close() {
        try {
            scope.coroutineContext.job.cancelAndJoin()
        } finally {
            httpClient.close()
        }
    }
}
