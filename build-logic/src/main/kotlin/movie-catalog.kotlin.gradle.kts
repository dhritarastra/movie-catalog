// Kotlin on Java 21 with JUnit 5, for every Kotlin module.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Treat Spring's nullability annotations as Kotlin nullability, and put annotations on
        // constructor properties where both the parameter and the property can take them.
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
