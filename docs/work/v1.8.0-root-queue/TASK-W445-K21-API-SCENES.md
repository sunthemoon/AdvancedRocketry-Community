# TASK W445: package K21, the API scenes on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w445`, branch `codex/v1.8.0-w445` from the shared branch |
| Decision | Root decision R630 (QUEUE row W445) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K21, rows D-1 and D-3 to D-7, plan choices C1, C15, C17 and C18; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI11 and FI19 to FI21 and rows D01 to D12; [CAMPAIGN-CHECKLISTS-D](records/CAMPAIGN-CHECKLISTS-D.md); [W426](records/W426.md) (K01: C6's release, sources and API JARs and the independent API fixture binary with their hashes under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/candidate/`); [W406](records/W406.md) (K06's payload and atmosphere-tag packs); the v1.3 runners [flight](../../../scripts/run_v130_adapter_flight_smoke.py), [component](../../../scripts/run_v130_component_smoke.py), [fuel](../../../scripts/run_v130_fuel_smoke.py), [recovery](../../../scripts/run_v130_adapter_recovery_smoke.py), [atmosphere](../../../scripts/run_v130_atmosphere_boundary_smoke.py), [suit](../../../scripts/run_v130_suit_equipment_smoke.py) and [satellite payload](../../../scripts/run_v130_satellite_payload_smoke.py) (read only; reuse their scene layouts); the K07 to K10 drivers under `scripts/campaign_prep/` as models; C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | K01's JARs (read only, hash-checked), K06's packs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/` (read only), and a fresh world made by C6 itself or a copy of FI08 under `.../fixtures/K03/FI08/` (say which and why) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K21/`: each scene as a source copy with a manifest and `SHA256SUMS.txt`, the two removal copies with full inventories, and the qualification runs. Tracked: `scripts/campaign_prep/k21_api_scenes.py`, `tests/test_campaign_prep_k21.py`, and the record `docs/work/v1.8.0-root-queue/records/W445.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, no hold over two hours; detached launchers start with no console window. Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to `compat-test-mod/`, to the cells, to an existing script, to K01's or K06's files, or to a budget. Starting any client (the installed and omitted client connections run in the campaign). Editing NBT, SavedData or region files by hand or by script. Using a source world for a removal (C18). |

## What to prepare

1. **Server with the API fixture.** A C6 server with K01's release JAR plus the independent API fixture binary,
   both hash-checked; record the fixture's declared dependency metadata (required on both sides, no display-test
   exemption) as the D-1 expectation for the campaign's installed and omitted client connections (C15). Write
   those two client connections as scripted client steps; do not run them.
2. **Component and loader scene (D-5, FI19, C17).** The v1.3 component layout on C6, starting from half the
   component capacity (750 of 1,500) and using the ordinary item loader, with the completion target's eligibility
   read back; escrow fuel stays in FI29 and is not used here.
3. **Moon room and suit steps (D-3, D-4, FI20, C17).** The v1.3 Moon room on C6 using only the tag override that
   the atmosphere runner's `tag_pack` builds (K06's hashed pack), read back the authoritative tags after a reload,
   and write the equipment, refill and HUD and reconnect steps for a real player (the historical actor counts 999,
   998 and 1,997 are not expectations).
4. **Payload scenes (D-6, FI21).** The satellite payload scene with K06's payload pack and its changed-definition
   variant, the terminal and owner as parameters, and the stale and range probes listed as K18's controls.
5. **Removal copies (D-7, C18).** Two fresh copies of the payload and component scenes, each with a full file
   inventory and hashes taken before the fixture mod is removed, then started on C6 without the fixture and the
   outcome read back and recorded; the sources are never used.
6. **Flight and recovery (D02, D10).** Reuse the v1.3 adapter flight and recovery layouts on C6 where the
   checklist needs them, or record why not.

## What to do

1. Write the driver and tests (Linux-safe).
2. Build each scene on C6 under the lock, read back, save, stop, copy out with a manifest (JAR and fixture hashes,
   pack hashes, every command, coordinates, owners, readbacks, inventory and hashes), then load a copy once more
   and record that the readbacks match.
3. Map each scene to D01 to D12 in the record and list anything NOT_COVERED with the reason.
4. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
