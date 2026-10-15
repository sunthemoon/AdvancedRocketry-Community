# TASK W75R: revise the W11b3 design note after its review

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is
design work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w75-w11b3-design` (branch `codex/v1.8.0-w75-w11b3-design`, with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W11B3-DESIGN.md` (revision 1, `918d5e9e`) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W75-review/REVIEW.md`, SHA-256 `352e5b47de81ec34384db580fe5ebbcc68c61c3d9455b4ed8d74ed982cae1d01` |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W75-review/ROOT-DECISIONS.md`, SHA-256 `0b33f5548c9c7f60d38adf9a32f0cb58ac27c88af9fd668753b3894c7df44633` |
| Reservation decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (R1 to R9; cite the hash you read) |
| W11b2 code | Read only through Git at `d178e727` (or a later commit of `codex/v1.8.0-w11b2-rocket-integration` if you cite it); do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration`, where continuation 18 is working |
| Record | Add a "Revision 2" section to `records/W75.md`. Keep the existing text |

## What to do

1. Apply Root's decisions 1 to 6 to the note, finding by finding (M1 to M4, L1 to L12, I1 to I6). Mark the
   note "revision 2" and keep its structure; a changed section may grow, but keep the note readable and use
   complete sentences (AGENTS.md section 10, the review's L3).
2. In `records/W75.md`, add a table: each finding, where the revision answers it (section and line), and a
   one-line summary.
3. Do not change anything the review found correct unless a decision requires it.

## Commands

- No Gradle, GameTest or JVM run. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files
  Git tracks; write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code, and any asset copy.
- ADRs, the risk register, status files and the ledger (Root).
