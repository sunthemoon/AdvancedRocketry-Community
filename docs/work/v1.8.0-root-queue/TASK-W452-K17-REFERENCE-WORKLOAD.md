# TASK W452: package K17, the combined reference workload world on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w452`, branch `codex/v1.8.0-w452` from the shared branch |
| Decision | Root decision R640 (QUEUE row W452) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K17, row A-4 and plan choices C4 and C10; checklist [A](records/CAMPAIGN-CHECKLISTS-A.md) rows A08 to A11; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) section 4 (the reference load); the package records [W434](records/W434.md) (K04), [W440](records/W440.md) (K08), [W442](records/W442.md) (K08b), [W441](records/W441.md) (K09, including its finding that 200-tick data missions finish during the warm-up), [W444](records/W444.md) (K10's load driver) and [W403](records/W403.md) (K15's observers); the drivers [k04_rockets.py](../../../scripts/campaign_prep/k04_rockets.py), [k08_stations.py](../../../scripts/campaign_prep/k08_stations.py), [k08_station_load.py](../../../scripts/campaign_prep/k08_station_load.py), [k09_missions.py](../../../scripts/campaign_prep/k09_missions.py), [k09_mission_load.py](../../../scripts/campaign_prep/k09_mission_load.py), [k10_endgame_load.py](../../../scripts/campaign_prep/k10_endgame_load.py) and the `scripts/campaign_observe/` package; QUEUE row W445 (R638: no `data merge` or other NBT write in fixture setup); C6's identity in QUEUE row "C6 run" (R581); C6's code under `src/main/` (read only) |
| Inputs | The sealed fixtures under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/`: K08 `ten/`, K09 `missions-101/`, K04 `2048/` and the K03 worlds they came from (read only: work on copies and check each parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `.../fixtures/K17/`: the combined world as a source copy with a manifest and `SHA256SUMS.txt`, the schedule, and the qualification runs. Tracked: `scripts/campaign_prep/k17_reference.py`, `tests/test_campaign_prep_k17.py`, and the record `docs/work/v1.8.0-root-queue/records/W452.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, no hold over two hours. Detached launchers start with no console window (see WORKER-RULES). Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to the cells, to an existing script or sealed fixture, or to a budget, config value or the reference load. Starting any client. Editing NBT, SavedData or region files by hand or by script, and `data merge` or any other NBT-writing command (R638). Lowering the load to make a run fit. Treating Windows figures as REF evidence. |

## Root's choices for the open points

1. **One world.** Build a single world on C6 that holds the whole docs/17 load at once. Start from the copy whose
   content C6's commands cannot recreate (likely K09's `missions-101`, from the v1.6 world) and add the rest on it
   with the existing drivers' native command sequences (K08's station commands for ten station records, K04's
   2,048-block layout and the rockets). Record the choice and why. Name every command and hook in the manifest.
2. **Build time and run time.** Release-test hooks may build the world, as K04, K08, K09 and K10 did. The four-hour
   run of A08 has no test properties, so the world and its schedule must work without them. Anything at run time
   that needs a player is a scripted client step for accounts A and B (O7 supplies them later).
3. **Sixteen rooms (C4, A11).** Sixteen sealed rooms of one recorded footprint of at most 4,096 blocks, each with an
   oxygen vent supplied by ordinary C6 blocks (a generator and an oxygen supply fed from chests or pipes, if C6
   allows it without a player). Read back the shipped `atmosphere.maxVolumeCells` and
   `atmosphere.maxInspectionsPerLevelTick` unchanged. If the supply needs a player, place the blocks empty with their
   inputs in chests and write the supply as client steps. Record each room's coordinates and boundary cell for A11's
   open and restore steps.
4. **Missions (W441).** At least 100 satellite and mission records, with the number ACTIVE recorded at each sample.
   Short data missions finish in the warm-up, so prefer kinds and deadlines that stay ACTIVE through the samples, and
   state how many remain ACTIVE over a four-hour run without players; write the client steps that refill them.
5. **Rockets and transfer.** Two rockets in flight during sampling, the 2,048-block structure assembled and
   disassembled once through the normal path, and one dimension transfer, each placed so the schedule can trigger it
   (server command where C6 has an ordinary one, otherwise a client step).
6. **Schedule (C4, C10).** Vanilla autosave, a flushed save every 30 minutes, and one orderly restart at two sampled
   hours. Write a driver that runs this schedule with K15's samplers (raw ticks, RSS, GC, forced chunks) and records
   every event, the active counts and an accounting baseline (identities and item, fluid and fuel totals) before and
   after. Qualify it once on Windows with a compressed schedule and short labelled windows, including one restart;
   that is a qualification, not a measurement.

## What to do

1. Write the driver and the tests (Linux-safe: governance runs them on Linux).
2. Build the world on C6 under the lock, read back every load component, save, stop, copy it out with a manifest
   (parents and their checksum lists, C6 JAR hash and commit, every command, coordinates, IDs, readbacks, the
   schedule, file inventory and hashes), then load a copy once more on C6 and record that the readbacks match.
3. Run the compressed qualification on a fresh copy and report each component present or missing over it.
4. Map the world and the schedule to A08, A09, A10 and A11 step by step in the record; list anything NOT_COVERED
   with the reason. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
