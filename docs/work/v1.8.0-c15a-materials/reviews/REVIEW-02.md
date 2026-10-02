# C15a independent implementation review, round 2 (C15aR2, confirmation)

- **Reviewed commit:** `1a9f956e` on `codex/v1.8.0-classic-content`.
- **Commits since round 1 (`f95e851a`):** `9cdd9e0` (H1), `16e7a64` (M1), `66996a1` (M2), `f5073dc` (M3), `86bcb14` (L1), `5ae4d5c` (L2), `3a669fa` (I1), `87ef9ff` (I6, I7), `a89f700` (revision 4 iron paragraph) and `1a9f956` (dispositions, archived REVIEW-01, re-run packet). `git diff --stat a89f700 1a9f956e` touches only `docs/`, so the packet's test run on `a89f700` covers all the code in `1a9f956e`.
- **Method:** the repository was only read (`git archive`, `show`, `diff`, `log`, `ls-tree`, `rev-parse`). I exported `1a9f956e` to `r2/tree` and did all work in this directory.
- **Working copy during the review:** the repository moved to HEAD `9a8b0e0` ("propose ADR-063 revision 5 for the C15b surfaces"), and uncommitted C15b changes appeared, as the coordinator announced. None of that is part of this review.
- **Not checked:** a client, a start with JEI installed, a dedicated server, `/reload` at runtime, and CI.

## 1. Commands run and results

Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8 `--offline`, Python 3.13. All runs used `r2/tree` and one Gradle build at a time.

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `./gradlew --offline clean build runData runGameTestServer` (`r2/gradle-full.log`) | 0 | 5 min 22 s. **All 353 required GameTests passed**. `:test` was executed: **1,404 JUnit tests in 262 suites, 0 failures/errors/skips**. 18 `ERROR` lines, the same as the packet. No `Parsing error loading recipe` |
| 2 | `src/generated/**` hash list taken before and after run 1, excluding `.cache` (`r2/generated-*.sha256`) | – | 895 files, **identical** (one fewer than round 1: `pressing_titanium_dust.json` is gone) |
| 3 | `sha256sum build/libs/*.jar` | – | main `fcc5a878…`, API `252463ff…`, sources `a5581ebe…`: **all three equal `artifacts.json`** |
| 4 | `r2/probes/entries_check.py` against the packet's `artifact-entries.json` | 0 | API 48/48, sources 2,202/2,202, main 2,777/2,777 entries: same names, same order, same sizes and CRC-32 values |
| 5 | `probes/jar_check.py` (main JAR) | 0 | 0 duplicates. All 500 v1.8 generated files are packaged byte-identical. Each of the three tool tags appears once and equals its v1.8 copy |
| 6 | `python -B scripts/validate_v180_content_ledger.py`, and the same with `--require-accepted` | 0, 0 | `PASS` with 653 units: IMPLEMENTED 67, REDESIGNED 135, PLANNED 275 |
| 7 | `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_screen_generated_art` | 0 | `Ran 60 tests in 139.0s — OK` |
| 8 | `python -B scripts/check_client_imports.py` | 0 | `[PASS]` |
| 9 | `sha256sum -c SHA256SUMS.txt` in the packet | 0 | 10/10 OK |
| 10 | `r2/probes/check_identity.py` (source identity against `1a9f956e`) | 0 | 557 entries. Missing 0, hash differences 0, working copy ≠ committed 0. Not listed: only the packet's own 12 files |
| 11 | `r2/probes/check_review_archive.py` | 0 | `independent-review-files.json` lists 27 files. All 27 equal my round-1 files byte for byte, in the listing and in `independent-review.zip`. `reviews/REVIEW-01.md` equals my `REVIEW.md` (SHA-256 `f0e6b202…`) |
| 12 | 14 JUnit mutation runs, one mutant each: `./gradlew --offline test --tests <filter>` (`r2/probes/run_junit_mutants.sh`; logs and `summary.txt` in `r2/mutants-junit/`) | see §3 | 12 killed, 2 survived |
| 13 | G1: probes + N7, `./gradlew --offline runGameTestServer` (`r2/gradle-G1-N7.log`) | 1 | All three tag probe recipes were rejected at start, for the right reason. N7 crashed the server through the `@AfterBatch` guard (§3) |
| 14 | G2: probes + M6 + N5 (`r2/gradle-G2.log`) | 1 | 3 required tests failed: M6 (1) and N5 (2). Both probes passed |
| 15 | G3: probes + N4 + N8 (`r2/gradle-G3.log`) | 1 | 3 required tests failed: N8 (1) and N4 (the delivered test plus my probe) |
| 16 | Restore the tree; `probes/tree_vs_git.py` | 0 | `src/`, `tests/` and `tools/` equal `1a9f956e` again. The only differences are 35 old `docs/work/v1.0–v1.3` and `scripts/*.sh` files whose line endings `git archive` rewrites; they predate C15a |

### Probes

- **Rolling tag recipe** (`review_probe_rolling_tag`). Round 1 failed it with "cannot be empty or carry NBT". Now:
  ```
  Invalid Rolling Machine recipe …: machine recipes name items; tag ingredients are not supported yet
  ```
  I added two more data-pack probes, and both are refused the same way:
  - `review_probe_precision_tag`: `Caused by: java.lang.IllegalArgumentException: machine recipes name items; tag ingredients are not supported yet`
  - `review_probe_electrolyzer_tag`: `Invalid Electrolyzer recipe …: machine recipes name items; tag ingredients are not supported yet`

  A probe GameTest confirms that the shipped `electrolyzer_water` still loads as an `ElectrolyzerRecipe` and that none of the tag probes loaded. The 353-test clean run also passes the electrolyzer kernel GameTests, which use `electrolyzer_water`.
- **Dilithium XP.** The round-1 probe now passes:
  ```
  REVIEW-PROBE dilithium_ore drops=[1 dilithium_ore] dropsItself=true xpOver20Breaks=0
  ```
  The deepslate variant is the same. With the old `DropExperienceBlock` restored (N4), the probe measured 67 XP again.

## 2. Round-1 findings: status

| Round 1 | Status | Verified by |
|---|---|---|
| **H1:** rutile → titanium dust → furnace | **Resolved** | `V180MaterialRecipes` has no titanium ore → dust recipe, and 17 press recipes remain. `aPulsePressesAnOreIntoTwoDust` asserts `NO_RECIPE` and that the rutile stays, and `everyPressInputMatchesOneRecipe` asserts that no recipe takes rutile. Re-adding the old recipe (N5) fails both GameTests. ADR-063 revision 4 §3 records the decision, and the CHANGELOG says "not rutile". Note C15aR2-I2 |
| **M1:** dilithium XP farm | **Resolved** | Plain `Block` for every ore; `dilithiumOreDropsItselfWithoutExperience`; probe at 0 XP; N4 killed |
| **M2:** kernel recipes vs tags | **Resolved** | All three kernel serializers refuse tag entries in `fromJson` and `fromNetwork`. The three start-time probes are refused with the right message. ADR-061 revision 7 and ADR-063 revision 4 state the deviation, its consequences and its expiry. Two remaining test gaps: C15aR2-L1 |
| **M3:** press serializer untested | **Resolved** | M14, M15, M16 and M17 are all killed by `SmallPlatePressRecipeSerializerTest`. M16 is killed twice, by the round trip and by the malformed-network test |
| **L1:** ambiguity refusal untested | **Resolved** | M6 is killed by the JUnit `selectionUsesOneMatchAndRefusesTwo` and by the GameTest `anAmbiguousBlockIsRefusedAndStays`. N8 (`press()` ignores `ambiguous()`) is killed by the GameTest. N7 (restore removed) is caught by the `@AfterBatch` guard, see §3 |
| **L2:** ore tags and worldgen numbers unpinned | **Resolved** | M8, M9 and M12 are killed by `everyOreCarriesItsOreTagsAsBlockAndItem`, M10 and M11 by `overworldVeinNumbersAndTargetsAreTheContract`. Every ore block other than rutile now cooks |
| **L3:** JEI items of §9 | **Partly** (accepted as an open, tracked item) | The VERIFICATION row is reworded, and the ledger row's basis is stated. A client start with JEI is listed as an open C15 item in the implementation log. It has not been run (the JEI runtime JAR is not in the offline cache) |
| **I1:** duplicate umbrella entries | **Resolved** | `forge:ores` lists six tags, each once. N6 (a duplicate re-added) is killed |
| **I2:** flaky tests outside C15a | **Recorded** | Implementation log. Both tests passed in run 1 |
| **I3:** sources JAR did not reproduce | **Resolved** | My clean export now gives `a5581ebe…`, and all 2,202 entries are identical to `artifact-entries.json`. The stated cause, an empty untracked `src/main/java/example/` directory adding a directory entry, fits the 94-byte difference seen in round 1 |
| **I4:** art tuned against the screen; `coil_side` near the threshold | **Recorded** | The ADR-061 §4.5 visual review is listed as an open item |
| **I5:** synthetic unloaded-neighbour test | **Acknowledged** | VERIFICATION says so |
| **I6:** press refuses Forge `conditions` | **Documented** | CHANGELOG data-pack note |
| **I7:** CHANGELOG wording | **Resolved** | Piston protection and the dilithium raw item are reworded; the upgraded-world note is added |
| Revision 4 (iron paragraph) | **Resolved** | Condition (two or more ingots), cost (9 → 4; nine legacy recipes use iron plates and five use iron sheets, which I checked against the upstream recipe files) and the rejected alternative are now stated |

## 3. Mutation results, round 2

| ID | Mutant | Result | Killed by |
|---|---|---|---|
| M6 | `select()` uses the first match when two or more match | killed | `SmallPlatePressRecipeTest.selectionUsesOneMatchAndRefusesTwo` (JUnit); `anambiguousblockisrefusedandstays` (G2) |
| M8 | `rutile_ore` removed from item tag `forge:ores/titanium` | killed | `V180MaterialResourcesTest.everyOreCarriesItsOreTagsAsBlockAndItem` |
| M9 | `aluminum_ore` removed from item tag `forge:ores/aluminum` | killed | same |
| M10 | rutile vein size 6 → 12 | killed | `overworldVeinNumbersAndTargetsAreTheContract` |
| M11 | aluminum minimum height −16 → −64 | killed | same |
| M12 | deepslate variant removed from block tag `forge:ores/dilithium` | killed | `everyOreCarriesItsOreTagsAsBlockAndItem` |
| M14 | press 4,096-byte JSON bound removed | killed | `SmallPlatePressRecipeSerializerTest.fieldsSchemaAndSizeAreBounded` |
| M15 | press field-set check removed | killed | same |
| M16 | `fromNetwork` ignores the count | killed | `theNetworkRoundTripKeepsTheIngredientAndTheCount`, `malformedNetworkDataIsRejected` |
| M17 | press `schema_version` check removed | killed | `fieldsSchemaAndSizeAreBounded` |
| N1 | `requireItemsOnly` accepts tag entries | killed | `BoundedItemIngredientCodecTest` (both tests), `RollingMachineRecipeTest`, `PrecisionAssemblerRecipeTest` |
| N2 | Electrolyzer `fromNetwork` without `requireItemsOnly` | **survived** | C15aR2-L1 |
| N3 | Precision Assembler `fromNetwork` uses `decode` instead of `decodeItemsOnly` | **survived** | C15aR2-L1 |
| N4 | dilithium ore back to `DropExperienceBlock(2..5)` | killed | `dilithiumoredropsitselfwithoutexperience` (and my probe) |
| N5 | `pressing_titanium_dust.json` re-added | killed | `apulsepressesanoreintotwodust` ("Rutile pressed into dust"), `everypressinputmatchesonerecipe` ("Expected 17 … found 18") |
| N6 | duplicate `#forge:ores/tin` in the item umbrella | killed | `everyOreCarriesItsOreTagsAsBlockAndItem` |
| N7 | the ambiguity GameTest does not restore the recipe set | caught | the `@AfterBatch` guard throws "The overlapping probe recipe was not removed". The GameTest server crashes (crash report) and `runGameTestServer` fails |
| N8 | `press()` ignores `selection.ambiguous()` | killed | `anambiguousblockisrefusedandstays` |

## 4. New code: checks for new defects

- **`BoundedItemIngredientCodec.decodeItemsOnly` / `requireItemsOnly`.** Both run the existing bounded `validate`: at most 4,096 characters, 1–32 entries, each entry exactly `{item}` or `{tag}`. They then refuse every entry without `item`. `decode` is unchanged, and the small plate press still uses it, so press recipes keep their tags.
- **Use in the three kernel serializers.** Rolling and Precision call `decodeItemsOnly` in `fromJson` and `fromNetwork`. The Electrolyzer calls `requireItemsOnly` before vanilla `Ingredient.fromJson` in both. No shipped kernel recipe uses a tag (`electrolyzer_water`, both `precision_*` recipes and all rolling recipes name items), so nothing shipped changes.
  - Side effect on the Electrolyzer: it now also enforces the bounded shape. That means exactly one key per entry, at most 32 entries, and no Forge custom ingredient types. Before, it took any vanilla or Forge ingredient form. This only affects third-party data packs, and they get a logged load error (C15aR2-I4).
- **`SmallPlatePressRecipe.select`.** It reuses `matching()`, which stops at two. It returns ambiguous for two or more matches, the single recipe for one, and empty for none. `press()` checks `ambiguous()` before `NO_RECIPE`. Order and semantics are correct.
- **The ambiguity GameTest's recipe swap.**
  - `replaceRecipes(overlapping)` and `replaceRecipes(original)` run in one synchronous call on the server thread, with the restore in `finally`. No tick and no other test can see the extra recipe, and the test has its own batch.
  - `original` holds the same recipe objects, so the machines' recipe-signature checks are unaffected.
  - `replaceRecipes` also clears `RecipeManager.hasErrors`. This only matters for a server whose recipes failed to load; I found no consumer that matters on the GameTest server.
  - The `@AfterBatch` guard works. When it fires, it crashes the GameTest server rather than failing one test, but the build still fails loudly (N7).
- **Dilithium:** every ore is now a plain `Block` with no mining XP; smelting still gives XP. **Rutile:** excluded from both ore → dust and smelting, consistently.
- I found **no new defect** in this code.

## 5. Findings, round 2

### Low

**C15aR2-L1: the network-side tag refusal is tested only for the Rolling Machine.**

*Files:* `ElectrolyzerRecipe.java:284-288`; `PrecisionAssemblerRecipe.java:360-363`.

*Evidence:* mutants N2 (the Electrolyzer's `fromNetwork` check removed) and N3 (Precision `fromNetwork` back to `decode`) both survived their recipe tests and `BoundedItemIngredientCodecTest`. Only `RollingMachineRecipeTest.tagIngredientsAreRejectedFromJsonAndFromTheNetwork` swaps a tag into a wire buffer. The VERIFICATION row says "tag and mixed ingredients are refused, also from the network" for all three recipe types.

*Impact:* low. A server of the same version refuses tags at load and never sends them. A client that does receive one would fail on the barrier stack in any case. Only the message and the "all three cases behave the same" claim are unprotected.

*Fix:* add the rolling wire-swap test to `PrecisionAssemblerRecipeTest` and to an Electrolyzer recipe test, or narrow the VERIFICATION wording.

### Info

**C15aR2-I1: a design note for ADR-061 revision 7's C16a expiry.** The rolling and precision recipe signatures are built from the *resolved* alternatives (`RollingMachineRecipe.java:217-222`, `PrecisionAssemblerRecipe.java:89-134`). Running processes store that signature and compare it after a reload (`RollingMachineProcessController.java:332`, `RollingMachineBlockEntity.java:211`). If C16a resolves tags lazily, a change in a tag's contents (a mod or data pack added) would change the signature, and in-progress processes would turn `INVALID_RECIPE`. Revision 7's C16a condition should also require that the signature come from the recipe's JSON form (or define what happens to an active process when its tag changes).

**C15aR2-I2: several dusts have no source.** After H1, the titanium, steel, silicon, titanium aluminide and titanium iridium dusts have no recipe in C15a that makes them. The furnace recipes still accept them. This matches the legacy game standalone, where they came only from other mods. The C16d recipe graph ("every registered item obtainable") will need a source or an exemption entry for each.

**C15aR2-I3: ADR-061's review history lists revision 7 before revision 6.** This is cosmetic.

**C15aR2-I4: the Electrolyzer now refuses more data-pack shapes than tags.** The items-only check also applies the bounded shape: one key per entry, at most 32 entries, no Forge custom ingredient types. The CHANGELOG data-pack note mentions only tags. A third-party electrolyzer data pack in a non-canonical form would now fail at load (logged). No shipped recipe is affected.

## 6. Evidence packet

- **Checksums and identity:** `SHA256SUMS.txt` verifies (10/10), and `source-identity.json` matches `1a9f956e` exactly (557 entries).
- **Test runs:**
  - `run-commit.txt` is `a89f7003`, and only documentation changed after it.
  - `gametest-summary.json` records 353 passed, with no failures. `junit-summary.json` records 262 suites and 1,404 tests, 0 failures. `root-gradle-01.log` ends "All 353 required tests passed" and "BUILD SUCCESSFUL in 4m 51s", with 18 ERROR lines. `root-gradle-02.exit` is 0.
  - `generated-status.txt` and `post-gradle-diff.txt` are empty. `pre-run-status.txt` lists only the two documentation paths that were then committed in `1a9f956`, plus the untracked development-docs folder, which I did not read.
- **JARs:** `jar-resource-check.txt` matches my JAR. `artifacts.json` and `artifact-entries.json` match my clean build exactly.
- **Python:** the Python exit files are as stated, with `--closure` at 1 as expected.
- **VERIFICATION claims:** every claim matches the archive, except the network wording in C15aR2-L1.
- **Review archive:** the archived REVIEW-01 and the independent-review files are unmodified copies of mine.

## 7. Verdicts

**(a) C15a delivery: ACCEPT.** Every round-1 finding that required a change is resolved and verified by re-run mutants and probes. L3 and I4 remain as tracked open C15 items, which is acceptable for this slice. C15aR2-L1 is recommended and does not block.

**(b) ADR-063 revision 4: ACCEPT AS WRITTEN.** The rolling-items paragraph, the iron condition, cost and rejected alternative, the rutile decision, the ambiguity rule with its §9 test, and the §4 changes are accurate against Forge 1.20.1-47.4.10 and against the code. The iron-plate route (the press, or re-keying `rolling_iron_bars`) remains the owner's choice; either is acceptable as disclosed.

**(c) ADR-061 revision 7: ACCEPT AS WRITTEN.** Its facts are correct and were checked by `javap` (recipes are parsed before tags are bound on a fresh start and on `/reload`; recipes are sent before tags on login) and by the start-time probes. The deviation, the expiry with C16a and the restoration duty are stated, and the codec enforces them for all three kernel recipe types. Two optional edits: the C16a signature condition (I1) and the history order (I3).
