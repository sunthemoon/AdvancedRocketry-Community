# TASK W381: two local GameTest failures, diagnosis

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w381`, branch `codex/v1.8.0-w381`, at the shared head |
| Decision | Root decision R489 (QUEUE row W381) |
| Open items | `docs/status/CURRENT_VERSION.md`: "Item-cargo replay mismatch GameTest (2026-11-18)" and "Transfer recovery GameTest (2026-11-18)" |
| Record | `docs/work/v1.8.0-root-queue/records/W381.md` |
| Write scope | The two GameTest classes named below, their support code under `gametest/` and `resourceescrow/`, exact entries in `scripts/gametest_expected_log.json` if a diagnostic adds a log line, their unit tests, the production code on the failing path only if you prove a defect there, and the record. |

## The two failures

Each failed once in a local full GameTest run and passed in the hosted regressions. Both local runs shared the
machine with other workers that were compiling.

1. **`ItemCargoReconciliationGameTests.mismatchedFrozenReplayDigestHoldsWithBothEndpointsUnchanged`**
   - Source: `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/ItemCargoReconciliationGameTests.java`,
     line 50.
   - It failed in W379's run 1, at phase 14 of its second direction: "A mismatched replay lost its operation or changed
     an endpoint". The log does not say which of the three checks failed.
   - Log: `D:/GitHub/ARCE-Task-Evidence/root-queue/W379/gt1/latest.log`; checker output: `.../W379/gt1/checker.log`.
   - The rerun (`.../W379/gt2/`) passed. W379's record on branch `codex/v1.8.0-w379` describes both runs.
2. **`RocketTransferRecoveryGameTests.transferRecoveryReconcilesAllEntityPresenceCases`**
   - Source: `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RocketTransferRecoveryGameTests.java`,
     line 43.
   - It failed in W380's run: "both recovery did not complete: RETRY_LATER", in batch `monitor_governed`.
   - The checker also reported:
     - the duplicate-destination recovery WARN in an undeclared batch;
     - the expected remove-duplicate-destination and rebuild-destination WARNs missing from `flight_recovery`.
   - Log: `D:/GitHub/ARCE-Task-Evidence/root-queue/W380/gametest-latest.log`; checker output: `.../W380/gametest-checker.log`.

## What to do

1. **Read each log around the failure.**
   - Establish what the fixture and the server did, tick by tick. Use the batch start, the phases, the log lines of
     the operation or transfer, the autosave cadence (6,000 ticks; W375 found an overlap cause before), and other
     batches that ran at the same time.
   - For failure 2, explain why its WARN lines appeared in another batch: which test's transfer they belong to, and
     whether two tests shared state.
2. **Try to reproduce.** Run the affected batches repeatedly under the heavy-JVM lock, for example through the batch
   filter the earlier diagnoses used.
   - Bound the repetitions, for example 20 per batch.
   - Run some of them with CPU load from a parallel compile, because both failures happened under load.
3. **Then one of three outcomes.**
   - **The cause is a wrong test assumption.** Fix it in the test, with a unit or GameTest that pins the fix.
   - **The cause is a production defect.** Fix it on the failing path, with a test that fails without the fix.
     Production save, network and admission behaviour must not change otherwise.
   - **The cause is not found.** Add failure-only diagnostics, as W378 did for the concurrent-flight fixture. A failure
     message should name the exact check and the observed state, with bounded reads only. Keep both open items open.
4. **Never raise a bound or a timeout, add a retry, or skip an assertion** to make a failure pass.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies and GameTest worlds after each run.
- Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W381/`.
- Run the touched classes' unit tests and the GameTest log checker on every full run.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives:
  - the tick-level reading of each failure;
  - the reproduction attempts and their results;
  - the cause or the narrowed finding;
  - the change;
  - the commands;
  - the evidence paths.
- The last line of the record, and the last line of your answer, is the path of `records/W381.md`.
