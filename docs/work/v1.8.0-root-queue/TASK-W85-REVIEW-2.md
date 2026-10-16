# TASK W85-review-2: independent read-only review of the revised P1-F8 design (minimal option)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W85-DESIGN.md`, revision 2, and `records/W85.md` on branch `codex/v1.8.0-w85-p1-f8-design`, commit `6ee8dc5b` |
| First review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/REVIEW.md`, SHA-256 `4dbb7f9ee948f20f7dce167534d1a583242f2adba3a329e20ffa14aa3ba1feb8` (REDESIGN; R1 to R6) |
| Root's direction | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/ROOT-DECISIONS.md`, SHA-256 `f808a2460e58969509e571f498e553131ba0aedf947d15a35c115269927f7351` |
| Checkout | `D:/GitHub/arce-v180-w85-review-2`, detached at `6ee8dc5b`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review-2/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

1. **The minimal option is safe for what it claims.** The order stays: T's checked removal is acknowledged before any
   block is restored. Check the no-change pre-checks, the "disassembly pending" status, the automatic restoration after
   acknowledgement, the session-only pending entry that keeps T's area and journal slot reserved, and the write-back
   of the identical T after a clean failure, rollback or slow journal. Can any path recreate a rocket over restored
   blocks, duplicate or lose a rocket or resource, or write T back after a block was restored?
2. **The two corrections to Root's direction** (the named-pad claim is already released at landing commit; the
   reservation can be re-established by writing T back): are they right, at the cited lines?
3. **The residuals** (a partly failed rollback, an unwritable write-back, a crash between removal and write-back, a
   crash after removal losing a never-saved entity): are they stated exactly, with scope, duration, log volume and
   recovery in the proposed R-035 text, and is anything missing?
4. **The claim of what it closes** in ADR-065's "Open before W11b3 or any admission: F8", and the recommendation that
   W83 become an admission prerequisite.
5. **Each first-review finding R1 to R6:** answered, avoided by the minimal option, or recorded as a residual?
6. **No saved-format change** is claimed: confirm no record, key, schema or root changes.
7. **Tests and readability.**

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server. Reading compiled classes with `javap` is allowed.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- A table of R1 to R6: answered, avoided, recorded, or open.
- New findings, each with an ID, a severity, the section and line, the problem, and a suggested change.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
