# TASK W52F: move the v1.8 imports out of the v0.1.0 managed resource root

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w52f-import-root` (from the shared branch) |
| Branch | `codex/v1.8.0-w52f-import-root` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W52F.md` (new) |
| Evidence | Governance run 38616170516 at `77919159`, step "Verify v0.1.0 audit and provenance transforms"; its log is `D:/GitHub/ARCE-Task-Evidence/root-queue/ci/38616170516/job.log` |

## The defect

`scripts/validate_v010_asset_baseline.py` (lines 255 to 347) allows only the v0.1.0 imported targets and
the v0.1.0 generated manifest in `src/main/resources/assets/advancedrocketrycommunity/` and
`src/generated/resources/`. Every later version keeps its resources in its own
`src/generated/v*/resources` root (`build.gradle` lines 136 to 153).

The W48 importer (`tools/import/import_v180_assets.py`, `RESOURCE_ROOT` and `TARGET_ROOT` at lines 53
and 54) writes v1.8 imports into `src/main/resources/`. W52 (`199e67dd`) imported six K6 item icons
there, so the baseline validator fails:

> unrecorded resources entered the distributable roots: `.../textures/item/data_satellite_package.png`,
> `laser_lens.png`, `satellite_control_chip.png`, `satellite_package.png`,
> `solar_transmitter_module.png`, `survey_scanner_module.png`

`validate_repository.py --require-approved-identity` repeats the failure and derives a v0.1.0 Gate
contradiction from it. Branch CI does not run these validators, so the merge did not catch it.

## Root's decision

v1.8 imports get their own root, `src/imported/v1.8/resources/`, packaged like the other roots.
Historical outputs stay immutable, the v0.1.0 validator is not changed, and the datagen output root
`src/generated/v1.8/resources` stays datagen-only.

- **Root** adds `srcDir 'src/imported/v1.8/resources'` to `build.gradle` as its own commit on your
  branch before you start. If it is not there when you start, stop and say so.
- **You** change the importer and everything that names the old target root.

## What to do

1. **The importer.** Change its target root to `src/imported/v1.8/resources/assets/advancedrocketrycommunity/`.
   - Keep every check: the collision check against the other roots (line 449) must also cover
     `src/main/resources/` now.
   - Update its tests (`tests/test_import_v180_assets.py`). Keep every assertion; change only paths.
   - Add a test that refuses a target under `src/main/resources/`.
2. **The plans.** Change the target paths in `tools/import/v180-k6-plan.json` and
   `tools/import/v180-k1-plan.json`. K1 is still blocked, so only its paths change.
3. **The K6 files.**
   - Move the six PNGs with `git mv`. Their bytes do not change.
   - Regenerate `docs/provenance/v1.8.0-c18d-k6.json` with the importer, not by hand, and check
     that only the target paths change.
   - Search for any other file that names the old paths (the asset plan, records, tests, the
     ledger tools) and update it. Leave sealed evidence and historical records unchanged; list them.
4. **Verify:**
   - `python -B scripts/validate_v010_asset_baseline.py` passes;
   - `python -B scripts/validate_repository.py --require-approved-identity`: the v0.1.0 asset and
     Gate-contradiction failures are gone. Root expects one remaining FAIL, the Markdown link targets
     that C19 owns; report exactly what remains;
   - the importer's `verify` passes for K6, using an upstream clone at the pinned commit
     `c5cd5af62fc07cd4e0d24f06a16033f181c47c04` on D: (clone it if none exists; record where);
   - both jars (`build/libs`) contain each of the six textures exactly once, at
     `assets/advancedrocketrycommunity/textures/item/`;
   - the item models still resolve them: run the GameTests or the unit test that checks model
     texture references, whichever covers it, and name it.
5. **Record** the commits, commands and results.

## Commands

- The importer's tests and the whole unit suite once (`gradlew test`, JDK 17).
- `runData` under the lock; the tree must stay clean.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf` and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- `build.gradle`, workflows, status files, the ledger and ADRs (Root).
- The Markdown link failures of `validate_repository.py` (C19).
