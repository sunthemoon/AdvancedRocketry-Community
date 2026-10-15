# TASK W80R2: revision 3 of the offline-passenger release design

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is design
work only: write no production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w80-offline-passenger-design` (branch `codex/v1.8.0-w80-offline-passenger-design`, at `68bf09c7` with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W80-DESIGN.md` (revision 2, `0b7bc153`) |
| Second review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review-2/REVIEW.md`, SHA-256 `6371824378d974c6574e559f3563f65eb8e038552ed0fcff011947b3ab500972` |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review-2/ROOT-DECISIONS.md`, SHA-256 `d061ca88dfb0d3b729e8eee8f984e923615ae6b6ec878e67995ebb21cbdbaedf`; the earlier ones in `W80-review/ROOT-DECISIONS.md` (decision 5 as corrected) still apply |
| Record | Add a "Revision 3" section to `records/W80.md`. Keep the existing text |

## What to do

1. Apply Root's decisions 1 to 8 to the note, finding by finding (N1 to N13, and the open part of R4). Mark the note
   "revision 3" and keep its structure. Use complete sentences (AGENTS.md section 10).
2. In `records/W80.md`, add a table: each finding, where revision 3 answers it, and a one-line summary.
3. You may read compiled Minecraft, Forge and Brigadier classes with `javap` to check a claim; record what you read.
   Run no game, Gradle or GameTest.

## Commands

- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code.
- ADRs, the risk register, status files and the ledger (Root).
