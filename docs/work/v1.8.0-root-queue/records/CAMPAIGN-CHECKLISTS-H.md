# Campaign checklists H: endgame systems

Unrun P4 instructions for H01–H07 of [the plan](FINAL-CAMPAIGN-PLAN.md), from
[v1.7 sections 10–13](../../../versions/V1.7.0-ENDGAME-SYSTEMS.md),
[G4/G7/G8](../../../releases/v1.7.0/GATE-STATUS.md),
[the operator guide](../../../ENDGAME-OPERATOR-GUIDE.md) and
[the reference method](../../../releases/v1.7.0/PERFORMANCE.md).

Accepted common procedures for **every H row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared preconditions and evidence

H means harness/operator and P means human; an arrow states the expected observation for that action.
Use P2's representative v1.3–v1.7 world, specifically its endgame copy. The scene requires preparation to freeze device/target coordinates,
endpoint/station IDs, account UUIDs, inventories and fixture archives/hashes first; these are absent from the version
checklist. Include the laser drill, railgun, black-hole generator, gravity field and elevator, not only one device.
Use P1's C6 JAR/hash and Java 17 on Forge 47.4.10 without JEI by default; record actual JEI state, and use only
JEI 15.56.0.205 if installed. Repeat the 47.4.23 compatibility smoke. Pin difficulty, view-distance and
simulation-distance; sources do not assign mandatory values (the historical performance run used distances 2). Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.

Ordinary rows need no marked bay campaign gate or extra JVM property. Keep admission switches off and endgame
settings as recorded; physical laser mining defaults off, so its destructive case requires disposable-fixture
configuration, not a silent production change. After a config edit, restart and check `/arce endgame status`;
`/reload` does not reload the common config. Phase 4 V2 has two real authenticated accounts A/B on a packaged DS,
`online-mode=true`, `white-list=true`, `enforce-whitelist=true`, `enable-rcon=false` and restricted access. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.

Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`. Keep V1 under
`E/phase2-v1/<machine>/<round>/`, DS/S2 under `E/phase3-ds/<row>/`, V2 under `E/phase4-v2/<sitting>/` and REF/SOAK
under `E/phase5-ref/<run>/`. Capture native server/client logs, candidate/JAR identity, fixture/config hashes,
hardware/JVM, UTC step times and before/after resource, ticket and endpoint projections. Screenshots are
`<row>-<step>-<client>-<language>-<scale>.png`; videos are `<row>-<client>.mkv`. Index artifacts and SHA-256 in
`E/results/RESULTS.md`, `results.json` and `FINDINGS.md`, then seal `E/SHA256SUMS.txt`. No blank result means PASS.

## Package preparation index (W456, R649)

This index resolves the package notes below; it is preparation, not execution or a Gate verdict.
Each entry gives the sealed input, its checksum-list SHA-256, the recipe/step mapping and the
remaining partial or NOT_COVERED work. Paths are external read-only inputs, never repository links.
Only K02 (W447) and K12 (W454) remain package deliveries in progress. A delivered recipe is not
an observed client result; preserve every original action, oracle, result field and budget.

### H-K10: endgame sources, physical-layer cut and throughput shortfall

Source: `W444.md; QUEUE.md row W444`. Root acceptance/disposition: **R635** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K10/idle/SHA256SUMS.txt` | `f58aac12bfb70b206f09ba660681c39a6b2246b7cfd0b6a661fee0e1f3de9a2c` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K10/active/SHA256SUMS.txt` | `a685aaffae4931a8af3b4a57fc97f751cfcd203c1aee4d85611e21b16bd0450f` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K10/large-range/SHA256SUMS.txt` | `a150410f9f1ee58b6e7855451db513dd880b6f76594b984af06d4b958ce56280` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K10/blocks/batch-cut/SHA256SUMS.txt` | `f1c2ba6e90490cd3dc42712ac4964be8b567b4ddf2b2caa6e8d7bf5eeb7784f2` |

**Recipe and original-step mapping.** manifest.json client_steps.H01/H02/H06/menus, actors, cut_mapping and throughput map H01/H02/H06/H07; large-range plus blocks/batch-cut map H03. Use readable-index supplements for device UUIDs and k10_endgame_load.py observations/cleanup for H05/H07. Physical cut is LaserPhysicalDrill.remove:218; the cut's recover-1/recover-2 keep the same 9,216-cell oracle.

**Remaining coverage and reasons.** The physical cut proves only an unsaved first-layer rollback, not every payment/marker ordering; 16 historical transit/ride cuts remain mapped, not rerun. Arbitrary oversized range has no C6 input, so NOT_COVERED. H04 remains NOT_COVERED under D9/C27. All actors are synthetic/UUID/FakePlayer, not real clients. Railgun load is NOT_MET (~5.9 escrows/sec versus unchanged 16/20 ticks); R635 keeps that shortfall open for combined/REF work. K17 is not an endgame throughput qualification. Internal caches/all Forge tickets, full REF/soak and V1/V2 remain unqualified.

**Mapping in checklist H.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| H01 | idle/active manifest.json client_steps.H01 and readable-index -> original shared-target contention actions, bound owner/device IDs and positive controls; real accounts still needed. |
| H02 | idle/active manifest.json client_steps.H02/menus -> original five-device non-owner/non-OP actions; station VISIT does not imply device control. |
| H03 | blocks/batch-cut manifest.json cut_mapping plus recover-1/-2 -> original external-stop/two-restart action at LaserPhysicalDrill.remove:218; large-range manifest client_steps.H03_large_range -> separate boundary input. |
| H04 | No real protection-mod scene: NOT_COVERED under D9/C27. Event/adapter simulations cannot be counted as a real-mod pass. |
| H05 | active/large-range manifest observations/cleanup and load-stopped.jfr -> original long-run/cleanup/readback actions, not a completed four-hour REF. |
| H06 | idle/active client_steps.H06/menus -> original screens and generating Cygnus disc viewpoints; real V1/V2 not supplied. |
| H07 | active/large-range throughput/load_counts/actors + k10_endgame_load.py -> original ADR-054 measurement steps; NOT_MET remains open. core-railgun is a 32-iron functional witness, not throughput acceptance. |

### H-K15: bounded observers, with explicit remaining visibility gaps

Source: `W403.md; QUEUE.md row W403; W452.md/R648`. Root acceptance/disposition: **R548; R648 for A08/A09** in `../QUEUE.md`.

**Recipe and original-step mapping.** Tracked scripts/campaign_observe/{ticks,process,jdk,host,evidence}.py and scripts/run_campaign_observer.py at implementation 385c079dc10718f781a425527cd3e9a0765a0518 supply matched-copy window/process/heap/JFR/evidence operations. Use sample_ticks/force-load queries and owned-process-only diagnostics at the row's existing sampling step; K17 schedule and K08/K09/K10 load drivers supply concrete harness integrations.

**Remaining coverage and reasons.** W403 created no sealed live fixture or SHA256SUMS.txt: none is invented. Window aggregates cannot prove whole-run percentiles/spike lengths. C6/K17's release-test raw sampler is used only in the separately labelled R648 REF run for A09. All-Forge-ticket, queue-internal, client/GPU allocation and direct cache-release proof remain NOT_COVERED where unavailable; histogram/heap/RSS proxies are labelled, not direct evidence. Actual REF acceptance/full windows and overhead checks remain unexecuted.

**Mapping in checklist H.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| H05 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |
| H07 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |

### H-K16: v2 core scene, mapped controls and conditional recheck batches

Source: `W427.md; W443.md; QUEUE.md rows W427/W443`. Root acceptance/disposition: **R595; R628 supersedes final/ with v2/** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/v2/SHA256SUMS.txt` | `27df12a711d23f500dd7370fab35a6c5d457f6eebd93e8366ee191889b140fa9` |

**Recipe and original-step mapping.** v2/batch-index.json maps each row/step/file; manifest.json core_route/devices/supplies and candidate-recipes.json give counts/positions/recipes. A02/I01 use core-00/core-01, core-room/core-suit, core-rocket-assemble/-fuel/-invalid, core-flight-plan/-observe; A06/A13/A17 use core-station/core-screen-*; E07 uses sp-pause; J20/J21 use rechecks.json and batches/j21-*.json. controls.json gives effective viewport targets, not old pointer replay.

**Remaining coverage and reasons.** R628 accepts only the en_us 1280x720 scale-3 dedicated rehearsal; scale2/fullscreen/zh_cn, integrated pause and 63 J21 requests are generated, not rehearsed. Fullscreen screen gate needs checking. B-5/3-2/8-2 remain checkpoint-only; no complete continuous quantitative crafting witness or all-screen/first-join/dual-seat/station ownership-transfer procedure is supplied. Return flight/paid warp/satellite flow, O5 accounts, N15-N19 physical setup and V1/V2 remain partial. Never replay old NBT-writing setup; R638 applies. Desktop-wide Alt/Shift are the recorded night-round operating condition.

**Mapping in checklist H.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| H06 | rechecks.json j21-a-f4/-a-f5 requests -> original two-sided disc/long-reason views with K10; generated, not rehearsed. |

### H-K17: combined reference world and four-hour schedule

Source: `W452.md; QUEUE.md row W452`. Root acceptance/disposition: **R648** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/source/SHA256SUMS.txt` | `d03941ecdb4bff0d1742cf452959293b951e2e26b2972d8983e6f5c0ccd3daf1` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K17/readback-settled/SHA256SUMS.txt` | `a4aba18f3649e82a8e00e5d20f0efc508ad3c35c8b21357938a9811c7efc3df4` |

**Recipe and original-step mapping.** source/layout.json maps the 16 Moon rooms, ten station IDs, mission records and 2048/64/64 pads. source/schedule.json actions and client_steps.{connect,assembly_2048,flights,missions,a11} map A08-A11/I05/I10; use k17_reference.py schedule/run on disposable copies. Two 7200-s sampled segments, 120-s warm-up, flushed saves each 1800 s, one restart at sampled 7200 s, autosave retained.

**Remaining coverage and reasons.** R648: A08 has no test properties; keep forge tps means/RSS/GC/forced chunks. A09 is a separate labelled REF run on the same world/schedule adding only -Dadvancedrocketrycommunity.releaseTestHooks=true for the read-only raw sampler; compare forge tps means under C10's baseline-spread review rule. The 16 forced room chunks are declared workload, not unexpected tickets. Without clients flights/disassembly/dimension transfer remain scripted; ACTIVE missions fall from 101 to zero within ~6000 ticks. Record actual counts and ordinary refills, not a sustained-load claim. K17 does not supply sustained 500/1000 mission or H07 endgame throughput. Full four hours/REF/two real connections/all-ticket/direct-cache evidence remains unexecuted.

**Mapping in checklist H.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| H05 | Source/schedule provide common rooms/stations/missions/rocket load only; original endgame coexistence/throughput/soak still needs K10 campaign assembly and REF. |

## H01 — Multiple players contend for one target

**Phase/level/machine/accounts:** 4 V2, real clients A/B on GPU hardware and packaged DS.
**Gate:** Ordinary endgame copy. **Preconditions:** Two authorized device owners, one common valid target and
known initial energy/cargo; include contention appropriate to each system, with the target rule in the fixture manifest.

1. [H] Capture `/arce endgame status` and `/arce endgame endpoint list` as operator. → Device IDs and ownership match the fixture.
2. [P/H] A selects the common target through its device's ordinary screen. → Server presents a valid quote/confirmation.
3. [P/H] B selects that target through its own device. → B's session does not overwrite A's target or consent.
4. [P/H] Submit both confirmed actions with recorded server ordering. → Accepted work is bounded and resources
   charged once; a contention refusal is readable, not duplicate processing or a second payout.
5. [H] Run `/arce endgame audit all` and inspect both endpoints. → Audit, inventories, energy and visible outcomes
   agree. Repeat for each fixture-defined contention variant; sources give no universal winner or exact refusal code.

**Accepted preparation/action steps (W408):**

Preparation is required for the target/device coordinates, contention scenes, owners, exact menus and native invocation; actor inputs are not two-real-client evidence. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.

**Evidence to keep:** Both videos/logs, target/consent data, tick/order transcript, audit and exact before/after
resource projections in phase 4.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H02 — Non-owner and non-operator authority

**Phase/level/machine/accounts:** 4 V2, A owner and B non-owner/non-OP, packaged DS.
**Gate:** Ordinary endgame copy. **Preconditions:** Five device types with recorded owners and private state;
prepare station-member-but-not-device-owner variant without granting B operator rights.

1. [P] B opens each accessible device screen. → Visibility does not grant device ownership or mutation authority.
2. [P] B attempts target, start, shipment, bind and configuration controls applicable to that device. → Unauthorized
   actions are refused and leave energy, cargo, target and station permissions unchanged.
3. [P] B attempts `/arce endgame device owner <pos> <player>`. → Permission-level-2 operator command is unavailable to B.
4. [P] Repeat B's actions as a station member without device ownership. → Membership does not bypass device policy.
5. [P] A performs the authorized counterpart. → The valid operation works; refusal is not merely a disabled fixture.
6. [H] Compare `/arce endgame audit all`, root, inventories and loaded chunks. → No unauthorized mutations,
   secret target-coordinate disclosure or arbitrary client-requested loading.

**Accepted preparation/action steps (W408):**

Preparation is required for the five-device permission scenes, owner/member policies and positive controls; use R546 tool receipts/unchanged state and logs only on logging branches. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.

**Evidence to keep:** Both clients' refusal and owner-success views, account/role manifest, audit and unchanged
resource/chunk projections in phase 4. Pair this with I11's v1.8 owner-bound devices, without dropping either row.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H03 — Forced stop inside a block-processing batch

**Phase/level/machine/accounts:** 3 S2, packaged DS on development PC; no client required.
**Gate:** Disposable native test fixture only. **Preconditions:** P2 endgame world and current C6 forced-stop runner
mapped to [the recovery matrix](../../../releases/v1.7.0/RECOVERY-MATRIX.md); physical-mining setting pinned if used.

1. [H] Snapshot target blocks, device input/output/energy and pending work before the batch. → Baseline is complete.
2. [H] Start the fixture's block-processing batch. → The named pre/post-processing boundary appears in the runner log.
3. [H] Kill only the runner-owned server process tree at that boundary. → Record the cut and actual stopped files;
   do not repair the world or use a graceful stop as a forced-stop substitute.
4. [H] Restart the same copy. → Recovery preserves one authoritative effect/payment; uncertain work stops with diagnostics.
5. [H] Restart a second time and compare the same fields. → No repeated destruction, duplicated drops, lost escrow
   or unbounded recovery. Include the v1.7 large-range request check: oversized requests must be bounded or refused.

**Accepted preparation/action steps (W408):**

Preparation is required for the matched C6 batch-cut seam, owned-process external-stop invocation and large-range variant. No old boundary is guessed. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.

**Evidence to keep:** Runner command/version, cut receipt, stopped-file projections, both restart logs and block,
inventory/energy/ledger comparisons in phase 3. Exact batch/cut command is a Root prerequisite, not provided by §10.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H04 — Protection integration

**Phase/level/machine/accounts:** 3 DS, development PC and A; real-mod variant only if separately authorized.
**Gate:** Ordinary copy. **Preconditions:** Root's O6 adapter/event-simulation route, with an allowed target and a
denied target. Real third-party mods require the selected names, versions, licenses and setup; none is silently added.

1. [H] Record the protection fixture/provider and its allow/deny regions. → The manifest states whether this is an
   adapter simulation or a real-mod matrix; one is not labelled as the other.
2. [P/H] Run a valid operation on an allowed target. → Work and costs follow the normal server policy.
3. [P/H] Submit the same operation to the denied target. → Protection refusal precedes destructive work; no denied
   block change, shipment, ride or duplicate/refunded-and-applied effect.
4. [H] Run `/arce endgame zone list` and the relevant audit page. → The refusal is attributable and does not expose
   private coordinates to an unauthorized client.
5. [H] Save and restart the copy twice, then repeat allow/deny. → Protection semantics and saved device state survive.

**Accepted preparation/action steps (W408):**

(C27) Record the inherited real-protection-mod item NOT_COVERED in v1.8, reason D9/O6 support scope; it moves to v1.9. Run adapter/event simulation only as supplementary evidence, never a real-mod pass. Its allow/deny scene still requires preparation; no new third-party mod is selected. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.

**Evidence to keep:** Adapter or real-mod identity/configuration/hashes, region manifest, event/audit transcript,
allowed and denied state comparisons and restart logs in phase 3.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H05 — Long device run without tickets, caches or watchdog

**Phase/level/machine/accounts:** 5 SOAK, non-root Debian 12 reference node, 4 vCPU/8 GiB, Java 17, `Xmx4G` max;
two real clients from phase 4 through SSH tunnels to the loopback-bound server, or owner-approved verified equivalent O7.
**Gate:** Reference copy, admission switches off.
**Preconditions:** P2 docs/17 reference workload plus the endgame devices of H07. Keep the node exclusive.

1. [H] Record initial device, cache, endpoint and ticket counts with `/arce endgame status` and host samples.
   → Reference state and active workload are fixed, not an empty server.
2. [H] Run the devices within the joint workload for at least four continuous hours. → No watchdog/OOM; work
   progresses within bounded queues and each system's budget.
3. [H] Sample tick times, RSS, GC, cache and ticket counts for the entire run. → No monotonic memory growth or
   unexpected forced chunk; elevator arrival tickets are bounded and temporary.
4. [H] Complete/cancel active work and unload its chunks. → Tickets/cache entries release; cargo remains conserved.
5. [H] Perform the workload's periodic normal saves/restarts and compare state. → No duplicate/lost resources;
   report restart intervals rather than stitching together interrupted partial soaks.

**Accepted preparation/action steps (W408):**

Preparation is required for the endgame workload/cleanup controls; preparation is required for the combined reference world and save/restart schedule; preparation is required for the qualified long-run observers. Owner O7 supplies real connections/equivalent decision; budgets and full duration stay unchanged. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open. Entry H-K17 (R648) supplies the following inputs: combined reference world and four-hour schedule. The full four-hour REF/two-real-client run remains unexecuted; decaying missions do not establish sustained load. Entry H-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.

**Evidence to keep:** Continuous workload/timing/memory/GC/ticket samples, start/end resource projections,
normal-stop/restart logs and host/JVM/config identity in phase 5. An interrupted soak needs a new full run.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H06 — Endgame screens and effects

**Phase/level/machine/accounts:** 2 V1, A on main and second real GPU categories.
**Gate:** Ordinary fixture. **Preconditions:** P2 scenes for laser, railgun, black-hole generator, gravity and
elevator, including successful and refused states and several simultaneous visible devices.

1. [P/H] Open each device's ordinary screen and select a target. → Target, danger confirmation and costs are readable.
2. [P/H] Confirm a valid operation. → Progress, effects and completion agree with server state; no obscured key feedback.
3. [H] Exercise each scene's refused operation. → Failure remains readable alongside active effects, not only in logs.
4. [P] Inspect laser, black-hole, gravity, elevator and railgun effects together. → No missing resources or excessive
   occlusion; record frame times and first stalls, not software-renderer performance.
5. [H] Repeat English/Chinese and GUI scales 2/3/4, windowed/fullscreen. → Essential controls stay in bounds;
   text, not color alone, communicates danger/status. Sources require a basic color-blind/readability check,
   but give no numeric contrast threshold.
6. [H] Press F3+T and reconnect. → Correct resources/state return without persistent OpenGL error storms.
7. [P] Judge the same scenes on the second GPU category. → Separate environment and judgement are retained.

**Accepted preparation/action steps (W408):**

(J21 A-F4/A-F5) At a generating Cygnus X-1 black-hole generator view the ring from two blocks below and five blocks above: visible from both sides. Also open railgun, black-hole generator, laser drill and laser target at their longest reason lines, scale 3, en_us/zh_cn: lines fit, full hover available. Preparation is required for the generating scene/coordinates; preparation is required for the regenerated recheck batches. C2's effective viewport rule applies. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open. Entry H-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.

**Evidence to keep:** Per-device screenshots, effect/screen video, scale/language/contact-sheet index, raw frame
samples and both machines' client/server logs in phase 2. The owner supplies the visual verdict.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H07 — Per-system reference budgets and durable flushes

**Phase/level/machine/accounts:** 5 REF, H05's reference node/accounts. **Gate:** Reference fixture only; any
`refload build` test hook is isolated and its full invocation/property must be pinned by Root.
**Preconditions:** [ADR-054 §7](../../../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md) load:
16 logical and 4 physical drills; 32 railguns, 16 requesting launch every 20 ticks; 16 burning black-hole generators;
64 fields with 20 verified player actors; 8 elevator pairs with 4 rides/minute; 1,024 endpoints, 1,024 tombstones
and 192 four-stack transit records. This supplements, not replaces, docs/17's base workload.

1. [H] Build and archive the exact load and actor method. → Counts/rates are observable, including physical mining;
   report actual escrow throughput, not only requested launches (the old run achieved about 2/s against 16/s).
2. [H] Measure idle after the method's 11-minute warm-up and retain the first cold 1,200-tick window too.
   → All endgame idle work mean ≤0.1 ms/tick.
3. [H] Drive the full load and read `/arce endgame timing` for three consecutive 1,200-tick windows 70 seconds apart.
   → Loaded mean ≤2.0 ms/tick and non-flush-tick P99 ≤8 ms.
4. [H] Compare per-system means. → Drills ≤0.8 ms; ledger ≤0.5 ms; black-hole generators ≤0.2 ms;
   field lookups ≤0.3 ms; rides ≤0.2 ms.
5. [H] Retain every ramp and steady-state flush duration. → Each coalesced/barrier flush ≤60 ms at the reference
   root, ≤250 ms at accounted maximum and ≤400 ms at the 4 MiB recovery bound; do not hide flushes outside P99.
6. [H] Capture ticket/cache counts during rides and after settlement/cancel/stop. → No persistent tickets;
   pending ride-arrival tickets ≤64, released on terminal paths.
7. [H] Compare whole-server mean/P95/P99/RSS to docs/17 and retain JFR/GC/network data. → Same global budgets as
   H05; a >60 ms reference flush is a failed obligation requiring Root's ADR follow-up, not a host-lag waiver.

**Accepted preparation/action steps (W408):**

(C28; ADR-054) Classify two real connections separately from builder fixture players (not real players). Report achieved railgun throughput against the unchanged required launch load; a shortfall is not met, never a reason to lower it. Windows history is not REF evidence. Preparation is required for the actors, throughput method and native driver; preparation is required for the REF sampling. O7 supplies real connections. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open. Entry H-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.

**Evidence to keep:** Phase 5 load/throughput manifest, idle/cold/loaded timing output, raw per-flush samples
including ramp, JFR/host samples, ticket audit and exact budget table with actual values.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- **Answered:** C27 real-mod H04 NOT_COVERED in v1.8, D9/O6, adapter supplementary and real-mod scope v1.9; C28 actor classification and unchanged achieved-versus-required load. H06 explicitly rechecks two-sided disc and long endgame reasons.
- **H01-H07:** The device/target/owner/resource scene, menu/native/refload controls, actors and throughput method remain required. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.
- **H03:** The C6 cut seam/large-range variant still requires qualification. Entry H-K10 (R635) supplies the following inputs: endgame sources, physical-layer cut and throughput shortfall. Railgun throughput remains NOT_MET; the listed physical-cut limits, real-client and REF gaps remain open.
- **H05/H07:** Raw-tick/JFR/all-ticket/cache integration and a combined world remain required. O7/O11 supply connections/node access; Windows history is not REF evidence. Entry H-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open. Entry H-K17 (R648) supplies the following inputs: combined reference world and four-hour schedule. The full four-hour REF/two-real-client run remains unexecuted; decaying missions do not establish sustained load.
- **H06:** Regenerated two-sided-disc/long-text batches plus K10's scene remain required; O9 supplies second GPU.
Entry H-K16 (R595; R628 supersedes final/ with v2/) supplies the following inputs: v2 core scene, mapped controls and conditional recheck batches. Only the recorded en_us scale-3 rehearsal is qualified; the full witness, other display cells and listed live controls remain open.
