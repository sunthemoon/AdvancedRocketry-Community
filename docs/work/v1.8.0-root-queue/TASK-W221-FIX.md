# TASK W221-fix: the named-pad oracle's terminal release and native location checks

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session; this file hands two
review findings of W221 to you.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w221` (W221's; continue on its branch) |
| Branch | `codex/v1.8.0-w221`, now at `9ac7ba3ed` (pushed by Root; local commits only from here, Root pushes and merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W221.md`: append a section "R211 review corrections" |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W221-review/REVIEW.md` (SHA-256 `11cbf251…2d16d08ab1`), findings W221-R1 and W221-R2; the reviewer's counterexamples are `oracle_counterexamples.py` and `oracle-counterexamples.json` beside it |

**Root decision R211.** W221's review approved with changes. Two oracle false positives in
`scripts/loader_s6_named_pad.py`: W221-R1 (Medium) `check_interval` bounds the release transitions at one but never
requires the terminal live pad to be unclaimed or the count to be exact, so a RELEASED event reporting no release
passes; W221-R2 (Medium) `inspect` flattens entities from every dimension and keeps only their NBT, so a rocket whose
region file sits in the wrong dimension passes. The archived rows are not affected (the reviewer checked them
independently). Test-side only.

## What to do

1. **W221-R1:** for RELEASED and every completed restart, require an unclaimed terminal live pad and exactly one
   transition when the interval starts claimed (zero otherwise), and compare the terminal live pad's complete state
   with the durable terminal state.
2. **W221-R2:** keep native location provenance (dimension, region file, slot and chunk coordinates) for each rocket,
   loader and physical pad, and check it against the expected source or destination, not only the entity's embedded
   snapshot.
3. Tests that fail first on the reviewer's two counterexamples (with intact framing and sequence for R1; a moved region
   file for R2), plus a wrong-slot case; then replay every archived input of the seven rows through the corrected
   oracle and record the result as a replay. No native rerun; failed attempts stay failed.

## Verification

The new tests alone, failing first; the loader S6 and named-pad Python tests; the replay. No Gradle native run,
GameTest or full build unless a Java file changes, in which case its test class and the `resourceescrow` package.

## Not in this task

Native reruns, other findings, production changes, ADRs, status files and the ledger.
