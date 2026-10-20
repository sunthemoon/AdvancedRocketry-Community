# TASK W116F: W116's review follow-up, the contention GameTest's shared force batch (W116-R1)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W116.md](records/W116.md) (section 7.2)
and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W116-review/REVIEW.md` (SHA-256 `d032498c…de9e`), finding
W116-R1 and its probe in `D:/GitHub/ARCE-Task-Evidence/root-queue/W116-review/probes/`, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w116f` (new; from the shared branch at the commit Root names in the launch prompt) |
| Branch | `codex/v1.8.0-w116f` (local commits only; Root merges) |
| Record | append a "W116F" section to `docs/work/v1.8.0-root-queue/records/W116.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W116F/` |

**The finding.** `RocketRetirementCoverageGameTests` stages a governed capture, returns to the tick loop, then stages
the ordinary capture on a later tick, and asserts only a gap below ten ticks. The barrier can dispatch the first
capture at once after an idle interval (`EscrowBarrier.java:273-276`), so the pair may use two forces and the test can
pass without ever running `forceMixed`. The mixed-batch unit tests (`EscrowMixedBatchTest`) do prove the production fix;
this is a GameTest evidence gap.

## What to do (test code only)

1. Make the contention GameTest (and the shared-slot GameTest, if it has the same gap) establish a known open batch
   window before staging the pair, and assert that both captures enter the same dispatched batch or share its single
   worker force, through the existing read-only test accessors or a new package-private, test-only one. Keep every
   existing assertion (proof validity, the governed counters, the ordinary return).
2. Show the new assertion fails when the pair is staged as before (a temporary local change, kept as a patch in the
   evidence folder, not committed).
3. Correct the record's same-batch claim in section 7.2.

No production behaviour, batching, budget, timeout or existing assertion changes. Stop and report if any seems needed.

## Verification

The changed GameTest classes through a targeted GameTest run, then one full GameTest with the unchanged log checker on
committed source, under the heavy-JVM lock (other workers hold it in turn; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB first, and that 6 GiB of memory is free before each Gradle run.

## Not in this task

W98-R1's fix, W98-R6 (Root decision R42), N3-B, ADRs, the risk register, status files and the ledger (Root).
