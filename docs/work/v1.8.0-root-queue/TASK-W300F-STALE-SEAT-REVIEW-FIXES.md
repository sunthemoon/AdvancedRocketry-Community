# TASK W300F: W300R's notes on the stale seat release

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w300f`, branch `codex/v1.8.0-w300f`, created by Root from the shared head after W300's merge |
| Decision | Root decision R348 (QUEUE row W300F) |
| Review to answer | `D:/GitHub/ARCE-Task-Evidence/root-queue/W300R/REVIEW.md`: APPROVE WITH NOTES, findings F1 to F8 |
| Earlier work | [TASK-W300](TASK-W300-STALE-SEATS.md) and `records/W300.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W300F.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W300F/` |

## What to change

1. **F1 (Medium).** `RocketSeatInput` treats a player as riding only when `player.getVehicle()` is this rocket entity,
   so a player riding another loaded copy of the same logical rocket (same `assemblyTransactionId`, another entity
   UUID) is counted absent and loses the seat after 40 ticks; the next reconcile can then keep the copy without it.
   Treat a player whose vehicle is any `RocketEntity` with the same assembly transaction ID as riding (reset the timer,
   never release). Also hold the timer while a `RocketTransferFences` fence names this logical rocket, so a fenced
   assignment that the login skips is not released (`MATCH_LIMIT` is not escrow-backed). Add a GameTest with two loaded
   copies of one logical rocket, and one for the fence, each failing before the change.
2. **F3 (Low).** The shared guard now includes `RocketRuntime.seatReconnectPending(player)`, which is true for every
   player while the transfer journal is not operational, so a ground sneak no longer releases the seat then (W287's
   behaviour changed). Evaluate `seatReconnectPending` only on the absence branch (`!riding`), so the sneak path is as
   W287 left it, with a test.
3. **F2 (Low), documentation.** In `records/W300F.md`, state the accepted limits: a stationary rocket with no transfer
   journal record (assembled or fuelled and never launched, or a landed record retired) reseats nobody at login, so an
   online player who is not riding loses the seat after the grace period and can BOARD again; and a reconnect that
   expires after `MAX_WAIT_TICKS` is followed by the same release. Add a GameTest that pins the record-less behaviour.
4. **F4 to F8.** Read them; fix any that is a one-line change with a test (for example F6's strong `ServerPlayer`
   references, if a UUID suffices), and say in the record why the rest stay as they are. If `RocketTransferService.java`
   would go past 800 lines, move code out instead.

## Verification

The new tests; the seat roster, stale seat, reconnect, rejoin, bay exit and transfer GameTests; `./gradlew build` with
all unit tests (`--no-build-cache` or `compileJava --rerun`); the full GameTest run with `scripts/check_gametest_log.py`
under the heavy-JVM lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The grace period's length, seats in flight or transfer, rider position (W292), the ledger, the status file and QUEUE.
