# C15b independent implementation review, round 1 (C15bR1)

- **Reviewed commit:** `7be92263` on `codex/v1.8.0-classic-content` (v1.8 base `39fc16c`; C15a accepted at `0a99d17`). C15b commits: `9a8b0e0`, `495d758`, `361c02b`, `7d5d60c`, `3d4cae8`, `d682e72`, `0744fba`, `3e24ddf`, `2a74f90`, `ef4bb85`, `7be9226`. The evidence packet tested `ef4bb85`. `7be9226` changes only documents and the packet, so the code I built is the tested code.
- **Scope:**
  - the C15b delivery: the Moon, Mars and Venus surfaces, biomes, structures, charred trees, Moon and Mars ores, switches, release-test hooks, DataGen output, tests, evidence packet and the 50 ledger rows;
  - the changes to earlier versions' tests (`361c02b`, `3d4cae8`, `2a74f90`, `ef4bb85`);
  - the S1 harness in `root-checks.zip`;
  - ADR-063 revision 5 (proposed).
- **Method:**
  - I read AGENTS.md, ADR-061 (§1, §2, §4.2, §5, §6), ADR-062 §1–§3, all of ADR-063 including revision 5, VERIFICATION.md, the C15b section of the implementation log, the full non-generated diff `0a99d17..7be92263`, and the generated JSON (dimensions, noise settings, biomes, features, structures, sets, tags, loot).
  - I read the legacy sources as facts only, from the pinned upstream checkout used by the packet (`…/arce-v180-c14-…/upstream/ar`).
  - I checked Forge behaviour with `javap -c` on the 47.4.10 jars (`fmlcore`, the mapped Forge jar).
  - I built and ran the export and added four reviewer probe GameTests (not part of the commit). I ran 17 mutations in three sets.
  - I rebuilt the v1.7 handoff JAR and re-ran the S1 harness, then ran two extended variants of it.
- **Isolation:**
  - The repository was only read (`git show`, `git diff`, `git log`, `git archive`).
  - All work happened in `C:/Users/Administrator/AppData/Local/Temp/arce-v180-c15b-review-38439d11`. One exported tree existed at a time: the v1.8 tree was deleted while the v1.7 handoff JAR was built, then re-exported.
  - The retained v1.6 world and its libraries were only copied, never changed (the harness verifies the source inventory).
  - The untracked `AdvancedRocketry-Community-v1plus-Development-Docs/` folder was not read.
  - All server copies were deleted after use, and the exported tree at the end (`git archive 7be92263` reproduces it). Logs are in `logs/`, probes and mutation records in `probes/` and `probes-out/`, and the S1 receipts in `s1/`, `s1b/` and `s1c/`.

## 1. Commands run and results

Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8 `--offline`, Python 3.13.

| # | Command (log) | Exit | Key numbers |
|---|---|---|---|
| 1 | `git archive 7be92263 \| tar -x -C tree` | 0 | 504 MB tree |
| 2 | `./gradlew --offline clean build runData runGameTestServer` (`logs/gradle-full-1.log`) | 0 | 5 min 17 s. **All 370 required GameTests passed.** `:test` was executed, not taken from the cache: **1,419 JUnit tests in 265 suites, 0 failures/errors/skips**. 18 `ERROR` lines and 0 `FATAL`, the same as the evidence |
| 3 | SHA-256 of all 975 files under `src/generated`, before and after run 2 | – | Identical. The only additions are the 12 gitignored `v1.8/resources/.cache` files, so DataGen is deterministic |
| 4 | `sha256sum build/libs/*.jar` after run 2, and again after `assemble` on a fresh export (`logs/gradle-v18-assemble.log`) | 0 | main `90b9ca23…`, API `252463ff…`, sources `c13d8d37…`: **all three equal the evidence** |
| 5 | `probes/jar_probe.py`, `probes/tag_union.py` | 0 | Main JAR 2,890 entries, sources JAR 2,311 entries, 0 duplicates. The Moon, Mars and Venus dimensions and the Moon, Mars and Venus noise settings appear once each, with the v1.8 bytes. The v0.3 and v1.4 superseded copies are absent. The v1.4 biomes `mars` and `venus` still ship. The v1.8 `mineable/pickaxe` and `needs_iron_tool` tags contain every entry of the earlier roots |
| 6 | Probe run: run 2 plus `ReviewProbeGameTests` P1–P3 (`logs/gradle-probe-1.log`) | 0 | 373 passed. Probe lines are in `probes-out/probe-lines.txt` (H1, I2) |
| 7 | Mutation set A plus probe P4 (`logs/gradle-mutA.log`, `logs/gradle-mutA-junit.log`) | 1 | 2 GameTests failed (MA1, MA10 killed). MA2, MA3, MA4 and MA12 survived the GameTests and the C15b JUnit classes (`V180PlanetResourcesTest`, `SurfaceShapesTest`, `V180SurfaceArtTest`); see §2 and `probes/mutations.json` |
| 8 | Mutation set B (`logs/gradle-mutB.filtered.log`) | 1 | MB1, MB2 and MB4 killed. MB4 (unclamped chunk writes) then crashed real Moon generation ("asking a region for a chunk out of bound"), so the rest moved to set C |
| 9 | Mutation set C (`logs/gradle-mutC.log`, `logs/gradle-mutC-junit.log`) | 1 | MB3 and MB5 killed by GameTests; MB5 and MB8 killed by JUnit. **MC7 (lava pool above the rim) survived** |
| 10 | Restore, then `diff -rq` of `src/` and `build.gradle` against `git archive 7be92263` | 0 | Identical, except the gitignored `.cache` |
| 11 | `python -B scripts/validate_v180_content_ledger.py`, then with `--require-accepted`, then with `--closure` | 0 / 0 / 1 | `PASS`, 653 units: IMPLEMENTED 78 (67 + 11), REDESIGNED 174 (135 + 39), PLANNED 225 (275 − 50). The closure run fails as expected until C19 |
| 12 | `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation tests.test_screen_generated_art` | 0 | `Ran 70 tests … OK` |
| 13 | `tools/audit/screen_generated_art.py --root src/generated/v1.8/resources --vanilla 1.12.2=… --vanilla 1.20.1=…` | 0 | `{"CLEAR": 25}`. The JSON equals the packet's `python/art-screen.json` byte for byte |
| 14 | `check_client_imports.py`, `validate_v120_machine_resources.py`, `validate_v090_resources.py`, `inventory_v180_content.py --check`, `vanilla_derivation.py --check` | 0 | All `PASS` or up to date. The v0.9 audit counts 1,010 files where the packet counts 1,056. The script walks `src/generated/v*/resources` with `rglob("*")`, so the difference is gitignored `.cache` files in the working copy, not content |
| 15 | `validate_repository.py --require-approved-identity` | 1 | 34 passed, 10 failed. All 10 failures are git queries ("not a git repository"), so this check cannot run in an export; not counted |
| 16 | `sha256sum -c SHA256SUMS.txt`; `probes/check_identity.py` | 0 | 7/7 OK. All 690 `source-identity.json` entries match `7be92263`. Only the packet's own 8 files are unlisted |
| 17 | v1.7 handoff JAR: `git archive 55da6a5`, `./gradlew --offline assemble` (`logs/gradle-v17-assemble.log`) | 0 | Main JAR `4326d3d6…`, **equal to the handoff hash** |
| 18 | S1 harness from `root-checks.zip`, with only `REPO` pointed at my export (`s1/`) | 0 | **PASS.** Same starts (Moon `moon_crater=4`, Mars `mars_crater=4`, Venus `geode=1, volcano=1`). Same highest surfaces (27/83/150; the hook reports top + 1). Same explored samples (end stone y 3 / red sand y 70 / yellow terracotta y 92). 108 explored chunks unchanged. ms per chunk: Overworld 60.2, Moon 12.3, Mars 14.0, Venus 15.2 |
| 19 | S1 variants `s1b/` and `s1c/`. The upgrade phase also force-loads every explored chunk and the ring of chunks around the square, and `s1c` compares chunks −5..4 | 0 | **300 explored chunks unchanged.** The ring the v1.7 build had saved at intermediate statuses was finished by v1.8 as hybrid terrain (M1, `probes-out/s1c-*`) |
| 20 | `javap -c` on `ConfigFileTypeHandler`, `ConfigFileTypeHandler$ConfigWatcher`, `GameTestServer`, `server.Main` (`probes/javap-*.txt`) | – | See M3 and M4 |

The packet's numbers all agree with my runs: 370 GameTests, 1,419 JUnit tests in 265 suites, 18 `ERROR` lines, the three JAR hashes, the JAR resource check, the 25 `CLEAR` textures, the ledger `PASS` and the S1 results.

Not verified:
- any client: models, textures, fog and sky colours, the Moon's black sky;
- `/reload`;
- the CI workflow;
- the packet's final `validate_repository.py` run, which needs a git checkout.

## 2. Findings

### High

**C15bR1-H1: most Venus geodes break the surface. Some float above the ground as discs of shell and ore.**

*Contract:*
- ADR-063 §5, table at line 258: "ore geodes (Venus) | structure `geode`, one piece below the surface".
- Revision 5, line 327: "centred four blocks of cover plus their half height below the surface".
- VERIFICATION line 118 names the test `aGeodeWritesOnlyItsChunkWithinRadius24BelowTheSurface`.

*Code:*
- `GeodeStructure.java:34-40` samples the surface once, at the chunk's middle column, and centres the lens below that one height.
- `GeodePiece.java:86-98` then writes the lens over up to 48 × 48 columns, whatever their own surface is.
- The v1.4 Venus terrain is very uneven: its density is `gradient + 1.1·|noise|`, so columns with |n| > 0.91 are solid up to the build limit. One sample area had surfaces from y 88 to 255 within 12 × 12 chunks.

*Evidence (reviewer probes, unmutated tree, `probes-out/probe-lines.txt`):*
- P1 computes 1,600 real generation points with `findValidGenerationPoint` and compares each lens column with the noise surface (`getBaseHeight`). **1,251 of 1,600 geodes (78 %) reach or pass the surface in at least one column.** In another 169, the roof lies only 1–3 blocks below the top block somewhere (`COVER` intends 4 blocks in between). Of all lens columns, 482,236 of 2,023,092 (24 %) are breached. The worst column has its roof **153 blocks above the ground**: a geode centred under a plateau extends over the cliff and floats.
- P3 generates 144 real Venus chunks: 8,674 geode shell blocks, **1,334 columns whose top block is geode shell**, and 675 columns with cave air directly under the top block.

*Tests:*
- The geode GameTest (`PlanetSurfaceGameTests.java:175-188`) places its centre by hand with the same formula and checks only the centre column.
- Mutation MA2 (`COVER` 4 → 0, so even at the centre column the roof lies directly under the top block) survived the GameTests and the C15b JUnit classes.

*Impact:* in every new Venus chunk area, shells and iron, gold, copper, tin and redstone clusters lie open on hillsides or hang in the air. These blocks are saved into the chunks for good, and the evidence claims the opposite.

*Required change:*
- Place the geode against the lowest surface over its lens. Sample a bounded grid with `getBaseHeight` (for example 5 × 5 points), set `centreY = min − COVER − reach`, and return no start when the relief across the lens exceeds a stated bound or the lens would leave the Level.
- Add a GameTest that takes real generation points over at least 500 chunks, like probe P1, and asserts cover ≥ `COVER` (or at least ≥ 1) for every lens column.
- Restate the revision 5 rule as a property that holds for every column.

### Medium

**C15bR1-M1: upgraded worlds get a band of hybrid Moon terrain that the seam disclosure does not mention: new heights, bare stone, `plains` biome, no ores or craters.**

*Contract:*
- ADR-063 §5 Seams (lines 274-275): "Chunks generated before the upgrade keep their old surface; chunks generated afterwards use the new terrain."
- ADR-061 §6 requires disclosing "the seam … and what players will see".

*Evidence:* in run `s1c`, the upgrade phase force-loaded chunks −7..6 around the explored square of the copied world.
- Every chunk the v1.7 build had saved past NOISE was finished by v1.8 with the old flat terrain. The 300 explored chunks were unchanged.
- The ring the v1.7 build had saved at the BIOMES status already held the flat generator's `minecraft:plains`. v1.8 filled that ring with new noise terrain, but the Moon surface rule is keyed on the biome only (`V180PlanetWorldgen.java:262-271`).
- Result (`probes-out/s1c-moon-top-blocks.txt`): 76 Moon chunks with the new terrain and biome `plains` everywhere. In the six chunks I listed, **218–253 of 256 top blocks are bare stone**, and 10 of the 76 chunks have no turf at all (dump of chunk (−10, −10): `probes-out/s1c-moon-chunk-m10-m10.txt`).
- Ores appear only near the chunk edges, because placed features are filtered by biome. By vanilla's rule, structure starts are computed once per chunk, so craters cannot start in chunks whose starts v1.7 already computed. That includes the wider ring v1.7 saved at the STRUCTURE_STARTS status.
- Mars and Venus show the same ring with their old biomes. There the top block is still correct, because their surface rules do not test the biome.

*Impact:* every upgraded world with explored Moon areas gets a band about a chunk wide of grey stone, sky-blue `plains` terrain around them, between the flat end stone and the new regolith. It has no ores. Craters are also missing wherever v1.7 had already computed structure starts, a wider ring. The S1 check did not show this: its upgrade phase loads one explored chunk per body and never generates next to the explored square.

*Required change:*
- Make the Moon top and filler independent of the biome for foreign biomes, for example a fallback `ON_FLOOR`/`UNDER_FLOOR` moon-turf rule after the two biome rules, or turf chosen by height.
- Disclose the band, with its missing ores and the structure-free ring, in ADR-063 §5 Seams and in the release notes.
- Add the seam step to `native_c15b_check.py` (as in `s1c/scripts/native_c15b_seam_check.py`), with an assertion on the finished ring's top blocks.

**C15bR1-M2: the structure-bound tests prove less than VERIFICATION and their own Javadoc claim, and three real defects survive them.**

*The before/after comparison does not exist:*
- `PlanetSurfaceGameTests.java:57` says each test "compares every block around it before and after".
- VERIFICATION line 118 adds "and every block outside the piece is unchanged".
- No snapshot is taken. `placeChunkByChunk` only records calls whose method name is `setBlock` (`:383-409`). A write through `setBlockAndUpdate`, `removeBlock`, `destroyBlock` or `getChunk(…).setBlockState`, made via the proxy, runs on the real level unrecorded.

*Surviving mutants (`probes/mutations.json`):*
- **MC7** raises the volcano's lava pool three blocks, so lava sits at or above the crater rim and spills when updated. It survived because `SurfaceShapesTest.java:69` hard-codes `coneRise(0) + 1` instead of using the piece's `poolLevel` (`VolcanoPiece.java:76`), and the GameTest only checks lava at the pool position.
- **MA12** drops `rim_max_y` from the saved crater piece. It survived: `savedPiecesLoadBackAndRefuseAnotherSchema` compares the re-encoded tag with the saved one, so a field that is never written is never missed. After a restart such a crater would read `rim_max_y` 0 and build no rim in its remaining chunks.
- **MA1** removes the floor clamp. It was killed only because, in that run's random world (M3), the Moon surface at (−20 008, 20 008) happened to be below y 20 (`PlanetSurfaceGameTests.java:134`).

*Also:* VERIFICATION line 120 says crater rims are "capped at y 44 by the crater test and `SurfaceShapesTest`". On the Moon a rim cannot exceed 36 + 6 = 42, so neither test exercises the cap. The §9 grid samples noise heights only (`:95`), not generated blocks. In 144 generated Moon chunks (probe P3, unmutated) the highest top block was y 33, so the bound holds, but by argument rather than by the test.

*Required change:*
- Either implement the snapshot comparison (the box plus a one-chunk margin) or correct both claims.
- Move the pool level into `VolcanoShape` and test that value. Also assert that no lava lies outside the core and the crater after placement.
- In the saved-piece test, compare the loaded piece's numbers with the constructed piece's, not tag with tag.
- Give the crater test a base below y 20 on purpose, so the floor clamp is always exercised.

**C15bR1-M3: the GameTest world has a new random seed each run, not "seed 0", so the terrain-dependent tests and the failure analysis vary from run to run.**

*Claim:*
- VERIFICATION line 214 and implementation log line 264: "the GameTest Overworld is a normal world, seed 0".
- The flake analysis puts the failures down to "the layout of the batches".

*Evidence:*
- `build.gradle:295` deletes `build/gametest/world` before every run.
- `build/gametest/server.properties` has an empty `level-seed`, and the new `level.dat` held seed 610609575218378370 with the normal preset.
- So the world does not get the seed-0 `GameTestServer.WORLD_OPTIONS` constant (`probes/javap-GameTestServer.txt`); the seed comes from the empty `level-seed`.
- My P2 crater-start statistics differed between two runs of the same code (`probes-out/probe-lines.txt`).
- Batch order is also shuffled between runs.

*Impact:* the rock, gravel and terrain around every test change with each run. So does what the C15b terrain tests exercise: the crater floor clamp (M2), the Mars rock check at a fixed y − 12 (`PlanetaryWorldGameTests.java:72`), which can fail where a crater rim sits on deep filler, and the volcano and geode sites. A green run is one sample, and a failure may not reproduce.

*Required change:*
- Pin the GameTest seed, for example by writing `level-seed=<fixed>` before the run starts, and correct both statements.
- Alternatively, make every terrain test build or choose its own terrain, as `moonSurfaceStaysBetweenY12And36ForTwoSeeds` already does with its fixed second seed.

**C15bR1-M4: switch tests write the COMMON config file, which Forge reloads on another thread. This is a likely cause of the unexplained press failure, and the packet's account of that failure is not supported.**

*Forge behaviour:*
- `ConfigFileTypeHandler.reader` builds the config with `sync()` and `autosave()` and registers a `FileWatcher` (`probes/javap-ConfigFileTypeHandler.txt`, offsets 15, 21, 129).
- `ConfigWatcher.run` calls `load()` and then `afterReload()` on the watcher thread (`probes/javap-ConfigWatcher.txt`).
- So every `BooleanValue.set` in a test writes the file, and a later reload can put back whatever the file held when that reload read it.

*What the tests do:*
- C15b adds twelve such writes in `withSwitchOff` (`PlanetFeatureGameTests.java:277-284`): six on/off pairs.
- The evidence's own debug log shows 11 config reloads during the run, two of them while the `planet_features` batch was running (`gametest-debug.log` lines 1608 and 1610).

*The press failure on `3e24ddf`:*
- A disabled press's target block was gone. If a stale reload turned `classic.smallPlatePress` back on before the rising edge, the press acted. That would explain it.
- The packet says "with no plates saved near it". On `3e24ddf` the plate assertion came after the failing block assertion (`ef4bb85~1:MaterialGameTests.java:193-194`), so it never ran.
- The switch itself was restored by the `@AfterBatch` hook (`MaterialGameTests.java:52-56`), and the C15b tests restore theirs in `finally`. Those resets are also file writes, though, and race with the same reloads.

*Required change:*
- Give `WorldgenSwitches` and the press switch a test-only in-memory override that GameTests use instead of `ForgeConfigSpec.set`.
- Correct the "no plates" sentence and record the press failure as an open item with this hypothesis. Keep the `ef4bb85` diagnostics, which now report the switch value at the rising edge.

**C15bR1-M5: revision 5 justifies the fixed Venus layout with a legacy fact that is wrong.**

*Text:*
- ADR-063 lines 315-316: "the same in every world, as the legacy planets' biome layers did not follow the world seed".
- Repeated in `PatchBiomeSource.java:16` and `V180PlanetDimensions.java:34`.

*Legacy:* `ChunkManagerPlanet` builds its layers from `world.getSeed()` (`ChunkManagerPlanet.java:63`) and calls `initWorldGenSeed(seed)` on the final layers (`:173-174`). Legacy planet biomes therefore followed the world seed.

*Impact:* the owner is asked to accept a player-visible choice (every world gets the identical Venus layout) on a false premise. The real constraint is technical: 1.20.1 biome sources receive no seed, and the copied router has no climate.

*Required change:* correct the three texts and state the real reason and the player impact. If the owner wants a per-world layout, the option is to add a seeded climate noise to the Venus router; its terrain density would stay unchanged, though the noise settings file would differ from v1.4.

### Low

**C15bR1-L1: on Venus cliffs, some volcano cones overhang.**
- `VolcanoStructure.java:32-34` also samples one surface. `VolcanoPiece.java:91` fills each cone column down to its own ground, but the box clamps the fill at base − 32.
- Probe P4: 99 of 900 real volcano generation points (11 %) have cone columns whose ground lies below the box, so the cone is cut off there with a gap of up to 23 blocks underneath.
- Change: handle this with H1, for example by refusing starts whose relief under the cone exceeds `SKIRT`.

**C15bR1-L2: some delivered rules have no test.**
- MA3 swapped the two turfs between the Moon biomes and survived both suites. `newChunksUseTheNewSurfacesAndBiomes` accepts either turf in either biome, and the JUnit test only checks that both names occur.
- MA4 made the charcoal log flammable and also survived, although "does not burn" is in revision 5 and in the ledger row.
- Change: assert the biome-to-turf pairing (rule JSON or generated columns), and add a fire or flammability check for the log.

**C15bR1-L3: the ledger rows are mostly honest, but four need clearer notes.**
- `worldgen:MapGenCaveExt`, `MapGenHighCaves`, `MapGenMassiveRavine` and `MapGenRavineExt` are REDESIGNED as "vanilla … carvers in planet biome data". C15b delivers nothing for them, and nothing shows that a data-pack planet biome with `minecraft:cave` carves on these Levels. Cite a data-guide section or an A0 check.
- `worldgen:WorldGenCharredTree` is IMPLEMENTED, but it adds a stub branch the legacy tree did not have.
- `biome:volcanic` is IMPLEMENTED without its legacy creeper spawns, and `biome:hotdryrock` keeps the v1.4 sky rather than the legacy `0x664444`.
- Change: name these differences in the notes column. All 50 targets and evidence paths are otherwise correct, and every row's unit and IDs appear as whole tokens in VERIFICATION.

**C15bR1-L4: revision 5 and the release notes miss some player-visible differences.**
- §5 line 248 still says Mars and Venus "switch to multi-biome sources". Mars uses a `minecraft:fixed` source with one biome (`V180PlanetDimensions.java:53`).
- Biome temperatures changed from v1.4: Mars 0.1 → 0.9, Venus 2.0 → 1.0. These are the legacy values, but in new Mars chunks water no longer freezes, since 0.9 is above vanilla's 0.15 freezing threshold.
- A geode reads its ore tag when each chunk is placed. "Finishes the same way after a restart" therefore holds only while data packs keep that tag unchanged.
- The CHANGELOG (line 59) gives the Moon drop as 44–68 blocks and omits "up to about 76 over a crater floor" (ADR-063 line 293).
- Change: state these in revision 5 and in the notes.

**C15bR1-L5: a test was narrowed, and a clean-up is not failure-safe.**
- `V180MaterialResourcesTest.java:172` now keeps only `overworld_*` placed features, so no test fails on an unexpected extra placed feature in the v1.8 root.
- The elevator zone test's clean-up is not in `finally`, so its failure left its rider online and failed the v1.5 sky-context test as well. This happened in `prechecks/precheck-2` and in `failed-run-3e24ddf`.
- Change: assert the full placed-feature set, and make the fixture clean-up failure-safe.

**C15bR1-L6: S1 proves less than its table suggests.**
- "108 explored chunks unchanged" includes 105 chunks the v1.8 server never loaded: per body only the sample chunk (−3, −3) is loaded. No seam is exercised.
- The Overworld reference is measured first, with a cold JIT (slowest chunk 1.1–1.4 s), which loosens the budget.
- Change: adopt the seam step from M1, warm up before measuring, or measure the reference last. My variants confirm the explored chunks stay unchanged even when they are all loaded and their ring is finished (300 chunks).

### Info

- **C15bR1-I1:** every reproducible packet number matches (§1). I could not reproduce the final `validate_repository.py` run in an export (row 15).
- **C15bR1-I2:** the Moon's ore yield is lower than the "one vein of 16 per chunk" wording suggests. The y 4–40 range lies mostly in turf and air above the Moon's thin stone. In 144 generated Moon chunks, iridium appeared in 85 chunks (1,272 blocks); on Mars it appeared in all 144 (2,635 blocks). The source is still guaranteed. Worth one sentence in §4.
- **C15bR1-I3:** the crater radius distribution (cubed uniform) gives 72 % of radii below 24, against the legacy `getBaseRadius` 88 %, and about 10 % at 40 or above, against about 7 %. "Weighted … as in the legacy generator" is approximate.
- **C15bR1-I4:** `PatchBiomeSource` (1–16 biomes) and `CraterStructure` (ordered ranges) validate by throwing from their constructors. `RegistryDataLoader` catches the exception and reports it, so a bad data pack fails startup with a message. A `DataResult` error would name the field.
- **C15bR1-I5:** the v1.7 test changes keep their intent.
  - `361c02b` waits until the generator has really evaluated (`orbitBody` is set only in `eligibility()`); the 21-tick bound is unchanged.
  - `3d4cae8` and `ef4bb85` only add messages; their assertions are equivalent.
  - `2a74f90` puts a glass roof 3 blocks above the anchor. That is outside the 2-block arrival platform the ride rules check, so the obstruction rule is not weakened.
  - The kept logs support the elevator explanation: the platform was filled with gravel, with no entities. They support the sky-context explanation: the extra recipient `c4d7340c…` is the zone test's rider. The press account is covered in M4.
- **C15bR1-I6:** AGENTS.md compliance.
  - No client imports; no new packets.
  - Generation is bounded: radius and height caps, 32 ore kinds at most, 16 biomes at most, pieces clamped to their chunk.
  - Saved pieces carry schema 1.
  - No new class exceeds 500 lines. `ElevatorGameTests` (720 lines) grew by 30.
  - The release-test hooks require operator level and the JVM flag. The radius is capped at 8, but `sample` takes unbounded coordinates; that is acceptable for a test hook.

## 3. Verdicts

**C15b: accept with required changes.**
- Required: H1 (geode placement and a real-generation test), M1 (Moon surface fallback and disclosure of the upgrade band, plus the S1 seam step), M2 (correct the test claims, the pool level and the saved-piece check), M3 (pin the GameTest seed or make the tests independent of it, and correct the statement), M4 (in-memory switch overrides for tests, correct the press account) and M5 (correct the Venus rationale).
- Recommended: the Lows.

Everything else holds:
- The Moon terrain, ore sets and numbers, switches, drops, tools, saved-piece schema, Venus patch source and superseded-file exclusions match the contract.
- The pieces write only inside their chunk and box (MB2 and MB4 were killed).
- Explored chunks keep their surfaces.
- The evidence reproduces.

**ADR-063 revision 5: accept with changes.** Before acceptance:
1. Replace the geode rule with one that guarantees cover over the whole lens, or refuses the start (H1). Add the volcano relief limit (L1).
2. Correct the fixed Venus salt rationale and state its player impact (M5).
3. Add the hybrid upgrade band and the structure-free ring to the seam text (M1).
4. State Mars's single-biome fixed source, the biome temperatures, and the stub branch as an addition. Qualify "finishes the same way" for the geode ore tag (L4).

The other revision 5 decisions are consistent with the legacy facts and the code:
- two crater structures, floors at y 5 or above because of the bedrock gradient;
- no carvers;
- no spawns;
- the five switches;
- the surfaces;
- drops and tools;
- schema-versioned saved pieces.
