# V180 C16d: `makeMaterialsForOtherMods` replaced by common tags, delivery evidence

Date: 2026-10-15. Written by W57 under
[TASK-W57](../v1.8.0-root-queue/TASK-W57-MATERIALS-FOR-OTHER-MODS.md) for the C16d ledger row
`config:CATEGORY_GENERAL.makeMaterialsForOtherMods`, which [VERIFICATION.md](VERIFICATION.md)
(W31) and `records/W56.md` (section 1) left PLANNED. Every commit of this repository named below
resolves with `git cat-file -e` and is an ancestor of the W57 branch head named in
`records/W57.md`; `c5cd5af` is the upstream Advanced Rocketry commit.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## The decision

On 2026-10-08 the owner chose "通用标签互操作，额外配方由数据包提供（推荐）"
([OWNER-DECISIONS-02](OWNER-DECISIONS-02.md) line 7). Lines 15 to 18 apply it: built-in recipes
accept common-tag members, data packs provide extra recipes for materials that only other mods
define, the legacy behaviour gap is disclosed, and no `makeMaterialsForOtherMods` boolean is
added. ADR-061 section 2.2 item 2 (lines 101 to 103) and ADR-064 section 2 (lines 165 to 191)
carry the mechanism. [CONTRACT-02](CONTRACT-02.md) section 2.4 (lines 140 to 150) proposes the
disposition REDESIGNED, with "the tag-based catalog of every kernel and classic recipe type" as
the evidence.

The legacy switch was on by default. It made the legacy machines produce plates, rods and other
products for materials that only other mods registered (CONTRACT-01 line 62, citing
`ARConfiguration.java:413` and `AdvancedRocketry.java:1244-1257` at upstream `c5cd5af`).

## Commits

| Commit | Content |
|---|---|
| `cb52c0b3` (2026-10-03, C15a) | The material set and its tags: common `forge:` tags on every product, this mod's tags for sheets, coils, fans and boules. Adds `V180MaterialResourcesTest` |
| `66996a1f` (2026-10-03, C15aR1-M2) | Made the rolling, precision and electrolyzer recipes refuse tag ingredients until C16a. The old CHANGELOG entry described this rule |
| `85a19757` (2026-10-04, C16a) | ADR-064 section 2: tag ingredients again, resolved after the tags load; a tag of more than 32 items disables only its recipe until the next reload. The v1.2 rolling and precision recipes move to tags. Adds `V180KernelRecipeResourcesTest` and `DeferredRecipeTagTest` |
| `7b1a87a1` (W57) | Corrects the v1.8.0 "Data packs:" CHANGELOG entry and adds the disclosure below |
| `9e8e2da0` (W57) | Adds `MachineRecipeMaterialTagsTest`, the whole-catalog test |

W56 read hosted run 38603920684 (v1.8 development regression) back through the GitHub REST API: it
passed at `cc36612c`, which contains `cb52c0b3`, `66996a1f` and `85a19757` (`records/W56.md`,
"Commands and results"). The two W57 commits have no hosted run yet; they are on a local branch
until Root merges them.

## Tags in and tags out

- **Every machine codec accepts tags.** All eleven machine recipe types decode ingredients through
  `BoundedItemIngredientCodec.decode`: `RollingMachineRecipe.java:332,367`,
  `PrecisionAssemblerRecipe.java:384,421`, `ElectrolyzerRecipe.java:287,324`,
  `SmallPlatePressRecipe.java:156,177` and, for the seven classic types,
  `ClassicMachineRecipe.java:145`, after `ClassicItemInput.java:23` validated the same form when
  the recipe loaded. An entry is exactly one `item`
  or one `tag`, an array holds 1 to 32 entries, and any other key (such as a Forge custom
  ingredient `type`) is refused (`BoundedItemIngredientCodec.java:135-167`). Unit tests accept a
  tag in each family: `RollingMachineRecipeTest` line 102, `PrecisionAssemblerRecipeTest` line
  200, `BoundedItemIngredientCodecTest` lines 41 to 59 (electrolyzer), `SmallPlatePressRecipeSerializerTest`
  line 40 and `ClassicRecipeRowsTest` line 20.
- **Resolution after the tags load.** The rolling, precision, electrolyzer and classic recipes
  resolve each ingredient to 1 to 32 items once the tags are bound, and disable only that recipe,
  with a logged error, until the next tag reload (`DeferredProcessDefinition.java:34-49`,
  `ClassicMachineManager.java:175-184`). `DeferredRecipeTagTest` (lines 28 to 50) covers a tag
  before binding, a 33-item tag and the recovery. The Small Plate Press tests its ingredient
  directly and has no 32-item bound on the resolved tag.
- **This mod's products carry the tags.** `V180MaterialResourcesTest` line 141 checks that every
  entry of the material table is in its product tag; lines 64 to 67 and 97 to 98 check that the
  rolling and press recipes take only tags. `V180KernelRecipeResourcesTest` (lines 30 to 75) checks
  that the v1.2 rolling and precision recipes use tags in the v1.8 copy, the processed resources
  and the runtime JAR.
- **No setting.** No COMMON, SERVER or CLIENT value is named `makeMaterialsForOtherMods`.

## The whole-catalog test

`src/test/java/io/github/sunthemoon/advancedrocketrycommunity/recipegraph/MachineRecipeMaterialTagsTest.java`
(commit `9e8e2da0`) reads the packaged resources (the processed `build/resources/main`, after the
build's superseded-file exclusions) without starting Minecraft beyond `MinecraftBootstrap`. Its six
tests:

| Test (line) | What it checks |
|---|---|
| `theCatalogReadsEveryPackagedRecipeOfEveryRegisteredMachineRecipeType` (84) | The recipe types come from the real registers that `ModRegistries` wires (`RegisteredItemReachabilityTest.registered`). Every packaged recipe in this mod's namespace has a registered type, and every registered type has at least one packaged recipe |
| `everyMachineRecipeNamesMaterialFormsOnlyThroughTheirTags` (93) | No ingredient entry names an item of a material form; each entry is exactly one `item` or one `tag` |
| `everyMachineRecipeTagNamesOneTo32Items` (101) | Each tag entry resolves, in the clean classpath, to 1 to 32 items |
| `theRuleCoversTheMaterialTableAndLeavesOtherInputsAlone` (113) | Every material table entry and the vanilla copper, iron and gold ingots, blocks, raw items and ores fall under the rule; `carbon_brick` and `thermite` do too; W56's other item inputs do not |
| `anItemPlantedInPlaceOfAnyFormTagIsFound` (138) | For every packaged form-tag entry, a copy with one of its items in its place fails; so do an item among tag alternatives and a custom ingredient entry |
| `noConfigurationValueReplacesTheLegacySwitch` (174) | No key at any depth of `CommonConfig.SPEC` (COMMON), `ForceFieldConfig.SPEC` (SERVER) or `ClientConfig.SPEC` (CLIENT) equals `makeMaterialsForOtherMods`, ignoring case, and no file under `src/main` contains the name |

**The rule.** A material form is a form tag of the material table: each tag folder of the
products (`ingots`, `nuggets`, `dusts`, `plates`, `rods`, `gears`, `gems`, `storage_blocks`, and
this mod's `sheets`, `coils`, `fans`, `boules`), plus `ores` and `raw_materials`, for each material
and ore name of `MaterialCatalog` and each name that this mod ships a tag file for in those
folders (this adds `forge:ingots/carbon` and `forge:dusts/thermite`). The members come from every
classpath copy of the tag: this mod's, Forge's and vanilla's, with nested tags expanded. That is
210 tags holding 106 items. An item ingredient that is one of them fails, and the message names the
tag to use. No recipe ID is exempted.

**Result on the packaged data (W57 branch).** 11 registered types, 82 recipes read, no violation:

| Type | Registered at | Recipes |
|---|---|---|
| `advancedrocketrycommunity:electrolyzing` | `ModRecipes.java:26` | 1 |
| `advancedrocketrycommunity:rolling` | `ModRecipes.java:37` | 18 |
| `advancedrocketrycommunity:precision_assembling` | `ModRecipes.java:48-49` | 8 |
| `advancedrocketrycommunity:lathe` | `ModRecipes.java:58` | 7 |
| `advancedrocketrycommunity:arc_furnace` | `ModRecipes.java:63` | 5 |
| `advancedrocketrycommunity:cutting` | `ModRecipes.java:64` | 14 |
| `advancedrocketrycommunity:crystallizing` | `ModRecipes.java:69` | 2 |
| `advancedrocketrycommunity:chemical_reacting` | `ModRecipes.java:72` | 7 |
| `advancedrocketrycommunity:laser_etching` | `ModRecipes.java:75` | 2 |
| `advancedrocketrycommunity:centrifuging` | `ModRecipes.java:78` | 1 |
| `advancedrocketrycommunity:small_plate_press` | `MaterialContent.java:71-72` | 17 |

The lathe registers directly; the other six classic types register through
`ModRecipes.classicType` (lines 81 to 85). The remaining item inputs are `minecraft:sand`,
`minecraft:charcoal`, `minecraft:redstone_block`, the vanilla logs and stems, `minecraft:bone`,
`minecraft:ender_eye` and this mod's components (`silicon_wafer`, `laser_lens`, `pipe_seal`,
`control_board`, `tracking_circuit`, the circuit plates, `empty_canister`, `lightwood_log`,
`super_high_pressure_tank` and `carbon_scrubber_cartridge`). None is a material form. The
largest tag entry is `forge:armors/helmets` with 7 items.

**Negative control.** With two planted item ingredients in the v1.8 generated recipes
(`minecraft:gold_ingot` for `forge:ingots/gold` in `precision_basic_circuit_plate.json`, and
`advancedrocketrycommunity:titanium_aluminide_sheet` for
`advancedrocketrycommunity:sheets/titanium_aluminide` in
`chemical_reacting_space_breathing_helmet.json`), the class ran 6 tests with 1 failure:
`everyMachineRecipeNamesMaterialFormsOnlyThroughTheirTags` listed exactly the two plants and the
tag to use for each. The plants were then removed with `git checkout`. The whole unit suite at
`9e8e2da0` passed afterwards: 3,260 tests in 574 classes, including this class's six, with no
failures, errors or skips.

## Disclosure

`CHANGELOG.md`, v1.8.0 section (commit `7b1a87a1`):

> The legacy `makeMaterialsForOtherMods` setting is gone, and no setting replaces it. Machine
> recipes take common `forge:` tags, so other mods' ingots, plates and dusts work wherever this mod
> uses the same material, and this mod's materials carry the same tags for other mods' recipes.
> The legacy setting, on by default, also made machine recipes for materials that only other mods
> define, such as plates of another mod's metal. Those recipes are no longer generated; a data pack
> can add them.

The same commit corrects the "Data packs:" entry, which still said that the rolling, precision and
electrolyzer recipes refuse tags (the rule of `66996a1f`).

## Delivered row

| Unit | Disposition | Delivery |
|---|---|---|
| `config:CATEGORY_GENERAL.makeMaterialsForOtherMods` | REDESIGNED | No configuration value. Every machine recipe names a material through its common `forge:` tag (or this mod's tag for sheets, coils, fans and boules), so other mods' members of the same tags work as inputs, and this mod's products carry those tags. Recipes for materials that only other mods define are not generated; data packs add them. The CHANGELOG discloses the gap |

## Open items

- **Hosted run.** None yet for `7b1a87a1` and `9e8e2da0`; the next v1.8 development regression after
  Root's merge covers them.
- **Silicon wafer selector.** The four circuit plate recipes name the item
  `advancedrocketrycommunity:silicon_wafer`. CONTRACT-02 section 2.1 rule 1 (lines 50 to 51) and
  lines 94 to 95 proposed the existing tag `advancedrocketrycommunity:silicon_wafers` (one member)
  for the legacy `waferSilicon`. The wafer is a component, not a material form, so the rule above
  does not cover it. No recipe was changed; Root decides.
- **S1, V1 and V2.** No dedicated-server, restart, real-GPU or two-client evidence was collected.
  The row adds no block, store or art.
