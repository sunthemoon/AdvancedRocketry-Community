# TASK W124F: W124's review follow-ups (W124-R1, R2) under Root decision R50

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W124](TASK-W124-CARGO-C-E2-RECONCILIATION.md),
W124's record (`docs/work/v1.8.0-root-queue/records/W124.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W124-review/REVIEW.md` (SHA-256 `9eb112e5…4336`) with its probe
`probes/W124PublicationOrderProbeTest.java`, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w124`, branch `codex/v1.8.0-w124` at `6111d458`; continue on that branch |
| Record | append a "W124F" section to `docs/work/v1.8.0-root-queue/records/W124.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W124F/` (keep W124's evidence unchanged) |

## Root decision R50 (2026-10-21)

1. **W124-R1, fixed before merge.** `EscrowCargoReconciliation.applyNext` walks the source before the destination, so a
   load consumes the device permit (tank, epoch and marker) before the rocket's replay is checked against the frozen
   digests; a mismatch then holds the operation with the device already debited. When the rocket is BEFORE, finish the
   budgeted replay and validate its frozen snapshot, invariant and native-envelope commitments before publishing either
   endpoint that is still BEFORE. Keep the one-reconstruction budget and the final authority checks; do not publish the
   rocket first instead (that only reverses the risk); an endpoint already AFTER is never replayed or rolled back.
2. **W124-R2, covered before merge.** Add deterministic native cases for each direction that persist only the source
   side and only the destination side (load `(rocket 0, device 1)` and `(1, 0)`, unload `(1, 0)` and `(0, 1)`), each
   asserting the chosen side, the raw epochs and amounts and the retained PREPARED record before recovery (a deficit or
   an apparent surplus of 6,000 mB), and after each of two restarts exactly 5,000 mB, one transfer, retired records and
   pruned markers. A gated fixture may choose which side stores, in the existing probe family, inert with the gate off.

## What to do, each in its own commit with a test that fails before the change

1. The review's probe as a permanent driver-level regression (a replay mismatch for a load leaves both endpoints'
   resources, epochs and markers unchanged), then the R1 ordering fix; the same for an unload.
2. The R2 cases in the harness and oracle, then their native run as a supplementary evidence folder.
3. The record.

No saved format, schema, hash, bound, budget or timeout changes; no existing assertion is relaxed. Stop and report if any
seems needed.

## Verification

Each changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on committed
source; then the four R2 native cases (two restarts each) and a rerun of W124's cases that touch the changed ordering,
all under the heavy-JVM lock, releasing it and deleting every server copy and `build/gametest/world` in the `finally` of
the same detached script that took it (WORKER-RULES). Check C: and D: above 10 GiB and that 6 GiB of memory is free
before each Gradle run or native start.

## Not in this task

F1 (W125), C-E3, D-E, ADRs, the risk register, status files and the ledger (Root).
