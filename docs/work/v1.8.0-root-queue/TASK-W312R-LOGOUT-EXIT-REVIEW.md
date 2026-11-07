# TASK W312R: independent review of W312's logout exit save

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w312` at `7e475c322`, checked out read-only and detached at `D:/GitHub/arce-v180-w312r`. Its base is W314's integration commit `7bcb1283e` (it contains W300F) plus Root's TASK revisions |
| Implementer's record and design | `docs/work/v1.8.0-root-queue/records/W312.md` on that branch, section "Revision 2 implementation" (the design was committed first, `e5e5176cb`); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W312/revision2/` |
| Task it implements | [TASK-W312](TASK-W312-RELEASED-SEAT-REJOIN.md), revisions 1 and 2 (Root decisions R364 and R365) |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W312R/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes the position saved in a player's file at logout (AGENTS.md section 13) |

## What to check

1. **The seat stays assigned.** `RocketPassengerLogout` calls `player.unRide()` in `PlayerLoggedOutEvent` before
   `PlayerList.remove` saves the player. Confirm that no path through `unRide`, `stopRiding`, the rocket's passenger
   removal, the bay capture listener or the seat input releases the seat or mutates the manifest for a disconnecting
   player, and that a kept seat is still reseated by the reconnect.
2. **When it acts.** Only for a loaded, operational, stationary rocket (ASSEMBLED, FUELED or LANDED), with no active
   transfer, no rejoin guard, no release fence, no held bay receipt and no internal move. Look for a case it should
   exclude and does not, and for a case it skips that leaves the old fall.
3. **The exit and its safety check.** It proves `getDismountLocationForPassenger`'s exact box SAFE with
   `BayExitSafety` and the shared exit budget, then lets the native dismount place the player. Check that the place
   proved is the place the player is saved at, and that the check loads no chunk and stays within its budget.
4. **Saves.** Autosaves while seated, the initial `saveAll` at server stop and the later logout save, a crash before
   logout, and old saves: say whether the record's stated limits are right and acceptable.
5. **Event order and other listeners.** `EventPriority.LOWEST`: could another mod's or this mod's logout listener
   see a player who is no longer riding and misbehave? Is `hasDisconnected()` reliably true here?
6. **Tests.** Do the 14 GameTests fail without the hook (the record's controlled red run), and do they cover the cases
   above? Check that `RocketPassengerPersistenceGameTests`' rule (no `RootVehicle` in the save) still holds.

Run the `rocket_logout_*` batches and the rejoin, seat roster, stale seat and reconnect batches once, under the
heavy-JVM lock, using the backslash lock path from WORKER-RULES (`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`).
A full GameTest run is not required: the record's two full-run failures (a bay gantry exit and the sleep-in-vacuum
test) were fixed on the shared branch by W313 and W315 after this branch's base. Work in your own Temp folder for any
scratch export, and delete it when you finish.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each point above, and the commands
you ran with their results. The last line of your answer is the REVIEW.md path.
