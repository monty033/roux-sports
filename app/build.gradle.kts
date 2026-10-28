import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "net.montycasa.roux"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "net.montycasa.roux"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        // Device performance testing: the shipping code path with none of the debug runtime overhead that
        // makes every Compose scroll feel heavier than it will on a release build. Minification stays off
        // so this measures the runtime difference alone; R8 becomes its own reviewed step.
        // Signed with the debug key so it installs over the debug build - which is exactly why it must
        // never be published, and why it is not called "release".
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets.named("test") {
        // Exercise the captured corpus directly so app and data tests cannot drift apart.
        resources.directories += "../data/src/test/resources"
    }

    lint {
        // Abort the build on lint errors.
        abortOnError = true
        // Treat warnings as errors so they cannot accumulate silently.
        warningsAsErrors = true
        // Analyze dependencies too, where proprietary SDK risks actually live.
        checkDependencies = true
        // targetSdk is deliberately pinned in the catalog; bump it as a reviewed decision,
        // rather than in response to lint nagging about an older target.
        disable += "OldTargetApi"
        // Catalog pins are deliberate and reviewed; dependency currency is handled by deciding
        // to bump versions in the catalog rather than failing a build. Disabling only these
        // two checks keeps the remaining lint checks enforcing correctness issues as errors.
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }

    dependenciesInfo {
        // This embeds SDK metadata that F-Droid's scanner flags and this app does not need.
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(libs.versions.jvmTarget.get()),
        )
    }
}

dependencies {
    implementation(project(":data"))

    implementation(platform(libs.compose.bom))
    // Registers androidx.activity.ComponentActivity in the debug manifest so createComposeRule() has a
    // host activity under Robolectric. Debug-only: it is not on any shipped build's classpath, and the
    // release audit in S10.2 would catch it if it ever were.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.ktor.client.okhttp)
    // AppContainer passes an okio Path to the preference store, whose constructor takes one; okio is
    // already on the runtime classpath through DataStore and Ktor, so this is a compile-time need only.
    implementation(libs.okio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.ktor3)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(":domain:test", ":data:test")
}

abstract class VerifyNoProprietaryDeps : DefaultTask() {
    @get:Input
    abstract val coordinates: SetProperty<String>

    @get:Input
    abstract val unidentifiedArtifacts: SetProperty<String>

    @TaskAction
    fun verify() {
        val bannedGroups =
            setOf(
                // Google platform services and Firebase.
                "com.google.android.gms", // Google Play services.
                "com.google.firebase", // Firebase services.
                "com.google.android.play", // Google Play SDKs.
                // Attribution and measurement.
                "com.appsflyer", // AppsFlyer attribution.
                "com.adjust.sdk", // Adjust attribution.
                "com.kochava", // Kochava measurement.
                "io.branch", // Branch attribution and deep linking.
                "com.singular", // Singular attribution.
                "com.mparticle", // mParticle customer data collection.
                "com.tenjin", // Tenjin attribution.
                // Ad networks and mediation.
                "com.google.android.ads", // Google mobile ads.
                "com.google.ads", // Google advertising SDKs.
                "com.applovin", // AppLovin ads and mediation.
                "com.facebook.ads", // Facebook advertising.
                // Blocks the Facebook Android SDK and Audience Network wholesale: this app has no
                // Facebook login or sharing requirement and does not want their event collection.
                "com.facebook.android",
                "com.ironsource", // ironSource ads and mediation.
                "com.unity3d.ads", // Unity Ads.
                "com.vungle", // Vungle advertising.
                "com.chartboost", // Chartboost advertising.
                "com.inmobi", // InMobi advertising.
                "com.startapp", // StartApp advertising.
                "com.mopub", // MoPub ads and mediation (root covers com.mopub.mobileads).
                "com.tapjoy", // Tapjoy advertising.
                "com.adcolony", // AdColony advertising.
                "com.smaato", // Smaato advertising.
                // Analytics, telemetry and crash reporting.
                "com.facebook.appevents", // Facebook app event analytics.
                "com.flurry", // Flurry analytics.
                "com.mixpanel", // Mixpanel analytics.
                "com.segment", // Segment event collection.
                "com.amplitude", // Amplitude analytics.
                "com.braze", // Braze marketing automation and analytics.
                "com.posthog", // PostHog product analytics.
                "com.crashlytics", // Crashlytics crash reporting.
                // Datadog publishes its Android SDK under com.datadoghq, not com.datadog.
                "com.datadoghq", // Datadog RUM and telemetry.
                "io.sentry", // Sentry crash reporting and telemetry.
                "com.microsoft.appcenter", // Microsoft App Center analytics and crash reporting.
                "com.bugsnag", // Bugsnag crash reporting.
                "com.newrelic.agent", // New Relic telemetry.
                "com.optimizely", // Optimizely experimentation and analytics.
            )
        val resolvedCoordinates = coordinates.get().sorted()
        val violations =
            resolvedCoordinates.flatMap { coordinate ->
                val group = coordinate.substringBefore(':')
                bannedGroups
                    .filter { group == it || group.startsWith("$it.") }
                    .map { bannedGroup -> "$coordinate (matched banned group $bannedGroup)" }
            }
        violations.forEach { logger.error(it) }
        val unidentified = unidentifiedArtifacts.get().sorted()
        unidentified.forEach {
            logger.error(
                "VIOLATION: Runtime artifact '$it' has an origin that could not be resolved; " +
                    "it cannot be verified as free of proprietary code.",
            )
        }
        if (violations.isNotEmpty() || unidentified.isNotEmpty()) {
            throw GradleException(
                "Proprietary runtime dependency verification failed: " +
                    "${violations.size} banned group match(es), ${unidentified.size} unidentified runtime artifact(s).",
            )
        }
        logger.lifecycle("Verified ${resolvedCoordinates.size} runtime/probe coordinates: no proprietary dependencies.")
    }
}

val verifyNoProprietaryDeps =
    tasks.register<VerifyNoProprietaryDeps>("verifyNoProprietaryDeps") {
        group = "verification"
        description = "Rejects proprietary dependencies in the resolved debug and release runtime classpaths."
        // Map resolved artifacts to serializable task inputs; the action never captures Project
        // or Configuration, so the same check also runs when the configuration cache is reused.
        listOf("releaseRuntimeClasspath", "debugRuntimeClasspath").forEach { classpath ->
            coordinates.addAll(
                configurations.named(classpath).flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
                    artifacts.mapNotNull { artifact ->
                        val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                        component?.let { "${it.group}:${it.module}:${it.version}" }
                    }
                },
            )
            // A local file AAR/JAR has no module identity, so it cannot be matched against the
            // denylist. Collect it instead of dropping it, so it fails loudly rather than being
            // silently skipped - an artifact whose origin is unknown is not a verified one.
            unidentifiedArtifacts.addAll(
                configurations.named(classpath).flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
                    artifacts
                        .filter { artifact ->
                            val component = artifact.id.componentIdentifier
                            component !is ModuleComponentIdentifier && component !is ProjectComponentIdentifier
                        }.map { it.file.absolutePath }
                },
            )
        }
        // Exercise the failure path without adding or downloading a real dependency.
        coordinates.addAll(
            providers.gradleProperty("roux.verifyDeps.probe").map { listOf(it) }.orElse(emptyList()),
        )
    }

tasks.named("preBuild") {
    dependsOn(verifyNoProprietaryDeps)
}
