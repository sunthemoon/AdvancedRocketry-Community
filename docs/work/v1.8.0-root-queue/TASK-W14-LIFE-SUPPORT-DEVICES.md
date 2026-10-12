# TASK W14: CO2 scrubber, gas charging and seals

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w14-life-support` |
| Branch | `codex/v1.8.0-w14-life-support` (local only; Root merges) |
| Ledger plan | C18a (`oxygenScrubber`, `oxygenCharger`, `pipeSealer`, `oxygenDetection`, the carbon cartridge, `OXYGEN.scrubberRequiresCartrige` and the other C18a configuration rows that sections 3 and 4 cover) |
| Record | `docs/work/v1.8.0-root-queue/records/W14.md` |

## Goal

Players can extend a sealed room's oxygen with a CO2 scrubber, refill suit and pressure
tanks at a gas charger, and seal pipe and cable gaps. All of it is server-authoritative,
finite and conserved, and integrated with the existing atmosphere room service and the
merged suit equipment.

## Read

- ADR-066 (`docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md`):
  - section 3.1 (scrubber and instruments) and section 3.2 (seals and lighting);
  - section 4 (gas charging and finite player resources);
  - section 2 (shared authority and limits);
  - section 9, D1 and D5 (owner-confirmed: finite scrubber savings with a nonzero minimum).
- The existing atmosphere code (`atmosphere/**`): the oxygen vent, the room service, the
  seal detector (ADR-067). Also the merged equipment code (`equipment/**`, W03, W06): the
  armor equipment root, the pressure tanks, and the gas bounds that W06 enforces.
- The chemical reactor (W10) for the carbon cartridge recipe, if W10 is merged when you
  start. Otherwise leave the cartridge's chemical recipe as a follow-up and give it a plain
  crafting recipe only if §3.1 allows one.

## Scope

1. **CO2 scrubber and carbon cartridge** (§3.1, D5):
   - one unstackable cartridge slot;
   - finite savings with the nonzero minimum the owner confirmed;
   - `scrubberRequiresCartrige` behaviour;
   - bounded per-room work.
2. **Gas charging** (§4): a charger fills suit oxygen and hydrogen tanks, and pressure-tank
   items, from its own fluid store.
   - It is conservative and bounded.
   - It refuses an overfill.
   - It never changes the armor root except through the equipment's authoritative methods.
3. **Seals and instruments** (§3.2 and the `oxygenDetection` row): the pipe sealer, and the
   instrument items that §3.1 lists, if they are not already implemented. Check the seal
   detector before adding anything.
4. **Configuration:** the C18a keys that these sections map. Follow WORKER-RULES for the
   exact configuration count and the cold-start log expectations.
5. **Data:**
   - models and original textures;
   - loot and recipes;
   - en_us and zh_cn text.

   All through data generation.

## Tests (required)

- JUnit:
  - the scrubber savings arithmetic and its minimum;
  - the charger transfer bounds;
  - the codecs.
- GameTests:
  - the scrubber extends a sealed room's oxygen by exactly the specified savings, and
    stops without a cartridge when it is required;
  - the charger fills a suit and a pressure tank exactly and refuses an overfill;
  - gas is conserved across a save and load;
  - the pipe sealer seals and unseals a gap.
- The whole unit suite once. Each new class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- The observatory (C18c).
- HUD bars (C18d).
- The laser gun and mining tools (§5.3).
- Status files and the ledger.
