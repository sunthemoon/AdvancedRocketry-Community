# TASK W102: the etcher chunk-unload GameTest fails intermittently on hosted runners

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is a parallel
bug.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w102` (new; from the shared branch at `fac40f53`) |
| Branch | `codex/v1.8.0-w102` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W102.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W102/` |

## The problem

`C16cRestartGameTests.etcherJobCrossesARealChunkUnloadAndReloadWithExactAccounting` (batch `w64_etcher_unload`, in
`machine/classic/runtime/C16cRestartGameTests.java`) puts a fixture in an isolated chunk a million blocks away, runs a
job until it pauses for energy, drops the fixture's chunk tickets and then waits up to 600 paced ticks for
`ChunkEvent.Unload`, `!level.hasChunkAt(origin)` and the controller's removal. On hosted runners it timed out ("Timed
out waiting for the fixture chunk to unload") on runs 39099242168 at `fcd549c9`, 39102812313 at `93eb3ad2` and
39113531249 at `38b437a7`, and passed on three other hosted runs, in W83b's local full run and in Root's local run of
the first 13 batches (29 of 29). The batch order and the tests before it were identical in passing and failing runs.

## What to do

1. **Find why the chunk sometimes does not unload in time.** Read the vanilla unload path for 1.20.1
   (`ServerChunkCache`, `ChunkMap.processUnloads` and its `hasMoreTime` condition, the unload queue, the save of a
   freshly generated far chunk, and `GameTestTickPacer`'s effect on spare tick time) and use the hosted logs (Root can
   give you the three failing logs; the job logs are downloadable with `scripts`-style GitHub API calls through
   `git credential fill`, never printing the token) and local runs, including runs with a slowed host if that helps.
2. **Make the test deterministic without a longer wait.** AGENTS section 5 forbids raising its timeout or its 600-tick
   wait to make it pass; change what the test waits on or how it drives the unload so that the unload happens within
   the existing bound on any host, keeping every assertion about accounting, removal and reload. If the only fix is a
   longer wait, stop and report; that needs an ADR.
3. Run the class's batch alone repeatedly under load (for example 20 runs while other workers compile), the whole unit
   suite if Java outside the test changes, and one full GameTest with the unchanged log checker, all on committed
   source.

## Write scope

`C16cRestartGameTests`, `C16cMachineFixture`, the shared GameTest pacing or tick helpers if the fix belongs there (keep
other tests' behaviour unchanged and say which tests use them), new tests, and the record. No production change; no
relaxed assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W97B, W98, W101) also run tests; take the heavy-JVM lock for every GameTest run and
release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free before each heavy
start (C: has about 12 GiB). Remove your own lock and `build/gametest/world` afterwards; if the tool refuses, write the
paths in the record and final answer at once.

Not in this task: other intermittent tests, ADRs, the risk register, status files and the ledger (Root).
