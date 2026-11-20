# TASK W401: turn the campaign's open "needs Root" items into decisions and work packages

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w401`, branch `codex/v1.8.0-w401` from the shared branch |
| Decision | Root decision R534 (QUEUE row W401) |
| Sources | [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) (section 5 lists every open question and its "needs Root" remainder); the step lists [CAMPAIGN-CHECKLISTS-A](records/CAMPAIGN-CHECKLISTS-A.md) to [-J](records/CAMPAIGN-CHECKLISTS-J.md); [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) (sections 5 and 7); [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md) section 6; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md); [master test plan](../../05-MASTER-TEST-PLAN.md) |
| Output | `docs/work/v1.8.0-root-queue/records/CAMPAIGN-PREP-BREAKDOWN.md`, a DRAFT record |
| Write scope | That one record. Docs only. No code, script, test, ADR, register, status file, QUEUE, checklist or other record. No JVM, Gradle, GameTest, native run or client: the heavy-JVM lock belongs to candidate C4's native runs. |

## Why

W398's manifest answers what existing evidence can answer and leaves 69 "needs Root" remainders. They mix three
kinds of thing: choices Root can make now from the documents (for example a trial count, a sampling window, or
recording an item as NOT_COVERED with its reason), preparation that needs building or running (fixtures, packs,
tools, scenes), and arrangements only the owner can provide (accounts, time, machines). Root needs them sorted, so
that it can take the choices in one decision and dispatch the preparation as a few work packages.

## What to do

1. For every row of the manifest's section 5, take each "needs Root" remainder and classify it:
   - **Choice**: Root can decide from the documents. Write a proposed choice in one or two sentences, with its
     source. A choice never invents a pass/fail threshold that docs/17, the version documents or an ADR does not
     give; where a document gives no limit, the choice is a measurement procedure and a review rule ("record and
     compare with the matched baseline; a Medium or higher finding is raised when ..."), and you say that no limit
     exists. A choice never relaxes a check, never grants a player more than ordinary rules allow and never changes
     production code. Recording something NOT_COVERED is a valid choice when the documents allow it; give the
     reason the record will carry.
   - **Preparation**: something must be built or run. Name the phase 1 step (P2, P3, P5, P6, P7, P8, P9, or P4 for
     a checklist change), what exactly is built, from which existing script, GameTest fixture or record, whether it
     needs the heavy-JVM lock, whether it needs candidate C4 itself or only its identity, and a rough size (hours).
   - **Owner**: it needs the owner's accounts, time or machines. Name the packet item (O4, O5, O7, O8 or O9) or
     say that it needs a new one.
   An item can have parts of more than one kind; split it.
2. Group the preparation items into work packages that one worker session can do in about one working day, with no
   two packages writing the same files. For each package give its inputs, outputs, the rows it serves, the lock
   need and the dependencies between packages. Two packages already exist: W399 (the tracked client harness with
   the attach mode and frame-time reader) and W400 (the four Forge and JEI runtime cells); do not repeat them, but
   list the rows they serve.
3. List the choices in one table, numbered C1, C2 and so on, ready for Root to accept or change in one decision.
4. Give the totals: choices, preparation hours by package, owner items, and what blocks the phase 1 dry run (P9).
5. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files that exist
   on the shared branch; use a code span for anything else.

## Rules

- Read only, apart from your record.
- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
