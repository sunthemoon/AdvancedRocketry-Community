# TASK W312FR: independent review of W312F's logout exit check and server-stop detach

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w312f` at `e8d26f5a3`, checked out read-only and detached at `D:/GitHub/arce-v180-w312fr`. It carries W312 (logout detach before the save) on W312's integration commit `199e4d80d`; review W312F's own commits `43842b771` to `e8d26f5a3`, and W312's code where W312F changes it |
| Implementer's record | `docs/work/v1.8.0-root-queue/records/W312F.md` on that branch; W312's record `records/W312.md` there, with W312F's labelled corrections; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W312F/` |
| Task it implements | [TASK-W312F](TASK-W312F-LOGOUT-EXIT-REVIEW-FIXES.md), Root decision R375, answering the first review `D:/GitHub/ARCE-Task-Evidence/root-queue/W312R/REVIEW.md` |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W312FR/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes the position saved in a player's file at logout and at server stop (AGENTS.md section 13) |

## What to check

1. **The logout's own check** (`LogoutExitSafety`, `RocketLogoutExitWorld`). It accepts the exact exit if the shared
   check says SAFE, or if the player would fall at most three blocks down a clear, hazard-free column onto stable
   support. For this check only, `rocket_assembler`, `landing_pad` and `machine_casing` count as ordinary blocks, and
   the rider's own rocket and other players do not block the exit. Look for a case where it accepts a place that hurts
   the player after login (fall damage, suffocation, fire, lava, magma, a cactus, a hole into the void), and for a case
   where W312R's finding F1 would still save the seat.
2. **The bay exit selector is unchanged.** `BayExitSafety`, `BayPassengerExitWorld` and the selector must behave as
   before; confirm that no shared code path changed for them.
3. **Server stop.** The `ServerStoppingEvent` handler (HIGHEST priority) detaches online riders of stationary rockets
   before `stopServer` saves them. Check that it runs before that save in Forge 47.4.10, that the seat stays assigned,
   that a connected (not disconnecting) player's dismount triggers no listener that releases a seat or starts a bay exit,
   and what happens past the shared per-tick budget (about 42 riders, by the record).
4. **The exception guard.** One WARN and an unchanged save; the expected-log entry matches only the injected test.
5. **Tests.** Do the new tests fail on W312's code (the record's red run: six of them), and do they cover the cases
   above, including the 7-1 shape on a real `rocket_assembler`?

Run the `rocket_logout_*`, rejoin, seat roster, stale seat and reconnect batches and the `bay_exit_*` batches once,
under the heavy-JVM lock, using the backslash lock path from WORKER-RULES (`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`).
A full GameTest run is not required. Work in your own Temp folder for any scratch export, and delete it when you finish.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each point above, and the commands
you ran with their results. The last line of your answer is the REVIEW.md path.
