# TASK W255: import batch P2, the bipropellant rocket motor models

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w255` (new; branch `codex/v1.8.0-w255` from the shared head named in your prompt, which includes W249) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`: batch P2, sections 4 and 5 |
| Contract | [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [TASK-W249-FIX](TASK-W249-FIX.md) (R271, R276, R278), [W249's record](records/W249.md) and [design note](records/W249-design.md), and ADR-061 section 4 item 4's R271 paragraph |
| Record | `docs/work/v1.8.0-root-queue/records/W255.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W255/` |

**Root decision R281**, under the owner's delegation:

1. **Scope.** `bipropellant_rocket_motor` and `advanced_bipropellant_rocket_motor` and their block items, from the
   bipropellant and advanced bipropellant OBJ, MTL and texture triples that W245 matched (P2). The fuel and oxidizer
   roles and every ID, recipe and server rule stay as they are. Nuclear, tank, saw, laser gun and every other model
   stay out.
2. **Reuse P1's contract exactly**: ordinary `forge:obj` block models shared by placed blocks, block items and
   assembled rockets; the transform transcribed from upstream's blockstate; textures in `textures/block/`, with the
   `_classic` naming rule and its reason as W249 recorded it; `map_Kd` rewrites recorded by the importer; no runtime
   fallback; `noOcclusion()` for the two motors if their meshes, like P1's, do not fill the cube (R278), after the same
   check of every gameplay use and with the same kind of GameTest. If a mesh needs something P1's contract does not
   cover, stop and report.
3. **W249-R8.** Trim the bipropellant units from the broad asset-plan rows that no remaining file of theirs matches,
   in the asset-plan draft.
4. Write the plan from Python with LF line endings (the importer now refuses CR bytes); verify on a clean export.

As in W249: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance
validator with the CI build's arguments; drafts (not edits) for the asset plan, the ledger, `build.gradle` exclusions,
`.gitattributes` and governance's import-verify list, for Root to apply. Replaced NEW art is listed with a proposed
disposition, never deleted.

## Verification

- The new tests failing first where they can; the atlas, motor model, client and data unit tests and the whole unit
  suite; runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator.
- Focused GameTests for the bipropellant motors' existing batches and any new occlusion test, under the heavy-JVM
  lock; no full GameTest run is needed.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

P3 and P4, S1, P0, H1, U1, fuel or bay behaviour, sounds (D5), status files and the ledger beyond the drafts.
