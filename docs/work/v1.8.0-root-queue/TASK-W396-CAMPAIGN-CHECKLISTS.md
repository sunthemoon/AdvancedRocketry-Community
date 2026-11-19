# TASK W396 and W397: the final campaign's step lists (phase 1, step P4)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | W396 | W397 |
|---|---|---|
| Worktree | `D:/GitHub/arce-v180-w396`, branch `codex/v1.8.0-w396` | `D:/GitHub/arce-v180-w397`, branch `codex/v1.8.0-w397` |
| Inventory groups | A, B, C, D and E (56 rows) | F, G, H, I and J (61 rows) |
| Output | `docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md` to `-E.md` | `docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-F.md` to `-J.md` |

Both branches start from the shared branch. Decision: Root decision R530 (QUEUE rows W396 and W397).

## Why

[FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md), phase 1, step P4, asks for one numbered step list per inventory
row before the campaign starts, in the form of the master test plan's section 11 (ID, preconditions, steps,
expected, actual, screenshot or video, tester, date, build hash), with each row's sources read in full. Candidate
C4 (`659bbcb6a`) is running its native campaign now; the owner's real-client sessions follow it. These lists are
what the owner and the harness will follow, so they must be exact and runnable, not summaries.

## What to do

1. Read [the master test plan](../../05-MASTER-TEST-PLAN.md) section 11 for the step-list form, the plan's section 2
   (what the campaign is), section 4 (the inventory) and section 5 (the phases), and section 6 (evidence layout).
2. For each row of your groups, read the row's sources in full (the version document's section, the records, the
   test plan rows it cites). Then write one step list:
   - **Header:** the row ID and title, its phase and level (V1 or V2, dedicated server, reference node), the machine
     and accounts it needs, and whether it runs on the gated campaign world (decision O2) with switches off.
   - **Preconditions:** the exact fixture (by the plan's P2 names), the Forge and JEI combination, server settings,
     the JVM properties if any, and the state to set up before step 1.
   - **Steps:** numbered, one action each, with exact commands, coordinates, items and GUI paths where the sources
     give them. Mark the steps that need a human hand and the steps the harness can drive.
   - **Expected:** for each step whose outcome matters, the observable result (what is on screen, in the log, in
     the world or in a saved file), with exact values from the sources. Where the sources do not give a value, say
     so; do not invent one.
   - **Evidence to keep:** screenshots, video, logs and files, named as section 6 lays them out.
   - **Result fields:** actual, screenshot or video, tester, date, build hash (left blank).
3. Keep the plan's row IDs and do not change the plan. If a row's sources conflict, or a step cannot be written
   without a decision, list it at the end of the file under "Open questions for Root", with the row ID and what is
   missing.
4. Write in plain English, in full sentences where prose is needed, and keep each file under about 1,500 lines. Link
   only to tracked files that exist on the shared branch; use a code span for anything else.
5. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`.

## Rules

- Change nothing outside your output files. No code, script, test, ADR, register, status file, QUEUE or other record.
  No native run, Gradle, GameTest or client session: the heavy-JVM lock belongs to C4 until about 2026-11-21.
- Commit on your branch in one commit per group file or one commit in total, in the style of AGENTS.md section 6.
  Do not push.
- The last line of your answer is the path of your last output file.
