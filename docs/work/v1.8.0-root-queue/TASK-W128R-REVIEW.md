# TASK W128R-review: independent read-only review of W27d1, the bay deployment coordinator

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w128r` at `b8d38e275`, on the shared commit `573f288c8`. Read `git diff 573f288c8..b8d38e275`. Review worktree `D:/GitHub/arce-v180-w128r-review`, a detached checkout of `b8d38e275` |
| Task it answers | [TASK-W128](TASK-W128-W27D1-COORDINATOR-ADMISSION.md): Root decision R49 (Q1 to Q10) and the R121 resumption section |
| Design | [records/W27A-DESIGN.md](records/W27A-DESIGN.md) sections 4, 4.1, 8, 8.1, the W27d row of section 9, B1 to B18 and section 10; the W27d1 sub-leaf of [records/W127-PLAN.md](records/W127-PLAN.md); [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) item 11 and [records/W129-DESIGN.md](records/W129-DESIGN.md) sections 4.6 and 5.1 |
| Record | `docs/work/v1.8.0-root-queue/records/W128.md` on the branch: the one-page design and the "Resumption" section |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R/` (hashes in the record) |
| Risk draft | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-review/input/R-042-draft.md`, Root's draft of the rest of risk entry C |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice: it writes INTENT and REJECTED receipts and holds rockets in flight. Deployment admission
must stay off outside the GameTest server and the gated release-test hooks.

## What to check

1. **Admission stays off in production.**
   - Trace every path into the coordinator's checkpoint, D1 and D2. Each must need a GameTest server or the gated
     release-test hooks.
   - In a production world without a receipt file (NO_FILE), the gate lock's bay part must always be false, and no
     production behaviour may change.
   - `BayReceiptCommitCut` and `BayReleaseTestCommands` must be installed only where `EscrowTestEndpoints` allows.
2. **B14 under ADR-074 item 11.** Check the record's table:
   - WRITE_PENDING waits with the claim held and nothing run;
   - a REFUSED request rejects every bay;
   - UNPROVED after the request rejects every bay; WRITE_REQUIRED requests again;
   - at stopping, and on the crash path without a stopping event, no effect runs after the latch.
3. **D1 and D2.**
   - Reservations go through the real admission authority.
   - FAILED_BEFORE_REPLACE restores the prior state; UNKNOWN retries the exact candidate.
   - The registry permit works only inside one validation call and for its previews. `launch`, `launchIdle` and
     `decommission` ignore it, and no public caller can construct one.
   - Every D2 outcome is REJECTED. Nothing is registered and no package is consumed.
4. **Exits and restart rows.**
   - Q1's claim lifetime.
   - The restart-row table in the one-page design.
   - REPAIR and REGISTRY_BLOCKED hold, and runtime holds write nothing.
   - The exit wait has no time limit. Can it hold a rocket after every receipt naming it is gone?
5. **The gate lock (Q3, Q8).**
   - The design lists the production call sites. Check each against the code, and look for any call site it misses.
   - `escrowIdle` and `escrowLogicalLocked` stay escrow-only.
   - `BayReceipts.holdsRocket` answers true off the server thread. Find every caller of `idle`, `logicalLocked`,
     `resourcesIdle` and `holdsRocket` that can run off the server thread, such as proof readback or IO completions.
     Any such caller now treats every rocket as held.
   - A dismount is never cancelled (R49 Q2).
6. **Bounds and formats.**
   - These must hold: 256 claims, 64 cached snapshots, 4,096 runtime rejections, 32 steps per tick, at most 128
     block-entity visits per snapshot and the first 8 bays.
   - Nothing else may change: the receipt store's saved format, any other format, the protocol, or any bound, budget,
     quota or timeout.
   - The lang files change only by runData's output.
   - The three new WARN manifest entries must be exact.
   - New classes are under 500 lines.
7. **Tests and evidence.**
   - List every earlier test file the diff changes, and check that no assertion was relaxed.
   - The new tests test real behaviour, not their fixtures.
   - The native cases ran on `4e7b18843`. Check that `4e7b18843..b8d38e275` changes only GameTest classes, the log
     manifest and the record.
   - The unit suite ran on `01fad87a1`; check the same for that range.
8. **The failing GameTest.** `SealedSpecialHostAutomationGameTests.tankAndBayStaySealedUnderTickingAutomation` timed
   out in all three full runs. Root compared its batch in the W128R logs and in three green full runs from other
   branches (`W156/all-6a258d37c-20261027-095714`, `W152K/full-final`, `W129bc/continuation-20261027/final`):
   - **Wall time** from batch start to `ARCE_DISASSEMBLY_DURABILITY_COMPLETE`: 2.0 to 2.3 s on W128R, against 1.6 to
     2.6 s on the others.
   - **Ticks:** W128R's runs reached about tick 400, against 300 to 340 on the others.
   - **Root's conclusion:** this is W166's cause 1 (unpaced ticks against wall-clock proof I/O), not a slice change.

   Check this, and look for any W27d1 path the test can reach, for example `RocketManager.tick` running the
   coordinator before the sealed coordinator.
9. **Risk draft.** Check every statement in `R-042-draft.md` against the code, and list anything wrong or missing.
10. **Merge.** List conflicts with:
    - the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`);
    - W129bc at `26113dc63`, which rewrites the satellite registry's writer, including `SatelliteMissionSavedData`;
    - W152K at `96d9cf542` and W164 (`codex/v1.8.0-w164`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the seven new unit classes, `SatelliteRegistryFenceTest` and `V180SatelliteBayDataTest`;
- the `resourceescrow`, `rocket.bay` and `satellite` packages;
- `python -B -m unittest discover -s tests -p "test_run_v180_bay_forced_stop.py"`.

Targeted unit-test runs need no heavy-JVM lock (WORKER-RULES). Check 6 GiB free memory before each run. No GameTest,
runData, full build or server of your own. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W128R-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
