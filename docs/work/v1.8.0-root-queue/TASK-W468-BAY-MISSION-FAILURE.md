# TASK W468: diagnose the bay mission matrix failure in run 45190217982

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w468`, branch `codex/v1.8.0-w468` from the shared branch |
| Decision | Root decision R672 (QUEUE row W468) |
| Sources | `BayMissionRowGameTests.initialMissionProofReplayAndPromotionMatrix` and the code it drives under `src/main/` (read only; `src/main` equals C6 on that branch); [W466](records/W466.md) and [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) rows F2.2 to F2.7 and F5.7, which cite that method's pass in C6's run 44770334930; [W333-DESIGN](records/W333-DESIGN.md) |
| Inputs (read only) | Run 45190217982 (branch `codex/v1.8.0-w463`, head `d772a4bdd`, whose `src/main` equals C6 and whose change was Python only), attempt 1, shard 3 failed with `initialmissionproofreplayandpromotionmatrix failed! D3/D4/replay/promotion queried or recreated initial/current/history: Variant[change=NONE, entry=REGISTERED_REMOVED_DATA]` at 14:23:52 UTC. Root saved its job log at `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/recurrence-survey/failed-job-logs/45190217982-a1-137754059562.log.gz` and its shard and regression artifacts at `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/recurrence-survey/run-45190217982/`; C6's passing shard artifacts are at `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/hosted-shards-44770334930/`. Open ZIPs read only with `python -I` |
| Outputs | Tracked: only the record `docs/work/v1.8.0-root-queue/records/W468.md` |
| Lock | Read only by default. If a local reproduction is needed, run only that one batch with the heavy-JVM lock per WORKER-RULES, at most five repetitions, no console window, and record each. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to tests, scripts or other records. Calling the failure fixed or harmless without evidence. |

## What to do

1. From the test source, explain what the assertion checks and which observation fired for variant
   `change=NONE, entry=REGISTERED_REMOVED_DATA`.
2. From the failed run's log and `gametest.log`, compared with C6's passing run, find what differed: batch neighbours
   and order, autosaves or writer completions inside the window, timing. Check whether the batch order differs.
3. Classify the cause as TEST (the fixture or its observation is timing-sensitive while the product behaves as
   designed), PRODUCT (C6 queries or recreates the initial mission when it must not; state what a player could meet)
   or UNKNOWN, with the evidence. For PRODUCT, propose known-issue text and a v1.9 follow-up; for TEST, describe the
   fixture fix for v1.9 or a later candidate.
4. **Record** the findings, commands and anything left open, and run `python -B scripts/validate_repository.py`; its
   `Summary:` line must show `0 failed`.

## Rules

- Complete sentences. Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
