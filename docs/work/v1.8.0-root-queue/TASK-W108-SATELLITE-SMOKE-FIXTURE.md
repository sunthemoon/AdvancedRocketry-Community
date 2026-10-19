# TASK W108: the v1.3 satellite payload smoke's fixture spaces its terminal actions (W101 finding)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) and `records/W101.md` section 5 first.
You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w108` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w108` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W108.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W108/` |

## The problem

W101 (merged `d2f0d00c`) made seven of the eight v1.3 native smokes pass on v1.8. The satellite payload
smoke still fails at the fixture's `arce_sat_fixture create`: `SatellitePayloadFixture` (in
`src/adapterTest`, lines 60 to 64) places the terminal, fills it, and calls `act(terminal, OWNER, 2)`
(assemble) and `act(terminal, OWNER, 3)` (launch) in the same tick. v1.8 refuses a player's second
state-changing intent within `satellites.intentIntervalTicks` (ADR-049 section 10, C8a; at least 10
ticks), so `act` throws. The smoke's reinstalled phase also sends `claim` and its replayed `claim`
back to back. W101 inferred this from the code; confirm it first.

## What to do

1. Confirm the cause natively or with a GameTest-free reproduction (for example the server log with the
   command's exception visible, or a focused unit test of the fixture command path). If the cause is
   something else, stop and report.
2. The fixture's `create` places, fills and assembles only. The smoke sends the existing `launch` action
   as a separate command at least 10 ticks later, and spaces the reinstalled phase's two `claim`
   commands by at least 10 ticks. Measure the gap in server ticks (for example by waiting for a logged
   tick count or game time), not with a wall-clock sleep alone. The minimum interval itself stays
   unchanged.
3. Keep every check of the smoke, including the replayed claim's IDEMPOTENT result. The adapter GameTest
   that calls the same `create()` helper must keep passing unchanged.
4. Run the satellite smoke natively once on a JAR built from your committed source, under the heavy-JVM
   lock, and record the result. Run the v1.3 smoke Python tests and
   `python -B scripts/validate_repository.py --require-approved-identity`.

## Write scope and not in this task

Write scope: `src/adapterTest/.../SatellitePayloadFixture.java`, `scripts/run_v130_satellite_payload_smoke.py`,
`tests/test_v130_satellite_payload_smoke.py`, and the record. No production Java change, no change to the
intent interval, and no relaxed assertion, limit or timeout.

Take the heavy-JVM lock for the build and the native run, and release it at once afterwards; other
workers wait for it. Check that C: and D: have more than 10 GiB free first. Delete the smoke's server copy
afterwards; keep logs, results and manifests under 100 MB. If the tool refuses your cleanup, write the
paths in the record. Not in this task: other smokes, ADRs, the risk register, status files and the ledger.
