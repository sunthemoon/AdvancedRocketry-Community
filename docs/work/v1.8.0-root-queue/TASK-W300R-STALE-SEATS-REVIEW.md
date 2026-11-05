# TASK W300R: independent review of W300's stale seat release

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w300` at `261573290` (base `ee35242f2` on the shared branch), checked out read-only and detached at `D:/GitHub/arce-v180-w300r` |
| Implementer's record and design | `docs/work/v1.8.0-root-queue/records/W300.md` on that branch (the design section was committed before the implementation); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W300/` |
| Task it implements | [TASK-W300](TASK-W300-STALE-SEATS.md) with Root decisions R338, R343 and R344 |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W300R/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes persisted seat assignments (AGENTS.md section 13) |

## What to check

1. **Who loses a seat.** A seat is released only for an online, connected, alive, non-fake player who is not rejoin
   guarded, not in an internal move, has no pending reconnect, and has not ridden that loaded, operational, stationary
   rocket without an active transfer for 40 consecutive eligible ticks. Look for a path that releases a seat that must
   be kept: a landed disconnect and its reconnect (including a reconnect delayed by journal or chunk readiness), a
   player in another dimension while the rocket's chunk stays loaded, a held bay exit, a rocket on a station or another
   body, a server stop or restart, a dimension change in the same tick, and a player who boards a second rocket.
2. **The shared LEAVE helper.** W300 moved LEAVE into `RocketSeatRelease` and, after its first full run, fixed a
   regression where the helper looked up the player in the online list instead of keeping the explicit caller
   (`a1b2ddc5e`). Compare the menu LEAVE, sneak and new absence paths with `RocketFlightService` before W300 (at the
   base) and say whether any caller now behaves differently.
3. **The absence grace during terminal transfer holds** (`ea1d6983a`, `e452d7142`): that it resets whenever the hold
   applies and starts fresh after it.
4. **Persistence and crash windows.** No schema change is claimed. W300's record names a risk: a crash before the
   entity is saved can restore the previous seat. Say whether that is acceptable (it restores a seat, it never loses
   one) or must change.
5. **The operator command** `/arce rocket release-seat <rocket> <player>`: permission level 2 rechecked at execution,
   only an already loaded rocket, cached profiles only (no remote lookup), and each refusal reported.
6. **W287FR's notes N1 to N3:** the WARN keeps the exception, `sealedFrozen` is evaluated once per rocket tick only
   before the first release, and `stationary` is shared.
7. **Tests.** The 19 new GameTests: do they fail without the code they test, and are the keep cases covered?
8. **File sizes.** `RocketTransferService.java` is at 798 lines; say whether W300's additions there belong elsewhere.

Run the seat roster and stale seat GameTests on the branch several times, and the reconnect and rejoin batches once,
under the heavy-JVM lock (`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`, protocol in WORKER-RULES). Build with
`--no-build-cache` or `compileJava --rerun`. A full GameTest run is not required. Work in your own Temp folder for any
scratch export, and delete it when you finish.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each point above, and the
commands you ran with their results. The last line of your answer is the REVIEW.md path.
