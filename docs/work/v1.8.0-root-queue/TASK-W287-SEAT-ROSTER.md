# TASK W287: a vanilla dismount must clear the rocket's seat assignment

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w287`, branch `codex/v1.8.0-w287`, created by Root from the shared head |
| Decisions | R319 (QUEUE row W287) and R334, the design direction below |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 2 finding 2-4, with the screenshots and receipts it names (`m03`, `n04`, `n07`) |
| Record | `docs/work/v1.8.0-root-queue/records/W287.md`, with a design note; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W287/` |
| Review | Persistence-sensitive (AGENTS.md section 13): a one-page design note before code, and a separate read-only review before Root merges |

## What a real client showed

A player who left a landed rocket's seat by sneaking was off the rocket and on the ground, but stayed on the rocket's
seat list: after a reconnect the server put them back in the seat, and a fluid fuel loader bound to the rocket refused
it as "moving, occupied or ridden" although nobody rode it. Leaving with the flight screen's LEAVE SEAT cleared the
entry, and then neither happened. Read-only code: only the LEAVE request removes the seat assignment
(`rocket/server/RocketFlightService.java` lines 562 to 579); vanilla's sneak dismount only calls `stopRiding`;
`RocketTransferRecoveryService.tryReconnect` (lines 289 to 320) seats every listed player on login; and
`fuel/loader/LoaderEligibility.occupied` (line 124) treats a listed seat as occupied.

## Root's design direction (R334, under the owner's delegation of design points)

A dismount the player chose (a sneak, or any vanilla dismount the player initiates) while the rocket is on the ground
and not in a flight or transfer phase clears that player's seat assignment exactly as LEAVE SEAT does, through the same
server path and its persistence. A dismount caused by a disconnect, a dimension transfer, a recovery, a server stop or
other server-side handling keeps the assignment, so that reconnecting during or after a flight still seats the player
as today. Write in the design note how the server tells the two apart (for example Forge's `EntityMountEvent` with
`isDismounting`, the player's connection state and the rocket's flight state), which persisted fields change, how a
save taken between the dismount and the assignment's removal behaves, and what happens for players already saved
with a stale assignment (for example, cleared on load when the player is not riding a landed rocket). If the code
shows this direction cannot work, stop after the design note and report the alternative you recommend.

## What to do

1. The design note (one page, in the record), before code.
2. GameTests that fail on the shared head: a sneak dismount of a landed rocket followed by a reconnect does not reseat
   the player; a fluid fuel loader is not refused as occupied after such a dismount; a disconnect while seated (landed
   and in flight) still reseats on login; LEAVE SEAT behaves as before.
3. Implement the design with the smallest change, keeping server authority and the existing persistence format unless
   the note justifies a change (then with its migration and schema version).

## Verification

The new GameTests failing before and passing after; the rocket flight, transfer, recovery, reconnect and loader
GameTests; `./gradlew build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the
heavy-JVM lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, has a separate session review it, pushes for a sharded run and merges.

## Not in this task

Rider position (W292), the flight screen's texts (W289), landing selection (W290, W291), the loaders' admission
switch, the ledger, the status file and QUEUE.
