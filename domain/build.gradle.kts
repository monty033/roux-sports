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
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("roux.rootDir", rootDir.absolutePath)
}
