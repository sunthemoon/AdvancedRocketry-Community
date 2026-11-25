# TASK W473: draft sign-off texts for v1.8's remaining residual risks

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w473`, branch `codex/v1.8.0-w473` from the shared branch |
| Decision | Root decision R689 (QUEUE row W473), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)), which lets Root sign residual-risk acceptances on the owner's behalf, with the owner as the responsible person and failed results kept failed |
| Model | [R044-SIGNOFF-DRAFT](records/R044-SIGNOFF-DRAFT.md), whose section 5 text Root signed under R679 |
| Sources | The [risk register](../../11-RISK-REGISTER.md) rows R-021, R-030, R-035, R-039, R-040, R-041, R-042, R-043 and R-044; the ADRs and records each row cites; [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) and [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) revision 8 (all six production admission switches stay off in v1.8, R682); [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) and the campaign checklists A to J; [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md) section 7; [KNOWN-ISSUES](../../releases/v1.8.0/KNOWN-ISSUES.md); C6 `c8fa16e16be87554f26c402546733df6555d15e6` source (read only) |
| Output | One new tracked file, `docs/work/v1.8.0-root-queue/records/RESIDUAL-RISK-SIGNOFF-DRAFTS.md`, plus the record `docs/work/v1.8.0-root-queue/records/W473.md` |
| Not allowed | Editing the risk register, any ADR, status file, ledger, QUEUE, KNOWN-ISSUES or source. Writing that a risk is accepted or signed. Running Gradle, GameTest or any JVM. |

## Goal

Before P11, Root must decide each remaining residual-risk scope. These are the rows whose text still says the risk
acceptance waits: R-021, R-030, R-035, R-039, R-040, R-041, R-042 and R-043, and R-044's other parts (unsupported
device roots and the 256-coordinate pool shared by about 15 protected device families). R-044's downgrade paragraph
is already signed. Write one draft per scope that Root can sign as written, or that says exactly what must happen first.

## For each scope, write

1. **Register line.** The row's line number in `docs/11-RISK-REGISTER.md`, and a one-sentence summary of the
   residual risk.
2. **v1.8 shipping reach.** With all six switches off, say which triggers can happen in an ordinary production world
   and which need a test world, a managed ledger or an admission switch. Cite the code by path and the test or record
   that shows it. If a trigger's reach is not settled by the code or a record, say so; do not guess.
3. **Evidence on C6 and what is open.** Name the records, GameTests, native cuts and campaign packages that cover it
   on C6 or that still carry over from an earlier candidate. Say why the earlier evidence still applies, such as no
   source difference in the paths involved. List what is still open, using campaign row IDs, reference-node items,
   NOT_COVERED items and intermittent items.
4. **Proposed signing text.** Chinese, in the style of R044-SIGNOFF-DRAFT section 1, with an English rendering below
   it, covering:
   - scope;
   - what happens;
   - extent and duration;
   - supported recovery;
   - operator requirements;
   - failed results kept failed;
   - what the signature does not cover (no switch, Gate, version or release);
   - re-review conditions, at the latest before v1.9.0, and what voids the signature.

   Leave the signature line as `负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿`.
5. **Preconditions.** The evidence that must exist before signing, by campaign row or record. Write `none` if it can be
   signed now.
6. **Recommendation.** Exactly one of:
   - `SIGN_NOW`;
   - `SIGN_AFTER` with the named evidence;
   - `NEEDS_KNOWN_ISSUE` with a proposed KI-V180 text;
   - `DO_NOT_ACCEPT`, which needs a fix or a waiver ADR, with the reason.

## Also write

- A summary table: scope, reach in v1.8, recommendation and preconditions.
- Any risk-register statement you found that C6's code or records contradict, as a finding for Root. Do not fix it.
- Any operator-guide or KNOWN-ISSUES text a scope needs that does not exist yet, as a proposal.

## Rules

- Cite tracked files on the shared branch as links. Cite worker-branch or outside-Git files as code spans.
- Use full sentences. Define any internal code name before you use it.
- Run `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
