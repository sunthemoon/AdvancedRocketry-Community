# TASK W11b2, continuation 38M: rerun the 71 remaining settlement completion cuts on continuation 38's source

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2-CONTINUE-38](TASK-W11B2-CONTINUE-38.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at `99b6bd69` plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local commits only; Root pushes and merges) |
| Source under test | `66ecf2c83143b77988e9e1187a175faae95f2fbe`, continuation 38's final executable source |
| Input | `records/W11B2-F7F8.md`, "Continuation 38", and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C38/` (especially `summary.json`, which names the 71 cuts) |
| Record | Add a "Continuation 38M" section to `records/W11B2-F7F8.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C38M/` |

Continuation 38 changed `scripts/run_v180_rocket_settlement_completion.py`: its oracle now pins the integrated journal
root 3 with an empty landing outbox, requires genuinely empty floor slots, checks receipts against the frozen O1 members
and asserts the actual hold reason on each restart. It reran the 47 cuts its own changes needed. The other 71 cuts that
the runner offers (ACCEPT, ABANDON, CHAIN_ACCEPT, CHAIN_ABANDON, NORMALIZE, NAMED_ACCEPT and NAMED_ABANDON at their O2,
O3, O3J, O4, O_BIND, VETO and VETO_CHECKPOINTED boundaries) have no result with the new oracle on the integrated source.
Run them.

## What to do

1. Check that the worktree is clean and that `66ecf2c8` is an ancestor of HEAD with no code change after it
   (`git diff --stat 66ecf2c8 HEAD -- src scripts tests` shows nothing).
2. Build the jar from that source with `gradlew.bat --offline jar --console=plain` under the heavy-JVM lock, and record
   its SHA-256. Continuation 38's final jar SHA-256 was
   `3f581e91f2f223f4ab022e120f075860702ab9214245683a93046f737b47c3a2`; say whether yours matches.
3. Run the 71 cuts listed under `other_matrix_cuts_not_rerun` in continuation 38's `summary.json` with `scripts/run_v180_rocket_settlement_completion.py`,
   two restarts each, pinned to the source SHA and jar, under the heavy-JVM lock, in batches that keep each run under
   about two hours. Use your own disposable server copies under `D:/GitHub/ARCE-Task-Evidence/Temp/W11b2-C38M-*`. If a
   start fails for an environmental reason (for example a port bind failure before world initialization), keep its
   evidence, and rerun only that case in a fresh copy; count only complete passes.
4. If a cut fails on its oracle, do not change the oracle, the probe or any production code: record the case, the
   assertion, the log lines and the disk snapshot, finish the other cuts, and report.
5. Write the record section: the commands, jar hash, per-batch results, total cuts and restarts, any failure with its
   evidence path, and the warning and overload counts. Commit the record only.

## Write scope and boundaries

Only the record section and your evidence directory. No change to Java code, Python runners, tests, the log checker or
its expectations. Never remove a lock you do not own. Check that C: and D: have more than 10 GiB free before each
batch. Remove your own Temp server copies when a batch ends; if the tool policy refuses a deletion, list the exact paths
in the record for Root and continue.

Continuation 39 runs its GameTest and benchmark in `D:/GitHub/arce-v180-w11b2-rocket-integration` and shares the
heavy-JVM lock; wait for it as WORKER-RULES says.

## Not in this task

Any code change; the C0 to C8 cuts; F10 and the benchmark; investigation of the research fairness test (another task).
