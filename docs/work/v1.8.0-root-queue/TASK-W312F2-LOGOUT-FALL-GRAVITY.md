# TASK W312F2: the logout fall allowance under high gravity

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w312f2`, branch `codex/v1.8.0-w312f2`, created by Root on W312F's integration commit `6ca360286` (W312 and W312F with the shared head merged in) with this TASK merged in |
| Decision | Root decision R378 (QUEUE row W312F2) |
| Review to answer | `D:/GitHub/ARCE-Task-Evidence/root-queue/W312FR/REVIEW.md`, finding F1 (Medium), with its probe `probe/W312FRProbeGameTests.java` and `probe-observations.txt` |
| Earlier work | [TASK-W312F](TASK-W312F-LOGOUT-EXIT-REVIEW-FIXES.md), `records/W312F.md` and `records/W312.md` on this branch |
| Record | `docs/work/v1.8.0-root-queue/records/W312F2.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W312F2/` |

## The finding

`LogoutExitSafety` (lines 69 to 79) accepts a drop of up to `MAX_DROP = 3` blocks as SAFE. ARCE scales a player's
landing distance by the gravity multiplier before vanilla computes damage (`CelestialGravityController`, lines 123 to
160, `scaledFallDistance`), so inside a 2g gravity field a three-block drop counts as six and deals 3 health. W312FR's
probe showed 20 to 20 health at 1g and 20 to 17 at 2g.

## What to do

1. Accept a drop only when the scaled landing distance stays damage-free:
   - keep the three-block geometric ceiling;
   - the drop times the gravity multiplier that will apply at the landing must not exceed vanilla's damage-free
     distance;
   - use a conservative multiplier: the largest of those at the exit and at the landing cell, from the same rules
     `CelestialGravityController` applies, including gravity field controllers and station gravity;
   - when the multiplier cannot be known, accept no drop. The shared SAFE case with no drop is unaffected.
2. Do not add damage immunity or change the gravity rules. The bay exit selector and the shared budget stay unchanged.
3. Add an integration GameTest like W312FR's probe:
   - a real gravity field controller at 2g, a clear three-block column, native logout, an offline seat release, native
     login, a wait past spawn immunity and a landing through `doCheckFallDamage`;
   - the 2g case keeps the seat save, or saves a damage-free place, and loses no health;
   - a 1g control still saves the exit and loses no health;
   - a low-gravity case keeps the three-block ceiling.

   The new 2g test must fail on the current code.
4. Correct the record wording that the exception guard leaves the save unchanged: say that it does so for a failure
   before the native detach (W312FR's note in point 4).

No saved field, packet or protocol change.

## Verification

- The new tests, failing before the change where stated.
- The `rocket_logout_*`, gravity, rejoin and seat batches, and the `bay_exit_*` batches.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root re-checks the fix against W312FR's finding, reruns the probe's case, and merges.

## Not in this task

Gravity rules, fall damage, the bay exit selector, seat release rules, the ledger, the status file and QUEUE.
