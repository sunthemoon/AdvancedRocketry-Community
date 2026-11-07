# TASK W317: the per-orbit route warning on the warp controller screen

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w317`, branch `codex/v1.8.0-w317`, created by Root from the shared head |
| Decision | Root decision R368 (QUEUE row W317) |
| Background | [W309](records/W309.md) made the warp quote warn when no rocket route starts or ends at the target's own orbit (`WarpQuote.targetOrbitHasRoutes`, ADR-044's W309 note), but only `/arce station warp` shows it. The warp controller screen cannot: `WarpControllerMenu.viewValues` and `ClassicControlKind.WARP_CONTROLLER` carry exactly seven values (W309 record, "Warp controller screen") |
| Record | `docs/work/v1.8.0-root-queue/records/W317.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W317/` |

## Why

A player who warps a station from the controller screen to Tau Ceti e or Cygnus X-1 strands its docked rockets without
being told. Most players use the screen, not the command.

## What to do

1. **Design first (one page, in the record), because this changes what the classic controls packet carries.**
   - Add one value to the warp controller's view: the displayed quote's `targetOrbitHasRoutes`, with a value meaning
     "no quote shown".
   - Show where the value comes from on the server (the same quote that fills the cost and cost class), how it stays
     consistent with the other values in the same view, its bounds, and how the client validates it.
   - Bump `ClassicControlsNetwork`'s protocol version, and say why that is enough for a mismatched client and server.
   - No saved field changes.
2. **Implement it.** The server fills the value from the quote it already computes. The screen shows the command's
   warning next to the cost, in both languages, when a quote is shown and the flag says no routes. The screen draws
   nothing when there is no quote.
3. **Keep the command's wording and the warning's meaning the same as W309's.** One translation key may serve both
   the command and the screen, if that fits.

Root will have a separate session review the design and the diff read-only before merge.

## Verification

- A test that the view carries the flag for Tau Ceti e (false), the Moon (true) and no quote.
- The decode bound for the new value.
- A screen layout test for the warning next to the cost.
- The existing warp, station controls and classic controls tests.
- `./gradlew build` with all unit tests.
- runData, then `git diff --exit-code` after committing the generated files.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Warp rules, costs and admission, routes, other controllers, the ledger, the status file and QUEUE.
