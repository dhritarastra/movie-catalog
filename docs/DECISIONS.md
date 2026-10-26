# Decisions

Short records of the choices behind this codebase: what was decided, why, and what it costs.
The overall plan is in [PLAN.md](PLAN.md); this file records how it was carried out.

## D1. Version set: Spring Boot 4.1.2 with its built-in gRPC starters

**Decision.** Build on Spring Boot 4.1.2 and use its first-party starters
`spring-boot-starter-grpc-server` and `spring-boot-starter-grpc-client`. Let Boot's BOM manage
every version it can. Pin only what it cannot manage, in `gradle/libs.versions.toml`.

| Component | Version | Source |
|---|---|---|
| Spring Boot | 4.1.2 | pinned |
| Kotlin | 2.3.21 | matches Boot's managed `kotlin.version` |
| Spring gRPC | 1.1.2 | managed by Boot |
| grpc-java / `protoc-gen-grpc-java` | 1.84.1 | managed by Boot; plugin pinned to match |
| protobuf-java / `protoc` | 4.36.2 | managed by Spring gRPC; `protoc` pinned to match |
| Spring GraphQL | 2.0.6 | managed by Boot |
| Testcontainers | 2.0.5 | managed by Boot |
| protobuf Gradle plugin | 0.10.0 | pinned |
| Gradle | 9.8.2 | wrapper, with distribution checksum |
| Java | 21 | Gradle toolchain; downloaded automatically through the foojay resolver |

**Why.** Spring Boot 4.1 ships gRPC support built on Spring gRPC 1.1. The separate
`spring-grpc-spring-boot-starter` stops at 1.0.x, which targets Boot 4.0. Using Boot's own
starters means one BOM decides the gRPC, protobuf and Spring versions together, so there is
no compatibility matrix to maintain. Kotlin stays on the version Boot is tested with, rather
than the newest 2.4.x.

**How it was checked.** The managed versions were read from the published
`spring-boot-dependencies` and `spring-grpc-dependencies` POMs on Maven Central. Then
`./gradlew build` compiled the protos and all three services and ran their tests.

## D2. Container health checks use the standard gRPC health service

**Decision.** Docker checks catalog-service and media-service by calling the standard
`grpc.health.v1.Health` service on their gRPC port. The services get no extra HTTP port.
The gateway is an HTTP application already, so it uses Actuator's `/actuator/health`.

**Status.** The gRPC health service is in place and tested. `services/Dockerfile` installs
`grpc_health_probe` v0.4.59 in each service image, checked against the SHA-256 checksums
published with that release, and Compose starts each service only once its dependencies
report healthy.

**Why.**
- The probe exercises the port that actually serves traffic.
- Spring Boot's gRPC server publishes the health service and refreshes it from application
  health every few seconds (`spring.grpc.server.health.schedule.*`). That health includes
  the database once `spring-boot-health` is on the classpath.
- An Actuator HTTP port would add a second embedded server to each gRPC service just for
  health checks.

**How it was checked.** `GrpcHealthTest` in both services starts the app against a
Testcontainers Postgres. It waits for `SERVING`, stops the database, and then waits for
`NOT_SERVING`.

**Finding.** Spring Boot only starts the gRPC server once the application defines at least
one gRPC service (`@ConditionalOnBean(BindableService)`). The skeleton services therefore
register `MovieService` and `MediaService` classes whose methods still return
`UNIMPLEMENTED`.

## D3. Java gRPC stubs on both sides

**Decision.** Generate grpc-java stubs only. Do not also generate grpc-kotlin coroutine stubs.

**Why.**
- The gateway is a blocking Spring MVC application, so blocking client stubs fit it.
- Streaming uses `StreamObserver` on the server. For uploads the gateway uses the async stub
  and writes each chunk from gRPC's on-ready callback, so a slow media service applies
  backpressure instead of the gateway buffering the file. Downloads read the blocking stub's
  iterator straight into the HTTP response.
- One set of generated code serves client and server, and it keeps the build to a single
  protoc plugin.

**Cost.** Server streaming code is more verbose than it would be with Kotlin `Flow`. Revisit
this if that becomes a problem in the media service.

## D4. Contract conventions (proto and GraphQL)

- **IDs** are UUID strings.
- **Calendar dates** are ISO-8601 strings (`yyyy-MM-dd`), validated by the catalog.
  This avoids extra date types and scalar adapters.
- **Nullable fields** use proto3 `optional`, so "no value" stays distinct from an empty
  string or zero.
- **Enums** start with an `*_UNSPECIFIED = 0` value. The catalog rejects it as `INVALID_ARGUMENT`.
- **Batch RPCs** return maps keyed by the requested ID, and each one documents how missing IDs
  are represented.
- **Artwork bytes** never travel in a GraphQL or catalog message. They are streamed only to
  and from media-service.
- **The proto files live in one `proto` module.** Every service compiles against the same
  generated classes.

## D5. Full-replacement updates guarded by a version

**Decision.** `updateMovie` and `updatePerson` replace every editable field and require the
`version` the client last read. A mismatch returns `ABORTED`, which the gateway reports as
`CONFLICT`.

**Why.** The edit forms always send the whole record. Replacing all fields removes the need
for field masks and for telling "omitted" from "null" during input binding. The version check
still stops one edit from silently overwriting another. See [PLAN.md](PLAN.md) §3, decision 6.

**Versions start at 1.** Proto3 sends 0 for an omitted `int32`, so an omitted
`expected_version` and an explicit 0 are indistinguishable. Starting versions at 1 and
rejecting `expected_version < 1` as `INVALID_ARGUMENT` means a request that forgets the
version is refused instead of being compared against a stored version. This was chosen over
`optional int32` because it closes the same gap without a presence check in every handler.
The `movie` and `person` tables will enforce it with `CHECK (version >= 1)` when the migrations
are written.

## D6. One Postgres container, one database and role per service

**Decision.** Compose runs a single Postgres 17 container. An init script creates `catalog`
and `media` roles and databases, and revokes the default `CONNECT` privilege from `PUBLIC`.

**Why.** Each service owns its data. Neither can connect to the other's database, which was
checked by attempting it with each role. One container keeps the local setup light.
Postgres 17 also supports `UNIQUE NULLS NOT DISTINCT`, which the credit constraint needs.

## D7. Pagination rejects out-of-range values instead of clamping

**Decision.** `PageRequest` requires `offset >= 0` and `1 <= limit <= 100`. Anything else is
`INVALID_ARGUMENT`, which the gateway reports as `BAD_REQUEST`. The catalog never clamps.
The gateway applies the default page size (20, from the GraphQL schema) before calling the
catalog, including when a client sends `limit: null`. This refines the "capped page size" in
[PLAN.md](PLAN.md) §3, decision 9: the cap is enforced by rejecting values, not by adjusting them.

**Why.** Suppose the catalog silently cut a requested limit of 500 down to 100. The gateway
would report 500 back, and a client computing the next offset as `offset + limit` would skip
records 100 to 499. Rejecting out-of-range values keeps the page returned identical to the
page requested, without adding a "limit actually used" field to every list response. It also
settles what `limit = 0` means: proto3 sends 0 when the field is unset, and that is now an
error rather than a hidden default.

**Not affected.** Search is not paginated, so the catalog still caps its `limit`. A shorter
result list cannot cause records to be skipped.

## D8. Plain SQL through JdbcClient, not JPA

**Decision.** Catalog and media read and write with Spring's `JdbcClient` and hand-written SQL.
Rows are mapped to Kotlin data classes by small functions next to the queries.

**Why.** The schema is small and leans on Postgres features: `text[]` genres, `SELECT ... FOR UPDATE`
for the artwork version check, and later `UNIQUE NULLS NOT DISTINCT`. With plain SQL every query
the service runs is visible in one file, there is no session or lazy loading to reason about, and
the locking is explicit. The cost is writing the row mapping by hand.

## D9. GraphQL multipart uploads through a router function

**Decision.** `GraphQlMultipartHandler` implements the GraphQL multipart request spec. It is
registered as a router function for `POST /graphql` with `multipart/form-data`, ordered ahead of
Spring for GraphQL's own route, puts each file into the operation's variables, and then runs the
operation through Spring's `WebGraphQlHandler`.

**Why.** The assessment asks for frontend-to-backend communication over GraphQL, so the upload is
a GraphQL mutation rather than a separate REST endpoint. Running it through `WebGraphQlHandler`
means the `Upload` scalar, schema validation and error resolvers behave exactly as for JSON.

**Finding.** A plain `@PostMapping("/graphql")` never runs: Spring for GraphQL's router function
matches the path first and answers any non-JSON body with 415. Ordering a narrower router function
ahead of it fixes that. Batched operations (a JSON array in `operations`) are not supported.

## D10. What upload progress means

The browser's progress bar measures bytes sent to the server it is talking to. Behind nginx that
is the whole story only up to nginx: nginx receives the full request before passing it on, and
Spring writes multipart files to temporary storage before the resolver runs. So the UI treats
"100% sent" as the start of **processing** (forwarding, validation, storage and attaching), not as
success, and only reports success when the GraphQL response confirms it. Cancel is offered only
while bytes are still being sent.

The 5 MiB limit is enforced at every layer, each answering in the same JSON error shape: the
browser before sending, nginx (`client_max_body_size 6m`, leaving room for the other parts),
Spring's multipart limits, the gateway, and media as chunks arrive.

## D11. Artwork storage

**Decision.** Media stores each artwork's original and a 400px-wide thumbnail on a volume, under
`<storage-dir>/<artwork id>/`, with metadata in its own database. Files are written to a temporary
folder first and moved into place only after validation; the database row is written last.

**Why.** A row never points at missing files, and a half-written upload is never served. Each
upload gets a new ID, so image URLs never change content and can be cached for a year.

**Limitations.** Files whose deletion fails are left behind and logged; there is no clean-up job.
JPEGs in CMYK colour cannot be decoded by the JDK's image library and are rejected as corrupt.

## D12. The web app

**Decision.** SvelteKit 3 with Svelte 5, built as a static single-page app (`adapter-static` with an
`index.html` fallback) and served by nginx. Pages fetch their data in the component rather than in
SvelteKit `load` functions. GraphQL types are generated from the gateway's schema and committed.

**Why.**
- Movie IDs are only known at runtime, so nothing can be prerendered.
- Fetching in the component lets each page show its own loading skeleton, empty state and
  error with Retry, and abort a request when the user moves on, so a slow earlier response
  never overwrites newer data.
- Committing the generated types keeps the Docker build independent of the backend schema file;
  CI regenerates them and fails if they differ.
