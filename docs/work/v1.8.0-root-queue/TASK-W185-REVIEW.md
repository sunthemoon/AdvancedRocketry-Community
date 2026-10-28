# TASK W185-review: independent read-only review of W27d3

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w185` at `31bf36d10`, cut from the shared head `a9375878b`; the slice is `a9375878b..31bf36d10` (14 commits). Review worktree `D:/GitHub/arce-v180-w185-review`, a detached checkout of `31bf36d10` |
| Task it answers | [TASK-W185](TASK-W185-W27D3-COPIES-AND-ABSENCE.md) |
| Design | `docs/work/v1.8.0-root-queue/records/W185.md` on the branch (the one-page design at its top); [records/W127-PLAN.md](records/W127-PLAN.md), the W27d3 sub-leaf; [records/W27A-DESIGN.md](records/W27A-DESIGN.md) sections 4.1, 5 and 8.1 |
| Risk | R-043 in [the risk register](../../11-RISK-REGISTER.md), which Root updates before merge |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W185/` |
| Hosted run | the run on `31bf36d10` (Root pushed the branch at 01:45); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W185-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice: it consumes copies, removes neutral non-survivors, proves absence and routes passengers.
Codex implemented it, so a Claude reviewer gives the independent read. Deployment admission must stay off outside the
GameTest server and the full release-test gate.

## What to check

1. **The carried-over fixes.** R62: D6 saves nothing while it waits. R65: the checked low edge moves from `min - 1`
   to `min - 2`, still at most 16 columns per end, with a placement over the limit held, never truncated. The record
   says existing boundary assertions were updated to the wider geometry; confirm none was loosened.
2. **Recognition and election.** The exact UUID first; at most 64 identities across both ends, the cap a conflict;
   blocked images counted; a destination copy never PREPARED authority; the absent-source rebuild only after both
   admissible ends load.
3. **Consumption and removal.** D5 consumes every recognized copy with its own end's after hash. Only consumed neutral
   non-survivors are removed, after a valid exact survivor exists and assigned online passengers are routed. Can any
   path remove a copy that still holds unconsumed packages, or remove the only copy?
4. **Absence proofs.** Every census column must be entity-loaded before capture, including the parts W27d2 did not
   await, and no cold slot counts as absent. The separately recorded origin columns: is the home-column membership
   (`abc2c7b0b`) right, and is anything now called proved that was never observed?
5. **Passengers and logins.** A login during a held claim stays queued, with no settlement or journal removal.
   Coordinator movement is limited to an online assigned passenger and the exact survivor.
6. **Unchanged:** formats, bounds, budgets, quotas, timeouts and `RocketTransferService`. Check the shared cargo-work
   allowance that changed-copy publication now uses (`ad4e83a2f`): what else waits while one copy reserves it?
7. **The native harness's save matcher.** `777afa400` fixed `SAVED`, which also matched the start of a save. Say
   which W170 native cases (merged at `61c2f6245`) relied on that matcher for a saved-image claim, and whether their
   recorded results still prove what W170's record says.
8. **The coordinator over 500 lines.** The record says its added methods only forward lifecycle calls. Check.
9. **R-043.** List each sentence of R-043 that W27d3 makes wrong or incomplete, for Root's update.
10. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W164 (`codex/v1.8.0-w164`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes, and the
`rocket.bay`, `rocket.server`, `resourceescrow` and `rocket.transfer` packages; the harness's Python tests. Targeted
unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or
server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W185-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
