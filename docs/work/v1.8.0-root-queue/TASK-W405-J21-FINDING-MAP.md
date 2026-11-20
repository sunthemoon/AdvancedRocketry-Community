# TASK W405: the map from every real-client finding to its task, commit and current disposition (package K19)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w405`, branch `codex/v1.8.0-w405` from the shared branch |
| Decision | Root decision R541 (QUEUE row W405) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md): package K19 and choice C42; [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) section 7.1 and row J21; [CAMPAIGN-CHECKLISTS-J](records/CAMPAIGN-CHECKLISTS-J.md) row J21; the [QUEUE](QUEUE.md); records W280 to W282, W284 to W296 and W301 and their follow-ups. Outside Git, read only: the three real-client reports `D:/GitHub/ARCE-Task-Evidence/pre-v18-client-20261006/REPORT.md` (2026-10-06, before v1.8), `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md` (H12) and `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` (H13), with the request batches beside them |
| Output | `docs/work/v1.8.0-root-queue/records/J21-FINDING-MAP.md`, a DRAFT record |
| Write scope | That one record. Docs only. No code, script, test, checklist, ADR, register, status file, QUEUE or other record. No JVM, server or client. |

## What to do

1. List every finding of the three reports: its report, its ID as the report gives it (for example F1, or a round
   and step), its severity as the report states it, and a one-line description. Include informational
   observations and the round-8 residuals; mark them as such.
2. For each finding, follow it through the QUEUE and the records to its current state, and give exactly one
   disposition from choice C42:
   - **Fixed**: the task, the merge commit on the shared branch and the record that shows the fix, and the check
     that proved it (a GameTest, a native run or a client round);
   - **Known issue**: where it is listed for users, with its text;
   - **Matches the design**: the design document or record (for example W294) that says so;
   - **Recheck on C4**: no task fixed it and no decision accepted it, or the fix has not been seen in a real client.
   Then, for every Fixed finding, say whether the fix was seen in a real client after it merged; if not, it also
   needs the C4 recheck (C42's last disposition applies on top).
3. For every finding that needs a recheck, give the recheck as one or two concrete client steps (scene, action,
   expected result) taken from the original batch where one exists, and the checklist row that already covers it,
   or say that no row does.
4. List separately the informational observations and residuals for Root to accept or reject one by one; none is
   accepted by default.
5. Give the totals by disposition, and the findings from the 2026-10-06 report that no later record mentions.
6. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files that exist
   on the shared branch; use a code span for anything else.

## Rules

- Read only, apart from your record.
- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
