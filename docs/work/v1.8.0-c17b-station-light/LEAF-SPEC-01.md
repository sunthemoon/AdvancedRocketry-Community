# C17b ordinary station light: proposed finite leaf

Republication date: 2026-10-05. The original author proposal is 16,237 bytes,
SHA-256 `25f4bb7bfa5df3d01c350fea5fe57e643fe635debc1780d5d2ed92a440775950`.
This copy adds only this notice and rehomes one evidence link. Normative rules
are unchanged. The historical PROPOSED status below is not rewritten;
[TASK.md](TASK.md) records the later scoped Root acceptance and its limits.

Date: 2026-10-05. Status: PROPOSED; no source assignment or adoption. Fixed source: `33b690f001f5ecca88589d44582369a8d73f1785`; named documentation child: `ae7e2a3f9da29576aaf4a8471cf319c7a706ae0d`. Effective policy is the live Root AGENTS, not a historical checkout. Root remains the sole integrator and committer.

## 1. Basis and exact boundary

Accepted ADR-065 revision4:672-675 requires `station_light`, ordinary original-model light emission15, no owner state and no active tick, with explicit material/collision/loot data. ADR:130-143 permits a plain block without BE and requires permanent new IDs; 1.12.2 world loading is unsupported. The D2-A prerequisite at :1166-1171 concerns orbital implementation, not this constant no-state lamp. This proposal neither implements nor waives D2-A, typed propulsion, first-event outbox/hold or shared checked writers.

The independent eligibility input is [PROPOSAL-01.md](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17-lighting-leaf-20261005-f309be/PROPOSAL-01.md), 11,547 B, SHA256 `9c810df0d9848381f74e687a287d37c15aa5c240843b2fda49b7750a21901254`; its source observations are input evidence, not this proposal's acceptance. The current ledger keeps legacy `block:circleLight` PLANNED. Legacy technical `block:lightSource` remains REJECTED. The asset plan explicitly EXCLUDES `textures/blocks/stationlight.png`; no read/import/recolour/trace of it is permitted.

**All following material, recipe, language and art choices are NEW modern proposals, not accepted legacy numerical facts or a parity assertion.** Independent review and scoped Root adoption must precede source/art authoring.

## 2. Implementation-ready proposed behavior

| Field | Exact proposal |
|---|---|
| Block and matching item ID | `advancedrocketrycommunity:station_light` |
| Registry implementation | Ordinary `net.minecraft.world.level.block.Block` and matching ordinary `BlockItem`; DeferredRegister, no new subclass/service |
| State/collision | Default full solid opaque cube; no additional state properties, facing, waterlogging or on/off switch; no `noOcclusion`/`noCollission` option |
| Emission | Constant15 in every state; normal vanilla light-engine updates, not a mod tick callback or a forced publication delay |
| Properties | `MapColor.COLOR_LIGHT_GRAY`, `SoundType.METAL`, strength `3.0F, 6.0F`, `requiresCorrectToolForDrops()` |
| Tool | Add only to `minecraft:mineable/pickaxe`. No `needs_stone_tool`, `needs_iron_tool` or `needs_diamond_tool` addition: every vanilla pickaxe tier is adequate. Other tools/hands must not be promoted to correct tools |
| Other behavior | Ordinary Block defaults, including placement/piston behavior; no resource capability, interaction menu, redstone toggling, FE, custom fluid rule, owner or world/station query |
| BlockItem | Default64 stack maximum; no custom item NBT/durability/schema/capability |
| Scope of placement | Ordinary block placement wherever existing vanilla/Forge world and protection rules permit; the name does not add station-only ownership restrictions or bypass protection |
| Survival loot | Native correct-tool break yields one self item, no custom payload. Native creative breaking remains vanilla. No Silk Touch/Fortune bonus; loot uses one item entry and `minecraft:explosion_decay` |

The loot table is `minecraft:block`, one pool, `rolls:1.0`, `bonus_rolls:0.0`, one `minecraft:item` entry named `advancedrocketrycommunity:station_light`, and pool function `{"function":"minecraft:explosion_decay"}`. It does not copy state/BE data. Without explosion radius the native table produces one item; with an explosion radius ordinary decay may produce zero or one, never four or a guaranteed survivor. `requiresCorrectToolForDrops`/pickaxe behavior is a mining admission property, not a promise that invoking a loot table directly with an arbitrary tool simulates survival breaking.

No automatic airtight tag is added. Its full collision may be SEALED by the existing `ServerLevelVolumeWorldView` priority after explicit tags/API overrides; it does not override datapack/addon boundary policies or increase scan/tick limits. No rocket-movable, force-field, survey or other authority tag is changed by this leaf.

## 3. Exact acquisition and unlock proposal

Recipe ID `advancedrocketrycommunity:station_light`, type `minecraft:crafting_shaped`, category `building`, fixed full3x3 pattern:

```text
IGI
GLG
IGI
```

Keys are exact item IDs, not tags: `I=minecraft:iron_ingot`, `G=minecraft:glass`, `L=minecraft:glowstone`. One craft consumes four iron ingots, four glass **blocks**, and one glowstone **block**, yielding four new untagged station-light BlockItems. No circuit, station, Moon, Fluid/FE, research or hidden ingredient. Iron nuggets/plates, glass panes/tinted/stained glass and glowstone dust are not substitutes. Vanilla shaped matching supplies its normal horizontal mirror behavior; this pattern is symmetric. Smaller crafting grids, missing slots and any wrong ingredient refuse. Query/matching/assembly preserve inputs; actual crafting consumption follows native crafting handling. There is no custom crafting remainder.

Recipe-unlock advancement ID `advancedrocketrycommunity:recipes/building_blocks/station_light`, parent `minecraft:recipes/root`, no display object. `has_glowstone` uses `minecraft:inventory_changed` with one predicate selecting exact `minecraft:glowstone`, count min1. `has_the_recipe` uses `minecraft:recipe_unlocked` with this recipe ID. Requirements are exactly `[["has_glowstone","has_the_recipe"]]` (vanilla OR); rewards list only this recipe. No XP, items, function or tutorial/research authority. This is recipe-book discoverability, not an extra research gate or a replacement for the server's existing limited-crafting rules.

This acquisition is deliberately NEW and uses ordinarily available vanilla materials. No historical recipe body was used; no legacy parity/full C16d progression closure is asserted. A graph/resource check must establish the actual registered recipe and its inputs, not merely this prose.

## 4. Original resources and finite provider contract

Before drawing or implementation, Root records `docs/provenance/v1.8.0-c17b-station-light-new-resources.md` as NEW repository-authored MIT work. It binds the future provider, texture path, geometry/material/acquisition choices and reuse of the existing encoder, while explicitly retaining the old bitmap exclusion. Existing analyzer provenance is a procedural precedent, not lamp asset permission. No bitmap is generated in this proposal.

The author creates an original16x16 fully opaque RGBA grid: independently designed pale illuminated centre within a light-grey metal housing/border, with restrained contrast. It must not read/trace/sample/transform upstream, LibVulpes, Mojang/Minecraft/Forge or third-party art. The source grid/palette, final PNG SHA/bytes and visual originality review are bound during source/result review. The same new face texture is used on all six cube faces. No external image API/import, shader, emissive-layer metadata or sound asset is introduced.

`V180StationLightData` exposes literal ID/TEXTURE constants, `clientFiles()`, `serverFiles()`, fresh `grid()`, and fresh `texture()` results; the constructor is `(PackOutput, boolean client, boolean server)`, implementing DataProvider. Reuse `V180MaterialArt.png(grid,tint)` without editing that utility or reusing its existing art templates. JSON values must be freshly allocated per call; caller edits cannot change later output. No mutable world data/static cache. Client/server toggles write only their respective files; future Root provider registration uses their OR. Use the existing `DataProvider.saveStable` and `CachedOutput.writeIfNeeded` conventions. Output files are literal bounded paths, not caller-controlled paths.

Seven NEW generated paths, all relative to `src/generated/v1.8/resources/`:

1. `assets/advancedrocketrycommunity/blockstates/station_light.json`: exactly one empty variant pointing to `advancedrocketrycommunity:block/station_light`.
2. `assets/advancedrocketrycommunity/models/block/station_light.json`: parent `minecraft:block/cube_all`, texture `all=advancedrocketrycommunity:block/station_light`; reference vanilla template ID only, no model/bitmap copy or custom loader.
3. `assets/advancedrocketrycommunity/models/item/station_light.json`: parent `advancedrocketrycommunity:block/station_light`.
4. `assets/advancedrocketrycommunity/textures/block/station_light.png`: new16x16 RGBA PNG, <=4,096 bytes, fixed existing encoder, no animation/ancillary metadata.
5. `data/advancedrocketrycommunity/loot_tables/blocks/station_light.json`.
6. `data/advancedrocketrycommunity/recipes/station_light.json`.
7. `data/advancedrocketrycommunity/advancements/recipes/building_blocks/station_light.json`.

Each JSON <=16,384 UTF-8 bytes, each contains only its stated content (not a new runtime NBT limit). `V180StationLightLanguage.translations(boolean chinese)` returns exactly one immutable name mapping: `block.advancedrocketrycommunity.station_light` = `Station Light` / `空间站灯`. Root's existing language provider writes it into existing `assets/advancedrocketrycommunity_v180/lang/en_us.json` and `zh_cn.json`; the leaf must not write competing language files.

Root adds the one pickaxe entry through existing `V180MaterialData.Blocks` into `data/minecraft/tags/blocks/mineable/pickaxe.json`. Preserve every old tag member/language value, change no tier tag, and leave all other generated paths byte-exact. Do not add the loot again to `MaterialLoot`: this leaf's provider owns its one loot path. Exact seven new outputs + three shared generated modifications must be checked after actual DataGen.

## 5. Exclusive prospective write scope

Worker only four NEW source paths:

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180StationLightData.java`
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180StationLightLanguage.java`
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180StationLightDataTest.java`
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/StationLightGameTests.java`

Plus its own `docs/work/v1.8.0-c17b-station-light/PROGRESS-01.md` and `HANDOFF-01.md` if separately granted. No extra runtime/art class is needed: original grid/texture helpers live in the single small data provider. If that becomes a mixed >500-line class, propose a scope adjustment before a fifth source file, not a broad framework.

Root owns existing `registry/ModBlocks.java` (one `STATION_LIGHT` plain Block with exact properties), `ModItems.java` (one matching BlockItem), `ModCreativeTabs.java` (one MAIN tab entry), `datagen/BootstrapDataGenerators.java` (one provider registration), `V180LanguageProvider.java` (one translation delegate), `V180MaterialData.java` (one pickaxe-tag entry), generated outputs, pre-authoring provenance/asset records, task/disposition, ledger/status and Git. No new BE/menu/channel/config/common-setup registration or client-screen file.

No existing source file is worker-writable. The future implementation task must bind a clean same-version worktree/base, actual central proposals and exact pre/postimages. Root commits/source-publishes exact reviewed files, then runs the fixed-commit regression before recording delivery. Development snapshots/tests remain development evidence.

## 6. Evidence-based verification obligations

**JUnit:** exact IDs/seven paths/client-server partitions/model references; exact recipe keys/pattern/count/category, unlock OR/reward structure and exact loot function; fresh JSON/grid/texture and immutable language maps; PNG signature16x16/8-bit RGBA/CRC/filter/allowed chunks/fully opaque pixels/size, same-source/runtime byte determinism and no existing-template/bitmap input. Assertions derive from this contract, not only aggregate test counts. No test-created registry items after freeze.

**GameTests:** resolve the literal ID through actual registries and refuse absence/AIR fallback (no synthetic item or dependency on a nonexistent central field); check registered Block/BlockItem identity and defaults, property/table/tool-tag values, zero custom state/BE/random ticking; RecipeManager lookup/matching/assembly of four untagged items and unchanged inputs, wrong/missing9-slot inputs, pane/dust/metal negatives and native remainders; actual loaded placement/collision/emission and bounded block-light publication; actual native loot with no explosion radius and radius-sensitive <=one output; existing boundary priority with full-cube/default/AIR/unloaded controls and no new tickets. Restore owned blocks/fixtures in every terminal path; no dimension/owner/vent/global-registry mutation or expanded budgets. Use `@GameTestHolder`/`@PrefixGameTestTemplate(false)`, existing `empty` or `atmosphere_test` templates. Async propagation waits only within a fixed40-tick test deadline, not assumed two ticks or widened on failure. Radius-sensitive random results must use a reproducible native seed/control, not claim universal explosion survival from a finite sample.

Recipe-book listener may be tested with the existing connected non-FakePlayer fixture if it exercises inventory acquisition rather than direct award/CriteriaTriggers invocation. That helper always reports creative; it cannot establish survival break/consumption by merely changing game-mode fields. Native loot/tool predicates likewise are not an actual survival-player break. Keep that distinction and either use a separately verified suitable local fixture or leave actual survival interaction to the dedicated/client acceptance checklist. Connected mocks are not V1/V2 or packaged restart proof.

**Root regression after explicit capacity/execution permission:** targeted named JUnit, no-cache clean build/test, runData twice with exact generated delta/repeat equality, unfiltered GameTest, strict source/provenance/resource/recipe/ledger and JAR member/API checks. Pin actual commands/exit/XML/GT/JAR/runtime and failures; do not reuse a hosted job predating the leaf as its result. Heavy local jobs remain prohibited while relevant free space <10GB; an eligible hosted batch may supply only the scopes actually executed. No Java or art generation is authorized by this proposal.

**Player/native/client:** a later separately reviewed ordinary-profile test and clean stop/restart verify placed block/item identity/emission with no BE data, survival pickaxe break/self-drop and recipe acquisition. V1 on real GPU: original model/texture, inventory item, light/occlusion, EN/zh names/resource reload; V2 where multiplayer behavior is claimed. Native/server/client checks are unrun. Full v1.8 G0-G9/preceding gates and ledger delivery are separate human/Root decisions, not closed by one lamp.

## 7. Non-goals, rollback and remaining choices

No orbital controls, station owner/migration, force field, projector, pad, concrete duplicate, propulsion/resource/coordinator/first-event schemas, equipment, FE, tick service, runtime protection override, API/protocol expansion, custom lighting engine or world scan. No raw data guard/refusal, new risk acceptance or blanket save behavior. Full ordinary light does not require a private resource writer.

The proposed material/recipe/art/name/tool-tier values need independent review and scoped Root acceptance. Nothing here selects a previously pending propulsion/first-event/equipment owner branch. A reviewer identifying a real public/product-semantic conflict must report it; conditional authority is not an ADR waiver. Final pixel grid is an originality/source-review item after provenance, not an unresolved runtime authority API.

IDs remain registered after delivery; no old `circleLight`/`lightSource` ID or MissingMappings shim. Reverting an unadmitted candidate is a code rollback; once worlds contain the new block, removing its ID is not a safe downgrade. Use a pre-upgrade world backup/normal controlled release rollback, not an invented down-converter or deleting player blocks. Root alone may update the PLANNED ledger row after qualified delivery; excluded legacy art remains excluded.
