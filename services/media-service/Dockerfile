# media-service: artwork files and thumbnails over gRPC.
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
RUN ./gradlew --no-daemon --console=plain :services:media-service:bootJar

# Health probe: calls the standard grpc.health.v1 service on the port that serves traffic.
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
# Artwork files are written to /var/lib/media (MEDIA_STORAGE_DIR), a volume in docker-compose.yml.
RUN useradd --system --uid 10001 --create-home app \
 && mkdir -p /var/lib/media && chown app /var/lib/media
COPY --from=probe /grpc_health_probe /usr/local/bin/grpc_health_probe
COPY --from=build /src/services/media-service/build/libs/media-service.jar /app/app.jar
USER app
WORKDIR /app
EXPOSE 9092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
