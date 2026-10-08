# CL18A-THERMITE TEST-DESIGN-01 draft (unreviewed, nothing run)

Levels per docs/17 section 2. Each case is separate; no mega-test. Expected
values come from CONTRACT-01 and stay review inputs until Root freezes them.

## A0 (JUnit, `src/test`)

| ID | Case | Fails when |
|---|---|---|
| T01 | data path set is exact: 2 blockstates, 2 block + 2 item models, 2 loot, 2 recipes, 2 unlocks, 1 tag, 2 PNGs | missing or extra path, wrong namespace |
| T02 | `thermite` recipe: shapeless, ingredients are exactly the two `MaterialTags` dust tags, result 1 | other type, serializer, count or ingredient |
| T03 | `thermite_torch` recipe: `minecraft:stick` + item `thermite` -> 4 | tag ingredient, count not 4 |
| T04 | unlocks: thermite OR(aluminum dust tag, iron dust tag); torch has `thermite`; each rewards its recipe | wrong criteria or reward |
| T05 | tag `forge:dusts/thermite` = [`thermite`], `replace` false | any other member |
| T06 | loot: both blocks drop one `thermite_torch`, `survives_explosion` | wall drops itself or 0/2 items |
| T07 | blockstates: standing one variant; wall four `facing` variants; models reference only the NEW textures | vanilla texture reference |
| T08 | art: 16x16 grids, valid symbols, RGBA decode, identical bytes on two runs | nondeterminism, bad size |
| T09 | en_us/zh_cn have item, torch and wall-torch keys in `advancedrocketrycommunity_v180`, no duplicate key | missing/duplicate key |
| T10 | torch classes: light 14; no BlockEntity, ticker, atmosphere or `net.minecraft.client` import | any present |

C16d recipe-graph checks must keep passing with thermite reachable from ores.

## A1 (GameTest, `<system>_<behavior>_<expected>`)

- G01 `thermite_torch_floor_place_emits_light_14`: item use on a top face
  gives the standing block; emission and block light at the cell are 14.
- G02 `thermite_torch_wall_place_keeps_facing`: each of four side faces gives
  the wall block with that facing; ceiling use places nothing, keeps the item.
- G03 `thermite_torch_unsupported_place_refused`: no support -> no block,
  stack count unchanged.
- G04 `thermite_torch_support_removed_drops_once`: for both variants, exactly
  one `thermite_torch` item entity, block becomes air.
- G05 `thermite_torch_survival_break_drops_one`; creative break drops none.
- G06 `thermite_torch_switch_off_still_lights`: with
  `lifeSupport.classicDevicesEnabled=false` (restored in `finally`) placement
  and light 14 unchanged.
- G07 `thermite_torch_vacuum_cell_stays_lit`: in a cell the existing
  atmosphere fixture reports as vacuum, light stays 14 after invalidation ticks.
  [A] reuse of an existing vacuum fixture is unchecked.
- G08 `thermite_recipe_manager_matches_exact`: crafting grid matches T02/T03;
  two aluminum dust, or dust + stick, matches nothing; press recipes
  `pressing_aluminum_dust` and `pressing_iron_dust` exist and yield 2.

## S1/S2 and native (Root executes)

- N01 packaged dedicated server: no client-class load; craft, place both
  variants, save, stop, restart; facings kept, light 14, breaks drop once.
- N02 world created before the leaf opens without errors or scans; placement
  works.
- N03 `/reload`: recipes, unlocks and tag remain; placed blocks unchanged.
- N04 `classic.smallPlatePress=false`: record the acquisition outcome Root
  chooses; no silent unobtainable item.

## V1 (real GPU; V0 does not substitute)

Moon surface: both variants visible, no missing texture, correct wall
orientation, particles, lighting; GUI/JEI names in both languages.

## Commands and evidence

Java 17 `./gradlew clean build`, `test`, `runData` twice with
`git diff --exit-code`, `runGameTestServer`, strict repository, provenance and
resource validators (ADR-066:908-911). Keep raw logs, exit codes, source and
JAR hashes and first failures. Never weaken assertions or extend timeouts.
