# V180 C15b — the Moon, Mars and Venus surfaces: verification

Date: 2026-10-03. Scope: slice C15b of ADR-062 §6 under
[ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md),
[ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) and
[ADR-063](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) §4 (the Moon
and Mars ores), §5, §8 and §9. ADR-063 revision 3 is accepted; revisions 4 (C15a) and 5
(this slice) are **proposed** and wait for the owner. Branch
`codex/v1.8.0-classic-content`; base `39fc16c` (the C14 acceptance); tested commit
`ef4bb85`. Development evidence only: **no Required Gate, candidate or release
decision**. No saved-data schema changes; the three planet Levels get a new generator
for chunks generated after the upgrade.

## Commits

| Commit | Content |
|---|---|
| `9a8b0e0` | ADR-063 revision 5 (proposed): the C15b decisions of §5 |
| `495d758` | Asset plan: the geode shell texture is drawn new instead of imported (ADR-061 §4.2) |
| `361c02b` | The v1.7 black-hole idle GameTest waits for the generator's first real evaluation before timing its re-check; the 21-tick bound is unchanged |
| `7d5d60c` | ADR-063 revision 5 additions: drops and tools, saved pieces |
| `3d4cae8` | Two v1.7 GameTests name what blocks an elevator arrival or lies near a laser marker in their failure messages; assertions unchanged |
| `d682e72` | The surfaces, biomes, structures, charred trees, Moon and Mars ores, switches, release-test hooks and tests |
| `0744fba` | The release-test hook `sample` loads the column it reports (found by the third S1 attempt) |
| `3e24ddf` | A GameTest checks that each body's generator tries its own structure sets (the S1 world had structures off, and no GameTest showed that worldgen tries the sets) |
| `2a74f90` | The v1.7 elevator anchor fixture lays a roof against gravel falling from the rock above the test space (named by the `3d4cae8` diagnostics); assertions unchanged |
| `ef4bb85` | The C15a press switch GameTest names what it saw when it fails; assertions unchanged |
| this commit | The evidence packet, the ledger delivery and the status documents |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `biome:moon` | IMPLEMENTED | `advancedrocketrycommunity:regolith_highlands` (moon turf over stone) |
| `biome:moondark` | IMPLEMENTED | `advancedrocketrycommunity:regolith_lowlands` (dark moon turf over stone) |
| `biome:hotdryrock` | IMPLEMENTED | `advancedrocketrycommunity:ferric_regolith` (Mars, ferric sand over red sandstone) |
| `biome:volcanic` | IMPLEMENTED | `advancedrocketrycommunity:volcanic` (Venus, basalt; charred trees) |
| `biome:volcanicbarren` | IMPLEMENTED | `advancedrocketrycommunity:volcanic_lowlands` (Venus, basalt) |
| `block:moonTurf` | IMPLEMENTED | `advancedrocketrycommunity:moon_turf` |
| `block:moonTurf_dark` | IMPLEMENTED | `advancedrocketrycommunity:dark_moon_turf` |
| `block:hotTurf` | IMPLEMENTED | `advancedrocketrycommunity:ferric_sand` |
| `block:charcoalLog` | IMPLEMENTED | `advancedrocketrycommunity:charcoal_log` (does not burn; drops one charcoal, itself with Silk Touch) |
| `block:geode` | IMPLEMENTED | `advancedrocketrycommunity:geode_shell` (iron pickaxe until the C18b jackhammer) |
| `block:basalt` | REDESIGNED | vanilla basalt in the Venus surface |
| `worldgen:WorldGenCharredTree` | IMPLEMENTED | the `charred_tree` feature in the volcanic biome |
| `worldgen:MapGenCrater` | REDESIGNED | crater structures `moon_crater` and `mars_crater` |
| `worldgen:MapGenCraterHuge` | REDESIGNED | crater structures (radius capped at 48) |
| `worldgen:MapGenCraterSmall` | REDESIGNED | crater structures (small radii, weighted as legacy) |
| `worldgen:MapGenVolcano` | REDESIGNED | the `volcano` structure (cone radius at most 32) |
| `worldgen:MapGenGeode` | REDESIGNED | the `geode` structure (radius 16–24) |
| `worldgen:MapGenCaveExt` | REDESIGNED | no carvers on the three bodies, as the legacy defaults; vanilla cave carvers stay available to data-pack planet biomes |
| `worldgen:MapGenHighCaves` | REDESIGNED | as `worldgen:MapGenCaveExt` |
| `worldgen:MapGenMassiveRavine` | REDESIGNED | as `worldgen:MapGenCaveExt`, with vanilla canyon carvers |
| `worldgen:MapGenRavineExt` | REDESIGNED | as `worldgen:MapGenMassiveRavine` |
| `worldgen:CustomizableOreGen` | REDESIGNED | configured ore features with the `planet_ores` server switch |
| `config_file:oreConfig.xml` | REDESIGNED | Moon and Mars ore placed features in data packs |
| `config:WORLDGEN.EnableOreGen` | REDESIGNED | the `overworld_ores` and `planet_ores` server switches |
| `config:WORLDGEN.generateCraters` | REDESIGNED | the `craters` switch and the crater structures |
| `config:WORLDGEN.generateVolcanos` | REDESIGNED | the `volcanoes` switch and the volcano structure |
| `config:WORLDGEN.generateGeodes` | REDESIGNED | the `geodes` switch and the geode structure |
| `config:WORLDGEN.geodeBaseSize` | REDESIGNED | the geode structure's radius 16–24 |
| `config:WORLDGEN.geodeVariation` | REDESIGNED | as `config:WORLDGEN.geodeBaseSize` |
| `config:WORLDGEN.geodeOres` | REDESIGNED | the `geode_ores` block tag (at most 32 kinds) |
| `config:WORLDGEN.geodeOres_blacklist` | REDESIGNED | data packs replace the `geode_ores` tag |
| `config:WORLDGEN.GenerateCopper` | REDESIGNED | Moon and Mars copper ore placed features |
| `config:WORLDGEN.CopperPerChunk` | REDESIGNED | as `config:WORLDGEN.GenerateCopper` (10 veins of 6) |
| `config:WORLDGEN.CopperPerClump` | REDESIGNED | as `config:WORLDGEN.GenerateCopper` |
| `config:WORLDGEN.GenerateTin` | REDESIGNED | Overworld, Moon and Mars tin ore placed features |
| `config:WORLDGEN.TinPerChunk` | REDESIGNED | as `config:WORLDGEN.GenerateTin` (Moon and Mars: 10 veins of 6) |
| `config:WORLDGEN.TinPerClump` | REDESIGNED | as `config:WORLDGEN.GenerateTin` |
| `config:WORLDGEN.GenerateRutile` | REDESIGNED | Overworld, Moon and Mars rutile ore placed features |
| `config:WORLDGEN.RutilePerChunk` | REDESIGNED | as `config:WORLDGEN.GenerateRutile` (Moon and Mars: 6 veins of 6) |
| `config:WORLDGEN.RutilePerClump` | REDESIGNED | as `config:WORLDGEN.GenerateRutile` |
| `config:WORLDGEN.generateAluminum` | REDESIGNED | Overworld, Moon and Mars aluminum ore placed features |
| `config:WORLDGEN.AluminumPerChunk` | REDESIGNED | as `config:WORLDGEN.generateAluminum` (Moon and Mars: 1 vein of 16) |
| `config:WORLDGEN.AluminumPerClump` | REDESIGNED | as `config:WORLDGEN.generateAluminum` |
| `config:WORLDGEN.generateIridium` | REDESIGNED | Moon and Mars iridium ore placed features (none in the Overworld) |
| `config:WORLDGEN.IridiumPerChunk` | REDESIGNED | as `config:WORLDGEN.generateIridium` (1 vein of 16) |
| `config:WORLDGEN.IridiumPerClump` | REDESIGNED | as `config:WORLDGEN.generateIridium` |
| `config:WORLDGEN.generateDilithium` | REDESIGNED | Overworld, Moon and Mars dilithium ore placed features |
| `config:WORLDGEN.DilithiumPerChunk` | REDESIGNED | as `config:WORLDGEN.generateDilithium` (Mars: 1 vein of 16) |
| `config:WORLDGEN.DilithiumPerChunkLuna` | REDESIGNED | the Moon dilithium ore placed feature (10 veins of 16, the airless count) |
| `config:WORLDGEN.DilithiumPerClump` | REDESIGNED | as `config:WORLDGEN.generateDilithium` |

Fifty rows: 11 IMPLEMENTED, 39 REDESIGNED. The Moon and Mars ores sit between
y 4 and 40; the Moon's target the vanilla stone replaceables, Mars red sandstone.

Registered by C15b (five blocks with their block items, named in both languages):
`advancedrocketrycommunity:moon_turf`, `advancedrocketrycommunity:dark_moon_turf`, `advancedrocketrycommunity:ferric_sand`, `advancedrocketrycommunity:charcoal_log`, `advancedrocketrycommunity:geode_shell`.

Also, all in this mod's namespace: the biomes `regolith_highlands`,
`regolith_lowlands`, `ferric_regolith`, `volcanic` and `volcanic_lowlands`; the
structure types and piece types `crater`, `volcano` and `geode`; the feature
`charred_tree`; the biome source `patches`; the noise `moon_terrain`; the noise
settings `moon`, `mars` and `venus`; the structures `moon_crater`, `mars_crater`,
`volcano` and `geode` with their sets; twelve configured and placed ore features
(`moon_*_ore`, `mars_*_ore`); the block tag `geode_ores`; four
`has_structure/*` biome tags; and the COMMON values `worldgen.planetOres`,
`worldgen.craters`, `worldgen.volcanoes`, `worldgen.geodes` and
`worldgen.charredTrees` (default on). The release-test commands
`/arce surface release-test generate|sample` exist only with the release-test hook
property.

The v1.8 dimension files for the Moon, Mars and Venus supersede the v0.3 Moon and
the v1.4 Mars and Venus copies, and the v1.8 Mars and Venus noise settings supersede
the v1.4 ones (their routers are copied unchanged; only the surface rules change).
`build.gradle` excludes the superseded copies from the main and sources JARs.

## Contract tests (ADR-063 §9 for C15b)

| Requirement | Test |
|---|---|
| A0 worldgen and tag JSON audit | `V180PlanetResourcesTest`: the Moon Level, router, noise and biome source; Mars and Venus keep the v1.4 router and change only surfaces and biomes; biomes spawn nothing and carve nothing; the Moon and Mars veins against the contract; structures and sets; blocks with loot, models, tools and names; the charcoal log and other drops |
| A0 structure and feature bounds | `SurfaceShapesTest`: craters within radius, rim and reach; crater ranges validated; volcanoes within radius 32 and height 48 with a contained lava pool; geodes within radius 24 with an open middle; the Venus patches use every biome and are stable |
| A0 DataGen determinism | `runData`, then `git status -- src/generated` clean; `V180SurfaceArtTest` re-encodes the six textures and compares them with the committed bytes |
| A0 asset derivation | No file is imported. The six drawn textures pass `tools/audit/screen_generated_art.py` with the 19 C15a textures (25 `CLEAR`) |
| A1 ore placement | `eachBodyCarriesItsOreSetWithIridium` (the Moon and Mars biomes carry their six veins each, iridium on both, Moon dilithium 10 of 16; Venus none), `eachPlanetVeinReplacesOnlyItsBodysRockNearItsOrigin` (each vein replaces only its body's rock, within 5 blocks), `planetVeinsStayInTheirChunkAndFollowTheServerSwitch` (each vein's count of positions inside the origin chunk at y 4–40 on the Moon and Mars; none with the switch off) |
| A1 structure bounds | `aMoonCraterWritesOnlyItsChunkWithinItsBounds`, `aVolcanoWritesOnlyItsChunkWithinRadius32AndHeight48`, `aGeodeWritesOnlyItsChunkWithinRadius24BelowTheSurface`: each piece is placed chunk by chunk through a recording level; every write lies inside the chunk being generated and the piece's box, and every block outside the piece is unchanged; `eachBodysGeneratorTriesItsOwnStructureSets` (worldgen tries each body's own structure sets and no other) |
| A1 feature bounds | `aCharredTreeStaysWithinItsReach` (twelve seeds: a trunk of six to eight logs, nothing beyond one block sideways) |
| A1 Moon height bound | `moonSurfaceStaysBetweenY12And36ForTwoSeeds` (a 64 × 64 grid, 4,096 columns, for the world seed and a second seed; both biomes on their own height bands); crater rims are capped at y 44 by the crater test and `SurfaceShapesTest` |
| A1 Moon landing | `aMoonRocketArrivesAtY80OverTheHighestHighlands`, `aMoonRocketArrivesAtY80OverACrater` |
| A1 developer platform | `theDeveloperPlatformStaysInOpenSkyOverTheHighestHighlands` |
| A1 each server switch | `planetVeinsStayInTheirChunkAndFollowTheServerSwitch` (`planet_ores`), `eachStructureSwitchStopsItsStarts` (`craters` on the Moon and Mars, `volcanoes`, `geodes`), `theCharredTreeFollowsItsServerSwitch` (`charred_trees`), `CommonConfigTest` (61 values) |
| New surfaces in new chunks | `newChunksUseTheNewSurfacesAndBiomes` (top, filler, rock and biome on the three bodies), the updated v1.4 `PlanetaryWorldGameTests` |
| Saved pieces (revision 5) | `savedPiecesLoadBackAndRefuseAnotherSchema` |
| Drops and tools (revision 5) | `theCharcoalLogDropsCharcoalAndTheGeodeShellNeedsAnIronPickaxe`, `theCharcoalLogDropsCharcoalAndTheOtherSurfaceBlocksThemselves` |
| S1 packaged server | `native_c15b_check.py` (below): crater starts on the Moon and Mars and a geode start on Venus in new chunks, generation within 1.5 × the Overworld's time per chunk, explored chunks unchanged |

The two Tau Ceti Levels of the S1 sentence come with C15c.

## Native check (S1)

A copy of the retained v1.6 world (`arce-v160-c9-…/attempt-04/native-server/world`) was opened by the
v1.7 handoff build (main JAR `4326d3d6…`, rebuilt from `55da6a5`, equal to the handoff hash), which
generated and saved a 6 × 6 chunk square around the origin of the Moon, Mars and Venus (the "explored"
chunks). The v1.8 build of the tested commit then opened the same world on a packaged dedicated server
(`native_c15b_check.py`, release-test hooks on, no clients). The copy's `level.dat` has structures turned
on (the source world has `generate_features` 0):

| Check | Result |
|---|---|
| Moon explored column (-40, -40) keeps its old top | `minecraft:end_stone` at y 3, biome `minecraft:plains` |
| Mars explored column (-40, -40) keeps its old top | `minecraft:red_sand` at y 70, biome `advancedrocketrycommunity:mars` |
| Venus explored column (-40, -40) keeps its old top | `minecraft:yellow_terracotta` at y 92, biome `advancedrocketrycommunity:venus` |
| Moon new chunks (9 × 9 around chunk 64, 64) | tops `advancedrocketrycommunity:dark_moon_turf`, `advancedrocketrycommunity:moon_turf`; biomes `advancedrocketrycommunity:regolith_highlands`, `advancedrocketrycommunity:regolith_lowlands`; highest surface y 26; starts {advancedrocketrycommunity:moon_crater=4} |
| Mars new chunks (9 × 9 around chunk 64, 64) | tops `advancedrocketrycommunity:ferric_sand`; biomes `advancedrocketrycommunity:ferric_regolith`; highest surface y 82; starts {advancedrocketrycommunity:mars_crater=4} |
| Venus new chunks (9 × 9 around chunk 64, 64) | tops `minecraft:basalt`; biomes `advancedrocketrycommunity:volcanic_lowlands`; highest surface y 149; starts {advancedrocketrycommunity:geode=1, advancedrocketrycommunity:volcano=1} |
| Generation time (81 chunks each, synchronous) | Overworld reference 63.8 ms/chunk; moon 13.3 ms/chunk; mars 15.3 ms/chunk; venus 14.9 ms/chunk (budget 1.5 × reference); slowest single chunk: overworld 1408 ms, moon 225 ms, mars 192 ms, venus 192 ms |
| Explored chunks after the upgrade | 108 chunks (6 × 6 per body): every block section equal before and after |
| Logs | Both phases start and stop cleanly (exit 0); the log scan finds no unexpected error or warning; the source world is unchanged |

Explore replies: mars: No blocks were filled; moon: No blocks were filled; venus: No blocks were filled.

The phases' logs, commands, receipts and the summary are in `root-checks.zip` (`native/`).

## Generated art

The six textures (moon turf, dark moon turf, ferric sand, the charcoal log side and
top, the geode shell) are 16 × 16 palette grids in `V180SurfaceArt`, written by
DataGen (`NEW`). The legacy turf and log textures were excluded as vanilla-derived
(ADR-061 §4.8), and the geode shell rule became `REGENERATE` (`495d758`). The screen
reports all 25 v1.8 textures `CLEAR` against both vanilla clients
(`python/art-screen.json` in `root-checks.zip`). The screen is a measured check, not
proof of originality; the ADR-061 §4.5 human visual review is open.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, Python 3.13.

| Command | Result |
|---|---|
| `gradlew --offline clean build test runData runGameTestServer` on `ef4bb85` | Exit 0, 5m 23s. **370 required GameTests** passed. `git status -- src/generated` after DataGen: clean |
| `gradlew --offline test --rerun` | Exit 0, 2m 9s. **1,419 JUnit tests / 265 suites**, 0 failures, errors or skips |
| JAR resource check (`jar-resource-check.txt`) | No duplicate entries; the v1.8 Moon, Mars and Venus dimension and noise-settings files, `needs_iron_tool`, `needs_stone_tool` and `mineable/pickaxe` appear once each in the main and sources JARs, with the v1.8 bytes; no empty directories (`RESULT PASS`) |
| `python -B scripts/validate_v180_content_ledger.py` | Exit 0; `"result": "PASS"` |
| `… --require-accepted` | Exit 0; `"result": "PASS"` |
| `… --closure` (expected to fail until C19) | Exit 1; `"result": "FAIL"` |
| `python -B tools/audit/inventory_v180_content.py --upstream <Temp> --check` | Exit 0; `inventory: up to date` |
| `python -B tools/audit/vanilla_derivation.py --upstream <Temp> --vanilla … --check` | Exit 0; `vanilla derivation: up to date` |
| `python -B tools/audit/screen_generated_art.py --root src/generated/v1.8/resources --vanilla 1.12.2=… --vanilla 1.20.1=…` | Exit 0; `{"CLEAR": 25}` |
| `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation tests.test_screen_generated_art` | Exit 0; `Ran 70 tests in 114.294s; OK` |
| `python -B scripts/validate_v120_machine_resources.py` | Exit 0; `PASS: 9 machine blocks, recipes, loot, models, tags and bilingual keys` |
| `python -B scripts/validate_v090_resources.py` | Exit 0; `[PASS] Beta resource audit: 1056 files, 289 JSON, 755 bilingual keys, 310 asset references` |
| `python -B scripts/check_client_imports.py` | Exit 0; `[PASS] No net.minecraft.client references outside the client package` |

Final validators: after packaging, `validate_repository.py --require-approved-identity` exit 0 (45 passed, 0 failed); `validate_v1plus_planning.py` exit 0; `-m unittest tests.test_v1plus_planning` exit 0 (15 tests); `validate_bootstrap_provenance.py` exit 0; the ledger validator exit 0. They need the finished packet, so their log is `packaging/out/validation-final.log` in the Temp evidence directory, outside `root-checks.zip`.

The three JARs (`artifacts.json`, with every entry in `artifact-entries.json`): main
`90b9ca23…`, API `252463ff…`, sources `c13d8d37…`. The full build log has 18 ERROR lines and
0 FATAL, from intentional failure-injection GameTests and the missing
`server.properties`. This is not a clean-log claim.

## Fixed during the slice, before the first commit

- The Moon surface was one block low: the density is zero at S, so the top block lies at
  S − 1; S is now 21 + 12n + 4|n|.
- The volcano's skirt wrote below its box on sloped ground; the box now reaches the skirt
  depth and every write of every piece is clamped to its own box and the chunk.
- The geode's half height truncated towards zero and reached past the radius; it uses
  floor division.
- GameTests read the worldgen heightmap, which is stale on loaded chunks; they read
  `WORLD_SURFACE`.
- Moon tests shared the fixed pad area; `MoonPadArea` gives it a known shape first.
- The v1.3 atmosphere rooms on the Moon were buried by the new terrain; they moved to
  y 64.
- Heavy structure tests loaded chunks synchronously and stalled the tests running beside
  them; they now load through region tickets and wait.
- The surface test's rock check assumed a fixed filler depth; it now walks down the
  filler, which the vanilla surface depth and a crater rim can make up to about 18 blocks
  deep.
- A v1.7 GameTest timed the black-hole generator's idle re-check from a status that a new
  generator reads before it ever ticks (`361c02b`).
- GameTests stand in natural terrain below the surface (the GameTest Overworld is a
  normal world, seed 0; tests start at y -60), and which earlier test or rock surrounds
  a test depends on the layout of the batches, which C15b's new batches shift. Two v1.7
  GameTests failed once each in pre-commit runs and passed in the next: an elevator ride
  down cancelled as `ARRIVAL_OBSTRUCTED` (the rides test, then the zone test) and two
  items near a laser marker. `3d4cae8` made both failure messages name what was there.
  The Gradle evidence run on `3e24ddf` (`failed-run-3e24ddf/`) then failed three tests:
  the elevator zone test, whose message named gravel filling the arrival platform;
  `theServerSwitchTurnsThePressOff` (C15a), whose block was gone five ticks after the
  rising edge of a disabled press, with no plates saved near it (cause unknown); and the
  v1.5 sky-context test, which counted the zone test's rider, left online because the
  zone test failed before its clean-up. `2a74f90` roofs the elevator anchor fixture;
  `ef4bb85` makes the press test name what it saw. The pre-commit GameTest logs are in
  `prechecks/`; the laser test did not fail again.
- The S1 harness needed six attempts before the final run, each kept in `root-checks.zip`:
  1. `attempt-01-failed`: the console `fill` that explores the planets needs loaded
     chunks; the harness now force-loads the square, retries the fill and releases it.
  2. `attempt-02-failed`: the release reply reads "for force loading"; the v1.6 world
     also holds blocks of the adapter compat fixture mod, which both phases now install,
     as the v1.7 harness did.
  3. `attempt-03-failed`: a defect in the release-test hook `sample`: it read an unloaded
     column's height as the minimum build height (void air at y -1) and its biome from
     the biome source. `0744fba` loads the column first.
  4. `attempt-04-failed`: each hook line is logged and also echoed as console feedback, so
     a query matched the previous body's line; queries now match the dimension and
     position.
  5. `attempt-05-failed`: one explored Moon chunk was reported changed by a comparison of
     raw section data (palette and packed longs, light-only sections included); its
     content was the same flat Moon. The harness now compares decoded block states and
     writes any difference with its position (`native/explored-differences.json`).
  6. `attempt-06-structures-off`: passed, but no body showed a structure start: the
     retained v1.6 world was created with structures off (`generate_features` 0), so
     vanilla never tried a structure set. The harness now turns structures on in the
     copied `level.dat` (the source world stays unchanged and is verified) and requires
     crater starts on the Moon and Mars and a geode start on Venus; `3e24ddf` adds the
     GameTest that each body's generator tries its own sets.
- The Gradle evidence runs on `d682e72` and `0744fba` (369 GameTests and 1,419 JUnit tests
  each, all passing) are kept as `superseded-run-d682e72/` and `superseded-run-0744fba/`;
  this packet's run, and S1's seventh attempt, are on the commit named above.

## Not verified

- Any client: the new blocks' models and textures, the biome colours and fog, and the
  Moon's black sky (V0/V1); the ADR-061 §4.5 human visual review of the six textures.
- A long exploration: the S1 run generates 9 × 9 chunks per body; the GameTests sample
  4,096 Moon columns for two seeds.
- `/reload` with changed worldgen data (worldgen registries load at start only).
- The CI workflow on GitHub (this branch has no pull request).
- ADR-063 revisions 4 and 5 and ADR-061 revision 7 are proposed, not accepted.

Next: the C15b implementation review, then the owner's decision on ADR-063 revisions
4 and 5 and ADR-061 revision 7, then C15c in
[COMPLETION-PLAN](../../status/COMPLETION-PLAN.md).
