# TASK W303: can the elevator ride or the rejoin pin carry a held floating counter into 1 g?

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w303`, branch `codex/v1.8.0-w303`, created by Root from the shared head |
| Decision | Root decision R345 (QUEUE row W303) |
| Earlier work | [W301](records/W301.md) (section "Read-only inspection of adjacent moves"): the safety return now resets vanilla's floating counter once after a confirmed arrival (`SpaceSafetyReturnService.move`), because W285's low-gravity allowance holds `aboveGroundTickCount` at 79/80 at 0 g and a cross-dimension move into 1 g then kicked the player on the next connection tick (W278 round 6 finding 6-1). [W285](records/W285.md) added the allowance |
| Record | `docs/work/v1.8.0-root-queue/records/W303.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W303/` |

## The question

W301 found by reading the code, without reproducing it, that two other trusted moves reuse the player's connection and
call `teleportTo` without resetting the counter:

1. `endgame/elevator/ElevatorRides.java`, at its arrival commit (around line 217): a rider held at 0 g (for example
   boarding at a zero-g station) arriving on a 1 g body.
2. `rocket/rejoin/RocketRejoinGuards.java`, in `pin` (around line 116): a guarded session that waited unsupported at
   0 g on its new connection and is then placed in the Overworld.

## What to do

1. For each move, write a connected-player GameTest first (reuse the fixtures in `gametest/SpaceSafetyReturnGameTests.java`,
   `gametest/LowGravityFlightGameTests.java` and the elevator and rejoin GameTests) with flight disabled: hold the
   counter as W285's allowance does at 0 g, perform the real move into 1 g, and assert that the player is still
   connected for at least 100 ticks after arrival. Say in the record whether each reproduces.
2. Where it reproduces, reset `player.connection.aboveGroundTickCount` to 0 once, only after the move is confirmed
   (as W301 did), and nowhere else. Where it does not, say why (for example the rider is a passenger, or the counter
   is already reset on that path) and keep the test as a guard.
3. Keep `GravityFlightBudget`, the allowance values, the elevator and rejoin rules and every other move unchanged.

## Verification

The new tests failing before and passing after where they reproduce; the elevator, rejoin, safety return and
low-gravity GameTests; `./gradlew build` with all unit tests (`--no-build-cache` or `compileJava --rerun`); the full
GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The rejoin fixtures' dependence on spawn terrain (W304), the allowance values, the ledger, the status file and QUEUE.
