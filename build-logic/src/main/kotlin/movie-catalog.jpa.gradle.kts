// A service that stores its data in Postgres through Spring Data JPA, with Flyway migrations
// and Bean Validation, and tests against a real Postgres in Testcontainers.
plugins {
    id("movie-catalog.spring-service")
    // No-argument constructors for JPA entities, which Kotlin classes do not have otherwise.
    id("org.jetbrains.kotlin.plugin.jpa")
    id("org.jetbrains.kotlin.plugin.allopen")
}

// Kotlin classes are final; Hibernate needs entities it can subclass for lazy loading.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // Feeds application health (including the database) into the gRPC health service.
    implementation("org.springframework.boot:spring-boot-health")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
