# TASK W90-review: independent read-only review of the governed flight readiness design note

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents.

| Item | Value |
|---|---|
| Subject | [records/W90-DESIGN.md](records/W90-DESIGN.md), revision 1, and [records/W90.md](records/W90.md), merged into the shared branch at `af4a7351` (the author's commits are `f2e97d7f` and `ccc21a2f`) |
| Task it answers | [TASK-W90-READINESS-DESIGN](TASK-W90-READINESS-DESIGN.md) |
| W11b2 code | The note cites W11b2 at `379525ea`. Read it only through Git (`git show 379525ea:<path>`, `git grep <pattern> 379525ea`). Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` or `D:/GitHub/arce-v180-w11b2-c53`, where continuation 53 is working |
| Checkout | `D:/GitHub/arce-v180-w90-review`, detached at this task's commit, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W90-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The problem is real and stated exactly.** `recoveryEndsAlreadyLoaded` requires every discovery-margin column of
   both ends to be loaded and entity-ticking, and `refreshGovernedFlightTickets` makes only each end's origin column
   entity-ticking. Check the note's Minecraft 1.20.1 facts (ticket levels 31 to 33, entity section visibility, what
   `getEntitiesOfClass` sees, entities still loading, empty-Level ticking, ticket expiry and persistence) against the
   mapped Forge jar.
2. **Option (a) is safe and bounded:** the shared column helper equals the predicate's columns; the refresh happens
   only after a checked ACTIVE classification and never while held; F8 releases exactly the same set in each end's
   Level; expiry is a real backstop; no ticket outlives the transfer by more than the expiry; the cost table is right.
   Is (a) within design 2's "existing bounded flight tickets" (Q3), or does it change that rule?
3. **Q1 (the destination at F1 and F2).** Confirm or refute from the code that a governed launch to a destination with
   no player near the pad is refused at F1 today, and why continuation 52's `native-03` armed all eight (was the Moon
   test player's simulation distance covering the pads?). Then judge option (A): what F1's and F2's destination check
   protects today, and whether moving that protection to the post-F2 census and holds before ASCENT loses any
   guarantee (a duplicate or stale copy of L, an occupied or changed pad, the replay exclusion, the exact-source
   return). Recommend (A), (B), (C) or another option.
4. **Q2 (the admission bound):** is it real, and is the shared helper with the existing `chunk_budget` refusal right
   for every pad path (surface, station, named pads) and for F1's source?
5. **What must not change** (section 3): the acknowledgement order, the proofs, the leases, the holds, the replay
   exclusion, the saved formats, and that ordinary reconciliation, pruning and held repair add no tickets.
6. **Players, logs, tests and estimates, the proposed R-035 and ADR-065 text, and the open questions Q3 to Q5.**

Take no heavy-JVM lock and run no Gradle build, GameTest or native server: continuation 53's benchmark needs a quiet
machine. You may read compiled classes with `javap` only while `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` does
not exist; otherwise read the sources jar with an unzip listing.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID, a severity, the section and line, the problem, and a suggested change.
- A separate answer on Q1: confirmed or not, and the option you recommend, with reasons.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
