# TASK W264: import batch U1, the suit workstation's empty armor-slot icons

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w264` (new; branch `codex/v1.8.0-w264` from the shared head named in your prompt) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, SHA-256 `861320967dde0492569d8e31f2874a0d7288c603af8d2bca5720c720fb97925a`, section 6 (U1) and section 7 (F201 to F204); W245's U1 row |
| Contract | W246's flat-texture rule (byte copies into the stitched `textures/item/` or `textures/block/` folder, `_classic` where a name is taken, the atlas test) |
| Parallel batches | W260, W261 and W265 also give Root asset-plan drafts; do not edit central files |
| Record | `docs/work/v1.8.0-root-queue/records/W264.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W264/` |

**Root decision R291 (U1).** Import `textures/gui/space_helmet.png`, `space_chestplate.png`, `space_leggings.png` and
`space_boots.png` and give them a real consumer: the suit workstation's four armor slots show the matching icon while
empty, the way vanilla's inventory shows its empty armor-slot icons (`Slot.getNoItemIcon()` with a block-atlas sprite,
so the files go in a stitched folder). Occupied slots, the screen's labels and module rendering, GUI scale, input and
the server-selected slot rules do not change. No new packet, state or authority.

As before: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance validator
with the CI build's arguments; drafts (not edits) for the asset plan, the ledger and governance's import-verify list.
Write the plan from Python with LF line endings; verify on a clean export.

## Verification

- A unit or GameTest that each armor slot reports its icon and that a filled slot does not, failing first; the atlas,
  client and data unit tests and the whole unit suite; runData twice with identical output if any generated file changes.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator; the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

Other GUI art (X2), the laser gun (H1), status files and the ledger beyond the drafts.
