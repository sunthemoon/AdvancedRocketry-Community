# TASK W111: make the escrow proof GameTests deterministic on slow hosted runners

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W94.md](records/W94.md) (the proof
retry, with its W94B section) and [TASK-W94B](TASK-W94B-PROOF-RETRY-TIMEOUTS.md) (the per-phase budgets) first. You
are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w111` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w111` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W111.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W111/`; the three hosted job logs are in its `hosted/` folder |

## The problem

Three hosted regression runs failed an escrow proof GameTest (batch `escrow_core`); local runs pass:

| Run, commit | Failed | Proof retry lines just before |
|---|---|---|
| job 117631066897, `843ea4dc` | `supersededEntityProofRetriesTheSameFrontierAfterBackoff` and `ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier`, both "Didn't succeed or fail within 200 ticks" | an entity `inject_save` at fixture tick 122, then `superseded` at 143 |
| job 118106130673, `6d120bc8` | `supersededTerrainProofRetriesTheSameFrontierAfterBackoff`: "Retry fixture proof exceeded its explicit force/IO completion budget" | a terrain `capture` and `inject_save` at fixture tick 127, no result 40 paced ticks later |
| job 118201165777, `d2f0d00c` | `ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier`, "Didn't succeed or fail within 200 ticks" | a terrain `inject_save` at fixture tick 162 and `superseded` at 183; the retry's capture came at 283, after the test had failed |
| job 118223033108, `bd1c78b9` | the same test, the same message | the same pattern: terrain `inject_save` at 163, `superseded` at 184, failure at 200, the retry's capture at 284 |

Two of the last three hosted regression runs failed this way, so this task comes first in the queue.

Root's lead, not a finding: the forced-supersession retry tests (W94B) run in the same batch as the core proof tests.
Their injected saves, or the force batches they cause, may supersede or delay the concurrent core tests' proofs, and a
late first attempt plus the 100-tick retry backoff cannot fit in a 200-tick test. Confirm or refute this from the
logs and the code (which proof each `ARCE_GAMETEST_PROOF_RETRY` line belongs to, what supersedes it, and what the
barrier waited on: the IO worker queue, the force or the readback).

## What to do

1. Find the cause, with evidence: name the test or mechanism that supersedes or delays each failed proof.
2. Make the tests deterministic **without** raising a timeout, a tick budget or a retry limit and without weakening an
   assertion (AGENTS.md section 5). Acceptable directions include isolating tests that inject saves into their own
   batch, so that no concurrent test's proof can be superseded by them; making a test wait for its own fixture to be
   ready before its timed proof phase, as W94B did with its per-phase budgets; or removing a cross-test shared
   resource. A change that only makes the failure rarer is not enough: explain why the race can no longer occur.
3. Reproduce the failure locally first if you can (for example the `escrow_core` batch under load, with the batch
   filter `-Dadvancedrocketrycommunity.gameTestBatches=escrow_core`), then show it gone: at least 20 repetitions of
   the affected batches under the same load, and one full GameTest with the unchanged log checker, all on committed
   source. Say whether you add a batch name (about 452 now; the run order reshuffles past 768).

## Write scope and not in this task

The escrow GameTest classes and their fixtures (`EscrowCoreGameTests`, `EscrowProofRetryGameTests`,
`EscrowGameTestProof` and their helpers), the log expectation file if a moved batch changes an expectation's batch
scope, new tests, and the record. No production change; if the cause is in production code, stop and report it as a
finding. Take the heavy-JVM lock for every GameTest run and release it between repetitions, since other workers wait;
check that C: and D: have more than 10 GiB free first; run long heavy jobs as detached processes, not as tool
background jobs. At the end, delete your export trees and `build/gametest/world` and make sure the lock is released.
Not in this task: other flaky tests, ADRs, the risk register, status files and the ledger (Root).
