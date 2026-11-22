# TASK W443: K16 version 2, fix the core route found by P9's dedicated rehearsal

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w443`, branch `codex/v1.8.0-w443` from the shared branch |
| Decision | Root decision R624 (QUEUE row W443), following R613 on [W430](records/W430.md) and [W439](records/W439.md) |
| Sources | [TASK-W427](TASK-W427-K16-CORE-WORLD-BATCHES.md) and its generator `scripts/campaign_prep/k16_core.py`; the tracked harness `scripts/run_campaign_client_session.py` and `scripts/campaign_client/` with its README; [W430](records/W430.md) (findings 1 to 6); the rehearsal of 2026-11-22 23:35 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase1-prep/p9-rerun/` (`dedicated/results.jsonl`, `dedicated/evidence/screenshots/`, `dedicated/evidence/*.log`, `dedicated-submitter.jsonl`, `RUNBOOK.md` and `tools/`; read only); K16's sealed set `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/final/` (read only); C6's code under `src/main/` (read only) |
| Outputs | Outside Git: a new sealed set `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/` (never change `final/`) and any rehearsal runs under `.../phase1-prep/p9-v2/`. Tracked: changes to `scripts/campaign_prep/k16_core.py` and `scripts/campaign_client/` with tests under `tests/`, and the record `docs/work/v1.8.0-root-queue/records/W443.md` |
| Lock and window | Server builds take the heavy-JVM lock with the WORKER-RULES loop. A client run is allowed only between 23:30 and 08:00 (R604), through a copy of W439's runner and submitter under `.../phase1-prep/p9-v2/tools/` with fresh run names, never over W439's files. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to K16's `final/` set, to W430's or W439's files, or to a budget. Changing the constants other drivers import from `k16_core.py` (JAR, candidate, hashes and paths used by the K07, K08 and K09 drivers) except by adding new names. |

## What the rehearsal showed (Root's review of the 24 screenshots, R624)

All 15 requests reported PASS (harness actions only); the game state matched the intent in 8 of 14, 3 of them partly:

1. `core-room`: the HUD read `ATM // VACUUM`, `O2 0/2000`, `SUIT 0/4`; the server reported `Atmosphere=VACUUM vents=0 providers=0` although the vent at (34, 150, 34) holds 1000 oxygen and 40000 FE. The player died of vacuum exposure, so `core-flight-plan` clicked a death screen and `core-flight-observe` respawned the player.
2. `core-rocket-assemble` only validated (`validated: blocks=5 mass=250 thrust=1000 fuel=2000 seats=1`); no rocket entity existed afterwards, and no request fuels the rocket (fuelling is meant at (12, 150, 53)).
3. `core-screen-lathe` opened no screen; its closing ESC opened the vanilla Game Menu, which `core-screen-terminal` then captured.
4. `core-station`: warp energy 0 FE; the target click moved Cygnus X-1 to Moon, so the quote was in-system and refused with `WARP_INSUFFICIENT_ENERGY`.
5. None of the five hover screenshots shows a tooltip.
6. Visual observations, not route defects: the combustion generator's player inventory has no slot outlines; the railgun's status tooltip covers the +1/+16 buttons and part of the inventory.

## What to do

1. **Gate each request on its precondition.** Add a bounded way for a request to check a server readback (or the open
   screen) before its actions and to stop with a clear `PRECONDITION_FAILED` result instead of acting blindly,
   either as a harness op with tests or in the submitter; send ESC only when a screen is open.
2. **Room (item 1).** Read C6's atmosphere and vent code and the fixture's room. Find why the vent counts zero
   and the suit is empty. If the fixture is wrong (seal, vent placement or facing, supply, suit filling), fix the
   fixture with normal commands. If the evidence points to C6, write a native reproduction and record it as a
   product finding for Root; do not work around it in the fixture by skipping the check.
3. **Rocket (item 2).** Add the normal assembly and fuelling steps so a rocket entity exists, is fuelled, and the
   flight requests have something to board, each gated as in step 1.
4. **Lathe, station and hover (items 3 to 5).** Fix the lathe aim, the station target selection (and give the
   station enough ordinary energy for the intended quote, or make the request's intent the refusal and say so),
   and the hover points against `controls.json`.
5. **W430's harness findings.** Resolve findings 2 (`pauseOnLostFocus`) and 4 (the lock label `W399 campaign`
   should name the session) in `scripts/campaign_client/`, with tests; record 3, 5 and 6 as still open or answered.
6. **Item 6** goes into the record as visual observations for the campaign's V1 review; do not change the mod.
7. **Build and seal** K16 v2 with the updated generator on C6 under the lock (manifest, `SHA256SUMS.txt`,
   `USAGE.md` naming what changed from `final/`), run `check` on new session files, and, inside the window, one
   dedicated rehearsal of the 14 core requests with the copied runner; review its screenshots yourself and report
   per request what is visible and whether it matches the intent.
8. Write the record (changes, hashes, test and check results, the rehearsal review, remaining issues) and run
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
