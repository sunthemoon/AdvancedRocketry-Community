# TASK W119F: W119's review follow-up, the terminal's blocked-registry result (W119-R1)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W119](TASK-W119-W27C2-REGISTRY-PROOF.md),
[records/W119.md](records/W119.md) and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W119-review/REVIEW.md`
(SHA-256 `4980aae0…c7c8e`), finding W119-R1, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w119f` (new; from the shared branch at the commit named in your prompt, which has W119 merged) |
| Branch | `codex/v1.8.0-w119f` (local commits only; Root merges) |
| Record | append a "W119F" section to `docs/work/v1.8.0-root-queue/records/W119.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W119F/` |

**The finding.** With the same owner, identity and blocked registry, `SatelliteLaunchService` returns
`REGISTRY_BLOCKED` (`SatelliteLaunchService.java:75-78`), but the terminal adapter throws and maps it to
`UNSUPPORTED_DATA` (`SatelliteManager.java:213-226`). Both refuse and keep the package, but section 7 of
[records/W27A-DESIGN.md](records/W27A-DESIGN.md) (lines 645-653) requires the terminal and the service to give identical
results, and its F4 matrix includes blocked registries (lines 851-859). Root decides: the terminal gives the service's
result.

## What to do, each in its own commit

1. A test that fails before the fix: the terminal and the service, given the same owner, identity and blocked
   registry, return the same result code, and the package stays in the terminal.
2. The fix: the terminal reports `REGISTRY_BLOCKED` and its EN and zh text, without using an exception to change the
   result; keep any existing diagnostic log line.
3. The record.

No saved format, schema, bound, budget or timeout changes; no existing assertion is relaxed. Stop and report if any
seems needed.

## Verification

The changed test classes alone, then the satellite packages, then the whole unit suite, each on committed source; one
Gradle process at a time, with 6 GiB of free memory checked before each. If a GameTest asserts the old terminal result,
change it only as this decision requires and say so; run the affected GameTest batch under the heavy-JVM lock,
releasing it and deleting `build/gametest/world` in the `finally` of the same detached script (WORKER-RULES).

## Not in this task

W27c3, W27d, W27e, ADRs, the risk register, status files and the ledger (Root).
