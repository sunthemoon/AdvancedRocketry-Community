# TASK W124-review: independent read-only review of W124 (cargo leaf C-E2, reconciliation) before merge

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W124 and both Claude slots are busy, so this is a fresh Codex session with no context from the author (as for
W109-review and W114-review).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w124` at `6111d458` (base `4ed1c5f1`; tested source `4e727958`). Your review worktree `D:/GitHub/arce-v180-w124-review` is a detached checkout of `6111d458`; read the diff with `git diff 4ed1c5f1..6111d458` there |
| Task it answers | [TASK-W124](TASK-W124-CARGO-C-E2-RECONCILIATION.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 3.2 (items 4 to 6), 3.3 ("Normal settlement") and 4 (the stop-point table), section 6's C-E row and the cut mapping's "Ordinary prefix/apply" row; the fill path's reconciliation as the model ([records/W11B3-DESIGN.md](records/W11B3-DESIGN.md)) |
| Record | `docs/work/v1.8.0-root-queue/records/W124.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W124/` (the native matrix of 20 cases, 60 starts) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W124-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Exactly once.** For a load and an unload, walk K0 to K10 and both restarts (section 4's table). Can any order of
   publication, store, crash or restart credit the rocket or the device twice, debit twice, or lose the delta? The
   rocket's classification must use the composite signature (the whole snapshot's content hash), so an AFTER rocket is
   never classified BEFORE; the device compares its own tank signature.
2. **Rolling a BEFORE side forward**: the after snapshot is recomputed through the budgeted replay and compared with the
   frozen digest before anything is published; a mismatch holds, never publishes. A mixed state rolls the BEFORE side
   forward; AFTER on both sides goes to the barrier; L2 retires; C8 drops stale markers without decrementing epochs.
3. **The device adapter and permit**: observe, change, prune and disk decode for the fluid cargo devices; the permit is
   one-use, server-thread and publishes the tank delta and marker together; no public setter.
4. **Typed readback**: bounded `AnvilRecordReader` decode, the content hash and composite recomputed off-thread on an
   immutable decode, digests compared on the server thread; nothing on the server thread newly unbounded.
5. **R39**: the native check before `RocketCargoMutation.apply` and before the device tank changes, at C2 and readback;
   a refusal holds only that operation as REPAIR with `OTHER_STATE`, other components continue.
6. **F1**: named (transfer-guarded) cargo stays PREPARED with nothing lost, pinned by a test and marked for the F1 fix
   (W125); confirm C-E2 did not add its own variant of the fix.
7. **The fill path is unchanged**: the existing fill reconciliation tests and assertions; list every edited pre-existing
   test or fixture file and say whether anything is weaker; `EscrowCoordinator` did not grow; classes over 500 lines.
8. **The native evidence**: do the 20 cases cover section 4's stop points for both directions, and does the oracle
   compare raw totals before recovery and conservation after it?
9. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W124's base
   added W118 (D-P), W120 (D-A), W123 (W27c3) and Root's records: preview with `git merge-tree` and say what the merge
   must keep, in particular in the shared cargo capture, eligibility and binding classes.
10. **The record** against the code.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W124's
new unit classes, then the `resourceescrow`, `rocket.cargo` and `rocket.fuel.loader` packages. You may add a probe test
to check a finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is
free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock.
Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W124-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
