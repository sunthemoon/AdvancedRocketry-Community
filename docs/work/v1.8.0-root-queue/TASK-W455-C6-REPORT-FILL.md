# TASK W455: fill the C6 switch report and the fuel report from C6's evidence

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w455`, branch `codex/v1.8.0-w455` from the shared branch |
| Decision | Root decision R646 (QUEUE row W455) |
| Sources | [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) (376 cells still say PENDING) and [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md) (37 items say PENDING_C6); QUEUE row "C6 run" (R581, R615, R616, R618, R620, R629, R632, R634) and the rows of [W446](records/W446.md) (R636), [W448](records/W448.md) (R637), [W450](records/W450.md) (R639) and [W451](records/W451.md) (R641); [W368-DESIGN](records/W368-DESIGN.md), [W367-DESIGN](records/W367-DESIGN.md), [W325-MATRIX](records/W325-MATRIX.md); the earlier fills of [C4-SWITCH-REPORT](records/C4-SWITCH-REPORT.md) and [C5-SWITCH-REPORT](records/C5-SWITCH-REPORT.md) as models of wording |
| Inputs (read only) | Under `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/`: `campaign-attempt2/` (the 78-family run that ended 2026-11-22 at 11:06: 74 PASS and 4 FAILED), `campaign/` (attempt 1, interrupted, history only), `x03-rerun/` (R616's rerun, PASS), `x02-s5-attempt3/`, `x02-s5-triage/`, `x02-s5-satellite/` (attempt 2 counts for nothing, R620); and `D:/GitHub/ARCE-Task-Evidence/x01-crash-replay/c8fa16e16be87554f26c402546733df6555d15e6/` with its `-triage/` sibling and the `followup-bay/` and `followup-passive/` folders below it |
| Outputs | Tracked: the two reports and the record `docs/work/v1.8.0-root-queue/records/W455.md`. Nothing else |
| Lock | No server runs and no lock: this is a reading and writing task. Run the validator only. |
| Not allowed | Inventing a result, a verdict or a pass. Changing a FAILED to anything else. Changing any file other than the two reports and the record (status files, the ledger, ADRs, the risk register and the QUEUE are Root's). Linking to a file that is not tracked on the shared branch (cite outside evidence as code spans). |

## What to do

1. **Each PENDING cell.** Replace it with C6's actual result for that row, from the evidence above: the family or case
   name, PASS or FAILED as the runner reported it, and the evidence path as a code span. A row that the C6 run does
   not cover stays open with a short reason (for example "client row, campaign phase 2", "REF only", or
   "NOT_COVERED: <reason>"). Where Root has recorded a cause or disposition (the decisions listed above), cite the
   decision number and use its wording; do not add a new disposition.
2. **The three R-044 failures** (cargo-G2-REUSE, w336-downgrade, accounting-root3-old-jar) are reported as FAILED,
   the known downgrade limit kept failed as the owner directed on 2026-11-13. The X03 failure of attempt 2 is the
   harness path length (R616) and the rerun's PASS is reported beside it.
3. **Section 13 (X01 to X03).** Replace the planning rows with the results: X01 S3 (3 PASS and 9 FAIL of 12), the
   triage (5 PASS and 7 FAIL; 72 of 82 states pass both starts; 10 late-loss residual states with W448's wording),
   and W451's observation (no quantity lost in the five lost-history states, the next operation untested); X02
   attempt 3, the triage totals with and without the original bay images, the bay BETA-2002 cause (R636), the
   satellite oracle-contract cause (R639) and the ledger STALE residual; X03's rerun. Keep the stated residuals of
   section 13.5 and add the observed ones.
4. **The fuel report.** Fill each PENDING_C6 item the same way from the same evidence; leave open what C6 does not
   cover, with the reason.
5. **Record** a table of every changed cell or item (section, row, old text, new result, evidence), the count of cells
   left open and why, and run `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Commit on the branch in reviewable commits (one per report is fine) plus the record, in the style of AGENTS.md
  section 6. Do not push.
- The last line of your answer is the path of the record.
