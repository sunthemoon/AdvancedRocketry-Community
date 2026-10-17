# W11b3: fluid propulsion loader and production admission

**Revision 2**, 2026-10-16. **Proposed design, not admission approval or runtime evidence.**
Assignment: [W75](../TASK-W75-W11B3-DESIGN-NOTE.md); revision task: [W75R](../TASK-W75R-W11B3-DESIGN-REVISION.md).
Revision 1 is commit `918d5e9e` (2026-10-15). Revision 2 applies the independent review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W75-review/REVIEW.md` (SHA-256
`352e5b47de81ec34384db580fe5ebbcc68c61c3d9455b4ed8d74ed982cae1d01`) under Root's decisions in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W75-review/ROOT-DECISIONS.md` (SHA-256
`0b33f5548c9c7f60d38adf9a32f0cb58ac27c88af9fd668753b3894c7df44633`). The section "Revision 2" of
[W75](W75.md) maps every finding to the text that answers it. The note keeps its six sections.

**Baselines.** The shared baseline of revision 1 is `0c5021976838cdcf9a19aa614861725075ee354d`; this
revision also reads the shared branch at `e3296239`, which contains W77 (merged at `b451e436`). W11b2
is read through Git only, at `d178e727`, including frozen `W11B2-DESIGN.md`, `W11B2-DESIGN-2.md`
revision 1 and `W11B2.md` continuation 15. Two later commits of
`codex/v1.8.0-w11b2-rocket-integration` are cited for names only: `4856ab78` (continuation 17, which
applies Root-R6) and `8ed38aa5` (continuation 18, which applies Root-R8 and is still in progress).
Later work is not presumed complete or reviewed.

**Notation.**
- `J` means `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`, and `Test` means the same
  package under `src/test/java`. Pinned citations are reproducible with
  `git show d178e727:<expanded path>`.
- "Transfer record T" means the rocket's record in the checked transfer journal
  (`RocketTransferSavedData`), as in design 2.
- ID prefixes keep different things apart:
  - Root-R1 to Root-R9 are Root's reservation decisions (section 2);
  - P1-F3, P1-F8, P1-F9, P1-N1 and P1-N2 are W11b2's part-1 findings (section 1);
  - D2-C0 to D2-C8 and D2-F0 to D2-F8 are design 2's reconciliation and flight steps
    (`W11B2-DESIGN-2.md` sections 5.3 and 5.4);
  - cut-K, cut-C, cut-E, cut-R, cut-O and cut-G are the forced-stop cut families of
    [W11A section 7](W11A-DESIGN.md), and cut-N0 to cut-N3 are this note's creation cuts (section 5);
  - O1 to O4 are the operator-settlement steps of W11A section 5 (O1 durable intent, O2 survivor
    checkpoints, O3 durable retirement, O4 unlock), and O3J is the checked transfer-journal removal that
    design 2 places between O3 and O4;
  - W02-F3 is W02's refill finding and W13-N1 is W13's occupancy finding.
- Names explicitly called proposed do not exist yet.

Authority: [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
and [W11A sections 7 and 8](W11A-DESIGN.md). Root owns approval and central-document changes.

## 1. Scope and blocking prerequisites

| Ledger unit in the [content ledger](../../v1.8.0-content-ledger.csv) | W11b3 disposition |
|---|---|
| `block:fuelingStation`; follower `block_entity:ARfuelingStation` | Deliver the native-Fluid propulsion extension `advancedrocketrycommunity:fluid_fuel_loader`. Preserve `fuel_loader`, its schema-2 batches and its recipes. This supplements the REDESIGNED row; it makes no retroactive crash-atomicity claim. Row 82 targets `fuel_loader` with the note "fluid fuels added in C17a"; Root updates that note when W11b3 merges (section 6). |
| `libvulpes:java/itemLinker`; follower `libvulpes:recipe/linker` | Deliver `advancedrocketrycommunity:linker`, its recipe and the rocket-binding half. The primary row remains incomplete until W11e. Nothing is copied from LibVulpes. |
| `block_variant:loader/4` and `/5`; `block_entity:ARFluidUnloader` and `ARFluidLoader` | **W11c**: cargo Fluid unload and load, and the pressurized-tank snapshot adapter. `/5` is `rocket_fluid_loader`, not `fluid_fuel_loader`; propulsion banks are not captured cargo tanks. |
| `block_variant:loader/2` and `/3`; `ARRocketUnloader` and `ARRocketLoader` followers | **W11d**: item cargo. |
| `block:monitoringStation` and the remaining linker roles | **W11e**: the full monitor, control and linker. There is no remote or redstone launch here. |
| `block_variant:loader/1`; `block_entity:ARsatelliteHatch` | W27b supplies the block only. W27c1 to W27c3 supply the receipt, registry and journal infrastructure, W27d deployment, and W27e evidence and enabling. Credited-source bay support remains W27f. |

The existing `J/rocket/fuel/FuelLoaderBlockEntity.tickServer/findTarget` uses item batches, 25 abstract
units per tick and a search for a nearby loaded rocket
([ADR-027](../../../decisions/ADR-027-ROCKET-ITEM-FUELS.md)). Do not retrofit it into a Fluid device or
reuse its nearest-target search. Hand exchange and that loader keep their ordinary two-store
limitations, while obeying escrow interlocks. The gas charge pad (D4), cargo, bay deployment, automatic
launch and signed settlement-loss accounting are non-goals.

**Before enabling any admission**, close the following part-1 findings. At the pinned commit none has
complete closure evidence; P1-F9 has since been delivered by W77. Root assigned each one on 2026-10-16
(decision 4 on the W75 review), and section 5 lists the leaves.

| Finding and pinned evidence | Required closure | Owner |
|---|---|---|
| P1-F3, scheduling: `J/rocket/server/RocketTransferService.tickSource/spawnDestination` and `RocketTransferSavedData.pollWriter` still wait for whole-root acknowledgement. | Use exact per-record version and absence leases, keeping one writer, bounded captures and final guards. A two-transfer delayed-write test and eight-transfer native timing must show that independently acknowledged work advances. `resourceMutationUnproved(logical)` alone is insufficient. | Its own design note and read-only review, then a leaf, after the W11b2 merge (P1-F3-D, P1-F3). |
| P1-F8: `RocketDisassemblyService.executePlayer/disassembleForReleaseTest` releases the landed reservation **before** `execute`. | Keep claims through failure. A successful restoration must fence old-transfer recovery before checked removal and release. Test obstructed and rollback failure, and S2 between restoration, removal and release: no lost claim and no rocket recreated over blocks. Moving a call after discard alone is insufficient. | Its own design note, after the W11b2 merge: [W77](W77.md) found that a crash-safe order needs a durable record that disassembly is under way, so that recovery cannot recreate a rocket over restored blocks (P1-F8-D, P1-F8). |
| P1-F9: `RocketTransferRecoveryService` rebinds, and `journal.put(resumed)` precedes the deferred `addFreshEntity`; a failure repeats work. | Retry one acknowledged identity and phase without redundant writes or new UUIDs. Test repeated spawn rejection, unchanged write count and sequence, later success, and restart without duplication. | **Done**: W77, merged at `b451e436`. W77's tests reload checked files inside a GameTest server and claim no separate-process restart; leaf B3-S6 supplies that representative. |
| P1-N1: `RocketEscrowGate.escrowIdle` permits a matching idle ledger; `RocketSettlementIdleGameTests.acknowledgedSettlementRestoresMountRefuelAndLaunch` ends at COUNTDOWN. Continuation 15 keeps three unexpected fence warnings. | Partial, **not closed**. Preserve the refusal of a foreign or missing ledger. Complete design 2's D2-C0 to D2-C8 and D2-F0 to D2-F8, two receipt-backed and two ordinary-L2 flights with successor refill, replay and cancel tests, and an unchanged log checker. No blanket idle exemption. | W11b2's governed-flight work, not W11b3. |
| P1-N2: `RocketTransferService.failBackToSource` mutates and removes transfer record T before `afterAcknowledged`; two queued generations can refuse `queueEffect`. | Reserve the continuation atomically before mutation or removal; a full queue leaves the source, transfer record T and pad intact. Test two occupied slots, delayed acknowledgement, one release, and UNKNOWN or restart. Governed returns keep exact-source permits. | A leaf after the W11b2 merge, one commit and one test (P1-N2). |

**W13-N1:** the loader's preparation needs neither new persisted parked-region indexing nor changes to
arrival readiness: it binds an already present stationary source and never selects or creates an
arrival. Keep W13's limitation explicitly; this note does **not** close it. For a separately assigned
landing fix, recommend the first option: loaded entity sections in all of the at most 16 arrival
chunks, rechecked before placement, with no loading. `NamedLandingPadOccupancy.discoveryBox` also covers
origins outside that footprint, so that fix alone must not claim to detect every unloaded rocket outside
the footprint. Complete occupancy coverage may still require the separately reviewed bounded
parked-region index.

<div style="page-break-after: always;"></div>

## 2. Admission, reservations and one authority

**Exact enabling condition.** Production admission stays disabled until all of the following hold:
- W11b1 and the completed W11b2 are reviewed and integrated, and section 1's prerequisites are closed;
- the required D3-A S2 cuts of section 5 pass **on integrated source with the real loader and two
  competing devices**, together with the native timing and reservation proofs.

While production stays disabled, gated S2 exercises the same adapter. Root reviews the evidence before
enabling; there is no runtime self-certification or Gate approval. Pin the enabling commit, smoke-test
without test hooks, and rerun the cuts that integration affects.

At `d178e727`, `EscrowCoordinator.productionAdmissionEnabled()` returns false, admission is the
package-private `prepareTest(op, observedSection, completion)`, and ledger creation is
`ResourceEscrowSavedData.forTests`. **There is no existing production admission entry point.**

**Proposed production entry (built in leaf B3-M).** `EscrowCoordinator.prepareFill` accepts a validated,
immutable native admission capture and returns a typed result. It is extracted from the reservation and
queue core of `prepareTest`; no block invokes or ungates a test fixture. It has two parts:
- a pure check against a given root: `EscrowSettlementCapacity.requireAdmission` (the five-marker limit
  and the settlement fit) and then the candidate preflight `preflight(prospective, candidate)`, both
  exactly as W11b2 pins them at merge;
- an owner-thread admission: `EscrowAdmissionAuthority.tryReserve`, the queue entry and the endpoint
  locks.

The candidate preflight is where Root-R6's headroom lives. At `4856ab78` it adds one floor retirement's
ceiling (`FLOOR_RETIREMENT_BYTES`: one operation, one 2 KiB receipt and one 1 KiB floor top-up), one
operation slot and one receipt slot, while every reserved region is charged at its ceiling (Root-R8,
applied at `8ed38aa5`). `prepareFill` therefore uses the combined reservation with Root-R6's headroom and
Root-R8's charging. It adds no capacity rule of its own and never uses the preflight without a named
candidate, so a fill can never take the space that a floor retirement needs.

**Production ledger creation (built in leaf B3-E).** Ordinary queries, unbound loaders and unrelated
worlds create no ledger. A world's first admitted fill creates it in this order:

1. Run `prepareFill`'s check against a virtual empty root, `EscrowLedger.empty(id)` with the ID the new
   ledger will carry, which is not stored. Then call `tryReserve` while the PROPULSION delegate (below) is
   NO_FILE. A refusal at either point creates nothing.
2. In the same owner-thread action, create the ledger SavedData through a proposed production method
   (not `forTests`), attach its coordinator to the delegate, which becomes LOADED, and queue the
   already-reserved operation. Creation has no refusal path of its own, and nothing that can fail runs
   between `tryReserve` and the queue entry, because a reservation has no release call.
3. The coordinator's first checked write is the **creation baseline**: an empty root at writer generation
   1 with no operations. The existing writer publishes it, and autosave never publishes queued
   admissions. Its exact acknowledgement precedes the capture of the queued operation's L1. From there on
   the path is the existing-ledger path that W11b1 and W11b2 already cut.
4. A definite baseline failure before L1 removes the queued operation, and with it the reservation; a
   failed baseline readback when the new coordinator binds counts as definite. The empty root stays in
   RAM, and a later checked write may publish it, so the disk holds either no ledger or a valid empty
   one. An UNKNOWN baseline keeps the operation queued and its reservation; the writer
   resolves it by bounded readback or retries the exact candidate every 200 ticks, as W11b1 specifies, and
   three unresolved failures make the ledger unavailable.

While the baseline or L1 is outstanding, the player sees `PREPARING` (section 3); nothing has moved yet.
Cut-N0 to cut-N3 in section 5 cover this path.

**One attempt.** The loader's fair server-thread queue is the only production caller; its menu and its
capability never call `prepareFill`. An attempt runs these steps on the server thread:

1. **Eligibility.** All of the following must hold, or the attempt refuses with a section 3 status:
   - escrow storage is enabled and available;
   - the device is claimed, its binding is exact, and the device and rocket owners match;
   - device and rocket are in the same Level, their chunks are FULL with loaded entity sections, and the
     rocket is within 6 blocks;
   - the rocket is stationary and ASSEMBLED, FUELED or LANDED, with no actual or assigned passengers and
     nothing riding it (stricter than ADR-065; see "Passengers" in section 3);
   - assembly recovery has nothing pending for the rocket;
   - the checked transfer journal has no transfer record T for the rocket, or only a settled COMMITTED
     record of a stationary arrival (`RocketTransferService.resourceMutationAllowed` at `d178e727`);
   - no bay hold or flight hold names the rocket. In particular, no ARMED entry exists for it, not even a
     terminal one: `prepareTest` refuses any ARMED entry (`J/resourceescrow/EscrowCoordinator.java:50`),
     so a successor refill waits until D2-F8 prunes the entry, not only until D2-F7;
   - both resource-mutation layers allow a change. The entity layer is `RocketEscrowGate.idle(rocket)`,
     which `RocketEntity` enforces on every resource change (`J/rocket/entity/RocketEntity.java:208`).
     The service layer is `RocketRuntime.resourceMutationAllowed(rocket)`, which checks pending
     assembly-transaction recovery in `RocketManager` and the journal rules of
     `RocketTransferService.resourceMutationAllowed`, including `resourceMutationUnproved`. For a
     same-direction chain, whose earlier markers make `idle` false, step 2's coordinator prefix check
     takes the place of the idle test; nothing else is relaxed.
2. **Capture.** Select the unique eligible bank (section 4). Run side-effect-free simulations, then capture
   the complete envelopes, identities, epochs, hashes, owner, snapshot and journal guard in one capture.
   The counted markers come from that **same capture** and are checked against `beforeWrapper`
   (`EscrowNativeBinding.Admission`, not the fixture's `captureTest`). Revalidate before publication, and
   run no external callback during apply. Same-direction chaining uses the coordinator's prefixes, never a
   bypass of an ordinary lock or a frozen frontier.
3. **Reservation.** Run `prepareFill`: its check, then `tryReserve(..., PROPULSION)`, then the queue entry
   and endpoint locks, as one owner-thread action. A refusal issues no sequence, changes no tank or bank,
   and leaks no slot. A definite failure before L1 cancels only unapplied, uncaptured work; an UNKNOWN
   outcome keeps the candidate and its reservations.
4. **Durable progress.** The exact L1 acknowledgement authorizes D2-C2 apply. The joint own-store, force
   and stable-readback proofs authorize the D2-C7 L2 write, and only L2's exact acknowledgement permits
   D2-C8 release. Leaf B3-E adds the real loader registration, its signature, proof validators and restart
   reconciliation. The pinned `RocketEscrowAudit.attempt` still returns `RECONCILIATION_UNIMPLEMENTED`; a
   renamed fixture is not production recovery. There is no historical resource fallback, no unproved
   success callback, and no direct tick-time mutation from tank to rocket.

**Root's reservation decisions, not inferred authority.** They are in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`, read on 2026-10-16 at
SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`. Revision 1 cited
`1cff184d…`, which held only Root-R1 to Root-R4. W11b2 continuation 18 is still running and may add
decisions. **W11b3 follows the reservation decisions in force when W11b2 merges**, and every capacity in
this note means the figure W11b2 pins at merge.

- **Root-R1:** preview the candidate's merged component to prove that its operation and receipt fit, but
  charge **every unsettled component, the candidate's included, at the fixed ceiling**. Settled
  components keep their charge. Include floors, receipts, both flight tables and framing. No bound
  increases. Five live-plus-retired marker IDs is the measured admission limit per rocket; eight remains
  the decode and shared-quota limit. The root's effective capacity is below 64; it is measured and pinned
  by W11b2, not advertised as 64. At `d178e727`, `EscrowSettlementCapacityTest` pins five and six, and
  `EscrowAdmissionCaptureTest` pins the marker-capture digest.
- **Root-R2:** the combined checks cover admission, floor O1, O3 receipts, O4 heads, ARMED and terminal
  writes, and the D2-F1 reservations. Do **not** add them to the sticky-reason and autosave persistence of
  `snapshot(false, ...)`; it adds no authority, stays governed by the existing codec bounds, and gains no
  new save refusal.
- **Root-R3:** `EscrowReservationTransitionTest` fills the measured capacity, persists the longest sticky
  reasons through the checked autosave, and asserts that the repaired preflight and a following O1 and
  ARMED write succeed. It must fail under preview-only candidate charging.
- **Root-R4, as corrected by Root-R7:** old-rule operations exist only in development fixtures, not in
  player roots. Add no migration or load refusal for them; their reason saves still persist. On such a
  root the existing preflight refuses an authority-growing write only when that write raises the reserved
  total; a write that keeps or lowers it can pass. Do not confuse this with design 2's refusal of older
  development **formats**.
- **Root-R5:** a floor retirement (`TRANSFER_FLOOR_RETIRE`, created by a confirmed floor O1) is new work.
  It keeps the combined preflight in `beginSettlement` and refuses without mutation, with one bounded
  operator line: "Escrow is full; retry floor retirement after other settlements or pruning."
  (`EscrowSettlementCapacity.FLOOR_FULL` at `4856ab78`). No future retirement is reserved per retained
  floor.
- **Root-R6:** fill admission always keeps headroom for one floor retirement at its ceiling, whether or not
  the root holds a retained floor, so the capacity does not depend on floor history. W11b2 measures and
  pins the capacities, guarded and unguarded, with and without retained floors.
- **Root-R8:** every reserved region is charged at its ceiling, occupied or empty: an unsettled operation
  (`OP_BYTES` plus its 2 KiB receipt), a receipt slot (2 KiB), a named floor (1 KiB) and each slot of the
  two 64-entry flight tables (1 KiB). After Root-R8 only fill admission and a floor retirement raise the
  reserved total, so Root-R6's headroom survives every other transition.
- **Root-R9:** `EscrowFlightTableReservationTest` requires that a floor retirement still passes after the
  cleanup of all 64 terminal entries; a walk test asserts that no non-admission transition raises the
  reserved total; the capacities are re-measured and re-pinned.

**Shared authority.** Move the one `EscrowAdmissionAuthority` from the ledger-owned `EscrowCoordinator`
(`J/resourceescrow/EscrowCoordinator.java:21` constructs it, and line 31 registers PROPULSION in the
constructor) to the runtime-owned `EscrowRuntime`. **Terms:** `EscrowRuntime` is a capability of the
Overworld `ServerLevel`, so it lives as long as the running server; its Javadoc says "Overworld-lifetime".
A coordinator exists for each bound ledger SavedData; its Javadoc says "Server-lifetime coordinator",
which stops being true once a test hook replaces the ledger. This note says "runtime-owned" and
"ledger-owned", and leaf B3-U corrects both Javadocs.

- **One registration.** At `ServerStartingEvent`, after the Levels exist and before the first tick,
  `EscrowRuntime` registers exactly one PROPULSION index delegate. `registerIndex` still throws on a
  second registration (`EscrowAdmissionAuthority.java:36`, pinned by
  `authorityRequiresItsOwnerThreadAndCannotRegisterAnIndexTwice`), and nothing else registers PROPULSION.
- **Startup lookup.** In the same handler the runtime calls the load-only lookup
  `ResourceEscrowSavedData.existing(server)`, which never creates a file, and binds an existing ledger at
  once rather than in the END phase of the first tick (`J/resourceescrow/EscrowRuntime.java:137-139`).
  The ledger's checked baseline is the one the pre-start managed-data check already reads
  (`ManagedSavedDataPayloadValidator`, which calls `EscrowLedgerBaseline.require`), as Root decided for
  the continuation 09 finding M4: a failure before start refuses startup and preserves the files, and a
  later failure marks escrow unavailable with one bounded line, never a tick exception.
- **Delegate states**, using the terms of [W27A section 2](W27A-DESIGN.md):
  - NOT_LOADED, from registration until the startup lookup. The delegate reports `Index.loaded` false, so
    `tryReserve` returns UNAVAILABLE for every consumer.
  - NO_FILE, when no ledger file existed. The index is loaded and known to be empty.
  - LOADED, once a coordinator is attached. The index holds that coordinator's durable ledger records,
    queued operations and captured writer candidates, UNKNOWN ones included, as the pinned constructor
    gathers them.

  The authority still sees only `Index.loaded`. The three states live in the delegate, which holds a
  reference and never touches storage on refresh; the first-fill path reads them to choose between
  creation and the existing ledger.
- **Coordinator attach.** A coordinator attaches its index supplier to the delegate and never registers.
- **Ledger replacement.** Only test hooks replace the ledger SavedData. They do so through one runtime
  method that retargets the delegate on the owner thread and refuses while the old coordinator holds
  queued, captured or UNKNOWN work, so no reservation disappears with it. Direct `DimensionDataStorage`
  swaps in GameTests (`J/gametest/RocketJournalInterlockGameTests.java:47,57`) and the creation in
  `forTests` move to that method, so a second ledger in one runtime retargets instead of throwing at
  registration. Retargeting to no ledger returns the delegate to NO_FILE.
- **First fill.** The creation order above: check against a virtual empty root and reserve while NO_FILE,
  then create the ledger and queue the operation in one owner-thread action; a definite failure before L1
  removes both.
- **Bay.** W27c1 registers BAY as NOT_LOADED before admission and owns the retained 4,096 and 128-per-owner
  quotas; implement no bay store here. Index unique durable, queued, captured and UNKNOWN operations,
  including REPAIR and released-but-unresolved bay receipts. NOT_LOADED refuses, and NO_FILE means
  known-empty. There is no timeout release or eviction. The shared limits of 64 per server and 8 per
  rocket are in addition to the five-marker, byte and one-per-device limits. [W27B](W27B.md) has **no**
  receipt store or admission. Contract: [TASK-W27A2, its finding F3](../TASK-W27A2-BAY-REDESIGN.md), and
  [W27A section 2](W27A-DESIGN.md).
- **Contract delta for Root.** W27A section 2 says that the one authority "belongs to the coordinator".
  Under this note it belongs to `EscrowRuntime`, and each store's coordinator attaches to a delegate that
  the runtime registered. The frozen `tryReserve` and `tryRetain` results and the no-release rule are
  unchanged. Root records the wording change before W27c1 continues.
- **Sequencing.** Leaf B3-U makes this move as the first, small leaf after the W11b2 merge, and Root
  merges it before W11b3's other leaves and W27c1 continue. The contract above is then frozen for both.

<div style="page-break-after: always;"></div>

## 3. Device and binding contract

Proposed are separate pure codecs and a service, and thin `FluidFuelLoaderBlock`,
`FluidFuelLoaderBlockEntity`, `FluidFuelLoaderMenu`, a client-only screen and `LinkerItem`, registered
with stable IDs through DeferredRegister. There is no capability for captured world banks, no general
cargo framework, no FE cost and no propulsion unloader.

| Surface | Proposed contract |
|---|---|
| Block-entity persistence | The block entity saves one root under the NBT key `arce_fluid_fuel_loader`: schema 1, at most 32 KiB, depth 20 and 2,048 nodes. It holds the owner, a fresh placement UUID, the binding, the native tank and the escrow section. The tank holds one Fluid, 0 to 16,000 mB, in a native envelope of at most 8 KiB with native round-trip, depth and node checks. The escrow section is schema 1 and holds at most one marker, in at most 256 bytes, depth 8 and 128 nodes, the bound that W11b2's test endpoints apply (`J/resourceescrow/EscrowTestEndpoints.java:93`); its production codec and W11A section 1's signature `arce-escrow-fluid-tank-1:` are built in leaf B3-M. Fields, types and the registered canonical Fluid ID are strict; nothing is truncated or substituted. A missing root means an empty, unclaimed device. |
| Rocket binding | Schema 1, at most 512 bytes, depth 8 and 32 nodes. It holds the logical rocket UUID, the Level `ResourceLocation` (at most 128 ASCII characters), the physical UUID and position as a **locator**, and an optional station UUID. A replaced or moved identity, an owner or context change, or a duplicate is rejected. Successors are rebound explicitly; there is no nearest-rocket scan and no chunk loading. The binding and the tank persist across restarts. |
| Linker selection | The linker item stores `arce_linker`: schema 1, at most 1 KiB, depth 8 and 32 nodes, holding one device UUID, Level and `BlockPos`, and no arbitrary NBT, entity number or list. A player selects a loaded loader; a server-side use on the rocket entity then binds it. Sneak-use clears an idle selection or binding; a protected device refuses. An invalid or future selection is inert and can be cleared explicitly. The other linker roles remain W11e. |
| Capabilities | One local `FLUID_HANDLER` capability, the same for every `Direction` and for the null side. Fill and drain work only while the device is claimed, its root is valid, its chunk is loaded and its tank is unlocked; the capability never drains the rocket. There is no item inventory and no FE. Simulation has no side effects. A stale handle, a call off the server thread or a reentrant call returns zero or empty. Lifecycle events invalidate handles. Automation acts on the device's persisted ownership, not on FakePlayer privilege. |
| Menu | Only the owner may open the menu, from within 8 blocks; other players, operators included, receive NOT_OWNER, and an unclaimed device's menu opens for no one. `stillValid` checks the exact block entity, its load state and its device state. The menu shows the Fluid, the amount and capacity in mB, the binding and the status through bounded scalar container data. Vanilla's `ClientboundContainerSetDataPacket` sends each value as a 16-bit short, so the amount and the capacity (0 to 16,000) take one slot each, and the Fluid takes two slots: the low and high 16 bits of the session's numeric Fluid registry ID, which Forge's registry synchronization makes the same on server and client, with -1 for empty. The client resolves that ID for display only and shows a generic label for an unknown value; it is never saved or sent back. The panel is original and drawn in code; it sends no native tags or foreign coordinates and adds no custom C2S packet or protocol change. Local container use, filling the tank from a held Fluid container, is validated separately and never admits a rocket transfer. |
| Native drop | Breaking a device yields one nonstackable loader item that carries the resources; there is no separate Fluid spill. The carrier is schema 1 and holds only the owner and the tank, in at most 12 KiB, depth 18 and 1,024 nodes, with no identity, binding or escrow markers. Validated placement creates a fresh device UUID with no binding, and the owner rule below. The block entity overrides `onlyOpCanSetNbt()` to return true, and the item refuses a `BlockEntityTag`, as `J/machine/tank/PressurizedTankItem.java:26` does, so a non-operator cannot restore arbitrary block-entity NBT. Removal of a protected device (below) refuses before loot. Destruction without drops is not salvage. |

**Owner on placement.** The carrier is a custom item tag that `onlyOpCanSetNbt` does not gate, so a
non-operator in creative mode can write any owner into it. Placement therefore applies this rule:
- a carrier with no owner, or whose owner is the placer, gives a device owned by the placer;
- an operator's placement keeps the carrier's owner, so an operator may place a device for another player;
- a non-operator who places a carrier with another player's owner places an **unclaimed** device: the
  carrier's owner is not kept, nothing is admitted, and the capability refuses fill and drain.

Breaking an unclaimed device drops a carrier with no owner, so for non-operators ownership follows
possession of the item, and a carrier can never make a non-operator's placement create a device owned by
someone else. Leaf B3-A tests both the operator case and the non-operator foreign-owner case.

**Protected devices.** A device is protected when it is escrow-locked (its section holds a marker, or a
queued, captured or UNKNOWN operation names it), or when its root is unsupported (invalid, future,
quarantined, or naming a Fluid that is not registered). An unsupported root also disables the device.
Following R-038 for the satellite bay, a protected device refuses player removal before loot, is removed
from an explosion's block list, drops nothing, and refuses extraction. `/setblock`, `/fill` and raw
third-party replacement remain detectable repair residuals, not salvage.

**Client-to-server checks.** The only client actions are vanilla block use, entity use, and menu or
container clicks; there is no bank-selection packet. The server checks each one on the server thread:
- the sender is connected and alive, and is neither a FakePlayer nor a spectator;
- the held item, hand, menu, window ID and state match;
- the world border and the dimension are correct, and the chunk is loaded **before** any lookup;
- the block-entity type, device UUID, owners and role match;
- the rocket is stationary and its resource state allows the action;
- the actor is within 8 blocks, and the endpoints are within 6 blocks of each other.

Both binding uses require the owner to be online, and there is no operator ownership bypass. The server
validates the entity it resolves; the incoming entity integer ID is never authority. Linking allows at
most one change per player per 10 ticks, and stale or replayed requests refuse. Client amounts, banks,
costs and snapshots are never authoritative. The owner's logout after L1 does not stop recovery.

**Player status.** The proposed `FluidFuelLoaderStatus` has the refusal members `ADMISSION_DISABLED`,
`ESCROW_FULL`, `LEDGER_UNAVAILABLE`, `TRANSFER_JOURNAL_UNPROVED`, `ESCROW_PENDING`, `UNBOUND`,
`STALE_BINDING`, `WAITING_UNLOADED`, `OUT_OF_RANGE`, `NOT_OWNER`, `ROCKET_BUSY`, `EMPTY`, `BANK_FULL`,
`INCOMPATIBLE_FLUID`, `AMBIGUOUS_BANK`, `UNSUPPORTED_DATA` and `INVALID_DATA`, and the progress members
`READY`, `PREPARING` and `TRANSFERRING`. Keys use
`message.advancedrocketrycommunity.fluid_fuel_loader.<lowercase_member>` in en_us and zh_cn. Examples:
"Escrow capacity full; no fuel moved." / "托管容量已满；未移动燃料。" and "Binding is stale; bind the
loader again." / "绑定已失效；请重新绑定装载器。"
- `PREPARING` covers a queued operation whose creation baseline or L1 is not yet acknowledged, including
  an UNKNOWN outcome: "Preparing escrow; no fuel has moved yet." / "正在准备托管；尚未移动燃料。" This is
  true until D2-C2 apply, which needs the exact L1 acknowledgement.
- `TRANSFERRING` covers an applied operation that is progressing through its proofs and L2.
- `ESCROW_PENDING` says that resources are locked pending durable confirmation (an UNKNOWN write after
  apply, a stalled barrier, repair or an unloaded endpoint). It must not claim "no fuel moved" after L1 or
  apply.

Never expose tentative credit or label a held operation complete.

**Passengers.** ADR-065 section 4.2 and D2-F1 ("board/refuel") allow refuelling after boarding. This
design is stricter by choice: a fill refuses while the rocket has actual or assigned passengers or
anything riding it, with status `ROCKET_BUSY`. The rule only refuses more than the ADR allows, so it adds
no authority path. Leaf B3-V adds a V2 case: a second client boards, the owner's fill shows
`ROCKET_BUSY` on both clients, and the fill proceeds after the passenger leaves.

## 4. Exact refill and resources

[W02F](W02F.md)'s W02-F3 refill moves `min(1,000, local mB, eligible bank room)` in integer mB, at most
once per device per 20 ticks, with no catch-up burst after unload or restart. For a bank at 1,251 of
2,000 mB and a 1,000 mB input, it transfers 749 mB and keeps 251 mB locally; there is no rounding and no
whole-container requirement.

**A cap, not a throughput.** A device holds at most one operation, so each step waits for L1, apply, the
own-store, force and readback barrier, and L2 before the next step can be admitted; 1,000 mB per 20 ticks
is an upper bound. Meanwhile the menu shows `PREPARING`, then `TRANSFERRING`, or `ESCROW_PENDING` while
held. Leaf B3-B reports the measured cycle time per device.

Banks are filtered by propulsion role, the current catalog, complete native Fluid compatibility and
positive room. One eligible bank selects PRIMARY or OXIDIZER on the server. More than one gives
`AMBIGUOUS_BANK`; none distinguishes `INCOMPATIBLE_FLUID` from `BANK_FULL`. A nonempty bank with a
different tag, and abstract fuel, are never overwritten. Zero-side-effect simulations precede the
capture. A catalog change invalidates a preparation, not a durable operation's frozen payload. The
existing whole-container drain limitations remain; no sub-container drain or propulsion unloader is
claimed. Intake from containers, players or third-party pipes is ordinary native storage, **outside**
the loader-to-rocket D3-A guarantee.

**Recipes.** Proposed shapeless recipes: the loader takes an **empty** `pressurized_tank`, a
`liquid_io_board`, a `control_board` and a `machine_casing`; the linker takes a `tracking_circuit`,
`minecraft:iron_ingot` and `minecraft:redstone`. A `pressurized_tank` item carries its Fluid in its item
tag (`PressurizedTankItem`: "The tank root is carried directly in the Item tag"), and a plain ingredient
matches the item whatever its tag, so a filled tank would be consumed and its Fluid deleted. The loader
recipe therefore uses a proposed custom ingredient that matches a `pressurized_tank` only when
`PressurizedTankItem.placeable` accepts it and its tag holds no tank root or a root that decodes to an
empty Fluid. No current recipe uses `pressurized_tank` as an ingredient, so this is the first. Leaf B3-D
tests that a filled tank and a tank with an invalid root do not match, and verifies every ingredient in
the integrated recipe graph.

**Art and provenance.** Original generated 16x16 tank and port textures, the linker icon, JSON models,
blockstates, loot, recipes, advancements and English and Chinese text go under
`src/generated/v1.8/resources`, through a proposed `V180FluidFuelLoaderData` provider; no legacy art is
reused.
- Before authoring, add the declaration
  `docs/provenance/v1.8.0-c17a-fluid-fuel-loader-preauthoring.md`, listing NEW code and text and
  GENERATED output, MIT, author, reviewer and date, as
  [docs/08](../../../08-ASSET-LICENSE-AND-PROVENANCE.md) requires.
- After generation, add the JSON record `docs/provenance/v1.8.0-c17a-fluid-fuel-loader.json` with
  generator and target hashes, following the existing pairs of a `-preauthoring.md` declaration and a
  `.json` record. `scripts/validate_v180_provenance.py` parses JSON records and only lists Markdown ones,
  so a JSON record lets it check the hashes.
- Run the generated-art screen that [W53](W53.md) used (`tools/audit/screen_generated_art.py` over
  `src/generated/v1.8/resources`, with both pinned clients) and keep its report. Every new texture must
  be CLEAR. W53's records are bound by `V180ArtRedrawProvenanceTest`.

Existing sealed provenance is immutable. A later upstream substitution needs its **own** UPSTREAM_AR_MIT
row with source commit, path and hash, target and hash, and transformation before copying, and belongs
under `src/imported/v1.8/resources`. The linker's ledger row is not permission to import LibVulpes.
Neither W75 nor W75R copies an asset or upstream file.

<div style="page-break-after: always;"></div>

## 5. Verification and implementation leaves

All entries below are **planned**; this design produces no evidence. The order is: prerequisites and
reviewed contracts, the authority move, model and codecs, the device, the escrow adapter, data and UI,
integrated evidence, then Root's enable decision. Leaf IDs identify independently verifiable work, not
extra versions or Gate approvals.

**Cost basis.** Each forced-stop cut costs three JVM starts: the run to the cut and two restarts.
W11b2's recent native runs took about 20 seconds per start, inspection included: 33 starts in 10.5
minutes (continuation 11) and 15 starts in 5 minutes (continuation 15), per the `native-summary.json`
files under `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C11` and `W11b2-C15/delivery`. A full GameTest
run takes 15 to 18 minutes (W11b2 continuation 15, W77). Lock hours below are
(starts x 20 s + 15 minutes of packaging and setup + any GameTest run) x 1.5 for one rerun margin. They
are planning figures, not budgets. The unit suite (about 6 to 8 minutes) needs no lock.

**Prerequisite leaves** (Root's decision 4):

| Leaf / dependency | Builds | Acceptance | JVM starts, lock hours |
|---|---|---|---|
| P1-F9 / done | `RocketTransferRecoveryService` retries the acknowledged destination UUID (W77, merged at `b451e436`). | W77's two real-service GameTests. The separate-process failed-respawn cut is one of B3-S6's representatives. | None further. |
| P1-F3-D / done | Nothing: the design note [W86-DESIGN.md](W86-DESIGN.md), revision 2, frozen by Root on 2026-10-16 after one read-only review (APPROVE-WITH-CHANGES). | Sequence leases on the one acknowledgement, `actionable(L)`, single-use reservations, no saved-format change. | 0 |
| P1-F3 / W11b2 merged | Per-record scheduling as W86-DESIGN revision 2 specifies; its first commit is P1-N2 (below). The leaf first rechecks W86's site table against the merged W11b2 code. | Section 1's closure and W86-DESIGN section 8: the two-transfer delayed-write test, the A0 cases, and eight-transfer native timing with the escrow-consumer measurements; the hard criterion is "an actionable flight that did not step: 0". | 1 GameTest run and about 10 starts; about 1 hour (W86's planning figure). |
| P1-F8-D / done | Nothing: the design note [W85-DESIGN.md](W85-DESIGN.md), revision 3 (the minimal option), frozen by Root on 2026-10-16 after two read-only reviews. | No format change: today's order kept, no-change pre-checks, `DISASSEMBLY_PENDING`, a write-back of the identical T and a restoration latch. | 0 |
| P1-F8 / P1-F8-D reviewed | The durable record and the retained claim in `RocketDisassemblyService`. | Section 1's closure, with native cuts on both sides of restoration, removal acknowledgement and release, a failed removal writer and two restarts. | 1 GameTest run and about 6 cuts (18 starts); about 1 hour. |
| P1-N2 / W11b2 merged | The first, self-contained commit of the P1-F3 leaf: `failBackToSource` checks today's two-slot queue before it changes anything (W86-DESIGN section 5). | Section 1's closure and G-N2 with its stop or abort variant; "one release" waits for P1-F3's scheduler commit. | 1 GameTest run and 9 or 12 starts; about 0.9 hours. |
| P1-N1 and Root-R1 to Root-R9 / W11b2 | W11b2's own work, not W11b3's. | Section 1 and section 2, as pinned at the W11b2 merge. Continuation 15's historical 3,258 unit tests and 1,059 GameTests do not override its failed log checker or its missing native flight evidence. | W11b2's own estimate. |

**W11b3 leaves:**

| Leaf / dependency | Builds | Acceptance and verification | JVM starts, lock hours |
|---|---|---|---|
| B3-U / W11b2 merged | The authority move of section 2: the runtime-owned `EscrowAdmissionAuthority`, the PROPULSION delegate and its three states, coordinator attach, the startup lookup and early bind, the test-hook retarget method, and the corrected Javadoc terms. | Extend `Test/resourceescrow/EscrowAdmissionAuthorityTest`: 63 propulsion plus 1 bay and the reverse; mixed 8 per rocket and the ninth refusal; UUID collision; durable, queued, captured and UNKNOWN copies counted once; REPAIR and released-unresolved receipts; NOT_LOADED and NO_FILE; over-capacity restart without eviction; REJECTED re-arm with the same ID; exact release. Add each delegate state, a fill and a bay admission in the first tick, and ledger replacement (retarget, refusal while work is held, no throw). The five-marker limit is an additional production rule; synthetic 7+1 authority fixtures do not authorize seven native fills. W11b3 uses its real loader writer plus a test bay index; a real mixed-store S2 remains W27c1's and W27d's prerequisite before bay enabling. | 1 GameTest run; about 0.5 hours. |
| B3-M / B3-U | Pure codecs for the tank root, binding, linker selection, carrier and escrow section, and the `arce-escrow-fluid-tank-1:` signature; bank selection and refill arithmetic; `FluidFuelLoaderStatus`; `EscrowCoordinator.prepareFill`. | A0 tests: codec boundaries, round trips, future and unknown versions and sizes for the tank, binding and carrier; the escrow section with one marker, 256 bytes, and future and unknown versions; a pin of the signature. The unique bank; exactly 1, 749 and 1,000 mB; full, incompatible and ambiguous cases. Retired plus live markers 0 to 5 and the sixth refusal; a same-capture mismatch; native tag preservation; integer overflow. **Root-R6 headroom:** with a root admitted to its pinned capacity, a fill that would take the floor-retirement headroom is refused without mutation, and a floor retirement then passes; the test fails if the headroom is removed. **Root-R8 charging:** `prepareFill` refuses when a candidate is charged by its preview only. Run each new class alone, then all unit tests. | None. |
| B3-A / B3-M | `FluidFuelLoaderBlock`, `FluidFuelLoaderBlockEntity`, `FluidFuelLoaderMenu`, the client-only screen, `LinkerItem`, the carrier, their registrations and the loot entry. Admission is not wired. | A1 with the real loader and rocket: simulation; stale and reentrant capability; owner, range and unloaded rejection; duplicate IDs; menu replay; unload and reload; one-carrier break and place; the carrier owner rule, operator and non-operator foreign-owner cases; protected removal by player, explosion and command replacement, for an escrow-locked fixture and an unsupported root; non-operator NBT, asserted as `ForceFieldGameTests` line 308 does; cut-G2 (occupied or riding rocket, mount and reconnect, `/ride`, `/tp`, travel, bypass movement). | runData once and 1 GameTest run; about 0.5 hours. |
| B3-E / B3-A | The loader's fair queue calling `prepareFill`; production ledger creation and the creation baseline; the loader endpoint adapter and its signature registration; proof validators; production reconciliation in place of `RECONCILIATION_UNIMPLEMENTED`; restart reconciliation. Production admission stays disabled and a gated switch exercises it. | A1: two competing devices; mixed-bank prefixes; the first fill creating the ledger; every refusal compares both resource envelopes and the queue and count state; actual production recovery, not chest or armor-stand fixtures. | 1 GameTest run; about 0.5 hours. |
| B3-D / B3-A | `V180FluidFuelLoaderData`, the art, models, blockstates, recipes with the empty-tank ingredient, advancements, English and Chinese text, and both provenance files. | runData twice with a clean diff; resource references; recipes and reachability, including the filled-tank refusal; registry IDs; provenance hashes; loot; a CLEAR art screen. S1: packaged dedicated-server sidedness without client class loading; old-world upgrade without escrow; the old item loader unchanged; old entity schema 2 to 3; a valid new device and root restarted twice; future and malformed roots preserved, and downgrade refused or preserved as applicable; cut-G2's S1 part. | runData twice, 1 GameTest run and about 6 S1 starts; about 1 hour. |
| B3-S1 to B3-S6 / B3-E, B3-D and the P1 leaves | Nothing: S2 evidence by cut family, below. | Below. | About 4 hours in total. |
| B3-V / B3-D | Nothing. | V1 on a real GPU: labels, Fluid and status display, binding feedback, scales and resource reload. V2 with two real clients: ownership, competing interaction, `PREPARING`, pending and refusal synchronization, disconnect, and the passenger case of section 3. V0 and fake players do not replace these. | One native server session with real clients; about 2 to 3 hours, scheduled with the owner's client campaign. |
| B3-B / B3-E and the P1 leaves | A benchmark harness only. | The budgets below, distinguishing native reference-load results from microbenchmarks, and the cycle time per device. No throughput acceptance from code constants. | About 6 hours, including the four-hour soak of docs/17. |

**Required D3-A S2 cuts.** Every cut stops on each side of a durable acknowledgement, including after
replacement before the callback, and restarts **twice**. The control totals are W11A section 7's:
A=5,000 to 4,000 mB and bank 0 to 1,000 mB; with B=5,000, A=B=4,000 and bank 2,000. There is no
tentative withdrawal at any cut. Every S2 leaf pins the source commit, the JAR and the test hooks, and
compares native raw quantities, markers and epochs, not only status strings.

- The **full real-loader matrix** runs only where the loader adapter takes part: cut-N0 to cut-N3,
  cut-K0 to cut-K10, cut-C1 and cut-C2, D2-C0 to D2-C8, cut-E1, cut-E2, cut-E4, cut-R1 and cut-R3. Cuts
  that name two devices use two competing loaders. Mono-propellant runs everywhere; bipropellant runs on
  the representative subset named per leaf.
- The **reused families** are cut-E3, cut-E5, cut-R2, cut-O1 to cut-O4, O-veto, cut-G1, cut-G3 and D2-F0
  to D2-F8. They reuse W11b2's integrated evidence, plus one real-loader representative each.

| Leaf | Cuts | Scope and required outcome | Estimate |
|---|---|---|---|
| B3-S1, creation | cut-N0: stop after creation and queueing, before the baseline write. cut-N1: stop after the baseline file is replaced, before its callback. cut-N2: a baseline that fails definitely, and one that ends UNKNOWN. cut-N3: a refused first attempt (SERVER_FULL from a test bay index, a preflight refusal, and UNAVAILABLE while NOT_LOADED). | After each restart the world has no ledger or a valid empty one; the tank and the bank are unchanged; no sequence was issued and no reservation remains; the next attempt succeeds. The player sees `PREPARING` while the baseline is pending and `LEDGER_UNAVAILABLE` after three unresolved failures. Mono only. | 7 cuts, 21 starts; about 0.5 hours. |
| B3-S2, normal transaction | cut-K0 to cut-K10: before and staged L1; L1 replaced and callback; debit before credit and unsaved apply; source-only and destination-only store; force, readback and CONFIRMED; L2 staged, replaced, callback and release. | Real loader; bipropellant at cut-K2, cut-K6 and cut-K9. | 14 cuts, 42 starts; about 0.75 hours. |
| B3-S3, chains and reconciliation | cut-C1 and cut-C2: a two-device chain, unequal endpoint prefixes, latest-frontier proof, partial retirement and opposite-direction refusal. D2-C0 to D2-C8: production endpoint reconciliation on both sides of L1, the barrier proofs, L2 and the D2-C8 release. | Two competing loaders; bipropellant on one chain and one D2-C7 cut. | About 18 cuts, 54 starts; about 0.8 hours. |
| B3-S4, storage failures | cut-E1: observed own-store and superseding-store failure. cut-E2: a newer save, generation or unstable header, an external `.mcc`, and decoded bounds. cut-E4: definite and UNKNOWN L1 and L2, overlapping autosave, and a stale callback. | Real loader, mono. ADR-065 and R-032's boundary holds: unobservable native failures are diagnostic residuals, **not PASS guarantees**, and observable failures must block retirement. No new injection is authorized. | About 11 cuts, 33 starts; about 0.65 hours. |
| B3-S5, recovery | cut-R1: 255 and 256 floor saturation, distinct and coalesced keys, UNKNOWN L1 with unload and restart. cut-R3: a missing, replaced or behind ledger, and a foreign, missing, OTHER or relocated loader endpoint. | Real loader, mono. No historical fallback and no forced chunk loads. | About 10 cuts, 30 starts; about 0.6 hours. |
| B3-S6, reused families | One real-loader representative each of cut-E3, cut-E5 (fair progress of loader work past held or unloaded records), cut-R2, cut-O1 to cut-O4 (chains and normalization, each survivor or absence proof, and the O3J boundary), O-veto with the destination saved, cut-G1 (old, future or invalid loader root and endpoint, native veto and saturation), cut-G3 (shutdown with loader L1 or L2 queued, in flight or UNKNOWN, and a failed close), and D2-F0 to D2-F8 (with P1-F8's and P1-N2's crash windows, P1-F9's failed respawn, the reservations' longest sticky reason, and a successor refill only after D2-F8). | W11b2's integrated results for the rest of each family. Keep UNKNOWN accounting explicit: no false zero-loss claim, and never 6,000 mB available from the 5,000 mB fixture. | About 12 cuts, 36 starts; about 0.7 hours. |

The reduced matrix is about 72 cuts and 216 JVM starts, about 4 lock hours in total, split across six
leaves so that no single run holds the heavy-JVM lock for long. cut-G2 is required A1 and S1 coverage in
B3-A and B3-D, not a forced-stop cut.

**Benchmarks and budgets.** At most 64 commits per tick through a fair queue; at most 32 KiB charged per
fuel operation; at most 8 reconciliations per tick; one endpoint capture per tick; ledger writes start at
most once per 10 ticks; barriers at most once per Level per 20 ticks (16 terrain and 16 entity chunks, 32
reads); 16 MiB of detached memory. Fuel rebuilds no snapshot. Charge the full-envelope and hash work, and
cache validated immutable data or use bounded preparation rather than skip checks. Shared reconstruction
remains one per tick, 16 MiB and 16,384 visits. Exercise 2,048 blocks, 128 payloads, 1 MiB of NBT and
complex tags; the largest snapshots and journal; 64 competing records (synthetic where the effective
admission is lower); both saturated 256-region caches; blocked and unloaded work; and eight governed
flights including pruning. Report main-thread capture, hash and publication time, IO, queue age,
allocations and GC, chunk-load latency, and the cycle time per device.
[docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) applies: Xmx at most 4 GiB, RSS at most 5.5 GiB, mean
at most 25 ms, P95 at most 50 ms and P99 at most 100 ms, the unchanged spike and soak rules, and no
watchdog, OOM or forced loads.

**Execution.** Targeted A0 tests and the full `gradlew.bat --offline test` need no heavy lock. A full
`clean build`, runData, GameTest, S1, S2, the native server and native benchmarks require
`D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm`; check that C: and D: each have at least 10 GiB free first.
Run the unchanged GameTest log checker. Keep the command, exit, result and cut manifests, the logs, the
source commit, the JAR and raw-store hashes; delete only owned disposable servers and exports. No source
archives, no checksum targets in build directories, no new file over 50 MB, and at most 100 MB of
evidence per slice. Missing native or client evidence remains NOT_RUN.

<div style="page-break-after: always;"></div>

## 6. Proposed merge text and decisions requiring Root

**Proposals only.** Root records the protective scope before merging the implementation, and adds exact
commit and evidence references when they are verified. This note accepts no risk and closes no finding.

### R-035 proposed update

> **Scope.** W11b3 creates an escrow ledger in a player world at its first admitted fill, so the current
> row's "production worlds have no ledger" ceases to apply. The protections that the row confines to
> worlds with a ledger, which until now occurred only in tests, then reach player worlds: invalid rocket
> or block-entity envelopes are isolated with their linked records, and an unserializable block-entity
> root uses `GuardedChunkSaves`. A component hold covers one connected escrow component; with one marker
> per device and five live-plus-retired markers per rocket, that is one rocket and at most five loaders.
> Loader and rocket resources stay locked through exact durable resolution, including unload, restart,
> UNKNOWN outcomes and repair. A refused admission moves no fuel, and a retry never abandons authority.
> Intake, player and pipe transfers, and ADR-027 item-loader transfers, remain outside D3-A.
>
> **Capacity.** The shared and decode limits stay 64 per server, 8 per rocket and 1 per device. Admission
> also enforces five live-plus-retired marker IDs per rocket and the combined reservation within 1 MiB,
> which charges every reserved region at its ceiling (Root-R8). Fill admission always keeps headroom for
> one floor retirement, so new fills cannot starve retirement (Root-R6). A floor retirement that does not
> fit refuses without mutation and logs one bounded operator line, "Escrow is full; retry floor
> retirement after other settlements or pruning." (Root-R5). Root publishes the effective capacities that
> W11b2 pins at merge, guarded and unguarded, with and without retained floors, and does not present 64
> as promised throughput. Stranded retired markers can permanently prevent fills, so bounded pruning must
> progress. Permanent receipts (64, never collected online) can exhaust the space. The READY, ARMED and
> terminal bounds and interlocks remain, and an offline passenger can hold a handoff indefinitely.
>
> **Save and load refusal.** Root-R1 to Root-R9 add no sticky-reason save refusal and no migration or load
> refusal for old-rule fixtures; the existing refusal of older development formats remains. A failed
> managed-start baseline refuses startup and preserves the files; a later failure marks escrow
> unavailable with one bounded diagnostic, never a tick exception. A downgrade needs a whole-world backup.
>
> **Unsupported loader roots.** A loader root that is invalid, future or names a Fluid that is not
> registered disables the device and keeps its raw data. Such a device is protected as R-038 protects a
> bay: player removal refuses, explosions skip it, and it drops nothing. The scope is that one device.
> The state lasts until its cause goes: a device whose Fluid's mod is installed again revalidates and
> works on its next load, while a future or invalid root waits for a supporting jar or offline repair, and
> restarts do not clear it. Following [W27B-R1](W27B.md#w27b-r1), a root that writes and reads back
> losslessly, or whose check exceeds its work budget, is kept verbatim and saved; operational oversize
> alone is not save denial. Observed native incompatibility uses `GuardedChunkSaves`: the whole terrain
> chunk is refused, and after 256 denied coordinates the Level's subsequent terrain saves are refused
> until the Level closes. Entity and global stores still save separately. No block-entity save exception,
> omission, normalization or dirty clearing is used as routine control. `/setblock`, `/fill` and raw
> replacements remain detectable repair residuals, not salvage.
>
> **Logs.** One line per state, reason or backoff transition, never one per tick. Repeated transitions and
> inherited errors have no lifetime cap.
>
> **Recovery.** Retryable IO recovers online. Contradictory or missing authority, exhausted capacity and
> incompatible roots need offline repair on a whole-world copy: preserve the originals and hashes of every
> Level's region, entity and POI files, the global journals, ledger and data, the player data and the
> metadata; verify conservation, fresh proofs and two saves and restarts before replacing the whole
> world. Never reset one store. R-031, R-032, power or OS loss, per-file rollback, UNKNOWN settlement loss
> and W13-N1 occupancy remain unaccepted.

### ADR-065 proposed implementation note

> W11b3 adds `fluid_fuel_loader` and the rocket-binding half of the linker; cargo variant 5 remains W11c.
> One tank of 16,000 mB with an 8 KiB native bound moves at most 1,000 mB per device per 20 ticks to the
> unique eligible bank; ambiguity refuses without a bank packet. Bounded, versioned roots, complete native
> capture and settlement reservations, with Root-R6's headroom and Root-R8's charging, use the same
> runtime-owned authority as the future bay; test admission is never used. The first fill creates the
> ledger after a checked empty baseline. Enable only after integrated production-loader and two-device
> S2, prerequisite closure and benchmarks, citing the source, the JAR and the Root or owner decision;
> until then admission stays disabled. Close P1-F3, P1-F8, P1-N1 and P1-N2 only against verified
> evidence; P1-F9 was delivered by W77. R-035 records the scope; the ordinary-transfer residuals, R-032,
> BAY_SOURCE_PROTECTED and the Required Gates remain unchanged.

### Open decisions, with recommendations

| Decision | Recommended answer and consequence |
|---|---|
| Prerequisite ownership | **Decided** by Root on 2026-10-16 (decision 4): P1-F9 is done by W77; P1-F3 and P1-F8 each get a design note and a read-only review before their leaf; P1-N2 is a leaf; P1-N1 stays with W11b2's governed-flight work; P1-F3, P1-F8 and P1-N2 follow the W11b2 merge. Root keeps the D2-C and D2-F paths and Root-R1 to Root-R9 in W11b2, and reviews the integrated diff and native results before enabling. Branch movement is not evidence. |
| Authority lift from W27c1 | **Decided** (decisions 2 and 4): B3-U moves the lifetime and injection only, merged first; section 2's registration contract is then frozen for W11b3 and W27c1. Bay persistence, its index and the retained quotas stay W27c1. |
| W27A section 2 wording | Root records section 2's contract delta: the authority belongs to `EscrowRuntime`, and each store's coordinator attaches to a delegate that the runtime registered. |
| Reservation decisions after continuation 18 | W11b3 follows the decisions in force when W11b2 merges. Root re-checks section 2 against them at that point. |
| Ledger row 82 | Root updates the target note of `block:fuelingStation` when W11b3 merges. `fuel_loader` stays its target; `fluid_fuel_loader` is the C17a Fluid extension that ADR-065 already lists. |
| W13-N1 | A separate landing fix, still open. Prefer loaded-arrival readiness first; keep the limitation for rockets outside the footprint or separately review an index. |
| Capacity and UNKNOWN loss | Keep the bounds and Root-R1 to Root-R9; publish the capacities W11b2 pins at merge and the permanent-receipt limits. The owner explicitly dispositions R-035 and UNKNOWN loss before enabling. Never invent zero loss. |
| Device, loot and network | After review, adopt the separate device without FE, the bounded custom carrier with section 3's owner rule, and the vanilla interaction surface; no arbitrary non-operator NBT restoration. |
| Enabling and bay compatibility | Review the integrated evidence first. Loader use binds the rocket; the neutral-only bay later refuses it with BAY_SOURCE_PROTECTED until W27f. No bay admission or limitation is accepted here. |

W75 and W75R deliver only this design and its execution record. They change no code, tests, assets, ADR,
risk register, ledger, status, build or workflow files, and this document satisfies no runtime Gate.
