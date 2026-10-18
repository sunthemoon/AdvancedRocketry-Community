# TASK W91R: the W91 review's follow-ups (Root decision R30)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W91-review/REVIEW.md` (SHA-256
`1e973150506be68c9318a7087d91e2ea5db3ac78e11ff8f9b7a2f2cda31c7079`) first, then `records/W91.md` and
`records/W86-DESIGN.md` (sections 2.1, 3, 6 and Appendix B) as the findings cite them. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91r` (new; from the shared branch at `264c4e30`, which has W91 merged) |
| Branch | `codex/v1.8.0-w91r` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W91R.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91R/` |

## Root decision R30 (recorded here)

Root merged W91 (`264c4e30`) on the review's MERGE-WITH-FOLLOWUP verdict and decides the findings:

1. **W91-R1: option (a).** W86 revision 2 specifies that W85's M2 (the landed removal's continuation) opts in to run
   while another record's write is failing; the code does not. Queue only that continuation with `duringFailure =
   true` (a `Port` method or flag, with `RocketLandedDisassemblyPort` calling the six-argument `afterAcknowledged`), and
   add the review's A0 case: the removal is acknowledged, a disjoint generation then ends NOT_COMMITTED, and M2
   restores within the confirmation lifetime. Nothing else opts in.
2. **W91-R2: drop the precheck** at `RocketTransferService.prepareLaunch` (the added `journal.actionable(L)` refusal,
   about `:155-158` at `cbf235a9`); the interlock and `reserve` cover every other state. Add a case for a legacy launch
   before the session's baseline commit (admitted; its countdown runs only after that commit) and keep a case that a
   busy L is still refused before any change.
3. **W91-R3:** restore the 128-slot world root on every path in `EscrowPendingLandingGameTests` (an `@AfterBatch` hook
   for its batch, or another cleanup that runs on failure); keep every assertion.
4. **W91-R4** (predates W91): make `PlanetaryFlightAdmissionTest` bootstrap Minecraft before it reads registries, in
   its own commit, so that a filtered run that starts with it does not break the classes after it.
5. **W91-R5:** switch `EscrowFlightBenchmarkDefer` to `clean()`, then rerun the native governed benchmark
   (`scripts/run_v180_governed_benchmark.py`, as `records/W11B2-C51.md` ran it) on your committed source and compare
   with continuation 54's figures in ADR-065's W11b2 part 2 note (mean 4.17 ms, P95 12.04 ms, P99 39.43 ms, the largest
   non-save tick 156 ms, the DEFER held 20 ticks over 77 visits).
6. **Root's own finding: two `RocketJournalScheduleMatrixTest` cases sit close to their 30-second timeouts.** On a quiet
   machine they take about 10 s and 20 s (W91I's evidence); under load they time out, and a slow hosted runner can make
   20 s exceed 30 s. Reduce their cost without removing a generated case, weakening an assertion or raising the timeout
   (for example a test committer that keeps the checked-write behaviour the tests check but skips what they do not, or
   less repeated setup). Report the before and after times. If that cannot be done within AGENTS section 5, stop and
   report.

W91-R6 and W91-R7 are informational; the whole-root consumers' wait under traffic is W91N2's measurement.

## What to do

Apply R30 items 1 to 6, each in its own commit. Then, on committed source and serially: each changed class alone, the
whole unit suite, `python -B scripts/validate_repository.py --require-approved-identity`, and one full GameTest with the
unchanged log checker. Say whether you add a GameTest batch (the hosted log shows about 439 batch names; a run-order
reshuffle comes past 768).

## Write scope

`rocket/server/RocketLandedDisassemblies`, `RocketLandedDisassemblyPort`, `RocketTransferService` (item 2 only),
`resourceescrow/EscrowFlightBenchmarkDefer`, `EscrowPendingLandingGameTests` and its support class,
`PlanetaryFlightAdmissionTest`, `RocketJournalScheduleMatrixTest` and its fixtures, new tests, and the record. No saved
format change, no other change to an acknowledgement order, a proof, a lease or the replay exclusion, and no relaxed
assertion, limit or timeout. Stop and report if one seems needed.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W90b, W91N2) also run tests; take the heavy-JVM lock for every GameTest and
native run and release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free before
each heavy start. Delete the benchmark's server copy afterwards. When your heavy runs end, remove your own lock and your
`build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses,
write the paths in the record and final answer at once.

Not in this task: W91N and W91N2, the F3 and N2 kill/restart cuts, ADRs, the risk register, status files and the ledger
(Root).
