# TASK W85-review: independent read-only review of the P1-F8 design note

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W85-DESIGN.md` and `records/W85.md` on branch `codex/v1.8.0-w85-p1-f8-design`, commit `08474506` |
| Assignment the note answers | [TASK-W85](TASK-W85-P1-F8-DESIGN.md), items 1 to 8 |
| Checkout | `D:/GitHub/arce-v180-w85-review`, detached at `08474506`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

1. **Completeness** against TASK-W85's eight items.
2. **Accuracy** of each code citation, including the claim that W11b2's governed (record-3) rockets never reach this
   path (read W11b2 only through Git at the commit the note names).
3. **Crash safety:** walk every crash point of the new order. No rocket may be recreated over restored blocks, no
   rocket or resource may be lost or duplicated, and the landed reservation is released exactly once. Check the
   restart decision table, including the offline-repair hold.
4. **The record schema 4 choice:** older jars rejecting it, what that does to a whole world's startup (as W80's review
   found for a journal root bump), and whether journal root 3, W44's outbox and W11b2's record 3 stay untouched.
5. **The save-wait step** (the note's open question 1) and the residual about entity-chunk saves: are the reasoning and
   the proposed risk text sound? Check the save-ordering assumption the note says is unverified, from the 1.20.1
   bytecode if you can.
6. **Safety** (AGENTS.md section 3.3): bounds and schema versions, and a risk proposal for any hold or refusal.
7. **Tests and feasibility**, and **readability** (section 10).

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server. Reading compiled classes with `javap` is allowed.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID, a severity, the section and line (and code file and line where relevant), the problem,
  and a suggested change.
- A table of TASK-W85's items 1 to 8: answered, partly, or not.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
