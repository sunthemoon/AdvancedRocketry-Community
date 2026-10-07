# CL16D-COMPONENTS-GRAPH-01: components and survival recipe graph, contract draft 01

Date: 2026-10-07. Milestone: v1.8.0 / C16d. Status: **proposed, not frozen**.
Author: Claude (delegated worker, interactive session). Task:
[TASK-01](TASK-01.md) as published by Root at `90f257ff`
(SHA-256 `9081e58fd27f21e47366effc24555e54116b2b65995ca89a7271509258c821fa`).
Code basis: `a65dcbf68143ce63af3b2205b0c36af02eaae0e8` (the registered worktree base).

This draft proposes a component mapping, a bounded recipe-graph contract and the Root
integration surfaces they need. It changes no source, recipe, tag, registry, ledger row or
Gate, and it is not an implementation. Every value marked **OPEN** needs a Root input or an
owner decision before freezing. The companion [TEST-DESIGN-01](TEST-DESIGN-01.md) derives its
expected results from this text.

Terms used below:

- **Effective resource set**: the data files that the packaged main JAR contains after
  `processResources` applies the exact-path exclusions in `build.gradle:149-217`.
- **Producer**: anything that turns prerequisites into an item, a fluid or a capability
  (a recipe, a loot table on a reachable block, a world feature in an accessible Level, a
  machine behaviour declared in code, a mission table).
- **Capability**: a non-item prerequisite: a formed machine, Forge Energy at a rate, access to
  a Level, a discovery, a research balance.
- **Root item**: a vanilla item that the graph treats as available without a modelled producer,
  listed in a committed, reviewed root file.

## 1. Inputs read and their status

| Input | What it fixes | Path / identity |
| --- | --- | --- |
| ADR-061 §1.2, §1.3, §1.5, §2.2, §5.3 | flattened IDs, placeholder components, recipe changes never rewrite inventories, tags replace `makeMaterialsForOtherMods`, the graph tool and its rules | `docs/decisions/ADR-061-…md` lines 55-111, 424-462 |
| ADR-062 §6 | C16d scope and order: depends on C16b and C16c; C17a needs tracking circuits, C17b circuits and the user interface, C17c beacon circuits, C18a the user interface and carbon brick, C18c depends on C16d | `ADR-062-…md:172-188` |
| ADR-063 §1, §4, Tau Ceti paragraph | steel/alloys/boules wait for C16b/C16c; iridium only from the Moon and Mars; both Tau Ceti Levels reachable only after an interstellar warp; every C16-C18 output must stay reachable without them | `ADR-063-…md:112-115, 231-239, 454-460` |
| ADR-064 Context, §1-§3, §8-§10 | legacy machine/recipe numbers, tag ingredients after binding, the combustion generator (40 FE/t), the etcher and cutting recipes, components, graph tool, no-Tau variant, rebalance rule | `ADR-064-…md:39-65, 165-219, 305-390` |
| ADR-066 §6.3 | graph extension for ground research and discovery; `unlit_torch` and effect-only IDs are the only listed exemptions; equipment, ground research machines and ordinary recipe outputs are never exempt | `ADR-066-…md:765-778` |
| ADR-035, ADR-037, ADR-043, ADR-044 | navigation planner is the authority for routes and fuel; final-destination discovery gate; Tau Ceti only by station warp | `docs/decisions/` |
| Content ledger | the ten assigned rows | `docs/work/v1.8.0-content-ledger.csv:257, 500, 502-506, 514-515, 560` |
| Legacy manifest | legacy recipe file names, types and SHA-256 (bodies are **not** local) | `legacy-manifest/recipes.csv` |
| Legacy Java, pinned `c5cd5af` | `itemIC`, `itemCircuitPlate`, `itemMisc` registration; `ingotCarbon` = `misc:1`; `makeMaterialsForOtherMods` semantics | Temp copies hash-equal to `legacy-manifest/java-files.csv` (§8) |
| Prior read-only research | steel has no first source; press, rod, coil and generator routes exist; quartz is a conditional root | `docs/work/v1.8.0-c16a-hatches/SURVIVAL-FEASIBILITY-01.md`; `D:/ARCE-Task-Evidence/v1.8.0/acad-649b0a9bb2/FEASIBILITY-01.md` (SHA-256 `af5c6dfe…52cc`); `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16d-acquisition-leaf-20261006-462b80/PROPOSAL-01.md` |
| Current registry and DataGen | seven item `DeferredRegister`s; three kernel recipe types plus the press type; no C16b/C16c recipe types yet | `registry/ModItems.java`, `registry/ModRecipes.java`, `material/MaterialContent.java`, `datagen/ModRecipeProvider.java`, `datagen/PrecisionAssemblerRecipeProvider.java` |

The earlier research is planning evidence. This draft relies on it only where the cited
source line was re-read at the base commit.

## 2. Component mapping for the ten assigned units

Proposed modern IDs follow ADR-061 §1.2 (one ID per legacy variant, `snake_case`) and reuse the
two example names that ADR-061 §1.2 already fixes (`tracking_circuit`, `item_io_board`).

| Ledger unit | Legacy fact | Proposed modern role and ID | Current producer | Legacy producers (body status) | Notes |
| --- | --- | --- | --- | --- | --- |
| `item_variant:ic/1` | "Tracking Circuit", `ic` meta 1 (`AdvancedRocketry.java:479, 542`) | `advancedrocketrycommunity:tracking_circuit` | none | precision assembler `trackingcircuit.json` (`a17dd4c6…06ee8`, body OPEN) | consumed by C17a engines and C17c beacon (ADR-062 §6) |
| `item_variant:ic/3` | "Control Circuit Board" | `advancedrocketrycommunity:control_board` (**OPEN**: or `control_circuit_board`) | none | crafting `controlcircuitboard.json` (`6a234e9c…d3ee`), precision `controlcircuitboard_prec.json` (`699b8026…c59f`), bodies OPEN | needed by the lathe and other classic machines (SURVIVAL-FEASIBILITY-01) |
| `item_variant:ic/4` | "Item IO Circuit Board" | `advancedrocketrycommunity:item_io_board` (ADR-061 example) | none | crafting `iocircuitboard.json` (`e48a9109…81dc`), precision `iocircuitboard_prec.json` (`617175a6…f9f`), bodies OPEN | item hatches |
| `item_variant:ic/5` | "Liquid IO Circuit Board" | `advancedrocketrycommunity:liquid_io_board` | none | crafting `liquidiocircuitboard.json` (`97bc684d…cdad`), precision `liquidiocircuitboard_prec.json` (`bc3c7763…57dca3`), bodies OPEN | fluid hatches |
| `item_variant:itemCircuitPlate/0` | "Basic Circuit Plate", `itemCircuitPlate` meta 0 (`:478, 541`) | `advancedrocketrycommunity:basic_circuit_plate` | none | etcher `basiccircuitplateetcher.json` (`f61ed418…820f`): 2 plates from a gold plate, redstone and 4 wafers, 1,200 ticks × 400 FE/t, lens kept (ADR-064 Context); precision `basiccircuitplate.json` (`15fa9515…e7`, body OPEN) | cut into 4 basic circuits (300 × 100) |
| `item_variant:itemCircuitPlate/1` | "Advanced Circuit Plate" | `advancedrocketrycommunity:advanced_circuit_plate` | none | etcher `advcircuitplateetcher.json` (`665847a4…61bb`): 2 plates with a redstone block, 1,200 × 600 (ADR-064 Context); precision `advcircuitplate.json` (`190d3ea5…305e`, body OPEN) | cut into 4 advanced circuits |
| `item_variant:misc/0` | "User Interface", `misc` meta 0 (`:480, 573`) | `advancedrocketrycommunity:user_interface` | none | crafting `userinterface.json` (`dc07f355…e599`, body OPEN) | C17b warp controller, C18a devices |
| `item_variant:misc/1` | "Carbon Brick", `misc` meta 1; registered as `ingotCarbon` (`:587`) | `advancedrocketrycommunity:carbon_brick`, item tag `forge:ingots/carbon` (ADR-064 §10.1) | none | crafting `charcoalbrick.json` (`28ec6e6c…62d2`, shapeless, body OPEN) | C18a scrubber cartridge; legacy chemical reactor cartridge refresh also returns charcoal |
| `material:Carbon` | LibVulpes material "Carbon", product `ingot` only (`legacy-inventory.json:5058-5066`) | **the same item** `carbon_brick`; no second item, no other carbon products | — | — | ledger note "legacy ingotCarbon is the carbon brick" |
| `config:CATEGORY_GENERAL.makeMaterialsForOtherMods` | default `true`; when on, `registerOre` collects other mods' ore-dictionary product names so the legacy machines also made plates, rods and other products for materials AR did not use (`ARConfiguration.java:413`, `AdvancedRocketry.java:1244-1257`) | no configuration key; recipes consume common tags (ADR-061 §2.2) | tag ingredients already used by the v1.8 rolling and precision copies | — | see §2.2 |

Both `misc/1` and `material:Carbon` therefore point at one registry ID. A delivery record must
list both unit IDs and the one modern ID as whole tokens (ADR-061 §5.4).

### 2.1 Proposed item properties

- Plain `Item` instances (no block, no NBT kind), stack size 64, in the existing materials or
  components creative tab chosen by Root. None of them is a `DevelopmentComponentItem`.
- `carbon_brick` is **not** a furnace fuel unless the legacy body proves otherwise (**OPEN**,
  pending the `charcoalbrick.json` body and any legacy fuel handler). Inventing a burn time would
  add an FE source to the graph.
- No tags beyond `forge:ingots/carbon` are proposed. A `advancedrocketrycommunity:circuits/*`
  tag membership for the new boards is not proposed: the existing `circuits/basic` and
  `circuits/advanced` tags name exactly one item each, and widening them would change every
  recipe that consumes them.
- Textures: the asset plan lists `textures/items/*circuitplate.png`, `trackingcircuit.png`,
  `controliocircuit.png`, `itemiocircuit.png`, `liquidiocircuit.png` and `userinterface.png` as
  `IMPORT` for C16d (`v1.8.0-asset-plan.csv:242-247`), and `carbonbrick.png` as `EXCLUDE`
  (vanilla-derived, `:79`), so the carbon brick needs a `NEW` drawing. Import and provenance stay
  with Root and the asset review; this draft approves nothing.

### 2.2 `makeMaterialsForOtherMods`

Accepted ADR-061 §2.2 says common-tag recipes "replace the legacy `makeMaterialsForOtherMods`
switch". This draft evaluates that mapping instead of adding a boolean:

- **Covered:** our machines accept other mods' members of a tag that a built-in recipe names
  (for example another mod's `forge:ingots/titanium` in the rolling machine), and our products
  carry common tags so other mods accept them. The v1.8 rolling and precision copies already
  use tags (`src/generated/v1.8/.../recipes/rolling_aluminum_plate.json` names
  `forge:ingots/aluminum`).
- **Not covered:** the legacy switch also generated machine recipes for materials that AR did
  not define (for example plates of another mod's metal). Tags do not do this. A data pack can
  add such recipes.
- **Proposed disposition:** `REDESIGNED` with no configuration key, evidence = the tag-based
  recipe catalog of every kernel and classic machine type, once the seven C16b/C16c recipe
  types exist and use tags. The behaviour gap above is disclosed in the batch notes.
  **OPEN (owner):** confirm that dropping recipe generation for undefined materials is the
  accepted player impact, or require a different disposition under ADR-062 §7.
- Graph consequence: external tag members never count as roots in the clean-world graph (§4.3).

### 2.3 Component dependencies that are not assigned units

The graph and the C16d delivery also depend on, but this task does not own:

- `basic_circuit` (`ic/0`) and `advanced_circuit` (`ic/2`), delivered in v0.1.0 with crafting
  stand-ins; ADR-064 Context gives their classic cutting-machine recipes.
- `silicon_wafer`, cut from a silicon boule in the legacy game (`siliconwafer.json`,
  `f5f6dfcb…3e7`), a v0.1.0 crafting stand-in today (quartz + redstone → 2).
- The fourteen ADR-061 §1.3 placeholder components, which lose their placeholder tooltip and
  get classic recipes in C16d. Their recipes are a C16d obligation outside these ten units.
- The machines whose recipe types produce the plates and circuits: precision assembler
  (existing, `precision_assembling`, at most 5 inputs and 2 outputs,
  `PrecisionAssemblerChannels.java`), laser etcher (C16c), cutting machine (C16b), crystallizer
  and arc furnace (silicon chain).

## 3. Legacy payloads Root must supply before freezing

The legacy bodies of eleven recipe files are not available in any local, pinned, unpacked source
I could verify. Only their names, types and SHA-256 are committed. Before the mapping freezes,
Root (or a task permitted to inspect the pinned archive `AdvancedRocketry-c5cd5af….zip`,
SHA-256 `40eb5d436a021238df080cd39c9d7422851ffe495e3ac656d56ee5b21dcca01d`) records each body
with its hash check:

| Legacy file | Type | SHA-256 (legacy-manifest) | Needed for |
| --- | --- | --- | --- |
| `trackingcircuit.json` | precision assembler | `a17dd4c66b7cf05952a7db2ad4a9cbcd299e93460dd02aebf682325575b06ee8` | `tracking_circuit` |
| `controlcircuitboard.json` | crafting shaped | `6a234e9cae6ff502d6e243e2013389e34c9d75c3ea7589c12277d73ac40ad3ee` | `control_board` |
| `controlcircuitboard_prec.json` | precision assembler | `699b80267a894d60574fc9a54a383ce75470e4440c597129dea8b075a6ddc59f` | `control_board` |
| `iocircuitboard.json` | crafting shaped | `e48a9109867a831bfe5f577ae0b7f6083f55727698b17f4c7fba3f954a9d81dc` | `item_io_board` |
| `iocircuitboard_prec.json` | precision assembler | `617175a667c19d0c4ad2bd3e11c13b36a78247774e1a5ba1b1b17f7bf86bff9f` | `item_io_board` |
| `liquidiocircuitboard.json` | crafting shaped | `97bc684da69d199c53e38631f16a1086c6981fea853df4baef06db9a3d4fcdad` | `liquid_io_board` |
| `liquidiocircuitboard_prec.json` | precision assembler | `bc3c77632089f34e2bee7941a9e2ee6be9a27894e2fc9e89ec560653dd57dca3` | `liquid_io_board` |
| `basiccircuitplate.json` | precision assembler | `15fa951546181e68bb0221936939c35ee60d198ff1e73259b12e5dbc8f7db2e7` | `basic_circuit_plate` |
| `advcircuitplate.json` | precision assembler | `190d3ea59b7f5d0795dd4c1421132286434c2740b2c45e4e498e7bb5bb92305e` | `advanced_circuit_plate` |
| `userinterface.json` | crafting shaped | `dc07f355dc95072ba3a5d57fde2948a7dd6209ca69dd3277c30a3c6beb64e599` | `user_interface` |
| `charcoalbrick.json` | crafting shapeless | `28ec6e6ce7aaf732dc166765795844a56aefdaa85acb409670fbf1b693bb62d2` | `carbon_brick` |

The bootstrap analysis in §6 also needs the bodies of the machine recipes `cuttingmachine.json`
(`78a2c5d4…f6b9`), `precisionlaseretcher.json` (`f33fb946…7a24`),
`precisionassemblingmachine.json` (`d7491f53…cda`) and `rollingmachine.json` (`f2aa7cfe…5e4`),
plus the etcher and cutting circuit bodies whose numbers ADR-064 already quotes.

Mapping rules for those bodies, proposed:

1. Ore-dictionary names map to the ADR-061 §2.1 common tags (`ingotCopper` → `forge:ingots/copper`,
   `plateGold` → `forge:plates/gold`, `waferSilicon` → `advancedrocketrycommunity:silicon_wafers`).
   A legacy name with no accepted tag is **OPEN**, not guessed.
2. LibVulpes items in a body (for example a LibVulpes structure block or motor) map only through
   an accepted ADR mapping (ADR-064 §4: motors tag, `machine_casing`, `endgame_casing`); any other
   LibVulpes reference is **OPEN**.
3. A precision-assembler body that needs more than 5 item inputs or 2 outputs does not fit the
   existing type and needs a reviewed change, not a silent truncation.
4. Counts, shapes and machine time/energy keep the legacy values unless a rebalance item in §7
   proposes otherwise.

## 4. Graph model

### 4.1 Node and producer kinds

| Kind | Identity | Available when |
| --- | --- | --- |
| item | registry ID | any one producer of it is enabled, or it is a root item |
| fluid | registry ID | any one producer is enabled (`minecraft:water`, `minecraft:lava` are roots) |
| machine | the controller block ID plus "formed" | its controller item and every part in its pattern are available (multiplicities are recorded but not consumed, §4.6) |
| energy | `fe` plus the rate it can sustain | a reachable FE producer can meet the rate (§4.5) |
| level | dimension `ResourceKey` | the Level access rule in §4.7 holds |
| discovery | celestial body ID | §4.8 holds |
| research | owner-scoped research balance, as a capability "can earn research" | §4.8 holds |

A producer is enabled when **all** its prerequisites are available (AND). Each ingredient
selector inside a producer is satisfied when **any** one of its members is available (OR).
Quantities do not make a producer unavailable (§4.6).

### 4.2 Input extraction

1. **Effective resources.** The extractor reads the processed main resources that Gradle builds
   for the JAR (`build/resources/main` after `processResources`), not the individual
   `src/generated/*` roots. Gradle's exact-path exclusions (for example the v1.2 copies of
   `precision_control_circuit`, `precision_guidance_module` and `rolling_iron_bars`) are then
   already applied, and two files with the same resource path cannot both be counted. Root
   passes the directory as a test system property (proposed `arce.graph.resources`); a missing or
   empty directory fails the test, it is never treated as an empty catalog.
2. **Registered universe.** Plain JUnit here only sets Minecraft's bootstrap flag
   (`testsupport/MinecraftBootstrap.java:13-35`) and cannot run Forge registration, so the graph
   cannot read live registries. Proposal: a committed, sorted inventory
   `src/test/resources/recipe-graph/registered-content.txt` (items and fluids in the
   `advancedrocketrycommunity` namespace), read by the JUnit graph, plus a GameTest that compares
   it with `ForgeRegistries.ITEMS` and `ForgeRegistries.FLUIDS` in both directions and fails on
   any difference. This follows the existing `network-protocols.txt` pin pattern. A new item
   therefore cannot escape the graph by being absent from the inventory.
3. **Recipe JSON.** Every recipe in the effective set is read once. Each `type` needs a declared
   adapter (§4.4). An unknown type, an unreadable file, a file over 65,536 bytes
   (`ProcessDefinition.MAX_DEFINITION_BYTES`) or a missing field fails with a named diagnostic.
4. **Tags.** Item and fluid tags are read from the effective set merged with the Forge and
   Minecraft tag files on the test runtime classpath, with Minecraft's merge semantics
   (`replace`, nested `#tag`, `required: false` entries). A tag reference that cannot be
   resolved fails. Nesting deeper than 8 levels or a tag cycle fails.
5. **Code-registered recipes and code producers.** The base commit has **no** production
   code-registered recipe (`replaceRecipes` appears only in `gametest/MaterialGameTests.java:222`).
   Producers implemented in Java (the oxygen vent's canister swap, the press acting on a block,
   gas-harvest and asteroid missions, the laser drill, generators) enter the graph only through a
   committed producer table (§4.4) whose entries name their source class. **OPEN (Root):** whether
   to add a static diagnostic that lists production classes constructing project `ItemStack`s
   and fails on a class missing from the table. Without it, review of the table is the only
   guard.
6. **Loot tables.** Block loot tables in the effective set give the drops of a block that is
   available in an accessible Level or placed by the player. Supported conditions:
   `survives_explosion`, `match_tool` against a reachable tool (silk touch is a vanilla root
   capability), `random_chance` with a positive chance, `table_bonus`, `block_state_property`.
   Any other condition or function that can remove an entry is an unsupported blocker for that
   entry, never an implied success.
7. **World features.** A block placed by a feature in a biome of a Level counts once that Level
   is accessible (§4.7). The feature inventory comes from the effective worldgen data (biome
   modifiers, biome feature lists, configured and placed features). The Overworld ores of
   ADR-063 §4 are guarded by `worldgen.overworldOres`; the graph uses default configuration
   values only and records which switches it assumed (§4.9).

### 4.3 Roots

1. A committed root file `src/test/resources/recipe-graph/roots.json` lists every vanilla item or
   fluid that a modelled producer needs and the graph does not derive. Each entry has the ID, an
   acquisition class (`overworld_natural`, `overworld_crafted`, `nether`, `end`, `trade`,
   `capability`) and a one-line reason. Vanilla recipes, loot and structures are **not** modelled;
   the root file is reviewed instead.
2. Tag members from other mods are never roots. A selector whose only available members come from
   other mods is unavailable in the clean-world graph.
3. Project items are never roots. Creative-tab presence is not availability.
4. **OPEN (owner):** ADR-061 §5.3 says the roots are "vanilla survival items obtainable in the
   Overworld". Today `forge:gems/quartz` (only Nether quartz in vanilla 1.20.1) feeds
   `silicon_wafer`, and through it `basic_circuit`, `machine_casing` and every v1.2 machine. Two
   readings are possible: (a) any vanilla item a survival player can get starting from the
   Overworld, including the Nether and the End, or (b) only items found in the Overworld itself.
   Proposal: adopt (a) for the release assertion, record the class of every root, and always
   produce an additional diagnostic report under (b) so the Nether dependency stays visible.
   Under (b) today the casing chain would be unreachable.

### 4.4 Producer adapters

Each adapter turns one recipe type or producer class into producers with explicit inputs,
facility, energy and outputs. Proposed initial set:

| Adapter | Inputs | Facility | Energy | Notes |
| --- | --- | --- | --- | --- |
| `minecraft:crafting_shaped`, `crafting_shapeless` | each ingredient selector | none | none | container items (buckets) return their container |
| `minecraft:smelting`, `blasting` | ingredient | `minecraft:furnace` / `blast_furnace` root | furnace fuel: any root fuel | |
| `advancedrocketrycommunity:rolling` | ingredient, fluid | formed rolling machine | `energy_per_tick` | |
| `advancedrocketrycommunity:precision_assembling` | up to 5 inputs | formed precision assembler | `energy_per_tick` | |
| `advancedrocketrycommunity:electrolyzing` | empty canisters, water | formed electrolyzer | `energy_per_tick` | two outputs |
| `advancedrocketrycommunity:small_plate_press` | the block below | placed `small_plate_press` item, `minecraft:obsidian`, a redstone pulse root | none | in-world; the input block's item is consumed |
| the seven C16b/C16c types (`arc_furnace`, `lathe`, `cutting`, `crystallizing`, `chemical_reacting`, `laser_etching`, `centrifuging`) | per ADR-064 §1.4 codec | formed machine of that type | `energy_per_tick` | added with each machine; until then their outputs have no producer. Catalysts kept in the machine (the etcher lens) are prerequisites, not consumed inputs. Centrifuge chance outputs with weight ≥ 1 are producers of their item |
| block loot table | reachable placement or world feature | tool condition | none | §4.2.6 |
| world feature | accessible Level | none | none | §4.2.7 |
| pump | accessible Level holding the fluid source, `pump` item | FE ≥ 100 per source block | | enriched lava only from Venus volcanoes generated after C16a (ADR-064 §9.5) |
| bucket / canister fill | fluid + empty container | none | none | |
| gas harvest, asteroid, laser drill tables | mission or drill prerequisites (§4.8) | satellite, station, drill formed | as the system needs | from `gas_harvest/`, `asteroid_types/`, `laser_drill_tables/` |
| FE producers (§4.5) | fuel or environment | placed generator | — | produce the energy capability |

An adapter that reads a field it does not understand fails the run. Adding a new recipe type or
producer class without an adapter fails the run (§4.2.3).

### 4.5 Energy

1. The energy capability carries a sustained rate. A powered producer needs a rate at least its
   `energy_per_tick`.
2. The combustion generator gives 40 FE/t from any root furnace fuel and pushes up to 1,000 FE/t
   (`CombustionBurn.java:5-8`). Because generators are craftable from roots and stack side by
   side, the reachable sustained rate from combustion is bounded by how much one machine can
   accept, not by one generator: the graph computes `ceil(energy_per_tick / 40)` generators and
   records the number in the report (for example 10 for the 400 FE/t etcher recipe, ADR-064 §3).
3. The rate a machine can accept comes from its power input (the C16a `power_input_plug`, at most
   64 hatches per controller; v1.2 machines through their energy port). **OPEN (Root):** the
   per-plug and per-port receive limits are not frozen in the base code for the C16 family; the
   graph must read them from code constants once they exist and fail if the recipe rate exceeds
   the machine's total receive limit.
4. Other FE producers (solar generator, microwave receiver, black hole generator) are added with
   their own environment or capability prerequisites. No producer is assumed to supply FE before
   its item and environment are available, and no other mod's power is counted.
5. Buffered energy is not a rate: a recipe whose rate exceeds what the reachable producers can
   sustain is unavailable even if its total energy fits a buffer.

### 4.6 Quantities

The graph is an existence model: inputs, multiplicities, pattern part counts, fuel and FE totals
are recorded in the report but not consumed. Two exceptions are checked: a producer whose output
count is zero is not a producer, and a centrifuge chance with weight 0 is not a producer.
Quantity feasibility (for example 27 casings for the precision assembler) stays a playthrough
check (ADR-066 §6.3).

### 4.7 Level access

1. The Overworld is always accessible.
2. Another Level is accessible when a rocket that the server's planner rates `READY` for a route
   from an accessible Level to that destination can be built from available items, with an
   available fuel and, if the destination requires discovery (ADR-037), that discovery (§4.8).
3. The rocket requirement is computed with the existing pure-Java planner and route catalog
   (ADR-035), not a hand-written boolean: for each route the graph builds the smallest committed
   test blueprint (motor, tank, seat, guidance computer and their counts) and asks the planner for
   a quote. **OPEN (Root):** the planner entry point usable from JUnit, and whether blueprints are
   committed per route or derived. The blueprint's parts are AND prerequisites.
4. Station Levels: a station is accessible when the station deployment kit and its delivery
   rocket are available. A station warp (ADR-044) to another system needs the warp core, the
   discovery of the target and the warp energy (`stations.warpCostInterstellar`, default
   8,000,000 FE). Tau Ceti f and g are accessible only this way (ADR-063).
5. Landing rules do not change availability; a quote is not a landing guarantee (ADR-035), and the
   graph does not claim one.

### 4.8 Research and discovery

1. A discovery of body B is available when the data satellite path is available: satellite
   terminal, data satellite package and its builder inputs, a rocket able to reach orbit, and the
   research fee (100) earned by an earlier mission (ADR-037, ADR-063).
2. "Can earn research" is available when one research mission can be flown; the balance amount
   is a quantity and is recorded, not consumed.
3. ADR-066 §6.3 adds ground-survey edges: the C18c observatory and processor, power, surface or
   station access. They are added with C18c; the C16d graph declares the extension point and
   fails on an unknown producer instead of skipping it.
4. Visiting a body records its discovery (ADR-037), so a surface reachable without a discovery gate
   can still unlock research elsewhere. The graph models only the gates the server enforces.

### 4.9 Configuration

The graph runs with default COMMON values. It records every switch it depends on
(`worldgen.overworldOres`, `classic.smallPlatePress`, machine profile switches, the sawmill
switch). A run with a switch turned off is a separate diagnostic, not the release assertion.

## 5. Exemptions, cycles, failure and reporting

### 5.1 Exemptions

- Committed file `src/test/resources/recipe-graph/exemptions.json`, Root-owned, entries
  `{ "id", "category", "reason", "decision" }`.
- Allowed categories, from ADR-061 §5.3 and ADR-066 §6.3: `technical_block`, `fluid_block`,
  `force_field`, `unlit_torch`, `effect_only`, `creative_only`.
- The run fails when an exempted ID is not in the registered inventory, when an exempted ID is the
  output of any recipe or producer in the effective set (ordinary outputs are never exempt), or
  when an entry has no reason or decision reference.
- An exempted ID that turns out reachable is a report warning (the exemption may be removable), not
  a failure.

### 5.2 Fixpoint and cycles

1. Availability is the least fixpoint of the AND/OR rules starting from roots and the Overworld.
   Every available node therefore has a well-founded first route: no available node depends on
   itself. A cycle among available nodes is allowed (ADR-061: "a cycle is allowed only when every
   member also has an acyclic route", which the fixpoint guarantees).
2. After the fixpoint, unavailable registered items and fluids that are not exempt are failures.
   The report classifies each one:
   - **missing producer**: no producer outputs it;
   - **blocked**: producers exist, and the report lists the first unavailable prerequisite of each
     producer, in producer order;
   - **hard-locked cycle**: it belongs to a strongly connected component of the
     unavailable-node dependency graph in which no member has an enabled producer; the report
     lists the component and every prerequisite entering it from outside.
3. Order is deterministic. Producers are processed in rounds; inside a round in ascending
   producer-ID order (ASCII), then node-ID order. The first route of a node is the first producer
   that became enabled for it. Equal rounds tie on producer ID. Input file order, map iteration
   order and thread scheduling cannot change any output byte.

### 5.3 Variants

| Variant | Change | Assertion |
| --- | --- | --- |
| `release` | defaults, roots under reading (a) | every registered non-exempt item and fluid is available; no hard-locked cycle |
| `no_tau_ceti` | remove `tau_ceti_f` and `tau_ceti_g` from accessible Levels (and their routes, discovery targets and warp destinations) | every output of C16-C18 recipes and producers is available; items that become unavailable are compared with a committed expected list `no-tau-unavailable.txt`, each line a C15c optional item with a reason; any difference fails |
| `overworld_only_roots` | roots of class `overworld_natural` and `overworld_crafted` only | diagnostic report only (§4.3 OPEN) |

ADR-064 §10.2 names the no-Tau variant; ADR-063 requires "every C16-C18 output reachable without
them". The expected-unavailable list keeps new Tau-only content visible in review.

### 5.4 Report

Written to `build/reports/recipe-graph/<variant>.json` and `.md` (not committed; archived as
evidence), schema 1:

- input identities: SHA-256 of the sorted effective resource list and contents, the registered
  inventory, roots, exemptions and expected-unavailable files; base commit; variant; switches;
- per available node: first route (producer ID, inputs chosen, facility, energy rate and generator
  count, Level, discovery), round number;
- per unavailable node: classification and blockers as in §5.2;
- cycles found among available nodes (informational) and hard-locked components;
- exemptions applied, unused exemptions, counters and limits reached.

The Markdown report is a rendering of the JSON, sorted by node ID.

### 5.5 Limits

Proposed hard limits; exceeding one fails with a named diagnostic, never a truncated graph:

| Limit | Value | Reason |
| --- | ---: | --- |
| effective recipe files | 4,096 | about 185 today; seven new types × at most 1,024 recipes each would exceed this only with packs |
| bytes per recipe file | 65,536 | `ProcessDefinition.MAX_DEFINITION_BYTES` |
| nodes | 16,384 | items + fluids + capabilities |
| producers | 32,768 | |
| ingredient alternatives per selector | 32 for kernel and classic machine types (`ProcessInput.MAX_VARIANTS`), 1,024 otherwise | |
| tag nesting depth | 8 | |
| diagnostics kept per run | 256, then a count | matches the repository validator's error cap |
| report size | 8 MiB | |

The fixpoint does at most one pass per round and at most `producers` rounds, so work is bounded by
producers × prerequisites. No network, no world, no Forge bootstrap.

## 6. Current-data expectations and bootstrap risks

These follow from the rules above applied to the base commit by reading; they are expectations
for the test design, not executed results.

1. Available with today's data under reading (a): iron rods, copper coil, small plate press and
   iron plates (press needs obsidian and a redstone pulse), combustion generator and FE from it,
   silicon wafer and basic circuit through crafting (quartz root), machine casing, the v1.2 rolling
   machine, precision assembler and electrolyzer with their ports.
2. Unavailable today: steel and everything that needs it (motors, pressurized tank, pump), as the
   earlier research found; all ten assigned components (no producer); every C16b/C16c product.
   The release assertion therefore cannot pass before C16b and C16c deliver their producers.
3. **Bootstrap risk A (circuits).** The classic route makes basic and advanced circuits only in the
   cutting machine (C16b). If the cutting machine's own recipe needs a basic circuit or a board
   that itself needs one, the only escape is a crafting or precision route. Until the bodies in §3
   are known, removing the existing crafting stand-ins could create a hard-locked cycle. The graph
   must run on the frozen payloads before any stand-in is removed (§7).
4. **Bootstrap risk B (wafers).** The legacy wafer comes from a silicon boule (crystallizer, C16c),
   whose silicon ingot comes from the arc furnace (C16b). The etcher (C16c) needs wafers for basic
   circuit plates. The crafting wafer stand-in is today's only early route.
5. **Bootstrap risk C (steel).** The arc furnace is the accepted first steel source (ADR-064 §8.1).
   If its controller, hatches or blast bricks need steel, motors or boards that need steel, no
   acyclic route exists. Root must obtain an explicit contract disposition for an escape rather
   than add a substitute recipe (earlier research, F1 and F4).
6. **Plates before the etcher.** The legacy precision-assembler plate recipes
   (`basiccircuitplate.json`, `advcircuitplate.json`) give a plate route through the existing v1.2
   precision assembler, before the C16c etcher exists. Whether to restore both producers (the
   legacy had both) is a mapping decision for Root once the bodies are known; restoring both is
   the legacy behaviour.

## 7. Rebalance proposals

ADR-064 §10.3: existing v1.x recipes that the classic chain supersedes move to the classic
components "where the graph shows a route". Each item below is a separate proposal for Root and,
where it changes balance, the owner. None changes a player's inventory (ADR-061 §1.5). Each is
listed in the CHANGELOG when applied.

| ID | Existing recipe | Proposal | Condition | Running-process effect |
| --- | --- | --- | --- | --- |
| RB-1 | `precision_control_circuit` (precision: 2 iron + 2 redstone → advanced circuit + 2 redstone torches, v1.2 stand-in; v1.8 tag copy) | retire after the cutting-machine advanced circuit route is available in the `release` graph | graph shows the classic route; CHANGELOG | ADR-064 §2.3: an active process on a removed recipe keeps progress and resources and pauses with `recipe_missing`; disclose this; no automatic conversion of old processes whose origin is unproved |
| RB-2 | `silicon_wafer` crafting (quartz + redstone → 2) | **keep** as the early route until the graph proves the boule → cutting route without it; then owner decides keep (convenience) or retire | risk B | crafting only, no process state |
| RB-3 | `basic_circuit` crafting (wafer + copper + redstone → 2) | keep until risk A is resolved; then owner decides | risk A | crafting only |
| RB-4 | `advanced_circuit` crafting (basic circuit + gold + diamond) | keep until the cutting route exists; then owner decides | risk A | crafting only |
| RB-5 | `precision_guidance_module` (precision → comparator) | no change: it does not stand in for a classic component | — | — |
| RB-6 | recipes that name `basic_circuit` / `advanced_circuit` through the `circuits/*` tags | no change: they keep working whichever producer supplies the circuit | — | — |

Old in-progress tasks: a process recorded under a v1.2 recipe whose origin cannot be proved stays
in the repair-required state of ADR-064 §2.5; this draft proposes no automatic migration.

## 8. Upstream reading, provenance and license

- Read before upstream inspection: `UPSTREAM.md`, `NOTICE.md`, docs 02 and 08.
- Upstream files read: `AdvancedRocketry.java` (SHA-256
  `e290371acdb441c6c5fe187bb82a28c27f1bc8db201e54c45a4080778c7aa911`) and
  `api/ARConfiguration.java` (`78ce40293f42c5a13add1e09d8cca13dc0afa8ac462df1a05f43f9dfcde4e1d4`),
  both from `C:/Users/Administrator/AppData/Local/Temp/arce-v160-upstream-audit-a4ceab6678b544b3af0bd4a798dd60d6/`
  and both byte-equal to the committed `legacy-manifest/java-files.csv` entries of commit
  `c5cd5af62fc07cd4e0d24f06a16033f181c47c04` (MIT). Only the lines cited above were used, as
  behaviour facts. Nothing was copied.
- No archive, cache, download, other fork or LibVulpes source was opened.
- Impact of this draft: no code, data, asset, provenance, configuration, schema, network or save
  change. The future implementation adds registry IDs (permanent under ADR-061 §1.4), recipes,
  one tag, test resources and a GameTest; it adds no persisted state and no network message.

## 9. Root integration surfaces (future, not authorized here)

1. Registry: eight new items for the ten units (`tracking_circuit`, `control_board`,
   `item_io_board`, `liquid_io_board`, `basic_circuit_plate`, `advanced_circuit_plate`,
   `user_interface`, `carbon_brick`; the configuration unit adds none and two units share the
   carbon brick), their models, language entries, tag `forge:ingots/carbon` and creative-tab
   placement.
2. DataGen: recipes from the frozen §3 bodies; v1.8 output root; CHANGELOG entries for §7 items.
3. Build: the `arce.graph.resources` test property (§4.2.1).
4. Test resources: `registered-content.txt`, `roots.json`, `exemptions.json`,
   `no-tau-unavailable.txt`; the registry-equality GameTest.
5. Test code: the graph extractor, adapters, fixpoint and report as test-side classes (no
   production framework, ADR-061 §5.3 calls it a check); each class under 500 lines.
6. Ledger: the ten rows move only with a delivery record that names each unit and modern ID;
   `makeMaterialsForOtherMods` per §2.2.

## 10. Open items

| ID | Question | Owner |
| --- | --- | --- |
| G-OPEN-1 | Legacy bodies of the eleven component recipes and four machine recipes (§3) | Root |
| G-OPEN-2 | `control_board` versus `control_circuit_board`; `liquid_io_board` versus `fluid_io_board` | Root |
| G-OPEN-3 | Carbon brick fuel value, if any | Root, from the legacy body |
| G-OPEN-4 | `makeMaterialsForOtherMods` player impact acceptance | owner |
| G-OPEN-5 | Root reading (a) or (b) for vanilla Nether items | owner |
| G-OPEN-6 | Machine receive limits for FE (C16a plug, v1.2 ports) | Root |
| G-OPEN-7 | Planner entry point and route blueprints for Level access | Root |
| G-OPEN-8 | Static code-producer diagnostic (§4.2.5) | Root |
| G-OPEN-9 | Both legacy plate producers (precision and etcher) or one | Root |
| G-OPEN-10 | Escape for bootstrap risks A-C if the frozen payloads produce a hard lock | Root, owner if balance changes |

This contract does not pass build, native, restart, V1/V2 or any v1.8 G0-G9 Gate.
