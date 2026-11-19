# Campaign checklists G: satellites and resource missions

These unrun P4 lists preserve G01–G07 of [the campaign](FINAL-CAMPAIGN-PLAN.md). Read
[v1.6 sections 10–13](../../../versions/V1.6.0-SATELLITE-RESOURCE-MISSIONS.md),
[G7/G8](../../../releases/v1.6.0/GATE-STATUS.md) and
[ADR-050 sections 1–2](../../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md).
[ADR-051](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) resolves the version
checklist's physical-instance wording: these are logical registry records without regions, dimensions or coordinates.
The fields below are deliberately blank: these are instructions, not acceptance evidence.

## Shared preconditions and artifact rules

H marks harness/operator actions; P marks human actions/judgements. Each arrow supplies the expected observation.
Use the P2 representative v1.3–v1.7 world, specifically the mission fixture, on disposable copies. Root must pin its
archive/hash, terminal coordinates, satellite/mission/instance IDs, launch packages, owner UUIDs, target bodies,
reward snapshots and the ordinary start/claim/cancel controls before running. The cited version sections do not
give these values; do not invent them. Include data, survey, asteroid and gas missions and at least three satellite
types, not just one timed mission.

Use C4 from P1 with Java 17, Forge 47.4.10 and no JEI by default; record the actual versions and use the 47.4.23
compatibility profile for its smoke. If JEI is present it is 15.56.0.205. Phase 3 DS uses the packaged server on the
development PC; phase 2 V1 uses a real GPU and includes the second GPU category required by G8. Phase 4 V2 uses
two real authenticated accounts, `online-mode=true`, `white-list=true`, `enforce-whitelist=true`,
`enable-rcon=false`, restricted access and exactly the two clients. No marked bay gate or extra JVM test property
is needed for ordinary mission play; all unrelated production switches remain unchanged. Pin difficulty, view and
simulation distances in the run manifest; the cited sources supply no fixed numbers.

Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/<C4 short SHA>/`. DS/S2 goes under
`E/phase3-ds/<row>/`; V1 under `E/phase2-v1/<machine>/<round>/`; V2 under `E/phase4-v2/<sitting>/`; REF under
`E/phase5-ref/<run>/`. Retain native logs, exact controls/commands, UTC step times, configuration, state projections,
candidate/JAR SHA-256 and fixture hashes. Name screenshots `<row>-<step>-<client>-<language>-<scale>.png` and
videos `<row>-<client>.mkv`. Add artifact hashes/references to `E/results/RESULTS.md`, `results.json` and
`FINDINGS.md`, then seal `E/SHA256SUMS.txt`. A missing fixture/driver means NOT_COVERED, never PASS.

## G01 — Active missions, restart, 500/1,000 load and flush cost

**Phase/level/machine/accounts:** 3 DS on development PC with A; 5 REF on 4 vCPU/8 GiB/Debian 12, non-root,
Java 17 and `Xmx4G` maximum, loopback server/SSH-tunnel access on an exclusive node.
**Gate:** Ordinary fixture; no campaign marker.
**Preconditions:** P2 mission world with more than 100 active missions; separate 500-, 1,000- and synthetic
worst-case-root fixtures. Capture scheduler clock, statuses, deadlines, registry epoch, rewards and terminal receipts.

1. [H] Start the >100-active-mission world on C4. → Registry restores without dropped missions or duplicate rewards.
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

**Evidence to keep:** Phase 3 restart projections and writer-drain logs; phase 5 500/1,000/worst-root manifests,
raw timing/GC/RSS/flush samples, backlog and reward comparisons, and reference hardware record.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G02 — Bounded offline catch-up

**Phase/level/machine/accounts:** 3 DS, development PC, A. **Gate:** Ordinary fixture.
**Preconditions:** P2 missions owned by A, due over a multi-hour interval. Record logical game time and deadlines;
the source says “hours” but gives no exact duration. Root must fix the duration before execution.

1. [P] Start the selected missions and log A out. → Missions remain owned by A; no claim happens on disconnect.
2. [H] Leave the server running for the recorded multi-hour period. → Logical clock advances with server game
   time, not the owner's connection; completions and offline work are bounded per scheduler pass.
3. [P] Reconnect A. → Mission status and balances agree with the server, with no unlimited catch-up spike or duplicate payout.
4. [H] Run a separate stopped-server interval, preserving the same initial fixture. → Downtime does not advance
   the mission clock; `/time` is not a legitimate way to advance game time for this case.
5. [H] Restart the running-server case twice and compare records. → Catch-up and claims are stable and repeat safely.

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

**Evidence to keep:** Both client logs/video and server tick-correlated claim transcript, exact rewards, balances
and terminal projections in phase 4. If same-tick delivery cannot be established, record NOT_COVERED for that variant.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## G04 — Forced stops around reward persistence

**Phase/level/machine/accounts:** 3 S2, packaged DS on development PC; no client required for the native cuts.
**Gate:** Only the native runner's documented release-test setup on a disposable copy, never production enablement.
**Preconditions:** P2 mission/reward fixture, current C4 runner and named pre-write/post-write cuts from
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

**Evidence to keep:** Paired screenshots/video at each phase, account permissions, private balance projections,
both client logs and server log in phase 4.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- G01–G07: Pin the P2 archive, IDs, coordinates, ordinary GUI controls and current native driver invocations.
  The version checklist alone does not supply runnable fixture creation commands.
- G01/G02/G05: Freeze reference sampling windows, the multi-hour online/offline duration, per-kind expiry and
  the candidate's configured scheduler limits; do not use wall-clock downtime as mission progress.
- G03: Supply the same-tick input/observation mechanism. A human's two near-simultaneous clicks alone cannot prove it.
- G04: Map the historical recovery cuts to C4's asynchronous snapshot/durable epochs before selecting the cuts.
- G05/G06: The version/plan still says instance chunks and instance visuals; ADR-051 explicitly defines logical
  instances with no coordinates/world region. These lists test terminal unloading, record reclamation and logical
  presentation; Root must record the disposition of the inherited physical wording, not require invented dimensions.
