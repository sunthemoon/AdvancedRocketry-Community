# TASK W249-review: independent read-only review of import batch P1 (the mono motor models)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w249` at `4f120f1af`: W249's commits `945d560c5` (design note) to `1d551eb53` (record) on top of `d6e4b0fc8` (W246's `17bdece9b` with the shared head), Root's `74f93589f` applying W249's central drafts, and Root's merge of the shared head. Review `git diff d6e4b0fc8 4f120f1af` minus the shared-head merge. Review worktree `D:/GitHub/arce-v180-w249-review`, a detached checkout of `4f120f1af` that Root creates |
| Task it answers | [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md), Root decision R271 |
| Record | `docs/work/v1.8.0-root-queue/records/W249.md` and `records/W249-design.md` on the subject branch; evidence and drafts in `D:/GitHub/ARCE-Task-Evidence/root-queue/W249/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Imports.** The six files are byte copies of history-clear upstream files (OBJ and MTL unchanged, the two PNGs
   byte-identical), the only rewrites are the two `map_Kd` references, recorded in the pending provenance record, and
   import verify passes. The asset plan's six exact rows are the only first-match change (rerun a first-match audit).
2. **The baked route.** The `forge:obj` model JSONs load in 1.20.1 Forge 47.4.10 as written (check the loader's
   expected keys against the Forge sources or bytecode: `model`, `flip_v`, `emissive_ambient`, materials and
   textures); the `map_Kd` sprites resolve in the block atlas; the transform matches upstream's blockstate; the block
   item inherits the block model with sensible display transforms; nothing else (blockstates, IDs, recipes, the
   bipropellant, nuclear and tank models) changes.
3. **The atlas test.** It follows each `forge:obj` model to its OBJ, `mtllib` and every material map, and fails on
   unknown loaders and `overrides` (W246-R6); its fixtures fail on the old checker.
4. **The resource auditor** (`scripts/validate_v090_resources.py`): the OBJ handling distinguishes model metadata from
   texture slots without weakening any existing check.
5. **Root's draft application** (`74f93589f`): the build exclusion, `.gitattributes`, the ledger notes and the ADR-061
   paragraph are correct and minimal.
6. **The sharded branch run** on `4f120f1af` once it finishes, and **nothing weakened**.

## Rerun

In your review worktree only: the client and data unit tests, `V180MotorModelDataTest` and the atlas tests, the
importer, content-ledger and resource Python tests, import verify, the provenance validator with the CI build's
arguments (build the JAR first) and the repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W249-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
