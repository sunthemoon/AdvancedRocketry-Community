# TASK W258: import batch P3, the nuclear rocket motor model

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w258` (new; branch `codex/v1.8.0-w258` from the shared head named in your prompt) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`: batch P3, sections 4 and 5 |
| Contract | [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [TASK-W249-FIX](TASK-W249-FIX.md) (R271, R276, R278), [W249's record](records/W249.md) and [design note](records/W249-design.md), ADR-061 section 4 item 4's R271 paragraph |
| Parallel batches | W255 (P2) and W256 (S1) are not merged yet; all three give Root drafts for the central files, so do not edit them |
| Record | `docs/work/v1.8.0-root-queue/records/W258.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W258/` |

**Root decision R284**, under the owner's delegation:

1. **Scope.** `nuclear_rocket_motor` and its block item, from the nuclear engine OBJ, MTL and texture that W245
   matched for P3. Its texture is the MTL's real image, `nuclearengine`, not the stale `nuclearcombustion` name in
   the legacy blockstate. The nuclear core, tanks and every server rule (core adjacency, nozzle orientation, thrust)
   stay as they are.
2. **P1's contract**, with one addition: `RocketNuclearNozzleBlock` already has a `FACING` property, so the blockstate
   rotates the one baked model for all six facings, transcribed from upstream's blockstate and pinned by a test for
   each facing (and the item display transforms). No new property, state, block entity, packet or renderer.
   `noOcclusion()` under R278 if the mesh does not fill the cube, after the same check of every gameplay use and with
   the same kind of GameTest; the stitched `textures/block/` folder with the `_classic` rule; recorded `map_Kd`
   rewrites; no runtime fallback.
3. Write the plan from Python with LF line endings; verify on a clean export.

As in W249: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance
validator with the CI build's arguments; drafts (not edits) for the asset plan, the ledger, `build.gradle` exclusions,
`.gitattributes` and governance's import-verify list, for Root to apply. Replaced NEW art is listed with a proposed
disposition, never deleted.

## Verification

- The new tests failing first where they can; the atlas, motor model, client and data unit tests and the whole unit
  suite; runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator.
- Focused GameTests of the nuclear propulsion batches and any new occlusion test, under the heavy-JVM lock; no full
  GameTest run is needed.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

P4, P0, H1, U1, the nuclear core, fuel or bay behaviour, status files and the ledger beyond the drafts.
