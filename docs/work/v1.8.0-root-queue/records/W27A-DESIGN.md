# W27a design note: the satellite bay and its deployment receipts (C17c-02)

Date: 2026-10-13. Status: design for one independent read-only review (AGENTS.md section 13); not
implementation or Gate evidence. Base `dac5cfd7`, which includes the W11b1 merge `f1eb5d83`. Inputs:
ADR-065 sections 4.1, 4.1.1, 8, 10 and 11 with the W11b1 note; [W11A-DESIGN.md](W11A-DESIGN.md) round 2;
[W11B2-DESIGN.md](W11B2-DESIGN.md) and the W11b2 branch at `cf184331` (continuation 03 moves the checked
journal write off the server thread); ADR-010; ADR-049 sections 6 and 7; ADR-050 sections 2, 7 and 9;
ADR-051. This note changes no code, test, ADR, risk entry, status file or ledger; section 10 holds the
text Root adds. Steps D0 to D7 are defined in section 4, cuts B1 to B14 in section 9. Page breaks
divide five pages.

## 0. Checked facts

Java 17.0.7 `javap -p -c` on `forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e` (the W11a and W11b2 jar). These are
static checks, not native results.

| Class and method | Consequence for the bay |
|---|---|
| `ItemStack.<init>(CompoundTag)` 33-59; `DefaultedMappedRegistry.get(ResourceLocation)` 6-17; `ItemStack.of(CompoundTag)` 0-26; `ItemStack.save(CompoundTag)` 14-31, 85-89 | An unknown item ID becomes air; `of` returns `EMPTY` on any failure (debug line only); `save` writes `minecraft:air`. Decode and re-encode silently deletes a package whose item is missing, so the bay keeps the raw tag. |
| `SlotItemHandler.mayPickup(Player)` (simulated `extractItem`), `mayPlace(ItemStack)` (`isItemValid`) | A lock in the item handler locks the menu slot too. |
| `HopperBlockEntity.getContainerAt(Level,double,double,double)` (`WorldlyContainerHolder`, then `instanceof Container`); `HopperBlockEntity.ejectItems` → `VanillaInventoryCodeHooks.insertHook`; `VanillaInventoryCodeHooks.getItemHandler(Level,double,double,double,Direction)` 65-70 | A block entity that is neither reaches hoppers and droppers only through `ITEM_HANDLER`, so one lock covers menu and automation. |
| `BlockItem.updateCustomBlockEntityTag(Level,Player,BlockPos,ItemStack)` 40-63 | `onlyOpCanSetNbt()` with `canUseGameMasterBlocks()` is the only gate on `BlockEntityTag` loading. |
| `Entity.shouldBeSaved()` 20-47; `RocketEntity.hasExactlyOnePlayerPassenger()` returns false (`RocketEntity.java:401-404`) | A crewed rocket at altitude is still written to its entity chunk, so the W11b1 barrier can prove it with players aboard. |
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

## 1. Decision summary

The deployment registers at the ascent-complete checkpoint. The package is consumed at altitude while the
transfer waits in TRANSIT. Once a barrier proves the consumed rocket on disk and the journal acknowledges
the consumed record, the receipt retires, and only then does the transfer continue. Receipts live in
their own managed file, written by a second instance of the W11b1 writer; the ledger is unchanged. The
registry's checked replace gains exact acknowledgements, which are the only proof of registration.
Phases: INTENT, REGISTERED, REJECTED and REPAIR; retirement removes the receipt.

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
fluid L1 and L2, the cost W11b2 continuation 03 has just moved off that thread for the journal. One
coordinator still owns both stores and the union of their pending counts, the "same authority" of
ADR-065 section 10.

**Receipt.** Schema 1, at most 1,024 bytes, depth 3, 40 nodes. With 128-character ASCII IDs and short
keys, the worst case counted field by field is 941 bytes; an A0 test encodes it at that bound.

| Fields | Content |
|---|---|
| `schema_version`, `op`, `seq` | 1; the unique operation UUID; a positive sequence no greater than `issued_through` |
| `rocket`, `bay`, `satellite` | The key: logical rocket UUID, bay position relative to the snapshot origin (int[3]), and the satellite UUID from the package identity |
| `owner`, `definition`, `kind`, `orbit` | The agreed owner; canonical ASCII IDs of at most 128 characters; the kind ID; the orbit body |
| `package_sig` | SHA-256 (32 bytes) of the raw package tag in sorted-key canonical form (W11b2's streaming digest) |
| `transfer`, `src_before`, `src_after`, `dst_before`, `dst_after` | The transfer T at the checkpoint; content hashes (32 bytes each) of T's source and destination snapshots before and after consumption. The after-hashes are set with REGISTERED |
| `phase`, `reason`, `preexisting` | The phase; a bounded reason name of at most 40 characters; whether the registry already held the satellite at D0 |
| `registry_epoch`, `created_tick`, `updated_tick`, `attempts` | The ADR-050 save epoch after the proving write (audit only); server ticks; a retry count of at most 1,000 |

Codec invariants: unique `op`, unique key, and at most one receipt per satellite; REGISTERED requires all
four hashes; unknown fields refuse. The codec refuses and never normalizes.

**Writer.** Generalize `EscrowLedgerWriter` into one typed writer per managed type, or adopt W11b2
continuation 03's journal writer if it already did so. It keeps the W11b1 behaviour: one captured
generation and one successor; exact `(generation, digest, outcome)` completions on the server thread;
unknown outcomes resolved by bounded readback or retried as the exact candidate every 200 ticks; three
unresolved failures make the store unavailable (BAY_RECEIPTS_UNAVAILABLE); a drain at
`ServerStoppingEvent` and an abort on the crash path. The policy is `CheckedSavedDataPolicy(BAY_RECEIPTS,
atomicMove)` and then `forceDirectory` on POSIX. The server thread only captures an immutable persistent
map; encoding, digest, staging, force, readback and replacement run on the writer thread. Writes start at
most once per 10 ticks, and autosave never publishes queued intents (the W11b1 C2 rule).

**Capacity, reserved before registration.** At D1 the coordinator reserves one slot at the receipt's
maximum size, so later phases never fail on size (the ADR-050 section 7 rule). Counting used, queued,
captured and unknown-outcome receipts, admission requires at most 4,096 receipts and 128 per owner, and
at most 64 pending operations per server and 8 per rocket, where unresolved receipts (INTENT or
REGISTERED) count together with ledger records and queued escrow admissions. Slots are reserved at 1 KiB,
so the section bound holds. A definite failure before replacement releases the reservation; an unknown
outcome keeps it. Full, unavailable or blocked storage refuses with the package in place. Nothing is
evicted, decommission never resets a receipt, and no command deletes one.

<div style="page-break-after: always;"></div>

## 3. The registry proof

`SatelliteMissionSavedData` keeps `AtomicSavedData`'s synchronous checked replace, and keeps ADR-050
section 2's write policy, save epoch and threading. Composition adds three things:
1. A RAM `mutationSequence`, incremented by every path that sets the dirty flag (state changes, clock-only
   changes, restore reports).
2. `checkpoint(watch)`. On the server thread it captures the sequence `s` and, for each watched satellite,
   the identity tuple (owner, definition, kind, and the orbit body or the initial mission ID). It runs the
   existing checked replace, then `forceDirectory`.
   - COMMITTED sets `acknowledged = (s, digest)`.
   - If the committer was attempted and threw, a bounded `readPayload` comparison decides between
     COMMITTED and UNKNOWN, as `StationRegistrySavedData.java:554-580` does. UNKNOWN clears `acknowledged`.
3. Autosave (`save(File)`) records acknowledgements the same way, so a later successful write can also
   prove an earlier registration.

**Proof rule.** A bay registration made at sequence `m` is durable when a COMMITTED completion has
`s >= m` and its captured tuple for the satellite X equals the receipt's. Decommission of X is fenced from
D1 until REGISTERED is durable (section 4), and nothing else removes satellites (pruning removes only
missions), so `s >= m` already implies presence; the tuple check is defence in depth, and a mismatch gives
REPAIR with REGISTRY_SIGNATURE_CONFLICT. The digest is W11b2's streaming canonical digest, because W11b1's
canonical buffer stops at 262,144 bytes and the registry may reach 16 MiB (`SatelliteLimits.java:62`).

**Cost.** At most one `checkpoint` per tick covers every registration of that tick (at most 32); ADR-050
section 2 already performs this synchronous barrier for each terminal launch. W27d measures it at a 16 MiB
registry against docs/17 P95 and P99. If that fails, stop and report: an off-thread registry writer needs
ADR-050 section 2's follow-up ADR with separate snapshot and durable epochs. The bay contract does not
depend on the transport.

**Shared registry write fencing**, in `SatelliteMissionSavedData` so that every caller is covered:
- `launch` and `launchIdle` of X return the new code DEPLOYMENT_PENDING while a receipt for X is INTENT,
  REGISTERED or REPAIR, unless the caller is that receipt's operation. REJECTED never blocks: the bay has
  not registered X, and the ADR-049 section 6 replay semantics apply.
- `decommission` of X returns DEPLOYMENT_PENDING while a receipt for X is INTENT or REPAIR, or is
  REGISTERED without an exact acknowledgement yet. A durable REGISTERED receipt leaves the ADR-049
  section 7 rules unchanged.
- A blocked registry (`preservedBlockedData`) refuses admission with REGISTRY_BLOCKED.

## 4. The sequence

All steps run on the server thread. "Durable" means an exact acknowledgement.

| Step | Action | Durable afterwards | A forced stop leaves |
|---|---|---|---|
| D0 validate | At the ascent-complete checkpoint of record T for logical rocket L (section 7): shared validation per bay, at most 8 bays per rocket and 32 steps per tick. It also requires a current checked acknowledgement of the transaction journal with no record for L (section 8). | Nothing. A rejection keeps a runtime reason only. | Nothing; the package stays in the bay. |
| D1 intent | Reserve capacity. Append INTENT, or re-arm the existing REJECTED receipt of X with the same `op`. Request a receipt write. L is now bay-pending: the transfer waits in TRANSIT, and decommission and other launches of X are fenced. | INTENT. A definite write failure cancels the attempt (runtime RECEIPT_WRITE_FAILED); an unknown outcome retries the exact candidate. | Before replacement: nothing. After it: INTENT. |
| D2 register | In the INTENT callback: revalidate, then call the shared launch service (`launch` with `initialMissionId(X)` for data, `launchIdle` otherwise). SUCCESS, or IDEMPOTENT with a matching identity, continues. A refusal becomes REJECTED with its reason, written with the next generation. | INTENT; the registry changed in RAM only. | INTENT; the registry with or without X. |
| D3 prove | Registry `checkpoint` (section 3). On FAILED or UNKNOWN, keep INTENT and the RAM registration and retry at most once per 200 ticks; three failures give the runtime reason REGISTRY_UNPROVED. Never reject in-session after a RAM registration. | INTENT; the registry file holds X. | INTENT with X durable. |
| D4 registered | When every bay of L has an outcome, prepare the consumption once through the ADR-065 section 4.1.1 queue (one reconstruction per tick): compute S' (the consumed source entity snapshot) and the consumed source and destination snapshots of T, and keep only their hashes. Write REGISTERED with the four hashes and `registry_epoch`. Its exact acknowledgement lifts the decommission fence. | REGISTERED. | INTENT (recovery promotes it) or REGISTERED. |
| D5 consume | Recompute the consumed snapshots and require the recorded hashes (otherwise REPAIR with CONSUMPTION_MISMATCH). Publish together S' on the entity and T' in the journal: same transfer ID and phase, consumed snapshots, recomputed checksum. Request the checked journal write and a W11b1 entity barrier on S. | REGISTERED; the consumption exists in RAM. | Any combination of S or S' and T or T' on disk. |
| D6 prove the source | The journal acknowledges a generation that holds T' and no other record for L. The barrier stores, forces and reads back; its validator requires L's entity record with S's snapshot ID and content hash `src_after`. When both hold, request retirement. | Proof tokens, in RAM. | REGISTERED, with S' and T' durable. |
| D7 retire | Write a receipt generation without the receipt, guarded like `EscrowCoordinator.retire`: the barrier token must still be valid at capture and before replacement, or D6 repeats. The exact acknowledgement frees the slot and ends the TRANSIT wait. | Nothing remains. | REGISTERED (the proofs repeat) or retired. |

**Replay.** Consumption is idempotent per copy. While a REGISTERED receipt exists, every copy of
`(L, bay, X)` whose package signature matches is dead: it is consumed and never registered again, whether
X is live or decommissioned. A copy whose bay holds `consumed_op = op` is already consumed. A copy that
holds another package, or was consumed by another operation, becomes REPAIR with BAY_CONFLICT, and its
package is kept.

**Restart classification.** A fair queue, at most 32 per tick, runs before the transfer service ticks L.

| Receipt | Registry as loaded | Result |
|---|---|---|
| INTENT | X present, tuple equal | REGISTERED through D4. The loaded file is the disk state. |
| INTENT | X absent | REJECTED with INTERRUPTED. Decommission was fenced from D1 and nothing else removes satellites, so absence means the registration never became durable. The package stays, and the next checkpoint re-arms the same receipt. |
| INTENT | tuple differs | REPAIR with REGISTRY_SIGNATURE_CONFLICT. |
| INTENT | registry blocked | Runtime REGISTRY_BLOCKED; treated like REPAIR for the wait (section 7). A REGISTERED receipt no longer needs the registry and proceeds. |
| REGISTERED | X present or absent | D5 on whichever copies are unconsumed, then D6 and D7. |
| REJECTED or REPAIR | any | No transition. |

**Decommission interlock.** Decommission is refused with DEPLOYMENT_PENDING from D1 in RAM until REGISTERED
has an exact acknowledgement. INTENT is durable before any registration, so the fence survives a restart.
After that, the ADR-049 section 7 rules apply unchanged: idle, no unfinished mission, no live receiver.
Data satellites stay busy with their initial mission. Decommission never changes the receipt.

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
4. D0's transaction-journal condition held and L has been in flight since, so no assembly or disassembly
   record for L can hold the unconsumed bay.
5. No live entity of L in RAM holds X in that bay.

The TRANSIT wait lasts until this retirement is acknowledged, so the destination always spawns from T'
after the receipt is gone. The source stays put between proof and retirement; any vanilla save of its
chunk in between (normally only an autosave) invalidates the token and repeats D6. A save-event
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
`consumed_op` marker plays that role, and while a receipt exists it governs every copy. After retirement,
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
(`RocketTransferService.java:499-509`, audit `ascent_complete`). After a restart, a PREPARED record already
in TRANSIT whose checkpoint has not run in this session runs D0 for bays without a receipt. The orbit body
is `record.sourceFlightData().plan().sourceBody()`, which for a station launch is the station's captured
orbit body (`RocketFlightService.java:303-305`); a plan older than schema 3 carrying `space` fails
orbitability and is rejected. The client's destination choice is never used.

**The TRANSIT wait.** `spawnDestination` (`:511-513`) also requires `bays.transferReady(L)`: no D0 request
queued and no INTENT or REGISTERED receipt of L. `keepLoaded` keeps renewing the transfer's
`FLIGHT_TICKET`. A healthy wait is about 70 to 140 ticks (three receipt writes at the 10-tick cadence, one
registry checkpoint, two reconstructions, one journal write, one 20-tick barrier batch).
Retryable storage failures keep waiting, as W11b2 journal failures already stall transfers. REPAIR and
REGISTRY_BLOCKED release the wait: the package flies on unconsumed, the receipt stays, L is locked against
launch and disassembly until offline repair, and this released receipt never retires online.

**Validation, shared by D0 and D2:** package and identity decoding; owner agreement; an operational
registry and a loaded catalog; for data, `SatelliteDefinition.allows(body)` and a known catalog body, as
`validateDefinitionAndTarget`; for other kinds, as `SatelliteKindLifecycle`: `kindDefinitionForPrimary`,
`launch_targets`, an orbitable body, discovery where required, `SatelliteBlueprints.evaluateIdentity` and
`requiredLifetimeResearch`; a dry run of the registry's `admission` (global and owner satellites, missions,
unfinished missions, accounts, byte budgets); and the receipt capacity of section 2. A rejection keeps the
package and its reason. Retries reuse the existing receipt and its `op`; the satellite UUID always comes
from the package.

**Production launch service.** Extract `SatelliteLaunchService` from `SatelliteManager.launch`
(`SatelliteManager.java:195-235`) and `SatelliteKindLifecycle.launchIdle` (`:43-113`):
`validate(server, owner, identity, body)` and `register(server, request, gameTime)`, keyed by owner UUID,
with no `ServerPlayer`, no FakePlayer, no `releaseTestHooks` property and no flush. The terminal becomes
`validatePlayerIdentity`, the service, and its existing `flush` barrier, with identical results (parity
tests). The bay calls the same methods, then `checkpoint`. Offline owners work because accounts are keyed
by UUID. The service is internal; no public API is added for non-data kinds.

<div style="page-break-after: always;"></div>

## 8. Interlocks with W11b1 and W11b2

- **Gate.** `RocketEscrowGate.idle` and `logicalLocked` also require that no INTENT, REGISTERED, REPAIR or
  released receipt names L. Every W11b2 matrix row therefore also covers a bay-pending rocket at rest:
  the mutation layers, boarding, mounting, teleport, `/kill`, `/data`, disassembly and launch. The
  transfer service itself is exempt during the TRANSIT wait, and D5 runs inside the coordinator's apply
  scope, which satisfies W11b2's defence-in-depth check.
- **Recovery matching.** Matching needs equal content hashes. `RocketTransferEntities.matches` and
  `isCommittedAuthority` therefore also accept an entity with the same snapshot ID whose hash is the
  other half of a `(src_before, src_after)` or `(dst_before, dst_after)` pair of a receipt of L that has
  not retired. Without this, a crash between D5 and D7 leaves S' unmatched against T, and recovery could
  rebuild a second rocket. A rebuild from T is safe: the copy's bay is dead under the receipt, and D5
  consumes it.
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
  marker names a rocket in flight, and floors compare epochs (W11b2 section 2). A0 pins both facts.
- **ADR-044.** A waiting PREPARED record keeps blocking warps of the station regions it overlaps, like any
  PREPARED record.

## 9. Slices, tests and S2 cuts

| Slice and dependency | Scope | Tests and cuts |
|---|---|---|
| W27b; shared branch only | Bay block, block entity, menu, capability, locks, schema-1 root, `satellite_bay_v1`, loot, recipe, original art, EN and zh. Deployment disabled. | A0 root codec at every bound. A1 menu, hopper and dropper; owner and receipt refusals; transaction lock; assembly and disassembly byte-equal; raw unknown item; `onlyOpCanSetNbt`. S1 native save and reload. |
| W27c; W11b1, and W11b2 continuation 03's writer if merged | Receipt type, codec, writer, index, capacity union; `SatelliteLaunchService`; registry acknowledgements, `checkpoint`, fences; transaction-journal acknowledgement; a gated test driver in place of the rocket. No production entry. | A0 codec at 941 bytes, 4,096 and 128; capacity with 64 and 8; writer outcomes; sequence rule; launch-service parity. A1 real files, injected committers, fences, byte-preserving startup refusal. S2 with the driver: B1-B3, B9, B11-B13. |
| W27d; W27b, W27c, W11b2 integrated | Checkpoint, TRANSIT wait, D0-D7, journal rewrite, barrier proof, matching tolerance, gate and fences, restart classification, status lines, messages, audit. Admission behind a server switch, off by default. | A0 model of every prefix of D1-D7 for one and eight bays, with decommission interleavings. A1 real rockets with data and solar bays. Benchmarks: a 32-registration checkpoint at 16 MiB; D5 on a 2,048-block, 128-payload rocket within section 4.1.1. S2 every cut. |
| W27e; W27d | Packaged evidence; Root and owner decide on enabling admission; V1 menus, V2 two clients. | Full matrix, two restarts each, pinned JAR, docs/17 reference load. |

**Cuts.** Each cut stops on both sides of its boundary; for writes, that means staged-only and
replaced-before-callback. Two restarts follow. The fixture is an Earth to Moon rocket with one data bay
whose definition allows Earth and one solar bay. The expected result is that each satellite is registered
exactly once and its bay consumed, or the receipt is REJECTED with the package byte-equal.

| Cut | Boundary | Required outcome |
|---|---|---|
| B1 | INTENT write | Before: nothing; a later retry. After: INTENT; the restart gives REJECTED (INTERRUPTED), and the next checkpoint reuses the `op`. |
| B2 | Registration in RAM; the registry checkpoint | Before the checkpoint: REJECTED (INTERRUPTED). After it: promotion to REGISTERED, no second registry entry. |
| B3 | REGISTERED write | INTENT promoted, or REGISTERED; decommission refused until durable. |
| B4 | Register, then decommission the idle non-data satellite, then stop before S' is saved | Replay consumes; X stays absent; no new registration. The same with X still live. |
| B5 | D5 publication with only S' saved, and with only T' acknowledged | Matching tolerance accepts S or S'; no second rocket; the replay completes. |
| B6 | Source and destination restore: REBUILD_SOURCE from T with S missing, before retirement; REBUILD_DESTINATION from T' after it | The rebuilt source is consumed by replay; the rebuilt destination comes from T' and is already consumed. |
| B7 | D6 proofs done, retirement not written | The wait persists; D6 repeats; the receipt retires; then the destination spawns. |
| B8 | Retirement write | After it: no receipt; the consumed bay is inert; the registry is unchanged. |
| B9 | Registration refused after INTENT | REJECTED durable; package kept; the retry reuses the `op`. |
| B10 | Signature conflict; blocked registry | REPAIR or REGISTRY_BLOCKED; wait released; L locked; package kept. |
| B11 | Full store: 4,096, owner 128, pending 64 and 8 | Refusal before INTENT; package byte-equal. |
| B12 | Receipt write failed before replacement, and unknown, at D1, D4 and D7 | Exact-candidate retry; no early release or registration. |
| B13 | Registry checkpoint failed, and unknown | INTENT kept; decommission and terminal launch refused; a later success promotes. |
| B14 | Stale assembly record at D0 | A checked write, then D1. If the write fails, every bay is rejected and nothing becomes durable. |

The proposed harness is `scripts/run_v180_bay_forced_stop.py`, with the gated flags `releaseTestHooks`
and `bayCheckpoint=<cut>` and the pause marker `ARCE_RELEASE_BAY_PAUSED cut=<id> op=<uuid>`. The probe only
pauses; it never launches or registers anything.

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
REGISTERED is durable. While a receipt exists, every copy of its bay is consumed on sight and never
registered again. Transfer recovery accepts a rocket whose snapshot hash differs from its record only by a
receipt's consumption. Ordinary flushes, autosaves and save events are never proof. Native forced-stop
evidence and owner acceptance remain separate.

**ADR-050 implementation note, "W27".** The registry's checked replace records an exact acknowledgement
(session mutation sequence and digest), resolves a failed commit by bounded readback, and forces the
directory where supported. Launch and decommission consult the bay receipt fence (DEPLOYMENT_PENDING).
Write policy, threading, save epoch and limits are unchanged, and the section 2 gate for an off-thread
writer still applies.

**Risk entry C (new ID): bay receipt protection scope and availability.** A future or invalid receipt root
refuses whole-world startup and keeps its bytes, as in R-031; recovery is the W11b1 whole-world offline
procedure, never restoring or deleting the receipt file alone. An unresolved deployment holds its rocket
in TRANSIT with its passengers; retryable storage failures extend that wait with no time limit, as W11b2
journal failures do. REPAIR, or INTENT with a blocked registry, releases the wait but locks the rocket
(launch, disassembly, resources) and the package (extraction, deployment) until offline repair; such a
receipt never retires online. Full capacity (4,096, 128 per owner, 64 and 8 pending) refuses
deployments; REJECTED receipts hold capacity until their package leaves a world bay. One audit line per
transition and one per backoff transition, with no lifetime cap. This registers mechanics; it accepts no
risk.

**Risk entry D (new ID): bay residuals outside S2.** (1) A stale rocket copy outside every journal and
receipt, such as an earlier flight's source entity left on disk by a vanilla writer failure the mod cannot
observe (the R-032 class), can bring a package back after its receipt retired. (2) After retirement, a
per-file rollback or deletion of the receipt store, registry or transfer journal is undetectable. (3) OS
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
6. The recovery matching tolerance for consumed snapshot hashes.
7. The transaction-journal acknowledgement as a D0 precondition.
8. No online exit for REPAIR, and released receipts never retire online.

Not in this design: the fluid loader, linker and cargo (W11b3 onward); a new network channel or bay status
in the rocket flight menu; any change to ADR-049's terminal replay of packages the bay never handled.

Remaining risks: the checkpoint cost at a 16 MiB registry is unmeasured; the TRANSIT wait has no time limit
under storage failure; W11b2 is not integrated, so its interfaces here come from its design note and
branch `cf184331`; W11b1's intermittent BARRIER_UNSTABLE also slows D6 retries.

No current-version Required Gate is met by this note. Next: one independent read-only review, then W27b.
