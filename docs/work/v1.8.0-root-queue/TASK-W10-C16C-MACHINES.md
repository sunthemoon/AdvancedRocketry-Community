# TASK W10: crystallizer, chemical reactor, precision laser etcher and centrifuge

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w10-c16c-machines` |
| Branch | `codex/v1.8.0-w10-c16c-machines` (local only; Root merges) |
| Ledger plan | C16c (`centrifuge`, `chemicalReactor`, `crystallizer`, `precisionlaseretcher`, `quartzcrucible`, `iquartzcrucible`, their configuration, recipe XML and JEI rows) |
| Record | `docs/work/v1.8.0-root-queue/records/W10.md` |

## Goal

The four remaining classic machines run on the merged classic kernel exactly as the lathe,
arc furnace and cutting machine do. The centrifuge's chance outputs are retained and never
re-rolled.

## Read

- ADR-064 (`docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md`):
  - section 9 in full: it is the specification;
  - section 1 (machine family);
  - section 2 (tag ingredients);
  - the Context section for the legacy numbers;
  - sections 11 to 13.
- The merged kernel: `machine/classic/runtime/**`, and the records
  `docs/work/v1.8.0-root-queue/records/W01.md`, `W01F.md`, `W01G.md` and `W05.md`. W05 shows
  how a new machine is added as a kind with its own pattern, recipe type and data,
  without forking the runtime. Follow it.
- The `machine_clearance` tag (W01G) for legacy null cells.

## Scope

1. **Crystallizer** (§9.1):
   - the 2 × 2 × 3 layout with six `quartz_crucible` blocks, each crafted from a
     `quartz_crucible_shell` item;
   - recipe type `crystallizing`: dilithium dust to dilithium crystal; silicon ingot and
     nugget to a silicon boule; 300 × 20;
   - the COMMON `machines.crystallizerMaximumGravity` (0 to 10, default 0 meaning no limit),
     measured at the controller from the body or the containing station, never from an
     area field or a player.
2. **Chemical reactor** (§9.2):
   - the 2 × 2 × 3 layout;
   - recipe type `chemical_reacting`: rocket fuel, and bone meal with nitrogen, with the
     legacy numbers.
   - The carbon cartridge and the space-protection recipes wait for C18a and C18b. Leave the
     recipe type ready for them.
3. **Precision laser etcher** (§9.3):
   - the 3 × 3 × 3 layout: slabs (any `minecraft:slabs`), `machine_casing` in the tower
     cells, `endgame_casing` in the laser cell;
   - recipe type `laser_etching`: the two circuit-plate recipes with the legacy numbers;
   - the `laser_lens` is an input that stays in its hatch, through a same-channel debit and
     restoration in the same transaction.
4. **Centrifuge** (§9.4):
   - the 4 × 3 × 3 layout with `endgame_casing`;
   - recipe type `centrifuging`: 1,000 mB of enriched lava gives 1,000 mB of lava, plus up to
     four nuggets by chance; 200 × 10;
   - the chance list is separate, at most 16 entries, in JSON order: copper 100, iron 100,
     tin 100, gold 75, iridium 1;
   - one independent roll from 1 to 100 per candidate, stopping after four successes;
     identical items merge within stack limits;
   - the seed comes from the machine UUID, the recipe signature and the batch ordinal;
   - the chosen outputs are recorded with the transaction ID before they are applied, and
     are reused on a full output, a retry or a recovery;
   - follow the PREPARED rules exactly;
   - an ordinal overflow refuses a new batch.
5. **Enriched lava** (§9.5): check whether the Venus volcano already places enriched lava
   (ADR-063 §5 and C16a). If it does not, add it for newly generated volcanoes only.
6. **JEI**: a category and catalyst for each machine, following the lathe's.
7. **Data**:
   - models, original textures, loot and crafting recipes;
   - en_us and zh_cn names.

   All through data generation. Follow WORKER-RULES for new configuration keys, the exact
   configuration count and the log expectations.

## Tests (required)

- JUnit:
  - the recipe data for each machine;
  - the centrifuge chance selection (deterministic seed, the stop after four, merging,
    the iridium cap);
  - the codec bounds of the chance list.
- GameTests, for each machine:
  - formation in four rotations, and refusal with a wrong part;
  - one recipe end-to-end, with exact counts and energy;
  - a full-output pause without loss.
- Also:
  - crystallizer: refused above the gravity limit;
  - etcher: the lens stays in its hatch;
  - centrifuge: a full output, then a retry, gives the same chosen nuggets, and a
    save/load in the middle of a batch does not re-roll.
- The whole unit suite once. Each new class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- The C18a cartridge and the C18b space-protection recipes.
- Kernel ownership or removal rules (report a defect instead of reworking it).
- Status files and the ledger.
