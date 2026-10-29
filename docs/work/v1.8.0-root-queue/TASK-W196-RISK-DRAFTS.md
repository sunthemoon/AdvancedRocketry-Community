# TASK W196-risk-drafts: exact R-043 and R-042 text for W27d4 (released flight)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | none; read the shared head (`origin/codex/v1.8.0-classic-content`) and `codex/v1.8.0-w196` at `ef33db7fd` with `git show` |
| Inputs | the R-043/R-042 sections of the three W196 reviews: `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-review/REVIEW.md`, `.../W196-rereview/REVIEW.md` and `.../W196-rereview2/REVIEW.md`; the W196 record on the branch |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-review/input/W196-risk-drafts.md`, plus an audit script and its JSON result in the same folder |

R-043 and R-042 in `docs/11-RISK-REGISTER.md` (each one Markdown table row, in Chinese) describe W27d2 and W27d3. W27d4
lets a coherent REPAIR or REGISTRY_BLOCKED bay group fly on under `released = 1`, with the R168 and R170 fixes. Root
applies the drafts at W196's merge.

## What to do

1. From the three reviews' lists and the code at `ef33db7fd`, write each needed change as an exact replacement in this
   format, which Root's apply script parses:

   ````text
   Target: `docs/11-RISK-REGISTER.md`

   Replace exactly:
   ```text
   <a unique substring of the current file>
   ```
   With:
   ```text
   <the new text>
   ```
   ````

   Cover at least: what W27d4 releases and what it never releases (UNEXPECTED_COPY and the other physical-authority
   holds stay held, latched before release authority exists); the `released = 1` acknowledgement before the first
   delegated step; the exact permits and their recheck; REGISTERED consumption before delivery; the restart dispatch of
   released receipts and the T-absent offline hold; R168's suspension of delegation on a refused effect and the
   passenger obligation before source removal; R170's cleanup of a physical-held claim's proof turn; R-042's NOT_LOADED
   lock with a released receipt on a landed rocket; the new log lines; and the residual and conservative holds the
   reviews name.
2. Keep the table rows on one line each, with the same column count, in the register's style (complete Chinese
   sentences). Do not weaken any existing statement that still holds.
3. Write an audit script that checks every anchor is unique in the current shared-head file, that the replacements do
   not overlap, and that each row keeps its column count; run it and keep its JSON result.

## Not in this task

Any tracked file, ADRs, status files and the ledger.
