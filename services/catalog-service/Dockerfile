# Built from the repository root, which has build-logic and proto.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY proto proto
COPY services services
RUN ./gradlew --no-daemon --console=plain :services:catalog-service:bootJar

# grpc_health_probe for the health check, pinned by version and checksum.
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
RUN useradd --system --uid 10001 --create-home app
COPY --from=probe /grpc_health_probe /usr/local/bin/grpc_health_probe
COPY --from=build /src/services/catalog-service/build/libs/catalog-service.jar /app/app.jar
USER app
WORKDIR /app
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
