# TASK W349: fix W348's findings in W346's save-boundary case

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w346`, branch `codex/v1.8.0-w346` at `c4cabd653` with this TASK merged by Root. W346's session has ended; its work is committed. |
| Decision | Root decision R419 (QUEUE row W349), from W348's review `D:/GitHub/ARCE-Task-Evidence/root-queue/W348/REVIEW.md` (read it first, with its probes) |
| Read first | [TASK-W346](TASK-W346-B65-NATIVE-SAVE-BOUNDARY.md), `records/W346-DESIGN.md` and `records/W346.md` in this worktree |
| Record | `docs/work/v1.8.0-root-queue/records/W349.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W349/` |

## Why

W348 found the save-boundary control and the native save and rebuild sequence sound, but the native oracle in
`scripts/run_v180_bay_save_boundary.py` never inspects inventories. Its probe `probe_complete_case.py` makes the full
case pass both restarts with the registered data satellite package in a saved player inventory. TASK-W346 item 3
requires that no package of the group is in any bay, inventory or item entity after restart 1, and that restart 2
keeps that state. W346 merges only with this fixed.

## What to do

1. **Finding 1, inventories (blocking).** After the kill and after each restart, check every persisted inventory
   the stopped world holds for the group's registered package, by its satellite identity: player files (inventory
   and ender chest), and container and other non-bay block-entity payloads in the region files the case reads. Do
   not count the deliberately unregistered solar package, and do not read the held destination Level's unsaved
   terrain as kill-state evidence. If you limit the scan to known locations, prove the fixture has no other
   inventory-bearing location and reject any unexpected one. Add negative tests that make the complete case fail
   with such a package in a player file and in a container payload, using W348's
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W348/probe_complete_case.py` and `probe_oracles.py` as the starting
   point. W348's probe must then report FAILED for its wrong state.
2. **Finding 2, the local permission predicate.** Add a gate test that registers `BaySaveBoundaryReleaseTestCommands`
   alone, as W343 did for R042 (`theR042RegistrationItselfRequiresPermissionTwo`), so removing this class's own
   permission-2 predicate fails a test. Show it with a scratch mutant that is not committed.
3. **Finding 3, POI.** Add a labelled correction to `records/W346-DESIGN.md`: POI columns are written by
   `poiManager.tick` without a `noSave` check, so `noSave` does not stop them; the case uses no POI data as evidence.
4. **Native smoke on a local JAR** of `W346_B6_SAVE_BOUNDARY` with both restarts under the stricter oracle, and the
   M1 scratch JAR again, which must still fail for the same reason. Discard the scratch copies.

## Verification

- The W346 runner tests, the new negatives, the bay runner, W331 and driver tests, with PYTHONPATH set to the tests
  directory and TEMP and TMP on D:.
- `./gradlew build` with every unit test (item 2 touches a Java test).
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; the save-boundary command's behavior; W345's driver families; the status file, QUEUE and the risk
register.
