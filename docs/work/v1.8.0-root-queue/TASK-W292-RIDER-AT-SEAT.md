# TASK W292: riders sit at the rocket's base instead of at their seat

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w292`, branch `codex/v1.8.0-w292`, created by Root from the shared head |
| Decision | Root decision R325 (QUEUE row W292) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 3 finding 3-4 (in Chinese; screenshots `u11-station-l0` and the flight shots under `evidence/`) |
| Record | `docs/work/v1.8.0-root-queue/records/W292.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W292/` |

## The finding

On an 11-block rocket whose seat block is at the top (base + 10), the rider always sits 1.15 blocks above the
rocket's base (for example `Pos [0.5, 129.15, 0.5]`), inside the column rather than at the seat. In first person
the player sees the inside of the rocket; in third person the camera is at the bottom. The reporter read
`rocket/flight/RocketPassengerPosition.java` lines 5 to 12 (a fixed y of 1.15 for every seat) and
`RocketEntity.positionRider` (lines 602 to 620), which uses it directly.

## What to do

1. Seat each rider at its assigned seat block's position in the rocket's structure snapshot (the seat's x, y, z
   relative to the rocket origin, at a sitting height on that block), so a tall rocket shows the view from its seat.
   Keep riders inside the rocket's bounds, and keep a fallback for a seat assignment whose seat block cannot be found
   (today's position).
2. Change nothing in seat assignment, the seat roster (W287, W300), persistence or packets. If the client needs the
   seat positions, derive them from data it already has (the rocket's synced structure); if it does not have them,
   stop and say what is missing before adding anything to a packet.
3. Check the cases that move or read riders: boarding, LEAVE SEAT and sneak release, the bay exit (its descent and
   safe-exit search start from the rider), transfers and reconnects that remount a rider, and the elevator and
   escrow fixtures that place passengers. None may start from a different position than they expect; adjust only
   where the rider's position is an input that must now be the seat's.

## Tests

A GameTest with a tall rocket whose seat is near the top, asserting the rider's position is at the seat; one with two
seats at different heights; and the fallback. The existing seat roster, bay exit, transfer, reconnect and passenger
GameTests must pass unchanged in their assertions.

## Verification

The new tests; the rocket passenger, seat roster, bay exit, transfer, reconnect and escrow GameTests; `./gradlew build`
with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Seat assignment, rocket models and rendering beyond the rider's position, the ledger, the status file and QUEUE.
