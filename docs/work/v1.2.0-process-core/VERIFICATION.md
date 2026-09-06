# v1.2.0 process core verification

Date: 2026-09-06

Branch: `codex/v1.2.0-process-core`

Scope: `V120-CORE-01`, `V120-CORE-02`

## Verified behavior

- Process definitions enforce schema 1, encoded-size, ID, input/output count,
  ingredient-variant, duration and energy bounds.
- Tick transitions preserve progress while paused, consume exactly one configured
  energy step while running, and leave Item/Fluid mutation to completion commit.
- Completion simulation is side-effect free and emits a revisioned before/after plan
  with deterministic SHA-256 fingerprinting.
- Commit persists `PREPARED` and `APPLYING` before atomic resource replacement, then
  records the replay marker, persists `APPLIED` and clears the journal.
- Recovery applies an exact before snapshot once, finalizes an exact after snapshot,
  and preserves divergent state and journal as `RECOVERY_REQUIRED`.
- A repeated transaction UUID after journal clearing is a no-op through the persisted
  last-applied marker.

## Commands and results

See [unit-test-summary.txt](unit-test-summary.txt) and
[source-boundary.txt](source-boundary.txt).

The full release commands (`clean build`, `runData`, `runGameTestServer`), dedicated
restart matrix, long-duration load, real-GPU and two-client checks were not run for
this pure Java leaf slice. They remain required before a v1.2.0 candidate can freeze.

## Evidence integrity

Hashes for the two raw summaries are recorded in [checksums.txt](checksums.txt).
