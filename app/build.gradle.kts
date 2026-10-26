import org.gradle.api.artifacts.component.ModuleComponentIdentifier

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.ktor3)

    testImplementation(libs.junit)
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

    @TaskAction
    fun verify() {
        val bannedGroups =
            setOf(
                "com.google.android.gms",
                "com.google.firebase",
                "com.google.android.play",
                "com.crashlytics",
                "com.google.android.ads",
                "com.google.ads",
                "com.applovin",
                "com.facebook.ads",
                "com.facebook.appevents",
                "com.flurry",
                "com.mixpanel",
                "com.segment",
                "com.amplitude",
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
        if (violations.isNotEmpty()) {
            throw GradleException("Proprietary runtime dependencies found: ${violations.size} banned group match(es).")
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
        }
        // Exercise the failure path without adding or downloading a real dependency.
        coordinates.addAll(
            providers.gradleProperty("roux.verifyDeps.probe").map { listOf(it) }.orElse(emptyList()),
        )
    }

tasks.named("preBuild") {
    dependsOn(verifyNoProprietaryDeps)
}
