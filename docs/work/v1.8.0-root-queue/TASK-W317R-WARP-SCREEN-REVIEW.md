# TASK W317R: independent review of W317's warp controller route warning

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w317` at `3474744e2` (base `ee0f6aa0a` on the shared branch), checked out read-only and detached at `D:/GitHub/arce-v180-w317r` |
| Implementer's record and design | `docs/work/v1.8.0-root-queue/records/W317.md` on that branch (the design was committed first, `3b0c064a9`); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W317/` |
| Task it implements | [TASK-W317](TASK-W317-WARP-SCREEN-ROUTE-WARNING.md), Root decision R368 |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W317R/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes what the `classic_controls` channel carries and bumps its protocol to 3 (AGENTS.md section 13) |

## What to check

1. **The wire change.** The warp controller's view gains an eighth value (`TARGET_ORBIT_HAS_ROUTES`: -1 no quote, 0
   none, 1 present) with bounds in `ClassicControlKind.WARP_CONTROLLER`. Check the encode and decode bounds, that a
   view with seven values or an out-of-range eighth value is refused, and that no other control kind changed.
2. **Version negotiation.** `ClassicControlsNetwork` goes from protocol 2 to 3 with exact-version acceptance. Say
   whether a client or server on protocol 2 is refused cleanly, and whether `network-protocols.txt` and
   `NetworkProtocolPinTest` match.
3. **Consistency.** The flag must come from the same displayed quote as the cost and cost class in the same view, so the
   screen never shows a warning for a different target than the cost. Look for a path where the quote is replaced,
   expires or is cancelled and the flag stays.
4. **Meaning.** The screen warns exactly when W309's command warns (`WarpQuote.targetOrbitHasRoutes`), and never when
   no quote is shown. Check both languages' text and the layout at GUI scales 2 and 3.
5. **The expected-log change.** `scripts/gametest_expected_log.json` gains `station_controls_route_warning: 2` for the
   new GameTest's own cleanup. Confirm that these are lines the new test itself causes and that no unrelated warning is
   hidden.
6. **Tests.** Do the new unit and GameTests fail without the code they test?

Run the classic controls, station controls and warp unit tests, and the new and existing warp controller GameTest
batches once, under the heavy-JVM lock. Keep the backslash lock path from WORKER-RULES
(`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`). A full GameTest run is not required. Work in your own Temp folder for
any scratch export, and delete it when you finish.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each point above, and the commands
you ran with their results. The last line of your answer is the REVIEW.md path.
