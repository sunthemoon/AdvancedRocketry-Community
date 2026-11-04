# TASK W283: four GameTests that fail intermittently under load

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w283`, branch `codex/v1.8.0-w283`, created by Root from the shared head |
| Decision | Root decision R315 (QUEUE row W283) |
| Record | `docs/work/v1.8.0-root-queue/records/W283.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W283/` |

## The failures

All four ran at code that passed elsewhere, and none was touched by the change under test at the time.

| Test | Message | Where it failed | Evidence |
|---|---|---|---|
| `rocket/server/RocketReconnectSchedulingGameTests.reconnectWaitsForItsQueuedSpawnWithoutRecoveringTheSource` | `Own spawn did not queue` (line 73) | Hosted, shard 2, both attempts of run 41776065592 (W274's branch, documentation and Python only) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W274/ci-41776065592-shard2-attempt1.log` and `-attempt2.log` |
| `resourceescrow/EscrowGovernedLiveReturnGameTests.dismountBetweenCancelledCheckpointAndReplacementRefusesWrite` | `Definite F7 callback lost the first retry backoff` (`EscrowTerminalRetryCut` line 42) | Hosted, shard 2, second attempt of the same run | `-attempt2.log` above |
| `ClassicScaleGameTests.sixteenOfEveryKindStayIdleThenHalfResumeAfterRealUnload` | `Scale stage did not finish: 7` (lathes and cutting machines still RUNNING) and `Stage 5 did not finish` (lathes UNLOADED) | Local full runs of W281 and W280 under 24 and 26 `server-overloaded` warnings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W281/regression/` and `W280/after/` |
| `ArcCuttingGameTests.sawmillOffRefusesNewWoodJobsAndPausesAndResumesAnActiveJob` | `Batch did not complete exactly once` | Local full run of W280 | `D:/GitHub/ARCE-Task-Evidence/root-queue/W280/after/` |

Earlier fixes of the same kind are W270 to W273 (records in this folder): GameTestServer never sleeps, so tick-counted
deadlines measure host speed, and `GameTestTickPacer` holds waiting ticks to 50 ms; fixtures that stop driving an
asynchronous submission miss a designed rejection.

## What to do

For each test, in order of the table:

1. Reproduce it: run its batch repeatedly, with and without load (state how you made the load; W272's and W273's
   records show working methods), and, where you can, force the suspected interleaving deterministically.
2. Find the cause: a fixture that depends on host speed or on an ordering the product does not promise, or a product
   race. If it is a product defect (for example, a lost retry callback or a machine that never resumes after a real
   unload), stop for that test and report it with a failing test instead of changing the fixture.
3. Fix fixture causes at the cause, keeping every assertion and its strength: no raised timeouts or wait bounds, no
   skipped tests, no batch moves, no expected-log manifest changes (AGENTS.md section 5). Pacing with
   `GameTestTickPacer` is acceptable where the fixture's deadline is meant to measure server time.

## Verification

For each changed fixture: the reproduction failing before and passing after, then its batch 20 times in a row under
load with a search for undeclared WARN or ERROR lines. Then the whole unit suite, the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock, and `python -B scripts/validate_repository.py
--require-approved-identity`. Record any test you could not reproduce, with the number of loaded runs you tried.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Other intermittent entries in the status file, the expected-log manifest and checker, production behaviour unless a
failing test shows a defect (then report it), the ledger, the status file and QUEUE.
