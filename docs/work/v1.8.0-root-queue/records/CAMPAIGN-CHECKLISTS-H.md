# Campaign checklists H: endgame systems

Unrun P4 instructions for H01–H07 of [the plan](FINAL-CAMPAIGN-PLAN.md), from
[v1.7 sections 10–13](../../../versions/V1.7.0-ENDGAME-SYSTEMS.md),
[G4/G7/G8](../../../releases/v1.7.0/GATE-STATUS.md),
[the operator guide](../../../ENDGAME-OPERATOR-GUIDE.md) and
[the reference method](../../../releases/v1.7.0/PERFORMANCE.md).

Accepted common procedures for **every H row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C4 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared preconditions and evidence

H means harness/operator and P means human; an arrow states the expected observation for that action.
Use P2's representative v1.3–v1.7 world, specifically its endgame copy. The scene **needs package K10** to freeze device/target coordinates,
endpoint/station IDs, account UUIDs, inventories and fixture archives/hashes first; these are absent from the version
checklist. Include the laser drill, railgun, black-hole generator, gravity field and elevator, not only one device.
Use P1's C4 JAR/hash and Java 17 on Forge 47.4.10 without JEI by default; record actual JEI state, and use only
JEI 15.56.0.205 if installed. Repeat the 47.4.23 compatibility smoke. Pin difficulty, view-distance and
simulation-distance; sources do not assign mandatory values (the historical performance run used distances 2).

Ordinary rows need no marked bay campaign gate or extra JVM property. Keep admission switches off and endgame
settings as recorded; physical laser mining defaults off, so its destructive case **needs package K10** disposable-fixture
configuration, not a silent production change. After a config edit, restart and check `/arce endgame status`;
`/reload` does not reload the common config. Phase 4 V2 has two real authenticated accounts A/B on a packaged DS,
`online-mode=true`, `white-list=true`, `enforce-whitelist=true`, `enable-rcon=false` and restricted access.

Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/`. Keep V1 under
`E/phase2-v1/<machine>/<round>/`, DS/S2 under `E/phase3-ds/<row>/`, V2 under `E/phase4-v2/<sitting>/` and REF/SOAK
under `E/phase5-ref/<run>/`. Capture native server/client logs, candidate/JAR identity, fixture/config hashes,
hardware/JVM, UTC step times and before/after resource, ticket and endpoint projections. Screenshots are
`<row>-<step>-<client>-<language>-<scale>.png`; videos are `<row>-<client>.mkv`. Index artifacts and SHA-256 in
`E/results/RESULTS.md`, `results.json` and `FINDINGS.md`, then seal `E/SHA256SUMS.txt`. No blank result means PASS.

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

**Needs package K10**: target/device coordinates, contention scenes, owners, exact menus and native invocation; actor inputs are not two-real-client evidence.

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

**Needs package K10**: five-device permission scenes, owner/member policies and positive controls; use R546 tool receipts/unchanged state and logs only on logging branches.

**Evidence to keep:** Both clients' refusal and owner-success views, account/role manifest, audit and unchanged
resource/chunk projections in phase 4. Pair this with I11's v1.8 owner-bound devices, without dropping either row.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## H03 — Forced stop inside a block-processing batch

**Phase/level/machine/accounts:** 3 S2, packaged DS on development PC; no client required.
**Gate:** Disposable native test fixture only. **Preconditions:** P2 endgame world and current C4 forced-stop runner
mapped to [the recovery matrix](../../../releases/v1.7.0/RECOVERY-MATRIX.md); physical-mining setting pinned if used.

1. [H] Snapshot target blocks, device input/output/energy and pending work before the batch. → Baseline is complete.
2. [H] Start the fixture's block-processing batch. → The named pre/post-processing boundary appears in the runner log.
3. [H] Kill only the runner-owned server process tree at that boundary. → Record the cut and actual stopped files;
   do not repair the world or use a graceful stop as a forced-stop substitute.
4. [H] Restart the same copy. → Recovery preserves one authoritative effect/payment; uncertain work stops with diagnostics.
5. [H] Restart a second time and compare the same fields. → No repeated destruction, duplicated drops, lost escrow
   or unbounded recovery. Include the v1.7 large-range request check: oversized requests must be bounded or refused.

**Accepted preparation/action steps (W408):**

**Needs package K10**: matched C4 batch-cut seam, owned-process external-stop invocation and large-range variant. No old boundary is guessed.

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

(C27) Record the inherited real-protection-mod item NOT_COVERED in v1.8, reason D9/O6 support scope; it moves to v1.9. Run adapter/event simulation only as supplementary evidence, never a real-mod pass. Its allow/deny scene **needs package K10**; no new third-party mod is selected.

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

**Needs package K10**: endgame workload/cleanup controls; **needs package K17**: combined reference world and save/restart schedule; **needs package K15**: qualified long-run observers. Owner O7 supplies real connections/equivalent decision; budgets and full duration stay unchanged.

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

(J21 A-F4/A-F5) At a generating Cygnus X-1 black-hole generator view the ring from two blocks below and five blocks above: visible from both sides. Also open railgun, black-hole generator, laser drill and laser target at their longest reason lines, scale 3, en_us/zh_cn: lines fit, full hover available. **Needs package K10**: generating scene/coordinates; **needs package K16**: regenerated recheck batches. C2's effective viewport rule applies.

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

(C28; ADR-054) Classify two real connections separately from builder fixture players (not real players). Report achieved railgun throughput against the unchanged required launch load; a shortfall is not met, never a reason to lower it. Windows history is not REF evidence. **Needs package K10**: actors, throughput method and native driver; **needs package K15**: REF sampling. O7 supplies real connections.

**Evidence to keep:** Phase 5 load/throughput manifest, idle/cold/loaded timing output, raw per-flush samples
including ramp, JFR/host samples, ticket audit and exact budget table with actual values.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- **Answered:** C27 real-mod H04 NOT_COVERED in v1.8, D9/O6, adapter supplementary and real-mod scope v1.9; C28 actor classification and unchanged achieved-versus-required load. H06 explicitly rechecks two-sided disc and long endgame reasons.
- **H01-H07:** device/target/owner/resource scene, menu/native/refload controls, actors and throughput method **needs package K10**.
- **H03:** C4 cut seam/large-range variant **needs package K10**.
- **H05/H07:** raw-tick/JFR/all-ticket/cache integration **needs package K15**, combined world **needs package K17**. O7/O11 supply connections/node access; Windows history is not REF evidence.
- **H06:** regenerated two-sided-disc/long-text batches **needs package K16** plus K10's scene; O9 supplies second GPU.
