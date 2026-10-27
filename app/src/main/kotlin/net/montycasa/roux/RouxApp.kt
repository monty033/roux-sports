package net.montycasa.roux

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import net.montycasa.roux.data.cache.ResponseCache
import net.montycasa.roux.data.net.NhlApi
import net.montycasa.roux.data.net.NhlHttpClientFactory
import net.montycasa.roux.data.prefs.DataStorePreferencesRepository
import net.montycasa.roux.data.repo.scoresRepository
import net.montycasa.roux.data.repo.standingsRepository
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
class RouxApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            filesDir = filesDir.absolutePath.toPath(),
            engine = OkHttp.create(),
            version = versionName(),
        )
    }

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
        )

    val scores: ScoresRepository = scoresRepository(api = api, cache = cache, clock = clock)

    val standings: StandingsRepository = standingsRepository(api = api, cache = cache, clock = clock)
}
