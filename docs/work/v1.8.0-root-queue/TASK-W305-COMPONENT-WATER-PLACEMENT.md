# TASK W305: W299's component water regression depends on where it lands

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w305`, branch `codex/v1.8.0-w305`, created by Root from the shared head |
| Decision | Root decision R350 (QUEUE row W305) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W300/ci/`: `job-127275787728.log` (attempt 1) and `a3-*.log` (attempt 3) of run 42034347740, W300's integration commit `c6293734d`, shard 1 |
| Earlier work | [W299](records/W299.md) (`AdapterDropFixture.withFlowingStartControlWater`), [W302](records/W302.md) (an enclosure that keeps ambient fluids out of a sealed fixture), [W301F](records/W301.md) (fixture water walled in and restored) |
| Record | `docs/work/v1.8.0-root-queue/records/W305.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W305/` |

## The failure

W300 adds GameTest batches, which move later tests. On W300's integration commit, shard 1 failed twice, the same way,
on `RocketComponentGameTests.flowingWaterCannotDropComponentStartButton` (batch `w299_adapter_start_button`): after
the pending disassembly was rejected and resubmitted, every admission reply was
`validation.advancedrocketrycommunity.rocket.target_occupied` (ticks 224 to 231, then 244 onwards) or `null` (232 to
243), and the test timed out with `Unexpected disassembly admission result: null` (`AdapterAsyncFixture` line 73
accepts only `region_busy` as a refusal). The registration water test in the same batch passed. The cause is not
known: something occupies the component's restore target at this placement, perhaps ambient terrain fluid or the
fixture's own water.

## What to do

1. **Reproduce at the failing placement.** On a throwaway commit that is never merged, put W300's branch
   (`codex/v1.8.0-w300` at `261573290`) on the shared head, and run the shard-1 batch selection from the evidence log in
   one GameTest server under the heavy-JVM lock. Record what occupies the restore target when `target_occupied` is
   returned, and what makes the reply `null`.
2. **Fix the fixture so it does not depend on its placement**, in the way the cause calls for: keep ambient terrain
   fluids out of the fixture (as W302 did) and keep the fixture's own water from reaching anything but the start-control
   cell (as W301F did), with everything it changes outside the template restored. Keep the drop assertions, regions,
   timeouts and the zero-drop requirement unchanged.
3. **Make the failure say why.** When admission is refused for a reason the fixture does not expect, the message should
   name the reply and, for `target_occupied`, what occupies the target; do not accept `target_occupied` as a pass.
4. If the cause is in production code, stop and report it with a failing test.
5. Rerun the same selection with the fix and show it passes; check whether the other adapter fixtures share the
   exposure and say so in the record.

## Verification

The reproduction failing before and passing after on the same placement; the adapter batches (`w164_adapter_success`,
`w164_adapter_fault`, the `w272_*` and `w299_*` batches) 10 times in a row; the whole unit suite if any class changes;
the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges; W300 merges after this.

## Not in this task

W300's change, production assembly and disassembly, the adapter API, the expected-log manifest, the ledger, the status
file and QUEUE.
