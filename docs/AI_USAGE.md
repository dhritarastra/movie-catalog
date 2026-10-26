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
| 26 Oct | Claude Code, Codex | Reviewed the Day 1 scaffolding separately. Claude Code found 7 issues; Codex found 3 more (pagination limit, version-zero ambiguity, CI wording) | Codex ran the build, started all three apps against Postgres and called real gRPC health checks. Claude Code checked each finding against the code. All were fixed, or settled as contract decisions (DECISIONS.md D5, D7) |

## My review notes

<!-- Piyush: add what you reviewed, changed or rewrote yourself, and anything you would do differently. -->
