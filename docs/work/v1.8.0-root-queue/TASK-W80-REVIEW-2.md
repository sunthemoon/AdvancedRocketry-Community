# TASK W80-review-2: independent read-only review of the redesigned offline-passenger release

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W80-DESIGN.md`, revision 2, on branch `codex/v1.8.0-w80-offline-passenger-design`, commits `0b7bc153` (note) and `68bf09c7` (record `records/W80.md`, "Revision 2") |
| First review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/REVIEW.md`, SHA-256 `8f03b9057e52fa2eb61e190145a45f21d15e83e472505dd4092539de465d8961` (REDESIGN; R1 to R8) |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/ROOT-DECISIONS.md`, SHA-256 `0748860558f1a7fc2a314699f50018c1c6bc093faf4e91ca5883aef1efe0ff83`, including the correction to decision 5 at its end |
| Checkout | `D:/GitHub/arce-v180-w80-review-2`, detached at `68bf09c7`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review-2/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

1. **Each first-review finding R1 to R8:** is it closed by the revision, as Root's decisions require? Cite the
   section and line.
2. **The separate rejoin file** (decision 1): its schema, bounds and checked writer; the order of the two files'
   writes; each crash point and what a restart finds; that worlds which never use the command are untouched; and how
   an older jar treats the new file.
3. **Player safety:** no player is lost, duplicated, damaged or left inside a removed rocket, from the first login
   callback through the readback that retires the placement (decision 2), including a save that fails while
   returning normally.
4. **Seat revocation** stays separate from placement and never removes a later boarding.
5. **Command policy** as corrected: level 3 or higher; automatic level-2 origins refused.
6. **Safety** (AGENTS.md section 3.3) and **readability** (section 10).

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server. Reading compiled classes with `javap` is
allowed.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- A table of R1 to R8: closed, partly, or open, with evidence.
- New findings, each with an ID, a severity, the section and line, the problem, and a suggested change.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
