plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // -Xemit-jvm-type-annotations lets Bean Validation see rules like List<@Size(max = 50) String>.
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property", "-Xemit-jvm-type-annotations")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
