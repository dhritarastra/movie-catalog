# Movie Catalog

A movie catalog with artwork, cast and creators. It has a Svelte + TypeScript frontend and
Kotlin Spring Boot services: the frontend talks to the backend over GraphQL, and the
services talk to each other over gRPC.

> **Status: in progress.** You can list, create and view movies and upload artwork through the
> whole stack. Cast, creators, search, editing and deleting are next; see the schedule in
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
| `services/gateway` | GraphQL API and image downloads for the web app; calls the other services over gRPC |
| `web/` | SvelteKit single-page app, served by nginx in Docker |

## Run everything with Docker

Needs only **Docker with Compose** (Docker Desktop or Colima).

```bash
docker compose up --build --wait
```

Then open **http://localhost:3000**. The first build takes a few minutes while Gradle and npm
download dependencies. Services start in order as each reports healthy.

To stop, run `docker compose down`. To also **delete all data** (movies and uploaded images),
run `docker compose down -v`.

Only the web app (port 3000) and Postgres (5432, for development) are published, and only on
`127.0.0.1`. To change ports or passwords, copy `.env.example` to `.env` and edit it.

## Develop locally

Needs **JDK 17+** to run Gradle (the build uses Java 21, downloaded automatically if missing),
**Node.js 22**, and Docker.

```bash
docker compose up -d --wait postgres
```

Then start each service in its own terminal:

```bash
./gradlew :services:catalog-service:bootRun
```

```bash
./gradlew :services:media-service:bootRun
```

```bash
./gradlew :services:gateway:bootRun
```

```bash
cd web && npm install && npm run dev
```

Open **http://localhost:5173**. Vite forwards `/graphql` and `/media` to the gateway on port 8080.

| Component | Port |
|---|---|
| web (Vite dev server) | 5173 |
| gateway (HTTP; GraphQL at `/graphql`, images at `/media/{id}`) | 8080 |
| catalog-service (gRPC) | 9091 |
| media-service (gRPC) | 9092 |
| Postgres | 5432 (`POSTGRES_PORT`) |

`bootRun` does not read `.env`; only Compose does. The services' defaults match `.env.example`.
If you change the Postgres port or a password, export the matching variables before `bootRun`,
for example `CATALOG_DB_URL=jdbc:postgresql://localhost:5433/catalog`. Each service lists its
variables in `src/main/resources/application.yml`. Uploaded images go to `./media-data`
unless `MEDIA_STORAGE_DIR` is set.

After changing `services/gateway/src/main/resources/graphql/schema.graphqls`, regenerate the web
app's types with `cd web && npm run codegen`.

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

```bash
./gradlew build
```

runs every backend test. Tests that need Postgres start their own database with Testcontainers,
so Docker must be running.

```bash
cd web && npm test
```

runs the web unit tests. `npm run check` type-checks and `npm run lint` checks formatting and
lint rules.

CI runs all of these on pushes to `main` and on pull requests (`.github/workflows/ci.yml`).

## API at a glance

GraphQL at `POST /graphql`; the full schema is
[`schema.graphqls`](services/gateway/src/main/resources/graphql/schema.graphqls).

```graphql
query {
  movies(page: { offset: 0, limit: 12 }) {
    totalCount
    items { id title releaseDate artworkUrl(variant: THUMBNAIL) }
  }
}
```

Artwork is uploaded with the
[GraphQL multipart request spec](https://github.com/jaydenseric/graphql-multipart-request-spec):

```bash
curl http://localhost:3000/graphql \
  -F 'operations={"query":"mutation($f: Upload!){ uploadArtwork(movieId: \"<id>\", version: 1, file: $f){ version artworkUrl } }","variables":{"f":null}}' \
  -F 'map={"0":["variables.f"]}' \
  -F '0=@poster.png;type=image/png'
```

Images are JPEG or PNG, up to 5 MB and 8000×8000 pixels. Errors carry `extensions.code`
(`BAD_REQUEST` with `fieldErrors`, `NOT_FOUND`, `CONFLICT`, `RESOURCE_EXHAUSTED`,
`SERVICE_UNAVAILABLE`, ...).

## Documentation

- [docs/PLAN.md](docs/PLAN.md): scope, architecture, contracts, testing gates and schedule
- [docs/DECISIONS.md](docs/DECISIONS.md): decisions, trade-offs and known limitations
- [docs/AI_USAGE.md](docs/AI_USAGE.md): how AI tools were used and how their output was checked

## How AI tools were used

Claude Code was used for planning, version research and generating code. Astra and Codex
reviewed the plan and the code. Every change is checked by building and testing it, by running
it, and by my own review. The full log is in [docs/AI_USAGE.md](docs/AI_USAGE.md).
