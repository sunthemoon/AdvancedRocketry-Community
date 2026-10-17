# TASK W11b2, continuation 54: the benchmark's world-first DEFER, and governed GameTests that tolerate an F1 retry

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-45](TASK-W11B2-CONTINUE-45.md), [TASK-W11B2-CONTINUE-51](TASK-W11B2-CONTINUE-51.md) and
[TASK-W11B2-CONTINUE-53](TASK-W11B2-CONTINUE-53.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c54` (new, at the integrated W11b2 head `c420e521`, which contains continuation 53, plus this task) |
| Branch | `codex/v1.8.0-w11b2-c54` (local commits only; Root pushes and merges) |
| Input | Continuation 53's record `records/W11B2-C53.md` and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C53/` (`native-01/`, `checks/gametest-01-latest.log`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); Root decisions R1 to R20; R16 item 3(c) (`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C43/ROOT-DECISION-R16.md`): "The world-first DEFER in `MoonLandingCompletion` and `EscrowGovernedArrival` keeps its semantics. Include it in the benchmark and change it only if a budget fails." |
| Record | `records/W11B2-C54.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C54/` |

No other implementation worker is running. A Claude design session (W90R) may be running in another worktree; it
runs no JVM.

## Where things stand

Continuation 53's native run on `453094c3` completed the unchanged eight-flight workload and its restart within the
docs/17 numbers (MSPT mean 4.14 ms, P95 12.42 ms, P99 36.81 ms, largest non-save tick 164 ms, RSS 1.40 GiB), but the
runner reported `INCOMPLETE_COVERAGE` for exactly one item, `checkpoint:WORLD_FIRST_DEFER`: the first Moon landing won
its election at once, so `MoonLandingCompletion.complete` never reached step 4's DEFER. Its full GameTest failed one
test, `EscrowGovernedSourceFlightGameTests.landedDismountCompletesCheckedHandoffAndCleanup`: its F1 proof reported
`SUPERSEDING_SAVE` (backoff 100) and the scenario, which calls `EscrowGovernedPreparation.begin` once and asserts
"Source preparation was discarded" never happens, failed. A superseding save is a legitimate F1 retry under design 2
(F1 proof failures use the 100, 400 and 1,200-tick backoff). The test passed in continuation 52's run and in hosted
runs 39029453767 and 39039330403.

## What to do

1. **Exercise the world-first DEFER in the benchmark (R16 item 3(c)).** Make the first Moon landing of the eight
   reach step 4's DEFER for a bounded number of ticks and then complete through the unchanged product path, using
   the existing gated release-test hooks (for example the first-event cut seams and their latch, or a disposition
   source held at DEFER while the first-event write is pending), not a product change and not a fabricated
   checkpoint. The landing must stay deferred long enough for several dispatcher visits, then land as a checked idle
   survivor with the exact fuel. Report how many deferred visits occurred and what they cost (per-visit time if the
   recorder can attribute it; otherwise say so). Do not remove or weaken the runner's coverage requirement.
2. **Make the governed GameTest scenarios tolerate a legitimate F1 retry.** Find what saved the chunk during the
   failed test's F1 (the log around continuation 53's `gametest-01-latest.log` line 8543; for example the periodic
   autosave or another test). Then change the shared `Scenario` launch in `EscrowGovernedSourceFlightGameTests` (and
   any other governed GameTest that launches once and asserts no discard) so that a discard whose logged F1 reason is
   exactly `SUPERSEDING_SAVE` is re-requested once the retry schedule allows (`retryReady`), at most three attempts,
   while any other discard reason still fails the test and every other assertion stays as it is. Add a unit test or
   GameTest that forces one superseding save at F1 and shows the scenario completes after the retry. Record which
   tests you changed and why this is not a relaxed assertion.
3. **Run the whole unit suite and one full GameTest** on your committed source, with the unchanged log checker.
4. **Run the measured benchmark** exactly as TASK-W11B2-CONTINUE-51 items 1 to 4 say, on your committed source, on the
   quiet machine, with the restart, and report every number against docs/17's unchanged budgets. The runner must
   report full coverage. If it does not, report the first gap with evidence.

## Write scope and boundaries

The benchmark probe, runner, recorder and load helpers and their tests; the governed GameTest classes' launch
scaffolding; the first-event release-test seams only to expose an existing hold to the benchmark case; new tests; and
the record. No production class outside the release-test seams, no change to an acknowledgement order, a proof, a
lease, a schema, the replay exclusion, the log checker or its expectations, and no relaxed budget, limit, timeout or
assertion. Do not serialize the eight flights or shrink the workload. If you add a GameTest batch, say so (376 batch
names now; about 384 reshuffles the run order).

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C54/` and disposable server
sessions under `D:/GitHub/ARCE-Task-Evidence/Temp/W11b2-C54-<n>`. C: has about 13 GiB free: check that C: and D: have
more than 10 GiB free before each heavy start, and stop and report if C: falls below that. Never remove a lock you do
not own. If the tool policy refuses to let you remove your own lock, world or server copy, write the exact path in
your record and final answer and continue; Root removes it.

## Not in this task

Production admission, W11b3, W90 (tickets and readiness), ADRs, the risk register, the R-035 text, status files and
the ledger (Root).
