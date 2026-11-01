# Built from the repository root, which has build-logic and proto.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY proto proto
COPY services services
RUN ./gradlew --no-daemon --console=plain :services:gateway:bootJar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app
COPY --from=build /src/services/gateway/build/libs/gateway.jar /app/app.jar
USER app
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
