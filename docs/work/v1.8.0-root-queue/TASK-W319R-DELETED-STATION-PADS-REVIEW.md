# TASK W319R: independent review of W319's deleted-station pad cleanup

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w319` at `0a1e01074` (base `f8af1335f` on the shared branch), checked out read-only and detached at `D:/GitHub/arce-v180-w319r` |
| Implementer's record and design | `docs/work/v1.8.0-root-queue/records/W319.md` on that branch (the design section was written before the code); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W319/` |
| Task it implements | [TASK-W319](TASK-W319-DELETED-STATION-PADS.md), Root decision R372 |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W319R/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes what the saved `LandingPadIndex` keeps when a station is deleted (AGENTS.md section 13) |

## What to check

1. **When removal happens.** `StationManager` calls `LandingPadIndex.removeStation` after every deletion guard and the
   registry deletion succeed, and before the existing flush. Look for a path where a refused, failed or partial deletion
   removes pad entries, or where a deleted station keeps them.
2. **What it removes.** Only that station's bucket (at most eight entries) and the transient console choices that name
   it; other stations, their claims and choices, and the epoch counter are unchanged. Check whether removing a bucket
   while a landing or transfer still holds a claim on one of its pads can strand that transfer.
3. **Old saves.** Existing orphan entries are kept on load, with no pruning or scan. Say whether that is right, and
   whether an unavailable or malformed index is preserved as before.
4. **Schema and limits.** Schema 1, fields, limits, IDs and epoch meaning unchanged.
5. **The escrow test cleanup and the expected-log counts.** The two escrow GameTests now remove their synthetic
   entries; `scripts/gametest_expected_log.json` gains counts for the three new batches' station deletions. Confirm
   that these lines come from the new tests themselves and that no unrelated warning is hidden.
6. **Tests.** Do the new GameTests and unit tests fail without the fix?

Run the new `pad_station_delete*` batches, the landing pad, station and pad selection batches, and the two escrow
batches once, under the heavy-JVM lock, using the backslash lock path from WORKER-RULES
(`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`). A full GameTest run is not required. Work in your own Temp folder
for any scratch export, and delete it when you finish.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each point above, and the commands
you ran with their results. The last line of your answer is the REVIEW.md path.
