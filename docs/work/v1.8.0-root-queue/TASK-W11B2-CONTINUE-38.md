# TASK W11b2, continuation 38: design section 6, F6 and F9 (the settlement fixtures and their native cuts)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md), [TASK-W11B2-CONTINUE-36](TASK-W11B2-CONTINUE-36.md) and the
documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at `5874fa47` plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local commits only; Root pushes and merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 6, rows F6 and F9, and Root decisions R1 to R13 as listed in TASK-W11B2-CONTINUE-33 |
| Input | `records/W11B2-F7F8.md`, "Continuation 36" (status, inspect, retry, receipt-cap wording and console provenance delivered; F6 and F9 open) |
| Record | Add a "Continuation 38" section to `records/W11B2-F7F8.md`. Keep the existing text. Do not edit `records/W11B2.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C38/` |

Root reviewed continuation 36's steps 1 and 2 and removed its GameTest world. In this task, F6 and F9 are design
section 6's findings, numbered after the settlement review (`W11b2-settle-review/REVIEW.md`). They are not the
flight-order steps F6 to F8.

Continuation 37 (Codex, worktree `D:/GitHub/arce-v180-w11b2-rocket-integration`) runs in parallel on reconciliation rows
C3 to C8 and the C0 to C8 forced-stop cuts.

## What to do (commit each step with its tests)

1. **F6.** Assert the actual hold reason on each restart of the settlement veto cuts. Add separately checkpointed
   source states with a reinstalled veto, to prove save refusal across restarts. Keep the existing non-floor cases and
   their results as frozen-observation-mismatch evidence; do not relabel them.
2. **F9.** Cut the floor choice and every other O1 choice on the final source, with named-transfer fill guards and
   truly empty entity slots. Update the old intent runner to follow the eventual O2 to O4 path without removing its
   pre-O1 and retained-intent assertions.
3. **Native cuts** for steps 1 and 2, two restarts each, pinned to the committed source and its jar. List any cut that
   is left.

## Write scope and boundaries

- In scope: the settlement probes, fixtures and runners for F6 and F9, their tests, GameTests and Python oracles, and
  this record. Under R13 item 3, a narrow additive edit to a production file is allowed only when the frozen design or
  R1 to R13 already require it; record each one with its reason.
- Out of scope, owned by continuation 37: `RocketEscrowAudit`, `EscrowReconciliationScheduler`, the C0 to C8 driver,
  `EscrowNativeBinding` and its validators.
- No change to an acknowledgement order, a proof, a hold's effect, the replay exclusion, the log checker or its
  expectations. Use a new GameTest batch only when a fixture needs isolation.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step. Run the unchanged log checker after each GameTest run; report a remaining
warning with its line, and report the overload count separately. Stop only for a change to an acknowledgement order, a
proof, a hold's effect or the replay exclusion: commit what you have with your record, and report. Questions sent
through the task conversation do not reach Root; the record and the final answer do. If you run short of time, stop at
a committed step boundary with every test of that step passing, and say exactly where the next continuation starts.
One GameTest run and one set of native cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C38/`. Continuation 37
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If a GameTest run
crosses midnight, check every rolled log part.

## Not in this task

C0 to C8 (continuation 37); F10 and the eight-flight benchmark (later); F7, F8 and F11 (continuation 36, delivered);
production admission; W11b3; the bay slices; W27f reconstruction; signed per-fluid accounting; the part-1 review's
findings F3 (W86), F8 (W85) and N2; ADRs, the risk register, the R-035 text, status files and the ledger (Root).
