# TASK W261: import batch P0, the landing pad texture

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w261` (new; branch `codex/v1.8.0-w261` from the shared head named in your prompt) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`: batch P0 and the `textures/blocks/rocketpad_*.png` row of section 3 |
| Contract | W246's flat-texture batches ([TASK-W246](TASK-W246-FLAT-TEXTURES.md), [TASK-W246-FIX2](TASK-W246-FIX2.md)): byte copies into the stitched `textures/block/` folder, the `_classic` rule, the atlas test |
| Parallel batch | W260 (P4, the fuel tanks) runs at the same time; both give Root drafts for the central files |
| Record | `docs/work/v1.8.0-root-queue/records/W261.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W261/` |

**Root decision R287**, under the owner's delegation:

1. **Single-tile look.** The landing pad shows one upstream tile on its top face: the variant meant for a pad cell
   surrounded by pad on every side, so that a grid of pads reads as one surface. Identify that variant from
   upstream's blockstate or code, and say how. Keep the existing side and bottom faces unless upstream's block uses
   the same tile there. A connected look would need neighbour-aware client models that also work inside assembled
   rocket snapshots; it is not part of v1.8.
2. **The other fifteen variants** move to EXCLUDE in the asset-plan draft, one exact row each or one row with an
   explicit file list, with the reason "connected look not adopted (R287)", rather than being imported unused.
3. **Nothing else changes**: the pad's ID, recipe, collision, landing selection and occupancy rules, and every other
   block. The retained NEW `landing_pad` art is listed with a proposed disposition, never deleted.
4. Write the plan from Python with LF line endings; verify on a clean export.

As before: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance
validator with the CI build's arguments; drafts (not edits) for the asset plan, the ledger and governance's
import-verify list, for Root to apply.

## Verification

- The atlas, client and data unit tests and the whole unit suite; runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

A connected pad look, other blocks, status files and the ledger beyond the drafts.
