# ADR-063 — Materials, ores and planetary surfaces (C15)

```yaml
status: ACCEPTED
revision: 5
date: 2026-10-03
revision_4: PROPOSED in C15a; the accepted text is revision 3 until the owner accepts revision 4
revision_5: PROPOSED in C15b; section 5 as settled while implementing it
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-10-03
acceptance_basis: maintainer confirmation on 2026-10-03 after five independent review rounds
target_version: v1.8.0
slices: [C15a, C15b, C15c]
development_dependency: ADR-016, ADR-031, ADR-032, ADR-033, ADR-036, ADR-037, ADR-043, ADR-061, ADR-062
supersedes: ""
```

## Context

C15 delivers the ledger rows every later batch builds on: the metal materials,
ores and the small plate press (C15a), the Moon, Mars and Venus surfaces and
features (C15b), and fixed worlds for the six exotic legacy biomes (C15c). The
[content audit](../work/v1.8.0-content-audit.md) §3.1–§3.2 and §3.9 record the
legacy behaviour and the asset findings:

- materials and product items come from LibVulpes, which v1.8 does not
  approve as an asset source (ADR-061 §4.1); Advanced Rocketry adds titanium
  aluminide and titanium iridium; recipes name products through the ore
  dictionary;
- legacy product recipes (`util/RecipeHandler.java` 60–170): ore → ingot by
  smelting; 9 nuggets ↔ 1 ingot; 9 ingots ↔ 1 block; three ingots on a
  diagonal → 4 rods; rolling machine ingot → plate (300 ticks, 20 FE/t) and
  plate → sheet (300 ticks, 200 FE/t); small plate press metal block → 4 plates
  and ore → 2 dust; dust → ingot by smelting; coil from 8 ingots around a hole;
  gear from 4 rods, 2 plates and 1 ingot; fan from 4 plates and a rod; the
  steel fan is an ingredient of ten legacy recipes;
- the small plate press (`BlockSmallPlatePress` 95–150) is itself a downward
  piston: when it receives redstone power and the block below is a press
  recipe input and the block two below is obsidian, it removes the input block,
  drops the output item and extends; it has no owner and checks no protection;
- legacy ore generation defaults: copper 10 veins of 6, tin 10 of 6, rutile 6
  of 6, aluminum 1 of 16, dilithium 1 of 16, iridium off (1 of 16 when
  enabled). The generator (`OreGenerator` 43–80) ran in every dimension with
  stone, so the Moon and Mars got the same copper, tin, rutile and aluminium;
  the "Luna" dilithium count (10 per chunk) applied to every planet without an
  atmosphere, not only the Moon. Legacy players got iridium from the
  "Iridium Enriched" asteroid (`asteroidConfig.xml`), which the v1.6 resource
  tables (ADR-052) replaced with a gold, diamond and emerald `rich_asteroid`, so
  no current source yields iridium;
- legacy biomes set the top and filler blocks: moon turf (highlands), dark moon
  turf (lowlands), ferric sand (hot dry rock), basalt (volcanic), snow over
  packed ice with crystals (crystal chasms), gravel (ocean spires), grass
  (alien forest);
- craters have radii from 8 to about 91 blocks (`MapGenCrater.getBaseRadius`
  182–192) and raise a rim; volcanoes (`MapGenVolcano`, size 64) and geodes
  (radius 24–48) are multi-chunk generators;
- electric mushrooms give no light (light level 0);
- the legacy Moon turf, dark Moon turf and ferric sand textures are recolours
  of the vanilla grass top, the charcoal and lightwood log tops and leaves are
  recolours of vanilla logs and leaves, and the plate press faces are a
  filtered vanilla piston (audit §3.9); they are excluded.

The modern Moon is a flat Level of end stone; Mars and Venus (ADR-033) are
noise Levels of vanilla blocks with a fixed biome each.

## Decision

### 1. Material set (C15a)

| Material | Products registered by this project | Ore and raw |
|---|---|---|
| titanium | ingot, nugget, dust, plate, sheet, rod, gear, coil, block | `rutile_ore`, `deepslate_rutile_ore`, `raw_rutile` |
| aluminum | ingot, nugget, dust, plate, sheet, coil, block | `aluminum_ore`, `deepslate_aluminum_ore`, `raw_aluminum` |
| tin | ingot, nugget, dust, plate, block | `tin_ore`, `deepslate_tin_ore`, `raw_tin` |
| steel | ingot, nugget, dust, plate, sheet, rod, gear, fan, block | — |
| iridium | ingot, nugget, dust, plate, rod, coil, block | `iridium_ore`, `raw_iridium` (Moon and Mars placement, §5; none in the Overworld) |
| dilithium | dust, crystal | `dilithium_ore`, `deepslate_dilithium_ore` |
| silicon | ingot, nugget, dust, plate, boule | — (wafer exists) |
| titanium aluminide | ingot, nugget, dust, plate, sheet, rod, gear, block | — |
| titanium iridium | ingot, nugget, dust, plate, sheet, rod, gear, block | — |
| copper (vanilla ingot and block) | nugget, dust, plate, sheet, rod, coil | vanilla |
| iron (vanilla ingot and block) | dust, plate, sheet, rod | vanilla |
| gold (vanilla ingot and block) | dust, plate, coil | vanilla |

The product lists are the legacy ones: the LibVulpes material flags
(`LibVulpes.java` 349–359 at `c2ca79d`, read for facts only, ADR-061 §4.1)
and Advanced Rocketry's two alloys (`AdvancedRocketry.java` 897–898). No
legacy product is dropped and none is added; vanilla supplies the copper, iron
and gold ingots and blocks. The silicon nugget stays because the legacy boule
recipe (silicon ingots plus a silicon nugget) uses it unless C16c changes
that recipe. The five coils (copper, gold, aluminum, titanium, iridium) form
the `advancedrocketrycommunity:coils` group.

IDs follow vanilla word order (`titanium_ingot`, `raw_tin`, `tin_ore`,
`deepslate_tin_ore`, `titanium_block`). Coils are blocks; every other product
is an item. Tags follow ADR-061 §2 (`forge:` tags, plus
`advancedrocketrycommunity:sheets/…`, `coils/…`, `fans/…`, `boules/…` and the
`advancedrocketrycommunity:coils` group that replaces the legacy `blockCoil`).
Rutile ore and its deepslate variant carry both `forge:ores/rutile` and
`forge:ores/titanium`, because the legacy game registered rutile under both
`oreRutile` and `oreTitanium`; raw rutile carries `forge:raw_materials/rutile`
only. There is no separate Moon dilithium ore: the Moon's base block is
vanilla stone (§5), so the ordinary `dilithium_ore` serves it, as the legacy
generator used one dilithium block everywhere.
Rutile does not smelt in a furnace: titanium comes from the electric arc
furnace (C16b), as in the legacy game. Steel, the alloys and silicon boules
likewise wait for C16b and C16c; the recipe graph check (ADR-061 §5.3) starts
in C16d.

Art: one greyscale template per product kind, drawn new for this project
(`NEW`), tinted per material by a client colour handler with the legacy
material colours; ore and storage-block textures are drawn new. No LibVulpes
or vanilla-derived file is used.

### 2. Recipes (C15a)

- Smelting and blasting: ore, raw item and dust → ingot (dilithium ore →
  dilithium dust, as in the legacy smelting recipe); rutile excepted.
- Crafting: nuggets, blocks, rods (3 ingots → 4 rods), gears, coils and the
  steel fan with the legacy shapes, all on tags.
- Rolling machine (existing kernel recipe type, which requires its water
  input): ingot → plate, 300 ticks at 20 FE/t; plate → sheet, 300 ticks at
  200 FE/t; 100 mB of water each.
- Small plate press: metal block → 4 plates; ore → 2 dust.

**Revision 4 (C15a implementation findings, proposed).**

- *Iron plates.* The accepted v1.2 recipe `rolling_iron_bars` already takes
  iron ingots (two ingots → eight bars), and the rolling machine refuses an
  input that two recipes match. With an iron ingot → plate recipe, any stack
  of two or more iron ingots would match both and roll nothing (a single
  ingot would still match only the plate recipe), so iron is the one
  material without it: iron plates come from the small plate press (iron
  block → 4 plates). Every other plate material rolls its ingot; iron plates
  still roll into iron sheets. Cost: nine ingots make four plates, where the
  legacy game rolled one ingot into one plate; nine legacy recipes use iron
  plates and five use iron sheets, which are made from plates. Rejected
  alternative (C15aR1, open to the owner): re-key the project's own v1.2
  recipe `rolling_iron_bars`, which is not a legacy recipe (for example to
  iron plates or rods), and keep legacy iron rolling; ADR-061 §1.5 allows
  recipe changes with release notes. The recipe graph check (C16d) sees the
  press route.
- *Rolling ingredients name items.* The kernel recipe type resolves its
  ingredient when recipes load, before tags are bound, so a tag ingredient
  would refuse to load on a fresh start, load against stale tags on
  `/reload`, and could break a joining client's recipe sync (recipes are sent
  before tags). Rolling recipes name the item, as `rolling_iron_bars` does,
  and the kernel codec now rejects tags (ADR-061 §2.2, revision 7); crafting,
  smelting and press recipes stay on tags. This gives up ADR-061 §2.2's tag
  interoperability for the rolling machine until C16a: another mod's
  titanium, aluminum or copper ingot does not roll yet.

### 3. Small plate press (C15a)

A full block `small_plate_press`, always facing down, without a block entity.
On a rising redstone edge it reads the block below and the block two below. If
the block below matches a `small_plate_press` recipe (its block form is in the
recipe's ingredient), has no block entity and a non-negative destroy speed,
and the block two below is obsidian, the press removes the block below with a
normal block update and spawns the output item at its position. One operation
per rising edge; no fluid, energy or menu. The recipe type is data-driven with
bounded fields (one input ingredient, one output stack of at most 64). The
press acts only within loaded chunks. Its textures are drawn new.

*Revision 4 (proposed).* The ingredient may name tags (`forge:storage_blocks/…`,
`forge:ores/…`); its JSON is bounded when the recipe loads and the tags are
resolved when the press acts, after tags are bound. If two recipes match the
block below, the press does nothing rather than pick one by load order. A
switch `classic.smallPlatePress` (COMMON, default on) turns the press off; a
disabled press keeps its blocks and does nothing on a pulse. Rutile does not
press (C15aR1-H1): the legacy recipe generator made ore → dust press recipes
only for a material with its own ore and dust, and titanium's ore is rutile, a
separate material without dust. A press route would give titanium dust that
smelts in a furnace and bypasses the electric arc furnace §1 requires.

**Protection: the piston exception.** The press has no owner (no block
entity), so the ADR-054 §5 chain, which binds effects to a device owner, does
not apply. It is treated as a vanilla piston instead, and documented as the
piston-equivalent exception to ADR-061 §6: before acting it posts Forge's
cancellable `PistonEvent.Pre` for its position, direction down and move type
extend, which claim mods already use to stop pistons crossing claim borders,
and does nothing if the event is cancelled. Its reach is one block, the same as
a piston's, and it removes only a block that is a press recipe input.

### 4. Ore placement (C15a)

A Forge biome modifier `advancedrocketrycommunity:overworld_ores` adds placed
features to `#minecraft:is_overworld` biomes: tin (10 veins of 6), rutile (6 of
6), aluminum (1 of 16), dilithium (1 of 16), uniform between y −16 and 64 with
deepslate variants below y 0. Iridium is not placed in the Overworld, as in the
legacy defaults, and vanilla keeps generating copper there. Data packs may
override or remove the modifier, and a server switch turns it off (ADR-061
§3.5).

*Revision 4 (proposed).* Each vein's targets are the vanilla
`stone_ore_replaceables` and `deepslate_ore_replaceables` tags, so the
deepslate variant appears wherever a vein replaces deepslate (below y 0 and in
the vanilla transition band), as vanilla ores do. The features are named
`overworld_<ore>_ore`, leaving the plain names free for the Moon and Mars
features of C15b. The server switch is the COMMON value
`worldgen.overworldOres` (default on), read by a `server_switch` placement
modifier that every Overworld ore feature lists first; turning it off stops
the ores in new chunks without a data pack.

**Moon and Mars ores (C15b).** As in the legacy game, the Moon and Mars get the
same metal ores, through their own biomes' feature lists (§5): copper (vanilla
copper ore, 10 veins of 6), tin (10 of 6), rutile (6 of 6), aluminum (1 of 16)
and iridium (1 of 16), uniform between y 4 and y 40. Dilithium follows the
legacy airless rule: 10 veins of 16 on the Moon, which has no atmosphere, and 1
of 16 on Mars. On the Moon the ores replace stone; on Mars they replace its base
block, red sandstone. No other Level gets these ores.

**Iridium source.** Iridium ore is placed on the Moon and Mars (§5), one vein
of 16 per chunk, uniform between y 4 and y 40 (the legacy `IridiumPerChunk` and
`IridiumPerClump` defaults). This replaces the legacy iridium-enriched asteroid
as the guaranteed source: the Moon is the first rocket destination, so the
titanium-iridium alloy (C16b) and the advanced bipropellant and nuclear engines
(C17a) that need it stay reachable, and the recipe graph check (ADR-061 §5.3)
finds the Moon as the Level that gives access to iridium. Asteroid and laser
drill tables may list iridium later only by a data revision under ADR-052 or
ADR-055; no batch depends on that.

### 5. Moon, Mars and Venus (C15b)

- Blocks: `moon_turf`, `dark_moon_turf`, `ferric_sand`, `charcoal_log`,
  `geode_shell`, with textures drawn new.
- Biomes: `regolith_highlands`, `regolith_lowlands` (Moon), `ferric_regolith`
  (Mars), `volcanic`, `volcanic_lowlands` (Venus), with the legacy top and
  filler blocks over vanilla stone (Moon) or over the v1.4 base blocks, red
  sandstone (Mars) and basalt (Venus).
- **Moon terrain, bounded below the landing height.** The Moon becomes a noise
  Level with a two-biome source and low relief, not the legacy height: the
  surface lies between y 12 and y 36 (lowlands y 12–20, highlands y 20–36),
  crater floors stay at or above y 4 and crater rims at or below y 44, so no
  generated block lies above y 63. The legacy base height would put the
  surface near y 75–100.
- **Moon landing unchanged (ADR-033).** The Moon keeps its fixed pads around
  (8, 80, 8) and the legacy minimum landing y 80 without a ground-support
  check, exactly as on today's flat Moon, whose surface is y 4: a rocket
  arrives at y 80 or above, over terrain that is always lower. The developer
  platform (`SafeCelestialTravel`, a 5 × 5 floor at y 79 with three blocks of
  clearance) therefore always sits in open sky and never inside a hill. The
  ADR-033 support check for other surfaces is not adopted on the Moon, because
  it would move existing Moon arrivals.
- **Mars and Venus shape unchanged.** Mars and Venus keep their v1.4 noise
  router, base block and heights, and switch to multi-biome sources with the
  new biomes; only the top and filler blocks and the features change.
- **Generation mechanisms.** In 1.20.1 a placed feature may write only within
  the 3 × 3 chunks around its origin chunk, and a carver only removes blocks.
  Each legacy generator therefore gets the mechanism its size needs:

  | Generator | Mechanism | Bounds |
  |---|---|---|
  | craters (Moon, Mars) | structure `crater`, one piece whose bounding box covers the bowl and the raised rim | radius 8–48 (legacy up to about 91, capped for the generation budget); structure set spacing 6 and separation 3 chunks; Moon floor ≥ y 4 and rim ≤ y 44 (§5 Moon terrain) |
  | volcanoes (Venus) | structure `volcano`, one piece (cone, crater and lava core) | cone radius ≤ 32 (legacy size 64), height ≤ 48 above the surface; spacing 16, separation 8 chunks |
  | ore geodes (Venus) | structure `geode`, one piece below the surface | radius ≤ 24 (legacy 24–48, capped for the generation budget); ores from a block tag; spacing 8, separation 4 chunks |
  | charred trees (Venus) | placed feature | ≤ 4 blocks from the origin, height ≤ 12 |
  | Moon and Mars ores (§4) | vanilla `ore` configured features | vein size ≤ 16 |
  | caves and canyons | vanilla cave and canyon carvers where the legacy planets had them | vanilla bounds |

  A structure piece writes only into the chunk being generated (it is clipped
  to that chunk's box), so its size is limited by the generation budget, not by
  the feature write radius. Structure starts are saved in chunks by vanilla, so
  a crater or volcano started before a restart finishes in chunks generated
  after it. The structures have no loot and no advancement.
- Every structure and feature is bounded in size and count, runs only at chunk
  generation and has a server switch: a disabled structure returns no
  generation point and a disabled feature places nothing; starts already
  saved still finish.
- **Seams (ADR-033, ADR-061 §6).** The three Levels keep their IDs (data-pack
  dimension definitions take precedence over `level.dat`, so existing worlds
  get the new generator for new chunks). Chunks generated before the upgrade
  keep their old surface; chunks generated afterwards use the new terrain.
  Expected seams:
  - Moon: old chunks are flat end stone with the surface at y 4; new chunks
    rise to y 12–36, so explored areas end in a wall of 8–32 blocks (up to 40
    at a crater rim) and a change from end stone to moon turf over stone.
  - Mars and Venus: no height step, because the terrain shape is unchanged; the
    top block changes (red sand to ferric sand on Mars, yellow terracotta to
    basalt on Venus), and craters and volcanoes appear only in new chunks.
  - The upgrade band (revision 5, C15bR1-M1): around explored areas the old
    build saved a ring of chunks part-way through generation. The new build
    finishes them with the new terrain but keeps the biome they already
    stored (the old `plains` on the Moon, the v1.4 biomes on Mars and Venus).
    Their surfaces are right (the Moon's highland turf does not depend on the
    biome), but they get none of the new biomes' ores, and no crater, volcano
    or geode starts in chunks whose structure starts the old build had already
    computed, a ring about one chunk wider. The band is about a chunk wide.
  Arrivals keep their rules: the Moon keeps its fixed y 80 rule over lower
  terrain, and Mars and Venus land on the heightmap (ADR-033). Players who
  want the new terrain everywhere start a new world or explore farther out.
  The release notes repeat these step heights and the backup advice of the
  celestial data guide.
- **Moon arrival height (player impact).** A Moon arrival stays at y 80 or
  above without ground support, as it is today, so a rocket hangs over the
  regolith instead of standing on it, unlike the legacy game, whose rockets
  descended to the ground. Today the drop to the flat surface (y 4) is 76
  blocks; over the new terrain it is 44–68 blocks (surface y 12–36), and up
  to about 76 over a crater floor. Players get down as they do now: by
  building down from the rocket, from the developer platform at y 79 where
  it is used, or with the jetpack (C18b); C18a's gravity-scaled fall damage
  makes the fall survivable at Moon gravity. Landing Moon rockets on the
  heightmap is a change to ADR-033's fixed-pad rule and needs its own
  landing-rule ADR, which must keep existing Moon pads and recovery
  identities working; v1.8 does not make that change.

*Revision 5 (proposed, C15b).* Settled while implementing §5:

- **Crater structures.** One structure type `crater` with two structures:
  `moon_crater` (floor ≥ y 5, rim ≤ y 44) and `mars_crater` (floor ≥ y 5,
  rim ≤ y 250), radius 8–48 weighted towards small craters as in the legacy
  generator, bowl depth at most 11 (15 above radius 32), rim at most one
  block per eight of radius. Floors stop at y 5 because the bedrock gradient
  reaches y 4. Both run at the raw-generation step; volcanoes run with the
  surface structures and geodes with the underground structures.
- **Volcano relief.** A volcano does not start where any column of its cone
  has its ground more than 32 blocks (the skirt) below the base, so a cone
  never hangs over a Venus cliff.
- **Venus biomes.** The router is copied unchanged, so its climate
  parameters are all zero and a multi-noise source cannot split `volcanic`
  from `volcanic_lowlands`. Venus uses a new biome source
  `advancedrocketrycommunity:patches`: irregular patches from jittered cell
  centres (cells of 32 quarts) with a fixed salt. A 1.20.1 biome source
  receives no world seed, so the layout is the same in every world: the
  volcanic and volcanic lowland patches lie in the same places in every
  world, while the terrain and structures still follow the seed. The legacy
  planets' biome layers did follow the world seed (C15bR1-M5 corrected an
  earlier statement here). A per-world layout would need a seeded climate
  noise added to the Venus router, its terrain density unchanged, so the
  noise settings would no longer equal the v1.4 file; that is open to the
  owner. The Moon keeps a multi-noise source on its own router.
- **Caves and canyons.** The legacy generator ran caves and ravines only on
  planets whose definition enabled them (`generateCaves`, default off), and
  the legacy Moon did not; Mars and Venus were not legacy default planets.
  The three bodies therefore get no carvers. Vanilla cave and canyon carvers
  stay available to data-pack planet biomes, which is how the four cave and
  ravine rows are delivered.
- **Spawns and colours.** The new biomes spawn nothing, as the v1.4 Mars and
  Venus biomes (the legacy volcanic biome's creepers are not restored). Mars
  and Venus keep their v1.4 fog and sky colours; the Moon biomes have a black
  sky.
- **Geodes.** Radius 16–24, centred four blocks of cover plus their half
  height below the lowest ground of a 5 × 5 grid over the lens; where a
  column's ground still lies lower, its roof comes down, so that no geode
  block lies within four blocks of any column's ground (the geode never opens
  to the surface, on cliffs too); ores from the block tag
  `advancedrocketrycommunity:geode_ores` (default: the legacy list, iron,
  gold, copper, tin and redstone ores).
- **Charred trees.** In the `volcanic` biome only, on average once every ten
  chunks (the legacy decorator's extra-tree chance with no trees per chunk),
  a trunk of six to eight charcoal logs with at most one stub branch.
- **Switches.** COMMON values `worldgen.planetOres`, `worldgen.craters`,
  `worldgen.volcanoes`, `worldgen.geodes` and `worldgen.charredTrees`
  (default on), read through the same `server_switch` placement modifier and
  by each structure's generation point.
- **Surfaces.** The Moon's top and filler are the turfs over vanilla stone
  (moon turf on the highlands, dark moon turf on the lowlands); Mars has
  ferric sand top and filler over red sandstone; Venus is basalt throughout.
- **Drops and tools.** The two turfs and ferric sand dig with a shovel and
  drop themselves. The geode shell (hardness 6, blast resistance 2,000) drops
  itself only for an iron pickaxe or better: the legacy geode needed the
  jackhammer at harvest level 2, which comes in C18b. The charcoal log does
  not burn and drops one charcoal, as the legacy log did (itself with Silk
  Touch; Fortune adds 0 up to its level, where the legacy log added 0 up to
  one less).
- **Saved pieces.** Crater, volcano and geode pieces save schema version 1
  with their numbers, so a start saved before a restart finishes the same
  way after it; a piece of another schema is refused, and vanilla then drops
  that start with a logged error.

### 6. Classic exoplanet worlds (C15c)

Two new bodies in the Tau Ceti system (ADR-043), each landable with a startup
Level like Mars and Venus (ADR-033) and discovery required (ADR-037):

| Body | Biomes | Environment |
|---|---|---|
| `tau_ceti_f` | `alien_forest`, `marsh`, `deep_swamp`, `ocean_spires` | breathable, 1.0 atm, 295 K |
| `tau_ceti_g` | `stormland`, `crystal_chasms` | not breathable, 1.4 atm, 255 K |

Flora and features: lightwood log, leaves, sapling and planks with the large
alien tree; electric mushrooms (no light, as in the legacy game; the client
lightning flash is an effect setting, never real lightning); swamp trees; inverted pillars; six
crystal block colours with large crystal clusters (moved here from C15b,
where no world uses them). The trees, pillars and clusters are placed features
whose writes stay within 12 blocks horizontally of the origin, inside the
feature write radius:
the large alien tree (height 20–29, as legacy), swamp trees (height 40–49 with
roots down to 20 below, as legacy, canopy radius ≤ 12), inverted pillars
(radius 5, height 20–33, as legacy) and crystal clusters (height 10–49, as
legacy; the lean is capped so the top stays within the bound). Textures
follow the asset plan; files the derivation check excluded (the lightwood
leaves, log top and planks) are drawn new.
Bindings for the two new Levels are added at startup by the existing ADR-032
rules; Tau Ceti e is unchanged.

**Access path.** Both bodies are orbitable and landable, and both require
discovery. A player reaches them in four steps, each an existing mechanism:

1. **Discovery (ADR-037, ADR-043).** The data satellite's allowed targets gain
   `tau_ceti_f` and `tau_ceti_g` through a v1.8 copy of
   `satellite_definitions/data_satellite.json` (duration 200 ticks, yield 120,
   discovery cost 100, unchanged), which supersedes the v1.5 copy (§8). The
   target is chosen at the satellite terminal and does not depend on where the
   satellite flies, as for Tau Ceti e (ADR-043), so a player discovers the
   bodies without leaving the Sun's system. A surface visit records discovery
   as for every mapped body.
2. **Warp (ADR-044).** A known, orbitable body is a warp target. A station warps
   to the orbit of `tau_ceti_f` or `tau_ceti_g` at the interstellar cost
   (`stations.warpCostInterstellar`, default 8,000,000 FE), or between them at
   the in-system cost. Rockets never fly between systems (ADR-043), so the
   station is the only way in and out.
3. **Routes.** Three bidirectional routes, all inside the Tau Ceti system, are
   added in the same v1.8 output: `tau_ceti_f_surface_orbit` (25 distance
   units) and `tau_ceti_g_surface_orbit` (35) between each surface and its
   orbit, and `tau_ceti_f_g` (60) between the two surfaces. A rocket docked at a
   station plans from the station's orbit body (ADR-044 §5), so it can land on
   the surface below, return to the station, and fly between the two surfaces.
   No Earth route is added; it would be interstellar and the route validator
   rejects it (ADR-043).
4. **Landing.** The bodies use the heightmap landing rule of Mars and Venus
   (ADR-033), not the Moon's fixed pads.

Tau Ceti e keeps no route, as ADR-043 disclosed.

**Optional content.** Nothing in the classic progression depends on C15c. The
lightwood, electric mushrooms, swamp trees and crystal blocks feed no recipe of
another batch; the only legacy consumer of a crystal (`crystal@3` in the vacuum
laser recipe) was redesigned by ADR-055, whose laser drill is built from the
endgame casing and a laser lens without crystals. The recipe graph check
(ADR-061 §5.3) treats both Levels as reachable only after an interstellar warp
and must still find every C16–C18 output reachable without them.

*Revision 6 (proposed, C15c).* Settled while implementing §6:

- **Bodies.** Both orbit Tau Ceti, are landable and orbitable, need
  discovery and have environment effects. `tau_ceti_f`: gravity 1.0, orbit
  199,000,000 at a period of 20,764,800 ticks, solar intensity 0.8, radiation
  0. `tau_ceti_g`: gravity 1.2, orbit 19,400,000 at 646,400 ticks, solar
  intensity 1.2, radiation 0.1. Distances follow the real planets' (1.33 and
  0.13 AU, on the scale of Tau Ceti e) and periods use Tau Ceti e's ticks per
  real day. With §6's atmospheres neither world harms an unprotected player
  (cold below 240 K, pressure above 2 atm and sunlight above 1.5 cause damage);
  Tau Ceti g is not breathable.
- **Skies.** Each body has its own atmosphere and visual profile, and the two
  visual profiles are new built-ins beside the Moon, Mars, Venus and space
  ones: a blue-green day sky for f and the dark storm sky of the legacy
  stormland (`0x202020`) for g.
- **Levels and terrain.** Dimension types `tau_ceti_f` and `tau_ceti_g` (as
  Mars: heights 0–256 and a day cycle). Each Level has low relief from one 2D
  noise `n` in [-1, 1], as the Moon (§5), over vanilla stone with bedrock at
  y 0–4 and no caves, aquifers or ore veins. Tau Ceti f has water up to y 62
  and its top block at `63 + 22n` (y 41–85); the same noise is the
  continentalness of a multi-noise source, so the biomes follow the ground:
  ocean spires below n = -0.3 (the sea floor at y 56 and below), marsh to -0.1
  (shallows, y 57–60), deep swamp to 0.1 (the shore, y 61–65) and alien forest
  above. Tau Ceti g
  has no sea and its top block at `96 + 8n` (y 88–104), a plateau like the
  legacy stormland and crystal chasms (base height 1); its two biomes lie in
  irregular patches (the `patches` source of revision 5, its own salt), the
  same in every world as on Venus.
- **Landing ground.** A rocket lands only on one of eight fixed pads around
  the origin, on ground with no fluid and nothing standing in its footprint
  (ADR-006, ADR-033). Unchanged, Tau Ceti f put all eight pads under the sea in
  89 of 1,000 seeds, and its jungle grass, trees, mushrooms and crystals block
  most land pads on both worlds (in one GameTest run a rocket came down only on
  the fifth Tau Ceti f pad). Each world therefore keeps a landing ground of
  radius 160 around the origin: the farthest pad's widest footprint (16
  chunks, 64 blocks around a pad on a chunk corner) reaches about 136 blocks,
  and a feature writes up to 12 blocks from where it starts. No Tau Ceti
  feature, and no alien forest grass (the vanilla jungle patch, placed by this
  mod's `alien_forest_grass`), starts inside it (the
  `advancedrocketrycommunity:landing_ground` placement filter). On Tau Ceti f,
  `n` is raised to at least 0.2 inside it (the
  `advancedrocketrycommunity:landing_ground_floor` density function: 0.2 within
  160 blocks, falling by one per 96 blocks beyond), so the pads stand on dry
  alien forest grass at y 67 or above and the sea begins about 24 blocks
  farther out. Tau Ceti g has no sea and needs only the filter.
- **Biomes.** The legacy surfaces and colours: alien forest (grass over dirt;
  grass `0x7777FF`, foliage `0x55FFE1`, water `0x8888FF`), marsh (grass over
  dirt, clay and lily pads), deep swamp (grass over dirt; water `0xE0FFAE`, sky
  `0x203020`), ocean spires (gravel), stormland (grass over dirt; grass and sky
  `0x202020`, rain), crystal chasms (snow block over packed ice, temperature
  0.1). Nothing spawns, as in revision 5 (the legacy deep-swamp slimes and
  stormland creepers are not restored).
- **Features.** Each is a placed feature behind a COMMON switch and writes only
  within 12 blocks horizontally of its origin; numbers are the legacy ones
  unless stated:
  - lightwood tree (`worldgen.lightwoodTrees`): in 1 of 20 alien forest
    chunks; a 2 × 2 trunk 20–29 high with branches and leaf blobs, the branches
    shortened so that nothing passes 12 blocks (the legacy tree reached about
    14); dense grass (the vanilla jungle patch, 25 per chunk);
  - giant swamp tree (`worldgen.swampTrees`): in 1 of 100 deep swamp chunks;
    40–49 high with roots to 20 below and a canopy within 12 blocks; vanilla
    swamp oaks, sugar cane, lily pads, mushrooms and blue orchids around it;
  - marsh: vanilla clay disks and lily pads;
  - inverted pillar (`worldgen.invertedPillars`): in about half the ocean
    spire chunks (legacy 7 in 16); 20–33 high from the sea floor, widening
    from radius 1 at the foot to 5 at the top; mossy cobblestone in the lower
    third, cobblestone, then dirt under a grass top;
  - crystal cluster (`worldgen.crystalClusters`): in 1 of 36 crystal chasm
    chunks; 10–49 high, edge radius 2–5, one of the six colours, the lean capped
    so that the top stays within 12 blocks;
  - electric mushrooms (`worldgen.electricMushrooms`): one patch per stormland
    chunk, 64 tries within 8 blocks;
  - charred trees (the `worldgen.charredTrees` switch of revision 5): six per
    stormland chunk, as the legacy stormland's trees.
- **Blocks.** Lightwood log (a log that burns, hardness 3), lightwood leaves
  (vanilla leaves with light 8: decay, a sapling in 1 of 100 drops and nothing
  else, as legacy; shears or Silk Touch take the leaves),
  lightwood sapling (a vanilla sapling: two stages, bone meal succeeds in 45 %
  of uses, as legacy; grows the lightwood tree where it fits), lightwood planks
  (hardness 3, light 4; four from a log), six crystal blocks (`violet_crystal_block`,
  `blue_crystal_block`, `green_crystal_block`, `red_crystal_block`,
  `yellow_crystal_block`, `orange_crystal_block`: hardness 2, glass sound,
  translucent, one drawn texture tinted with the legacy colours, each drops
  itself) and the electric mushroom (mushroom placement, hardness 0, no light).
  The lightwood blocks join the vanilla log, planks, leaves and sapling tags, so
  vanilla wood recipes accept them.
- **Electric effect.** Electric mushrooms throw sparks on the client. During
  rain in a stormland the client also flashes the sky and plays a distant
  thunder sound, at most once every five seconds; the CLIENT value
  `effects.electricMushroomFlashes` (default on) turns the flashes off. Nothing
  is struck, set on fire or sent over the network.
- **Assets.** Every C15c texture is drawn new (ADR-061 §4.2, as the geode shell
  in revision 5): the crystal, lightwood sapling and electric mushroom textures
  the plan would import and the lightwood log under authorship review; the
  asset plan's rules for them become `REGENERATE`.
- **Recipe graph.** The check with both Levels removed (§9) runs with the C16d
  recipe graph tool; C15c adds only the planks recipe and the vanilla tag
  memberships above.

### 7. Persistence and migration

No saved-data schema changes. New IDs only (ADR-061 §1). New Levels bind on
first start. Existing discovery records stay valid; the two new bodies start
undiscovered in every world, old and new. Data packs that already use any new
ID in this namespace fail startup through the existing binding checks.

### 8. Generated data layout

- **Output root.** DataGen writes every v1.8 resource to
  `src/generated/v1.8/resources`, the one generated root of this version (C15
  to C18 add to it). The run configuration switches its `--output` to that root
  and adds `src/generated/v1.7/resources` to the `--existing` list; the root is
  added to the main resource source set. `runData` followed by
  `git diff --exit-code` verifies it.
- **Superseded copies.** The v1.8 root holds new versions of resources that
  earlier roots already contain. The earlier files stay where they are (their
  generated-manifest checks keep passing) and are excluded from
  `processResources` and `sourcesJar`, as ADR-043 did for the data satellite:
  - `src/generated/v0.3/resources`: `dimension/moon.json` (the flat Moon
    generator);
  - `src/generated/v1.4/resources`: `dimension/mars.json`,
    `dimension/venus.json`, `worldgen/noise_settings/mars.json` and
    `worldgen/noise_settings/venus.json` (new biome sources and surface rules;
    the noise router is copied unchanged);
  - `src/generated/v1.5/resources`: `satellite_definitions/data_satellite.json`
    (§6);
  - the `minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool` block tags
    of `src/generated/v1.7/resources`, joining the earlier copies already
    excluded, plus a new `minecraft:needs_stone_tool` copy for the ores.
  The `build.gradle` exclusions are the integrator's change (ADR-060), made in
  the same commit as the first v1.8 DataGen output, and the
  one-authoritative-copy-per-resource audit (ADR-031, ADR-037) reruns over all
  roots.
- **Old biomes stay.** The v1.4 biomes `advancedrocketrycommunity:mars` and
  `advancedrocketrycommunity:venus` stay registered and shipped, because chunks
  generated before the upgrade store them (ADR-061 §1.4); the new biome sources
  no longer list them. The Moon had no biome of its own; its old chunks keep
  `minecraft:plains`, the biome of the flat generator.

### 9. Tests

- A0: material table completeness against the ledger; recipe and tag JSON
  audit; DataGen determinism; feature bound checks; the asset records and the
  derivation check for every imported file; the JEI plugin registers a plate
  press category whose recipe views list every `small_plate_press` recipe
  (`integration:jei/platePresser`), tested through the client compat package
  as for the v1.2 machines (ADR-061 §3.3), and the game starts with and without
  JEI.
- A0: the data satellite definition lists both new bodies; the three Tau Ceti
  routes stay inside one system; the recipe graph check passes with both Tau
  Ceti Levels removed from the reachable set.
- A1: plate press (block → plates; ore → dust; no obsidian; block entity
  below; unpowered; repeated pulses; unloaded neighbour; a cancelled
  `PistonEvent.Pre` leaves the block in place; revision 4: an ambiguous match
  leaves the block in place), smelting and rolling
  recipes, ore feature placement in a test chunk (the Overworld set without
  iridium; the Moon and Mars sets with iridium; Moon dilithium at the airless
  count), bound tests for the crater,
  volcano and geode structures (bounding box within the stated radius, every
  write inside the chunk being generated) and for each placed feature (every
  write within the 3 × 3 chunks around its origin), a
  Moon height bound test (no generated block above y 63 over a sampled grid of
  at least 4,096 columns for two seeds, crater rims included), a rocket
  landing on the Moon over a crater and over the highest highlands (arrival at
  y 80 or above, as before), developer platform travel to the Moon over the
  highest highlands (open sky above the y 79 floor), each server switch, and
  the Tau Ceti
  path: a data-satellite discovery of `tau_ceti_f`, an interstellar warp to its
  orbit, a docked rocket's landing on its surface and return to the station
  (revision 6: on the first pad), and on both new Levels dry, clear ground
  within 32 blocks of each pad (revision 6).
- S1: a packaged dedicated server generates chunks in the Moon, Mars, Venus
  and both new Levels without errors, within the tick budget of a reference
  chunk-generation run, and an upgraded v1.7 world keeps its explored chunks.

## Alternatives

### A. Keep the flat Moon

Avoids seams but drops the classic regolith terrain and craters.

### A2. Legacy Moon height with the ADR-033 support check

Restores the legacy surface height (about y 75–100), but buries explored areas
behind a wall of 70–95 blocks, puts terrain above the fixed y 80 landing and
the y 79 platform, and changes where existing Moon arrivals land. Rejected in
favour of terrain bounded below y 64.

### B. Make Tau Ceti e landable

Changes an existing body's capabilities and binding; new bodies avoid that.

### C. Recolour the legacy turf textures

They are themselves recolours of the vanilla grass top; any further edit stays
a vanilla derivative (ADR-061 §4.8).

### D. Let a landing clear plants (revision 6)

A landing could accept replaceable plants (grass, ferns, snow layers) in the
footprint and remove them, as the legacy rocket simply came down on them. That
changes ADR-006's rule that a pad be free of blocks and that a failed landing
never overwrite the world, and it changes Overworld landings too; it would not
help a pad under the sea. Left to a rocket-landing ADR; revision 6 keeps the
pads clear by world generation instead.

## Consequences

- Classic materials, ores and surfaces exist before the machines that use them.
- The batch draws its own material, ore, turf and plate-press art.
- Explored areas of the Moon, Mars and Venus keep their old look until players
  reach new chunks.

## Revisit when

- the owner prefers a flat Moon, or different exoplanet bodies;
- a feature exceeds its chunk-generation budget;
- a landing-rule ADR lands Moon rockets on the heightmap (the arrival height
  above then changes).

## Acceptance record

Accepted on 2026-10-03 by root after the maintainer confirmed it in the
development session, following five independent contract-review rounds
(rounds 1 to 4 accepted the v1.8 contracts with required changes; round 5
accepted ADR-060..063). Every Critical, High and Medium finding is resolved,
and the round-5 Low and Info findings are applied in this text. Reports and
dispositions are in the [preparation
evidence](../work/v1.8.0-preparation/VERIFICATION.md).

Acceptance freezes this contract for the v1.8 batches. It is not a
runtime-completion claim, a Gate PASS or a publication decision. Later changes
need a new revision and review.

## Review history

- Revision 1: proposed after C14 review round 1, for review round 2.
- Revision 2: answers review round 2 (M3–M6, L4–L7, I4), one commit per
  finding; see
  [review-02-dispositions](../work/v1.8.0-preparation/review-02-dispositions.md).
- Revision 3: review round 3 accepted revision 2; C14R3-L2 (the Moon arrival
  height) is stated in §5; see
  [review-03-dispositions](../work/v1.8.0-preparation/review-03-dispositions.md).
- Revision 4 (proposed, 2026-10-03): C15a implementation findings in §2
  (iron plates from the press; rolling recipes name items), §3 (tags resolved
  when the press acts, ambiguous matches refused, the press switch) and §4
  (vanilla replaceable tags, feature names, the ore switch). For the C15a
  implementation review and the owner's acceptance, with the C15a evidence
  packet (`docs/work/v1.8.0-c15a-materials`).
- Revision 5 (proposed, 2026-10-03): C15b decisions in §5 (two crater
  structures of one type, the Venus patch biome source, no carvers on the
  three bodies, no spawns, geode and charred-tree numbers, the five switches,
  the surfaces, the drops and tools, the saved pieces). For the C15b
  implementation review and the owner's acceptance.
- Revision 6 (proposed, 2026-10-03): C15c decisions in §6 (the bodies' numbers,
  their skies, Levels and terrain, the landing ground, the six biomes, the
  features with their switches and numbers, the blocks, the electric
  mushroom's client effect, all textures drawn new, the recipe graph check in
  C16d) and alternative D. For the C15c implementation review and the owner's
  acceptance.
