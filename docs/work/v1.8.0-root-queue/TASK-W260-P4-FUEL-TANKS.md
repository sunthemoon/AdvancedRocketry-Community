# TASK W260: import batch P4, the fuel tank model

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w260` (new; branch `codex/v1.8.0-w260` from the shared head named in your prompt, which includes W255, W256 and W258) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`: batch P4, sections 4 and 5, and the tank hazards in "Concrete model hazards" |
| Contract | [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md), [TASK-W249-FIX](TASK-W249-FIX.md) (R271, R276, R278) and the batches built on them: [TASK-W255](TASK-W255-P2-BIPROPELLANT-MOTORS.md), [TASK-W258](TASK-W258-P3-NUCLEAR-MOTOR.md); `MonoMotorOcclusionGameTests.assertNonOccludingColumns` |
| Findings to fold in | W258-R1 to R3 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W258-review/REVIEW.md` (SHA-256 `79932f70b943d9d82e851d1457a2168e97a3e37701890f40b8cd656158cf5f3d`) |
| Record | `docs/work/v1.8.0-root-queue/records/W260.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W260/` |

**Root decision R286**, under the owner's delegation:

1. **Standalone look.** One static middle-tank mesh for the three fuel-tank IDs (`rocket_fuel_tank`,
   `bipropellant_fuel_tank`, `oxidizer_fuel_tank`) and their block items, from the middle tank OBJ, MTL and `tank.png`
   that W245 matched. The top and bottom tank meshes, the `endtank` materials and every other file stay out: a
   connected top, middle and bottom look would need state that ordinary tanks do not have and that assembled
   snapshots do not carry, so it is not part of v1.8's P4. IDs, recipes, capacities and fuel roles do not change.
2. **P1's contract**: ordinary `forge:obj` block models, upstream's transform, the stitched `textures/block/` folder
   with the `_classic` rule and its recorded reason, recorded `mtllib` and `map_Kd` rewrites (including the
   mixed-case material names W245 lists), no runtime fallback, and `noOcclusion()` under R278 if the mesh does not
   fill the cube, after the same check of every gameplay use and covered through `assertNonOccludingColumns`.
3. **W258's findings.** Trim `block:nuclearrocketmotor` from the broad `models/block/models/*` row and fix its reason
   (R2); keep `tests/test_validate_v180_content_ledger.py`'s batch-order fixture exercising the rule without relying
   on that stale owner, for example by also setting the nuclear model rows to IMPORT and C17a in the fixture (R1);
   keep the occlusion fixtures within the test space that the `empty` template clears, or document why a cell
   beyond it is safe (R3).
4. Write the plan from Python with LF line endings; verify on a clean export.

As before: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance
validator with the CI build's arguments; drafts (not edits) for the asset plan, the ledger, `build.gradle` exclusions,
`.gitattributes` and governance's import-verify list, for Root to apply. Replaced NEW art is listed with a proposed
disposition, never deleted.

## Verification

- The new tests failing first where they can; the atlas, model, client and data unit tests and the whole unit suite;
  runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator.
- Focused GameTests of the tanks' existing batches and the occlusion batches, under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

Connected tank presentation, the top and bottom meshes, X3's exclusions, fuel or bay behaviour, status files and the
ledger beyond the drafts.
