buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Worktrees live under .worktrees/, inside this directory, so a plain glob reaches them and
    // formats another branch's working tree - which then fails this branch's own gate. They are
    // separate checkouts with their own gate; leave them alone.
    val shared =
        listOf(
            "**/build/**",
            ".worktrees/**",
        )
    kotlin {
        target("**/*.kt")
        targetExclude(shared)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude(shared)
        ktlint(libs.versions.ktlint.get())
    }
}
