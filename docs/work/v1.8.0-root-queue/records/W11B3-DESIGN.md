# W11b3: fluid propulsion loader and production admission

Date: 2026-10-15. **Proposed design, not admission approval or runtime evidence.**
Assignment: [W75](../TASK-W75-W11B3-DESIGN-NOTE.md). Five pages, separated below.
Shared baseline: `0c5021976838cdcf9a19aa614861725075ee354d`. W11b2 is inspected **only at
`d178e727`**, including frozen `W11B2-DESIGN.md`, `W11B2-DESIGN-2.md` revision 1 and
`W11B2.md` continuation 15. Later work is not presumed complete.
In code citations, `J` means `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`,
and `T` means the same package under `src/test/java`; pinned citations are reproducible with
`git show d178e727:<expanded-path>`. Names explicitly called proposed do not exist yet.
Authority: [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
and [W11A sections 7–8](W11A-DESIGN.md). Root owns approval and central-document changes.

## 1. Scope and blocking prerequisites

| Ledger unit in the [content ledger](../../v1.8.0-content-ledger.csv) | W11b3 disposition |
|---|---|
| `block:fuelingStation`; follower `block_entity:ARfuelingStation` | Deliver native-fluid propulsion extension `advancedrocketrycommunity:fluid_fuel_loader`. Preserve `fuel_loader`, schema-2 batches and recipes. Supplement the REDESIGNED row; no retroactive crash-atomicity claim. |
| `libvulpes:java/itemLinker`; follower `libvulpes:recipe/linker` | Deliver `advancedrocketrycommunity:linker`, recipe and rocket-binding half. Primary row remains incomplete until W11e; no LibVulpes copy. |
| `block_variant:loader/4` and `/5`; `block_entity:ARFluidUnloader` and `ARFluidLoader` | **W11c**, cargo Fluid unload/load and pressurized-tank snapshot adapter. `/5` is `rocket_fluid_loader`, not `fluid_fuel_loader`; propulsion banks are not captured cargo tanks. |
| `block_variant:loader/2` and `/3`; `ARRocketUnloader` and `ARRocketLoader` followers | **W11d**, item cargo. |
| `block:monitoringStation`, remaining linker roles | **W11e**, full monitor/control and linker. No remote/redstone launch here. |
| `block_variant:loader/1`; `block_entity:ARsatelliteHatch` | W27b supplies the block only; W27c1–c3 supply receipt/registry/journal infrastructure, W27d deployment, W27e evidence/enabling. Credited-source bay support remains W27f. |

The existing `J/rocket/fuel/FuelLoaderBlockEntity.tickServer/findTarget` uses item batches,
25 abstract units/tick and a nearby loaded-rocket search ([ADR-027](../../../decisions/ADR-027-ROCKET-ITEM-FUELS.md)).
Do not retrofit it into a Fluid device or reuse its nearest-target search. Hand exchange and that
loader retain their ordinary two-store limitations, while obeying escrow interlocks. Gas charge pad
(D4), cargo, bay deployment, automatic launch and signed settlement-loss accounting are non-goals.

**Before enabling any admission**, close the following part-1 findings. These IDs are not W02 F3
or design-2's normalization F3/flight F8. At the pinned commit none has complete closure evidence:

| Finding / pinned evidence | Required closure, in W11b2 before integration or an assigned W11b3 prerequisite |
|---|---|
| F3 scheduling: `J/rocket/server/RocketTransferService.tickSource/spawnDestination` and `RocketTransferSavedData.pollWriter` still wait for whole-root acknowledgement. | Use exact per-record version/absence leases, retaining one writer, bounded captures and final guards. A two-transfer delayed-write test and eight-transfer native timing must show independently acknowledged work advances. `resourceMutationUnproved(logical)` alone is insufficient. |
| F8: `RocketDisassemblyService.executePlayer/disassembleForReleaseTest` releases the landed reservation **before** `execute`. | Retain claims through failure. Successful restoration must fence old-transfer recovery before checked removal/release. Test obstructed/rollback failure and S2 between restoration/removal/release: no lost claim or rocket recreated over blocks. Moving a call after discard alone is insufficient. |
| F9: `RocketTransferRecoveryService` rebinds and `journal.put(resumed)` precedes deferred `addFreshEntity`; failure repeats work. | Retry one acknowledged identity/phase without redundant writes/new UUIDs. Test repeated spawn rejection, unchanged write count/sequence, later success and restart without duplication. |
| N1: `RocketEscrowGate.escrowIdle` permits a matching idle ledger; `RocketSettlementIdleGameTests.acknowledgedSettlementRestoresMountRefuelAndLaunch` ends at COUNTDOWN. Continuation 15 retains three unexpected fence warnings. | Partial, **not closed**. Preserve foreign/missing-ledger refusal; complete design-2 C0–C8/F0–F8, two receipt-backed and two ordinary-L2 flights with successor refill, replay/cancel tests and unchanged log checker. No blanket idle exemption. |
| N2: `RocketTransferService.failBackToSource` mutates/removes T before `afterAcknowledged`; two queued generations can refuse `queueEffect`. | Reserve continuation atomically before mutation/removal; full queue leaves source/T/pad intact. Test two occupied slots, delayed acknowledgement, one release, UNKNOWN/restart. Governed returns retain exact-source permits. |

**W13 N1:** neither new persisted parked-region indexing nor arrival-readiness changes are necessary
to the loader's preparation: it binds an already present stationary source and never selects or creates
an arrival. Retain W13's limitation explicitly; this note does **not** close it. For a separately assigned
landing fix, recommend the first option (loaded entity sections in all at-most-16 arrival chunks,
rechecked before placement, no loading). `NamedLandingPadOccupancy.discoveryBox` also covers origins
outside that footprint: that fix alone must not claim to detect every unloaded off-footprint rocket.
Complete occupancy coverage may still require the separately reviewed bounded parked-region index.

<div style="page-break-after: always;"></div>

## 2. Admission, reservations and one authority

**Exact enabling condition:** W11b1/completed W11b2 are reviewed/integrated; section 5's required
D3-A S2 cuts pass **on integrated source with the real loader and two competing devices**, alongside
prerequisite fixes, native timing and reservation proofs. Keep production disabled while gated S2
exercises the same adapter. Root reviews evidence before enabling; no runtime self-certification or
Gate approval. Pin the enable commit, smoke-test without hooks and rerun integration-affected cuts.

At `d178e727`, `EscrowCoordinator.productionAdmissionEnabled()` returns false, admission is package-private
`prepareTest(op, observedSection, completion)`, and ledger creation is `ResourceEscrowSavedData.forTests`.
**There is no existing production admission entry point.** Proposed `EscrowCoordinator.prepareFill`
accepts a validated, immutable native admission capture and returns a typed result. Extract the common
reservation/queue core from `prepareTest`; do not invoke or ungate test fixtures from a block. Add a
production creation method, called only for an otherwise eligible attempt, with checked baseline
acknowledgement before L1. Ordinary queries, unbound loaders and unrelated worlds create no ledger.

The loader's fair server-thread queue, not its menu/capability, is the sole production caller:

1. Require enabled/available storage, claimed device, exact binding/matching owners, same Level,
   FULL terrain/loaded entity sections, <=6 blocks, stationary ASSEMBLED/FUELED/LANDED, no actual/
   assigned passengers or riding. Check assembly recovery, checked journal NONE/settled COMMITTED,
   bay/flight holds and both resource-mutation layers.
2. Select the unique eligible bank; simulate and capture complete envelopes/identities/epochs/hashes/
   owner/snapshot/guard. Markers come from that **same capture**, checked against `beforeWrapper`
   (`EscrowNativeBinding.Admission`, not fixture `captureTest`). Revalidate before publication; no
   external callbacks during apply. Same-direction chaining uses coordinator prefixes, never an
   ordinary-lock or frozen-frontier bypass.
3. Run `EscrowSettlementCapacity.requireAdmission` and the corrected combined reservation, then
   `EscrowAdmissionAuthority.tryReserve(..., PROPULSION)`; install queue/endpoint locks as one owner-thread
   action. Refusal issues no sequence, changes no tank/bank, and leaks no slot. Definite pre-L1 failure
   cancels only unapplied, uncaptured work; UNKNOWN retains candidate/reservations.
4. L1 exact acknowledgement authorizes C2 apply; joint own-store/force/stable-readback authorizes C7 L2;
   only its exact acknowledgement permits C8 release. Add real loader registration, signatures, proof
   validators and restart reconciliation. The pinned `RocketEscrowAudit.attempt` still returns
   `RECONCILIATION_UNIMPLEMENTED`; a renamed fixture is not production recovery. No historical resource
   fallback, unproved success callback, or direct tick-time tank-to-rocket mutation is allowed.

**Root reservation decisions, not inferred authority:** 2026-10-15 file
`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`, SHA-256
`1cff184db88c738e8f697ed793298f14bf50a9e444c52ef4d851b05e066ac64d`:

- **R1:** preview the candidate's merged component to prove operation/receipt fit, but charge **every
  unsettled component, including it, at the fixed ceiling**. Settled components keep their charge;
  include floors, receipts, both flight tables and framing. No bound increases. Five live-plus-retired
  marker IDs is the measured rocket admission limit; eight remains the decode/shared-quota limit.
  The root's effective capacity can be below 64: Root's unguarded arithmetic is 63; guarded fixtures
  must be measured and pinned, not advertised as 64. `EscrowSettlementCapacityTest` pins five/six;
  `EscrowAdmissionCaptureTest` pins the marker-capture digest, both at `d178e727`.
- **R2:** combined checks cover admission, floor O1, O3 receipts, O4 heads, ARMED/terminal writes and
  F1 reservations. Do **not** add them to `snapshot(false, ...)` sticky-reason/autosave persistence;
  it adds no authority and stays governed by existing codec bounds, with no new save refusal.
- **R3:** strengthen `EscrowReservationTransitionTest`: fill measured capacity, persist the longest
  sticky reasons through checked autosave, assert repaired preflight, subsequent O1 and ARMED succeed.
  It must fail under preview-only candidate charging. The pinned test only prints that refusal;
  its successful test result is not this proof.
- **R4:** old-rule operations exist only in development fixtures, not player roots. Add no migration
  or load refusal for them. Their reason saves still persist; existing authority-growth checks refuse
  without mutation until resolution/pruning frees space. Do not confuse this with design-2's already
  specified refusal of older development **formats**.

**Shared authority:** move the one `EscrowAdmissionAuthority` from ledger-owned `EscrowCoordinator`
to server-lifetime `EscrowRuntime`, injecting it into the lazy coordinator. W27c1 reuses it. Index unique
durable/queued/captured/UNKNOWN operations, including REPAIR/released-but-unresolved bay receipts.
Startup indexes must be validated: NOT_LOADED refuses; NO_FILE means known-empty. No timeout release
or eviction. Shared 64/server and 8/rocket limits are additional to five-marker/byte/one-device limits.
[W27B](W27B.md) has **no** receipt store/admission. W27c1 registers BAY as NOT_LOADED before admission
and owns retained 4,096/128-owner quotas; implement no bay store here. Contract:
[TASK-W27A2 F3](../TASK-W27A2-BAY-REDESIGN.md), [W27A section 2](W27A-DESIGN.md).

<div style="page-break-after: always;"></div>

## 3. Device and binding contract

Proposed separate pure codecs/service and thin `FluidFuelLoaderBlock`, `FluidFuelLoaderBlockEntity`,
`FluidFuelLoaderMenu`, client-only screen and `LinkerItem`; register stable IDs with DeferredRegister.
No captured-world-bank capability or general cargo framework. No FE cost or propulsion unloader.

| Surface | Proposed contract |
|---|---|
| BE persistence | `arce_fluid_fuel_loader`, schema 1, <=32 KiB/depth 20/2,048 nodes: owner, fresh placement UUID, binding, native tank, schema-1 escrow section (<=256 bytes/one marker). One Fluid, 0..16,000 mB, <=8 KiB native envelope with native round-trip/depth/node checks. Strict fields/types/registered canonical ID; no truncation/substitution. Missing root means empty/unclaimed. |
| Rocket binding | Schema 1, <=512 bytes/depth 8/32 nodes: logical UUID, Level ResourceLocation (<=128 ASCII), physical UUID/position **locator**, optional station UUID. Reject replacement/moved identity, owner/context change or duplicate. Explicitly rebind successors; no nearest scan/loading. Persist binding/tank across restart. |
| Linker selection | `arce_linker`, schema 1, <=1 KiB/depth 8/32 nodes: one device UUID/Level/BlockPos, no arbitrary NBT/entity number/list. Select loaded loader, then server-side entity use binds rocket. Sneak-use clears idle selection/binding; protected devices refuse. Invalid/future selection is inert and explicitly clearable. Other roles remain W11e. |
| Capabilities | One local `FLUID_HANDLER`, fill/drain only claimed/valid/loaded/unlocked; never drains rocket. No item inventory/FE. Simulation is side-effect-free; stale handles, wrong thread/reentrancy return zero/empty. All sides share guards, lifecycle invalidates handles. Automation uses persisted ownership, not FakePlayer privilege. |
| Menu | Owner-local <=8 blocks; exact BE/load/state `stillValid`. Display Fluid, mB/capacity, binding/status via bounded scalar container data; Fluid registry ID is display-only. Original drawn panel, no native tags/foreign coordinates/new custom C2S/protocol change. Separately validated local container use never admits rocket transfers. |
| Native drop | One nonstackable resource-bearing loader item, no separate Fluid spill. Carrier schema 1: owner+tank only, <=12 KiB/depth 18/1,024 nodes; no identity/binding/escrow markers. Validated placement preserves owner, creates fresh UUID/unbound device. `onlyOpCanSetNbt=true`; no arbitrary non-op BlockEntityTag restoration. Protected player/explosion removal refuses before loot. No-drop destruction is not salvage. |

**Every C2S** (vanilla block/entity use and menu/container click; no bank-selection packet): server
thread, connected live non-FakePlayer/non-spectator sender, correct held item/hand/menu/window/state;
world border/dimension, loaded chunk **before** lookup, type/device UUID, owners/role, stationary/
resource state, actor <=8 blocks and endpoints <=6. Both binding uses require the owner online;
no operator ownership bypass. Validate the resolved entity, not its incoming integer ID as authority.
Linking: <=one change/player/10 ticks; stale/replayed requests refuse. Client amounts/banks/costs/
snapshots are never authoritative. Owner logout after L1 does not stop recovery.

**Player refusal:** proposed `FluidFuelLoaderStatus` members `ADMISSION_DISABLED`, `ESCROW_FULL`,
`LEDGER_UNAVAILABLE`, `TRANSFER_JOURNAL_UNPROVED`, `ESCROW_PENDING`, `UNBOUND`, `STALE_BINDING`,
`WAITING_UNLOADED`, `OUT_OF_RANGE`, `NOT_OWNER`, `ROCKET_BUSY`, `EMPTY`, `BANK_FULL`,
`INCOMPATIBLE_FLUID`, `AMBIGUOUS_BANK`, `UNSUPPORTED_DATA`, `INVALID_DATA`, plus `READY`/`TRANSFERRING`.
Keys use `message.advancedrocketrycommunity.fluid_fuel_loader.<lowercase_member>` in en_us/zh_cn.
Examples: “Escrow capacity full; no fuel moved.” / “托管容量已满；未移动燃料。”;
“Binding is stale; bind the loader again.” / “绑定已失效；请重新绑定装载器。”
`ESCROW_PENDING` instead says resources are locked pending durable confirmation: it must not claim
“no fuel moved” after L1/apply. Never expose tentative credit or label a held operation complete.

## 4. Exact refill and resources

[W02F](W02F.md)'s F3 refill uses `min(1,000, local mB, eligible bank room)`, integer mB,
at most once/device/20 ticks with no unload/restart catch-up burst. For 1,251/2,000 mB and a
1,000 mB input, transfer 749 and retain 251 locally; no rounding or whole-container requirement.
Filter banks by propulsion role, current catalog, complete native Fluid compatibility and positive
room. One eligible bank selects PRIMARY/OXIDIZER server-side; more than one gives AMBIGUOUS_BANK;
none distinguishes incompatible/full. Never overwrite a nonempty different-tag bank or abstract fuel.
Zero-side-effect simulations precede capture. Catalog changes invalidate preparation, not a durable
operation's frozen payload. Existing whole-container drain limitations remain; no sub-container drain
or propulsion-unloader claim is made. Intake/container/player or third-party pipe persistence is
ordinary native storage, **outside** the loader-to-rocket D3-A guarantee.

Proposed shapeless recipes: loader = `pressurized_tank` + `liquid_io_board` + `control_board` +
`machine_casing`; linker = `tracking_circuit` + `minecraft:iron_ingot` + `minecraft:redstone`.
Verify all ingredients in the integrated recipe graph. Original generated 16x16 tank/port textures,
linker icon, JSON models, blockstates, loot, recipes/advancements and EN/zh text go under
`src/generated/v1.8/resources` via a proposed `V180FluidFuelLoaderData` provider; reuse no legacy art.
Before authoring, add `docs/provenance/v1.8.0-c17a-fluid-fuel-loader-new-resources.md`, listing
NEW code/text and GENERATED output with generator/target hashes, MIT, author/reviewer and date,
as [docs/08](../../../08-ASSET-LICENSE-AND-PROVENANCE.md) requires. Existing sealed provenance is immutable.
Any later upstream substitution needs its **own** UPSTREAM_AR_MIT row with source commit/path/hash,
target/hash and transformation before copying, and belongs under `src/imported/v1.8/resources`.
The linker ledger row is not permission to import LibVulpes. W75 copies no asset or upstream file.

<div style="page-break-after: always;"></div>

## 5. Verification and implementation leaves

All entries below are **planned**, with no evidence produced by this design. Sequence: prerequisites
and reviewed contracts → model/binding → adapter/recovery → data/UI → integrated evidence → Root's
enable decision. Stable IDs identify independently verifiable work, not extra versions or Gate approvals.

| Leaf / dependency | Acceptance and verification |
|---|---|
| B3-P / W11b2 | Close section 1's five findings and R1–R4; complete design-2 C/F paths. Independently review actual persistence/admission diff before integration. Historical continuation-15 A0 3,258/A1 1,059 passes do not override its failed log checker or missing native flight evidence. |
| B3-M / B3-P contract | A0 codec boundary/round-trip/future/unknown/size tests for tank, binding and carrier; unique bank, exact 1/749/1,000 mB, full/incompatible/ambiguous cases; marker+live capacity 0..5 and sixth refusal, same-capture mismatch, native tag preservation and integer overflow. Run each new class alone, then all unit tests. |
| B3-A / B3-M | A1 real loader+rocket: simulation, stale/reentrant capability, owner/range/unloaded rejection, duplicate IDs, menu replay, two competing devices, mixed-bank prefixes, unload/reload, one-carrier break/place, protected explosion/command replacement and non-op NBT. Every refusal compares both resource envelopes and queue/count state. Test actual production recovery, not chest/armor-stand fixtures. |
| B3-U / B3-P | Extend `T/resourceescrow/EscrowAdmissionAuthorityTest`: 63 propulsion +1 bay and reverse, mixed 8/rocket and ninth refusal, UUID collision, durable/queued/captured/UNKNOWN copies once, REPAIR/released unresolved, NOT_LOADED/NO_FILE, over-capacity restart without eviction, REJECTED re-arm same ID and exact release. Five-marker limit is an additional production rule; synthetic 7+1 authority fixtures do not authorize seven native fills. W11b3 uses real loader writer plus a test bay index; real mixed-store S2 remains W27c1/d's prerequisite before bay enablement. |
| B3-D / B3-A | runData twice/clean diff, resource references, recipes/reachability, registry IDs, provenance/hash and loot tests. S1 packaged dedicated sidedness, no client class loading; old-world upgrade without escrow, old item loader unchanged, old entity schema 2 →3, valid new device/root restart twice, future/malformed roots preserved and downgrade refused/preserved as applicable. |
| B3-S / all above integrated | Packaged S2 matrix below, pin source/JAR and test hooks; real loader and two competing loaders. Repeat mono and bipropellant/native-tag cases; native raw quantities/markers/epochs, not just status strings. |
| B3-V / B3-D | V1 real GPU: labels, Fluid/status, binding feedback, scales/resource reload. V2 two real clients: ownership, competing interaction, pending/refusal synchronization and disconnect. V0/fake players do not replace these. |
| B3-B / B3-A, B3-P | Benchmarks below; distinguish native reference-load results from microbenchmarks. No throughput acceptance from code constants. |

**Required D3-A S2 cuts** from W11A section 7, on both sides of each durable acknowledgement,
including replace-before-callback and **two restarts per cut**:

- **K0–K10:** before/staged L1, L1 replace/callback, debit-before-credit and unsaved apply,
  source-only/destination-only store, force/readback/CONFIRMED, L2 staged/replace/callback/release.
- **C1/C2:** two-device chain, unequal endpoint prefixes, latest-frontier proof/partial retirement,
  opposite-direction refusal. Control totals: A=5,000 →4,000, bank=0 →1,000; with B=5,000,
  A=B=4,000 and bank=2,000. No tentative withdrawal at any cut.
- **E1–E5:** observed own-store/superseding-store failure; new save/generation/unstable header,
  external `.mcc` and bounds; eviction/force failures; definite/UNKNOWN L1/L2, overlapping autosave,
  stale callback; repeated failure, backoff/recovery and fair progress past unloaded/held records.
  Preserve ADR-065/R-032's boundary: unobservable E1/E3 native failures are diagnostic residuals,
  **not PASS guarantees**; observable failures must block retirement. No new injection is authorized.
- **R1–R3:** 255/256 floor saturation and distinct/coalesced keys, UNKNOWN L1 unload/restart;
  stale journal/duplicate/reconnect/destructive recovery; missing/replaced/behind ledger and
  foreign/missing/OTHER/relocated endpoint. No historical fallback or forced chunk loads.
- **O1–O4 and O-veto:** accept-current/abandon/floor settlement, chains and normalization, each
  survivor/absence proof and O3J/O4 release boundary, source-veto with destination saved. Keep
  UNKNOWN accounting explicit; no false zero-loss claim or 6,000 mB available from the 5,000 fixture.
- **G1/G3:** old/future/invalid root/endpoint and native veto/saturation; shutdown queued/in-flight/
  UNKNOWN L1/L2, failed close. **G2** (occupied/riding, mount/reconnect, `/ride`, `/tp`, travel,
  bypass movement) remains required A1/S1 coverage, not mislabeled a forced-stop cut.
- **Design-2 C0–C8/F0–F8:** full production endpoint reconciliation and subsequent governed flights;
  board/refuel/prune then immediate stop without a helpful autosave, complete-source checkpoint,
  ARMED, checked T, source/destination phases, passenger/source-absence proofs, handoff and cleanup.
  Include section 1's F8/N2 crash windows, failed respawn and reservations' maximum sticky-reason case.

**Benchmarks/budgets:** <=64 commits/tick, fair queue, <=32 KiB charged/fuel operation, <=8
reconciliations/tick, one endpoint capture/tick, ledger starts <=once/10 ticks, barriers <=once/Level/
20 ticks (16 terrain+16 entity chunks/32 reads), 16 MiB detached memory. Fuel rebuilds no snapshot;
charge full-envelope/hash work and cache validated immutable data/use bounded preparation rather
than skip checks. Shared reconstruction remains one/tick, 16 MiB/16,384 visits. Exercise 2,048
blocks/128 payloads/1 MiB NBT/complex tags, max snapshots/journal, 64 competing records (synthetic
where effective admission is lower), both saturated 256-region caches, blocked/unloaded work and
eight governed flights including pruning. Report main-thread capture/hash/publication, IO, queue age,
allocations/GC and chunk-load latency. [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md): Xmx<=4 GiB,
RSS<=5.5 GiB, mean<=25/P95<=50/P99<=100 ms; unchanged spike/soak rules, no watchdog/OOM/forced loads.

**Execution:** targeted A0 and full `gradlew.bat --offline test` need no heavy lock. Full `clean build`,
runData, GameTest, S1/S2/native server and native benchmarks require
`D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm`; check C:/D: >=10 GiB first. Run the unchanged GameTest
log checker. Keep command/exit/result/cut manifests, logs, source commit, JAR and raw-store hashes;
delete only owned disposable servers/exports. No source archives, build-directory checksum targets
or new file >50 MB; slice evidence <=100 MB. Missing native/client evidence remains NOT_RUN.

<div style="page-break-after: always;"></div>

## 6. Proposed merge text and decisions requiring Root

**Proposals only:** Root records protective scope before merging implementation and adds exact
commit/evidence references when verified. This note accepts no risk or finding closure.

### R-035 proposed update

> W11b3 creates escrow ledgers in participating production worlds; “production worlds have no ledger”
> ceases to apply. Loader/rocket operations stay locked through exact durable resolution, including
> unload/restart, UNKNOWN and repair. Refused admission moves no fuel; retry never abandons authority.
> Intake/player/pipe and ADR-027 item-loader transfers remain outside D3-A.
>
> Shared/decode limits stay 64/server, 8/rocket, 1/device. Admission also enforces five live-plus-retired
> marker IDs and all-component ceiling/receipt/floor/flight-table reservations within 1 MiB. Publish
> measured no-transfer/guarded effective capacities, not 64 as promised throughput. Stranded markers
> can permanently prevent fills; bounded pruning must progress. Permanent receipts (64 members, no
> online collection) can exhaust space. READY/ARMED/terminal bounds/interlocks remain; offline
> passengers can hold handoff indefinitely.
>
> R1–R4 add no sticky-reason save refusal or old-rule fixture migration/load refusal. Existing older-format
> refusal remains. Managed-start baseline failure refuses startup/preserves files; later failure marks
> escrow unavailable with one bounded diagnostic, never a tick exception. Downgrade needs whole-world backup.
>
> Invalid/future/unavailable-Fluid roots disable the device and retain raw data. Follow W27B-R1's
> distinction: lossless or work-budget-exhausted roots are retained verbatim/by reference; operational
> oversize alone is not save denial. Observed native incompatibility uses GuardedChunkSaves: whole
> terrain chunk refused; 256 denied coordinates can refuse subsequent Level terrain saves until close.
> Entity/global stores remain separate. No BE-save exception, omission, normalization or dirty clearing
> as routine control. Protected removal drops nothing; `/setblock`, `/fill`/raw replacements remain
> detectable repair residuals, not salvage.
>
> Log once per state/reason/backoff transition, not tick; repeated transitions and inherited errors have
> no lifetime cap. Retryable IO can recover; contradictory/missing authority, exhausted capacity or
> incompatible roots require offline whole-world-copy repair. Preserve originals/hashes for all
> Level region/entity/POI, global journals/ledger/data, players and metadata; verify conservation,
> fresh proofs and two save/restarts before whole-world replacement. Never reset one store. R-031/032,
> power/OS loss, per-file rollback, UNKNOWN settlement loss and W13 occupancy remain unaccepted.

### ADR-065 proposed implementation note

> W11b3 adds `fluid_fuel_loader` and rocket-only linker binding; cargo variant 5 remains W11c.
> One 16,000 mB/8 KiB tank moves <=1,000 mB/device/20 ticks to a unique eligible bank; ambiguity
> refuses without a bank packet. Bounded/versioned roots, complete native capture and settlement
> reservations use the same server-lifetime authority as the future bay; never test admission.
> Enable only after integrated production-loader/two-device S2, prerequisite closure and benchmarks,
> citing source/JAR and Root/owner decision. Until then admission stays disabled. Close F3/F8/F9/N1/N2
> only against verified evidence. R-035 records scope; ordinary-transfer residuals, R-032,
> BAY_SOURCE_PROTECTED and Required Gates remain unchanged.

### Open decisions, with recommendations

| Decision | Recommended answer / consequence |
|---|---|
| Prerequisite ownership? | Root keeps C/F paths and R1–R4 in W11b2, explicitly assigns remaining part-1 fixes, reviews integrated diff/native results before enabling. Branch movement is not evidence. |
| Authority lift from W27c1? | Move lifetime/injection only to W11b3; freeze section 2 contract. Bay persistence/index/retained quotas stay W27c1. |
| W13 N1? | Separate landing fix, still open. Prefer loaded-arrival readiness first; retain off-footprint limitation or separately review an index. |
| Capacity/UNKNOWN loss acceptable? | Preserve bounds/R1–R4, publish measured and permanent-receipt limits; owner explicitly dispositions R-035/UNKNOWN before enablement. Never invent zero loss. |
| Device/loot/network? | Adopt separate no-FE device, bounded custom carrier and vanilla interaction surface after review; no arbitrary non-op NBT restoration. |
| Enablement/bay compatibility? | Review integrated evidence first. Loader use binds the rocket; neutral-only bay later refuses BAY_SOURCE_PROTECTED until W27f. No bay admission/limitation acceptance here. |

W75 delivers only this design and its execution record. No code, tests, assets, ADR,
risk register, ledger, status, build or workflow changes; no runtime Gate is satisfied by this document.
