# TASK W94B: W94's forced-supersession GameTests time out on the hosted runner

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W94](TASK-W94-ESCROW-CORE-SUPERSEDING-FLAKE.md)
and [records/W94.md](records/W94.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w94b-proof-retry` (from the shared branch, which has W94 merged at `ec7214c5`) |
| Branch | `codex/v1.8.0-w94b-proof-retry` (local commits only; Root merges) |
| Input | Hosted run 39060903571 at `8c3f5717` failed W94's two new GameTests in `EscrowProofRetryGameTests`, both "Didn't succeed or fail within 200 ticks". Log: `D:/GitHub/ARCE-Task-Evidence/root-queue/W94/hosted-39060903571-regression.log` (SHA-256 `4b00da2500831c5090e46aab03a39640ad6f343547cf8f8725aea75088dff309`), lines 14116 to 14149: the terrain case was superseded at fixture tick 21 (attempt 1) and again at fixture tick 131 (attempt 2), and the entity case's forced supersession came only at fixture tick 127, so neither could finish within 200 ticks |
| Record | `records/W94.md` (add a W94B section) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W94/` |

## The problem

`EscrowGameTestProof` allows three attempts with the product backoff (`EscrowReason.backoff`: 100, then 400 ticks), but
the tests that use it keep 200-tick timeouts. A second natural supersession after the forced one therefore always times
out, and a late forced supersession leaves no room for the backoff. The tests passed 30 of 30 locally and failed on
the hosted runner, where the 13-test `escrow_core` batch runs more slowly.

## What to do

1. **Find why the terrain case was superseded a second time** (vanilla's eager save after the W36 precondition's
   10-second cooldown, another `escrow_core` test, or an entity save), and why the entity case's injected save came so
   late (fixture tick 127).
2. **Fix the two new tests** so that they are deterministic: for example, take the forced superseding save at a fixed,
   early point, and keep vanilla's save cooldown covering every retry window (a fresh vanilla save right before each
   attempt), so that the only supersession is the injected one. Their timeout may be set from the retry arithmetic of
   the attempts they actually allow, stated in the test, because they are new tests; do not make it larger than that
   arithmetic needs.
3. **The existing tests W94 changed** (`EscrowCoreGameTests`' two barrier tests, the readiness and the empty-slot
   settlement tests) keep their existing timeouts. Show for each that one retry after the product's first backoff fits
   within it with the W36 precondition; if a second natural supersession can still exhaust it, say so plainly in the
   record as a residual, and do not raise the timeout.
4. **Prove it**: the `escrow_core` batch at least 30 times with the machine loaded (run it while a full unit suite runs
   in another process, or with `-XX:ActiveProcessorCount=2`), then the whole unit suite and one full GameTest with the
   unchanged log checker, under the heavy-JVM lock.

## Write scope and boundaries

`EscrowProofRetryGameTests`, `EscrowGameTestProof` and its unit test, the four existing tests' setup only (not their
timeouts or assertions), and the record. No production class, log checker or expectation change.

## Commands

As WORKER-RULES says. Other workers (W91D, W92, W95) also run tests; take the heavy-JVM lock for every GameTest run and
release it between runs when another worker waits. When your heavy runs end, remove your own lock with a plain
`rm -rf` of exactly `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` after checking that its `owner.txt` names you; if the
tool refuses, write the path in your record and final answer at once.

## Not in this task

Production changes, W91D, W92, W95, ADRs, the risk register, status files and the ledger (Root).
