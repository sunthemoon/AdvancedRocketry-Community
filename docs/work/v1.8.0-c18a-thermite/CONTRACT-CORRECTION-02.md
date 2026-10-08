# CL18A-THERMITE CONTRACT-CORRECTION-02 (author return, unreviewed)

Bounded successor to CONTRACT-01 (d9d97eed, unchanged) for REPORT-01 M1, M2
and L1 only. **[F]** source fact read in this worktree at d4a7aea9; **[P]**
proposal at its recorded authority; **[U]** unverified platform/runtime
input; **[R]** Root decision.

## 1. Superseded and unchanged

Superseded: CONTRACT-01 section 3 lines 43 and 46-56; section 7 lines 95-98;
section 9 "consumes none"; HANDOFF-01 risks 1-2. Unchanged, and not accepted
by this text: sections 1, 2, 4, 5, 6, 8 and the rest of 7, including the open
switch non-gating, umbrella tag, dropsLike/loot and native-torch items.

## 2. Ingredients and tags (M1)

[F] ADR-061 section 2.2: recipes consume tags wherever a material is meant;
the revision-7 limit covers kernel machine recipes only and keeps crafting
open to other mods' materials. Thermite is ledger `material:Thermite`. The
finding is accepted; no exact-item exception is sought.

[P] Vanilla `minecraft:crafting_shapeless`, no new serializer:
- `thermite`: tag `forge:dusts/aluminum` + tag `forge:dusts/iron`
  (`MaterialTags.item(..., DUST)`) -> 1 thermite; unlock on holding either tag.
- `thermite_torch`: item `minecraft:stick` + tag `forge:dusts/thermite` -> 4;
  unlock on holding `forge:dusts/thermite`.
Our tag keeps sole member `thermite`, `replace` false; foreign members are
accepted by design. [F] `MaterialTags.forge` is private and `item()` needs a
catalog Material; [R] leaf-local TagKey or a Root-owned shared helper.
[F] Recipes never rewrite held items; a missing ID fails through the
aggregated reload error (ADR-061 section 1.5-1.6).

Compatibility: `small_plate_press`, `pressing_aluminum_dust` and
`pressing_iron_dust` stay byte-identical; V180MaterialRecipes is not edited;
no ingredient names the thermite item; amounts stay ADR-066:209-210 proposals.

## 3. Acquisition route (L1)

[F] MaterialCatalog:82-83, 165-168: aluminum has DUST and `hasOwnOre()` is
true; iron is DUST, `OreKind.VANILLA` (103-104); V180MaterialRecipes:191-195
emits ore tag -> 2 dust for both. SmallPlatePressBlock:65-107 acts only on a
**world block directly below the press** with **obsidian below that block**,
both loaded, on a rising edge, with no BlockEntity, non-negative hardness, one
unambiguous recipe and an uncanceled `PistonEvent.Pre`; it removes the block
and spawns one zero-velocity ItemEntity of 2 dust. Generated `aluminum_ore`
loot gives the ore only with Silk Touch, else `raw_aluminum`; raw items are
not blocks and cannot be pressed. No other dust route was found in the files
read (bounded, not universal).

Survival installation: press crafted from a piston over three
`forge:ingots/iron` (V180MaterialRecipes:54-58); obsidian; ore block either
Silk-Touch mined and placed, or natural ore left in place with obsidian put
beneath it; press on top; a lever or button beside it. One off->on toggle is
one operation.

Conservation per operation: -1 ore block, +2 matching dust; press and
obsidian remain; another edge on air gives NO_INPUT and no item. One
aluminum and one iron operation: 2+2 dust -> 2 thermite -> 8 torches
(arithmetic, not a run).

Configuration consequences [R], no invented fallback:
- `classic.smallPlatePress=false`: DISABLED before any world change
  (:66-68), so no new dust; existing dust, thermite and torches stay usable
  and recipes load. Root records this limited configuration or authorizes a
  separate route task.
- Overworld ores off (CommonConfig:251-252): aluminum ore stops in new chunks
  only; existing ore still presses.

[P] Dependency: the leaf consumes the delivered C15a material/tag/press route,
not the C15a fan, C16d UI/carbon brick, C16 fluid/vent/station or save work.
A Root leaf decision under ADR-066:939-941 is still required.

## 4. NEW art plan (M2)

(a) **Before any pixel authoring**: a NEW MIT declaration
`docs/provenance/v1.8.0-c18a-thermite-new-resources.md` in the adopted
seal-detector shape ([F] its line 28 keeps classic dispositions unchanged):
planned `textures/item/thermite.png` and `textures/block/thermite_torch.png`,
grids/palette/alpha in leaf DataGen, the unchanged `V180MaterialArt.png`
encoder, vanilla model/particle identifiers only; then bytes/hashes,
originality screen and independent art review. [P] Pixels are not derived
from or traced over the legacy files.
(b) **Later, separately**: terminal handling of asset-plan rows 226
(`thermitetorch.png`) and 229 (`thermite.png`), which stay IMPORT candidates.
NEW art imports, clears or closes nothing. [R] Root picks the route under
ADR-066 section 7.3. Text tension left for Root: ADR-061:209-211 lets a batch
tighten (move to EXCLUDE) in its own commit; ADR-066:869-870 asks for an
ADR-062 revision/pin when the importable list changes.
No asset plan, allowlist or origin-finding change is made or authorized.
