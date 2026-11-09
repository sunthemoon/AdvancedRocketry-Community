# TASK W342: B6.5's destination rebuild from a consumed bay transfer, as a GameTest

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w342`, branch `codex/v1.8.0-w342`, created by Root from the shared head |
| Decision | Root decision R412 (QUEUE row W342), from W338's finding 3 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W338/REVIEW.md` (read it first) |
| Read first | W27A's B6, B7 and B8 rows in [W27A-DESIGN](records/W27A-DESIGN.md) (around line 894), [W331](records/W331.md) finding 4, `RocketTransferRecoveryDecision`, `RocketTransferRecoveryService` (the REBUILD_DESTINATION branch) and the existing `RocketTransferRecoveryGameTests` |
| Record | `docs/work/v1.8.0-root-queue/records/W342.md` and the cut list `records/W342-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W342/` |

## Why

W338 showed that the destination rebuild is reachable in production: after the transfer, production removes the
source, and an ordinary save of the source Level can persist that removal before the destination Level saves the new
rocket. A crash in that gap leaves a committed consumed transfer record and neither entity, and recovery chooses
REBUILD_DESTINATION. W27A's B6 row requires that this rebuild uses the consumed record and resurrects no package. No
test checks that with bay packages today; W331's case shows only the KEEP_SOURCE alternative.

## What to do

1. **Cut list first** (`records/W342-DESIGN.md`, committed before the code).
2. **GameTests, test-only:** build the state directly in a GameTest, with no production seam: a satellite bay group
   whose transfer record is committed and consumed (all registering packages consumed, the registry holding the
   registered satellites, the bay receipts retired), the source and destination rocket entities absent. Run the
   ordinary recovery and assert: REBUILD_DESTINATION is chosen; the rebuilt rocket comes from the consumed record;
   every package stays consumed, with no satellite item or package back in any bay or inventory; the registry is
   unchanged; no receipt reappears; the consumed operations are stable across a reload of the SavedData. Add controls:
   the same state with the source present chooses KEEP_SOURCE, and one with an unconsumed record behaves as W27A says.
   Check the case once with the consumed-state guard disabled in a scratch copy and show that the test fails; discard
   the copy.
3. **A native assessment, not an implementation.** In the cut list, describe how a gated native control could cut
   between the source Level's entity save and the destination Level's entity save (which save calls, the gate, what
   it would and would not show). Root decides later whether to build it.

## Verification

- The new GameTests alone, then the full GameTest run with `scripts/check_gametest_log.py`; any manifest addition
  names only lines your new tests emit.
- `./gradlew build` with every unit test.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), taken per step and released between
  steps; other workers share it.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; a native case or gated save control; W338's findings 1 and 2 (W341); the status file, QUEUE and the
risk register.
