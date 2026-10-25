# TASK W161: three intermittent GameTest failures

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) and the "Intermittent relogin GameTest" note in
[CURRENT_VERSION](../../status/CURRENT_VERSION.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w161` |
| Branch | `codex/v1.8.0-w161` from the shared head `410f3ef8a` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W161.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W161/`; the two hosted job logs are in its `input/` folder |

Each failure below passed on a rerun of the same commit. Find the cause in each case; do not make it rarer.

1. **The relogin handoff.** `EscrowGovernedSourceFlightGameTests.logoutDuringAscentAndLoginAfterLandingCompletesHandoffAndCleanup`
   timed out at 600 ticks on the first attempt of hosted run 40152525151 at `32c8ded4`
   (`input/hosted-40152525151-job-121046857704.log`, line 16912): the passenger logged in after landing and the
   handoff never completed. Find why from the log and the code, reproduce the condition on purpose if you can, and
   fix the cause, in the test or in production.
2. **Terminal-cut fixtures clean up on failure.** In the same log, `ascentDismountCompletesCheckedHandoffAndCleanup`
   (line 18730) and `blockedLandingReturnsToF6WithPassenger` (line 18746) then failed with "Terminal cut requires
   exclusive owner-thread setup", because the first fixture releases its terminal-cut setup only on success. Make
   every terminal-cut fixture release that setup when its test fails or times out, so one failure cannot fail the
   tests after it. Show this with a test.
3. **The release-boarding WARN.** On the first attempt of hosted run 40199348299 at `c54de4b0`
   (`input/hosted-40199348299-job-121186992556.log`, line 13254), all 1,399 GameTests passed, but the log check failed
   on an undeclared `ARCE_REJOIN_RELEASE_HELD ... state=CAPTURE_FAILED` in batch `rocket_release_boarding`.
   `RocketReleaseBoardingGameTests.sameSeatBoardsOnlyAfterS2AndSurvivesReloadWithoutRevivingOldCopies` makes the rider
   leave during B1 (`awaitS2`, `leaveUnderFence`), which can make `RocketReleaseCoordinator.capture()` fail a capture
   that is in progress. Decide from the W80 design ([records/W80-DESIGN.md](records/W80-DESIGN.md)) whether that
   departure should fail the capture.
   - If it should, make the test reach one outcome every time; declare a WARN in the manifest only if it then always
     occurs, with its exact count.
   - If it should not, fix production with a regression that fails first.

## Rules

- No longer timeouts, no loosened assertions, no deleted or skipped tests, and no manifest entry for a line that only
  sometimes appears (AGENTS §5).
- If production code changes, list each change in the record; Root then arranges an independent review before the
  merge. A change to saved data, the journal or the sidecar is out of scope: stop and report instead.
- Do not touch the areas W152H and W160F are changing: `rocket/sealed`, the sealed scheduling and admission classes,
  and the rejoin readback and native classes. If a fix needs them, stop and report.

## Verification

- Each part's new or changed tests, failing first where you reproduce the cause.
- The batches of all four named tests, run three times each.
- The whole unit suite and one complete GameTest that passes with the unchanged log checker.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; the W152H review
shares it. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free.

## Not in this task

- Any other flaky test you notice: list it in the record instead.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
