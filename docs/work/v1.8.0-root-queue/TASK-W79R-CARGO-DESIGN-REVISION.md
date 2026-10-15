# TASK W79R: revise the cargo loader design note after its review

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is design
work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w79-cargo-design` (branch `codex/v1.8.0-w79-cargo-design`, with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W11CD-DESIGN.md` (revision 1, `51338beb`) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W79-review/REVIEW.md`, SHA-256 `fcf3d7eccddcd9e392066d1d2dedd515ea955994bb3f9957ae37627e8829aafa` |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W79-review/ROOT-DECISIONS.md`, SHA-256 `1e0265016e277a1706966f6cf8ae37eacc40d730b7568edcae9633a3d7547e17` |
| W11b2 code | Read only through Git at `646bf9ee` (or a later commit of `codex/v1.8.0-w11b2-rocket-integration` if you name it); do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Record | Add a "Revision 2" section to `records/W79.md`. Keep the existing text |

## What to do

1. Apply Root's decisions 1 to 7 to the note, finding by finding (H1, M1 to M4, L1, L2, I1). Mark the note
   "revision 2" and keep its structure. Use complete sentences (AGENTS.md section 10).
2. In `records/W79.md`, add a table: each finding, where the revision answers it (section and line), and a
   one-line summary. Correct the baseline explanation the review's M1 names.
3. Do not change anything the review found correct unless a decision requires it.

## Commands

- No Gradle, GameTest or JVM run. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code, and any asset copy.
- ADRs, the risk register, status files and the ledger (Root).
