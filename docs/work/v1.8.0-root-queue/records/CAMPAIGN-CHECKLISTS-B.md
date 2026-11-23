# Final campaign checklists B: expansion kernel

Status: planned, not executed. Covers B01-B07 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.2. The v1.1.1
maintenance document creates no additional campaign row and does not discharge
these deferred tests.

Accepted common procedures for **every B row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared execution requirements

Use the candidate, authentication, driver markers, restart protocol, result
metadata and section-6 evidence naming specified in
`CAMPAIGN-CHECKLISTS-A.md`. P1 pins C6 (R581 names
`c8fa16e16`), not a historical v1.1 artifact. All rows use ordinary disposable
worlds, **not O2's marked world**; production switches stay off. No JVM test
properties are required. Default combination: Java 17, Minecraft 1.20.1,
Forge **47.4.10**, **JEI absent**. Record any additional 47.4.23 or JEI
15.56.0.205 run as a separate cell. DS has a real client on the development PC;
V1 requires its real GPU; V2 requires two independently authenticated accounts,
not FakePlayers. No test is run by writing this document.

P2 supplies **a v1.0 world**, **the reference workload world of docs/17
section 4**, and immutable fixture hashes. Derived scenes and valid/invalid
packs need a P2 manifest with paths, positions, UUIDs and before-state; sources
do not provide a single ready-made campaign fixture for every row. Use copies.
The version sources are [v1.1.0](../../../versions/V1.1.0-EXPANSION-KERNEL.md)
and [v1.1.1](../../../versions/V1.1.1-MAINTENANCE.md).

## Package preparation index (W456, R649)

This index resolves the package notes below; it is preparation, not execution or a Gate verdict.
Each entry gives the sealed input, its checksum-list SHA-256, the recipe/step mapping and the
remaining partial or NOT_COVERED work. Paths are external read-only inputs, never repository links.
No package delivery remains in progress. Root accepted K02 as partial under R661/R664/R667
and K12 as partial under R655; their uncovered observations and unexecuted client/REF work remain open.
A delivered recipe is not an observed client result; preserve every original action, oracle, result field and budget.

### B-K02: historical sources and partial forced-stop delivery

Sources are [W431](W431.md), [W437](W437.md), [W447](W447.md) and [W461](W461.md) through [W465](W465.md).
Root accepted the source preparation under R611, W437 under R631 and W447 under R661 as partial K02,
the UNKNOWN reruns under R664, the flight classifications/protocol under R663/R665/R666, and W465 under R667.
These decisions are recorded in [QUEUE](../QUEUE.md); K02 remains partial, not a campaign or Gate pass.

| External checksum list or retained run index | SHA-256 of the listed file |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/SHA256SUMS.txt` | `06bf9946a02e6bda58577811c9c7b69ca84fb779f38f14fa7843fd6d42f357ab` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R612-W437-restart-20261122/SHA256SUMS.txt` | `e5078a1c2153df3987d0f81f9548fa61ee3ba7000377147bd4b2a4205a9c5572` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R631-W447/inventory.json` | `c8665bd53513e81f80c4597040c9f69015a4d64ab0d8fac623e8f01752c7cc6e` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W461/inventory.json` | `af675b05ab8ec48d79350af5c021dcb0210223ba74132c23014d8b69756d5a29` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W461/control-010-attempt-2/inventory.json` | `102e2d105a0bac03b269cc9ab3b825b93c87231684a44c9d317d3dff71a8ba1f` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W462/artifact-hashes.json` | `6ac00d083f1499a747d521cd150a3700bfb3cb2cb5e646242fa4de9f93ff609e` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R663-W463/corrected/summary.json` | `929418496c6d9ea7b87453ba13556c971bc1657047a1c4035b85cb4cf22cda9e` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R665-W464/artifact-hashes.json` | `25533a156c47d2fbcd33cc58139eab6a47c657024bde68e540e5ca11ab5e0ce4` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R666-W465/artifact-hashes.json` | `9833228907a1e374f6af61da810732ba967b69705c9a63457505a139df878efe` |

**Recipe and original-step mapping.** The K02 root above contains each source's `manifest.json`, `source-world/` and qualification directory.
FI01 maps to A03 and retains a scripted Beta archive, not a historical player world.
FI02 maps to B01 and is reconstructed on the pinned v1.0 JAR, not historically played.
FI03 maps to B04 and is a reconstructed, flushed TRANSIT_PREPARED source with no passenger; its C6 copy-load proves readability, not completed flight recovery.
FI04 supplies the pre-sky/API core baseline but not the missing historical adapter/custom-payload branch.
FI09 maps to I03 and reconstructs all 30 old blocks, 57 items and 19 block-entity types at 24 positions on pinned v1.7.
W431 records the exact layouts, host JAR hashes and copy-load qualifications; none of these inputs is a played-world-history claim.

For A04, `R666-W465/run.py`, `summary.json`, `baseline-forced-chunks.json` and `baseline-world-identity.json` identify the flight run of record.
It has **7 PASS and 3 FAIL: NOT_REACHABLE_ON_C6** under R666's loaded-ends contract, accepted under R667.
The uncounted baseline persists only Overworld chunk (24,24) and Moon chunk (0,0); no counted case adds a chunk.
All four modes (`--wait-ready-status`, `--sealed-disassembly-receipt`, `--journalled-launch-receipt` and `--settled-disassembly-protocol`) are explicit.
The 30-second pause and 45-second disassembly budgets, identity/fuel/authority checks, SEALED/DONE receipts, five restored blocks and exact inventory oracle remain unchanged.
This is an unmanned stand-in for a nearby requester, not connected-player or passenger qualification.

W447's `R631-W447/native/plan.json` and `native/summary.json` cover all 89 direct sealed-disassembly rows, with 81 PASS and 8 FAIL in that attempt.
W461's `R662-W461/native/summary.json` and `control-010-attempt-2/cohort-summary.json` add passing attempts for all eight UNKNOWN rows and both controls under R664.
Together they give a passing C6 attempt for every one of the 89 rows, not an all-attempts pass or an integrated-flight proof.
The eight original UNKNOWN/log-gate failures remain failed; W461 also retains control 010's first failed Yggdrasil key-fetch attempt separately from its authorized second PASS.
W461 permits exactly one matching intentional UNKNOWN ERROR only at the selected UNKNOWN stage; all other log and recovery/resource checks remain.
W437's fresh transaction matrix remains 13 PASS and 12 NOT_REACHABLE_ON_C6.
W447 maps eight of those twelve legacy disassembly purposes to sealed semantic counterparts, without relabelling a legacy checkpoint as passed; the full mapping is in checklist A, entry A-K02.

**Remaining coverage and reasons.** W437's flight 2 PASS/8 FAIL, W447's flight 5 PASS/5 FAIL and W463's corrected flight 5 PASS/5 FAIL remain counted history, not rewritten by W465.
W447's DESCENT/LANDED cleanup failures and W463's unloaded-destination rejections are HARNESS findings under W462/W464, not counted passes or a diagnosed PRODUCT defect.
DESTINATION_SPAWNED, PASSENGERS_TRANSFERRED and SOURCE_REMOVED remain failed NOT_REACHABLE_ON_C6 cuts: the clean-journal pause cannot stop before C6 dispatches the next acknowledged phase.
RESTORING:3, RESTORING:4, RESTORING:5 and FAILED:1 have no exact sealed counterpart; all twelve original legacy rows remain NOT_REACHABLE_ON_C6.
Genuine adjacent-chunk entity arrival, current-seat selection and native 200-tick expiry remain separate NOT_COVERED observations because K02 has no genuine controls for them.
Passengers, real clients, played historical sources and the missing historical adapter payload remain outside K02's qualified coverage.
[KI-V180-DISASSEMBLY-01](../../../releases/v1.8.0/KNOWN-ISSUES.md) records the support boundary: wait for settlement, make a fresh request after preparation, reconfirm exact fuel disposal and stay online in the same dimension within eight blocks until completion.
This guidance and R666's baseline do not qualify real-player behavior.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B01 | FI02's manifest, source-world and qualification-final directory supply the reconstructed v1.0 normal source under R611. It is not historically played; passenger and player-session observations remain unqualified. |
| B03 | FI02 supplies the reconstructed v1.0 source under R611; K06 supplies the route packs. K02's copy-load is not a live reload, quote or two-restart route observation. |
| B04 | FI03's manifest, source-world and qualification-final directory supply the reconstructed v1.0 in-flight source under R611. Its flushed TRANSIT_PREPARED copy-load proves readability only; real passenger/current-seat recovery and repeated campaign reopens remain unqualified. |

### B-K06: 23 hashed data/resource packs, not blanket live qualification

Source: `W406.md; QUEUE.md row W406`. Root acceptance/disposition: **R550** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/SHA256SUMS.txt` | `3be8336be6e1aed8fc2859323d0ecbae9b7209f2fc8048a09944407a45992ca5` |

**Recipe and original-step mapping.** manifest.json packs[].path/rows/prerequisites/load_check and campaign_values give the per-row reload sequence: B03 route baseline/valid/invalid/unknown; B06 test_mars/locked; E01 itinerary; E05 100/128/129 and ID bounds; F05 station overlays; D04 tag pair; D08/D09 payload pair; E06/E11 sky resource packs. Run load_check at the existing row step and retain actual generations/quotes.

**Remaining coverage and reasons.** candidate_loads_run=false in this manifest. K21 separately qualified tag/payload loads, K08 loaded station baselines and K07 binding refusal; those do not prove B03's live quotes/two restarts, every catalog, sky load, removed selection or cooldown. Unperformed branches remain NOT_COVERED. Invalid packs are refusal-only, never setup. The 48-character value is a station name. Preserve the manifest's recorded generator commit and C6 JAR identity.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B03 | manifest.json packs[path=route_baseline.zip/route_valid.zip/route_invalid_reference.zip/route_unknown_body.zip].load_check -> original reload/rejection/two-restart actions; preserve graph and quote distinction. |
| B06 | packs[data_only_test_mars.zip/route_locked.zip/route_discovery_rule_open.zip].load_check -> original removed/locked menu steps; a missing-body rejection is not a removed selectable node. |

### B-K08: station sets, two-account recipes and ordinary-energy copies

Source: `W440.md; W442.md; QUEUE.md rows W440/W442`. Root acceptance/disposition: **R621/R627** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/two/SHA256SUMS.txt` | `021440c6b39bd9864ba5106358b454e2f105103b85e2ecf3b2cbe4acb646ee52` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/ten/SHA256SUMS.txt` | `0919948566148e793440c0bade53006f0e2486f41b485ce7210ad9149d7cc1e2` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/hundred/SHA256SUMS.txt` | `1089dab5a1f238eb955d3d751fb2d0206b9bccebe696418ad87bb0b21ba7c773` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/client-steps-addendum/SHA256SUMS.txt` | `405e16eea3273dec74e4f2c2904be59cd878bd4e5729b6c204aa1a0f305f8847` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/two/SHA256SUMS.txt` | `9e11421235fa268c33b421cb22e6eb665e3852a9f0b610cd65e5e54de4d9d693` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/target-removal/SHA256SUMS.txt` | `79f68839d090a25598c13197607cba0a5bb858fa67e703e79ce6110f95fbdb4c` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/cost-change/SHA256SUMS.txt` | `1c2bb1169b55c9f682a685b1b66b8c88344999ce6c84a4b1b19e86d345eeba80` |

**Recipe and original-step mapping.** K08 manifests stations/regions/commands and client-steps-addendum/client-steps.json variants[].client_steps.actions/approach/ordinary_creation/overlay/pending/docked_rocket map B02, E05/E09/E10 and F01-F07. Use K08 ten/hundred with k08_station_load.py for F01; use a fresh K08B two/target-removal/cost-change source-world per paid F02/F03/F05/F07 operation, retaining the same bound station IDs.

**Remaining coverage and reasons.** K08B closes only energy preparation: two A/B 3,288,096/2,902,914 FE; removal A 2,756,374; changed-cost A 8,381,014. Operator-created stations and placeholder owners are not ordinary acquisition/O5 evidence. Bind by fresh command-built outputs, never editing sealed owners. Live local consent/warp, memory-only countdown, F05 before-due overlay and two post-abort restarts, docked rocket, membership/refusals and V1/V2 remain scripted/NOT_COVERED (no saved pending or docking command). Full REF windows and direct caches remain unqualified.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B02 | K08/two/manifest.json regions/station IDs and addendum variants[two].client_steps.actions/approach -> original two-context, unload/reload and client views. |

### B-K15: bounded observers, with explicit remaining visibility gaps

Source: `W403.md; QUEUE.md row W403; W452.md/R648`. Root acceptance/disposition: **R548; R648 for A08/A09** in `../QUEUE.md`.

**Recipe and original-step mapping.** Tracked scripts/campaign_observe/{ticks,process,jdk,host,evidence}.py and scripts/run_campaign_observer.py at implementation 385c079dc10718f781a425527cd3e9a0765a0518 supply matched-copy window/process/heap/JFR/evidence operations. Use sample_ticks/force-load queries and owned-process-only diagnostics at the row's existing sampling step; K17 schedule and K08/K09/K10 load drivers supply concrete harness integrations.

**Remaining coverage and reasons.** W403 created no sealed live fixture or SHA256SUMS.txt: none is invented. Window aggregates cannot prove whole-run percentiles/spike lengths. C6/K17's release-test raw sampler is used only in the separately labelled R648 REF run for A09. All-Forge-ticket, queue-internal, client/GPU allocation and direct cache-release proof remain NOT_COVERED where unavailable; histogram/heap/RSS proxies are labelled, not direct evidence. Actual REF acceptance/full windows and overhead checks remain unexecuted.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B07 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |

### B-K16: v2 core scene, mapped controls and conditional recheck batches

Source: `W427.md; W443.md; QUEUE.md rows W427/W443`. Root acceptance/disposition: **R595; R628 supersedes final/ with v2/** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/SHA256SUMS.txt` | `27df12a711d23f500dd7370fab35a6c5d457f6eebd93e8366ee191889b140fa9` |

**Recipe and original-step mapping.** v2/batch-index.json maps each row/step/file; manifest.json core_route/devices/supplies and candidate-recipes.json give counts/positions/recipes. A02/I01 use core-00/core-01, core-room/core-suit, core-rocket-assemble/-fuel/-invalid, core-flight-plan/-observe; A06/A13/A17 use core-station/core-screen-*; E07 uses sp-pause; J20/J21 use rechecks.json and batches/j21-*.json. controls.json gives effective viewport targets, not old pointer replay.

**Remaining coverage and reasons.** R628 accepts only the en_us 1280x720 scale-3 dedicated rehearsal; scale2/fullscreen/zh_cn, integrated pause and 63 J21 requests are generated, not rehearsed. Fullscreen screen gate needs checking. B-5/3-2/8-2 remain checkpoint-only; no complete continuous quantitative crafting witness or all-screen/first-join/dual-seat/station ownership-transfer procedure is supplied. Return flight/paid warp/satellite flow, O5 accounts, N15-N19 physical setup and V1/V2 remain partial. Never replay old NBT-writing setup; R638 applies. Desktop-wide Alt/Shift are the recorded night-round operating condition.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B04 | core-flight-plan/-observe -> ordinary present-day flight input only; historical in-flight source/controls stay K02 and are not replaced. |
| B05 | Core route scene does not supply server receipt ticks or a complete two-client target-selection fixture; unproved same-tick V2 remains NOT_COVERED. |
| B06 | controls.json/core flight menu input -> original destinations/removed-selection steps; no live pack/menu qualification is inferred. |

### B-K17: combined reference world and four-hour schedule

Source: `W452.md; QUEUE.md row W452`. Root acceptance/disposition: **R648** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/source/SHA256SUMS.txt` | `d03941ecdb4bff0d1742cf452959293b951e2e26b2972d8983e6f5c0ccd3daf1` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/readback-settled/SHA256SUMS.txt` | `a4aba18f3649e82a8e00e5d20f0efc508ad3c35c8b21357938a9811c7efc3df4` |

**Recipe and original-step mapping.** source/layout.json maps the 16 Moon rooms, ten station IDs, mission records and 2048/64/64 pads. source/schedule.json actions and client_steps.{connect,assembly_2048,flights,missions,a11} map A08-A11/I05/I10; use k17_reference.py schedule/run on disposable copies. Two 7200-s sampled segments, 120-s warm-up, flushed saves each 1800 s, one restart at sampled 7200 s, autosave retained.

**Remaining coverage and reasons.** R648: A08 has no test properties; keep forge tps means/RSS/GC/forced chunks. A09 is a separate labelled REF run on the same world/schedule adding only -Dadvancedrocketrycommunity.releaseTestHooks=true for the read-only raw sampler; compare forge tps means under C10's baseline-spread review rule. The 16 forced room chunks are declared workload, not unexpected tickets. Without clients flights/disassembly/dimension transfer remain scripted; ACTIVE missions fall from 101 to zero within ~6000 ticks. Record actual counts and ordinary refills, not a sustained-load claim. K17 does not supply sustained 500/1000 mission or H07 endgame throughput. Full four hours/REF/two real connections/all-ticket/direct-cache evidence remains unexecuted.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B07 | source schedule matched sampled segments -> original cache/full-load comparisons; no direct cache counters or end-to-end B workload qualified. |

### B-K18: bounded client intent JAR and case/readback matrix

Source: `W432.md; intent-test-mod/README.md; QUEUE.md row W432`. Root acceptance/disposition: **R601** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS.txt` | `aac26e987be0617ad63cec39b54aefb0fe7f3372085863ab14cd49731e392e57` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS-CORRECTED-1.txt` | `d29b209b8e7133f2d50f8463d3d9a1fa616572b2fe8b919ddbe58de254ec188f` |

**Recipe and original-step mapping.** Install arce-intent-test-1.0.0.jar only on the probing client. intent-test-mod/README.md Triggering one case steps 1-6 and Case table map C08/D09/I11 flight_stale/range/non_owner/late and classic_stale/range/non_owner/late; E03 flight_forbidden_surface/private_station; E07 late and motion_disabled. R/C/M/L readbacks are required separately. F8 captures; a single F9 queues at most one bounded packet.

**Remaining coverage and reasons.** Use SHA256SUMS-CORRECTED-1.txt: the original list's final-validator hash is stale and preserved, not silently repaired. There are three C2S channels, five S2C-only; no payload-terminal, research-menu, callback-fault, packet-burst, late-S2C/cache, all-ticket or same-tick claim control exists. Those branches remain NOT_COVERED with the README's named A0/A1 references/gaps. Client queue log is not server receipt. Actual real-client/server qualification and unchanged-state/loaded-chunk readbacks remain campaign work.

**Mapping in checklist B.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| B05 | No G03/B05 server arrival-tick observer; client queue timestamp cannot prove same tick. |

## B01 - Continue a v1.0 world after upgrade

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary
world, switches off, no properties. **Source:** v1.1 section 10.1.

**Preconditions:** Copy P2 **a v1.0 world**, including a fueled Earth/Moon
rocket, station and satellite/research state; baseline Forge/JEI absent.
Capture the source's target IDs, flight journal, station orbit/rights, mission
and resource projections before first C6 load. This is distinct from A03's Beta.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Start the copied world on C6. | Supported upgrade; stable identities and retained original data. |
| 2 | [H] Travel Earth to Moon through the production rocket console. | Correct named target and one authoritative fuel debit; no default-Earth fallback. |
| 3 | [H] Return through the same production flow. | Earth/Moon behavior remains usable with intact cargo/seats. |
| 4 | [H] Travel to the existing authorized station. | Same station UUID, membership and orbit identity. |
| 5 | [H] Continue the saved satellite mission/claim flow. | Captured mission/reward state retained; no second award for an old claim. |
| 6 | [H] Save/stop the copied server. | Clean exit and complete saved projections. |
| 7 | [H] Reopen the same copy. | No repeated migration/resource reset. |
| 8 | [H] Compare the saved subsystem projections. | IDs and resource balances reconcile, including legitimate processing. |
| 9 | [H] Repeat steps 6 to 8 for the second restart. | Idempotent second reopen. |
| 10 | [H] Rehash the original and backup. | Neither source was changed. |

**Accepted preparation/action steps (W408):**

(C7) Entry B-K02 pins FI02's normal v1.0 source and FI03's flushed TRANSIT_PREPARED source, with manifests and C6 copy-load qualifications accepted under R611. Both were reconstructed on the pinned historical JAR; FI03 has no passenger and its copy-load proves readability, not completed recovery. Label each rebuild "reconstructed on JAR (hash), not historically played"; it proves format migration, not played-world history. H02 covers the legacy-passenger branch only. Where no retained played in-flight source exists, that claim is NOT_COVERED for that reason. Never edit earned balances, IDs or binding history.

**Evidence to keep:** `phase3-ds/B01/` source/copy manifests, before/after
target/schema projections, each open log and exit, flight/station/claim receipts,
resource ledgers, client screenshots and original-hash comparison.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B02 - Two orbit contexts inside shared Space

**Header:** Phase 2 V1 and phase 3 DS; development PC, real GPU client/account
A and station owners defined in the fixture; ordinary world, switches off,
no properties. **Sources:** v1.1 sections 10.2 and 11.2.

**Preconditions:** P2 v1.0-world-derived copy with two stations in the same
shared Space Level, one orbiting Earth and one the Moon; baseline/JEI absent.
Use different fixture owners where ownership limits require it. Manifest gives
the two station UUIDs, disjoint regions, members, positions and orbit-body values.
Do not assume that Space's Level ID alone determines either body's environment.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Enter the Earth-orbit station as an authorized account. | Station context names Earth; region/environment agree with its record. |
| 2 | [H] Open its station/rocket interface. | Correct station and orbit body, not the Moon station's display. |
| 3 | [H] Enter the Moon-orbit station as its authorized account. | Same physical Space dimension but different station/orbit context. |
| 4 | [H] Open that station's interface. | Correct Moon orbit information and independent permissions. |
| 5 | [H] Attempt a request with the other station's non-member fixture account. | Permissions do not leak between the two regions. |
| 6 | [H] Save/stop the server. | Both station records preserved. |
| 7 | [H] Reopen and revisit each region. | Correct context and rights at each station. |
| 8 | [H] Repeat steps 6 to 7 for the second restart. | No cross-station state after reload. |
| 9 | [P] Review the two orbit displays against server records. | V1 visuals agree with the correct station's body; station climate is not a planet surface hazard. |

**Accepted preparation/action steps (W408):**

(C8) Each real account owns one station by ordinary creation. For 10/100-record loads use permission-2 `/arce station admin create`, with a distinct owner UUID per station and an operator-created label, never a per-owner bypass. Preparation is required for the exact regions, UUID/energy manifest and commands. Entry B-K08 (R621/R627) supplies the following inputs: station sets, two-account recipes and ordinary-energy copies. Ordinary account binding, consent/warp/docking/membership actions and the listed live/REF observations remain open.

**Evidence to keep:** `phase3-ds/B02/` region/context/rights projections and
restart logs; phase-2 screenshots of each body's label/sky and the permission
response. This does not replace B05/A06's actual two-client results.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B03 - Atomic route reload and rejection

**Header:** Phase 3; DS; development PC, one real client/account A and console
operator; ordinary copy, switches off, no properties. **Source:** v1.1 section 10.3.

**Preconditions:** P2 v1.0-world-derived copy; baseline/JEI absent; W406's hashed baseline/valid/invalid packs listed below, plus originals.
The direct Earth-Moon definition changes 50 to 75, while the shortest graph
plan remains 50 via two orbit legs. Live quotes/readback still require qualification. Entry B-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the active route catalog and current quote. | Baseline generation, target and server cost available. |
| 2 | [H] Install the valid route pack on the disposable copy. | Only intended fixture files change. |
| 3 | [H] Issue `reload` on the server console. | Valid route/catalog candidate is published. |
| 4 | [H] Reopen the destination interface. | Quote/reachability follows the new valid route data. |
| 5 | [H] Replace that pack with the invalid candidate. | Recorded invalid fixture, not an arbitrary production-pack edit. |
| 6 | [H] Issue `reload` again. | Reload rejection with bounded diagnostic; previous valid catalog remains active. |
| 7 | [H] Request the same destination quote. | Last valid target/cost retained; no empty catalog or fallback destination. |
| 8 | [H] Restore the valid pack. | A valid on-disk pack is present before restart. |
| 9 | [H] Issue `reload`. | Recovery to valid definitions without data loss. |
| 10 | [H] Run two clean restarts of this same copy using checklist A's protocol. | Route state and persisted targets remain correct after each. |

**Accepted preparation/action steps (W408):**

(W406) Begin with the baseline, then replace it with the valid variant; test invalid-anchor and unknown-body packs separately only **after** a valid load, restoring valid files before restart. Capture `/arce celestial route advancedrocketrycommunity:earth_moon`, catalog generation, direct definition and selected plan legs plus quote. Direct distance changes 50→75, but two 25-unit orbit legs keep shortest distance 50: do not require a +25 fuel increase or infer cache freshness from price alone. This refines step 4 to the actual graph, not an invented quote. Preparation is required for the live load, quotes/plan readback and two restarts; entry B-K02 supplies FI02's pinned reconstructed source manifest and qualification-final input under R611, not a completed live route-reload test. Entry B-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `route_baseline.zip` | `5734f7f9256106b1d919551e2013b99c9c92e20be4ee65ecfa0c305cab2f77e5` | A; original bidirectional Earth–Moon surface route, distance 50; 11 bodies/12 routes. |
| `route_valid.zip` | `af571df269d2ae39492c62b051ea2797986f9d585f7e3770e83b612d47b2a9b9` | A; direct route distance 75; alternate 25+25 path keeps the shortest distance at 50. |
| `route_invalid_reference.zip` | `709509268fba3b398acbc77ac3609c11aaf6840c64e60caf62e781f7cb781705` | R; `Unknown route anchor type advancedrocketrycommunity:invalid_anchor`; retain valid pair. |
| `route_unknown_body.zip` | `5891700045f94b37c6ac4e75b7ec9dae7135a4ca39cda53a8ab22c13d2d9123c` | R; route `advancedrocketrycommunity:earth_moon` references missing body `k06:unknown_body`. |


**Evidence to keep:** `phase3-ds/B03/` pack bytes/hashes, catalog/quote
projections, all reload diagnostics, client feedback and restart comparisons.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B04 - Restart a player's in-flight target

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary
copy, switches off, no properties. **Source:** v1.1 section 10.4; section 8
also requires old in-flight transactions to finish under their original semantics.

**Preconditions:** P2 v1.0-world-derived copy, legal fueled rocket owned by A,
recorded stable target ID and route; baseline/JEI absent. Use the current C6
schema/protocol, not the historical flight=2/protocol=6 numbers as a C6 pin.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Select the fixture destination through A's rocket console. | Stable typed target and authoritative quote recorded. |
| 2 | [H] Confirm launch. | Durable active plan/target and fuel ledger created. |
| 3 | [H] Capture the in-flight phase, target and journal. | Before-state identifies the exact checkpoint. |
| 4 | [H] Save/stop while that flight is still active. | Normal restart case, not a claimed power-loss cut. |
| 5 | [H] Restart that same world. | Target ID and original plan semantics retained. |
| 6 | [H] Reconnect A. | Authorized recovery and one rocket/seat authority. |
| 7 | [H] Observe completion of the recovered flight. | Intended destination, not automatic Earth or another session's target; one debit. |
| 8 | [H] Save/stop and restart the settled world once more. | Second restart preserves destination, assets and resources without replay. |

**Accepted preparation/action steps (W408):**

(C7) Keep the ordinary C6 restart separate from upgrading K02's historical in-flight copy; H02 is legacy-passenger-only. A rebuilt source is labelled reconstructed on its hashed JAR, not historically played; the retained-played-source claim is NOT_COVERED if unavailable. Entry B-K02 supplies FI03's sealed manifest and qualification-final input under R611; this is an unmanned reconstructed TRANSIT_PREPARED source, not a played-source or completed-recovery claim. Preparation is still required for the current-flight timing/controls. Do not edit earned balances or identities. Entry B-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.

**Evidence to keep:** `phase3-ds/B04/` selected/active/recovered target
projections, journal/fuel/seat comparisons, disconnect/rejoin logs and screenshots.
An ordinary flight is not evidence for upgrading a historical in-flight fixture;
that missing migration fixture must be reported separately.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B05 - Concurrent independent target selections

**Header:** Phase 4, sitting 1; V2; authenticated real accounts A/B and two
clients; ordinary world, switches off, no properties. **Source:** v1.1 section 10.5.

**Preconditions:** Baseline/JEI absent; P2-derived scene with a separate legal
rocket and permitted destination per account, distinct target IDs and enough
fuel; manifest binds each account, rocket, menu and target. O5 supplies concurrent
input to the two signed-in windows; no FakePlayer substitutes.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Open A's rocket destination interface. | A's rocket/menu identity recorded. |
| 2 | [P] Open B's distinct rocket interface. | B's independent rocket/menu identity recorded. |
| 3 | [P] Select the two different targets concurrently on the coordinated cue. | Each client retains its own choice and quote; log the actual input/receipt times. |
| 4 | [P] Confirm A's launch. | A's plan contains A's target and costs. |
| 5 | [P] Confirm B's launch. | B's plan contains B's target, unaffected by A. |
| 6 | [H] Compare both plans and arrivals against the initial account map. | No target/session/fuel/seat crossover. |

**Accepted preparation/action steps (W408):**

(C9) Keep each intent's actual server receipt tick, not just W399 input/completion timestamps. Claim "same tick" only if both server receipts prove it; otherwise report the tick gap, check independent plans/one settlement with no gain or loss, and mark the same-tick V2 branch NOT_COVERED with its named A1 test. No overlap tolerance exists. W399 supplies session records; server-tick correlation still requires a qualified method if ordinary receipts are insufficient, and the scene still requires preparation. Owner O5 supplies the second input operator. Entry B-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open. Entry B-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.

**Evidence to keep:** `phase4-v2/sitting-1/` both videos/logs, coordinated
step table and server plan/arrival projections. Sources give no millisecond
concurrency tolerance; record observed simultaneity rather than invent one.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B06 - Readable destinations, locked routes and test content

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off, no properties. **Sources:** v1.1 sections 11.1, 11.3 and 11.4.

**Preconditions:** P2 v1.0-world-derived scene with Earth/Moon travel, one
unknown/removed destination, one locked route and the data-pack-only `test_mars`
body of v1.1 section 6.9; baseline/JEI absent. Record its actual namespaced ID
from the pack, not the later built-in Mars. English/Chinese, scales 2/3/4 and
windowed/fullscreen follow the campaign V1 environment.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Open the destination interface on the Earth/Moon route. | Readable names rather than raw internal identity as the player-facing label. |
| 2 | [H] Select the unknown/removed destination fixture. | Readable refusal, no automatic fallback or asset loss. |
| 3 | [H] Select the locked-route fixture. | Lock and reason visible; no unauthorized launch. |
| 4 | [H] Install the valid `test_mars` data-only pack. | No Java enum or production code change. |
| 5 | [H] Issue `reload`. | Valid test body/routes appear through data. |
| 6 | [H] Reopen the destination interface. | Test body discoverable and explicitly identified as test content. |
| 7 | [H] Complete the old Earth/Moon selection/launch flow. | No visible or functional regression. |
| 8 | [P] Review all language/scale/window cells. | Names and refusal/test labels remain readable; no critical clipping. |

**Accepted preparation/action steps (W408):**

(W406) The unknown-body ZIP is a catalog **rejection**, never setup for a selectable unknown node. For an actually removed destination, hold `advancedrocketrycommunity:test_mars`, remove its valid pack and reload; capture actual menu/refusal behavior. The data-only fixture is not built-in Mars and creates no Level: accepted catalog data is not proof of surface travel. The locked pack uses built-in undiscovered Mars with an otherwise eligible flight; expect DISCOVERY_REQUIRED. Preparation is required for the live loads/removed-selection recipe; preparation is required for the bound menus/display controls. Entry B-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications. Entry B-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `route_unknown_body.zip` | `5891700045f94b37c6ac4e75b7ec9dae7135a4ca39cda53a8ab22c13d2d9123c` | R; route `advancedrocketrycommunity:earth_moon` references missing body `k06:unknown_body`. |
| `route_locked.zip` | `ddfc89cb8eb868e55c193977b403e2a9cf7db4d02a8a2fd0eb38a1bf577d0ac7` | A; built-in Mars requires discovery; otherwise eligible undiscovered flight returns `DISCOVERY_REQUIRED`. |
| `data_only_test_mars.zip` | `743981cae448942dd226ff73d0fb0d85bd3662dacb7404df6bf9ef7378c9b323` | A; exact tracked body/route seeds in real loader directories; 12 bodies/13 routes; distance 225, bidirectional. No new Level. |


**Evidence to keep:** Phase-2 screenshots of valid/unknown/locked/test
destinations, pack diff/hash, reload logs and old-route server receipts.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B07 - BodyContext cache and full-workload regression

**Header:** Phase 5; REF; accepted 4-vCPU/8-GiB Debian 12 node, normal user,
workload accounts/equivalent under O7; ordinary world, switches off, no properties.
**Sources:** v1.1 sections 13.1 and 13.4; [quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md) section 4.

**Preconditions:** P2 **reference workload world of docs/17 section 4** and
the two-station orbit scene from B02; baseline/JEI absent, heap <=4 GiB, RSS/tick
sampling plus a cache/query profiling method. C10/R535 supplies the matched baseline and Root-review rule below;
no percentage allowance exists.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the full workload's MSPT/RSS/GC and query/cache profile. | Same load as the documented reference, not an empty world. |
| 2 | [H] Query context repeatedly in both station regions. | Correct per-region body; cache avoids per-tick full-table scans. |
| 3 | [H] Apply the fixture's valid context/catalog invalidation. | Subsequent query reflects the new generation, not stale context. |
| 4 | [H] Repeat the profile after invalidation. | Cache recovers without monotonic growth or unbounded scanning. |
| 5 | [H] Compare the complete regression interval with the matched baseline. | Record absolute budgets and baseline deltas; mean <=25 ms, P95 <=50 ms, P99 <=100 ms, no sustained RSS above 5.5 GiB. |

**Accepted preparation/action steps (W408):**

(C10, R535) Use the common matched-copy/host/cell plan, two discarded warm-up minutes, ten sampled minutes and two baseline runs; baseline spread is a Root-review trigger, not a numerical allowance or preset severity. Preparation is required for the direct cache invalidation/query observer and comparable baseline; preparation is required for the reference world. Entry B-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open. Entry B-K17 (R648) supplies the following inputs: combined reference world and four-hour schedule. The full four-hour REF/two-real-client run remains unexecuted; decaying missions do not establish sustained load.

**Evidence to keep:** `phase5-ref/<run>/B07/` workload/environment record,
raw samples, cache invalidation/query traces, baseline identity and calculated
deltas. No numerical claim of no significant regression without a defined comparison.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **Answered:** C7 reconstruction/H02 scope, C8 ownership setup, C9 server-tick reporting/no overlap tolerance, C10/R535 matched sampling/review, W406 pack bytes/hashes/source-derived outcomes.
- **B01/B04:** Entry B-K02 supplies FI02/FI03's pinned source/schema/boundary manifests and C6 copy-load qualifications under R611. FI03 remains an unmanned reconstructed TRANSIT_PREPARED source; current-flight controls and repeated campaign recovery still require qualification. No retained played in-flight source means that played-world claim is NOT_COVERED. Entry B-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.
- **B02:** Regions/owners/energy and commands must be recorded, without owner-limit bypass. Entry B-K08 (R621/R627) supplies the following inputs: station sets, two-account recipes and ordinary-energy copies. Ordinary account binding, consent/warp/docking/membership actions and the listed live/REF observations remain open.
- **B03/B06:** Live pack loads/quotes/graph legs/removed-menu and two-restart qualification remain required; scene/control binding remains required. No fuel-price increase is promised by the distance-75 definition; rejected unknown data is not setup. Entry B-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications. Entry B-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.
- **B05:** Actual server receipt-tick correlation remains required if ordinary receipts lack it; scene preparation remains required. O5 supplies coordinated input. Missing same-tick proof is NOT_COVERED with a named A1 test. Entry B-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open. Entry B-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.
- **B07:** Direct cache/invalidation profiling and a reference world remain required; the comparison rule is answered.
Entry B-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open. Entry B-K17 (R648) supplies the following inputs: combined reference world and four-hour schedule. The full four-hour REF/two-real-client run remains unexecuted; decaying missions do not establish sustained load.
