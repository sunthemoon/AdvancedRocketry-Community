# TASK W453: package K11, the disposable campaign world kit on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w453`, branch `codex/v1.8.0-w453` from the shared branch |
| Decision | Root decision R643 (QUEUE row W453) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K11, rows J-1 to J-4 and J-13, plan choices C33, C34, C35 and C44; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI17 and FI29; checklist [J](records/CAMPAIGN-CHECKLISTS-J.md) rows J01 to J04 and J23 to J29; [W254-V2-CHECKLIST](records/W254-V2-CHECKLIST.md) sections 0 to 3; [W11B3-DESIGN](records/W11B3-DESIGN.md); [W399](records/W399.md) and [W400](records/W400.md) (the session files and the marked-world procedure); the [bay exit runner](../../../scripts/run_v180_bay_exit_cuts.py); the drivers under `scripts/campaign_prep/` as models; QUEUE row W445 (R638: no `data merge` or other NBT write in fixture setup); C6's identity in QUEUE row "C6 run" (R581); C6's code under `src/main/` (read only) |
| Inputs | The sealed K16 revision 2 core world under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/` where a row needs the core route, or a fresh world made by C6 itself (read only: work on copies and check each parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `.../fixtures/K11/`: the kit as source copies with manifests and `SHA256SUMS.txt`, the session files and the qualification runs. Tracked: `scripts/campaign_prep/k11_world_kit.py`, `tests/test_campaign_prep_k11.py`, and the record `docs/work/v1.8.0-root-queue/records/W453.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, no hold over two hours. Detached launchers start with no console window (see WORKER-RULES). Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to the cells, to an existing script or sealed fixture, or to a budget or config value. Enabling any production admission switch, or treating the campaign marker and test properties as production enablement. Starting any client. Editing NBT, SavedData or region files by hand or by script, and `data merge` or any other NBT-writing command (R638). |

## Root's choices for the open points

1. **The marked world.** Build the disposable campaign world as W399 and W400 define it: the marker file, the two
   campaign properties and the separate fuel property `-Dadvancedrocketrycommunity.escrowLoaderAdmission=true` as
   session files, never in the ordinary world. Record that each property has no effect without the full release-test
   gate, and add the no-hook control (the same world without the properties) with its expected refusals.
2. **Bay scenes (FI17, J01 to J04).** Place the two-seat Earth-Moon bay rocket, the data bay and the research-locked
   solar bay, the INTENT hold position 48 blocks up and the empty origin column, the safe 3 by 3 pad one below origin
   and the fall case, with every coordinate and seat pinned. The rocket is fuelled by hand in the campaign, so leave
   its fuel as a client step. Account UUIDs stay parameters until the owner gives them (O5).
3. **Cap and status (C33).** Write the menu-status sequence and the cap recipe for the fixture's owner only; a
   different owner's admission is shown only by account B on B's own bay.
4. **J03 (C34).** NOT_COVERED while the switches are off; record W254 step 3.4 as "not run; covered natively by X5".
5. **Online and exit variants (C35).** Give each of the ten online variants and EXIT.0 to EXIT.10 one disposition:
   real-client with its recipe on the marked world, or synthetic-only with its W325 native case named. K20 later
   copies this table into checklist J; write it as a file K20 can use.
6. **Fuel menu (C44, J23 to J29).** Write the steps for A's loader menu, B's ordinary refusal and the permitted
   synchronized feedback, the hold, seat-clear and probe steps and the refusal variants; B never opens A's
   owner-only menu.

## What to do

1. Write the driver and the tests (Linux-safe: governance runs them on Linux).
2. Build each kit world on C6 under the lock, read back its state, save, stop, copy it out with a manifest (parents and
   their checksum lists, C6 JAR hash and commit, every command, coordinates, seats, the session files, readbacks, file
   inventory and hashes), then load a copy once more on C6 with and without the session files and record that the
   readbacks match and the no-hook control refuses as expected.
3. Map each scene and recipe to J01 to J04 and J23 to J29 step by step in the record; list anything NOT_COVERED with
   the reason. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
