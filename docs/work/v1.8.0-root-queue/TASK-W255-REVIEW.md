# TASK W255-review: independent read-only review of import batch P2 (the bipropellant motor models)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w255` at `4d096a7f5`: W255's commits `62387aa80` (design note) to `52c39135b` (record) on top of `13c4b1eae`, Root's commit applying W255's central drafts, and Root's merge of the shared head `b0788eb65`. Review worktree `D:/GitHub/arce-v180-w255-review`, a detached checkout of `4d096a7f5` that Root creates |
| Task it answers | [TASK-W255](TASK-W255-P2-BIPROPELLANT-MOTORS.md), Root decision R281; P1's contract in [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md), [TASK-W249-FIX](TASK-W249-FIX.md) and the W249 review and recheck in `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-review/` and `W249-recheck/` |
| Record | `docs/work/v1.8.0-root-queue/records/W255.md` and `records/W255-design.md` on the subject branch; evidence and drafts in `D:/GitHub/ARCE-Task-Evidence/root-queue/W255/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W255-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Imports.** The six files are byte copies of history-clear upstream files apart from the recorded `map_Kd`
   rewrites; the plan is LF and import verify passes in a fresh clone; the asset plan's changes are exactly W255's
   rows and the owner trims of W249-R8 (rerun a first-match audit).
2. **P1's contract.** The two models load as ordinary `forge:obj` block models in Forge 47.4.10, the transform matches
   upstream's blockstates, the block items inherit them, the textures sit in the stitched folder with the `_classic`
   rule, and nothing else (IDs, recipes, fuel and oxidizer roles, server rules, other models) changes.
3. **`noOcclusion()`** for the two bipropellant motors (R278): check every gameplay use yourself, and that the shared
   `chemicalMotor` helper and the occlusion GameTest cover all four chemical motors.
4. **Root's draft application**: the ledger notes, `.gitattributes` and the governance import-verify line.
5. **The sharded branch run** on `4d096a7f5` once it finishes, and **nothing weakened**.

## Rerun

In a fresh clone or your review worktree only: import verify for every v1.8 record, the importer, content-ledger,
provenance and resource Python tests, the atlas, motor, client and data unit tests, the whole unit suite and the
repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W255-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
