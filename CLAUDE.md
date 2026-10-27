# Movie Catalog: working conventions for Claude Code

A technical assessment: a SvelteKit + TypeScript web app and Kotlin Spring Boot services. The web
app talks GraphQL to the gateway; the services talk gRPC to each other. The author must be able
to explain every line, so keep code plain and conventional, and comment the "why", not the "what".

## Ground rules

- Never invent requirements. The brief asks for: movies with artwork; add, edit and delete movies;
  artwork upload; cast and creators with add, edit and delete; search across movies and people;
  at least two Spring Boot services, gRPC between them, GraphQL to the frontend; unit tests with
  edge cases; a responsive UI with progress feedback; a README on setup, running and AI usage.
- Conventional Spring and Kotlin over clever: Spring Data JPA for persistence, Bean Validation
  annotations for input rules, `@ConfigurationProperties` for anything tunable.
- One top-level class, interface or object per Kotlin file, named after it.
- Limits and timeouts that an operator might tune live in `application.yml`, not in code.
  Field rules that mirror a database constraint stay as validation annotations.
- Every edit and delete of a movie or person carries the version the user saw; a stale
  version is a conflict, never a silent overwrite.
- A test class for every production class with behaviour: repository (`@DataJpaTest` against
  Postgres), service (MockK), gRPC endpoint and GraphQL controller.
- Nothing is committed unless the `verify` skill has passed. Commit and push only when the
  author says so. No attribution lines in commit messages.
- `docs/` is the author's private working folder (plan, decisions, usage log). It is not in
  the repository; never link to it from code or the README.

## Where things go

| What | Where |
|---|---|
| gRPC contracts | `proto/src/main/proto/` |
| Catalog (movies, people, credits, search) | `services/catalog-service` |
| Media (artwork files, thumbnails) | `services/media-service` |
| GraphQL gateway and image downloads | `services/gateway` |
| Shared Gradle conventions | `build-logic/` |
| Database migrations | `src/main/resources/db/migration/` in each service (Flyway) |
| Web app | `web/`, operations in `web/src/lib/graphql/operations.ts` |
| Browser smoke test (one flow, extended per feature) | `web/e2e/smoke.spec.ts` |

## Process

1. Work one slice at a time: a feature end to end, from migration to page, through the
   `ship-slice` skill.
2. Write tests with the `gen-tests` checklist alongside the code, not afterwards.
3. Run the `verify` skill after every change. Report its output, never a summary of it.
4. Ask the `code-reviewer` agent for a review. The author decides which findings to apply.
   A finding is reproduced first, then fixed with a test that fails without the fix.
5. After each step, report what changed, how it was checked, what was not checked, and
   what is next.

## AI tooling in this repo

- Skills (`.claude/skills/`): `verify`, `gen-tests`, `ship-slice`.
- Agent (`.claude/agents/`): `code-reviewer`, read-only.
