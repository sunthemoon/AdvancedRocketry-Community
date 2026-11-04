# TASK W301: the safety return to the Overworld must not end in a floating kick

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w301`, branch `codex/v1.8.0-w301`, created by Root from the shared head |
| Decisions | Root decision R339 (QUEUE row W301) and Root decision R340 (the design below) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 6, findings 6-1 to 6-3 (in Chinese; screenshots and logs under `evidence/` in that folder) |
| Earlier work | [W285](records/W285.md) added the low-gravity airborne allowance (`celestial/service/GravityFlightBudget.java`, `LowGravityFlightAllowance.java`) and the zero-g station drift threshold |
| Record | `docs/work/v1.8.0-root-queue/records/W301.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W301/` |

## The failure (finding 6-1, medium)

On a dedicated server with `allow-flight=false`, a survival player in the Space Level at 0 g outside every station
region was returned after 100 ticks. No station landing point was loaded, so the return went to the Overworld:
the server logged `ARCE_SAFETY_RETURN ... outcome=returned destination=overworld`, then five `kicked for floating too
long` lines in the same second, and the client showed `Flying is not enabled on this server` without the return
message. With the station's landing chunk loaded the return went to the station and nobody was kicked.

The reporter's reading of the code (not verified): at 0 g `GravityFlightBudget.adjust` holds vanilla's
`aboveGroundTickCount` at 79/80 for up to 800 ticks; the outside-region threshold is
`SafetyReturnPhase.ADRIFT_TICKS = 100`, so the counter is at 80 when the return runs; the cross-dimension teleport
in `SafeCelestialTravel.teleport` does not reset the counter, `LowGravityFlightAllowance` drops its phase on the
dimension change, `clientIsFloating` stays true until the client confirms the teleport, and the next connection tick
counts 81 in 1 g and disconnects the player.

## Design (Root decision R340)

1. **Reset after a successful return.** In `SpaceSafetyReturnService.move`, once `arrived` is confirmed (both the
   station and the Overworld destination), set `player.connection.aboveGroundTickCount` to 0 before the announcement.
   This is a one-time reset after a server-initiated move, like vanilla's own reset on landing; it does not change
   `GravityFlightBudget`, which still never resets the counter itself. Do not reset it on a refusal or a dropped job.
   First confirm the reporter's reading with a failing test; if the cause is different, stop and report it.
2. **Avoid a parked rocket at the Overworld arrival (finding 6-2, low).** Both returns in round 6 put the player at
   the spawn arrival, inside a rocket parked there. For the safety return's Overworld path only (do not change
   `SafeCelestialTravel.prepare` for its other callers), when a rocket entity's box intersects the player's arrival
   box, pick the nearest column within a small fixed radius (at most 81 columns) that is in an already loaded chunk,
   has its feet at the motion-blocking height, no liquid at or below the feet, no block collision for the player's box
   and no rocket entity in it. Never load a chunk for this. If no column qualifies, keep today's arrival (a return
   inside a rocket is better than no return) and log it in the existing INFO audit line. The `TELEPORT` protection
   check then runs on the chosen column as it does today.
3. **Documentation (finding 6-3, information).** Say in the operator-facing text that describes gravity (find where
   W285's allowance is described for server operators; `docs/CELESTIAL-DATA-GUIDE.md` if nothing better exists) that
   the allowance also bounds slow-falling descents: at the Moon's gravity a slow-falling player descends about 0.15
   blocks per tick, so a slow-falling descent longer than the allowance is still kicked with `allow-flight=false`.

Look, without changing them, at the elevator ride's cross-dimension arrival (`endgame/elevator/ElevatorRides.java`
around line 217) and the rejoin guard's move (`rocket/rejoin/RocketRejoinGuards.java` around line 116): say in the
record whether either can carry a held counter from 0 g into 1 g. Root queues any fix.

## Tests

1. A GameTest that reproduces 6-1 first: a connected survival test player with `allow-flight` off, at 0 g outside
   every station region, no loaded station arrival, returned to the Overworld; assert that the player is still
   connected and in the Overworld for at least 100 ticks after arrival, that the counter was reset, and that the return
   message was sent. It must fail on the shared head without step 1. Reuse the fixtures in
   `gametest/SpaceSafetyReturnGameTests.java` and `gametest/LowGravityFlightGameTests.java`.
2. A GameTest for step 2: a rocket entity placed at the Overworld arrival; the return lands the player in a nearby
   free column outside the rocket's box; with every nearby column blocked, the arrival is today's point. Keep
   `withNoCandidateThePlayerReturnsToTheOverworld` and its expectation unchanged when no rocket is there.
3. Unit tests for the column choice if it is written as pure code.

## Verification

The new tests; `SpaceSafetyReturnGameTests`, `LowGravityFlightGameTests` and the suit motion GameTests; `./gradlew
build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock;
`python -B scripts/validate_repository.py --require-approved-identity`. Any new WARN or ERROR a test deliberately
causes needs its own expected-log entry for that test only.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

`GravityFlightBudget`'s allowance values; the station drift thresholds; the rocket landing rules (W290 changes the
Overworld landing over liquid); finding 6-4 (`OnGround` on a 0 % station platform, which belongs to W294); the
elevator and rejoin moves beyond the note above; the ledger, the status file and QUEUE.
