# TASK W85R2: revision 3 of the P1-F8 design

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is design
work only: write no production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w85-p1-f8-design` (branch `codex/v1.8.0-w85-p1-f8-design`, at `6ee8dc5b` with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W85-DESIGN.md` (revision 2) |
| Second review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review-2/REVIEW.md`, SHA-256 `63c9477581ca2a14b16f973f2de3357325ce64e1952e3b559194622b250e0ffa` |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review-2/ROOT-DECISIONS.md`, SHA-256 `1f8957301ff174284979ed67b70e696261de7911755125c1d875a1b08cec1b27` |
| Record | Add a "Revision 3" section to `records/W85.md`. Keep the existing text |

## What to do

1. Apply Root's decisions 1 to 4 to the note, finding by finding (N1 to N6). Mark the note "revision 3" and keep its
   structure. Use complete sentences (AGENTS.md section 10).
2. In `records/W85.md`, add a table: each finding, where revision 3 answers it, and a one-line summary.
3. You may read compiled Minecraft and Forge classes with `javap`; record what you read. Run no game, Gradle or GameTest.

## Commands

- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git tracks
  (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; F3 and N2.
- ADRs, the risk register, status files and the ledger (Root).
