# Final campaign checklists E: planetary expansion

Status: planned, not executed. Covers E01-E11 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.5. Writing these
procedures does not run the campaign, approve a Gate or enable a switch.

Accepted common procedures for **every E row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared setup and source interpretation

Use the run conventions in `CAMPAIGN-CHECKLISTS-A.md`: P1's C6 identity
(R581 names `c8fa16e16`), `[H]` harness versus `[P]` human action,
authenticated V2 settings, two-restart DS protocol, and the campaign section-6
evidence root/filenames/result metadata. Use Java 17, Minecraft 1.20.1,
Forge **47.4.10**, **JEI absent** unless a separately identified 47.4.23 /
JEI 15.56.0.205 compatibility cell is run. All worlds are ordinary disposable
copies, **not O2's marked world**; keep admission switches off and no JVM
test properties. Do not enter a disabled bay/cargo/fuel test path for these rows.

P2 supplies **representative v1.3 to v1.7 worlds**, **a v1.0 world**, **the
reference workload world of docs/17 section 4**, and associated immutable hashes.
The planetary scene is a P2-derived disposable copy with a fueled legal rocket,
powered Satellite Terminal, Data Satellite components, supplied sealed rooms,
complete oxygen-filled equipment and authorized station access. The scene requires preparation to name
actual paths, coordinates, owners, station IDs and each pack variant; the source
manuals do not provide a universal fixture placement script. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

Read sources: [v1.4 version](../../../versions/V1.4.0-PLANETARY-EXPANSION.md),
[MT140-01 to MT140-09](../../../releases/v1.4.0/MANUAL-TEST.md),
[visual report](../../../releases/v1.4.0/VISUAL-VALIDATION-REPORT.md),
[G7/G8](../../../releases/v1.4.0/GATE-STATUS.md),
[navigation cases](../../v1.4.0-nav/MANUAL-CASES.md),
[sky cases](../../v1.4.0-sky/MANUAL-CASES.md),
[discovery cases](../../v1.4.0-discovery/MANUAL-CASES.md),
[celestial data guide](../../../CELESTIAL-DATA-GUIDE.md),
[discovery guide](../../../PLANETARY-DISCOVERY-GUIDE.md),
[sky guide](../../../PLANETARY-SKY-GUIDE.md) and
[migration limits](../../../releases/v1.4.0/MIGRATION-REPORT.md).
The navigation cases' old flight-protocol-7 precondition conflicts with later
discovery's protocol 8; both identify historical builds. Use matching C6
client/server and P1's actual protocol inventory, not mixed old artifacts.

The namespace below is `advancedrocketrycommunity`. Earth is the Overworld;
Moon, Mars, Venus and Space use their actual registered Levels. The gas giant
is an **unmapped body**, not a Level. `/arce celestial goto` is not a general
planet teleporter; use normal rocket travel or P2's explicitly recorded setup.
Do not invent a gas-giant dimension or use a raw Level change as discovery proof.

## Package preparation index (W456, R649)

This index resolves the package notes below; it is preparation, not execution or a Gate verdict.
Each entry gives the sealed input, its checksum-list SHA-256, the recipe/step mapping and the
remaining partial or NOT_COVERED work. Paths are external read-only inputs, never repository links.
Only K02 (W447) and K12 (W454) remain package deliveries in progress. A delivered recipe is not
an observed client result; preserve every original action, oracle, result field and budget.

### E-K03: reconstructed FI05-FI08 chain

Source: `W436.md; QUEUE.md row W436`. Root acceptance/disposition: **R614** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI05/SHA256SUMS.txt` | `86185f41558aff1fa917a4de3fff78a0f6413107983e3593ccbfd078de0f4cf0` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI06/SHA256SUMS.txt` | `c3fc97cdd481b620f6ed114431d5b23f04aa6046d9b864cd7a92959030efb8b2` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI07/SHA256SUMS.txt` | `b588390b7224c85c321712aec0e40bcf202eb2bab54d68e517f5dcbd93171f66` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI08/SHA256SUMS.txt` | `d29f4656341ee439aab6d64f4f2fdbb6580f639dcf53c10815c79c6806e8a994` |

**Recipe and original-step mapping.** Each FI05-FI08 manifest.json commands/content and CONTENT-READBACK.json maps E10's v1.4-v1.7 loads and feeds K07-K10. Use source-world copies and the separate qualification observations.

**Remaining coverage and reasons.** Historically played/complete pre-sky claims NOT_COVERED (no retained played source). FI06 membership/charged-warp, FI07 active resource/gas missions and FI08 drill/black-hole/gravity gaps are not filled by the historical chain; use the current packages' qualified inputs or scripted actions, never synthetic saves.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E10 | FI05-FI08/manifest.json content/host/commands and CONTENT-READBACK.json -> each original upgrade-and-reopen step. E09 station acquisition needs K08/player action; chain does not supply historically played/pre-sky acceptance. |

### E-K06: 23 hashed data/resource packs, not blanket live qualification

Source: `W406.md; QUEUE.md row W406`. Root acceptance/disposition: **R550** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/SHA256SUMS.txt` | `3be8336be6e1aed8fc2859323d0ecbae9b7209f2fc8048a09944407a45992ca5` |

**Recipe and original-step mapping.** manifest.json packs[].path/rows/prerequisites/load_check and campaign_values give the per-row reload sequence: B03 route baseline/valid/invalid/unknown; B06 test_mars/locked; E01 itinerary; E05 100/128/129 and ID bounds; F05 station overlays; D04 tag pair; D08/D09 payload pair; E06/E11 sky resource packs. Run load_check at the existing row step and retain actual generations/quotes.

**Remaining coverage and reasons.** candidate_loads_run=false in this manifest. K21 separately qualified tag/payload loads, K08 loaded station baselines and K07 binding refusal; those do not prove B03's live quotes/two restarts, every catalog, sky load, removed selection or cooldown. Unperformed branches remain NOT_COVERED. Invalid packs are refusal-only, never setup. The 48-character value is a station name. Preserve the manifest's recorded generator commit and C6 JAR identity.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E01 | campaign_values.E01_surface_itinerary/E01_route_legs and route load_checks -> original consecutive-travel steps. Server mass/quotes/debits must be observed. |
| E05 | packs[catalog_100.zip/catalog_128.zip/catalog_129.zip/catalog_id_128.zip/catalog_id_129.zip].load_check -> original catalog/ID/control/cooldown steps on separate complete baselines. |
| E08 | Use the named recipe/step selector above at the unchanged original row actions; preparation is partial as described, not an observed result. |

### E-K07: seven earned planetary/hazard variants

Source: `W438.md; QUEUE.md row W438`. Root acceptance/disposition: **R617** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/discovered/SHA256SUMS.txt` | `5330ebe043370bc1a795d37067fb215f8e2cb3ce3a3395f2c288ffd5ddacb547` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/undiscovered/SHA256SUMS.txt` | `ce4ec77ffed74857e219a75086fa9968c0fc6f869d03f236117118600d4fc211` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/private-station/SHA256SUMS.txt` | `7fca7ba15db6b03acfa3ba46d224a39b8ccaf761712cf1716091b805fa812adc` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/paid-pending/SHA256SUMS.txt` | `4bc88b4ef7c5c87968a6edf9277d6fb6634864396c33e71971c130371ac301e3` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/blocked-binding/SHA256SUMS.txt` | `6b1cb4bbab060d4278f31c6c47baa2c2b99a6067393c3d70c1314c8edb51b538` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/pre-sky/SHA256SUMS.txt` | `f8c75e78dfa7bad964a6a4a3dcf3c8e36f6293e086db8bf4eeec2ecb0bf4a26b` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/hazards/SHA256SUMS.txt` | `82dfc1ed05495a4a0e96e1226bd96b0c9daef676aa27df1504eab517aa6f828a` |

**Recipe and original-step mapping.** Each manifest.json commands/readbacks/effective_values maps E01 values; discovered/undiscovered/private-station map E02/E03; hazards.hazard_plan.campaign_steps maps E04; paid-pending/blocked-binding map E08; pre-sky from FI04 maps E09/E10; hazard_plan.sites supplies E06/E11 viewpoints.

**Remaining coverage and reasons.** Discovery is world-wide, research owner-private: undiscovered uses FI04, not a balance reset on FI05. Private station is operator-created with placeholders. Paid-pending uses the fixed hook actor and heals on normal reload, so a retained pending client menu is NOT_COVERED. Isolated thermal/pressure/solar player damage is NOT_COVERED because a full built-in suit grants all protection and an incomplete set loses oxygen protection. R617 leaves live hazards/pending-menu controls as campaign steps; quotes/flights/rendered skies/O5/V1/V2 remain unexecuted.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E01 | discovered/manifest.json effective_values -> original research/travel/config steps; C6 quotes/flights not supplied. |
| E02 | discovered/undiscovered manifests -> original private captured-price/shared discovery steps; use FI04 for undiscovered world. |
| E03 | private-station/manifest.json commands/readbacks -> original private-station owner/member/refusal steps; K18 forbidden-surface case has separate prerequisites. |
| E04 | hazards/manifest.json hazard_plan.sites/equipment/campaign_steps -> each original isolated/combined/supplied-room step; isolation remains NOT_COVERED, not inferred from full-suit protection. |
| E06 | hazards/manifest.json hazard_plan.sites plus discovered effective sky resource hashes -> original per-body/day/night/second-GPU observations, not rendered results. |
| E08 | paid-pending and blocked-binding manifests commands/readbacks -> original pending/reload/recovery actions. No-hook paid receipt heals, so retained menu control is missing. |
| E09 | pre-sky/discovered manifests -> original new-body station procedure's world seed only; K08/player owns station acquisition. |
| E10 | pre-sky/manifest.json parent_source=FI04 -> original first-load/upgrade sky comparison; reconstructed, not played; K03 chain complements it. |

### E-K08: station sets, two-account recipes and ordinary-energy copies

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

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E05 | two manifest's 48-character station name and addendum ordinary_creation -> original long-name display action; no body-name field invented. |
| E09 | addendum ordinary_creation + separate FI06 copy with eligible A/B -> original normal deployment around target body; K08 operator-created ownership is not this result. |

### E-K15: bounded observers, with explicit remaining visibility gaps

Source: `W403.md; QUEUE.md row W403; W452.md/R648`. Root acceptance/disposition: **R548; R648 for A08/A09** in `../QUEUE.md`.

**Recipe and original-step mapping.** Tracked scripts/campaign_observe/{ticks,process,jdk,host,evidence}.py and scripts/run_campaign_observer.py at implementation 385c079dc10718f781a425527cd3e9a0765a0518 supply matched-copy window/process/heap/JFR/evidence operations. Use sample_ticks/force-load queries and owned-process-only diagnostics at the row's existing sampling step; K17 schedule and K08/K09/K10 load drivers supply concrete harness integrations.

**Remaining coverage and reasons.** W403 created no sealed live fixture or SHA256SUMS.txt: none is invented. Window aggregates cannot prove whole-run percentiles/spike lengths. C6/K17's release-test raw sampler is used only in the separately labelled R648 REF run for A09. All-Forge-ticket, queue-internal, client/GPU allocation and direct cache-release proof remain NOT_COVERED where unavailable; histogram/heap/RSS proxies are labelled, not direct evidence. Actual REF acceptance/full windows and overhead checks remain unexecuted.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E05 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |
| E06 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |
| E07 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |
| E11 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |

### E-K16: v2 core scene, mapped controls and conditional recheck batches

Source: `W427.md; W443.md; QUEUE.md rows W427/W443`. Root acceptance/disposition: **R595; R628 supersedes final/ with v2/** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/SHA256SUMS.txt` | `27df12a711d23f500dd7370fab35a6c5d457f6eebd93e8366ee191889b140fa9` |

**Recipe and original-step mapping.** v2/batch-index.json maps each row/step/file; manifest.json core_route/devices/supplies and candidate-recipes.json give counts/positions/recipes. A02/I01 use core-00/core-01, core-room/core-suit, core-rocket-assemble/-fuel/-invalid, core-flight-plan/-observe; A06/A13/A17 use core-station/core-screen-*; E07 uses sp-pause; J20/J21 use rechecks.json and batches/j21-*.json. controls.json gives effective viewport targets, not old pointer replay.

**Remaining coverage and reasons.** R628 accepts only the en_us 1280x720 scale-3 dedicated rehearsal; scale2/fullscreen/zh_cn, integrated pause and 63 J21 requests are generated, not rehearsed. Fullscreen screen gate needs checking. B-5/3-2/8-2 remain checkpoint-only; no complete continuous quantitative crafting witness or all-screen/first-join/dual-seat/station ownership-transfer procedure is supplied. Return flight/paid warp/satellite flow, O5 accounts, N15-N19 physical setup and V1/V2 remain partial. Never replay old NBT-writing setup; R638 applies. Desktop-wide Alt/Shift are the recorded night-round operating condition.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E07 | batch-index.json entry sp-pause -> original step9 integrated pause branch. Generated only, not rehearsed; no dedicated pause substitution. |

### E-K17: combined reference world and four-hour schedule

Source: `W452.md; QUEUE.md row W452`. Root acceptance/disposition: **R648** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/source/SHA256SUMS.txt` | `d03941ecdb4bff0d1742cf452959293b951e2e26b2972d8983e6f5c0ccd3daf1` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/readback-settled/SHA256SUMS.txt` | `a4aba18f3649e82a8e00e5d20f0efc508ad3c35c8b21357938a9811c7efc3df4` |

**Recipe and original-step mapping.** source/layout.json maps the 16 Moon rooms, ten station IDs, mission records and 2048/64/64 pads. source/schedule.json actions and client_steps.{connect,assembly_2048,flights,missions,a11} map A08-A11/I05/I10; use k17_reference.py schedule/run on disposable copies. Two 7200-s sampled segments, 120-s warm-up, flushed saves each 1800 s, one restart at sampled 7200 s, autosave retained.

**Remaining coverage and reasons.** R648: A08 has no test properties; keep forge tps means/RSS/GC/forced chunks. A09 is a separate labelled REF run on the same world/schedule adding only -Dadvancedrocketrycommunity.releaseTestHooks=true for the read-only raw sampler; compare forge tps means under C10's baseline-spread review rule. The 16 forced room chunks are declared workload, not unexpected tickets. Without clients flights/disassembly/dimension transfer remain scripted; ACTIVE missions fall from 101 to zero within ~6000 ticks. Record actual counts and ordinary refills, not a sustained-load claim. K17 does not supply sustained 500/1000 mission or H07 endgame throughput. Full four hours/REF/two real connections/all-ticket/direct-cache evidence remains unexecuted.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E11 | Use the named recipe/step selector above at the unchanged original row actions; preparation is partial as described, not an observed result. |

### E-K18: bounded client intent JAR and case/readback matrix

Source: `W432.md; intent-test-mod/README.md; QUEUE.md row W432`. Root acceptance/disposition: **R601** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS.txt` | `aac26e987be0617ad63cec39b54aefb0fe7f3372085863ab14cd49731e392e57` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS-CORRECTED-1.txt` | `d29b209b8e7133f2d50f8463d3d9a1fa616572b2fe8b919ddbe58de254ec188f` |

**Recipe and original-step mapping.** Install arce-intent-test-1.0.0.jar only on the probing client. intent-test-mod/README.md Triggering one case steps 1-6 and Case table map C08/D09/I11 flight_stale/range/non_owner/late and classic_stale/range/non_owner/late; E03 flight_forbidden_surface/private_station; E07 late and motion_disabled. R/C/M/L readbacks are required separately. F8 captures; a single F9 queues at most one bounded packet.

**Remaining coverage and reasons.** Use SHA256SUMS-CORRECTED-1.txt: the original list's final-validator hash is stale and preserved, not silently repaired. There are three C2S channels, five S2C-only; no payload-terminal, research-menu, callback-fault, packet-burst, late-S2C/cache, all-ticket or same-tick claim control exists. Those branches remain NOT_COVERED with the README's named A0/A1 references/gaps. Client queue log is not server receipt. Actual real-client/server qualification and unchanged-state/loaded-chunk readbacks remain campaign work.

**Mapping in checklist E.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| E03 | flight_forbidden_surface/private_station cases -> original gas-surface/private-station refusal actions; require ready owner rocket, actual discovered forbidden target and nonmember B. |
| E05 | No catalog/cache/S2C injection; original cache/late-render branches remain NOT_COVERED, not covered by flight_late. |
| E07 | flight_late/classic_late/motion_disabled -> original late-C2S/lifecycle negatives; late S2C cache/render controls NOT_COVERED. |

## E01 - Research, consecutive planetary travel and return

**Header:** Phase 2 V1 and phase 3 DS; development PC, real GPU client/account
A; ordinary copy, switches off, no properties. **Sources:** v1.4 section 10.1,
MT140-01 and DISC-V1-02/03.

**Preconditions:** P2 planetary scene with undiscovered Mars/Venus, rocket
equipment and powered terminal; baseline/JEI absent. Record inventory/fuel,
research/discovery, terrain/location and room/equipment supply before each leg.
Built-in Data Satellite needs chassis, solar module, data storage and unbound
control chip. Its built-in mission lasts **200 running-server ticks**, awards
**120 research**, and captures **100 first-discovery fee** (net **20**) when
started undiscovered; later-started missions to discovered bodies net **120**.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Manufacture the Data Satellite through the powered terminal. | Correct package/component debit and bound chip. |
| 2 | [H] Launch its Mars research mission. | Owner/target/duration/fee captured from the effective definition. |
| 3 | [H] Claim after at least 200 running-server ticks. | Built-in first-discovery net 20; Mars opens once, no fuel supplied. |
| 4 | [H] Repeat steps 1 to 3 for Venus with its own valid package/chip. | Venus research/discovery recorded without resetting Mars or private balances. |
| 5 | [H] Open the rocket console's Star map and select Mars with Use surface. | Readable ready quote; selection alone does not launch. |
| 6 | [H] Confirm Launch in the console. | Server-authorized Mars arrival with one fuel debit and intact inventory/seats. |
| 7 | [H] Capture Mars terrain and environment. | Correct body, terrain, server environment and client HUD; C6 terrain follows current generation, not obsolete v1.4 art. |
| 8 | [H] Return Mars to Earth, then launch Earth to Venus (C19; record both quotes/debits). | Correct target/fuel quote, safe landing or explicit refusal; no silent alternate destination. |
| 9 | [H] Capture Venus terrain and environment. | Correct distinct body/terrain and intact assets. |
| 10 | [H] Save/stop the landed world. | Clean exit; stopped-state resource baseline. |
| 11 | [H] Restart the same copy and reconnect A. | Same identity, location, rocket, cargo and research. |
| 12 | [H] Launch the permitted return to Earth. | Correct arrival, one debit, no duplicated/lost asset. |
| 13 | [H] Save/stop and restart the same copy again. | Second restart retains returned state. |
| 14 | [P] Review terrain, environment, fuel and inventory comparisons. | Visible state agrees with server; legitimate consumption reconciles. |

C19/W406 enumerates Earth → Mars → Earth → Venus → Earth; quote each actual
leg and never change the graph to force direct Mars-to-Venus travel.

**Accepted preparation/action steps (W408):**

(C1/C19) Flight wire protocol is C6's pin **10**, not historical 7/8. Enumerate actual catalog/quote legs: Earth → Mars → Earth → Venus → Earth, `earth_mars` twice and `earth_venus` twice (namespace `advancedrocketrycommunity`), distances 80/80/95/95. Test every permitted consecutive pair: no direct Mars→Venus route is assumed or failed for being absent. Preparation is required for the actual per-leg server quotes/load qualification; preparation is required for the run manifest, mass, effective gravity/propulsion values and planetary scene. Mission 200/120/100 values remain as the original row states; no earned balances are reset. Entry E-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `route_baseline.zip` | `5734f7f9256106b1d919551e2013b99c9c92e20be4ee65ecfa0c305cab2f77e5` | A; original bidirectional Earth–Moon surface route, distance 50; 11 bodies/12 routes. |


**Evidence to keep:** Phase-2 per-body/terminal/console screenshots;
`phase3-ds/E01/` per-leg quote/debit/arrival records, terrain/body observations,
inventory/research ledger and both restart projections/logs.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E02 - Shared discovery and private captured mission prices

**Header:** Phase 4, sitting 2; V2; two authenticated real accounts A/B and
clients; ordinary world, switches off, no properties. **Sources:** v1.4
section 10.2, MT140-02 and DISC-V1-01/03, DISC-V2-01/02.

**Preconditions:** Fresh undiscovered-body variant of P2 planetary scene,
baseline/JEI absent; each account owns a terminal, satellite/chip and separate
rocket. Capture both research balances (use zero-start accounts for the numeric
oracle below), discovery, target quotes and inventories. Use built-in mission
values **200 ticks / 120 reward / 100 captured undiscovered fee**.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Open both rocket maps before discovery. | Mars/Venus muted/locked with full research-required explanation; Launch disabled. |
| 2 | [P] Start A's mission to the selected undiscovered body. | Captured fee 100 and owner A. |
| 3 | [P] Start B's mission to the same body before either claim. | B independently captures fee 100, not a future post-discovery price. |
| 4 | [P] Keep B's rocket console open through A's completed claim. | A gets net 20; shared discovery opens B's eligible target; B's private balance stays unchanged. |
| 5 | [H] Observe the next console navigation refresh. | Built-in guide specifies refresh within **20 ticks**; ready quote still requires explicit launch. |
| 6 | [P] Claim B's completed mission. | B also gets net 20, not 120; its start-time fee remains captured. |
| 7 | [P] Repeat each claim. | Neither private balance changes again. |
| 8 | [P] Start and claim a later mission after discovery. | Later mission fee zero; its owner earns 120, other account unchanged. |
| 9 | [P] Attempt the other account's chip/terminal operation. | Refusal without package/research mutation. |
| 10 | [P] Reconnect B. | Shared discovery and separate balances persist. |
| 11 | [H] Restart the copied server and reconnect both clients. | Same discoveries/captured fees/claim receipts; no repeat reward. |
| 12 | [H] Reload the declared discovery-rule variant while a previously ready selection is held. | Destination rules/quotes refresh; an already prepared flight is not silently rewritten or cancelled by discovery alone. |

**Accepted preparation/action steps (W408):**

(C7) Preparation is required for the fresh undiscovered and private-station copies, preserving earned balances/IDs/history; zero-start numeric totals are conditional, never obtained by resetting an account. Owner O5 still supplies coordinated real inputs. Use the packs below for held-menu rule changes, not a grant of research. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `route_locked.zip` | `ddfc89cb8eb868e55c193977b403e2a9cf7db4d02a8a2fd0eb38a1bf577d0ac7` | A; built-in Mars requires discovery; otherwise eligible undiscovered flight returns `DISCOVERY_REQUIRED`. |
| `route_discovery_rule_open.zip` | `3bef68fe7d3dccc981d5a127f9dd46b2a8e6eeb64ae870bf15104e99aeca4391` | A; only Mars's discovery requirement becomes false; other flight checks remain. |


**Evidence to keep:** Sitting-2 synchronized videos/screenshots in both
languages, both player balances before/after each claim, captured fee/mission
snapshots, refresh tick receipts, reconnect/restart/reload logs and explicit
launch observations. Zero-start net figures are conditional on that fixture,
not a reason to reset an existing account to obtain the expected total.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E03 - Private station access and gas-giant refusal

**Header:** Phase 4, sitting 2, with phase-3 DS comparisons; V2, DS; real
accounts A/B, B non-OP; ordinary world, switches off, no properties.
**Sources:** v1.4 section 10.4, MT140-03, navigation V2 cases and DISC-V2-01.

**Preconditions:** P2 private station around a discovery-locked body, A owner,
B initially nonmember; gas giant with an accessible orbit-station fixture but
**no surface Level**. Baseline/JEI absent. Record station UUID/region/member
set, discovery, both menus and one active countdown fixture. Membership changes
use supported station controls, not direct edits to observed SavedData.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Request the private station before orbit-body discovery. | Discovery lock and independent access check remain in effect. |
| 2 | [P] Complete the authorized shared discovery. | Knowledge opens eligible routes but grants no station membership. |
| 3 | [P] Attempt B's nonmember station request after discovery. | Still denied; resources and station authority unchanged. |
| 4 | [P] Add B through the ordinary invitation/accept flow. | Membership only for this station. |
| 5 | [P] Request that station as B. | Member's valid quote/arrival allowed, subject to normal route/fuel checks. |
| 6 | [P] Remove B while both navigation menus remain open. | Permission refresh removes B's access without substituting a different station. |
| 7 | [P] Inspect the gas giant node in Star map. | Public metadata visible, Use surface unavailable; no invented Level. |
| 8 | [H] Submit the approved bounded gas-giant surface intent. | Server refuses even after discovery; no fuel debit, chunk load or asset change. |
| 9 | [P] Select a permitted gas-giant orbit station. | Separate valid station destination, not a generic gas-giant surface. |
| 10 | [P] Begin the recorded authorized station countdown. | Active target and fuel ledger recorded. |
| 11 | [H] Revoke/remove the selected station through the supported fixture action. | No unauthorized arrival; preserve active-plan and refresh observations. |
| 12 | [P] Change B's editable target choice. | A's active target is not replaced by B's selection. |
| 13 | [P] Cancel from the controlling client's console. | Actual cancellation target follows the active plan; no extra fuel debit. |
| 14 | [H] Save/stop and restart the station copy. | Station identity/discovery/rights persist. |
| 15 | [H] Repeat step 14 for the second restart. | Same saved rights and no unintended gas-giant Level. |

**Accepted preparation/action steps (W408):**

(C7/C14 corrected by R546) Preparation is required for the naturally earned discovery/private-station variants; preparation is required for the bounded forbidden gas-surface/stale intents and receipt/state proof. No tool reach means NOT_COVERED with reason and named A1 test, not a pass from a disabled Use surface control. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open. Entry E-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open.

**Evidence to keep:** Sitting-2 both menu/quote videos, active/editable target
projections, membership/gas-intent/cancellation receipts, loaded-chunk and fuel
comparisons; `phase3-ds/E03/` both restart state/log sets. If the bounded
surface intent is unavailable, disabled UI alone does not close server refusal.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E04 - Exposure, passive equipment and supplied climate rooms

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off, no properties. **Sources:** MT140-04 and celestial data guide's
Environmental exposure section.

**Preconditions:** P2 planetary exposure variants with
`environment_effects=true` on unambiguous surfaces, survival-mode A, supplied
sealed rooms and four correctly slotted protection items. Baseline/JEI absent.
Manifest gives actual body/position/config values; sources do not provide one
ready-made world for all hazards. Keep oxygen separate from passive protection.

| Hazard | Exact source condition |
|---|---|
| Cold | Temperature **below 240 K**. |
| Heat | Temperature **above 330 K**. |
| High pressure | Pressure **above 2**. |
| Sunlight | Solar intensity **above 1.5**, daytime and direct sky visibility. |

| Step | Action, repeated for each isolated hazard and the combined-hazard fixture | Expected observation |
|---|---|---|
| 1 | [H] Capture server environment, equipment, oxygen, health and protection state. | Relevant threshold/opt-in true; no creative/spectator exemption. |
| 2 | [H] Expose the unprotected survival player for the recorded interval. | At most one **2-health-point damage attempt every 20 ticks**, not one per hazard; pressure > heat > cold > sunlight priority; localized warning. |
| 3 | [H] Equip the full passive protection set for the tested hazard. | Four count-one correctly slotted tagged ArmorItems protect that exposure, not oxygen by implication. |
| 4 | [H] Repeat exposure with suit oxygen deliberately exhausted in the supplied fixture. | Passive insulation can remain but suffocation is not prevented by the passive tag. No duplicate simultaneous suffocation attempt. |
| 5 | [H] Enter the sealed room with powered, oxygen-supplied vent. | Finite supply provides climate protection, including breathable but hot/high-pressure air. |
| 6 | [H] Open the fixture seal. | No room protection while broken/pending/unsupplied. |
| 7 | [H] Restore the seal and supply. | Correct protection returns after valid room state. |
| 8 | [H] Enter ordinary outdoor cover in the sunlight fixture. | Direct sunlight blocked; cover alone does not cool air or reduce pressure. |
| 9 | [H] Return to safety. | Exposure resets; no persistent/offline exposure debt. |
| 10 | [P] Review action-bar and oxygen HUD against the server record. | Messages distinguish oxygen from remaining climate hazards and are readable. |

Actual observed health loss may be reduced/cancelled by armor, resistance,
spawn immunity or another damage hook; keep attempts and those modifiers
visible in the record. Radiation is metadata, **not** an implemented damage
hazard. Station orbit never imports its planet's surface heat/pressure.

**Accepted preparation/action steps (W408):**

(C20) Leave damage modifiers, difficulty and game rules at C6/vanilla defaults and record effective values. Exercise each hazard alone and combined, keeping damage attempts distinct from mitigated health loss. Preparation is required for the isolated/combined sites, equipment, exact coordinates and observation intervals. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

**Evidence to keep:** Per-hazard step screenshots/video, effective pack/tag
values, damage-attempt/health/oxygen/resource timeline, vent supply and logs.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E05 - Star-map controls, bounded large catalogs and reload

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off, no properties. **Sources:** v1.4 section 11.2, MT140-05 and
all six navigation V1 cases.

**Preconditions:** Fueled P2 planetary rocket, accessible station list,
long-ID body and **48-character station-name** fixture, valid **100-body**
and **128-body** catalogs, valid/invalid reload variants and missing/rejected
client-cache method; baseline/JEI absent. Run English/Chinese, GUI scales
**2/3/4**, smallest supported and large windows, windowed/fullscreen.
C2/R535 supplies tested pixels/effective scales; no supported minimum is declared.
W406 reads C6's 100-tick cooldown; live retry/presentation still requires qualification. Entry E-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications.

| Step | Action, for each display/catalog cell | Expected observation |
|---|---|---|
| 1 | [H] Open the rocket flight console and select Star map. | Correct catalog/quote generation, readable title/details/status. |
| 2 | [H] Click a body node. | Correct selected body and metadata; selection does not launch. |
| 3 | [H] Drag the viewport. | Bounded panning; record node/selection position. |
| 4 | [H] Scroll the viewport. | Correct zoom and hit testing. |
| 5 | [H] Click outside the clipped viewport. | No selection of invisible out-of-viewport nodes. |
| 6 | [H] Use < and > to focus every body in the current catalog. | All 100 or 128 valid nodes reachable; retain frame timing and overlap/clipping findings. |
| 7 | [H] Cycle Station n/m and hover its tooltip. | Only permitted stations; correct 48-character name and route status. |
| 8 | [H] Choose Use station or Use surface for the selected valid target. | Chosen target returns to console; no implicit launch. |
| 9 | [H] Change the fixture's fuel/component/control state. | Console quote/reason matches server legality, capacity/thrust/fuel and permission. |
| 10 | [H] Exercise BOARD and LEAVE through the console while the map workflow is active. | Current seat/menu state stays consistent; map is not an alternate authority. |
| 11 | [H] Start a valid countdown. | Active plan distinct from editable selection. |
| 12 | [H] Return to the console and cancel it. | Cancellation remains reachable and targets the active plan. |
| 13 | [H] Reload the valid data variant while the map is open. | New catalog/quote generation synchronizes; no mismatched-generation launch. |
| 14 | [H] Reload the invalid variant. | Last valid body/route pair retained with bounded readable diagnostic. |
| 15 | [H] Exercise the declared missing/rejected client-catalog case. | Safe disabled launch/fallback, not a stale ready quote. |
| 16 | [H] Request refresh within the actual full-catalog cooldown. | Rate limit respected; automatic retry/recovery time recorded. |
| 17 | [H] Reopen the menu. | Correct current catalog and quote; no stale identity. |
| 18 | [P] Review every display cell's screenshots and timing. | Full controls/tooltips/long text readable without critical clipping. |

**Accepted preparation/action steps (W408):**

(C2/C14/C21; W406) Use corrected effective viewports; no supported minimum exists. Full-catalog cooldown reads **100 ticks** in C6 source; still record its effective retry/presentation. On separate complete baseline copies with only the 11 original bindings, load each valid catalog/ID variant; lifetime bindings must never be erased to fit a case. The 48-character value is a **station** name, `K06 station ` plus 36 ASCII `n` characters, created by ordinary permitted K08 operations, not a nonexistent body-name pack/field. Test 129 bodies or ID characters only as post-valid-load rejections, never another row's setup. Preparation is required for the live loads/retry observations; preparation is required for the station; preparation is required for the missing/rejected/late client control; preparation is required for the cache readback. Entry E-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications. Entry E-K08 (R621/R627) supplies the following inputs: station sets, two-account recipes and ordinary-energy copies. Ordinary account binding, consent/warp/docking/membership actions and the listed live/REF observations remain open. Entry E-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `catalog_100.zip` | `665c08bd860551c31105374a8e1f3bd803fd5a7051139874b9e536c867d2e51b` | A; 89 additional logical bodies, total 100; retain all stock definitions/routes. |
| `catalog_128.zip` | `e829962688e2ecc50f07cc3d5d21800e823304b981258fc5381ebc1c53a71679` | A; 117 additional logical bodies, total 128. |
| `catalog_129.zip` | `afad4f0296ea01f421748b702a404f5b9f1d00bc4dc6289d9220dbf93b737145` | R; `celestial_bodies: resource count exceeds 128`. Never use as another row's setup. |
| `catalog_id_128.zip` | `2d5a1d8d9d336e53b7f2aefbd35818f4dc3b9f8646d544d02c824724a91e644b` | A; ID is `k06:` plus 124 ASCII `l` characters; total 12 bodies. |
| `catalog_id_129.zip` | `eea52a593f7adb4fa236574768cf03a8ae29224c91b3b7e786b1547dd469f60e` | R; `definition resource ID exceeds its bound or is empty`, before body decoding. |


**Evidence to keep:** Phase-2 per-cell screenshots, node/input/selection
table, raw frame times, catalog/pack hashes, generation and cooldown/recovery
receipts, active/editable target comparisons and client/server logs.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E06 - Planetary sky matrix on two real GPU categories

**Header:** Phase 2; V1 on **two real GPU categories** under O9; real client
A on each, development PC plus owner's second GPU machine; ordinary worlds,
switches off, no properties. **Sources:** v1.4 sections 11.1 and 11.5,
MT140-06, visual report, SKY-V1-01/03/04/06/07/09 and sky guide.

**Preconditions:** P2 scene for **Earth, Moon, Mars, Venus and shared Space**
with station context, valid custom profile, invalid-schema and oversized-profile
packs, and missing-profile fixture; baseline/JEI absent. Record each machine's
OS/CPU/GPU/driver/resolution/JVM, render distance, packs and no unstated rendering
mods. Repeat day/night where the type permits, English/Chinese scales **2/3/4**,
windowed/fullscreen. Gas giant has no surface sky to visit. O8 supplies frame-time
tool; Vanilla/Forge baselines come from A12 on the same machines/settings.

| Step | Action, separately on each GPU and each listed body | Expected observation |
|---|---|---|
| 1 | [H] Enter the recorded viewpoint by the permitted route. | Client Level/body/station catalog agrees with server context. |
| 2 | [H] Set day through the recorded server time control. | Where time applies, Earth normal sky, airless star field or distinct planetary sky/sun/fog as configured; no missing textures/clouds. |
| 3 | [H] Set night through the same control. | Correct time-dependent profile rather than prior-body geometry. |
| 4 | [P] Inspect horizon, sun and stars while turning the view. | No holes, severe seam, flicker, sun artifacts or persistent OpenGL errors. |
| 5 | [H] Enter the water observation fixture. | Vanilla visibility restriction preserved; sky does not draw over it. |
| 6 | [H] Enter the lava observation fixture. | Same vanilla restriction preserved. |
| 7 | [H] Enter the powder-snow observation fixture. | Same visibility restriction preserved. |
| 8 | [H] Apply the fixture's blindness effect. | Restricted visibility preserved. |
| 9 | [H] Replace it with the darkness fixture. | Darkness behavior preserved. |
| 10 | [H] Clear the effect/immersion. | Ordinary fog returns correctly. |
| 11 | [H] Change render distance. | Planetary fog never reveals beyond ordinary visible terrain. |
| 12 | [H] Enter the covered/cave viewpoint. | Additional planetary ambience stops; no sky drawn through cover. |
| 13 | [H] Enable and reload the valid custom profile with F3+T. | Intended profile replacement; authority/environment unchanged. |
| 14 | [H] Reload the invalid-schema profile variant. | Prior valid map retained with bounded diagnostic. |
| 15 | [H] Reload the oversized-file variant. | Prior map retained; profile file limit **16,384 UTF-8 bytes** enforced. |
| 16 | [H] Remove the test profile pack and reload. | Base resources restored; no persistent GPU-buffer/cache growth. |
| 17 | [H] Exercise the allowed unknown/unavailable-profile fixture. | Conservative dimension fallback, not another body's presentation; server rules unchanged. |
| 18 | [H] Compare frame times and memory with same-machine baselines. | Keep average FPS, 1% low, stalls and cache observations; no invented absolute FPS limit. |
| 19 | [P] Compare both hardware-category results. | Both have complete real-GPU observations, not V0 screenshots. |

Profile constraints also include **128** winning files, **16** JSON nesting
levels and **128-character** IDs. Invalid duplicate Level mappings cannot be
forced into the server catalog to obtain a client test; if no allowed rejected-
catalog fixture exists, keep that SKY-V1-07 branch NOT_COVERED for Root.
SKY-V1-08's pre-sky-world identity/time/light/inventory comparison is in E10.

**Accepted preparation/action steps (W408):**

(C5, R535; W399/W403) Use A's same-cell/same-viewport Vanilla/Forge paired captures, twice-run baselines, average/1% low/first-stall/longest-frame definitions and reporting-only 50 ms. No FPS floor or stall limit exists; spread triggers Root review only. Preparation is required for the direct allocation/cache observation and client sky-pack live checks; preparation is required for the viewpoints/effect scenes. O8/O9 supply approved capture/version and two GPU categories. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

**Evidence to keep:** `phase2-v1/<machine>/<round>/` complete body/time/
display/immersion/effect matrix, native screenshots, videos, frame-time/memory
data, profile bytes/hashes and client/server logs for **each** GPU. Hash copies
from the second machine. Do not count a fifteen-minute tour as full E06 there.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E07 - Transition lifecycle, late menus and bounded ambience

**Header:** Phase 2 V1; additionally phase 4 sitting 2 V2 for SKY-V2-01 and
navigation V2 lifecycle; real GPU A, plus B for two-client steps; ordinary
copies, switches off, no properties. **Sources:** v1.4 section 11.3,
MT140-07, SKY-V1-02/05, SKY-V2-01 and navigation V2 case 3.

**Preconditions:** P2 planetary scene and separate second world, normal audio,
baseline/JEI absent, valid profile reload pack; synchronized real-client capture.
Additional ambience is at most **one non-looping sound**, at least **400
unpaused client ticks** apart; Moon/Space add none. This is audible/client-time
behavior, not proof from server time alone.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Rapidly traverse the permitted body sequence. | No previous-body geometry, color, fog or growing stale GPU buffer state. |
| 2 | [H] Disconnect during the recorded pending menu/refresh state. | Old world/menu resources are retired. |
| 3 | [H] Join the separate world. | No prior-world sky/fog/sound; unavailable catalog uses conservative fallback. |
| 4 | [H] Return to the planetary fixture and reload profiles. | Correct fresh map/cache; no old geometry retained. |
| 5 | [H] Reconnect/change dimension/replace the test rocket in separately recorded pending-menu variants. | Late responses do not update another rocket/menu generation; IDs and actual receipt order retained. |
| 6 | [P] Listen while exposed on Mars/Venus. | At most one quiet non-looping added sound; actual spacing >=400 unpaused client ticks. |
| 7 | [P] Change the Ambient/Environment volume slider. | Added ambience follows the slider, ordinary game audio not globally muted. |
| 8 | [H] Enter cover. | Added sound stops. |
| 9 | [H] Exercise a real paused-client fixture if available. | Pause does not create a burst or reset cooldown; a multiplayer menu is not claimed to pause the world/client. |
| 10 | [H] Travel/reload/disconnect in the sound lifecycle variants. | Sound stops on each transition; cooldown not reset; no lingering audio. |
| 11 | [P] Listen on Moon and Space. | No added planetary ambience there. |
| 12 | [P] Put A and B on different planets in the V2 sitting. | Each receives its own synchronized body presentation. |
| 13 | [P] Reconnect/reload/travel A independently of B. | A's changes do not alter B's sky/sound or server environmental rules. |

**Accepted preparation/action steps (W408):**

(C14/C22) Run the pause branch in phase 2 using W399 `mode=launch`, `singleplayer=true`, one copied-save client; preparation is required for the qualified integrated round and converted batches. If unavailable by that phase, pause alone is NOT_COVERED because dedicated menus do not pause the world. SKY-V2-01/navigation V2 lifecycle remains sitting 2. Late-packet intent receipt/readback and direct cache readback still require qualification; unavailable branches carry reason and named A1 test. Entry E-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open. Entry E-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.

**Evidence to keep:** Phase-2 audio/video, transition/body/menu/rocket IDs,
raw cache/memory observations and unpaused-tick timing; sitting-2 both-client
media/logs and separate sky/environment receipts. Preserve any unavailable
late-packet/pause branch as NOT_COVERED; do not silently drop it because it was
absent from the inventory's short summary.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E08 - Paired body/route reload, removal and restoration

**Header:** Phase 3; DS; development PC, real client A and console operator;
ordinary disposable copy, switches off, no properties. **Sources:** v1.4
section 10.3, MT140-08, DISC-MIG-01 and migration report.

**Preconditions:** P2 planetary world with visited/researched body and station,
complete backed-up packs, valid and invalid paired body/route variants, coherent
removed-body/routes/satellite-target variant and exact same-ID restoration.
Baseline/JEI absent. Capture catalog generation, destination availability,
station/progress identities and `advancedrocketrycommunity_planetary_bindings.json`.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Install the valid paired body/route pack on the copy. | Hashed candidate inputs recorded. |
| 2 | [H] Issue `reload`. | One validated body/route generation published. |
| 3 | [H] Install the declared invalid pair. | Deliberate validation fault recorded, no source world change. |
| 4 | [H] Issue `reload` again. | Previous valid pair retained; bounded diagnostic, not an empty catalog. |
| 5 | [H] Install the coherent removed-body/dependent-route/target variant. | No dangling route/mission references accidentally invalidate the intended removal test. |
| 6 | [H] Issue `reload`. | Removed body unavailable for new entry; saved Level/binding/station/discovery records not deleted. |
| 7 | [H] Attempt a new arrival to the removed body. | Safe refusal, no fallback or fuel/asset loss. |
| 8 | [H] Restore the same body identity plus valid dependent routes/targets. | No remapping/reuse of old identity or binding deletion. |
| 9 | [H] Issue `reload`. | Prior discovery/routes become usable again; paid pending discovery can finish without a second reward. |
| 10 | [H] Save/stop and restart the copy. | Station/rocket/progress/binding identities retained. |
| 11 | [H] Repeat step 10 for the second restart. | No extra migration/claim replay. |
| 12 | [H] Compare original fixture hashes and all preserved projections. | Originals intact; no unexplained resource/identity change. |

An allowed **blocked historical-progress** variant also follows DISC-MIG-01.
That independently backed-up state remains required; never erase historical IDs
to make it proceed. Run this additional step list on that variant: Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the blocked claim, paid receipt, account totals and discovery IDs. | Complete historical baseline; unpaid READY is not treated as an already paid claim. |
| 2 | [H] Repeat the claim through the ordinary terminal. | No second reward/fee; an unavailable target or **128 historical-discovery-ID** capacity does not evict an old ID. |
| 3 | [H] Restore the same unavailable target with its valid related definitions. | Original identity retained; no replacement body with a different ID. |
| 4 | [H] Observe bounded automatic retry. | The paid discovery completes when its documented preconditions permit, without another award; capacity blockage may remain blocked. |
| 5 | [H] If still blocked, follow the documented operator recovery on a new backed-up copy. | Record the actual action and outcome; no erased history, selective store merge or manually promoted scratch file. |
| 6 | [H] Compare the complete ledger and pending state again. | Exactly one paid receipt; retained IDs/assets and any unresolved condition documented. |

The binding file separately retains **128 lifetime bindings / 32,768 UTF-8
bytes**. A `.json.pending` binding file blocks startup pending recovery; it is
not the research/discovery `.arce-pending` scratch file and must not be promoted
or deleted as a normal test shortcut. This row is S1/reload, not an S2 cut claim.

**Accepted preparation/action steps (W408):**

(C23) Recovery is only a complete-world backup restore or documented station commands on another backed-up copy. If a variant has no supported recovery, report it as such. Never selectively merge backups, erase IDs or promote scratch files. Preparation is required for the earned paid-pending/blocked histories, coherent removal/restore variants and supported recovery recipe; missing pack load/quote controls still require qualification. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open. Entry E-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications.

**Evidence to keep:** `phase3-ds/E08/` all pack variants, generation/reload/
refusal/restore logs, binding and station/progress projections, pending-claim
receipt/balance timeline, restart state and unchanged-original checks.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E09 - A station around a newly added body

**Header:** Phase 3; DS; development PC, real client/account A; ordinary
copy, switches off, no properties. **Source:** v1.4 section 10.5.

**Preconditions:** P2 planetary scene, discovered/accessible Mars or Venus
surface, station-eligible account with ownership capacity, legal fueled rocket;
baseline/JEI absent. Record the actual body/Level and station ownership limit.
Use ordinary station creation while standing on that mapped orbitable surface.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Arrive on the selected new surface by normal flight. | Correct body context and authorized arrival. |
| 2 | [H] Create the station through supported station controls. | New stable station UUID with that surface's orbit-body ID. |
| 3 | [H] Request the created station through the rocket console. | Correct named station/quote and permission checks. |
| 4 | [H] Travel to the station. | Shared Space Level, committed station region and correct orbit context. |
| 5 | [H] Save/stop and restart the same copy. | Station orbit, ownership, region, rocket and inventory preserved. |
| 6 | [H] Reconnect A and inspect the orbit information. | Still the chosen new body, not implicit Earth/Space. |
| 7 | [H] Repeat steps 5 to 6 for the second restart. | Stable context/rights after reopen two. |

**Accepted preparation/action steps (W408):**

(C8) Use an account with ordinary ownership capacity, not a bypass; the two real accounts each create one owned station by ordinary play. Preparation is required for the eligible owner/surface scene and for the station command/region manifest. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open. Entry E-K08 (R621/R627) supplies the following inputs: station sets, two-account recipes and ordinary-energy copies. Ordinary account binding, consent/warp/docking/membership actions and the listed live/REF observations remain open.

**Evidence to keep:** `phase3-ds/E09/` creation/arrival receipts, station
UUID/orbit/member/region and environment projections, both restart logs and
client orbit screenshots. E03 is the separate nonmember/private-access matrix.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E10 - Representative upgrade worlds and pre-sky preservation

**Header:** Phase 3 DS, phase-2 V1 observation for SKY-V1-08; development PC,
real GPU client A; ordinary copies, switches off, no properties. **Source:**
MT140-09, migration report and SKY-V1-08.

**Preconditions:** P2 **representative v1.3 to v1.7 worlds**, individually
inventoried before C4, plus a pre-sky world variant for the specific sky
upgrade check. Baseline/JEI absent; matching provider/pack hashes. Never create
new state on C6 and label it an untouched historical upgrade fixture.

| Step | Action, for each source-world variant | Expected observation |
|---|---|---|
| 1 | [H] Hash the complete stopped original and its backup. | Source identity/file inventory recorded before copying. |
| 2 | [H] Copy it into a new run directory. | Original remains read-only and recoverable. |
| 3 | [H] Open the copy with C6 and matching dependencies. | Supported migration or explicitly retained refusal; no silent unknown-data loss. |
| 4 | [H] Capture stable body/Level/binding IDs, station orbits/rights, rockets/cargo/fuel, research/discovery and provider payloads. | Field-level conservation with legitimate elapsed progress reconciled. |
| 5 | [H] Capture Moon/Space positions, terrain/time/light settings and inventories in the pre-sky variant. | Same identities/settings/contents as the historical fixture; renderer changes do not migrate gameplay state. |
| 6 | [H] Save/stop and reopen the same copy. | No repeated migration/debit/reward. |
| 7 | [H] Compare the full saved projections. | Stable identities and exact accounting, not just successful startup. |
| 8 | [H] Repeat steps 6 to 7 for the second reopen. | Idempotent second migration/readback. |
| 9 | [H] Continue one representative station/rocket/research operation. | Upgraded world remains playable. |
| 10 | [P] Inspect pre-sky Moon/Space presentation. | New effects only where dimension-type effects opt in; higher-priority type overrides remain authoritative. |
| 11 | [H] Rehash every original/backup. | No original changed; failures stay with their own copied run. |

**Accepted preparation/action steps (W408):**

(C7/C8) Preparation is required for the hashed historical v1.4-v1.7 chain; preparation is required for the pre-sky copy from an eligible ordinary owner. Label rebuilt worlds "reconstructed on JAR (hash), not historically played". No retained played pre-sky source means the played-world claim is NOT_COVERED; format migration remains testable. Keep earned balances/bindings, no ownership-limit bypass. Entry E-K03 (R614) supplies the following inputs: reconstructed FI05-FI08 chain. Historically played-source claims and the listed historical-state gaps remain NOT_COVERED. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

**Evidence to keep:** `phase3-ds/E10/<source>/` old runtime/world/pack
hashes, raw inventory and schemas, migration differences, both restart logs,
continued-use receipts and original-hash comparison; phase-2 pre-sky screenshots.
This is not direct 1.12.2 loading, arbitrary downgrade or a complete R-044 waiver.
Do not merge state files from different backups to claim recovery.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E11 - Sky resource lifecycle and reference measurements

**Header:** Phase 2 V1 sky profiling and phase 5 REF; real GPU client A plus
accepted normal-user 4-vCPU/8-GiB Debian node/workload accounts; ordinary worlds,
switches off, no properties. **Sources:** v1.4 section 13.2 and GATE-1.4 G7.

**Preconditions:** P2 planetary scene and **reference workload world of
docs/17 section 4**; baseline/JEI absent, server heap <=4 GiB. Same-machine
Vanilla/Forge frame-time baselines from A12. The actual resource-allocation/cache
profiling tool and version still require qualification; screenshots alone cannot prove
that sky resources are not rebuilt every frame. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record a steady planetary-sky frame/allocation trace. | Cached resources reused, not rebuilt on each rendered frame. |
| 2 | [H] Trigger the valid resource reload. | Bounded deliberate buffer rebuild; obsolete buffers released. |
| 3 | [H] Change world/body and repeat the trace. | Correct cache invalidation, no stale geometry or monotonic retained growth. |
| 4 | [H] Sample CPU/RSS/GC/tick/network/cache under the reference workload. | Raw values tied to hardware/workload/interval and catalog size, not empty-server inference. |
| 5 | [H] Compare the client trace against its baseline. | Frame times, stalls and cache allocation reported; no invented absolute FPS/resource count threshold. |
| 6 | [H] Compare server measures against the quality budgets. | Mean/P95/P99 MSPT <=25/50/100 ms, sustained RSS <=5.5 GiB, no OOM/watchdog/unexpected forced chunks; report every CPU/network/cache finding. |

**Accepted preparation/action steps (W408):**

(C5, R535; W403) Keep paired Vanilla/Forge frame measures, 1% low, first/longest stall and twice-run baseline spread for Root review only; no FPS floor, stall limit or extra CPU/network/cache limit exists. Preparation is required for the actual per-frame allocation/GPU/cache and raw-tick observation, not proof from histograms/screenshots; missing direct proof is NOT_COVERED. Preparation is required for the reference world. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open. Entry E-K17 (R648) supplies the following inputs: combined reference world and four-hour schedule. The full four-hour REF/two-real-client run remains unexecuted; decaying missions do not establish sustained load.

**Evidence to keep:** Phase-2 raw resource/frame/allocation traces and E06
media references; `phase5-ref/<run>/E11/` host/JVM/workload/config identity,
all raw CPU/memory/tick/network/cache samples and explicit budget comparisons.
Sources supply no extra numerical CPU/network/cache limit; report that absence,
not an invented pass criterion. Dedicated server must not load sky client classes.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **Answered:** C1 flight protocol 10; C19/W406 itinerary Earth-Mars-Earth-Venus-Earth; C7/C8 history/ownership; C20 default modifiers; C21/W406 catalogs/source cooldown 100; C2 effective pixels; C22 integrated pause/sitting-2 lifecycle; C23 supported recovery only.
- **E01-E11:** Planetary coordinates, earned world variants and effective mission/config/resource values remain required; live quotes/pack loads/recovery/runtime cooldown still require qualification. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open. Entry E-K06 (R550) supplies the following inputs: 23 hashed data/resource packs, not blanket live qualification. Live loads, quotes, cooldown behavior and restart observations remain unqualified except for the separately cited package qualifications.
- **E02/E03:** Earned undiscovered/private-station variants remain required; O5 supplies second real input. Never reset balances. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.
- **E03/E05/E07:** Forbidden-surface/cache/late probes with receipt/A1 references remain required; direct allocation/cache observation remains required. Entry E-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.
- **E04:** Isolated/combined default-modifier hazard sites/equipment remain required. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.
- **E05:** exact 48-character station name is answered; permitted creation still requires preparation. Valid catalogs use separate complete 11-binding baselines; never erase history or use invalid catalogs as setup. Entry E-K08 (R621/R627) supplies the following inputs: station sets, two-account recipes and ordinary-energy copies. Ordinary account binding, consent/warp/docking/membership actions and the listed live/REF observations remain open.
- **E06/E11:** Live observer/client sky-pack qualification remains required; viewpoints still require preparation. O8/O9 confirm capture and second GPU category. Entry E-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.
- **E07:** A qualified integrated pause round remains required, otherwise pause alone is NOT_COVERED. Sitting-2 SKY/navigation V2 lifecycle remains required. Entry E-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.
- **E08:** Earned paid-pending/blocked copies, coherent packs and actual supported recovery remain required; absent supported recovery stays explicit. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.
- **E09/E10:** A station-eligible owner/commands, historical chain and pre-sky source remain required; the played-source claim is NOT_COVERED where not retained. Entry E-K08 (R621/R627) supplies the following inputs: station sets, two-account recipes and ordinary-energy copies. Ordinary account binding, consent/warp/docking/membership actions and the listed live/REF observations remain open. Entry E-K03 (R614) supplies the following inputs: reconstructed FI05-FI08 chain. Historically played-source claims and the listed historical-state gaps remain NOT_COVERED. Entry E-K07 (R617) supplies the following inputs: seven earned planetary/hazard variants. Real-player hazard isolation, retained pending-menu controls and the listed live-client observations remain open.

## W396 preparation and verification record

This section replaces a separate W396 record because TASK-W396 permits changes
only to `CAMPAIGN-CHECKLISTS-A.md` through `CAMPAIGN-CHECKLISTS-E.md`. W396's
write scope is exactly those five files on `codex/v1.8.0-w396`, based on shared
commit `d8fddeaab`. The delivered documentation commit is reported in the final
worker output; its source base is not misrepresented as a tested product build.

Completed scope: one numbered action/expected-observation checklist for each
of the 56 inventory rows A01-A18, B01-B07, C01-C08, D01-D12 and E01-E11.
The files preserve blank result fields and identify fixtures, account/machine
requirements, Forge/JEI cells, switch-off ordinary-world execution, media/data
to retain and unresolved inputs. Additional source-manual checks are included
under the relevant existing row IDs rather than changing the campaign plan.

Design choices: reuse P2's fixture names, require concrete run manifests before
execution, use matching C6 artifacts instead of historical protocol pins, keep
C05's O6 disposition distinct from a pass, and keep missing tooling/values
explicit. No ADR decision, performance limit, production switch or Gate changes.

Not done: campaign execution; fixture/harness implementation; Gradle, GameTest,
native server, clients, actual V1/V2/REF/SOAK, push, merge, tag or release approval.
No automated tests are added or modified for this documentation-only task.
OpenPet was unreachable; its failed progress event did not block the work.

Commands actually used for preparation were `Get-Content`, bounded Python text
selections, `rg`, `git status --short`, `git branch --show-current`,
`git log -1 --oneline`, `git worktree list` and
`Get-CimInstance Win32_Process`. They established the task/rules, clean starting
worktree, assigned branch and independent W397 process. Source reads had no
product execution. Some broad reads exceeded the tool output limit and were
repeated as bounded sections; a nonexistent Electrolyzer-recipe lookup produced
no input and was not used as evidence. `apply_patch` created only the five
assigned outputs.

Verification performed on the five staged outputs:

| Command or check | Actual result |
|---|---|
| `python -B scripts/validate_repository.py` | Exit 0 on three runs, including the final staged check: 45 passed, 0 pending, 0 warnings, 0 failed each time. |
| Inline Python coverage/form/link audit against `git ls-tree -r --name-only d8fddeaab` and the plan's inventory | Exit 0: exactly 56 original IDs in order, 504 numbered actions, 76 links to files at the shared base; every row has preconditions, header, evidence and five blank result fields; each file under 1,500 lines. |
| `git diff --cached --check` | Initial draft failed on 90 trailing spaces in A's blank fields; all removed with a scoped patch. Recheck passed with exit 0. |
| `git diff --check`, `git diff --cached --stat`, `git diff --cached --numstat` and scoped diff review | Whitespace checks passed; only the five assigned Markdown outputs are staged. |
| Inline Python staged-scope check using `git diff --cached --name-only` | Exit 0: write scope is exactly A through E. |

Several context-only patch attempts were rejected without changes. No failed
command or unsuccessful source lookup is counted as passing product evidence.
The local documentation commit uses `docs(test): add campaign checklists for groups A-E`;
its resulting SHA is in the final worker response and discoverable with
`git log -1 -- docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-E.md`.
No sealed evidence packet or additional worker record is created.

Known risks: P1/P2/P3 still need concrete run manifests and some packaged
fixture/intent/cache controls; O5/O7/O8/O9 require the owner's account, time or
equipment arrangements. Historical protocol/artifact references cannot substitute
for matching C6 identities. These are execution prerequisites/open questions,
not passed observations. No current version's complete Required Gate set is
satisfied by these written procedures.
Remaining work is Root's review of P4/open questions, P1-P3/P5-P9 preparation and
the scheduled v1.8 final campaign, not implementation of a later version.
