# TASK W246-review: independent read-only review of the flat texture batches

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w246` at `e495440bb`: W246's commits `81f6cff20` (import) to `9abea95ad` (record), Root's `8be926991` applying W246's whole-file drafts, W246-fix's `4ec6cef2a` to `cee414db5` (the superseding provenance record, R267), and Root's merges of the shared head (`001a7cd3c`, `63620fcff`). Review worktree `D:/GitHub/arce-v180-w246-review`, a detached checkout of `e495440bb` |
| Task it answers | [TASK-W246](TASK-W246-FLAT-TEXTURES.md) and [TASK-W246-FIX](TASK-W246-FIX.md), Root decisions R262 and R267 |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md` (batches T1 to T3) |
| Record | `docs/work/v1.8.0-root-queue/records/W246.md` on the subject branch; drafts, audits and logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246/` and `W246-fix/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Imports.** The sixteen textures are byte copies of history-clear upstream files, the provenance stays
   PENDING_HUMAN_REVIEW, import verify passes, `sawbladeiron.png` and the arc furnace's active face stay out, and the
   crucible's REVIEW and EXCLUDE faces stay out.
2. **Bindings.** Each provider change only swaps a texture reference: IDs, recipes, model parents, geometry, the
   airlock's half, open and hinge states and the torch's standing and wall forms are unchanged. runData output is
   deterministic and matches the committed generated files.
3. **The applied asset plan.** W246 split the blast-brick wildcard and renumbered every later order by two. Confirm
   that first-match handling is unchanged for every other legacy asset (rerun W246's audit or your own), and that the
   ledger rows changed only in their notes.
4. **Replaced NEW art.** The sixteen dispositions in `replaced-art.md` are true (which files are still referenced, the
   crucible's five faces), and nothing tracked was deleted.
5. **The superseding provenance record** names exactly the changed pinned targets with their new hashes, leaves the
   earlier records untouched, and makes the provenance validator pass.
6. **The sharded branch run** on `e495440bb` once it finishes, and **nothing weakened**.

## Rerun

In your review worktree only: the importer, content-ledger and provenance Python tests, import verify, the provenance
validator with the CI build's arguments, the data and client unit tests, and both validators. Do not run GameTests,
servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W246-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
