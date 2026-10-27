---
name: ship-slice
description: Deliver one feature end to end - migration, catalog, gRPC, gateway, GraphQL, web page and tests - then verify, get a code review, let the author choose the findings, and hand over a browser walkthrough. Use when asked to build, ship or "do the whole loop for" a feature such as credits or search.
---

# ship-slice

The loop used for every feature. The author decides at three points: which review findings to
apply, whether the screens look right, and when to commit and push.

## Steps

1. **Scope.** Restate the feature in the brief's words, list what is in and out, and name the
   files expected to change. Wait for the author's go-ahead if anything is unclear.
2. **Build it bottom up:** migration, entity and repository, service, gRPC endpoint and proto,
   gateway client and GraphQL controller, schema, web operations (`npm run codegen`), pages.
   Follow `CLAUDE.md`.
3. **Tests** with the `gen-tests` skill, in the same change as the code they cover, plus new
   steps in the single smoke flow.
4. **Verify** with the `verify` skill. Stop here if anything fails.
5. **Check by hand:** send the edge cases through nginx with curl, and look at the new pages in a
   browser at phone (375px) and desktop widths.
6. **Review.** Ask the `code-reviewer` agent to review the change. Show the author its findings,
   each with a recommendation: apply, defer with a reason, or disagree with a reason.
7. **Fix** what the author chose. Reproduce each finding first, then fix it with a test that
   fails without the fix. Run `verify` again.
8. **Walkthrough.** Start the stack and give the author one line per behaviour:
   `Open <URL>, do <action> -> you should see <result>`, including the error and not-found
   states.
9. **Close.** On the author's approval, commit in logical parts (catalog, gateway, web, docs),
   push, and confirm that all three CI jobs passed.

## Rules

- No step is skipped because the previous one looked fine.
- Report after each step: what changed, how it was checked, and what is next.
