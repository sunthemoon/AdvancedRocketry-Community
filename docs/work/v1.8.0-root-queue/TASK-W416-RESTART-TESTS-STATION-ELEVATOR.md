# TASK W416: restart GameTests for the warp core and controller, the elevator endpoints, and three simple devices

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w416`, branch `codex/v1.8.0-w416` from the shared branch |
| Decision | Root decision R565 (QUEUE row W416), following R564 |
| Sources | [MACHINE-CENSUS](../../releases/v1.8.0/MACHINE-CENSUS.md) findings F3, F8 and F12 (each names the existing tests and what they do not cover); the version document's section 9 (each machine needs at least a happy, a failure and a restart test); the block entities, the station and elevator services and their saved forms, and the existing GameTests the census cites |
| Record | `docs/work/v1.8.0-root-queue/records/W416.md` |
| Write scope | New GameTest methods (in the existing GameTest classes the census names, or a new class beside them in the same package) and test-only fixture helpers; the record. No change to product behaviour, saved formats or existing tests. W415 works on other devices at the same time; do not touch its classes. |
| Lock | Take the heavy-JVM lock (WORKER-RULES) for each GameTest server run. Candidate C5's native campaign and W415 also take it; the campaign runner waits for you. Keep each run to the batches you add and release the lock as soon as it ends. Never hold it while you edit. |

## What to add

1. **F8, `warp_core` and `warp_controller`**: reopen the core's block entity and the station registry from their
   current saved forms with a charging warp, a pending warp and a committed warp, and check through the controller and
   the warp service the exact energy debit, orbit and countdown afterwards; each state continues exactly once.
2. **F12, the elevator anchor and terminal**: restore both endpoint block entities together with the pair, transit
   and ride registry snapshots, with the exact inventory and energy, and check that an arrival happens at most once.
3. **F3, `small_plate_press`, `pipe_seal` and `airlock_door`**: a chunk save and reload for each: the press produces
   no extra powered edge or output; the door's two halves keep one consistent open and powered state; a stale seal
   handle stays inert and a fresh forwarding lease works.

Each test states in its name and a short comment which finding it closes. Keep the existing tests.

## Checks

- Run the new batches under the lock and record each run's command, duration, batch list and result. Then run the
  unit suite for the touched packages and `python -B scripts/validate_repository.py`, which must end with `0 failed`.
- A failing new test that shows a product defect is a finding: stop, record it with the evidence, and do not change
  product code in this task.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
