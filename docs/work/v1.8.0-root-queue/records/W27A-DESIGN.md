# W27a design note: the satellite bay and its deployment receipts (C17c-02)

Date: 2026-10-13. Round 2: revised for independent review, not an accepted implementation or Gate
result. Round 1 was `b55a41f2` on base `dac5cfd7`; this revision starts at `5ab9d343`, which includes
the shared-branch merge and W21b schema 4. Write scope: this design and `W27A2.md` only. Inputs:
ADR-065 sections 4.1, 4.1.1, 8, 10 and 11 with the W11b1 note; [W11A-DESIGN.md](W11A-DESIGN.md) round 2;
[W11B2-DESIGN.md](W11B2-DESIGN.md), and W11b2 branch HEAD pinned for this read at
`899dcc16cd6c23310f09e947fc9c3700806163e3`, including `W11B2.md`, `RocketTransferFences` and
`RocketTransferRecoveryService`; ADR-010; ADR-049 sections 6 and 7; ADR-050 sections 2, 7 and 9;
ADR-051. This note changes no code, test, ADR, risk entry, status file or ledger; section 10 holds the
text proposed for Root. The full independent review is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W27a-review/REVIEW.md`, verified SHA-256
`6f5b186fb066868bb4037fc6c23c20213977e901935bce56b75810f09ed245a1`. Root's decisions are in
`TASK-W27A2-BAY-REDESIGN.md`. Steps D0 to D7 are defined in section 4, cuts B1 to B18 in section 9;
section 12 answers F1-F5.

## 0. Checked facts

Java 17.0.7 `javap -p -c` on `forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e` (the W11a and W11b2 jar). These are
static checks, not native results. All listed classes/methods were re-inspected in round 2 with
`C:/Program Files/Java/jdk-17.0.7/bin/javap.exe -p -c -classpath <mapped-jar> <class>`.

| Class and method | Consequence for the bay |
|---|---|
| `ItemStack.<init>(CompoundTag)` 33-59; `DefaultedMappedRegistry.get(ResourceLocation)` 6-17; `ItemStack.of(CompoundTag)` 0-26; `ItemStack.save(CompoundTag)` 14-31, 85-89 | An unknown item ID resolves to the default air item; `of` returns `EMPTY` on a `RuntimeException` (debug line only). Re-encoding that missing-item stack writes air instead of the original ID, so the bay keeps the raw tag. |
| `SlotItemHandler.mayPickup(Player)` (simulated `extractItem`), `mayPlace(ItemStack)` (`isItemValid`) | A lock in the item handler locks the menu slot too. |
| `HopperBlockEntity.getContainerAt(Level,double,double,double)` (`WorldlyContainerHolder`, then `instanceof Container`); `HopperBlockEntity.ejectItems` → `VanillaInventoryCodeHooks.insertHook`; `DropperBlock.dispenseFrom` 64-75 → `dropperInsertHook`; `VanillaInventoryCodeHooks.getItemHandler(Level,double,double,double,Direction)` 65-70 | A bay BE implementing neither container surface exposes its inventory to these automation paths only through `ITEM_HANDLER`. The handler must refuse simulated and actual mutation, and `isItemValid`, while locked. |
| `BlockItem.updateCustomBlockEntityTag(Level,Player,BlockPos,ItemStack)` 40-63 | Server-side `onlyOpCanSetNbt()` requires a player with `canUseGameMasterBlocks()` before merging/loading item `BlockEntityTag`. A non-operator cannot restore a carried guarded root through that vanilla path. |
| `Entity.shouldBeSaved()` 20-47; project `RocketEntity.hasExactlyOnePlayerPassenger()` returns false | A live, non-riding rocket is save-eligible with players aboard. The barrier must still obtain and verify its actual entity-chunk record; save eligibility is not a durability proof. |
| `DistanceManager.addTicket(long,Ticket)` 17-31 (`addOrGet`, then `setCreatedTick`); `Ticket.timedOut(long)` (`createdTick` plus `TicketType.timeout()`) | Re-adding an existing timed ticket refreshes it, so the transfer's per-tick `keepLoaded` keeps a waiting rocket loaded past the 400-tick `FLIGHT_TICKET` timeout. |
| `SavedData.save(File)` 1-62; `MinecraftServer.tickServer` (`sipush 6000`, `irem`, `saveEverything`) | Re-checked from W11b2: a vanilla write logs a failure and clears the dirty flag anyway, and autosave runs every 6,000 ticks. Neither is proof. |

Facts from the current source:
- Transfer recovery accepts a rocket only when its snapshot ID **and content hash** equal the record's
  (`RocketTransferEntities.java:371-388`, `:204-214`). The hash covers dimension and origin
  (`RocketStructureSnapshot.java:129-141`), so source and destination copies differ; the transfer
  checksum covers both (`RocketTransferChecksum.java:61-62`).
- `AtomicSavedData.flush` (`AtomicSavedData.java:38-50`) uses `CheckedSavedDataFile.replace`
  (`CheckedSavedDataFile.java:33-74`), which forces the staged file but not the directory, has no readback
  after a committer failure and records no sequence. `EscrowLedgerWriter` does both
  (`EscrowLedgerWriter.java:112-146`) but encodes and digests on the owner thread (`:36-39`).
- At the pinned W11b2 HEAD, `RocketTransferFences.classify`, `loadedReason` and `actionAllowed` refuse
  `logicalLocked`; `RocketTransferRecoveryService.recover` restores a stationary source, and
  `settleRecoveredSource`/`finishSourceSettlementIfComplete` can remove T. `tryReconnect` can elect
  duplicates. None implements a bay-first altitude resume. These recovery/fence blobs are unchanged
  from the initially read `f516f816`. Continuation 03's off-thread journal writer is locally committed
  (`a86d9a20`); the later `5e0c7562` record has narrow test/native and split-cost evidence pinned to
  `f516f816`, but credited recovery, settlement and complete native/reference-performance evidence
  remain open. It is a dependency to review/integrate, not proof that W27d prerequisites are complete.

## 1. Decision summary

The deployment registers at the ascent-complete checkpoint. The package is consumed at altitude while the
transfer waits in TRANSIT. Once a barrier proves the consumed rocket on disk and the journal acknowledges
the consumed record, the receipt retires, and only then does the transfer continue. Receipts live in
their own managed file, written by a second instance of the W11b1 writer; the ledger is unchanged. The
registry's checked replace gains exact acknowledgements, which are the only proof of registration.
Phases: INTENT, REGISTERED, REJECTED and REPAIR; retirement removes the receipt.
The coordinator owns restart reconstruction at altitude before ordinary recovery or reconnect. It
retains PREPARED T through D7. Only exact, action-scoped permits may cross a bay lock; the transfer
service has no general exemption. Replay identity excludes mutable mission history, and both consumers
share one admission authority (sections 2, 3, 7 and 8).

## 2. The receipt store

**File and root.** `data/advancedrocketrycommunity_bay_receipts.dat`, `ManagedSavedDataType.BAY_RECEIPTS`,
introduced in v1.8.0, schema 1, epoch `v1.8.0-bay-receipts`, no legacy schema. An Overworld-owned
`BayReceiptSavedData`, bound to the server-lifetime escrow runtime (no static collections), is created
lazily on the first admission. Root: `schema_version`, `format_epoch`, `store_id` UUID, nondecreasing
`issued_through` and `writer_generation`, and `receipts` (at most 4,096). The section is at most 4,194,304
bytes and the root at most 4,195,328 (1 KiB of framing), depth 4, 131,072 nodes. A future or invalid root
refuses whole-world startup and keeps its bytes, as RESOURCE_ESCROW does (W11b1 C3; risk entry C).

**Why a separate file.** The ledger root has a closed schema-1 field set and a 1 MiB bound, and its writer
encodes on the server thread. Adding 4 MiB of receipts would multiply the server-thread cost of every
fluid L1 and L2. W11b2 continuation 03 requires journal encoding/IO off-thread; its local writer exists,
but its recorded partial evidence does not complete integrated recovery/performance acceptance. One
coordinator still owns both stores and the union of their pending counts, the "same authority" of
ADR-065 section 10.

**Receipt.** Schema 1, at most 1,024 uncompressed bytes including the compound's framing, depth 3,
40 nodes. UUIDs are int[4]; hashes byte[32]; `bay` int[3]; `schema_version` and `attempts` ints;
sequences, epochs and ticks longs; `preexisting` and `released` bytes (0/1); other fields strings. `definition` and `orbit`
are canonical ASCII IDs of at most 128 characters. `kind` is exactly the closed `SatelliteKind.id()`
encoding (`data`, `survey`, `solar`, `asteroid_miner`, `gas_harvester`, longest 14), not another
128-character ID. `phase` is one of the four names below; `reason` is at most 40 ASCII characters.
The earlier 941-byte estimate is superseded: the future codec must encode every optional field at
its maximum, and measure actual receipt, list and root framing. No estimate waives the 1 KiB limit.
Round-2 in-memory NBT field arithmetic for the 24 frozen fields below is 938 bytes including an empty
named-compound header/end (3,842,048 bytes for 4,096 such allowances before list/root framing). This is
a sizing cross-check, not an implemented codec result; tests must exercise the real encoding/bounds.

| Fields | Content |
|---|---|
| `schema_version`, `op`, `seq` | 1; the unique operation UUID; a positive sequence no greater than `issued_through` |
| `rocket`, `bay`, `satellite` | The key: logical rocket UUID, bay position relative to the snapshot origin (int[3]), and the satellite UUID from the package identity |
| `owner`, `definition`, `kind`, `orbit` | The agreed owner UUID; definition ID; closed kind ID; the physical checkpoint body. `orbit` is request/audit context, not an initial-mission identity or an existing satellite's replay target |
| `package_sig` | SHA-256 (32 bytes) of the raw package tag in sorted-key canonical form (W11b2's streaming digest) |
| `transfer`, `src_before`, `src_after`, `dst_before`, `dst_after` | Exact T at the checkpoint; content hashes (32 bytes each) of its source and destination snapshots before/after the immutable consumption group. Before hashes identify the source revision; after hashes are set with REGISTERED. Snapshot IDs, end dimensions/origins and source entity UUID are taken only from that checked T, never a different transfer |
| `phase`, `reason`, `preexisting`, `released` | The phase; bounded reason name; whether the registry already held X at D1; durable released-but-unresolved flag, initially 0, forced to 1 before delegating a released flight |
| `registry_epoch`, `created_tick`, `updated_tick`, `attempts` | The ADR-050 save epoch after the proving write (audit only); server ticks; a retry count of at most 1,000 |

Codec invariants: unique `op`, unique key, and at most one receipt per satellite; REGISTERED requires all
four hashes; released receipts cannot re-arm or retire online; unknown fields refuse. The codec refuses
and never normalizes. No released software wrote this draft schema, so this refinement needs no migration.

**Writer.** Generalize `EscrowLedgerWriter` into one typed writer per managed type, or adopt W11b2
continuation 03's journal writer if it already did so. It keeps the W11b1 behaviour: one captured
generation and one successor; exact `(generation, digest, outcome)` completions on the server thread;
unknown outcomes resolved by bounded readback or retried as the exact candidate every 200 ticks; three
unresolved failures make the store unavailable (BAY_RECEIPTS_UNAVAILABLE); a drain at
`ServerStoppingEvent` and an abort on the crash path. The policy is `CheckedSavedDataPolicy(BAY_RECEIPTS,
atomicMove)` and then `forceDirectory` on POSIX. The server thread only captures an immutable persistent
map; encoding, digest, staging, force, readback and replacement run on the writer thread. Writes start at
most once per 10 ticks, and autosave never publishes queued intents (the W11b1 C2 rule).

**Capacity, reserved before registration.** One server-lifetime `EscrowAdmissionAuthority` belongs to the
coordinator, not to either file/writer. Bay and W11b3 must call its same `tryReserve(op, owner, rocket,
consumer)` before queuing a new operation. No consumer has an independent 64-slot allowance. The pending
set is the union of unique operation UUIDs in durable unresolved ledger records and bay receipts, queued
admissions/re-arms, active/successor captures, and unknown write outcomes. Copies of one operation in
several states cost one slot; a UUID reused with a different consumer/identity is refused. INTENT,
REGISTERED, REPAIR and released-but-unresolved receipts all count, including a runtime REGISTRY_BLOCKED
hold. The same union enforces 64/server and 8/rocket. A bay group charges one slot per receipt, not one
slot per rocket. On startup both stores are validated and indexed before any admission; a union over
the cap refuses new work but neither evicts records nor prevents bounded recovery.

The separate retained-receipt union (durable + queued + captured + unknown) enforces 4,096/server,
128/owner and a 1 KiB lifecycle byte reservation. It includes durably REJECTED receipts. A REJECTED
receipt releases its *pending* slot only after the refusal is exactly acknowledged and no unresolved
registration/candidate can still commit; it keeps its retained slot. To return to INTENT, find the same
satellite/package receipt, validate the new bay key and D0, reacquire pending capacity for the same `op`,
and force the key/context update and INTENT before D2. If full, leave REJECTED/package unchanged.
Never allocate a replacement `op` to evade the count. An acknowledged retryable rejection releases the
pending reservation again. Retirement/offline durable resolution releases it only at the exact removal
acknowledgement. A definite pre-replace D1 failure releases only a never-applied admission with no
captured successor; UNKNOWN keeps every reservation. REPAIR or releasing the flight wait is not a
resolution. Full, unavailable or blocked storage keeps the package; nothing is evicted or reset by
decommission, and no online command deletes a receipt.

<div style="page-break-after: always;"></div>

## 3. The registry proof

`SatelliteMissionSavedData` keeps `AtomicSavedData`'s synchronous checked replace, and keeps ADR-050
section 2's write policy, save epoch and threading. Composition adds three things:
1. A RAM `mutationSequence`, incremented by every path that sets the dirty flag (state changes, clock-only
   changes, restore reports).
2. `checkpoint(watch)`. On the server thread it captures the sequence `s` and, for each watched satellite,
   immutable identity tuple `I = (satelliteId, ownerId, definitionId, kind)`. It runs the
   existing checked replace, then `forceDirectory`.
   - COMMITTED sets `acknowledged = (s, digest)`.
   - If the committer was attempted and threw, a bounded `readPayload` comparison decides between
     COMMITTED and UNKNOWN, as `StationRegistrySavedData.java:554-580` does. UNKNOWN clears `acknowledged`.
3. Every write entry (terminal barrier `flush`, bay `checkpoint`, the 100-tick coalesced flush and
   autosave/stop `save(File)`) uses this one captured-outcome/epoch bookkeeping, so a later successful
   write can prove an earlier registration. Replace-then-throw readback resolves the exact frozen
   candidate, advances E once at most, and neither clears `flushPending` nor starts a new E+1 candidate
   while UNKNOWN remains unresolved. Failure retains dirty/pending state and retries that candidate.

W21b root schema 4, literal epoch `v1.8.0-ground-surveys`, tagged instance origins and `ground_surveys`
are preserved. A clean current-schema load does not write or advance `save_epoch`; RAM proof metadata
adds no registry NBT fields/schema migration. All existing state/clock mutations must be sequenced.

**Proof rule.** A bay registration made at sequence `m` is durable when a COMMITTED completion has
`s >= m` and its captured I equals the receipt's `(satellite, owner, definition, kind)`. Decommission of X is fenced from
D1 until REGISTERED is durable (section 4), and nothing else removes satellites (pruning removes only
missions), so `s >= m` already implies presence; the tuple check is defence in depth, and a mismatch gives
REPAIR with REGISTRY_SIGNATURE_CONFLICT. The digest is W11b2's streaming canonical digest, because W11b1's
canonical buffer stops at 262,144 bytes and the registry may reach 16 MiB (`SatelliteLimits.java:62`).

**Fresh data versus replay.** At a fresh DATA launch, D2 must return SUCCESS with X and the atomic initial
mission `M = SatelliteManager.initialMissionId(X)` for the receipt's checkpoint body. Verify that returned
mission's immutable ID, satellite/owner/definition and target immediately from the launch result, before
other mutations, and associate sequence m with the atomic insertion. This checks initial-mission
behaviour, not a lifetime retention condition. Later cancellation, claim, pruning or a replacement
mission cannot undo that insertion. A descendant checked snapshot proves I at s >= m even if M has
finished/disappeared; no current or historical mission is required by the proof.

For an already matching data X (`preexisting` sampled again at D1), D2 is IDEMPOTENT, makes no mission,
and watches I at the observation sequence; it never calls `initialMissionId` to reconstruct a missing
mission. At restart, INTENT + matching disk I proves registration for either case; the loaded registry
is an acknowledged disk observation in the new session, not an invented old sequence m. If no restore
mutation occurred, no new write is needed merely to reproduce that proof. Restoration changes require
a fresh checkpoint before D4. The D1 fence forbids deleting/re-registering X while INTENT, but legitimate
mission claim/cancel/start/prune and kind-state updates remain allowed and must be sequenced.

For fresh non-data launches, also check the returned fixed orbit equals the request. For existing
non-data replay, an older fixed orbit or different blueprint is not a conflict with a newly selected
target/component list: ADR-049 matches I, not the new request. Never compare receipt `orbit` with a data
mission or require an existing non-data satellite to have launched into the present checkpoint body.

**Cost.** At most one `checkpoint` per tick covers every registration of that tick (at most 32); ADR-050
section 2 already performs this synchronous barrier for each terminal launch. W27d measures it at a 16 MiB
registry against docs/17 P95 and P99. If that fails, stop and report: an off-thread registry writer needs
ADR-050 section 2's follow-up ADR with separate snapshot and durable epochs. The bay contract does not
depend on the transport.

**Shared registry write fencing**, in `SatelliteMissionSavedData` so that every caller is covered:
- `launch` and `launchIdle` of X return the new code DEPLOYMENT_PENDING while a receipt for X is INTENT,
  REGISTERED or REPAIR, unless the caller holds the coordinator's private permit for that operation.
  No public request can authorize itself merely by supplying `op`. REJECTED never blocks: the bay has
  not registered X, and the ADR-049 section 6 replay semantics apply.
- `decommission` of X returns DEPLOYMENT_PENDING while a receipt for X is INTENT or REPAIR, or is
  REGISTERED without an exact acknowledgement yet. A durable REGISTERED receipt leaves the ADR-049
  section 7 rules unchanged.
- A blocked registry (`preservedBlockedData`) refuses admission with REGISTRY_BLOCKED.

## 4. The sequence

All steps run on the server thread. "Durable" means an exact acknowledgement.

| Step | Action | Durable afterwards | A forced stop leaves |
|---|---|---|---|
| D0 validate | At the ascent-complete checkpoint of exact PREPARED T for L: physical bay checks, then shared replay-first validation (section 7), at most 8 bays/rocket and 32 steps/tick. Require checked T, a current checked transaction-journal acknowledgement with no record for L, and the reconstruction eligibility of section 4.1. | Nothing. A rejection keeps a runtime reason only. | Nothing; the package stays in the bay. |
| D1 intent | Reserve capacity. Append INTENT, or re-arm the existing REJECTED receipt of X with the same `op`. Request a receipt write. L is now bay-pending: the transfer waits in TRANSIT, and decommission and other launches of X are fenced. | INTENT. A definite write failure cancels the attempt (runtime RECEIPT_WRITE_FAILED); an unknown outcome retries the exact candidate. | Before replacement: nothing. After it: INTENT. |
| D2 register | In the INTENT callback: revalidate, then call the shared launch service (`launch` with `initialMissionId(X)` for data, `launchIdle` otherwise). SUCCESS, or IDEMPOTENT with a matching identity, continues. A refusal becomes REJECTED with its reason, written with the next generation. | INTENT; the registry changed in RAM only. | INTENT; the registry with or without X. |
| D3 prove | Registry `checkpoint` (section 3). On FAILED or UNKNOWN, keep INTENT and the RAM registration and retry at most once per 200 ticks; three failures give the runtime reason REGISTRY_UNPROVED. Never reject in-session after a RAM registration. | INTENT; the registry file holds X. | INTENT with X durable. |
| D4 registered | Close admission for this checkpoint. Once every admitted bay has an outcome, freeze one consumption group for exact (L,T): only proven registrations are consumed; REJECTED bays remain byte-equal. Through the section 4.1.1 queue (one reconstruction/tick), compute S' and T' once. Every REGISTERED receipt in the group has the same whole-source/destination before/after hash pairs. Publish all group outcomes in one receipt generation, then lift their decommission fences on exact acknowledgement. | REGISTERED group and acknowledged refusals. | INTENT (recovery promotes it) or the complete frozen group. |
| D5 consume | Recompute the consumed snapshots and require the recorded hashes (otherwise REPAIR with CONSUMPTION_MISMATCH). Publish together S' on the entity and T' in the journal: same transfer ID and phase, consumed snapshots, recomputed checksum. Request the checked journal write and a W11b1 entity barrier on S. | REGISTERED; the consumption exists in RAM. | Any combination of S or S' and T or T' on disk. |
| D6 prove the source | The journal acknowledges T' and no other record for L. Barriers store, force and read back the exact source entity-chunk record at the bound altitude position/TRANSIT state with its snapshot ID and `src_after`; section 4.1 also proves no competing recognized source/destination copy remains in the bounded end regions. All tokens must be valid together. | Proof tokens, in RAM. | REGISTERED, with S' and T' durable. |
| D7 retire | Write a receipt generation without the receipt, guarded like `EscrowCoordinator.retire`: the barrier token must still be valid at capture and before replacement, or D6 repeats. The exact acknowledgement frees the slot and ends the TRANSIT wait. | Nothing remains. | REGISTERED (the proofs repeat) or retired. |

**Replay.** Consumption is idempotent per copy. While a REGISTERED receipt exists, every copy of
`(L, bay, X)` whose package signature matches is dead: it is consumed and never registered again, whether
X is live or decommissioned. A copy whose bay holds `consumed_op = op` is already consumed. A copy that
holds another package, or was consumed by another operation, becomes REPAIR with BAY_CONFLICT, and its
package is kept.

**Restart classification.** First dispatch durable `released = 1` receipts to the released-flight
protocol; they never promote, register, reject-to-rearm or retire online. The following table applies
to non-released receipts. The coordinator's section 4.1 hook owns T before *any* ordinary recovery,
readiness ticket, duplicate election, reconnect settlement or transfer tick. Classification alone does
not rebuild a source or authorize journal removal. The fair queue is at most 32 steps/tick.

| Receipt | Registry as loaded | Result |
|---|---|---|
| INTENT | X present, I equal | Prove the loaded identity per section 3, then REGISTERED through D4. No mission-history lookup. |
| INTENT | X absent | REJECTED with INTERRUPTED. Decommission was fenced from D1 and nothing else removes satellites, so absence means the registration never became durable. The package stays, and the next checkpoint re-arms the same receipt. |
| INTENT | tuple differs | REPAIR with REGISTRY_SIGNATURE_CONFLICT. |
| INTENT | registry blocked | Runtime REGISTRY_BLOCKED; treated like REPAIR for the wait (section 7). A REGISTERED receipt no longer needs the registry and proceeds. |
| REGISTERED | X present or absent | D5 on whichever copies are unconsumed, then D6 and D7. |
| REJECTED or REPAIR | any | No transition. |

### 4.1 Coordinator-owned altitude recovery

**Chosen protocol.** Resume the bay source at altitude, not ordinary stationary-source rollback. This
keeps round 1's consumption/retirement hashes, source proof and PREPARED journal authority valid. A
stationary alternative would need a second source revision/position, durable journal-removal proof and
different offline-passenger settlement; ordinary recovery supplies none of that as a bay contract.

**Admission eligibility.** D0 requires an operational source with a neutral W11b2 escrow wrapper
(epoch 0, no markers or ledger binding), no escrow operation/frozen frontier/tombstone or floor protecting
this (L,T)/source identity, and no competing transaction. This is a conservative first-bay-slice choice:
T contains snapshots and flight data, not the source's escrow wrapper. Without a full durable source
envelope, rebuilding a credited/non-neutral source would invent or reset its epoch. Such a source gets
runtime BAY_SOURCE_PROTECTED with its package intact; there is no exemption for it. Supporting that
source class requires W11b2's complete-envelope recovery within v1.8 and separate evidence, not merely
hash tolerance. Neutral destination endpoints from ordinary transfers remain eligible. The hook always
rechecks the underlying escrow protection even after admission, and a conflict becomes locked REPAIR.

**Ownership and binding.** At server start, load/validate/index both coordinator stores before exposing
rocket recovery. A durable INTENT/REGISTERED/REPAIR receipt claims its exact T; an unknown in-session
candidate claims the same tuple. One session claim binds store UUID, operation/group IDs, L, T, owner,
PREPARED phase, checked record digest, source/destination snapshot IDs, end dimensions/origins, and
allowed before/after hashes. The source reference includes T's `sourceEntityId`, the physical Level,
origin chunk and `(origin.x + 0.5, origin.y + FLIGHT_ALTITUDE_BLOCKS, origin.z + 0.5)` (48 blocks at this
baseline). Snapshot `sourceOrigin` remains the launch origin; it is not the entity's altitude Pos.
Claims/proofs are reconstructed in RAM every session, never accepted from a caller or persisted token.
For a held, non-released claim, missing T, another T/owner/end/snapshot ID, invalid checksum, unexpected phase or a hash outside the
group is TRANSFER_AUTHORITY_CONFLICT/REPAIR; do not substitute another record or invoke ordinary recovery.

**R0 claim.** Remove the claimed T from ordinary live/settled/recovery elections. The hook reports
OWNED_WAIT until completion, including during every asynchronous D1-D7 write. Both automatic and manual
recovery, queued callbacks and login work consult it again at their final action boundary. A callback
captured before a receipt claim must be invalidated, not grandfathered in. Before source discovery or
reconstruction, request/read the exact checked T baseline (UNKNOWN waits); no stationary rollback or
`journal.remove(T)` is permitted while the claim is active.

**R1 load only recorded ends.** A coordinator permit may activate only the source/destination end
regions derived from checked T, including the existing lookup margin, at most 16 chunk columns per end.
Use the W11b2 loaded-only index and exact source UUID lookup, wait for FULL/entity-loaded/ticking
readiness, and retain ordinary transfer ticket bounds/cadence. Process one such group per tick through
the snapshot-work queue; no synchronous multi-region/world scan. No client coordinate, receipt body ID
or passenger login supplies a chunk to load. Wider regions/missing Levels wait or require repair without
additional tickets. Manual REPAIR work does not acquire new recovery tickets. A healthy claimed group
renews only its recorded transfer tickets while waiting; other healthy groups remain scheduled fairly.

**R2 recognize/elect/reconstruct.** Inspect bounded loaded candidates at both ends and T's exact source
UUID (at most `MAX_TRANSFER_ENTITY_MATCHES`, currently 64; reaching the cap is a conflict). A source copy
must match L/owner, source snapshot ID and `src_before` or `src_after`; a destination copy must match
the destination ID/hash pair, never the source pair. For INTENT without a frozen outcome, only the
original before hash is allowed. Every candidate must satisfy the neutral-wrapper rule; an unexpected
identity, payload, position/relocation or protected duplicate is retained and locked, not overwritten.
Election is coordinator-owned: prefer the exact source UUID, never choose the destination as authority
for PREPARED T. Hold recognized duplicates frozen until their packages can be consumed under the
REGISTERED group; no `keepOne`/`discardAll` call is allowed merely because hashes are tolerated.

If the exact source is absent after all recorded source columns' entity sections have loaded, create
it from T's source snapshot using T's original source UUID, L and owner, neutral wrapper and complete
source flight/fuel/plan/passenger authority. Do not call `rebuildSource`, whose current implementation
uses `stationarySource`. A pure bay-resume constructor establishes TRANSIT with activeTransferId T,
source body/target, manifest and fuel/debit vector preserved; it does not re-run countdown, burn fuel,
charge another launch or start any satellite mission. Set Pos to the bound altitude position, zero
velocity, then add in the scoped reconstruction action. UUID collision or failed publication waits or
locks repair, never creates a second random source. An exact loaded source may legitimately have a
lagging COUNTDOWN/ASCENT save; establish the same TRANSIT authority from checked T/intent and position
without replaying the launch's side effects. Reconstructed TRANSIT starts at recovery game time; a
session-ready signal after D7, not elapsed time alone, controls destination spawn.

**R3 consume and normalize.** Classify receipt/registry, finish D4, then D5 idempotently on the source and
every recognized duplicate with the same group hashes. Mixed `(S',T)` and `(S,T')` are separate supported
cases: recompute only the unconsumed half and require the recorded after hash before publication. Keep
PREPARED T, its ID, source UUID, flight data and reservations; rewrite only snapshots/checksum to T'.
Preserve REJECTED bay packages and previously retired `consumed_op` markers. A group cannot admit a new
member after its hashes freeze. Partial D1 admission, mixed outcomes or a partial retirement must never
recompute a different whole-rocket group from only the remaining receipts. T'/S' and their consumed
markers provide the frozen complete image; remaining REGISTERED receipts must agree with that image.

After a valid survivor exists, remount only online assigned passengers to it through a scoped
MOVE_ASSIGNED_PASSENGER permit. Consume then discard only recognized neutral non-survivors, and obtain
stable entity-chunk readback proofs of their absence throughout the bounded recorded end regions.
Offline assignments remain in T/source flight data; neither login nor being all-online may call
`finishSourceSettlementIfComplete` or remove T. Offline players need not connect for D6/D7: a later login
is routed to the ready source, or the destination after normal acknowledged transfer. An unmatched or
outside-bound copy is not silently deleted; quarantine and retain the receipt (residual D addresses
unrecorded historical copies, not a claimed successful election).

**R4 prove/retire/handoff.** Recreate D6 proofs, including exact survivor UUID, physical chunk/Pos,
TRANSIT state, active T, manifest/resources and consumed snapshot, plus duplicate-absence proofs and
the checked T' digest. All entity/absence tokens must stay valid from retirement capture through the
writer's pre-replace guard. Superseding saves, joins/leaves, movement or changed T invalidate the group;
repeat proof without releasing the claim. D7 may retire members separately only under this same full
group proof; the claim remains until *all* blocking members are durably resolved and no candidate can
restore one. Only the final exact removal acknowledgement returns READY_SOURCE(T', sourceUUID) to the
transfer service. It restores live classification, rechecks the source and normal W11b2 journal/escrow
guards, then advances normally from consumed T'. With no receipts after a crash, ordinary recovery may
roll consumed T' back to a stationary source; this is safe and is not claimed to resume the flight.

Released-but-unresolved REPAIR/REGISTRY_BLOCKED cases retain their reservation and logical lock. Release
of the flight wait is possible only for a coherent, checked, recognized payload authority, through the
exact released-flight permits in section 8. An unknown source/signature is not eligible for release.
Their `released = 1` group flags must be exactly acknowledged before the first delegated flight step;
restart never infers release from a RAM reason alone. They cannot enter R4 retirement, even after they
finish the flight. Shutdown drains checked authority
writes but executes no reconstruction/mount/spawn gameplay callbacks; crash teardown discards RAM
claims/tokens, which the next session reconstructs before recovery. No timeout substitutes for proof.

**Decommission interlock.** Decommission is refused with DEPLOYMENT_PENDING from D1 in RAM until REGISTERED
has an exact acknowledgement. INTENT is durable before any registration, so the fence survives a restart.
After that, the ADR-049 section 7 rules apply unchanged: idle, no unfinished mission, no live receiver.
Data missions may progress, release their binding and be pruned; decommission is still subject to the
receipt fence until D4 acknowledgement. Decommission never changes the receipt.

<div style="page-break-after: always;"></div>

## 5. Retirement

A fair retirement queue (at most 8 per tick) retires a receipt (D7) only when all of these hold:
1. REGISTERED is durable.
2. A W11b1 barrier proof, valid at the retirement's capture and before its replacement, shows L's
   authoritative entity with every bay of this operation consumed (`consumed_op = op`) and content hash
   `src_after`.
3. The journal has acknowledged a generation, captured after the consumption, in which every record for L
   has consumed bays (checked on the server thread at capture; at most one record per rocket,
   `RocketTransferSavedData.java:100`).
   Its active/successor/unknown generations and every deferred same-T callback must also be unable to
   publish a before-consumption record after this acknowledgement. Writer ordering/current authority
   and the bay dispatch fence establish this; a file read alone does not. Startup never automatically
   adopts an older backup/staged journal generation to bypass the current checked authority.
4. D0's transaction-journal condition held and L has been in flight since, so no assembly or disassembly
   record for L can hold the unconsumed bay.
5. No live entity of L in RAM holds X in that bay, and valid entity-chunk absence tokens cover recognized
   removed duplicates in both bounded ends. The survivor's physical Pos/TRANSIT/active T and neutral
   wrapper match the coordinator reference, not merely its snapshot hash.

The coordinator claim lasts through retirement of the complete registered group, so the destination
always spawns from T' after its REGISTERED receipts are gone (retained REJECTED receipts need not vanish).
The source stays put between proof and retirement; any vanilla save of its
chunk in between invalidates the token and repeats D6. A save-event
observation is never proof;
only the barrier and the checked writers' exact acknowledgements count, in callback order, so the order of
the coordinator's and the source's durable acknowledgements is proven by construction.

Retirement frees capacity only; it never registers a satellite, refunds a package or rewrites the
registry. REJECTED receipts survive transfers and disassembly and retire when their package leaves a world
bay, or when a checkpoint finds their bay without that package; they never block, and D0 re-arms one for
whichever bay now holds its package, keeping its `op`. REPAIR receipts have no online exit in v1.8 (the
offline whole-world procedure of W11A-DESIGN section 5). A receipt whose wait was released (section 7)
cannot meet conditions 2 to 4 online and stays retained.

**Tombstones.** The bay uses none of the ledger's 64 tombstones. In the source, the consumed bay's
`consumed_op` marker plays that role, and while REGISTERED exists it governs every recognized copy. After retirement,
no unconsumed copy remains under the S2 assumptions; risk entry D lists what lies outside them.

## 6. The bay block

- **Block and block entity.** `rocket_satellite_bay` (ledger unit `block_variant:loader/1`) has one slot.
  The block entity stores its placing `owner` and overrides `onlyOpCanSetNbt()` to return true. It is not
  a `Container`, and its block is not a `WorldlyContainerHolder` (section 0), so its menu and its
  `ITEM_HANDLER` capability are its only surfaces.
- **Schema-1 package root.** At most 4,352 bytes, depth 18, 272 nodes. Fields: `schema_version` 1, `owner`
  UUID, then at most one of `package` (the raw native item tag) or `consumed_op` (UUID). The `package` tag
  has the native item bound of 4,096 bytes, depth 16 and 256 nodes, as `FuelPayloadBounds.item`, with
  `Count` 1. The raw tag is kept byte for byte and decoded only to show a live stack and to read the
  identity. Unknown fields, both entries at once, or wrong types refuse the load and quarantine the bay;
  the data is preserved and the bay locked.
- **Package identity decoder.** `SatelliteItemData.readTag` on the package's `tag` must give a VALID
  identity, in `satellite_package` for a non-data identity or `data_satellite_package` for a data one
  (`SatelliteTerminalInventory.validPackage`). A FUTURE identity (UNSUPPORTED_DATA) or an unknown item ID
  (PACKAGE_UNAVAILABLE) is kept raw and refuses deployment and extraction, since `ItemStack.of` would
  return `EMPTY`; player breaking and explosions then skip the bay (W11b2 matrix rows 19 and 20), and
  `/setblock` stays a documented operator bypass.
- **Normal removal policy.** For a valid, available and unclaimed world bay, normal break/explosion
  removes the package exactly once as its ordinary package item and drops an empty bay item; a
  `consumed_op` bay drops only the empty bay. There is no guarded `BlockEntityTag` carrier and no
  automatic owner/root restoration on ordinary placement. A new bay gets the placing owner's UUID.
  Loot and removal callbacks share one extraction flag so they cannot both emit the package. World
  saves preserve the original package tag until removal. Guarded raw/unknown/future/quarantined roots
  instead refuse player break and are removed from the explosion's block list, with no drops; live NBT
  replacement also preserves the held root. `/setblock`, `/fill` and third-party raw replacement remain
  detection-only operator residuals, not this preservation policy. A serializable quarantined root is
  written unchanged. If native serialization cannot preserve it, use the existing `GuardedChunkSaves`
  denial (whole terrain chunk, then Level-wide after 256 denied coordinates, until Level closes);
  proposed risk C below must be registered before W27b enables that save refusal. Never substitute an
  empty/sanitized root or rely on non-operator placement to restore operator-only NBT.
- **Host rocket adapter.** A built-in adapter, `advancedrocketrycommunity:satellite_bay_v1`, sits beside
  `VanillaContainerRocketAdapter` in `RocketAdapterRegistry.freeze()` and `RocketBlockEntityAdapters.defaults()`.
  Capture validates the root and copies it exactly; restore loads it and must recapture an equal payload
  (the existing check). Several bays share the 128-payload budget (`RocketLimits.java:8-10`).
- **Flight lock.** A captured bay has no live slot: it has no menu or capability, and ADR-065 section 4.1
  forbids generic handlers over captured payloads, so loaders never reach it. A world bay refuses insertion
  and extraction while an assembly or disassembly transaction claims its position. Otherwise its
  capability allows both. The menu opens for the owner or an operator, not a spectator, within 8 blocks.
- **Identity agreement.** Insertion requires package owner = bay owner and no INTENT, REGISTERED or
  REPAIR receipt for X. D0 requires bay owner = package owner = rocket owner (the transfer record's
  `ownerId`), and X not keyed to another `(rocket, bay)` in a blocking phase. Replay requires
  `(L, bay, X, package_sig)` or `consumed_op` to match.
- **Limits.** At most 8 deployments per rocket per checkpoint, in sorted bay-position order (the rest:
  runtime ROCKET_DEPLOYMENT_LIMIT), and at most 32 steps per server tick through one round-robin queue keyed
  by logical rocket, which also serves restart recovery. Bays come from the snapshot's block-entity list by
  adapter ID (at most 128 visits, cached per snapshot ID and hash); there is no world or structure scan.
- **Status.** The world menu shows phase and reason in two `ContainerData` slots, looked up by X in the
  receipt index or the runtime rejection map (at most 4,096 entries, cleared at stop). No new channel.

## 7. The orbit checkpoint and the production launch service

**Checkpoint.** `RocketTransferService.tickSource`, at the ASCENT to TRANSIT transition
(audit `ascent_complete`; W11b2's pinned `tickSource`). After a restart, a PREPARED record already
in TRANSIT whose checkpoint has not run in this session runs D0 for bays without a receipt, but never
adds a new member to a group whose REGISTERED hashes have frozen. The orbit body
is `record.sourceFlightData().plan().sourceBody()`, which for a station launch is the station's captured
orbit body (`RocketFlightService.java:303-305`); a plan older than schema 3 carrying `space` fails
orbitability and is rejected. The client's destination choice is never used.
This restart entry runs only after the bay-first dispatch has established source authority; ordinary
PREPARED rollback is not a substitute. A receipt already REGISTERED replays its frozen outcome without
consulting a removed catalog/body or applying today's launch validation again.

**The TRANSIT wait.** `spawnDestination` also requires `bays.transferReady(L)`: no D0 request
queued, no blocking recovery claim and no unresolved non-released member of L's group. A released-flight
step instead requires its exact permit, never an idle-state exemption. The coordinator,
under its exact wait permit, keeps renewing only the recorded transfer's
`FLIGHT_TICKET`. Round 1's 70-140 tick estimate is not a measured latency guarantee: group proof now also
covers duplicates/absence, and storage scheduling controls completion. Instrument queue ages and each
checked write/reconstruction/barrier under the unchanged budgets.
Retryable storage failures keep waiting, as W11b2 journal failures already stall transfers. REPAIR and
REGISTRY_BLOCKED may release the wait through section 8's checked, released-flight protocol: unresolved
packages fly on unconsumed, any already-registered consumption remains governed by its receipt, L is locked against
launch and disassembly until offline repair, and this released receipt never retires online.

**Bay physical validation, D0 and D2.** Decode the raw package/identity, enforce bay/package/rocket owner
agreement and the exact source/transfer/position/neutral-wrapper authority, and resolve the plan's
source body in the celestial catalog. For *every* kind, reject a missing body or
`!body.capabilities().orbitable()` with TARGET_NOT_ALLOWED (an unavailable celestial catalog has its
explicit unavailable reason). This common physical check precedes kind-specific validation; it is not
inferred from data `validateDefinitionAndTarget`, which lacks orbitability. It also rejects legacy
`space` plans. Station launches use the captured station orbit body even if the station subsequently
moves; never look it up anew or use the destination picker. Receipt admission/reservation is a separate
coordinator requirement. These physical checks are bay-only; they do not change terminal replay.

**Shared launch order, used by both adapters.**
1. Require server-thread authority, valid identity and caller ownership, an operational registry and
   the deployment fence (only the internal coordinator permit can authorize its own operation).
2. Look up X before consulting *new-launch* catalogs, bodies/targets, blueprint stats, research,
   accounts or quotas. Existing matching I returns IDEMPOTENT with X and no new mission; a mismatching
   I returns IDENTITY_CONFLICT. Do not re-evaluate components, compare the new requested target with an
   old orbit, or reject on full satellite/mission/account/byte quotas. A matching RAM record whose
   registration is dirty/unproved still needs the adapter's persistence barrier before consumption.
3. Only for absent X, require the loaded catalogs. DATA retains `SatelliteDefinition.allows(body)`
   and known-target validation, uses the existing `discoveryRequired` mission flag, and starts the
   initial mission. Do **not** require prior discovery for DATA: its mission may be the discovery
   mechanism. Non-data uses `kindDefinitionForPrimary`, `launch_targets`, known orbitable body,
   discovery when required, `SatelliteBlueprints.evaluateIdentity` and `requiredLifetimeResearch`.
   Then dry-run/admit the global/owner satellite, mission, unfinished-mission, account and byte budgets.
4. Revalidate this same ordered branch at D2, not the fresh-launch branch unconditionally. On refusal
   keep the package and reason. Retry uses the retained receipt `op` and the package's original X.

The current non-data `SatelliteKindLifecycle.launchIdle` already has branch 2; the data registry
`SatelliteMissionRegistry.launch` also matches I independent of current mission, but the current
`SatelliteManager.launch` wrapper checks definition/target first. This extraction deliberately moves
that wrapper's existing-identity case ahead of new admission too, as Root's F4 decision directs. It
preserves ADR-049 replay semantics rather than claiming that the old data wrapper already handles
removed definitions. Pin that changed wrapper ordering explicitly in parity tests.

**Production launch service.** Extract `SatelliteLaunchService` from `SatelliteManager.launch`
(`SatelliteManager.java:195-235`) and `SatelliteKindLifecycle.launchIdle` (`:43-113`):
`validate(server, owner, identity, body)` and `register(server, request, gameTime)`, with an explicit
REPLAY versus FRESH validated request, keyed by owner UUID,
with no `ServerPlayer`, no FakePlayer, no `releaseTestHooks` property and no flush. The terminal becomes
`validatePlayerIdentity`, the service, and its existing `flush` barrier, with identical results (parity
tests, with the data-wrapper ordering correction documented above). The bay performs its physical
checks separately, calls the same methods, then `checkpoint`. Offline owners work because accounts are keyed
by UUID. The service is internal; no public API is added for non-data kinds.

<div style="page-break-after: always;"></div>

## 8. Interlocks with W11b1 and W11b2

- **Gate.** `RocketEscrowGate.idle` and `logicalLocked` also require that no INTENT, REGISTERED, REPAIR or
  released receipt names L. Every W11b2 matrix row therefore also covers a bay-pending rocket at rest:
  the mutation layers, boarding, mounting, teleport, `/kill`, `/data`, disassembly and launch. The
  transfer service has **no** exemption. D5 and recovery actions use coordinator-owned scoped permits; ordinary
  escrow locks, markers, floors, tombstones and journal UNKNOWN still refuse.
- **Required W11b2 hook (not present at `899dcc16`).** Add an internal
  `RocketTransferBayRecoveryGate.beforeAction(server, journal, record, action)` dispatch contract,
  implemented/registered by the coordinator's `BayTransferRecovery`. `action` distinguishes
  RECOVER_AUTO, RECOVER_BY_ID, ELECT_DUPLICATES, RECONNECT, SETTLE_SOURCE, REMOVE_JOURNAL,
  TICK_SOURCE, SPAWN_DESTINATION and each deferred flight/recovery callback. Results are UNCLAIMED
  (ordinary W11b2 rules), OWNED_WAIT/REPAIR (no ordinary action), or a coordinator-only exact action
  permit/READY_SOURCE handoff. The existence of a bay claim is queried before ticketing/classification;
  it is not cleared by `RocketTransferFences.permit`. Root must add these entry/final-boundary hooks to
  W11b2's scope; W27d supplies the bay protocol, not a second independent transfer-recovery service.
  Specifically wire `recoverNext`, `recoverById`, `recover`, `tryReconnect`,
  `settleRecoveredSource`, `finishSourceSettlementIfComplete`, `classify`/`actionAllowed`, entity
  join/leave, and `tickLive`/`spawnDestination`. Journal remove/rebind/phase callbacks recheck ownership
  before publication. Keep `RocketTransferService` below its class-size ceiling by extracting dispatch.
- **Permit scope.** A private coordinator-created token binds session, action, op/group, L, T, owner,
  exact checked old/next record digests, end, snapshot ID/hash pair, entity UUID, physical reference and
  expected wrapper/resources. It expires on mutation/save observation/join/leave or after that action;
  an operation UUID in a request is never authority. Allowed held actions are bounded recorded-end
  loading/ticket refresh, neutral source reconstruction, TRANSIT/position establishment, exact
  consumption/journal rewrite, recognized-copy removal, assigned-passenger movement and barrier
  capture. No normal rollback, arbitrary mutation, settlement, discard, launch, quota bypass or T
  removal is authorized before D7. The entity/mount/movement guard validates the precise permitted
  action while keeping its ordinary defence in depth; it must not turn `idle()` true globally.
- **Recovery matching.** Before/after hash tolerance exists only inside this dispatcher for exact
  (L,T,end,snapshot ID,owner), with its recorded physical/reference checks. Ordinary
  `RocketTransferEntities.matches`/`isCommittedAuthority` retain exact-hash semantics. Tolerance
  recognizes a mixed consumption write; it does not authorize `keepOne`, rebuild, remount or removal
  by ordinary recovery. Released-flight recovery also uses the dispatcher, never a global hash bypass.
- **Released-flight permits.** For a coherent REPAIR/blocked-registry group only, the coordinator can
  force `released = 1` on its unresolved receipts, retain their reservations and delegate normal flight
  steps for that exact checked T. Complete recognized REGISTERED consumption before copying a package
  to the destination; INTENT/REPAIR packages remain intact, and incoherent groups are not released. Each
  scoped step validates the unchanged unresolved package signatures, already-consumed markers,
  owner/end/snapshot IDs, neutral wrapper and old/next journal digests, including destination
  publication, passenger movement, source removal and eventual reservation cleanup. Restart dispatch
  uses the checked phase's source/destination authority without registering or retiring a receipt.
  No ordinary source-settlement branch may remove PREPARED T instead of resuming the declared flight.
  A missing T is accepted only after the released flight's ordinary checked completed-reservation
  cleanup; the receipt remains a logical lock and no longer triggers source reconstruction. During
  restart, released + absent T is retained as an offline-repair hold, never treated as registration
  absence or permission to redeploy. It cannot distinguish per-file deletion, which is outside S2.
  Incoherent/unknown payloads remain frozen for offline repair. Completion changes availability only:
  the unresolved receipts keep the 64/8 charge and the rocket's launch/disassembly/resource lock.
- **Journal rewrite.** D5 adds one journal mutation, allowed only for a PREPARED record named by a
  REGISTERED receipt of L. It keeps the transfer ID, phase, flight data and reservations, changes only the
  two snapshots and the checksum, and is written by W11b2's checked writer with exact acknowledgement.
- **Assembly recovery.** The W11b2 assembly fence also fences when a bay receipt names L. D0's
  transaction-journal condition makes that case unreachable in practice.
- **Transaction journal.** `ROCKET_TRANSACTIONS` adopts the W11b2 checked policy and acknowledgement
  (sequence, root digest, record digests). At D0, if the acknowledgement is not current, request one
  checked write and keep L waiting. If it fails, all bays of L are rejected with
  ASSEMBLY_JOURNAL_UNPROVED.
- **Escrow signatures and floors.** D5 changes L's escrow signature but not its epoch. No ledger record or
  marker may govern a bay-admitted source; section 4.1 explicitly refuses protected/non-neutral sources.
  Earlier exact-transfer floors cannot be ignored merely because the rocket is in flight. A0 pins the
  admitted neutral case and refusals. Bay and W11b3 share `EscrowAdmissionAuthority`; W11b3 is not a
  dependency for an isolated bay, but mixed-consumer capacity/recovery tests are a prerequisite for
  enabling them together.
- **ADR-044.** A waiting PREPARED record keeps blocking warps of the station regions it overlaps, like any
  PREPARED record.

## 9. Slices, tests and S2 cuts

| Slice and dependency | Scope | Tests and cuts |
|---|---|---|
| W27b; shared branch only | Bay block, block entity, menu, capability, locks, schema-1 root, `satellite_bay_v1`, section 6's ordinary-drop/guarded-retention policy, recipe, original art, EN and zh. Deployment disabled; Root records any save refusal first. | A0 root codec at every bound. A1 menu, hopper/dropper simulation and actual calls; owner/receipt refusals; transaction lock; assembly/disassembly byte-equal; raw unknown/future roots; `onlyOpCanSetNbt`; valid normal/explosion drops exactly once, guarded roots no drops and no non-op carrier restoration. S1 native save/reload, serializer refusal with exact chunk/Level scope. |
| W27c; W11b1, and reviewed W11b2 off-thread writer if reused | Receipt type, codec, writer, index and single admission authority; shared launch service; registry acknowledgements/checkpoint/fences and all write-entry bookkeeping; transaction-journal acknowledgement; gated driver, no production entry. | A0 actual full-field 1 KiB and root/list framing, 4,096/128 retained and 64/8 pending; F2-F4 matrices below. A1 real files, injected committers, schema-4/epoch preservation and startup refusal. Driver S2 B1-B3/B9/B11-B13 tests receipt/registry ordering only, never source recovery acceptance. |
| W27d; W27b, W27c, W11b2 integrated and independently reviewed | Exact section 8 hook plus section 4.1 protocol, TRANSIT wait, D0-D7, journal rewrite/barriers and duplicate-absence proofs, narrow permits/restart dispatch, status/messages/audit. Require actual off-thread checked journal writer, its authority guards and required evidence, not just a branch name. Admission off by default. | A0 every D1-D7 prefix for one/eight bays and mixed outcomes; action-scope/end/hash/phase/epoch refusals. A1 real rockets/data/solar, source missing/present, duplicate join and offline login. F5 matrix below. Benchmarks: 32 registrations at 16 MiB, D5 on 2,048 blocks/128 payloads and bounded recovery reads. Native S2 all B1-B18 via real restart service. |
| W27e; W27d | Packaged evidence; Root and owner decide on enabling admission; V1 menus, V2 two clients. | Full matrix, two restarts each, pinned JAR, docs/17 reference load. |

**Pinned regression matrices (planned, not executed).**
- F2: fresh DATA atomically creates the deterministic initial mission for the captured body. Before
  D3/D4, cancel it, claim it, prune eligible history, or replace it with a later mission; include a
  later successful coalesced flush and autosave after a failed checkpoint. Re-run each as preexisting
  I replay and INTENT restart promotion. Prove I and no extra mission/registry entry; no proof step
  queries current/initial/history retention. Changed owner/definition/kind still conflicts.
- F3: 63 unresolved escrow operations plus one bay fill the same 64 slots; a second bay and a loader
  both refuse. Reverse consumers; count a durable/captured/successor/UNKNOWN copy once. Put all 64 in
  REPAIR, release flight waits and restart: capacity remains full. Durably reject one never-registered
  bay, reuse the freed pending slot, then try re-arming its REJECTED receipt: full refuses without
  changing op/key/package; a later exact resolution permits re-arm with the same op. Exercise the
  shared 8/rocket count with a 7-escrow + 1-bay admission-authority fixture (production source/escrow
  interlocks can refuse earlier and are not bypassed), retained 4,096/128 independently of pending, mixed-store load before
  admission, and a failed/unknown retirement. W11b3 and bay use the very same authority instance.
- F4: with matching I, remove the kind/data definition, primary component or other component; alter
  component stats/research; choose a different/removed/disallowed requested target; fill global/owner
  satellite, mission, unfinished, account and byte quotas. Non-data terminal/shared replay stays
  IDEMPOTENT with zero admission work. DATA also takes the accepted early-identity branch (explicit
  wrapper-order correction). A dirty matching X after failed persistence is not consumed until the
  exact adapter barrier succeeds; replace-then-throw is resolved by frozen readback. Conflict,
  unauthorized caller, blocked registry and another operation's deployment fence still refuse.
  Compare terminal output/package extraction and shared-service result; test bay physical checks
  separately, since a removed physical checkpoint body is not the terminal's chosen replay target.
- F5: for DATA and non-data separately, use a known non-orbitable body, legacy plan `space`, missing
  body, and station launch whose captured orbit body differs from destination and later station orbit.
  The first three refuse before kind validation, preserving package/receipt identity; the station
  case uses the captured body. An undiscovered but allowed orbitable DATA target still starts its
  discovery mission, while non-data keeps its required discovery gate. REGISTERED replay is unaffected
  by later catalog removal. No client target or body ID may activate another chunk.
- Registry transport: clean schema-4 reload changes neither literal epoch nor save epoch; successful,
  failed-before-replace and UNKNOWN checkpoints preserve tagged mission/ground origins, ground surveys,
  resources/accounts and raw blocked roots. Cover *each* barrier/coalesced/autosave/stop entry, and
  replace-then-throw without double E advancement or lost `flushPending`. Prove obsolete callbacks and
  stale before/after hashes do not acquire a coordinator permit.

**Cuts.** Each cut stops on both sides of its boundary; for writes, that means staged-only and
replaced-before-callback. Two restarts follow. The fixture is a neutral-wrapper Earth to Moon rocket with one data bay
whose definition allows Earth and one solar bay. The expected result is that each satellite is registered
exactly once and its bay consumed, or the receipt is REJECTED with the package byte-equal.

| Cut | Boundary | Required outcome |
|---|---|---|
| B1 | INTENT write | Before: nothing; a later retry. After: INTENT; the restart gives REJECTED (INTERRUPTED), and the next checkpoint reuses the `op`. |
| B2 | Registration in RAM; the registry checkpoint | Before the checkpoint: REJECTED (INTERRUPTED). After it: promotion to REGISTERED, no second registry entry. |
| B3 | REGISTERED write with source S present and separately absent from disk; online/offline manifest variants | Actual restart dispatch claims PREPARED T before ordinary recovery. INTENT promotes or REGISTERED resumes; source is established at its exact altitude chunk/Pos/TRANSIT. No stationary rollback/journal removal; decommission remains fenced until durable D4. |
| B4 | Register, then decommission idle non-data X, then stop before S' save; X live/absent and S present/absent | Coordinator replay consumes without registering X again. Exact source reconstruction works independently of registry presence; offline assignments survive and no reconnect settlement removes T. |
| B5 | D5 mixed disk `(S',T)` and `(S,T')`, tested separately, with loaded neutral duplicates at both ends and offline passengers | Scoped end/hash recognition, not global tolerance. Complete only the unconsumed half; elect/reconstruct the named source, consume/remove recognized duplicates, prove their disk absence. Keep PREPARED T until D7 and one usable logical rocket afterward. |
| B6 | Source absent before D7 with T before or after consumption; destination absent after D7 | Bay constructor rebuilds original source UUID at altitude/TRANSIT from exact T/T', never ordinary `rebuildSource` stationary state. After retirement ordinary destination reconstruction uses consumed T'; no package resurrection. Include conflicting/protected duplicate and non-neutral-source admission refusal. |
| B7 | D6 proofs complete, retirement not written, with offline passengers; source loaded and cold-chunk variants | Restart invalidates all RAM tokens, obtains source/duplicate-absence/T' proofs anew and retains the claim. A player logging in while retirement is queued cannot elect/remove/settle T. Initially RAM-absent source is loaded before election; no old proof is reused. Do not delete independently forced S' to manufacture a supported S2 absence. |
| B8 | Retirement staged-only and replaced-before-callback; partial group retirement; online/offline passengers | Before: remaining REGISTERED receipts own T and repeat complete-group proofs. After final replacement: no receipt; consumed S'/T' are independently durable, registry unchanged. Ordinary restart may choose stationary consumed-source recovery; destination, if created, is consumed. |
| B9 | Registration refused after INTENT | REJECTED durable; package kept; the retry reuses the `op`. |
| B10 | Signature conflict; blocked registry | REPAIR or REGISTRY_BLOCKED; coherent flight may be released by an exact permit, incoherent authority remains frozen; L locked and receipt/pending charge retained, package kept. |
| B11 | Full store: retained 4,096/owner 128, mixed pending 64/rocket 8; REPAIR/released/UNKNOWN and restart | Shared authority refuses before INTENT, counts each unique op once, package byte-equal; REJECTED re-arm reacquires pending capacity without a new op. |
| B12 | Receipt write failed before replacement, and unknown, at D1, D4 and D7 | Exact-candidate retry; no early release or registration. |
| B13 | Registry checkpoint failed, and unknown | INTENT kept; decommission and terminal launch refused; a later success promotes. |
| B14 | Unproved transaction journal or a stale assembly record at D0 | If RAM already has no record for L, require its exact checked write before D1; failure rejects all bays. If a record for L actually remains, refuse ASSEMBLY_JOURNAL_PENDING until legitimate transaction recovery/removal is durably proved; writing the stale record is not absence proof. |
| B15 | Receipt/registry/journal replacement followed by exception, before callback/readback resolution | Resolve exact candidate as COMMITTED or UNKNOWN; no duplicate epoch increment, slot release, outcome or gameplay effect. Restart reads the actual target; package and T follow that authority. |
| B16 | Source/duplicate-absence proof invalidated while retirement is captured/queued | Superseding save, late join/leave or motion cancels replacement at the guard. Receipt/claim persists; new proof required, no stale token retirement. |
| B17 | REPAIR/unloaded work ahead of healthy groups; partial D1 and mixed REGISTERED/REJECTED/REPAIR group | Bounded fair scheduling completes eligible unrelated work without bypassing 64/8. Group hashes stay immutable; coherent released-flight work remains unresolved/locked; no online repair deletion or partial-consumption proof based only on remaining receipts. |
| B18 | Clean server stopping with active/successor/unknown writes, and crash teardown | Stop drains authority without executing launch/rebuild/mount/spawn callbacks; crash publishes no completion. Restart recreates reservations/claims before real recovery, never trusts session tokens or an unapplied capture. |

The proposed harness is `scripts/run_v180_bay_forced_stop.py`, with the gated flags `releaseTestHooks`
and `bayCheckpoint=<cut>` and the pause marker `ARCE_RELEASE_BAY_PAUSED cut=<id> op=<uuid>`. The probe only
pauses; it never launches or registers anything.
For B3-B8 and B15-B18 the packaged harness must boot the real production restart wiring
(`RocketTransferRecoveryService` and login dispatch), not invoke the receipt classifier/test driver
directly. Capture raw S/T/receipt/registry files, Pos/flight/manifest and command/callback trace with
source/JAR pins. Distinguish staged-only, replaced-before-callback and readback-resolved outcomes;
count satellite/mission registrations, packages, usable rocket identities and retained reservations.
The driver cannot satisfy this native authority contract. All cases and benchmarks here remain NOT_RUN.

<div style="page-break-after: always;"></div>

## 10. Proposed text for Root

**ADR-065 implementation note, "W27: satellite bay deployment receipts".** This note records mechanics;
it changes no decision and accepts no risk (entries C and D). Receipts live in a separate schema-1 managed
root, `BAY_RECEIPTS`: a 4 MiB section, at most 4,096 receipts, 128 per owner and 1 KiB each. The D3-A
coordinator owns it and writes it with its one-writer machinery and exact acknowledgements. A future or
invalid root refuses whole-world startup. A deployment writes INTENT before it registers, and proves the
registration with an exact checked acknowledgement of the registry. It then writes REGISTERED and consumes
the package at altitude, in the rocket and its journal record together, while the transfer waits in
TRANSIT. The receipt retires only after a W11b1 barrier proves the consumed rocket and the journal
acknowledges the consumed record, and the transfer continues only after that retirement. Decommission of
the satellite is refused from intent until
REGISTERED is durable. While REGISTERED exists, recognized copies of its bay are consumed under
coordinator authority and never registered again. A bay-first recovery dispatcher claims exact logical
rocket/transfer/end/snapshot authority before ordinary recovery, duplicate election, reconnect
settlement or journal removal. It establishes a neutral source at its recorded altitude/TRANSIT state,
keeps PREPARED T through D7, and obtains stable survivor/duplicate-absence proofs. The first slice refuses
non-neutral source wrappers whose complete reconstruction authority is absent from T. Hash tolerance
is local to the coordinator's action permits, never a transfer-service exemption. Released unresolved
operations keep the shared 64/8 reservation and lock. One admission authority counts unique
durable/queued/captured/unknown operations across both stores, including REPAIR. Immutable satellite
identity proves replay independently of mission history; the shared service handles existing I before
new-launch admission, with separate common bay physical orbitability checks. Ordinary flushes,
autosaves and save events alone are never proof. Native forced-stop
evidence and owner acceptance remain separate.

**ADR-050 implementation note, "W27".** The registry's checked replace records an exact acknowledgement
(session mutation sequence and digest), resolves a failed commit by bounded readback, and forces the
directory where supported. Launch and decommission consult the bay receipt fence (DEPLOYMENT_PENDING).
Every registry write entry shares the same captured outcome and one-time epoch advancement, including
replace-then-throw resolution; UNKNOWN retains dirty/flush-pending state and the exact candidate.
Write policy, synchronous threading, schema-4 literal epoch, tagged origins, ground surveys and limits
are unchanged, and the section 2 gate for an off-thread writer still applies. The replay tuple contains
satellite UUID, owner, definition and kind, never current/retained initial mission. Fresh data launch
still atomically creates its initial mission, whose later progression is not a deployment conflict.

**Risk entry C (new ID): bay receipt protection scope and availability.** A future or invalid receipt root
refuses whole-world startup and keeps its bytes, as in R-031; recovery is the W11b1 whole-world offline
procedure, never restoring or deleting the receipt file alone. An unresolved deployment holds its rocket
in TRANSIT with its passengers; retryable storage failures extend that wait with no time limit, as W11b2
journal failures do. REPAIR, or INTENT with a blocked registry, may release only a coherent checked
flight through coordinator-scoped permits but locks the rocket
(launch, disassembly, resources) and the package (extraction, deployment) until offline repair; such a
receipt never retires online. Full capacity (4,096, 128 per owner, 64 and 8 pending) refuses
deployments. REPAIR and released/unresolved receipts keep the pending 64/8 charge until exact durable
resolution; durably REJECTED receipts retain only their 4,096/128/byte slot and must reacquire pending
capacity before INTENT. Non-neutral source reconstruction is refused in the first bay slice. Valid
world-bay removal drops the package once and an empty bay; raw/future/quarantined roots remain in place
under break/explosion protection and are saved unchanged when serializable. If serialization would
omit them, `GuardedChunkSaves` denies the entire terrain chunk; after 256 denial coordinates, all later
terrain saves in that ServerLevel are denied until Level close, with no production online reset.
Entity/other stores may still save independently. The mechanism throws from the existing save guard,
not as ordinary receipt-flow control; it requires Root's risk/ADR registration before use and the
whole-world offline recovery procedure. One audit line per
transition and one per backoff transition, with no lifetime cap; inherited save/error logs also have
no lifetime cap. This registers mechanics; it accepts no
risk.

**Risk entry D (new ID): bay residuals outside S2.** (1) A stale rocket copy outside every journal and
receipt, such as an earlier flight's source entity left on disk by a vanilla writer failure the mod cannot
observe (the R-032 class), can bring a package back after its receipt retired. (2) Per-file rollback or
deletion of the receipt store, registry or either journal is outside the guarantee throughout the
deployment, not just after retirement. During INTENT, restoring only an older registry can falsely
make a formerly registered X look INTERRUPTED and permit re-registration; REGISTERED plus absent X
cannot distinguish such rollback from lawful decommission, and intentionally consumes. Retired state
cannot detect every older external copy. These are not successful exactly-once recovery cases. (3) OS
crashes, power loss and hardware faults. Whole-world backups mitigate all three. Exactly-once deployment
depends on the accepted D3-A proof and native cuts, never on clean restarts.

## 11. Choices for review, exclusions and remaining risks

Choices the reviewer should confirm:
1. Consumption at altitude with a TRANSIT wait. Consuming after landing instead would spawn the destination
   from T with the package, keep receipts for a whole flight, and need absence proofs for the old source.
2. A separate receipt file, not a section of the ledger root.
3. A synchronous registry checkpoint under the ADR-050 section 2 barrier policy.
4. At restart, INTENT with an absent satellite becomes REJECTED (INTERRUPTED); it never re-registers by
   itself.
5. D0 rejections are runtime-only; REJECTED receipts are kept for their retry identity.
6. Coordinator-owned altitude reconstruction with end-specific consumed hashes, private action permits
   and duplicate-absence proofs, rather than stationary recovery or a global tolerance/exemption. The
   conservative neutral-source restriction remains explicit until complete-envelope credited recovery
   is designed and verified; do not silently reset source epochs.
7. The transaction-journal acknowledgement as a D0 precondition.
8. No online exit for REPAIR, and released receipts never retire online.

Not in this design: implementing the fluid loader/linker/cargo (W11b3 onward), a new network channel or
rocket-flight bay UI, production/test code, ADR/risk edits, status/ledger changes, or release approval.
Shared admission is required before mixed consumers are enabled. ADR-049 replay semantics remain;
section 7 explicitly corrects the current data wrapper's earlier fresh-validation ordering.

Remaining risks: the checkpoint cost at a 16 MiB registry is unmeasured; the TRANSIT wait has no time limit
under storage failure; W11b2 is not integrated and the required dispatcher/action hooks do not exist at
the pinned `899dcc16` HEAD. Its credited-world recovery/settlement and complete native/performance
evidence are still open in `W11B2.md`. A non-neutral source is not deployable by this conservative first
slice. The shared ADR-065 note records W29's per-slot stability fix; that is not bay D6 evidence, and
observed save/proof invalidation still requires retries. Multi-copy proof cost and the 32-registration
maximum are unmeasured. Proposed save-denial scope must be registered by Root before implementation.

No current-version Required Gate is met by this note. Next: one independent read-only review, then W27b.

## 12. Answers to the round-1 findings

These are specification changes for review, not claims of runtime verification or a revised verdict.

| Finding accepted by Root | Revision and required evidence |
|---|---|
| F1, High | Sections 4.1/5/8 define coordinator-owned altitude recovery before all ordinary recovery/election/reconnect/removal actions, exact L/T/end/snapshot/position binding, neutral-source reconstruction, PREPARED preservation through full-group D7, duplicate consumption/absence proofs and offline-passenger handling. Section 8 names the absent W11b2 `RocketTransferBayRecoveryGate.beforeAction` hook and each call site, without a global exemption. B3-B8 use real restart wiring with source present/missing, cold chunks, mixed S/T writes and offline passengers. B15-B18 cover ambiguous replacement, invalidation, fairness and stop/drain. Non-neutral-source reconstruction is refused rather than resetting escrow identity. |
| F2, Medium | Section 3 uses immutable I and distinct fresh DATA versus existing replay proof rules. A fresh initial mission is checked from its atomic launch result, never required for later checkpoint/replay/promotion. Claim/cancel/start/prune remain legal. Section 9 pins cancelled, claimed, pruned and replaced initial missions plus later autosave/coalesced success. |
| F3, Medium | Section 2 specifies one coordinator admission authority and a unique-op union across both stores/all writer states. REPAIR/released unresolved operations retain 64/8 charges; only durable resolution releases them. REJECTED retains its receipt slot and reacquires pending capacity with the same op before forced INTENT. Section 9/B11 pins 63 escrow + 1 bay, reverse/8-per-rocket, saturation/REPAIR/restart, UNKNOWN and re-arm cases. |
| F4, Medium | Section 7 makes the existing-I branch precede new-launch catalog/stat/research/quota checks while preserving operational registry, ownership and deployment fences. Terminal barrier and bay exact checkpoint still precede extraction/outcome. Section 9 pins removed definitions/components, changed targets/stats/research, full quotas, dirty matching X and persistence failures. The current data-wrapper ordering difference is documented rather than calling it already implemented. |
| F5, Medium | Section 7 adds one common bay physical catalog/body/orbitability check before kind-specific validation, separates it from terminal replay and keeps DATA discovery behaviour. Section 9 pins both kinds against non-orbitable, legacy `space`, missing body and captured station orbit, plus catalog removal after REGISTERED. |

Other review clarifications are included: closed kind encoding and actual framing tests (section 2),
one ordinary-drop/guarded-retention policy and proposed save-denial scope (sections 6/10), all registry
write-entry/epoch and schema-4 preservation checks (sections 3/9), corrected W11b2 implementation/evidence
tense, immutable multi-bay group rules, mixed-consumer enabling conditions, and rollback residuals that
apply before retirement as well as after it. Round 1's separate bounded receipt store, decommission
interlock, raw identity retention, explicit adapter, flight lock, production server/owner service,
performance stop rule and pause-only native probe remain.
