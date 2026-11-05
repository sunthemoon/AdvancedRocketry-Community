# TASK W287FR: independent review of W287F's fixes to the seat roster change

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w287` at `2b4de3a28`: W287's change (`d4185146d`, base `ef33f273f`) plus W287F's fixes `41284cf53`, `e0fe0efed` and record `2b4de3a28`. Root creates a read-only detached checkout at `D:/GitHub/arce-v180-w287fr` |
| Earlier review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W287R/REVIEW.md` (CHANGES REQUIRED: F1 High, F2 Medium, F3 and F4 Low; F5 is queued separately as W300) |
| Fix task | [TASK-W287F](TASK-W287F-SEAT-ROSTER-FIXES.md); the implementer's record is `docs/work/v1.8.0-root-queue/records/W287.md` on the branch (W287F section); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W287F/` |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W287FR/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes a persisted seat assignment (AGENTS.md section 13) |

## What to check

1. **F1.** A sneak while `RocketRuntime.sealedFrozen(rocket)` is true, or while `updateFlightData` refuses, no longer
   throws out of `RocketEntity.tick`; the seat is kept; one bounded WARN is logged. Check that the catch cannot hide a
   half-applied change (a player dismounted while the flight data says otherwise, in a way that reconnect or save then
   handles wrongly), and whether catching every `RuntimeException` hides failures that should still surface.
2. **F2.** `RocketFlightService.java` is under 800 lines, and `RocketSeatInput` keeps the same guards as the code it
   replaced (compare with `d4185146d`).
3. **F3.** The added GameTests cover the three cases W287R named and fail without the guards they test. They set
   private fields by reflection (`activeTransferId`, `unreadable`); judge whether that is sound and whether it leaves
   state behind for later tests on failure.
4. **F4.** The INFO line when a sneak releases a seat.
5. **The expected-log entry** added to `scripts/gametest_expected_log.json`: it must be scoped to the one test that
   drives the WARN, with an exact count.
6. **New batch.** W287F adds the GameTest batch `rocket_seat_roster_refusal`, which moves later tests' positions.
   Say whether its fixture can depend on where it lands.

Run the seat roster GameTests on the branch (several times) under the heavy-JVM lock
(`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`, protocol in WORKER-RULES). Build with `--no-build-cache` or
`compileJava --rerun` (W287R's note F6) and say which you used. A full GameTest run is not required.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each of F1 to F4, and the
commands you ran with their results. The last line of your answer is the REVIEW.md path.
