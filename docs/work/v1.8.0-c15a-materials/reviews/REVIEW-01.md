# C15a independent implementation review, round 1 (C15aR1)

- **Reviewed commit:** `f95e851a` on `codex/v1.8.0-classic-content` (base `39fc16c`, C14 acceptance). Commits `d666aff`, `920308e`, `f2006e8`, `cb52c0b`, `baa20f9`, `d8f80ed`, `f95e851`.
- **Scope:** the C15a delivery (material set, ores, recipes, the small plate press, Overworld ore features, v1.8 DataGen root, tests, evidence packet, ledger rows) and ADR-063 revision 4 (proposed).
- **Method:** I read AGENTS.md, ADR-061, ADR-062 §1/§7, ADR-063 (all sections, including revision 4), VERIFICATION.md, the C15a implementation-log section and the full non-generated diff `39fc16c..f95e851a`, and spot-checked the generated JSON. I checked Forge 1.20.1-47.4.10 behaviour with `javap -c` on the mapped Forge JAR in the Gradle cache. I also built and ran the tree, ran three mutation runs and two evidence probes, and screened the art.
- **Isolation:** the repository was only read (`git archive`, `git show`, `git diff`, `git log`, `git ls-tree`). All work happened in `C:/Users/Administrator/AppData/Local/Temp/arce-v180-c15a-review-251d3b5dc61fbe69`. The untracked `AdvancedRocketry-Community-v1plus-Development-Docs/` folder was not read.
- **Working-copy changes by someone else:** while this review ran (03:25–03:39 local time), the repository's working copy gained changes I did not make: `config/CommonConfig.java` and `CommonConfigTest.java` modified, and `celestial/surface/` and `config/WorldgenSwitches.java` new. They look like C15b work in progress. They are not part of `f95e851a`, this review did not read them, and they had no effect on the exported tree.

## 1. Commands run and results

Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8 `--offline`, Python 3.13. All runs used the tree copy `tree/`, which was exported with `git archive f95e851a | tar -x`.

| # | Command | Exit | Key numbers |
|---|---|---|---|
| 1 | `./gradlew --offline clean build runData runGameTestServer` (`gradle-full.log`) | 0 | 5 min 17 s; **All 351 required GameTests passed**. `:test` was executed, not taken from the cache: **1,393 JUnit tests in 260 suites, 0 failures/errors/skips**. 18 `ERROR` lines, the same as in the evidence, all from intentional failure injection and the missing `server.properties` |
| 2 | Hash list of `src/generated/**`, taken before and after run 1 (excluding `.cache`) | – | 896 files, **identical**: DataGen leaves no diff |
| 3 | `sha256sum build/libs/*.jar` after run 1, and again after a rebuild on the restored tree (`assemble`, exit 0) | – | main `6e9238b8…` **= evidence**; API `252463ff…` **= evidence**; sources `60f7a03c…` **≠ evidence `9d715998…`** (2,209,906 B vs 2,210,000 B), see I3 |
| 4 | `probes/jar_check.py` on the rebuilt main JAR | 0 | 2,777 entries, 0 duplicates. All 501 v1.8 generated files are packaged byte-identical. `needs_iron_tool`, `mineable/pickaxe` and `needs_stone_tool` appear once each and equal the v1.8 copies. No `.cache` entries |
| 5 | Mutation set A + probes: `./gradlew --offline runGameTestServer` (`gradle-mutA.log`) | 1 | 7 required tests failed: 6 mutants killed, 1 probe failed as intended (§3) |
| 6 | Mutation sets B + C: `./gradlew --offline --continue test runGameTestServer` (`gradle-mutBC.log`) | 1 | JUnit: 1,393 tests, 1 failed (M13). GameTests: 3 failed (M13, plus two unrelated tests, see I2) |
| 7 | Restore the tree, then `./gradlew --offline runGameTestServer` (`gradle-clean-rerun.log`) | 0 | All 351 required tests passed. `probes/tree_vs_git.py` shows that `src/`, `tests/` and `tools/` equal `f95e851a` |
| 8 | `python -B scripts/validate_v180_content_ledger.py` | 0 | `PASS`, 653 units: IMPLEMENTED 67 (52 + 15), REDESIGNED 135 (133 + 2), PLANNED 275 (292 − 17) |
| 9 | `… --require-accepted` | 0 | `PASS` |
| 10 | `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_screen_generated_art` | 0 | `Ran 60 tests in 140.2s — OK` |
| 11 | `python -B scripts/check_client_imports.py` | 0 | `[PASS] No net.minecraft.client references outside the client package` |
| 12 | `python -B tools/audit/screen_generated_art.py --root src/generated/v1.8/resources --vanilla 1.12.2=… --vanilla 1.20.1=…` (client SHA-256 `8ada07da…` / `56b71336…`, the ADR-061 pins) | 0 | `{"CLEAR": 19}`. The `files` section is identical to the evidence's `python/art-screen.json` |
| 13 | `probes/screen_vs_legacy.py`: the same measures against the 356 upstream AR PNG files at `c5cd5af` | 0 | 19 `CLEAR`. The best legacy match is a rank of 0.60 or below |
| 14 | `sha256sum -c SHA256SUMS.txt` in the evidence folder; `probes/check_identity.py` | 0 | 7/7 OK. All 549 `source-identity.json` entries match `f95e851a`. The only files not listed are the packet's own 8 files |
| 15 | `javap -c` on `Ingredient`, `Ingredient$TagValue`, `ReloadableServerResources`, `WorldLoader`, `MinecraftServer`, `PlayerList`, `DropExperienceBlock` (`javap/`) | – | See H1, M1 and M2 |

The evidence packet matches my runs. The 351 GameTests, the 1,393 JUnit tests in 260 suites, the 18 `ERROR` lines, the main and API JAR hashes, the JAR tag check, the 19 `CLEAR` art results, the ledger `PASS` and the Python unit-test logs (`py-07` exit 1, `py-07b` exit 0) all agree with what I measured.

Not checked:
- any client (tints, creative tab, JEI category), and startup with JEI (the JEI runtime JAR is not in the offline cache);
- a dedicated server or packaged run;
- `/reload` at runtime (H1 and M2 reason about it from bytecode);
- LibVulpes textures, which are not available locally;
- the GitHub CI workflow.

## 2. Findings

### High

**C15aR1-H1: the press opens a furnace route to titanium that ADR-063 §1 excludes and the legacy game did not have.**

*Files:*
- `src/generated/v1.8/resources/data/advancedrocketrycommunity/recipes/pressing_titanium_dust.json` (ingredient `#forge:ores/titanium`), produced by `V180MaterialRecipes.java:168-172`;
- `titanium_ingot_from_dust_smelting.json` and `titanium_ingot_from_dust_blasting.json`;
- asserted by `MaterialGameTests.java:79-80` and `MaterialRecipeGameTests.java:102`.

*Scenario:* a player mines rutile ore with silk touch, puts it on obsidian under a press and gives a redstone pulse. The press gives 2 titanium dust, and a vanilla furnace turns them into 2 titanium ingots. That is two ingots per ore, with no energy, from the Overworld, in C15a.

*Contract:* ADR-063 §1 says: "Rutile does not smelt in a furnace: titanium comes from the electric arc furnace (C16b), as in the legacy game."

*Legacy:* `util/RecipeHandler.java` adds a press "ore → 2 dust" recipe only for a material that itself has `DUST` and (`ORE` or vanilla). In the ADR-063 §1 table, which is the legacy product list ("No legacy product is dropped and none is added"), titanium has no ore of its own and rutile has no dust. So the legacy generator made no press recipe that turns rutile into titanium dust. I inferred this from the code and the table; the LibVulpes source was not available to me.

*Evidence that the route works today:* the delivered GameTests assert both steps: deepslate rutile presses into titanium dust, and titanium dust smelts into a titanium ingot. VERIFICATION ("rutile never") and the CHANGELOG ("rutile waits for the electric arc furnace") present rutile as gated, which the press route contradicts.

*Fix:*
- Recommended: drop `pressing_titanium_dust`, so rutile does not press, as in the legacy game. Change `aPulsePressesAnOreIntoTwoDust` to assert that rutile is refused (`NO_RECIPE`), and add a resource test.
- Alternatively, the owner accepts this route explicitly in revision 4. In that case, correct VERIFICATION and the CHANGELOG, and the C16d recipe graph has to treat titanium as reachable without energy.

### Medium

**C15aR1-M1: dilithium ore is an endless XP source.**

*Files:*
- `MaterialContent.java:110` makes `DropExperienceBlock(properties, UniformInt.of(2, 5))`;
- `V180MaterialData.java:56-61` makes the dilithium ores `dropSelf`.

*Scenario:* a player in survival breaks a dilithium ore with an iron pickaxe and gets the ore back plus 2–5 XP. They place it and break it again, as often as they like. In 1.20.1 Forge, `DropExperienceBlock.getExpDrop` gives XP whenever silk touch is 0, and nothing records whether a block was placed by a player. Vanilla avoids this because no XP-giving ore drops itself. The code comment's comparison with "other gem ores" does not hold: those drop gems.

*Evidence:* the probe GameTest `ReviewProbeGameTests.reviewProbeDilithiumOreIsNoXpFarm`, run in run 5 and since removed, logged:

```
REVIEW-PROBE dilithium_ore drops=[1 dilithium_ore] dropsItself=true xpOver20Breaks=73
```

*Fix:* make both dilithium ores a plain `Block` (or give them `ConstantInt.of(0)`); the smelting recipe already gives 1.0 XP. Add a GameTest that the ore drops itself with 0 XP when broken without silk touch.

**C15aR1-M2: revision 4's "rolling recipes name items" breaks ADR-061 §2.2, and ADR-061 §2.2 now rests on a premise the code cannot meet. The underlying kernel behaviour is a v1.2 defect that C16 will hit.**

*Files:*
- `BoundedItemIngredientCodec.java:53-76`, `resolveAlternatives`, called from the constructors at `RollingMachineRecipe.java:87` and `PrecisionAssemblerRecipe.java:89`;
- `ElectrolyzerRecipe.java:89/401`, which has its own copy of the same check.

All three constructors run in both `fromJson` and `fromNetwork`.

*Facts I verified:*
1. **Recipes parse before tags are bound.** `ReloadableServerResources.loadResources` runs all reload listeners, including the recipe manager, first. Tags are bound afterwards: `WorldLoader.lambda$load$1` on a fresh start and `MinecraftServer.lambda$reloadResources$26` on `/reload` call `updateRegistryTags`.
2. **A tag ingredient fails on a fresh start.** Forge's `Ingredient$TagValue.getItems` returns a `BARRIER` stack named "Empty Tag" for an empty or unbound tag. `resolveAlternatives` then rejects it as an item that carries NBT. My probe data-pack recipe `review_probe_rolling_tag` (`{"tag":"forge:ingots/tin"}`) failed at GameTest server start with `Invalid Rolling Machine recipe …: machine ingredients cannot be empty or carry NBT`.
3. **On `/reload`, the same recipe would load against the previous tags** (stale), because the old tags stay bound while it parses. I reasoned this from the bytecode; I did not run it.
4. **On login, recipes are sent before tags.** In `PlayerList.placeNewPlayer`, `ClientboundUpdateRecipesPacket` comes before `ClientboundUpdateTagsPacket`. A kernel recipe with a tag that loaded through `/reload` would therefore throw in the client's `fromNetwork`, because it resolves alternatives against the client's unbound or stale tags. I reasoned the consequence; I did not run it.

*Effect on the contract:*
- ADR-061 §2.2 says: "Recipes consume tags … Other mods' titanium therefore works in our machines", and that the 32-variant limit is "enforced by `BoundedItemIngredientCodec` when the recipe is parsed". No kernel recipe can use a tag.
- Revision 4 works around this for rolling without saying that it gives up §2.2 for the rolling machine: another mod's titanium, aluminum or copper ingot will not roll.
- C16's machine family (ADR-061 §3.1) relies on §2.2.

*Fix:*
- Revision 4 should state the deviation from ADR-061 §2.2 and give it an expiry (for example, the C16a shared machine family).
- Correct the ADR-061 §2.2 sentence about the parse-time limit.
- In the C16a kernel work, resolve tag alternatives lazily after binding (on first lookup, or rebuilt on `TagsUpdatedEvent`, enforcing the 32-variant limit there). Until then, the codec should reject `tag` entries in kernel recipes outright, so that start and `/reload` behave the same and client sync cannot break.

**C15aR1-M3: the press recipe serializer, the slice's only data-pack and network entry point, has no tests.**

*Files:* `SmallPlatePressRecipe.java:128-178`.

*Surviving mutants:*
- M14: the 4,096-byte JSON limit removed;
- M15: the field-set check removed;
- M16: `fromNetwork` ignores the count it reads and makes 4;
- M17: the `schema_version` check removed.

Each survived the full JUnit and GameTest suites (run 6). `SmallPlatePressRecipeTest` covers only `matching()`. `SmallPlatePressViewTest` covers only the constructor's count and empty-ingredient checks. Nothing calls `fromJson`, `toNetwork` or `fromNetwork`. The v1.2 kernel recipes have such tests.

*Scenario:* a later edit drops a bound or breaks the network count, and nothing fails. For example, clients would then show 4 plates in JEI while the server gives a different count.

*Fix:* add serializer tests like `RollingMachineRecipeTest`:
- JSON over 4,096 bytes;
- missing or extra top-level and result fields;
- `schema_version` other than 1, and non-integer values;
- counts of 0, 65, and above the item's max stack;
- an unknown item;
- more than 32 ingredient entries, and a tag entry;
- a `toNetwork`/`fromNetwork` round trip of an item and a tag ingredient;
- a truncated buffer and an oversized string.

### Low

**C15aR1-L1: the refusal of ambiguous matches, a revision 4 behaviour, is not tested.**

*Files:* `SmallPlatePressBlock.java:93-96`.

*Evidence:* mutant M6 (`if (false && matches.size() > 1)`, so the press acts on the first match) survived all 351 GameTests (run 5). The "Ambiguity" row in VERIFICATION cites only data checks (`everyPressInputMatchesOneRecipe`, `everyRollingInputMatchesOneRecipe`) and the pure `matching()` test. None of them shows that `press()` refuses two matches and leaves the block in place.

*Fix:* add a GameTest that temporarily adds an overlapping recipe through `RecipeManager#replaceRecipes` (and restores the set), presses, and asserts `AMBIGUOUS` with the block still present. Alternatively, move the decision into a pure method and unit-test it.

**C15aR1-L2: ore tags and worldgen numbers are not pinned by tests.**

*Surviving mutants (run 6):*
- M8: `rutile_ore` removed from the item tag `forge:ores/titanium`;
- M9: `aluminum_ore` removed from the item tag `forge:ores/aluminum`, so stone aluminum ore neither smelts nor presses;
- M10: rutile vein size 6 → 12;
- M11: aluminum minimum height −16 → −64;
- M12: the deepslate variant removed from the block tag `forge:ores/dilithium`.

*Why they survive:*
- `V180MaterialResourcesTest` checks ore tags only for the titanium/rutile block tags.
- `oresRawItemsAndDustSmeltIntoIngots` cooks raw aluminum but not aluminum ore.
- The vein test accepts any count up to 2 × size.
- The placement test uses one random seed, which happened to keep −64 hidden for a single aluminum position.

*Fix:*
- a JSON audit of every configured feature (`size`, both targets) and placed feature (`count`, height anchors) against `OverworldOres.VEINS`;
- item and block ore tags for every stone and deepslate ore;
- `cooks()` for every ore block.

**C15aR1-L3: the JEI items of ADR-063 §9 are only partly evidenced.**

*Files:* `ArceJeiPlugin.java` (press category, catalyst and recipes); `SmallPlatePressViewTest`.

- The press registration in `ArceJeiPlugin` is not tested. `SmallPlatePressViewTest` covers the view record only.
- §9 also asks that "the game starts with and without JEI", but only the JEI-absent GameTest server ran. VERIFICATION's "Not verified" section admits this.
- However, the contract-test table row reads as if the plugin registration were tested ("the plugin registers the category, its catalyst and every `small_plate_press` recipe").
- The ledger row `integration:jei/platePresser` is IMPLEMENTED on the strength of code inspection alone.

*Fix:*
- Reword the table row.
- Run the JEI-present start (as V120-INT-02 did for v1.2), or record it as an open C15 item, before C15 closes.

### Info

**C15aR1-I1: duplicate entries in the ore umbrella tags.** `forge:ores` (item and block) lists every sub-tag twice, and `#forge:ores/rutile` and `#forge:ores/titanium` twice each. The cause is that `addTag` is called once per stone and deepslate entry (`V180MaterialData.java:128,160`). The tag loader removes duplicates, so this is harmless. It is the same class of slip as the duplicated coils that the implementation log reports fixing.

**C15aR1-I2: two flaky GameTests outside C15a.** `debtandcreditsettleontheengine` ("The credit did not dig exactly two layers", `LaserPhysicalGameTests`) and `theskycontextisserverderivedsentonchangeandfollowseverychange` (`StationSkyContextGameTests`) failed once in run 6. That run's mutations changed only data JSON and the press serializer, neither of which the superflat GameTest world uses. Both tests passed in runs 1, 5 and 7. They are worth recording as flaky in v1.5 and v1.7 code.

**C15aR1-I3: the sources-JAR hash in `artifacts.json` does not reproduce from a clean export.** Mine is `60f7a03c…` at 2,209,906 B; the evidence records `9d715998…` at 2,210,000 B. The main and API JARs reproduce byte for byte, and archive tasks are configured as reproducible, so the evidence run probably packed a working-copy difference, such as line endings or an untracked file. The packet does not list the JAR's entries, so the cause cannot be determined. Recommendation: record an entry/hash listing for each JAR in later packets.

**C15aR1-I4: art provenance.**
- All 19 textures are `CLEAR` against both vanilla clients (reproduced) and against the 356 legacy AR textures (my probe).
- `coil_side.png` scores rank 0.7488 against 1.12.2 `particle/footprint.png` with IoU 1.0, just below the `SUSPECT` threshold of 0.75. The press faces were redrawn after earlier `SUSPECT` results, and the grey palette was chosen to avoid vanilla greys. Both changes are documented, but both tune the art against the screen, and the palette change removes the exact-colour measures from play.
- The ADR-061 §4.5 human visual review should therefore record a comparison of `coil_side`, `coil_top`, `storage` and the three press faces.
- LibVulpes textures could not be screened.
- The code is not a transliteration of the legacy `BlockSmallPlatePress`. The legacy press takes the last matching recipe and extends a piston head.

**C15aR1-I5: the "unloaded neighbour" test is synthetic.** The press, its target and the anvil share one chunk column. A press that receives `neighborChanged` therefore always has a loaded target, and the `isLoaded` guard only fires for the anvil below the build floor. `anUnloadedTargetIsNeitherPressedNorLoaded` calls `press()` at an arbitrary far position instead. That is acceptable; this note only explains what the test proves.

**C15aR1-I6: the strict field set rejects Forge's `"conditions"` key.** `SmallPlatePressRecipe.java:133` rejects any key outside its field set, so data packs cannot use Forge's `"conditions"` in press recipes. Forge's `RecipeManager` leaves the key in the JSON it passes on. This follows the kernel recipe policy; record it in the data-pack documentation.

**C15aR1-I7: CHANGELOG wording.**
- "claim mods that stop pistons stop it too" holds only for mods that cancel `PistonEvent.Pre`. Because the press and its target share a chunk column, chunk-claim border checks never trigger. The protection is therefore exactly that of a vanilla piston, as ADR-063 §3 intends.
- "ores with deepslate variants … and raw items": dilithium has no raw item.
- Rolling machines in upgraded worlds that hold copper or gold ingots will start rolling them; `920308e` shows the effect on a fixture. The release notes should say so (ADR-061 §1.5).

## 3. Mutation results

| ID | Mutation | Result | Killed by |
|---|---|---|---|
| M1 | obsidian check removed | killed | `withoutobsidianorwithablockentitynothingispressed` |
| M2 | `PistonEvent.Pre` post removed | killed | `acancelledpistoneventleavestheblock` |
| M3 | `isLoaded` check removed | killed | `anunloadedtargetisneitherpressednorloaded` (by the result code) |
| M4 | rising-edge check removed | killed | `onlyarisingedgepresses` |
| M5 | press switch ignored | killed | `theserverswitchturnsthepressoff` |
| M6 | ambiguity refusal removed | **survived** | L1 |
| M7 | ore switch ignored by `SwitchPlacement` | killed | `overworldplacementsfollowtheserverswitch` |
| M8 | `rutile_ore` removed from item tag `forge:ores/titanium` | **survived** | L2 |
| M9 | `aluminum_ore` removed from item tag `forge:ores/aluminum` | **survived** | L2 |
| M10 | rutile vein size 6 → 12 | **survived** | L2 |
| M11 | aluminum minimum height −16 → −64 | **survived** | L2 |
| M12 | deepslate variant removed from block tag `forge:ores/dilithium` | **survived** | L2 |
| M13 | dilithium press output 2 → 3 | killed | `V180MaterialResourcesTest` (JUnit) and `apulsepressesanoreintotwodust` |
| M14 | press JSON 4,096-byte bound removed | **survived** | M3 |
| M15 | press field-set check removed | **survived** | M3 |
| M16 | `fromNetwork` ignores the count | **survived** | M3 |
| M17 | press `schema_version` check removed | **survived** | M3 |

Probes, not mutations:
- the dilithium XP probe failed as intended (M1 finding);
- the rolling tag probe recipe was rejected at start (M2 finding).

Both probes were removed, and the tree was verified equal to `f95e851a` before run 7.

## 4. Contract conformance (checked, no finding)

- **§1 material table:** 86 entries + the press = 87, matching `MaterialCatalogTest` and the ADR table, with the stated IDs and kinds.
- **Tags:** Forge and project tags as required:
  - rutile ore and deepslate rutile ore carry both `forge:ores/rutile` and `forge:ores/titanium`;
  - raw rutile carries only `forge:raw_materials/rutile`;
  - `forge:gems/dilithium` holds the dilithium crystal;
  - `advancedrocketrycommunity:coils` holds the five coils.
- **§2 recipes:**
  - smelting and blasting of ore, raw item and dust (rutile excluded; dilithium ore → dust);
  - the legacy crafting shapes for rods, gears, coils, the fan, nuggets and blocks, all on tags;
  - rolling at 300 ticks, 20 or 200 FE/t and 100 mB, item-based, with no iron ingot → plate;
  - the press: block → 4 plates, ore → 2 dust (see H1 for titanium).
- **§3 press:** rising edge stored in `POWERED`; no block entity; `PistonEvent.Pre` posted before acting; `removeBlock` with flag 3; the item spawned at the target; never loads a chunk; server-only (`isClientSide` guard); the switch works. I found no item-duplication path: the press removes the block without drops, refuses block entities and blocks with a negative destroy speed, and ore → 2 dust is the legacy doubling.
- **§4 worldgen:**
  - four placed features, each listing `server_switch` first;
  - counts and sizes 10×6, 6×6, 1×16 and 1×16;
  - uniform between −16 and 64, targeting `stone_ore_replaceables` and `deepslate_ore_replaceables`;
  - the biome modifier on `#minecraft:is_overworld` at `underground_ores`;
  - no iridium anywhere in the Overworld; the Moon's flat generator has `features:false`;
  - `SwitchPlacement` reads a ForgeConfigSpec value, which is safe off-thread, and its codec is flat and rejects unknown names.
- **§8:** `--output` points at the v1.8 root and v1.7 is added to `--existing`. The root is a resource directory. The v1.7 tool tags are excluded from `processResources` and `sourcesJar`. Exactly one copy per path reaches the JAR; only the three ADR-043/v0.3 duplicates remain across roots, and they were already excluded.
- **AGENTS.md §3:**
  - no client imports in common code;
  - `DeferredRegister` throughout;
  - bounded loops (the press is O(recipes) per rising edge);
  - bounded JSON and network sizes;
  - no new saved data;
  - `V180MaterialArt` has 510 lines, a single responsibility, and is recorded in the implementation log.
- **Ledger:**
  - 17 rows delivered (15 IMPLEMENTED, 2 REDESIGNED), all naming the batch's VERIFICATION.md.
  - `block:platepress` fits IMPLEMENTED: same mechanic, modern ID.
  - `config_file:SmallPlatePress.xml` and `worldgen:OreGenerator` fit REDESIGNED. The `OreGenerator` row's planned target was the Overworld features.
  - Moving 13 `config:WORLDGEN.*` rows and `CustomizableOreGen` to C15b is allowed by ADR-062 §7 (own commit) and is justified, because their Moon and Mars half is C15b work.

## 5. ADR-063 revision 4 — claim check

| Claim | Verdict |
|---|---|
| "The kernel recipe type resolves its ingredient when recipes load, before tags are bound, so a tag ingredient would refuse to load" | **True on a fresh start** (bytecode order verified; probe rejected). **Incomplete:** on `/reload` the recipe would load against the previous tags, and a loaded tag recipe can break client recipe sync on login (M2). It also contradicts ADR-061 §2.2, which the revision does not mention |
| "`BoundedItemIngredientCodec.resolveAlternatives` makes tag ingredients fail" | True: Forge's empty-tag `BARRIER` stack carries a custom name (NBT) and is rejected |
| "An iron ingot → plate recipe would stop both" (ambiguity with `rolling_iron_bars`) | True for an input of **2 or more ingots** (`RollingMachineRecipeResolver` 62-79; `rolling_iron_bars` takes `input_count` 2). A single ingot would still match only the plate recipe |
| Press tags "resolved when the press acts, after tags are bound" | True. In Forge 47.4.10, `Ingredient.getItems()` caches on first use with no invalidation check, so resolution happens at the first press after each load and holds until the next reload. That is coherent, because recipes and tags are replaced together |
| Ambiguous press matches do nothing | Implemented, but not tested (L1) |
| Vanilla replaceable tags, `overworld_<ore>_ore` names, `worldgen.overworldOres` with `server_switch` first | Implemented and sound |

## 6. Verdicts

**(a) C15a delivery: ACCEPT WITH REQUIRED CHANGES.**

Required:
- H1, as the owner decides it (recommended: no rutile → titanium dust);
- M1;
- M3;
- L1;
- M2's documentation part (state the ADR-061 §2.2 deviation) before revision 4 is accepted.

Recommended in the same round: L2 and L3. The Info items are for the record.

The build, DataGen determinism, JAR layout, ledger and evidence are reproducible and honest, apart from I3.

**(b) ADR-063 revision 4: ACCEPT WITH CHANGES.**

1. **§2 "Rolling ingredients name items":**
   - say that this gives up ADR-061 §2.2 tag interoperability for the rolling machine;
   - correct ADR-061 §2.2's "enforced … when the recipe is parsed";
   - bind an expiry: the C16a machine family resolves tags after binding (or the codec rejects `tag` entries until it does);
   - describe the `/reload` and login behaviour (M2).
2. **§2 "Iron plates":**
   - state the ambiguity condition precisely (two or more ingots);
   - state the cost: 9 ingots → 4 plates, versus the legacy 1 ingot → 1 plate. `plateIron` appears in nine legacy upstream files, and `sheetIron` (made from plates) in five;
   - name the alternative the owner rejected: re-keying the v1.2 project recipe `rolling_iron_bars`, which is not a legacy recipe, for example to iron plates or rods, would keep legacy iron rolling. ADR-061 §1.5 permits recipe changes with release notes.
   - Either choice is acceptable once it is disclosed.
3. **§3:** add the rutile decision (H1), and add the ambiguity refusal to the §9 A1 tests.
4. **§4 changes:** accept as written.
