# TASK W213: the ground-survey START spike test exceeded its 500 ms budget twice

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w213` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w213` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W213.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W213/` |
| Test | `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/persistence/GroundSurveyScaleTest.java`, `startOnARootNearTheBoundStaysWithinTheSpikeBudget` (lines 167 to 188; `SPIKE_BUDGET_NANOS` 500 ms, docs/17's single-spike budget; W55's two warm-up STARTs, then the median server-thread cost of seven) |
| Failures | hosted run 40997283292, attempt 1, at `66386d5b8` (W210's branch): "START median cost 570.709567 ms at 16,743,201 bytes"; the XML is in that run's artifact and in `D:/GitHub/ARCE-Task-Evidence/root-queue/W210-review/`. Earlier: 565 ms once locally under full-build load (QUEUE, continuation 09). Attempt 2 of the same run and every other recent run passed |
| Background | the commits that last touched the test and its subject: `22675ee9a` (START timed by its server-thread cost), `101564488` (ground additions proven to fit before encoding the root), `9b7850cc1`; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

**Root decision R187.** The test is outside W210's diff and passed on the rerun, so W210 merged; the test itself is now
a task. It has failed twice by 13 to 14 percent, both times under load. Either START near the 16 MiB bound really costs
close to 500 ms on the server thread (a production cost that the budget should see), or the measured "server-thread
cost" includes time that is not START's own work (GC, JIT, other threads, the measuring itself).

## What to do

1. **Measure what START costs and where,** near the bound, on this machine idle and under a parallel CPU load you
   state (for example a second Gradle test run), with enough repetitions to give the distribution of the median the
   test asserts: encode, size proof, reservation, copy and GC, timed directly. Compare the hosted failure's printed
   `ARCE_W21BF_START` line where the artifact has it.
2. **A real production cost close to the budget:** write it up with the numbers and stop for Root; a remedy is a
   separate task with a design note.
3. **A measurement fault** (time charged to START that is not its work): fix the measurement so that it charges
   exactly START's server-thread work, keeping the 500 ms limit, the warm-ups, the sample count and every assertion,
   with a test that fails first for the fault you found; show the corrected distribution idle and under load.

## Verification

The changed test alone, repeated (state the count and the pass rate, idle and under load); the satellite persistence
package; the whole unit suite once. No GameTest, native run or full build.

## Not in this task

The 500 ms budget, other tests, production changes, ADRs, status files and the ledger.
