# W11a2: D3-A escrow/receipt coordinator, revised design

Date: 2026-10-12. Status: design for independent review, not implementation or Gate evidence.
Base: `731d3c796606e5cb90d3ea01ed992bc2c636df76`, including shared base `19da6240`.
This supersedes the round-1 contract at `8e767cd6` and its factual/proof claims in `W11A.md`.
Authority: ADR-065 sections 4, 8, 10 and 11.2; Root's decisions in TASK-W11A2.
Only this design and `W11A2.md` change; no code/tests, ADR/risk/status/ledger edits or cargo/item/bay
implementation. Page breaks divide the six-page design. All W11b work remains planned.

## 0. Checked storage facts

Java 17 `javap -p -c` checked the mapped Forge 47.4.10 jar in the Gradle cache:
`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
Static checks only, not native fault-cut results.

| Class and method | Consequence |
|---|---|
| `RegionFile.write/writeToExternalFile/flush/close` | Inline sectors precede header; external commit follows header without explicit payload force. Flush forces writer channel; healthy close pads/forces/closes. Reject `.mcc`. |
| `RegionFileStorage.getRegionFile/flush` | 256-entry cache; eviction removes before close; flush forces the whole remaining cache. |
| `IOWorker.store`, `lambda$store$3`, `storePendingChunk/runStore/synchronize`, `lambda$synchronize$7/$8` | Pending replacement shares its result future; removal precedes writing; failure completes exceptionally. Synchronization awaits current pending futures, then forces; no completed-error history. |
| `ChunkMap.save`, `ChunkStorage.write/flushWorker` | Dirty clears before serialization/Save event; write discards future; flushWorker joins. Event is not proof. |
| `PersistentEntitySectionManager.storeChunkSections/areEntitiesLoaded`, `EntityStorage.storeEntities/flush` | PENDING refuses; nonempty FRESH initiates load/refuses. Complete saveable entities precede consumer. EntityStorage writes DataVersion/Position/Entities, hides result. |
| `Entity.shouldBeSaved/save/startRiding(Entity, boolean)` | Riding entities are not standalone saved authorities; single-player predicate is virtual. Forced mount skips canAddPassenger, not Forge's check. |
| `TeleportCommand.performTeleport`, `RideCommand.mount`, `Entity.changeDimension`, `ForgeEventFactory.canMountEntity`, `ForgeHooks.onTravelToDimension` | Cancellable `/tp`, mount and dimension events; `/ride` uses forced mounting. |
| `MinecraftServer.runServer/stopServer`, `ServerLevel.close`, `ServerLifecycleHooks.handleServerStopping/Stopped`, `SavedData.save(File)` | Stopping precedes storage close attempts; stopped follows caught failures too. Ordinary SavedData catches IO/clears dirty. Neither is proof. |

`RocketEntity.hasExactlyOnePlayerPassenger()` returns false: rockets stay entity-chunk-owned.
No occupants is conservative policy, not a player-file requirement; also require
`!rocket.isPassenger()`. ADR-073 only copies pending reads, not write errors or disk-reader locking.

## 1. Authority and bounded format

Overworld-owned `ResourceEscrowSavedData` has a server-lifetime coordinator, no static world collections.
File: `data/advancedrocketrycommunity_resource_escrow.dat`;
`ManagedSavedDataType.RESOURCE_ESCROW`, introduced v1.8.0, schema 1, epoch
`v1.8.0-resource-escrow`, explicitly no legacy schema. Future/invalid roots refuse world
start under existing managed-type validation, preserving the file. Invalid endpoint sections
block only that endpoint/associated records. Count queued, pending and uncertain operations:
<=64/server, <=8/rocket, <=1/BE. No eviction or normalization.

| Envelope | Frozen fields and limits |
|---|---|
| Root | `schema_version`, epoch, `ledger_id` UUID, nondecreasing `issued_through` and `writer_generation` longs; `records` <=64, `floors` <=256, `tombstones` <=64; total uncompressed <=1,048,576 bytes. |
| Operation | Schema 1; unique `op_id` UUID and positive `seq` <=issued watermark; owner UUID, nonnegative `created_tick`; kind; persisted phase PREPARED/STALLED/REPAIR/SETTLING_ACCEPT/SETTLING_ABANDON, optional reason; frozen payload/SHA-256, source/destination references and `transfer_guard`. <=12,288 bytes, depth 16, 512 nodes. |
| Payload | Admit only `FLUID_PROPULSION_FILL`: ASCII canonical Fluid ID <=128 characters, 1..1,000 mB, native tag within existing 8 KiB envelope. Reserve `FLUID_CARGO_LOAD/UNLOAD`, `ITEM_CARGO_LOAD/UNLOAD`, `BAY_DEPLOY`, never admit them here; item bound one stack <=4 KiB. |
| Endpoint reference | <=512 bytes; kind, Level ResourceLocation; BE position/type/device UUID (fresh per placement), or logical rocket/entity UUID/chunk; unit PRIMARY/OXIDIZER/TANK; before epoch, after=before+1, before/after signatures. No numeric dimension identity. |
| Transfer guard / floor | Exact `(logical_rocket_id, transfer_id)` or checked no-transfer state, acknowledged journal generation and record digest; protected entity identity, resource epoch/signature and highest sequence. Each floor <=1 KiB; no matching against a later independent transfer. |
| Endpoint section | `resource_escrow`, schema 1: ledger UUID, resource epoch, markers `{op_id,seq,role,epoch_after}`; rocket <=1 KiB/8 markers, BE <=256 bytes/1 marker. Entity schema 3; schema 2 migrates to epoch 0/no markers; older binaries preserve future roots. Loader schema 1 <=32 KiB/depth 20/2,048 nodes; loot excludes escrow identity/markers. |
| Settlement / tombstone | Settlement intent fits the operation envelope and freezes chosen endpoint resource states/digests. Tombstone <=2 KiB: retired component/op IDs, missing endpoint identities, transfer guards, chosen survivor digests, payload amount/hash and audited loss; returns remain quarantined. No online tombstone collection in v1.8. |

Rocket signatures retain `arce-escrow-rocket-1:`, logical ID, snapshot content hash and
`RocketFuelState.resourceSignature()` including propulsion revision; loader signatures retain
`arce-escrow-fluid-tank-1:`, device UUID and `RocketNativeResources.signature` of native Fluid.
Admission validates total bytes, including worst-case retirement growth and reservations:
individual maxima need not all fit simultaneously. Refuse overflow, unknown fields/kinds and
noncanonical native values without truncation.

<div style="page-break-after: always;"></div>

## 2. One writer, exact acknowledgements

Reuse `CheckedSavedDataFile.replace` and station checked-outcome handling. A **per-type writer
policy** replaces AtomicSavedData's hardcoded allowlist/committer without changing other types.
Autosave/explicit commits use one `EscrowLedgerWriter`: one immutable <=1 MiB snapshot in flight,
one successor, <=64 referenced requests. Server thread captures/validates; background writer
encodes/stages/forces/readbacks/atomically replaces, forcing directory where supported
(POSIX; Windows omission documented), no fallback. Completion `(generation,digest,outcome)`
posts to server thread; exact snapshot acknowledgement only, L1 before L2. Older callbacks
cannot clear newer dirty state; autosave cannot implicitly apply queued admissions.

Definite pre-replace L1 failure cancels unapplied admissions/reservations (`LEDGER_WRITE_FAILED`).
Replace-attempt failure checks bounded target readback; unknown outcome retains exact candidate,
locks/reservations until resolved/rewritten before dependent generations. Unload cannot cancel
unknown L1; acknowledged L1 waits if endpoints unload. Never restore old snapshots. L2 failure
retains locks. Three unresolved failures -> LEDGER_UNAVAILABLE, admission refused, retry/200 ticks;
exact acknowledgement clears it. Restart uses validated target, never `.arce-pending`.

## 3. Normal transaction and endpoint proof

| Boundary | Action and acknowledgement | Forced-stop result |
|---|---|---|
| Admit / K0 | Pure validation, capacity reservation, lock endpoints, queue immutable preparation; no resource change. | No L1: discard preparation, resources unchanged. |
| L1 / K1-K2 | Writer durably adds PREPARED records, frozen guards/reservation keys and advances watermark. Group publication occurs only after exact acknowledgement. | Before replace: old root. After replace, even before callback: recover those records. |
| Apply / K3 | Server-thread non-reentrant publication of debit, credit, epochs and markers together. Partial interruption is recovered from L1; no release. | Each endpoint is BEFORE or an applied prefix. |
| Own store / K4-K5 | Serialize exact loaded endpoint chunks on server thread; submit through their existing `IOWorker.store`; retain each future. | One side written: L1 rolls the other forward. |
| Force / K6 | After successful own-store future, `synchronize(true)` on that same writer, asynchronously. No routine join. | Unproved record remains; restart repeats proof. |
| Readback / K7 | After force, bounded disk read, stable header/record and generation, identities, marker prefix/frontier and signature. Server-thread validation issues CONFIRMED token. | L1 remains until checked L2. |
| L2 / K8-K9 | Checked ledger snapshot removes only jointly confirmed records and converts reservation to floor where needed. | Staged only: replay. Replaced: no replay; stale markers are retired. |
| Release / K10 | Only exact L2 acknowledgement drops retired markers in RAM and unlocks endpoints with no other pending record. Never reset epochs. | Ordinary resources remain as checkpointed. |

**Terrain:** exact current FULL chunk, no loading; set unsaved. `ChunkSerializer.write`, same
Forge Save protections, detached validated tag -> own `IOWorker.store` (AT `ChunkStorage.worker`).
Preserve POI/full-type/legacy-index bookkeeping. Never serialize a stale ChunkAccess. Any
serialization/event/false/store/force failure restores current matching chunk's dirty flag
on server thread; never clear newer dirtiness or bypass a veto.

**Entities:** AT `ServerLevel.entityManager`, `storeChunkSections`, writable `permanentStorage`,
`EntityStorage.worker/emptyChunks`. One Level-scoped `EntityPersistentStorage` decorator
delegates ordinary load/store/flush/close. Barrier `storeChunkSections(chunk, e -> {})` captures
complete saveable ChunkEntities; serialize vanilla envelope using Entity.save, submit to the
**original** worker, retain future/update empty bookkeeping. No second/replacement IOWorker.
Check `areEntitiesLoaded` first; false/no capture cannot acknowledge or load. Serialization
failure must not omit a rocket; bounds/errors stall the barrier.

**Generations:** terrain Save attempts and decorator stores (even empty) increment monitored
chunk generation; extra attempts conservatively invalidate. A newer observed save between own
store and readback validation restarts store -> force -> readback. Pending replacement shares
the retained future, exposing failure. Compare slot location/timestamp, length/compression and
file identity before/after complete record read; require stable generation, inline data.
`AnvilRecordReader` generalizes precision reader: 1 MiB compressed/4 MiB decoded.

The proof confirms a **contiguous applied prefix**, not one operation's obsolete standalone
after-hash, and compares it to the frozen live frontier. Freeze frontier/markers from capture
through L2 (or failed-attempt cancellation); no chained apply may alter it. Saves/unloads serialize the same
protected frontier. Unload is not proof: await loaded registration/new own store. Known failure
before L2 invalidates the token; L2 cannot authorize an unproved endpoint.

Residual, exactly: **a vanilla writer failure the mod cannot observe**, such as a failed eviction
close or a later write that corrupts the chunk after confirmation. It is outside S2, in the
same class as the per-file rollback ADR-065 section 8 already excludes; W11b2 must register it.
This does not select ADR-027/054's ordinary unbarriered-transfer residual. No new Mixin is
needed for the specified own-store/event/decorator contract. If native checks require another
injection, stop and report its class/method/injection and evidence for Root's owner ADR.

<div style="page-break-after: always;"></div>

## 4. Recovery, floors and one transition table

BE-load/entity-add registration validates ledger identity before insertion; copies cannot
replace genuine registration. Duplicate/relocated UUIDs stay locked. Rotating queue classifies
<=8 records/tick, ascending sequence **per endpoint**; skip unloaded/manual conflicts fairly.

Markers form a contiguous epoch prefix of endpoint records (global seq need not be adjacent).
BEFORE: exact before epoch/signature, no next marker. AFTER: complete prefix/highest frontier
epoch/signature. Validate chain before mutation; drop retired markers without decreasing epochs.
BOTH BEFORE rolls both forward; mixed rolls BEFORE only; BOTH AFTER runs barrier. OTHER never
permits overwrite. Partial retirement requires proof at both endpoints, retains remaining
locks/prefixes, updates floor in same L2. Group publication is server-thread-only.

| Reason / state | Restart and transition | May automatically release? |
|---|---|---|
| PREPARED/APPLIED/CONFIRMING/CONFIRMED | PREPARED persisted; later normal phases transient. Reclassify and repeat proof after restart. | Only successful L2. |
| WAITING_UNLOADED | Transient; recheck exact loaded identities on load, without tickets. | No; resumes normal work. |
| BARRIER_STALLED: observed IO/force/unstable header/superseding save | Persist STALLED/reason after three failures; backoff 100, 400, then 1,200 ticks. Restart retains reason, schedules fresh proof; successful proof clears it. | Yes, through normal L2 only. |
| BARRIER_STALLED: veto/external/bounds | Same persisted STALLED; retries never bypass protection. Exact supported save/readback can clear; Level-lifetime veto requires offline repair. | Only after valid proof. |
| LEDGER_UNAVAILABLE | Runtime writer gate; disk phase still authoritative after restart. Keep uncertain generations/reservations until exact outcome resolves. | Resume after exact acknowledgement, not an IO timeout. |
| MISSING_ENDPOINT, OTHER_STATE, FOREIGN_MARKER, LEDGER_REPLACED, LEDGER_BEHIND, INVALID_ENDPOINT, RELOCATED_ENDPOINT, FLOOR_CONFLICT | Persist REPAIR/reason when writable (otherwise retain runtime lock); survives restart. No automatic overwrite/retirement, even if endpoint reappears. | No; audited settlement or offline repair. |
| SETTLING_ACCEPT / SETTLING_ABANDON | Persist chosen settlement intent; restart resumes it, never ordinary roll-forward. | Only its checkpoint and retirement acknowledgements. |

Orphan marker: different/missing ledger -> LEDGER_REPLACED; seq above watermark -> LEDGER_BEHIND;
otherwise remove marker, keep resources/epoch, subject to identity/tombstone checks. Retired
markers cannot prove former endpoint identity. UUID/watermark cannot detect every partial
rollback after marker removal; restore whole-world backups only.

**Journal prerequisite:** checked-durable ROCKET_TRANSFERS snapshot, not ordinary flush.
Freeze generation/digest and settled COMMITTED identity or positively checked no-transfer state.
RAM phase, absent/blocked journal or unknown outcome -> TRANSFER_JOURNAL_UNPROVED, no invented
authority. Preserve journal schema/limits/checksums unless ADR-065 separately requires migration.

**Reservations:** before L1, union durable floors + queued/in-flight/unknown-L1 keys <=256,
coalescing rocket/transfer. Persist keys/guards in L1 for restart reconstruction. Definite
pre-L1 cancellation releases its reference; unknown outcome never does. L2 converts to floor,
retaining shared references. Reserve byte growth; ESCROW_FULL before mutation. Status: used,
reserved (excluding already-used keys), tombstones/bytes. At 255 only one new key enters;
outstanding work can finish.

**Recovery fence:** every discard/overwrite/rebind/materialization first consults records,
tombstones and that transfer's floor:
KEEP_SOURCE, REMOVE_DESTINATION_KEEP_SOURCE, KEEP/REMOVE/REBUILD_DESTINATION, REBUILD_SOURCE,
duplicate election/reconnect/remount. Retain protected authority only after identity/resource
generation validation; never overwrite with arrival resources. Otherwise FLOOR_CONFLICT,
including stale PREPARED/two-present-entity cases. Later independent transfers do not match.
Start-only collection requires validated journal and proved generation retirement/checked
durable removal, not absent/blocked journal or RAM removal. Long uptime exhausts 256 slots;
validated restart can reclaim retired keys. No online collection/quota eviction in v1.8.

<div style="page-break-after: always;"></div>

## 5. Interlocks, durable operator settlement and recovery procedure

Admission: operational coordinator, equal owners, bound FULL-loaded endpoints/entity sections,
stationary same-Level ASSEMBLED/FUELED/LANDED rocket within 6 blocks, no passengers/not riding,
checked journal/reserved capacity. Linker schema-1 <=1 KiB first-device UUID/Level/position;
second-click connected/non-FakePlayer/non-spectator actor, owner/role/<=8-block checks per ADR-065.
Logical binding, no nearest scan/client Entity ID. D-5: owner may log off after L1.

Both `RocketRuntime.resourceMutationAllowed` **and** lower
`RocketFlightService.resourceMutationAllowed` consult the coordinator. Pending/preparing/
settling rockets refuse hand exchange, ADR-027 loader/release probes, launch preparation,
countdown start/commit, flight/transfer, disassembly/quotes, resource materialization and
operator mutations. Pending loaders refuse capability/menu/container fill/drain, second
operation, rebind/owner change, ordinary player/explosion destruction before loot/replacement.
Same-direction chaining only, <=8/rocket and one/BE; opposite directions refuse. Tentative
credits cannot be consumed/withdrawn. Normal saving/unloading/display remains allowed.
Physical W01 hatch facades remain controller-owned world banks, not live captured-rocket
capabilities; later cargo must preserve that separation.

Cancel `EntityMountEvent` for protected rider/vehicle, forced boarding/remount and `/ride`;
guard production/reconnect callers too. Cancel `EntityTeleportEvent.TeleportCommand` and
`EntityTravelToDimensionEvent`. Bypassed relocation detected on tick/register/save invalidates
proof -> RELOCATED_ENDPOINT, no silent UUID adoption. `/setblock`, `/fill`, `/kill`, raw/third-party
replacement bypasses require repair. Never force-load endpoints.

**Commands:** permission 2; status/inspect reasons/capacity, no payload dumps; retry schedules
only retryable work. accept-current is limited to manual conflicts/stalls with all endpoints
available; abandon requires proven
MISSING in a loaded referenced chunk (not unloaded). Preview/confirm current digest (<=512-byte
input); audit actor/op IDs, quantities/hashes/loss. Settle entire connected pending component
(<=64 records), including chains, never an isolated dependent link.

| Settlement acknowledgement | Contract, including forced stop |
|---|---|
| O1: intent durable | Lock component; reserve tombstone/byte space; freeze exact live survivor resources, epochs, identities/markers and missing identities in SETTLING_ACCEPT/ABANDON records. Oversize intent refuses intact, requiring offline repair. Before O1 no choice exists; after O1 restart follows it, not normal replay. |
| O2: survivors checkpointed | Same own-store -> force -> stable readback barrier for **every survivor**, confirming the frozen chosen state/prefix. Nothing is refunded or created. A veto/error leaves everything locked and releases nothing. Before/after any individual endpoint acknowledgement restart repeats proof. |
| O3: retirement durable | One checked write retires component records, installs/updates floors and (abandon) tombstones, with actor/accounting digest. Before replace retain intent; after replace stale markers are retired, survivors remain checkpointed. |
| O4: unlock | Only exact O3 callback removes retired markers and unlocks survivors. A returning abandoned identity is locked by its durable tombstone, cannot expose/drop contents or enter recovery election; offline repair must account for it. |

Survivor differing from O1 -> manual conflict, no fallback/re-selection without durable intent.
Operator acceptance of inconsistent totals is audited, not conservation proof. Abandon records
loss, never reconstructs/refunds historic resources or bypasses floors.

**W11b2 risk entry/ADR-065 implementation note, before protection implementation/integration:** future/invalid escrow root
refuses **whole-world startup**, not online BLOCKED mode. Endpoint quarantine affects its object
and connected operations until validated settlement/offline repair. Unserializable BE roots
reuse save protection only with the documented scope: entire terrain chunk, and after 256
denial coordinates saturate, all later terrain saves in that ServerLevel; denial lasts until
Level close, no production online reset. Entity and other independent stores may still save.
Coordinator logs one bounded state change and one message per backoff transition; continuing
retries/transitions have **no lifetime cap**. Existing EventBus/ChunkMap, EntityStorage, IOWorker
and force-error logs also have **no lifetime cap**. Register the section-3 unobservable vanilla
writer residual separately; this is not acceptance of inherited R-021 or of a new save veto.

**Offline whole-world repair:** stop admissions and server; back up all Levels' region/entities/
POI, global data/journals/ledger, playerdata and metadata together with hashes. Work only on a
copy using a compatible binary/tool, retaining raw invalid files. Diagnose identities, marker
prefixes, live/disk quantities, journal guards and floors; repair malformed data or restore one
consistent whole-world backup, not delete escrow alone. Any intentional discard needs explicit
accounting and tombstones/floors preventing returning copies. Run managed-root/endpoint native
validation and conservation checks, then two save/restart cycles and a successful new barrier
on the copy. Compare raw stores and verify no unresolved conflicts or unintended emissions;
only then replace the complete world while stopped, retaining the untouched backup. No online
command repairs a Level-lifetime save veto.

<div style="page-break-after: always;"></div>

## 6. Budgets and shutdown

Keep ADR-065 section 4.1.1: <=64 committed resource operations/tick, fair device queue;
<=1 whole-snapshot reconstruction/tick, 16 MiB charged bytes/16,384 record visits with bounded
cursor for later cargo. Fuel requires no reconstruction; <=32 KiB charged/operation. Ledger
starts <=once/10 ticks, one writer; barriers <=once/Level/20 ticks, <=16 terrain+16 entity
chunks and <=32 disk records per batch, one bounded readback worker; reconciliation <=8/tick.
Serialize <=1 endpoint chunk/tick server-wide; bound detached capture memory at 16 MiB and
refuse oversize proof without truncating vanilla saves. Fair queues skip blocked work.

Benchmark separately: server-thread capture/validation/serialization/publication, background
stage/force/readback latency, queue age and allocations/GC. Exercise 64 competing records,
saturated 256-region caches in terrain **and** entities, eviction, autosave, unloaded/blocked
records and chained credits under docs/17 reference load. Region forcing is cache-wide.
Keep Xmx<=4 GiB, RSS<=5.5 GiB, mean MSPT<=25 ms, P95<=50 ms, P99<=100 ms, zero watchdog/OOM/
unexpected force-loading; use docs/17's existing spike/soak definitions. If costs fail, reduce
admission cadence or subdivide capture and rerun; never relax budgets/omit force/readback.

On `ServerStoppingEvent`, stop admission; drain queued ledger generations and post their exact
callbacks on the owning thread while storage remains open. Pump only this bounded completion
queue, never ordinary gameplay; do not start new applies or barriers during drain.
Shutdown autosaves only join frozen queued generations; after writer close they retain dirty
state without enqueueing writes or claiming acknowledgement. Unfinished barrier callbacks are discarded.
Resolve unknown writes conservatively; failure leaves durable records unretired. Join the
active writer before releasing world ownership (never race a new process against its staging
file). Stuck IO means shutdown has not completed; a hard stop recovers the durable root.
Do not require final endpoint barriers
to empty the ledger. After storage closes, submit no synchronization. `ServerStoppedEvent`
only releases service handles/records results; it never proves flush or retires work.

## 7. Fault cuts and evidence (planned, not run)

A0 enumerates reachable ledger/endpoint prefixes for one operation and two chained operations,
writer generations, reasons, floors/reservations and settlement. Assert conservation **or**
locked explicit repair/accounted operator loss, idempotence on two recoveries, and no tentative
withdrawal. A1 uses injectable storage/committer boundaries plus real GameTestServer files;
S2 uses a packaged dedicated server, gated `EscrowReleaseProbe` checkpoints, harness hard kill,
two restarts and bounded raw file inspection. Cut pauses freeze server ticks and relevant
writer gates so autosave cannot obscure the chosen boundary. Every normal/settlement durable
acknowledgement is cut on both sides, including after replacement before callback.

Fixture: A=5,000 mB, empty 2,000 mB mono bank, transfer=1,000 mB. Chains add B=5,000 mB and
another 1,000 mB. Control outcome A=4,000, bank=1,000; chain A=B=4,000, bank=2,000.

| Cuts | Method and required outcome |
|---|---|
| K0/K1 before L1, staged-only | A0/A1/S2: no mutation/replay; orphan staging ignored. |
| K2/K3 L1 replaced/callback, debit-before-credit and applied-unsaved | A0/A1/S2: roll exact BEFORE sides forward once; no exposure before L2. |
| K4/K5 source-only/destination-only forced | A1/S2: repair other side from L1 to control total; ordinary unload/reload never forces chunks. |
| K6/K7 before/after force, readback and CONFIRMED callback | A1/S2: record retained without L2, fresh barrier on restart. |
| K8/K9/K10 L2 staged/replaced/callback/release | A0/A1/S2: staged retains record; replaced retains checkpointed resources/floor; stale marker removal never decrements epoch. |
| C1/C2 chains: rocket prefix 1/B prefix 2; latest prefix proof; partial retirement; opposite-direction request | A0/A1/S2: control chain total, exact contiguous epochs, only jointly proved retirement; opposite direction refused. |
| E1 own store fails; pending superseding store external move fails | A1 injected IO + S2 writer gate: retained future fails; no L2/unlock even if later synchronize succeeds. |
| E2 newer save between store/force/readback; changing header/record; external `.mcc`; decoded bounds | A1/S2: invalidate generation/proof and repeat, or STALLED; never old after-hash acknowledgement. |
| E3 cache-eviction close/force fails | A1 distinguishes failure propagated to own store (no L2) from unrelated unobservable failure; S2 diagnostic reproduction records the latter as excluded residual, not a guarantee PASS. |
| E4 definite/unknown L1 and L2 outcomes; autosave/explicit overlap; stale writer callback | A0/A1/S2: exact-generation acknowledgement only, no early release, uncertain reservations retained. |
| E5 three barrier failures, IO recovery, stalled restart, two further restarts, healthy work behind blocked/unloaded work | A0/A1/S2: reason table and fairness; healthy work completes, manual conflicts remain locked. |
| R1 255 floors/two distinct requests; same-key coalescing; unknown L1 unload/restart; saturated 256 | A0/A1/S2: second distinct request refuses before mutation; reserved+used never exceeds 256; outstanding work retires. |
| R2 journal stale PREPARED with both entities present; KEEP_SOURCE/destructive source branch, duplicate election/reconnect | A1/S2: unproved admission refused; protected cut fixtures report FLOOR_CONFLICT without overwrite/delete/remount. |
| R3 ledger missing/replaced/ahead/older copy, endpoint missing/OTHER/foreign/relocated, retired marker | A0/A1/S2: locked repair where detectable; no historical snapshot fallback; document partial-rollback detection limit. |
| O1-O4 before/after intent, each survivor store/force/readback, retirement replace/callback and unlock; repeat for chains and abandon | A0/A1/S2: resume frozen settlement; no release before O3, checkpointed survivors after it; returning missing identity frozen by tombstone. |
| O-veto settlement with destination saved/source chunk vetoed | A1/S2: total cannot become available as 6,000 mB; no retirement/unlock; offline repair only. |
| G1 old entity schema 2, future/invalid root and endpoint, native save-veto/saturation | A0/A1/S1/S2: migration preserves resources; root refuses start unchanged; endpoint scope/log volume matches risk entry. |
| G2 occupied/riding rocket, forced production/reconnect mounting, `/ride`, `/tp`, dimension travel, bypass relocation | A1/S1: refuse or detect with locked repair; no silent rebinding. |
| G3 stop while L1/L2 queued/in-flight, unknown completion, failed vanilla close | A1/S2: drain exact writer or retain unresolved records; stopped event is never proof. |

S2 covers stopped-process ordering with successful forces, not hardware power loss/OS crash,
arbitrary per-file rollback or the explicitly named unobservable-writer residual. Evidence pins
source commit/JAR hash, commands, logs, cuts, resource counts and file hashes; no full source
archive. Proposed harness: `scripts/run_v180_escrow_forced_stop.py`, like the existing v1.0
transaction harness; gated `releaseTestHooks`/`escrowCheckpoint` flags, bounded pause marker
`ARCE_RELEASE_ESCROW_PAUSED cut=<id> op=<uuid>`. Fixtures are not delivered evidence/test counts.

<div style="page-break-after: always;"></div>

## 8. W11b implementation slices and subsequent scope

| Slice / dependency | Observable scope (all planned) | Tests/cuts required before moving on |
|---|---|---|
| W11b1: reviewed design | Coordinator/model, bounded codecs, per-type checked policy, immutable ledger writer, own-store barrier, AT/decorator, stable Anvil reader, test-only endpoints. **No production admission.** | A0 generation/prefix/reason/codec tests; A1 real-store/barrier tests; test-endpoint S2 K0-K10, C1/C2, E1-E5, G3; saturated-cache/main-thread vs IO benchmark. Independent persistence actual-diff review. |
| W11b2: W11b1 | Rocket schema-3 migration, both mutation layers, mount/movement interlocks, checked journal prerequisite, recovery fences, floors/reservations, operator settlement/tombstones, managed pre-start policy, risk entry and ADR-065 implementation note. **Admission stays disabled.** | A0/A1 reservation/settlement/recovery and old-world fixtures; S1 native upgrade/preservation; S2 R1-R3, O1-O4/O-veto, G1-G3 and repeated K/C cuts using integrated gated probes. One independent read-only persistence/recovery review before integration. |
| W11b3: W11b1+W11b2 reviewed/integrated | `fluid_fuel_loader`, rocket-binding half of linker, tank/menu/capabilities/loot/recipe/original art and translations; packaged S2 conservation evidence. | A0/A1 native Fluid round-trip/simulation/full/incompatible/stale/reentrant/owner and ambiguous-bank tests; all section-7 applicable cuts with production loader, two competing devices, old-world restart; S1 sidedness, V1/V2 UI/authority evidence as applicable, benchmarks. Enable admission only after required D3-A S2 cuts pass on the integrated source; Gate/owner authority unchanged. |

Fuel consumer keeps 16,000 mB tank/8 KiB native bound, <=1,000 mB/device/20 ticks and bank
room/role/catalog checks. Select the unique eligible typed bank; otherwise AMBIGUOUS_BANK,
no new bank-selection packet. It fixes W02 F3's precise refill half, not sub-container draining
(no propulsion unloader in ADR-065). Existing ADR-027 loader/hand two-store limits remain
explicitly outside D3; they still obey pending-resource interlocks.

Later v1.8 slices only: W11c cargo Fluid loaders/unloaders/pressurized-tank adapter with
snapshot cursor; W11d item cargo; W11e full linker/monitor, whose launch refuses ESCROW_PENDING.
No implementation here. Bay sketch: C17c requires a separately bounded schema-1 4 MiB receipt
store (<=4,096 total/128 owner/1 KiB each), checked registry/receipt acknowledgements and
source-generation/transfer/disassembly/decommission fences. Its receipt retirement contract
must be reviewed before admission; ordinary AtomicSavedData flush is not automatically proof.

## 9. Answers to the independent review

Review: `D:/GitHub/ARCE-Task-Evidence/root-queue/W11a-review/REVIEW.md`, SHA-256
`1cfcbb8f6d4406f4302ed73cd346f99f3429ce9cf4bffcae17d1fc107316b0fe`.
Root accepted all findings in TASK-W11A2; this document proposes their concrete implementation
contract, not a self-awarded review verdict.

| Finding | Revised answer |
|---|---|
| F1 | Sections 0/3/7: own retained store future, force before stable readback, observed-generation restart, contiguous frontier through L2, external refusal; exact unobservable vanilla writer residual and no extra Mixin. E1-E3 distinguish covered and excluded failures. |
| F2 | Sections 5/7: O1 durable choice, O2 every survivor checkpoint, O3 durable floor/tombstone retirement, O4 unlock; veto cannot release, chains settle together, returning abandoned identity stays frozen. |
| F3 | Section 4: checked journal admission plus all destructive/rebinding/materialization/reconnect actions fenced; R2 includes stale phase and two entities. |
| F4 | Sections 1/4/7: union capacity and byte reservation before L1, persisted guard keys, unknown outcomes retained, used/reserved status, validated start-only collection and uptime limitation. |
| F5 | Section 4: one STALLED-vs-REPAIR table; persisted reasons, automatic-clear rules and fair scheduling; E5 verifies recovery/restarts. |
| F6 | Sections 2/6: one off-thread immutable-snapshot writer, exact generations for autosave/explicit L1/L2, shutdown drain; saturated-cache benchmark separates tick cost from IO latency. |
| F7 | Sections 1/5: root refuses startup; endpoint scope separate; W11b2 risk/ADR note specifies chunk/Level veto, duration, uncapped inherited logs and whole-world offline recovery. |
| F8 | Sections 0/5: rocket override corrected; conservative no-occupant plus not-riding policy; Forge mount boundary and production/reconnect callers protected. |
| Other requested checks | Sections 2-7: both mutation layers; command/travel movement; no-op entity-store consumer and loaded check; deliberate dirty restoration; per-type writer policy; narrower rollback detection; stopped event not proof; prefix/group/partial retirement and foreign-registration rules. |
| D-1..D-5 | Binding half and exact checks retained; AT is wired on shared base `19da6240` (no new build line); no-occupant is policy; floors require F3/F4; offline owner after L1 allowed. |

Remaining risks are unmeasured native storage/performance, entity-schema fixtures, save-veto
availability and bounded floor/tombstone exhaustion. No current-version Required Gate is met
by this design. Next action is independent review, then W11b1 within v1.8; no later-version work.
