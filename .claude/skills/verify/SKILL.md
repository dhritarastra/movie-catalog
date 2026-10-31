---
name: verify
description: Prove nothing is broken after a change - builds and tests the backend and web app, starts the full Docker stack and runs the browser smoke test, then reports PASS or FAIL per check. Use after any code change, before any commit, or when asked "does it still work".
---

# verify

## Checks, in order

| # | Check | Command (from the repo root) | Passes when |
|---|---|---|---|
| 1 | Backend build and every backend test (Testcontainers needs Docker) | `./gradlew build` | `BUILD SUCCESSFUL` |
| 2 | Generated GraphQL types match the schema | `cd web && npm run codegen && git diff --exit-code -- src/lib/graphql/generated` | no diff |
| 3 | Web formatting and lint | `cd web && npm run lint` | exit 0 |
| 4 | Web type check | `cd web && npm run check` | `0 ERRORS` |
| 5 | Web unit tests | `cd web && npm test` | all passed |
| 6 | Stack starts and every health check passes | `docker compose up --build -d --wait` | every container `Healthy` |
| 7 | Browser smoke test through nginx | `cd web && npm run test:e2e` | `1 passed` |

## Steps

1. Run the checks in order. Stop at the first failure.
2. Report one line per check: PASS or FAIL, plus the test counts from checks 1 and 5.
3. On a failure, quote the first real error (not the whole stack trace) and name the file most
   likely at fault.
4. If a check could not run (for example Docker is not available), report it as NOT RUN with the
   reason. Never report it as passing.

## Rules

- Never report success without the passing output in front of you.
- Never weaken, skip or delete a test to make verify pass. Fix the code, or raise the problem.
- When a feature adds a page or a user-visible behaviour, extend the single flow in
  `web/e2e/smoke.spec.ts` in the same change.
- CI (`.github/workflows/ci.yml`) runs the same checks on every push. After a push, confirm
  all three jobs passed before calling the work done.
