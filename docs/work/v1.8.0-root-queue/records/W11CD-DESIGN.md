# W11c and W11d: rocket cargo loaders and unloaders

**Revision 1**, 2026-10-16. **Proposed design, not admission approval or runtime evidence.**
Assignment: [W79](../TASK-W79-CARGO-LOADERS-DESIGN.md); execution record: [W79](W79.md). AGENTS.md section 13
requires a read-only review of this note before any code.

**Baselines.**
- The shared branch is read at `c11fbaa8`, which holds the frozen [W11B3-DESIGN](W11B3-DESIGN.md) revision 2.
- W11b2 is read through Git only, at `646bf9ee`: continuation 18's `8ed38aa5` merged with shared `52d7d5b7`.
  The tank, rocket model, payload adapter, transfer-entity and disassembly classes cited below are
  byte-identical at `c11fbaa8`. Later W11b2 work is not presumed.
- Root's reservation decisions are read in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`,
  SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`, the same file W11b3 revision 2 read.
- The AR 1.12 devices are read from `Advanced-Rocketry/AdvancedRocketry` at the pinned commit
  `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, path `src/main/java/zmaster587/advancedRocketry/tile/infrastructure/`.
  The SHA-256 of all four files equals the [legacy inventory](../../v1.8.0-legacy-inventory.json). Nothing is copied.

**Notation.**
- `J` means `src/main/java/io/github/sunthemoon/advancedrocketrycommunity` at `646bf9ee`; every citation is
  reproducible with `git show 646bf9ee:<expanded path>`.
- "Design 2" is `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-2.md` at `646bf9ee`; it exists only on the
  W11b2 branch.
- Leaf IDs B3-* are W11b3's (revision 2, section 5). C-* are W11c's and D-* are W11d's, proposed here. Cut names
  are W11b3's: cut-N, cut-K, cut-E, cut-R, cut-O, cut-G, D2-C and D2-F.
- A **payload host** is the block entity that a snapshot payload materializes into at disassembly; it is the
  "single native cargo carrier" of ADR-065 section 3.1. A **drop carrier** is a device item that carries the
  device's resources (W11b3 section 3). The two are unrelated.
- The **cargo projection**, the **payload cursor** and the **identity rule** are defined in section 2.
- Names called proposed do not exist yet.

Authority: [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) sections 1,
3.1, 4.1, 4.1.1, 4.2 and 10; [W11A](W11A-DESIGN.md) sections 1, 3 to 6 and 8; W11b3 revision 2. Root owns
approval and every central document.

## 1. Scope against the ledger

| Ledger unit in the [content ledger](../../v1.8.0-content-ledger.csv) | Disposition |
|---|---|
| `block_variant:loader/5`; follower `block_entity:ARFluidLoader` | **W11c**: `rocket_fluid_loader` moves Fluid from its tank into one cargo tank of a docked rocket. |
| `block_variant:loader/4`; follower `block_entity:ARFluidUnloader` | **W11c**: `rocket_fluid_unloader` moves Fluid from one cargo tank of a docked rocket into its tank. |
| No row of its own (ADR-065 section 4.1) | **W11c**: the `pressurized_tank` payload host adapter, the cargo projection, the identity rule and the cargo kinds in the escrow core. They serve rows `/4` and `/5`. |
| `block_variant:loader/3`; follower `block_entity:ARRocketLoader` | **W11d**: `rocket_item_loader`, from its 4-slot buffer into one chest or barrel of the rocket. |
| `block_variant:loader/2`; follower `block_entity:ARRocketUnloader` | **W11d**: `rocket_item_unloader`, the reverse. |
| `libvulpes:java/itemLinker` | W11c and W11d add the four devices as first endpoints of W11b3's rocket-binding role, with no schema change. The row stays incomplete. |
| `block:monitoringStation` and its follower; the monitor and station-pad linker roles; comparator output; redstone launch | **W11e**. |
| `block:fuelingStation` (`fuel_loader`) and W11b3's `fluid_fuel_loader` | Not cargo devices. Propulsion banks are never cargo. |

The IDs are ADR-065 section 2's; block-entity type IDs equal block IDs.

**What the AR 1.12 devices did.** All four are LibVulpes hatches (`TileFluidHatch`, `TileInventoryHatch`) that
implement `IInfrastructure`. A player links one with the LibVulpes linker, or the assembling machine links its
connected infrastructure; the link range is 32 blocks (`getMaxLinkDistance`), and liftoff unlinks it
(`disconnectOnLiftOff`). Every server tick while linked, with no rate limit:
- the fluid loader drains its tank into every fluid tile of `rocket.storage.getFluidTiles()`, and the fluid
  unloader fills its tank from them;
- the item loader moves one stack from its 4 slots into the first empty or mergeable slot among
  `rocket.storage.getInventoryTiles()`, through the item capability or `IInventory`, skipping the guidance
  computer; the item unloader moves one stack the other way;
- a redstone output mode (ON, INVERTED or OFF) reports a full or empty rocket, a redstone input mode gates
  operation, and a six-side selector chooses the sides.

Two legacy defects are not reproduced. The item loader's capability branch calls `insertItem(i, stack, false)`
and then empties the hatch slot without using the returned remainder (`TileRocketLoader.java` lines 137 and
138). The item unloader's merge branch checks only `isItemValidForSlot`, not that both stacks are the same item
with equal tags (`TileRocketUnloader.java` lines 69 to 72).

**What v1.8 does not do:** redstone input, output or side selection; linking through the assembler; links
beyond ADR-065 section 4.2's 6-block endpoint rule; unbounded per-tick moves (the cap is 1,000 mB or one stack
per device per 20 ticks); generic capability access to every captured block entity, which ADR-065 section 4.1
forbids; any access to propulsion banks or to the satellite bay payload; modded chest subclasses (section 5.3);
a comparator output on the devices. **Not in this note:** W11b3, W11e, the bay slices, the gas charge pad,
ADRs, the risk register, status files and the ledger.

**Prerequisites.** W11b3's B3-U, B3-M, B3-A (the linker) and B3-E are merged first. Cargo admission is enabled
only after W11b3 section 1's part-1 closures (P1-F3, P1-F8, P1-N1 and P1-N2) and this note's evidence. Leaf
C-I (section 2.5) follows P1-F8 and P1-N2 because it edits the same transfer and disassembly code.

<div style="page-break-after: always;"></div>

## 2. What cargo is

### 2.1 Where cargo lives at `646bf9ee`

- A rocket entity holds one immutable `RocketStructureSnapshot` (`J/rocket/entity/RocketEntity.java:67`). It is
  set once, and a second set throws (`:135`); there is no setter. A snapshot carries at most 128 block-entity
  payloads (`J/rocket/RocketLimits.java:8`), each at most 262,144 bytes and 1,048,576 bytes in aggregate.
- The default payload hosts are `vanilla_container_v1` (a chest or barrel: `Items`, or `LootTable` and its
  seed, plus `CustomName` and `Lock`) and the satellite bay adapter (`J/rocket/forge/RocketBlockEntityAdapters.java:31`,
  `J/compat/rocket/RocketAdapterRegistry.java:82`). Any other block entity rejects assembly
  (`J/rocket/forge/ServerLevelRocketScanWorld.java:80`). **A rocket that contains a `pressurized_tank` cannot be
  assembled today, so no rocket carries Fluid cargo.**
- The content hash covers every payload (`J/rocket/model/RocketStructureSnapshot.java:141`). Propulsion amounts
  live in `RocketFuelState` in the flight data, not in the snapshot, so a fuel fill keeps the content hash.
  Design 2 section 5.1 says so and adds: "a future snapshot-changing action needs its own explicit binding
  update". A cargo operation is that action.
- No live cargo capability exists (ADR-065 section 1). Propulsion banks are `RocketFuelState` banks without
  block entities; no payload cursor can name them.

### 2.2 The cargo projection

A cargo operation changes one payload and therefore the content hash. Its rocket endpoint uses the existing
named projection of design 2 section 5.1, `ROCKET_COMPOSITE_V1 = hash("arce-escrow-rocket-1:" + L + ":" +
contentHash + ":" + FUEL_VECTOR_V1)`. The kind is reserved (`J/resourceescrow/EscrowSignatureKind.java:5`) and
refused today by the native binding (`EscrowNativeBinding.java:16`), the floor codec (`EscrowCodec.java:122`)
and READY heads (`EscrowFlightTables.java:19`). Rules:

- Cargo kinds sign the rocket endpoint with `ROCKET_COMPOSITE_V1`; fuel kinds keep `FUEL_VECTOR_V1`. Prefix
  chains require contiguous signatures (`EscrowPrefix.validateChains`, `EscrowPrefix.java:41`), so one chain
  cannot mix the two; admission refuses that case with a status (section 3.2).
- Apply also proves that propulsion is untouched: `FUEL_VECTOR_V1` before equals after.
- The binding's `invariant` field holds the invariant-envelope projection of the **after** state. That
  projection includes the content hash (`EscrowInvariant.java:15`), so the floor written at retirement
  (`EscrowFloors.java:74`) names the state it certifies. Classifying a rocket as BEFORE uses the composite
  before signature, which already covers the before content hash.
- **Legacy scalar-fuel rockets refuse cargo** (status `LEGACY_ROCKET`): `FUEL_VECTOR_V1` does not exist for
  them (`J/rocket/flight/RocketFuelState.java:84`). Disassembly still restores their chests. See Q3.
- **Receipt-backed rockets refuse cargo** (status `RECEIPT_BACKED`), meaning rockets with a READY head or
  settlement history. The idle gate compares the head's invariant, which contains the content hash, with the
  live rocket (`RocketEscrowGate.java:49`), and launch arming does the same (`EscrowFlightAuthority.java:51`).
  One cargo operation would leave such a rocket permanently non-idle and unlaunchable. See Q2.

The device sides keep W11A section 1's signature `arce-escrow-fluid-tank-1:` (device UUID and the native Fluid
signature) for fluid devices. Item devices get the proposed `arce-escrow-item-buffer-1:` over the device UUID
and the canonical digest of the 4-slot list.

### 2.3 The payload cursor and the preparation cursor

W11A section 8's "snapshot cursor" names two different things.

**The payload cursor (persisted)** identifies the one payload an operation addresses: the snapshot UUID `s`,
the position `p` relative to the snapshot origin, and the adapter ID `a`.
- **Selection** is server-side and deterministic, with no packet. Walk the snapshot's canonical block order
  (positions sorted by the snapshot constructor) and take the first payload whose adapter matches the device
  and that is eligible. The fluid loader takes the first tank holding the same Fluid with room, otherwise the
  first empty tank; the fluid unloader takes the first nonempty tank whose Fluid the device accepts; item rules
  are in section 5.3. This keeps the legacy iteration order but moves into one target per operation.
- **Stability.** Every cargo mutation keeps the snapshot ID (ADR-065 section 4.1). Relocation issues a new one
  (`relocated`, `RocketStructureSnapshot.java:215`), as does reassembly, and geometry never changes under one
  ID. A pending operation locks the rocket against flight, transfer and disassembly. The cursor is therefore
  valid for exactly one docked rocket instance. Recovery that finds another snapshot ID, no payload at `p`, or
  another adapter classifies the endpoint OTHER_STATE, which is REPAIR; it never retargets.
- **Persisted form.** The cursor exists only inside the operation payload, written in L1. Cargo payload schema
  1 is `{v: 1, cursor, fluid: {FluidName, Amount 1..1,000, Tag?}}` for Fluid and `{v: 1, cursor, stack: {id,
  Count, tag?}, from: byte, to: byte}` for items. The cursor takes at most 256 bytes, depth 2 and 8 nodes; the
  whole payload stays within the unchanged operation payload bound of 8,192 bytes, depth 12 and 384 nodes
  (`EscrowOperation.java:34`). A Fluid whose canonical stack and cursor exceed that bound is not movable as
  cargo (`CARGO_TOO_LARGE`) and stays where it is. No device persists a cursor; the menu shows the cursor of its
  own pending operation, or of the next selection, as scalar data.

**The preparation cursor (transient)** is ADR-065 section 4.1.1's bounded resumable preparation. It holds the
rocket UUID, snapshot ID, before content hash, block index and the bytes and visits charged so far, in RAM
only, and it is discarded on restart, unload, cancellation or server stop. It is used only when the preparer's
conservative bound for one reconstruction exceeds a tick's budget: one reconstruction per server tick,
16 MiB of charged bytes and 16,384 block visits. While it exists, a transient preparation lock makes
`RocketRuntime.resourceMutationAllowed`, launch and disassembly refuse; publication still revalidates.

### 2.4 The tank payload host and the cargo mutation

**Adapter `pressurized_tank_v1`** (proposed, leaf C-P):
- It supports exactly the block-entity type `advancedrocketrycommunity:pressurized_tank`. Its payload is `{tank:
  <TankSave root>}`: schema 1, at most 8,192 bytes, depth 16 and 1,024 nodes
  (`J/machine/tank/TankSave.java:16`). Capture refuses a tank whose `repairRequired()` is true. Capacity is not
  stored; it derives from the COMMON multiplier, as for a placed tank (`TankCapacity.BASE` 64,000 mB times 0.25
  to 4.0).
- Restore passes the root to `PressurizedTankBlockEntity.load`, followed by the existing capture-equality check
  in `RocketBlockEntityAdapters.restore`.
- It joins `defaults()` and `freeze()`, and its type is reserved against external registration, like chest,
  barrel and bay.
- Rockets that contain tanks become assemblable. That is a visible change; S1 checks that existing snapshots
  are unchanged.

**The cargo mutation** (proposed pure `RocketCargoMutation`) builds the after snapshot from the before snapshot,
the cursor and the frozen delta. It keeps the snapshot ID, dimension, origin, creation time, anchors, stats,
propulsion and every block except the one payload. It refuses unless geometry, stats, anchors and propulsion
are identical, the payload and aggregate bounds hold, and the snapshot codec round-trips. It is deterministic,
so L1 freezes the after digests, and apply and restart reconciliation recompute the after snapshot and compare.
No after snapshot is kept between capture and apply, which bounds memory to one reconstruction in flight.
Fluid room is computed from the COMMON capacity at capture. Apply never rechecks capacity, because an
over-capacity balance is legal in a tank; it checks only the frozen before state.

### 2.5 The identity rule (leaf C-I)

The transfer service recognizes a landed rocket as the authority of its COMMITTED record by snapshot ID **and
content hash** (`J/rocket/server/RocketTransferEntities.java:252-261`), and recovery election matches the same
way (`:419`). That record remains after landing until disassembly releases it (`RocketTransferService.java:424`,
called only from `RocketDisassemblyService.java:136,164`). After one cargo operation on a landed rocket:
- `resourceMutationAllowed` (`RocketTransferService.java:405`) refuses, because the record names a rocket that
  no longer matches, so no further fuel or cargo can move;
- disassembly leaves the record in place;
- recovery then sees the destination as missing and stops at FLOOR_CONFLICT: safe, but stuck until an offline
  repair.

Fuel never meets this, because fuel keeps the content hash. The proposed rule:
- add the derived `RocketStructureSnapshot.structureDigest()`, SHA-256 with prefix `arce-rocket-structure-1:`
  over the schema, dimension, origin, bounds, each block's position, state and payload adapter ID (not payload
  data), the anchors, stats and propulsion signature. It is cached in the immutable snapshot, never persisted,
  and its one pass is charged to the reconstruction budget;
- `isCommittedAuthority` and recovery's `matches` accept a rocket whose snapshot ID is equal and whose content
  hash is equal (today's rule), **or** whose structure digest is equal while its escrow section is bound to the
  current ledger with an epoch above 0.

Content can differ from the journal's snapshot only through an acknowledged escrow operation, which binds the
section and advances the epoch. Every election, discard and rebuild consults `EscrowRecoveryFence` first: a
rocket with a nonzero epoch is protected (`EscrowRecoveryFence.java:16`), and a floor refuses a kept rocket
whose epoch is below it (`:61`). A neutral rocket keeps the exact-hash rule. Transaction-world matching of
assembly and disassembly records (`ServerLevelRocketTransactionWorld.java:222-229`) is unchanged: a pending
transaction refuses cargo (`RocketTransactionExecutor.java:73`), and disassembly freezes the latest snapshot.
The rule does not cover the bay's package consumption (step D5 of [W27A](W27A-DESIGN.md) sections 4 and 8),
which rewrites its PREPARED transfer record and which W27d and W27f specify. The rule changes recovery
identity, so the read-only review of this note must cover it.

<div style="page-break-after: always;"></div>

## 3. Admission, escrow and settlement

### 3.1 Reused unchanged

- The runtime-owned `EscrowAdmissionAuthority` after B3-U, and the one delegate W11b3 registers. It is named
  PROPULSION but indexes the escrow ledger, whatever the operation kind. `tryReserve` keeps 64 per server, 8 per
  rocket and 1 per device, and there is no release call.
- `requireAdmission`'s five live-plus-retired markers per rocket (`EscrowSettlementCapacity.java:11`) and
  `preflight(prospective, candidate)` with Root-R6's headroom and Root-R8's ceiling charging, as W11b2 pins them
  at merge. A cargo operation is charged exactly like a fill: `OP_BYTES` plus a 2 KiB receipt, and a named floor
  when it is transfer-guarded. **Fuel and cargo share the five markers of one rocket.**
- B3-E's production ledger creation and creation baseline: the first admitted cargo operation creates the
  ledger in the same order.
- The writer, exact L1 and L2 acknowledgements, the own-store, force and readback barrier, the reconciliation
  scheduler (8 per tick), floors, recovery fences and the shutdown drain.
- W11b3 section 2's eligibility list, step 1, word for word: escrow storage available; claim, exact binding and
  equal owners; same Level, FULL chunks with loaded entity sections, within 6 blocks; a stationary ASSEMBLED,
  FUELED or LANDED rocket with no actual or assigned passengers and nothing riding it; no pending assembly
  recovery; the transfer-journal rule; no bay or flight hold, ARMED entries included; both mutation layers.

### 3.2 New

1. **Kinds.** `EscrowOperation.Kind` gains `FLUID_CARGO_LOAD` and `FLUID_CARGO_UNLOAD` (W11c) and
   `ITEM_CARGO_LOAD` and `ITEM_CARGO_UNLOAD` (W11d), the names W11A section 1 reserved. A load has the device as
   source and the rocket as destination; an unload has the rocket as source. Validation, binding and floors
   assume today that the rocket is the destination (`EscrowOperation.java:46`, `EscrowNativeBinding.java:45`,
   `EscrowFloors.retire` at `:64`). Leaf C-K adds a rocket-side accessor used by the binding, floors, guard
   checks and the capacity preview. Fluid endpoints use unit `TANK`; W11d adds `CONTAINER`. Native rocket keys
   still collapse to PRIMARY (`EscrowEndpoint.java:25`), so fuel and cargo share one whole-rocket epoch.
2. **One live cargo operation per rocket.** A cargo operation is admitted only when no ledger record, queued or
   captured operation, or marker other than retired settlement markers names the rocket. There are no cargo
   chains in v1.8, and fuel and cargo, or load and unload, never mix on one rocket. W11A section 5 already
   forbids opposite directions and prefix contiguity forbids mixed projections; refusing chains also avoids
   holding earlier links' after snapshots in memory. W11b3's fill admission gains the mirror refusal while a
   cargo operation names the rocket, reported as its `ROCKET_BUSY`. A device refused with
   `OTHER_TRANSFER_PENDING` receives the rocket's next turn through a RAM token, so a fuel loader and a cargo
   device cannot starve each other. See Q4.
3. **Entry point.** The proposed `EscrowCoordinator.prepareTransfer` generalizes W11b3's `prepareFill` to accept
   a fill capture or a cargo capture: the same pure check, then the same owner-thread admission. `prepareFill`
   becomes a call to it, and W11b3's tests stay unchanged. A load mirrors a fill: a SOURCE marker on the device
   and a DESTINATION marker on the rocket. An unload reverses the roles: a SOURCE marker on the rocket and a
   DESTINATION marker on the device. Each side keeps its one-marker or five-marker bound.
4. **Capture.** In one capture: the complete rocket envelopes E and E' (`EscrowNativeEnvelope.rocket` with the
   after snapshot and after wrapper), the device's before and after envelopes, the cursor, both composite
   signatures, the unchanged fuel vector, owner, snapshot ID and journal guard. The counted markers come from
   the same capture and are checked against `beforeWrapper` (Root's N4). Today `requireAdmission` binds the
   marker section only for `FUEL_VECTOR_V1` (`EscrowSettlementCapacity.java:111`), and the preview charges the
   five-marker allowance only for it (`:71`); both extend to native rocket endpoints of either kind.
5. **Apply.** A one-use server-thread permit, as design 2 section 5.1 specifies for fills, lets `RocketEntity`
   publish the after snapshot and the next wrapper and marker together, through a new permit-gated method and
   never a public setter; the device publishes its tank or buffer delta and marker. Apply recomputes the after
   snapshot and compares it with the frozen digest. The rocket visual republishes because the content hash
   changed (ADR-065 section 11.1); C-B measures the cost (Q6).
6. **Proofs.** Readback decodes the rocket record with the bounded `AnvilRecordReader`, recomputes the content
   hash and composite signature off-thread on the immutable decode, and the server thread compares digests.
   The device record is checked as in W11b3.
7. **One fair queue.** B3-E's loader queue becomes one server-wide resource queue for the fuel loader and the
   four cargo devices: at most 64 commits per tick, and at most one whole-snapshot reconstruction per tick
   (16 MiB, 16,384 visits). Fuel entries charge no reconstruction, and a maximum-size rocket cannot be starved
   by small requests.
8. **Its own gate.** Cargo admission has its own switch, disabled until Root or the owner enables each kind
   after its evidence (Q11).

### 3.3 Settlement of a cargo operation

**Normal settlement** keeps the K0 to K10 sequence. Restart reconciliation recomputes the after snapshot when
the rocket is BEFORE and rolls it forward once; a mixed state rolls the BEFORE side forward; AFTER on both sides
goes to the barrier. The device side is W11b3's.

**Operator settlement (O1 to O4)** needs extension, because today:
- `EscrowSettlementIntent` refuses a component with a member that is not a fill (`EscrowSettlementIntent.java:98`);
- the receipt amount counts fills only (`EscrowSettlementTombstones.java:26`);
- survivor observation, normalization, pruning, head creation and receipt projection accept only
  `FUEL_VECTOR_V1` (`EscrowSettlementIntent.java:50`, `EscrowSettlementTombstones.java:81`,
  `EscrowSettlementPruning.java:161`, `EscrowReceiptCodec.java:43`).

New: cargo members are allowed. The receipt's `payload_amount` holds mB or an item count, and a new wire field
`payload_kind` joins the closed receipt dictionary; fuel receipts omit it, so existing receipts are unchanged.
Root accepted that dictionary under decision L2, so this addition needs Root, and
`scripts/inspect_escrow_receipt.py` and its test change in step. Composite survivors are observed with the
composite signature and invariant. At O4, a composite survivor's READY head is written in the
`FUEL_VECTOR_V1` projection (the fuel signature at that epoch and the live invariant), because heads accept no
other kind; the rocket is then receipt-backed, and cargo refuses it (section 2.2). Abandon records the frozen
payload amount, hash and loss, and a returning abandoned identity stays frozen, as today.

**Reservations.** W11c and W11d follow the decisions in force when W11b2 merges; at the time of writing those
are Root-R1 to Root-R9. Cargo adds no region type and changes no bound: its regions are an operation and, when
guarded, a named floor. Root-R6's headroom applies to every cargo admission, so the capacities W11b2 pins hold
for any mix of fuel and cargo. C-K repeats the R6 and R8 requirement tests with cargo candidates.

<div style="page-break-after: always;"></div>

## 4. Disassembly and launch: the latest cargo, exactly once

Cargo is always in exactly one place, the device store or the addressed payload of the rocket's current
snapshot, and it moves only through one acknowledged operation. Each cargo operation advances the rocket's
whole epoch, which is never reset (W11A section 3, K10).

| Stop point (both directions) | Durable state | After restart |
|---|---|---|
| Before L1, including during preparation | Old ledger; both sides BEFORE | Nothing durable; the preparation is discarded; device and payload unchanged. |
| L1 replaced, before or after its callback | PREPARED record with the cursor and the frozen after digests | Reconciliation recomputes the after snapshot and rolls each BEFORE side forward once. |
| Apply published but unsaved | Same as above | Same as above. |
| One side stored | That side AFTER: epoch plus one, marker, after signature | The other side rolls forward from L1; there are never two copies. |
| Both stored, before L2 | AFTER on both sides | Barrier proof, then L2. |
| L2 replaced, callback or release | Records retired | Stale markers are dropped. |

A duplicate cannot arise: the rocket's classification compares the composite signature, which includes the
content hash of the whole snapshot, so an AFTER rocket is never classified BEFORE; the device compares its own
tank or buffer signature.

**Disassembly** requires both mutation layers idle (`RocketDisassemblyService.java:115`), so it never sees an
operation in flight. It materializes `rocket.snapshot()`, which holds the latest payloads, through the
transaction journal (`:177`), which freezes that snapshot; tanks restore through `pressurized_tank_v1` and
chests through `vanilla_container_v1`. Crash windows inside disassembly are P1-F8's, whose durable
disassembly-under-way record is a prerequisite. The quote binds the content hash (`:189`), so a quote taken
before a cargo operation is refused, and a repeat cannot materialize again because the entity is gone. A
device bound to the rocket shows `STALE_BINDING` afterwards.

**Materializing an older snapshot** cannot recreate unloaded cargo (ADR-065 section 1):
1. The **assembly journal**: a pending entry refuses cargo (region overlap in `hasPendingRecovery`), and
   `EscrowRecoveryFence.transaction` refuses assembly recovery over a rocket whose epoch is not 0 (`:75`).
2. The **transfer journal's** source and destination snapshots: a rocket with a nonzero epoch is protected, so
   KEEP_SOURCE and rebuild paths stop at FLOOR_CONFLICT. When a record names the rocket, the operation is
   transfer-guarded and its retirement writes a floor at the after epoch (`EscrowFloors.retire`), so
   REBUILD_DESTINATION and a stale kept copy are refused (`EscrowRecoveryFence.java:61`). Composite floors need
   C-K, because the floor codec refuses them today.
3. With C-I, the landed rocket stays its record's authority, so ordinary play never routes into those
   recoveries.
4. Rolling back one entity file remains outside the claim (R-031, R-032).

**Launch** requires idle. The new transfer record freezes the latest snapshot (ADR-065 section 4.1), and an
escrow-bound rocket launches through ARMED with a bound, pruned wrapper (`EscrowFlightAuthority.java:35`), as
after a fill. Relocation issues a new snapshot ID, so no operation or cursor spans a flight. The transfer
record holds two maximum-size snapshots (`RocketFlightLimits.java:22`), and cargo admission refuses an after
snapshot beyond `RocketLimits`, so a successful load can never make a rocket unlaunchable.

<div style="page-break-after: always;"></div>

## 5. The devices

### 5.1 Shared contract

W11b3 revision 2 sections 3 and 4 apply unchanged: client actions are vanilla block use, entity use and menu or
container clicks, with no custom packet and no protocol change; every check of its "Client-to-server checks";
the 8-block actor and 6-block endpoint distances; the binding and linker codecs; one binding change per player
per 10 ticks with the owner online; recovery continues after the owner logs out; the stricter passenger rule
(`ROCKET_BUSY`); automation acts on the device's persisted ownership, never on FakePlayer privilege. The
differences:

| Surface | Fluid loader and unloader (W11c) | Item loader and unloader (W11d) |
|---|---|---|
| Classes (proposed) | `RocketFluidCargoBlock`, `RocketFluidCargoBlockEntity` (the block fixes the role: LOAD for `/5`, UNLOAD for `/4`), `RocketFluidCargoMenu`, a client-only screen | `RocketItemCargoBlock`, `RocketItemCargoBlockEntity`, `RocketItemCargoMenu`, a client-only screen |
| Root | NBT key `arce_rocket_fluid_cargo`: schema 1, at most 32 KiB, depth 20 and 2,048 nodes. It holds the owner, a fresh placement UUID, the role (must equal the block's), the binding, one tank of 0 to 16,000 mB in a native envelope of at most 8 KiB, and the escrow section (at most 256 bytes and one marker). | `arce_rocket_item_cargo`: the same, with 4 slots in place of the tank (bounds in section 5.3). |
| Capability | `FLUID_HANDLER`, the same on every side; the loader only fills and the unloader only drains, under W11b3's rules (claimed, valid root, loaded chunk, unlocked, side-effect-free simulation, zero for stale, off-thread or reentrant calls). | `ITEM_HANDLER` over the 4 slots; the loader only inserts and the unloader only extracts; the same rules, and `isItemValid` applies the stack bounds. |
| Menu | W11b3's scalar container data, plus the role and the cursor (relative x, y and z). Owner only, within 8 blocks. A held container exchanges through `TankContainerExchange.plan` (ADR-064's one-unit rule) in both devices. | 4 vanilla slots and the player inventory, owner only, usable both ways by the owner; status, binding and cursor (x, y, z and slot) as scalar data. |
| Drop | W11b3's drop carrier (owner and tank, at most 12 KiB, depth 18, 1,024 nodes) and its owner-on-placement rule. | The buffer spills as ordinary item drops, as vanilla containers do, and the device item carries nothing, so the placer owns a placed device. ADR-065 section 4.2 allows one resource carrier only (Q7). |
| Protection | An escrow-locked or unsupported root protects the device, as R-038 and W11b3 do: player removal refuses before loot, explosions skip it, it drops nothing, and extraction refuses. `onlyOpCanSetNbt()` returns true, and the item refuses a `BlockEntityTag`. | The same. |

### 5.2 Fluid rules (W11c)

Each attempt moves `min(1,000, source mB, destination room)` in integer mB, at most once per device per 20
ticks, with no catch-up after unload or restart. On the rocket, room is the COMMON tank capacity minus the
payload amount; on the device, 16,000 mB minus its amount. A destination accepts when it is empty or holds the
same Fluid with an equal tag. For a 64,000 mB tank at 63,251 mB and a device with 1,000 mB, 749 mB move and 251
stay. One operation per rocket at a time makes this a cap, not a throughput. An unknown or unsupported tank
payload is skipped by selection; if nothing else is eligible, the status is `UNSUPPORTED_DATA`. Intake from
containers, players or pipes is ordinary native storage, outside D3-A.

### 5.3 Item stacks (W11d)

- **Bounds.** A registered item ID of at most 128 ASCII characters, not air; a count from 1 to
  `min(64, getMaxStackSize())`; `ItemStack.save` output of at most 4,096 bytes, depth 16 and 512 nodes, without
  a `ForgeCaps` key; and a lossless round trip (`ItemStack.of(tag).save` equal to `tag`). A stack that fails
  stays where it is (`CARGO_TOO_LARGE` or `INVALID_DATA`).
- **Comparison and signing.** Two stacks merge only when they are the same registered item with equal tags
  (`ItemStack.isSameItemSameTags`) and equal canonical NBT digests; display names never decide. The payload
  hash is the existing canonical `RocketNativeResources.signature`; the device signature is section 2.2's.
- **Containers.** Only `vanilla_container_v1` payloads whose block state is `minecraft:chest`,
  `minecraft:trapped_chest` or `minecraft:barrel` are cargo targets, with 27 slots. Other `ChestBlockEntity`
  subclasses that the adapter captures (up to 54 slots) refuse with `NO_CARGO_STORAGE`, because the payload
  alone cannot prove their slot count (Q9). A payload with a `LootTable` refuses with `UNRESOLVED_LOOT`
  (ADR-065 section 4.1). `CustomName` and `Lock` are preserved; `Lock` is not enforced, as for vanilla hoppers.
  The after `Items` list is re-sorted by slot, as the adapter's whitelist does.
- **Partial transfer.** A load takes the first nonempty buffer slot (0 to 3). Its target is the first container
  slot, in slot order, that holds the same item and tag with room, otherwise the first empty slot; it moves
  `n = min(buffer count, getMaxStackSize() - target count)`. An unload takes the first nonempty container slot
  whose stack the buffer can accept, preferring a buffer slot with the same item and tag and room, otherwise the
  first empty buffer slot, and moves `n = min(container count, room)`. The payload freezes the item, `n`, the
  tag and both slots. The remainder stays in its slot, locked with the device until release; a crash rolls
  exactly `n` forward, never the whole stack. ADR-065 section 4.2 allows one such stack mutation per device per
  20 ticks.

### 5.4 Status

The proposed `RocketCargoStatus` keeps W11b3's members `ADMISSION_DISABLED`, `ESCROW_FULL`,
`LEDGER_UNAVAILABLE`, `TRANSFER_JOURNAL_UNPROVED`, `ESCROW_PENDING`, `UNBOUND`, `STALE_BINDING`,
`WAITING_UNLOADED`, `OUT_OF_RANGE`, `NOT_OWNER`, `ROCKET_BUSY`, `UNSUPPORTED_DATA`, `INVALID_DATA`, `READY`,
`PREPARING` and `TRANSFERRING`, with W11b3's meanings. It adds `DEVICE_EMPTY`, `DEVICE_FULL`, `NO_CARGO`,
`CARGO_FULL`, `NO_CARGO_STORAGE`, `INCOMPATIBLE_CARGO`, `CARGO_TOO_LARGE`, `UNRESOLVED_LOOT`,
`OTHER_TRANSFER_PENDING`, `RECEIPT_BACKED` and `LEGACY_ROCKET`. Keys are
`message.advancedrocketrycommunity.rocket_cargo.<lowercase_member>` in en_us and zh_cn, for example "Another
transfer on this rocket is pending; nothing moved." / "该火箭有另一项转移待完成；未移动任何货物。" and "Preparing
escrow; no cargo has moved yet." / "正在准备托管；尚未移动货物。". `ESCROW_PENDING` never claims that nothing
moved after L1.

### 5.5 Recipes, loot, translations, art and provenance

- **Recipes** (proposed, shaped; every input exists): the fluid loader is `" H "` over `"LMT"`, and the fluid
  unloader is `"LMT"` over `" H "`, with H `minecraft:hopper`, L `liquid_io_board`, M `machine_casing` and T an
  empty `pressurized_tank` through B3-D's empty-tank ingredient. The item devices use the same shapes with I
  `item_io_board` and C `control_board` in place of L and T. They cannot collide with `fluid_fuel_loader`'s
  shapeless recipe. C-D and D-D verify reachability in the integrated recipe graph.
- **Loot**: one loot table per block. Fluid devices drop through `getDrops` with the drop carrier; item devices
  drop the plain item and spill their buffer, unless protected.
- **Translations**: "Rocket Fluid Loader" / "火箭流体装载器", "Rocket Fluid Unloader" / "火箭流体卸载器", "Rocket
  Item Loader" / "火箭物品装载器" and "Rocket Item Unloader" / "火箭物品卸载器", plus the status keys, through the
  language providers.
- **Art**: original 16x16 textures drawn in code by a proposed `V180RocketCargoArt`, with distinct port marks for
  load and unload, and models, blockstates, loot, recipes, advancements and text under
  `src/generated/v1.8/resources` through a proposed `V180RocketCargoData`. The linker's art is W11b3's.
- **Provenance**: before authoring, add `docs/provenance/v1.8.0-c17a-rocket-fluid-cargo-preauthoring.md` (W11c) and
  `docs/provenance/v1.8.0-c17a-rocket-item-cargo-preauthoring.md` (W11d), listing NEW code and text and
  GENERATED output, MIT, author, reviewer and date, as [docs/08](../../../08-ASSET-LICENSE-AND-PROVENANCE.md)
  requires. After generation add the matching `.json` records with generator and target hashes, which
  `scripts/validate_v180_provenance.py` checks, and run W53's generated-art screen; every texture must be
  CLEAR. No upstream or LibVulpes asset is used. The asset plan has no row for the `/2` to `/5` textures, so
  nothing may be imported; a later substitution needs its own UPSTREAM_AR_MIT row and belongs under
  `src/imported/v1.8/resources`.

<div style="page-break-after: always;"></div>

## 6. Verification and implementation leaves

All entries are **planned**; this note produces no evidence. The cost basis is W11b3's: about 20 seconds per
JVM start, a forced-stop cut costs three starts, a full GameTest run takes 15 to 18 minutes, and lock hours are
(starts x 20 s + 15 minutes + any GameTest run) x 1.5. The unit suite needs no lock. Each implementation leaf
runs each new test class alone, then the whole unit suite.

**W11c leaves** (after B3-E; enabling also needs the P1 closures):

| Leaf / dependency | Builds | Acceptance (A0 is pure Java, A1 is GameTest) | JVM starts, lock hours |
|---|---|---|---|
| C-I / P1-F8, P1-N2 | `structureDigest()`; the identity rule in `isCommittedAuthority` and `matches`. | A0: the digest is equal across cargo mutations and changes with geometry, state, adapter or propulsion. A1: a landed rocket stays its record's authority after a gated cargo mutation; a neutral rocket with a different content hash is not; a stale duplicate meets FLOOR_CONFLICT before any discard; disassembly then releases the landed record. | 1 GameTest run; 0.5 h. |
| C-K / B3-E | The cargo kinds, the cargo payload and cursor codecs, the rocket-side accessor, `ROCKET_COMPOSITE_V1` in binding, floors and settlement, the receipt field and script, exclusivity, `prepareTransfer`. | A0: codec bounds, future and unknown versions and fields; direction checks; composite accepted for cargo kinds only; composite floors; settlement accept and abandon with a cargo member, receipt amount and kind, the head written in the fuel projection; exclusivity and the turn token; same-capture markers for composite; the five-marker preview; **Root-R6 and Root-R8 requirement tests with cargo candidates, failing without headroom or with preview-only charging**; the receipt-dictionary Python test. | 1 GameTest regression run; 0.5 h. |
| C-P / C-K | `pressurized_tank_v1`; `RocketCargoMutation`; selection; the preparation budget, cursor and lock; the permit-gated snapshot publication. | A0: adapter round trip and bounds; 1, 749 and 1,000 mB; over-capacity, incompatible and unknown Fluid; payload and aggregate bound refusals; propulsion unchanged; same snapshot ID and new hash; deterministic recompute; selection order; budget accounting. A1: assembly with tanks; disassembly restores once; a cargo tank and a propulsion bank holding the same Fluid, where only the tank moves. | 1 GameTest run; 0.5 h. |
| C-A / C-P | The two fluid devices, menus, screen, drop carrier, linker roles, registrations and loot; admission not wired. | A1 as B3-A, for both devices: capability simulation, stale and reentrant handles, owner, range and unload refusals, menu replay, the carrier owner rule, protected removal by player, explosion and command, non-operator NBT, cut-G2's A1 part. | runData once, 1 GameTest run; 0.5 h. |
| C-E / C-A, C-I | The shared fair queue, captures, apply permits, proof validators, production and restart reconciliation for cargo kinds, behind the cargo gate. | A1: two devices on one rocket serialize; fuel and cargo alternate; the first cargo operation creates the ledger; every refusal compares both envelopes and the queue and counts; real production recovery recomputes the after snapshot. | 1 GameTest run; 0.5 h. |
| C-D / C-A | `V180RocketCargoArt` and `V180RocketCargoData`, recipes, text, both provenance files for W11c. | runData twice with a clean diff; references, recipe graph, loot, registry IDs, provenance hashes, a CLEAR screen. S1: packaged dedicated-server sidedness; an old world with chest rockets unchanged; a tank rocket restarted twice; future and malformed device roots and tank payloads preserved; cut-G2's S1 part. | runData twice, 1 GameTest run, about 6 S1 starts; 1 h. |
| C-S1 to C-S5 / C-E, C-D, the P1 leaves | Nothing: S2 evidence, below. | Below. | 222 starts; 3.75 h. |
| C-V / C-D | Nothing. | V1 on a real GPU: labels, Fluid and status display, cursor display, scales, resource reload, the rocket visual after a cargo operation. V2 with two real clients: ownership, two devices on one rocket, `PREPARING` and pending synchronization, the passenger case, disconnect. | Shared with D-V in one native session; 2 to 3 h. |
| C-B / C-E | A benchmark harness only. | The budgets below, with native reference-load results kept apart from microbenchmarks, and the cycle time per device. | About 3 h; the docs/17 soak is shared with B3-B (Q12). |

**W11d leaves** reuse W11c's escrow path:

| Leaf / dependency | Builds | Acceptance | JVM starts, lock hours |
|---|---|---|---|
| D-K / C-K | The item kinds, `CONTAINER`, the stack codec and the item-buffer signature. | A0: stack bounds, `ForgeCaps` refusal, round trip, comparison, the signature pin, item settlement receipts. | None. |
| D-P / D-K, C-P | The container mutation, slot selection and partial moves. | A0: 27-slot scope, `LootTable` refusal, `CustomName` and `Lock` kept, partial counts, canonical re-sort, bounds. A1: a chest rocket loads and unloads; disassembly restores once. | 1 GameTest run; 0.5 h. |
| D-A / D-P | The two item devices, menus, screen, spill drops, linker roles. | A1 as C-A, plus spill-once on break and an oversized-NBT stack that stays in the buffer. | runData once, 1 GameTest run; 0.5 h. |
| D-E / D-A, C-E | Item entries in the shared queue and reconciliation. | A1: competing devices, a partial move across restart, refusal comparisons. | 1 GameTest run; 0.5 h. |
| D-D / D-A | Data, art, text and both provenance files for W11d. | As C-D. | runData twice, 1 GameTest run, about 6 S1 starts; 1 h. |
| D-S1, D-S2 / D-E, D-D | Nothing: S2, below. | Below. | 57 starts; 1.25 h. |
| D-V, D-B | Nothing. | In C-V's session and C-B's harness. | About 1 h on top of C-B. |

**S2 cut families that apply.** Each cut stops on both sides of its durable acknowledgement, including after
replacement and before the callback, and restarts twice. The control totals follow W11A section 7: a
5,000 mB device and an empty 64,000 mB cargo tank, moving 1,000 mB; for unloads, the reverse. Every leaf pins
the source commit, the JAR and the hooks, and compares raw native quantities, markers and epochs.

| Leaf | Cuts | Required outcome | Estimate |
|---|---|---|---|
| C-S1, normal | cut-K0 to cut-K10 for a load and for an unload, 14 cuts per direction as B3-S2 counts them, with the cut-K0 stop taken during preparation. | One copy of the cargo at every cut; the rocket-as-source path is new in W11c, so both directions run in full. | 28 cuts, 84 starts; 1.1 h. |
| C-S2, reconciliation and storage | D2-C2, D2-C5, D2-C6 and D2-C7 per direction; cut-E1 (own store); cut-E2 (a newer save, and a rocket record near the inline-sector limit); cut-E4 (definite L1, UNKNOWN L1 and UNKNOWN L2); one cut-E3 and one cut-E5 representative. | Recomputed roll-forward; observable failures block retirement; unobservable ones stay diagnostic residuals. | 16 cuts, 48 starts; 0.8 h. |
| C-S3, recovery and roots | cut-R1 with a composite floor at 255 and 256; cut-R2 with a cargo-mutated rocket and a stale duplicate; cut-R3 with a missing cursor, an OTHER payload and a relocated device; cut-G1 for a future device root and an invalid tank payload; cut-G3 with cargo L1 in flight and L2 UNKNOWN. | FLOOR_CONFLICT or REPAIR without overwrite; no historical fallback; roots preserved. | 9 cuts, 27 starts; 0.6 h. |
| C-S4, settlement | Both sides of cut-O1 to cut-O4 for ACCEPT_CURRENT on an unload; two ABANDON cuts; O-veto. | Composite survivors checkpointed; head in the fuel projection; never 6,000 mB from the 5,000 mB fixture. | 11 cuts, 33 starts; 0.65 h. |
| C-S5, creation, flight and disassembly | cut-N1 with a first cargo operation; D2-F1, D2-F6 and D2-F8 after a cargo operation; P1-F8's three windows (before restoration, before record removal, before release) with a cargo-mutated rocket; after an unload, transfer recoveries that would materialize an older snapshot (REBUILD_DESTINATION, KEEP_SOURCE and REBUILD_SOURCE). | The latest cargo restored exactly once; no recreation. | 10 cuts, 30 starts; 0.6 h. |
| D-S1, items | cut-K2, cut-K3, cut-K4, cut-K5 and cut-K9 per direction; a partial move stopped after apply and after one side is stored. | Exactly `n` items move; the remainder is intact. | 12 cuts, 36 starts; 0.7 h. |
| D-S2, items | cut-R3 for a changed container and a `LootTable` payload; cut-O1 and cut-O3; one disassembly and one rebuild case; cut-G1 for item roots. | As C-S3 to C-S5. | 7 cuts, 21 starts; 0.55 h. |

cut-C1 and cut-C2 do not apply, because cargo never chains; A1 covers the refusal. cut-N0, cut-N2 and cut-N3 are
B3-S1's shared creation code, and only cut-N1 is repeated. cut-G2 is A1 and S1 coverage.

**Benchmarks and budgets.** ADR-065 section 4.1.1 and W11A section 6 apply unchanged: one whole-snapshot
reconstruction per tick at 16 MiB and 16,384 visits, at most 64 commits per tick, 16 MiB of detached memory and
at most 8 reconciliations per tick. Exercise 2,048 blocks, 128 payloads, 1 MiB of NBT, 8 KiB Fluid tags and
4 KiB item tags; capture, recompute at apply, off-thread readback hashing; visual republication after each
operation; a mixed fuel and cargo queue with a maximum-size rocket among small requests; 64 synthetic competing
records. [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) applies: Xmx at most 4 GiB, RSS at most 5.5 GiB, mean
MSPT at most 25 ms, P95 at most 50 ms and P99 at most 100 ms, and no watchdog, OOM or forced load. If a budget
fails, lower the admitted cadence or subdivide preparation; never relax a budget or skip a check.

**Execution.** Targeted A0 and the full `gradlew.bat --offline test` need no lock. A full `clean build`,
runData, GameTest, S1, S2, the native server and benchmarks take `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm`
after checking 10 GiB free on C: and D:. Run the unchanged GameTest log checker. Missing native or client
evidence stays NOT_RUN.

<div style="page-break-after: always;"></div>

## 7. Proposed merge text and decisions requiring Root

**Proposals only.** Root records the scope before the implementation merges and adds commit and evidence
references once verified. This note accepts no risk and closes no finding.

### R-035 proposed additions

> **Cargo scope (W11c, W11d).** The escrow ledger also holds cargo operations between four new devices and a
> docked rocket's cargo payloads: pressurized tanks for Fluid, and vanilla chests and barrels for items. Each
> cargo operation publishes a new rocket snapshot with the same snapshot ID and a new content hash. An
> escrow-bound rocket whose content differs from its transfer record is still that record's authority when its
> snapshot structure is unchanged (the identity rule); a neutral rocket keeps the exact-hash rule, and every
> recovery still consults the escrow fence first.
>
> **Limits.** One live cargo operation per rocket, with no chains, and no mixing of fuel and cargo or load and
> unload. Fuel and cargo share the five live-plus-retired markers of one rocket and the reservation charges of
> Root-R1 to Root-R9; no bound changes. Rockets with settlement history or a READY head, and legacy scalar-fuel
> rockets, refuse cargo devices until they are disassembled and reassembled. A Fluid or item stack whose
> canonical form exceeds the cargo payload bound, a container with an unresolved loot table, and modded chest
> subclasses are not movable as cargo and stay in place.
>
> **Save refusal.** An unsupported device root (invalid, future, or naming an unregistered Fluid or item) is
> protected as R-038 protects a bay: player removal refuses, explosions skip it, it drops nothing. The scope is
> that one device, until its cause goes. An unsupported tank payload inside a snapshot follows the existing
> rocket quarantine. A rocket entity record that grows past vanilla's inline sector limit after cargo loads
> stalls the barrier (cut-E2), and the resources stay locked until repair; observable failures block
> retirement.
>
> **Recovery.** As the row states: whole-world offline repair on a copy, never one store.

### ADR-065 proposed implementation note

> W11c adds `rocket_fluid_loader` and `rocket_fluid_unloader`, the `pressurized_tank_v1` payload host and the
> cargo projection `ROCKET_COMPOSITE_V1`; W11d adds `rocket_item_loader` and `rocket_item_unloader` for vanilla
> chest and barrel payloads. Each device holds one 16,000 mB Fluid or 4 slots of at most 64 items and 4 KiB per
> stack, and moves at most 1,000 mB or one stack per 20 ticks into or out of one payload chosen
> deterministically by the server, never a propulsion bank. A cargo operation publishes a whole new snapshot with
> the same snapshot ID through the shared fair queue and the runtime-owned authority, with Root-R6's headroom and
> Root-R8's charging; test admission is never used. Disassembly restores the latest cargo exactly once, and
> rocket epochs and transfer floors stop an older snapshot from recreating unloaded cargo. Cargo admission stays
> disabled until the cargo S2 evidence, the part-1 closures and the benchmarks pass, and the Root or owner
> decision is recorded with the source and the JAR. R-035 records the scope; R-031, R-032 and the Required Gates
> are unchanged.

### Open questions, with recommendations

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Adopt the identity rule (section 2.5), and when? | **Yes**, as leaf C-I after P1-F8 and P1-N2, reviewed with this note. Without it, the first unload at a destination strands the rocket in FLOOR_CONFLICT. The alternative, rewriting the COMMITTED record's snapshot with every cargo operation as W27A's D5 rewrites a PREPARED record, makes each operation a three-store transaction with another checked journal write and cut set, all queued on the journal writer that P1-F3 is still fixing. |
| Q2 | Receipt-backed rockets: refuse cargo, or let a cargo L2 advance the READY head? | **Refuse** in v1.8. A head-advance write is new authority over heads and the launch check; disassembly and reassembly clear the history. |
| Q3 | Legacy scalar-fuel rockets: refuse, or add a scalar composite projection? | **Refuse.** Disassembly already restores their chests; a scalar projection is new authority for little gain. |
| Q4 | One live cargo operation per rocket, with no chains? | **Yes.** It bounds memory and removes the chain cuts. Chains can be a later, separately reviewed change. |
| Q5 | Deterministic payload selection or a player choice? | **Deterministic**, as legacy; a choice would need a packet and a protocol change. |
| Q6 | The rocket visual republishes after every cargo operation (ADR-065 section 11.1 keys it by content hash). | **Keep it** and measure in C-B. If budgets fail, lower the cadence first; keying the visual by the structure digest would need its own ADR note. |
| Q7 | Item devices: spill drops or a drop carrier? | **Spill**, as vanilla containers; there is no custom item NBT to police, and the owner is the placer. |
| Q8 | Automation direction | **Directional**, as legacy: loaders only accept input and unloaders only give output; the owner's menu works both ways. |
| Q9 | Modded `ChestBlockEntity` subclasses | **Refuse**: a payload cannot prove their slot count. A later adapter may add them. |
| Q10 | The receipt wire field `payload_kind` (decision L2's closed dictionary) | **Accept** it; fuel receipts omit it, so existing receipts are byte-identical. |
| Q11 | One admission switch for fuel and cargo, or one per kind? | **One per kind**: fuel can be enabled after W11b3's evidence without waiting for cargo. |
| Q12 | Benchmark soak | **Share** one docs/17 soak for fuel and cargo after W11d, if B3-B has not yet run it. |

W79 delivers only this design and its record. It changes no code, tests, assets, ADR, risk register, ledger,
status, build or workflow files, and it satisfies no runtime Gate.
