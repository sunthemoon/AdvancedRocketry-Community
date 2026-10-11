# TASK W05: electric arc furnace and cutting machine

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w05-arc-cutting` |
| Branch | `codex/v1.8.0-w05-arc-cutting` (local only; Root merges) |
| Base | W01 candidate `7fb1c1a3`: the hatch family, generic classic controller and lathe |
| Ledger plan | C16b (`arcfurnace`, `blastbrick`, `cuttingMachine`, `sawBlade`, `sawBladeIron/0`, the arc furnace, cutting machine and sawmill config/JEI rows) |
| Record | `docs/work/v1.8.0-root-queue/records/W05.md` |

## Goal

A survival player can make steel and titanium in an electric arc furnace, and cut
wafers, circuits and planks in a cutting machine. Both run on the W01 kernel exactly
as the lathe does. Steel gets its first real production route.

## Read

- ADR-064 (`docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md`):
  - section 8, items 1 and 3;
  - section 1 (machine family) and section 2 (tag ingredients);
  - the Context section for the legacy recipes and numbers;
  - sections 12 and 13.
- The W01 record `docs/work/v1.8.0-root-queue/records/W01.md` and its code in
  `machine/classic/runtime/**`. The controller is generic: add each machine as a new
  kind with its own pattern, recipe type and data, and do not fork the runtime.
- The legacy layouts and recipes, as behavioural facts only, from the pinned original
  repository (W01 shows how to cite `TileLathe`). Do not copy code or art.

## Scope

1. **Electric arc furnace** (§8.1):
   - the controller;
   - the new `blast_brick` block (hardness 3, blast resistance 6);
   - the legacy 5 × 5 × 5 pattern of blast bricks, coils and air in four rotations;
   - the recipe type `advancedrocketrycommunity:arc_furnace`, with the legacy recipes and
     numbers as data. Steel needs charcoal, as in legacy. Include the titanium route if the
     legacy recipes have one, since the jackhammer's titanium waits for it.
2. **Cutting machine** (§8.3):
   - the controller;
   - the new `saw_blade` block and the `iron_saw_blade` item;
   - the legacy 1 × 2 × 3 pattern with a motor;
   - the recipe type `advancedrocketrycommunity:cutting`;
   - wafers and circuits at 300 ticks × 100 FE;
   - planks from logs at 80 × 10. Data generation writes one recipe of six planks per
     vanilla log and stem type, and one for lightwood.
   - Recipes marked `sawmill` stop while the COMMON value `machines.sawmillVanillaWood`
     (default on) is off.
3. **JEI**: a category and catalyst for each machine, following the lathe's.
4. **Data**:
   - models, original textures, loot, crafting recipes for the controllers, the blast
     brick and the saw blade;
   - en_us and zh_cn names.

   All through data generation.
5. Fix only what you must in the W01 runtime to support the two machines, and explain each
   change in your record. Root is reviewing W01 in parallel. If you find a W01 defect,
   record it with a reproduction instead of reworking W01's design.

## Tests (required)

- JUnit: the recipe data (counts, energy, times, the plank recipe per log type) and the
  sawmill switch.
- GameTests, for each machine:
  - formation in all four rotations, and refusal with a wrong part;
  - one recipe end-to-end through the hatches, with exact energy and item counts (steel
    from iron and charcoal; one wafer or circuit recipe; one plank recipe);
  - a full-output pause without loss.
- The sawmill switch off stops a plank recipe without loss.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- The C16c machines (crystallizer, chemical reactor, laser etcher, centrifuge).
- Changing W01's removal or ownership rules.
- The rolling machine, precision assembler or electrolyzer.
- Status files and the ledger.
