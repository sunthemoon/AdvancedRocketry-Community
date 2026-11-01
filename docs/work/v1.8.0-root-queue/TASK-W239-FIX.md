# TASK W239-fix: guard the exit selector before its validity callback

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w239` (W239's; continue on its branch) |
| Branch | `codex/v1.8.0-w239`, now at `295589374` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W239.md`: append "R250 review correction"; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W239/` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W239-review/REVIEW.md` (SHA-256 `4bdca02c…bee954e219`): **BLOCK**, W239-R1 (High); its probe `ValidityReentryProbe.java` and `validity-reentry.log` are beside it |

**Root decision R250.** Apply the review's remedy for W239-R1. In `BayExitSelector.selectOwned`, the validity
callback runs before the active-decision guard is set. A reentrant `select` from it can therefore complete a second
selection for one detach, charge the budget twice, and leave an orphaned reservation. A later selection then crashes.

1. Acquire the decision and reentrancy guard before calling any external validity callback, and release it in
   `finally` on every exit path.
2. Assign the attempt sequence only after validation succeeds, so stale preflight checks cost no attempt or
   candidate units.
3. Same-context recursion during validation yields `PENDING` with no extra work. A different context caches
   `REENTRANT_REFUSED`.
4. Regression tests that fail first on `295589374`:
   - the review's probe, same-context, as a repository test;
   - the different-context form;
   - after each, releasing the original context leaves no orphaned reservation, and a following ordinary exit
     succeeds.
5. Check every other external callback in the four classes (the safety port, completion callbacks) for the same
   pattern, and say what you find.

## Verification

- The four test classes alone.
- The `rocket` test package.
- The whole unit suite.
- The repository validator.

The same reviewer's finding is checked again before S2 starts.

## Not in this task

S2 and S3, any hook or integration, status files and the ledger.
