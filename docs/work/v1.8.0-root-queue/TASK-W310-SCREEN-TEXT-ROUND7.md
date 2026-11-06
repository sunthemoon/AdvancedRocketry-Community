# TASK W310: round 7's screen text findings

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w310`, branch `codex/v1.8.0-w310`, created by Root from the shared head |
| Decision | Root decision R358 (QUEUE row W310) |
| Background | W278 round 7 on the JAR of `15fa195a1`: `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, section "第 7 轮", findings 7-3, 7-4 and 7-6, with screenshots under `evidence/r07-ds/screenshots/` and `evidence/r07-eg/screenshots/`. [W293](records/W293.md) fixed the earlier rounds' screen text; follow its approach |
| Record | `docs/work/v1.8.0-root-queue/records/W310.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W310/` |

## What to do

1. **Finding 7-3 (low), overlapping text.** Move or fit the text so that nothing overlaps or touches, at GUI scales 2
   and 3, in English and Chinese:
   - the satellite terminal's status line is drawn under the target `<` and `>` buttons (`SatelliteTerminalScreen`,
     screenshot `b1-term-00-idle`);
   - the satellite bay's notice touches the middle slot's frame (`SatelliteBayScreen`, `b1-bay-00-idle`);
   - the satellite builder's hint `Insert a chassis and a primary mo…` is still cut (hover shows it). Fit or wrap it if
     its panel allows.
2. **Finding 7-4 (low), raw IDs.** Show translated names instead of raw IDs where round 7 still saw them:
   - the black-hole generator's `Orbit body` and `Fuel table` lines (`b1-bh-01-status-hover`);
   - the railgun's `Destination at` (`b1-raila-04-route-hover`);
   - the elevator's `Selected anchor` (`b1-elev-05`);
   - the star map's info line `Moon / advancedrocketrycommunity:moon` (`e1-starmap-2`).

   `OrbitalLaserDrillScreen` line 104 already translates its body line; share that code instead of copying it. For the
   fuel table, use a translated name if one exists; otherwise say what it would take. If a design document asks the
   star map to show the ID, keep it and say so.
3. **Finding 7-6 (information), refusals on the endgame screens.**
   - On the railgun, "Not enough energy [INSUFFICIENT_ENERGY]" appears only in the bottom notice, while the status line
     and its hover still say `OK [OK]` (`b1-raila-06`).
   - The elevator's Bind refusal says "An elevator endpoint rule fails [ELEVATOR_RULE]" without naming the rule, although
     `ElevatorRules.Check` holds the failed rule's name (`b1-elev-05`).
   - **Root decision:** the bottom notice stays the refusal channel, and the status line describes the device.
   - In this task, name the failed elevator rule in the notice if the client already receives it.
   - Keeping the railgun's refusal visible until the next action waits for W311. W311 is adding that mechanism to the
     shared screen notice (`ScreenMessageState`, `ScreenActionMessages`), and Root will queue its use on the endgame
     screens after W311's merge. Do not change the railgun's notice handling here.
4. **Star map.** It still lists the shared Space body as a focusable body under Earth, with "No surface arrival; inspect
   orbit stations" (`e1-starmap-next-2`). From W308 and W309 on, no station can be warped to or created around that
   body. Leave it out of the star map, unless the star map needs it to reach existing stations; say which.

No saved field, packet, menu-data or protocol change. If an item needs one, do not make it; say in the record what it
would take. Language keys go through the existing data generators (en_us and zh_cn).

## Verification

- A unit or layout test for each text fit and each translated line, where W293's test approach allows; the existing
  screen tests.
- `./gradlew build` with all unit tests.
- runData, then `git diff --exit-code` after committing the generated files.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Screenshots are not required; a later client round will check the screens.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The warp controller screen (W309), new device behaviour, the ledger, the status file and QUEUE. W311 runs in parallel
and is changing `RocketFlightScreen`, `ScreenMessageState`, `ScreenActionMessages`, `RocketFlightFeedback`,
`RocketFlightService` and `RocketEntity`; do not edit them or `RocketFlightScreenLayout`. The flight console's
station button bracket (`[ STATION ArceT…` touching the button's left edge, `c1-43`) is left for after W311's merge.
