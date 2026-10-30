# TASK W211-interim-review: independent read-only review of B3-S6's matrix, oracle and completed rows

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents. W211's own
session is still running in `D:/GitHub/arce-v180-w211` and holds the heavy-JVM lock at times: do not touch that
worktree, its evidence or the lock.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w211` at `fedd1874b` (pushed), cut from `e00ef1ff2`; the slice so far is `e00ef1ff2..fedd1874b`. Review worktree `D:/GitHub/arce-v180-w211-review`, a detached checkout of `fedd1874b` |
| Task it answers | [TASK-W211](TASK-W211-B3S6-LOADER-REUSED-FAMILIES.md) with Root decisions R182, R185, R186, R188, R190, R192 and R194 |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md`, section 3, "W203-P2", and section 1.2's rows |
| Record | `docs/work/v1.8.0-root-queue/records/W211.md` at `fedd1874b` (newest sections first) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W211/` (read only; the folders up to `r192/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W211-interim-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is an interim review while the campaign continues (tranches A4 to C are in progress past `fedd1874b`). Its purpose
is to find errors in the matrix, the oracle or the completed rows before more native runs depend on them.

## What to check

1. **The matrix.** Every representative S7 names (E3, E5, R2, O1 to O4 with O3J, O-veto, G1, G3, D2-F0 to F8 with
   F8/N2's crash windows, F9 and refill after F8) is mapped to existing evidence with a real fuel loader, to a W211 row,
   to a labelled residual, or to a stated exclusion; nothing is credited by analogy (a chest, armour stand or cargo
   device is not a loader representative); W176's failed downgrade rows are not passes; R192's exclusion of the
   writer-successor L2 form rests on `EscrowCoordinator.retireNative` and a unit test that pins it.
2. **The oracle and observers.** `scripts/loader_s6_oracle.py`, the S6 recovery observer (R185), the unload wait
   (R186), UUID canonicalization (R188) and snapshot value comparison (R190): each is at least as strict as the rule it
   replaced; held amounts come from durable records, never from raw sums; UNKNOWN is never read as zero; the tests that
   failed first really exercised the native forms. Name any check that a wrong result could pass.
3. **The completed rows** (A1 E3, E5, R2 KEEP_SOURCE, DESTRUCTIVE_SOURCE and DUPLICATE_RECONNECT, the seven G3 rows):
   recompute each from the retained native results and images; both restarts completed; resources, epochs,
   identities, records and markers exactly as claimed.
4. **The failure history:** every failed attempt is still recorded as failed; each R190-style renewal met its stated
   conditions (images unchanged and explained, a test-side defect, a failing-first fix, a replay); no budget was
   extended silently.
5. **Test-side only:** no production source, schema, budget, timeout or shared runner changed, and the inventory
   registration (R185) changed only the new producer's entries.
6. **Counts:** the cumulative native starts, restarts, passed and failed attempts match the campaign reports.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the S6 Java test classes; the loader S6
oracle and inventory Python tests; the oracle and replay scripts on the retained evidence. Targeted unit runs need no
heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete the
worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (SOUND SO FAR, SOUND WITH CHANGES, or STOP); findings with an ID (W211-I1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
