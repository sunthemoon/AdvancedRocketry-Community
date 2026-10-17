# TASK W91F: P1-F3's remaining unit and GameTest cases, and the full regression

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91E-P1-F3](TASK-W91E-P1-F3.md) and
[records/W91.md](records/W91.md) (its W91D and W91E sections) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91E's record `935fe5b8` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Inputs | R23 to R26 as the earlier W91 tasks name them; W91E's fixture corrections `50413e87` (compiled, not yet rerun) |
| Record | `records/W91.md` (extend it) |

This task is the tests and the full regression only. The native timing, benchmark and cuts are the next task (W91G).
Keep each run serial: never compile or edit while a Gradle test or GameTest run is in progress in this worktree.

## What to do

1. **Rerun the two corrected fixtures** (`journal_fail_back_admission`, `journal_concurrent_flight`) on committed
   `50413e87` or later.
2. **The remaining W86 section 8 cases** that W91E lists: the uncompleted A0 combinations; G2 (UNKNOWN beyond ticket
   expiry with loaded and unloaded captures, and recovery); G3 (definite failure); G4 (governed concurrency); and N2's
   blocked-arrival, UNKNOWN and restart variants that can run in-process. Fix only test fixtures; if a case shows a
   production defect, fix it in its own commit within R23 and say so.
3. **The full regression on committed source:** the whole unit suite (serial, nothing else compiling) and one full
   GameTest with the unchanged log checker, under the heavy-JVM lock. Say whether you add a GameTest batch (about 380
   batch names; about 384 reshuffles the run order).
4. Record what remains for W91G (the native timing, the zero-missed-step measurement, the escrow-consumer
   measurements, the unchanged writer benchmark and the native F3 and N2 cuts).

## Commands and not in this task

As TASK-W91C says. Other workers (W94B, W92F, W90a) also run tests; take the heavy-JVM lock for every GameTest run and
release it between runs when another worker waits. When your heavy runs end, remove your own lock and your
`build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses,
write the paths in your record and final answer at once. The native runs are W91G's, not this task's.
