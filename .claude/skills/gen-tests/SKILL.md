---
name: gen-tests
description: Write tests for a Kotlin or Svelte change with the edge-case checklist applied - one test class per production class, boundaries, invalid input, missing and malformed IDs, version conflicts and failure paths. Use while implementing a slice, or when asked to add or review tests.
---

# gen-tests

## Where each kind of test goes

| Production class | Test | Runs against |
|---|---|---|
| JPA repository | `@DataJpaTest` | Postgres in Testcontainers, so constraints and collation are real |
| Service | plain JUnit with MockK | mocks; covers rules, conflicts and not-found |
| gRPC endpoint | in-process gRPC server (`@CatalogGrpcTest`) | the real service and database |
| GraphQL controller | `ExecutionGraphQlServiceTester` with the gRPC clients mocked | the gateway's schema and error mapping |
| Validation rules on an input class | a `jakarta.validation.Validator` | the annotations alone |
| Web logic (`*.ts`) | Vitest in Node | pure functions |
| Whole user flow | the single Playwright flow | the Docker stack |

Name a test after the behaviour it proves: `` `a stale version is a conflict and changes nothing` ``.

## Edge-case checklist

Go through every line for the code under test; skip only the ones that cannot apply.

- **Text limits:** at the limit passes, one past fails; blank and whitespace-only; leading and
  trailing spaces are trimmed; non-ASCII text (accents, emoji).
- **Numbers and dates:** zero, negative, lowest and highest allowed; impossible dates
  (`2016-02-30`); dates in the future where they make no sense.
- **IDs:** unknown (NOT_FOUND) is different from malformed (BAD_REQUEST / INVALID_ARGUMENT).
- **Versions:** a stale version is a conflict and changes nothing; an omitted version (0) is
  invalid; deleting twice reports NOT_FOUND.
- **Updates replace every field:** an omitted optional field is cleared, not kept.
- **Paging:** offset below 0 and limit outside 1 to the maximum are rejected, never adjusted;
  an empty last page; a stable order for equal sort keys.
- **Errors across services:** UNAVAILABLE and DEADLINE_EXCEEDED map to SERVICE_UNAVAILABLE, and
  the UI never claims that nothing changed.
- **UI:** loading, empty, error-with-retry and not-found states; double submit; a slow response
  (hold it with `page.route` in the smoke test rather than sleeping).

## Rules

- A test for a bug must fail without the fix. Check it by putting the old code back once.
- Assert on outcomes the user or caller sees, not on private details.
- Never delete or weaken an existing test to make a change pass.
