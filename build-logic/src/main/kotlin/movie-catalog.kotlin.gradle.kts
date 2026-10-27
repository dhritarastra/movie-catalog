// Kotlin on Java 21 with JUnit 5, for every Kotlin module.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Treat Spring's nullability annotations as Kotlin nullability, put annotations on
        // constructor properties where both the parameter and the property can take them, and
        // keep annotations on type arguments in the bytecode, so Bean Validation sees rules on
        // list elements such as List<@Size(max = 50) String>.
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property", "-Xemit-jvm-type-annotations")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
