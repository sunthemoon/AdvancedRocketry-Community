# TASK W153-rereview: re-review of the W153 fixes (W153F, W153G, W153H)

Root: Claude. Date: 2026-10-24. This continues your W153 review session (`W153-review/REVIEW.md`, SHA-256
`59e893c1…5b45`, verdict BLOCK). Same rules: read-only, no commit, write only to your output folder, no other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w153` at `b40f5919`. Read `git diff caef08fd..b40f5919`. Your review worktree `D:/GitHub/arce-v180-w153-review` is now a detached checkout of `b40f5919` |
| Tasks | [TASK-W153F](TASK-W153F-REVIEW-FIXES.md) (R99), [TASK-W153G](TASK-W153G-R5-AND-R1-MATRIX.md) (R102), [TASK-W153H](TASK-W153H-FULL-SUITE-FAILURES.md) |
| Record | the W153F, W153G and W153H sections of `docs/work/v1.8.0-root-queue/records/W153.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153F/`, `W153G/` and `W153H/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153-review/REREVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Each finding closed.** W153-R1 under R99: the acceptance cutoff at PREPARED submission, the monitor reporting the
   real outcome, arming invalidated after the cutoff, and the live membership revocation at both cuts. W153-R2 to
   W153-R5 as their fixes claim, including R102's -1 for unavailable on protocol 2 (kinds 0 to 3 unchanged). W153-R6's
   correction file leaves the sealed evidence untouched.
2. **W153H's production fix** (`e8fef790`, `EscrowFlightJournal` and `EscrowFlightCheckpoint`). F1 keeps a checked
   absence lease for its logical rocket and transfer through the proof, and takes the exact serial lease only when the
   writer assigns a serial. Check that no intervening change to that rocket or transfer can pass unseen, that serial
   conflicts and unacknowledged removals still refuse, and that proof, source, acknowledgement and F2 are unchanged.
   Check that no schema, saved record, budget or recovery decision changed. Check that the five new unit tests fail
   on the old code, and that the diagnosis (an ordering defect exposed by the new batch order, plus success-only cleanup
   in the concurrent-flight fixture) explains all seven failures.
3. **Nothing weakened.** No test lost an assertion, timeout or precondition; the log checker and its expectations are
   unchanged except W153's six reviewed rejection lines.
4. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and with W152
   (`codex/v1.8.0-w152`, still in progress): shared files and conflicts.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes alone, then
the `resourceescrow`, `rocket.monitor`, `classiccontrol` and `rocket.server` packages. No GameTest, runData or server;
no heavy-JVM lock. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

One verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings only where something remains, with an ID (W153-R7, ...),
severity, file and line, problem and suggested change; the commands you ran and their results; the SHA-256 of
REREVIEW.md on the last line.
