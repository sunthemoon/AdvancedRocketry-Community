# TASK W293: truncated text, raw IDs, hidden bars and missing slot frames on the remaining screens

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w293`, branch `codex/v1.8.0-w293`, created by Root from the shared head |
| Decisions | Root decisions R326 and R330, and round 5 finding 5-1 (QUEUE row W293) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`: round 3 finding 3-6, round 4 findings 4-2 and 4-3, round 5 finding 5-1 (in Chinese; the screenshots named there are under `evidence/`) |
| Approach to follow | [W281](records/W281.md) (slot frames and text widths on the earlier screens) and [W289](TASK-W289-ROCKET-UI-TEXT.md) (text fitting on the rocket screens) |
| Record | `docs/work/v1.8.0-root-queue/records/W293.md`; logs and before-and-after screenshots under `D:/GitHub/ARCE-Task-Evidence/root-queue/W293/` |

## The findings

1. **Satellite terminal (3-6):** buttons read `LAUN…` and `CANC…`; status lines `Terminal ready / op… [SUCCESS]` and
   `TERMINAL POWER 900…` are cut; `Research… [PENDING_DISCOVERY]` is still shown three seconds after a claim; the
   player inventory has no slot frames.
2. **Satellite builder (3-6):** the terminal's status text appears on it; `OUTPUT_BLOCKED` shows right after a
   successful build.
3. **Rocket satellite bay (3-6):** a notice is cut off over the Inventory title; no slot frames.
4. **Flight console (3-6):** `STATION ArceTe` and a route that loses its last letter (check after W289, which fitted
   other console text).
5. **Warp controller (3-6):** raw IDs on the controller and its countdown.
6. **Railgun and elevator (4-2):** the last Received slot hides the energy bar below about 28 % (`ElevatorScreen`
   lines 108 to 110, `ElevatorMenu` line 97, `RailgunMenu` line 95).
7. **Endgame screens (4-3):** truncated laser drill and black-hole generator status lines, a laser drill tooltip that
   runs past the bottom of the screen, a stale "Last stop" line, and no slot frames on the endgame menus.
8. **Electrolyzer (5-1):** its charge slot hangs below the inner panel, the status text overlaps it, and the player
   inventory has no slot frames.

## What to do

Fix each in the screen and menu layout, following W281's slot-frame and text-width approach: fit or wrap text to its
panel (with the full text on hover where it must be shortened), show translated names instead of raw IDs, refresh
stale lines from the server's current state, keep tooltips on screen, and move slots so that no bar or label is
hidden. All new text goes through the existing data generators (English and Chinese). Server authority, menu slot
indices that saved data or automation depend on, packet formats and protocol versions stay unchanged; if one must
change, stop and say so first.

## Tests

Unit tests for text fitting and labels where the screen logic allows it (as W281 and W289 did), and a menu test for
any moved slot (the slot still accepts and returns the same items). Before-and-after screenshots from a client run if
your setup has one; otherwise say so, and Root arranges a visual check.

## Verification

The new tests; the existing screen, menu and satellite, warp, endgame and electrolyzer tests; `./gradlew build` with
all unit tests; `runData` with no unexpected diff; the full GameTest run with `scripts/check_gametest_log.py` under the
heavy-JVM lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Screen behaviour beyond layout and text, new art, the rocket screens W289 covered, the ledger, the status file and
QUEUE.
