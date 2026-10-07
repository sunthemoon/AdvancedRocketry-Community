# CL16D-COMPONENTS-GRAPH-02: components and survival recipe graph, successor contract draft 02

Date: 2026-10-08. Milestone: v1.8.0 / C16d. Status: **proposed, not frozen**.
Author: Claude (delegated worker, owner-started interactive session). Task:
[TASK-02](TASK-02.md), read in the Root checkout at `65dd821180f6c0304340fc51d8d1d11df6d29347`
(SHA-256 `a09d7f636e842d6e3d15ccde718652fffeaf09349aafc25368c937cb5405b5b9`).
Worktree base: `a65dcbf68143ce63af3b2205b0c36af02eaae0e8`. Root source checkpoint named by the
TASK: `f9f2d9d2c5eb0de2c9f5d28160ab7804fc57eaef`. Between the two commits only HUD, client
configuration and their tests changed under `src/` (HANDOFF-02 records the command), so every
source line cited below has the same bytes at both.

This draft replaces [CONTRACT-01](CONTRACT-01.md) as the proposal under review. Draft 01 stays
unchanged as historical input. Nothing here changes source, recipes, tags, registry, ledger,
ADRs or Gates, and nothing here is an implementation. **OPEN** marks a question that stays with
the named owner; a proposal is labelled as such and is not a decision.

Inputs added since draft 01:

| Input | Identity |
| --- | --- |
| Independent review of draft 01 (F1-F6) | [REPORT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16d-graph-independent-20261008-01/REPORT-01.md), SHA-256 `7764c25717d1b2c4b23aec09dcc64de90b86cf9af9d25613be5013530c59bc44` |
| Owner decisions on G-OPEN-4 and G-OPEN-5 | [OWNER-DECISIONS-02](OWNER-DECISIONS-02.md), SHA-256 `bd53cea6350009b3db1c8aa143554605a471a6e473f864b7c43f9ef97802d717` |
| Twenty legacy recipe bodies, read-only behaviour references | [Root retrieval leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16d-legacy-recipe-inputs-root-20261008-01/REPORT-01.md); provenance SHA-256 `378b95fa8c8eb76652ca46d604a7f25497149944f071a021c68777403df2f2be`; MIT LICENSE SHA-256 `1f9978a442976337a86ea9ea5a0c97ae6b1bdab56d9661d05938f163b8e55be3`; upstream commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04` |

The bodies were read from that leaf only. No archive, cache, unpacked Temp tree or other upstream
file was opened for this draft.

## 1. Disposition of the review findings and earlier open items

| Item | Disposition in this draft | Where |
| --- | --- | --- |
| F1 (High) Level-only access loses orbit/station body | **Addressed by redesign.** Access is keyed by body context (surface, orbit, station of body B), never by Level alone. Station contexts come only from station creation on B's surface or a warp to B; a rocket route to an orbit never creates a station. Body-local producers bind to a context. Negative two-station and warp fixtures added. | §5; TEST-DESIGN-02 X01-X09 |
| F2 (High) continuous throughput replaced energy existence | **Addressed by redesign.** Availability is finite completion: a connected FE producer plus a receiver whose capacity covers the largest atomic debit. Throughput is a report-only diagnostic. Connection needs a witness row (ARCE has no FE transport block; generators push to adjacent receivers only). Pump and terminal use their own debit rules. Machine capacity values not frozen in code stay OPEN. | §4; E01-E12 |
| F3 (Medium) discovery prerequisite wrong | **Addressed.** Discovery follows the data mission as coded: logical launch from the terminal, no rocket, same-claim credit then spend, target must be in the effective definition's `allowed_targets`. Redstone charge is an alternative to FE. | §6; D01-D07 |
| F4 (Medium) tests contradict contract and catalog | **Addressed.** Exempt IDs may not have any producer (no warning case remains). The required universe (project inventory) is separated from validation of every referenced ID against the full registered universe; vanilla outputs are legal nodes. The real mixed-output recipe becomes an extractor fixture. | §3.2, §8; R03, R07 |
| F5 (Medium) unbounded non-recipe inputs and wrong work bound | **Addressed.** Every input family has file, byte, aggregate, depth and node limits checked before parsing. The fixpoint is a counter worklist with a cumulative work counter; total work is linear in the input references, not rounds × producers. | §3.3, §7.1, §11; L01-L14 |
| F6 (Medium) route and cycle reports undefined | **Addressed.** Synchronous rounds, complete ordering, the minimal-route metric (derivation height), AND/OR-aware hard-lock definition and exact report fields are defined. | §7, §10; G01-G16 |
| G-OPEN-1 legacy bodies | **Input gap closed** by the Root retrieval. Translations in §2 are proposals; translated recipes, tags, IDs and stand-in retirement are not approved by the retrieval. | §2 |
| G-OPEN-4 `makeMaterialsForOtherMods` | **Owner decided** "通用标签互操作，额外配方由数据包提供（推荐）": common-tag interoperability; extra recipes for undefined other-mod materials come from data packs; no boolean. Behaviour gap disclosed in §2.4. | §2.4 |
| G-OPEN-5 Nether roots | **Owner decided** "显式验证下界入口与下界材料（推荐）": Nether materials are roots only behind an explicit Nether-access capability whose own prerequisites are Overworld roots. Nether quartz is not a free root. The exact witness below is a proposal for review. | §3.4 |
| G-OPEN-2, -3, -6, -7, -8, -9, -10 | Restated with narrower content in §14 | §14 |

## 2. Component mapping grounded in the legacy bodies

### 2.1 Translation rules (proposal)

1. **Legacy item references.** `advancedrocketry:ic` data 0-5, `advancedrocketry:itemcircuitplate`
   data 0-1 and `advancedrocketry:misc` data 0-1 map to exact modern items (table in §2.2), not to
   tags. `libvulpes:structuremachine` maps to `advancedrocketrycommunity:machine_casing` (ADR-064
   §4). `advancedrocketry:wafer` data 0 maps to the existing item tag
   `advancedrocketrycommunity:silicon_wafers` (v0.1.0, one member today).
2. **Ore-dictionary names** map to common tags:

   | Legacy name | Proposed selector | Repository use today |
   | --- | --- | --- |
   | `dustRedstone` | `forge:dusts/redstone` | used by `precision_control_circuit` |
   | `dustCopper`, `dustGold` | `forge:dusts/copper`, `forge:dusts/gold` | v1.8 tag files, members `copper_dust`, `gold_dust` |
   | `plateIron`, `plateCopper`, `plateGold`, `plateSteel` | `forge:plates/<metal>` | v1.8 tag files |
   | `gearSteel` | `forge:gears/steel` | v1.8 tag file, member `steel_gear` |
   | `ingotGold` | `forge:ingots/gold` | used |
   | `gemQuartz` | `forge:gems/quartz` | used by `silicon_wafer` |
   | `gemLapis` | `forge:gems/lapis` | **not used; existence in Forge tag data not verified here (T-OPEN-1)** |
   | `slabWood` | `minecraft:wooden_slabs` | **not used; T-OPEN-1** |
   | `dyeLime` | `forge:dyes/lime` | **not used; T-OPEN-1** |
   | `dustGlowstone` | `forge:dusts/glowstone` | **not used; T-OPEN-1** |
   | `bouleSilicon` | `forge:boules/silicon` is not present; the v1.8 tag is `advancedrocketrycommunity:boules/silicon` | C16c decides (T-OPEN-2) |
   | `lensPrecisionLaserEtcher` | none; the etcher lens is a C16c item | C16c (T-OPEN-2) |

   T-OPEN-1 (Root): confirm each tag exists in the Forge 1.20.1 data on the build classpath, or
   choose the exact vanilla item. No cache or Forge JAR was inspected for this draft.
3. **Exact vanilla items** keep exact IDs: `minecraft:ender_eye`, `minecraft:redstone_block`,
   `minecraft:glass_pane`, `minecraft:repeater`, `minecraft:diamond`, `minecraft:furnace`,
   `minecraft:dropper`, `minecraft:iron_block`, `minecraft:obsidian`. Legacy `minecraft:coal`
   data 1 is charcoal and maps to exact `minecraft:charcoal` (not the `minecraft:coals` tag, which
   would also admit coal).
4. **Machine recipe fields.** Legacy `time` maps to `processing_time` and `energy` to
   `energy_per_tick` (the legacy field is per tick; ADR-064 Context quotes the etcher as
   1,200 ticks × 400 FE/t). Counts and output counts stay. A legacy ingredient with no `count`
   means 1.
5. **Fit.** All three-ingredient precision bodies fit `PrecisionAssemblerChannels`
   (`INPUT_COUNT = 5`, `OUTPUT_COUNT = 2`). A tag that resolves to more than 32 items disables a
   kernel or classic recipe (ADR-064 §2.1, `ProcessInput.MAX_VARIANTS = 32`); the graph adapter
   applies the same rule (§3.5).
6. A legacy reference with no accepted mapping stays **OPEN**; it is never guessed.

### 2.2 The ten units, their bodies and proposed producers

| Ledger unit | Modern ID (proposal) | Legacy producer bodies (file, SHA-256 in `legacy-manifest/recipes.csv`) | Proposed modern producers |
| --- | --- | --- | --- |
| `item_variant:ic/1` | `tracking_circuit` | `trackingcircuit.json` (precision) | precision: `forge:dusts/redstone` ×1, `minecraft:ender_eye` ×1, `basic_circuit_plate` ×1 → 1; `processing_time` 900, `energy_per_tick` 50 |
| `item_variant:ic/3` | `control_board` (G-OPEN-2) | `controlcircuitboard.json` (shaped), `controlcircuitboard_prec.json` (precision) | shaped `rvr / dwd / dpd`: r `forge:dusts/redstone`, v `forge:gems/quartz`, d `forge:dusts/copper`, w `minecraft:wooden_slabs`, p `forge:plates/iron` → 1; precision: `forge:dusts/redstone`, `forge:plates/copper`, `forge:plates/steel` → 1, 200 ticks, 10 FE/t |
| `item_variant:ic/4` | `item_io_board` | `iocircuitboard.json`, `iocircuitboard_prec.json` | shaped as above with d = `forge:dusts/gold`; precision: redstone, `forge:plates/gold`, `forge:plates/steel` → 1, 200 × 10 |
| `item_variant:ic/5` | `liquid_io_board` (G-OPEN-2) | `liquidiocircuitboard.json`, `liquidiocircuitboard_prec.json` | shaped with d = `forge:gems/lapis`; precision: redstone, `forge:gems/lapis`, `forge:plates/steel` → 1, 200 × 10 |
| `item_variant:itemCircuitPlate/0` | `basic_circuit_plate` | `basiccircuitplate.json` (precision), `basiccircuitplateetcher.json` (etcher) | precision: `forge:ingots/gold`, `forge:dusts/redstone`, `silicon_wafers` ×1 → 1, 900 × 100; etcher (C16c type): lens (catalyst), `forge:plates/gold`, `forge:dusts/redstone`, `silicon_wafers` ×4 → 2, 1,200 × 400 |
| `item_variant:itemCircuitPlate/1` | `advanced_circuit_plate` | `advcircuitplate.json`, `advcircuitplateetcher.json` | precision: `forge:ingots/gold`, `minecraft:redstone_block`, `silicon_wafers` ×1 → 1, 900 × 100; etcher: lens, `forge:plates/gold`, `minecraft:redstone_block`, `silicon_wafers` ×4 → 2, 1,200 × 600 |
| `item_variant:misc/0` | `user_interface` | `userinterface.json` (shaped, blank top row) | shaped `lrl / fgf` (blank row dropped as in the accepted press amendment): l `forge:dyes/lime`, r `forge:dusts/redstone`, f `forge:dusts/glowstone`, g `minecraft:glass_pane` → 1 |
| `item_variant:misc/1` | `carbon_brick`, tag `forge:ingots/carbon` | `charcoalbrick.json` (shapeless) | shapeless: 6 × `minecraft:charcoal` → 1 |
| `material:Carbon` | the same `carbon_brick` | none of its own | — |
| `config:CATEGORY_GENERAL.makeMaterialsForOtherMods` | no key | — | §2.4 |

Full body hashes: trackingcircuit
`a17dd4c66b7cf05952a7db2ad4a9cbcd299e93460dd02aebf682325575b06ee8`, controlcircuitboard
`6a234e9cae6ff502d6e243e2013389e34c9d75c3ea7589c12277d73ac40ad3ee`, controlcircuitboard_prec
`699b80267a894d60574fc9a54a383ce75470e4440c597129dea8b075a6ddc59f`, iocircuitboard
`e48a9109867a831bfe5f577ae0b7f6083f55727698b17f4c7fba3f954a9d81dc`, iocircuitboard_prec
`617175a667c19d0c4ad2bd3e11c13b36a78247774e1a5ba1b1b17f7bf86bff9f`, liquidiocircuitboard
`97bc684da69d199c53e38631f16a1086c6981fea853df4baef06db9a3d4fcdad`, liquidiocircuitboard_prec
`bc3c77632089f34e2bee7941a9e2ee6be9a27894e2fc9e89ec560653dd57dca3`, basiccircuitplate
`15fa951546181e68bb0221936939c35ee60d198ff1e73259b12e5dbc8f7db2e7`, basiccircuitplateetcher
`f61ed418606e19d1c16e2b5752d650f8c53c7cca05e90d9568fc86f430c1820f`, advcircuitplate
`190d3ea59b7f5d0795dd4c1421132286434c2740b2c45e4e498e7bb5bb92305e`, advcircuitplateetcher
`665847a41e0af5b761a538e2efbc5e1696b6b80af47f02da435319c9be0361bb`, userinterface
`dc07f355dc95072ba3a5d57fde2948a7dd6209ca69dd3277c30a3c6beb64e599`, charcoalbrick
`28ec6e6ce7aaf732dc166765795844a56aefdaa85acb409670fbf1b693bb62d2`.

Proposed retention: restore **both** legacy producers wherever the legacy game had two (boards:
crafting and precision; plates: precision and etcher). The crafting boards and the precision
plates are the only routes that do not need steel or a C16b/C16c machine (§2.5). Which producers
to retain is a Root mapping decision (G-OPEN-9); dropping the crafting boards or the precision
plates is expected to make the classic chain unreachable until steel exists.

Item properties stay as proposed in draft 01 §2.1: plain items, stack 64, no
`DevelopmentComponentItem`, no `advancedrocketrycommunity:circuits/*` membership for the new
boards. `carbon_brick` gets **no** burn time: `charcoalbrick.json` defines only its crafting, and
no legacy fuel handler was read in this task (G-OPEN-3 stays with Root; any burn time is a new
FE source and must enter the graph as a producer).

### 2.3 Adjacent bodies the graph needs but this task does not own

| Body | SHA-256 | Translation (proposal) | Owner |
| --- | --- | --- | --- |
| `basiccircuit.json` (cutting) | `dc6245aa22c10a67810769c7354e283d0b7ac2903d8a534fb15e5ac06080e36a` | `basic_circuit_plate` → 4 `basic_circuit`, 300 × 100 | C16b cutting type |
| `advbasiccircuit.json` (cutting) | `be5169fbfdb665cd0c471a593174bf9547502f46aa31724a712f631681da27a7` | `advanced_circuit_plate` → 4 `advanced_circuit`, 300 × 100 | C16b |
| `siliconwafer.json` (cutting) | `f5f6dfcbd1e63bb6ea557d4795705a44f204b0579c6932476f8a7a6ab22dd3e7` | silicon boule → 4 `silicon_wafer`, 300 × 100 | C16b; boule from C16c |
| `cuttingmachine.json` (shaped) | `78a2c5d472043b2a11249d62c08caf104448f3f21f9c04be7f580a990d08f6b9` | `aba / cde / opo`: a `forge:gears/steel`, b `user_interface`, c `item_io_board`, d `machine_casing`, e `control_board`, o `minecraft:obsidian`, p `forge:plates/steel` | C16b |
| `precisionlaseretcher.json` (shaped) | `f33fb9465f9783bcb3f222f1211428d009e8a744f292ff0342758c643a0c7a24` | `psp / abc / rrr`: p `forge:plates/gold`, s `user_interface`, a `item_io_board`, b `machine_casing`, c `control_board`, r `advanced_circuit` | C16c |
| `precisionassemblingmachine.json` (shaped) | `d7491f5396683918c89a3f958412ed6dbcff93c2bcc1bf55fbc0b0da830bccda` | repeater, `user_interface`, diamond / `item_io_board`, casing, `control_board` / furnace, `forge:gears/steel`, dropper | v1.2 machine already delivered; see RB-7 |
| `rollingmachine.json` (shaped) | `f2aa7cfed8c84081e365b572862e6567ce4546e61e72adfcd7b5ce4574e015e4` | `forge:gears/steel` ×2, `user_interface` / `item_io_board`, casing, `control_board` / iron block, `liquid_io_board`, iron block | v1.2 machine already delivered; see RB-7 |

### 2.4 `makeMaterialsForOtherMods` (owner decision applied)

- Built-in recipes consume common tags, so other mods' tag members are accepted as inputs and
  ARCE products carry common tags.
- Recipes for materials that ARCE does not define (for example plates of another mod's metal) are
  not generated. Data packs can add them. **Disclosed behaviour gap:** the legacy switch, on by
  default, generated such machine recipes automatically; v1.8 does not.
- No configuration key is added. Proposed ledger disposition: `REDESIGNED`, evidence = the
  tag-based catalog of every kernel and classic recipe type once the seven C16b/C16c types exist.
- Graph consequence: another mod's tag member is never a root and never makes a selector
  available in the clean-world graph (§3.4).

### 2.5 Bootstrap analysis with the bodies (expectations, not executed results)

Reading the bodies against current data gives these chains:

1. **Boards by crafting.** Inputs: redstone, wooden slab, iron plate (small plate press on an
   iron block, `pressing_iron_plate`), copper or gold dust (press on `forge:ores/copper` /
   `forge:ores/gold`, `pressing_copper_dust`, `pressing_gold_dust`), lapis, and **quartz**. Quartz
   is behind Nether access (§3.4). No circuit and no steel is needed.
2. **Boards by precision** need `forge:plates/steel`, so steel (arc furnace, C16b).
3. **Plates by precision** need a gold ingot, redstone or a redstone block, and a silicon wafer.
   The only wafer producer before C16b/C16c is the v0.1.0 crafting stand-in (quartz + redstone →
   2), so plates are also behind Nether access. The v1.2 precision assembler exists.
4. **Tracking circuit** needs an ender eye. In vanilla 1.20.1 the eye is crafted from an ender
   pearl and blaze powder; blaze rods come only from Nether fortresses, so the eye is a
   Nether-gated root (§3.4).
5. **User interface** needs glowstone dust: Nether-gated in this proposal (§3.4, R-OPEN-1 for the
   witch-drop alternative).
6. **Cutting machine** body needs steel gears and a steel plate, the user interface, the item IO
   and control boards, a casing and obsidian. It needs **no circuit**, so the draft-01 risk "circuit
   needs the cutter, the cutter needs a circuit" does not arise from this body. Its blocker is
   steel (risk C).
7. **Etcher** body needs three advanced circuits. Their legacy route is the cutting machine on an
   advanced circuit plate (precision route available). The current crafting stand-in
   (`advanced_circuit`: basic circuit + gold + diamond) and `precision_control_circuit` also make
   advanced circuits. So the etcher is reachable once the cutting machine or a stand-in is.
8. **Risk C (steel) remains the decisive bootstrap question.** The arc furnace body was not in
   the requested set. If the arc furnace or its hatches need steel, motors or precision boards, no
   acyclic route exists. Root must obtain its body and a contract disposition (G-OPEN-10) before
   any stand-in is retired.
9. **Legacy v1.2 machine bodies.** The legacy rolling machine and precision assembler need a
   steel gear. The delivered v1.2 recipes need advanced circuits and a casing instead. Restoring
   the legacy bodies would put steel in front of the rolling machine; RB-7 keeps the delivered
   recipes.

## 3. Inputs, universes and roots

### 3.1 Effective resource set

Unchanged from draft 01 §4.2.1: the extractor reads `build/resources/main` after
`processResources`, so the exact-path exclusions in `build.gradle` (for example
`currentSatelliteDefinition` at line 146 and `supersededV120ProcessRecipes`) are applied and a
resource path is counted once. The directory comes from a test system property (proposal
`arce.graph.resources`); missing or empty fails as `input_missing`.

### 3.2 Universes

1. **Required universe** `U_req`: every item and fluid registered in the
   `advancedrocketrycommunity` namespace. It is read by JUnit from a committed, sorted
   `src/test/resources/recipe-graph/registered-content.txt` and checked in both directions against
   `ForgeRegistries.ITEMS` and `ForgeRegistries.FLUIDS` by a GameTest (plain JUnit here only
   bootstraps Minecraft, `testsupport/MinecraftBootstrap.java`). Every member of `U_req` must be
   available or exempt.
2. **Reference universe** `U_ref`: every item or fluid ID that appears anywhere in the extracted
   inputs (ingredients, tag members, outputs, loot entries, producer rows, roots). The JUnit run
   writes the sorted non-project subset to the report and compares it byte for byte with a
   committed `external-references.txt`; the same GameTest checks that every line of that file is
   registered. A referenced ID that is neither in `U_req` nor in the checked external list fails as
   `unknown_reference`.
3. Vanilla and other non-project IDs are ordinary nodes. They may be produced (for example
   `minecraft:comparator` from `precision_guidance_module` and `minecraft:redstone_torch` from
   `precision_control_circuit`, `PrecisionAssemblerRecipeProvider.java:43, 86`) and they may be
   roots. They are never required.

### 3.3 Input families and parse limits

Each family is enumerated before parsing. Limits apply in the order: file count, then file size
from the directory listing, then aggregate bytes, then a streaming parse that counts JSON depth
and nodes. Exceeding any limit fails with `input_limit:<family>:<limit>` and no graph is built.

| Family | Source | Max files | Max bytes per file | Max JSON depth | Max JSON nodes per file |
| --- | --- | ---: | ---: | ---: | ---: |
| recipes | effective `data/*/recipes/**` | 4,096 | 65,536 | 16 | 4,096 |
| item and fluid tags | effective `data/*/tags/{items,fluids}/**` plus Forge/Minecraft tag files on the test classpath | 8,192 | 65,536 | 8 | 8,192 |
| block loot tables | effective `data/*/loot_tables/blocks/**` | 4,096 | 65,536 | 32 | 4,096 |
| worldgen (biome modifiers, placed and configured features, biome feature lists) | effective `data/*/worldgen/**`, `data/*/forge/biome_modifier/**` | 4,096 | 262,144 | 32 | 16,384 |
| celestial bodies, systems and routes | effective catalog files, decoded with the existing production codecs and their own limits | as the production codec | as the production codec | — | — |
| mission and drill tables | `satellite_definitions/`, `gas_harvest/`, `asteroid_types/`, `laser_drill_tables/` | 256 | 65,536 | 16 | 4,096 |
| committed graph files (roots, exemptions, producer table, connection witnesses, expected-unavailable, external references, registered content) | `src/test/resources/recipe-graph/` | 16 | 1,048,576 | 8 | 65,536 |

All families together: at most 64 MiB of bytes read. Tag resolution: at most 4,096 resolved members
per tag and nesting depth 8; a tag cycle or an unresolvable required reference fails. A member list
longer than 32 for a kernel or classic machine selector marks that recipe `disabled_by_tag_size`
(the runtime rule), not a graph failure.

### 3.4 Roots, Nether and End gates

`src/test/resources/recipe-graph/roots.json` lists every vanilla item or fluid that the graph does
not derive. Entry fields: `id`, `class`, `gate`, `reason`, optional `alternatives` (text only,
never used by the graph).

| Class | Gate | Meaning |
| --- | --- | --- |
| `overworld_natural` | none | generated in Overworld terrain or dropped by Overworld mobs in a new survival world |
| `overworld_crafted` | none | a vanilla recipe chain whose inputs are all `overworld_*` roots |
| `nether` | `cap:access:minecraft:the_nether` | needs a Nether visit, including vanilla items crafted from a Nether input (ender eye, blaze powder) |
| `end` | `cap:access:minecraft:the_end` | needs the End |

Rules:

1. A root with a gate is available only after its gate capability is available.
2. **Nether access** (owner decision): producer `vanilla:nether_portal` outputs
   `cap:access:minecraft:the_nether`. Its prerequisites are the AND of `minecraft:obsidian` (frame;
   the count 10 is recorded, not consumed) and the OR selector {`minecraft:flint_and_steel`,
   `minecraft:fire_charge`}. Proposed root classes for those inputs: obsidian
   `overworld_natural` with reason "lava source + water, mined with a diamond pickaxe or cast in
   place"; flint and steel `overworld_crafted` (iron ingot + flint); fire charge is `nether`
   (blaze powder) and so cannot open the first portal. The witness is a vanilla fact recorded for
   review; vanilla recipes and loot are not modelled.
3. **End access** (only if some required node needs an `end` root): producer
   `vanilla:end_portal` with prerequisites `minecraft:ender_eye` (12 recorded) and Nether access.
   No current required chain is expected to need it.
4. Trades, structure chests and wandering-trader goods are not admitted as release roots. They may
   appear in `alternatives` text.
5. Project items are never roots. Another mod's tag member is never a root (§2.4). Creative-tab
   presence is not availability.
6. Proposed Nether-gated roots needed by C16d: `minecraft:quartz` (via `forge:gems/quartz`),
   `minecraft:ender_eye`, `minecraft:glowstone_dust`. R-OPEN-1: glowstone dust also drops from
   Overworld witches; this proposal still classes it `nether` because the witch route is a rare
   mob drop, and the class changes only the diagnostic variant (§9), not the release result.
7. A root entry for an ID that some modelled producer already makes is allowed only for vanilla
   IDs and is reported (`root_also_produced`) so reviewers can drop it.

### 3.5 Producer adapters

Each adapter turns a recipe type or a producer-table row into producers with **slots**. A slot is
one ingredient selector, facility, context, energy or capability requirement; a slot is satisfied
when any of its members is available (OR); a producer is enabled when every slot is satisfied
(AND).

| Adapter | Slots | Notes |
| --- | --- | --- |
| `minecraft:crafting_shaped`, `crafting_shapeless` | one per distinct ingredient selector | container items return their container; quantities recorded |
| `minecraft:smelting`, `blasting` | ingredient; facility root (`minecraft:furnace` / `blast_furnace`); fuel selector over root furnace fuels | |
| `advancedrocketrycommunity:rolling`, `precision_assembling`, `electrolyzing` | each input; formed machine; `cap:fe:<receiver>` for `energy_per_tick` (§4) | >32-member tag disables the recipe |
| `advancedrocketrycommunity:small_plate_press` | input block; placed press; obsidian backing; redstone pulse root | G-OPEN-11: whether an ore input needs the ore as an item (silk touch) or can be pressed in place; the press acquisition contract owns that fact |
| the seven C16b/C16c types | per ADR-064 §1.4 codec, added with each machine; catalysts kept in the machine are slots, not consumed | until added, their outputs have no producer |
| block loot | the block (placed or generated in a context); tool condition | supported conditions as draft 01 §4.2.6; any other condition blocks that entry |
| world feature | `cap:surface:<body>` of the body whose surface Level holds the biome | §5 |
| producer table row (`code:<row>`) | as listed in the row | §3.6 |

### 3.6 Producer table and reconciliation (G-OPEN-8 narrowed)

Producers implemented in Java (pump, canister fill, electrolysis by code, gas harvest, asteroid
missions, laser drill, generators, station creation, warp, discovery, portals) are rows in a
committed `producers.json`: `id`, `source_class`, `source_method`, `outputs`, `slots`, `energy`
(consumer receiver and atomic debit, §4), `context`, `observation` (the GameTest or JUnit name
that shows the row's effect with exactly these slots), `reason`.

Properties:

1. **Omission fails closed.** The fixpoint is monotone: removing a producer can only make nodes
   unavailable. A code producer missing from the table can therefore cause a false failure
   (`no_producer`), never a false pass. Coverage claims are made relative to the table and the
   effective data, and the report says so.
2. **Over-claiming is the real risk.** A row that lists fewer slots than the code checks gives a
   false pass. Each row must name an observation test that performs the production with only the
   listed prerequisites present; a row without one fails `row_unobserved`. Root reviews rows
   against the cited source.
3. **OPEN (Root, G-OPEN-8):** whether to add a static scan of `src/main/java` that lists classes
   constructing project `ItemStack`s or fluids for review. It improves completeness of the table;
   it is not needed for soundness (property 1).

## 4. Energy: finite completion, with throughput as a diagnostic

### 4.1 Facts from current code

- `ProcessMachineLogic.java:29-31` pauses with `WAITING_ENERGY` when stored energy is below one
  tick's `energyPerTick`; progress is kept and resumes (`:33-42`). A recipe therefore completes
  with any positive supply as long as the receiver can hold one tick's debit.
- `CombustionGeneratorBlockEntity.java:96-125` pushes only to the six adjacent positions, only into
  loaded chunks, never into another combustion generator. ARCE has no FE transport block (no cable
  or conduit class in `src/main/java`). The solar generator, microwave receiver and black hole
  generator also push to neighbours (`SolarGeneratorBlockEntity.java:126`,
  `MicrowaveReceiverBlockEntity.java:125`, `BlackHoleGeneratorBlockEntity.java:317`).
- Receivers: rolling machine energy port capacity 20,000, receive limit 1,000 per call
  (`RollingMachinePortBlockEntity.java:48-49`); precision assembler port capacity 20,000
  (`PrecisionAssemblerPortPersistence.java:21`), receive limit 1,000 per call
  (`PrecisionAssemblerPortStorage.java:111`); electrolyzer capacity 20,000
  (`ElectrolyzerBlockEntity.java:39`); classic power plug 0..10,000 FE per plug
  (`ClassicHatchState.java:19`); pump buffer 10,000 with a 100 FE debit per source block and a
  5-tick cooldown (`PumpBudget.java:5-10, 27-33`); satellite terminal capacity 10,000, assembly
  debit 1,000, launch threshold 2,000, one redstone dust adds 2,000
  (`SatelliteTerminalBlockEntity.java:57-60, 403-415`); warp core forwards FE into the station's
  warp balance, at most 10,000,000 (`StationLimits.java:32`), costs 2,000,000 in-system and
  8,000,000 interstellar by default (`WarpSettings.java:76`).

### 4.2 Availability rule

`cap:fe:<receiver>` (for example `cap:fe:advancedrocketrycommunity:precision_assembler_energy_input_port`)
is available when all of:

1. some FE producer row G is enabled in a context where the consumer can also be placed;
2. a **connection witness** row `(G, receiver)` exists: G pushes to an adjacent block and the
   receiver block exposes a receive-capable `ForgeCapabilities.ENERGY` on at least one face that
   the formed structure leaves free; the row names the GameTest that places one G next to the
   receiver of a formed machine and observes stored energy rise;
3. the receiver's capacity is at least the consumer's **largest atomic debit** (one tick's
   `energy_per_tick` for a process; 100 for the pump; 2,000 for terminal launch; the warp cost for
   the warp balance), and its per-call receive limit is positive;
4. the receiver has no passive drain (stated per row; current receivers have none).

A consumer whose atomic debit exceeds its receiver capacity is unavailable with
`energy_debit_exceeds_capacity`. Example: a 20,001 FE/t recipe on a 20,000 FE port never progresses.

### 4.3 Throughput diagnostic (report only)

For every enabled powered producer the report records `energy_per_tick`, `processing_time`, total
FE (`energy_per_tick × processing_time`), the sustained rate of one witnessed generator (40 FE/t
combustion, `CombustionBurn.java:5-8`), the ticks to complete with one generator
(`ceil(total / 40)` when the rate is below `energy_per_tick`, else `processing_time`) and the
generators needed for full speed (`ceil(energy_per_tick / 40)`). It also records whether that
count exceeds the free faces stated in the witness row. None of these changes availability.

Worked examples (proposal values): tracking circuit 900 × 50 = 45,000 FE, 1,125 ticks on one
combustion generator; precision plate 900 × 100 = 90,000 FE, 2,250 ticks; etcher basic plate
1,200 × 400 = 480,000 FE, 12,000 ticks.

### 4.4 Open energy bindings

- G-OPEN-6 (Root): classic machines. Whether a classic controller debits one tick across several
  plugs (sum) or per plug, the plug's per-call receive limit and how many plugs a pattern can
  hold are C16a/C16b hatch-contract facts. Until frozen, the classic adapter fails
  `binding_unfrozen` instead of assuming a value. The legacy 600 FE/t etcher recipe fits one
  10,000 FE plug either way.
- Draft 01's rule "buffered energy is not a rate" and its G12 oracle are withdrawn.

## 5. Context and access

### 5.1 Context capabilities

| Capability | Meaning | Current source |
| --- | --- | --- |
| `cap:surface:<body>` | a player can stand on body B's surface Level | `TravelTarget.BodySurface`; `RocketTargetFlightPlanner.java:114-125` rejects the Space Level for surfaces |
| `cap:orbit:<body>` | a player can be in B's orbit context in the Space Level | `TravelTarget.Orbit`; `:126-135` |
| `cap:station:<body>` | a committed station whose `orbitBody` is B exists and a player can be aboard | `StationState.orbitBody`; `:136-146` resolves a Station target to its orbit body |
| `cap:access:minecraft:the_nether`, `cap:access:minecraft:the_end` | vanilla dimensions | §3.4 |
| `cap:discovered:<body>`, `cap:research` | §6 | |

The Overworld is the surface of `advancedrocketrycommunity:earth`; `cap:surface:earth` is the only
context available at round 0. **No producer outputs a Level-only capability.** All orbit and station
contexts share one Space Level, and that fact never makes one context imply another.

### 5.2 Producers of contexts

1. **Rocket route** `route:<route id>:<from>-><to>` for each direction of each effective route:
   slots = the context of the source anchor (`surface` for a surface anchor; `orbit` or `station`
   of the same body for an orbit anchor, because a docked rocket plans from the station's orbit
   body, ADR-044 §5), the blueprint parts and fuel of the committed test blueprint for that route,
   `cap:discovered:<to body>` when that body has `discovery_required` (PlanetaryDiscoveryPolicy),
   and a READY quote from `RocketTargetFlightPlanner.plan` (pure, takes catalogs and stats). Output:
   `cap:surface:<to>` or `cap:orbit:<to>`, never `cap:station:*`.
2. **Station creation** `code:station_create:<body>`: slots `cap:surface:<body>` and
   `advancedrocketrycommunity:station_deployment_kit`. `StationManager.createForPlayer` sets
   `orbitBody` to the body whose surface the player stands on (`StationManager.java:76-90`). Output
   `cap:station:<body>`. No rocket is needed to create the station; boarding it later is a route to
   `cap:orbit:<body>` plus the station target.
3. **Station warp** `code:warp:<from>-><to>`: slots `cap:station:<from>`, a warp core placed in the
   station, `cap:discovered:<to>` or `<to>` needing no discovery (`StarSystemKnowledge.bodyKnown`),
   `<to>` orbitable, and `cap:fe:warp_balance` with atomic debit = the in-system or interstellar cost
   by `WarpCostClass` (`StationWarpService.java:422-470`). Output `cap:station:<to>`. Players aboard
   move with the station (ADR-044 logical relocation), so this also yields presence there.
4. **Portals** as in §3.4.

### 5.3 Binding body-local producers

- World features and surface loot bind to `cap:surface:<body>` of the body whose surface Level
  contains the biome.
- Producers that run in the Space Level bind to the context their code checks: the black hole
  generator binds to `cap:station:<body>` for a body with a singularity profile, because its
  eligibility reads the committed station's live `orbitBody`
  (`BlackHoleGeneratorBlockEntity.java:263-290`); station-local environment producers bind to
  `cap:station:<body>`; a producer that works anywhere in the Space Level must say so in its row and
  then needs `cap:orbit:<any body>` or `cap:station:<any body>` through an OR slot.
- Tau Ceti f and g are reachable only through `code:warp:*->tau_ceti_f|g` followed by their
  in-system routes (ADR-063 lines 425-452).

### 5.4 Planner binding (G-OPEN-7 narrowed)

`RocketTargetFlightPlanner.plan(stats, fuel, source, sourceDimension, destination, celestial,
routes, stations, requestId, gameTime)` is a static pure method. The graph needs (Root): a JUnit
entry that builds `CelestialCatalog` and `RouteCatalog` from the effective resources with the
production codecs, a `RocketStats` calculator for a committed blueprint, and the committed
blueprint per route. Until these exist the route adapter fails `binding_unfrozen`.

## 6. Discovery and research

From current code:

- Discovery of B needs a data mission that targets B. `SatelliteMissionRegistry.launch` refuses a
  target outside the definition's `allowed_targets` (`SatelliteMissionRegistry.java:192-194`); the
  effective definition is the v1.8 `data_satellite.json` (`build.gradle:146`), which lists nine
  bodies including `tau_ceti_f` and `tau_ceti_g`, yield 120, discovery cost 100.
- Launch is logical, from the satellite terminal: no rocket is involved
  (`SatelliteTerminalBlockEntity.java:278-321`).
- The claim credits the yield and spends the captured fee in one operation
  (`SatelliteMissionRegistry.java:455-461`, `ResearchAccount.java:39-51`), so the first data mission
  discovers B from an empty account and leaves 20 research.

Producer `code:data_mission:<body>`:

| Slot | Members |
| --- | --- |
| terminal | `advancedrocketrycommunity:satellite_terminal` placed |
| package | the assembly inputs: `satellite_chassis`, `satellite_solar_module`, a data payload item that `SatellitePayloadRuntime.definitionFor` maps to `data_satellite`, a blank `satellite_control_chip` (`SatelliteTerminalInventory.java:45-52`) |
| power | OR of {`minecraft:redstone` (slot 5 charge), `cap:fe:advancedrocketrycommunity:satellite_terminal`} with atomic debit 2,000 (launch threshold; assembly needs 1,000) |
| target | B ∈ `allowed_targets` of the effective definition and `discovery_required` true; otherwise no producer exists for `cap:discovered:<B>` |

Outputs: `cap:discovered:<B>` and `cap:research`. Ownership and the 200-tick duration are recorded,
not modelled. Physical access is separate: discovery permits a route (§5.2), it never yields a
surface or orbit context. Ground-survey edges (ADR-066 §6.3) arrive with C18c as extra producers of
`cap:research`; they are not substituted for discovery.

## 7. Fixpoint, routes and cycles

### 7.1 Algorithm and ordering

1. **Canonical IDs.** Nodes: `item:<id>`, `fluid:<id>`, `cap:<family>:<args>`. Producers:
   `recipe:<id>`, `loot:<table>:<entry index>`, `feature:<placed feature>:<body>`, `code:<row id>`,
   `route:<id>:<from>-><to>`, `vanilla:<name>`, `root:<id>`. Ordering is by Java `String.compareTo`
   on these strings (UTF-16 code units; all IDs are ASCII).
2. **Synchronous rounds.** Round 0: every ungated root and `cap:surface:advancedrocketrycommunity:earth`.
   A producer is enabled in round k ≥ 1 when every slot has a member that became available in a
   round < k. Its outputs that are not yet available become available in round k. Equivalently,
   `rank(node) = min over producers (1 + max over slots (min over members rank))`.
3. **Counter worklist.** Each producer keeps a count of unsatisfied slots; each node keeps a list of
   its (producer, slot) occurrences. Nodes that became available in round k−1 are processed in
   ascending node ID. For each occurrence whose slot is still unsatisfied, the slot records that
   node as its satisfying member and the counter drops; a producer reaching 0 joins round k's list.
   Round k's producers are then applied in ascending producer ID; a node is credited to the first
   producer in that order that outputs it. Work is proportional to the total number of
   slot-member occurrences plus nodes plus producers (counted, §11), not to rounds × producers.
4. Input file order, member order inside a selector, map iteration and threads cannot change any
   report byte.

### 7.2 Minimal route

ADR-066 §6.3 asks for a minimal reachable route. **Metric (proposal): derivation height**, the
round number of §7.1. The reported route of a node is: its crediting producer, and for each slot the
satisfying member (the earliest-round member, ties to the smallest node ID), recursively. This route
has minimal height among all routes. It is not claimed minimal in total steps, machines or
resources; the report states `route_metric: "height"` and also prints the size of the chosen
route tree (distinct producers) as information.

### 7.3 Unavailable nodes and hard locks

After the fixpoint, for each unavailable required node n:

- `no_producer`: no producer outputs n.
- otherwise each producer P of n has **unsatisfied slots**, all of whose members are unavailable.
  The blocker graph has an edge n → m for every member m of every unsatisfied slot of every
  producer of n (capabilities included).
- Compute strongly connected components of the blocker graph (iterative Tarjan, bounded stack). A
  component is **cyclic** when it has two or more nodes or one node with a self-edge.
- Call P **cycle-blocked** when every unsatisfied slot of P has at least one member in n's
  component, and **externally blocked** otherwise.
- A cyclic component C is a **hard lock** when every producer of every member of C is
  cycle-blocked. Then C is self-sufficient but has no entry: seeding any one producer's missing
  members inside C would unlock all of C. Classification `hard_lock`, with the member list and, per
  member, each producer's in-component slot members.
- Every other unavailable node is `blocked`, with each producer's unsatisfied slots and their
  members. If it lies in a cyclic component that is not a hard lock, the report adds
  `cyclic_but_entry_blocked` and lists the externally blocked producers (the entries) and their
  outside blockers.

Consequences: a singleton without a self-edge is never a cycle; an optional recycling producer
next to an externally blocked acyclic producer is `blocked` (with the note), not a hard lock.

### 7.4 Cycles among available nodes

Reported for information: cyclic components of the producer dependency graph restricted to
available nodes. Each member has a finite rank, so each has an acyclic route; such cycles never
fail the run (ADR-061 §5.3).

## 8. Exemptions

`exemptions.json` (Root-owned): `id`, `category`, `reason`, `decision`. Categories:
`technical_block`, `fluid_block`, `force_field`, `unlit_torch`, `effect_only`, `creative_only`.
Failures: ID not in `U_req` (`exemption_unknown`); ID output by any producer, data or table
(`exemption_has_producer`); missing reason or decision (`exemption_incomplete`). Because project
items are never roots and an exempt ID has no producer, an exempt ID can never be available, so
draft 01's "exempt but reachable" warning is removed. Unused exemption entries do not exist by
construction; the report lists applied entries.

## 9. Variants

| Variant | Change | Assertion |
| --- | --- | --- |
| `release` | defaults; roots per §3.4 with gates | every member of `U_req` is available or exempt; no `hard_lock` |
| `no_tau_ceti` | remove every context, discovery and warp output keyed by `tau_ceti_f` or `tau_ceti_g` (surface, orbit, station, warp targets), and the routes touching them | every output of every C16-C18 recipe and producer that is in `U_req` is available, except the IDs listed in a committed `no-tau-unavailable.txt` (each with a C15c reason); any difference fails |
| `overworld_without_nether` | remove `vanilla:nether_portal` and `vanilla:end_portal` | diagnostic report only; shows what the Nether gate holds back |

Default COMMON configuration values only; the report lists the switches assumed
(`worldgen.overworldOres`, `classic.smallPlatePress`, machine profile and sawmill switches). A run
with a switch off is a separate diagnostic.

## 10. Report

`build/reports/recipe-graph/<variant>.json` (not committed; archived as evidence), schema 1, and
a Markdown rendering sorted by node ID. Exact fields:

| Field | Content |
| --- | --- |
| `schema_version` | 1 |
| `variant`, `base_commit`, `switches` | as run |
| `inputs` | for each family: `files`, `bytes`, `sha256` of the sorted path list plus contents; and the SHA-256 of each committed graph file |
| `counters` | `nodes`, `producers`, `slots`, `slot_member_occurrences`, `work_units`, `rounds`, `blocker_edges`, `scc_components` |
| `route_metric` | `"height"` |
| `available[]` | `node`, `round`, `producer`, `slots[]` (`slot`, `member`, `member_round`), `route_size` |
| `unavailable[]` | `node`, `class` (`no_producer`, `blocked`, `hard_lock`), `notes[]`, `producers[]` (`producer`, `unsatisfied_slots[]` (`slot`, `members[]`)) |
| `hard_locks[]` | `members[]`, `edges[]` (`from`, `producer`, `slot`, `to`) |
| `available_cycles[]` | `members[]` |
| `throughput[]` | `producer`, `energy_per_tick`, `processing_time`, `total_fe`, `ticks_one_generator`, `generators_full_speed`, `exceeds_witness_faces` |
| `external_references` | sorted list (§3.2) |
| `exemptions_applied[]` | `id`, `category` |
| `diagnostics[]`, `diagnostics_dropped` | `code`, `subject`, `detail`; at most 256 kept |

Every array is sorted by its first field, then by the next.

## 11. Limits

| Limit | Value | Failure |
| --- | ---: | --- |
| nodes | 16,384 | `limit:nodes` |
| producers | 32,768 | `limit:producers` |
| slots per producer | 64 | `limit:slots` |
| members per slot | 32 for kernel/classic types (runtime rule disables, §3.3); 1,024 otherwise | `limit:members` |
| slot-member occurrences in total | 1,048,576 | `limit:occurrences` |
| `work_units` (one per occurrence visit, node pop, producer application, blocker edge) | 8,388,608 | `limit:work` |
| blocker edges | 1,048,576 | `limit:blocker_edges` |
| diagnostics kept | 256, then a count | — |
| report size | 8 MiB | `limit:report` |

Every limit is checked before the allocation or visit it guards. Exceeding one stops with the named
failure and no partial report is presented as a result. Input-family limits are in §3.3. These are
upper bounds for correctness, not a performance claim; no JVM or timing run was made.

## 12. Rebalance proposals

ADR-064 §10.3. Each is separate; none rewrites inventories (ADR-061 §1.5); each goes in the
CHANGELOG when applied; none migrates a running process whose origin is unproved (ADR-064 §2.5).

| ID | Proposal | Condition |
| --- | --- | --- |
| RB-1 | retire `precision_control_circuit` after the cutting-machine advanced-circuit route is available in `release` | graph shows the classic route; running processes on the removed recipe pause with `recipe_missing` and keep progress (ADR-064 §2.3) |
| RB-2 | keep the `silicon_wafer` crafting stand-in until the boule → cutting route is available without it; then owner decides | risk B |
| RB-3 | keep `basic_circuit` crafting until the cutting route exists; then owner decides | — |
| RB-4 | keep `advanced_circuit` crafting until the cutting route exists; then owner decides | — |
| RB-5 | `precision_guidance_module`: no change | — |
| RB-6 | recipes naming `circuits/*` tags: no change | — |
| RB-7 | keep the delivered v1.2 `rolling_machine` and `precision_assembler` recipes; do not restore the legacy bodies, which add `forge:gears/steel` | restoring them is a separate owner proposal after the arc furnace body is qualified |
| RB-8 | restore both legacy producers per component where the legacy game had two (§2.2) | G-OPEN-9 |

## 13. Provenance, impact and integration surfaces

- Upstream use in this draft: the twenty bodies in the Root leaf, read as behaviour references.
  Nothing was copied into the repository. Future recipes are new DataGen output written from these
  facts, recorded in development provenance before code (ADR-061).
- Impact of this draft: none on code, data, assets, configuration, schema, network or saves.
- Future implementation (Root, not authorized here): eight registry items and their models,
  language and `forge:ingots/carbon` tag; DataGen recipes per §2.2 for the producers Root retains;
  the `arce.graph.resources` property; test resources `registered-content.txt`,
  `external-references.txt`, `roots.json`, `exemptions.json`, `producers.json`,
  `connection-witnesses.json`, `no-tau-unavailable.txt`; the registry-equality GameTest and one
  observation test per producer and witness row; test-side graph classes, each under 500 lines.
- Assets: component textures stay with the C16d asset review; `carbonbrick.png` is `EXCLUDE` and
  needs a NEW drawing. Nothing is approved here.

## 14. Open items

| ID | Question | Owner |
| --- | --- | --- |
| G-OPEN-2 | `control_board` or `control_circuit_board`; `liquid_io_board` or `fluid_io_board` | Root |
| G-OPEN-3 | carbon brick burn time (none proposed; a legacy fuel handler was not read) | Root |
| G-OPEN-6 | classic plug debit, receive limit and plug count (§4.4) | Root, C16a/C16b |
| G-OPEN-7 | planner JUnit entry, catalog decoding, `RocketStats` from a committed blueprint, blueprint per route | Root |
| G-OPEN-8 | optional static scan for code producers (§3.6) | Root |
| G-OPEN-9 | which legacy producers to restore per component (RB-8) | Root |
| G-OPEN-10 | arc furnace body and its hatch prerequisites; escape for risk C | Root; owner if balance changes |
| G-OPEN-11 | press inputs: ore pressed in place or ore item (silk touch) | Root, press acquisition contract |
| T-OPEN-1 | existence of `forge:gems/lapis`, `forge:dyes/lime`, `forge:dusts/glowstone`, `minecraft:wooden_slabs` in build-classpath tag data | Root |
| T-OPEN-2 | etcher lens and silicon boule selectors | C16c |
| R-OPEN-1 | glowstone dust root class (`nether` proposed; witch drop is an Overworld alternative) | Root review |
| R-OPEN-2 | obsidian and flint-and-steel root witnesses for the first portal (§3.4 rule 2) | Root review |
| E-OPEN-1 | connection-witness rows and free-face counts per receiver | Root |

This contract does not pass build, native, restart, V1/V2 or any v1.8 G0-G9 Gate.
