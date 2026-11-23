# Campaign checklists G: satellites and resource missions

These unrun P4 lists preserve G01–G07 of [the campaign](FINAL-CAMPAIGN-PLAN.md). Read
[v1.6 sections 10–13](../../../versions/V1.6.0-SATELLITE-RESOURCE-MISSIONS.md),
[G7/G8](../../../releases/v1.6.0/GATE-STATUS.md) and
[ADR-050 sections 1–2](../../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md).
[ADR-051](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) resolves the version
checklist's physical-instance wording: these are logical registry records without regions, dimensions or coordinates.
The fields below are deliberately blank: these are instructions, not acceptance evidence.

Accepted common procedures for **every G row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared preconditions and artifact rules

H marks harness/operator actions; P marks human actions/judgements. Each arrow supplies the expected observation.
Use the P2 representative v1.3–v1.7 world, specifically the mission fixture, on disposable copies. The manifest requires preparation to pin its
archive/hash, terminal coordinates, satellite/mission/instance IDs, launch packages, owner UUIDs, target bodies,
reward snapshots and the ordinary start/claim/cancel controls before running. The cited version sections do not
give these values; do not invent them. Include data, survey, asteroid and gas missions and at least three satellite
types, not just one timed mission. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.

Use C6 from P1 with Java 17, Forge 47.4.10 and no JEI by default; record the actual versions and use the 47.4.23
compatibility profile for its smoke. If JEI is present it is 15.56.0.205. Phase 3 DS uses the packaged server on the
development PC; phase 2 V1 uses a real GPU and includes the second GPU category required by G8. Phase 4 V2 uses
two real authenticated accounts, `online-mode=true`, `white-list=true`, `enforce-whitelist=true`,
`enable-rcon=false`, restricted access and exactly the two clients. No marked bay gate or extra JVM test property
is needed for ordinary mission play; all unrelated production switches remain unchanged. Pin difficulty, view and
simulation distances in the run manifest; the cited sources supply no fixed numbers.

Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`. DS/S2 goes under
`E/phase3-ds/<row>/`; V1 under `E/phase2-v1/<machine>/<round>/`; V2 under `E/phase4-v2/<sitting>/`; REF under
`E/phase5-ref/<run>/`. Retain native logs, exact controls/commands, UTC step times, configuration, state projections,
candidate/JAR SHA-256 and fixture hashes. Name screenshots `<row>-<step>-<client>-<language>-<scale>.png` and
videos `<row>-<client>.mkv`. Add artifact hashes/references to `E/results/RESULTS.md`, `results.json` and
`FINDINGS.md`, then seal `E/SHA256SUMS.txt`. A missing fixture/driver means NOT_COVERED, never PASS.

## Package preparation index (W456, R649)

This index resolves the package notes below; it is preparation, not execution or a Gate verdict.
Each entry gives the sealed input, its checksum-list SHA-256, the recipe/step mapping and the
remaining partial or NOT_COVERED work. Paths are external read-only inputs, never repository links.
Only K02 (W447) and K12 (W454) remain package deliveries in progress. A delivered recipe is not
an observed client result; preserve every original action, oracle, result field and budget.

### G-K09: 102/501/1001-active source sets and mapped cuts

Source: `W441.md; QUEUE.md row W441`. Root acceptance/disposition: **R625** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K09/missions-101/SHA256SUMS.txt` | `5b027e007f8b610f9b80f99366a0b8c1a5eb20a9337224738c50ecee00e613a2` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K09/missions-500/SHA256SUMS.txt` | `08a626789f83cf582855ec2342b1c88f59bda885e3e09ba0f2255a2bc7099bc6` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K09/missions-1000/SHA256SUMS.txt` | `0217b28fad685ca6927f45a7eb56b0930c35ec4f284a0bb52b8b5e0428d67e14` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K09/missions-101/cut-A/SHA256SUMS.txt` | `bb6268ea16a2403de2a46f33033ca4887543a92eed2861921e3bb0cf749b183d` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K09/missions-101/cut-B/SHA256SUMS.txt` | `29b00acab7e42b5f9c2c7746376a7b70d17f18eb5ea139393d34d4a998331905` |

**Recipe and original-step mapping.** manifest.json missions/effective_values/scheduler/client_steps.workflow and controls map G01/G02/G05-G07. cut_mapping and cut-A/cut-B with recover-A-1/-2 and recover-B-1/-2 map G04's separate before/after reward-store cuts. Use k09_mission_load.py for matched windows; preserve logical clocks and each deadline.

**Remaining coverage and reasons.** Active asteroid/gas missions NOT_COVERED: C6 setup completes the asteroid and cannot start active gas. 200-tick data missions finish inside warm-up; sustained 500/1000 load remains unprepared, not supplied by K17's smaller refill recipe. No arrival server tick exists: G03 V2 same-tick is NOT_COVERED; actual counterpart is A0 ResourceMissionsTest.twoOwnersSettleInTheSameTickIndependently, not A1. Cuts C/D/neither-store are mapped, not qualified; logical TTL/tickets do not prove physical instance travel, internal cache cleanup or every Forge ticket. Full soak/real 30-minute stop/REF/client claims remain open.

**Mapping in checklist G.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| G01 | missions-101/500/1000 manifests effective_common/effective_values/scheduler/missions and k09_mission_load.py -> original startup/load/save/restart/500/1000 steps. 102/501/1001 ACTIVE only at source, not throughout sample. |
| G02 | manifest.json clock/mission deadlines and load-restart receipts -> original offline/logical-tick/catch-up steps; downtime is not progress. |
| G03 | manifest.json same_tick -> original concurrent claim action; tick=null/NOT_COVERED, A0 counterpart named, no server-arrival witness. |
| G04 | manifest.json cut_mapping.A/B/C/D/neither plus cut-A/cut-B/recover-* -> each original boundary action; only A/B qualified, C/D/neither NOT_COVERED. |
| G05 | manifest.json instances/effective_values and A/B restart receipt reconciliation -> original logical instance/ticket/reclaim steps; synthetic advance1220 is not real TTL/physical travel. |
| G06 | manifest.json client_steps.workflow/controls -> original terminal/destination/reward presentation steps; no physical instance dimension exists. |
| G07 | manifest.json client_steps.preconditions/controls -> original two-owner launch/claim/cancel/refusal steps; terminals owner-private or OP, no invented membership observer. |

### G-K15: bounded observers, with explicit remaining visibility gaps

Source: `W403.md; QUEUE.md row W403; W452.md/R648`. Root acceptance/disposition: **R548; R648 for A08/A09** in `../QUEUE.md`.

**Recipe and original-step mapping.** Tracked scripts/campaign_observe/{ticks,process,jdk,host,evidence}.py and scripts/run_campaign_observer.py at implementation 385c079dc10718f781a425527cd3e9a0765a0518 supply matched-copy window/process/heap/JFR/evidence operations. Use sample_ticks/force-load queries and owned-process-only diagnostics at the row's existing sampling step; K17 schedule and K08/K09/K10 load drivers supply concrete harness integrations.

**Remaining coverage and reasons.** W403 created no sealed live fixture or SHA256SUMS.txt: none is invented. Window aggregates cannot prove whole-run percentiles/spike lengths. C6/K17's release-test raw sampler is used only in the separately labelled R648 REF run for A09. All-Forge-ticket, queue-internal, client/GPU allocation and direct cache-release proof remain NOT_COVERED where unavailable; histogram/heap/RSS proxies are labelled, not direct evidence. Actual REF acceptance/full windows and overhead checks remain unexecuted.

**Mapping in checklist G.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| G01 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |

### G-K18: bounded client intent JAR and case/readback matrix

Source: `W432.md; intent-test-mod/README.md; QUEUE.md row W432`. Root acceptance/disposition: **R601** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS.txt` | `aac26e987be0617ad63cec39b54aefb0fe7f3372085863ab14cd49731e392e57` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/SHA256SUMS-CORRECTED-1.txt` | `d29b209b8e7133f2d50f8463d3d9a1fa616572b2fe8b919ddbe58de254ec188f` |

**Recipe and original-step mapping.** Install arce-intent-test-1.0.0.jar only on the probing client. intent-test-mod/README.md Triggering one case steps 1-6 and Case table map C08/D09/I11 flight_stale/range/non_owner/late and classic_stale/range/non_owner/late; E03 flight_forbidden_surface/private_station; E07 late and motion_disabled. R/C/M/L readbacks are required separately. F8 captures; a single F9 queues at most one bounded packet.

**Remaining coverage and reasons.** Use SHA256SUMS-CORRECTED-1.txt: the original list's final-validator hash is stale and preserved, not silently repaired. There are three C2S channels, five S2C-only; no payload-terminal, research-menu, callback-fault, packet-burst, late-S2C/cache, all-ticket or same-tick claim control exists. Those branches remain NOT_COVERED with the README's named A0/A1 references/gaps. Client queue log is not server receipt. Actual real-client/server qualification and unchanged-state/loaded-chunk readbacks remain campaign work.

**Mapping in checklist G.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| G03 | No settlement packet or server arrival-tick probe; same-tick V2 NOT_COVERED and A0 counterpart, not a fabricated A1. |

## G01 — Active missions, restart, 500/1,000 load and flush cost

**Phase/level/machine/accounts:** 3 DS on development PC with A; 5 REF on 4 vCPU/8 GiB/Debian 12, non-root,
Java 17 and `Xmx4G` maximum, loopback server/SSH-tunnel access on an exclusive node.
**Gate:** Ordinary fixture; no campaign marker.
**Preconditions:** P2 mission world with more than 100 active missions; separate 500-, 1,000- and synthetic
worst-case-root fixtures. Capture scheduler clock, statuses, deadlines, registry epoch, rewards and terminal receipts.

1. [H] Start the >100-active-mission world on C6. → Registry restores without dropped missions or duplicate rewards.
2. [H] Allow a scheduler pass and capture all due/status changes. → At most 32 completions and 64 inspections
   per 20-tick pass under ADR-050; no unbounded full completion. Record the candidate's effective configured limits.
3. [H] Stop normally and restart the same copy twice. → Compare mission IDs, snapshots, reward accounts and receipts
   after each start; a completion may be retried, but never paid twice.
4. [H] On REF, sample 500 active missions through scheduler completion and reward delivery. → Record tick
   mean/P95/P99, CPU, RSS, GC, network and queue/backlog progression against docs/17.
5. [H] Repeat at 1,000 missions as extended pressure. → Report separately; do not relabel it as the 500 reference run.
6. [H] Measure barrier/coalesced flushes at 500, 1,000 and the worst-case root. → Show snapshot and durable epoch
   publication on the activated candidate, rather than assuming the older synchronous ADR-050 implementation.
7. [H] Compare reference measurements to mean ≤25 ms, P95 ≤50 ms, P99 ≤100 ms, no sustained RSS >5.5 GiB,
   no watchdog/OOM/unexpected forced chunks. → If flush costs exceed the budgets, retain the failure and refer it
   to Root; the historical 631 ms development-host maximum is not a reference result or waiver.

**Accepted preparation/action steps (W408):**

(C10/C24, R535) Read C6's effective shipped scheduler defaults without increasing them. Use the standard matched sampling plan for short measurements; online missions run inside the four-hour soak, deadlines set so at least one mission of each kind expires inside its window. Offline work includes that soak restart plus a separate 30-minute stop (procedure parameter); downtime is not mission progress. Preparation is required for the >100/500/1,000 scenes, per-kind deadlines/default readback and driver; preparation is required for the raw sampling. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open. Entry G-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.

**Evidence to keep:** Phase 3 restart projections and writer-drain logs; phase 5 500/1,000/worst-root manifests,
raw timing/GC/RSS/flush samples, backlog and reward comparisons, and reference hardware record.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G02 — Bounded offline catch-up

**Phase/level/machine/accounts:** 3 DS, development PC, A. **Gate:** Ordinary fixture.
**Preconditions:** P2 missions owned by A, due over a multi-hour interval. Record logical game time and deadlines;
C24 chooses the four-hour online soak plus its restart and a separate 30-minute stop,
with one per-kind expiry inside the running window; these are procedure parameters.

1. [P] Start the selected missions and log A out. → Missions remain owned by A; no claim happens on disconnect.
2. [H] Leave the server running for the recorded multi-hour period. → Logical clock advances with server game
   time, not the owner's connection; completions and offline work are bounded per scheduler pass.
3. [P] Reconnect A. → Mission status and balances agree with the server, with no unlimited catch-up spike or duplicate payout.
4. [H] Run a separate stopped-server interval, preserving the same initial fixture. → Downtime does not advance
   the mission clock; `/time` is not a legitimate way to advance game time for this case.
5. [H] Restart the running-server case twice and compare records. → Catch-up and claims are stable and repeat safely.

**Accepted preparation/action steps (W408):**

(C10/C24) The online interval is inside the four-hour soak; the stopped-server control is the soak restart plus one 30-minute stop, a procedure parameter. Read effective default scheduler limits; arrange one expiry per kind inside the appropriate running window, not wall-clock downtime. Preparation is required for the deadlines, scene and scheduler observations. Use A's matched sample/review rule where comparing costs. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.

**Evidence to keep:** Disconnect/reconnect and stop/start times, logical clock/deadline snapshots, scheduler work
counts/tick samples, mission/reward projections and logs in phase 3.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G03 — Two players settle in the same tick

**Phase/level/machine/accounts:** 4 V2, real clients A/B on packaged DS. **Gate:** Ordinary fixture.
**Preconditions:** Each account owns a ready mission with a known reward snapshot; terminal buffers have room.
Configure the harness's input synchronization and server-tick observation before attempting the claim.

1. [P/H] Open each account's mission terminal. → Each sees its own mission, status and reward.
2. [P/H] Submit the two claims in the same measured server tick. → Each settles its own reward exactly once;
   one account never receives the other's reward.
3. [P/H] Replay both claim controls. → No second reward, component extraction or account increment.
4. [H] Compare account, terminal and mission fields before/after. → Total credit equals the two captured rewards;
   both intents' tick is proven, not inferred from simultaneous video.

**Accepted preparation/action steps (W408):**

(C9) Keep each claim's server receipt tick; "same tick" requires one identical tick in the server record, with no overlap tolerance. Otherwise report the gap, still check the two distinct once-only rewards/no gain or loss, and mark only same-tick V2 NOT_COVERED with its named A1 test. Preparation is required for the tick observation and ready missions; bounded intent support, if needed, still requires qualification. Owner O5 supplies coordinated inputs. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open. Entry G-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open.

**Evidence to keep:** Both client logs/video and server tick-correlated claim transcript, exact rewards, balances
and terminal projections in phase 4. If same-tick delivery cannot be established, record NOT_COVERED for that variant.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G04 — Forced stops around reward persistence

**Phase/level/machine/accounts:** 3 S2, packaged DS on development PC; no client required for the native cuts.
**Gate:** Only the native runner's documented release-test setup on a disposable copy, never production enablement.
**Preconditions:** P2 mission/reward fixture, current C6 runner and named pre-write/post-write cuts from
[the v1.6 recovery matrix](../../../releases/v1.6.0/RECOVERY-MATRIX.md). Pin runner revision and candidate arguments.
Freeze one cut per complete copied world; A/B/D/C below are the matrix's historical cut names, not guessed commands.

1. [H] Capture mission, registry and terminal state before a claim. → Baseline includes reward snapshot, receipts and epochs.
2. [H] Stop only the runner-owned server at each selected durable boundary. → Preserve actual disk state
   and cut receipt; do not substitute a normal stop or single-file rollback to manufacture the cut.
3. [H] Restart that copy for observation 1. → Apply the exact row oracle below, including its acknowledged receipt state.
4. [H] Save and restart the same recovered copy for observation 2. → Same resource result, no second payout;
   a new source copy is used only when starting a different cut.
5. [H] Compare total resources across registry, terminal incoming/output and account after both restarts. → One
   authoritative payout for these non-rebind controls; uncertain state remains diagnosable rather than repaid.

   | Cut | Durable boundary and required recovery |
   |---|---|
   | Neither store | READY/no receipt; can claim again and pay once. |
   | A | Chunk receipt saved, registry not: CLAIM_RECOVERED, CLAIMED/instance DEPLETED, no new items; acknowledge then release. |
   | B | Registry CLAIMED/unacknowledged, chunk receipt absent: rematerialize once at the paying terminal, acknowledge then release. |
   | D | Both saved: acknowledge only after the chunk tag is seen, drop receipt after the next flush. |
   | C | Registry reverted by the actual mixed-save cut, operator cancel before terminal load: CANCELLED plus receipt, paid once, instance QUARANTINED, PAID_THEN_CANCELLED once, receipt retained. |

6. [H] Record excluded residuals next to the result. → ADR-051's operator-rebind/new-terminal-first double payment
   and lost asynchronous chunk write after acknowledged save are disclosed limitations, not covered by a blanket
   “never loses or duplicates under every failure” claim. Do not create those by destructive file manipulation here.

**Accepted preparation/action steps (W408):**

(C25) The exact matching C6 saved-boundary/epoch map must be recorded before selecting a historical cut. A cut without a match is NOT_COVERED with that reason, not presumed equivalent to a synchronous historical write. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.

**Evidence to keep:** Runner command/version, candidate installed-copy hash, cut logs, stopped-disk projections,
both restart logs and exact reward conservation comparison in phase 3.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G05 — Instance unloading, reclamation and ticket cleanup

**Phase/level/machine/accounts:** 3 DS, development PC with A. **Gate:** Ordinary mission fixture.
**Preconditions:** P2 logical asteroid instance records and gas missions with recorded IDs, status, expiry
and owner. There is no instance chunk to enter or unload under ADR-051. Use the owning terminal's actual chunk
for unload/reload; capture initial loaded/forced chunks, registry/cache counts and configured TTL (default
168,000 logical ticks, allowed 24,000–1,728,000). Reclamation follows DEPLETED/EXPIRED plus 1,200 ticks.

1. [P/H] Inspect an AVAILABLE record and start its asteroid mission through its terminal. → Only the owner's same-system
   instance becomes ALLOCATED; no physical region, dimension, coordinate disclosure or ticket is created.
2. [P/H] Leave the terminal's chunk and remove only fixture-owned loads. → The terminal unloads; logical mission
   processing does not require an instance ticket. Return and read the same mission/instance snapshot.
3. [H] Claim a completed asteroid mission and observe retention. → Instance becomes DEPLETED, reward reconciles once;
   DEPLETED record is eligible for bounded removal after 1,200 logical ticks.
4. [H] Cancel a separate ordinary non-rebound asteroid mission after reconciliation. → Its instance returns AVAILABLE
   if unexpired, otherwise EXPIRED; no fabricated reward. Rebound/operator cancellation uses quarantine, not that return rule.
5. [H] Allow a third AVAILABLE instance to reach its configured logical expiry. → EXPIRED, then bounded removal after
   1,200 ticks; neither an ALLOCATED instance nor a live mission is discarded to fake cleanup.
6. [H] Restart twice and compare chunk tickets, instance records and caches. → Reclaimed instances do not resurrect
   or leave forced chunks; active instances still correspond to their mission snapshots. Gas missions do not acquire
   a fictitious asteroid-instance region.

**Accepted preparation/action steps (W408):**

(C24/C26; ADR-051) Read effective default scheduler/TTL values, arrange per-kind expiry deadlines inside the C24 online window and 30-minute stop control; never count downtime as progress. Test the inherited "instance chunks" as owning-terminal unloading and logical record reclamation, not a nonexistent world region. Preparation is required for the concrete mission/deadline/unload controls. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.

**Evidence to keep:** Instance lifecycle/expiry manifest, chunk and `/forceload query` observations, cache/record
counts, cancellation/completion receipts and restart comparisons in phase 3.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G06 — Satellite, mission and destination presentation

**Phase/level/machine/accounts:** 2 V1 on main and second GPU categories, A. **Gate:** Ordinary fixture.
**Preconditions:** P2 satellite assembly/mission world with data, survey, asteroid and gas workflows, readable
failure/cancel/expiry fixtures and the relevant star-map targets. Keep the reference reward snapshots visible in the manifest.

1. [P/H] Assemble the satellite package through the normal assembly screen. → Components, output and any refusal
   agree with the server; no missing model/texture.
2. [P/H] Launch and start each selected mission from its terminal. → Progress/status agree with the registry,
   without client-granted completion.
3. [H] Select each mission target on the star map. → Displayed target matches the actual body's mission/instance context.
4. [P] View the logical asteroid instance/reward selection and gas-giant target context. → Display matches the selected
   record/body; ADR-051 provides no physical asteroid-instance destination. Do not invent a visit or a gas-giant surface.
5. [H] Exercise failure, cancellation and expiry separately. → Each gives readable, distinct feedback, without a false success.
6. [P/H] Claim the completed resource mission. → Screen state changes with the single server payout.
7. [H] Repeat the screens in English/Chinese, GUI scales 2/3/4 and after F3+T. → No essential clipped controls,
   missing resources, stale target or status. Record the same flow on the second real GPU category.

**Accepted preparation/action steps (W408):**

(C26; ADR-051) State explicitly in the result that inherited "instance visuals" means logical record/target/reward presentation; no instance dimension/region is implemented or invented. Preparation is required for the real terminal/mission/UI scene. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.

**Evidence to keep:** Mission gameplay video, step screenshots, progress/target/reward projections and client/server
logs in phase 2, with hardware and language/scale indexes. Sources supply no mission-specific FPS threshold.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G07 — Two-client mission state

**Phase/level/machine/accounts:** 4 V2, real clients A/B and packaged DS. **Gate:** Ordinary fixture.
**Preconditions:** P2 mission world; A owns the mission and B has only the intended observer/member permissions.
Prepare a separate B-owned mission so private balances can be compared.

1. [P/H] Open both mission views before start. → Each view exposes only the authorized state.
2. [P/H] A starts a mission while B observes the permitted shared view. → Both authorized views match the server's
   target, progress and phase; B does not gain A's claim rights.
3. [P/H] Advance through ready and claim. → Both views refresh to the same authoritative state; private rewards stay private.
4. [P] Disconnect and reconnect B. → Restored view reflects current mission state, not a stale ready/claimable snapshot.
5. [P/H] Repeat with B's mission and with a refusal/cancelled mission. → Ownership does not leak across clients;
   cancel/failure is not presented as successful completion.

**Accepted preparation/action steps (W408):**

Preparation is required for the actual owners, missions, observer permissions and start/claim/cancel controls; R546's common receipt/state rule applies to every negative action. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.

**Evidence to keep:** Paired screenshots/video at each phase, account permissions, private balance projections,
both client logs and server log in phase 4.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- **Answered:** C24 online soak/restart plus separate 30-minute stop/default limits/per-kind expiry procedure; C10/R535 samples; C9 same-tick proof/no tolerance; C25 cut mapping rule; C26/ADR-051 logical presentation/terminal unloading/record reclamation instead of physical-instance wording.
- **G01-G07:** Immutable mission IDs/owners/packages/rewards, controls, effective defaults, deadlines and driver remain required. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.
- **G01/G02/G05:** Raw scheduler/tick/all-ticket observations remain required alongside K09's workload. Downtime is not progress. Entry G-K15 (R548; R648 for A08/A09) supplies the following inputs: bounded observers, with explicit remaining visibility gaps. Direct cache/GPU/all-Forge-ticket proof and actual REF qualification remain open.
- **G03:** Actual receipt-tick observation remains required; extra intent support, if required, still needs qualification. O5 coordinates inputs. Unproved identical ticks means same-tick V2 NOT_COVERED with named A1 test. Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open. Entry G-K18 (R601) supplies the following inputs: bounded client intent JAR and case/readback matrix. The listed unsupported intent/timing controls remain NOT_COVERED, and real-client receipt/state qualification remains open.
- **G04:** Each historical cut's C6 epoch/save-boundary mapping remains required; no match means NOT_COVERED with reason.
Entry G-K09 (R625) supplies the following inputs: 102/501/1001-active source sets and mapped cuts. Sustained 500/1,000 mission load, real-client same-tick proof and the listed unqualified cuts remain open.
