# W267: evidence for the R295 asset exclusions

Root decision R295 (2026-11-03, under the owner's delegation, recorded in
[TASK-W267](../v1.8.0-root-queue/TASK-W267-ASSET-EXCLUSIONS.md)) moves the 41 remaining non-laser IMPORT files of
W263's inventory to EXCLUDE: X1 (2 files), X2 (14), X3 (18), J0 (2), U1 (4) and the arc furnace's active face (1).
This file is the evidence for that change. The asset plan itself is a Root-only file, so the change is a draft that
Root applies; the branch's plan is unchanged.

The base is shared head `a67d55dab881b4ca6cfc905e91de3d88f4f37b9c`, which includes W260 and W261. W263's inventory
is `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, SHA-256
`861320967dde0492569d8e31f2874a0d7288c603af8d2bca5720c720fb97925a` (rechecked). The pinned original Advanced Rocketry
tree is commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`.

## 1. The plan draft

The draft is written by `make_draft.py` from the base plan with `git show`, as LF bytes. Every unchanged line is kept
verbatim except its renumbered order field. The draft and its patch are outside Git:

| Artifact (under `D:/GitHub/ARCE-Task-Evidence/root-queue/W267/`) | SHA-256 |
|---|---|
| Base plan `docs/work/v1.8.0-asset-plan.csv` at `a67d55dab` | `58cd525677d92192ff89f64d33b17bcb7b99cf4b310c7211d574ac19245e8f24` |
| `drafts/docs/work/v1.8.0-asset-plan.csv` (the plan to apply) | `9011d4732bf9dea8db60a198c327120153c26987a61896ebfda20b3250d27373` |
| `drafts/v1.8.0-asset-plan.patch` (unified diff, one line of context) | `49e3523e1dd1e9e2693785258460e319c613ab11ab50ccd6f4bfca9ec81c9c34` |
| `tools/make_draft.py` (the generator) | `fe0793f0eeb80782d28744539f320528fc2e4b04825829cd1b5aba9fab6f7ce2` |

The patch is long only because 22 inserted rows renumber every later row. Semantically the draft:

- converts nine IMPORT rules in place: the exact files `textures/blocks/blastbrickfront_active.png`,
  `textures/blocks/combustion.png`, `textures/blocks/smallconnector.png`, `textures/gui/genericstation.png`,
  `models/jetpack.obj` and `textures/models/jetpack.png`, and the three narrow patterns `textures/gui/space_*.png`,
  `textures/gui/jetpack*.png` and `models/item/models/sawBlade.*`, each of which matches exactly its group's files and
  nothing governed by an earlier rule;
- replaces four IMPORT wildcards with 26 exact EXCLUDE rows at the same position: `textures/gui/buttons/*.png` (2),
  `textures/gui/*.png` (8), `models/block/models/*` (5) and `models/item/models/*` (11);
- changes no other rule. The three laser-gun rules (W265-fix's scope), all 81 REVIEW rules (including the LibVulpes
  `models/item/models/motor.*` and `models/motor.obj` rows) and every EXCLUDE, IMPORTED and REGENERATE rule keep their
  pattern, handling, plan, owners and reason.

| Handling (files) | Base | Draft |
|---|---|---|
| IMPORT | 45 | 4 (the laser gun only) |
| EXCLUDE | 226 | 267 |
| IMPORTED | 88 | 88 |
| REGENERATE | 444 | 444 |
| REVIEW | 95 | 95 |
| Rules | 400 | 422 |

Owners were trimmed, never added:

- The active face keeps `block:arcfurnace` and drops `block:blastbrick`. In the pinned upstream its only consumer is
  `blockstates/arcfurnace.json` (the `front` of its four `state=true` variants); the blast-brick block never used it.
- `models/item/models/*` named three owners for all eleven files. Each exact row now names only its own unit:
  `rocketmotor.*` `block:rocketmotor`, `advRocketmotor.*` `block:advRocketmotor`, and the seven tank files
  `block:fuelTank`.
- The two GUI wildcards had no owner (`-`); their ten exact rows keep `-`. Every other converted row keeps its single
  existing owner. The five block tank rows keep the existing `block:fuelTank`, although upstream also used the top and
  bottom meshes in the bipropellant, nuclear and oxidizer tank blockstates; adding owners was outside the task.

Every new row's reason begins with "R295 W267 exclusion:" and names what replaces the file. The full rows are in the
patch; section 2 summarises them.

## 2. Per-file evidence

"Upstream use" is what the pinned original tree did with the file. "Replacement" is what the mod does today; section 4
lists the checks behind it. Source SHA-256 values come from `legacy-manifest/assets.csv`.

### X1: block sprites

| File | Source SHA-256 | Upstream use | Replacement |
|---|---|---|---|
| `textures/blocks/combustion.png` | `9fe34da3a8e262964cbdb1ff9b87764c3b719725d9d08a387e571d6136e22826` | None found: the motor blockstates and MTLs use `textures/models/combustion` instead. | The P1 `rocket_motor` OBJ draws the separate imported model texture `block/combustion_classic` through `rocketmotor.mtl` (W249). |
| `textures/blocks/smallconnector.png` | `976bd0324f2b8659916b92f108da56735d05874fc4889da57d7caa0138b8c873` | The `support` texture of `models/block/solararraypanel.json` and `seat.json`. | The formed solar array draws the K3 `solar_array.obj` with NEW `solar_array_front`; the NEW `solar_array_panel` and `power_output_plug` models replace the panel's support part. |

### X2: GUI bitmaps (14 files)

| File | Source SHA-256 | Upstream use | Replacement |
|---|---|---|---|
| `textures/gui/borderhorizontal.png` | `28df9bc711b5c50f8e14f9a89c3f0ff253292d5c4e9f4ea6849a11fee4fb2403` | Image frames in the observatory and warp controller screens. | Screens draw their panels and frames with code fills (`ClassicControllerScreen`, `StationControllerScreen`). |
| `textures/gui/bordervertical.png` | `92955b73ef0a6d77ad0561ee53671b2074c5bc821dd30c7353fe79a8feb182ae` | As above. | As above. |
| `textures/gui/buttons/buttongeneric.png` | `a7f8e0ef5311db2cd8a34f900b94793dbcdeb9b658ed2c8b51b1ba94980759d0` | The generic `ModuleButton` face in many machine screens. | Native `Button` widgets. |
| `textures/gui/buttons/buttongeneric_hover.png` | `d1572dd924cd4bcacfb363f35be4a1a58e4a1d5e41d6f4f42d4beea6e2d837b4` | Its hover face. | Native `Button` widgets. |
| `textures/gui/framebg.png` | `ee343c1e64025c0aa5a6388e2306be9155e54d191eb4ab1c1aa295b3da4a6acf` | The HUD frame of the rocket and suit overlays and the atmosphere analyser. | `LifeSupportHud` draws its panels with code fills. |
| `textures/gui/genericstation.png` | `868a557574b14422c7a2fe68983b2649c0b0cd8c735c961c64084e268f23a75c` | The station screens' background (`ModuleStellarBackground`). | `StationControllerScreen` draws the controller panel in code with native buttons. |
| `textures/gui/guibuttonbluefuture.png` | `a358a037b68f9158fec4377e05c21c46f7533474ca88151fc66f65767834f391` | None found in the Advanced Rocketry code. | Native `Button` widgets. |
| `textures/gui/guibuttonbluefuture_hover.png` | `06753122a7128f01641b9ac1bd57aca475485462b3abfe947cf946a195f3f408` | None found. | Native `Button` widgets. |
| `textures/gui/jetpack.png` | `85c191aeb0dd8e56fc78b128b97268dd0ab691a5586d0ce593483114df58b42a` | The jetpack's enabled HUD icon (`ItemJetpack`). | `LifeSupportHud` hydrogen and suit panels from `SuitHudClient`; on, off and hover states are action-bar text (`SuitMotionService.Reply`). |
| `textures/gui/jetpackdisabled.png` | `6696420ec9058e8a45c3dde7e7364f4fb78f9c37bb7ed6a0e12e72b2e7c54d73` | The disabled icon. | As above. |
| `textures/gui/jetpackhover.png` | `ada99451a43432e09b47ed0fb37afdcb5f4932ab59c7d97089575251a6b64860` | The hover-mode icon. | As above. |
| `textures/gui/recticle.png` | `533cc0f1cfaef1001ce238c7e506ce84b4c41b668815ef3a3afd320b8c5d818f` | A destination reticle drawn in the space-travel sky. | Destinations are chosen on the code-drawn `RocketStarMap` and shown in the `LifeSupportHud` rocket panel. No sky reticle is drawn. |
| `textures/gui/selection.png` | `d700e35307afee0e10a398bd0aa47ee0e7b22237e255d21c06315d2cf91c47af` | The planet selector's selection circle. | The code-drawn `RocketStarMap` marks the selected body (the ADR-035 selector redesign). |
| `textures/gui/starrybg.png` | `7c3b7d1d976895487b9e0b2bb05c74ec9f540f584ace412639fe68701367d4bf` | None in the Advanced Rocketry code: its screens used LibVulpes's own `starryBG`. | `RocketStarMap` and `StationControllerScreen` draw their backgrounds in code. |

R295 limits X2 to these fourteen files. The seventeen REVIEW button and warning files and the earlier EXCLUDE sheets
keep their own rules, which come before the replaced wildcards.

### X3: item meshes and tank variants (18 files)

| File | Source SHA-256 | Replacement |
|---|---|---|
| `models/block/models/bottomtank.mtl` | `4efb20f3ef655e1d9edbbf1770dbcecd33ff84f07cd7ba55ad98024c0f481bb7` | No connected tank variant: tanks are standalone (R286). |
| `models/block/models/bottomtank.obj` | `7685d49c9ea95d6db9ad15da0018c6912e4f71388ba047316e30dd6c97482d99` | As above. |
| `models/block/models/endtank.mtl` | `a4bda7bbf972e0a55551933dceb2c57a8211b21f42e82070b82cd308a5ac23f5` | No matching OBJ exists in the pinned tree. |
| `models/block/models/toptank.mtl` | `250509910de7b71b829bf0734cea85f172a60074e221c7fee0e770506a757b82` | No connected tank variant (R286). |
| `models/block/models/toptank.obj` | `ded0254e15f4e2617eb328310822a3b5a81d9678a3a402d7cc30b6f8f9f8a12b` | As above. |
| `models/item/models/advRocketmotor.mtl` | `c201656017f2e3e5ae0a79a33a7784cea0871a0fbfb02515f855e8513d656079` | The `advanced_rocket_motor` block item inherits the P1 block mesh `advrocketmotor.obj` (W249). |
| `models/item/models/advRocketmotor.obj` | `78440cca580685fb49592de225fb5d709dc189db62d3ac408be38aec92a76eee` | As above. |
| `models/item/models/bottomTank.mtl` | `1eab3ce9ac57c91e27f3dedc7adb6a25a1cc86d4a1804e6f279fa629fbac9f61` | The fuel-tank block items inherit the P4 `middletank.obj` block mesh (W260, R286). |
| `models/item/models/bottomTank.obj` | `bc4eec5271b2a1805133adcd3aed0e2de292f21d3e24c9afe062ea3c02caf050` | As above. |
| `models/item/models/endTank.mtl` | `5117d100ec8d321e160f2d7721c3a869eb25b872e0d84cf3d2d15af21d266c8a` | No matching OBJ exists. |
| `models/item/models/middleTank.mtl` | `bf10a689e4c177306e3f07835c91f67747a43fedcfeccebadd9e334575500e8f` | The block items inherit the P4 block mesh. |
| `models/item/models/middleTank.obj` | `1cce7baa470a9f44d64a1398667dbec2f1b078392fffb5b397a0ef4ac7ec3623` | As above. |
| `models/item/models/rocketmotor.mtl` | `c201656017f2e3e5ae0a79a33a7784cea0871a0fbfb02515f855e8513d656079` | The `rocket_motor` block item inherits the P1 block mesh `rocketmotor.obj` (W249). |
| `models/item/models/rocketmotor.obj` | `dcd28d6e74cb72c9b9ead30b4345af78e54aba0cd55cd39f35868aba3a1ebe16` | As above. |
| `models/item/models/sawBlade.mtl` | `77af7fe4375300874f90d351699e84b18d73f667bb2d8194feea4787a12c40ef` | The `saw_blade` block item inherits the S1 block mesh `sawblade.obj` (W256). |
| `models/item/models/sawBlade.obj` | `8705a274c4c72568f36b72af087b2f9167660b53d50f57b939de809e82c05208` | As above. |
| `models/item/models/topTank.mtl` | `cc789e20280ca6ec7cb74bcb0d2413ce541601d9972617209618c5d400f98890` | The block items inherit the P4 block mesh. |
| `models/item/models/topTank.obj` | `06e44cc3c9137b7b7e9fba4bf29b5c85d275c551e4b267d2c61e150e8496618f` | As above. |

The rocket, bipropellant and oxidizer fuel-tank blocks all bake `models/block/models/middletank.obj`, their blockstates
have a single variant with no `tankstates` property, and assembled rockets render each block's own baked model through
`RocketEntityRenderer`, so they show the same middle tank. In the pinned upstream the top and bottom meshes were
selected by `tankstates=top` and `tankstates=bottom` in four tank blockstates; that connected look is deliberately not
reproduced (R286).

### J0, U1 and the active furnace face

| File | Source SHA-256 | Upstream use | Replacement |
|---|---|---|---|
| `models/jetpack.obj` | `acc4ba51bb2c4fad52f5206016548f7cb24de79ba474fcc563b3bf697a03d472` | The worn jetpack (`RenderJetPack`). | `SuitComponentLayer` draws the NEW worn jetpack from code boxes and `textures/entity/suit_components.png`. The legacy OBJ has 136 faces and no `usemtl` statement at all, the format failure W245 recorded. |
| `textures/models/jetpack.png` | `c7ac6c1680bcbc417e998ec44058cd60c27ff4a8c5556b2bd1f8706521e08976` | That OBJ's texture. | As above. |
| `textures/gui/space_boots.png` | `cbff8bdb4603171226ce9221e46b9676fcae32ecf668d42e6a328efabc74efd3` | `TextureResources.armorSlots`, drawn per worn piece by the suit HUD (`RocketEventHandler`). | `SuitWorkstationScreen` has one labelled armor slot and one module slot drawn in code (R292, [W264](../v1.8.0-root-queue/records/W264.md)); `LifeSupportHud` lists installed suit modules as text. |
| `textures/gui/space_chestplate.png` | `47a10dda549d85c1c1b4dadcf3d6d7d044e4b70cde005817b43fa9adc2765b73` | As above. | As above. |
| `textures/gui/space_helmet.png` | `9bf5f5de669c1fd1dbc30ad5e7dfb93a8bee58f63a7450c5dad0e5cbe7b82273` | As above. | As above. |
| `textures/gui/space_leggings.png` | `ce464cb221b36d7d82cf8734df5ece1cd77abebf523abed2ccb78fa267ddfb7a` | As above. | As above. |
| `textures/blocks/blastbrickfront_active.png` | `ac55caac8a6c6916eaa061fba6fe3bf408f2885dff9a3ad6bbcaf8b9d758c24d` | The arc furnace's active `front` (`blockstates/arcfurnace.json`). | The arc furnace keeps its static idle front `block/blastbrickfront` (W246); `ClassicControllerBlock` has `FACING` only, and no active blockstate is added for art. |

**Appearance limits.** None of these replacements is pixel-identical to the legacy look: the screens and HUD have no
bitmap frames, button faces or slot icons; there is no sky reticle; the tanks do not show connected top and bottom
pieces; the worn jetpack is the NEW code model; and the arc furnace front does not change while it runs. Real-GPU V1
checks of these screens and models remain open and are not claimed here.

## 3. No-reference scan

`audit_w267.py` (committed beside this file) scans every tracked file of commit `4b26e5a5d` with the draft applied.
The scan does not read the plan, so it describes the branch tree as well. A file counts as referenced only through a
structural reference:

- resource JSON under `src/main/resources`, `src/generated/*/resources` and `src/imported/*/resources`: every string
  value (model IDs, parents, OBJ model paths and texture slots), read in our namespaces and resolved both as a path and
  as a sprite ID, including the 1.12 form `models/<file>.obj`;
- MTL `map_*` and `bump` maps and OBJ `mtllib` statements, resolved against the file's folder; header comments, which
  name `.blend` files, are not references;
- every string literal in `src/main/java`, runtime code and DataGen providers alike;
- `source_path` and `target_path` entries of the import plans (`tools/import/*.json`) and provenance records
  (`docs/provenance/*.json`). Their derivation `related` lists name pixel relatives, not consumers.

It also hashes every tracked file under `src/` for the 41 source SHA-256 values and checks that no file exists at any
packaged path the files would take (legacy or singular folder names) in any resource root.

Result: **none of the 41 files has a consumer reference, a byte copy under `src/` or a packaged target.** The scan
read 618 resource files, 2,373 runtime Java files, 173 DataGen providers, 14 import plans and 46 provenance records
(58,327 structural references), and looked for the files' paths in 1,508 test files. The per-file results, including
every source SHA-256, are in [audit-draft.json](audit-draft.json).

**Positive controls.** The same scan must find five live imported files, each through the named kinds of evidence, so
that a silent parser cannot pass vacuously. All five are found:

| Control | Found through |
|---|---|
| `models/block/models/middletank.obj` | Generated block-model JSON, the W260 import plan, provenance and packaged target (a rewritten copy, so no byte copy) |
| `models/block/models/middletank.mtl` | The imported OBJ's `mtllib`, import plan, provenance and packaged target |
| `textures/blocks/blastbrickfront.png` | The `arc_furnace` model's texture slot, import plan, provenance, packaged target and byte copy |
| `textures/models/centrifuge.png` | The imported `centrifuge.mtl` `map_Kd`, import plan, provenance, packaged target and byte copy |
| `models/solar_array.obj` | The `ObjModelFixture` Java literal, import plan, provenance, packaged target and byte copy |

**Informational mentions.** The audit also searches the same resource, Java, import-plan and provenance files as plain
text (outside OBJ and MTL comments) for a path fragment of each file, such as `gui/jetpack` or `item/models/topTank`.
It finds only derivation relatives: the W246 provenance record lists the active face, and the W249, W255 and W258
records list `textures/blocks/combustion.png`, as pixel relatives of the files they imported. In tests,
`tests/test_validate_v180_content_ledger.py` uses the active face as a validator fixture (and names the
`models/block/models/*` pattern as a rule, not a file), and `V180FlatTextureImportTest` asserts that the active face
is not imported. No test names another excluded file.

## 4. The replacements that work today

The audit checks each replacement named in the reasons in the tree at `4b26e5a5d` (all PASS; these checks do not read
the plan):

- X1: `models/block/rocket_motor.json` bakes `models/block/models/rocketmotor.obj`, whose `rocketmotor.mtl` maps
  `block/combustion_classic`, and that texture exists. `ObjModelFixture.SOLAR_ARRAY` draws `models/solar_array.obj`;
  `solar_array_front.png`, the `solar_array_panel` model with its NEW top and the `power_output_plug` model exist.
- X2: no Java literal in `src/main/java` names a mod `textures/gui` file, and none of the 97 client classes calls
  `blit(` or `setShaderTexture`. `LifeSupportHud` reads `SuitHudClient.current()` and draws with `graphics.fill`;
  `SuitMotionService.Reply` carries the jetpack states; `StationControllerScreen` adds native buttons;
  `ClassicControllerScreen` fills its panel; `RocketStarMap` is the code-drawn map.
- X3: the six item models (`rocket_motor`, `advanced_rocket_motor`, the three fuel tanks and `saw_blade`) have their
  block model as parent, and those block models bake the P1, P4 and S1 OBJ files. The rocket, bipropellant, oxidizer
  and nuclear fuel-tank blockstates have no `tankstates` property. `RocketEntityRenderer` renders blocks with
  `renderSingleBlock`.
- J0: `SuitComponentLayer.TEXTURE` is `textures/entity/suit_components.png`, and the file exists.
- U1: `SuitWorkstationMenu` holds a two-slot container, and `SuitWorkstationScreen` draws slot backgrounds with fills.
- Active face: the `arc_furnace` model's front is `block/blastbrickfront`, its blockstate has only the four `facing`
  variants, and `ClassicControllerBlock` registers `FACING` only.

The existing unit tests that pin these replacements were also run on the branch.
`gradlew.bat --offline test` filtered to the twelve classes `V180MotorModelDataTest`, `V180FuelTankModelDataTest`,
`V180SawModelDataTest`, `V180PropulsionDataTest`, `V180SolarArrayDataTest`, `K3ObjModelTest`,
`V180FlatTextureImportTest`, `PackagedBlockAtlasCoverageTest`, `ArcCuttingDataTest`, `SuitComponentsTest`,
`SuitHudSummaryTest` and `LifeSupportHudPanelsTest` ran 77 tests at `4b26e5a5d`, with no failure, error or skip
(144 seconds). They pin the P1, P4 and S1 meshes and their shared block items, the K3 solar-array model, the W246
static furnace faces and the absent active face, the packaged block atlas, the NEW worn jetpack parts and the HUD
panels. The JUnit XML is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W267/java-replacements/junit/`.

## 5. The 35 retained NEW textures

These are the 35 unreferenced NEW textures of W263 section 5. In the tree at `4b26e5a5d`, whose resources equal the
base's, every file still has W263's SHA-256 and no binding: no texture slot in any resource JSON, no MTL map and no
runtime Java literal names it.
Model IDs and parents are not bindings, so for example the `blast_brick` blockstate's model ID does not bind the
`blast_brick.png` texture. The same search finds W263's four live-art controls (`quartz_crucible`,
`suit_workstation_side`, `basic_laser_gun` and `suit_components`).

The proposed disposition of each file is **keep as history pending human review**; none qualifies as live art, and
none is deleted. Their providers still emit them, so removing one later means changing its provider and tests after
human review; deleting a PNG alone would be undone by `runData`.

| NEW file (under `src/generated/v1.8/resources/assets/advancedrocketrycommunity/`) | Displaced by | SHA-256 | Bindings | Proposed disposition |
|---|---|---|---|---|
| `textures/block/blast_brick.png` | W246 T1 to T3 | `4ca1ab5af3cffad860e80e68e475f19b61f9a147600de0ec5e5736e92d3ddcfa` | 0 | keep as history pending human review |
| `textures/block/arc_furnace.png` | W246 T1 to T3 | `1f3b64d694487f5c4de1e6198690ea61d5ffd4c6c0ee3cc0c0b60665da01c0d8` | 0 | keep as history pending human review |
| `textures/block/airlock_door_bottom.png` | W246 T1 to T3 | `f60dbae0c139fc9f44194c416ac6cb76b9a09b677594307ed99ca3210c054d62` | 0 | keep as history pending human review |
| `textures/block/airlock_door_top.png` | W246 T1 to T3 | `9d022bffebc285a2e87ba3a75528052e5d2bc3683d9ec1686c5f6afa516bb5d1` | 0 | keep as history pending human review |
| `textures/block/suit_workstation_top.png` | W246 T1 to T3 | `df87136c38bb16f94efb29ca83af7a9538b7f2a0a2f454b4c2d0dc404fe3d851` | 0 | keep as history pending human review |
| `textures/block/thermite_torch.png` | W246 T1 to T3 | `562393a306790a3f2d40edab58562ebcdf01d955d008dc5d6ce96cf090b3f39d` | 0 | keep as history pending human review |
| `textures/item/carbon_scrubber_cartridge.png` | W246 T1 to T3 | `008cddc50c44df338db05a011e95b2a2c1341e8c78cc6294ae672eddd8115cd3` | 0 | keep as history pending human review |
| `textures/item/thermite.png` | W246 T1 to T3 | `f40ee6913b3f55cf1ca39ca9c65379c2a09203be1b257925235c36c466ecca44` | 0 | keep as history pending human review |
| `textures/item/airlock_door.png` | W246 T1 to T3 | `6df187b04fa34a4df3530014cbb85b05ca767f7bad352ab579e94f564a7e2d95` | 0 | keep as history pending human review |
| `textures/item/seal_detector.png` | W246 T1 to T3 | `40d23a8a6842d577e61720a46963cf587d347a8584eb892156dc9ead4e65fa3d` | 0 | keep as history pending human review |
| `textures/item/jetpack.png` | W246 T1 to T3 | `75ec65b676f4e9f5711aa5a8a07e115526db43805325698e36283310b4c3e74f` | 0 | keep as history pending human review |
| `textures/item/jackhammer.png` | W246 T1 to T3 | `771eaebb41fcc90c4d9622f8c807705cca67d51ca3b64784e7ee4057cfb53563` | 0 | keep as history pending human review |
| `textures/item/hover_upgrade.png` | W246 T1 to T3 | `68d732046f9f2ba4d87bf31d8a74a142fa4c1b4ab8d3e3558a56c1e448d2441a` | 0 | keep as history pending human review |
| `textures/item/flight_speed_upgrade.png` | W246 T1 to T3 | `f0fe2c70fe4aea556e8af811299505e88d92df5c29e7d6f51d3d1d492cf078d9` | 0 | keep as history pending human review |
| `textures/item/tracking_circuit.png` | W246 T1 to T3 | `e78f2712e9cc2ad6de13c4ffd35906adbda6480da407f13f7741c1b39e8b0884` | 0 | keep as history pending human review |
| `textures/block/advanced_rocket_motor_side.png` | W249 P1 | `f5107786e181c27eeb06ee653b733e875a14331007132d58376247d2ab517d68` | 0 | keep as history pending human review |
| `textures/block/advanced_rocket_motor_top.png` | W249 P1 | `0cd09ac7f658f22532c78b9e66ea35ab603e2ee99aa0f61fbed44db0a39b7a4d` | 0 | keep as history pending human review |
| `textures/block/advanced_rocket_motor_bottom.png` | W249 P1 | `740be5843eac7fe79087be6c216fee3c387b39ff12b5f7ec1f6f928a40f4a96c` | 0 | keep as history pending human review |
| `textures/block/bipropellant_rocket_motor_side.png` | W255 P2 | `c6aca4d0e969f7e0489e35c9d92dac37959716b7d9d26bc97394cbda0b2376ed` | 0 | keep as history pending human review |
| `textures/block/bipropellant_rocket_motor_top.png` | W255 P2 | `aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7` | 0 | keep as history pending human review |
| `textures/block/bipropellant_rocket_motor_bottom.png` | W255 P2 | `14518ee5dffe0889110fe6cd832602a7b8ed2ffd68404d26482d62381ab04c61` | 0 | keep as history pending human review |
| `textures/block/advanced_bipropellant_rocket_motor_side.png` | W255 P2 | `26caa7d04482fbba1ba92e33a05221588b4ddf5f643a2448954992aa8e5b9e6a` | 0 | keep as history pending human review |
| `textures/block/advanced_bipropellant_rocket_motor_top.png` | W255 P2 | `aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7` | 0 | keep as history pending human review |
| `textures/block/advanced_bipropellant_rocket_motor_bottom.png` | W255 P2 | `14518ee5dffe0889110fe6cd832602a7b8ed2ffd68404d26482d62381ab04c61` | 0 | keep as history pending human review |
| `textures/block/nuclear_rocket_motor_side.png` | W258 P3 | `af44ad6c790a14e298b235b244ad5c9be62960e3b847373c047525e77aab30dc` | 0 | keep as history pending human review |
| `textures/block/nuclear_rocket_motor_top.png` | W258 P3 | `601dd93c46d0674d8e928c89a8b70ce0256b2bb5b720fdd2f93fbe3a67687fcf` | 0 | keep as history pending human review |
| `textures/block/nuclear_rocket_motor_bottom.png` | W258 P3 | `bb4b084b9067f496f0bee743192d2061a78262e03d1843403fefe90f1e3d0df4` | 0 | keep as history pending human review |
| `textures/block/bipropellant_fuel_tank_side.png` | W260 P4 | `c6aca4d0e969f7e0489e35c9d92dac37959716b7d9d26bc97394cbda0b2376ed` | 0 | keep as history pending human review |
| `textures/block/bipropellant_fuel_tank_top.png` | W260 P4 | `aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7` | 0 | keep as history pending human review |
| `textures/block/bipropellant_fuel_tank_bottom.png` | W260 P4 | `aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7` | 0 | keep as history pending human review |
| `textures/block/oxidizer_fuel_tank_side.png` | W260 P4 | `dbc81965b91afb01a2defccf2004b2b140dc303508ff2c77b3645f3de8e9be33` | 0 | keep as history pending human review |
| `textures/block/oxidizer_fuel_tank_top.png` | W260 P4 | `f74c434c5b69a544f0c382d04914e16859fbefc99d38b5af4e2c5a3a6236f52d` | 0 | keep as history pending human review |
| `textures/block/oxidizer_fuel_tank_bottom.png` | W260 P4 | `f74c434c5b69a544f0c382d04914e16859fbefc99d38b5af4e2c5a3a6236f52d` | 0 | keep as history pending human review |
| `textures/block/saw_blade.png` | W256 S1 | `98a7326b340a56d1e80b1a2404efa540da1ed2f0d37fd3b6fa1d89906eaca3f1` | 0 | keep as history pending human review |
| `textures/block/landing_pad.png` | W261-fix P0 | `0485c02e998660e22d79ed361554e20505089396d3a96b4992b145db6647fc2a` | 0 | keep as history pending human review |

## 6. Ledger notes

No unit's note needs to change. The notes of `block:arcfurnace`, `block:blastbrick`, `block:rocketmotor`,
`block:advRocketmotor`, `block:fuelTank`, `block:sawBlade`, `block:solararray`, `block:suitWorkStation`,
`block:warpMonitor` and `item:jetPack` describe what is delivered (W246 flat textures, P1 and P4 meshes with shared
block items, the S1 mesh, the controller-owned array, the station screen and the jetpack attachment). None of them
promises one of the excluded files, so excluding them makes no note wrong. Dispositions, targets and evidence are
unchanged.

## 7. Verification

`run_checks.py` made a shared local clone of commit `4b26e5a5d`, ran two checks on it as committed, copied the draft
plan over it and ran the rest, then removed the clone. Each step's command, exit code, duration and log SHA-256 is in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W267/verify-4b26e5a5d/steps/`. Every step ended as expected
(`verification-summary.json`, SHA-256 `e927adc35d93f729c39efa60272354aade1ba17142265c2e8be48744cf463bc9`). The pinned
upstream is an LF clone of `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, as CI's Linux checkout is.

| Check | Plan | Result |
|---|---|---|
| `scripts/validate_v180_content_ledger.py --require-accepted` | branch | PASS: 653 units; IMPORT 45, EXCLUDE 226. |
| `audit_w267.py --base a67d55dab --upstream <LF clone>` (the first-match audit) | draft | PASS: exactly the 41 files change, each from IMPORT to EXCLUDE; every other legacy asset keeps an identical first-match rule; 0 unused rules, 0 unmatched assets; the production first match (`v180_import_checks.load_inputs`) agrees for all 898 assets; references, controls, replacements and NEW textures as above. Result: [audit-draft.json](audit-draft.json). |
| `validate_v180_content_ledger.py`, then `--require-accepted` | draft | Both PASS: 653 units; IMPORT 4, EXCLUDE 267, IMPORTED 88, REGENERATE 444, REVIEW 95. |
| `validate_v180_content_ledger.py --closure` | draft | Exit 1 as expected and unchanged: 16 ledger rows are still PLANNED and 95 assets are still REVIEW. |
| `tools/audit/inventory_v180_content.py --check` | draft | PASS ("inventory: up to date"). |
| `tools/import/import_v180_assets.py verify` for all 13 import plans and records (k1 to k6, W244, W246, W249, W255, W256, W258, W260, W261), the CI loop | draft | All 13 exit 0: every target and record reproduces byte for byte. |
| `python -m unittest tests.test_import_v180_assets tests.test_validate_v180_content_ledger tests.test_validate_v180_provenance tests.test_validate_v090_resources` (importer, ledger, provenance and resource tests) | draft | 166 tests pass in 347 seconds. |
| The three fixture tests with the base version of the ledger test file | draft | Exit 1 as expected: all three fail (two miss the IMPORT-only error, one reports `'IMPORT' != 'EXCLUDE'`). |
| The same three tests with the branch's test file | draft | 3 pass. |
| `scripts/validate_v090_resources.py` | draft | PASS: 1,964 files, 582 JSON, 1,585 bilingual keys, 800 asset references. |
| `docs/work/v1.8.0-c18d-asset-closure/check_audit_01.py` (the W47 asset coverage check) | branch and draft | Exit 1 as before, with 289 problems; the two 290-line outputs are identical, so the draft adds and removes no problem. This historical checker asserts W47-era plan citations and allowlist ceilings that later batches superseded, as W263 section 8 explains. |
| `scripts/validate_repository.py --require-approved-identity` | draft | PASS: 45 passed, 0 pending, warnings or failures. |
| `git diff --stat` and `git diff --check` after applying the draft | draft | One file, 124 insertions and 102 deletions; no whitespace errors. |

On the branch itself (worktree, commit `4b26e5a5d`), the full Java unit suite passed: 6,229 tests in 1,120 classes,
no failure, error or skip (553 seconds); the targeted run is in section 4.

## 8. Notes for Root's integration

- Apply the draft as a semantic edit if the plan has changed since `a67d55dab`. W265-fix changes the laser-gun rows,
  which sit next to the replaced `models/item/models/*` wildcard: keep W265-fix's laser rows, replace the four
  wildcards with the 26 exact rows at their positions, convert the nine in-place rows, then renumber.
- The ledger-test commit (`3c4676a91`) must land with or before the plan change. Without it, three validator tests
  fail on the new plan (shown in section 7).
- After integration the audit can be rerun from the repository root against the integrated plan:
  `python -X utf8 -B docs/work/v1.8.0-w267-asset-exclusions/audit_w267.py --base <the commit just before the R295 plan
  change> --upstream <pinned upstream checkout>`. If W265-fix's plan change lands first, use a base after it, or the
  first-match comparison will also report the laser-gun files.
- No REVIEW row, allowlist entry, ADR, import, status file or ledger row is changed. ADR-062's allowlist pin stays
  valid because the allowlist is untouched; an IMPORT ceiling is not an import obligation.
