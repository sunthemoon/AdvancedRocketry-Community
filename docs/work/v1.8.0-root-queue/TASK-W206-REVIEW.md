# TASK W206-review: independent read-only review of the W90 cold-destination measurement

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w206` at `24a076f9c`, cut from `546d9c614`; the slice is `546d9c614..24a076f9c` (7 commits, test-side only). Review worktree `D:/GitHub/arce-v180-w206-review`, a detached checkout of `24a076f9c` |
| Task it answers | [TASK-W206](TASK-W206-W90-COLD-DESTINATION-COST.md), Root decision R175 |
| Record | `docs/work/v1.8.0-root-queue/records/W206.md` on the branch (the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W206/` |
| Hosted run | the run on `24a076f9c` (Root pushed the branch at 05:40); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W206-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W206 reports all six flight, capacity, expiry, replacement, disconnect and restart checks passing, and measured
overruns against docs/17: new-terrain request ticks of 509 to 933 ms, a 2,081.733 ms capacity burst, a 542.537 ms
restart-cut request, and callback-bearing P99 of 124.9 to 132.7 ms.

## What to check

1. **The plan** (`708833488`) was committed before any native run, and the runs follow it: the real launch entry, no
   prewarmed measured end, no support tickets, cold controls with no other ticket.
2. **The observers** change no behaviour: no extra loads, tickets or saves, and nothing on the measured path that the
   product would not do. Their own cost is inside the measured ticks; say whether it could explain any overrun.
3. **The figures.** Reproduce the tables from the retained evidence; check the cohorts (all, work, callback-bearing),
   that the corrected summaries (`31df84576`, `360e02a68`) keep the earlier files, and that no slow tick is excluded.
4. **The lifecycle checks:** eight warm-ups and the ninth refused, the per-player cap unchanged, expiry, replacement,
   disconnect, F8 release apart from chunk eviction, and the process restart.
5. **Attribution.** From the evidence (and JFR if retained), where the request spikes, the capacity burst and the
   callback-bearing ticks go, in enough detail for a remedy task. Say what is measured and what is inferred.
6. **Nothing weakened:** no production change; caps, lifetimes, budgets, timeouts and assertions unchanged.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W204 (`codex/v1.8.0-w204`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `W206ObservationTest` and the
`rocket.server` and `resourceescrow` unit packages; the W206 Python tests and summaries on the retained evidence.
Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build
or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W206-R1, ...), severity, file and line,
problem and suggested change; the attribution for section 5; the commands you ran and their results; the SHA-256 of
REVIEW.md on the last line.
