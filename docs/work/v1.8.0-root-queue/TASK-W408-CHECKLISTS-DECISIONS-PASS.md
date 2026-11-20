# TASK W408: write the accepted choices and the delivered tools into checklists A to J (package K20, first pass)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w408`, branch `codex/v1.8.0-w408` from the shared branch |
| Decision | Root decision R547 (QUEUE row W408) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) sections 2 and 3 (choices C1 to C44) and section 8 (Root's changes, R535); [J21-FINDING-MAP](records/J21-FINDING-MAP.md) sections 4 and 8 (rechecks and R542); [W402](records/W402.md) and the QUEUE row W402 (R546's decisions on its findings); the delivered tools and fixtures: [W399](records/W399.md) (client harness, session files), [W400](records/W400.md) (the four cells), [W403](records/W403.md) (observers and evidence root), [W406](records/W406.md) (data packs); [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) section 6 (evidence layout) |
| Write scope | The ten step lists `docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md` to `-J.md`, and a short record `docs/work/v1.8.0-root-queue/records/W408.md`. Nothing else. |
| Not allowed | Any JVM, server or client. Changing a row's expected result except where an accepted choice or decision says so; adding a pass/fail limit no source gives; removing a step. |

## What to do

1. For each row, apply every accepted choice that names it (C1 to C44, as R535 changed them): add the procedure
   parameters, the NOT_COVERED dispositions with their reasons, the review rules and the "no limit exists" notes
   where the choice says so. Quote the choice ID in the step, for example "(C6)".
2. Add the rechecks of J21-FINDING-MAP section 4 to the rows it names (or to J21), the R542 items N15 to N19 as
   recheck steps, J21's precondition naming the 2026-10-06 report, and steps for A-F4 (the disc seen from above)
   and A-F10c (the rocket's entity name).
3. Apply R546's decisions on W402's findings: J15 step 1 uses the namespace `advancedrocketrycommunity` (W402 section
   5); I11 and every refusal step prove a refusal by the intent tool's own receipt and unchanged server state read
   back, and by the server log only where the code writes one (W402 section 6, finding 1); `suit_motion` needs no
   distance step; the force-field projector's silent non-operator use is recorded as the known Low finding.
4. Replace placeholders that the delivered tools and fixtures now answer: the harness commands and session-file
   fields (W399), the cell names and `cell.json` paths (W400), the observer commands, evidence root and checksum
   commands (W403), and each pack's file name, SHA-256 and expected outcome (W406) in B03, B06, D04, D08, D09, E01,
   E02, E05 and F05. Use paths under `D:/GitHub/ARCE-Task-Evidence/` as code spans.
5. Leave every remaining open input as an explicit "needs package Knn" marker with the package of the breakdown that
   supplies it; do not invent coordinates, values or fixtures.
6. Update each file's open-questions section: mark what this pass answered, and list what remains with its package.
7. The record lists, per file, the rows changed and why (choice, decision or tool), and what remains for the second
   pass. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files on the
   shared branch; use a code span for anything else.

## Rules

- Commit on the branch in one commit (or one per file group) in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
