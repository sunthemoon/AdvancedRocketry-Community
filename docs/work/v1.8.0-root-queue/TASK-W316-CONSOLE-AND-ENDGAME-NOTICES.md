# TASK W316: round 7's last screen items after W311

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w316`, branch `codex/v1.8.0-w316`, created by Root from the shared head |
| Decision | Root decision R367 (QUEUE row W316) |
| Background | W278 round 7 findings 7-3 and 7-6 (`D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, section "第 7 轮"). [W310](records/W310.md) fixed the rest of them and left these two for after W311; [W311](records/W311.md) added the refusal pin (`ScreenActionMessages.RefusalOwner`, `ScreenMessageState`) |
| Record | `docs/work/v1.8.0-root-queue/records/W316.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W316/` |

## What to do

1. **Finding 7-3, the station button.** On the flight console, the selected station button's text `[ STATION ArceT…`
   touches the button's left edge (screenshots `c1-43` and `f1-pad-10-console-*` under `evidence/r07-ds/screenshots/`).
   Fit it inside the button with the same margin as the other buttons, at GUI scales 2 and 3, in English and Chinese,
   with the full name still on hover (`RocketFlightScreen`, `RocketFlightScreenLayout`).
2. **Finding 7-6, endgame refusals.** On the railgun, a refusal such as "Not enough energy [INSUFFICIENT_ENERGY]"
   shows only in the bottom notice and disappears on the notice's timer, while the status line still says `OK [OK]`
   (`b1-raila-06` under `evidence/r07-eg/screenshots/`).
   - Root decision: the endgame screens keep a refusal visible until the player's next action on that screen, as the
     flight console does since W311. Make the railgun, elevator, laser drill and black-hole generator screens
     `RefusalOwner`s.
   - Release the refusal on each of their controls and when the screen's state changes in a way that answers it.
   - A success or progress message is not a refusal.
   - Reuse W311's mechanism; do not add a second one.

No saved field, packet, menu-data or protocol change. Language keys go through the existing data generators (en_us and
zh_cn) if any are needed.

## Verification

- A unit or layout test for item 1, as W293 and W310 did. For item 2, unit tests of which messages each screen pins and
  of the release points.
- The existing screen tests.
- `./gradlew build` with all unit tests.
- runData, then `git diff --exit-code` after committing any generated files.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Endgame device behaviour, naming the failed elevator rule (it needs a menu-data change), other screens, the ledger, the
status file and QUEUE.
