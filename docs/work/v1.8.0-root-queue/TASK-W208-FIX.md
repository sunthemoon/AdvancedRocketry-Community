# TASK W208-fix: the replay auditor's chest payload and the expiry seam's boundary test

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session; this file hands
two of W208's review findings to you.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w208` (W208's; continue on its branch) |
| Branch | `codex/v1.8.0-w208`, now at `2269b69ef` (pushed by Root; local commits only from here, Root pushes and merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W208.md`: append a section "R181 review corrections" |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W208/` (add a `r181/` folder; change no existing file there) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W208-review/REVIEW.md` (SHA-256 `b0e5cabb…76929e951`), findings W208-R1 and W208-R3; its `review_checks.py` and `audit-mutation-probe.json` show R1's reproduction |

**Root decision R181.** W208's independent review approved with changes. W208-R1 (Medium): `r180-native-audit.py`
checks the seven diamonds in each `*-native.json` but, after replaying the raw archive, compares only the block states,
so a wrong chest payload passes. W208-R3 (Low): no W208 unit test drives `W208WriterControl.tick`, so the 120-tick
automatic release of either expiry scenario is untested. Both are test-side and are fixed on W208's branch before it
merges. W208-R2 (the synchronous C1 admission sizing) is a production remedy queued separately; it is not in this task.

## What to do

1. **W208-R1.** Keep `r180-native-audit.py` and its recorded hash unchanged. Add a corrected auditor in `r181/` that
   compares each replayed chest's complete BlockEntity payload (native and sealed) with the retained census, not only
   the block states, and a negative test that fails for a changed count, a changed tag and an extra item (it must fail
   against the old auditor's logic first). Rerun it on all 27 retained observations in `native-06` and `native-07`
   and record the script and result hashes.
2. **W208-R3.** A unit test with a bounded asynchronous committer and a controlled tick supplier, for both the C1 and
   the C3 expiry scenarios: the writer stays held and unacknowledged through entered+119, releases at entered+120, and
   the next matching write is not injected again; cleanup in `finally`. Record the failing-first result (against a
   deliberately early release or a missing seam, then restored) before the passing one.
3. Append the record section: what each finding was, what changed, the commands and results, and the new hashes. Do
   not rewrite the sealed R180 evidence or the earlier record sections.

## Verification

The new and changed W208 unit classes; the `rocket.persistence` and `rocket.server` packages; the W208 Python tests;
the corrected auditor on the 27 observations. Targeted unit runs need no heavy-JVM lock. No native case, GameTest,
runData or full build: the native runs are not repeated.

## Not in this task

W208-R2 and any production change, new native runs, budgets, bounds, timeouts, ADRs, status files and the ledger.
