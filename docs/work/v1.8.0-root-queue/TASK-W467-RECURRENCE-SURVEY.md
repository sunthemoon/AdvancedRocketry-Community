# TASK W467: recurrence survey of the hosted regression runs since 2026-11-02

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w467`, branch `codex/v1.8.0-w467` from the shared branch |
| Decision | Root decision R672 (QUEUE row W467) |
| Sources | [docs/status/CURRENT_VERSION.md](../../status/CURRENT_VERSION.md), section "Intermittent tests and observations": each item, its test or tests, its fix (the cited record and its merge in [QUEUE](QUEUE.md)) and its closing condition; the cited records |
| Inputs (read only) | Root collected, with the CI token kept to Root: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/recurrence-survey/runs.json` (all 346 runs of `v180-development.yml` created from 2026-11-02 to 2026-11-23: id, branch, head SHA, created time, attempts, final conclusion), `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/recurrence-survey/attempts.json` (385 attempts with each job's conclusion; a first attempt of a successful single-attempt run has `jobs: null`) and `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/recurrence-survey/failed-job-logs/` (the 122 logs of every failed or cancelled job, gzip, named `<run>-a<attempt>-<job>.log.gz`). A successful attempt means every registered GameTest passed, since the shard checker requires the union of executed tests to equal the inventory |
| Outputs | Tracked: only the record `docs/work/v1.8.0-root-queue/records/W467.md`. Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/recurrence-survey/survey.json` with the tables below |
| Lock | No server runs and no lock. Run the validator only. |
| Not allowed | Editing the status file, QUEUE or any record but yours; network access; treating a cancelled attempt as a pass or a failure of a test it did not report. |

## Root's rule for the closing conditions (R672)

For an item whose condition is that runs, hosted runs, shared runs or later runs show no recurrence, report **MET**
when at least 20 successful attempts belong to runs whose head commit contains the item's fix commit (for an item
without a fix, runs created after the observation), and no attempt after that point reports a failure of the item's
test or tests; otherwise **NOT_MET**, with the recurrences or the shortfall. Report **LOCAL_CONDITION** for an item
whose condition names local or loaded full runs (give the hosted figures as supplementary), and **UNDETERMINED**
where the condition asks for something these data cannot show (for example a found cause or a merge that has not
happened), with the reason.

## What to do

1. **Failures.** From every failed job log, list each failing test or unit check: run, attempt, branch, head SHA,
   created time, shard, test name, the exact failure line (`LogTestReporter` lines, timeouts, unit test names), and
   for build failures what failed.
2. **Items.** For each status-file item, give its test names, fix commit (or observation date) and the source of
   each, then count with `git merge-base --is-ancestor <fix> <head>` in your worktree: runs and successful attempts
   containing the fix, and every later attempt that reports the item's test. List head SHAs not present locally and
   leave them out of the counts. Apply Root's rule.
3. **Unrecorded failures.** List every failure from step 1 that no status-file item, QUEUE row or record mentions by
   run id or by test name with that date, for example run 45190217982 on `codex/v1.8.0-w463`.
4. **Record** the tables, the commands and anything left open, and run `python -B scripts/validate_repository.py`;
   its `Summary:` line must show `0 failed`.

## Rules

- Complete sentences. Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
