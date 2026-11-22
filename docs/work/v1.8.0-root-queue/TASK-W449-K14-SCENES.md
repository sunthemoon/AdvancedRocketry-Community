# TASK W449: package K14, the sleep, research, landing and paired formation scenes on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w449`, branch `codex/v1.8.0-w449` from the shared branch |
| Decision | Root decision R633 (QUEUE row W449) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K14, rows I-4, J-1, J-9 and J-10, plan choices C32, C40 and C41; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI31 and rows I07, J18 and J19; checklists [I](records/CAMPAIGN-CHECKLISTS-I.md) (I07) and [J](records/CAMPAIGN-CHECKLISTS-J.md) (J18, J19, J20); [W37B](records/W37B.md) and the [D1 outcome freeze](../v1.8.0-c18a-sleep/D1-OUTCOME-FREEZE-33.md) with its B1 and R1 conditions; [W21A-DESIGN](records/W21A-DESIGN.md) section 7; [W189](records/W189.md) and [W311](records/W311.md); [W241](records/W241.md) and [W402](records/W402.md) (K13's packs); the drivers [k04_rockets.py](../../../scripts/campaign_prep/k04_rockets.py), [k05_machines.py](../../../scripts/campaign_prep/k05_machines.py) and [k16_core.py](../../../scripts/campaign_prep/k16_core.py) as models, with [W434](records/W434.md), [W435](records/W435.md) and [W443](records/W443.md); C6's identity in QUEUE row "C6 run" (R581); C6's code under `src/main/` (read only) |
| Inputs | The sealed K16 revision 2 core world `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/` (W443; not `final/`, `world/`, `ready/` or `prepared/`) and the sealed FI08 under `.../fixtures/K03/FI08/` as the prior-world copy; K05's formed machines under `.../fixtures/K05/` and K13's packs under `.../fixtures/K13/` where they serve a row. All read only: work on copies and check each parent's `SHA256SUMS.txt` first |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K14/`: each scene as a source copy with a manifest and `SHA256SUMS.txt`, plus the qualification runs. Tracked: `scripts/campaign_prep/k14_scenes.py`, `tests/test_campaign_prep_k14.py`, and the record `docs/work/v1.8.0-root-queue/records/W449.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, no hold over two hours. Detached launchers start with no console window (see WORKER-RULES). Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to the cells, to an existing script or sealed fixture, to a balance, budget or config value. Starting any client. Editing NBT, SavedData or region files by hand or by script. Force-loading a chunk the checklist does not load by ordinary play. Treating ordinary sleep as the planet-respawn extension. |

## Root's choices for the open points (FI31 and I07)

1. **Host.** Every scene is made on C6 from copies of K16 revision 2 (or of FI08 for the prior-world cases) with
   C6's registered commands, as K04, K05 and K16 did. Name each command and hook in the manifest. Where a row needs a
   formed structure that K05 already sealed, cite K05's copy instead of rebuilding it.
2. **J18 sleep (C40).** Read the freeze table and pin, on each of the six hosts (Moon, Space, Mars, Venus, Tau Ceti f
   and g), a valid supplied bed, the station VISIT, and the prior home tuple and its absence. Qualify natively what the
   server can show alone: the V02 holder readback on a fresh copy and on the FI08 copy, the V03 override, missing and
   reused-type packs with reload and their diagnostics, the V07 forced, off-world, invalid-home and pending-backup cases
   on FI08 copies, the V12 portal controls, and two save and reopen cycles of each with disk tuple readbacks. Write
   V04 to V06, V08 to V11 and V13 as scripted client steps with preconditions on the prepared copies, and the V06
   B1 and R1 recipe exactly as the freeze defines it; a callback the server cannot qualify stays NOT_COVERED with that
   reason. Record the planet-point extension as not implemented in v1.8, not as a row.
3. **J19 research (C41).** Read the UI strings and the section-7 effective values from C6 (language files, config
   defaults and the code) into the manifest. Form the observatory and the astrobody data processor in four rotations
   from their tracked patterns with lens and power plugs placed, supply power by ordinary C6 blocks (at least 20,000 FE
   for a survey and 4,000 FE for an analysis; name the source), and record device UUIDs, positions and starting
   balances read back from the server. The owner, competitor, rebind, full-account and pause variants come from
   ordinary player actions only: write each as scripted client steps with the starting copy and the expected
   readback; do not edit a balance. Add the station case on a station copy if K16 revision 2 has one; otherwise list
   which copy the campaign uses.
4. **J20 landing.** Build a tall rocket with its top seat nine blocks above origin and its origin 12 to 15 blocks into
   an entity section, in the way K04 assembles legal rockets, in survival and creative variants; pick a safe Moon
   slow-fall spot from rest; place a named pad in a chunk that stays unloaded at the start. Read back the rocket's
   seat positions, the pad name and that its chunk is unloaded, and write the client steps for J20's seven checks.
5. **I07 paired scene (C32).** Prepare the phase-4 paired break, repair and tracking scene: the six multiblocks and the
   precision laser etcher formed in each supported facing (from K05 where sealed) and an elevator with both endpoints,
   with two seat positions for clients A and B. Write the break, repair, rotate, restore, unload and reload steps as
   client steps. Map K13's missing-resource pack to the scene it is loaded over; the actual pack load is a client
   step and stays NOT_COVERED here. The 1.12 screenshot comparison stays NOT_COVERED under C32.

## What to do

1. Write the driver and the tests (Linux-safe: governance runs them on Linux).
2. Build each scene on C6 under the lock, read back its state, save, stop, copy it out with a manifest (parent and its
   checksum list, C6 JAR hash and commit, every command, coordinates, device IDs, readbacks, file inventory and
   hashes), then load a copy once more on C6 and record that the readbacks match.
3. Map each scene and each qualified control to the rows it serves (I07 steps 1 to 7, J18 steps 1 to 14, J19 steps 1
   to 11, J20 steps 1 to 7) in the record; list anything NOT_COVERED with the reason.
4. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
