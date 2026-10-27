// The build's shared conventions, as precompiled script plugins under src/main/kotlin.
// It reads the same version catalog as the main build, so every version is pinned in one place.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
