# One image definition for every Spring Boot service; SERVICE picks which one is packaged.
# Build context is the repository root, because services compile against the shared proto module.
# Uses no BuildKit-only features, so it builds with the classic builder too.

# Builds every service's jar. This stage does not depend on SERVICE, so all three images share
# the same cached layer and the Gradle build runs once per source change.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY proto proto
COPY services services
RUN ./gradlew --no-daemon --console=plain bootJar

# Health probe for the gRPC services: calls the standard grpc.health.v1 service (DECISIONS D2).
# Pinned by version and by the checksums published with the release.
FROM eclipse-temurin:21-jre AS probe
ARG PROBE_VERSION=v0.4.59
RUN arch="$(dpkg --print-architecture)" \
 && case "${arch}" in \
      amd64) sha=b74ea9e3fe0e4fb19008a77422b6475e29141b30833a6f82c18941bf6eb6a136 ;; \
      arm64) sha=f6d3e6a62734a66bab0584bd008897c2b7183a6cf0a5003082820ae2970f24d7 ;; \
      *) echo "unsupported architecture ${arch}" && exit 1 ;; \
    esac \
 && curl -fsSL -o /grpc_health_probe "https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${PROBE_VERSION}/grpc_health_probe-linux-${arch}" \
 && echo "${sha}  /grpc_health_probe" | sha256sum -c - \
 && chmod +x /grpc_health_probe

FROM eclipse-temurin:21-jre
ARG SERVICE
RUN useradd --system --uid 10001 --create-home app \
 && mkdir -p /var/lib/media && chown app /var/lib/media
COPY --from=probe /grpc_health_probe /usr/local/bin/grpc_health_probe
COPY --from=build /src/services/${SERVICE}/build/libs/${SERVICE}.jar /app/app.jar
USER app
WORKDIR /app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
