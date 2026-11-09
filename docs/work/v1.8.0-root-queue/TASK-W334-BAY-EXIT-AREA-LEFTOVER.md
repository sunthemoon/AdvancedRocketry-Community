# TASK W334: the bay exit fixture area meets a leftover block entity

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w328f`, branch `codex/v1.8.0-w328f`, at W328G's delivered commit `8d22b9ba7` with this TASK merged in. Commit on top of it. |
| Decision | Root decision R402 (QUEUE row W334) |
| Record | `docs/work/v1.8.0-root-queue/records/W334.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W334/` |
| Failing run | Hosted run 42612205968 at `8d22b9ba7`, shard 3. Root's copy of the shard log: `D:/GitHub/ARCE-Task-Evidence/root-queue/W334/run42612205968-shard3.log` |

## The failure

The branch carries W328, W329, W328F and W328G. Its hosted run failed one GameTest in shard 3, and every other test
passed:

```text
unassignedridergetsonlylocalplacement failed! Bay exit LOCAL_ONLY: java.lang.IllegalStateException:
Bay exit fixture area would replace a block entity at BlockPos{x=186, y=-2, z=189}
```

The test is `BayPassengerExitGameTests.unassignedRiderGetsOnlyLocalPlacement` (batch `bay_exit_local`, template
`rocket_test`). `BayPassengerExitGameTestFixture` line 168 sets the fixture origin at `absolutePos(3, 4, 3)`.
`BayPassengerExitGameTestArea.prepare` then checks every cell within 5 columns of that origin, from 3 below to 55
above, and refuses if any block entity is there. So the area reaches two columns outside the test's own structure on
the low x and z sides, and down to one block above the structure floor.

W329 added two GameTest batches. Batch order is HashMap order, so the new batches moved others (see `records/W68.md`
section 6). The same test passed in the shared head's runs and in W329's local full run. Root's reading: an earlier
batch left a block entity in a cell that vanilla does not clear for this test, and the new order put that cell inside
the area. This is a fixture placement weakness, not a defect in W328 or W329, but confirm or correct that reading. Root
re-ran the whole run (a second attempt) to see whether the failure repeats; the record should say what that attempt
showed.

## What to do

1. **Find what is there.** Name the block entity at that cell, and the earlier batch and test that left it, from the
   shard log and the batch layout (vanilla `GameTestRunner` placement, the batch order in the log), or by reproducing
   shard 3's batch sequence locally. If it cannot be identified, say what was tried.
2. **Fix the fixture, not the symptom.** The refusal exists so that the fixture never destroys a running test's
   blocks. Keep that protection for anything a running test owns. Choose and justify one of these, or a better one:
   - keep the area inside cells that vanilla clears for this test's own structure (for example by moving the origin
     or shrinking the reach, if every exit candidate still fits), or
   - before validation, clear block entities in the area that no running test can own, because their batch has
     finished, and log each one cleared, as W323 did for leftover items.
   The fix must cover every test that uses this fixture, not only this one. Each test's meaning, assertions and
   timeouts stay unchanged.
3. **A regression.** A GameTest or unit test that places a leftover block entity in the area before the fixture starts,
   and shows the new behaviour: the leftover is avoided, or cleared and logged. It must fail without the fix.
4. **Check the siblings.** Look for other fixtures that check or change cells outside their own structure (search for
   `getChunkNow` and fixed-area preparation in the GameTest sources), list them in the record, and fix them only if
   they have the same weakness.

## Verification

- The bay exit batches alone, with `JAVA_TOOL_OPTIONS=-Dadvancedrocketrycommunity.gameTestBatches=...`.
- `./gradlew build` with every unit test, then the full `runGameTestServer` with `scripts/check_gametest_log.py`,
  under the heavy-JVM lock (backslash lock path, as WORKER-RULES says). Other workers share the lock, so take it per
  step and release it between steps. Call Python by its full path in any detached script.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a hosted run and merges W328 to W334 together.

## Not in this task

Production code; any change to W328's, W329's, W328F's or W328G's production or tests; the risk register, the status
file and QUEUE.
