// pluginManagement must precede the other settings blocks per Gradle's documented
// rules. Gradle 9.8.2 does not reject the reverse order, but nothing is gained by
// relying on that tolerance.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "roux-sports"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":domain")
include(":data")
