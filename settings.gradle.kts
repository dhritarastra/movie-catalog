pluginManagement {
    // Convention plugins shared by every module (build-logic/src/main/kotlin).
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets the Java 21 toolchain be downloaded automatically when it is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "movie-catalog"

include(
    "proto",
    "services:catalog-service",
    "services:media-service",
    "services:gateway",
)
