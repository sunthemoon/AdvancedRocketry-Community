# TASK W237: the GameTest completion-marker check across a midnight log roll

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w237` (new; branch `codex/v1.8.0-w237` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W237.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W237/` |
| Failure | Shared sharded regression 41252856608 at `a9aa5659`, shard 2 (job 124692154241, log `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/job-124692154241.log`): "All 317 required tests passed" at 23:59:58 UTC; the server shut down at 00:00:00, Forge rolled `latest.log` at midnight, and `build.gradle`'s `runGameTestServer` `doLast` (lines 464 to 473) threw "Forge GameTest completion marker is missing from latest.log". Root reran the failed jobs |
| Precedent | W135's midnight handling in `scripts/check_gametest_log.py` (lines 22 to 98 and 466 to 560): it rebuilds a run that a midnight roll split from `logs/<yyyy-MM-dd>-<n>.log.gz` and `latest.log`, and requires the midnight between them with no longer silence than `MAX_ROLL_GAP` |

**Root decision R241.** With four shards per run, a run crossing 00:00 UTC is now more likely. The marker check must
find this run's marker across a midnight roll. It must never accept a marker from an earlier run: at startup, Forge
also rolls the previous session's `latest.log` into a fresh `.gz`, so an mtime filter alone would let a crashed run
pass locally on its predecessor's marker.

1. Make the completion check rebuild the run exactly as the checker does. Reuse the checker's own reader, for
   example a narrow `--completion-only` mode that `build.gradle` calls. Do not keep a second, diverging
   implementation.
2. Keep every existing refusal:
   - no fresh `latest.log`;
   - no marker for this run;
   - a split that the checker's rules cannot rebuild.
3. Tests that fail first:
   - a run split at midnight with the marker in `latest.log` passes;
   - a split with the marker only in the rolled part passes;
   - a crashed run whose only marker is in the startup-rolled previous session fails;
   - an unsplit run passes or fails as today;
   - malformed gaps fail.
4. **`build.gradle` is Root's file.** Put the exact `build.gradle` change in
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W237/build-gradle.patch`, and commit only the script and tests. Root
   applies the patch on your branch.

## Verification

- The changed and existing log-check Python suites.
- With the patch applied in a temporary copy, `runGameTestServer` on one small batch under the heavy-JVM lock, plus
  a simulated rolled-log directory for the split cases.
- The repository validator.

## Not in this task

The checker's other rules, expectations, ceilings, the workflow, status files and the ledger.
