# TASK W36: diagnose and fix the joint-proof `SUPERSEDING_SAVE` GameTest flake

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w36-joint-proof` |
| Branch | `codex/v1.8.0-w36-joint-proof` (local only; Root merges) |
| Kind | Parallel bug (escrow test reliability) |
| Record | `docs/work/v1.8.0-root-queue/records/W36.md` |

## Problem

The test `EscrowCoreGameTests.jointTerrainSlotProofRetainsTheOtherEndpointLease` fails
intermittently on local runs with "Joint terrain proof did not confirm both endpoints:
SUPERSEDING_SAVE, proofs 0".
- **Where it failed:** W28, W29, W34 and W11b2 continuation 04 each saw it once.
- **Hosted CI:** it has not failed there yet.
- **The first fix did not hold.** Root fix `4b116fae` saves the fixture chunk once before the
  single-attempt barrier, and the failure still happened on a branch that contains it.

The barrier's refusal is correct behaviour, because a newer save of the endpoint chunk
superseded the proof. The question is which save that is.

## Scope

1. **Find the superseding save.**
   - Instrument, temporarily or behind a test-only flag, the escrow save monitor
     (`EscrowRuntime.monitor()` and the barrier's generation check). Record which code path
     produced the superseding save:
     - vanilla eager unload saves (`ChunkMap.processUnloads` and `saveChunkIfNeeded`);
     - autosave (`saveAllChunks`);
     - the barrier's own store being misattributed;
     - an entity-section save;
     - the GameTest framework's template cleanup;
     - another test in the same batch.
   - Reproduce it. For example, run the `escrow_core` batch repeatedly in one GameTest server,
     or under load.
   - Cite the vanilla methods from the mapped jar with `javap`.
2. **Fix the cause.**
   - **If the barrier misattributes its own save, or a save of an unrelated chunk:** that is a
     product bug. Fix it in the barrier or monitor, with a unit test.
   - **If it is a legitimate vanilla save the fixture cannot prevent:** make the fixture
     deterministic without weakening the assertion. Options:
     - hold the chunk out of eager saving;
     - wait, paced, for vanilla's pending save of the chunk to finish before starting the
       barrier;
     - fix the batch order, so another test does not save the chunk.

     Retrying the barrier is acceptable **only** if the test still asserts that the first
     attempt either confirmed or was refused with `SUPERSEDING_SAVE` for an identified
     legitimate save, and the record explains why.
   - Never relax the confirmation, shared-capture or invalidation assertions.
3. **Evidence.** Run the fixed test at least 30 times in a loop (an `escrow_core`-only GameTest
   run, repeated), with zero failures. Then run the full `runGameTestServer` once under the lock.

## Commands

- The whole unit suite once.
- The repeated escrow GameTest runs, and one full `runGameTestServer` under the lock, then the
  log checker.

## Not in this task

- Other escrow behaviour.
- W11b2 (in progress elsewhere).
- Status files and the ledger.
