# TASK W125-review: independent read-only review of W125 (the F1 fix under R48) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W125 and both Claude slots are busy, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w125` at `23385aee` (base `fb0e88d9`; tested source `3f2524d8`). Your review worktree `D:/GitHub/arce-v180-w125-review` is a detached checkout of `23385aee`; read the diff with `git diff fb0e88d9..23385aee` there |
| Task it answers | [TASK-W125](TASK-W125-F1-NAMED-FILL-FIX.md) (Root decision R48) |
| Design | [records/W121-DESIGN.md](records/W121-DESIGN.md) with R48 at its top, the W121 review `D:/GitHub/ARCE-Task-Evidence/root-queue/W121-review/REVIEW.md` (its probes, including the starvation probe), and R52 at the top of [records/W126-DESIGN.md](records/W126-DESIGN.md) (D2-3's three outcomes) |
| Record | `docs/work/v1.8.0-root-queue/records/W125.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W125/` (the sealed bundle `verify-3f2524d8-20261021-205618`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W125-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The self-guard** is its own reconciliation predicate (not `ordinaryFillsOnly`): it admits ordinary guarded PREPARED
   fills and STALLED fills with a retryable proof-failure reason, including pending reason transitions, and keeps every
   identity, journal, frontier, tombstone, manual-conflict, settlement, flight and bay exclusion; a persisted stall is
   cleared only by the existing successful proof and L2 path. Can it admit anything R48 excludes?
2. **The no-revoke rule** permits only the guarded settlement, no other recovery action; there is no C8 hand-back that
   grants recovery authority (W121-R2).
3. **The transient reason** `TRANSFER_JOURNAL_UNPROVED` with its 20-tick scheduler delay: does it end the starvation
   (rerun the W121 review's starvation probe against the fix), and do status and inspect show it with its retry time?
   It must not be persisted, manual or settlement authority.
4. **F2**: the bounded loader-index lookup reports `RELOCATED_ENDPOINT` for a loaded relocated loader, never adopts the
   new place, refuses an overflow past 64 Levels with `BARRIER_BOUNDS`, and the abandon preview and confirmation apply
   the same loaded-only rejection.
5. **Exactly once**: walk K0 to K10 and both restarts for a named fill, including the E5 case (three failures, repaired
   storage, a restart while STALLED); no resource spent twice, no fence weakened beyond the guarded settlement.
6. **The native evidence**: 13 PASS and 2 known limitations over 45 starts; are the first and second restarts and the
   three outcomes (resources conserved, refuelling restored, launch allowed or refused) reported separately as R48 and
   R52's D2-3 require, and are the cross-Level cases correctly not counted as passes?
7. **Tests and limits** (AGENTS.md section 5): list every edited pre-existing test or fixture and say whether anything
   is weaker; no limit, budget or timeout relaxed; `RocketTransferService` under 800 lines; classes over 500 lines.
8. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W125's base
   added W120, W124 (C-E2) and others: preview with `git merge-tree` and say what the merge must keep, in particular in
   the reconciliation scheduler, driver and view that C-E2 also changed.
9. **The record** against the code.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W125's
new unit classes, then the `resourceescrow`, `rocket.server` and `rocket.fuel.loader` packages, and the W121 review's
starvation probe. Before each Gradle run, check that at least 6 GiB of memory is free; one Gradle process at a time; no
GameTest, `runData` or server; no heavy-JVM lock. Keep any probe in the output folder. Delete the worktree's `build` and
`.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W125-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
