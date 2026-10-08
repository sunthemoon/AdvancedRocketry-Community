# CL18A-THERMITE-CONTRACT-01 draft (author return, unreviewed)

Author: delegated Claude worker for TASK-01. Draft only: no recipe, switch or
dependency choice is adopted by this text. Labels: **[F]** source fact read at
this worktree (base 4ac74585); **[P]** Root/ADR proposal or author proposal;
**[A]** unchecked platform assumption; **[B]** blocker before runtime.

## 1. Scope and exclusions

In: stable `thermite` item and common dust tag; ordinary `thermite_torch`
standing/wall pair; survival acquisition, recipes and unlock; placement,
support, orientation, drops; light 14 in vacuum; lifecycle; switch, reload and
existing-world behavior; NEW resource/provenance plan; file ownership; tests.

Out (stay PLANNED under CL18A-TORCHES): unlit torches/pairs/root/queues,
`combustion_*` tags, fire interception, `addtorch`, `torchBlocks`,
`dropExtinguishedTorches`, the placement event, ADR-054 adapter, pipe seals,
heat, machines, save veto, R-021, equipment, legacy asset import. No PNG now.

## 2. Stable identities

| ID (`advancedrocketrycommunity:`) | Kind | Basis |
|---|---|---|
| `thermite` | plain Item, no NBT | [F] ADR-066:208; ledger 496 |
| `thermite_torch` | standing block + its BlockItem | [F] ADR-066:208; ledger 144 |
| `thermite_wall_torch` | wall block, no own item | [P] vanilla torch pairing |
| item tag `forge:dusts/thermite` | sole member `thermite` | [P] from ledger 569 `dustThermite` |

[F] Non-project products use the `forge` namespace (MaterialTags:31-38,
MaterialCatalog:24). [P] Thermite is not added to `MaterialCatalog.Material`;
no new material family, serializer or Product. Whether `thermite` also joins
the `forge:dusts` umbrella tag is **[B]** Root's choice, because that umbrella is
emitted by the shared `V180MaterialData.Items` provider.

## 3. Acquisition, recipes and unlock

[F] Aluminum: `DUST`, own ore `STONE_AND_DEEPSLATE` (MaterialCatalog:82-83).
Iron: `DUST`, `OreKind.VANILLA` (MaterialCatalog:103-104). The small plate press
turns a tagged ore into two dust (`pressing_<id>_dust`, V180MaterialRecipes:
189-196); its shaped recipe is piston over three iron ingots, unlocked by a
piston (V180MaterialRecipes:53-58). The press acts on a rising redstone edge and
does nothing while `classic.smallPlatePress` is false (CommonConfig:253-256).
[A] `ALUMINUM.hasOwnOre()` is true; Root confirms in source or by recipe output.
No machine recipe is invented here.

[P] Root proposal under review (ADR-066:209-210), both shapeless:
- `thermite`: `MaterialTags.item(ALUMINUM, DUST)` + `MaterialTags.item(IRON,
  DUST)` -> 1 `thermite`; category MISC; unlock when holding either dust tag.
- `thermite_torch`: `minecraft:stick` + exact item `thermite` -> 4
  `thermite_torch`; category DECORATIONS; unlock when holding `thermite`.
Author recommendation: exact item (not the tag) in the torch recipe, so foreign
`dusts/thermite` items do not silently enter this graph; the tag is export only.
Survival route: ores -> press (redstone pulse) -> 2+2 dust -> 2 thermite -> 8
torches. **[B]** With `classic.smallPlatePress=false` no dust source was found
in the read files; Root must state whether another dust route exists or accept
that thermite becomes unobtainable while the press is disabled.

## 4. Block behavior

[A] Vanilla 1.20.1 `TorchBlock`/`WallTorchBlock` semantics, used through two
small subclasses so constructor visibility is not assumed:
- Standing: needs center support below; wall: needs a sturdy horizontal face;
  `facing` (4 horizontal values) is the only state; ceiling placement refused.
- One `StandingAndWallBlockItem` places the variant for the clicked face.
- `noCollission`, instabreak, wood sound, `lightLevel` constant **14** for both
  (ADR-066:210), independent of atmosphere, dimension or switches.
- Support loss pops the block and drops one `thermite_torch`; wall loot
  mirrors standing (`dropsLike`). Explosion uses `survives_explosion`.
  Creative break drops nothing.
- Client `animateTick` may emit `minecraft:flame`/`smoke` by registry
  reference only; no heat damage, fire spread, ignition or world explosion.

## 5. Vacuum light and lifecycle

No BlockEntity, SavedData, random/scheduled tick, network packet, C2S intent,
static collection or atmosphere query. Light is the vanilla light engine on
place/break. Vacuum compatibility here means the block's light never reads
atmosphere state. Exclusion from the future `combustion_torches` tag is that
leaf's test (ADR-066:230).

## 6. Switch, reload and existing worlds

[F] `lifeSupport.classicDevicesEnabled` gates classic instruments and retains
items/recipes (CommonConfig:318-320; ADR-066:110-114). [P] No new switch; the
torch is passive and does no work/debit/output, so the existing switch does not
affect placement, light or recipes. Root must accept this explicitly; ADR-066
says no scope is deferred silently by a switch. `/reload` only reloads recipe,
tag and advancement JSON; placed blocks are unaffected. Existing worlds gain the
IDs with no migration or scan; later removal needs a Missing Mapping plan
(V1.8.0 version doc section 8). No schema version applies: nothing persists
beyond BlockState and a plain ItemStack.

## 7. NEW resources and provenance

[F] Asset plan rows 226 and 229 currently say IMPORT for legacy
`thermitetorch.png`/`thermite.png`. **[B]** This leaf proposes NEW art instead;
changing that disposition needs the ADR-062 revision/pin of ADR-066:866-870
before runtime. Pattern [F]: station light declares NEW art before pixel
authoring, owns a 16 x 16 grid in its DataGen class, encodes with the unchanged
`V180MaterialArt.png` (V180StationLightData:67-77) and records origin in
`docs/provenance/v1.8.0-c17b-station-light-new-resources.md`. Planned outputs:
`textures/item/thermite.png`, `textures/block/thermite_torch.png`; models by
identifier `minecraft:item/generated`, [A] `minecraft:block/template_torch` and
`template_torch_wall`; English/Chinese names `Thermite`/`铝热剂`, `Thermite
Torch`/`铝热火把`, wall name key included, in `advancedrocketrycommunity_v180`
(ADR-066:70-71). Grids, screening and per-file clearance are separate review.

## 8. Proposed file ownership

Leaf, all NEW, after Root binds SHA/worktree (package root elided):
`atmosphere/torch/ThermiteTorchBlock`, `ThermiteWallTorchBlock`;
`datagen/V180ThermiteData` (models, blockstates, loot, recipes, unlocks, tag,
PNGs), `datagen/V180ThermiteLanguage`; `gametest/ThermiteTorchGameTests`;
tests `datagen/V180ThermiteDataTest`, `atmosphere/torch/ThermiteTorchBlockTest`;
`docs/provenance/v1.8.0-c18a-thermite-new-resources.md`. Root-owned edits:
`ModBlocks`, `ModItems`, `ModCreativeTabs`, `BootstrapDataGenerators`,
`V180LanguageProvider`, GameTest registration, `src/generated/v1.8/**`,
ledger, assignment and asset plan (shape copied from the station-light leaf).

## 9. C18 dependency disposition (narrow, not an exception)

ADR-066:939-941 lists C18a needs: C15a fan, C16d UI/carbon brick, C16 fluid/tag
conversion, vent migration, station access query and protection authority.
[P] This leaf consumes none: no fan/UI/fluid/vent/station input, and placement
uses the vanilla BlockItem path with no new world-change adapter. That is a
claim for review, not an accepted exception; if Root or the reviewer finds a
consumed dependency, this leaf is BLOCKED. The ADR-054 adapter and remaining
CL18A-TORCHES units are untouched and still required for their own leaf.
