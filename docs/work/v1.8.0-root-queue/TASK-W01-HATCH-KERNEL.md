# TASK W01: classic hatch family, controller and the first lathe

Root: Claude. Date: 2026-10-11. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w01-hatch-kernel` |
| Branch | `codex/v1.8.0-hatch-kernel` (local only; Root merges) |
| Ledger plans | C16a (hatches and controller), C16b (the lathe) |
| Record | `docs/work/v1.8.0-root-queue/records/W01.md` |

## Why

The seven classic machines (C16b and C16c) all need the hatch family and a controller
on the v1.2 multiblock kernel. On 2026-10-11 the owner chose to build them as the
accepted ADR-064 describes, with Forge events. The proposed ADR-068 (hooks on
vanilla's internal placement and removal calls) and the native/JDI observation work
are **not** prerequisites. Do not implement ADR-068 and do not add debugger probes.

## Goal

A survival player can build a lathe from the legacy layout and run it through
hatches. Item and Fluid resources stay with the controller and are never lost or
duplicated.

## Read

- ADR-064 (`docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md`):
  section 1 (machine family) in full, section 2 (tag ingredients), section 8 (lathe
  part), and sections 11 to 14.
- The v1.2 kernel: `machine/multiblock/**` (patterns, formation lifecycle),
  `machine/process/**`, and how `machine/rolling` and `machine/precision` use them.
  `data/advancedrocketrycommunity/machine_patterns/rolling_machine.json` is a pattern
  example.
- Existing unregistered classic code: `machine/classic/resource/**` (controller-owned
  banks) and `machine/classic/adapter/**`. Reuse the bank model, codecs, lathe recipe
  rows and pattern policy if they fit. The canonical-hash, guard-ticket and native
  observation classes are optional. Leave them in place, and do not wire them in
  unless you need them.

## Scope

1. **Hatch family** (ADR-064 §1.2): `item_input_hatch`, `item_output_hatch` (4 slots),
   `fluid_input_hatch`, `fluid_output_hatch` (16,000 mB), `power_input_plug`
   (10,000 FE).
   - Blocks, block entities and items; registry entries.
   - Item and Fluid hatches are facades over the controller's banks. They expose a
     capability only while bound to a loaded, formed controller, and not during a
     transaction.
   - The power plug stores its own FE.
   - At most 64 hatches per controller; one controller per hatch, and a conflict
     refuses formation.
2. **Removal rules** (ADR-064 §1.2):
   - Breaking an Item hatch drops its bank once.
   - Breaking a Fluid hatch keeps its bank at the controller for a rebuilt hatch.
   - Breaking the controller drops all Items once and drains the Fluids.
   - When the controller is unloaded or its data is unsupported, refuse removal of a
     bound hatch for players (`BlockEvent.BreakEvent`), explosions
     (`ExplosionEvent.Detonate`) and Forge-aware entity destruction. Never load the
     controller's chunk.
   - Hatches and controller cannot be pushed by pistons.
3. **Lathe** (ADR-064 §8, §1.3, §1.4):
   - Controller block and the pattern `machine_patterns/lathe.json` with the legacy
     2 × 1 × 4 layout and four rotations.
   - Recipe type `advancedrocketrycommunity:lathe` with the bounded codec.
   - The legacy lathe recipes as data.
   - One recipe at a time: energy per tick from the plugs, and inputs and outputs
     moved in one transaction at the end.
   - It pauses on redstone, a full output, a missing hatch or an unloaded hatch chunk.
   - Progress survives unload and restart.
   - Use the per-tick match budgets of §1.3.
4. **Menu** (ADR-064 §1.5 and §14): a server-authoritative controller screen showing
   formed state, progress, energy and the reason it waits. There are no client actions
   besides opening.
   - Add a JEI category only if the repository already has JEI integration you can
     follow. Otherwise leave JEI for later and note it.
5. **Data**: blockstates, models, original textures, loot tables, crafting recipes for
   the hatches, plug and lathe controller, plus en_us and zh_cn names. All through data
   generation.

## Tests (required)

- JUnit for pure logic you add (codec limits, recipe matching, bank transfers).
- GameTests:
  - formation in all four rotations, and refusal with a wrong part;
  - one lathe recipe end-to-end, from the input hatch through to the output hatch,
    with exact energy and item counts;
  - redstone pause;
  - full-output pause without loss;
  - Item hatch break drops its bank exactly once;
  - Fluid hatch break keeps its bank and a rebuilt hatch reaches it;
  - controller break drops everything once;
  - break refused while the controller is unavailable (use the lifecycle manager's
    unload notifications, as the precision unload tests do);
  - progress kept across a save/load of the block entities.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- The other six machines (arc furnace, cutting machine and C16c). They follow in later
  tasks on top of this kernel, so keep the controller generic: machine kind, pattern
  and recipe type are parameters.
- Changing the rolling machine, precision assembler or electrolyzer ports.
- ADR-068, native/JDI probes, the CME investigation, and status files or the ledger.
