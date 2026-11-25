# TASK W475: bring the v1.8.0 release draft set up to C6's recorded results and Root's decisions

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w475`, branch `codex/v1.8.0-w475` from the shared branch |
| Decision | Root decision R693 (QUEUE row W475), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Write scope | The files in `docs/releases/v1.8.0/` except `KNOWN-ISSUES.md`, plus the record `docs/work/v1.8.0-root-queue/records/W475.md` |
| Sources | [CURRENT_VERSION](../../status/CURRENT_VERSION.md); [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md); [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md); [OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md); [RESIDUAL-RISK-SIGNOFF-DRAFTS](records/RESIDUAL-RISK-SIGNOFF-DRAFTS.md); [R044-SIGNOFF-DRAFT](records/R044-SIGNOFF-DRAFT.md); [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) revision 8; [KNOWN-ISSUES](../../releases/v1.8.0/KNOWN-ISSUES.md); the [risk register](../../11-RISK-REGISTER.md); [QUEUE](QUEUE.md) rows from R581 to R692 and the records they cite (above all W451, W461 to W466, W470, [W471](records/W471.md), [W472](records/W472.md) and [W473](records/W473.md)) |
| Not allowed | Marking any Gate, requirement, version, release or risk as passed or accepted beyond what a Root decision already records. Turning a FAIL, NOT_COVERED or PENDING result into a pass. Copying an earlier candidate's result as C6's. Editing status files, the ledger, the register, ADRs, KNOWN-ISSUES, QUEUE or source. Running Gradle, GameTest or any JVM. |

## Goal

The release draft set in `docs/releases/v1.8.0/` still describes several C6 items as pending that now have recorded
results or decisions:

- C6's native campaign (attempt 2: 74 PASS and 4 FAILED) and the X01 to X03 dispositions;
- the switch decisions (R682, all six off);
- the downgrade signature (R679), KI-V180-UPGRADE-01 (R680) and the X03-D operator guidance (R681);
- provenance approval (R683, R685);
- the four residual-risk signatures (R691);
- phase 2 rounds 1 and 2 of the final campaign (R686 to R688, R690).

Reconcile every file so that a reader at P11 sees C6's current recorded state. Keep everything not yet run as
PENDING, with the reason and what will close it.

## What to do

1. For each file in the write scope, list the statements that recorded results or decisions have superseded,
   and update them in place with the result, its evidence (a link for tracked files, a code span for files outside
   Git) and the decision number. Keep the file's existing structure. Keep failed results failed and quote their
   recorded counts exactly.
2. `RELEASE-EVIDENCE.md`: update the identity table (native selection, the campaign and residual risks), add rows
   for the decisions above, and list the remaining work for P11. That work is:
   - phase 2's remaining V1 rows;
   - the attach-mode rows with the owner's account;
   - phases 3 and 4 (packaged server, reference node, soak and V2);
   - the six unsigned residual-risk scopes and their preconditions;
   - the four intermittent items;
   - the owner's one confirmation for the tag, the `main` merge, the release and the Gates.
3. `GATE-STATUS.md`: for each Gate G0 to G9, give its blockers now, citing the records. No Gate passes.
4. `MANUAL-TEST.md` and `VISUAL-VALIDATION-REPORT.md`: record phase 2's real-GPU environment and that rounds 1 and
   2 ran 43 sessions with no step scored PASS, under the per-step rule of R690. Every row's actual result stays
   PENDING; do not copy round 01 or 02 observations in as row results.
5. Keep every link valid: link only to files tracked on the shared branch and give everything else as a code span.
   Run `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.
6. In the record, give a per-file list of what changed and why, the statements you left PENDING, and any recorded
   result you found that contradicts another record, as a finding for Root.

## Rules

- Full sentences, and define internal code names before you use them (AGENTS.md section 10).
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
