# TASK W309: stations in orbits that no rocket route reaches

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w309`, branch `codex/v1.8.0-w309`, created by Root on W308's integration commit `15fa195a1` with this TASK merged in |
| Decision | Root decision R357 (QUEUE row W309) |
| Background | W308 (record `records/W308.md`, section 6): the shared Space body is no warp target any more (`WarpTargetRule`), but `/arce station create` still accepts it, because `StationManager` checks only `orbitable`. Separately, no rocket route anchors an orbit of Tau Ceti e (an orbit-only body by ADR-043), so a station warped there strands its docked rockets, and the quote does not warn, because the warning (`WarpQuote.targetSystemHasRoutes`, filled by `StationWarpService.routesInSystem`) asks whether any route touches the target's star system |
| Record | `docs/work/v1.8.0-root-queue/records/W309.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W309/` |

## What to do

1. Operator station creation (`/arce station create` through `StationManager`) refuses the shared Space body with
   the refusal it already uses for a body that cannot be orbited. Reuse `WarpTargetRule` or a shared rule beside it;
   do not change the Space body's catalog definition. A station that already orbits the Space body is unchanged.
2. The warp quote's route warning becomes per orbit: it warns when no rocket route starts or ends at an orbit of the
   target body (the anchor the rocket planner resolves a station source or destination to), instead of when no route
   touches the target's star system. Keep the `WarpQuote` field's place and type (rename it if the meaning changes)
   and update the command's warning text to say that rockets cannot reach or leave the station in that orbit.
3. Find out whether the warp controller screen can show the same warning without a packet, protocol or menu-data
   change (for example from data the client already has). If it can, show it next to the cost; if it cannot, do not
   add one, and say in the record what it would take.
4. Say in the record whether ADR-043 or the route data intend any route to Tau Ceti e's orbit. Do not add routes.
5. Draft the ADR-044 implementation note for the per-orbit warning in the record for Root to add; do not edit the ADR.

No saved field, packet or protocol change; if one seems needed, stop and say so.

## Verification

A GameTest or unit test for each of items 1 and 2 that fails before the change; the existing station creation, warp,
station-controls and Tau Ceti path tests; `./gradlew build` with all unit tests; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

New routes, the Space body's catalog definition, other warp rules, the ledger, the status file and QUEUE.
