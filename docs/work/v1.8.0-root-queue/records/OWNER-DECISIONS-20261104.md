# Owner decisions of 2026-11-04: answers to the brief of 2026-11-03

Channel: the owner's Claude Code Root session, 2026-11-04. Between Root's hourly checks of 09:35 and 10:34 the owner
asked, verbatim:

> 列一下待确认清单吧，我手动测了确认下

Root answered in the session with a checklist built from [OWNER-BRIEF-20261103](OWNER-BRIEF-20261103.md), numbered as
follows: 1, D5 (the ten NEW sounds, with their `/playsound` IDs and in-game triggers; `machine_large` has no in-game
source today); 2, G1 to G8 (the 95 REVIEW files of W268); 3, D7 (the 30 provenance records that say
`PENDING_HUMAN_REVIEW`); 4, the gas charge pad, A or B; 5, the brief's item 7 (sleeping off-world and the planet
respawn point); 6, the final campaign; 7, the risk decisions, which wait for evidence. The test JAR named there was
the artifact `v180-build-6f3bd461bf95b16291cf766b6869f9c15d06bd82-1` of hosted run 41630143345, whose code equals the
shared head `521cb9b95`.

Between 10:34 and 11:05 the owner replied, verbatim (pasted text in the same session):

> 1.音效都通过了
> G1~G6,G8都可以通过，G7这个可以用新的音频的
>
> 2.物品图标可以用新的图 大多数新的图都是可以的，还存在部分看着临时拼接的贴图
> 比如:
> 轧制机怎么四周还是活塞头的图的
> 轧制机的物品输入方块，这个抽屉，emmm，有点一言难尽，好像当前不少输入输出的贴图还得改下，顶上是个什么红石贴图、四周抽屉的这种。。
> 然后罐子，当前直接是玻璃瓶的，这个后续有考虑用原版的氧气罐之类的贴图吗
>
> 3.通过
>
> 4.充气台可以放到1.9
>
> 5.不改变
>
> 6.这个可以先挂起，或者说单机测完完整步骤即可，联机性能最后再说

The reply's numbers follow Root's checklist, except that its first item answers both D5 and G1 to G8, and its second
item comments on the NEW art while answering G2. Item 7 of the checklist, the risk decisions, got no answer and
stays open.

## What the owner decided

| Item | The owner's words | Recorded meaning and scope |
|---|---|---|
| D5 | 音效都通过了 | All ten NEW sounds are kept as they are: `air_hiss_loop`, `basic_laser_gun`, `combustion_rocket`, `electric_shock_small`, `gravity_controller`, `laser_drill`, `lathe`, `machine_large`, `railgun_bang` and `rolling_machine`. None needs a change. |
| G1 to G6, G8 | G1~G6,G8都可以通过 | Accepted as W268's proposal states them: 85 legacy files become EXCLUDE. This excludes the old files only; it does not clear their authorship (ADR-061 section 4.1). |
| G7 | G7这个可以用新的音频的 | The ten legacy OGGs are not wanted: v1.8 uses the NEW audio, so they become EXCLUDE as well, instead of staying REVIEW as the proposal drafted. With G1 to G6 and G8, all 95 REVIEW files become EXCLUDE. This finds nothing about the old files' authorship. |
| G2 and the NEW art | 物品图标可以用新的图 大多数新的图都是可以的，还存在部分看着临时拼接的贴图 | G2 is accepted. Most NEW art is acceptable; some textures look like temporary assemblies and are to be redone. The owner's examples: the rolling machine shows piston-head art; its item input block, and many input and output blocks, show a redstone or concrete top and a drawer-like side; the canisters show a glass bottle. The owner asks whether a texture like the original oxygen tank is planned. Root's answer and the follow-up task are below (R308). |
| D7 | 通过 | The owner's provenance and licence review passes for the 30 v1.8 records that say `PENDING_HUMAN_REVIEW` (listed below). Their review status becomes APPROVED, with the owner as reviewer and 2026-11-04 as the review date. This approves those records as they stand at `521cb9b95`; art that changes later gets a new pending review. |
| Gas charge pad | 充气台可以放到1.9 | Option A of the brief's item 6: v1.8 ships O1 (W226's canister exchange) for every player, and the pad's ledger row closes on O1's evidence. W174's automatic charging moves to v1.9, with its design kept as is. This supersedes the part of PAD-D4 (2026-10-31) that kept automatic charging for dedicated servers and LAN guests in v1.8. |
| Planet respawn when sleeping | 不改变 | No change: sleeping off-world keeps the existing respawn point when `allowPlanetRespawn` is on. |
| Final campaign | 这个可以先挂起，或者说单机测完完整步骤即可，联机性能最后再说 | The campaign is suspended. For now, a complete step-by-step test in single-player is enough; multiplayer and performance (the two-client V2 sessions, the docs/17 reference-node workload and the joint four-hour soak) are taken up last. This does not mark V1, V2, S1 or any Gate as passed or waived, and does not change ADR-065's bay admission, which stays off in production. |

The 30 records of D7, under `docs/provenance/`: the upstream imports `v1.8.0-c18d-k1`, `-k2`, `-k3`, `-k4`, `-k6`,
`v1.8.0-c18d-w244`, `v1.8.0-w246-flat-textures`, `v1.8.0-w246-rebound-item-models`, `v1.8.0-w249-motor-models`,
`v1.8.0-w255-bipropellant-motor-models`, `v1.8.0-w256-saw-model`, `v1.8.0-w258-nuclear-motor`,
`v1.8.0-w260-fuel-tanks`, `v1.8.0-w261-landing-pad`, `v1.8.0-w265-laser-gun` and `v1.8.0-w265-rebound-item-model`;
and the NEW-art records `v1.8.0-c16d-acquisition`, `v1.8.0-c17a-fluid-fuel-loader`, `v1.8.0-c18b-jackhammer`,
`v1.8.0-c18b-laser-gun`, `v1.8.0-c18d-new-art`, `v1.8.0-c18d-presentation`, `v1.8.0-cargo-tank-tag`,
`v1.8.0-circuit-plates`, `v1.8.0-control-board`, `v1.8.0-gas-charge-pad-new-art`, `v1.8.0-item-io-board`,
`v1.8.0-liquid-io-board`, `v1.8.0-tracking-circuit-and-rebalance` and `v1.8.0-w53-art-redraw`.

## Root's follow-up decisions

| ID | Decision | Reasons |
|---|---|---|
| R304 | **W274 adopts ADR-062 revision 7 with all 95 REVIEW files as EXCLUDE**: W268's draft at `b5639aee1`, rebuilt on the current shared head, amended so that G7's ten OGGs are excluded too, with the origin findings in the owner's name citing this record, W268's validator guard and fixture updates, and revision 7 accepted on this record. A separate session reviews the adoption before Root merges it. | The owner accepted all eight groups, G7 by choosing the NEW audio. The proposal's adoption steps require a decision record, a rebuild on the adoption head and a review. |
| R305 | **The ten `sound_event` ledger rows close** as IMPLEMENTED in v1.8.0 on the NEW audio, with an evidence file that cites this record, after W274's merge. Root edits the ledger. | D5 is the only hold the ledger gives for these rows. Root closes them after the legacy OGGs they own are excluded, so that no delivered unit keeps a REVIEW file. |
| R306 | **D7's 30 records are set to APPROVED** by a worker task after W274's merge (W276), changing only each record's review status, reviewer, date and basis, with the repository's provenance validators passing. | The owner's review passed. The records share validators with W274's adoption, so the two changes run in sequence. |
| R307 | **The gas charge pad's ledger row (`block:oxygenCharger`) closes on O1's evidence** (W226), and the v1.9 plan gets W174's automatic charging. Root edits the ledger, the status file and the version documents. | Option A, as the owner chose. |
| R308 | **W275 replaces the temporary-looking art** that the owner reported, as NEW art: every block model face that shows a vanilla texture as a stand-in (for example the rolling machine's piston-top front, the ports' concrete and redstone-block tops, the precision assembler's crafting-table front, the guidance computer's lamp top), the drawer-like `machine_casing_side`, and the canister and pressure-tank icons. Ore models keep vanilla stone and deepslate as their base, which is how ores are drawn. A pre-authoring NEW-art provenance record comes first, and the vanilla derivation screen runs on the result. The owner looks at the new art in game afterwards; until then its record stays pending. | The answer to the owner's question about the tank: vanilla Minecraft has no oxygen tank, and AR 1.12's tank icons (`pressuretank0` to `pressuretank3`) are among the files G2 excludes, because their authorship is not cleared. W275 therefore draws NEW icons in that spirit: metal gas cylinders with a valve, the gas or tier marked by colour bands, for the three canisters and the four pressure tanks. |

Root also keeps the following. The risk decisions (the brief's item 4) wait for evidence, now from the single-player
test rather than the campaign. The five loader variants stay PLANNED until their admission. No Gate, version or
release is marked passed.

## W274 file-scope index (derived, not additional owner wording)

This index expands the 95-file scope already authorized by G1-G8 and R304 above. It is a mechanical index from
W268's reviewed proposal at `b5639aee1`, not a new owner statement or an authorship finding. The original words and
decisions above are unchanged. Every listed legacy file is EXCLUDE; G7 follows the owner's amendment choosing NEW
audio. Paths are relative to `assets/advancedrocketry/` at upstream commit
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`. The index lets per-file findings cite this decision record directly while
retaining the validator's exact-asset record check.

| Legacy asset | Group | Source SHA-256 |
|---|---|---|
| `models/item/models/motor.mtl` | G6 | `e42a24530869a054eda7462eb3735553c1ec9a28023c95a7e71120f79dc14db9` |
| `models/item/models/motor.obj` | G6 | `87b7707e5195bf38a5b1d661daa3c0ff74a1cc83af41d702e37628f81d2a7354` |
| `models/motor.obj` | G6 | `02c727b9eb66fc5ece70576845e38e8d9c03bf176c2e114fa14ab84b4fe07046` |
| `sounds/airhissloop.ogg` | G7 | `f22364b53b66314e5377aee1d85bdfdc883dea76bfa67fba0c27ba2bea912f72` |
| `sounds/basiclasergun.ogg` | G7 | `00c97b7f80b8a6204cb999cb68516a67b286350be06a4fa901d0b6819395efc2` |
| `sounds/combustionrocket.ogg` | G7 | `18a34e864d1b56bd937f04498d8e21cf4f82d12147d892fbe9a6e2a0efcab01f` |
| `sounds/electricshocksmall.ogg` | G7 | `004051efb25324f970a607de71b02379131027c461625fa160847200880a384b` |
| `sounds/gravityohhh.ogg` | G7 | `0d70f4fd0d43d94d9c2ec92367cb5334f7f484ba7aeb896a4ce3a79cd96dde64` |
| `sounds/laserdrill.ogg` | G7 | `dbe75ef85cd9191a024e2bffaa0a13a691a8d9d0cc8d5ca311d063ebab4a64e5` |
| `sounds/lathe.ogg` | G7 | `673d70feacea2d3a1031ae13d960879ef79b7eca3539e75a2322fb1190d75a32` |
| `sounds/machinelarge.ogg` | G7 | `05308c20260a3e6fdd995f620a06d7cdb5e709a274dcf88e7598fc84c0c637e1` |
| `sounds/railgunbang.ogg` | G7 | `5b63dfc455678bd85237c64cd2d029dcd03997015be8c12b0197ae5f6004e002` |
| `sounds/rollingmachine.ogg` | G7 | `673d70feacea2d3a1031ae13d960879ef79b7eca3539e75a2322fb1190d75a32` |
| `textures/armor/spacesuit_layer1.png` | G4 | `7ac3d475ba451ee266f19b5e701ee91e77023bb84bde75d5af97f83595fdc3a1` |
| `textures/armor/spacesuit_layer1_overlay.png` | G4 | `b08ac177e1cd4b12e6ffdbb4fe3329bf7ab1f2df394aa434b8b52342efe308d3` |
| `textures/armor/spacesuit_layer2_overlay.png` | G4 | `50cbee4333b2ea9992cb5cd84f6882d85aae9af176e874e7ebdaca9cb346edf8` |
| `textures/blocks/atmospheredetector.png` | G1 | `2e4d6ba0bc4fa1fb2f86ee6bc62456a200a79a4a09c99de6a32ad81f15a721cd` |
| `textures/blocks/atmospheredetector_active.png` | G1 | `fe52a855f30b124784f80d3e4a91d7e7acf4aa83d2c7cbdd3bf1cfeeba5b1e7b` |
| `textures/blocks/beacon.png` | G8 | `4925a6775061f753c3f140b2247fe718385c98f38e926eae73bcc66c0e346673` |
| `textures/blocks/controlpanel.png` | G1 | `7eb980646afc6dea9b2110e003384a4b2e13b3245c39f338b4457f6b02c74da1` |
| `textures/blocks/crystallizer.png` | G1 | `ac9f9ac3df4d64846d2f38bf614a4ffd1d203d6729d436347136dafa16f0d499` |
| `textures/blocks/crystallizer_active.png` | G1 | `bbba5be3f386b0c96137098efa6b9b9efc1963ee48d665ed171c1f0d29b7a4ef` |
| `textures/blocks/cuttingmachine.png` | G1 | `d4bf7f571d5d386befeae2e571e7ff0be2314829b78b175b35d4948c34f2da32` |
| `textures/blocks/cuttingmachine_active.png` | G1 | `d15e5f55c7104b73e1200303b4d7a1b51f962157781f6cedd330471fd8632bef` |
| `textures/blocks/fluid/lava_flow.png` | G5 | `3f9dfe1ee363bec798fb78dffbafe72a605a75491c9cfc11dc2d21471a5f64b2` |
| `textures/blocks/fluid/lava_flow.png.mcmeta` | G5 | `2054ec23771561d5c388c5aa16ec87c9690c220c85ffdb28608868ce8c27d14e` |
| `textures/blocks/fluid/lava_still.png` | G5 | `bc1d47723ef56effb0690d355db63a64efbf563f7c54229bcc85793cbc05f423` |
| `textures/blocks/fluid/lava_still.png.mcmeta` | G5 | `966ddb55dae4ac5960610f1732f0ac36e4952ac95c34f5798413c9cdb60de7f3` |
| `textures/blocks/forcefield.png` | G8 | `e3e8741da6ff8915903b873bd0871a016ff467170bdd25551c1020e2ba32f49c` |
| `textures/blocks/forcefield.png.mcmeta` | G8 | `a148f3e86c9bf40ad4587ee1f11d23b6296875a678a09a48e309371de00a8be0` |
| `textures/blocks/forcefieldprojector.png` | G8 | `f4198630712704b190f8dcc53608e21559d99d7116c42921cd4d19b947021179` |
| `textures/blocks/forcefieldprojector_off.png` | G8 | `f89cd2544369d59224bf0682af8d0a62e1ff6969b2cae5cf9f55a9fce9a20998` |
| `textures/blocks/forcefieldprojectorfront.png` | G8 | `714ffea57623c2481b91575f2d236727c527cc13cc41ba5bf12cf4cdc494030c` |
| `textures/blocks/fuelingmachine.png` | G1 | `6c01999e420211d1dcf1dd8f8a0a52560adf92c3ade9916ccd6abaf258a21b80` |
| `textures/blocks/gaschargertop.png` | G1 | `36e2001bcfd5a31770c8e156e1ce58d59027f669ea812fd56c7f4810dcd82c58` |
| `textures/blocks/guidancecomputer.png` | G1 | `479bc4d9a880d14291b487ed9b28c5920a02b475922f749a3b873b633f6753bd` |
| `textures/blocks/guidancecomputeraccesshatch.png` | G1 | `8cfe6f83c26c71ec8fe134560570145c3c3cc3b8f44f12def5ca08e5a19b2400` |
| `textures/blocks/machinegeneric.png` | G6 | `ebcf3d56473362fb564df1440385cb9c53b7d26bbc6e8c6c456b8e966b361fbe` |
| `textures/blocks/machinenuclear.png` | G1 | `4c3e58810060d76b297e103392a8f3a87a8a2ec53dde43917fe5f41f66ebfad2` |
| `textures/blocks/machineorientationcontrol.png` | G1 | `e0fbe894ccb04ffc5e42257b8566000986d5444446081c2f42c80eda51e02c5e` |
| `textures/blocks/machineprecisionlaseretcher.png` | G1 | `079d0773a6718a705c4d80e7ed65c100d9c98b144b060cb634d5809024c687e1` |
| `textures/blocks/machinescrubber.png` | G1 | `2e25be8a1e92df5c36ad80602d01e6368c2f846c8afc04ce6cd31ee463d02b8c` |
| `textures/blocks/machinescrubber_active.png` | G1 | `fa1df5bc75f9de59963c68f33b778d7039169a666bdcc0e4b8410dcf32642ace` |
| `textures/blocks/monitorfront.png` | G1 | `b3d52d7d3e80d1439050b2bcab238bb93d997e14d3ac0653e980c77989be0eef` |
| `textures/blocks/monitorfrontleft.png` | G1 | `19e623d2e4567af5bcfe12861dafd2f5a8be4e8d9667940f685d9b248d6469bb` |
| `textures/blocks/monitorfrontmid.png` | G1 | `6bb19da207cc674e0910fd02c51d71328cc9a5d22e141e01e55c1b96c26937ea` |
| `textures/blocks/monitorfrontright.png` | G1 | `a2abac551720b4ad5dddfebae07ce2842045e4a40a9ef9ca9becfc8ef101bfa4` |
| `textures/blocks/monitormoon.png` | G1 | `e1a0f069df62789df89fcf3291b96fedee10636a08a9774c98de93d1d92a9230` |
| `textures/blocks/monitorplanet.png` | G1 | `33c861c8ef6dc02dfc99e32959e2e1a8e88413d2fa81cc1e2d1745562187fe90` |
| `textures/blocks/monitorplanet_active.png` | G1 | `08c8f8b1c80b6202b1edd0a86baf7dfd1fb3acaf79806d4db924cc319f196f82` |
| `textures/blocks/monitorrear.png` | G1 | `2cdebf9f65bf5881a20af480b21a71e3cb8623b7aa8afd2dadac5bee7e0f2f65` |
| `textures/blocks/monitorrocket.png` | G1 | `a962906ee9ea08e472a935669a5d8104cf0022e3ad4529d654204fd177dd9923` |
| `textures/blocks/monitorsatellite.png` | G1 | `cf3ffbe147c7dcad7ea847701ef841aae8afb84442f4211636425580e8839e2b` |
| `textures/blocks/monitorside.png` | G1 | `4ed301657f3cd4912136881b8c10cc9ea83fbfa1e078331a4ca2b64ddc73aa7f` |
| `textures/blocks/panelside.png` | G1 | `35feccca5bbfc540f65800fa3115e814d3f5fa8329cccaa115e33555b5803613` |
| `textures/blocks/panelsideworkstation.png` | G1 | `50f6566c68a33131b8f52a9e3d02c2188ed88584a777e5bb7ad902292c2cd779` |
| `textures/blocks/qcrucible_top.png` | G1 | `a78ce54a602e590db4babf78f3e71585a31cf34cd8314a69ba5cb7cc87ef3c6f` |
| `textures/blocks/satellitebay.png` | G1 | `eb014fc79c1de0e9c74cf8d4c1cf855efd75f45f97b0bec72b71801726d92105` |
| `textures/blocks/seal.png` | G1 | `8fe4afc846bc66bee33a77a339f8aef04bf94c0153ebce04523998ab52e9f0c0` |
| `textures/blocks/solar.png` | G1 | `00cc30b275c27d07cc892b5067afb1d810e57208cfa52d4c2b181f0cb3dd43b3` |
| `textures/blocks/starshipcontrolpanel.png` | G1 | `c0b2ce6ff6f2c62b7083149d5f5278eace653bd980ae28d622ea8e3fd525461e` |
| `textures/gui/buttons/buttonautoeject.png` | G3 | `ef0f9a649feb473f255910328edc33b42a468683942511584249904a6d725dc3` |
| `textures/gui/buttons/buttonautoeject_hover.png` | G3 | `623c4f4be6533a4922c10656a835a7d65ef320db803408768534d98b96fb3ef4` |
| `textures/gui/buttons/buttonautoeject_pressed.png` | G3 | `d580e60740446f114397b1a2f5a041cac75114d0fe37b8247918228e1b3ede31` |
| `textures/gui/buttons/buttonredstoneinverted.png` | G3 | `539bf7906d7771ef3026372dada4b24fbc4e67ad5c07c0929c41617fb9b5bc2f` |
| `textures/gui/buttons/copy.png` | G3 | `8c81936c66bc7189c03da6b16887c3b4dffad3a445035a3464f51ca9de3637e1` |
| `textures/gui/buttons/copy_hover.png` | G3 | `c1ae695286d01271e19b291e57f827cce6e39ab6f84b217578350f0ad6dd6e7b` |
| `textures/gui/buttons/kill.png` | G3 | `b1c5ffcb77a9fcefa9133be62e311c5cbb835340523e9f30394fa0ce2a4dac70` |
| `textures/gui/buttons/kill_hover.png` | G3 | `b3ca8f09ae2a56f4c28955567281021a5666131e158899b5d4e648067826d07f` |
| `textures/gui/buttons/tabdata.png` | G3 | `fa4dbafc139c1bb29e09460e82485614042c88b4a080ea6ae10566603d4b8431` |
| `textures/gui/buttons/tabdata_hover.png` | G3 | `ca38c3377f3355c543e2f06d1ac892168ab0a6f6361c5d7c310ed65b022f909c` |
| `textures/gui/buttons/tabguidance.png` | G3 | `7d80ef35a7f6115e7662172548b568ee1f0a8780a11a05c73f3dc88078dd4e3c` |
| `textures/gui/buttons/tabguidance_hover.png` | G3 | `6d4b9503f33afd391f40e7b5f4c22a25ba9f7e22a8b6b794cf542996033047c1` |
| `textures/gui/buttons/tabplanet.png` | G3 | `0aff01d6c190e70c2ac712c04a9f89c011519cab9c260389d9fac0b0f50523d6` |
| `textures/gui/buttons/tabplanet_hover.png` | G3 | `991d7b0212fc3dccc40776aebf7ea79550edb9e2b925d0c25f5adc0c63cdb23b` |
| `textures/gui/buttons/tabwarp.png` | G3 | `80523778f556ea8971b1e339690bb00fc9c86554e501d4baec5d4c187604b4d0` |
| `textures/gui/buttons/tabwarp_hover.png` | G3 | `8c2f7575e84c4fcadd763a9679063e31edf80ea17c36f99e279d14f3ba6c89cd` |
| `textures/gui/warning.png` | G3 | `88c971a06bf2c4712e5640b834be1a82633e87e6372859d2c68a52882cc742f4` |
| `textures/items/antifogvisor.png` | G2 | `2eaaea45656663d6c2a1f7124d60d88b80f82664b922fb29670adbde442c06e2` |
| `textures/items/atmanalyzer.png` | G2 | `d5f04ceff835ad42a8af8fb4581eb1bf0e46c3750c470dd04d292d7370270be4` |
| `textures/items/beaconfinder.png` | G2 | `a19f794dd32c3904c00f346bd248f8efb65de7d76b39da43e71c90c2edd628e7` |
| `textures/items/bioniclegs.png` | G2 | `90df3b82a637fc50b824219fd9e27fb1d18f510d4c8fbd61ab2212219602fb7b` |
| `textures/items/earthbrightvisor.png` | G2 | `b5d40920ce9226c91e6bb674039cc00132b0574a3a72d0a4b81d1c1ebbd0290d` |
| `textures/items/landingboots.png` | G2 | `2f1806a72adf80f6b1032dfa56ada9d4aa25e74c68423bc17cffae4510f18223` |
| `textures/items/pressuretank0.png` | G2 | `e96889fbdc01a33bf5a66cbe7f8d6827e4ab4f78b8f0f2e3485ef2de16a4f22b` |
| `textures/items/pressuretank1.png` | G2 | `41aba3582ad64108ef1ce8537c48d8bb5bb8309eb448085bf30cf11102e81a96` |
| `textures/items/pressuretank2.png` | G2 | `94bd41a30b07f67166b3c637539ff77ec47c7bf2635b6fe07e675c64c5f9e585` |
| `textures/items/pressuretank3.png` | G2 | `e799e994113f943677ff8165c8572f27d7cfedd06e6c845c9776cd23a534d02e` |
| `textures/items/sawbladeiron.png` | G2 | `7d905b07ed70b77f319b633c45194af7840bf778c610b88024f3192eb32594a7` |
| `textures/items/spaceboots.png` | G4 | `79ce28db42f00c86f90f99140ac3be60168ed0fcaf69452445e1dad6bb1bb5f6` |
| `textures/items/spaceboots_overlay.png` | G4 | `8a4c62941843cd40a57cbded184ccc1e6eeafb0e6c3cca73abe61cd513a189a5` |
| `textures/items/spacechestplate.png` | G4 | `a84114f2315e4bc70cc04092f0834f113ce8016bea3b1c30b23068c76d46572c` |
| `textures/items/spacechestplate_overlay.png` | G4 | `2c56f35586d4f8e87d2d8768b6985466e7c7df6a1f487ceb7840170b8e08e800` |
| `textures/items/spacehelmet_overlay.png` | G4 | `51ae33ded2c3cc1d5ca117b1f6940b42d7bfb5bebc9f415c8f310ccf948d669e` |
| `textures/models/beacon.png` | G8 | `70ba37771e7f0c5ff2a009b6f2ea875a52c3f71de7abb63268aded87b11c4ed1` |
