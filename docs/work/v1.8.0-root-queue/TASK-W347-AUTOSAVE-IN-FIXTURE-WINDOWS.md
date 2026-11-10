# TASK W347: vanilla's autosave inside GameTest fixture windows

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w347`, branch `codex/v1.8.0-w347`, created by Root from the shared head |
| Decision | Root decision R417 (QUEUE row W347) |
| Evidence | Shard 0 of shared run 42748839833 at `3a82236e8`, copied to `D:/GitHub/ARCE-Task-Evidence/root-queue/ci/42748839833/job-129611574570.log` |
| Record | `docs/work/v1.8.0-root-queue/records/W347.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W347/` |

## Why

In shard 0 of shared run 42748839833 every test passed, but the log check failed in batch
`rocket_rejoin_replace_failure` (`RocketRejoinReadbackGameTests.realReplacementFailureEventCannotProveOldFile`).
The manifest expectations `w159-player-backup-delete-sequence`, `w159-player-backup-retries` and
`w159-player-backup-abort` expect one failed player-file backup (10 WARN, 10 ERROR, 1 abort). The log had two (20,
20 and 2). The first came two ticks after the fixture's player logged in, before placement; the batch's prompt ticks
were `[12000, 12100]`. Vanilla 1.20.1 autosaves when `tickCount % 6000 == 0` (`MinecraftServer.tickServer`, checked
in W338's javap log `vanilla-MinecraftServer-javap.log`, offsets 59 to 96), and that autosave saves every player. So
the autosave at tick 12,000 fell inside the window in which the fixture blocks the player's `.dat_old` backup and
added a second failed save. The test's own assertion (`saves >= 2`) tolerated it; the exact manifest count did not.

`SatelliteRegistryTickGameTests.coalescedAndAutosaveRequestsArePublishedOnLaterTicks` failed once in W342's local
full run with "The commit was not requested by the coalesced flush", which may be the same mechanism: a save request
from the autosave landing in a short window the test expects to be quiet.

## What to do

1. **Confirm the cause** of both failures from the logs and the code. For the registry test, use W342's evidence
   under `D:/GitHub/ARCE-Task-Evidence/root-queue/W342/` (its first full run) and say whether the autosave tick falls
   in the failing window. If it does not, say what does, and stop at the evidence for that test.
2. **A shared helper** in the GameTest support code that tells a fixture how many ticks remain until the next
   autosave tick. A fixture whose window must not contain an autosave waits outside its measured window (before it
   installs its fault, blocks a file or starts counting) until the whole window fits before the next autosave tick
   or the autosave has passed. Do not disable or delay the server's autosave, and do not change production code.
3. **Fix the W159 Replace and Temp fixtures** (`RocketRejoinReadbackGameTests`), and the registry test if step 1
   confirms the same cause. Assertions, manifest counts and the measured timeouts stay as they are. If the wait needs
   ticks, keep it out of the measured budget as W344 does for the bay exit fixture. Raising `timeoutTicks` alone is not
   a fix (AGENTS.md section 5).
4. **Sweep**: list every GameTest whose window injects a save fault, blocks a world or player file, or expects an
   exact count of saves or save-related log lines in a short window. Fix only those an autosave can disturb, and
   record the ones you checked and left alone, with the reason.
5. **A regression**: a unit test of the helper's arithmetic at the boundaries (tick counts just below, at and just
   after a multiple of 6000), and a check that the fixed W159 Replace fixture does not open its window when the next
   autosave tick falls inside it. Do not make a test wait thousands of ticks for an autosave. Show, in a scratch run
   that is not committed, that the old fixture logs two failed backups when its window contains an autosave tick.

## Verification

- The changed batches alone, repeated (at least 10 runs each), then the full GameTest run with
  `scripts/check_gametest_log.py`; any manifest addition names only lines your tests emit.
- `./gradlew build` with every unit test.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; the server's autosave; other flaky tests; the status file, QUEUE and the risk register.
