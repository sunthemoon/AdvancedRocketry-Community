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
