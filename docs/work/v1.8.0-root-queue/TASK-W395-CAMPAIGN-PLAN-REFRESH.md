# TASK W395: refresh the final campaign plan for candidate C4

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w395`, branch `codex/v1.8.0-w395` from the shared branch |
| Decision | Root decision R526 (QUEUE row W395) |
| Output | [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md), edited in place as a DRAFT record, committed on the branch |
| Write scope | That one record. No code, script, test, register, ADR, status file, QUEUE or other record. No native run, Gradle or GameTest. |

## Why

W359 wrote the plan on 2026-11-13, before the accounting activation and before candidate C4 was prepared. Several
of its prerequisites are now done, the fuel loader's real-client rows are missing, and Root has taken the plan's
owner decisions O1 to O9 into the draft owner packet. The plan must be current before C4 finishes, so that the owner
gets one plan in the final packet.

## What to do

1. **Section 7.1, prerequisites.** Give each row its state on 2026-11-20 with a citation: the per-fluid accounting
   (W357 design, Slices A to E, the activation merge `5951d6df2`, W373, W373B and W373C, W391); the uncovered bay rows
   (W358, W363); the other uncovered rows (W360); candidate C4 (not built yet; it follows W391's merge); the
   single-player rounds' fixes; the V1.0 section 2 roles (still open). Cite QUEUE rows and records, not memory.
2. **The fuel loader's real-client rows (F18).** [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), row F18,
   says Root adds explicit fuel B3-V rows when expanding the plan's per-system checklist. Read
   [W11B3-DESIGN](records/W11B3-DESIGN.md), section 5 (B3-V), and add the rows to the plan's v1.8 per-system inventory
   (section 4.10) and to the phase that runs them (phases 2 to 4): loader labels, fluid and status display, binding,
   GUI scales and resource reload, competing clients, the bound-passenger case, disconnect. Each row names its V1 or
   V2 level, the account or GPU it needs, and its evidence, in the plan's existing row format. Say in each row that
   the fuel switch stays off unless the owner says yes in the packet, and how the row runs while it is off (the
   plan's existing rule for the bay and cargo rows, decision O2, is the model).
3. **Section 7.3, owner decisions.** Root's choices are in
   [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md), section 6: O1, O2, O3 and O6 are taken by Root under
   R456 as the plan recommended, and O4 changes to the scripted rehearsal with a recorded hand-played subset. Mark
   each O row with Root's choice and point the owner's remaining ones (O4, O5, O7, O8, O9) to the packet. Recompute
   section 8's totals for O4's change if the plan's own figures allow it; otherwise say which figure changes and by
   how much the plan's ranges say.
4. **Candidate wording.** Where the plan says Cn or C3, say C4 where the statement is about the coming candidate,
   and keep C3 where it is history.
5. **Run the repository validator** on your edit: `python -B scripts/validate_repository.py` must end with
   `0 failed`. Link only to tracked files that exist on the shared branch; use a code span for anything else.

## Rules

- Keep every row and figure the plan already has unless this task changes it; say in a short new section at the end
  ("Refreshed by W395") what changed and why.
- Write full sentences in plain English, as the plan does.
- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
