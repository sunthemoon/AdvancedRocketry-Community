# TASK W265-fix: the laser gun keeps upstream's flat icon (R293)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w265` (existing; branch `codex/v1.8.0-w265` at `075bebd21`) |
| Task it answers | [TASK-W265](TASK-W265-H1-LASER-GUN.md), Root decisions R291 and R293 |
| Record | Append a "W265-fix (R293)" section to `docs/work/v1.8.0-root-queue/records/W265.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W265-fix/` |

W265 found, and Root confirmed, that upstream's laser gun item model at the pin
(`models/item/basiclasergun.json`) is `item/generated` with `layer0: advancedrocketry:items/basicLaserGun` and four
hand display transforms; its `"model": "model/basicLaserGun"` key is not a field the 1.12 item model format reads, and
no upstream code registers an OBJ renderer for the gun. In 1.12 the laser gun therefore showed its flat icon in every
view, and the OBJ, MTL and model texture were never used. R291's "otherwise, the mesh everywhere" did not anticipate this
case and would give the gun a look it never had.

**Root decision R293**, under the owner's delegation, replaces R291's choice for H1:

1. **The flat icon.** Import `textures/items/basiclasergun.png` (byte copy) into the stitched `textures/item/` folder,
   with the `_classic` rule and its recorded reason if a name is taken, and make `basic_laser_gun`'s item model
   `item/generated` (or `item/handheld` if that is what the transforms need) with that `layer0` and upstream's four
   hand display transforms transcribed; the other views follow vanilla's defaults for a generated item.
2. **The mesh is not imported.** Take `basiclasergun.obj`, `.mtl` and the model texture out of the plan, the provenance
   records and the tree; regenerate the pending records (unsealed and unmerged). Their asset-plan rows go to EXCLUDE in
   the draft with the reason "upstream never rendered the laser gun mesh; the item used its flat icon (R293)". Keep
   W265's atlas fixtures only where they still test something real.
3. The retained NEW 16-pixel icon is listed with a proposed disposition, never deleted. The item's ID, recipe, mining,
   authority, use animation, beam and sounds do not change.
4. Fresh drafts for Root (asset plan, ledger note, governance pair, and whether `.gitattributes` still needs anything)
   based on your branch's base, noting that other batches change the same files in parallel.

## Verification

- The model test updated to the flat icon and its transforms, failing first on the mesh binding; the client and data
  unit tests and the whole unit suite; runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator; the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

Sounds (D5), other items, status files and the ledger beyond the drafts.
