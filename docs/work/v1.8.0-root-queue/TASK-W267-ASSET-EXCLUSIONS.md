# TASK W267: close the remaining IMPORT rows as exclusions (X1, X2, X3, J0, U1 and the furnace's active face)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w267` (new; branch `codex/v1.8.0-w267` from the shared head named in your prompt, which includes W260 and W261) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, SHA-256 `861320967dde0492569d8e31f2874a0d7288c603af8d2bca5720c720fb97925a`: section 6 (X1, X2, X3, J0, U1 and the T1 residual), section 4 (owners and consumers), section 5 (retained NEW art) and section 7 (exact files and hashes) |
| Decisions | R291 (exclusions in v1.8), R292 (U1's four icons excluded: the workstation has one armor slot, see `records/W264.md`), R295 below |
| Parallel batch | W265-fix (the laser gun) also changes the asset plan; it excludes its own mesh rows. Do not touch the laser gun's rows |
| Record | `docs/work/v1.8.0-root-queue/records/W267.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W267/` |

**Root decision R295**, under the owner's delegation. Each of these IMPORT files moves to EXCLUDE, one exact row per
file (or per exact pattern), with a reason that names what replaces it in the mod:

- **X1** (`textures/blocks/combustion.png`, `textures/blocks/smallconnector.png`): replaced by the motors' model
  texture (P1) and the solar array's K3 material and NEW panel and plug models.
- **X2** (the fourteen GUI bitmaps of W263 section 6): the screens and HUD draw these elements in code
  (`SuitHudClient`, `LifeSupportHud`, `StationControllerScreen`, `ClassicControllerScreen` and the native widgets).
  Only these fourteen; the REVIEW button and warning files and earlier EXCLUDE sheets stay as they are.
- **X3** (the eighteen item meshes and the tank top, bottom and end files): the block items reuse the block meshes
  (P1, P4, S1) and the tanks are standalone (R286); `endtank.mtl` has no matching OBJ. Keep the REVIEW LibVulpes
  `motor.*` files separate.
- **J0** (`models/jetpack.obj`, `textures/models/jetpack.png`): the NEW worn jetpack (`SuitComponentLayer` geometry
  and `suit_components.png`) replaces it; the legacy OBJ has faces without material statements.
- **U1** (`textures/gui/space_*.png`): the workstation has one labelled armor slot and one module slot (R292).
- **The furnace's active face** (`textures/blocks/blastbrickfront_active.png`): the arc furnace keeps its static idle
  front (W246); no active blockstate is added for art.

For each file give the evidence the exclusion needs: a scan showing no model, MTL, Java, JSON or import plan
references it, and the replacement that works today. Trim owners that no remaining file matches. Then list the 35
unreferenced NEW textures from W263 section 5 with a proposed disposition each (keep as live art, keep as history
pending human review); delete none. No REVIEW row, allowlist entry, ADR decision or import changes.

Give Root the asset-plan change as a draft (based on your branch's base), the evidence as a file under
`docs/work/v1.8.0-w267-asset-exclusions/`, and the ledger notes if any unit's note should change.

## Verification

- The content-ledger validator, a first-match audit showing exactly these files change and no rule is left unused,
  the importer, ledger and resource Python tests, the asset coverage check, and the repository validator, with the
  draft applied in a scratch copy.

Commit the evidence and record on the branch; do not push. Root applies the draft, pushes for a sharded run, and a
fresh session of the other engine reviews.

## Not in this task

The 95 REVIEW files (origin findings or the owner's D7 review), the laser gun, new imports, status files and the ledger
beyond the notes.
