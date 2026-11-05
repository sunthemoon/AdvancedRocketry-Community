# TASK W286: the atmosphere analyzer calls a supplied, sealed room non-breathable

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w286`, branch `codex/v1.8.0-w286`, created by Root from the shared head |
| Decision | Root decision R318 (QUEUE row W286) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 2, section R2 and finding 2-3 (in Chinese; screenshots `l11`, `l19` and the server log under `evidence/` in that folder) |
| Record | `docs/work/v1.8.0-root-queue/records/W286.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W286/` |

## The finding

On the Moon, a 3 by 3 by 3 room (iron walls and floor, a glass roof, a player-placed airlock door, an oxygen vent in
the floor with a combustion generator and a CO2 scrubber) was sealed and supplied: the vent read `Active | O2 995`,
the HUD read `ATM // ROOM SEALED`, the seal detector read `SEALED | SUPPLIED`, the suit's oxygen stopped falling, and
`/arce atmosphere status` run as the player reported `Atmosphere=BREATHABLE vents=1 providers=1 ... volumes=1
cells=27`. At the same moments the atmosphere analyzer, used three times inside the room, reported `Moon (Surface) |
Effective breathing: Non-breathable | ... | No confirmed controlled supply`. Outside it correctly reported
`Non-breathable ... pressure 0.0, temperature 220.0 K`. The expected reading inside is breathable with a controlled
air supply.

The reporter read `instrument/AtmosphereAnalyzerService.java` lines 38 to 68: it queries the eye position through
the same `AtmosphereManager.breathabilityAt` and `controlledAt` as the HUD, with the same manager instance as the seal
detector (`AdvancedRocketryCommunity.java` lines 214 and 218). The cause is not found.

## What to do

1. Reproduce with a GameTest: build a sealed, supplied room like the one above (reuse an existing sealed-room
   fixture if one fits), wait until the room is breathable by the same checks the HUD and the status command use,
   then call `AtmosphereAnalyzerRuntime.read` (or the server path the analyzer item uses) for a player standing in
   the room, and assert a breathable reading with a controlled supply. It must fail on the shared head if the bug is
   in the server path; if it passes, look at the client side of the reading (the packet, the screen or the item's use
   path) and at which position is queried (eye position, cell rounding, the player inside a door or vent cell).
2. Fix the cause where it is; keep the analyzer's other readings (outside, unsealed, unsupplied, unloaded) unchanged
   and covered.
3. If the cause is a timing or cache difference between the analyzer and the HUD, say which one is right and make the
   analyzer agree with the authoritative state, without a new cache.

## Verification

The new GameTest failing before and passing after; the existing analyzer, atmosphere and seal detector tests; `./gradlew
build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock;
`python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Atmosphere rules, vent and scrubber behaviour, the HUD, the ledger, the status file and QUEUE.
