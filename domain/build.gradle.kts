plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
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

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

lint {
    abortOnError = true
    warningsAsErrors = true
    // Catalog pins are deliberate and reviewed; dependency currency is handled by deciding
    // to bump versions in the catalog rather than failing a build. Disabling only these
    // two checks keeps the remaining lint checks enforcing correctness issues as errors.
    disable += setOf("GradleDependency", "NewerVersionAvailable")
}

dependencies {
    // kotlinx-coroutines is multiplatform, so observing flows does not tie :domain to Android or the JVM.
    // It is a real dependency rather than a transitive one: the contract itself declares Flow now.
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.compiler.embeddable)
}

// Protect G4's compiler lock against android.*; :data must adopt this check when S0.6 creates it.
val verifyNoAndroidOnClasspath =
    tasks.register("verifyNoAndroidOnClasspath") {
        group = "verification"
        description = "Verifies that Android platform jars cannot enable android.* references."
        // Configuration cache restores configuration providers as file-collection providers.
        val compileClasspath: Provider<out FileCollection> = configurations.compileClasspath
        doLast {
            val androidJars =
                compileClasspath.get().files.filter {
                    it.name == "android.jar" || (it.name.startsWith("android-") && it.name.endsWith(".jar"))
                }
            check(androidJars.isEmpty()) {
                "Android platform jar(s) on :domain compileClasspath: ${androidJars.joinToString { it.absolutePath }}. " +
                    "The G4 guard relies on android.* being uncompilable here; an Android jar on this classpath " +
                    "silently disables the lock."
            }
        }
    }

tasks.check {
    dependsOn(verifyNoAndroidOnClasspath)
}

tasks.withType<Test>().configureEach {
    systemProperty("roux.rootDir", rootDir.absolutePath)
}
