# TASK W343: fix W340's findings in W332's R-042 runner

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w332`, branch `codex/v1.8.0-w332` (W332's branch, with the shared head and this TASK merged by Root). W332's session has ended; its work is committed. |
| Decision | Root decision R413 (QUEUE row W343), from W340's review `D:/GitHub/ARCE-Task-Evidence/root-queue/W340/REVIEW.md` (read it first, with its probes and logs) |
| Read first | [TASK-W332](TASK-W332-NOT-LOADED-AND-POOL-NATIVE.md), `records/W332-DESIGN.md` and `records/W332.md` in this worktree |
| Record | `docs/work/v1.8.0-root-queue/records/W343.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W343/` |

## Why

W340 found no production regression in W332's seam or command gates, but three evidence gaps in
`scripts/run_v180_r042_native.py` and its coverage claims. W332 merges only with these fixed.

## What to do

1. **F1, ledger recovery.** The final L_LEDGER check must require a present ledger whose hash differs from S0; a
   missing file is not a changed ledger. Add runner-level negative tests for an absent and for an unchanged final
   ledger.
2. **F2, the pool's refusal reason.** `bay_refused` and `run_p` must assert the data bay's expected menu phase and
   reason: `NONE / DEPLOYMENT_CAPACITY` for P_SERVER and `NONE / OWNER_PENDING_FULL` for P_OWNER (confirm both values
   in the code). Add negative tests for an unrelated reason, `NONE`, and the other case's reason. Keep the existing
   package, receipt and disk checks.
3. **F3, unknown presence.** Restore `U_BAY` and `U_LEDGER` with W340's reversible trigger: hold a Windows handle on
   the valid receipt or ledger file with `FileDispositionInfo.DeleteFile=true` while the server starts, so that
   vanilla's directory walk still sees the file and the no-follow attribute query fails; clear the disposition in a
   `finally` before closing the handle, and verify the file's SHA-256 afterwards. Use W340's
   `WindowsPresenceProbe.java` and `probe_windows_presence.py` as the starting point; the trigger lives in the
   harness, never in production code. Each case shows the refusal (`ARCE-BETA-2000`, the storage hint first, the
   right type, `isPresenceUnknown`), that no world file changed, and a clean start once the handle is released, with a
   second restart. Correct W332-DESIGN's host-impossibility text with a labelled correction. If a native start still
   cannot reach the refusal, record exactly what was observed and leave the case NOT_COVERED.
4. **The R042 command permission.** W340 noted that `R042ReleaseTestGateTest` uses permission 4 and does not fail if
   the R042 subtree's own permission-2 predicate is removed. Add a test that does.
5. **Native smokes on a local JAR** for the changed families (N, L, P and the restored U), both restarts, plus one
   scratch run per changed oracle with its protection disabled that the oracle rejects; discard the scratch copies.

## Verification

- The new tests, every existing R-042, bay and cargo runner test, with TEMP and TMP on D:.
- `./gradlew build` with every unit test if any Java changes (only item 4 should touch Java tests).
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges W332 with these fixes.

## Not in this task

The seam `ReleaseTestLoadFailure` and production code; the installed-JAR verification and W332's families in the
campaign driver (a later task, after W341 and W339 merge); the status file, QUEUE and the risk register.
