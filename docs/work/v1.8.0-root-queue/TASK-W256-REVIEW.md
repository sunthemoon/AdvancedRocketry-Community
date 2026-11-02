# TASK W256-review: independent read-only review of import batch S1 (the saw block model)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w256` at `d231a0e0a`: W256's commits `cc091df1a` (design note) to `09a2dd2f2` (record) on top of `54d78cb82`, Root's commit applying W256's draft patch, Root's merge of the shared head `b0788eb65`, and Root's merge of the shared head `931977759` with W255, which resolved the asset plan, `.gitattributes` and governance conflicts. Review worktree `D:/GitHub/arce-v180-w256-review`, a detached checkout of `d231a0e0a` that Root creates |
| Task it answers | [TASK-W256](TASK-W256-S1-SAW-BLOCK.md), Root decision R282; P1's contract in [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [TASK-W249-FIX](TASK-W249-FIX.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W256.md` and its design note on the subject branch; evidence, `ROOT-DRAFTS.md` and `root-central-drafts.patch` in `D:/GitHub/ARCE-Task-Evidence/root-queue/W256/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W256-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Imports.** The four files are byte copies of history-clear upstream files apart from the recorded `mtllib` and
   `map_Kd` rewrites (including the `sawBlade.mtl` case fix); the plan is LF and import verify passes in a fresh clone.
2. **The asset plan.** The narrowing of `textures/items/sawblade*.png` to `sawbladeassembly.png`: no other legacy file
   changes first match unexpectedly (rerun a first-match audit), `sawbladeiron.png` stays REVIEW, and Root's merge
   with W255 kept every W255 row and moved W256's three edits to the right rows.
3. **P1's contract.** The saw block model loads as an ordinary `forge:obj` block model, the block item shares it, no
   facing was added, `iron_saw_blade` and the cutting machine's formed hull are unchanged, `noOcclusion()` (R278)
   leaves every gameplay use unchanged, and its GameTest failed first.
4. **Root's other draft application**: the ledger note, `.gitattributes` and the governance line.
5. **The sharded branch run** on `d231a0e0a` once it finishes, and **nothing weakened**.

## Rerun

In a fresh clone or your review worktree only: import verify for every v1.8 record, the importer, content-ledger,
provenance and resource Python tests, the atlas, client and data unit tests, the whole unit suite and the repository
validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W256-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
