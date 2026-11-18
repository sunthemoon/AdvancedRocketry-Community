# TASK W372D: finish the accounting activation (fresh session after W372C stalled)

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session, and you continue
work that an earlier Codex session started.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w372`, branch `codex/v1.8.0-w372`, at `b485b5dbe`; the worktree is clean |
| Decision | Root decision R498 (QUEUE row W372D). The rules of [TASK-W372](TASK-W372-ACCOUNTING-ACTIVATION.md), R466, R468 and R469 (QUEUE row W372) stay in force. |
| Record | `docs/work/v1.8.0-root-queue/records/W372.md` on this branch. Read all of it first. It holds the 135-row classification table (R468), the R8 arithmetic (R469) and the shared-fixture table. Add a section "W372D continuation (R498)". |
| Write scope | As TASK-W372 and R468 allow: format values (class 1) and explicit fixtures (class 2) in tests and GameTest fixtures, and the record. A production defect (class 3) stops the work and is reported with a failing test. No production change, no raised bound and no relaxed assertion. |

## Where the work stands

The earlier session (thread `01a1e9d6-6645-73b3-b272-c852ca1474ae`) stalled on a model call at about 04:30 and was
stopped. Its results:

- **Unit suite.** `efacdd7f8` passed all 6,589 tests. A clean build passed at `05748fd56`.
- **runData.** It passed with a clean generated diff.
- **First full GameTest at `05748fd56`.** 1,843 passed and 20 failed. The evidence is
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W372/r469-verification/` (`gametest-checker.log`, `gametest-logs`). The
  failures were:
  - "Legacy fluid component has no accounting baseline" (8 tests);
  - "Receipt changed" and "Inspector output changed" (5 tests);
  - `ACCOUNTING_MISMATCH` (6 tests);
  - `UNKNOWN_BASELINE` and "Baseline payload mismatch" (1 test each).
- **Fixture fixes.** Commits `62495fc38`, `911bda4d8`, `cf2a12575` and `b485b5dbe` fixed many of those fixtures.
- **Targeted GameTest run of 15 batches at `b485b5dbe`.** 3 of 31 failed (evidence `.../W372/r469-target1/`):
  - `mixedConsumersShareGlobalAndRocketLimits` (batch `w333_admission_rows`): "Native binding does not match
    operation";
  - `twoLogicalRocketsShareASlotThroughCheckedArmAndFreshOrdinaryRetirement` (batch
    `rocket_normalized_complete_flights`): the same message;
  - `cachedNativeViewStillChecksIdentityAtTheFinalBoundary` (batch `escrow_core`): "Removing the duplicate did not
    restore the actual observation".
- **Full unit suite at `b485b5dbe`.** 6,589 tests, 1 failure: `EscrowGovernedTerminalJournalTest`, line 69
  (`.../W372/r469-target1/full-unit.log`).

## What to do

1. **Classify and fix the four remaining failures** under R468's rules, and add their rows to the record's table.
   - A class 1 or class 2 fix keeps the assertion's purpose.
   - A class 3 finding stops the work: write it up with a failing test and do not change production.
2. **Rerun the targeted GameTest batches**, including the ones the first full run failed, until they pass.
3. **Full verification on the final committed source:**
   - the full unit suite;
   - `runData` with a clean diff;
   - a full GameTest run with the log checker;
   - the two native smokes with both restarts that TASK-W372 names.
4. **Update the record.** Give the commits, the classification rows, the commands, the results and the evidence
   paths. Copy evidence out of `build/` before the session ends.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies and GameTest worlds after each run. Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W372D/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only Claude review of the whole activation diff follows before the merge.
- The last line of your answer is the path of `records/W372.md`.
