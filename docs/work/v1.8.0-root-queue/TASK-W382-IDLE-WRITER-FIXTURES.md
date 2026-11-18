# TASK W382: the idle-writer wait in the remaining direct ledger replacements

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w382`, branch `codex/v1.8.0-w382`, at W381's integrated head `6dd155eb5`, which has W381's fixes |
| Decision | Root decision R492 (QUEUE row W382) |
| Prior work | W381's record `docs/work/v1.8.0-root-queue/records/W381.md` on this branch, sections "Failure 1" and "Not done, and for Root" |
| Record | `docs/work/v1.8.0-root-queue/records/W382.md` |
| Write scope | `resourceescrow/FluidCargoReconciliationGameTests.java`, the three probes named below, their unit tests or GameTests, exact entries in `scripts/gametest_expected_log.json` if a new GameTest logs, and the record. No production code. |

## The race

W381 found that a fixture which replaces the saved ledger through `ResourceEscrowSavedData.acknowledged` while the
coordinator has a successor generation in flight loses that replacement when the successor is acknowledged.
`ItemCargoReconciliationGameTests` now waits for `coordinator.writerIdle()` before each replacement.

## What to do

1. **`FluidCargoReconciliationGameTests`, around line 140 (its R39 branch).** Apply the same idle-writer wait before
   the replacement, as W381 did for the item-cargo R39 branch.
2. **`EscrowCargoRecoveryProbe`, `EscrowLoaderFloorProbe` and `EscrowSettlementCompletionProbe`.** Each calls
   `ResourceEscrowSavedData.acknowledged` directly. For each call, decide whether a successor generation can be in
   flight at that point:
   - Write down the reasoning, with the code path and line numbers.
   - Where the race is possible, add the idle-writer wait, or the equivalent for that probe's thread and lifecycle.
   - Where it is not possible, say why, and leave the call unchanged.
3. **Pin each change.** Where a change is made, pin it with a test that fails without it. Use the slow-write hold W381
   used: the `K1` checkpoint through `EscrowGameTestTerminalCut`. If a pin needs a native run rather than a GameTest,
   say so and do not run it.
4. **Run.**
   - Run the affected batches about ten times, through the repeat harness W381 kept in
     `D:/GitHub/ARCE-Task-Evidence/root-queue/W381/`.
   - Run one full GameTest run under the heavy-JVM lock, with the log checker.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete GameTest worlds after each run. Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W382/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The last line of the record, and the last line of your answer, is the path of `records/W382.md`.
