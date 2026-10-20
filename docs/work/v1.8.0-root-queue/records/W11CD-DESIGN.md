# W11c and W11d: rocket cargo loaders and unloaders

**Revision 2**, 2026-10-16. **Proposed design, not admission approval or runtime evidence.**
Assignments: [W79](../TASK-W79-CARGO-LOADERS-DESIGN.md) and
[W79R](../TASK-W79R-CARGO-DESIGN-REVISION.md); execution record: [W79](W79.md#revision-2).
This revision applies Root's seven review decisions. Root must re-check H1 and M1 to M4 before freezing it.
The review is `D:/GitHub/ARCE-Task-Evidence/root-queue/W79-review/REVIEW.md`, SHA-256
`fcf3d7eccddcd9e392066d1d2dedd515ea955994bb3f9957ae37627e8829aafa`; the decisions are
`D:/GitHub/ARCE-Task-Evidence/root-queue/W79-review/ROOT-DECISIONS.md`, SHA-256
`1e0265016e277a1706966f6cf8ae37eacc40d730b7568edcae9633a3d7547e17`, dated 2026-10-16.

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

## 2. What cargo is

### 2.1 Where cargo lives at `646bf9ee`

- A rocket entity holds one immutable `RocketStructureSnapshot` (`J/rocket/entity/RocketEntity.java:67`). It is
  set once, and a second set throws (`:135`); there is no setter. A snapshot carries at most 128 block-entity
  payloads (`J/rocket/RocketLimits.java:8`), each at most 262,144 bytes and 1,048,576 bytes in aggregate.
- The default payload hosts are `vanilla_container_v1` (a chest or barrel: `Items`, or `LootTable` and its
  seed, plus `CustomName` and `Lock`) and the satellite bay adapter (`J/rocket/forge/RocketBlockEntityAdapters.java:31`,
  `J/compat/rocket/RocketAdapterRegistry.java:82`). The scan checks tags before payload adapters
  (`J/rocket/forge/ServerLevelRocketScanWorld.java:70-84`). The production `rocket_movable` tag includes chest
  and barrel, but neither `pressurized_tank` nor trapped chest (`J/datagen/RocketBlockTagsProvider.java:24-35`).
  An adjacent untagged tank is BOUNDARY and is not captured; a boundary at the scan origin instead fails
  BLOCK_NOT_MOVABLE (`J/rocket/scan/RocketStructureScanTask.java:128-140`). If a data pack makes the tank
  movable, its missing adapter rejects assembly. **There is no built-in tank cargo support; both the tag and
  the adapter are required.** This corrects revision 1's unconditional assembly-rejection explanation.
- The content hash covers every payload (`J/rocket/model/RocketStructureSnapshot.java:141`). Propulsion amounts
  live in `RocketFuelState` in the flight data, not in the snapshot, so a fuel fill keeps the content hash.
  Design 2 section 5.1 says so and adds: "a future snapshot-changing action needs its own explicit binding
  update". A cargo operation is that action.
- No live cargo capability exists (ADR-065 section 1). Propulsion banks are `RocketFuelState` banks without
  block entities; no payload cursor can name them.

### 2.2 The cargo projection

A cargo operation changes one payload and therefore the content hash. Here `L` is the logical rocket UUID.
Its rocket endpoint uses the existing
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
  are in section 5.3. This preserves deterministic selection, not the exact legacy slot priority: the old
  loader iterates target slots before buffer slots, while section 5.3 selects the first buffer stack first.
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

**Effective eligibility.** One side-effect-free predicate, shared by selection and admission, validates the
native stack or Fluid, cursor, framed cargo payload, complete operation including its worst-case O1 settlement
reservation, and complete before/after device and snapshot envelopes. It checks every byte, depth and node
limit, the native round trips, the whole encoded snapshot's 1 MiB ceiling and the combined reservation before
sequence allocation, queueing, reservation or mutation. It repeats on final capture if an input changed.
An inner object passing its own limit does not imply cargo eligibility: a 4 KiB item at depth 16 or 512 nodes
can exceed the framed payload's depth 12 or 384 nodes, and an 8 KiB Fluid leaves no framing space. A refusal
preserves both endpoints and reports `CARGO_TOO_LARGE` (or `INVALID_DATA` for malformed data), without
truncation or a new save refusal. C-K/C-P and D-K/D-P test inner-passes-outer-fails byte, depth and node
boundaries, including O1 and full device/snapshot overhead; no bound is enlarged to fit local maxima.

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
- C-P also generates a current-version `rocket_movable` tag extension for `pressurized_tank` under
  `src/generated/v1.8/resources`, with `replace: false` and preauthoring plus generated-hash provenance.
  It does not edit the sealed v0.5 provider/output. Root owns any build-resource selection needed to include
  the extension without losing earlier values. C-P's assembly test uses the real production tag set, not a
  fixture tag, and proves tank capture and unchanged chest/barrel inclusion; the packaged tag is checked too.
- Trapped chest joins that extension only if C-P proves capture/restore equality with the unchanged
  `VanillaContainerRocketAdapter` on an actual vanilla trapped chest. Its `instanceof ChestBlockEntity`
  support (`:30`) is static evidence, not that test. Otherwise trapped chest remains data-pack-only and the
  generated extension omits it; no adapter broadening is authorized. Existing captured supported payloads
  remain restorable in either case. Tank assembly is a visible change; C-D's S1 checks old snapshots unchanged.

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
- disassembly is refused by the mutation gate, leaving the record in place;
- recovery then sees the destination as missing and stops at FLOOR_CONFLICT: safe, but stuck until an offline
  repair.

Fuel never meets this, because fuel keeps the content hash. The proposed rule:
- add the derived `RocketStructureSnapshot.structureDigest()`, SHA-256 with prefix `arce-rocket-structure-1:`
  over the schema, dimension, origin, bounds, each block's position, state and payload adapter ID (not payload
  data), the anchors, stats and propulsion signature. It is cached in the immutable snapshot, never persisted,
  and its one pass is charged to the reconstruction budget;
- Inside the existing owner, logical/physical identity, placement and state checks, `isCommittedAuthority`
  and recovery's `matches` use `sameSnapshotId && (sameContentHash || (sameStructureDigest &&
  currentLedgerBinding && epoch > 0))`. Equal snapshot ID is mandatory for both alternatives.

Content can differ from the journal's snapshot only through an acknowledged escrow operation, which binds the
section and advances the epoch. Every election, discard and rebuild consults `EscrowRecoveryFence` first: a
rocket with a nonzero epoch is protected (`EscrowRecoveryFence.java:16`), and a floor refuses a kept rocket
whose epoch is below it (`:61`). A neutral rocket keeps the exact-hash rule. Transaction-world matching of
assembly and disassembly records (`ServerLevelRocketTransactionWorld.java:222-229`) is unchanged: a pending
transaction refuses cargo (`RocketTransactionExecutor.java:73`), and disassembly freezes the latest snapshot.
The rule does not cover the bay's package consumption (step D5 of [W27A](W27A-DESIGN.md) sections 4 and 8),
which rewrites its PREPARED transfer record and which W27d and W27f specify. The rule changes recovery
identity, so the read-only review of this note must cover it.

**Recognition, not reconstruction authority (I1).** Pending operations and markers still fence actions.
A protected discarded candidate or protected non-first keeper vetoes duplicate removal; a stale exact-hash
copy preferred by election cannot silently displace a changed protected copy. Source recovery cannot
overwrite a credited source, and a named floor rejects a missing, wrong or behind keeper. Governed native
records remain routed and held before legacy recovery, including missing or terminal ARMED authority;
`permit` and `actionAllowed` enforce this independently of `matches`. Structure equality, ledger binding
and a nonzero epoch never prove a new ARMED serial, release acknowledgement or permission to rebuild a
missing identity. Design 2's replay exclusion and missing-source hold remain unchanged.
Passenger reconnect keeps the existing settled-authority lookup and the current manifest, not the stale
arrival manifest. C-E tests a previously disembarked passenger reconnecting after cargo mutation without
reboarding or recreating an old snapshot. C-I uses fixtures locally; section 6 assigns the real transaction.

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
   device cannot starve each other. Tokens are bounded by registered loaded work, discarded on unload or
   lifecycle end, and never override a hold or become durable authority. See Q4.
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

**Operator settlement (O1 to O4).** Root decision 1 retains Design 2's settlement representation. C-K admits
cargo members where `EscrowSettlementIntent.java:98` currently accepts fills only; it does not change native
rocket settlement observations to composite. Ordinary cargo prefix classification remains composite.

| Boundary | Frozen rule for cargo settlement |
|---|---|
| O1 observation | A native rocket observation keeps `FUEL_VECTOR_V1`, its fuel-vector signature, wrapper epoch/markers and the invariant-envelope digest containing the current snapshot UUID and content hash. Its `quantity` remains the observed propulsion total, never cargo mB or mixed item counts. The endpoint reference retains the operation's composite before/after signatures; those are distinct from the observation's settlement state. A fluid-device observation keeps its native tank signature and quantity; an item-device observation signs the entire typed 4-slot buffer and uses quantity 0 as an unused diagnostic, not an empty-buffer or zero-loss assertion. |
| Neutral survivor | O1 freezes the observed unbound wrapper and the prescribed `(ledger_id, epoch=0, markers=[])` wrapper. Only after acknowledged O1 may normalization bind it; resources, snapshot, owner and identity stay unchanged. Restart accepts exactly the old or prescribed wrapper and repeats binding idempotently. O2 proves only the normalized form. A veto or any other difference retains the hold. |
| O2 | One complete bounded native endpoint decode supplies both projections: recompute the cargo composite for prefix classification and the fuel-vector plus invariant for the frozen settlement observation. Never compare their digest strings as the same kind or invert a composite hash. ACCEPT_CURRENT can preserve an O1-chosen OTHER state; it must equal the frozen choice, not be forced into the original operation's AFTER state. Prove both survivors for unload (rocket source and device destination), or the exact surviving/absent endpoints for ABANDON. Typed item-NBT, identity, wrapper, position, occupancy and invariant checks stay mandatory. |
| O3 and O3J | Retain jointly valid own-store/force/readback proofs, the exact intent and the checked journal lease. Proof frontiers equal `intent.checkpointed()` in the settlement projection, as `EscrowCoordinator.java:112-139` requires. O3 installs the immutable unreleased receipts; O3J checks the required journal removal before O4. Neither a cargo kind nor C-I bypasses a proof or changes this order. |
| Restart after O3 replacement before callback | Checked startup readback/baseline acknowledgement recovers the durable root, intent and receipts. Reacquire O2 proofs from durable native stores against that same frozen choice and re-establish O3J's checked lease; do not take a new live choice or trust a lost callback. A missing, changed or unprovable endpoint remains held. O4 is then the same pure derivation from acknowledged root plus intent, not live fuel or snapshot re-observation. |
| O4 READY | For each chosen native survivor, use the direction-independent rocket-side member accessor, not today's destination-only lookup (`EscrowSettlementTombstones.java:88`). Derive logical ID and physical ID from the checkpointed endpoint, owner from that frozen member, anchor from the smallest member operation UUID, release generation from the exact O4 write, epoch from the checkpointed state, kind `FUEL_VECTOR_V1`, signature from that state's fuel signature and invariant from that observation. Carry forward the prior head's issued/completed serials, or 0/0 if absent; outcome is READY. These are exactly `EscrowFlightTables.Head`'s fields. A missing rocket produces no successor head; its negative protection remains. |
| Pruning and launch | Only the acknowledged released group `(ledger, decision digest, member IDs, O4 generation)` authorizes pruning. Rebuild bounded group work from released receipts on restart; remove only that group's retired prefix, preserving epoch, resources and later/live markers. Check the same fuel signature plus cargo-sensitive invariant and exact survivor identity; item-device pruning checks the whole buffer signature. A stop before wrapper saving simply repeats pruning. The rocket is now receipt-backed and refuses new cargo; its later launch still needs F1's complete pruned checkpoint, ARMED and the exact governed PREPARED acknowledgement. |

**Receipts and accounting.** Keep intent schema 2, receipt schema 3 and the closed positional receipt
dictionary. `payload_amount` is the frozen fuel-fill liability only for `FLUID_PROPULSION_FILL`; it remains
0 for cargo and floor retirement, not a cargo quantity or an assertion of no loss. `payload_hash` commits to
the exact cargo delta and cursor. Receipt `before_choice` holds O1 observations; `survivors` and `missing`
are derived from their checkpointed forms, including normalization, exactly as `EscrowReceiptCodec` does
today. No separate mutable projection is introduced. `accounting_status` stays UNKNOWN for ACCEPT_CURRENT,
ABANDON and floor retirement; no scalar fuel field is relabelled as cargo accounting. A returning abandoned
identity stays frozen. Signed per-resource accounting is outside v1.8. `payload_kind` is unnecessary under
this representation and is not added (Q10); any future need reopens Root's closed-dictionary decision.
C-K pins existing fuel and floor-retire receipt bytes and inspector output; D-K verifies that nonempty item
buffers with diagnostic quantity 0 cannot be mistaken for missing or empty survivors.

**Reservations.** W11c and W11d follow the decisions in force when W11b2 merges; at the time of writing those
are Root-R1 to Root-R9. Cargo adds no region type and changes no bound: its regions are an operation and, when
guarded, a named floor. Root-R6's headroom applies to every cargo admission. Five live-plus-retired markers
remain the upper admission allowance, not proof that every cargo candidate fits. The unchanged settlement
layout adds no second rocket signature or receipt field, but the framed payload, endpoint names and embedded
O1 can still exceed the complete 12 KiB operation, 2 KiB receipt, 1 KiB head or 1 MiB root ceilings.

**Size measurement prerequisite.** C-K and D-K must remeasure both directions with the real encoders, before
their dependent integration leaf: 0 to 4 retained markers plus the candidate, the longest permitted endpoint
names and sticky reason, the worst modified-UTF actor name, maximal admitted payload, neutral normalization,
ACCEPT_CURRENT and each ABANDON survivor, then O3, O4, pruning and governed launch. Report intent and complete
operation/receipt byte, depth and node maxima, both full flight tables, and Root-R6/R8/R9 transitions; pin old
fuel receipt bytes. Confirm whether a five-marker cargo case actually fits and refuse a sixth before any
side effect. C-K repeats R6/R8 tests with cargo and the direction-independent preview. The real-encoder
remeasurement is **NOT_RUN in this no-JVM design task**; five-marker usability and effective cargo capacity
are not yet established. If a candidate fails, eligibility refuses it without raising bounds; an allowance
or format change returns to Root. Fuel-only measured capacities are not cargo evidence.

**R40 amendment (Root, 2026-10-20).** W113 measured that, under these charges, a transfer-guarded receipt does not fit on any of the mod's own Levels: cargo of every kind (worst, an item unload at Tau Ceti g, 2,089 of 2,048 bytes) and also a guarded fuel fill (2,061 bytes on the Moon). Root decision R40 keeps the 2,048-byte receipt, the layout and every saved format, and changes two charges ([TASK-W114](../TASK-W114-RECEIPT-CHARGE-R40.md)): a new settlement intent's actor name must pass the 1.20.1 login rule (1 to 16 characters, code points 33 to 126), so admission charges 16 bytes instead of 128 NULs; and a component that contains cargo is charged one rocket marker, in `requireAdmission` and the shared preview alike, because admission already refuses cargo with a retained marker or another record on the rocket. Fuel keeps five markers. The worst guarded receipt at Tau Ceti g is then 1,557 bytes for cargo and 1,833 for fuel, and every encodable Level ID fits. A size refusal of the candidate's own component maps to `CARGO_TOO_LARGE` in C-E and D-E; `ESCROW_FULL` stays for a full root (W113 item 4). **R41** (Root, 2026-10-20, from W114-review): the one-marker charge belongs to the rocket a cargo member names, not to the whole settlement component, because a cross-kind endpoint UUID alias can join a fuel record on another rocket into a cargo component; that rocket keeps five markers. W114 with W114F implements R40 and R41 (merged `4153204e`).

## 4. Disassembly and launch: the latest cargo, exactly once

There is one spendable authority; mixed durable prefixes are locked until reconciled. Raw native files can
temporarily contain two apparent copies or a deficit. For a 1,000 mB load from a 5,000 mB device, destination-only
storage is 5,000 + 1,000 mB and source-only storage is 4,000 + 0 mB; neither is spendable while pending.
Reconciliation reaches 4,000 + 1,000 mB exactly once. Each operation advances the rocket's whole epoch, which
is never reset (W11A section 3, K10). Tests distinguish pre-recovery raw totals from post-recovery conservation.

| Stop point (both directions) | Durable state | After restart |
|---|---|---|
| Before L1, including during preparation | Old ledger; both sides BEFORE | Nothing durable; the preparation is discarded; device and payload unchanged. |
| L1 replaced, before or after its callback | PREPARED record with the cursor and the frozen after digests | Reconciliation recomputes the after snapshot and rolls each BEFORE side forward once. |
| Apply published but unsaved | Same as above | Same as above. |
| One side stored | That side AFTER: epoch plus one, marker, after signature; raw totals may disagree. | Both endpoints stay locked; the other side rolls forward from L1 once, restoring conservation before release. |
| Both stored, before L2 | AFTER on both sides | Barrier proof, then L2. |
| L2 replaced, callback or release | Records retired | Stale markers are dropped. |

A second spendable credit cannot arise: the rocket's classification compares the composite signature, which includes the
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
3. With C-I, an otherwise valid landed rocket remains recognizable as its record's authority. This changes
   recognition only; it grants no rebuild, cancellation or source-overwrite permission. Governed records
   never fall through to legacy recovery, even when their ARMED authority is missing or terminal.
4. Rolling back one entity file remains outside the claim (R-031, R-032).

**Launch** requires idle. The new transfer record freezes the latest snapshot (ADR-065 section 4.1), and an
escrow-bound rocket launches through ARMED with a bound, pruned wrapper (`EscrowFlightAuthority.java:35`), as
after a fill. Relocation issues a new snapshot ID, so no operation or cursor spans a flight. The transfer
record holds two maximum-size snapshots (`RocketFlightLimits.java:22`), and cargo admission validates the
whole encoded after snapshot against `RocketLimits`. This prevents a snapshot-size overflow in the transfer
record; it is not a guarantee of launch availability. Entity external-record barriers (section 7), capacity,
IO, missing authority and the other launch checks can still hold the rocket.

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
  stays where it is (`CARGO_TOO_LARGE` or `INVALID_DATA`). These are local maxima; every transfer also passes
  section 2.3's single eligibility predicate for all enclosing operation, device and snapshot bounds.
- **Comparison and signing.** Two stacks merge only when they are the same registered item with equal tags
  (`ItemStack.isSameItemSameTags`) and equal canonical NBT digests; display names never decide. The payload
  hash is the existing canonical `RocketNativeResources.signature`; the device signature is section 2.2's.
- **Containers.** Only `vanilla_container_v1` payloads whose block state is `minecraft:chest`,
  `minecraft:trapped_chest` or `minecraft:barrel` are cargo targets, with 27 slots. Other `ChestBlockEntity`
  subclasses that the adapter captures (up to 54 slots) refuse with `NO_CARGO_STORAGE`, because the payload
  alone cannot prove their slot count (Q9). A payload with a `LootTable` refuses with `UNRESOLVED_LOOT`
  (ADR-065 section 4.1). `CustomName` and `Lock` are preserved; `Lock` is not enforced, as for vanilla hoppers.
  The after `Items` list is re-sorted by slot, as the adapter's whitelist does.
  Normal trapped-chest assembly is conditional on C-P's unchanged-adapter test and production tag extension
  (section 2.4); otherwise only already captured data-pack-enabled trapped chests are eligible.
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

## 6. Verification and implementation leaves

All entries are **planned**; this note produces no evidence. The cost basis is W11b3's: about 20 seconds per
JVM start, a forced-stop cut costs three starts, a full GameTest run takes 15 to 18 minutes, and lock hours are
(starts x 20 s + 15 minutes + any GameTest run) x 1.5. The unit suite needs no lock. Each implementation leaf
runs each new test class alone, then the whole unit suite.

**W11c leaves.** Every C-* leaf requires merged B3-U/B3-M/B3-A/B3-E and Root's revision-2 freeze;
the table lists its additional direct prerequisites, whose dependencies are transitive. Root records the
section 7 protection scope before C-P/C-A or D-A implements it. Production admission remains disabled.
P1-F3, P1-F8, P1-N1 and P1-N2 are required for C-E and every real transaction/flight/disassembly evidence
leaf, not for a pure fixture claim. Root's build-resource integration for C-P's tag must precede its packaged
tag check. Leaf-local fixtures never stand in for the later production transaction.

| Leaf / dependency | Builds | Acceptance (A0 is pure Java, A1 is GameTest) | JVM starts, lock hours |
|---|---|---|---|
| C-I / P1-F8, P1-N2 | It adds `structureDigest()` and the grouped recognition predicate. | A0/A1 use explicit before/after snapshot fixtures, not cargo devices. They check stable structure versus changed geometry/state/adapter/propulsion, mandatory snapshot ID, neutral mismatch, protected duplicate veto and governed routing. Real mutation, reconnect and crash-safe disassembly belong to C-E/C-S5. | It needs 1 GameTest run, about 0.5 h. |
| C-K / No additional leaf. | It adds fluid cargo kinds/cursors, the rocket-side accessor, composite bindings/floors, exclusivity and `prepareTransfer`; settlement retains section 3.3's representation. | A0 covers codecs, direction, typed projections, frozen O4 derivation without live inputs, normalization/pruning, ACCEPT_CURRENT and both ABANDON survivors, unchanged receipt bytes/dictionary, turn-token lifetime and same-capture markers. It produces section 3.3's real-encoder size report and five-marker verdict. R6/R8 tests fail without headroom or with preview-only charging; R9 still permits reserved retirement. | It needs 1 GameTest regression run, about 0.5 h. |
| C-P / C-K and Root's pre-implementation risk entry. | It adds the tank host, mutation, selection, bounded preparation and permit-gated publication, plus the v1.8 production tag extension and its provenance from section 2.4. | A0 covers 1/749/1,000 mB, over-capacity/incompatible/unknown Fluid, every nested bound, unchanged propulsion/geometry/ID, new hash, deterministic recomputation and budget accounting. A1 uses mutation/adapter fixtures to capture and restore the after payload and distinguish cargo from same-Fluid propulsion. Real production-tag assembly and the conditional trapped-chest round trip are tested here; these are not crash-safe transaction/disassembly evidence. | It needs runData twice, 1 GameTest run and the packaged tag check, about 0.75 h. |
| C-A / C-P and the recorded device-protection scope. | It adds both fluid devices, menus/screen, carrier, linker roles, registration and loot, with admission unwired. | A1 tests local capabilities, simulation/stale/reentrant calls, ownership/range/unload, menu replay, carrier ownership, protected removal, non-operator NBT and cut-G2. It also tests raw preservation, work-budget oversize, observed native veto and Level saturation under section 7; command replacement remains a residual, not prevented salvage. | It needs runData once and 1 GameTest run, about 0.5 h. |
| C-E / C-A, C-I, C-K's size report and all four P1 closures. | It integrates the fair queue, capture/apply permits, typed proofs, production and restart reconciliation behind the per-kind gate. | A1 tests real load/unload and O1-O4, both unload survivor proofs, neutral normalization/veto, typed readback and pruning, serialization/fairness, first-ledger creation and refusal side effects. It runs C-I's real stale PREPARED/both-entity, reconnect and governed missing/terminal-authority cases; exactly-once latest-cargo disassembly requires P1-F8. It covers cancellation and two successive ordinary cargo-mutated flights, including successor mutation. | It needs 1 GameTest run, about 0.5 h. |
| C-D / C-A, C-P's tag/provenance and B3-D's empty-tank ingredient. | It adds W11c art, data, recipes, text and final provenance, preserving C-P's tag entries. | It runs runData twice with a clean diff, reference/recipe/loot/registry/provenance/art checks. S1 checks packaged sidedness and production tags, unchanged old chest rockets, two restarts of a tank snapshot fixture, protected roots and cut-G2. Actual cargo transaction restarts belong to C-S*. | It needs runData twice, 1 GameTest run and about 6 S1 starts, about 1 h. |
| C-S1 to C-S5 / C-E, C-D and the integrated prerequisites in the mapping below. | They produce S2 evidence only. | They run the enumerated cuts below. | They need 270 starts, about 4.2 h. |
| C-V / C-E, C-D and D-E/D-D for the joint session. | It produces client evidence only. | V1 checks labels, Fluid/status/cursor display, scales, reload and cargo visual refresh. V2 checks ownership, competing devices, preparation/pending synchronization, passengers and disconnect. | It shares one native session with D-V, about 2 to 3 h. |
| C-B / C-E, C-D and D-E/D-D for the mixed cargo run; B3-B for the joint soak. | It adds a benchmark harness only. | It measures the budgets below, native reference load separately from microbenchmarks and each device's cycle time. | It needs about 3 h excluding the shared docs/17 soak. |

**W11d leaves** reuse W11c's escrow path:

| Leaf / dependency | Builds | Acceptance | JVM starts, lock hours |
|---|---|---|---|
| D-K / C-K. | It adds item kinds, `CONTAINER`, the stack codec and buffer signature. | A0 covers local/nested limits, ForgeCaps refusal, round trip, equality and signature pins, item settlement/UNKNOWN semantics and the real-encoder size report of section 3.3. | It needs no native start. |
| D-P / D-K, C-P. | It adds container mutation, slot selection and partial moves. | A0 covers 27 slots, LootTable refusal, preserved names/locks, partial counts, canonical order and nested bounds. A1 uses explicit load/unload after-snapshot fixtures and adapter restoration; it makes no real-transaction or crash-safe disassembly claim. | It needs 1 GameTest run, about 0.5 h. |
| D-A / D-P and Root's recorded item-protection scope. | It adds both item devices, menus/screen, spill drops and linker roles, with admission unwired. | A1 repeats C-A's local security/protection checks, spill-once and unchanged oversized-NBT buffers. | It needs runData once and 1 GameTest run, about 0.5 h. |
| D-E / D-A, C-E and D-K's size report. | It adds real item transactions to the shared queue and reconciliation. | A1 checks competing devices, typed item-NBT O2/readback, both directions, partial moves/restart, normalization/veto, O3J/O4/pruning, both ABANDON survivors and refusal comparisons. Real latest-cargo exactly-once disassembly inherits C-E's explicit P1-F8 prerequisite. | It needs 1 GameTest run, about 0.5 h. |
| D-D / D-A, C-D. | It adds item data/art/text and provenance. | It repeats C-D's resource/S1 checks with item snapshot/device fixtures, not a claim about an unwired transaction. | It needs runData twice, 1 GameTest run and about 6 S1 starts, about 1 h. |
| D-S1, D-S2 / D-E, D-D and the integrated prerequisite mapping. | They produce S2 evidence only. | They run the enumerated item cuts below. | They need 90 starts, about 1.5 h. |
| D-V / D-E, D-D, C-V. | It produces client evidence only. | It checks item tags/counts, partial moves, ownership and synchronized state in C-V's joint session. | The joint-session estimate includes it. |
| D-B / D-E, D-D, C-B and B3-B for the joint soak. | It extends C-B's harness with item fixtures. | It measures admitted versus rejected NBT fixtures and mixed-load costs on the integrated build. | It adds about 1 h to C-B, excluding the shared soak. |

**S2 cut families that apply.** One enumerated cut is one forced stop followed by two restarts (three JVM
starts). A stated before/after pair counts as two cuts; an after-write cut means replacement before callback,
not a graceful stop. A single representative does not imply both sides were tested. The control totals follow W11A section 7: a
5,000 mB device and an empty 64,000 mB cargo tank, moving 1,000 mB; for unloads, the reverse. Every leaf pins
the source commit, the JAR and the hooks, and compares raw native quantities, markers and epochs.

| Leaf | Cuts | Required outcome | Estimate |
|---|---|---|---|
| C-S1, normal | cut-K0 to cut-K10 for a load and for an unload, 14 cuts per direction as B3-S2 counts them, with the cut-K0 stop taken during preparation. | Raw mixed-save totals are recorded while both endpoints are locked; after reconciliation, exactly one transfer is spendable and the total is conserved. Both directions run in full. | 28 cuts, 84 starts; 1.1 h. |
| C-S2, reconciliation and storage | D2-C2, D2-C5, D2-C6 and D2-C7 per direction; cut-E1 (own store); cut-E2 (a newer save, and a rocket record near the inline-sector limit); cut-E4 (definite L1, UNKNOWN L1 and UNKNOWN L2); one cut-E3 and one cut-E5 representative. | Recomputed roll-forward; observable failures block retirement; unobservable ones stay diagnostic residuals. | 16 cuts, 48 starts; 0.8 h. |
| C-S3, recovery and roots | cut-R1 with a composite floor at 255 and 256 (2); cut-R2 with a cargo-mutated rocket and a stale duplicate (1); cut-R3 with a missing cursor, an OTHER payload and a relocated device (3); cut-G1 for a future device root and an invalid tank payload (2); cut-G3 with cargo L1 in flight and L2 UNKNOWN (2). | FLOOR_CONFLICT or REPAIR occurs without overwrite or historical fallback; roots are preserved. | 10 cuts, 30 starts; 0.65 h. |
| C-S4, settlement | For unload ACCEPT_CURRENT: O1 before/after (2), neutral normalization before/after publication (2), O2 before/after each of the rocket-source and device-destination proofs (4), O3 before/after (2), O3J before/after (2), O4 before/after (2), pruning before/after wrapper store (2). ABANDON separately omits each endpoint (2). O-veto with destination saved and a normalization veto add 2. | O3-after specifically restarts from durable data only, reacquires exact O2/O3J proofs, completes O4 and pruning, and performs a governed launch. Every variant retains fuel-vector plus invariant settlement authority, cargo-sensitive equality and UNKNOWN loss; 6,000 mB must never become available from 5,000 mB. | 20 cuts, 60 starts; 0.9 h. |
| C-S5, creation, flight and disassembly | cut-N1 (1); D2-F1/F6/F8 after cargo (3); P1-F8 before restoration/record removal/release (3); unload followed by REBUILD_DESTINATION/KEEP_SOURCE/REBUILD_SOURCE attempts (3); stale PREPARED with both physical entities (1); disembarked-passenger reconnect (1); governed record with missing and terminal authority (2); successor cargo mutation and checkpoint between two completed ordinary cargo-mutated flights (1); exact-source cancellation after cargo (1). | Latest cargo materializes once; protected stale copies cannot win or be discarded unchecked. Governed records never enter legacy recovery. Both flights complete with fresh governed authority, and cancellation retains the current cargo without historical reconstruction. | 16 cuts, 48 starts; 0.8 h. |
| D-S1, items | cut-K2, cut-K3, cut-K4, cut-K5 and cut-K9 per direction; a partial move stopped after apply and after one side is stored. | Exactly `n` items move; the remainder is intact. | 12 cuts, 36 starts; 0.7 h. |
| D-S2, items | cut-R3 for changed container and LootTable (2); O1 after (1); O2 before/after each typed item-device and rocket proof on unload (4); O3 after (1); O3J after (1); O4 after (1); pruning after wrapper store (1); neutral normalization after publication and its veto (2); each ABANDON survivor (2); one P1-F8 disassembly, one rebuild refusal and cut-G1 item root (3). | Typed NBT/counts/remainders are identical to the frozen choice through readback, durable restart, release and pruning. O3-after continues through O4 and a governed launch with no live replacement choice. Quantity 0 is not interpreted as empty cargo or known zero loss. | 18 cuts, 54 starts; 0.85 h. |

**Changed-boundary and reuse mapping.** A reuse claim must name the integrated source SHA, JAR hash, test/cut
ID and evidence path before admission. No such integrated cargo evidence exists in this design task; the
following reuse prerequisites remain NOT_RUN until Root pins them, or the indicated tests are rerun.

| Boundary | Local and integrated coverage, or exact required reuse |
|---|---|
| Ordinary prefix/apply, both save orders and L1/L2 | C-P/D-P supply mutation fixtures only. C-E/D-E run real D2-C0 through D2-C8 A1 cases. C-S1 covers both-direction K boundaries; C-S2 repeats D2-C2/C5/C6/C7 with recomputation; D-S1 repeats the item-changing boundaries. The other item boundaries use the same writer/barrier implementation as C-S1/C-S2 only after the integrated build proves the typed adapter in D-E. |
| Composite binding/floor, direction and capacity | C-K/D-K real-encoder measurements and refusal tests cover R6/R8/R9, before/after invariants and unload's rocket-side accessor. C-S3 covers composite saturation and missing/OTHER/stale identities. B3-S5's unchanged ledger missing/replaced/behind and distinct/coalesced floor cases must be pinned on the integrated build. |
| Neutral survivor normalization and veto | C-E/D-E cover old versus normalized wrapper and mismatches in A1. C-S4 cuts both sides of publication and O2; D-S2 repeats item normalization and veto. Neither is replaced by fuel-only evidence. |
| O2 survivor/absence, O3, O3J, O4 and pruning | C-S4 explicitly covers both unload survivor proofs and both sides of O3/O3J/O4/pruning. D-S2 covers typed item-NBT readback, both survivor proofs and every after-write boundary; D-E covers their before-write refusal cases and the load-role counterpart. Both S2 O3-after cases run restart-to-O4/pruning/launch from durable data. ABANDON proves each absence separately. |
| C-I recognition, reconnect, replay and cancellation | C-I is fixture-only. C-E integrates real cargo with stale PREPARED/both entities, protected non-first keeper, changed snapshot ID, disembarked reconnect and missing/terminal governed authority. C-S5 repeats the named native cuts, exact-source cancellation and successor mutation across two cargo-mutated flights. B3-S6's remaining D2-F0 through D2-F8 acknowledgements and P1-N2/P1-F9 cases require exact integrated evidence; the governed dispatcher and proof order are unchanged. |
| Latest-cargo disassembly | C-E/D-E require P1-F8 and real devices; C-S5 repeats its three windows and D-S2 the item representative. No C-P/D-P restore fixture is evidence for this crash-safe claim. |
| Ledger creation and unchanged infrastructure | C-S5 repeats cut-N1 with cargo. Reuse B3-S1's exact cut-N0/N2/N3 results for the unchanged creation writer/baseline. C-S2 repeats E3/E5 representatives; remaining shared storage failure/shutdown cases require B3-S4/B3-S6 integrated results, with no change to own-store/force/readback or UNKNOWN handling. |
| Protected roots and native save scope | C-A/D-A exercise preservation, work-budget excess, observed incompatibility and 256-coordinate saturation in A1; C-D/D-D cover S1 and cut-G2. C-S3/D-S2 repeat cargo root recovery. Reuse B3-S6 cut-G1/G3 native veto/saturation/failed-close evidence only for the unchanged GuardedChunkSaves/store lifecycle, not the new cargo codecs. |
| Non-applicable chains | cut-C1/cut-C2 do not apply because cargo never chains; C-K/C-E/D-E prove chain, mixed-kind and opposite-direction refusal. Existing fuel chains remain covered by B3-S3 and are not removed. |

The enumerated matrix is **90 fluid cuts plus 30 item cuts: 120 cuts and 360 JVM starts**, rather than
revision 1's 279 starts. It costs about 5.6 S2 lock hours using the stated formula across seven leaves,
excluding prerequisite reruns. Separately plan 12 S1 starts, 10 GameTest runs and 8 runData invocations
(two now belong to C-P); Gradle daemon reuse is not an extra fixed JVM count. The joint V1/V2 session,
benchmarks and docs/17 soak are additional sessions, not hidden inside 360. None of these estimates is
runtime evidence or permission to shorten a test.

**Benchmarks and budgets.** ADR-065 section 4.1.1 and W11A section 6 apply unchanged: one whole-snapshot
reconstruction per tick at 16 MiB and 16,384 visits, at most 64 commits per tick, 16 MiB of detached memory and
at most 8 reconciliations per tick. Exercise 2,048 blocks, 128 payloads and a 1 MiB aggregate-NBT boundary fixture, distinguishing
the largest admitted fixture from oversized rejection fixtures. An 8 KiB Fluid representation or a 4 KiB
item is not automatically admitted once framing/depth/nodes and the whole snapshot codec are counted;
report the effective maximum under section 2.3 and measure refusal with no mutation. Measure capture,
recompute at apply, off-thread readback hashing and visual republication after each
operation; a mixed fuel and cargo queue with a maximum-size rocket among small requests; 64 synthetic competing
records. [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) applies: Xmx at most 4 GiB, RSS at most 5.5 GiB, mean
MSPT at most 25 ms, P95 at most 50 ms and P99 at most 100 ms, and no watchdog, OOM or forced load. If a budget
fails, lower the admitted cadence or subdivide preparation; never relax a budget or skip a check.

**Execution.** Targeted A0 and the full `gradlew.bat --offline test` need no lock. A full `clean build`,
runData, GameTest, S1, S2, the native server and benchmarks take `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm`
after checking 10 GiB free on C: and D:. Run the unchanged GameTest log checker. Missing native or client
evidence stays NOT_RUN.

## 7. Proposed merge text and decisions requiring Root

**Proposals only.** Root records this protection scope in `docs/11-RISK-REGISTER.md` and the ADR before the
protection is implemented, not merely before merge. C-P triggers that prerequisite for unsupported tank
payload/quarantine integration; C-A triggers it for fluid-device roots and D-A for item-device roots.
Pure codec work does not authorize a new runtime protection. Root adds commit/evidence references after
verification. This note accepts no risk and closes no implementation finding.

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
> **Preservation and device protection.** Inherit R-038 and W11b3 revision 2 without narrowing their scope.
> An invalid, future, quarantined or unregistered-Fluid/item device root stays verbatim and disables that
> device. Player removal refuses before loot, explosions skip it, extraction refuses and it drops nothing.
> This device-local protection lasts until the cause is resolved: restoring a missing mod allows validation
> on the next load; future/invalid data needs a supporting jar or offline repair. Restart alone does not
> clear it. Escrow locks separately last until proved reconciliation or operator settlement releases them.
>
> **Native terrain-save veto.** A raw root that writes and reads back losslessly, or exceeds the bounded
> native-check work budget, is saved verbatim; operational oversize alone never denies saving. Only observed
> native incompatibility invokes GuardedChunkSaves and vetoes the entire terrain chunk. At the inherited
> 256-denied-coordinate saturation boundary, subsequent terrain saves for that ServerLevel are vetoed until
> the Level closes. Entity, player and global stores still save separately; this is not an atomic world
> pause. Closing the Level clears the veto lifecycle, not the incompatible data, which can trigger it again
> on reload. No routine save exception, omission, normalization, empty-root replacement or dirty clearing is
> permitted. `/setblock`, `/fill` and raw third-party replacements remain detectable repair residuals.
>
> **Entity record and barrier availability.** An unsupported tank/container payload follows the existing
> rocket-entity quarantine/preservation rules, independently of terrain-chunk veto. Cargo can enlarge one
> rocket's native entity record beyond the supported inline-sector reader limit; cut-E2 must then leave its
> operation/endpoints locked and refuse retirement until a valid proof or offline repair is possible.
> Observable own-store/readback failures do the same; unobservable native failures remain the existing
> diagnostic residual, not a new safety guarantee. No new Level-wide entity-save veto is introduced.
> Retryable IO may recover online; other authority/capacity conflicts remain held. Existing shared journal
> writer stalls retain their separate R-035 transfer-availability scope rather than being called device-only.
>
> **Logs.** Emit one bounded audit line per state, reason or backoff transition, never one per tick. Repeated
> transitions and inherited native/Forge save errors have no lifetime log cap.
>
> **Recovery.** Stop the server and preserve the originals and hashes of every Level's terrain, entity and
> POI files, global journals, ledger/data, player data and metadata. Repair only a whole-world copy, verify
> conservation and fresh proofs with two saves and restarts, then replace the whole world. Never restore one
> store, substitute a cleaned root or treat restart as repair. R-031/R-032, per-file rollback, OS/power loss
> and UNKNOWN settlement loss remain unaccepted.

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
| Q1 | Adopt the identity rule (section 2.5), and when? | **Yes, conditional on the grouped predicate and I1's unchanged fences/routing**, as C-I after P1-F8/P1-N2. C-I proves recognition with fixtures; C-E/C-S5 prove the real transaction and negative/reconnect cases. It grants no reconstruction authority and avoids adding a third checked store to every cargo operation. |
| Q2 | Receipt-backed rockets: refuse cargo, or let a cargo L2 advance the READY head? | **Refuse** in v1.8. A head-advance write is new authority over heads and the launch check. Disassembly/reassembly creates a new logical rocket; it never erases the old identity's receipts or negative protections. |
| Q3 | Legacy scalar-fuel rockets: refuse, or add a scalar composite projection? | **Refuse.** Disassembly already restores their chests; a scalar projection is new authority for little gain. |
| Q4 | One live cargo operation per rocket, with no chains? | **Yes.** It bounds memory and removes the chain cuts. Chains can be a later, separately reviewed change. |
| Q5 | Deterministic payload selection or a player choice? | **Deterministic**, with the deliberate buffer-first priority difference in section 2.3; a player choice would need a packet and protocol change. |
| Q6 | The rocket visual republishes after every cargo operation (ADR-065 section 11.1 keys it by content hash). | **Keep it** and measure in C-B. If budgets fail, lower the cadence first; keying the visual by the structure digest would need its own ADR note. |
| Q7 | Item devices: spill drops or a drop carrier? | **Spill**, as vanilla containers; there is no custom item NBT to police, and the owner is the placer. |
| Q8 | Automation direction | **Directional**, as legacy: loaders only accept input and unloaders only give output; the owner's menu works both ways. |
| Q9 | Modded `ChestBlockEntity` subclasses | **Refuse**: a payload cannot prove their slot count. A later adapter may add them. |
| Q10 | Is `payload_kind` still needed? | **No for this revision.** Section 3.3 keeps fuel-only liability semantics, cargo payload hashes and UNKNOWN accounting, with no cargo scalar. Keep the closed dictionary and pin old bytes. A future need for the field is an open decision for Root, not permission to add or reindex wire names. |
| Q11 | One admission switch for fuel and cargo, or one per kind? | **One per kind**: fuel can be enabled after W11b3's evidence without waiting for cargo. |
| Q12 | Benchmark soak | **Share** one docs/17 soak on the integrated fuel-plus-fluid-and-item-cargo build after W11d. A prior fuel-only run is not cargo evidence. Until that run and the other prerequisites are recorded, cargo admission stays disabled. |

W79 delivers only this design and its record. It changes no code, tests, assets, ADR, risk register, ledger,
status, build or workflow files, and it satisfies no runtime Gate.
