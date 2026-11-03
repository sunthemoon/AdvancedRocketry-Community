# TASK W271: pace the bay hold fixture whose ordinary recovery retried on its deadline

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w271`, branch `codex/v1.8.0-w271`, created by Root from the shared head |
| Subject | `BayDeploymentHoldGameTests.aHeldClaimWithARealBayFollowsSection81Tests6123` (batch `bay_deployment_hook81`) and its class |
| Evidence | W270's local full GameTest run: `D:/GitHub/ARCE-Task-Evidence/root-queue/W270/full-latest.log` (SHA-256 `d0623b907230815df3e9efdb0f90f5d2d240427e5c92132829b2e31b55a6ae0e`), lines 5950 to 5985, and its checker output `full-check.log` |
| Record | `docs/work/v1.8.0-root-queue/records/W271.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W271/` |
| Decision | Root decision R301 (2026-11-03): a test-reliability fix, run while no content task is open |

## What the log shows

All 1,656 GameTests passed, but `scripts/check_gametest_log.py` failed on one WARN:

- 11:43:14.916, batch `bay_deployment_hook81` starts. In its stage 3 (`stations()`), section 8.1 test 2 puts a second,
  unclaimed transfer record whose source entity is live; the ordinary stationary recovery keeps the source.
- 11:43:15.508, `ARCE_TRANSFER_RETIREMENT_RETRY ... site=RECOVERED_SOURCE reason=DEADLINE retry_ticks=200` for that
  record: the recovery's retirement proof did not finish within its 100-tick partial-census deadline, about 0.3
  seconds of wall-clock time after the survivor entity became active.
- 11:43:16.103, the proof succeeded on attempt 2; 11:43:16.109, `ARCE_TRANSFER_RECOVERY ... action=KEEP_SOURCE
  status=RECOVERED` (declared for this batch); the test then passed.

The manifest declares this WARN only for `RocketPinnedCensusGameTests` (rule `w151-pinned-partial-census-deadline`),
which provokes it on purpose. In `bay_deployment_hook81` it is a host-speed effect: `GameTestServer` never sleeps
between ticks, so a deadline counted in ticks expires in a fraction of a second while the proof's save and readback
progress in wall-clock time (see `gametest/GameTestTickPacer`). Hosted runs have not logged it so far. The fixture
does not pace its waiting ticks; `BayHeldProofGameTests` paces every waiting tick of the same kind of fixture with a
`GameTestTickPacer` in its tick handler's `finally`.

## What to do

1. **Confirm the cause.** Find which part of the RECOVERED_SOURCE proof the deadline covers (`RocketOrdinaryRetirement`,
   the partial-census deadline, the proof barrier and its readback). Reproduce the retry deterministically: make that
   asynchronous part slower than 100 unpaced ticks but well under 100 paced ticks (5 seconds), for example a stall
   of about one second in the storage I/O or background executor injected once from the test side, as the
   2026-10-10 pacing analysis did. Show the current fixture logs the DEADLINE retry with the stall. Keep the
   reproduction out of the delivered code, or make it a separate GameTest only if it needs no production change.
2. **Pace the fixture, not production.** Give `BayDeploymentHoldGameTests.Restart` a `GameTestTickPacer` and pace each
   waiting tick, as `BayHeldProofGameTests` does. Keep every assertion, stage, bounded wait (1,150 and 380 ticks) and
   `timeoutTicks` unchanged; if pacing makes a bound too tight in wall-clock terms, stop and report the arithmetic
   instead of raising it. Show that with the same stall the paced fixture no longer logs the retry.
3. **Look for the same pattern.** List other GameTests that drive an ordinary recovery or retirement proof with a
   tick-counted deadline while not pacing their waiting ticks (start with the `Bay*GameTests` and
   `RocketTransferBayGameTests`). Report them with a sentence each; change only those that log, or can be made to
   log, the same undeclared retry, and say why for each.

Do not change `scripts/gametest_expected_log.json`, `scripts/check_gametest_log.py`, production code, the retirement
constants or any assertion. Do not delete or skip a test (AGENTS.md section 5).

## Verification

The stalled reproduction logging the retry before the change and not after it; the batch `bay_deployment_hook81` and
the other changed batches 20 times in a row under load (state how you made the load), each followed by the checker
over that run's log where the checker accepts a filtered run, otherwise by a search for undeclared WARN lines; the
GameTest classes you changed; the whole unit suite; the full GameTest run with `scripts/check_gametest_log.py`
under the heavy-JVM lock; the repository validator.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Production retirement behaviour, its constants and logs, the expected-log manifest and checker, W270's flight
budgets, other intermittent tests in the status file, the status file, QUEUE and the ledger.
