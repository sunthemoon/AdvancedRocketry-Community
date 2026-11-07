# TASK W312F: W312R's findings on the logout exit save

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w312f`, branch `codex/v1.8.0-w312f`, created by Root on W312's integration commit `199e4d80d` (W312 with the shared head merged in, so it includes W311's structure-sized rocket box) with this TASK merged in |
| Decision | Root decision R375 (QUEUE row W312F) |
| Review to answer | `D:/GitHub/ARCE-Task-Evidence/root-queue/W312R/REVIEW.md`: CHANGES REQUIRED, findings F1 to F6 and its probe |
| Earlier work | [TASK-W312](TASK-W312-RELEASED-SEAT-REJOIN.md) (revisions 1 and 2) and `records/W312.md` on this branch |
| Record | `docs/work/v1.8.0-root-queue/records/W312F.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W312F/` |

## Root decision R375

The logout save keeps R364's place: where LEAVE SEAT puts the rider, beside the rocket. The question it must answer is
whether the player would be hurt there after logging in, not whether a bay exit may be chosen there. It therefore gets
its own check, built on the shared one, and the bay exit selector stays exactly as it is.

## What to change

1. **F1 (High), the real rocket.** For the logout check only:
   - this mod's own full-cube, non-hazard structural blocks (an explicit list, at least `rocket_assembler`,
     `landing_pad` and the machine casing) count as stable support and as no hazard;
   - the rider's own rocket does not make the exit unsafe. Since W311 its box covers its structure, but it is not a
     collision surface, so a rider standing inside it after LEAVE SEAT is harmless;
   - when the exact exit has no support, the player may settle at most 3 blocks down a clear, hazard-free column to the
     first stable support (no fall damage). It must stay in loaded chunks and inside the same probe budget.

   Add a production-shaped GameTest: a rocket assembled on a real `rocket_assembler`, with a block beside its origin so
   that its box covers the exit, and a platform one block below the assembler's top, as in finding 7-1. It must fail
   before the change and pass after it.
2. **F2 (Medium), server stop.** Before `stopServer` saves players (`ServerStoppingEvent`), apply the same check and
   native detach to every online rider of a stationary rocket. The seat stays assigned, because no seat input ticks
   after that point.
   - Test the handler with connected players in a GameTest.
   - Correct the record's shutdown statement.
   - Root will check a real dedicated-server `/stop` with a seated client in the next client round.
3. **F3 (Low).** Other players at the exit do not make it unsafe; they are not collision surfaces. Mobs and other
   collidable entities still do.
4. **F4 (Low).** Add the missing exclusion tests: release fence, held bay receipt, internal move, and a hazard (lava or
   fire) at the exit.
5. **F5 (Low).** Wrap the hook's work so that an unexpected exception logs one WARN, naming the player and the rocket,
   and leaves the logout as it was before W312. Never block the save.
6. **F6 (nit).** Correct the record's "wide rocket geometry" wording for the post-W311 box. Leave the history as it is;
   Root notes the red-run commit in QUEUE.

No saved field, packet or protocol change. The shared `BayExitSafety` and `BayPassengerExitWorld` behaviour for the bay
exit selector must not change; prove it with the existing bay exit GameTests.

## Verification

- The new tests fail before the change, as stated above.
- The `rocket_logout_*`, rejoin, seat roster, stale seat, reconnect and all `bay_exit_*` batches.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. A second read-only review follows before merge, because this still changes saved
player positions.

## Not in this task

The bay exit selector, seat release rules, rider placement, the ledger, the status file and QUEUE.
