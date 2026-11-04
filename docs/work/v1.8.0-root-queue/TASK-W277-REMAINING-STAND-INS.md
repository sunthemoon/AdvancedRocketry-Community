# TASK W277: NEW art for the remaining stand-in faces and the drawer-like side

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w277`, branch `codex/v1.8.0-w277`, created by Root from the shared head |
| Decisions | R308 (the owner's feedback in [OWNER-DECISIONS-20261104](records/OWNER-DECISIONS-20261104.md)) and R314 (below) |
| Style to follow | [W275](records/W275.md), merged at `8e6728547`: `datagen/V180StandInArt.java`, `V180StandInArtData.java`, its provenance record `docs/provenance/v1.8.0-w275-stand-in-art.json`, its tests and its `build.gradle` exclusions |
| Record | `docs/work/v1.8.0-root-queue/records/W277.md`; logs and contact sheet under `D:/GitHub/ARCE-Task-Evidence/root-queue/W277/` |

## Scope

1. **The remaining vanilla stand-in faces** that TASK-W275 left out: `electrolyzer_active` front (sea lantern),
   `oxygen_vent_active` top (sea lantern), `guidance_computer` top (redstone lamp), `rocket_assembler` and
   `fuel_loader` tops (observer), `rocket_fuel_tank` (iron and copper blocks), `rocket_motor` (furnace top, blast
   furnace side), `rocket_seat` (dark oak planks, iron block), `station_deployment_kit` (ender eye icon),
   `satellite_terminal` (daylight detector), `warp_core` (crying obsidian), `microwave_receiver` (inverted daylight
   detector), `satellite_builder` (smithing table), `black_hole_generator` (crying obsidian, obsidian),
   `elevator_anchor` and `elevator_terminal` (iron block), `endgame_casing` (obsidian), `gravity_field_controller`
   (amethyst block), `laser_target` (obsidian), `orbital_laser_drill` (obsidian) and `railgun` (iron block). First
   check which model each block actually renders today (several were replaced by v1.8 models or imported OBJ models,
   for example the rocket motor and fuel tanks): change only faces that a player can see. Ore models keep vanilla
   stone and deepslate as their base.
2. **The drawer-like side (R314).** W275's record lists 24 models that still show
   `advancedrocketrycommunity:block/machine_casing_side`, the side with two drawer handles that the owner disliked:
   `astrobody_data_processor`, `centrifuge`, `chemical_reactor`, `crystallizer`, `cutting_machine`, `electrolyzer`,
   `electrolyzer_active`, `observatory`, `orbital_laser_drill`, `precision_laser_etcher`, `railgun`,
   `satellite_builder`, `black_hole_generator`, `elevator_anchor`, `endgame_casing`, `laser_target`,
   `microwave_receiver`, `satellite_terminal`, `fuel_loader`, `guidance_computer`, `rocket_assembler`, `oxygen_vent`,
   `oxygen_vent_active` and `machine_casing` (W275's `casing-references.json` has the file-level list). Bind their
   sides to W275's `machine_panel_side` (or a matching NEW variant where a lighter casing reads better, for example the
   plain machine casing block), through v1.8 models, as W275 did. `machine_casing_side.png` itself stays byte-identical
   with v0.1.0's approved record; do not change that file.

Root's decision R314, under the owner's delegation of design points: the owner's complaint about the drawer-like side
covers every block that shows it, so W277 rebinds all 24 rather than leaving two side styles in the mod.

Design guidance: faces show what the block does (a guidance computer's screen or lamp, a seat's padded frame, a warp
core's field, a laser drill's emitter), in the project's casing palette and W275's hatch style. Draw NEW art by code;
copy no vanilla or legacy art.

## What to do

Follow W275's method: a pre-authoring provenance record (`docs/provenance/v1.8.0-w277-stand-in-art.json`, review
`PENDING_HUMAN_REVIEW`), deterministic generation in datagen, texture-only v1.8 model bindings, the vanilla derivation
screen, tests like `V180StandInArtTest`, and an 8x before-and-after contact sheet. List in the record every historical
model copy that the v1.8 tree now supersedes, with its path, so Root can add the `build.gradle` exclusions (build
scripts are Root's); you may verify with an external init script as W275 did.

## Verification

`./gradlew runData` with `git diff --exit-code`; `./gradlew build` with all unit tests (with your external exclusion
script); the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; the art screen; the
provenance and resource Python suites; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, adds the exclusions, pushes for a sharded run and merges. The owner
then looks at the art in game.

## Not in this task

Model geometry, block IDs, behaviour, recipes and loot; W275's art; `machine_casing_side.png` and other v0.1.0 files;
the 30 records under D7; sounds; the ledger, the status file and QUEUE.
