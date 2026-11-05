# TASK W300: seats that outlive the player who left them

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w300`, branch `codex/v1.8.0-w300`, created by Root from the shared head (W287 merged at `77c62ef3b`) |
| Decisions | Root decisions R338 and R343 (QUEUE row W300) and R344 (the design direction below) |
| Background | `D:/GitHub/ARCE-Task-Evidence/root-queue/W287R/REVIEW.md` note F5; `D:/GitHub/ARCE-Task-Evidence/root-queue/W287FR/REVIEW.md` notes N1 to N3; [W287's record](records/W287.md) and design note; [TASK-W287](TASK-W287-SEAT-ROSTER.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W300.md` (with the design page as its first section); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W300/` |
| Review | This changes persisted seat assignments, so a separate read-only review follows your delivery (AGENTS.md section 13) |

## The problem

Since W287, sneaking out of a seat on the ground releases it. Other ways of leaving still keep the seat of a player
who no longer sits in the rocket: death while seated, `/tp`, `/ride ... dismount`, switching to spectator, a held bay
exit, a chorus fruit or ender pearl, and seats saved before W287. The reconnect then reseats the player, a bound
loader reports the rocket occupied, and an operator has no way to clear a seat. Seats kept for a player who
disconnected while the rocket was landed must stay: players are never saved as passengers, so "not riding at load"
cannot tell the two apart.

## Design direction (Root decision R344)

Write the one-page design as the first section of the record and commit it before the implementation. Use this
direction unless you find a case it breaks; then say so in the design and choose the narrowest safe alternative.

1. **Online and not riding means released.** A seat is released when its player is online on this server, connected,
   alive or respawned, not rejoin-guarded, not inside a trusted internal passenger move, and has not ridden that rocket
   for a fixed grace period (for example 40 ticks) while the rocket is loaded, operational and stationary
   (ASSEMBLED, FUELED or LANDED) with no active transfer. An offline player's seat is never released this way, so a
   landed disconnect keeps its seat. The reconnect path that reseats a player must run before the grace period can
   expire for that player.
2. **Bounded.** Check seats from the rocket's own server tick or a bounded per-tick scan, never by searching the world;
   at most the existing seat limit per rocket, and no chunk loading.
3. **Same release path.** Release through the existing `leave()`/`updateFlightData` path (or a shared helper that
   applies the same validation), with W287F's guards: skip while `sealedFrozen`, catch and log a refusal, keep the
   seat on refusal. Log one INFO line per release with its cause (for example `cause=ABSENT`).
4. **Held bay exits.** Find out whether a held bay exit keeps the seat on purpose (for example while a transfer or
   a bay claim depends on it). If it does, keep it while the hold lasts and release it after.
5. **Operator command.** Add an operator command (permission level 2) that releases one named player's seat on one
   loaded rocket, for a player who will never return; it refuses for a rocket in flight or transfer and reports the
   result. Follow the existing rocket command style and its argument validation.
6. **W287FR's notes (R343).** N1: pass the exception to the logger in `ARCE_SEAT_RELEASE_HELD` so the stack trace is
   kept (the expected-log entry still matches the message line). N2: evaluate `sealedFrozen` once per tick, only
   before the first `leave()`. N3: share `stationary` with `RocketFlightService` instead of copying it.

No new saved field is expected. If you need one, it needs a schema version and a migration test, and you must say
why in the design.

## Tests

GameTests for each way of leaving in the problem list (at least death, `/tp`, spectator, `/ride ... dismount` and an
ender pearl or chorus fruit), each releasing the seat after the grace period; a landed disconnect and reconnect that
keeps and restores the seat; a rejoin-guarded player and an internal move that keep it; a frozen root that keeps it;
a seat saved before W287 (a player online but not riding) that is released; the held bay exit as decided; the
operator command's success and its refusals. Each new test must fail without the code it tests.

## Verification

The new and existing seat roster, reconnect, rejoin, bay exit, loader and transfer GameTests; `./gradlew build` with
all unit tests (use `--no-build-cache` or `compileJava --rerun`, see W287R's note F6); the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`. Any new WARN a test deliberately drives needs its own expected-log entry for that test
only.

Commit on the branch; do not push. Root has the change reviewed, pushes for a sharded run and merges.

## Not in this task

Rider position (W292), seats in flight or transfer, the ledger, the status file and QUEUE.
