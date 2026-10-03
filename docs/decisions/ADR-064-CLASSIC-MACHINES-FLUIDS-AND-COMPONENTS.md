# ADR-064 — Classic machines, fluids and components (C16)

```yaml
status: PROPOSED
revision: 1
date: 2026-10-03
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.8.0
slices: [C16a, C16b, C16c, C16d]
development_dependency: ADR-016, ADR-019, ADR-025, ADR-051, ADR-052, ADR-054, ADR-061, ADR-062, ADR-063
supersedes: ""
```

## Context

C16 delivers the classic machines every later batch crafts with: the machine
family with power, fluids, a tank and a pump (C16a), the electric arc furnace,
lathe and cutting machine (C16b), the crystallizer, chemical reactor, precision
laser etcher and centrifuge (C16c), and the circuit components, the recipe graph
tool and a progression rebalance (C16d). The 55 C16 ledger rows name the units.
Legacy facts, read from the pinned upstream commit (`c5cd5af`) as facts only:

- **Machines.** Each legacy machine is a LibVulpes `TileMultiblockMachine`: a
  controller block in a fixed structure of hatches (item input `I`, item output
  `O`, fluid input `L`, fluid output `l`, power input `P`), casings, motors and
  machine-specific parts (`tile/multiblock/machine/*.java`). Layouts, as
  layers × rows × columns: arc furnace 5 × 5 × 5 (a blast brick shell around
  coils and air, its hatches on the roof and the front); lathe 2 × 1 × 4;
  cutting machine 1 × 2 × 3 (a motor and a saw blade); crystallizer 2 × 2 × 3
  (six quartz crucibles over the hatches); chemical reactor 2 × 2 × 3 (a
  motor); precision laser etcher 3 × 3 × 3 (slabs, two structure towers, a
  vacuum laser, a motor); centrifuge 4 × 3 × 3 (advanced structure blocks,
  registered as `casingCentrifuge`, and a motor).
  A structure accepted any motor tier (`LibVulpesBlocks.motors`) and any coil
  (`blockCoil`).
- **Recipes** (`assets/advancedrocketry/recipes/*.json`, plus code in
  `util/RecipeHandler.java` and the machines' `registerRecipes`), as time in
  ticks × energy per tick:
  - arc furnace: steel from an iron ingot and charcoal (6,000 × 1); silicon
    ingot from sand (12,000 × 1); 3 titanium aluminide from 7 aluminum and
    3 titanium ingots (9,000 × 20); 2 titanium iridium from an iridium and a
    titanium ingot (3,000 × 20);
  - lathe: 2 rods from an ingot of each material (300 × 20);
  - cutting machine: 4 silicon wafers from a silicon boule, 4 basic circuits
    from a basic circuit plate, 4 advanced circuits from an advanced circuit
    plate (300 × 100); 6 planks from a log, including lightwood (80 × 10);
  - crystallizer: a gem from its dust, a boule from an ingot and a nugget
    (300 × 20); it stops above `crystalliserMaximumGravity` (default 0,
    disabled);
  - chemical reactor: 20 mB rocket fuel from 10 mB oxygen and 10 mB hydrogen
    (100 × 10); 8 bone meal from a bone and 10 mB nitrogen (100 × 1); a
    refreshed carbon scrubber cartridge and charcoal (40 × 20); a space suit or
    armor piece with the space protection enchantment from the piece, a pipe
    sealer and four titanium aluminide sheets (100 × 10, code);
  - precision laser etcher: 2 basic circuit plates from a gold plate, redstone
    dust and 4 wafers (1,200 × 400), 2 advanced plates with a redstone block
    (1,200 × 600), each with the etcher lens kept;
  - centrifuge: 1,000 mB of enriched lava into 1,000 mB of lava and up to four
    nuggets by chance (200 × 10), the chances from `lavaCentrifugeOutputs`
    (copper, iron, tin, lead, silver 100, gold 75, diamond, uranium 10,
    iridium 1);
  - eleven XML files in the config folder overrode or added recipes.
- **Fluids** (`AdvancedRocketry.java` 718–760): oxygen, hydrogen and nitrogen
  are gaseous (density −1,000, viscosity 1,000); rocket fuel (density 800,
  viscosity 1,500, light 2); enriched lava (density 3,000, viscosity 6,000,
  1,300 K, light 15). Each had a world block and a bucket; the gases rose.
  Volcanoes held enriched lava in their conduit and in a bulb of radius 23
  under it (`MapGenVolcano` 55–75); it was the centrifuge's input.
- **Tank** (`BlockPressurizedFluidTank`, `TileFluidTank`): 64,000 mB times
  `blockTankCapacity` (default 1.0); a tank passes its fluid down into the tank
  below; the item keeps the fluid.
- **Pump** (`TilePump`): a 16,000 mB tank and an energy buffer of 1,000; it
  looks straight down to the first non-air block and, if that is a Forge fluid
  block, searches connected blocks of that fluid up to 64 blocks away with no
  bound on the search, drains one source block per operation (100 energy),
  every tick above half power and every ten ticks below, and pushes up to
  1,000 mB per tick into neighbours. It checks no protection, and vanilla
  water and lava were not Forge fluid blocks, so it did not pump them.
- **Power.** A legacy install had the LibVulpes coal generator; Advanced
  Rocketry itself makes no power before the solar generator (C17c).
- **LibVulpes** (motors, casings, hatches, the coal generator) is not an
  approved source in v1.8 (ADR-061 §4.1); its numbers and art are not used.

The project already has the v1.2 machine kernel (ADR-016, ADR-019): bounded
process definitions and transactions, data patterns for multiblocks, physical
ports, the rolling machine, the precision assembler and the electrolyzer, the
`machine_casing` block, the five coil blocks (C15a) and `endgame_casing`.
Hydrogen and oxygen exist as canister items: the electrolyzer turns two empty
canisters and 1,000 mB of water into a hydrogen and an oxygen canister, the
gas giant mission yields hydrogen canisters, and oxygen vents and suits use
oxygen canisters. ADR-061 revision 7 (proposed) lets kernel machine recipes
name items only until C16a, which must restore tag ingredients.

## Decision

### 1. Machine family (C16a)

1. **Structure.** Every classic machine is a multiblock on the v1.2 kernel: a
   controller block, a data pattern `machine_patterns/<machine>.json` with the
   legacy layout (§8, §9), four rotations and no mirroring. A motor cell
   accepts any block of the tag `advancedrocketrycommunity:motors` (§4), a coil
   cell any block of `advancedrocketrycommunity:coils`, a casing cell
   `machine_casing`, and a legacy `*` cell a casing or any family hatch.
2. **Hatches.** Five family blocks fill the legacy hatch cells:
   `item_input_hatch` and `item_output_hatch` (four slots each),
   `fluid_input_hatch` and `fluid_output_hatch` (one fluid, 16,000 mB each)
   and `power_input_plug` (10,000 FE). A hatch holds its own resources (the
   rolling machine's physical-port model, ADR-019), binds to at most one formed
   machine at a time (the first to bind; a second machine that needs it does
   not form), serves automation only while its machine is formed and not
   running a step that holds the hatch, and drops its contents when broken.
   The rolling machine, precision assembler and electrolyzer keep their v1.2
   ports.
3. **Processing.** A machine runs one recipe at a time through the kernel:
   it looks up the first recipe whose inputs its input hatches hold, reserves
   nothing, draws the recipe's energy per tick from its plugs while running,
   and at the end moves inputs and outputs in one transaction, so a broken
   hatch or a full output pauses it without losing anything. Progress survives
   chunk unloads and restarts; unloading a hatch's chunk pauses the machine.
   A redstone signal at the controller pauses the machine, as it pauses the
   v1.2 machines.
4. **Recipe types.** One recipe type per machine
   (`advancedrocketrycommunity:arc_furnace`, `lathe`, `cutting`,
   `crystallizing`, `chemical_reacting`, `laser_etching`, `centrifuging`),
   with a bounded codec: at most 4 item inputs and 4 item outputs, 2 fluid
   inputs and 2 fluid outputs, counts up to 64, fluid amounts up to 16,000 mB,
   time 1–72,000 ticks, energy 0–10,000 FE per tick, and (centrifuge only)
   chance outputs with a weight of 1–100. Unknown fields fail the recipe.
5. **Menus and JEI.** Each controller has a server-authoritative menu with the
   formed state, progress, energy and the reason it waits; there are no client
   actions besides opening. Each machine has a JEI category in the client
   compat package.

### 2. Tag ingredients after binding (C16a)

This ends the deviation of ADR-061 revision 7 for every kernel machine recipe,
the v1.2 rolling, precision and electrolyzer recipes included:

1. An item ingredient may name a tag again. A recipe keeps its JSON form; its
   tags resolve on first lookup after the server's tags are bound, and again
   after each tag reload, never when the recipe is parsed. A tag that resolves
   to more than 32 items (`ProcessInput.MAX_VARIANTS`) disables that recipe
   with a logged error until the next reload; the other recipes keep working.
2. The client receives recipes in their JSON form and resolves them against
   its own synced tags, for JEI only.
3. A running process stores the recipe's ID and a signature built from the
   recipe's JSON form, not from its resolved items. After a reload with
   changed tags, a process whose recipe still exists keeps running, and its
   inputs are checked against the current resolution when it completes; inputs
   that no longer match pause the machine with a stated reason, nothing is
   consumed and the progress is kept (the C15aR2-I1 condition).
4. Built-in recipes use the common tags (`forge:ingots/titanium`, …) wherever
   a material is meant: the v1.2 rolling recipes move to tags in C16a.

### 3. Combustion generator (C16a)

`combustion_generator`, one block: it burns furnace fuel (vanilla burn times)
at 40 FE per tick into a 20,000 FE buffer and pushes up to 1,000 FE per tick
into adjacent energy receivers. A coal therefore gives 64,000 FE. It burns only
while its buffer has room; a fuel with a container (a lava bucket) leaves the
empty container in its slot, as a furnace does. It has a menu (fuel slot,
burn time, buffer) and lights up while burning. The numbers are new: the
LibVulpes generator is not a source (ADR-061 §4.1), and 40 FE per tick covers
one arc furnace or lathe; the etcher needs ten generators or another mod's
power, which the recipe graph (§10) counts.

### 4. Motors and casings (C16a)

1. **Motors.** Four blocks, `motor`, `advanced_motor`, `enhanced_motor` and
   `elite_motor`, in the block and item tag `advancedrocketrycommunity:motors`.
   Every machine accepts any tier, as legacy; the tiers are crafting parts of
   later recipes (rocket engines, the nuclear core). Recipes (new): a motor
   from a copper coil, two steel plates, two iron rods and a steel ingot; each
   higher tier from the tier below, a coil of the next metal (gold, titanium,
   iridium) and two plates of that metal.
2. **Casings.** `machine_casing` (v1.2) is the legacy structure block.
   `endgame_casing` is the legacy advanced structure block: its display name
   becomes "Advanced Machine Casing" and later recipes that named the legacy
   advanced casing name it.

### 5. Fluids and canisters (C16a)

1. **Fluids.** `oxygen`, `hydrogen`, `nitrogen`, `rocket_fuel` and
   `enriched_lava` are Forge fluid types with the legacy colours, density,
   viscosity, temperature and light. Rocket fuel and enriched lava have source
   and flowing world blocks and buckets; enriched lava sets entities on fire as
   lava does. The three gases have no world block and no bucket: they live in
   tanks, hatches and canisters (the legacy rising gas blocks have no 1.20.1
   counterpart without a custom fluid engine).
2. **Canisters.** A canister is a fluid container of exactly 1,000 mB:
   `empty_canister`, `hydrogen_canister`, `oxygen_canister` and a new
   `nitrogen_canister`. Filling an empty canister with 1,000 mB of a gas
   swaps it for that gas's canister; emptying one returns an empty canister
   (the Forge item fluid handler, as a bucket). Tanks, fluid hatches and
   machines therefore exchange gases with canisters, and existing canisters
   keep their meaning for vents and suits (ADR-025).
3. **Nitrogen source.** A gas harvest table for Tau Ceti f (breathable,
   1 atm) yields nitrogen and oxygen canisters (8 and 2 per 1,000 ticks), a
   data revision of ADR-052's tables, so nitrogen has a source.

### 6. Pressurized tank (C16a)

`pressurized_tank`: one fluid, 64,000 mB times the COMMON value
`machines.tankCapacityMultiplier` (default 1.0, range 0.25–4.0, read when a
tank is created or loaded; a tank holding more than its capacity keeps the
fluid and accepts none). Buckets and canisters fill and empty it; it exposes
the fluid to automation on every side. When it changes, it pulls the same fluid
from the tank directly above it until it is full (one neighbour, no column
scan). The item keeps the fluid and its amount, saved with a schema version.

### 7. Pump (C16a)

`pump`: a 16,000 mB tank, 10,000 FE buffer, 100 FE per source block. It looks
straight down at most 64 blocks to the first non-air block; if that is a
fluid, it searches connected blocks of that fluid breadth-first within 32
blocks horizontally and 64 vertically, at most 4,096 blocks per search and 64
per tick, through loaded chunks only. It drains one source block per
operation, every 5 ticks while it has the energy, posting a standard block
break event as its owner first: a cancelled event skips that block and stops
the search. It never loads chunks, keeps no cache across unloads, pumps
vanilla water and lava (they are bucket fluids in 1.20.1) and pushes up to
1,000 mB per tick into adjacent fluid receivers. The owner is the player who
placed it; a pump without an owner (placed by a machine) drains nothing.

### 8. Arc furnace, lathe and cutting machine (C16b)

1. **Electric arc furnace.** The legacy 5 × 5 × 5 layout of `blast_brick`
   (new block: hardness 3, blast resistance 6), coils and air; the legacy
   recipes and numbers (§ Context). Steel needs charcoal, as legacy.
2. **Lathe.** The legacy 2 × 1 × 4 layout; 2 rods from one ingot of each
   material with rods (C15a, iron included; 300 ticks × 20 FE).
3. **Cutting machine.** The legacy 1 × 2 × 3 layout with a motor and
   `saw_blade` (new block); wafers and circuits (300 × 100) and planks from logs
   (80 × 10): DataGen writes one recipe of six planks per vanilla log and stem
   type and one for lightwood. Recipes marked `sawmill` stop while the COMMON
   value `machines.sawmillVanillaWood` (default on) is off. The iron saw blade
   item (`iron_saw_blade`) is the crafting part of the block.

### 9. Crystallizer, chemical reactor, etcher and centrifuge (C16c)

1. **Crystallizer.** The legacy 2 × 2 × 3 layout with six `quartz_crucible`
   blocks, each crafted from a `quartz_crucible_shell` item (the legacy
   `iquartzcrucible`); a gem from its dust and a boule from an ingot and a
   nugget (300 × 20): dilithium dust to dilithium crystal, silicon ingot and
   nugget to a silicon boule. The COMMON value
   `machines.crystallizerMaximumGravity` (default 0 = no limit, range 0–10)
   stops it where the gravity at the controller exceeds the value: the body's
   gravity multiplier, or a station's gravity as the area gravity rules
   resolve it (ADR-058).
2. **Chemical reactor.** The legacy 2 × 2 × 3 layout; rocket fuel and bone
   meal with nitrogen with the legacy numbers. The carbon cartridge refresh
   waits for C18a's cartridge and the space protection recipe for C18b's
   enchantment; those batches add them to this recipe type.
3. **Precision laser etcher.** The legacy 3 × 3 × 3 layout with slabs (any
   block of `minecraft:slabs`); the legacy structure towers and vacuum laser
   were redesigned earlier (the tower into the rocket assembler in v0.5, the
   vacuum laser into the advanced casing and the laser lens in v1.7, ADR-055),
   so `machine_casing` fills the tower cells and `endgame_casing` the laser
   cell. The two circuit plate recipes keep the legacy numbers; the etcher lens
   is the v1.7 `laser_lens`, an input that stays in its hatch, as legacy.
4. **Centrifuge.** The legacy 4 × 3 × 3 layout with `endgame_casing` (the
   legacy `casingCentrifuge` was the advanced structure block);
   1,000 mB of enriched lava into 1,000 mB of lava and up to four nuggets by
   chance (200 × 10), the chances in the data recipe, not a config list: the
   legacy weights over the nuggets this mod and vanilla register (copper 100,
   iron 100, tin 100, gold 75, iridium 1); data packs add other mods' nuggets.
5. **Enriched lava source.** As legacy (`MapGenVolcano` 55–75, the conduit
   and the bulb under it), volcanoes hold enriched lava: from C16a the Venus
   volcano (ADR-063 §5) places enriched lava instead of lava in its core and
   crater pool, in volcanoes generated afterwards. A pump on a crater pool
   drains it for the centrifuge. Volcanoes generated before keep vanilla lava.

### 10. Components, recipe graph and rebalance (C16d)

1. **Components.** Basic and advanced circuit plates, the basic and advanced
   circuits, the tracking circuit, the control, item IO and liquid IO circuit
   boards, the user interface and the carbon brick, as items with the legacy
   recipes (machine and crafting), named by the legacy roles. The carbon brick
   is the legacy `ingotCarbon` and gets the tag `forge:ingots/carbon`.
2. **Recipe graph tool** (ADR-061 §5.3): a JUnit check over the generated data
   and the code-registered recipes that every registered item is obtainable
   in survival from a new world, counting energy (the combustion generator),
   Level access and discovery. Its report lists each item's first route. C16d
   also runs it with the Tau Ceti Levels removed (ADR-063 revision 6).
3. **Rebalance.** Existing v1.x recipes that the classic chain supersedes
   (for example the v1.2 precision recipes that stand in for circuits) move to
   the classic components where the graph shows a route; each change is listed
   in the CHANGELOG.

### 11. Persistence and migration

Machines, hatches, the generator, the tank and the pump save schema version 1.
A machine saves its recipe ID, signature and progress; hatches, the tank and
the pump their resources. Unformed machines keep their hatches' contents. No
existing block, item or saved data changes ID; the v1.2 rolling recipes keep
their IDs when they move to tags.

### 12. Generated data and assets

DataGen writes every new model, blockstate, loot table, recipe, tag, pattern
and translation into the v1.8 root. Every C16 texture is drawn new (ADR-061
§4.2): the legacy machine textures and OBJ models are references only; the
asset plan rules for them become `REGENERATE`.

### 13. Tests

- A0: recipe codecs (bounds, unknown fields, chance weights), tag resolution
  after binding and the 32-variant limit, signatures from the JSON form, the
  canister fluid handler, the tank's capacity rule, the pump's search bounds,
  the generator's numbers; pattern JSON audits; DataGen determinism; the recipe
  graph (C16d).
- A1: each machine forms, runs a recipe, pauses on a full output or a removed
  hatch without loss, resumes after a restart and refuses a hatch bound to
  another machine; tag recipes run after a reload that changes the tag; the
  generator powers a machine; the tank passes fluid down and keeps it as an
  item; the pump drains a pool within its bounds, skips a protected block and
  stops at unloaded chunks; canisters fill and empty; the crystallizer stops
  above its gravity limit; the centrifuge's chances over 10,000 runs.
- S1: a packaged dedicated server runs each machine and the pump in a world
  upgraded from v1.7, with the existing rolling, precision and electrolyzer
  machines keeping their state.

## Alternatives

### A. Port the LibVulpes hatches and generator

Keeps the legacy look, but LibVulpes is not an approved source (ADR-061 §4.1),
and a LibVulpes namespace would collide with a real port.

### B. Gases as rising world fluids

Restores placeable oxygen, but 1.20.1 fluids flow down; a rising fluid needs a
custom fluid engine for little gameplay.

### C. Machine-specific ports for every machine

Keeps the v1.2 port model, but needs twenty-eight port blocks for seven
machines; the shared hatches match the legacy and stay bounded by binding.

## Consequences

- The classic machines and their components exist before propulsion (C17) and
  life support (C18).
- Kernel recipes accept tags again; other mods' materials work in every machine.
- Gases are container fluids; canisters become 1,000 mB containers.

## Revisit when

- the owner prefers machine-specific ports or a different power source;
- a machine exceeds its tick budget;
- a later batch needs gases in the world.

## Review history

- Revision 1 (proposed, 2026-10-03): the C16 contract, before its
  independent review.
