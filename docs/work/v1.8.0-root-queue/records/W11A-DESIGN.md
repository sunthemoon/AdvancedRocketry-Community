# W11a design: the D3-A escrow/receipt coordinator

Date: 2026-10-12. Base: `53d30f5a` on `codex/v1.8.0-w11a-d3-design`. Status: design for
independent review. It adds no code, test or ADR change. It specifies ADR-065 §10 (owner-confirmed
D3-A) for the §4.2 loaders and sketches the §8 satellite bay. W11b implements it after review.

## 0. Storage facts used

I checked these with `javap` against `forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`
(signatures and call chains only):

- `RegionFile.flush()` calls `FileChannel.force(true)`, and `close()` forces before closing.
  `RegionFile.write` writes new sectors, then the header, then frees the old sectors.
- `IOWorker.synchronize(flush)` stores all pending writes and, when `flush` is true, forces every
  cached region file. `ChunkStorage.flushWorker()` and `EntityStorage.flush(boolean)` `join()` it
  on the calling thread.
- `ChunkMap.save(ChunkAccess)` is private. It skips a chunk that is not unsaved, clears the flag,
  posts `ChunkDataEvent.Save`, and queues the tag with `IOWorker.store`.
- `ServerLevel.entityManager` and `PersistentEntitySectionManager.storeChunkSections` are private.
  The latter stores nothing while a chunk's entities are pending.
- `Entity.shouldBeSaved()` is false for a vehicle with exactly one player passenger; that rocket
  is saved in the player's file, not the entity chunk.
- A chunk record over 255 sectors goes to an external `.mcc` file that is never forced.

A `ChunkDataEvent.Save` therefore only shows that a tag was queued. This design never uses it as
evidence. W11b's barrier GameTests exercise these facts, and a Forge update must re-check them, as
ADR-073 requires for its Mixin.

## 1. The authority

**Decision.** A separately forced ledger is the durable authority for every pending transfer:
`data/advancedrocketrycommunity_resource_escrow.dat` in the overworld storage, new type
`ManagedSavedDataType.RESOURCE_ESCROW` (introduced `v1.8.0`, format epoch
`v1.8.0-resource-escrow`, root schema 1). Its record is the commit point. The endpoints (loader
block entity, rocket entity) only carry *markers* showing whether they applied an operation.

**Forcing.** `ResourceEscrowSavedData` extends `AtomicSavedData` (whose allowlist gains the new
type), so every write, autosave included, uses `CheckedSavedDataFile.replace`: stage, `force(true)`,
bounded readback, `ATOMIC_MOVE`. Its own `Committer` also forces the parent directory after the
move where the platform allows it (POSIX; skipped on Windows).

**Capacity.** At most 64 pending records per server, 8 per logical rocket and 1 per block entity.
A `REPAIR` record keeps its place until an operator resolves it and is never evicted. A full
ledger refuses admission with `ESCROW_FULL` and changes nothing.

**Cadence.** Writes are group commits on the server thread, at most one per 10 ticks while work
exists. Admitted operations wait in an in-memory queue outside the SavedData, so autosave cannot
write them early. An endpoint changes only after an explicit ledger write containing its record
returned normally; `AtomicSavedData.save(File)` swallows failures and never counts.

## 2. The record and its limits

| Root field | Type and bound |
|---|---|
| `schema_version` | int 1 with the format epoch |
| `ledger_id` | UUID, random at creation, never changes |
| `issued_through` | long ≥ 0, highest `seq` ever issued, never decreases |
| `records` | at most 64 records |
| `floors` | at most 256 `{logical_rocket_id, transfer_id, seq}` (§4) |

The ledger is at most 1,048,576 bytes uncompressed. A record (schema 1) is at most 12,288 bytes by
`RocketNbtSize`, depth 16, 512 nodes:

| Record field | Type and bound |
|---|---|
| `schema_version` | int 1 |
| `op_id` | UUID, unique, never reused |
| `seq` | long, unique, 1 ≤ `seq` ≤ `issued_through`, issued in admission order |
| `kind` | W11b admits only `FLUID_PROPULSION_FILL`; `FLUID_CARGO_LOAD`, `FLUID_CARGO_UNLOAD`, `ITEM_CARGO_LOAD`, `ITEM_CARGO_UNLOAD`, `BAY_DEPLOY` are reserved |
| `phase` | `PREPARED` or `REPAIR`, the only persisted phases |
| `repair_reason` | only with `REPAIR`, a §4 reason |
| `owner_id`, `created_tick` | UUID; long ≥ 0 (overworld game time) |
| `payload` | frozen native payload. Fluid: `id` (≤ 128 ASCII), `amount` 1..1,000 mB, optional `tag` within the existing 8 KiB bound. Item (reserved): one stack ≤ 4 KiB |
| `payload_sha256` | 64 lowercase hex |
| `source`, `destination` | endpoint references, ≤ 512 bytes each |

An endpoint reference holds `kind` (`BLOCK_ENTITY`, `ROCKET`; `SATELLITE_REGISTRY` reserved) and
`level`; for a block entity `pos`, `be_type` and `device_id` (regenerated on every placement); for
a rocket `logical_rocket_id`, `entity_id` and `chunk`; then `unit` (`PRIMARY`, `OXIDIZER` or
`TANK`), `epoch_before`, `epoch_after` (= before + 1), `before_sig` and `after_sig`.

Signatures cover resources only. Rocket: SHA-256 of `arce-escrow-rocket-1:`, the logical ID, the
snapshot content hash and `RocketFuelState.resourceSignature()` (descriptor, both banks with Fluid
ID, tag and amount, and the propulsion revision, which `updated()` raises by exactly one, so
roll-forward reproduces the after-signature). Loader: SHA-256 of `arce-escrow-fluid-tank-1:`, the
device ID and `RocketNativeResources.signature` of the tank's Fluid compound.

Each endpoint carries a `resource_escrow` section (schema 1; ≤ 1 KiB on a rocket, ≤ 256 bytes on a
block entity): `ledger_id`, `resource_epoch`, and `markers` (≤ 8 or ≤ 1) of
`{op_id, seq, role, epoch_after}`. On the rocket it is the one new key of **entity schema 3**;
schema 2 reads as epoch 0 without markers, and older binaries keep schema 3 as future data. The
new loader root (schema 1, ≤ 32 KiB, depth 20, 2,048 nodes) has it from the start; its loot
carrier excludes it. Any unknown field, schema, kind or out-of-range value preserves the ledger
verbatim and makes the coordinator `BLOCKED`: no admission, every marked endpoint locked, reason
shown by `/arce escrow status`. A bad endpoint section blocks only that endpoint.

## 3. Phases and barriers

In memory: `PREPARED` → `APPLIED` → `CONFIRMING` → `CONFIRMED` → retired, or `REPAIR`.

| Step | Action | Durable afterwards | Crash here (cut) |
|---|---|---|---|
| 1 Admit | §5 checks; pure preparation of payload, unit, epochs, signatures; lock both endpoints; queue | nothing | K0: no record; nothing to do |
| 2 L1 | At the slot, refuse and unlock records whose endpoints unloaded; one checked replace adds the rest and advances `issued_through` | records, watermark | K1, staged not moved: old ledger; the `.arce-pending` file is never read |
| 3 Apply | Same tick: debit tank, credit bank, add markers, raise epochs, mark both changed | as L1 | K2/K3: both before-state; both rolled forward |
| 4 Store | ≤ once per Level per 20 ticks: `setUnsaved(true)` + `ChunkMap.save` (loader chunk), `storeChunkSections` (rocket entity chunk), then `synchronize(false)` without `join` | unforced file writes | K4/K5: one side on disk; the other rolled forward |
| 5 Readback | On completion, a worker thread reads both records from `region/` and `entities/` | — | — |
| 6 Force | Only if step 5 saw both after-states with the marker: `synchronize(true)` | both, forced | K6: barrier repeats |
| 7 L2 | Mark `CONFIRMED`; the next slot removes the record (adding a floor if §4 needs one) | record gone | K7 staged: as K6. K8: stale markers dropped on load |
| 8 Release | After L2 returns: drop markers in memory, unlock; the credit becomes ordinary | next save | K9: nothing |

**Proof rule.** An endpoint is durable for an operation only when a readback saw its after-state
with that marker *before* a `synchronize(true)` that then completed. The force covers every page
dirty when it began, including the page read, and every later write still carries the marker,
which is dropped only at step 8. An endpoint that already unloaded skips step 4; its unload save
is queued and steps 5 and 6 apply. Steps 4 to 6 need six Access Transformer entries
(`ChunkMap.save`, `ChunkStorage.worker`, `ServerLevel.entityManager`,
`PersistentEntitySectionManager.storeChunkSections` and `permanentStorage`,
`EntityStorage.worker`). The reader is `PrecisionAssemblerPersistedChunkReader` generalized into
`persistence/AnvilRecordReader`, with its 1 MiB compressed and 4 MiB decoded limits.

**Failures.** If L1 fails before the move, its records are dropped, endpoints unlock and the
refusal is `LEDGER_WRITE_FAILED`. If a ledger write has an unknown outcome, the in-memory ledger is
a superset of any disk state, so the next slot rewrites it; nothing is applied or released until a
write returns, and after three failures the coordinator is `LEDGER_UNAVAILABLE` (no admission,
endpoints locked, one ERROR line, retry every 200 ticks). A barrier failure (vetoed save, `false`
from `ChunkMap.save`, IO error, readback mismatch or bound, external `.mcc`) keeps the record
`APPLIED` and locked with the in-memory state authoritative, marks the endpoint changed again and
retries after 100, 400, then every 1,200 ticks; after three it shows `REPAIR`
(`BARRIER_STALLED`) but keeps retrying. Nothing is ever rolled back.

## 4. Reconciliation on load

Endpoints register from `BlockEntity.onLoad` and `Entity.onAddedToWorld`. The coordinator is a
server-scoped service (no static world state) that loads the ledger before its first tick, then
classifies at most 8 records per tick in ascending `seq`, only once all their endpoints are
loaded. It never loads, tickets or forces a chunk. Endpoint E of record r is `BEFORE` (no marker,
epoch and signature equal the before values), `AFTER` (marker for r, epoch ≥ `epoch_after`,
signature equal to the after-signature of the highest-`seq` pending record it carries a marker
for), `UNLOADED`, `MISSING` (loaded but absent or another identity) or `OTHER` (anything else, such
as no marker with epoch ≥ `epoch_after`: the ledger is older than the endpoint).

| Source | Destination | Action |
|---|---|---|
| `BEFORE` | `BEFORE` | roll both forward, then the barrier |
| `AFTER` | `BEFORE` (or the reverse) | roll the `BEFORE` side forward, then the barrier |
| `AFTER` | `AFTER` | barrier |
| any `UNLOADED` | | wait; the loaded side stays locked |
| any `MISSING` / `OTHER` | | `REPAIR` (`MISSING_ENDPOINT` / `OTHER_STATE`); nothing changes |

Roll-forward is the only resolution once L1 is durable: the payload is in the ledger and each
locked endpoint is exactly before or after. With no abort path, a chained record never depends on
an undone one; records on one rocket reconcile in `seq` order. Every action is idempotent.

| Marker without its record | Action |
|---|---|
| `ledger_id` differs, or no ledger | `REPAIR` (`LEDGER_REPLACED`) |
| `seq` > `issued_through` | `REPAIR` (`LEDGER_BEHIND`) |
| `seq` ≤ `issued_through` | retired after proof: drop the marker, keep resources |
| the record names another identity (e.g. a creative NBT copy) | `REPAIR` (`FOREIGN_MARKER`) on the copy only |

**Retirement** happens only at step 7. A save event, an age rule, a clean stop or two restarts
never retire a record. After vanilla's final flushed save, `ServerStoppedEvent` runs one readback
and a final ledger write, so a clean stop normally leaves nothing pending (W11b verifies the order).

**Lost or unknown outcomes** keep resources: lost endpoint writes are replayed from the ledger, and
a lost or older ledger is caught by `ledger_id` and the watermark. Nothing restores an older
snapshot. Operators (permission 2, audited with the payload SHA-256) have `/arce escrow status`,
`inspect`, `retry`, `resolve <op> accept-current` (both endpoints loaded; drops markers and
retires, keeping both as they are) and `resolve <op> abandon` (only with a `MISSING` endpoint).
Neither command creates resources.

**Transfer floors.** A landed rocket keeps its `COMMITTED` transfer record until it flies or is
disassembled; if its entity were missing at restart, `REBUILD_DESTINATION` would rebuild the
arrival state, an older snapshot. When step 7 retires a record for such a rocket, the same write
adds a floor `{logical_rocket_id, transfer_id, seq}`. Before any `REBUILD_*`,
`RocketTransferRecoveryService` asks the coordinator; a matching floor or a pending record makes it
wait with `REPAIR` (`FLOOR_CONFLICT`). A floor matches only its `transfer_id` and is removed at the
first server start whose loaded journal lacks it. With 256 floors in use, operations needing a new
one get `ESCROW_FULL`.

## 5. Blocking

Admission needs: an operational coordinator; equal loader and rocket owners; the loader chunk FULL
and the rocket's entities loaded; an operational, stationary rocket in the same Level, within 6
blocks, in a fuel-accepting state, with **no passenger** (`ROCKET_OCCUPIED`, because of the
player-file rule in §0); `RocketManager.resourceMutationAllowed` apart from the coordinator's own
same-direction records; and room under §1.

| While a record names | Refused until step 8 |
|---|---|
| a rocket | everything behind `resourceMutationAllowed` (hand exchange, the ADR-027 `fuel_loader`, release-test refuelling, disassembly and its quotes, launch preparation); flight requests; countdown start and commit; transfer preparation; boarding; `REBUILD_*` recovery; an opposite-direction or ninth record; later, the monitor's automatic launch |
| a loader | tank fill and drain by capability, menu or container; rebinding; owner change; player and explosion removal, refused before the block changes with loot and replacement suppressed (ADR-019); a second record |
| neither | chunk and entity saving (never vetoed or deferred), unloading, display-only menus |

**Direction rule.** All pending records on one endpoint give it the same role, all credits or all
debits. Nobody can withdraw a tentative payload: credited fuel cannot be burned, drained or
materialized before step 8, since every consumer is refused above. Later unloaders hold their
credit in a non-extractable incoming slot until step 8 (ADR-054's incoming gate without its
save-observation rule). `/setblock`, `/fill`, `/kill`, raw `destroyBlock` and third-party movers
bypass the hooks and end in `REPAIR`. Rolling back one world file is outside S2 (ADR-065 §8).

## 6. Budgets

| Work | Bound |
|---|---|
| Admission | §4.1.1 fair queue: ≤ 64 committed operations per tick, round-robin by device; a fuel operation counts once |
| Snapshot reconstruction | ≤ 1 per tick, 16 MiB charged bytes, 16,384 visits. Fuel operations need none (banks live in flight data); cargo operations and their roll-forward use this limit and the resumable cursor |
| Bytes per fuel operation | ≤ 32 KiB charged to the 16 MiB counter |
| Ledger write | ≤ 1 per 10 ticks, ≤ 1 MiB, one force and readback |
| Barrier store | ≤ 1 per Level per 20 ticks, ≤ 16 terrain and 16 entity chunks, no `join` |
| Readback | ≤ 32 records per barrier, on one worker thread |
| Reconciliation | ≤ 8 records per tick |
| Logging | one line per record state change; repeats once per backoff step |

W11b reports p95/p99 tick cost of a 64-record ledger write and a 16+16-chunk barrier on the docs/17
reference against unchanged MSPT budgets. If over, it lowers the cadence; it never drops the force
or readback and never raises a budget.

## 7. S2 fault-cut plan

**A0.** The pure `EscrowCutModelTest` enumerates every durable-state combination the write order
allows (ledger before L1, after L1, after L2; each endpoint before, after with marker, released)
for one operation and for two chained ones, plus a rolled-back or replaced ledger, a missing
endpoint and a foreign marker. Fluid is conserved or the result is `REPAIR` with all resources
kept; reconciling twice equals once.

**A1.** GameTests capture ledger bytes and endpoint NBT at each cut through the ledger `Committer`
and an `EscrowFaultPlan` hook after each step, rebuild fresh endpoints and coordinator, and
reconcile. Barrier GameTests on real GameTestServer storage check that the reader finds the marker
and that a vetoed save confirms nothing.

**Native.** A dedicated server with `-Dadvancedrocketrycommunity.releaseTestHooks=true` and
`-Dadvancedrocketrycommunity.escrowCheckpoint=<cut>`. `EscrowReleaseProbe` (like
`RocketTransactionReleaseProbe`) performs that cut's durability action, logs
`ARCE_RELEASE_ESCROW_PAUSED cut=<cut> op=<uuid>` and sleeps on the server thread, so no autosave
runs. `scripts/run_v180_escrow_forced_stop.py` (like `run_v100_transaction_forced_stop.py`) kills
it hard, restarts twice (the second changes nothing) and reads the files with `NbtReader`.

Fixture: loader A with 5,000 mB `rocket_fuel`, a mono rocket with an empty 2,000 mB bank, one
1,000 mB operation; chained cases add loader B (5,000 mB) and a second operation.

| Cut | Produced by | Durable at kill (ledger / loader / rocket) | After restarts (loader / bank / ledger) |
|---|---|---|---|
| K0 admitted | A1; native pause before L1 | old / before / before | 5,000 / 0 / none |
| K1 L1 staged | A1 committer; native pause in it | old + pending file / before / before | 5,000 / 0 / none |
| K2, K3 L1 durable, applied unsaved | A1; native | `PREPARED` / before / before | 4,000 / 1,000 / retired |
| K4 loader only | native, loader chunk forced alone | `PREPARED` / after / before | 4,000 / 1,000 / retired |
| K5 rocket only | native, entity chunk forced alone | `PREPARED` / before / after | 4,000 / 1,000 / retired |
| K6, K7 both, L2 not done | native; A1 committer for K7 | `PREPARED` / after / after | 4,000 / 1,000 / retired |
| K8 L2 durable | native | none / marked / marked | 4,000 / 1,000 / none |
| K9 released | native control | none / after / after | unchanged |
| C1 rocket after op 1 only, B after op 2 | native | 2 × `PREPARED` / A, B after / op 1 | A 4,000, B 4,000 / 2,000 / retired |
| C2 loader and unloader on one rocket | A1 | — | second refused (direction rule) |
| F1 L1 fails before move | A1 | old | 5,000 / 0, `LEDGER_WRITE_FAILED` |
| F2 three unknown L1 outcomes | A1 | unknown | `LEDGER_UNAVAILABLE`; after restart as K2 |
| F3 loader chunk save vetoed | A1 | — | `APPLIED`, locked, `BARRIER_STALLED`; no rollback |
| F4 K2 ledger copy restored after K9 | native, harness swaps the file | `PREPARED` / released / released | `REPAIR` (`OTHER_STATE`); 4,000 / 1,000 kept |
| F5 ledger deleted at K6 | native | none / marked / marked | `REPAIR` (`LEDGER_REPLACED`); kept |
| F6 rocket killed while pending | A1 | — | `REPAIR` (`MISSING_ENDPOINT`); `abandon` keeps loader as is |
| F7 passenger or boarding | A1 | — | `ROCKET_OCCUPIED`; boarding refused |
| F8 floor conflict | A1 | — | recovery waits (`FLOOR_CONFLICT`), no rebuild |

S2 proves stopped-process ordering after forced writes, ADR-019's standard. Power loss and OS
crashes depend on the platform honouring `force` and directory metadata and are not claimed.

## 8. What W11b delivers, then later slices

W11b, commits in order: (1) pure model, codecs, classifier and the A0 test; (2)
`ResourceEscrowSavedData`, group commit and the pre-start migration entry; (3) Access Transformer,
`EscrowDurabilityBarrier`, `AnvilRecordReader` and barrier GameTests; (4) entity schema 3, §5
interlocks, floors, commands and translations, plus the AGENTS.md §3.3 risk entry (proposed R-023:
one record and its endpoints, or all escrow automation while `BLOCKED`; until a barrier succeeds
or an operator resolves it; one log line per state change; recovery by command or backup) and an
ADR-065 implementation note; (5) `fluid_fuel_loader`: block, block entity, 16,000 mB
`IFluidHandler` tank, menu, loot carrier, recipe and generated art, moving exact mB (≤ 1,000 mB per
device per 20 ticks, never above the bank's room) into the one bank whose role admits the Fluid
under the captured settings and current catalog (`AMBIGUOUS_BANK` when both do, so no new packet).
This resolves the refill half of W02 review F3; draining a sub-container balance stays a
documented limit, as ADR-065 §2 has no propulsion unloader. (6) S2 probe, harness and evidence.

| Later slice | Content | Coordinator use |
|---|---|---|
| W11c (C17a-03 part 1) | snapshot-carried cargo, §4.1.1 cursor and benchmarks, `rocket_fluid_loader`, `rocket_fluid_unloader`, `pressurized_tank` adapter | `FLUID_CARGO_*`; rocket signature uses the snapshot hash |
| W11d (C17a-03 part 2) | `rocket_item_loader`, `rocket_item_unloader` | `ITEM_CARGO_*` |
| W11e (C17a-04) | full `linker`, `rocket_monitoring_station` (§4.3) | moves nothing; the monitor shows `ESCROW_PENDING` and its automatic launch refuses |
| C17c-02 | `rocket_satellite_bay` | endpoint `SATELLITE_REGISTRY` |

The bay's registry endpoint is proven by the satellite registry's own `AtomicSavedData` flush.
Retained receipts (≤ 4,096, ≤ 128 per owner, ≤ 1 KiB) live in a separate schema-1 file,
`advancedrocketrycommunity_resource_receipts.dat` (≤ 4 MiB), so the hot ledger stays small. §8's
intent force, registration, receipt force and consumption map onto L1, the registry barrier, a
second ledger write and steps 3 to 7.

## 9. Open points and risks

- **D-1.** ADR-065 §4.2 forbids scanning by unbound devices, but the linker is C17a-04. I recommend
  W11b ship the linker's rocket-binding half (schema 1, ≤ 1 KiB, the §4.2 checks), not the ADR-027
  six-block search.
- **D-2.** The Access Transformer needs one `build.gradle` line, which belongs to Root. AGENTS.md
  §3.3 permits it; no Mixin is needed.
- **D-3.** Loaders skip occupied rockets, and boarding waits for release.
- **D-4.** Floors are collected only at server start; a long uptime could exhaust 256.
- **D-5.** An operation durable at L1 completes even if its player has logged off.

Risks: ledger and barrier costs are unmeasured; chunks with external records or vetoed saves (the
R-021 class) stall in `BARRIER_STALLED`; entity schema 3 changes every rocket's saved form, so
exact-NBT fixtures need careful updates; the ADR-027 `fuel_loader` and hand exchange keep their
two-store limits outside D3; the §0 facts must be re-checked on a Forge update.
