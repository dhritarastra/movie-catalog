# Movie Catalog: Implementation Plan

Technical assessment: Svelte + TypeScript frontend, Kotlin Spring Boot services,
gRPC between services, GraphQL between frontend and backend.
Deadline: Sunday 1 November 2026, end of day, per the email. Target a submission-ready
build on Saturday 31 October; confirm the deadline timezone if needed.

## 1. Requirements, priorities and completion criteria

The assessment requires the functionality and technologies below. Architecture choices,
limits and stretch features elsewhere in this plan are implementation decisions, not
additional requirements from the assessor.

| Assessment requirement | How it will be demonstrated |
|---|---|
| List movies with artwork | Paged grid with artwork, a placeholder when absent, and movie detail pages |
| Add, delete and update movies | Working UI forms and GraphQL mutations, including validation and failure feedback |
| Upload movie artwork | GraphQL multipart mutation with upload progress, processing state and a visible final result |
| List cast and creators | People list, person filmography, and distinct cast/creator sections on movie pages |
| Add, delete and update cast and creators | Person CRUD plus UI controls to add, edit and remove movie credits |
| Search movies, cast and creators | One search screen returning movies and people, with role labels and links |
| At least 2 Spring Boot services | 3 Kotlin Spring Boot applications: gateway, catalog-service and media-service |
| Service-to-service communication via gRPC | Gateway calls catalog and media through protobuf contracts |
| Frontend-to-backend communication via GraphQL | Data queries and mutations, including uploads, use GraphQL; document the image-byte GET assumption in §3 |
| Unit tests including edge cases | Requirement-to-test checklist, service and frontend unit tests, plus integration and browser smoke tests |
| Responsive UI and sufficient progress feedback | Mobile layout; loading, searching, saving, uploading, processing, empty and error states |
| Git repository and README | Accessible repository with exact setup/run/test commands and an AI-usage summary |
| Ability to explain submitted code | Explain and debug every component, including generated and AI-assisted code |

**Delivery baseline:** all assessment requirements, basic pagination and search, validation,
safe artwork handling, the version checks described below, accessible forms, reproducible
Docker setup, meaningful tests and documentation. Keep the three-service architecture;
the assessment only requires two, so be ready to explain the chosen separation.

**Stretch only after the baseline passes:** dark mode, fuzzy search with `pg_trgm`, genre/year
filters, user-selectable sorting, optimistic UI updates and additional image formats.
Do not let these displace required workflows, edge-case tests or submission preparation.

**Completion gate:** every required workflow works through the browser and actual services;
the documented commands work from a fresh clone; CI passes; the owner can explain the
implementation and limitations. A coverage percentage alone does not establish completeness.

## 2. Architecture

```text
Browser -> nginx (static web, /graphql and /media proxies)
              -> gateway (GraphQL and image GET)
                   -> gRPC -> catalog-service -> Postgres catalog database
                   -> gRPC -> media-service   -> Postgres media database + file volume
```

- **web:** SvelteKit (Svelte 5, TypeScript), built with `adapter-static` in SPA mode.
  Runtime movie/person IDs cannot all be prerendered. Configure the fallback page and
  disable SSR for the client-rendered routes. nginx serves the fallback for app routes;
  `/graphql` and `/media` have explicit proxy routes and must never return the SPA fallback.
  In dev, a Vite proxy forwards requests. Requests use the same origin.
- **gateway:** Spring for GraphQL, multipart upload handling, `GET /media/{id}`, and gRPC clients.
  nginx exposes it to the browser; catalog and media remain on the internal Compose network.
- **catalog-service:** owns movies, people, credits, search and business validation.
  Flyway manages its schema.
- **media-service:** validates, stores, resizes, serves and deletes artwork. Flyway manages
  image metadata; original images and thumbnails live in a persistent file volume.
- **Database per service:** two databases on one Postgres container. Each service owns its
  database; there are no cross-service SQL queries or foreign keys.

## 3. Key decisions (record rationale in docs/DECISIONS.md)

1. **Version compatibility:** use the official Spring gRPC project. Prove compatible pinned
   Spring Boot, Kotlin, Spring gRPC, protobuf plugin, Gradle and Java versions on Monday.
   Use a compatible community starter only if needed; record the decision. In the same
   spike, choose how Docker checks catalog/media health (§10), since that depends on the
   Spring gRPC server setup.
2. **Uploads:** use the GraphQL multipart request spec through a small gateway adapter because
   Spring for GraphQL does not support it directly. The adapter must execute the operation
   through the GraphQL execution pipeline, preserving schema validation and error handling.
   XHR provides transfer progress. Tuesday's spike must prove this through nginx and real services.
3. **Image-byte transport assumption:** GraphQL returns `artworkUrl`; image content is fetched
   with `GET /media/{id}?variant=thumb|full`. The gateway downloads it from media over a gRPC
   server stream. Document that GraphQL carries all application operations while image bytes
   use HTTP GET. Ask Sam whether this interpretation is acceptable if clarification is needed;
   do not silently replace the upload mutation with a REST endpoint.
   Uploads use new UUIDs and immutable contents; successful responses have an explicit cache
   lifetime plus `immutable`. Do not apply long-lived caching to errors.
4. **Bounded streaming:** upload in 64 KiB gRPC chunks, enforce cumulative file limits, and
   respect stream readiness/backpressure. Configure nginx, Spring request limits and timeouts
   consistently, allowing multipart overhead above the file-size limit. Document whether the
   HTTP multipart layer buffers to disk rather than claiming the whole path is unbuffered.
5. **Avoiding N+1:** use batch mappings for credits and any nested people/movie lookups.
   Back them with batch RPCs and verify call counts for the actual list/detail queries.
6. **Full-replacement updates:** `updateMovie`/`updatePerson` replace every editable field
   with the submitted input, guarded by the expected `version` (decision 7). Edit forms always
   submit the full record, so the API needs no unchanged/set/clear distinction: a null or
   absent optional field means "no value", and an empty genre list means "no genres".
   Required fields are non-null in the schema and validated by catalog. This avoids field
   masks and omitted-vs-null input binding; the version check still prevents lost updates.
   Artwork is not part of the input and changes only through the artwork mutations.
   Nullable proto fields use proto3 `optional` so absence maps to null consistently.
7. **Concurrent edits:** movie/person updates and deletes use an expected `version` checked
   atomically in the database. Artwork attach/remove operations also require the movie version,
   increment it and return the previous artwork ID from that same transaction. Return gRPC
   `ABORTED` / GraphQL `CONFLICT` for stale versions. The UI reloads before retrying.
   Credit updates use last-write-wins for this assessment; document this limit, enforce credit
   constraints transactionally, and refresh credits after each successful mutation.
8. **Search baseline:** case-insensitive literal substring matching on movie titles and person
   names, with trimmed input, bounded query/result lengths and deterministic ordering.
   Do not treat user-entered wildcard characters as search operators. Fuzzy ranking and
   trigram indexes are stretch work; Elasticsearch is out of scope.
9. **Pagination:** offset/limit with a total count, capped page size and a stable secondary ID
   sort. Validate negative/oversized inputs and handle an empty final page after deletion.
10. **Person deletion:** block deletion while credits exist (`FAILED_PRECONDITION`), explain
    the reason, and let the user remove credits first. Removing a credit leaves the person
    intact; deleting a movie cascades only to that movie's credits.

## 4. Data model and validation

- **movie:** `id` (UUID), `title`, nullable `synopsis`, nullable `release_date`, nullable
  `runtime_minutes`, `genres` (text[]), nullable `artwork_id`, `version`, timestamps.
- **person:** `id` (UUID), `name`, nullable `bio`, nullable `birth_date`, `version`, timestamps.
- **credit:** `id` (UUID), `movie_id`, `person_id`, `type` (`CAST | DIRECTOR | WRITER | PRODUCER`),
  nullable `character_name`, `billing_order`.
  - A person can have multiple roles, including both cast and creator roles on the same movie.
  - Normalize blank character names to null; creator credits have null character names.
  - Use `UNIQUE NULLS NOT DISTINCT (movie_id, person_id, type, character_name)` with a pinned
    PostgreSQL version supporting it (15+). This also rejects duplicate creator credits.
  - Foreign keys enforce valid movies/people; deleting a movie cascades, deleting a credited
    person is restricted. Index both foreign-key columns for credit lookups.
- **artwork** (media DB): `id`, `content_type`, `size_bytes`, `width`, `height`, `sha256`, `created_at`.
- Specify and share validation rules across service/UI: trimmed nonblank titles/names,
  bounded text lengths, valid dates, positive runtime when supplied, valid roles and nonnegative
  billing order. Titles and names are not unique identifiers; legitimate duplicates are allowed.
- Basic search needs no extension for the small seeded dataset. Add GIN trigram indexes only
  if fuzzy search is implemented.

## 5. API contracts (finalize during the first implementation slice)

This is an operation inventory, not a complete executable schema. Define all referenced
input/output types, scalars, nullability, validation limits and examples before implementing
each workflow. Generate frontend types from the checked-in schema.

**GraphQL**

```graphql
scalar Upload

type Query {
  movies(page: PageInput): MoviePage!
  movie(id: ID!): Movie
  people(page: PageInput): PersonPage!
  person(id: ID!): Person
  search(query: String!, limit: Int = 10): [SearchResult!]!
}
union SearchResult = Movie | Person

type Mutation {
  # Create and update share one input type; update replaces all editable fields.
  createMovie(input: MovieInput!): Movie!
  updateMovie(id: ID!, version: Int!, input: MovieInput!): Movie!
  deleteMovie(id: ID!, version: Int!): ID!
  uploadArtwork(movieId: ID!, version: Int!, file: Upload!): Movie!
  removeArtwork(movieId: ID!, version: Int!): Movie!
  createPerson(input: PersonInput!): Person!
  updatePerson(id: ID!, version: Int!, input: PersonInput!): Person!
  deletePerson(id: ID!, version: Int!): ID!
  addCredit(input: AddCreditInput!): Credit!
  updateCredit(id: ID!, input: UpdateCreditInput!): Credit!
  removeCredit(id: ID!): ID!
}
```

**gRPC**

- `catalog.v1.MovieService`: Create, Get, GetByIds, List, Update, Delete, SetArtwork, ClearArtwork.
  Update replaces all editable fields; versioned mutations include `expected_version`.
  Set/ClearArtwork atomically return the updated movie and previous artwork ID;
  Delete returns the deleted movie ID and artwork ID.
- `catalog.v1.PersonService`: Create, Get, GetByIds, List, Update, Delete.
- `catalog.v1.CreditService`: Add, Update, Remove, GetByMovieIds, GetByPersonIds.
- `catalog.v1.SearchService`: Search.
- `media.v1.MediaService`: Upload (client stream: one metadata message, then chunks),
  Download (server stream), Delete (safe to repeat for an already-missing ID).

Define IDs, date representations, enum handling, field violations and null-to-proto mapping
consistently. Batch RPCs return keyed results with explicit handling of missing IDs.

## 6. Artwork flow and consistency

**Upload and replacement**

1. The browser checks type and size, shows a preview and sends the current movie version.
   Start with JPEG and PNG, maximum 5 MiB (5,242,880 bytes). Add WebP only after verifying
   decoding and thumbnail support. Client checks are convenience; servers enforce limits.
2. XHR reports transfer progress. After transfer reaches 100%, show "Processing artwork"
   until the GraphQL response confirms storage and attachment; transfer completion is not success.
3. The gateway validates the request and checks that the movie exists. This early check does
   not replace the atomic version/existence check when attaching the artwork.
4. Stream the file to media. Media validates stream ordering, cumulative size and file
   contents (magic bytes), rejecting SVG and spoofed files. It then reads the declared width
   and height from the image header (`ImageReader.getWidth/getHeight`) and rejects images over
   the documented dimension/pixel limit before decoding any pixels, because a small file can
   declare enormous dimensions and exhaust memory during decoding. Only then does it fully
   decode the image, rejecting corrupt files. Write to a temporary path, generate a thumbnail,
   then finalize storage and metadata. Clean up local files if validation or persistence fails.
5. Call catalog `SetArtwork(movieId, expectedVersion, newArtworkId)`.
   - On confirmed success, use the previous artwork ID returned by that transaction for
     best-effort deletion. Never choose the old ID from a stale pre-upload read.
   - On a definite business rejection, such as version conflict or missing movie, delete the
     unattached new upload on a best-effort basis. Log cleanup failures with both IDs.
   - On timeout, connection loss or another unknown outcome, the catalog may have committed.
     Do not delete the new upload or automatically replay the mutation. Retain it, log the
     uncertain outcome and have the UI reload movie state. A read can help show the current
     state but is not sufficient proof for deleting a file while the original call may still run.

**Cancellation:** offer cancel during transfer and propagate disconnect/cancellation to active
gRPC work where supported. Media closes streams and removes temporary files on cancellation.
Once processing/attachment begins, do not promise cancellation or rollback; show processing
and reconcile the result by reloading if the response is lost. A finalized unattached file
whose ID was lost to cancellation can remain orphaned; document and log this limitation.

**Remove artwork / delete movie:** commit the catalog change first, then best-effort delete
the artwork ID returned by that transaction. Apply the same unknown-outcome rule: no file
deletion based on an unconfirmed catalog mutation. Deleting a movie also deletes its credits.

**Failure policy:** broken references are worse than retained files. Failed cleanup, process
crashes and ambiguous outcomes may leave orphans. A periodic reconciliation job/outbox is out
of scope; describe this limitation without claiming that compensation guarantees no orphans.

## 7. Errors and resilience

- Map gRPC status codes to GraphQL `extensions.code` and useful UI messages:
  - `NOT_FOUND` -> `NOT_FOUND`.
  - `INVALID_ARGUMENT` -> `BAD_REQUEST`, with structured field violations.
  - `ALREADY_EXISTS` / `ABORTED` -> `CONFLICT`.
  - `FAILED_PRECONDITION` -> `FAILED_PRECONDITION`, with an actionable explanation.
  - `RESOURCE_EXHAUSTED` -> `RESOURCE_EXHAUSTED`, including upload size limits.
  - `UNAVAILABLE` / `DEADLINE_EXCEEDED` -> `SERVICE_UNAVAILABLE`; mutations may have an
    unknown outcome, so the UI must not claim that no change occurred or blindly retry.
  - Unexpected failures -> a generic `INTERNAL_ERROR`; log details without exposing stack traces.
- Every gRPC call has a deadline: initially about 2 seconds for normal calls, longer for uploads,
  with values tested through the full proxy path. Do not automatically retry writes.
- The frontend handles GraphQL `errors` even on HTTP 200, plus transport/non-JSON failures.
- Catalog owns business validation; media owns file validation. The UI repeats checks for feedback.
- Pass a correlation ID through gateway/gRPC logs. Log uncertain mutations and cleanup failures
  with movie/artwork IDs. Use health/readiness checks for Compose startup.

## 8. Frontend workflows and feedback

- **Movies:** paginated list, detail, create/edit forms and confirmed deletion. Artwork upload,
  replacement/removal and missing-artwork placeholders are available on detail/edit screens.
- **People:** list, person detail/filmography, create/edit forms and confirmed deletion.
  Show why a credited person cannot be deleted and link to the affected credits/movies.
- **Credits:** on a movie, find an existing person or create one; assign a cast/creator role;
  edit role, character and billing order; remove the credit without deleting the person.
  A person may appear in both cast and creator sections.
- **Search:** `/search?q=` and a debounced header search, with movie/person labels and role
  context. Show searching, no results and failed-search states. Ignore stale responses so a
  slower earlier query cannot overwrite newer results.
- **Feedback:** loading skeletons, disabled duplicate-submit controls, saving indicators,
  upload percentage followed by processing, confirmed success, validation errors, retryable
  read failures and reload prompts for conflicts/uncertain writes. Wait for successful writes
  before updating local state; optimistic updates are stretch work.
- **Routing:** `/movies`, `/movies/new`, `/movies/[id]`, `/movies/[id]/edit`, `/people`,
  `/people/new`, `/people/[id]`, `/people/[id]/edit` and `/search?q=`. Verify direct navigation,
  browser refresh, back/forward and not-found states in the production nginx build.
- **Quality/accessibility:** GraphQL codegen, layouts checked at 375/768/1280 px, alt text,
  labelled inputs and field errors, keyboard navigation, focus management and delete dialogs.
  Announce progress through an `aria-live` region without excessive updates.

## 9. Testing and acceptance gates

Track assessment requirement -> user workflow -> happy path -> failure/edge cases -> test
location in a small checklist. Write tests alongside each slice rather than leaving them all
until Saturday. JaCoCo and Vitest report coverage; prioritize meaningful behavior assertions.

| Layer | Tools | Required coverage |
|---|---|---|
| catalog service | JUnit 5, MockK | CRUD, validation bounds, missing IDs, person deletion restriction, movie-credit cascade, version conflicts, full-replacement updates that clear optional fields and empty the genre list |
| catalog gRPC | In-process server | Status/field-violation mapping, malformed IDs, invalid/unknown enums, optional-field presence and version arguments |
| database/search | Testcontainers Postgres | Duplicate credits with null characters, FK constraints, atomic version checks, concurrent artwork replacements, substring/case/Unicode behavior, whitespace queries, literal wildcard input, page limits and stable ordering |
| media | JUnit 5, in-process gRPC, temporary files | Valid JPEG/PNG, spoofed/corrupt/SVG files, empty/malformed streams, exact/over size limits, excessive declared dimensions rejected before full decode, chunk assembly, thumbnail output, cancellation cleanup, storage failure and unknown ID |
| gateway GraphQL | GraphQL tests with mocked gRPC clients | Schema/input validation, full-replacement input mapping (null optional fields), error mapping, nested batch call counts and mutation versions |
| gateway HTTP/upload | Running Spring HTTP integration tests plus Compose smoke test | Multipart parsing through GraphQL, invalid operations/maps, limits, confirmed-rejection cleanup, confirmed-success old-file deletion, retained uploads on timeout-after-commit, cleanup failure and cancellation |
| web | Vitest, Testing Library | Movie/person/credit forms, loading/search/saving states, stale search responses, transfer-vs-processing states, cancel/error handling, GraphQL errors on HTTP 200 and conflict reload |
| browser smoke (required, one flow) | Playwright against Docker Compose | Create movie -> upload artwork -> create/attach cast and creator -> search -> edit -> remove credits -> delete person/movie; verify visible results. This single flow is the whole browser suite; everything else is tested at lower layers |

Also verify direct-link refresh for a newly created movie/person and persistence of catalog,
artwork metadata and files after a Compose restart without deleting volumes. Check layout
and keyboard use manually. `@GraphQlTest` alone does not prove custom multipart HTTP routing.
If a stretch feature is shipped, add tests for its behavior as well.

## 10. Repo, setup, CI and README

```text
movie-catalog/
  proto/            catalog/v1, media/v1
  services/         gateway, catalog-service, media-service
  web/
  docs/             PLAN.md, ARCHITECTURE.md, DECISIONS.md, AI_USAGE.md
  docker-compose.yml, .env.example, README.md
  .github/workflows/ci.yml
```

- **Repository:** commit the plan and implementation incrementally and push them. Keep generated
  build output, local IDE files and secrets out of Git. Confirm the assessors can access the repo.
- **Gradle:** multi-module Kotlin DSL, pinned version catalog, committed wrapper and Java 21
  toolchain. If automatic JDK download is promised, configure a toolchain resolver and verify
  it; otherwise document the local JDK prerequisite. Docker builds provide their own JDK.
- **Docker:** multi-stage builds; document `docker compose up --build` and exact prerequisites.
  Use readiness checks/startup dependencies, database initialization, persistent Postgres/media
  volumes and sufficient multipart limits. The catalog/media health-check approach is chosen
  in Monday's version spike (decision 1): either a gRPC health-probe binary in the images,
  calling the standard gRPC health service, or a small actuator HTTP management port.
  Record the choice in `DECISIONS.md` and verify the check reports readiness under Compose.
- **Seed data:** a small deterministic set of movies, people and credits with placeholder
  artwork, including a person with multiple roles. Seeding must not duplicate records or
  overwrite user changes on restart. Verify seeded media IDs refer to actual stored files.
- **CI:** Gradle build/tests, frontend lint/type check/tests, Docker Compose build, then the
  single-flow browser smoke test against the started stack. Capture logs on failure and tear down.
- **README:** exact setup/run/test commands; app URL and ports; environment variables;
  seed/reset instructions (clearly label destructive volume reset); prerequisites; architecture
  and API examples; supported image types/limits; trade-offs and known limitations; and an
  honest AI-usage summary linking to `docs/AI_USAGE.md`. No undocumented manual setup steps.

## 11. Schedule (26 October to 1 November 2026)

| Day | Work and exit condition |
|---|---|
| Mon 26 Oct | Acknowledge Sam's email if not already done. Commit/push plan; pin compatible versions and choose the health-check approach; establish Gradle/proto/Compose skeleton and CI; agree input/update contracts. |
| Tue 27 Oct | Prove web -> nginx -> GraphQL -> gRPC -> catalog listing and creation. Prove multipart GraphQL upload -> media -> catalog attachment -> displayed image, including progress/processing and a failure. Establish the browser smoke harness. |
| Wed 28 Oct | Complete movie/person/credit CRUD as browser-to-database slices with forms, validation, deletion rules and tests. Implement baseline search and pagination. |
| Thu 29 Oct | All required workflows usable. Complete artwork replacement/removal, version handling, uncertainty/cleanup behavior and required feedback states. Required browser smoke passes. |
| Fri 30 Oct | Feature freeze. Fix edge cases, production routing, persistence, mobile/accessibility issues and test gaps. Run the full CI stack. Stretch work only if the baseline is already complete. |
| Sat 31 Oct | Fresh-clone rehearsal using only README; final docs/AI log; mock walkthrough; confirm repository access and final commit. Target a submission-ready build and send submission when ready. |
| Sun 1 Nov | Contingency buffer and final fixes if needed; rerun affected checks and send submission by end of day. |

If behind schedule, drop stretch work first. If Tuesday's integration spike is still broken,
stop adding features and fix that path before building further layers. Do not defer mandatory
UI, unit tests, error feedback or documentation in favor of optional features.

## 12. Out of scope and documented limitations

Authentication/authorisation, S3/object storage, an event bus/outbox or periodic orphan cleanup,
Elasticsearch, internationalisation and Kubernetes. Explain relevant trade-offs briefly;
there is no requirement to design each future feature in detail. Document local filesystem
storage, best-effort cleanup, uncertain write outcomes, credit last-write-wins behavior and
the image GET interpretation explicitly. A deployed public demo is optional, not required.

## 13. Code ownership and submission checklist

- Update `DECISIONS.md` and `AI_USAGE.md` as work proceeds. Record where AI helped, what was
  reviewed/changed and how behavior was verified. Keep the account accurate for all tools used.
- Understand and be able to modify/debug every submitted component, whether written manually,
  generated from a schema or assisted by AI. Hand-writing a few selected files is not a substitute.
- Rehearse a short demo: start from README, create a movie, upload artwork, add cast/creator,
  search, edit and delete; then show validation, a conflict and a service failure.
- Be able to explain: service boundaries; GraphQL/gRPC flow; multipart execution; file limits
  and progress; full-replacement updates; database constraints; batching; timeout-after-commit and
  cleanup; concurrency limits; test choices; AI-assisted decisions; and what would change at scale.
- Before submission, verify: fresh clone works; required tests/CI pass; schema/proto/build files
  and assets are committed and pushed; no secrets or accidental local files are included;
  README and AI summary are complete; repository access works for assessors.
- Reply to the original email with the repository URL, final branch/commit or tag, quick-start
  instructions and any material limitations. Acknowledge receipt separately if still outstanding.

## 14. Technical references

- [Spring GraphQL transports and multipart support](https://docs.spring.io/spring-graphql/reference/transports.html)
- [Protobuf field presence (mapping nullable fields)](https://protobuf.dev/programming-guides/field_presence/)
- [PostgreSQL constraints and NULLS NOT DISTINCT](https://www.postgresql.org/docs/17/ddl-constraints.html)
- [gRPC status codes, including ambiguous mutation timeouts](https://grpc.io/docs/guides/status-codes/)
- [SvelteKit SPA fallback configuration](https://svelte.dev/docs/kit/single-page-apps)
