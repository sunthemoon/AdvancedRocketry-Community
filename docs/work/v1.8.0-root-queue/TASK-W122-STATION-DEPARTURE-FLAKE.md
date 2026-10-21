# TASK W122: parallel bug, the closed-orbit station departure GameTest that fails under local load

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first, and the memory of earlier fixes of this
class in QUEUE's parallel bugs (W73, W74, W76, W78, W102, W111: timing races fixed without a longer wait). You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w122` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w122` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W122.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W122/` |

## The failure

`PlanetaryAdmissionGameTests.closedOrbitStationCanDepartButStillRequiresLiveMembership` (batch `planetary_admission`)
fails with "Authorized closed-source departure did not enter countdown" at its post-acknowledgement COUNTDOWN assertion
(`PlanetaryAdmissionGameTests.java:389-394`). It failed in W98's local 72-batch run at `d74fdde3`, W118's second full run
at `78b5968a` (13 host-overload warnings), W117's final run at `9a46b712` (18) and W119's resumed run at `a0ad2fc1`, and
passed in the runs around each; it has not failed on a hosted run. W119-review traced the path: player flight intent,
`RocketFlightService.launch` (`:244`), `RocketTransferService.prepareLaunch`, the journal acknowledgement continuation
(`RocketTransferService.java:194-206`), which checks the current entity and flight permission before installing
COUNTDOWN; in W119's log the request answered `JOURNAL_PENDING` and no `ARCE_TRANSFER_PREPARE` followed before the
assertion. The four failing logs are under `D:/GitHub/ARCE-Task-Evidence/root-queue/` (W98, W118, W117 and W119 folders).

## What to do

1. Find what the continuation waited on or refused (the journal acknowledgement, the entity, the permission or the test's
   own wait), from the four logs and the code; reproduce it locally under load (for example the W111 load script's
   approach), in its own batch if needed.
2. Make the test deterministic without raising any timeout, budget or wait (AGENTS.md section 5): wait on the actual
   condition, pace to real ticks (WORKER-RULES), or isolate the fixture. If production code is wrong, stop and report
   with the evidence instead of changing it.
3. Show the fix: the test passes repeatedly under the same load that made it fail before (at least 20 loaded runs of its
   batch), then one full GameTest with the unchanged log checker, under the heavy-JVM lock, releasing it and deleting
   `build/gametest/world` in the `finally` of the same detached script that took it.

Check that 6 GiB of memory is free before each Gradle run and C: and D: above 10 GiB before heavy starts; any load
generator you start must be bounded in memory and time and stopped in a `finally`.

## Not in this task

Other flaky tests (record what you see), production behaviour, ADRs, the risk register, status files and the ledger
(Root).
