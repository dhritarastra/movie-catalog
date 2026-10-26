# Movie Catalog

A movie catalog with artwork, cast and creators. It has a Svelte + TypeScript frontend and
Kotlin Spring Boot services: the frontend talks to the backend over GraphQL, and the
services talk to each other over gRPC.

> **Status: in progress.** The build, gRPC contracts, service skeletons, draft GraphQL schema,
> Postgres and CI are in place. Features land slice by slice; see the schedule in
> [docs/PLAN.md](docs/PLAN.md#11-schedule-26-october-to-1-november-2026).

## Architecture

```text
Browser -> nginx (static web, /graphql and /media proxies)
              -> gateway (GraphQL and image GET)
                   -> gRPC -> catalog-service -> Postgres catalog database
                   -> gRPC -> media-service   -> Postgres media database + file volume
```

| Module | What it is |
|---|---|
| `proto/` | Shared `.proto` contracts; generates Java messages and gRPC stubs |
| `services/catalog-service` | Movies, people, credits and search (gRPC server) |
| `services/media-service` | Artwork storage, validation and thumbnails (gRPC server) |
| `services/gateway` | GraphQL API for the web app; calls the other services over gRPC |
| `web/` | Svelte frontend (added in a later slice) |

## Prerequisites

- **Docker** with Compose. This works with Docker Desktop or Colima; see the Colima note below.
- **JDK 17 or newer** to run Gradle. The build compiles and tests with Java 21, which Gradle
  downloads automatically if it is not installed.

## Run

```bash
cp .env.example .env    # optional: change local ports and passwords
docker compose up -d --wait
./gradlew build
```

Start the services in separate terminals:

```bash
./gradlew :services:catalog-service:bootRun
./gradlew :services:media-service:bootRun
./gradlew :services:gateway:bootRun
```

| Component | Port |
|---|---|
| Postgres | 5432 (`POSTGRES_PORT`) |
| catalog-service (gRPC) | 9091 |
| media-service (gRPC) | 9092 |
| gateway (HTTP, GraphQL at `/graphql`) | 8080 |

To reset the database, run `docker compose down -v`. **This deletes all data in the Postgres
volume.**

### Colima

Colima doesn't use the default Docker socket, so Testcontainers needs two variables before
`./gradlew build`:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

If `docker compose` reports an unknown command, use `docker-compose` instead. Alternatively,
add Homebrew's plugin folder to `cliPluginsExtraDirs` in `~/.docker/config.json`.

## Tests

`./gradlew build` runs every backend test. The tests that need Postgres start their own
database with Testcontainers, so Docker must be running. CI runs the same build on each push
(`.github/workflows/ci.yml`).

## Documentation

- [docs/PLAN.md](docs/PLAN.md): scope, architecture, contracts, testing gates and schedule
- [docs/DECISIONS.md](docs/DECISIONS.md): decisions and their trade-offs
- [docs/AI_USAGE.md](docs/AI_USAGE.md): how AI tools were used and how their output was checked

## How AI tools were used

Claude Code was used for planning, version research and generating code. A second agent,
Astra, reviewed the plan. Every change is checked by building and testing it, and by my own
review. The full log is in [docs/AI_USAGE.md](docs/AI_USAGE.md).
