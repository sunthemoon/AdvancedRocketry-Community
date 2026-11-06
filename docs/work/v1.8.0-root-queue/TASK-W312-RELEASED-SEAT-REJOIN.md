# TASK W312: a rider whose seat was released while offline rejoins at a safe place

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w312`, branch `codex/v1.8.0-w312`, created by Root from the shared head after W300F's merge |
| Decision | Root decision R360 (QUEUE row W312) |
| Background | W278 round 7 on the JAR of `15fa195a1`: `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, section "第 7 轮", finding 7-1, with screenshots `c1-80` to `c1-85-12` under `evidence/r07-ds/screenshots/`. Earlier work: [W292](records/W292.md), [W300](records/W300.md), `records/W300F.md`, [W301](records/W301.md) and [W304](records/W304.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W312.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W312/` |

## What to do

1. **Finding 7-1 (medium-low).** A rider sat in a seat 9 blocks above the rocket's base and disconnected. An operator
   then released the seat with `/arce rocket release-seat`. When the rider rejoined:
   - vanilla placed it at the seat's height, where it had been saved;
   - `rocket/rejoin/RocketRejoinProtection.java` (lines 23 and 43) blocked vanilla's remount, and
     `RocketTransferRecoveryService.tryReconnect` found no seat;
   - the rider fell into the rocket's body and lost 6 health on peaceful.

   **Root decision R360:** a player who logs in with a saved rocket vehicle but no seat in it is placed where LEAVE SEAT
   would place it (the rocket's exit position). If the rocket is not loaded or is gone, it is placed at the safe position
   that the existing rejoin guard uses. It takes no damage from the seat's height.
   - Cover every way a seat can be gone at login: an operator release, W300's stale seat release, and a disassembled
     or removed rocket.
   - A player whose seat is kept is still reseated by the reconnect.
2. Say in the record whether any other login path can still leave a player at a seat's height without a seat.

No saved field, packet or protocol change; if one seems needed, stop and say so.

## Verification

- A connected-player GameTest, like W301's and W303's, that fails before the change: seat a player high, disconnect,
  release the seat, rejoin, then check that the player stands at the exit position, is not inside the rocket and has
  full health.
- A case for the removed rocket, and the existing reconnect case that reseats a player whose seat is kept.
- The rejoin, seat roster, stale seat, reconnect and transfer GameTests.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The seat release rules themselves (W300, W300F), rider position (W292), the high-seat console (W311), the ledger, the
status file and QUEUE.
