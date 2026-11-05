# TASK W291: choosing a named landing pad from elsewhere, and losing the choice on the console

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w291`, branch `codex/v1.8.0-w291`, created by Root from the shared head |
| Decision | Root decision R324 (QUEUE row W291) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 3, finding 3-3 (in Chinese; screenshots `n50`, `n51`, `u50`, `u90`, `u91` under `evidence/` in that folder) |
| Record | `docs/work/v1.8.0-root-queue/records/W291.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W291/` |

## The finding

1. On Venus, `/arcepad select <station> <pad> 0` was refused with `Refused: station owner/operator, a valid name and an
   available pad slot are required.` After the tester force-loaded the station chunk holding the pad, the same command
   succeeded. The reporter read `station/pad/LandingPadCommands.java` lines 101 to 113: every failed condition sends
   the same `refused` text, and `LandingPadService.resolve` (lines 70 to 82) needs the pad's block entity to be loaded,
   which a station seen from another body usually is not.
2. The flight console then showed the station preselected; pressing the station button once more cleared the chosen
   pad without a word (`rocket/menu/RocketFlightSelection.selectNextStation`, lines 89 and 90, sets `selectedPad` to
   empty first), and the rocket landed at the station's default point instead of the pad. The console's route line
   shows only the station name, never the pad.
3. Choosing again and launching without pressing the button landed on the named pad, so the pad itself works.

## What to do

1. **Say the real reason.** Give each refusal of `/arcepad select` its own translated message: not the owner or an
   operator, unknown name, no free slot, and the pad not loaded. For an unloaded pad, decide from the code and the
   ADRs that govern pads and chunk loading whether selection may be recorded without loading the chunk (a server-side
   record checked again at launch or arrival, never loading a chunk because a client asked) or must stay refused with
   that reason. Prefer recording it if the arrival already checks the pad again; say what you chose and why in the
   record. Never force-load a chunk for a command.
2. **Keep the choice visible and deliberate.** Show the chosen pad's name on the flight console's route line. Pressing
   the station button must not silently drop a chosen pad: either keep the pad while the station stays the same, or
   clear it with a visible change on the console. Choose the smaller change that matches the existing menu's rules,
   and keep all selection server-authoritative and validated as it is now.
3. Add the translations through the existing data generators (English and the existing Chinese language file).

## Tests

GameTests or unit tests for each refusal reason; for the unloaded-pad case as decided; for the console keeping (or
visibly clearing) the pad when the station button is pressed; and for the route line naming the pad. Each must fail
before the change where it changes behaviour.

## Verification

The new tests; the existing landing pad, flight menu and station tests; `./gradlew build` with all unit tests;
`runData` with no unexpected diff; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM
lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Pad assignment rules, landing rules (W290), chunk loading policy beyond what the pad check needs, the ledger, the
status file and QUEUE.
