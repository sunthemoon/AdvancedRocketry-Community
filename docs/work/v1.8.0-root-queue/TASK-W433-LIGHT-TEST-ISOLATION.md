# TASK W433: make the light-transmission GameTests independent of their neighbours and of timing

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w433`, branch `codex/v1.8.0-w433` from the shared branch |
| Decision | Root decision R600 (QUEUE row W433) |
| Failures | Shared regression run 44861941486 at `76bff783e`, whose source equals candidate C6 (whose own run 44770334930 passed both tests): attempt 1, shard 1, `monoMotorsDoNotOccludeAndPassLightToTheCellBeneath` failed with "Unexpected block light beneath minecraft:iron_block {}: ... rocket_motor {source=15 probe=13 tested=14 beneath=13} advanced_rocket_motor {source=15 probe=13 tested=14 beneath=13} minecraft:iron_block {source=15 probe=13 tested=0 beneath=6}"; attempt 2, shard 1, `sawDoesNotOccludeAndPassesLightWithoutChangingServerGeometry` failed with "Saw transmission or opaque control failed". |
| Record | `docs/work/v1.8.0-root-queue/records/W433.md` |
| Write scope | The GameTest classes that hold these two tests and every other light-transmission or occlusion GameTest with an opaque control (find them all), test-only helpers, the record. No product code, no other test. |
| Lock | Targeted GameTest runs are heavy: take the heavy-JVM lock with the WORKER-RULES loop (C6 attempt 2 yields it for 120 seconds after each family), hold it only while the server runs. |

## What to do

1. Find the cause from the code and the log: the opaque control read light beneath it, so light reached that cell
   by another path. Check whether the test region is open to light from neighbouring test structures in the same batch
   (GameTests of a batch are placed side by side), whether a sky or block light source of another test can reach it,
   and whether the test reads light before the light engine has finished propagating.
2. Fix the tests only: enclose each measured column so no outside light can reach it (or measure in a sealed
   structure), wait for the light engine to finish its pending updates for the measured positions before reading,
   and keep every assertion, including the opaque control's. Do not raise timeouts or relax the expected values
   (AGENTS.md section 5).
3. Prove it: run the affected batches together with their usual neighbours several times (at least five runs), and
   once with a light source deliberately placed in the neighbouring structure to show the enclosure holds; record each
   run. Then run the unit suite for the touched packages and `python -B scripts/validate_repository.py`, which must end
   with `0 failed`.

## Rules

- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
