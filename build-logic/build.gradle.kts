plugins {
    `kotlin-dsl`
}

// The Gradle plugins the convention plugins apply. Their versions come from the version catalog.
dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.protobuf.gradle.plugin)
}
