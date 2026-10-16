# TASK W86R: revision 2 of the P1-F3 and N2 design

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is design
work only: write no production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w86-p1-f3-design` (branch `codex/v1.8.0-w86-p1-f3-design`, at `b989d31e` with the shared branch merged in by Root) |
| Note to revise | `docs/work/v1.8.0-root-queue/records/W86-DESIGN.md` (revision 1) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W86-review/REVIEW.md`, SHA-256 `e03efbf9b26a32001e5487fb53f44dd185233c64f5bf10c5d373336ed141bb8a` |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W86-review/ROOT-DECISIONS.md`, SHA-256 `c1ba7eb18e1de9600a4e6173c1c9a44ac1b0b874b3b2f0fca18a2b010166a0dd` |
| W11b2 code | As revision 1: read it only through Git at `a8d81d24`; do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Record | Add a "Revision 2" section to `records/W86.md`. Keep the existing text |

## What to do

1. Apply Root's decisions 1 to 8 to the note, finding by finding (W86R-1 to W86R-16). Mark the note "revision 2" and
   keep its structure. Use complete sentences (AGENTS.md section 10). If the note grows past five pages, move the
   tables of sites, bodies and changed tests into an appendix rather than cutting them.
2. Do not change the central rule (section 2.1) unless a finding requires it; if it does, say so first in the record.
3. In `records/W86.md`, add a table: each finding, where revision 2 answers it, and a one-line summary.
4. You may read compiled Minecraft and Forge classes with `javap`; record what you read. Run no game, Gradle or GameTest.

## Commands

- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git tracks
  (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; P1-F8's implementation; W11b3's other leaves.
- ADRs, the risk register, status files and the ledger (Root).
