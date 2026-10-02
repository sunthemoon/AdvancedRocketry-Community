# V180 C15a — materials, ores and the small plate press: verification

Date: 2026-10-03. Scope: slice C15a of ADR-062 §6 under
[ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md),
[ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) and
[ADR-063](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) §1–§4 and §8
(revision 3 accepted; revision 4, from this slice, is **proposed** and waits for the
implementation review and the owner's acceptance). Branch
`codex/v1.8.0-classic-content`; base `39fc16c` (the C14 acceptance); tested commit
`baa20f9`. Development evidence only: **no Required Gate, candidate or release
decision**, and no world migration (no saved-data schema changes).

## Commits

| Commit | Content |
|---|---|
| `d666aff` | Ledger: the thirteen `config:WORLDGEN.*` rows and `worldgen:CustomizableOreGen` move to C15b, which closes their Moon and Mars half (ADR-062 §7) |
| `920308e` | The v1.2 rolling capability GameTest stores diamonds instead of gold ingots, which C15a now rolls; its assertions are unchanged |
| `f2006e8` | ADR-063 revision 4 (proposed): iron plates from the press, item ingredients for rolling, press tags resolved when it acts, ambiguity refused, the two switches |
| `cb52c0b` | The material set, ores, recipes, the small plate press, Overworld ore features, v1.8 DataGen, runtime `1.20.1-1.8.0-dev` |
| `baa20f9` | Tests for ambiguous press matches and the switch codec, found missing by a mutation review |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:platepress` | IMPLEMENTED | `advancedrocketrycommunity:small_plate_press` |
| `config_file:SmallPlatePress.xml` | REDESIGNED | the `small_plate_press` recipe type with data pack recipes |
| `integration:jei/platePresser` | IMPLEMENTED | JEI small plate press category |
| `material:Titanium` | IMPLEMENTED | `advancedrocketrycommunity:titanium_ingot` and the titanium set |
| `material:Rutile` | IMPLEMENTED | `advancedrocketrycommunity:rutile_ore`, with the deepslate ore and raw rutile |
| `material:Aluminum` | IMPLEMENTED | `advancedrocketrycommunity:aluminum_ingot` and the aluminum set |
| `material:Tin` | IMPLEMENTED | `advancedrocketrycommunity:tin_ingot` and the tin set |
| `material:Steel` | IMPLEMENTED | `advancedrocketrycommunity:steel_ingot` and the steel set |
| `material:Iridium` | IMPLEMENTED | `advancedrocketrycommunity:iridium_ingot` and the iridium set (placed on the Moon and Mars in C15b) |
| `material:Dilithium` | IMPLEMENTED | `advancedrocketrycommunity:dilithium_crystal` and the dilithium set |
| `material:Silicon` | IMPLEMENTED | `advancedrocketrycommunity:silicon_ingot` and the silicon set |
| `material:TitaniumAluminide` | IMPLEMENTED | `advancedrocketrycommunity:titanium_aluminide_ingot` and its set |
| `material:TitaniumIridium` | IMPLEMENTED | `advancedrocketrycommunity:titanium_iridium_ingot` and its set |
| `material:Copper` | IMPLEMENTED | `advancedrocketrycommunity:copper_plate` and the copper products |
| `material:Iron` | IMPLEMENTED | `advancedrocketrycommunity:iron_plate` and the iron products |
| `material:Gold` | IMPLEMENTED | `advancedrocketrycommunity:gold_plate` and the gold products |
| `worldgen:OreGenerator` | REDESIGNED | Overworld ore placed features and the `overworld_ores` biome modifier |

The alloys and steel have their products but cannot be made yet: the electric arc
furnace comes in C16b, as ADR-063 §1 states. The silicon boule and the dilithium
crystal likewise wait for C16c.

Registered by C15a (87 blocks and items, named in both languages):
`advancedrocketrycommunity:aluminum_block`, `advancedrocketrycommunity:aluminum_coil`, `advancedrocketrycommunity:aluminum_dust`, `advancedrocketrycommunity:aluminum_ingot`, `advancedrocketrycommunity:aluminum_nugget`, `advancedrocketrycommunity:aluminum_ore`, `advancedrocketrycommunity:aluminum_plate`, `advancedrocketrycommunity:aluminum_sheet`, `advancedrocketrycommunity:copper_coil`, `advancedrocketrycommunity:copper_dust`, `advancedrocketrycommunity:copper_nugget`, `advancedrocketrycommunity:copper_plate`, `advancedrocketrycommunity:copper_rod`, `advancedrocketrycommunity:copper_sheet`, `advancedrocketrycommunity:deepslate_aluminum_ore`, `advancedrocketrycommunity:deepslate_dilithium_ore`, `advancedrocketrycommunity:deepslate_rutile_ore`, `advancedrocketrycommunity:deepslate_tin_ore`, `advancedrocketrycommunity:dilithium_crystal`, `advancedrocketrycommunity:dilithium_dust`, `advancedrocketrycommunity:dilithium_ore`, `advancedrocketrycommunity:gold_coil`, `advancedrocketrycommunity:gold_dust`, `advancedrocketrycommunity:gold_plate`, `advancedrocketrycommunity:iridium_block`, `advancedrocketrycommunity:iridium_coil`, `advancedrocketrycommunity:iridium_dust`, `advancedrocketrycommunity:iridium_ingot`, `advancedrocketrycommunity:iridium_nugget`, `advancedrocketrycommunity:iridium_ore`, `advancedrocketrycommunity:iridium_plate`, `advancedrocketrycommunity:iridium_rod`, `advancedrocketrycommunity:iron_dust`, `advancedrocketrycommunity:iron_plate`, `advancedrocketrycommunity:iron_rod`, `advancedrocketrycommunity:iron_sheet`, `advancedrocketrycommunity:raw_aluminum`, `advancedrocketrycommunity:raw_iridium`, `advancedrocketrycommunity:raw_rutile`, `advancedrocketrycommunity:raw_tin`, `advancedrocketrycommunity:rutile_ore`, `advancedrocketrycommunity:silicon_boule`, `advancedrocketrycommunity:silicon_dust`, `advancedrocketrycommunity:silicon_ingot`, `advancedrocketrycommunity:silicon_nugget`, `advancedrocketrycommunity:silicon_plate`, `advancedrocketrycommunity:small_plate_press`, `advancedrocketrycommunity:steel_block`, `advancedrocketrycommunity:steel_dust`, `advancedrocketrycommunity:steel_fan`, `advancedrocketrycommunity:steel_gear`, `advancedrocketrycommunity:steel_ingot`, `advancedrocketrycommunity:steel_nugget`, `advancedrocketrycommunity:steel_plate`, `advancedrocketrycommunity:steel_rod`, `advancedrocketrycommunity:steel_sheet`, `advancedrocketrycommunity:tin_block`, `advancedrocketrycommunity:tin_dust`, `advancedrocketrycommunity:tin_ingot`, `advancedrocketrycommunity:tin_nugget`, `advancedrocketrycommunity:tin_ore`, `advancedrocketrycommunity:tin_plate`, `advancedrocketrycommunity:titanium_aluminide_block`, `advancedrocketrycommunity:titanium_aluminide_dust`, `advancedrocketrycommunity:titanium_aluminide_gear`, `advancedrocketrycommunity:titanium_aluminide_ingot`, `advancedrocketrycommunity:titanium_aluminide_nugget`, `advancedrocketrycommunity:titanium_aluminide_plate`, `advancedrocketrycommunity:titanium_aluminide_rod`, `advancedrocketrycommunity:titanium_aluminide_sheet`, `advancedrocketrycommunity:titanium_block`, `advancedrocketrycommunity:titanium_coil`, `advancedrocketrycommunity:titanium_dust`, `advancedrocketrycommunity:titanium_gear`, `advancedrocketrycommunity:titanium_ingot`, `advancedrocketrycommunity:titanium_iridium_block`, `advancedrocketrycommunity:titanium_iridium_dust`, `advancedrocketrycommunity:titanium_iridium_gear`, `advancedrocketrycommunity:titanium_iridium_ingot`, `advancedrocketrycommunity:titanium_iridium_nugget`, `advancedrocketrycommunity:titanium_iridium_plate`, `advancedrocketrycommunity:titanium_iridium_rod`, `advancedrocketrycommunity:titanium_iridium_sheet`, `advancedrocketrycommunity:titanium_nugget`, `advancedrocketrycommunity:titanium_plate`, `advancedrocketrycommunity:titanium_rod`, `advancedrocketrycommunity:titanium_sheet`.

Also: the recipe type and serializer `small_plate_press`, the placement modifier
type `server_switch`, the creative tab `materials`, the configured and placed
features `overworld_tin_ore`, `overworld_rutile_ore`, `overworld_aluminum_ore` and
`overworld_dilithium_ore`, the biome modifier `overworld_ores` (all in this mod's
namespace), and the COMMON values `classic.smallPlatePress` and
`worldgen.overworldOres` (both default on).

## Contract tests (ADR-063 §9)

| Requirement | Test |
|---|---|
| A0 material table against the contract and the ledger | `MaterialCatalogTest` (the §1 table, 86 entries, 21 blocks, five coils); the ledger validator |
| A0 recipe and tag JSON audit | `V180MaterialResourcesTest` (rolling and press recipe sets and fields, no rutile smelting, tags and umbrellas, both rutile ore tags, the coils group, both languages, models, block states and loot tables, the tool tags as complete copies) |
| A0 DataGen determinism | `runData`, then `git status -- src/generated` clean; `V180MaterialArtTest` re-encodes every texture and compares it with the committed bytes |
| A0 feature bounds | `overworldVeinsReplaceStoneAndDeepslateNearTheirOrigin` (every vein write within 5 blocks of its origin, replacing only stone or deepslate with the matching variant); `overworldPlacementsFollowTheServerSwitch` (positions inside the origin chunk, y −16..64) |
| A0 asset records and derivation | No file is imported in C15a. The 19 drawn textures pass `tools/audit/screen_generated_art.py` against both vanilla clients (also a CI step) |
| A0 JEI plate press category | `SmallPlatePressViewTest`; the plugin registers the category, its catalyst and every `small_plate_press` recipe (no client run, see below) |
| A1 press: block → plates, ore → dust | `aPulsePressesAStorageBlockIntoFourPlates` (titanium, vanilla iron and copper blocks), `aPulsePressesAnOreIntoTwoDust` (tin, deepslate rutile → titanium dust, vanilla iron ore, dilithium) |
| A1 press: no obsidian, block entity, unbreakable | `withoutObsidianOrWithABlockEntityNothingIsPressed` |
| A1 press: unpowered, repeated pulses | `onlyARisingEdgePresses` (nothing while unpowered or while the signal is held; one operation per rising edge) |
| A1 press: unloaded neighbour | `anUnloadedTargetIsNeitherPressedNorLoaded` (`UNLOADED`; the chunk stays unloaded) |
| A1 press: cancelled `PistonEvent.Pre` | `aCancelledPistonEventLeavesTheBlock` |
| Server switches (ADR-061 §3.5) | `theServerSwitchTurnsThePressOff`, `overworldPlacementsFollowTheServerSwitch`, `SwitchPlacementTest` |
| Ambiguity | `everyPressInputMatchesOneRecipe`, `everyRollingInputMatchesOneRecipe` (every input at a full stack matches exactly one recipe), `SmallPlatePressRecipeTest` |
| A1 smelting and rolling | `oresRawItemsAndDustSmeltIntoIngots` (rutile never), `everyRollingInputMatchesOneRecipe` (300 ticks, 20 and 200 FE/t, 100 mB; no iron ingot → plate), `legacyCraftingShapesMakeTheProducts` |
| A1 Overworld ores without iridium | `overworldBiomesCarryTheOresWithoutIridium` (plains, desert and deep dark carry the four features; no Overworld feature places iridium; the Nether gets none) |

The Moon and Mars ore sets, Moon dilithium and the structure bounds belong to C15b.

## Generated art

The 19 textures are 16 × 16 palette grids in `V180MaterialArt`, written by DataGen
(`NEW`); the ore models reference vanilla stone and deepslate by resource location.
Two screens before the commit changed the art:

1. Against the 1.20.1 client, the silicon boule was `SUSPECT` against
   `item/tadpole_bucket.png` (overlap 0.12 over three colours): the even 24-step grey
   scale shares 120, 168 and 216 with the vanilla bucket. The palette moved to greys
   that few vanilla textures use (20, 44, 72, 93, 117, 141, 169, 191, 210, 238).
2. Against both clients, the press top and bottom (concentric bevels) were `SUSPECT`
   against 1.12.2 `blocks/itemframe_background.png` and `particle/footprint.png` by
   rank correlation (0.76 and 0.84 over fully opaque masks). They were redrawn as a
   bolted ram cap and a stud grid (rank 0.50 and 0.70).

Final screen: 19 `CLEAR` against both clients (`python/art-screen.json` in
`root-checks.zip`). The screen is a measured check, not proof of originality; the art
is drawn in this repository and reviewable as text grids.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, Python 3.13.

| Command | Result |
|---|---|
| `gradlew --offline clean build test runData runGameTestServer` on `baa20f9` | Exit 0, 4 min 53 s. **351 required GameTests** passed. `git status -- src/generated` after DataGen: clean |
| `gradlew --offline test --rerun` | Exit 0, 2 min 7 s. **1,393 JUnit tests / 260 suites**, 0 failures, errors or skips |
| JAR resource check (`jar-resource-check.txt`) | No duplicate entries; `needs_iron_tool`, `mineable/pickaxe` and `needs_stone_tool` appear once each, as the v1.8 copies |
| `python -B scripts/validate_v180_content_ledger.py` | Exit 0; `"result": "PASS"` |
| `… --require-accepted` | Exit 0; `"result": "PASS"` |
| `… --closure` (expected to fail until C19) | Exit 1; `"result": "FAIL"` |
| `python -B tools/audit/inventory_v180_content.py --upstream <Temp> --check` | Exit 0; `inventory: up to date` |
| `python -B tools/audit/vanilla_derivation.py --upstream <Temp> --vanilla … --check` | Exit 0; `vanilla derivation: up to date` |
| `python -B tools/audit/screen_generated_art.py --root src/generated/v1.8/resources --vanilla 1.12.2=… --vanilla 1.20.1=…` | Exit 0; `{"CLEAR": 19}` |
| `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation tests.test_screen_generated_art` | Exit 1; `Ran 70 tests in 101.785s; FAILED (failures=4)` |
| The same, after `d8f80ed` | Exit 0; `Ran 70 tests in 97.528s; OK` |
| `python -B scripts/validate_v120_machine_resources.py` | Exit 0; `PASS: 9 machine blocks, recipes, loot, models, tags and bilingual keys` |
| `python -B scripts/validate_v090_resources.py` | Exit 0; `[PASS] Beta resource audit: 974 files, 273 JSON, 745 bilingual keys, 290 asset references` |
| `python -B scripts/check_client_imports.py` | Exit 0; `[PASS] No net.minecraft.client references outside the client package` |

Final validators: after packaging, `validate_repository.py --require-approved-identity` exit 0 (45 passed, 0 failed); `validate_v1plus_planning.py` exit 0; `-m unittest tests.test_v1plus_planning` exit 0 (15 tests); `validate_bootstrap_provenance.py` exit 0; the ledger validator exit 0. They need the finished packet, so their log is `packaging/out/validation-final.log` in the Temp evidence directory, outside `root-checks.zip`.

The three JARs (`artifacts.json`): main `6e9238b8…`, API `252463ff…`, sources
`9d715998…`. The full build log has 18 ERROR lines and 0 FATAL, from intentional
failure-injection GameTests and the missing `server.properties`, as in C14. This is
not a clean-log claim.

## Fixed during the slice, before any commit

- Machine recipes resolve their ingredients when recipes load, before tags are bound,
  so a tag ingredient would have failed on a fresh start: rolling recipes name items,
  and the press bounds its JSON at load and resolves tags when it acts.
- An iron ingot → plate rolling recipe would have made itself and `rolling_iron_bars`
  ambiguous; iron plates come from the press (ADR-063 revision 4).
- The coils group is also the coil umbrella tag, so each coil was listed twice.
- The first GameTest run failed two tests: the v1.2 rolling capability test (its gold
  ingots now roll; `920308e`) and the new rising-edge test (the first plates were
  pushed out of the block placed on them). The log of that run was overwritten by the
  rerun; its two failure lines are quoted in the implementation log.

## Fixed after the run

The first Python check run failed four ledger-validator unit tests
(`python/py-07-unittest.log`, kept): the tests copy a fixture tree that held only the
C14 preparation folder, and the C15a rows now name this folder as their evidence.
`d8f80ed` copies every v1.8 batch evidence folder into the fixture; the rerun passes
70 tests (`python/py-07b-unittest.log`). The change is listed in
`post-run-changes.json`; it is not part of the Gradle run.

## Not verified

- Any client: tinted models, the creative tab, the JEI category on a real client
  (V0/V1), and startup with JEI installed. The GameTest server starts without JEI.
- Ore distribution in a newly generated world beyond the GameTests.
- A dedicated-server restart: C15a adds no saved data; the press keeps only its
  `powered` block state.
- The CI workflow on GitHub (this branch has no pull request).
- ADR-063 revision 4 is proposed, not accepted.

Next: the independent C15a implementation review, the owner's decision on ADR-063
revision 4, then C15b in [COMPLETION-PLAN](../../status/COMPLETION-PLAN.md).
