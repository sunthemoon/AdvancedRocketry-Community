# V180 C15c — the Tau Ceti f and g worlds: verification

Date: 2026-10-03. Scope: slice C15c of ADR-062 §6 under
[ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md),
[ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) and
[ADR-063](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) §6, §8 and §9.
ADR-063 revision 3 is accepted; revisions 4 (C15a), 5 (C15b) and 6 (this slice) are
**proposed** and wait for the owner. Branch `codex/v1.8.0-classic-content`; base `7be9226`
(the C15b delivery); tested commit `e39fd9d`. The tested tree also holds the answers to the
C15b review round 1 ([dispositions](../v1.8.0-c15b-surfaces/review-01-dispositions.md)),
so this run is their re-run evidence as well. Development evidence only: **no Required
Gate, candidate or release decision**. No saved-data schema changes; Tau Ceti f and g are
new Levels.

## Commits

| Commit | Content |
|---|---|
| `f32ec0c` | ADR-063 revision 6 (proposed): the C15c decisions of §6 |
| `315ccd8` | Asset plan: the crystal, lightwood sapling, electric mushroom and lightwood log textures are drawn new instead of imported (ADR-061 §4.2) |
| `d0f9545` | A C15b defect found by a C15c GameTest: a geode under low ground opened to the surface; each column's roof now stays four blocks under its own ground |
| `fae751b` | ADR-063 revision 6: the landing ground, alternative D and corrections to match the implementation |
| `28d24b8` | The bodies, Levels, terrain, biomes, features, landing ground, blocks, client effect, switches and tests |
| `ba96cea`, `182986d`, `32defdb`, `ee4a379`, `6d2c263`, `cff78b1`, `147e3ec`, `192674d`, `fb61bd0`, `f4aeafc`, `7cd4419`, `d5a185c`, `f1444a3`, `e39fd9d` | C15b review round 1 (see its dispositions) |
| this commit | The evidence packet, the ledger delivery and the status documents |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `biome:alien_forest` | IMPLEMENTED | `advancedrocketrycommunity:alien_forest` (Tau Ceti f; grass over dirt, lightwood trees, dense grass) |
| `biome:marsh` | IMPLEMENTED | `advancedrocketrycommunity:marsh` (Tau Ceti f; shallows with clay and lily pads) |
| `biome:deepswamp` | IMPLEMENTED | `advancedrocketrycommunity:deep_swamp` (Tau Ceti f; giant swamp trees; nothing spawns) |
| `biome:oceanspires` | IMPLEMENTED | `advancedrocketrycommunity:ocean_spires` (Tau Ceti f; gravel sea floor, inverted pillars) |
| `biome:stormland` | IMPLEMENTED | `advancedrocketrycommunity:stormland` (Tau Ceti g; charred trees, electric mushrooms; nothing spawns) |
| `biome:crystalchasms` | IMPLEMENTED | `advancedrocketrycommunity:crystal_chasms` (Tau Ceti g; snow over packed ice, crystal clusters) |
| `block:alienWood` | IMPLEMENTED | `advancedrocketrycommunity:lightwood_log` |
| `block:alienLeaves` | IMPLEMENTED | `advancedrocketrycommunity:lightwood_leaves` (light 8; a sapling in 1 of 100 drops) |
| `block:alienSapling` | IMPLEMENTED | `advancedrocketrycommunity:lightwood_sapling` (grows the lightwood tree) |
| `block:planks` | IMPLEMENTED | `advancedrocketrycommunity:lightwood_planks` (light 4; four from a log) |
| `block:electricMushroom` | IMPLEMENTED | `advancedrocketrycommunity:electric_mushroom` |
| `block:crystal` | REDESIGNED | six crystal blocks, one per colour, instead of one block with six variants |
| `block_variant:crystal/0` | IMPLEMENTED | `advancedrocketrycommunity:violet_crystal_block` |
| `block_variant:crystal/1` | IMPLEMENTED | `advancedrocketrycommunity:blue_crystal_block` |
| `block_variant:crystal/2` | IMPLEMENTED | `advancedrocketrycommunity:green_crystal_block` |
| `block_variant:crystal/3` | IMPLEMENTED | `advancedrocketrycommunity:red_crystal_block` |
| `block_variant:crystal/4` | IMPLEMENTED | `advancedrocketrycommunity:yellow_crystal_block` |
| `block_variant:crystal/5` | IMPLEMENTED | `advancedrocketrycommunity:orange_crystal_block` |
| `config:CLIENT.electricPlantsSpawnLightning` | REDESIGNED | the CLIENT value `effects.electricMushroomFlashes`: a sky flash and distant thunder only, nothing struck |
| `worldgen:WorldGenAlienTree` | REDESIGNED | the `lightwood_tree` placed feature (branches shortened to stay within 12 blocks) |
| `worldgen:MapGenSwampTree` | REDESIGNED | the `giant_swamp_tree` placed feature |
| `worldgen:MapGenInvertedPillar` | REDESIGNED | the `inverted_pillar` placed feature |
| `worldgen:WorldGenLargeCrystal` | REDESIGNED | the `crystal_cluster` placed feature (lean capped) |
| `worldgen:MapGenLargeCrystal` | REDESIGNED | the `crystal_cluster` placed feature |
| `worldgen:WorldGenElectricMushroom` | IMPLEMENTED | the `electric_mushrooms` placed feature |

Twenty-five rows: 18 IMPLEMENTED, 7 REDESIGNED. The
`worldgen:WorldGenCharredTree` row (C15b) now names the stormland as well.

Registered by C15c (eleven blocks with their block items, named in both languages):
`advancedrocketrycommunity:lightwood_log`, `advancedrocketrycommunity:lightwood_leaves`, `advancedrocketrycommunity:lightwood_sapling`, `advancedrocketrycommunity:lightwood_planks`, `advancedrocketrycommunity:electric_mushroom`, `advancedrocketrycommunity:violet_crystal_block`, `advancedrocketrycommunity:blue_crystal_block`, `advancedrocketrycommunity:green_crystal_block`, `advancedrocketrycommunity:red_crystal_block`, `advancedrocketrycommunity:yellow_crystal_block`, `advancedrocketrycommunity:orange_crystal_block`.

Also, all in this mod's namespace: the bodies `tau_ceti_f` and `tau_ceti_g` with their
routes (`tau_ceti_f_surface_orbit`, `tau_ceti_g_surface_orbit`, `tau_ceti_f_g`), sky
profiles, dimension types and Levels; the six biomes; the features `lightwood_tree`,
`giant_swamp_tree`, `inverted_pillar` and `crystal_cluster`; seven placed features
(including `stormland_charred_tree`, `electric_mushrooms` and `alien_forest_grass`);
the noises `tau_ceti_f_terrain` and `tau_ceti_g_terrain`; the density function type
`landing_ground_floor` and the placement modifier type `landing_ground`; the COMMON
values `worldgen.lightwoodTrees`, `worldgen.swampTrees`, `worldgen.invertedPillars`,
`worldgen.crystalClusters` and `worldgen.electricMushrooms` (default on); the CLIENT
value `effects.electricMushroomFlashes` (default on); and the body names
`body.advancedrocketrycommunity.tau_ceti_f` and `_g`. The v1.8 data satellite
definition adds both bodies to the v1.5 targets and supersedes the v1.5 copy, which
`build.gradle` leaves out of the JARs.

## Contract tests (ADR-063 §9 for C15c)

| Contract item | Evidence |
|---|---|
| A0 bodies, routes, data satellite | `V180ExoplanetResourcesTest` (revision 6 numbers; three routes inside Tau Ceti; the v1.8 data satellite lists the v1.5 targets and both bodies), `PlanetaryDiscoveryTest`, `StarSystemGameTests` |
| A0 Levels, biomes, features | `V180ExoplanetResourcesTest`: low-relief routers, the four-band multi-noise source of f and the patch source of g, no spawns or carvers, each feature's switch and legacy numbers, the landing ground floor and filter |
| A0 feature shapes | `ExoplanetShapesTest`: each shape within 12 blocks for 2,000 seeds, legacy heights, living leaves, the crystal lean distribution |
| A0 landing ground | `LandingGroundTest`: the radius covers the farthest pad's widest footprint and a feature's reach; the floor's values; both codecs bounded, flat and refusing out-of-range values |
| A0 DataGen determinism and art | `runData`, then `git status -- src/generated` clean; `V180ExoplanetArtTest` re-encodes the seven textures and compares them with the committed bytes |
| A0 CLIENT config | `ClientConfigTest` |
| A1 terrain | `tauCetiFRisesFromTheSeaInFourBiomeBands`, `tauCetiGIsAPlateauOfTwoPatchedBiomes`, `bothWorldsRunWithTheirBiomesAndFeatures`, `newTauCetiFChunksWearTheirBiomesSurfaces`, `newTauCetiGChunksWearTheirBiomesSurfaces` |
| A1 feature bounds | `eachTauCetiFeatureWritesWithinTwelveBlocks` |
| A1 blocks | `aLightwoodSaplingGrowsTheTreeWhereItFitsAndStaysWhereNot`, `theTauCetiBlocksDropAndGlowAsTheContractSays` |
| A1 each server switch | `eachTauCetiFeatureFollowsItsServerSwitch`, `CommonConfigTest` (66 values), `SwitchOverridesTest` |
| A1 landing ground | `tauCetiFHasDryClearGroundUnderEachPad`, `tauCetiGHasDryClearGroundUnderEachPad` (within 32 blocks of each fixed pad: solid, nonfluid ground and nothing on it) |
| A1 the Tau Ceti path | `aDiscoveredTauCetiFIsReachedByWarpAndADockedRocketLandsAndReturns`: a data satellite discovers Tau Ceti f (a warp there is refused before), a station with a docked rocket warps there under the production rocket-motion rule, and the rocket lands on the first pad and returns to the station |
| Earlier tests that follow the new content | `celestialCommandsReachFixedSafeDestinations` (eleven bodies), `interstellarWarpCostsMoreWarnsAboutRoutesAndChangesTheContext` (Tau Ceti has routes; the routeless warning is checked on Cygnus X-1) |
| S1 packaged server | `native_c15c_check.py` (below) |

The recipe graph check with both Levels removed from the reachable set comes with the
C16d recipe graph tool (revision 6).

## Native check (S1)

A copy of the retained v1.6 world (`arce-v160-c9-…/attempt-04/native-server/world`) was opened by the v1.7 handoff build (main JAR `4326d3d6…`), which generated and saved a 10 × 10 chunk square (chunks −5..4) around the origin of the Moon, Mars and Venus. The v1.8 build of the tested commit then opened it on a packaged dedicated server (`native_c15c_check.py`, derived from the C15b harness; release-test hooks on, no clients; structures on in the copy). The upgrade phase force-loaded chunks −7..6 of each body, so the v1.8 build loaded every explored chunk (C15bR1-L6); it then sampled every chunk of −10..9 outside that square, which finishes the band the v1.7 build had saved part-way (C15bR1-M1), and warmed up before measuring the Overworld reference (L6). A first attempt (`attempt-01-ring-too-near/`, passed) sampled chunks −6 and 5 only, which the v1.7 build had fully generated next to its forced square, so it did not reach the band.

| Check | Result |
|---|---|
| Moon explored column (−40, −40) keeps its old top | `minecraft:end_stone` at y 3, biome `minecraft:plains` |
| Mars explored column (−40, −40) keeps its old top | `minecraft:red_sand` at y 70, biome `advancedrocketrycommunity:mars` |
| Venus explored column (−40, −40) keeps its old top | `minecraft:yellow_terracotta` at y 92, biome `advancedrocketrycommunity:venus` |
| Moon band beyond the explored square (each chunk of −10..9 outside −7..6, 204 columns) | top / biome: `advancedrocketrycommunity:moon_turf / minecraft:plains` × 76; `minecraft:end_stone / minecraft:plains` × 128 (never bare stone; new terrain on the stored `plains` biome wears turf) |
| Mars band beyond the explored square (each chunk of −10..9 outside −7..6, 204 columns) | top / biome: `advancedrocketrycommunity:ferric_sand / advancedrocketrycommunity:mars` × 76; `minecraft:red_sand / advancedrocketrycommunity:mars` × 128 (never bare stone) |
| Venus band beyond the explored square (each chunk of −10..9 outside −7..6, 204 columns) | top / biome: `minecraft:basalt / advancedrocketrycommunity:venus` × 76; `minecraft:yellow_terracotta / advancedrocketrycommunity:venus` × 128 (never bare stone) |
| Moon new chunks (9 × 9 around chunk 64, 64) | tops `advancedrocketrycommunity:dark_moon_turf`, `advancedrocketrycommunity:moon_turf`; biomes `advancedrocketrycommunity:regolith_highlands`, `advancedrocketrycommunity:regolith_lowlands`; highest surface y 27; starts {advancedrocketrycommunity:moon_crater=4} |
| Mars new chunks (9 × 9 around chunk 64, 64) | tops `advancedrocketrycommunity:ferric_sand`; biomes `advancedrocketrycommunity:ferric_regolith`; highest surface y 83; starts {advancedrocketrycommunity:mars_crater=4} |
| Venus new chunks (9 × 9 around chunk 64, 64) | tops `minecraft:basalt`; biomes `advancedrocketrycommunity:volcanic_lowlands`; highest surface y 150; starts {advancedrocketrycommunity:geode=1, advancedrocketrycommunity:volcano=1} |
| tau_ceti_f new chunks (9 × 9 around chunk 64, 64) | tops `advancedrocketrycommunity:lightwood_leaves`, `minecraft:brown_mushroom`, `minecraft:grass`, `minecraft:grass_block`, `minecraft:oak_leaves`, `minecraft:vine`, `minecraft:water`; biomes `advancedrocketrycommunity:alien_forest`, `advancedrocketrycommunity:deep_swamp`, `advancedrocketrycommunity:marsh`; highest surface y 98 |
| tau_ceti_f landing pads (the eight centres) | tops `minecraft:grass_block`; surfaces y 68–68 |
| tau_ceti_g new chunks (9 × 9 around chunk 64, 64) | tops `advancedrocketrycommunity:charcoal_log`, `advancedrocketrycommunity:electric_mushroom`, `minecraft:grass_block`, `minecraft:snow_block`; biomes `advancedrocketrycommunity:crystal_chasms`, `advancedrocketrycommunity:stormland`; highest surface y 121 |
| tau_ceti_g landing pads (the eight centres) | tops `minecraft:grass_block`, `minecraft:snow_block`; surfaces y 92–100 |
| Generation time (81 chunks each, synchronous) | overworld 56.4 ms/chunk; moon 10.1 ms/chunk; mars 11.8 ms/chunk; venus 19.3 ms/chunk; tau_ceti_f 18.1 ms/chunk; tau_ceti_g 20.7 ms/chunk (budget 1.5 × the Overworld reference) |
| Explored chunks after the upgrade | 300 chunks (10 × 10 per body, all loaded by the v1.8 build): every block section equal before and after |
| Logs | Both phases start and stop cleanly (exit 0); the log scan finds no unexpected error or warning; the source world is unchanged |

The phases' logs, commands, receipts and the summary are in `root-checks.zip` (`native/`).

## Generated art

The seven textures (lightwood log side and top, leaves, sapling and planks, the crystal
and the electric mushroom) are 16 × 16 palette grids in `V180ExoplanetArt`, written by
DataGen (`NEW`); four asset-plan rows that would have imported legacy textures became
`REGENERATE` (`315ccd8`). The crystal is drawn untinted; the block colour handler tints
it with the six legacy colours. The screen reports all 32 v1.8 textures `CLEAR`
against both vanilla clients (`python/art-screen.json` in `root-checks.zip`). The screen
is a measured check, not proof of originality; the ADR-061 §4.5 human visual review is
open.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, Python 3.13.

| Command | Result |
|---|---|
| `gradlew --offline clean build test runData runGameTestServer` on `e39fd9d` | Exit 0, 6m 50s. **384 required GameTests** passed (seed 0). `git status -- src/generated` after DataGen: clean |
| `gradlew --offline test --rerun` | Exit 0, 3m 6s. **1,441 JUnit tests / 271 suites**, 0 failures, 0 errors, 0 skips |
| JAR resource check (`jar-resource-check.txt`) | No duplicate entries; the v1.8 Moon, Mars, Venus and Tau Ceti dimension and noise-settings files, the v1.8 data satellite, the tool tags and the wood tags appear once each in the main and sources JARs, with the v1.8 bytes; no empty directories (`RESULT PASS`) |
| `python -B scripts/validate_v180_content_ledger.py` | Exit 0; "result": "PASS", |
| `… --require-accepted` | Exit 0; "result": "PASS", |
| `… --closure` (expected to fail until C19) | Exit 1; "result": "FAIL", |
| `python -B tools/audit/inventory_v180_content.py --upstream <Temp> --check` | Exit 0; inventory: up to date |
| `python -B tools/audit/vanilla_derivation.py --upstream <Temp> --vanilla … --check` | Exit 0; vanilla derivation: up to date |
| `python -B tools/audit/screen_generated_art.py --root src/generated/v1.8/resources --vanilla 1.12.2=… --vanilla 1.20.1=…` | Exit 0; {"CLEAR": 32} |
| `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation tests.test_screen_generated_art` | Exit 0; Ran 70 tests in 120.583s; OK |
| `python -B scripts/validate_v120_machine_resources.py` | Exit 0; PASS: 9 machine blocks, recipes, loot, models, tags and bilingual keys |
| `python -B scripts/validate_v090_resources.py` | Exit 0; [PASS] Beta resource audit: 1154 files, 325 JSON, 774 bilingual keys, 348 asset references |
| `python -B scripts/check_client_imports.py` | Exit 0; [PASS] No net.minecraft.client references outside the client package |

Final validators: after packaging, `validate_repository.py --require-approved-identity` exit 0 (45 passed, 0 failed); `validate_v1plus_planning.py` exit 0; `-m unittest tests.test_v1plus_planning` exit 0 (15 tests); `validate_bootstrap_provenance.py` exit 0; the ledger validator exit 0. They need the finished packet, so their log is
`packaging/out/validation-final.log` in the Temp evidence directory, outside `root-checks.zip`.

The three JARs (`artifacts.json`, with every entry in `artifact-entries.json`): main
`1d200fc6…`, API
`252463ff…` (unchanged since C15a), sources
`17f01b4c…`. The full build log has 17 ERROR
lines and 0 FATAL, from intentional failure-injection GameTests and the transfer recovery tests. This is not
a clean-log claim. The pre-commit GameTest logs (six C15c runs, three runs of a copy of the tree, two runs on the
C15b round 1 fixes) are in `prechecks/`.

## Fixed during the slice, before the implementation commit

- Two earlier GameTests pinned values that C15c changes: the celestial list now
  reports eleven bodies, and a warp quote to Tau Ceti no longer warns about missing
  rocket routes. Both were updated to the exact new values; the routeless warning is
  now checked on Cygnus X-1.
- A Tau Ceti feature test found a C15b defect: a geode under low ground opened to the
  surface (`d0f9545`, recorded in the C15b dispositions).
- The first Tau Ceti path run landed on the fifth fixed pad. A probe of the terrain
  noise over 1,000 seeds put all eight pads under the sea in 89, and the jungle grass,
  trees, mushrooms and crystals block most land pads: revision 6 adds the landing
  ground, and the path test now requires the first pad.
- The landing ground's codecs threw from the record constructor on an out-of-range
  value (an error with a partial result still builds the record), and the placement
  filter's codec was not a map codec, so the dispatch read it from a nested `value`
  field and refused the generated placed features at start-up. Both now validate as a
  codec result and stay flat; `LandingGroundTest` checks both.
- The body names that the satellite terminal and star map read were missing from the
  first language output; the resource test now requires them.

## Open

- One of six GameTest runs before the seed was pinned stalled the Tau Ceti path's
  transfer after the destination spawned, with no log line (`prechecks/arce-c15c-gt5.log` in
  `root-checks.zip`); five runs passed, and so did this run. If it recurs, the test now
  reports the journal phase, the journal state and whether the rocket's chunk ticks.
- The C15a press switch failure of `3e24ddf` stays open with the config-reload
  hypothesis (C15bR1-M4); the switch tests no longer write the config file.

## Not verified

- Any client: the new blocks' models and textures and their tints, the two skies, the
  electric mushroom sparks and the stormland flashes and thunder (V0/V1); the ADR-061
  §4.5 human visual review of the seven textures.
- A long exploration of either world: the S1 run generates 9 × 9 chunks per world; the
  GameTests sample their own areas.
- `/reload` with changed worldgen data (worldgen registries load at start only).
- The CI workflow on GitHub (this branch has no pull request).
- ADR-063 revisions 4, 5 and 6 and ADR-061 revision 7 are proposed, not accepted.

Next: the C15c implementation review and the C15b review round 2, then the owner's
decision on ADR-063 revisions 4, 5 and 6 and ADR-061 revision 7, then C16a in
[COMPLETION-PLAN](../../status/COMPLETION-PLAN.md).
