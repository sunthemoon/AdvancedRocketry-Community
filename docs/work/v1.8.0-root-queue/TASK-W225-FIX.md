# TASK W225-fix: adapt the two CI wiring suites to the sharded workflow

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w225` (W225's; continue on its branch) |
| Branch | `codex/v1.8.0-w225`, now at `9c900c4c0` (Root's workflow commit; pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W225.md`: append "R216 wiring corrections" |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W225-review/REVIEW.md` (SHA-256 `34330362…8eec32979`), findings W225-R1 and W225-R2 |
| Hosted failure | run 41185583579; the build job log is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W225-hosted/` |

**Root decision R216.** The review found the sharded design sound but blocked it on two existing wiring suites that
were not adapted: W225-R1, `tests/test_check_v180_ci_host.py` (lines 128 to 139) still pins the old four-entry
failure-upload list with `build/gametest/logs/`, which the build job no longer produces; W225-R2,
`tests/test_check_gametest_log.py` `WorkflowIntegrationTests` (lines 482 to 491) splits at the removed `Run all Forge
GameTests` step and expects the old literal command, checker call and discovery selector. The first hosted run
stopped on R1.

## What to do

1. **W225-R1:** assert the new bounded build-evidence list, and separately require that each shard uploads its raw logs
   and its failed or incomplete evidence under `always()`; keep the prohibition on broad checkout and preflight exports.
2. **W225-R2:** adapt the integration test to the multi-job structure, keeping meaningful assertions for: Bash failure
   propagation, mandatory native execution in every shard, the runtime identity checks, the raw-log check per shard,
   and an aggregate that requires the build and all four shard results to succeed; check the discovery command the
   workflow actually uses.
3. Run every Python suite the workflow runs (with the workflow's own selectors) and confirm they pass; if a workflow
   line must change, write it as a patch to `D:/GitHub/ARCE-Task-Evidence/root-queue/W225/workflow-r216.patch` and do
   not edit `.github/workflows` yourself.

These are test updates for changed CI wiring: do not weaken any GameTest assertion, the log checker, budgets or
timeouts, and do not remove either suite.

## Verification

Both suites, failing first against an unadapted expectation where meaningful; all Python suites the workflow runs.
Root pushes and the hosted run qualifies the result.

## Not in this task

Other changes to the workflow or scripts, test or budget changes, status files and the ledger.
