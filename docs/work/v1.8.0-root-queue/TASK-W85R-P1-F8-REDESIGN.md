# TASK W85R: redesign P1-F8 after its review (minimal option first)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is design
work only: write no production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w85-p1-f8-design` (branch `codex/v1.8.0-w85-p1-f8-design`, with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W85-DESIGN.md` (revision 1, `08474506`) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/REVIEW.md`, SHA-256 `4dbb7f9ee948f20f7dce167534d1a583242f2adba3a329e20ffa14aa3ba1feb8` (verdict REDESIGN) |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/ROOT-DECISIONS.md`, SHA-256 `f808a2460e58969509e571f498e553131ba0aedf947d15a35c115269927f7351` |
| W11b2 code | Read only through Git at a commit you name; do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Record | Add a "Revision 2" section to `records/W85.md`. Keep the existing text |

## What to do

1. Follow Root's direction: evaluate the minimal option first, compare it briefly with a full protocol that answers
   the review's R1 to R6, and recommend one. Replace revision 1's restart table; do not patch it.
2. Write the recommended design as revision 2 of the note, with its crash points and what a restart finds, its tests,
   and the proposed R-035 and ADR-065 text. Say exactly which part of ADR-065's "Open before W11b3 or any admission: F8"
   it closes and which part becomes a recorded residual.
3. In `records/W85.md`, add a table: each finding R1 to R6, and how revision 2 answers it, avoids it, or records it.
4. You may read compiled Minecraft and Forge classes with `javap`; record what you read. Run no game, Gradle or GameTest.

## Commands

- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git tracks
  (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; F3 and N2.
- ADRs, the risk register, status files and the ledger (Root).
