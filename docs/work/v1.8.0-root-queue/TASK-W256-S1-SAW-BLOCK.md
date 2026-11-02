# TASK W256: import batch S1, the saw block model

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w256` (new; branch `codex/v1.8.0-w256` from the shared head named in your prompt, which includes W249) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`: batch S1 (sections 4 and 5; the saw rows of section 3) |
| Contract | [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [TASK-W249-FIX](TASK-W249-FIX.md) (R271, R276, R278), [W249's record](records/W249.md) and [design note](records/W249-design.md), ADR-061 section 4 item 4's R271 paragraph |
| Parallel batch | W255 (P2, the bipropellant motors) runs at the same time; both give Root drafts for the central files, so do not edit them |
| Record | `docs/work/v1.8.0-root-queue/records/W256.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W256/` |

**Root decision R282**, under the owner's delegation:

1. **Scope.** The `saw_blade` block and its block item, from the saw block OBJ, MTL, model texture and assembly
   icon that W245 matched for S1. `iron_saw_blade` keeps its NEW art; `sawbladeiron.png` stays REVIEW and out; the
   cutting machine's formed K2 hull is not touched.
2. **P1's contract**: an ordinary `forge:obj` block model shared by the placed block and its block item (the separate
   item mesh row is not imported; X3 decides it later); textures in the stitched `textures/block/` or `textures/item/`
   folder with the `_classic` rule where needed; the importer records every `map_Kd` and `mtllib` rewrite, including
   the `sawBlade.mtl` case and namespace fix; no runtime fallback; `noOcclusion()` if the mesh does not fill the cube
   (R278), after the same check of every gameplay use and with the same kind of GameTest.
3. **No facing.** The block has no facing property; do not add one or copy the legacy six-facing variants. If the
   mesh only reads correctly with a facing, stop and report.
4. Write the plan from Python with LF line endings; verify on a clean export.

As in W249: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance
validator with the CI build's arguments; drafts (not edits) for the asset plan, the ledger, `build.gradle` exclusions,
`.gitattributes` and governance's import-verify list, for Root to apply. Replaced NEW art is listed with a proposed
disposition, never deleted.

## Verification

- The new tests failing first where they can; the atlas, client and data unit tests and the whole unit suite;
  runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator.
- Focused GameTests of the saw block's and cutting machine's existing batches and any new occlusion test, under the
  heavy-JVM lock; no full GameTest run is needed.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

P2 to P4, P0, H1, U1, the cutting machine's hull, status files and the ledger beyond the drafts.
