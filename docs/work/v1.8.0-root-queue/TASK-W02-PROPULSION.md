# TASK W02: classic propulsion components and typed fuel modes

Root: Claude. Date: 2026-10-11. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w02-propulsion` |
| Branch | `codex/v1.8.0-propulsion` (local only; Root merges) |
| Ledger plan | C17a (engines, tanks, nuclear core, rocket configuration keys) |
| Record | `docs/work/v1.8.0-root-queue/records/W02.md` |

## Goal

A player can build rockets with advanced mono, bipropellant (fuel and oxidizer) and
nuclear propulsion. Each mode has its own Fluid content, the thrust and capacity
values of ADR-065, and clear assembly refusals for invalid mixes. The current basic
rocket keeps working unchanged.

## Read

- ADR-065 (`docs/decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`):
  section 2 (C17a identities and root handling), section 3 (D1-A, owner-confirmed), and
  section 3.1 (disassembly, owner-confirmed). Section 11 covers verification.
- The rocket model and assembly code under `rocket/**`, the existing `rocket_motor`,
  `rocket_fuel_tank` and fuel handling, and the ADR-026 component API.
- Existing calculators from earlier C17a work, which you should reuse:
  - C17a-NUM-01, at commit `9b50488a`;
  - C17a-NC1, at commit `451bddb6`.

  `git log --oneline --grep "C17a"` and the files in `docs/work/v1.8.0-c17-contract/`
  show where they are.

## Scope

1. **Blocks and items** with the C17a IDs of ADR-065 section 2: the advanced mono motor,
   both bipropellant motors, the bipropellant and oxidizer tanks, the nuclear motor, the
   nuclear fuel tank and the nuclear core. Include the remaining C17a block ID in that
   table if it belongs to propulsion.
   - Plain engines and tanks need no block entity unless they store something.
2. **Typed propulsion at assembly** (section 3):
   - mode detection that is independent of iteration order;
   - the tier table (mass, thrust, consumption);
   - tank roles and capacities;
   - nuclear thrust capped by the cores;
   - the compatibility table with external ADR-026 components, with its distinct refusal
     reasons;
   - aggregates that reject capacity above 2,048,000 or an overflow before any world
     extraction.

   ADR-026-only rockets keep their abstract mode.
3. **Fuels**: separate Fluid content for the bipropellant fuel, the oxidizer and the
   nuclear working fluid, as section 3 assigns them. Fuelling uses the existing loader
   path where it already supports Fluids. Loader variants and the monitor are out of
   scope.
4. **Configuration**: the `ROCKET.*` keys that section 3 maps (`thrustMultiplier`,
   `fuelCapacityMultiplier`, `nuclearCoreThrustRatio`, plus the fuel lists and the toggles
   that the ledger plans for C17a).
   - Server COMMON scope, bounded as the ADR says.
   - Values are captured at assembly.
5. **Disassembly** (section 3.1): the remaining propulsion resources follow the
   owner-confirmed rule.
6. **Persistence**:
   - new snapshot data carries its own schema version;
   - an older snapshot loads in its old mode;
   - unknown or oversized roots follow section 2: kept verbatim, never normalized.
7. **Data**: models, original textures, loot, crafting recipes, and en_us and zh_cn
   names, all through data generation.

## Tests (required)

- JUnit:
  - the tier table and the capacity aggregate limits;
  - mode classification across component orders;
  - every refusal reason in the compatibility table;
  - the multiplier bounds;
  - snapshot codec round trip and old-schema load.
- GameTests:
  - assemble and launch one rocket of each mode, with exact fuel and oxidizer debits;
  - refuse a mixed-mode rocket and a wrong tank role;
  - confirm the existing basic rocket still assembles with unchanged metrics.

  Pace any wait for chunk or entity loading with `gametest/GameTestTickPacer`, as
  `TauCetiPathGameTests` does, never with a bare tick count.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- Loader variants `loader/2` to `loader/5`, the monitoring station, the `linker` item
  (ADR-065 section 4). These come in a later task.
- Station controls (C17b), beacon, satellite bay, solar array (C17c).
- Machines and hatches (task W01).
- Status files and the ledger.
