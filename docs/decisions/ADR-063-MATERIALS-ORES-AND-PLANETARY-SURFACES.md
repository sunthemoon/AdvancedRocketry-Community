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
  Moon, iridium off;
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
| iridium | ingot, nugget, dust, plate, rod, block | `iridium_ore`, `raw_iridium` (no Overworld placement) |
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
deepslate variants below y 0. Iridium is not placed in the Overworld; asteroid
and laser drill tables may list it (their table versions change only by data
revision under ADR-052 and ADR-055). Data packs may override or remove the
modifier, and a server switch turns it off (ADR-061 §3.5). Moon placement
arrives with the Moon terrain in C15b.

### 5. Moon, Mars and Venus (C15b)

- Blocks: `moon_turf`, `dark_moon_turf`, `ferric_sand`, `charcoal_log`,
  `geode_shell`, with textures drawn new.
- Biomes: `regolith_highlands`, `regolith_lowlands` (Moon), `ferric_regolith`
  (Mars), `volcanic`, `volcanic_lowlands` (Venus), with the legacy top and
  filler blocks over vanilla stone (Moon, Mars) or vanilla basalt (Venus).
- The Moon becomes a noise Level with gentle relief (the legacy base height and
  variation) and a two-biome source; Mars and Venus switch to multi-biome
  sources with the new biomes.
- Features: craters as a carver (radius 8–48, at most one crater origin per
  chunk, Moon and Mars), volcanoes (Venus, cone radius ≤ 32 and height ≤ 48,
  at most one per 16×16 chunks), ore geodes (Venus, radius ≤ 12, ores from a
  tag), charred trees (Venus), vanilla cave and canyon carvers where the legacy
  planets had them, and the Moon dilithium ore (10 veins per chunk).
- Every feature is bounded in size and count, runs only at chunk generation
  and has a server switch.
- **Seams (ADR-033, ADR-061 §6).** The three Levels keep their IDs. Chunks
  generated before the upgrade keep their old surface (end stone on the Moon,
  red sand on Mars, yellow terracotta on Venus); chunks generated afterwards
  use the new terrain, so explored areas meet new terrain at a visible step
  and a change of block. Rocket landing uses the surface height (ADR-033), so
  arrivals are unaffected; players who want the new terrain everywhere start a
  new world or explore farther out. The release notes repeat this and the
  backup advice of the celestial data guide.

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

### 7. Persistence and migration

No saved-data schema changes. New IDs only (ADR-061 §1). New Levels bind on
first start. Data packs that already use any new ID in this namespace fail
startup through the existing binding checks.

### 8. Tests

- A0: material table completeness against the ledger; recipe and tag JSON
  audit; DataGen determinism; feature bound checks; the asset records and the
  derivation check for every imported file.
- A1: plate press (block → plates; ore → dust; no obsidian; block entity
  below; unpowered; repeated pulses; unloaded neighbour), smelting and rolling
  recipes, ore feature placement in a test chunk, a crater carver bound test,
  rocket landing on the new Moon terrain, each server switch.
- S1: a packaged dedicated server generates chunks in the Moon, Mars, Venus
  and both new Levels without errors, within the tick budget of a reference
  chunk-generation run, and an upgraded v1.7 world keeps its explored chunks.

## Alternatives

### A. Keep the flat Moon

Avoids seams but drops the classic regolith terrain and craters; the landing
rule already handles relief.

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
