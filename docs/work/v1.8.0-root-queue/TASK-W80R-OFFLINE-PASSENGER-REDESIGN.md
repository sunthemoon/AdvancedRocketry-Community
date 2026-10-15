# TASK W80R: redesign the offline-passenger release after its review

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is design
work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w80-offline-passenger-design` (branch `codex/v1.8.0-w80-offline-passenger-design`, with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W80-DESIGN.md` (revision 1, `cc30fcbd`) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/REVIEW.md`, SHA-256 `8f03b9057e52fa2eb61e190145a45f21d15e83e472505dd4092539de465d8961` (verdict REDESIGN) |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/ROOT-DECISIONS.md`, SHA-256 `a987b12c2e56a209454b57ac23d2c64a047885766640b102872189dbebfb88b2` |
| W11b2 code | Read only through Git at a commit of `codex/v1.8.0-w11b2-rocket-integration` that you name; do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Record | Add a "Revision 2" section to `records/W80.md`. Keep the existing text |

## What to do

1. Redesign the note under Root's decisions 1 to 5, answering every finding (R1 to R8). Decision 1 asks you to
   evaluate the separate rejoin file first; state your choice and the reason. Mark the note "revision 2".
2. Keep the parts the review found sound (the bounded journal approach, no timer in v1.8) unless a decision changes
   them. Use complete sentences (AGENTS.md section 10).
3. In `records/W80.md`, add a table: each finding, where the revision answers it, and a one-line summary.
4. You may read compiled Minecraft and Forge classes from the Gradle cache with `javap` to check a claim, as the
   review did; record what you read. Run no game, Gradle or GameTest.

## Commands

- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code.
- ADRs, the risk register, status files and the ledger (Root).
