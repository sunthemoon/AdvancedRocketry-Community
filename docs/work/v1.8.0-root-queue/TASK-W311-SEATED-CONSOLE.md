# TASK W311: the flight console from a high seat, and the named pad refusal on the console

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w311`, branch `codex/v1.8.0-w311`, created by Root from the shared head |
| Decision | Root decision R359 (QUEUE row W311) |
| Background | W278 round 7 on the JAR of `15fa195a1`: `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, section "第 7 轮", findings 7-2 and 7-5, with screenshots under `evidence/r07-ds/screenshots/`. Earlier work: [W292](records/W292.md) (riders sit at their seat block) and [W291](records/W291.md) (named pad selection) |
| Record | `docs/work/v1.8.0-root-queue/records/W311.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W311/` |

## What to do

1. **Finding 7-2 (medium-low).** A rider whose seat is far above the rocket's base cannot reopen the flight console
   after closing it. Right-click targets nothing in survival or creative (`c1-40`, `c1-41`, `c1-45`; the seat was 9.15
   blocks above the rocket's origin). The rocket entity's box is a fixed 1.5 by 4 blocks at its base
   (`registry/ModEntities.java` line 22), the console opens through `RocketEntity.interact` (line 561), and since W292
   the rider's eyes can be far above that box, beyond vanilla's pick reach.
   - **Root decision R359:** the rocket's entity box follows its structure's bounds, as upstream's `EntityRocket` sized
     itself from its storage (`setSize(max(sizeX, sizeZ), sizeY)`). A seated rider's view then starts inside the box,
     and vanilla's picking selects the rocket.
   - Keep the rocket not pushable and not a collision surface.
   - Check everything else that reads the entity box and say in the record what changes, if anything: area entity
     queries, loaders and linkers, landing and clearance checks, rendering bounds, W300's absence check, and the
     GameTests.
   - Check that vanilla's server-side interaction check accepts the interaction from the seat.
   - If this cannot work without a new packet, stop and write a one-page proposal for Root in the record (upstream also
     had a key binding, C, that sent a packet). Do not add a packet.
2. **Finding 7-5 (low).** When the selected named pad refuses a launch (`LANDING_PAD_UNAVAILABLE`, with the named pad's
   reason in the server log), the console's notice shows the result code's generic text, "All fixed destination pads are
   occupied or unsafe" (`datagen/FlightLanguageProvider.java` line 135). The notice disappears after about 3 seconds
   (`f1-pad-11-launch-3` to `5`). The precise reason goes only to chat (`rocket/transfer/NamedLandingPadStaging.java`
   line 33).
   - **Root decision:** the console shows a text that matches the refusal. Use the named pad's reason if the client
     already receives it; otherwise show a named-pad text when a named pad is selected.
   - The refusal stays visible until the player's next action on the console or until the flight plan changes.
   - If the precise reason would need a packet, menu-data or protocol change, do not make it; say in the record what it
     would take.

No saved field, packet, menu-data or protocol change. Language keys go through the existing data generators (en_us and
zh_cn).

## Verification

A test for each item that fails before the change. For item 1, for example, a GameTest seats a rider more than 6 blocks
above the base and checks that a pick from its eyes along its view selects the rocket, or that the box contains the
seat. Also run:

- the existing rocket entity, seat, flight menu, pad selection, loader, landing and W300 stale seat tests;
- `./gradlew build` with all unit tests;
- runData, then `git diff --exit-code` after committing the generated files;
- the full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock;
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Seat assignment and persistence (W300F runs in parallel on `RocketSeatInput` and the recovery service; do not edit
them), the rejoin placement of finding 7-1 (W312), other screens (W310), the ledger, the status file and QUEUE.
