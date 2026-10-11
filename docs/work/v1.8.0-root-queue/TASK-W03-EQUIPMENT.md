# TASK W03: suit workstation, pressure tanks and equipment modules

Root: Claude. Date: 2026-10-11. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w03-equipment` |
| Branch | `codex/v1.8.0-equipment` (local only; Root merges) |
| Ledger plan | C18b (workstation, pressure tanks, upgrade items, enchantment) |
| Record | `docs/work/v1.8.0-root-queue/records/W03.md` |

## Goal

A player can put their space suit in a suit workstation and install or remove
pressure tanks and upgrade modules. Everything stays on the armor item, with nothing
duplicated or lost.

## Read

- ADR-066 (`docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md`):
  - section 2 (shared authority, persistence and limits);
  - section 5.1 (slots, capacities, workstation and enchantment) in full;
  - section 5.2 for the module IDs only;
  - section 9 (decision status: D1 is owner-confirmed).
- The existing space suit, oxygen and equipment code (`equipment/**`, `atmosphere/**`), and
  the jackhammer as a recent C18b example.
- The C18b-RESERVE-15 calculator, which is verified pure code. Run
  `git log --oneline --grep "RESERVE"` to find it, and reuse it for tank capacity
  arithmetic.

## Scope

1. **Suit workstation** (§5.1):
   - one armor slot and one module transfer slot;
   - the fixed module layout per armor piece;
   - no upgrade stacks, at most one module of each type, one oxygen and one hydrogen tank;
   - menu only, with no automation capability;
   - install and remove through one authoritative server method, which moves the
     module's own bounded NBT and keeps gas, durability, enchantments and unrelated
     tags;
   - refusals leave both stacks unchanged;
   - breaking the workstation drops what it holds exactly once.
2. **Pressure tanks**: `low_pressure_tank`, `pressure_tank`, `high_pressure_tank` and
   `super_high_pressure_tank`, with the capacities of §5.1, installable in the CHEST
   positions. Gas charging stations belong to C18a and are out of scope. A tank keeps
   whatever gas it already holds.
3. **Module items**: register the upgrade items of §5.1 and §5.2 (hover, flight speed,
   visor types, padded, bionic and so on) so that they can be installed. Their motion
   or sensory effects (§5.2) are a later task. Here they are installable items only.
4. **Shared module-kind table**: define the bounded module-kind table and the immutable
   server equipment summary in `model/api`, as §5.1 requires, including
   `beaconFinderEnabled`. C17 will consume it later. Do not add the `beacon_finder` item
   itself.
5. **Enchantment** `spacebreathing`, as §5.1 specifies, together with the config keys
   the ledger plans for C18b that this scope uses (`OXYGEN.suitTankCapacity`,
   `OXYGEN.spaceSuitO2Buffer`).
6. **Persistence**: one schema-versioned armor root that owns both the modules and the
   gas. Old suits with no modules stay valid.
7. **Data**: models, original textures, loot, recipes, and en_us and zh_cn names, all
   through data generation.

## Tests (required)

- JUnit:
  - the slot layout rules;
  - the one-per-type and tank limits;
  - the module NBT round trip that preserves unrelated tags;
  - the armor root codec, including the old-suit load.
- GameTests:
  - install and remove each module type;
  - a full-slot refusal and a full-inventory refusal leave both stacks unchanged;
  - shift-click from detached copies;
  - workstation break drops exactly once;
  - an equipped suit keeps its modules across a save and load.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- Jetpack flight, hover, speed and other module effects, the toggle key and its packet
  (§5.2).
- The laser gun, mining tools, the oxygen charger and scrubber (C18a), and HUD or
  client configuration (C18d).
- Propulsion (W02), machines (W01), status files and the ledger.
