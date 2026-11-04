# TASK W285: low and zero gravity must not get players kicked for flying

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w285`, branch `codex/v1.8.0-w285`, created by Root from the shared head |
| Decisions | R317 and R322 (QUEUE row W285); R328, the design below |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 2 finding 2-2 and round 3 finding 3-1, with the server logs and screenshots they name |
| Record | `docs/work/v1.8.0-root-queue/records/W285.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W285/` |

## What a real client showed

On a dedicated server with the default `allow-flight=false`:

- **The Moon (2-2).** A survival player falling about 27 blocks into a crater was kicked after 4 seconds with
  "Flying is not enabled on this server" (`ArceTester1 was kicked for floating too long!`), while falling normally
  (`Motion y = -0.52`, `FallDistance 26.3`). At the Moon's gravity (0.165) an ordinary jump already lasts 2.4 s, and
  a fall of about 25 blocks or more exceeds vanilla's 80-tick airborne limit.
- **A station at 0 % gravity (3-1).** A survival player 1.5 blocks past the platform edge hung in place, could not
  return, and was kicked after 4 s; after reconnecting they were still adrift and would be kicked again. The jump key
  did nothing at 0 %.

The mod only resets vanilla's counter for suit flight (`equipment/motion/SuitMotionService.java` line 147, with the
access transformer's line 2). `station/safety/SpaceSafetyReturnService.java` lines 220 to 231 treat only players
outside the station region as adrift.

## Root's design (R328, under the owner's delegation of design points)

1. **Low gravity.** While a player is airborne in a dimension or station whose effective gravity factor is below 1,
   extend vanilla's airborne allowance in proportion to that factor (for example, allow `80 / factor` ticks), with a
   fixed upper bound you choose and justify; never reset the counter unconditionally, and change nothing at factor 1
   or above. Creative and spectator players, elytra and suit flight keep their existing handling.
2. **Zero gravity.** A survival or adventure player inside a station region who is off the platform and airborne for
   a bounded time (shorter than vanilla's limit) is returned to the station's safe point through the existing safety
   return path, with its message, instead of being kicked; the same applies on login if they are still adrift. Find
   and follow what the station gravity ADR (ADR-058) and ADR-040 or the safety-return design say about drifting; if
   they define 0 % jumping as intended, keep it and say so.
3. Keep the server authoritative: no client-supplied position or state decides any of this.

If the code or the ADRs show a reason this design cannot work as stated, stop and report it with the alternative
you recommend, instead of building a different design.

## What to do

Reproduce both cases in GameTests with a mock server player (an airborne player in Moon gravity past 80 ticks; a
player adrift beside a 0 % station platform) that fail on the shared head, implement the design, and add negative
tests: a player at normal gravity is still kicked by vanilla's rule, and a player hovering without falling in low
gravity is still bounded by the extended allowance.

## Verification

The new GameTests failing before and passing after; the suit motion, gravity and safety-return tests; `./gradlew
build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock;
`python -B scripts/validate_repository.py --require-approved-identity`. Note in the record that a real-client check
follows in Root's scripted run.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Gravity values, suit flight, jetpacks and fall damage (W38), station gravity controls, the other round-2 and round-3
findings, the ledger, the status file and QUEUE.
