# TASK W76: two intermittent GameTests from hosted run 38730366442

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w76-hosted-flakes` (from the shared branch) |
| Branch | `codex/v1.8.0-w76-hosted-flakes` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W76.md` (new) |
| Evidence | Hosted run 38730366442, first attempt, at `cf289a48`, a documentation-only commit after the W72 merge: the job log is `D:/GitHub/ARCE-Task-Evidence/root-queue/ci/38730366442/job.log` (lines 9213 and 10241). The six-host natural-beds test also failed in that attempt; it belongs to W74, not to this task. |

Two GameTests failed once on a hosted runner and did not fail on the earlier runs of the same code:

1. `FirstMoonLandingGameTests.theFirstMoonLandingElectsTheAboardPassengerAndLandsOnlyAfterTheConfirmation`
   ("The fixture origin never became entity ticking"). The fixture adds a FORCED region ticket on the
   Moon and then polls `isPositionEntityTicking` for at most 100 ticks
   (`rocket/server/FirstMoonLandingGameTests.java`, `Landing.setup`).
2. `AtmosphereSpawnAdmissionGameTests.spawnEggAndCommandAreExplicitExceptionsNotNaturalAdmission`
   ("The spawn egg did not place exactly one zombie in vacuum: CONSUME 0"). The egg was used, but
   `getEntitiesOfClass` found no zombie next to the clicked block
   (`gametest/AtmosphereSpawnAdmissionGameTests.java` line 154).

## What to do

1. **Find why each can fail.** Compare with the 2026-10-10 root cause: GameTest ticks run unthrottled
   against asynchronous chunk and entity loading. `GameTestTickPacer` is the accepted fix; W64 and W73
   (the landing-pad fixture's chunk retention) show the pattern. For the spawn egg, check whether an
   entity added to a section that is not yet accessible is invisible to `getEntitiesOfClass`.
2. **Reproduce under load if you can:** run each batch alone several times while another JVM keeps the
   CPU busy. W73's `D:/GitHub/ARCE-Task-Evidence/root-queue/W73/` holds a load helper and an isolated
   launcher you may reuse.
3. **Fix the cause in the test fixture.** Do not raise a timeout or a wait count, do not weaken an
   assertion, and do not split a test to hide its cost (AGENTS.md section 5). Production behaviour stays
   unchanged; if the only honest fix is in production code, stop and report.
4. Say what you tried and what each run showed. Give each test its own commit.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- The natural-beds and sleep-room tests (W74), production behaviour, workflows, status files, the ledger
  and ADRs.
