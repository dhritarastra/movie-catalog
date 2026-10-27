# gateway: GraphQL for the web app, and image downloads over HTTP.
# Build context is the repository root, because the build needs the shared Gradle conventions
# (build-logic) and the gRPC contracts (proto). Uses no BuildKit-only features.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY proto proto
# Every service is copied because settings.gradle.kts includes them all; only this one is built.
COPY services services
RUN ./gradlew --no-daemon --console=plain :services:gateway:bootJar

# The health check (docker-compose.yml) uses curl, which this image already has.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app
COPY --from=build /src/services/gateway/build/libs/gateway.jar /app/app.jar
USER app
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
