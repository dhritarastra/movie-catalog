plugins {
    id("movie-catalog.kotlin")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-bom").get()))
    implementation(project(":proto"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("springmockk").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
