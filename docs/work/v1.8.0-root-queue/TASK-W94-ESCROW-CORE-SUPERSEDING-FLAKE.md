# TASK W94: the escrow-core barrier GameTest's `SUPERSEDING_SAVE` flake

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) and [records/W36.md](records/W36.md) first. You are a
new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w94-escrow-core-flake` (from the shared branch) |
| Branch | `codex/v1.8.0-w94-escrow-core-flake` (local commits only; Root merges) |
| Input | Hosted run 39047343116 at `b01f441e` (the W11b2 merge) failed one GameTest: `EscrowCoreGameTests.ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier`, "Own-store/force/stable readback did not confirm: SUPERSEDING_SAVE". Its job log is `D:/GitHub/ARCE-Task-Evidence/root-queue/W94/hosted-39047343116-regression.log` (SHA-256 `5ac00747e2f6dccc431c54bd6623bebd3657bfcdd741bb7ca238f67baba68274`; the `escrow_core` batch of 11 tests starts at line 14090 and the failure is at line 14115, 13 seconds later) |
| Record | `docs/work/v1.8.0-root-queue/records/W94.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W94/` |

## Background

W36 found that vanilla's eager per-tick save of a freshly generated fixture chunk can supersede a single-attempt
barrier proof, and fixed the joint-proof test by waiting for vanilla's own save first (`VanillaChunkSave`, whose
10-second cooldown was meant to cover the test's barrier windows). Root applied the same precondition to
`ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier`. It has now failed on a hosted run whose batch order differs
from earlier runs (1,182 GameTests on the shared branch after the W11b2 merge; 377 batch names after continuation 54).
A superseding save is a correct refusal by the barrier, not a product defect. Continuation 53's dismount failure was
the same class; continuation 54 added `EscrowGameTestLaunch` for governed F1 retries.

## What to do

1. **Find what saved the slot** in this run (the log, the barrier windows' length against the 10-second cooldown,
   entity-section saves versus chunk saves, the other `escrow_core` tests and their saves), and say whether the other
   single-attempt barrier tests in `EscrowCoreGameTests` and related classes share the race.
2. **Fix the tests, not the product.** Either keep the precondition valid for the whole proof (for example, take a
   fresh vanilla save right before each barrier window), or let the test request the barrier again after a refusal
   whose reason is exactly `SUPERSEDING_SAVE`, after the product's backoff, at most three attempts, as
   `EscrowGameTestLaunch` does. Every other refusal must still fail the test, and the final confirmed proof and the
   conservation assertions stay exactly as they are. Do not raise a timeout. Record why the change is not a relaxed
   assertion.
3. **Prove it.** Run the `escrow_core` batch repeatedly (W36 used its batch filter; at least 30 runs), then the whole
   unit suite and one full GameTest with the unchanged log checker, under the heavy-JVM lock. If you add a batch, say
   so (377 batch names now; about 384 reshuffles the run order).

## Write scope and boundaries

`EscrowCoreGameTests` and the other escrow GameTest classes that share the race, their test helpers
(`VanillaChunkSave`, `ChunkSaveWatcher`, `EscrowGameTestLaunch` or a sibling), new tests, and the record. No production
class, log checker or expectation change, and no relaxed assertion, limit or timeout.

## Commands

As WORKER-RULES says, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W94/`. Other workers (W91B, W92, W93)
also compile and run tests; take the heavy-JVM lock for every GameTest run. Check that C: and D: have more than 10 GiB
free before each heavy start (C: has about 13 GiB). If the tool policy refuses to let you remove your own lock or
world, write the exact path in your record and final answer; Root removes it.

## Not in this task

Any production change, W91B, W92, W93, ADRs, the risk register, status files and the ledger (Root).
