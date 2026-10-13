# W11b2 design note: rocket integration of the D3-A escrow

Date: 2026-10-12. Status: design for one independent read-only review (AGENTS.md section 13); not
implementation or Gate evidence. Base `0a804174`. Inputs: [W11A-DESIGN.md](W11A-DESIGN.md) round 2
(sections 4, 5 and 8) and the round-2 review `D:/GitHub/ARCE-Task-Evidence/root-queue/W11a2-review/REVIEW.md`,
SHA-256 `e54fd579dd5dee437c16c5d385a5fe5254280e080c78f1c661d2d3231cb68dc2`. This note closes the review's
entry conditions P1 to P5 and specifies the rest of the W11b2 row. It changes no code, test, ADR, risk
entry or status file; section 7 holds the proposed text Root adds. W11b1 has no commits at this base,
so W11b1 interfaces named here are those its TASK and changes C1 to C6 require (section 8).

## 0. Checked facts

Same jar as W11a section 0 (`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`), JDK 17.0.7 `javap -p -c`. New facts
only; W11a section 0 covers the rest. Static checks, not native results.

| Class and method | Consequence for W11b2 |
|---|---|
| `SavedData.save(File)`, offsets 41-62 | In-place `NbtIo.writeCompressed`; an `IOException` is logged, then `setDirty(false)` runs anyway, so a failed write is forgotten. |
| `DimensionDataStorage.save` and `lambda$save$0` | Calls `SavedData.save(File)` for every cached file on the calling thread. |
| `ServerLevel.save(ProgressListener,boolean,boolean)` → `saveLevelData` → `DimensionDataStorage.save`; `MinecraftServer.tickServer` (`sipush 6000`, `irem`, `saveEverything`) | Autosave every 6,000 ticks and stop-save write data files on the server thread, before `ServerChunkCache.save`. |
| `KillCommand.kill` → `Entity.kill` → `remove(KILLED)` | `/kill` never calls `hurt`; `RocketEntity.hurt` returning false (`RocketEntity.java:442-444`) does not protect a rocket. `Entity.kill()` is public and overridable. |
| `EntityDataAccessor.setData` (offset 30), `BlockDataAccessor.setData` (offsets 20, 27) | `/data merge` and `/data modify` call `Entity.load` or `BlockEntity.load` plus `setChanged` on a live object, with no event. |
| `SpreadPlayersCommand.setPlayerPositions`, offsets 156-186 | Posts `EntityTeleportEvent.SpreadPlayersCommand` and honours `isCanceled`, so it can be refused, not only detected. |
| `TeleportCommand` (`onEntityTeleportCommand`, `isCanceled`); `Entity.startRiding(Entity,boolean)` (offset 49, before the force branch at 58); `ForgeEventFactory.canMountEntity` | `/tp` and every mount, forced or not (`/ride`, `RocketTransferEntities.java:282`, `RocketFlightService.java:446,454`), are cancellable. |
| `ServerLevel.getEntity(UUID)` → `LevelEntityGetterAdapter.get` → `visibleEntities`; `ServerLevel.isPositionEntityTicking` → `PersistentEntitySectionManager.canPositionTick` → `Visibility.isTicking`; `Visibility.<clinit>`; `PersistentEntitySectionManager.lambda$updateChunkStatus$6` | UUID lookup sees tracked entities only. TICKING visibility is also accessible, and accessibility changes start or stop tracking. So `areEntitiesLoaded && isPositionEntityTicking && getEntity(uuid) == null` proves absence from that loaded chunk. |
| `Entity.isAddedToWorld()` (Forge) | Distinguishes a live `/data` reload from the load of a fresh instance during chunk loading. |

## 1. P1: a checked-durable ROCKET_TRANSFERS state

**Today.** `RocketTransferSavedData` extends plain `SavedData` (`RocketTransferSavedData.java:29`). Its
`flush(server)` (`:199-205`) runs `DimensionDataStorage.save()`, which reaches the in-place write above.
Nine production sites flush at transfer boundaries (`RocketTransferService.java:155,168,431,675,719,736`;
`RocketTransferRecoveryService.java:275,309,330`); autosave and stop-save write the same file.

**Adoption of W11b1's per-type checked writer policy (C6).** One policy entry:
`ROCKET_TRANSFERS → SERVER_THREAD_CHECKED`, committer `CheckedSavedDataFile::atomicMove`, bound
`ManagedSavedDataType.ROCKET_TRANSFERS.maxUncompressedBytes()` (146,866,176 bytes from
`RocketFlightLimits.java:22-25`) and its compressed bound. SERVER_THREAD_CHECKED is today's
`AtomicSavedData` behaviour (CELESTIAL, SATELLITE_MISSIONS, ENDGAME), not the ledger's off-thread writer.
The journal keeps extending `SavedData` and overrides `save(File)` to call that writer by composition.
Inheriting `AtomicSavedData` would clash: its `flush(MinecraftServer)` is final and throws
(`AtomicSavedData.java:38-54`), while the journal's must stay non-throwing. The override follows
`AtomicSavedData.save(File)` (`:25-35`): return if clean, else checked replace, catch and log once per
failure streak, never throw. A failure after the committer ran is resolved by bounded readback as in
`StationRegistrySavedData.java:554-580`: candidate on disk means success, unreadable means unknown.
`flush(server)` keeps its signature, call sites and body, so other dirty files still save there.

**What this changes for existing journal writes.**
1. Every write of this file becomes stage, force, decoded readback and atomic replace. The in-place
   write is never used for it again, so no in-place write can race the move (review H2).
2. A failed write keeps the dirty flag and the previous file; autosave retries it.
3. Threading is unchanged: synchronous, server thread, the same points. The cost adds force and
   readback. Transfer step order, and so the existing v0.6/v1.0 transfer cut evidence, is unchanged.
4. A transient `.arce-pending` sibling may exist; start never reads it (W11a section 2).
5. Schema 2, fields, record and journal limits, checksums and pre-start migration are unchanged. The
   file stays a vanilla gzip root (`BoundedSavedDataIo.write` uses `NbtIo.writeCompressed`).
6. An absent file is created once, empty, on the first escrow need; production cannot reach that
   while admission is disabled.

**Acknowledgement and guard.** RAM-only state: `mutationSequence`, incremented by `put`, `replace`,
`remove`, `migrateCommitted` and load-time migration; and `acknowledged = (sequence, root digest,
record digests by transfer ID)`. The writer captures the sequence before encoding and sets
`acknowledged` only after replacing exactly that encoding (same thread; assert `isSameThread`). A
failure before replace keeps the old acknowledgement, which still describes the disk; an unknown
outcome clears it. Digests are SHA-256 of W11b1's canonical sorted-key encoding of `encodeRecord`, so
they compare across restarts. `acknowledgedGuard(logical)` returns `(logical, transferId|NONE,
recordDigest|NONE, rootDigest, sequence)` only when the journal is operational, clean and
`acknowledged.sequence == mutationSequence`. Admission accepts it only if the record is absent or is the
COMMITTED, settled, stationary landed reservation already accepted at `RocketTransferService.java:412-415`;
otherwise TRANSFER_JOURNAL_UNPROVED. L1 persists `(logical, transferId|NONE, recordDigest|NONE)`; the
sequence is session-local and diagnostic. At restart, a guard that differs from the loaded journal is
REPAIR with reason JOURNAL_GUARD_MISMATCH (only a per-file rollback or a bug can cause it).

**Absent file.** `acknowledged` starts empty every session, whether or not the file exists. The first
admission that finds an operational, clean, unacknowledged journal calls `ensureCheckedBaseline`
(`setDirty()`, then `flush(server)`). An absent file becomes a stamped empty root
(`SavedDataSchemaMigrator.stampCurrent`, `transfers: []`). The admission proceeds only if that write
was acknowledged; after a failure, at most one baseline attempt per 200 ticks. A blocked journal
(`preservedBlockedData`) is never rewritten.

**No journal change while pending.** Each journal mutation for a logical rocket with an unretired
escrow record is refused at its call site: `prepareLaunch`, `releaseLandedReservation`, every recovery
action (section 2), `migrateCommitted` at `RocketTransferService.java:674` and reconnect settlement.

**Benchmark and stop rule.** Extend W11b1's harness: (a) a reference-load cycle of 8 concurrent transfers
of 2,048-block rockets, with every checked flush, meets docs/17 mean, P95 and P99 MSPT; (b) a journal
at the bound (64 records, maximum snapshots) reports the write tick's server-thread time and heap peak
at Xmx 4 GiB, beside the in-place write measured by the same harness at `0a804174`. If (a) fails or (b)
runs out of memory or trips the watchdog, stop and report to Root: never relax budgets or shrink the
journal. Byte-equal readback or an off-thread writer joined by `flush` would need Root's decision.

**Tests and cuts.** A0: sequence, failure and unknown outcomes, guard comparison. A1 with real files and
an injected committer: dirty and old file kept on failure, autosave retry, absent-file baseline, blocked
journal untouched, non-throwing flush at all nine sites. S2: J1 baseline staged-only and replaced;
J2 boundary flush staged-only and replaced before acknowledgement; then the existing transfer
forced-stop harness, unchanged, with no regression.

<div style="page-break-after: always;"></div>

## 2. P2: fenced transfer records

**Fence.** Before any of the six `RocketTransferRecoveryAction` values (`KEEP_SOURCE`,
`REMOVE_DESTINATION_KEEP_SOURCE`, `REBUILD_SOURCE`, `KEEP_DESTINATION`, `REMOVE_SOURCE_KEEP_DESTINATION`,
`REBUILD_DESTINATION`; `RocketTransferRecoveryService.java:228-291`), and before `tryReconnect`'s duplicate
election, remount, `movePassenger` and `finishSourceSettlementIfComplete` (`:152-214`), recovery asks the
coordinator. It answers FENCE with a reason when (a) an unretired escrow record or marker names the
logical rocket, or a tombstone names it or a candidate entity; or (b) a floor `(logical, transferId)`
matches the record and the action would rebuild from the record, keep or elect an entity other than
the floor's protected UUID, keep that entity with a resource epoch below the floor's, or discard an
entity with markers or a nonzero epoch (FLOOR_CONFLICT). Otherwise the action runs unchanged; the
common restart case, a landed protected rocket kept as the unique destination, proceeds normally.

**ADR-044 classification.** A fenced record enters a third in-session set, `fencedTransfers`, beside
`liveTransfers` and `settledTransfers`. `stationRegionInMotion` (`RocketTransferService.java:286-297`)
passes `classified = live ∪ settled ∪ fenced` and counts a PREPARED record as moving when `live ∪ fenced`
holds it. ADR-044 section 5 applies unchanged: rule 2 is satisfied, so a fenced record no longer blocks
every warp (`RocketStationMotionRule.java:36-40`); under rule 3 a fenced PREPARED or mid-phase record
blocks only the station regions it overlaps; under rule 4 a fenced COMMITTED record blocks only during
its descent window. The H3 case (COMMITTED, destination killed) blocks no warp. A fenced record still
refuses its rocket's mutations (it is not settled, `:405-409`), keeps its landing region reserved
(`:78-81`) and keeps the one-transfer-per-rocket rule. `journalDiagnostics` (`:315-339`) adds
`fenced=<n>` and lists up to four with reason and the settle command.

**Retries without tickets.** `RocketTransferRecoveryService.step` (`:87-99`) excludes fenced records, so
`recoveryEntityChunksLoaded`, `keepLoaded` and `findMatches`, which add `FLIGHT_TICKET` tickets and load
origins (`RocketTransferEntities.java:99-111,130,317-336`), never run for them. `tryReconnect` treats a
fenced record as done for that player. A fenced record returns to the unclassified set only on an
`EntityJoinLevelEvent` of a rocket with its logical ID (at most once per 1,200 ticks), an O4
acknowledgement naming it, or `/arce rocket recover <transfer>` (one operator attempt). One WARN per entry
into FENCED or change of reason (`ARCE_TRANSFER_FENCED`); nothing per skipped attempt.

**Settlement of a conflict on a retired floor: `/arce escrow settle-floor <transfer> [confirm <digest>]`.**
Permission 2; only for a record fenced with FLOOR_CONFLICT whose floor has no unretired operation. It
requires the protected UUID MISSING by the section-5 rule (preview and confirm at least 200 ticks
apart), and no entity with the logical ID in either end's loaded entity chunks (read-only query;
WAITING_UNLOADED instead of loading). The preview shows the floor's last resource state and the signed
net change, recorded as audited loss.

| Step | Contract and forced-stop result |
|---|---|
| O1 intent | Checked ledger write of a settlement record (kind `TRANSFER_FLOOR_RETIRE`, phase SETTLING_ABANDON) freezing logical and transfer IDs, journal record digest, protected UUID, floor epoch and signature, both observations; reserves one tombstone. Before: no choice. After: restart resumes only this settlement. |
| O2 absence checkpoint | W11b1 barrier on the protected UUID's last bound entity chunk: own store, force, stable readback, confirming no entity with that UUID or logical ID on disk. A failure keeps O1 locked; restart repeats O2. |
| O3a tombstone | Checked ledger write installing the tombstone (logical ID, protected UUID, audited loss). |
| O3b journal | `journal.remove(T)` and a checked flush; waits for the exact acknowledgement of a snapshot without T. Restart between O3a and O3b repeats O3b; the tombstone keeps T fenced meanwhile. |
| O4 release | Checked ledger write retiring the settlement and the floor; its exact callback drops T from `fencedTransfers`. |

A returning protected identity is frozen by its tombstone. Each settlement uses one of 64 tombstones;
when full it refuses. Other floor conflicts (two protected copies, epoch regression, a stale copy at an
end) have no online exit in v1.8 and need offline repair.

**Tests and cuts.** A0: the fence over all six actions × {no floor, matching floor, other transfer's floor,
tombstone, pending}, and the motion predicate. A1, extending R2: floor, destination `/kill`ed, restart;
T is FENCED, no ticket for T in 1,200 ticks, warps of an unrelated station and of T's station after the
descent window succeed, diagnostics list T; settle-floor, then warp. S2: each O1 to O4 boundary cut on
both sides, then two restarts.

## 3. P3: assembly and disassembly recovery

**Admission.** `RocketManager.escrowAdmissionAllowed(rocket)` = `RocketTransferService.journalAllows`
(today's body of `resourceMutationAllowed`, `RocketTransferService.java:400-410`, without the coordinator)
**and** the assembly-journal check of `RocketManager.java:96-98` (journal operational, no
`hasPendingRecovery`, `RocketTransactionExecutor.java:73-80`). The coordinator calls it, then applies its
own same-direction chaining rule; its own records are the only exception.

**Fence.** `RocketTransactionRecoveryService.recoverLoaded` (`:106-127`) asks the coordinator before
`recoverBlocks` or `recoverEntity` can call `removeRocket` (`:181`, `:204`).
- ASSEMBLY record: FENCE if a rocket it would remove (BLOCKS authority, or a non-keeper duplicate) has
  markers or a nonzero epoch, or if an unretired record, floor or tombstone names the logical ID. A
  nonzero epoch means admission passed while the assembly journal held no record for that region, so
  the assembly had finished in memory; the disk record is a lagging autosave (the transaction journal
  has no explicit flush, `RocketTransactionSavedData.java:29,121-148`). Removal would discard credit (M3).
- DISASSEMBLY record: FENCE only for unretired markers, records or tombstones. Disassembly is admitted
  only when idle (`RocketDisassemblyService.java:115`), and D1 accounts for the resources.

A fenced transaction joins an in-session `fencedTransactions` set. `recoverOne` (`:42-89`) skips it
without calling `recoverLoaded` and without a CONFLICT result, so `RocketManager.java:231-234` stops
logging it every tick; one WARN per transition (`ARCE_ROCKET_RECOVERY_FENCED`), re-evaluation on section
2's triggers. The record stays, so `hasPendingRecovery` keeps that rocket's resources, disassembly and
admission refused; assembly records still do not block warp (ADR-044 section 5). Exit: offline repair.

**Tests and cuts.** A1: M3's stalled rollback refuses the fill; a stale assembly record over a rocket with
epoch 1 is FENCED, the rocket keeps 1,000 mB, no per-tick log; an idle epoch-1 rocket still
disassembles. S2: after an epoch-1 L2, stop with the stale record on disk; two restarts keep both.

<div style="page-break-after: always;"></div>

## 4. P4: guard layers and the A1 interlock matrix

**Placement.** `RocketTransferService.resourceMutationAllowed` becomes `journalAllows(rocket) &&
gate.rocketState(rocket) == IDLE`. All rocket layers reach it: `RocketFlightService.java:598-600`,
`RocketManager.java:94-99`, `RocketRuntime.java:45-47` (kept), `RocketDisassemblyService.java:115`, and
`prepareLaunch` (`RocketTransferService.java:62`) for player, admin, station and pad launches
(`RocketFlightService.java:311,314-360`). IDLE means no unretired record or marker names the rocket, its
endpoint section is valid and no tombstone names it; a floor alone is not a lock. The gate reads
coordinator state and never creates or dirties the ledger. Defence in depth: `RocketEntity.updateFlightData`
throws if the resource signature would change while the rocket is not IDLE, outside the coordinator's
apply scope; `RocketFlightService.board` refuses before writing the seat manifest.

| # | Entry point (refused, no change) | Where |
|---|---|---|
| 1 | Bucket or canister hand exchange | `RocketPropulsionInteraction.java:31` |
| 2 | ADR-027 `fuel_loader` target and eligibility | `FuelLoaderBlockEntity.java:128-134` |
| 3 | `/arce rocket release-test refuel` | `RocketCommands.java:222` |
| 4-7 | Player launch (C2S); `requestAdminFlight`, `requestAdminStationFlight`, `requestAdminPadFlight`; release-test `launch`, `launch-station`, `launch-surface` | `prepareLaunch` `:62` |
| 8-9 | Disassembly request and fuel quote; confirm; release-test `disassemble` | `RocketDisassemblyService.java:33,59-75,96,115,146` |
| 10 | Release-test `stage-recovery` (writes an assembly record over the rocket) | new check, `RocketCommands.java:430` |
| 11 | `/arce rocket recover` and automatic transfer and assembly recovery | sections 2 and 3 |
| 12 | Board | new check, `RocketFlightService.board` |
| 13 | `/ride`, forced remount, reconnect `movePassenger` | `EntityMountEvent` cancelled |
| 14-16 | `/tp`; `/spreadplayers`; dimension travel | `EntityTeleportEvent.TeleportCommand`, `.SpreadPlayersCommand`, `EntityTravelToDimensionEvent` cancelled |
| 17 | `/kill` | `RocketEntity.kill()` override refuses while not IDLE |
| 18 | `/data merge` or `modify` on the rocket | a live load (`isAddedToWorld`) never replaces the escrow section, and while not IDLE also keeps payload and resources; a position change gives RELOCATED_ENDPOINT |
| 19-21 | Test endpoint BE: player break; explosion; `/data merge block` | `BlockEvent.BreakEvent` cancelled; position removed in `ExplosionEvent.Detonate`; live `load` keeps the section |

The matrix is one GameTest class with one test per row. It uses W11b1's test endpoint and test hook to
hold one PREPARED record, and snapshots the rocket's entity-tag digest, flight data, position,
passengers, both journals and the ledger. It invokes the entry point as owner (and operator where only
an operator can), asserts the refusal code and unchanged snapshots, then retires the record through the
test L2 and asserts the same call succeeds, proving the refusal came from the escrow. A0 adds the pure
predicate with an escrow-state argument. Loader capability, menu and rebind refusals are W11b3's.

## 5. P5: the Lows against W11b2

- **L3.** Admission requires `flightData.passengers().assignments().isEmpty()`, `getPassengers().isEmpty()`
  and `!isPassenger()`. MISSING requires `getEntity(uuid) == null` while `areEntitiesLoaded(chunk)` and
  `isPositionEntityTicking(pos)` (section 0), observed at preview and again at confirm at least 200
  ticks later; otherwise WAITING_UNLOADED. `abandon` and `settle-floor` use it; `abandon`'s O2 also
  checkpoints each MISSING endpoint's absence as in section 2.
- **L4.** Preview and audit show the signed net change per Fluid and endpoint, so gain or duplication is
  as visible as loss. Status shows tombstones used of 64; when full, `abandon` and `settle-floor`
  refuse. RELOCATED_ENDPOINT has no online exit in v1.8 (`accept-current` keeps the fixed reference;
  `abandon` needs MISSING); offline repair only.
- **L5.** The residual is reworded as its own risk entry (section 7). Bypasses: `/data` on both endpoint
  kinds is refused (rows 18, 21) and `/spreadplayers` cancelled (row 15; vanilla honours it). `/setblock`,
  `/fill`, `discard()` and raw or third-party replacement stay detection-only (MISSING or INVALID, then
  REPAIR). The startup hint at `BetaWorldMigrationEvents.java:33` changes: when the refused file is the
  ledger, or a ledger exists, it names the whole-world offline procedure and says restoring single data
  files, including the migration backup, is unsafe. The risk entry adds capacity exhaustion and the warp
  effect.
- **L6.** A W11b1 and W11b3 benchmark item. W11b2 adds no barrier batching; it reruns W11b1's IOWorker
  queue-latency benchmark (vanilla chunk-load latency during barriers, `sync-chunk-writes` true and
  false) once on the integrated source.

## 6. The rest of the W11b2 row

| Item | W11b2 implements | Tests | S2 cuts |
|---|---|---|---|
| Rocket schema 3 | `ENTITY_SCHEMA_VERSION` 3 (`RocketEntity.java:51`); key set is schema 2's plus `resource_escrow` (`:242-244`), schema 1, at most 1 KiB: ledger UUID, epoch, at most 8 markers. Schema 2 loads as epoch 0 without markers and saves as 3. The bound at `:231-233` gains 1 KiB; a payload that no longer fits is preserved and quarantined, never truncated. An invalid section keeps the entity loaded, quarantines the endpoint (INVALID_ENDPOINT) and writes the raw section back. Pre-W11b2 binaries keep schema 3 verbatim, even above their bound (`:231-236,248-253`; `RocketNativeResources.preserve` never truncates). A transferred entity (`initializeTransferred`) is a new endpoint identity at epoch 0; launch needs no markers. | A0 codec at each bound; A1 schema-2 fixtures for both propulsion modes | G1: S1 upgrade, two idempotent restarts, the v1.7.0 jar preserves a schema-3 rocket byte for byte; S2 stop before and after the first schema-3 save |
| Mount and movement interlocks | Section 4 rows 12-18; detection on tick, registration and save compares Level, chunk and position with the bound reference (RELOCATED_ENDPOINT, no adoption) | Matrix rows; A1 detection through a test-hook `setPos` | G2 is A1 and S1 only (in-session) |
| Floors and reservations | Admission reserves a key only for a named transfer `(L,T)`; a NONE guard reserves nothing and L2 installs no floor, since a pre-credit record could then appear only through a per-file rollback. L2 turns `(L,T)` reservations into coalesced floors. Online collection: a floor is collectable once an acknowledged journal snapshot lacks T; the next ledger generation drops it, and start applies the same rule. Any later record, even with a replayed ID, is built from current state. Live floors are thus bounded by journal records (at most 64). | A0 union and collection, including 255 plus two requests; A1 `replace(T, T2)` and `releaseLandedReservation` collect; a failed ledger write keeps the floor | R1 as W11a section 7; CF1 collection staged, replaced, callback; R2 and R3 with real rockets |
| Settlement and tombstones | `/arce escrow status`, `inspect`, `retry`, `accept-current`, `abandon`, `settle-floor`: permission 2, preview and confirm digest (at most 512 bytes), whole connected component (at most 64), audit of actor, IDs, quantities and net change; O1 to O4 as W11a section 5. Tombstones: at most 64, not collected in v1.8; a returning identity is frozen at registration. | A0 state machine; A1 refusals (unloaded, not MISSING, oversize intent, tombstones full, chains) | O1-O4 both sides for accept, abandon, settle-floor and chains; O-veto; two restarts |
| Pre-start hand-off | W11b1 ships RESOURCE_ESCROW validation, lazy creation and G1 root tests (C3). W11b2 adds the section-1 policy entry and the L5 hint, and keeps admission disabled; only test hooks create a ledger. No `ManagedSavedDataType` or migration-chain change. | A1 hint text; a production-path world creates no ledger | G1 rerun on the integrated source |
| Risk entry and ADR note | Section 7; Root adds both before the protections merge (AGENTS.md 3.3). | — | — |

Fixture as W11a section 7 (5,000 mB test endpoint, empty 2,000 mB mono bank, 1,000 mB per operation).
S2 uses W11b1's test endpoint as the loader stand-in, a real rocket and integrated gated probes, and
repeats K0-K10, C1 and C2 with the rocket endpoint.

<div style="page-break-after: always;"></div>

## 7. Proposed text for Root

**Risk entry A, D3-A escrow protection scope and availability** (new ID; it extends the startup entry
Root adds for W11b1). A future or invalid escrow root refuses whole-world startup and is preserved. An
invalid endpoint section quarantines that rocket or block entity and its connected records until
settlement or offline repair. An unserializable endpoint block-entity root reuses `GuardedChunkSaves`:
the whole terrain chunk, and after 256 denial coordinates saturate, every later terrain save in that
ServerLevel, until the Level closes, with no production online reset; entity storage and other files
may still save, so stores can diverge. Unresolved records hold the shared capacity (64 per server, 8
per rocket, 1 per block entity); when it is exhausted every new admission is refused. A fenced transfer
record blocks warps of the station regions it overlaps while in a moving phase, never every warp. A
fenced assembly or disassembly record locks that rocket's resources and disassembly. Tombstones (64)
are not collected in v1.8; when full, `abandon` and `settle-floor` refuse. The coordinator logs one
line per state change and per backoff transition; retries and transitions have no lifetime cap, nor do
the inherited EventBus/ChunkMap, EntityStorage, IOWorker and force-error logs. RELOCATED_ENDPOINT,
fenced assembly records, veto stalls and other floor conflicts need the offline whole-world procedure
(W11A-DESIGN section 5). Restoring individual data files, including the migration backup, is a per-file
rollback that can revive transfer records or drop floors; restore whole-world backups only. This
registers mechanics; it is not acceptance of R-021 or of a new save veto.

**Risk entry B, storage faults the mod cannot observe after confirmation** (separate new ID). S2 covers
stopped-process ordering with successful forces. Outside it, like OS crash and power loss, are
environmental storage faults the mod cannot observe: a failed region-cache eviction close, which no
store future reports, and any later write that corrupts or replaces a confirmed record. The window
covers every write after confirmation, not only after L2. Code that calls `IOWorker`,
`RegionFileStorage` or `ChunkStorage.write` directly bypasses the Save-event and entity-decorator
monitor. An unreadable record makes vanilla regenerate or omit that chunk's contents after the escrow
record has retired: loss with no escrow diagnosis left. Whole-world backups mitigate it; W11b1's E3
diagnostic separates it from failures that reach the own store.

**ADR-065 implementation note, "W11b2: D3-A escrow rocket integration".** This note documents
protection mechanics; it changes no decision and accepts no risk (entries A and B). The transfer journal
is written only by the checked writer (stage, force, decoded readback, atomic replace) on the server
thread for its boundary flushes, autosave and stop; a failed write keeps the old file and the dirty
flag; schema, fields, limits and checksums are unchanged. Escrow admission requires an acknowledged
journal snapshot holding no record for the rocket or only its settled landed reservation; an absent
journal is written once as an empty schema-2 root on first need. Transfer, assembly and disassembly
recovery consult the escrow first and are fenced instead of discarding, rebuilding or electing over a
protected rocket. A fenced transfer record is classified under ADR-044 section 5, blocks only
overlapping station regions in moving phases and is skipped without chunk tickets. The save-protection
scope, durations, log volume and offline procedure are those of entry A. Restarting alone is not
repair. Native forced-stop evidence and owner acceptance remain separate.

**ADR-044 implementation note (optional).** A transfer record fenced by the D3-A escrow is classified for
rule 2; rules 3 and 4 apply unchanged, a fenced PREPARED record counting as not returned to a
stationary source.

## 8. Dependencies, choices for review, exclusions

W11b1 must export: the SERVER_THREAD_CHECKED policy mode accepting the journal bound (C6); a canonical
sorted-key tag digest; coordinator registration hooks for BE load and entity join and leave; barrier
readback of decoded entity records (for the absence predicate); the test endpoint and gated probes (C4).
W11b2 adds the settlement kind `TRANSFER_FLOOR_RETIRE` and the REPAIR reason JOURNAL_GUARD_MISMATCH to
the schema-1 codec. No released build writes a ledger before v1.8.0, so this is not a migration.

Choices the reviewer should confirm: online floor collection replaces W11a's start-only rule, enabled by
the checked journal; NONE guards create no floor; settle-floor adds three ledger writes and one journal
write; the assembly fence exits only offline; and the bound benchmark may hit the stop rule.

Not in W11b2: the loader, linker, production admission and loader capability, menu and rebind refusals
(W11b3); cargo (W11c onwards). Remaining risks: unmeasured checked-journal cost at the bound, entity
schema-3 fixtures on real old worlds, and the residuals in entries A and B. No current-version Required
Gate is met by this note; next is one independent read-only review, then W11b2 within v1.8.
