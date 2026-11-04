# TASK W281: the classic controller's energy line, three screen layouts and the tank hint

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w281`, branch `codex/v1.8.0-w281`, created by Root from the shared head |
| Decision | Root decision R312 (QUEUE row W281), from findings 1, 3 and 4 of W278's scripted single-player round 1 |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` section 4; screenshots under `evidence/r01-solo/screenshots/` (named below) |
| Record | `docs/work/v1.8.0-root-queue/records/W281.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W281/` |

## What a real client showed, and what to do

1. **The energy line (finding 1).** The controller screen of the lathe, crystallizer, cutting machine, chemical
   reactor, arc furnace and precision laser etcher always reads "Waiting for power", even while the machine runs
   (`r21`, `r31`, `r41`, `r51`, `r71`, `r81`). Root confirmed the cause: `client/ClassicControllerScreen.java` line 32
   draws the energy line with `screen.advancedrocketrycommunity.classic.energy` and `menu.energy()`, but
   `ClassicWait.ENERGY.key()` produces the same key, whose text is the wait reason without `%s`. Give the energy line
   its own key with the stored FE (and capacity, if the menu has it) in English and Simplified Chinese, keep the
   ENERGY wait reason's text, and add a test that the two keys differ and that the energy line shows its number.
   Check the other screens for the same key collision.
2. **Layouts (finding 3).** The rolling machine and precision assembler screens draw no slot frames for the player
   inventory (`r03-rolling-done`, `r10-precision-gui-before`); the assembler's "OUTPUT 0-1" and "FE" labels touch
   its inner frame (`r10`); the suit workstation draws its inventory as one grey block without slot separation, and
   its hint "-: remove; Shift: move to inventory" runs past the panel's right edge onto the "Inventory" title
   (`q00` to `q03`). Draw the slot frames as the other v1.8 screens do, move or shorten the labels so they sit inside
   the panel at GUI scales 2 and 3 in English and Simplified Chinese, and check the text widths with the font in a
   test where the project already has such checks.
3. **The tank hint (finding 4).** Right-clicking a fluid tank while holding an unrelated item (tin nuggets) shows
   "Cannot exchange a full 1,000 mB: check capacity, fluid, metadata and inventory space." (`f01`, `f03`, `f05`);
   only an empty hand shows the contents. When the held item is not a fluid container at all, show the contents as
   the empty hand does (or let the use pass through), and keep the failure message for real containers.

## Verification

Unit tests for each change; the affected screen and tank GameTests; `./gradlew runData` with `git diff --exit-code`
if language files are generated; `./gradlew build` with all unit tests; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`. Client screens are not testable in GameTests: say how you checked the layout (for
example a text-width test and screenshots from `runClient` if you can run it under the lock).

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The electrolyzer's slot synchronisation (W280), the canister refill data (W282), textures (W275, W277), machine
behaviour, recipes and energy values, the ledger, the status file and QUEUE.
