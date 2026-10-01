# v1.6.0 requirement map (development)

Maps the [v1.6 plan](../../versions/V1.6.0-SATELLITE-RESOURCE-MISSIONS.md) checklists
(sections 9–15) to the retained development evidence. "Open" means no evidence yet.
Nothing here is candidate-bound, and nothing is a Gate approval. The contract-level
map is [v1.6.0-contract-coverage](../../work/v1.6.0-contract-coverage.md).

Paths are under `docs/work/` unless stated otherwise. "C9" is the
[C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md).

## Section 9: automated tests

| Plan item | Evidence | State |
|---|---|---|
| Component combinations, invalid blueprints, stats | Blueprint vectors in `SatelliteBlueprintsTest`, builder GameTests ([C7a](../../work/v1.6.0-c7a-model/VERIFICATION.md), [C7b](../../work/v1.6.0-c7b-builder/VERIFICATION.md)) | Automated |
| Scheduler tick, offline catch-up, budget | `RegistryLifecycleTest`, `MissionDeadlineSchedulerTest` ([C8a-1](../../work/v1.6.0-c8a-scheduler/VERIFICATION.md)); native 1,000-mission backlog (C9) | Automated and native |
| Idempotent settlement and duplicate packets | `ResourceMissionsTest`, `DeliveryReconciliationTest` (36 rows), the 64 rebind orderings ([C8b](../../work/v1.6.0-c8b-delivery/VERIFICATION.md)); no packet carries an ID, seed or reward (ADR-049 §10) | Automated |
| Mission definition removal and version change | `RewardVerifier` outcomes ([C8a-2](../../work/v1.6.0-c8a-resources/VERIFICATION.md)); new starts refused, snapshots kept (C8b) | Automated |
| 500/1,000 synthetic missions | Native: timings at 500 and 1,000, a restart with 1,000, the 1,000-mission backlog and the worst-case root (C9) | Native (development host) |
| Asteroid instance allocate/recycle | `ResourceMissionsTest`; GameTest `cancelRecyclesAtTheBoundTerminalOnly` (C8b) | Automated |
| Gas resource rules | `gas-v1` vectors (C8a-2); GameTest `gasHarvestNeedsTheDiscoveredGasGiant` (C8b) | Automated |
| Legacy satellite fixture migration | `SatelliteRootThreeTest` (C7a); native v1.5 world upgraded before start with a backup (C9) | Automated and native |

## Section 10: dedicated server, restart and multiplayer

| Plan item | Evidence | State |
|---|---|---|
| 100+ active missions and restart | Native: 1,000 unfinished missions through restarts (C9) | Native |
| Bounded catch-up after hours offline | Native backlog: 1,000 due at once, 32 per pass (C9); `RegistryLifecycleTest` | Native |
| Two players settle in the same tick | `ResourceMissionsTest.twoOwnersSettleInTheSameTickIndependently` (C8b) | Automated; S2 with real players open |
| Forced stop before and after reward writes | Native cuts A–D (C9); `DeliveryCrashCutTest` (C8b); [recovery matrix](RECOVERY-MATRIX.md) | Native and automated |
| Mission instance chunk unload/recycle | **Disposition:** instances have no chunks (ADR-051 §1); allocate/recycle is tested instead (above) | Automated |
| No chunk-ticket leak | Source audit `DeliveryRuntimeTest.satelliteCodeNeverTakesAChunkTicket`; GameTest forced set unchanged; native forced set unchanged across every phase (C9) | Automated and native |

## Section 11: manual and visual

| Plan item | Evidence | State |
|---|---|---|
| Satellite assembly and mission UI and progress | GameTests compose the terminal view (C7b, C8b) | Automated; V0 not run on this Windows host; V1 open |
| Star-map mission targets match real bodies | `target_body` from the celestial tree (C8b); `mission verify` MATCH (GameTest) | Automated; V1 open |
| Asteroid instance and gas giant visuals | The delivery panel shows the instance yield and the product (C8b) | V1 open |
| Failure, cancel and expiry feedback | Explicit result codes in both languages (C7b, C8b) | Automated; V1 open |
| Multiplayer mission state sync | Server-selected terminal view per menu (C7b) | V2 open |

## Section 12: security and abuse

| Plan item | Evidence | State |
|---|---|---|
| The client cannot submit completion or reward | Menu intents are button IDs only; the server holds every selection (ADR-049 §10) | Automated |
| Mission frequency, quantity and target limits | Intent spacing, per-owner and global limits, byte budgets (C8a-1) | Automated |
| The random seed is not client-controlled | `MissionSeeds` (server `SecureRandom`, ADR-052 §3) (C8a-2) | Automated |
| Cancel or replay does not copy components or rewards | Reconciliation table, rebind orderings, crash cuts, 272 claims at one terminal (C8b); native cuts (C9) | Automated and native |
| Instance coordinates cannot be probed or loaded | Instances have no coordinates (ADR-051 §1); ticket source audit | Automated |

## Section 13: performance and resource budgets

| Plan item | Evidence | State |
|---|---|---|
| Fixed per-tick task budget | 32 completions, 16 expiries and 64 prunes per pass; 64 reconciliations per terminal per tick (C8a-1, C8b) | Automated |
| Offline catch-up in batches | Native backlog (C9) | Native |
| 500 missions within the reference budget; 1,000 as extended stress | Native on the development host (C9): every 500- and 1,000-mission window within the docs/17 budgets; the worst-case-root flush (up to 631 ms) keeps the ADR-050 §2 gate open | Development measurement; reference `[H]` |
| Tickets and caches cleaned after completion | No tickets; scan jobs transient (C7c) | Automated |
| Coalesced SavedData writes | `SatelliteWritePolicyTest` (C8a-1); native coalesced flush counts and times (C9) | Automated and native |

## Section 14: acceptance

| Condition | State |
|---|---|
| At least three satellite kinds and two resource mission kinds fully playable | `data`, `survey`, `solar`, asteroid and gas are implemented and tested; V1 play-through open |
| Missions in flight recover through restart, upgrade and definition change | Native and automated (above) |
| No permanent forced chunk loads | Automated and native |
| 500-mission reference pressure passes | Development host only; reference hardware open |
| Reward and component conservation | Automated and native |
| Zero Critical/High | See the final review in C9 |

## Section 15: archived evidence

| Item | Record |
|---|---|
| Scheduler ADR | [ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md) |
| 500/1,000 task performance | C9 (development host) |
| Reward idempotency/recovery | [RECOVERY-MATRIX](RECOVERY-MATRIX.md) |
| Mission gameplay video | Open (V1) |
| Migration report | C9 native `upgrade` phase |
| Chunk ticket audit | C9 and the source audit |
