# Campaign checklists I: v1.8 version acceptance

These are unrun P4 lists for I01–I11 of [the campaign](FINAL-CAMPAIGN-PLAN.md), using
[v1.8 sections 10–13](../../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md), [W62](W62.md),
[W188](W188.md) and the current, not superseded, model decisions named under I07.
No Gate or version is approved.

Accepted common procedures for **every I row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared preconditions, execution labels and evidence

H marks a harness/operator action; P marks a person's action/judgement. Each arrow gives the expected observation.
Use P1's C6 commit and hosted JAR SHA-256 on Java 17. Default is Forge 47.4.10 without JEI; compatibility is
47.4.23. JEI-present means exactly 15.56.0.205. I04 explicitly covers all four cells; I09 covers both JEI states.
V1 means a real GPU, with CPU/GPU/driver/resolution/window/GUI-scale recorded, not Xvfb/LLVMpipe.
P2 supplies immutable source fixtures; use disposable copies only. Its names below are fixture descriptions,
not already delivered archive filenames. These **needs package K02** — in progress (W447); entry I-K02 (historical), **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)** (machine) or
**K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)** (live control) archive hashes, coordinates, IDs and setup commands, as the rows specify.
Pin server difficulty, view/simulation distance, config and loaded switches before each run.

Phase 3 DS uses the packaged server on the development PC with one real client A. Phase 4 V2 uses two real
authenticated premium accounts A/B on the restricted packaged server with `online-mode=true`, `white-list=true`,
`enforce-whitelist=true`, `enable-rcon=false`. No ordinary I-row needs the marked bay/fuel campaign gate or extra
JVM property. Admission stays off; do not use gated cargo/fuel/bay transfers as ordinary production technology steps.
Gated asset/status views, if separately needed, belong to J and retain their own switch-off comparison.
Phase 5 REF/SOAK uses a non-root user on 4 vCPU/8 GiB/Debian 12, Java 17, server `Xmx4G` maximum,
bound to loopback with SSH-tunnel client access on an exclusive node.

Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`. Use
`E/phase2-v1/<machine>/<round>/`, `E/phase3-ds/<row>/`, `E/phase4-v2/<sitting>/` and `E/phase5-ref/<run>/`.
Retain `server.log`, native client logs, `results.jsonl`, `summary.json`, configuration, fixture and candidate
identities, raw samples and per-step UTC times. Screenshots are `<row>-<step>-<client>-<language>-<scale>.png`;
videos are `<row>-<client>.mkv`. Index each artifact/reference/SHA-256 in `E/results/RESULTS.md`, `results.json`
and `FINDINGS.md`; seal `E/SHA256SUMS.txt`. Each result also records Forge/JEI, machine, tester/script revision,
start/end UTC and JAR SHA-256. Missing prerequisites mean NOT_COVERED with a reason, not a speculative PASS.

## Package preparation index (W456, R649)

This index resolves the package notes below; it is preparation, not execution or a Gate verdict.
Each entry gives the sealed input, its checksum-list SHA-256, the recipe/step mapping and the
remaining partial or NOT_COVERED work. Paths are external read-only inputs, never repository links.
Only K02 (W447) and K12 (W454) remain package deliveries in progress. A delivered recipe is not
an observed client result; preserve every original action, oracle, result field and budget.

### I-K02: historical sources; forced-stop qualification in progress

Source: `W431.md; W437.md; QUEUE.md rows W431/W437/W447`. Root acceptance/disposition: **R611/R631** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/SHA256SUMS.txt` | `06bf9946a02e6bda58577811c9c7b69ca84fb779f38f14fa7843fd6d42f357ab` |

**Recipe and original-step mapping.** FI01/FI02/FI03/FI04/FI09 manifest.json and qualification directories map respectively to A03, B01, B04, pre-sky/API, and I03. Forced-stop runners retain W437's explicit sealed-disassembly option.

**Remaining coverage and reasons.** Needs package K02 — in progress (W447). W437's flight 2 PASS/8 FAIL (JOURNAL_PENDING) and transaction 13 PASS/12 NOT_REACHABLE_ON_C6 stay unchanged pending the follow-up. Historical sources are reconstructed, not historically played; genuine adjacent-chunk/current-seat/expiry controls remain separate, NOT_COVERED where absent.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I03 | FI01 manifest/qualification -> A03. FI02 -> B01. FI03 -> B04. FI04 -> pre-sky/API baseline. FI09 -> I03. A04/A05 runner controls remain W447 in progress; do not turn W437 failures/unreachable cuts into observations. |

### I-K04: legal 64/512/2048 and multi-rocket inputs

Source: `W434.md; QUEUE.md row W434`. Root acceptance/disposition: **R605** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/64/SHA256SUMS.txt` | `f131c6a61f3e4574535191465cdf9eea5f4a0bacd6144294efb967c371e33925` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/512/SHA256SUMS.txt` | `07a61be26a85db8893bab6782ea035fe1e8c1bdb54216d57e0700e169d1a4619` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/2048/SHA256SUMS.txt` | `df6588af878a25bf26c6c44ae6772c3e3c4fa5473d85776ef081a6ecc7a30713` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/multi/SHA256SUMS.txt` | `4bf6bd3310caff95147f6263802a046c7f6b01a91de0e461ef19944b70306627` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/64-relaunch/SHA256SUMS.txt` | `0b8e89a5850fd43d5ef3d6a85cd46f0a456d7e4e94e3d8b94e8862c4b4bb4c4c` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/512-relaunch/SHA256SUMS.txt` | `3509ddb6afe671fd723f976b1ddee1d93c616569c99b753c09b62867f882c623` |

**Recipe and original-step mapping.** Per-scene layout.json rockets/grants/overview and manifest.json build/readback map A10's 2048 assembly and A12/I09's first-use, relaunch and multiple-rocket observations. Use recorded native snapshot counts, seat blocks and observer positions.

**Remaining coverage and reasons.** Only these six qualified sources are selected here. W434's last 2048-relaunch readback was still unfinished at its record checkpoint; do not infer a new verdict from a later file's presence. The separate 128-payload boundary is NOT_COVERED: its synthetic test builder is absent from C6 and the framed 1-MiB specimen is refused. No GPU/cache/frame or operation-duration verdict is supplied.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I09 | Same six layouts -> original rendering/first-use/reload/heap comparison; the separate payload-boundary.json is NOT_COVERED, not an admitted visual scene. |

### I-K05: nine r608 machine scenes and field inventory

Source: `W435.md; QUEUE.md row W435`. Root acceptance/disposition: **R612; R608 preparation choices** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/a100-idle/SHA256SUMS.txt` | `239f4f439f8875dfb36995e8773355a49a7b5f3f9e78eb7393b38dfe836fd10f` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/b144-idle/SHA256SUMS.txt` | `3b4c28ac927e28cf6ed6ba40d7d4ba77cfea9c220ab8cda5fdf7c237a82dfec9` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/b144-half-active/SHA256SUMS.txt` | `b2bb9eeadcb3448846ae91b8178e192ad70a080de70d6d7637c97678ad9f457d` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/b144-unload-restart/SHA256SUMS.txt` | `a76b37a2ad9ed083148dea561684a836e2ee0edfce2c3f35c16985ba0ff4b779` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/damage-structure/SHA256SUMS.txt` | `8cac9c169dbc4e6b9fd093096b01fb9ff17b1b954dddb74c796df31989a78417` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/damage-controller/SHA256SUMS.txt` | `d52eeca0b94cb5ee11a6b16e4807b438581305555a99a12fa510861e61cf4c7d` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/damage-explosion/SHA256SUMS.txt` | `7f654afab7bc0bc10c1c87df03f0dce31b513faf6ada561c7f67c2acc4716297` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/two-owners/SHA256SUMS.txt` | `09bb41f6656db14fbdd8e43898441e595ec3b1008f5d18195461fd3529aa6eca` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/r608/declared-limits/SHA256SUMS.txt` | `11d82f88f3f5c88c1899d1a3b98d7e58b5ac92da8e12a1fc8278bf6b4e98cca9` |

**Recipe and original-step mapping.** r608/manifest.json recipes/scenes/declared_fields and each scene's manifest.json layout/ledgers: C01 a100/b144; C02 unload; C03 two-owners; C04 three damage modes; C06 declared-limits; C08 distance; I02/I06/I08 and J21 machine views. Match the original numbered action to its named scene, not to a passing verdict.

**Remaining coverage and reasons.** Half-active preparation is 56 real, energy-exhausted jobs plus 16 START_BY_MENU research sites, not 72 running jobs. Supply ordinary FE, load the 36 named chunks, bind accounts and perform the 16 menu starts before sampling. C13 has 18 fields inventoried but live at-limit/one-past refusal is NOT_COVERED (read-only projections/no setter). Cross-chunk ports, shared Precision, repair/resume, all delivered kinds/facings and actual GUI/V1/V2 remain partial. Piston rig is out of scope. C08 squared distance is 64 (radius 8), but no hostile intent was sent.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I02 | a100/b144 manifests -> original coexist/unload/restart actions for nine NORTH kinds; other kinds/facings not inferred. |
| I04 | r608/manifest.json recipes -> recipe execution/display comparisons; actual JEI counts/pages remain unexecuted. |
| I06 | Nine-kind layout views -> model-family/high-risk inspection; K13 is the complete inventory, not these nine kinds. |
| I08 | Layouts -> original scale/language/reload steps; per-screen real-client coverage remains unexecuted. |

### I-K13: asset/screen/sound/access inventories and resource packs

Source: `W402.md; QUEUE.md row W402`. Root acceptance/disposition: **R546** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K13/SHA256SUMS.txt` | `6553bcb6a17fdea8f79f4d7569912f9c626e3e5b36cd21855ed5824ed46c6628` |

**Recipe and original-step mapping.** classification.csv/model-families.csv/screens.csv map I02/I06/I08 and J21; access-matrix.csv maps I11/K18; sound-events.csv/sound-producers.csv and k13-long-text-pack.zip map J12-J16; k13-missing-resource-pack.zip maps I07 with K14. Read each CSV's named row/category, not historical aggregate counts.

**Remaining coverage and reasons.** Inventory/resource generation is delivered, not successful client load/audio/visual coverage. Audio-enabled live profile was not built (W402 explicitly excludes it), and per-cell JEI runtime category/recipe counts remain NOT_COVERED until campaign C31. C6 copy's seal supersedes older-candidate input hashes; preserve individual pack hashes already recorded below. No captured 1.12 session exists for I07.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I04 | No runtime-count fixture: screens/asset inventory does not supply C31 JEI totals; original per-cell page and usage-query steps remain to run. |

### I-K14: partial-r3 paired/research/tall/sleep scenes and 39 scripted steps

Source: `W449.md; QUEUE.md row W449`. Root acceptance/disposition: **R642** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K14/partial-r3/core-scenes/SHA256SUMS.txt` | `2bca0a1174f1e8a688da11fc5b075dccfb07f60db0d404056305118f7ba22dd4` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K14/partial-r3/prior-sleep/SHA256SUMS.txt` | `c58fc2f24161925c96fadeccba1e0fa3c9f20a3d47481d1fe27632aacfea411a` |

**Recipe and original-step mapping.** partial-r3/client-steps.json keys I07.01-I07.07, J18.01-J18.14, J19.01-J19.11, J20.01-J20.07 map one-to-one to original steps; layout.json, values.json, sleep-hosts.json and frozen-recipe.json retain geometry/effective values/freeze text. For J21 N15-N19 use J20's lifecycle/interaction recipes plus K08 station controls; no batch or rider result is inferred.

**Remaining coverage and reasons.** 33 sites, 25 structurally formed; eight research controller cells deliberately empty for A's player placement. Tall rockets are unfuelled/console-owned; named pad/elevator links/seat and rider actions remain scripted. FI08 prior-sleep has zero players, so prior-home/forced/pending-backup cases are NOT_COVERED there. R642 requires a new copy of the campaign's own A/B slept-and-saved world for those cases; V02 holders, V03 packs and V12 portal are campaign host steps, not already qualified. Callback/B1/R1, ordinary sleep, visible break/repair/tracking, real menus and every client-steps status stay NOT_COVERED until executed. Planet respawn extension is not implemented, not a passed row.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I07 | partial-r3/client-steps.json I07.01-I07.07 -> original steps1-7, one-to-one; layouts and missing-resource pack are input only. All seven visual/tracking outcomes remain NOT_COVERED. |

### I-K15: bounded observers, with explicit remaining visibility gaps

Source: `W403.md; QUEUE.md row W403; W452.md/R648`. Root acceptance/disposition: **R548; R648 for A08/A09** in `../QUEUE.md`.

**Recipe and original-step mapping.** Tracked scripts/campaign_observe/{ticks,process,jdk,host,evidence}.py and scripts/run_campaign_observer.py at implementation 385c079dc10718f781a425527cd3e9a0765a0518 supply matched-copy window/process/heap/JFR/evidence operations. Use sample_ticks/force-load queries and owned-process-only diagnostics at the row's existing sampling step; K17 schedule and K08/K09/K10 load drivers supply concrete harness integrations.

**Remaining coverage and reasons.** W403 created no sealed live fixture or SHA256SUMS.txt: none is invented. Window aggregates cannot prove whole-run percentiles/spike lengths. C6/K17's release-test raw sampler is used only in the separately labelled R648 REF run for A09. All-Forge-ticket, queue-internal, client/GPU allocation and direct cache-release proof remain NOT_COVERED where unavailable; histogram/heap/RSS proxies are labelled, not direct evidence. Actual REF acceptance/full windows and overhead checks remain unexecuted.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I09 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |
| I10 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |

### I-K16: v2 core scene, mapped controls and conditional recheck batches

Source: `W427.md; W443.md; QUEUE.md rows W427/W443`. Root acceptance/disposition: **R595; R628 supersedes final/ with v2/** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/SHA256SUMS.txt` | `27df12a711d23f500dd7370fab35a6c5d457f6eebd93e8366ee191889b140fa9` |

**Recipe and original-step mapping.** v2/batch-index.json maps each row/step/file; manifest.json core_route/devices/supplies and candidate-recipes.json give counts/positions/recipes. A02/I01 use core-00/core-01, core-room/core-suit, core-rocket-assemble/-fuel/-invalid, core-flight-plan/-observe; A06/A13/A17 use core-station/core-screen-*; E07 uses sp-pause; J20/J21 use rechecks.json and batches/j21-*.json. controls.json gives effective viewport targets, not old pointer replay.

**Remaining coverage and reasons.** R628 accepts only the en_us 1280x720 scale-3 dedicated rehearsal; scale2/fullscreen/zh_cn, integrated pause and 63 J21 requests are generated, not rehearsed. Fullscreen screen gate needs checking. B-5/3-2/8-2 remain checkpoint-only; no complete continuous quantitative crafting witness or all-screen/first-join/dual-seat/station ownership-transfer procedure is supplied. Return flight/paid warp/satellite flow, O5 accounts, N15-N19 physical setup and V1/V2 remain partial. Never replay old NBT-writing setup; R638 applies. Desktop-wide Alt/Shift are the recorded night-round operating condition.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I01 | manifest.json core_route, candidate-recipes.json and core-00/-01/assemble/fuel/flight/railgun -> original continuous route actions; granted core route is not a quantitative vanilla-to-endgame crafting witness or O4 hand-played subset. |
| I06 | controls.json/screen requests -> original high-risk/language inspection inputs; not every C30 screen or model has a complete batch. |
| I08 | controls.json/p9-session-*/core-screen-* -> original viewport/language/reload actions, with K13's full screen inventory; only one cell/scale/language rehearsed. |
| I11 | Core devices -> normal menu/holder preconditions; K18 supplies only its finite intent cases; missing branch/control remains NOT_COVERED. |

### I-K17: combined reference world and four-hour schedule

Source: `W452.md; QUEUE.md row W452`. Root acceptance/disposition: **R648** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/source/SHA256SUMS.txt` | `d03941ecdb4bff0d1742cf452959293b951e2e26b2972d8983e6f5c0ccd3daf1` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/readback-settled/SHA256SUMS.txt` | `a4aba18f3649e82a8e00e5d20f0efc508ad3c35c8b21357938a9811c7efc3df4` |

**Recipe and original-step mapping.** source/layout.json maps the 16 Moon rooms, ten station IDs, mission records and 2048/64/64 pads. source/schedule.json actions and client_steps.{connect,assembly_2048,flights,missions,a11} map A08-A11/I05/I10; use k17_reference.py schedule/run on disposable copies. Two 7200-s sampled segments, 120-s warm-up, flushed saves each 1800 s, one restart at sampled 7200 s, autosave retained.

**Remaining coverage and reasons.** R648: A08 has no test properties; keep forge tps means/RSS/GC/forced chunks. A09 is a separate labelled REF run on the same world/schedule adding only -Dadvancedrocketrycommunity.releaseTestHooks=true for the read-only raw sampler; compare forge tps means under C10's baseline-spread review rule. The 16 forced room chunks are declared workload, not unexpected tickets. Without clients flights/disassembly/dimension transfer remain scripted; ACTIVE missions fall from 101 to zero within ~6000 ticks. Record actual counts and ordinary refills, not a sustained-load claim. K17 does not supply sustained 500/1000 mission or H07 endgame throughput. Full four hours/REF/two real connections/all-ticket/direct-cache evidence remains unexecuted.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I10 | source/schedule.json actions/client_steps all -> original four-hour joint workload; A/B flight, disassembly, transfer and mission refills are scripted, REF and declared Forge/JEI coverage remain. |

### I-K18: bounded client intent JAR and case/readback matrix

Source: `W432.md; intent-test-mod/README.md; QUEUE.md row W432`. Root acceptance/disposition: **R601** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS.txt` | `aac26e987be0617ad63cec39b54aefb0fe7f3372085863ab14cd49731e392e57` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS-CORRECTED-1.txt` | `d29b209b8e7133f2d50f8463d3d9a1fa616572b2fe8b919ddbe58de254ec188f` |

**Recipe and original-step mapping.** Install arce-intent-test-1.0.0.jar only on the probing client. intent-test-mod/README.md Triggering one case steps 1-6 and Case table map C08/D09/I11 flight_stale/range/non_owner/late and classic_stale/range/non_owner/late; E03 flight_forbidden_surface/private_station; E07 late and motion_disabled. R/C/M/L readbacks are required separately. F8 captures; a single F9 queues at most one bounded packet.

**Remaining coverage and reasons.** Use SHA256SUMS-CORRECTED-1.txt: the original list's final-validator hash is stale and preserved, not silently repaired. There are three C2S channels, five S2C-only; no payload-terminal, research-menu, callback-fault, packet-burst, late-S2C/cache, all-ticket or same-tick claim control exists. Those branches remain NOT_COVERED with the README's named A0/A1 references/gaps. Client queue log is not server receipt. Actual real-client/server qualification and unchanged-state/loaded-chunk readbacks remain campaign work.

**Mapping in checklist I.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| I11 | All applicable README case-table rows + R/C/M/L and 28-menu holder map -> original policy actions, respecting actual public/member/owner/OP access; no self-only distance policy invented. |

## I01 — One technology route from vanilla resources to endgame

**Phase/level/machine/accounts:** 2 DS/V1, main real-GPU client A and packaged server.
**Gate:** Ordinary P2 technology-route world; admission switches off.
**Preconditions:** P2 new-world route fixture and the candidate's acquisition/reachability witness reports.
O4 is the owner's pending confirmation of scripted rehearsal plus a recorded hand-played subset, not a requirement
for a full survival-played run and not authorization to omit the complete route.

1. [H] Freeze the witness route, recipes, counts and source resource ledger. → Starts with vanilla resources;
   console grants or scene setup are disclosed rather than represented as survival acquisition.
2. [H/P] Follow the witness to the combustion generator. → The generator is crafted/formed and provides the expected
   server-authoritative energy from its documented inputs.
3. [H/P] Produce the route's classic-machine components through their actual processes. → Inputs, outputs,
   intermediate machines and advancement steps match the candidate's witness, without unavailable required nodes.
4. [H/P] Build, fuel and fly the route's legal rocket to the Moon and return. → Valid travel, inventory/fuel and
   life support remain coherent; no creative/gated fill is passed off as technology-route completion.
5. [H/P] Establish the station and complete an inter-system warp. → Costs/consent and station context agree with the server.
6. [H/P] Build and operate the selected endgame device. → Its normal recipe, permission and operation complete the route.
7. [P] Perform the owner-agreed subset by hand on C6 and review the full rehearsal video. → Exact covered steps,
   setup shortcuts, controls and judgement are recorded; build hash appears in the video index.

**Accepted preparation/action steps (W408):**

(C29) Use one continuous scripted C6 witness from first machine to the **railgun pair** endgame device (accepted choice), including recipes/counts/resources. **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**: route scene and witness manifest. O4 still chooses/records the hand-played subset; rehearsal is not a full survival-played claim.

**Evidence to keep:** Continuous/referenced route video, witness route and resource ledger, per-stage screenshots,
server/client logs and the owner-confirmed hand-played subset under phase 2.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I02 — Many machine types coexist, unload and scale

**Phase/level/machine/accounts:** 3 DS on development PC with A; 5 REF on the reference node.
**Gate:** Ordinary P2 many-machine layout.
**Preconditions:** The P2 many-machine fixture (W62's S6 layout), enumerating every delivered v1.8 machine kind,
idle/active states, coordinates, recipes, buffers, energy, fluids and progress. C30 selects 144 controllers without another scaling factor.

1. [H] Capture the complete layout's initial state. → Every machine kind is present, not just the old v1.2 idle fixture.
2. [H] Run the selected machines together. → Processes progress with exact resource conservation and bounded queues.
3. [H] Remove fixture-owned loads and move A away. → Machine chunks unload without permanent tickets.
4. [H] Return and then restart the copy twice. → Compare resources/progress after each reload; no duplicate output
   or lost state. This also covers W62's requirement to restart every v1.8 machine mid-job twice.
5. [H] On REF, scale the same declared layout and record idle measurements. → Work/cost is measured rather than
   inferred from a “no recipe search” counter.
6. [H] Activate the scaled layout and sample CPU/MSPT/RSS/GC/network. → Mean ≤25 ms, P95 ≤50 ms, P99 ≤100 ms,
   RSS not persistently >5.5 GiB, no watchdog/OOM/unexpected forced chunks; state the exact active population.

**Accepted preparation/action steps (W408):**

(C30) Use FI10's 144 controllers, 16 of nine kinds, with no further scaling factor. **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**: concrete many-machine layout/recipes/ledgers and per-machine restart sequence. Classification/inventory comes from W402/K13; no extra scaling is invented.

**Evidence to keep:** Phase 3 machine-by-machine inventory/progress/unload/restart comparisons and phase 5 layout,
scale factor, raw idle/active samples, queue/ticket counts and host record.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I03 — v1.7 placeholder and old-ID upgrade

**Phase/level/machine/accounts:** 3 DS, development PC and A. **Gate:** Ordinary upgrade copy.
**Preconditions:** P2 v1.7 world with every placeholder and old registry ID, its pre-upgrade ID/state manifest and
immutable archive/hash. The complete archive/ID manifest **needs package K02** — in progress (W447); entry I-K02.

1. [H] Hash the source archive and copy it without modifying the original. → Source remains recoverable and unchanged.
2. [H] Start that copy on C6 and enumerate old blocks/items/roots. → All stable IDs remain; replacement art/behavior
   does not silently delete placeholders or invent an ID migration.
3. [P/H] Use representative upgraded machines and inventories. → State and player resources are usable, not merely loadable.
4. [H] Stop and restart twice. → Compare the same IDs, contents and managed state after each start.
5. [H] Re-hash the original archive and backup. → Both unchanged; preserve failed copies separately if upgrade fails.

**Accepted preparation/action steps (W408):**

(C30) **Needs package K02** — in progress (W447); entry I-K02: immutable all-old-ID v1.7 source and manifest. Use K13's delivered inventory to enumerate new art; reconstruction remains labelled as such and is not a played-source claim.

**Evidence to keep:** Phase 3 archive identity, pre/post ID/state inventory, two restart comparisons and full logs.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I04 — Four Forge × JEI cells, real recipe display and usage

**Phase/level/machine/accounts:** 2/3 DS/V1, development PC, real-GPU client A.
**Gate:** Ordinary P2 many-machine/recipe world. **Preconditions:** Four clean profiles: Forge 47.4.10 and
47.4.23, each JEI absent or JEI 15.56.0.205 present, all with the same C6 JAR. Pin candidate-shipped category
counts from its resources/runner; W188's historical counts are not automatically C6 counts.

1. [H] Start the packaged server and client in the first matrix cell. → Correct Forge/JAR/JEI identity, no sidedness error.
2. [H] Join fresh. → For JEI-present cells, retain every category receipt in order: the last `status=registered`
   receipt after tags arrives has the shipped count; early zero/held counts are allowed, wrong final counts are not.
   Without JEI, require a normal join with no optional-class loading error, not a nonexistent JEI receipt.
3. [P/H] In JEI-present cells, open every ARCE category and inspect a tag-based input's alternatives. → Actual
   ingredients render, not barrier/“Empty Tag” placeholders. In absent cells, mark this JEI-only action not applicable
   and inspect the candidate's ordinary recipe/machine screen instead; do not attempt to open an uninstalled UI.
4. [P/H] In JEI-present cells, use recipe/usage lookup on representative inputs/outputs in every category. → Correct
   inputs, fluids, outputs and machine category in both indexes. In absent cells, exercise the pinned ordinary recipe.
5. [H] Run `/reload`, with JEI open only where installed. → Tags/recipes stay usable in the ordinary machine;
   present cells also keep the correct final JEI categories without duplicates.
6. [H] Stop and restart the same world twice and reconnect. → Tag gate resets per connection; correct final counts
   and screens return. Without JEI, ordinary machines and server work without optional client classes.
7. [H] Repeat steps 1–6 in each of the other three cells. → Preserve four separate results, not one combined receipt.

**Accepted preparation/action steps (W408):**

(C31; W400) Read category/recipe counts from C6 in each actual JEI-present named cell; W188 totals remain history. Show at least one recipe page and one usage query per category, capturing final synchronized receipts. The four manifest paths/hashes are in A. Runtime counts still **K13 supplied input/limits: asset/screen/sound/access inventories and resource packs; entry I-K13 (R546)**; recipe/machine scene **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**.

**Evidence to keep:** Phase 2 real-client pages/usages/screenshots and phase 3 per-cell command/profile manifests,
ordered category receipts, counts, initial/restart client/server logs and state comparisons. D9 limits v1.8 support
to these four cells; it does not prove or promise a third-party modpack matrix.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I05 — Long automated production conserves resources

**Phase/level/machine/accounts:** 5 SOAK on REF, two connected real players or O7 verified equivalent.
**Gate:** Ordinary P2 many-machine/reference workload, admission switches off.
**Preconditions:** A declared chain fed by vanilla hoppers or approved pipes, with recipe stoichiometry and initial
item/fluid/energy ledgers; no unknown third-party mod. Integrate this with I10's ≥4-hour workload.

1. [H] Inventory every input, output, internal buffer, tank and loose item before starting. → Exact initial totals exist.
2. [H] Run automated production for the complete multi-hour interval. → More than one batch completes, with no
   duplicated outputs, hidden spill or unexplained fluid/resource disappearance.
3. [H] Save and reload the chain during production. → Pending work resumes consistently; record restart boundaries.
4. [H] Continue production and capture final inventories/fluids/loose items. → Inputs minus recipe consumption and
   outputs plus in-progress state reconcile exactly; report conversions rather than equating unlike items.
5. [H] Compare periodic samples and final state. → No progressive resource gain/loss; an empty/idle soak is not coverage.

**Evidence to keep:** Phase 5 chain/recipe manifest, timestamped totals, all restart logs, conservation calculation,
spills and unresolved differences, alongside I10's continuous samples.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I06 — Sample all new presentation and inspect every high-risk asset

**Phase/level/machine/accounts:** 2 V1, A on real GPU. **Gate:** Ordinary P2 presentation/many-machine world.
**Preconditions:** Candidate asset inventory, sample selection and complete high-risk list, including all new
screens/models/textures/sounds. Missing sample definition is a preparation gap, not permission to omit a class.

1. [H] Map every new asset category and high-risk entry to a scene/screen. → Coverage is enumerated against C6.
2. [P/H] Display each selected screen/model/texture in its intended context. → No purple-black texture, absent
   mesh, clipped essential control or unreasonable model/collision placement.
3. [P] Listen to every selected sound at its real producer. → Sound and subtitle match the server-visible state;
   unbound producers are recorded, not silently “passed” with `/playsound`.
4. [H] Search the complete client log for missing model/texture/bake warnings and recurring OpenGL errors.
   → Zero missing model/texture acceptance; retain all matched lines and findings, not just a count.
5. [P] Review the contact sheets and free-look scenes. → Owner judgement is separate from harness capture.

**Accepted preparation/action steps (W408):**

(C30; W402/R546) Use K13's `classification.csv`/`model-families.csv`: inspect all high-risk OBJ/multiblock/armour and W244/W261/W265-corrected assets, plus one pick per other model family. Open **every screen once per language**, including the eleven no other row names (W402 section 4). The 48 unreferenced textures are v1.9 Low cleanup, outside this campaign (R546), not silently a passed sample. **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**: scene placement; screen navigation/recheck controls **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**.

**Evidence to keep:** Phase 2 coverage inventory, high-risk complete checklist, contact sheets, screenshots/video,
sound index, logs and matching-line list. I07/J15 provide deeper checks but do not replace the broad inventory.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I07 — OBJ, baked models, multiblocks and armour on a real GPU

**Phase/level/machine/accounts:** 2 V1 on real GPU A; phase 4 V2 supplementary formation/tracking observations
from W241, with B. **Gate:** Ordinary P2 presentation/machine/rocket scenes; no admission changes.
**Preconditions:** Sources [W241](W241.md), [W244](W244.md), [W249](W249.md), [W255](W255.md),
[W256](W256.md), [W258](W258.md), [W260](W260.md), [W261](W261.md), [W265](W265.md).
Use their final corrections: actual Capsule mesh, all-face `rocketpad_`, and flat laser-gun icon, not their earlier drafts.

1. [H] Form black-hole generator, orbital laser drill, normal precision assembler, railgun, rolling machine and
   surface elevator anchor in every supported facing. → Full-scale hull placement, UVs, lighting and culling are
   inspectable; smaller modern patterns may leave intentional world blocks visible, not automatic overlap approval.
2. [P] Walk around and through the elevator platform and inspect all six hulls from every side. → Record clipping,
   cross-cell overlap, z-fighting and clearance; anchor is canonical NORTH, not a legacy orbital hull or 508-block tether.
3. [H] Use the mirrored precision formation. → JSON/world-block fallback, not a falsely mirrored mesh.
4. [H] Rotate a retained directional controller, then restore its validated facing. → Hull clears on mismatch and
   returns after valid restoration; server formation/bindings are not rewritten by visual flags.
5. [H] Break/repair and unload/reload each body, with B observing in V2. → Formed hull and laser/accretion/tether
   effects agree on both clients; no stale hull after invalidation.
6. [H] Form the precision laser etcher in all four facings; rotate/restore its controller and break/repair its motor.
   → Hull follows validated facing, clears on mismatch, restores correctly; no coplanar overlap or wrong light/culling.
7. [H] Start, cancel and commit elevator rides; remove an endpoint and change Level. → One stationary Capsule per
   pending endpoint, doorway NORTH, no Door mesh; cage clears on terminal paths. Inspect inside/outside, countdown
   tint, rider visibility and the documented floor 0.25 blocks above rider feet/platform-corner protrusion.
8. [H] View both mono and both bipropellant motors placed, adjacent to tanks/pad surfaces and assembled on rockets.
   → Open nozzle does not hide neighboring faces, rim/seams remain inspectable; inspect UV orientation against W241's
   audited renderer inventory (C32, not unavailable 1.12 screenshots), flat/smooth lighting, particles and all eight item display contexts/both hands.
9. [H] Repeat motor inspection for the nuclear nozzle in all six facings. → Visual rotation follows each state;
   only the downward nozzle is flight-legal. No test assumes sideways legality.
10. [H] Inspect the static saw block and formed cutting machine in four machine facings. → Saw axle remains east–west;
    record its motor mismatch in two rotations and both placed/K2-hull blades crossing, parallelism or z-fighting.
    R285 accepts the axle limitation, not an unobserved visual result.
11. [H] Inspect the three standalone fuel tanks, open ends, adjacent faces, seams, particles, held/inventory views
    and assembled snapshots. → Rounded standalone mesh; no connected/top/bottom presentation is implied; nuclear tank unchanged.
12. [H] View the landing-pad grid from top, bottom and sides and in item/particle contexts. → `rocketpad_` on every
    face per R289, no connected border and no live NEW `landing_pad.png` side/particle assumption.
13. [P/H] Hold and use `basic_laser_gun` in both hands, then inspect GUI/ground/fixed/head views. → R293's flat icon
    and upstream hand transforms; judge grip, muzzle/beam alignment, animation, lighting, particles and sound, not the removed OBJ.
14. [P] Inspect armour and worn component fit from all sides, sneaking and with elytra. → No missing resources or
    severe clipping; pair with J14's full mount/HUD matrix.
15. [H] Press F3+T, reconnect and exercise approved missing-primary-resource packs. → Formed hulls use documented
    project fallbacks; missing/corrupt Capsule resource gives the existing line cage; ordinary baked motor/tank/saw
    models have no promised runtime fallback. Capture failures rather than pretending every model has one.

**Accepted preparation/action steps (W408):**

(C32) No captured 1.12 session exists; "compared with 1.12 screenshots" is NOT_COVERED for that reason. Compare instead to W241's audited renderer inventory at upstream `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, preserving W244/W261/W265 corrections and without importing unaudited art. W402 delivers `k13-missing-resource-pack.zip`, SHA-256 `81c43eea624037097b995d69d4317ee38a2b269f9fe99685c635acb35e88e1cf`, under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K13/`; its seven source-derived overrides/fallback expectations are W402 section 7, not observed loads. **K14 supplied input/limits: partial-r3 paired/research/tall/sleep scenes and 39 scripted steps; entry I-K14 (R642)**: phase-4 paired break/repair/tracking scene and actual pack checks.

**Evidence to keep:** Phase 2 per-model/facing/context screenshots, owner judgement, resource-pack names/hashes,
full client logs, reload observations and phase 4 paired formation views. Sources give no substitute automated
visual verdict; the 1.12-screenshot comparison remains NOT_COVERED under C32; use W241's audited inventory instead.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I08 — Language, GUI scale and resource reload

**Phase/level/machine/accounts:** 2 V1, A real GPU. **Gate:** Ordinary P2 many-machine/presentation world.
**Preconditions:** Complete candidate screen inventory, English/Chinese profiles and reproducible long labels/refusal states.

1. [H] Open every new v1.8 screen in English at scales 2, 3 and 4. → Labels, values, tooltips and essential buttons remain usable.
2. [H] Repeat in Chinese at each scale. → No raw translation key, clipped reason code or language-dependent unreachable control.
3. [H] Resize the window and repeat fullscreen. → Layout remains within the viewport; include long recipe/status names.
4. [H] Press F3+T with each representative screen open. → Textures/fonts restore; current server state is not reset or misreported.
5. [P] Review both-language contact sheets. → Actual readability is judged, not just pixel bounds. J14 separately covers HUD scales 1–6.

**Accepted preparation/action steps (W408):**

(C30) Open every delivered screen once in each language; K13's 32-row `screens.csv` includes overlays/non-menu screens and the eleven not named by another row. Use C2 effective viewports, never clamped scale 4 as a separate result. **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**: navigation/long-state controls; **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**: machine placement. W402 supplies screen/asset classifications.

**Evidence to keep:** Phase 2 screen × language × scale × mode index, screenshots, tooltips/reload timings and logs.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I09 — Rendering, reload heap/time and JEI responsiveness

**Phase/level/machine/accounts:** 2 V1, A on real GPU with P3 frame-time capture O8.
**Gate:** Ordinary P2 complex-model world and 64/512/2,048-block rockets.
**Preconditions:** Same hardware/resolution/settings for vanilla and empty Forge baseline; profiles with/without
JEI 15.56.0.205. Freeze sampling interval, frame collector and heap measurement method before running.

1. [H] Capture vanilla and Forge empty-scene frame times. → Average FPS, 1% low and first-stall baseline are retained.
2. [H] Capture the same metrics with the centrifuge OBJ and every formed v1.8 multiblock in view. → Compare to the
   baseline, not a guessed absolute FPS floor; include single/multiple rockets of 64/512/2,048 blocks.
3. [H] Record heap and timestamp immediately before F3+T. → A reproducible pre-reload sample exists.
4. [H] Press F3+T and record completion time and post-reload heap/cache. → Record elapsed time, first stall and
   resource/cache release; no missing model/texture or persistent OpenGL error storm.
5. [H] Repeat steps 3–4 for three reloads without JEI, then three with JEI. → Retain every value, not only the best run.
6. [P/H] With JEI present, open each classic category. → Record time to first drawn frame and actual page usability.
7. [P/H] Search and perform usage lookup. → Record response time and correct results with the full recipe set.

**Accepted preparation/action steps (W408):**

(C5, R535; W399/W403) Follow A's two same-cell Vanilla/Forge baseline runs, average/slowest-1%/first-stall/longest-frame definitions, reporting-only 50 ms and review-only spread trigger; no FPS/stall cutoff exists. Owned-client heap/histogram proxies are measured outside timed frames, never asserted as GPU/cache release. **K04 supplied input/limits: legal 64/512/2048 and multi-rocket inputs; entry I-K04 (R605)**: three rockets; **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry I-K15 (R548; R648 for A08/A09)**: direct heap/cache qualification and comparison plan; O8 approves capture/version.

**Evidence to keep:** Phase 2 `frametime/` raw data, baselines/settings/hardware, scene manifests, first-stall video,
three reloads per JEI state with heap/cache readings and per-category/search timing table. Sources set no numeric
FPS-ratio, reload-time or JEI-response cutoff; do not create one after seeing results.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I10 — Four-hour joint reference workload within the declared matrix

**Phase/level/machine/accounts:** 5 SOAK, REF node and two real clients or O7 verified equivalent.
**Gate:** Ordinary reference copy; admission switches remain off.
**Preconditions:** P2 docs/17 reference world, node exclusive, Java 17/`Xmx4G` max. Declare Forge/JEI cell and map
I04's complete four-cell rerun separately; D9 does not require four independent four-hour soaks.

1. [H] Inventory the load: two connected players; 16 oxygen devices/sealed bodies; ten stations; 100 missions;
   two active rockets. → Counts and actual activity are proven, not empty-server labels.
2. [H] Start continuous host/tick/RSS/GC/ticket sampling and the I05 production/H05 device workloads. → One joint
   run includes v1.8 content without double-counting elapsed time as separate runs.
3. [H] Assemble and disassemble a 2,048-block rocket during the run. → Capture bounded work, contents and timing.
4. [H] Perform a dimension transfer. → Record server authority, resources and cross-dimension recovery context.
5. [H] Perform periodic normal saves/restarts with field comparisons. → Writers drain normally and state remains conserved.
6. [H] Continue for at least four hours and compare all budgets. → Mean ≤25 ms; P95 ≤50 ms; P99 ≤100 ms;
   non-start/save spikes not sustained >500 ms; RSS not sustained >5.5 GiB; watchdog/OOM/unexpected forced chunks 0;
   no monotonic memory growth. Interrupted runs remain failed/incomplete and are not added together.
7. [H] Write the declared-cell and four-cell-coverage report. → Neither W188's short historical matrix nor a future
   v1.9 modpack run substitutes for this C6 soak.

**Accepted preparation/action steps (W408):**

**K17 supplied input/limits: combined reference world and four-hour schedule; entry I-K17 (R648)**: complete combined load, four sampled hours, 30-minute flushed saves and two-hour restart schedule (C4/R535 procedure parameters). **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry I-K15 (R548; R648 for A08/A09)**: complete raw-tick/all-ticket observation; O7/O11 supply real connections and REF acceptance. Keep each Forge/JEI result distinct.

**Evidence to keep:** Phase 5 raw full-duration samples, workload/actions/connection manifest, configuration,
restart/resource comparisons and exact actual budget table; link I04's four-cell evidence by row ID.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I11 — New-machine packet policy and endgame permissions with two clients

**Phase/level/machine/accounts:** 4 V2, A owner, B non-owner and station-member variants, packaged DS.
**Gate:** Ordinary P2 machine/endgame fixture.
**Preconditions:** Every new menu/intent mapped to its holder and policy; include beacon, landing pad,
force-field projector and pump as well as endgame devices. Record permissions and initial resources/chunks.

1. [P/H] A operates each valid local machine control. → Ordinary intended operation works with the correct holder/window.
2. [P] B tries the same controls on A's devices as a non-owner. → Refusal and no unauthorized state/resource mutation.
3. [P] Repeat as station member but not device owner. → Membership alone grants no prohibited endgame action.
4. [H/P] Exercise stale-window, out-of-reach, unloaded-holder, spectator and repeated-intent variants using the
   candidate's pinned shared-policy fixture. → Sender, distance, permission, state/range/load and rate checks hold;
   client-provided positions never cause arbitrary chunk loading. Do not inject an unspecified packet format.
5. [H] Compare both clients' views and the server's resource/chunk/audit projections. → Authoritative refusal/status
   synchronization and unchanged protected resources. W62's menu-specific tests are not a V2 substitute.

**Accepted preparation/action steps (W408):**

(C14 corrected by R546; W402 section 6) Use delivered K13 holder/policy matrix, with bounded tool receipt and unchanged server-state readback for every negative case; logs only where code emits one. `suit_motion` affects only the sender's own worn jetpack and needs **no distance step**. Beacon, pad, projector and pump are block-use devices, not invented menus/packets. The projector's silent non-operator use is accepted Low KI-V180-UI-01, still check unchanged state. **K18 supplied input/limits: bounded client intent JAR and case/readback matrix; entry I-K18 (R601)**: safe per-channel probes and named A1 references for unreachable/late cases; the live fixture **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**.

**Evidence to keep:** Phase 4 menu/holder/variant table, paired screenshots/video, both client logs, server policy
receipts, unchanged-resource/chunk comparisons and successful owner controls.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- **Answered:** C29 railgun-pair continuous witness; C30 high-risk/every-screen/family sample/144-controller scale; C31 page/usage query method; C32 audited W241 inventory with unavailable 1.12 screenshot comparison NOT_COVERED. W402 delivers classifications/screens/policies/resource packs; R546 covers unnamed screens, silent projector Low and no jetpack distance step.
- **I01:** complete witness/recipe/count/control manifest **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**; O4 chooses the hand-played subset.
- **I02/I03/I06/I08:** live machine scene **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**, all-old-ID historical source **needs package K02** — in progress (W447); entry I-K02, navigation/recheck batches **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**. Inventories/classifications are delivered, not new open choices.
- **I04:** runtime per-cell JEI counts **K13 supplied input/limits: asset/screen/sound/access inventories and resource packs; entry I-K13 (R546)**, recipe scene **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**. W400 names/manifest paths are answered.
- **I07:** actual missing-pack load and paired break/repair/tracking scene **K14 supplied input/limits: partial-r3 paired/research/tall/sleep scenes and 39 scripted steps; entry I-K14 (R642)**. No captured 1.12 session is invented.
- **I09:** three rocket scenes **K04 supplied input/limits: legal 64/512/2048 and multi-rocket inputs; entry I-K04 (R605)**, direct heap/cache observation **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry I-K15 (R548; R648 for A08/A09)**; O8 approves capture/version. No FPS/reload/response cutoff exists.
- **I05/I10:** combined workload **K17 supplied input/limits: combined reference world and four-hour schedule; entry I-K17 (R648)**, production recipe layout **K05 supplied input/limits: nine r608 machine scenes and field inventory; entry I-K05 (R612; R608 preparation choices)**, complete observers **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry I-K15 (R548; R648 for A08/A09)**. O7/O11 supply connections/node access.
- **I11:** live fixture **K16 supplied input/limits: v2 core scene, mapped controls and conditional recheck batches; entry I-K16 (R595; R628 supersedes final/ with v2/)**, bounded receipt/readback/probes and A1 references **K18 supplied input/limits: bounded client intent JAR and case/readback matrix; entry I-K18 (R601)**. Follow the actual holder matrix, not blanket owner-only policy.
