# V180 C15a — materials, ores and the small plate press: verification

Subsequent2026-10-04 correction:the original missing press acquisition route
is now independently source-reviewed and development-verified under accepted
ADR-063 revision7,with1,642 Root JUnit and460 Root/independent GameTests;
see [acquisition verification](PRESS-ACQUISITION-VERIFICATION.md).
The dated historical result/proposed-contract statements below remain unchanged;
they do not override the later conditional acceptances or approve version Gates.

Date: 2026-10-03. Scope: slice C15a of ADR-062 §6 under
[ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md),
[ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) and
[ADR-063](../../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) §1–§4 and §8.
ADR-063 revision 3 and ADR-061 revision 6 are accepted; ADR-063 revision 4 and
ADR-061 revision 7, from this slice and its review, are **proposed** and wait for the
owner. Branch `codex/v1.8.0-classic-content`; base `39fc16c` (the C14 acceptance);
tested commit `a89f700` (after implementation review round 1). Development evidence
only: **no Required Gate, candidate or release decision**, and no world migration (no
saved-data schema changes).

The first evidence run (on `baa20f9`, 351 GameTests and 1,393 JUnit tests) and its
packet are in the history at `f95e851`; this packet replaces it after the review.

## Commits

| Commit | Content |
|---|---|
| `d666aff` | Ledger: the thirteen `config:WORLDGEN.*` rows and `worldgen:CustomizableOreGen` move to C15b, which closes their Moon and Mars half (ADR-062 §7) |
| `920308e` | The v1.2 rolling capability GameTest stores diamonds instead of gold ingots, which C15a now rolls; its assertions are unchanged |
| `f2006e8` | ADR-063 revision 4 (proposed) |
| `cb52c0b` | The material set, ores, recipes, the small plate press, Overworld ore features, v1.8 DataGen, runtime `1.20.1-1.8.0-dev` |
| `baa20f9` | Tests for ambiguous press matches and the switch codec, found missing by a mutation review |
| `d8f80ed` | The ledger validator's test fixture copies the batch evidence folders |
| `f95e851` | The first evidence packet and the ledger delivery |
| `9cdd9e0` … `a89f700` | Implementation review round 1: one commit per finding ([dispositions](review-01-dispositions.md)) |

## Implementation review round 1

One independent reviewer ([report](reviews/REVIEW-01.md)) exported `f95e851`, rebuilt it
(the 351 GameTests, the 1,393 JUnit tests, DataGen, the main and API JAR hashes and the
art screen all reproduced), ran 17 mutations and two probes, and found 0 Critical,
1 High, 3 Medium, 3 Low and 7 Info. Verdicts: C15a **accept with required changes**;
ADR-063 revision 4 **accept with changes**. The main findings:

- **H1:** pressing rutile gave titanium dust that smelts in a furnace, bypassing the
  electric arc furnace ADR-063 §1 requires. Rutile no longer presses.
- **M1:** dilithium ore dropped itself and gave experience, an endless XP source. It no
  longer gives mining experience.
- **M2:** Minecraft parses recipes and sends them to clients before tags are bound, so
  the kernel machines could not honour ADR-061 §2.2's tag promise. Kernel recipes now
  reject tag ingredients outright until C16a resolves tags after binding (ADR-061
  revision 7, proposed).
- **M3, L1, L2:** the press serializer, the ambiguity refusal, the ore tags and the
  Overworld numbers gained tests; every surviving review mutant they cover now fails.

Every finding has a disposition in [review-01-dispositions](review-01-dispositions.md).

## Implementation review round 2

The same reviewer confirmed the fixes on `1a9f956` ([report](reviews/REVIEW-02.md)): it
rebuilt the export (353 GameTests, 1,404 JUnit tests, the three JAR hashes and every
JAR entry equal to this packet), re-ran the round-1 mutants (all now fail) and new
ones, and probed tag recipes for all three kernel machines (refused at start) and
dilithium experience (0). Findings: 0 Critical, 0 High, 0 Medium, 1 Low, 4 Info, no
new defect. Verdicts: **C15a accept**; **ADR-063 revision 4 accept as written**;
**ADR-061 revision 7 accept as written**. The Low (network-side tag refusal tested
only for rolling) and two Info notes are answered in their own commits
([review-02-dispositions](review-02-dispositions.md)).

## Changed after the run

`post-run-changes.json` lists the tested files changed after the run on `a89f700`: two
test classes (C15aR2-L1, `f00e2a3`) and the CHANGELOG's data-pack note (C15aR2-I4,
`d18a58f`). The affected test classes were re-run on `d18a58f`
(`post-run/junit-r2.log`, exit 0): `PrecisionAssemblerRecipeTest` 8 tests,
`BoundedItemIngredientCodecTest` 3, `RollingMachineRecipeTest` 5, all passing.

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:platepress` | IMPLEMENTED | `advancedrocketrycommunity:small_plate_press` |
| `config_file:SmallPlatePress.xml` | REDESIGNED | the `small_plate_press` recipe type with data pack recipes |
| `integration:jei/platePresser` | IMPLEMENTED | JEI small plate press category (registered by the plugin; the view is unit-tested; a client start with JEI is open, see below) |
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
furnace comes in C16b, as ADR-063 §1 states, and titanium waits for it too. The silicon
boule and the dilithium crystal likewise wait for C16c.

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
| A0 recipe and tag JSON audit | `V180MaterialResourcesTest`: rolling and press recipe sets and fields (17 press recipes, none for rutile), no rutile smelting, tags and umbrellas without duplicates, ore tags for all nine ores, the coils group, both languages, models, block states and loot tables, the tool tags as complete copies, and the Overworld vein numbers and targets against the contract |
| A0 DataGen determinism | `runData`, then `git status -- src/generated` clean; `V180MaterialArtTest` re-encodes every texture and compares it with the committed bytes |
| A0 feature bounds | `overworldVeinsReplaceStoneAndDeepslateNearTheirOrigin` (every vein write within 5 blocks of its origin, replacing only stone or deepslate with the matching variant); `overworldPlacementsFollowTheServerSwitch` (positions inside the origin chunk, y −16..64) |
| A0 asset records and derivation | No file is imported in C15a. The 19 drawn textures pass `tools/audit/screen_generated_art.py` against both vanilla clients (also a CI step); the reviewer also screened them against the 356 legacy textures (all `CLEAR`) |
| A0 JEI plate press category | `SmallPlatePressViewTest` tests the category's view of a recipe; the plugin's registration is not tested and no client started with JEI (open item) |
| A0 kernel recipe ingredients (revision 7) | `RollingMachineRecipeTest`, `PrecisionAssemblerRecipeTest`, `BoundedItemIngredientCodecTest`: tag and mixed ingredients are refused, also from the network |
| A0 press recipe serializer | `SmallPlatePressRecipeSerializerTest`: byte, field, schema, count, item, entry and network bounds, and a round trip |
| A1 press: block → plates, ore → dust | `aPulsePressesAStorageBlockIntoFourPlates` (titanium, vanilla iron and copper blocks), `aPulsePressesAnOreIntoTwoDust` (tin, deepslate aluminum, vanilla iron ore, dilithium; rutile refused and kept) |
| A1 press: no obsidian, block entity, unbreakable | `withoutObsidianOrWithABlockEntityNothingIsPressed` |
| A1 press: unpowered, repeated pulses | `onlyARisingEdgePresses` (nothing while unpowered or while the signal is held; one operation per rising edge) |
| A1 press: unloaded neighbour | `anUnloadedTargetIsNeitherPressedNorLoaded` (`UNLOADED`; the chunk stays unloaded; synthetic, as the review notes) |
| A1 press: cancelled `PistonEvent.Pre` | `aCancelledPistonEventLeavesTheBlock` |
| A1 press: ambiguous match (revision 4) | `anAmbiguousBlockIsRefusedAndStays` (an overlapping recipe loaded and restored), `SmallPlatePressRecipeTest` |
| Server switches (ADR-061 §3.5) | `theServerSwitchTurnsThePressOff`, `overworldPlacementsFollowTheServerSwitch`, `SwitchPlacementTest` |
| No experience farm | `dilithiumOreDropsItselfWithoutExperience` |
| Ambiguity of the data | `everyPressInputMatchesOneRecipe`, `everyRollingInputMatchesOneRecipe` (every input at a full stack matches exactly one recipe) |
| A1 smelting and rolling | `oresRawItemsAndDustSmeltIntoIngots` (every ore block but rutile; rutile never), `everyRollingInputMatchesOneRecipe` (300 ticks, 20 and 200 FE/t, 100 mB; no iron ingot → plate), `legacyCraftingShapesMakeTheProducts` |
| A1 Overworld ores without iridium | `overworldBiomesCarryTheOresWithoutIridium` (plains, desert and deep dark carry the four features; no Overworld feature places iridium; the Nether gets none) |

The Moon and Mars ore sets, Moon dilithium and the structure bounds belong to C15b.

## Generated art

The 19 textures are 16 × 16 palette grids in `V180MaterialArt`, written by DataGen
(`NEW`); the ore models reference vanilla stone and deepslate by resource location.
Two screens before the first commit changed the art:

1. Against the 1.20.1 client, the silicon boule was `SUSPECT` against
   `item/tadpole_bucket.png` (overlap 0.12 over three colours): the even 24-step grey
   scale shares 120, 168 and 216 with the vanilla bucket. The palette moved to greys
   that few vanilla textures use (20, 44, 72, 93, 117, 141, 169, 191, 210, 238).
2. Against both clients, the press top and bottom (concentric bevels) were `SUSPECT`
   against 1.12.2 `blocks/itemframe_background.png` and `particle/footprint.png` by
   rank correlation (0.76 and 0.84 over fully opaque masks). They were redrawn as a
   bolted ram cap and a stud grid (rank 0.50 and 0.70).

Final screen: 19 `CLEAR` against both clients (`python/art-screen.json` in
`root-checks.zip`). The review notes that both changes tune the art against the screen
and that `coil_side` scores rank 0.749 against 1.12.2 `particle/footprint.png`; the
ADR-061 §4.5 human visual review should compare `coil_side`, `coil_top`, `storage` and
the three press faces. The screen is a measured check, not proof of originality.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, Python 3.13.

| Command | Result |
|---|---|
| `gradlew --offline clean build test runData runGameTestServer` on `a89f700` | Exit 0, 4m 51s. **353 required GameTests** passed. `git status -- src/generated` after DataGen: clean |
| `gradlew --offline test --rerun` | Exit 0, 2m 7s. **1,404 JUnit tests / 262 suites**, 0 failures, errors or skips |
| JAR resource check (`jar-resource-check.txt`) | No duplicate entries; `needs_iron_tool`, `mineable/pickaxe` and `needs_stone_tool` appear once each, as the v1.8 copies |
| `python -B scripts/validate_v180_content_ledger.py` | Exit 0; `"result": "PASS"` |
| `… --require-accepted` | Exit 0; `"result": "PASS"` |
| `… --closure` (expected to fail until C19) | Exit 1; `"result": "FAIL"` |
| `python -B tools/audit/inventory_v180_content.py --upstream <Temp> --check` | Exit 0; `inventory: up to date` |
| `python -B tools/audit/vanilla_derivation.py --upstream <Temp> --vanilla … --check` | Exit 0; `vanilla derivation: up to date` |
| `python -B tools/audit/screen_generated_art.py --root src/generated/v1.8/resources --vanilla 1.12.2=… --vanilla 1.20.1=…` | Exit 0; `{"CLEAR": 19}` |
| `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_vanilla_derivation tests.test_screen_generated_art` | Exit 0; `Ran 70 tests in 103.993s; OK` |
| `python -B scripts/validate_v120_machine_resources.py` | Exit 0; `PASS: 9 machine blocks, recipes, loot, models, tags and bilingual keys` |
| `python -B scripts/validate_v090_resources.py` | Exit 0; `[PASS] Beta resource audit: 973 files, 273 JSON, 745 bilingual keys, 290 asset references` |
| `python -B scripts/check_client_imports.py` | Exit 0; `[PASS] No net.minecraft.client references outside the client package` |

Final validators: after packaging, `validate_repository.py --require-approved-identity` exit 0 (45 passed, 0 failed); `validate_v1plus_planning.py` exit 0; `-m unittest tests.test_v1plus_planning` exit 0 (15 tests); `validate_bootstrap_provenance.py` exit 0; the ledger validator exit 0. They need the finished packet, so their log is `packaging/out/validation-final.log` in the Temp evidence directory, outside `root-checks.zip`.

The three JARs (`artifacts.json`, with every entry in `artifact-entries.json`): main
`fcc5a878…`, API `252463ff…`, sources `a5581ebe…`. The full build log has 18 ERROR lines and
0 FATAL, from intentional failure-injection GameTests and the missing
`server.properties`. This is not a clean-log claim.

## Fixed during the slice, before the first commit

- Machine recipes resolve their ingredients when recipes load, before tags are bound,
  so a tag ingredient would have failed on a fresh start: rolling recipes name items,
  and the press bounds its JSON at load and resolves tags when it acts.
- An iron ingot → plate rolling recipe would have made itself and `rolling_iron_bars`
  ambiguous for two or more ingots; iron plates come from the press (ADR-063
  revision 4).
- The coils group is also the coil umbrella tag, so each coil was listed twice.
- The first GameTest run failed two tests: the v1.2 rolling capability test (its gold
  ingots now roll; `920308e`) and the new rising-edge test (the first plates were
  pushed out of the block placed on them). The log of that run was overwritten by the
  rerun; its two failure lines are quoted in the implementation log.
- After the first evidence run, four ledger-validator unit tests failed because their
  fixture lacked the batch evidence folders; `d8f80ed` fixed the fixture.

## Not verified

- Any client: tinted models, the creative tab, the JEI category on a real client
  (V0/V1), and startup with JEI installed (open C15 item). The GameTest server starts
  without JEI.
- Ore distribution in a newly generated world beyond the GameTests.
- A dedicated-server restart: C15a adds no saved data; the press keeps only its
  `powered` block state.
- `/reload` at runtime (the kernel codec now refuses tags, so start and `/reload` agree).
- The CI workflow on GitHub (this branch has no pull request).
- ADR-063 revision 4 and ADR-061 revision 7 are proposed, not accepted.

Next: the owner's decision on ADR-063 revision 4 and ADR-061 revision 7 (both recommended
for acceptance as written by the review; including the iron-plate route), then C15b in
[COMPLETION-PLAN](../../status/COMPLETION-PLAN.md).
