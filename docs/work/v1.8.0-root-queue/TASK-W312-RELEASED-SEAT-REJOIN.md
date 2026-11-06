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

## Revision 1 (Root, 2026-11-07, R364): save the exit position at logout

The first session stopped correctly (record `records/W312.md`, commit `4c63ff065`). A rider's save has no `RootVehicle`
(`RocketEntity.hasExactlyOnePlayerPassenger` is false by design), so at login nothing tells a former rider from an
airborne player. Root decides the following instead of a new saved field. This replaces the Root decision in item 1.

1. **At logout,** a player riding a stationary rocket is saved where LEAVE SEAT would place it: the rocket's existing
   exit position, from the same code LEAVE SEAT and the ground sneak use.
   - The player is not dismounted, its seat assignment is unchanged, and W287's rule that a disconnect keeps the seat
     still holds. Only the position that vanilla writes for the player changes.
   - Verify from the Forge 47.4.10 bytecode which hook runs before `PlayerList.remove` saves the player (for example
     `PlayerLoggedOutEvent`), and use that hook. Server stop saves players through the same path; check it.
   - When the rocket is not stationary (in flight or in transfer), or when no safe exit exists, change nothing: the
     transfer recovery owns those cases, as today.
2. **At login,** nothing new is needed:
   - a kept seat is reseated by the existing reconnect, from the exit position;
   - a seat released while the player was offline (operator release, W300's release) leaves the player standing at the
     exit position;
   - a removed or disassembled rocket leaves it there too.
3. **Old saves.** A rider saved before this change keeps the old behaviour once (the fall). State this limit in the
   record; do not edit offline player files.
4. **Design first.** Write a one-page design section in the record before the code: the hook, the exit computation,
   the stationary condition, what happens to an autosave while seated (unchanged), and the limits. Root will have a
   separate session review it read-only before merge, because it changes a saved player position.

The verification section above still applies. The connected-player GameTests must cover:

- an operator release while offline, then rejoin;
- a removed rocket while offline, then rejoin;
- a kept seat, reseated on rejoin;
- a rocket in flight at logout, where the saved position is unchanged;
- the existing `RocketPassengerPersistenceGameTests` invariant that the save has no `RootVehicle`.

## Revision 2 (Root, 2026-11-07, R365): detach before the save, as vanilla does after it

The second session found (record, "Revision 1 design inspection") that `Entity.saveWithoutId` writes a mounted
player's `Pos` with the vehicle's X and Z, so moving a still-mounted rider changes only the saved Y. It also confirmed
that `PlayerList.remove` fires `PlayerLoggedOutEvent`, then saves, then calls `player.unRide()`. Vanilla therefore
detaches every rider at logout already, right after the save, and W287's rule keeps the seat for that detach because the
player is disconnecting.

Root decides: "not dismounted" in revision 1 meant that the seat assignment stays; the physical mount may end at logout,
because vanilla ends it there anyway. The logout hook ends it before the save instead of after:

1. For a rider of a stationary rocket (ASSEMBLED, FUELED or LANDED, no active transfer, not guarded), the hook detaches
   the player through the same path that vanilla's `unRide` takes at logout, while the player is already marked as
   disconnecting. The player then stands at the dismount location (`getDismountLocationForPassenger`, the exit that
   LEAVE SEAT uses), and vanilla saves that position.
2. The durable seat assignment must be unchanged: assert in the GameTests that the manifest keeps the player after
   logout and that the reconnect reseats it. If the existing detach path would release the seat or start a bay exit's
   side effects for a disconnecting player, stop and say so in the record instead of working around it.
3. When the dismount location is unproved or unsafe, or the rocket is not stationary, leave the logout as it is today.

Everything else in revision 1 (old saves, autosaves, design section, separate review, GameTests) still applies.

## Not in this task

The seat release rules themselves (W300, W300F), rider position (W292), the high-seat console (W311), the ledger, the
status file and QUEUE.
