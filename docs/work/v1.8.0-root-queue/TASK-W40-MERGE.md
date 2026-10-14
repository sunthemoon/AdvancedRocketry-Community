# TASK W40 merge: bring the shared branch into W40 and rerun

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w40-safety-return` (at `b96d37a2` plus this task) |
| Branch | `codex/v1.8.0-w40-safety-return` (local only; Root merges) |
| Record | Add a "Shared merge" section to `docs/work/v1.8.0-root-queue/records/W40.md`. Keep the existing text |

W40 is based on `38ed9b95`. Root reviews and merges it only on top of the current shared branch.

## What to do

1. Merge `codex/v1.8.0-classic-content` (`78c5f7dd` or later) into this branch. It brings W33, W34,
   W36 and W38. Commit the merge on its own.
   - Expected conflicts: `CommonConfigTest`'s pinned count, `scripts/gametest_expected_log.json`,
     `tests/test_check_gametest_log.py`, the v1.8 language provider and generated language files
     (regenerate them with `runData` under the lock; never hand-edit `src/generated`),
     `AdvancedRocketryCommunity.java`.
   - Keep every assertion and every expectation from both sides. Under the W28 rule, no GameTest
     callback may schedule another.
2. Check that W40's player tick does not change W38's player-fall tests or W33's torch tests. Both
   use connected players in other Levels, not in the Space Level, so they should be unaffected.
3. Run the commands below. If a test fails, find the cause and fix it in W40 code or W40's own tests.
   Do not change other slices' code, tests, timeouts or expectations; if that seems necessary, stop
   and report it in the record.

## Commands

- The whole unit suite once.
- `runData` under the lock, then `git diff --exit-code -- src/generated`.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- Any W40 design change.
- Status files, the ledger, ADRs and the risk register (Root).
