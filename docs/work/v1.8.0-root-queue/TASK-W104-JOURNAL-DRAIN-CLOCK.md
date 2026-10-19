# TASK W104: journal tests that drain failed writes are slow and load-sensitive on Windows

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is a parallel
bug; it touches a persistence writer, so AGENTS section 13's rule for persistence slices applies (a one-page design
note before the change, and a read-only review before the merge).

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w104` (new; from the shared branch at the commit Root names in the launch prompt) |
| Branch | `codex/v1.8.0-w104` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W104.md` (new; the design note goes at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W104/` |

## The problem

`RocketJournalScheduleMatrixTest` has a 30-second class timeout. On this Windows machine, while other workers ran heavy
JVMs, W102 measured `generatedCommitsFailuresAndStopsPreserveCapacityAndExactlyOnceCompletion` at 30.0 s (a timeout,
twice, on unchanged journal code) and `recordTouchedAbsenceAndUntouchedAbsenceFollowTheAcknowledgementTable` at 25.8 s
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W102/unit-matrix-recheck.xml`). Hosted Linux runs pass.

Root's lead, not yet measured: `saveChecked` is `requestWrite` plus `RocketJournalWriter.drain`, which advances the
owner clock one tick per `Thread.sleep(1)`. A failed write sets `retryAt = tick + 200`, and a drain allows up to
`DRAIN_UNKNOWN_ATTEMPTS` (3) unknown outcomes. A 200-tick backoff was meant to be about 0.2 s of drain time, but on
Windows `Thread.sleep(1)` often lasts about 15.6 ms (the default timer period), which makes the same backoff about 3 s,
and scheduling under load adds more. `EscrowLedgerWriter` (line 269) has the same loop; its tests use 60-second
timeouts.

## What to do

1. **Measure first.** Time `Thread.sleep(1)` in this JVM on this machine, idle and under the load W102 used
   (`D:/GitHub/ARCE-Task-Evidence/root-queue/W102/cpu-load.py`, two processes pinned to two CPUs), and time each
   `saveChecked` in the two slow tests (a temporary probe that you remove). Confirm or reject the lead with numbers.
2. **If the lead holds, fix the clock, not the timeout.** Make the drain's backoff take the wall-clock time it was
   designed for on every platform, for example by waiting until a `System.nanoTime()` deadline per tick, or by moving
   the drain to the next event (the I/O completion or `retryAt`) while still counting the same owner ticks. Keep every
   observable rule: the 200-tick backoff in owner ticks, the three unknown attempts, the order of completions and
   callbacks, the owner-thread checks, interruption handling, `close` and `abort`, and the "continue the actual owner
   clock" behaviour that `shutdownDrainAtAHighServerTickDoesNotRestartTheWriterClock` covers. Apply the same change to
   `EscrowLedgerWriter` only if it has the same loop and the same cost; otherwise say why not.
3. Write the design note first (one page at the top of the record: the measured cause, the change, why each rule
   above is unchanged, and what a Windows dedicated server gains at shutdown with a failing disk). Then implement,
   with a unit test that fails on the old loop and passes on the new one without depending on the host's timer
   period (for example an injected clock or sleeper).
4. Run the journal and escrow writer test classes repeatedly under W102's load (at least 10 times each), the whole
   unit suite once, and one full GameTest with the unchanged log checker, all on committed source. Report the slow
   tests' times before and after.

If the cause is something else, report it with the numbers and stop before changing production code.

## Write scope

`RocketJournalWriter`, `EscrowLedgerWriter` (only as step 2 says), their tests, `RocketJournalScheduleMatrixTest`
(only to remove a temporary probe; no assertion, loop count or timeout change), new tests, and the record. No relaxed
assertion, longer timeout, fewer generated sessions or steps, or skipped case (AGENTS section 5).

## Commands and not in this task

As WORKER-RULES says. Take the heavy-JVM lock for the full GameTest and release it between runs; check that C: and D:
have more than 10 GiB free first. Remove your own lock, temporary probe and `build/gametest/world` afterwards; if the
tool refuses, write the paths in the record and final answer at once.

Not in this task: other slow tests, the journal's scheduling rules, ADRs, the risk register, status files and the
ledger (Root).
