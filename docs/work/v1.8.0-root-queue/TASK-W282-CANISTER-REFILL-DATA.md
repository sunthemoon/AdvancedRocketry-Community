# TASK W282: where a suit refill stores its oxygen, and where the empty canister goes

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w282`, branch `codex/v1.8.0-w282`, created by Root from the shared head |
| Decision | Root decision R313 (QUEUE row W282) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 1, finding 5 (in Chinese; the `data get entity` replies between 12:00 and 12:01 in `results.jsonl` in that folder) |
| Design to compare with | `docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md` and W226's O1 design (find it from `records/W226*.md` and the C18 life-support records) |
| Record | `docs/work/v1.8.0-root-queue/records/W282.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W282/` |

## The finding (information, needs a code check)

After refilling a worn suit from an oxygen canister held in hotbar slot 7:

1. The chestplate's NBT gained `arce_space_suit_oxygen.oxygen_units = 1000`, while the installed pressure-tank module
   still recorded `gas: EMPTY, stored: 0`. The screen, the HUD and the tooltip all showed 1000/2000, so nothing was
   lost; the reporter reads `ArmorEquipment.setReserve`'s comment about an active buffer as the reason.
2. The module's `kind` was written as `low_pressure_tank` while the item is a `pressure_tank`.
3. The empty canister appeared in inventory slot 0 (the first free slot), not in slot 7 where the full one was held.

## What to do

For each of the three points, find what ADR-066 and the O1 design say, and say in the record whether the behaviour
matches. Change only what deviates, each with a test that fails before the change:

- If the design keeps refilled oxygen in the suit's active buffer, keep it and say so; if it puts it in the tank
  module, move it there with a save migration test for suits already saved the other way (no oxygen may be lost or
  created either way).
- If `kind` is a stored identifier that is wrong for this item, correct it so that existing saves still load (keep
  reading the old value).
- If the design or vanilla convention says the emptied container replaces the used stack in the hand, do that when the
  hand is the source; otherwise keep the first free slot and say why.

No asset, the ledger, the status file or QUEUE. If a change needs a schema version or touches a saved field, write a
short design note at the top of the record first and say so in your answer; Root then has it reviewed.

## Verification

The new tests; the existing suit, canister, oxygen charger and life-support tests; `./gradlew build` with all unit
tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The canister and module art (W277), the oxygen charger (moved to v1.9 by the owner on 2026-11-04, `records/OWNER-DECISIONS-20261104.md`), the ledger, the status
file and QUEUE.
