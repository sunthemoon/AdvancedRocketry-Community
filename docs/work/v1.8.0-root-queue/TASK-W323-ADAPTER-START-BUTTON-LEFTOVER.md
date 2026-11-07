# TASK W323: the adapter start-button GameTest counts an earlier batch's leftover

Root: Claude. Date: 2026-11-08. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w323`, branch `codex/v1.8.0-w323`, created by Root from the shared head |
| Decision | Root decision R382 (QUEUE row W323) |
| Background | `AdapterFaultGameTests.flowingWaterCannotDropFixtureStartButton` (`src/adapterTest/`, batch `w273_adapter_start_button`, added by W273 and W299) failed in W321's first local full run and in W322's local full run, and passed in the hosted runs; it is open in the status file |
| Record | `docs/work/v1.8.0-root-queue/records/W323.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W323/` |

## The failure

W322's full run (`D:/GitHub/ARCE-Task-Evidence/root-queue/W322/`, record [W322](records/W322.md), line 75)
saw one dropped stone button at `(134.79, -60.0, 1598.55)` with age 80. The test asserts at tick 8 that no drop lies
within two blocks of its start button (`AdapterDropFixture.assertNoDrops`, test lines 99 to 116). An item 80 ticks old
existed before this test began. GameTest batches reuse the same grid positions, so an earlier batch's dropped button,
or the sibling's deliberate stone-button drop control, could be what it counted.

## What to do

1. Confirm where the counted item came from: an earlier batch at that position, the sibling
   `fixtureStillRejectsMemberAndCargoDrops` (which spawns stone buttons as drop controls), or something else. Use both
   failing logs, and reproduce if you can.
2. Make the test count only drops it causes. For example, record the item entities in the checked area before the water
   flows and compare against them, or clear and assert an empty area first. The assertion's meaning stays: water must
   not drop the fixture's start button. The timeout stays unchanged.
3. If the sibling's drop controls can reach this area, keep each test's items inside its own area.
4. Check the other adapter and fixture tests that use `assertNoDrops` or `noDrops` over a fixed area for the same
   dependence, and treat any you find the same way, or list them.

## Verification

- The `w273_adapter_start_button` batch several times, and the adapter fault, component and registration batches once.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Adapter behaviour, other intermittent tests, the ledger, the status file and QUEUE.
