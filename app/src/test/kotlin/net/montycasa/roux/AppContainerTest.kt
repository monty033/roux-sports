package net.montycasa.roux

import android.Manifest
import android.content.pm.PackageManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.net.ApiResult
import net.montycasa.roux.data.net.EspnHostNotAllowedException
import net.montycasa.roux.data.net.HostNotAllowedException
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createTempDirectory

/**
 * S3.5: the container is the only place the app chooses its engine, clock, cache and preference store, so
 * these tests are about the wiring being what it claims - not about the features built on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppContainerTest {
    private val app: RouxApp get() = RuntimeEnvironment.getApplication() as RouxApp

    /**
     * The container owns the preference store's scope now, so leaving it open leaks a coroutine and its file
     * claim into whichever test runs next.
     */
    @After
    fun closeContainer() {
        runBlocking { app.container.close() }
    }

    /** Unit tests run with the module directory as the working directory, so the source manifest is here. */
    private val projectDir: File get() = File(System.getProperty("user.dir") ?: ".")

    /**
     * The end of the chain the plan's release acceptance names: the version in the APK reaches the User-Agent.
     * The data layer already pins its own formatting, so what is unproven without this is the arrow from the
     * package to the container - and the fallback, which must not be what a real build carries.
     */
    @Test
    fun thePackagedVersionIsWhatTheContainerCarries() {
        val packaged =
            app.packageManager
                .getPackageInfo(app.packageName, 0)
                .versionName

        assertTrue("the package manager must report a version", !packaged.isNullOrBlank())
        assertEquals(packaged, app.container.version)
        assertTrue(
            "a release build must not carry the fallback: ${app.container.version}",
            app.container.version != "dev",
        )
        // The release versions are dates: YYYY.MM.DD, and YYYY.MM.DD.N for a later release on the same day.
        // Pinned here so a debug marker ("dev", a build suffix) cannot pass as a release version.
        assertTrue(
            "the version must look like a release version, not a debug marker: ${app.container.version}",
            Regex("""\d{4}\.\d{2}\.\d{2}(\.\d)?""").matches(app.container.version),
        )
    }

    /**
     * One container per process. A second would open the preferences file twice and DataStore refuses that,
     * so a lazy field that built a new instance per call would fail at the first screen that touched it.
     */
    @Test
    fun containerIsTheSameInstanceAcrossCalls() {
        assertSame(app.container, app.container)
    }

    /** The cheap half of the single-DataStore invariant; [aSecondContainerOverTheSameFilesDirIsRefused] is the real one. */
    @Test
    fun preferencesIsTheSameInstanceTwice() {
        assertSame(app.container.preferences, app.container.preferences)
    }

    /**
     * The invariant behind those identity assertions: DataStore refuses a second live instance over one file,
     * so a mis-wired container that built a store per screen fails loudly instead of silently splitting the
     * user's favourites across two files.
     *
     * The refusal is raised on first use rather than at construction - the first read here activates the file,
     * and the second store's read is what gets rejected. Asserting on construction alone would be a test that
     * cannot fail, which is what the first version of this test turned out to be.
     */
    @Test
    fun aSecondContainerOverTheSameFilesDirIsRefused() {
        runBlocking { app.container.preferences.getFavourites() }

        val second =
            AppContainer(
                filesDir = app.filesDir.absolutePath.toPath(),
                engine = OkHttp.create(),
            )

        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { second.preferences.getFavourites() }
            }
        } finally {
            // The refused container still owns a scope and a client; leaving them running leaks into later tests.
            runBlocking { second.close() }
        }
    }

    /**
     * The NHL allowlist belongs to the client, not the caller: anything outside `api-web.nhle.com`,
     * `assets.nhle.com` and `api.nhle.com` throws before the request reaches the engine, so a typo or a later
     * feature pointing at another host fails here rather than quietly contacting it.
     */
    @Test
    fun httpClientRefusesAHostOutsideTheAllowlist() {
        val thrown =
            assertThrows(HostNotAllowedException::class.java) {
                runBlocking { app.container.httpClient.get("https://example.com") }
            }

        assertEquals("example.com", thrown.host)
    }

    /**
     * The two leagues' clients refuse each other's hosts as the container wires them, not just as their factories
     * are written. The engine's recorded hosts are asserted so that a blocked request which quietly succeeded
     * cannot pass: an exception alone says nothing about whether the engine was reached first.
     */
    @Test
    fun theContainersTwoClientsRefuseEachOthersHosts() {
        withMockedContainer { container, engine ->
            runBlocking {
                assertTrue(container.espnApi.scoreboard("2026-11-22") is ApiResult.Success)
                assertEquals(listOf("site.api.espn.com"), engine.requestHistory.map { it.url.host })

                val nhlRefusal =
                    assertThrows(HostNotAllowedException::class.java) {
                        runBlocking { container.httpClient.get("https://site.api.espn.com/x") }
                    }
                assertEquals("site.api.espn.com", nhlRefusal.host)

                val espnRefusal =
                    assertThrows(EspnHostNotAllowedException::class.java) {
                        runBlocking { container.espnHttpClient.get("https://api-web.nhle.com/v1/score/2026-11-22") }
                    }
                assertEquals("api-web.nhle.com", espnRefusal.host)

                // Neither refusal reached the engine: the one request it saw is still the allowed one.
                assertEquals(listOf("site.api.espn.com"), engine.requestHistory.map { it.url.host })

                // And each client still reaches its own host through the same shared engine.
                assertEquals(
                    200,
                    container.httpClient
                        .get("https://api-web.nhle.com/v1/score/2026-11-22")
                        .status.value,
                )
                assertEquals(
                    listOf("site.api.espn.com", "api-web.nhle.com"),
                    engine.requestHistory.map { it.url.host },
                )
            }
        }
    }

    /**
     * Closing the container releases the ESPN client as well as the NHL one. A closed client's job completes, so
     * `isActive` is the observable that moves; it would stay true for the ESPN client if `close()` forgot it.
     */
    @Test
    fun closingTheContainerClosesBothClients() {
        withMockedContainer { container, _ ->
            assertTrue("the NHL client must start open", container.httpClient.coroutineContext.job.isActive)
            assertTrue("the ESPN client must start open", container.espnHttpClient.coroutineContext.job.isActive)

            runBlocking { container.close() }

            assertFalse("close() must close the NHL client", container.httpClient.coroutineContext.job.isActive)
            assertFalse("close() must close the ESPN client", container.espnHttpClient.coroutineContext.job.isActive)
        }
    }

    /**
     * A second container over its own temporary directory, so it cannot collide with the application's container
     * over [RouxApp.filesDir]. The engine answers 200 to everything and keeps the request history.
     */
    private fun withMockedContainer(block: (AppContainer, MockEngine) -> Unit) {
        val directory = createTempDirectory("roux-container-test").toFile()
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val container = AppContainer(filesDir = directory.absolutePath.toPath(), engine = engine, version = "test")

        try {
            block(container, engine)
        } finally {
            // Closing twice is harmless, so a test that already closed the container may close it again here.
            runBlocking { container.close() }
            engine.close()
            directory.deleteRecursively()
        }
    }

    /**
     * The NFL repository is built on the ESPN client, not the NHL one, and it is a separate instance from the NHL
     * loader over the same cache. The request the mock engine sees is the proof of which client it is wired to.
     */
    @Test
    fun theNflScoresRepositoryReadsThroughTheEspnClient() {
        withMockedContainer { container, engine ->
            runBlocking { container.nflScores.loadDay("2026-11-22", "2026-11-22") }

            assertEquals(listOf("site.api.espn.com"), engine.requestHistory.map { it.url.host })
            assertNotSame(
                "the two leagues must not share one repository instance",
                container.scores,
                container.nflScores,
            )
        }
    }

    /** The cache lives in app-private storage, so a stale response cannot be read or planted by another app. */
    @Test
    fun cacheDirectoryIsUnderFilesDir() {
        val directory = app.container.cacheDirectory.toString()

        assertTrue(
            "cache directory $directory must sit under ${app.filesDir.absolutePath}",
            directory.startsWith(app.filesDir.absolutePath + "/"),
        )
        assertTrue("cache directory should be the nhl-cache folder, was $directory", directory.endsWith("/nhl-cache"))
    }

    /**
     * The app's own manifest asks for INTERNET and nothing else.
     *
     * Parsed as XML rather than matched with a regex: `uses-permission` may carry other attributes
     * (`maxSdkVersion`, `tools:node`) and there is a second spelling, `uses-permission-sdk-23`. A regex that
     * only accepts `android:name` as the sole attribute passes a manifest that quietly gained a permission,
     * which is the one thing this test exists to prevent.
     *
     * Asserted against the source manifest because the merged set is not this app's decision: WorkManager
     * contributes WAKE_LOCK, RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE for the background notification work
     * planned later, and OkHttp contributes ACCESS_NETWORK_STATE.
     * [theMergedManifestAsksForNothingUnexpected] bounds that side with an allowlist.
     */
    @Test
    fun theAppsOwnManifestAsksForInternetAndNotifications() {
        // POST_NOTIFICATIONS is here on purpose: the app's only outward capability is still the network, and the
        // notification permission is a local one the user grants to this app alone.
        assertEquals(
            setOf(Manifest.permission.INTERNET, Manifest.permission.POST_NOTIFICATIONS),
            declaredPermissions(),
        )
    }

    /**
     * Nothing arrives from a dependency that the app did not ask for and has not agreed to.
     *
     * An allowlist, not a denylist of scary names: a denylist only catches permissions somebody thought of, so
     * a dependency adding `READ_MEDIA_IMAGES` or `BLUETOOTH_SCAN` would slip past.
     *
     * Compared as FULL names. Reducing each entry to its last dot segment would accept any package's
     * `INTERNET` or `WAKE_LOCK`, and would accept a dynamic-receiver permission minted by another app - which is
     * exactly the "scoped to this package" claim the allowlist makes.
     *
     * Limit: what is observable here is the app's merged manifest as this test runtime sees it. A release
     * build's merge is a different artifact; S10's release audit covers that side.
     */
    @Suppress("DEPRECATION")
    @Test
    fun theMergedManifestAsksForNothingUnexpected() {
        val merged =
            app.packageManager
                .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toSet()
                .orEmpty()

        val unexpected = unexpectedMergedPermissions(merged, app.packageName)
        assertEquals(
            "unexpected merged permission(s) $unexpected - justify each in allowedMergedPermissions, or keep " +
                "it out with tools:node=\"remove\" in the app manifest",
            emptySet<String>(),
            unexpected,
        )
        assertTrue("INTERNET must survive the merge", Manifest.permission.INTERNET in merged)
        assertTrue(
            "POST_NOTIFICATIONS must survive the merge, or the runtime grant can never be requested",
            Manifest.permission.POST_NOTIFICATIONS in merged,
        )
    }

    /**
     * The comparison, on synthetic input. The real merged set cannot exercise this: every entry in it happens
     * to be unique on its last dot segment, so a version that compared bare segments would pass against the app
     * today and only fail once a dependency declared a permission under another package. Testing the rule with
     * input that distinguishes it is what makes the full-name comparison load-bearing.
     */
    @Test
    fun aPermissionFromAnotherPackageIsUnexpected() {
        val foreign = "com.other.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

        assertEquals(
            setOf(foreign),
            unexpectedMergedPermissions(setOf(Manifest.permission.INTERNET, foreign), APP_PACKAGE),
        )
    }

    /** The names this app's own dependencies contribute are accepted, and the list is exact. */
    @Test
    fun theDocumentedMergedSetIsAcceptedAndNothingElseIs() {
        assertEquals(emptySet<String>(), unexpectedMergedPermissions(documentedMergedSet(), APP_PACKAGE))
        assertEquals(
            setOf("android.permission.CAMERA"),
            unexpectedMergedPermissions(documentedMergedSet() + "android.permission.CAMERA", APP_PACKAGE),
        )
    }

    private fun documentedMergedSet(): Set<String> =
        setOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.FOREGROUND_SERVICE,
            // Declared by this app: local notifications, granted by the user to this app only.
            Manifest.permission.POST_NOTIFICATIONS,
            "$APP_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )

    /** Parses the app's own manifest, so what this app asks for is checked as declared rather than as merged. */
    private fun declaredPermissions(): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(File(projectDir, "src/main/AndroidManifest.xml"))
        return listOf("uses-permission", "uses-permission-sdk-23")
            .flatMap { tag ->
                val nodes = document.getElementsByTagName(tag)
                (0 until nodes.length).map { nodes.item(it) }
            }.mapNotNull { element ->
                element.attributes?.getNamedItemNS("http://schemas.android.com/apk/res/android", "name")?.nodeValue
            }.toSet()
    }
}

/** What this app's package is, for the package-scoped permission AndroidX generates. */
private const val APP_PACKAGE = "net.montycasa.roux"

/**
 * Merged permissions that no entry of [allowedMergedPermissions] accounts for.
 *
 * Compared as full names on purpose. A permission is `package.NAME`, so reducing to the last segment would
 * accept another package's `INTERNET`, `WAKE_LOCK` or dynamic-receiver permission - and the whole point of the
 * allowlist is that the entry states whose permission it is.
 */
private fun unexpectedMergedPermissions(
    merged: Set<String>,
    packageName: String,
): Set<String> = merged - allowedMergedPermissions(packageName)

/**
 * A ceiling on the merged manifest, entry by entry:
 *
 * - INTERNET - ours, the only one this app declares.
 * - ACCESS_NETWORK_STATE - OkHttp/Coil checking connectivity before a request.
 * - WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE - WorkManager, which S6 uses for the
 *   best-effort favourite-game notifications.
 * - `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` - generated by AndroidX for runtime-registered
 *   receivers on API 33+, scoped to this package by construction.
 *
 * That is the whole merged set, checked rather than assumed: six entries, none of them sensitive.
 */
private fun allowedMergedPermissions(packageName: String): Set<String> =
    setOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.FOREGROUND_SERVICE,
        // Declared by this app for local notifications; granted by the user to this app only.
        Manifest.permission.POST_NOTIFICATIONS,
        "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )
