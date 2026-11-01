# TASK W236-fix: the K3 review's orientation and ledger corrections

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w236` (W236's; continue on its branch) |
| Branch | `codex/v1.8.0-w236`, now at `2e90a59b2` (pushed; its sharded branch run 41259999642 passed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W236.md`: append "R248 review corrections"; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W236/` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W236-review/REVIEW.md` (SHA-256 `bf9e43b7…74cb51c6`): APPROVE-WITH-CHANGES; W236-R1 Medium, W236-R2 and W236-R3 Low; the reviewer's derivations are beside it (`placement-affine.json`, `geometry-detail.json`, `suggested-fixture-check.json`, `tools/legacy_vs_new_placement.py`) |

**Root decision R248.** Apply the review's corrections. They change no server authority, save or network behaviour.

1. **W236-R1: the solar array.** Take the review's first option: `mirror_x: true`, `model_y_rotation_degrees: 270`,
   `offset_north: [0, 0, 1]`. That is the analyser and beacon convention, with the same model bounds, placed boxes and
   `render_bounds`. Update `ObjModelFixture.SOLAR_ARRAY` and `src/test/resources/obj-model-fixtures/solar_array.json`
   together.
2. **W236-R2: the observatory.** Set `model_y_rotation_degrees: 90` and keep the mirror and the offset.
3. **The orientation test.** Add a unit assertion that compares each K3 fixture, per facing, with the legacy
   transform transcribed from the upstream renderers at the pinned commit, as the reviewer's
   `legacy_vs_new_placement.py` does. It pins orientation, not only containment. It must fail first on `2e90a59b2` for
   the solar array and the observatory, and pass for the analyser and the beacon.
4. **W236-R3: the ledger row.** The content ledger is Root's file. Put the exact replacement in
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W236/W236-fix-drafts.md`, restoring "Forge parse/bake and V1 pending".

## Verification

- The client model, fixture and projection unit tests, and the whole unit suite.
- The repository validator.

Root pushes; the sharded branch run qualifies the result.

## Not in this task

Other machines, the K2 fixtures, renderers beyond these two fixtures, status files and the ledger itself.
