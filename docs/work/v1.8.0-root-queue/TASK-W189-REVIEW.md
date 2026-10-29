# TASK W189-review: independent read-only review of W189's rocket polish

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w189` at `204848c13`, cut from `90e0b15db`; the slice is `90e0b15db..204848c13`. Review worktree `D:/GitHub/arce-v180-w189-review`, a detached checkout of `204848c13` |
| Task it answers | [TASK-W189](TASK-W189-ROCKET-POLISH.md), with Root decision R153 |
| Record | `docs/work/v1.8.0-root-queue/records/W189.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W189/` |
| Hosted run | the run on `204848c13` (Root pushed the branch at 11:48); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W189-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **B5, flight tracking.** The rocket's tracking interval during flight states: the traffic per tracked rocket, that
   the interval returns to normal at rest, and that no packet format changed.
2. **B6, wording, under R153.** The schema-1 `RocketAssemblerData.detail` field and existing saved strings are
   unchanged in format; new reports carry the translated reason without the parameter map; operators still see the
   details. The slice added server-side language resources and language discovery (`e138fd3a4` to `325e40448`): is
   that needed, is it isolated to the rocket feedback, can it change what other language keys resolve to, and does it
   work on a dedicated server and in single-player? Run `check_client_imports.py`.
3. **B7, Moon slow fall.** The floor sits just above vanilla's motion dead zone, only under slow falling in scaled
   gravity, and attribute clamping cannot undo it.
4. **Nothing weakened:** assertions, timeouts and bounds. Name each changed existing test and why.
5. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes and the
`rocket` and `celestial` packages; `python -B scripts/check_client_imports.py`. Check 6 GiB free memory first. No
GameTest, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W189-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
