# ADR-063 — Materials, ores and planetary surfaces (C15)

```yaml
status: PROPOSED
revision: 1
date: 2026-10-02
deciders: [sunthemoon]
owner: sunthemoon
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
  drops the output item and extends;
- legacy ore generation defaults: copper 10 veins of 6, tin 10 of 6, rutile 6
  of 6, aluminum 1 of 16, dilithium 1 of 16 (Overworld) and 10 per chunk on the
  Moon, iridium off (1 of 16 when enabled); legacy players got iridium from the
  "Iridium Enriched" asteroid (`asteroidConfig.xml`), which the v1.6 resource
  tables (ADR-052) replaced with a gold, diamond and emerald `rich_asteroid`, so
  no current source yields iridium;
- legacy biomes set the top and filler blocks: moon turf (highlands), dark moon
  turf (lowlands), ferric sand (hot dry rock), basalt (volcanic), snow over
  packed ice with crystals (crystal chasms), gravel (ocean spires), grass
  (alien forest);
- craters have radii from 8 to about 70 blocks; volcanoes and geodes are large
  generated structures;
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
| titanium | ingot, nugget, dust, plate, sheet, rod, gear, block | `rutile_ore`, `deepslate_rutile_ore`, `raw_rutile` |
| aluminum | ingot, nugget, dust, plate, sheet, rod, coil, block | `aluminum_ore`, `deepslate_aluminum_ore`, `raw_aluminum` |
| tin | ingot, nugget, dust, plate, rod, block | `tin_ore`, `deepslate_tin_ore`, `raw_tin` |
| steel | ingot, nugget, dust, plate, sheet, rod, gear, fan, block | — |
| iridium | ingot, nugget, dust, plate, rod, block | `iridium_ore`, `raw_iridium` (Moon and Mars placement, §5; none in the Overworld) |
| dilithium | dust, crystal, block | `dilithium_ore`, `deepslate_dilithium_ore`, `moon_dilithium_ore` |
| silicon | ingot, boule | — (wafer exists) |
| titanium aluminide | ingot, nugget, dust, plate, sheet, rod, gear | — |
| titanium iridium | ingot, nugget, dust, plate, rod | — |
| copper (vanilla ingot) | nugget, dust, plate, rod, coil | vanilla |
| iron, gold (vanilla) | iron dust, plate, sheet and rod; gold dust and plate | vanilla |

IDs follow vanilla word order (`titanium_ingot`, `raw_tin`, `tin_ore`,
`deepslate_tin_ore`, `titanium_block`). Coils are blocks; every other product
is an item. Tags follow ADR-061 §2 (`forge:` tags, plus
`advancedrocketrycommunity:sheets/…`, `coils/…`, `fans/…`, `boules/…` and the
`advancedrocketrycommunity:coils` group that replaces the legacy `blockCoil`).
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

### 4. Ore placement (C15a)

A Forge biome modifier `advancedrocketrycommunity:overworld_ores` adds placed
features to `#minecraft:is_overworld` biomes: tin (10 veins of 6), rutile (6 of
6), aluminum (1 of 16), dilithium (1 of 16), uniform between y −16 and 64 with
deepslate variants below y 0. Iridium is not placed in the Overworld, as in the
legacy defaults. Data packs may override or remove the modifier, and a server
switch turns it off (ADR-061 §3.5). Moon and Mars placement arrives with their
terrain in C15b (§5).

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
- Features: craters as a carver (radius 8–48, at most one crater origin per
  chunk, Moon and Mars), volcanoes (Venus, cone radius ≤ 32 and height ≤ 48,
  at most one per 16×16 chunks), ore geodes (Venus, radius ≤ 12, ores from a
  tag), charred trees (Venus), vanilla cave and canyon carvers where the legacy
  planets had them, the Moon dilithium ore (10 veins per chunk) and the Moon
  and Mars iridium ore (§4).
- Every feature is bounded in size and count, runs only at chunk generation
  and has a server switch.
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
  Arrivals are unaffected: the Moon keeps its fixed y 80 rule over lower
  terrain, and Mars and Venus land on the heightmap (ADR-033). Players who
  want the new terrain everywhere start a new world or explore farther out.
  The release notes repeat these step heights and the backup advice of the
  celestial data guide.

### 6. Classic exoplanet worlds (C15c)

Two new bodies in the Tau Ceti system (ADR-043), each landable with a startup
Level like Mars and Venus (ADR-033) and discovery required (ADR-037):

| Body | Biomes | Environment |
|---|---|---|
| `tau_ceti_f` | `alien_forest`, `marsh`, `deep_swamp`, `ocean_spires` | breathable, 1.0 atm, 295 K |
| `tau_ceti_g` | `stormland`, `crystal_chasms` | not breathable, 1.4 atm, 255 K |

Flora and features: lightwood log, leaves, sapling and planks with the large
alien tree; electric mushrooms (light level 7; the client lightning flash is an
effect setting, never real lightning); swamp trees; inverted pillars; six
crystal block colours with large crystal clusters (moved here from C15b,
where no world uses them). Textures follow the asset plan; files the
derivation check excluded (the lightwood leaves and log top) are drawn new.
Bindings for the two new Levels are added at startup by the existing ADR-032
rules; Tau Ceti e is unchanged.

**Access path.** Both bodies are orbitable and landable, and both require
discovery. A player reaches them in four steps, each an existing mechanism:

1. **Discovery (ADR-037, ADR-043).** The data satellite's allowed targets gain
   `tau_ceti_f` and `tau_ceti_g` through a v1.8 copy of
   `satellite_definitions/data_satellite.json` (duration 200 ticks, yield 120,
   discovery cost 100, unchanged), which supersedes the v1.5 copy (§7). The
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

### 7. Persistence and migration

No saved-data schema changes. New IDs only (ADR-061 §1). New Levels bind on
first start. Existing discovery records stay valid; the two new bodies start
undiscovered in every world, old and new. Data packs that already use any new
ID in this namespace fail startup through the existing binding checks.

### 8. Tests

- A0: material table completeness against the ledger; recipe and tag JSON
  audit; DataGen determinism; feature bound checks; the asset records and the
  derivation check for every imported file.
- A0: the data satellite definition lists both new bodies; the three Tau Ceti
  routes stay inside one system; the recipe graph check passes with both Tau
  Ceti Levels removed from the reachable set.
- A1: plate press (block → plates; ore → dust; no obsidian; block entity
  below; unpowered; repeated pulses; unloaded neighbour), smelting and rolling
  recipes, ore feature placement in a test chunk (including iridium on the
  Moon and Mars and none in the Overworld), a crater carver bound test, a
  Moon height bound test (no generated block above y 63 over a sampled grid of
  at least 4,096 columns for two seeds, crater rims included), a rocket
  landing on the Moon over a crater and over the highest highlands (arrival at
  y 80 or above, as before), developer platform travel to the Moon over the
  highest highlands (open sky above the y 79 floor), each server switch, and
  the Tau Ceti
  path: a data-satellite discovery of `tau_ceti_f`, an interstellar warp to its
  orbit, a docked rocket's landing on its surface and return to the station.
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

## Consequences

- Classic materials, ores and surfaces exist before the machines that use them.
- The batch draws its own material, ore, turf and plate-press art.
- Explored areas of the Moon, Mars and Venus keep their old look until players
  reach new chunks.

## Revisit when

- the owner prefers a flat Moon, or different exoplanet bodies;
- a feature exceeds its chunk-generation budget.

## Review history

- Revision 1: proposed after C14 review round 1, for review round 2.
