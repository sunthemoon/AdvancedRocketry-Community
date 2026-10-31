# TASK W225: split the v1.8 regression's GameTests into several jobs (D18)

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Workflow files are
Root's: write the change as an exact patch outside the repository; you may commit supporting scripts and their tests
on your branch.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w225` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w225` (local commits only for scripts and tests; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W225.md` (new) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W225/workflow.patch` (the exact `.github/workflows/v180-development.yml` change), with an audit of it |
| Decision | D18 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): "拆分 GameTest 为多个 job（推荐）" |
| Background | `.github/workflows/v180-development.yml` (one `regression` job, `timeout-minutes: 75`, `runGameTestServer` at line 121); `scripts/check_gametest_log.py` and its manifest; ADR-072; the GameTest batch layout (`records/` notes on batch order: the batch order follows a HashMap) |

**Root decision R215.** The owner chose to split the GameTests rather than raise the limit. Recent regression jobs
take 57 to 73 of their 75 minutes, about 60 of them in GameTests.

## What to do

1. **Design, in the record:** how the GameTests are partitioned (by batch name, deterministic and complete: every
   batch in exactly one shard, a new batch fails the build until it is assigned or is assigned by a stated rule), how
   each shard runs (its own `runGameTestServer` with a batch filter, or the existing server with a selector), and how
   the results come back together: one job that requires every shard, checks that the union of executed tests equals
   the complete inventory with no test missing or run twice, runs `check_gametest_log.py` over each shard's log against
   that shard's slice of the manifest (or the whole manifest over the combined logs), and fails if any shard failed,
   timed out or was cancelled. The build, unit tests, artifact audit and runData stay once, before the shards, and the
   shards test the same built JAR (artifact identity checked). No per-test timeout, budget, assertion or log
   expectation changes.
2. **Scripts and tests** for the partition and the union check (failing first: a missing batch, a duplicated batch, a
   missing test in the union, a failed shard).
3. **The workflow patch**, with each shard's timeout stated and a target of each shard well under 60 minutes.
4. A dry run of the partition and union scripts on a retained full GameTest log (for example a recent hosted run's
   evidence) showing the shards cover exactly the recorded tests.

## Verification

The new tests alone, failing first; the Python suite for the touched scripts; the dry run. Root applies the patch and
judges it on a real hosted run.

## Not in this task

Test changes, timeouts or budgets of tests, other workflows, status files and the ledger.
