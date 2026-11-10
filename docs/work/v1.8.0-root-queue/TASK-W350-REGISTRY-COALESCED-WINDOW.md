# TASK W350: the satellite registry coalesced-flush test's window

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w350`, branch `codex/v1.8.0-w350`, created by Root from the shared head |
| Decision | Root decision R420 (QUEUE row W350) |
| Read first | W347's record on branch `codex/v1.8.0-w347` (`git show codex/v1.8.0-w347:docs/work/v1.8.0-root-queue/records/W347.md`), section 1 "Registry", and its scratch evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W347/` |
| Record | `docs/work/v1.8.0-root-queue/records/W350.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W350/` |

## Why

`SatelliteRegistryTickGameTests.coalescedAndAutosaveRequestsArePublishedOnLaterTicks` failed once in W342's first
local full run with "The commit was not requested by the coalesced flush". W347 established the cause: the batch
started one to three ticks after the autosave of tick 84,000. In that tick the autosave started a registry write, and
the coalesced flush in the same tick's END phase asked again. The registry writer starts at most one capture per 10
ticks (`CheckedRootWriter.CADENCE_TICKS`), so that request stayed pending. It started at about tick 84,011 and
captured the test's change, so the cover arrived without a coalesced flush counted after the test's own read. W347's
scratch run reproduced it (variant R1) and showed that two variants without that setup pass.

## What to do

1. **Fix the test's window, not production.** Open the test's measured part only when the registry writer is idle
   (no capture in flight and no pending request) and the next autosave falls after the window. Use the existing
   `gametest/GameTestAutosave` helper and an existing read-only view of the writer's state, or add a test-only
   accessor in the test support code. The test's assertions and its measured timeout stay as they are. A bounded
   wait before the measured part is allowed, with its own bound, as W347 did for the W159 fixtures. Raising
   `timeoutTicks` alone is not a fix (AGENTS.md section 5).
2. **Check the siblings** in `SatelliteRegistryTickGameTests` and other registry GameTests that assume no other write
   request in their window. Change only those with the same weakness, and list the ones you checked.
3. **A regression**: reproduce W347's R1 setup (a request left pending by the writer's cadence when the test body
   starts) in a GameTest or a scratch run, and show that the fixed test waits and then passes, while the old test
   fails in the same setup. Do not make a test wait thousands of ticks for an autosave.

## Verification

- The changed batches alone, repeated (at least 10 runs), then the full GameTest run with
  `scripts/check_gametest_log.py`; any manifest addition names only lines your tests emit.
- `./gradlew build` with every unit test.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code, including the registry writer's cadence; the W159 fixtures (W347); other flaky tests; the status
file, QUEUE and the risk register.
