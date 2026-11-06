# TASK W289: rocket interface text, the assembler and casing clicks, and the monitor's binding

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w289`, branch `codex/v1.8.0-w289`, created by Root from the shared head |
| Decision | Root decision R321 (QUEUE row W289) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 2 findings 2-6 to 2-8 and round 3 finding 3-5 (in Chinese; screenshots named there under `evidence/`) |
| Earlier work | [W281](records/W281.md) (text widths and slot frames on other screens) |
| Record | `docs/work/v1.8.0-root-queue/records/W289.md`; logs and before-and-after screenshots under `D:/GitHub/ARCE-Task-Evidence/root-queue/W289/` |

## The findings

1. **Placing blocks (2-6, 3-5).** A plain right-click with a block on the rocket assembler validates the structure
   instead of placing the block (`rocket/assembler/RocketAssemblerBlock.java` lines 34 to 40), and the machine casing
   consumes every right-click with its `machine_casing_inert` hint (`content/MachineCasingBlock.java` lines 43 to 66),
   so the first rocket block and every multiblock controller must be sneak-placed. Let a right-click while holding a
   placeable block item place it (the vanilla convention for blocks that are built on); keep the empty-hand and
   other-item behaviour.
2. **Flight console text (2-7).** "NO ACCESSIBLE STATIONS" is cut at both ends; BOARD and LEAVE SEAT reply
   "Flight request accepted; required fuel 0"; opening the console prints internal data
   ("MONO: ABSTRACT 1000; revision=40") in chat. Fit the text, give BOARD and LEAVE SEAT their own replies, and keep
   internal data out of player chat (log it if it is needed).
3. **Monitor and linker (2-7).** The monitor shows the raw destination ID `advancedrocketrycommunity:moon` (show the
   body's translated name); the linker says "Loader selected / Loader bound" when the selected device is a monitor
   (name the device).
4. **Fuel loaders (2-7).** The fluid fuel loader's status line runs past the panel's right edge; the item fuel loader
   uses the generic "Inserted one fuel item / Returned the item" while the language file already has fuel-unit
   texts. Fit the line and use the specific texts.
5. **The monitor's stale binding (2-8).** After a round trip the monitor shows "Stale binding": the binding stores the
   rocket entity UUID, and a transfer makes a new entity. Bind to the identity that survives a transfer (the logical
   rocket or assembly ID the transfer keeps), with a migration for saved bindings if the saved form changes (then
   write a short design note at the top of the record first and say so in your answer; Root has it reviewed).

All text goes through the existing data generators (English and Chinese); server authority, validation and packet
formats stay unchanged unless item 5 needs a saved-format change.

## Tests

A test for each item where one is practical (GameTests for the clicks and the binding round trip, unit tests for
text fitting and the replies), failing before the change. Before-and-after screenshots of each screen from a client
run if your setup has one; otherwise say so.

## Verification

The new tests; the flight menu, monitor, linker, loader, assembler and casing tests; `./gradlew build` with all unit
tests; `runData` with no unexpected diff; the full GameTest run with `scripts/check_gametest_log.py` under the
heavy-JVM lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The satellite, warp, endgame and electrolyzer screens (W293), pad selection (W291, merged), the ledger, the status
file and QUEUE.
