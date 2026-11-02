# TASK W265: import batch H1, the laser gun's held model

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w265` (new; branch `codex/v1.8.0-w265` from the shared head named in your prompt) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, SHA-256 `861320967dde0492569d8e31f2874a0d7288c603af8d2bca5720c720fb97925a`, section 6 (H1) and section 7 (F031, F032, F212, F248); W245's H1 row and its model hazards |
| Contract | P1's baked OBJ contract ([TASK-W249](TASK-W249-P1-MOTOR-MODELS.md), [TASK-W249-FIX](TASK-W249-FIX.md), ADR-061 section 4 item 4's R271 paragraph) applied to an item; `PackagedBlockAtlasCoverageTest` and its loader rules |
| Parallel batches | W260, W261 and W264 also give Root asset-plan drafts; do not edit central files |
| Record | `docs/work/v1.8.0-root-queue/records/W265.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W265/` |

**Root decision R291 (H1).** `basic_laser_gun` shows upstream's laser gun mesh (`models/item/models/basicLaserGun.obj`
and `.mtl` with `textures/models/basiclasergun.png`) through Forge's `forge:obj` loader, with the mixed-case material
references and the Diff and spec materials rewritten and recorded, the texture in a stitched folder, and display
transforms for the first-person and third-person hands, ground, fixed and GUI views.

- **Where the flat icon belongs.** Find out from upstream which views used the flat `textures/items/basiclasergun.png`
  and which used the mesh. If upstream showed the flat icon in the GUI and the mesh in hand, reproduce that with
  Forge's `forge:separate_transforms` loader and import the flat icon too; otherwise use the mesh everywhere and give
  the flat icon an exclusion row with its reason. Either way, extend `PackagedBlockAtlasCoverageTest` to follow any
  loader you add (its base and per-view models), so the test still fails on an unknown loader.
- **Nothing else changes**: the item's ID, recipe, mining, server authority, use animation, the existing
  `LaserBeamRenderer` beam and its sounds (D5). No new packet or state.

As before: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance validator
with the CI build's arguments; drafts (not edits) for the asset plan, the ledger, `.gitattributes` and governance's
import-verify list. Write the plan from Python with LF line endings; verify on a clean export. A short design note
first.

## Verification

- The new tests (the model contract, every display transform, the atlas walk of any new loader) failing first where
  they can; the client and data unit tests and the whole unit suite; runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator; focused GameTests of the laser gun's batches under the heavy-JVM lock; the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

Sounds (D5), other items, status files and the ledger beyond the drafts.
