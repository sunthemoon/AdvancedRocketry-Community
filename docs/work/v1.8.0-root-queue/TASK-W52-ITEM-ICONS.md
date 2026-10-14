# TASK W52: import the six item icons (closure batch K6)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w52-item-icons` (from the shared branch) |
| Branch | `codex/v1.8.0-w52-item-icons` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W52.md` (new) |
| Specification | `docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.md` sections 3 and 5, batch K6; `records/W48.md` (the importer); ADR-061 sections 4.4 to 4.9; `docs/work/v1.8.0-asset-plan.csv` (ADR-062's plan) |

Batch K6 imports the six class B item textures:
- `basiclens.png`;
- `microwavetransmitter.png`;
- `opticalsensor.png`, which AUDIT-01 assigns to the data satellite icon;
- `oremapper.png`;
- `satellite.png`;
- `satelliteidchip.png`.

Then it points their item models at them. W48's read-only smoke on the real upstream found these six
CLEAR.

## What to do

1. **Write the plan and the record.** The plan is `tools/import/v180-k6-plan.json`, with each row's
   source path, target path, transformation, owning ledger units and asset-coverage row. The record is
   `docs/provenance/v1.8.0-c18d-k6.json`.
2. **Run the importer.** Run `python -B tools/import/import_v180_assets.py generate`, then `verify`,
   with:
   - the pinned upstream clone (`--filter=blob:none`; a shallow clone fails);
   - the two client JARs from `tools/audit/fetch_vanilla_clients.py`, as W48's record shows.
3. **Point the models at the textures.** Change each item model in the DataGen provider that owns
   it, then run `runData`. A model must not reference a texture the plan does not import.
4. **The `satellite*.png` plan rule.** AUDIT-01 notes that it covers two class B rows and six
   class E rows, and suggests splitting it. Splitting changes ADR-062's importable list, which needs
   an ADR-062 revision and the owner's acceptance. So **do not edit `v1.8.0-asset-plan.csv`**.
   - If the importer accepts `satellite.png` under the existing IMPORT rule, import it and say so.
   - If it refuses without the split, import the other five, and stop and report for `satellite.png`.
5. **Hits stop the work.** If any derivation or history check is not CLEAR, stop for that row and
   report it. Do not appeal a HIT.
6. **Tests.**
   - The existing resource and provenance tests, extended to the new files.
   - Each item model resolves its texture.
   - The importer's `verify` in the record.

## Commands

- The importer's `generate` and `verify`, with exact arguments in the record.
- The whole unit suite once.
- `runData` under the lock, then `git diff --exit-code -- src/generated` after committing.
- `runGameTestServer` under the lock, then the log checker.
- Delete your upstream clone, client JARs and generated `build/gametest/world` at the end with a
  plain `rm -rf`. If the tool refuses, record the paths and Root deletes them.

## Not in this task

- The OBJ batches (K1 to K5), NEW art, exclusions and ADR-062 revisions.
- The ledger and status files (Root). Root marks the rows terminal after review.

## Amendment (Root, 2026-10-14, after the model-publication blocker in `records/W52.md`)

Root committed `843f92c8`. It excludes the six historical item models from `processResources` and
`sourcesJar`:
- `data_satellite_package` and `satellite_control_chip` (v0.8);
- `satellite_package`, `solar_transmitter_module` and `survey_scanner_module` (v1.6);
- `laser_lens` (v1.7).

Now:
1. **Revert your edits to the historical providers.** `SatelliteItemModelProvider`,
   `V160SatelliteProviders` and `V170EndgameProviders` are historical inputs.
2. **Write the six models from a v1.8 provider** registered in `BootstrapDataGenerators`, so
   `runData` writes them under `src/generated/v1.8/resources`, each bound to its imported icon.
3. **Check the jar.** Run `build` under the lock and check that the packaged jar holds exactly one
   copy of each model, the v1.8 one. Your six model-binding tests must then pass unchanged.
4. **The ledger validator** still reports the six sources as not handled as IMPORTED. Root updates
   the asset plan at merge. Leave it and state it in the record.
5. Finish the commands and the record.
