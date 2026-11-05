# TASK W288: a lit torch in a sealed, supplied room is unlit after the world loads again

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w288`, branch `codex/v1.8.0-w288`, created by Root from the shared head |
| Decision | Root decision R320 (QUEUE row W288) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 2, finding 2-5 (in Chinese; `evidence/r02d-torch/` and the `r02b`, `r02c`, `r02d` worlds named there, under that folder) |
| Code | `atmosphere/torch/` (`TorchCombustionService`, `TorchEvents`, `CombustionTorches`, `TorchWorldChanges`, `UnlitTorchBlock`) and the atmosphere room volumes |
| Record | `docs/work/v1.8.0-root-queue/records/W288.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W288/` |

## The finding

On the Moon, a sealed room supplied by an active oxygen vent held a lit `minecraft:torch` at (-3, 51, -1) when world
`r02b` was saved (confirmed by decoding the chunk offline). After the server of `r02c` started and stopped, with no
player joining, the block was `advancedrocketrycommunity:unlit_torch`. In `r02d` it was unlit three seconds after the
player arrived, while the HUD read `ROOM SEALED`. In another world a torch relit at 13:42:05 was unlit in the 13:47
save; leaving the Moon for 60 seconds and returning, or removing and replacing the scrubber's filter, did not unlight
it, and a torch lit about 5 seconds after closing the door stayed lit for 60 seconds. The reporter's guess, not
verified: when the chunk loads, the torch check runs before the room's volume has been rebuilt, so the room is not
yet known to be supplied.

## What to do

1. Reproduce with a GameTest (or the existing restart and chunk-reload fixtures): a sealed, supplied room with a lit
   torch, then a real unload and reload of its chunk, or a save and reload of the room's state, with no player
   nearby; assert the torch is still lit once the room is supplied again. It must fail on the shared head. If a
   reload does not reproduce it, use the saved worlds above (copies, never the originals) to find what the server
   does to that torch at start.
2. Fix the cause: a torch in a room whose atmosphere is not yet known after a load must not be extinguished; it may
   be extinguished only once the room is known to be unsupplied (or the column is known to be vacuum), by the same
   rules as now. Keep the existing combustion rules, budgets and bounds, and do not load chunks to decide.
3. Keep or add tests for the cases that must still extinguish a torch: a torch in vacuum, an unsealed room, a room
   whose supply stops.

## Verification

The new test failing before and passing after; the existing torch, combustion and atmosphere GameTests; `./gradlew
build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock;
`python -B scripts/validate_repository.py --require-approved-identity`. Delete any world copies you make when done.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Torch lighting rules, the vent and scrubber, the HUD, the ledger, the status file and QUEUE.
