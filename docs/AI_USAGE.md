# AI usage log

The assessment allows AI tools, provided I can explain every line I submit. This log records
where AI tools were used, what they produced, and how the result was checked. It is updated
with each piece of work.

## Tools

- **Claude Code** (Anthropic): planning, research and code generation, driven by my prompts.
- **Astra:** a second AI agent, used to review and strengthen the plan.
- **Codex** (OpenAI): independent review of the Day 1 scaffolding.

## Log

| Date | Tool | What it did | How it was checked |
|---|---|---|---|
| 26 Oct | Claude Code | Read the brief and drafted the implementation plan | Reviewed by Astra and by me. The scope cuts were my decision |
| 26 Oct | Astra | Reviewed the plan: added the baseline/stretch split and the unknown-outcome rules for uploads; tightened the data constraints and testing gates | Reviewed by Claude Code and by me. Four further changes were applied (full-replacement updates, checking image dimensions before decoding, choosing the health check during the version spike, a single-flow browser test) |
| 26 Oct | Claude Code | Looked up current versions on Maven Central and chose a compatible set (DECISIONS.md D1) | `./gradlew build` passes on the pinned set |
| 26 Oct | Claude Code | Generated the Gradle multi-module build, proto contracts, service skeletons, draft GraphQL schema, Compose setup and CI workflow | Build and tests pass locally. Database isolation was checked by hand with each role |
| 26 Oct | Claude Code | Wrote `GrpcHealthTest` to prove the health-check approach (DECISIONS.md D2) | The test stops Postgres and observes `NOT_SERVING` |
| 26 Oct | Claude Code | Built the first end-to-end slice: movie create/list in catalog, artwork upload/download in media, GraphQL and the multipart upload in the gateway, the SvelteKit pages and upload UI, Dockerfiles, nginx and the full Compose stack | 85 automated tests (68 backend, 17 web), plus requests sent by hand through the running services and checks in a real browser at desktop and phone widths. These found three bugs, all fixed: Spring's GraphQL route answered multipart requests with 415; the create form treated a runtime of 0 as empty; and `#lib` imports needed explicit `.ts` extensions in SvelteKit 3 |
| 27 Oct | Claude Code | Built movie editing and deleting and artwork removal: catalog Update/Delete/ClearArtwork, the gateway mutations with file clean-up, the edit page, confirmation dialogs, and smoke-test steps including a real version conflict | 88 backend tests, 17 web unit tests, and the extended Playwright flow against the full stack; on-disk artwork files compared against the database afterwards (no orphans). The tests caught one bug before commit: a Kotlin builder field shadowing the value being copied, so every artwork change reported a previous artwork of `""` |
| 27 Oct | Astra, Claude Code | Astra reviewed the first slice and identified six issues. Four were reproduced at runtime: failed downloads cached for a year, truncated JPEGs accepted, `artworkUrl(variant: null)` causing an internal error, and JSON `null` multipart parts causing a 500. Downloads ignoring gRPC backpressure was identified through code reasoning, and the missing browser smoke test through repository inspection. Claude Code then confirmed each in the code and fixed all six, adding the Playwright smoke test | For the four runtime issues: a regression test each, shown to fail on the old code, and Astra's reproductions replayed against the running stack. For backpressure: unit tests that drive the new download flow control with a fake gRPC call (not load-tested). The Playwright flow passes against the full stack locally and in CI run #3 |
| 26 Oct | Claude Code, Codex | Reviewed the Day 1 scaffolding separately. Claude Code found 7 issues; Codex found 3 more (pagination limit, version-zero ambiguity, CI wording) | Codex ran the build, started all three apps against Postgres and called real gRPC health checks. Claude Code checked each finding against the code. All were fixed, or settled as contract decisions (DECISIONS.md D5, D7) |

## My review notes

<!-- Piyush: add what you reviewed, changed or rewrote yourself, and anything you would do differently. -->
