# TASK W317F: W317R's note N1 on the route warning GameTest

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w317f`, branch `codex/v1.8.0-w317f`, created by Root on W317's integration commit `4d593a724` with this TASK merged in |
| Decision | Root decision R371 (QUEUE row W317F) |
| Review to answer | `D:/GitHub/ARCE-Task-Evidence/root-queue/W317R/REVIEW.md`, note N1 (Low) |
| Earlier work | [TASK-W317](TASK-W317-WARP-SCREEN-ROUTE-WARNING.md) and `records/W317.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W317F.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W317F/` |

## What to do

`WarpControllerRouteWarningGameTests` (lines 80 to 85 at `3474744e2`) navigates away and checks that the quote is gone
before it sends its rate-limited request, so that subcase only shows that an absent quote stays absent.

1. While a routeless quote is still displayed, request the same target again at once: assert `CONTROL_RATE_LIMITED`,
   then assert that the cost, cost class, route flag and token are all reset in the next view.
2. Check navigation separately, with its own live quote.
3. Show that the new subcase fails if a refused request stops clearing the displayed quote (a temporary local mutation,
   restored before you commit; say how in the record).

Test code only. No production, packet or protocol change. The other assertions stay as they are.

## Verification

- The warning GameTest batch and the station controls batches.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The screen layout and its real-client check (W317R N2, for the next client round), the ledger, the status file and
QUEUE.
