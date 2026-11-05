# TASK W287F: W287R's required changes to the seat roster change

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w287`, branch `codex/v1.8.0-w287` at `c222724a1` (W287's delivery; continue on it) |
| Review to answer | `D:/GitHub/ARCE-Task-Evidence/root-queue/W287R/REVIEW.md`: verdict CHANGES REQUIRED, findings F1 to F4; its probe `probe/W287RProbeGameTests.java` in that folder |
| Tasks | [TASK-W287](TASK-W287-SEAT-ROSTER.md) and Root's design direction R334 (unchanged) |
| Record | append a section to `docs/work/v1.8.0-root-queue/records/W287.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W287F/` |

## What to change

1. **F1 (High).** The sneak path calls `leave()` from `RocketEntity.tick()` without the `try`/`catch` that the menu
   path has, and `updateFlightData` can throw, for example `Rocket is retained by sealed disassembly` when the
   `rocket_transactions` root is not operational, which crashes the server on each sneak. Skip `leave()` when
   `RocketRuntime.sealedFrozen(rocket)` is true (vanilla then dismounts and the seat is kept, as before W287); catch
   `RuntimeException` around each per-passenger `leave()`, log one bounded WARN (rocket, player, reason) and keep the
   seat; add a GameTest (the reviewer's probe shows the setup) where a seated sneak with the transaction root
   unreadable completes without an exception and keeps the seat. Any new WARN a GameTest deliberately drives needs its
   own expected-log entry for that test only.
2. **F2 (Medium).** `RocketFlightService.java` is now 802 lines, over AGENTS.md section 3.4's 800-line limit. Move the
   input bridge (`dismountSneakingPassengers` and F1's guard) into a small class of its own in `rocket/server` (for
   example `RocketSeatInput`) that calls a package-private `leave()`, so that the file is back under 800 lines.
3. **F3 (Low).** Add the three cases the review names, each asserting the seat is kept: tick the rocket while the
   internal-move scope is open and the player still rides with sneak set; sneak on a LANDED rocket with an active
   transfer ID; sneak as a rejoin-guarded player.
4. **F4 (Low).** Log one INFO line when a sneak releases a seat, for example `ARCE_SEAT_RELEASED rocket=... player=...
   cause=SNEAK`.

F5 (stale seats from death, teleports, spectator mode and other dismounts) is outside this task; Root queues it.

## Build note (W287R F6)

On this machine the local build cache can restore an incomplete `compileJava` for W287's commits (368 classes missing,
including the `@Mod` class, so the GameTest server constructs 0 mods). Build with `--no-build-cache` or run
`compileJava --rerun` before trusting a local GameTest run, and say which you used.

## Verification

The new and existing seat roster GameTests; the rocket flight, transfer, recovery, reconnect and loader GameTests;
`./gradlew build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM
lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews (and has W287R's points checked), pushes for a sharded run and merges.

## Not in this task

F5's stale seats, rider position (W292), the ledger, the status file and QUEUE.
