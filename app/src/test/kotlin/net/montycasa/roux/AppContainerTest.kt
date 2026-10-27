package net.montycasa.roux

import android.Manifest
import android.content.pm.PackageManager
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import net.montycasa.roux.data.net.HostNotAllowedException
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
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
     * The NHL allowlist belongs to the client, not the caller: anything outside `api-web.nhle.com` and
     * `assets.nhle.com` throws before the request reaches the engine, so a typo or a later feature pointing at
     * another host fails here rather than quietly contacting it.
     */
    @Test
    fun httpClientRefusesAHostOutsideTheAllowlist() {
        val thrown =
            assertThrows(HostNotAllowedException::class.java) {
                runBlocking { app.container.httpClient.get("https://example.com") }
            }

        assertEquals("example.com", thrown.host)
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
    fun theAppsOwnManifestAsksForInternetOnly() {
        assertEquals(setOf(Manifest.permission.INTERNET), declaredPermissions())
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
        "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )
