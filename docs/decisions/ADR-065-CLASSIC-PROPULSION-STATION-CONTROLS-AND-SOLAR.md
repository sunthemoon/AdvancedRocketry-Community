# ADR-065 - Classic propulsion, station controls and solar content (C17)

```yaml
status: ACCEPTED
revision: 4
date: 2026-10-03
owner: sunthemoon
target_version: v1.8.0
slices: [C17a, C17b, C17c]
depends_on: [ADR-026, ADR-027, ADR-035, ADR-037, ADR-041, ADR-044, ADR-046, ADR-047, ADR-049, ADR-050, ADR-054, ADR-058, ADR-059, ADR-061, ADR-062, ADR-063, ADR-064]
amends_if_accepted: [ADR-021, ADR-026, ADR-041, ADR-046, ADR-047, ADR-049, ADR-054, ADR-058]
owner_decisions_confirmed: [D1-A, D1-disassembly-A, D2-A, D3-A]
owner_decisions_pending: []
accepted_by: sunthemoon
accepted_at: 2026-10-03
acceptance_basis: owner confirmed D1-A/D1-disassembly-A/D2-A/D3-A; independently reviewed final contract has no unresolved Critical/High/Medium; conditional authorization retains exact leaf and runtime admission requirements
```

This is an accepted conditional contract, not a runtime delivery, ledger closure
or Gate result. Exact leaf inputs retain their independent admission review.
The owner receipt
dated 2026-10-03 confirms D1-A, D1-disassembly-A, D2-A and D3-A against reviewed
revision 3. The integrator accepts the independently reviewed revision 4 under
the owner's conditional authorization; this does not verify its implementation.
Acceptance changes no technical or numeric behavior. The unselected alternatives are not fallbacks;
changing a selected product boundary requires a new explicit owner decision.
Exact schema/writer/recovery amendments must satisfy section 13 before dependent
runtime interactions are admitted. No source or asset is imported by this document.

## 1. Scope and factual baseline

ADR-062 section 6 allocates 41 primary ledger units to C17: 24 to C17a, 9 to
C17b and 8 to C17c. The complete list is in section 12. Internal MERGED rows
follow their owning units; assets require their own existing asset-plan entry and
provenance, not an automatic import permission from this ADR.

The reviewed working tree is based on `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`
on `codex/v1.8.0-classic-content`, with uncommitted C15/C16 development work.
The acceptance cursor remains v1.0; ADR-060 permits v1.8 development but does not
pass any release prerequisites. C17 implementation must first verify the C16
dependency deliveries it actually uses.

### Existing modern capabilities

- `RocketForgeMetrics` gives basic engines 1,000 thrust, tanks 1,000 abstract fuel
  capacity, engine mass 100 and tank mass 50. ADR-026 external definitions use the
  same abstract units. `RocketBlockMetrics` has no propulsion-kind field.
- `RocketFuelState` is one immutable scalar amount/capacity and up to 64 exactly-once
  debit UUIDs. It is not a fluid tank or a bipropellant state.
- `RocketStructureSnapshot` is immutable schema 1 and includes captured container
  payloads and their hash. The default allowlist only captures chests/barrels.
  No live rocket cargo capability is present. Editing a detached payload is not a
  cargo transfer, and materializing the original snapshot after unloading cargo
  would recreate that cargo.
- `RocketEntityData` is schema 2; flight and transfer schemas are 2. Existing
  transfer/disassembly journals must continue to own recovery authority.
- The existing `fuel_loader` has ADR-027 schema 2, one frozen consumed-item batch,
  preserved remainders, owner binding, 6-block loaded-entity range and 25 units/tick.
  This public item-fuel contract is not replaced by C17.
- `StationState` record schema 2 has a fixed allocated region/pad, orbit body,
  gravity, vacuum and solar angle, but no orbital-distance or rotation-rate field.
  The checked update policy permits expansion, gravity and the separately checked
  warp relocation only. It rejects arbitrary geometry/angle mutations.
- ADR-046 supplies command-based station/warp controls with local connected-player
  checks; literal-English feedback and incomplete notices expire in v1.8.
- `StationSkyContextPacket` carries only an optional orbit-body ID, not altitude,
  phase, angular velocity or a station identity.
- ADR-049 logical satellite launch is bound to a connected player/terminal.
  Package identity and registry replay semantics exist, but there is no unattended
  rocket-bay launch entry point.
- The server celestial solar intensity is bounded 0..16. The station resolver uses
  the orbited body's intensity (Space fallback when missing), not client daylight.

### Pinned legacy facts, not copied implementation

The reference is approved AR commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`.
Relevant source hashes are checked against the inventory in the accompanying
`upstream-source-checks.json`. LibVulpes is not an authorized source.

- Basic mono/bipropellant engines contribute thrust 10 and rate 1; advanced mono
  and advanced bipropellant engines thrust 50 and rate 3. Nuclear nozzles give
  thrust 35/rate 1, limited by vertically connected cores, each supplying
  `1,000 * nuclearCoreThrustRatio`. The assembler rejects mixed propulsion modes.
  Tanks contribute 1,000. These old units are not silently substituted for the
  modern 1,000-thrust engine baseline.
- Fluid defaults are rocket fuel with multiplier 2, hydrogen bipropellant fuel,
  oxygen oxidizer, hydrogen nuclear working fluid. Thrust/capacity/core multipliers
  default 1; gravity affects fuel and hand fueling are enabled; fuel is required.
- The monitor shows linked-rocket altitude, velocity, fuel and launch state and
  requests launch from redstone. Loader variants access docked items/fluids.
- Station altitude is orbital distance, not a different body. Distance defaults
  4, is at least 4, and the UI represents 0..190 (values below 4 effectively become
  4), displayed as `distance * 200 + 100` km. The legacy GEO gate is 177..181.
- Orientation has three sliders (EAST/UP/NORTH), each 0..120 mapped to signed
  rates -60..+60. Target velocity is rate/72,000 per tick. Three phases/rates are
  saved; the sky applies UP yaw and EAST pitch, and EAST supplies celestial angle.
  No physical station-block or passenger rotation was found in those consumers.
  It is not purely cosmetic: the elevator rejects nonzero angular velocities and
  EAST/NORTH tilt. Solar insolation itself reads the orbit body's peak value,
  ignoring orientation; it is zero while warping. Fixing the legacy mismatched
  phase/degrees and NORTH display scale is a modernization, not copying those bugs.
- The legacy solar generator has a 10,000-energy buffer and approximately
  `floor(2 * multiplier * insolation)` output, with sky/day checks. The solar array
  is a one-layer 22-by-3 pattern: two power output cells and controller in the
  first row, 63 wildcard panel/air/allowed structure cells behind it. It counts
  panels and produces approximately 2 energy per panel times solar conditions.
- The beacon is a 5-by-3-by-3 structure with a redstone-block cap and structure
  column/base. The finder is a helmet module drawing directional HUD marks.
- The force-field projector grows/retracts a straight line, maximum 32, at one
  cell per 5 ticks. It has no energy demand, owner or protection checks. The new
  implementation must supply protection and exact projected-cell ownership.

## 2. Stable identities and compatibility

All new IDs use `advancedrocketrycommunity`; no legacy namespace or metadata IDs
are registered. The following are proposed stable block+item IDs:

| Slice | IDs |
|---|---|
| C17a | `advanced_rocket_motor`, `bipropellant_rocket_motor`, `advanced_bipropellant_rocket_motor`, `bipropellant_fuel_tank`, `oxidizer_fuel_tank`, `nuclear_rocket_motor`, `nuclear_fuel_tank`, `nuclear_core`, `fluid_fuel_loader`, `rocket_item_loader`, `rocket_item_unloader`, `rocket_fluid_loader`, `rocket_fluid_unloader`, `rocket_monitoring_station` |
| C17b | `warp_controller`, `station_gravity_controller`, `station_altitude_controller`, `station_orientation_controller`, `landing_pad`, `station_light`, `force_field_projector`, technical `force_field` |
| C17c | `planetary_beacon`, `rocket_satellite_bay`, `solar_generator`, `solar_panel`, `solar_array`, `solar_array_panel` |

New item-only IDs: `linker` (C17a), `beacon_finder` (C17c). Existing
`rocket_motor`, `rocket_fuel_tank`, `fuel_loader`, `warp_core`, `gravity_field_controller`,
canisters and satellite packages retain every ID and supported behavior.
`minecraft:concrete` variants replace legacy concrete through resource references
in pad recipes; no ARCE concrete duplicate is registered.

New BE type IDs equal their controller block IDs. Technical force-field ownership
uses BE ID `force_field`; the field has no obtainable item/loot and is explicitly
listed in the recipe graph exemption file. Plain engines/tanks/panels/lights need
no BE unless their current slice actually stores fluid/ownership.

Adding new reserved host propulsion IDs cannot rename or alter an external
ADR-026 definition for another block. API component/fuel class bytes and callback
semantics are unchanged; any proposed API addition is a separate contract.

1.12.2 world loading is not supported. No Missing Mapping is required for genuinely
new IDs; a renamed existing public ID would require a new approved migration ADR.
Disable switches keep all registrations/root data loadable and stop new work.
Downgrade means restore the pre-upgrade backup, never remove new blocks in an old
binary or force down-convert new resource roots.

All native resource roots preflight byte/depth/node bounds before copying or
decoding native objects. Unknown/future/bounded malformed roots are saved verbatim,
disable capabilities/intents and refuse ordinary removal that could normalize or
lose resources. For already-loaded oversized roots, retain the original Tag
reference, pass it through BE serialization, and veto the whole outgoing chunk
save if its own root exceeds the operational budget; restore the dirty flag for
retry. Throwing from `saveAdditional` alone is not protection because vanilla can
catch it and omit that BE. The combustion native chunk-save regression is the
baseline to extend, not evidence that all new roots are already protected.

## 3. D1: propulsion and fuel representation (owner-confirmed D1-A)

**Owner-confirmed D1-A:** preserve the current engine/tank baseline and item fuel
API, add separate actual Fluid content and distinct oxidizer accounting, and
capture propulsion data at assembly. Do not pretend the scalar fuel amount is
hydrogen plus oxygen. Do not add arbitrary mod callbacks in a flight tick.

The proposed numeric baseline remains unchanged, subject to scoped contract acceptance:

| Type | Mass | Thrust | Consumption coefficient | Required tank role |
|---|---:|---:|---|---|
| Existing basic mono | 100 | 1,000 (unchanged) | 1 | mono |
| Advanced mono | 100 | 5,000 | 3/5 | mono |
| Basic bipropellant | 100 | 1,000 | 1 per fuel and oxidizer | bipropellant + oxidizer |
| Advanced bipropellant | 100 | 5,000 | 3/5 per fuel and oxidizer | bipropellant + oxidizer |
| Nuclear nozzle | 100 | 3,500, capped by cores | 2/7 at full core support | working fluid |
| Nuclear core | 100 | at most 100,000 supported nozzle thrust | no independent engine | none |

The scale factor of 100 preserves the legacy tier ratios without weakening the
current basic rocket. Each matching tank has mass 50 and base physical capacity
1,000 mB. `thrustMultiplier`, `fuelCapacityMultiplier`, `nuclearCoreThrustRatio`
are server COMMON finite fixed-point multipliers 0.1..4.0, default 1.0, captured
on new assembly. Aggregates still reject capacity beyond 2,048,000 and overflow,
before world extraction; no increase of RocketLimits or flight limits is implied.

Mixed host mono/bipropellant/nuclear engines or wrong host tank roles reject
assembly with a distinct reason, not whichever engine was visited last. An
ADR-026-only rocket retains its old abstract mode and metrics.

The owner-selected D1-A compatibility boundary is explicit and order-independent:

| Components after existing explicit-definition-over-tag resolution | Allowed mode |
|---|---|
| Basic or advanced mono host components plus external ADR-026 engine/capacity components | Abstract-only mode; external/old basic metrics remain unchanged, new advanced host metrics use the accepted tier table; no Fluid branch is offered |
| External ADR-026 engine/capacity components only | Existing abstract-only mode, including integrated engine+tank components |
| Any host engine/tank selecting typed Fluid mode plus any external component with positive thrust or capacity | Refuse before extraction with `EXTERNAL_COMPONENT_ABSTRACT_ONLY`; do not assign its capacity/thrust to a typed bank |
| Typed host propulsion plus external structural/seat/guidance components with zero thrust and capacity | Allow, preserving existing component mass/seat/guidance semantics |
| Mixed host propulsion modes or a host tank for the wrong mode | Refuse before extraction; never derive mode from iteration order |

An external integrated component is classified by its entire explicit definition,
not split into a host tag engine and an external tank. A definition registered
for a new host block ID still takes ADR-026 precedence, so that block becomes an
external abstract-only component rather than being counted in two modes. Existing
abstract snapshots with such definitions migrate as abstract-only and do not gain
Fluid eligibility on reload. No public typed component API or callback change is
introduced. The inability to combine external engine/tank metrics with new typed
propulsion is a visible player/compatibility limit, included in the selected D1-A
choice and operator/API documentation, not an implicit external-provider upgrade.
Tests cover integrated roles, explicit/tag precedence, all scan-order permutations,
old external-only saves, basic host/external saves and every mixed typed case.

Host nuclear cores
support only contiguous core columns ending directly above a downward nuclear
nozzle; all traversal uses the already bounded snapshot, never a new world scan.
Effective nuclear thrust is min(nozzle total, connected core capacity); no core
cannot launch. Partial core support adjusts consumption and thrust together using
integer rational arithmetic; zero-effective or insufficient thrust refuses.

Fluid fuel catalogs (data directory `rocket_fluid_fuels/`, schema 1) name known
source Fluid IDs, roles and integer efficiency numerators/denominators 1..1,000,
with at most 64 entries, 8 KiB/entry, 128 ID characters, and no unknown fields.
One Fluid may have different roles, but only one definition per (role, Fluid).
Default mono is `rocket_fuel` efficiency 2/1; bipropellant and working fluid
`hydrogen` 1/1; oxidizer `oxygen` 1/1. The four old fluid-list configuration units
map to these explicit tables, with operator documentation and validation; a reload
publishes a complete immutable catalog, preserving the last valid generation on
failure. No namespace-free registry names or metadata suffixes are accepted.

The existing `TravelFuelFormula` remains the route requirement oracle Q; when
`gravityAffectsFuels=false`, only its gravity term is omitted. Thrust/route,
discovery, passenger and landing checks still apply. New mono/bipropellant/nuclear
Fluid consumption is `ceil(Q * tierCoefficient / capturedFluidEfficiency)`;
bipropellant computes fuel and oxidizer independently and needs both whole debits.
Rate coefficients of several same-mode engines are thrust-weighted with checked
integer arithmetic. The entire immutable consumption vector and catalog signature
are captured in the accepted flight plan. Fuel is debited once at the existing
transaction checkpoint, not again at each visual phase or on catalog reload.
`rocketsRequireFuel=false` waives debit only; it does not grant discovery, landing,
ownership, thrust or passenger authority, and does not normalize stored fuel.

**Existing abstract fuel:** migrate its capacity, amount and debit ledger exactly
to an `abstract` content branch. Never invent Fluid from already abstract units.
Existing ADR-027 item batches keep their captured units/remainder and can fill
mono/abstract tanks at 25 units/tick; they cannot fill oxidizer or nuclear tanks.
An empty mono bank can select abstract or Fluid content on first fill; while
nonempty it cannot mix/convert the branches. Abstract fuel is consumed by its old
route semantics and is not drainable as Fluid. Fluid content is preserved by ID,
tag and mB and remains unloadable only as its actual Fluid; no abstract remainder
is minted as free Fluid. This limitation must be visible in the menu/tooltips.

`canBeFueledByHand` controls direct bucket/canister-to-rocket fueling, not ordinary
menu/automation transfers into an installed loader. Containers use one-unit
split/exchange as ADR-064 requires, preserve container metadata and complete
round-trip, and refuse intact if remainder space is unavailable. Survival consumes
only actually accepted Fluid; creative follows a documented instabuild convention.

**Unselected alternative D1-B:** restore legacy scale/formula and retune existing rocket
metrics/route requirements. This affects all existing/custom rockets and requires
explicit migration of captured stats, capacity and plans, survival progression
revalidation and compatibility consumer tests. It cannot be achieved by quietly
changing fallback tags or casting old mB to current units.

D1-A is the selected product boundary; D1-B and numeric/efficiency retuning are
not selected. Scoped contract review and acceptance remain required before
numeric runtime implementation; this decision-only revision grants neither.

### 3.1 D1-disassembly: remaining propulsion resources (owner-confirmed D1-disassembly-A)

The actual existing `RocketDisassemblyService` deliberately disposes of a single
abstract fuel balance only after player consent; it does not materialize fuel in
world tank blocks. New typed contents cannot reuse only that primary-scalar check.

**Owner-confirmed D1-disassembly-A:** extend that policy
to the complete propulsion vector. Ordinary rocket cargo Fluid is always restored
through its single native cargo carrier, never included in the disposal vector.
An all-zero propulsion vector needs no disposal consent. Any nonzero abstract,
mono Fluid, bipropellant fuel, oxidizer or working-fluid bank requires explicit
consent showing every bank role, Fluid ID/tag summary and exact amount. In
particular fuel=0/oxidizer>0 still requires consent. The quote binds actor, entity
and logical rocket IDs, ownership, resource revision, latest snapshot ID/hash,
position/flight state and the complete native bank vector (Fluid tags included),
not just its display summary. The bounded vector has at most two simultaneously
nonempty banks and uses existing native Fluid limits; malformed/future roots
cannot produce a quote. Existing consent lifetime, one-use tokens and local
server authority remain. Fill/unload/catalog-driven descriptor change after a
quote invalidates it; the actor must review a new quote. No automation, ordinary
break, release hook, creative player or operator silently bypasses typed consent.

Successful disassembly restores the latest ordinary cargo exactly once and
materializes propulsion hardware empty; moving propulsion balances are deliberately
disposed only as part of the same successful materialization decision. Never zero
any bank before restoration succeeds. A failed or unknown restoration retains all
banks and the current resource/snapshot authority, blocks replay while recovery
is unresolved, and logs no completed disposal. The existing assembly/disassembly
journal owns restoration; D3 supplies the selected crash boundary, not a second
fuel source. A successful disposal receipt/log names the complete confirmed vector
and revision. Repeat confirmation/materialization cannot produce cargo again.
Old scalar saves migrate exactly and retain their old consent behavior; migrating
does not turn abstract fuel into a fluid-bearing world tank. Menus must disclose
that actual Fluid can be unloaded before choosing intentional disposal.

**Unselected alternative D1-disassembly-B:** materialize actual propulsion Fluid into one
canonical native tank carrier per bank, deterministically allocated by sorted
relative position, and remove it from moving authority only after successful
restoration. Existing plain host tank blocks do not supply this storage capability.
This alternative requires a separately frozen BE/adapter/root/capacity migration
and placement-failure protocol; it is not delivered by attaching a Fluid field to
the old scalar. Abstract units still need their existing explicit disposal policy.
D1-disassembly-A is selected; D1-disassembly-B is not an implementation fallback.
Independent resource/recovery review and scoped contract acceptance remain required
before implementing the selected disposal policy.

Required tests: fuel=0/oxidizer>0, tagged working Fluid, cargo Fluid plus propulsion,
all-zero versus partial vectors, fill/unload after quote, old scalar migration,
expired/replayed/wrong-actor consent, failed/unknown restoration and repeated
materialization under every admitted recovery cut.

## 4. Mutable rocket resources, loaders and monitoring (C17a)

### 4.1 One authority for materialization

Introduce bounded immutable `RocketResourceState` and a pure mutation/reconciliation
service, with thin Entity/BE adapters. The authoritative moving-resource state
and the structure materialization payload must never disagree.

Recommended design: snapshot schema 2 stores the *current* captured resource
payloads and propulsion descriptor; each cargo mutation creates a new whole
immutable snapshot, retaining stable snapshot ID, geometry and assembly identity,
recomputing its content hash. It never edits the original object's copied NBT.
Flight stores its own revision/hash binding; count/metrics that did not change are
revalidated. An assembled transaction's historic initial snapshot is not a second
live cargo source, and is not used to restore a post-commit rocket. Existing
assembly recovery must distinguish completed extraction from unresolved rollback
before enabling any mutation. Transfer/disassembly freezes the exact latest
snapshot; all checksums, journals and visual-cache keys use that version.

No generic IItemHandler/IFluidHandler callback over every captured BE is allowed.
Item loaders support explicit chest/barrel payloads with resolved contents only:
deferred LootTable containers must be realized safely in the stationary world
before assembly, or refused for live loading/unloading. C16 `pressurized_tank`
gets a host adapter for ordinary Fluid cargo; propulsion banks are separate and
are never drained as cargo by accident. Up to the existing 128 BE payloads, native
item/fluid limits, 262,144 bytes/payload and 1,048,576 aggregate still apply. A
mutation is preflighted before either side changes; complete payload round-trip,
unknown item/Fluid identity and future schema failure leave both sides intact.

### 4.1.1 Whole-snapshot work admission

The count ceiling alone is not a performance claim. Cargo, propulsion fill/unload
and bay package-consumption mutations share a server-wide fair preparation queue.
Keep the ceiling of 64 committed resource operations/tick, but permit at most one
whole-snapshot reconstruction in a server tick. A reconstruction also has fixed
hard ceilings of 16 MiB charged snapshot-byte work and 16,384 block-record visits
per tick. Byte work counts every payload copy, native encode/decode and hashing
pass, including old/proposed envelopes and intermediate validation; a visit is
each pass over a block record, not each distinct position. Cached original bytes
may be reused only if still immutable and validated; skipping a payload/hash
check to fit the budget is forbidden. Visual refresh retains its separate existing
limits and is not performed 64 times because several requests were queued.

The mutation preparer must reserve a conservative checked upper bound for all
its passes before allocating/copying. If its bound cannot fit one tick, it uses
a bounded resumable preparation cursor with fixed memory bounds and fair deferral,
charging actual visits/bytes on each tick. The rocket resource revision is locked
while a prepared mutation is pending; no transfer/disassembly/countdown or other
endpoint mutation commits against it. Do not debit a source or expose a destination
before preparation/revalidation completes. Publish the new immutable snapshot,
resource state, revision and hash together at commit; the prior snapshot/hash
remains authoritative throughout preparation or cancellation. Restart discards
pure preparation only; any escrow phase instead reconciles under D3. Queue-full
refusal changes no resource and does not start world/chunk scanning.

Before rate admission, pure and native worst-case microbenchmarks must exercise
2,048 blocks, 128 payloads, full 1 MiB aggregate NBT, complex legal Fluid/item
tags, migration-envelope overhead and saturated competing endpoints on the docs/17
4 GiB reference heap. Report bytes/visits, allocations/GC and p95/p99 tick cost,
including hashing and visual publication, against unchanged docs/17 MSPT budgets.
These are proposed fixed maxima, not measured safe throughput. If they fail,
lower admitted throughput or subdivide bounded preparation and retest; do not
raise heap/MSPT budgets or silently reject formerly legal snapshots to claim
success. Fairness tests include a maximum-size rocket amid small requests so it
cannot starve. Native evidence is still required; none is supplied by this draft.

Entity schema becomes 3, snapshot 2, flight 3, and transfer journal 3 only after
their exact fields/migration fixtures are independently reviewed. Payload schemas
have their own integer version. New source/destination records must contain the
entire latest resource state and frozen consumption vector within unchanged record
budgets; a large legal old snapshot that leaves insufficient new-envelope space
is repair-required, never truncated. Old flight/transfer schema 1/2 recovery runs
with old checksums first, then migrates a settled instance once. Already active old
flights finish with their captured old amount/cost semantics. A blocked root never
falls back to initial cargo. Repeat migration is a no-op; future/invalid roots
are retained without emitting resources.

### 4.2 Docked infrastructure

`linker` stores schema-1 selection of a device UUID, Level ResourceLocation and
position, at most 1 KiB, with no arbitrary NBT. It identifies only the first local
endpoint; the server checks the second clicked loaded object, actors, owners,
distance and role. Both actors must be connected, non-FakePlayer, non-spectator;
use is within 8 blocks. Targets are the same owner's rocket or owner/operator
managed station pad. A device's binding is a UUID of the logical rocket and the
station ID where applicable, never a client-supplied Entity integer ID.

Rocket endpoints operate only while their exact chunks are already FULL and the
rocket is ASSEMBLED/FUELED/LANDED, stationary, same Level, within 6 blocks and owner
compatible; transfer/countdown locks the resource mutation service. No chunk
search/load or nearest-rocket scan is triggered by a linker packet. An unbound
device does no scanning. Stale destroyed/moved targets pause with clear reasons.

Item loader/unloader local buffers have 4 slots, max 64/item and 4 KiB/item.
One validated stack mutation per device per 20 ticks, global 64 resource mutations
per server tick through a fair loaded-device queue AND the whole-snapshot admission
limits in section 4.1.1. Fluid loader/unloader and
`fluid_fuel_loader` hold one 16,000 mB Fluid (8 KiB max native representation) and
move at most 1,000 mB per device per 20 ticks, with the same global queue. Transfer
simulation has zero side effects; stale capability references and reentrant
operations refuse. New endpoint roots are schema 1, at most 32 KiB, depth 20,
2,048 nodes; exact integer/UUID/enum fields, item/Fluid envelope validation and
native unknown-data quarantine follow ADR-061/064. Endpoints retain inventories
on unload; their menu and native loot have one chosen resource carrier only.

These transfers span a BE chunk and a rocket Entity file. Vanilla save order does
not make a two-store transfer crash-atomic. Owner-selected D3-A requires an
escrow/receipt authority with demonstrable durable barriers and full S2 cut
evidence before admission. No added ADR-027/054 ordinary-container torn-save
residual is selected. In-tick conservation plus clean restart is not an
exactly-once crash claim. This is D3 below, not a test waiver.

### 4.3 Monitor

The monitoring station menu shows status, height relative to captured origin,
vertical velocity, both fuel banks/working Fluid, required costs and refusal.
Comparator output is bounded 0..15 (height-progress mode default; fuel mode is
selectable), computed server-side without coordinates of somebody else's rocket.
The menu has an explicit launch button and destination selection from the existing
bounded accessible/discovered catalog. One rising redstone edge submits a fresh
request ID for the persisted armed destination; it does not continuously retry
every powered tick. Mode/arming/destination require a local owner/operator and
are invalidated when authority, station orbit, catalog or binding changes.

Automatic launch is a new production entry point, not `requestAdminFlight`: it
validates persisted owner, device/rocket/station access, current route/discovery,
passengers, costs, landing and cooldown even when the owner is offline. It never
passes `operator=true` merely because the launch is unattended. Rejections retain
cargo/fuel; retry requires a new edge or explicit intent. Permission/replay logic
is shared with the ordinary flight path, not duplicated as a weaker launcher.

## 5. Station controls, distance and orientation (C17b)

### 5.1 Screens over checked services

`warp_controller` presents the existing quote/request/confirm/cancel/status actions.
It is not another warp energy store. A request reuses the same station authority,
warp core presence, discovery, countdown and energy rules as ADR-044; opening a
screen alone cannot spend energy or create a confirmation. Refactor the command
service's own-connected-player/core check into a shared context validator:
screen actor is within 8 blocks of the exact loaded controller in the same region,
and a loaded warp core is within the existing 5-block core reach of the controller.
No arbitrary ray result or command-source impersonation is accepted from clients.
This explicit physical access alternative amends ADR-046; commands remain usable.

Gravity block is a front-end to `StationGravityService`, retaining 0..100% range,
same local owner/operator authority, existing unchanged-value no-op and write
cooldown/server-wide budget. It is distinct from `gravity_field_controller`.
Menus for altitude/orientation use equally strict checked candidate writes and
compare an observed station revision/hash before committing.

### 5.2 D2: orbital-distance and orientation behavior

**Owner-confirmed D2-A (bounded analytic legacy-goal restoration):** Add logical
orbital distance and three analytic orientation phases/rates without moving blocks,
regions, pads, players or entities. Distance is 4..190 integer units, initial 4;
it changes parent-body apparent size, altitude label and explicit GEO diagnostics,
not `orbit_body`, route distance, region coordinates or warp energy cost. Each
orientation phase is fixed-point milli-degrees 0..359,999; signed rates -60..60
turns per 72,000 ticks. Changing a rate samples phase at the current server logical
time into the new epoch, then persists the new rate. Phase is computed with modular
integer arithmetic from `(phase, rate, epoch)`, not a world-sized tick loop. All
three axes are represented consistently in the sky; no legacy degrees/turns or
NORTH display bug is copied. Commands and screens can set distance/rates, stop all
rates, and choose a fixed phase when stopped. Server controls never accept elapsed
time or final interpolated phase from the client.

Add a versioned `orbital_controls` compound to station record schema 3 (distance,
3 phases, 3 rates, epoch, independent format 1) and registry schema 5, retaining
existing warp energy and every identity/team/region field. Old record 2 becomes
distance 4, zero rates, and phases chosen to reproduce its stored sun angle;
gravity/vacuum/solar angle retain their old meaning. Transition whitelist gains
only `ALTITUDE_ONLY` and `ORIENTATION_ONLY` and does NOT relax relocation, pad,
owner, region, shrink or vacuum checks. Each update uses the existing checked
stage/fsync/readback/replace path, 100-tick station cooldown and shared server-wide
write budget; failures retain prior state and unknown writes quarantine as today.
The 8,192-byte record and 4 MiB registry budgets remain fixed and tested at maximum
population; near-limit legacy data is refused intact if it cannot encode safely.

Elevator compatibility is deliberately conservative: an unchanged-value no-op
remains a no-op, but changed altitude/orientation is refused while an elevator
endpoint pair is bound or any ride is pending, until the pair is unbound through
its existing authority. This implements the confirmed endpoint-condition guard
without inventing new tolerances for bound stations. No new command silently breaks
a tether or changes a completed ride. Warp and deletion guards remain unchanged.
Existing stations and elevators keep their old behavior after migration. GEO and
rotation restrictions on newly binding an elevator would be a further material
ADR-059 amendment, not implied here.

Replace station sky S2C payload with payload schema 2 and a bounded optional context
(body, sky kind, distance, phases/rates/epoch and sampled server time); no UUID/name/owner is
sent for visitors. Strict maximum 256 bytes, integer ranges, trailing-byte rejection
and client world/session cache clearing. Celestial network protocol is 3 -> 4,
with its exact message table and C18 sky-kind integration in section 11.
Publish on region/context change, and while any rotation rate is nonzero send
a heartbeat once per 20 ticks/player through the existing fair context-pass
budget; never exceed that rate. Sampling/interpolation uses section 5.2.1's
clock only. The effective
solar-intensity resolver does not start attenuating energy by altitude/orientation:
that would be an additional gameplay decision, not legacy equivalence.

**Unselected historical alternative D2-B (fixed-setting redesign):** distance remains a logical checked
setting, orientation is a fixed three-axis sky pose with zero rate and no
continuous rotation. It has a smaller save/wire/UI surface and retains all region/
elevator guards, but removes a legacy continuous-rotation behavior. It needs an
explicit owner-approved redesign and player impact, and corresponding ledger
disposition. Replacing altitude with a body picker, or omitting it, is not either
option and would leave the altitude unit undelivered.

The owner chose D2-A on 2026-10-03; D2-B is recorded only as the rejected alternative,
not another runtime option. Physical station rotation is not proposed. Contract
review must still verify the checked transition, migration and wire details;
product confirmation does not weaken fixed-space-region geometry or transfer
authority and is not acceptance of the whole ADR.

### 5.2.1 Restart-safe orbital clock and sampling contract

Current station records/registry have no persistent clock; the sky service's
getTickCount pass schedule is transient and is not an epoch authority. Add a
private pure `StationOrbitalClock` owned by the logical-server station service,
not a public API, global static collection or independent world/SavedData clock.
Its persisted authority is an exact `orbital_clock` compound in the same
Overworld-owned `advancedrocketrycommunity_stations` root schema 5 as the station
records: `schema_version` int 1 and `logical_tick` long 0..Long.MAX_VALUE, no other
fields, <=128 bytes/depth 2/three nodes including the compound. Runtime current
tick starts exactly at that valid saved logical_tick. It advances by one per
logical-server END tick, once for the entire server regardless of Level/player
count; the private server-lifecycle service is cleared on stop. It does not read
getTickCount, Level game/day time, wall time, the satellite clock or client time
to set/recover an epoch. `/time set`, world-time rollback and a checked station
file ahead of level.dat therefore cannot put this clock behind its own epoch.
Offline time contributes zero ticks; restart never runs a catch-up loop.

Every in-memory operational record has `0 <= epoch <= currentLogicalTick`, and
every serialized candidate has `0 <= epoch <= candidate.orbital_clock.logical_tick`
for EVERY retained station, including zero-rate records. A changed orientation
setting (including STOP) samples the clock once, computes the old phases at that sample, and stages
new phases/rates/epoch plus that same sampled root clock in one immutable candidate.
All checked station updates carry one sampled nondecreasing root clock through
the existing stage/force/readback/replace/publish path. Candidate validation permits
only that clock advancement plus the named exact record/balance transition; it
does not relax geometry/team/other-station checks. Known commit publishes the
new record and its clock floor together; failures before replacement retain the
old accepted record/clock floor. A runtime counter may keep advancing after a
known clean refusal, but cannot publish an uncommitted rate or epoch.

Use ADR-041's existing outcome-unknown quarantine and ordinary-save acknowledged
authority policy without replacing it with an invented success. On unknown write,
disable orbital updates, stop emitting station context (send NONE) and suspend
the clock service until restart/reconciliation. Any ordinary reassertion uses
the acknowledged record set and its last checked-commit clock floor, never an
unpublished candidate or live uncertain epoch. Readback of an exactly matching
candidate may resolve a checked error as committed only by the existing path.
On restart, load clock and records from ONE validated root; either coherent old
or coherent new authority can resume, never records from one file and epoch time
from another. Missing/malformed/future clock or epoch > saved logical_tick blocks
the whole original root and preserves it verbatim; do not clamp epochs, take
max(epoch) as a guessed repair, reset clock to zero or decode records selectively.

For ordinary healthy saves, serialization captures currentLogicalTick once with
the exact acknowledged live record set and revalidates all epochs, rather than
serializing an older clock floor beside a newly checked epoch. While rotation is
active, mark the registry dirty at most once per 20 server ticks so its normal
autosave/clean-stop save checkpoints that sample. This adds no forced disk write
per tick or new periodic fsync outside StationWriteBudget. Preserve the existing
checked-write/ordinary-save ordering; stale serializer state must not overwrite
a newer checked record/clock pair. A successful checked update has a durable
co-committed clock; ordinary autosave is not promoted to a disk barrier. An abrupt
stop can rewind only uncheckpointed visual rotation to the last surviving coherent
root (potentially the normal autosave interval). Clean restart resumes at the
saved phases/time with zero offline advancement. No resource, geometry, solar or
flight outcome depends on this visual elapsed interval. This checkpoint limitation
is disclosed, not a claim of per-tick durable animation or physical movement.

Root 1/2/3 -> 4 existing migration/recovery runs first. Root 4 -> 5 initializes
logical_tick=0 and every migrated controls epoch=0, rate=0, distance=4, choosing
phases to reproduce the unchanged saved sun-angle; identifiers/team/geometry/
gravity/vacuum/warp balance remain exact. New stationary records likewise start
at epoch=0/rates=0 so ordinary creation does not create an epoch beyond a checked
clock floor. Already-current valid root 5 is a no-op; a current root 5 lacking
clock is malformed, not an undocumented earlier migration. Clock metadata and
controls stay inside unchanged 4 MiB root/8 KiB record limits; near-limit inputs
refuse migration/start intact. Owner-selected C18 D3 first-event source outboxes
must join this same root-5/journal-3 field/migration freeze; the product selection
neither accepts its shared technical contract nor bumps the roots independently.

For phase, let `delta = logicalTick - epoch` after validating ordering, then
`phaseNow = floorMod(phaseAtEpoch + (delta % 72000) * rate * 5, 360000)`.
The reduced product is at most 21,599,700 in magnitude; no `elapsed * rate`
overflow or floating-point world-age drift is allowed. The runtime clock uses
checked/saturating increment: at Long.MAX_VALUE it stays there and exposes
CLOCK_EXHAUSTED. It does not wrap or renormalize epochs. Rotation freezes at the
sampled phase; refuse new nonzero rates, permit an authorized checked STOP to
sample those exact phases and set rates zero, and preserve other existing station
operations/settlement. Do not silently reset saved clocks under operator authority.

Server context encodes the same validated epoch/current sample basis. The client
computes phases at packet sample, then extrapolates only 0..40 local render ticks
since that context packet using the declared rate; addition saturates at
Long.MAX_VALUE. After that horizon hold the last bounded phase until a fresh
heartbeat, not indefinite off-server rotation. Interpolation never writes server
phase/elapsed time. Wrong epoch ordering is a decoding error; blocked/quarantined
server roots emit NONE instead of malformed frames. Cache/time estimates reset
on NONE, Level/session/logout/resource-context change.

Required deterministic fixtures: root-4 initialization/no-op repeat; fresh server
start from nonzero epoch; positive/negative/zero rates and modular wrap; clean
save/restart and long offline pause; `/time set` backward/independent level.dat
behind a forced station update; old/new/mismatched checked clock/record pairs,
known refusal/outcome-unknown/readback and ordinary reassertion; delayed autosave
visual rollback; epoch-ahead/future/missing clock raw preservation; Long.MAX_VALUE
increment/STOP and arbitrarily long elapsed phase; packet sample/extrapolation
40-tick cap/cache reset. Pure model fixtures precede native writer/order/restart
evidence; none is a runtime or Gate claim in this proposal.

### 5.3 Localization and notice gaps

Close ADR-046 UI-02 gaps in C17b: stable translatable station/warp code keys in the
established `advancedrocketrycommunity_v150` language namespace, EN/zh_CN, tests
asserting code tokens rather than English prose. Start/commit/abort notice names
the affected station to authorized actors and visitors currently inside its region;
does not reveal hidden other regions' coordinates or member IDs. Countdown only
uses action bar. Offline member notices use a separately bounded station-notice
SavedData: one latest committed summary per station (max 4,096), not a full event
history, with up to 32 member acknowledgement revisions each. Localized argument
envelope max 512 bytes, root schema 1/depth 16/max 8 MiB. New stations get an empty
summary; a new outcome replaces that station's summary in place with an increasing
revision and no per-event growth. Removed members' acknowledgements are removed
and no notice is sent without checking current membership. At login, an
unacknowledged member sees the latest outcome once, then the server records
acknowledgement; rapid intermediate events are explicitly summarized, not promised
as a complete audit feed. Audit details remain in the existing server audit.
Byte-budget admission, schema/overflow/restart and removed-member privacy tests
are required. It is evidence scope, not a second version-status file.

## 6. Landing pads, light and force fields (C17b)

### 6.1 Landing pads and stationary geometry

An owned landing-pad BE identifies an additional named target inside a committed
station region; it does not rewrite `StationState.landingPad`, whose allocated
cell geometry is an invariant. Owner/operator may register/remove it locally;
members may visit according to existing StationAccessAction.VISIT but cannot
register a private pad for another owner. Maximum 8 pads per station, 32-character
name, server-generated UUID and local position; total 4,096 stations * 8 admitted
only within a separately bounded pad-index root (schema 1, max 2 MiB). Epochs and
physical BE identity prevent a removed/replaced pad from inheriting reservations.
Index lookup is per station, not a world scan; unloaded pad is unavailable.

Rocket flight selection/transfer journal gains an optional pad UUID bound to its
station and observed epoch; the client never supplies a landing coordinate.
Destination staging validates pad authority/loaded identity plus full footprint,
height, obstruction and current occupancy. Candidate count stays 8; no operation
raises the existing 16 landing-chunk or 2,048 block-inspection limits. If a legal
thin/wide rocket exceeds these landing budgets at a chosen pad it gets a stated
refusal, never an out-of-bounds probe. Pad claims have one transfer UUID and
release on commit/cancel/recovery; every recovery cut is tested. Default cell pad
is still available for legacy flights and remains unchanged. Pads do not force
chunks or take independent flight tickets.

`station_light` is an ordinary original-model light level 15 block, no owner state
or active tick. Its material/collision and loot are explicit in DataGen. Concrete
references vanilla concrete registry IDs in its pad recipe; no vanilla bitmap is
copied.

### 6.2 Owner-protected force field

`force_field_projector` is an owner-bound, six-direction redstone device, enabled
by a per-profile server switch; redstone without a claimed owner is inert. It
places only into empty AIR (not a fluid, replaceable plant, snow, crop or BE), a
straight maximum-32 line. It places/retracts at most 1 cell per 5 ticks; all
projectors share a fair maximum 64 inspected/mutated cells per server tick. An
unloaded source/target stops work; no tickets. No energy fee is proposed because
the legacy device has none; adding a fee changes balance and needs approval.

Use ADR-054 protection-chain order (FULL chunk, devices/zones/station/spawn/API
authorization) plus cancellable Forge block-placement events before adding a
field, and its break/protection path before player/world destructive operations.
The actual exported enum is `api.endgame.EndgameEffect`, presently `BLOCK_BREAK`,
`ENTITY_GRAVITY`, `TELEPORT`, and `ApiVersions.current()` is 1.8. Acceptance of this
field slice amends ADR-054 section 5.1 and ADR-021 by appending `FIELD_PROJECTION`
and changing API minor 1.8 -> 1.9. Preserve all old constants/order and event
constructor/accessor signatures; serialize explicit stable names/IDs, never Java
ordinals. For placement post `EndgameEffectEvent` with system ID
`advancedrocketrycommunity:force_field_projector`, the device owner, initiating
connected actor or empty for autonomous redstone, and the one-cell batch box.
Cancellation permits no placement, claim, resource or room-boundary change.
Do not treat `BLOCK_BREAK` as a placement permission. Retraction/destructive
removal posts `BLOCK_BREAK` plus the standard break event/protection path.
Owner offline does not become permission-2 FakePlayer authority.

The API artifact allowlist/docs and isolated compatibility consumer must include
the enum addition and minimum minor 1.9. Run an old 1.8-compiled protection
listener against the 1.9 host, exercise its default/unknown-effect handling and
verify cancellation/old effect behavior; an enum-exhaustive listener is not
assumed compatible merely because linkage succeeds. If a listener throws or
cannot handle the new effect, refuse the effect and diagnose it, not bypass the
listener. New consumers test all four kinds and the unchanged event payload;
coordinating any other accepted v1.8 API addition must not reuse 1.9 for a later
separately released capability. This proposal supplies no compiled API evidence.

Each field BE stores schema-1 projector UUID, projector Level/position, owner and
generation, max 1 KiB/depth 8/64 nodes, so intersecting projectors cannot delete
one another's fields. A projector records no more than 32 claimed offsets in
its 4 KiB schema-1 root. Retraction/removal only deletes a field with an exact
matching claim; other blocks are untouched. Removal cannot synchronously visit
unloaded chunks. A field validates its already-loaded source on lifecycle/queued
validation; source absence because its chunk is unloaded is not proof of removal.
Queued cleanup handles proved orphan claims after their chunks load. A field has
no item loot and cannot become duplicated stock.

The physical boundary is deliberately independent of source availability. Every
existing `force_field` BlockState is full-cube collidable and SEALED under ADR-024's
pure immutable BlockState boundary classification; the callback never reads a BE,
projector, chunk, mutable world or cleanup registry. Here "inert" means no new
projection/retraction work, not a disappearing or permeable barrier:

| Field/source state | Collision and air boundary while block exists | Placement/removal behavior |
|---|---|---|
| Matching active loaded projector and valid field claim | Full-cube, SEALED | May grow/retract through exact-claim protection checks within the shared budgets |
| Source chunk unavailable | Full-cube, SEALED; unchanged BlockState | No new growth; do not remove merely for unload, force a chunk or infer a tombstone |
| Source proved removed, or durable orphan tombstone | Full-cube, SEALED until the actual protected removal succeeds | Queue exact-claim cleanup, never remove a different generation/projector or an unrelated block |
| Field or source owned root quarantined/future/malformed | Full-cube, SEALED; unchanged BlockState | No new effects; retain raw root and refuse ordinary normalization/removal; operator repair needs separate explicit authorization |
| Field successfully physically removed | AIR, permeable/noncollidable | Synchronously revoke affected supplied-room authority before any subsequent oxygen/pressure debit or effect |

Source unload/removal does not itself revoke a room boundary because the physical
sealed block still exists. A supplied room therefore remains physically sealed
until a boundary block is removed; oxygen is still finite and charged by the
existing room service, never provided by the field. At successful field placement
or removal, the block-update invalidation must synchronously revoke the affected
room cache/authority in the same server operation, using ADR-024's bounded dirty
cell mechanism; no later tick or full-room rescan may supply old authority. If an
event cancels cleanup, the physical sealed field remains and its tombstone retries
fairly. Room invalidation is not called for a refused operation that changed
nothing. Source and field in different chunks, subsequent unload/reload, stale
generations, crossed projectors, protected/canceled removal and supplied-room
authority/debit ordering all require native tests.

Projector explosions/removal while fields are unloaded retain a bounded orphan
cleanup tombstone until all <=32 claims are observed removed; admitting a device
requires space in that persistent cleanup root (schema 1, max 4,096 tombstones,
1 MiB). If it is full, placement or ordinary removal refuses before any effect;
it never discards claims to manufacture cleanup success. Explosions and world
removal use the same preflighted cleanup admission and protection decision; if
admission cannot preserve claims, keep the projector block/BE and fields intact
rather than pretending a tombstone was written. Tests cover a full cleanup root,
explosion cancellation, unavailable claims and restart. Exact codec/recovery and
native event interception must pass independent review before implementation;
this explicit state table is not permission to weaken raw-root preservation.

## 7. Beacon and finder (C17c)

Use the v1.2 multiblock pattern lifecycle for a 5-by-3-by-3 beacon, four rotations,
the AR layout with project-owned casing substituted for LibVulpes structure.
It requires a full loaded structure and server owner; disabling/unforming marks it
inactive and never forces a chunk. No new battery/power cost is inferred from the
unapproved LibVulpes consumer base. If a power cost is desired it is a new numeric
choice and must be captured before the slice begins.

Schema-1 beacon index maps stable beacon UUID -> owner UUID, body ResourceLocation,
Level ResourceLocation, position, structure generation, active flag and name <=32.
At most 256 records/body, 32/owner, 4,096 global, 1 MiB root. Missing body/catalog
pauses resolution without deleting data. Only local owner/operator can rename or
activate. Index registration/reconciliation is lifecycle/event-driven; finder
lookup scans at most the viewer's 32 records, not world blocks or all bodies.

The beacon finder is a helmet module interoperating with the C18b suit workstation;
until that dependency is delivered it is obtainable but cannot be called a complete
equipment integration. It also works held in hand, an explicit additive convenience.
The jointly required C18b catalog amendment makes `beacon_finder` eligible for
either of the existing two HEAD accessory slots, alongside `anti_fog_visor`,
`earthbright_visor` and `hover_upgrade`; these are not two exclusive visor/hover
positions. There is at most one finder and no third HEAD slot or increase of the
eight-total-module limit. It competes for that capacity, and is eligible only for
the built-in suit helmet through the authoritative workstation install/remove
service, not arbitrary enchanted/external-provider armor. The actual finder item
and bounded owned root move once with unrelated tags/durability preserved;
pending transaction, invalid/future module root or no compatible free slot refuses
intact. Unsupported armor/module roots remain byte-preserved without finder use.
The immutable server equipment summary carries a `beaconFinderEnabled` boolean
derived only from a live correctly worn valid built-in helmet; it does not expose
item NBT, gas, unrelated modules or beacon data. Held mode is evaluated separately
from main/off hand. Equipped OR held enables one private marker stream, never two
sets of markers. Removing/replacing/breaking/unequipping the helmet recomputes the
summary and clears markers unless a valid finder is still held. Actual workstation
installation, two occupied slots, duplicate module, remove/full inventory, restart,
equipment swap and equipped-versus-held privacy tests must run against both C17
and the matching C18 contract. C17 cannot close helmet integration against the
unchanged C18 revision-1 visor/hover-only table; its amendment/review is a dependency.
It shows up to 8 nearest *owned* active markers on the current server-resolved body,
sorted distance then UUID; gives direction and distance, not other owners' data.
Unloaded beacon records may be shown as last-known/inactive but never represented
as live powered beacons without current generation validation. S2C marker snapshot
schema/protocol 1 has at most 8 entries and 1 KiB, integer positions/name bounds;
updates at most once/20 ticks and only to a equipped/holding authenticated player.
Body changes/logout/resource reload clear client caches. No C2S position query,
cross-body survey or automatic route discovery is added.

## 8. Satellite bay (C17c)

`rocket_satellite_bay` holds one ordinary package (schema 1 root, native item
4 KiB bound plus package identity decoder), captured through an explicit host
rocket adapter. Its menu/capability permits insertion/extraction while stationary;
in flight the slot is locked. Owner/package/rocket identity must agree. Multiple
bays remain legal within the 128 BE budget; at most 8 deployments/rocket and
32/server tick through a fair recovery queue, no unbounded scan of a large rocket.

Deploy at the existing server ascent-to-orbit checkpoint, using the source body's
orbit (or source station's captured orbit body), not the client destination picker.
It respects definition launch_targets, body orbitability/discovery, component
catalog requirements, research, owner quotas and registry byte budgets. A `data`
package keeps ADR-010/049 initial mission behavior; non-data launch idle as today.
Any rejection keeps the package and exposes the reason; a later repeated orbit
checkpoint retries through the same receipt identity, not a new satellite UUID.

Add a production server/owner-based launch service that shares all pure identity,
catalog/body/account/admission validation with `SatelliteManager`. It must not use
the release-test hook, invent a live ServerPlayer or call terminal-only validation
with a FakePlayer. No new public API for non-data kinds is implied.

The existing `SatelliteMissionRegistry.decommission` deletes live satellite
identity; it is NOT an enduring deployment receipt. D3-A therefore requires a
receipt independent of `SatelliteState` in the scoped durable coordinator, keyed
by `(logicalRocketId, bayRelativePosition, satelliteId)` and operation UUID. It
also stores owner, definition/kind/orbit, frozen package signature, source revision
and before/after snapshot hashes plus phase/durable acknowledgments. Reserve
receipt capacity before registration: at most 4,096 retained deployment receipts,
128 per owner, <=1 KiB each in a separately bounded 4 MiB schema-1 receipt section;
the coordinator's 64/server, 8/rocket pending-operation limits additionally apply.
Full or quarantined receipt storage refuses admission with package intact. Never
evict an unresolved receipt to deploy another package or reset it on decommission.

Reserve/force the intent receipt before registry registration; prove registration
durable, then force the registered outcome receipt before exposing/removing the
source package. A matching replay consumes the still-retained package idempotently
without registering again, even if the satellite has since been decommissioned.
Before that registered receipt is independently durable, decommission of this
satellite is interlocked with `DEPLOYMENT_PENDING`; after it is durable, existing
idle/no-receiver/no-mission decommission may remove the live satellite but cannot
remove the receipt. Registry pruning, operator recovery and transferred-source
restoration must consult this authority, not classify missing live identity as
never deployed. Receipt/registry signature conflicts preserve package and require
repair; missing definitions retain pending raw state. Apply shared registry and
source write fencing so another launch path cannot race registration/decommission.

Retire a receipt only after its complete consumed-package source revision is
independently durable, every registered transfer/disassembly/source generation
can no longer restore an earlier payload, and the coordinator/source durable
acknowledgment order is proven. A save-event observation alone is not such proof.
Until that proof exists, retain it and refuse on full capacity; an operator cannot
delete it to clear quota. Retirement frees only receipt capacity, never registers
a satellite, refunds a package or rewrites the live registry. Arbitrary manual
rollback of only one world file is outside a proven multi-store S2 guarantee;
whole-world backup restore/downgrade remains the documented operator boundary.
The exact disk authority/receipt retirement barriers remain the focused D3-A
recovery-contract gate. Unselected D3-B would retain matching in-memory/clean-restart
receipts but require a new explicit owner decision on its added torn-save residual;
it could not claim durable exactly-once, with or without a current satellite identity.

Complete bay phase/receipt ownership survives flight transfer and disassembly;
no historic assembly snapshot restores a consumed package. Fault cuts must cover
before/after intent force, registry registration, registered-receipt force, idle
non-data decommission, source save, source transfer/destination restore, source
acknowledgment and receipt retirement. Include register -> decommission -> source
save/crash/replay with both live and missing satellites. Legacy registry raw-root
preservation and live quotas remain unchanged. Exactly-once is conditional on the
accepted D3-A proof, never inferred from two clean restarts.

## 9. Solar generator and array (C17c)

Single `solar_generator`: 10,000 FE buffer, FE output-only on six sides, no item or
Fluid bank. `solar_panel` is its crafting/structure part. `solar_array` uses the
1-by-22-by-3 AR layout on the existing pattern lifecycle, a controller and two
explicit power-output interfaces plus 63 cells that accept panels, AIR and the
named allowed project casing. `solar_array_panel` is counted as one collector;
at least one collector required. The output interface ID is chosen from existing
C16 family if it supplies a genuine output facade, otherwise register dedicated
`power_output_plug`, controller-owned FE only, under this slice. A power INPUT
plug must not be silently repurposed as bidirectional.

Four rotations, no mirroring, at most 66 inspected cells per revalidation and
existing fair kernel structure budget; formation cache invalidates on relevant
block/tag/catalog changes and unload. No per-tick full scan. Array energy buffer
20,000 FE; shared push/pull cap 1,000 FE/tick across all faces/plugs, generation
scoped/reentrant-safe capabilities as combustion's verified policy. Array resource
state lives only in its controller, not each plug or panel. Controller roots schema
1, at most 4 KiB, FE/progress/status strictly bounded; buffer survives restarts,
but FE destruction on ordinary break is disclosed (not another energy-copy item).

COMMON `energy.solarGeneratorMultiplier` fixed integer 1..4, default 1, affects both
profiles; disable switch gives zero generation and output but retains storage.
Generation `floor(2 * collectorCount * multiplier * solarIntensity)` is server-
computed; single count=1, array count<=63, intensity<=16, so at most 8,064 FE/tick
before buffer room. Buffer overflow stops generation without wrapping or negative
FE. Client brightness never influences authority. Insignificant old 1.0005 fudge
is removed because the modern Earth intensity is explicit.

Planet surface eligibility: loaded catalog/body, sky-visible controller top,
server daytime; rain/thunder reduce the same server-local irradiance using an
explicit shared function documented/tested in the slice, never client `skyDarken`.
Inside an operational station: resolver's orbit-body intensity, no atmosphere or
world-global day check; sky visibility uses the collector exposure face toward
space as declared by pattern orientation. Missing orbit body is shown unavailable
and uses ADR-041's Space fallback, not a hidden fixed Earth value. No station
angle multiplier is introduced by this contract. During warp countdown/transition,
generation policy must be specified consistently with the modern stationary warp
model; recommended zero only during the active commit/relocation guard, not a
new 10-second penalty merely copied from an old warp dimension.

Menus expose actual generation, FE, sky/day/structure/body reason; scalar-only
server synchronization. Client art is original or individually approved imports;
all profiles have recipes/loot/tags/translations and client-only JEI presentation
where that adds obtainable machine content. Neither solar profile produces a
satellite nor replaces ADR-049's orbital solar transmitter.

## 10. D3: two-store crash durability decision (owner-confirmed D3-A)

**Owner-confirmed D3-A:** implement an explicit same-authority escrow/receipt
coordinator for BE <-> moving-rocket cargo and satellite bay deployment, with
documented durable barriers and S2 fault-cut verification before admitting those
automation paths. Bounded pending transfers (64/server, 8/rocket), unique UUID,
before/after signatures, frozen native payload and endpoint epochs; mutations
and materialization remain blocked until reconciliation. No claimant can withdraw
a tentative destination payload. Lost/unknown write outcomes retain resources
and require repair instead of falling back to old snapshots. This is a scoped
resource coordinator, not a clone of all LibVulpes or a generic distributed system.

Exact disk authority (entity file/chunk writer vs separately forced ledger),
save observer limitations and any torn-save residual need a focused implementation
design review; this proposal does not assert that observing `ChunkDataEvent.Save`
means its asynchronous write is durable. Admission must not begin before the
selected durability contract is frozen.

**Unselected alternative D3-B:** retain ordinary two-store in-tick atomicity/clean restart
with the existing explicitly accepted ADR-027/054 torn-save residual class. This
is smaller but exposes save interruption/backup rollback to cargo duplication or
loss; it requires the owner to accept the added loader/bay scope with public impact,
representative S2 cuts and operator diagnosis, and cannot be marketed as durable
exactly-once. Choosing B does not waive known reproducible non-crash duplication
or malformed-data loss. The owner selected D3-A, not this added residual or a
same-tick-only substitute. No implementation may silently choose B to shorten tests.

## 11. Network, provenance, progression and verification

### 11.1 Exact proposed protocol transitions and message table

The actual `src/test/resources/network-protocols.txt` baseline is celestial 3
and flight 8. The proposed integrated transition is `celestial_snapshot` 3 -> 4
and `rocket_flight` 8 -> 9, not two independent bumps for C17 and C18. Every
existing discriminator/direction remains fixed. The table after acceptance is:

| Channel (namespace `advancedrocketrycommunity`) | Protocol | Index | Message | Direction |
|---|---|---:|---|---|
| `endgame` | 1 unchanged | 0 | `EndgameDeviceView` | PLAY_TO_CLIENT |
| `life_support_status` | 1 unchanged | 0 | `LifeSupportStatusPacket` | PLAY_TO_CLIENT |
| `celestial_snapshot` | 4 (from 3) | 0 | `CelestialSnapshotPacket` | PLAY_TO_CLIENT |
| `celestial_snapshot` | 4 (from 3) | 1 | `StationSkyContextPacket` payload schema 2 | PLAY_TO_CLIENT |
| `rocket_flight` | 9 (from 8) | 0 | `RocketFlightIntentPacket` | PLAY_TO_SERVER |
| `rocket_flight` | 9 (from 8) | 1 | `RocketFlightPlanPacket` | PLAY_TO_CLIENT |
| `rocket_flight` | 9 (from 8) | 2 | `CelestialSnapshotPacket` | PLAY_TO_CLIENT |
| `rocket_visual` | 1 unchanged | 0 | `RocketVisualChunkPacket` | PLAY_TO_CLIENT |
| `satellite` | 1 unchanged | 0 | `SatelliteTerminalViewPacket` | PLAY_TO_CLIENT |
| `satellite` | 1 unchanged | 1 | `SurveyScanResultPacket` | PLAY_TO_CLIENT |
| new `classic_controls` | 1 | 0 | `ClassicControlIntentPacket` | PLAY_TO_SERVER |
| new `classic_controls` | 1 | 1 | `ClassicControlViewPacket` | PLAY_TO_CLIENT |
| new `classic_controls` | 1 | 2 | `BeaconMarkerSnapshotPacket` | PLAY_TO_CLIENT |

`ClassicControlIntentPacket` covers new C17 menu settings/actions only, <=512 bytes.
`ClassicControlViewPacket` covers the open C17 control menu, <=8 KiB, selected
entry/status/strict scalar values only, no full inventories/native tags. The
beacon snapshot is <=1 KiB/eight entries. No C18 equipment/motion intent or
equipment-summary packet is implicitly assigned to this channel: C18 must freeze
its own discriminator/direction/version surface before its network implementation.
The `beaconFinderEnabled` summary is server domain input to this existing private
marker message, not a second marker channel. Shared station-sky fields ARE frozen
here and must be referenced by the matching C18 presentation amendment.

Station sky payload schema 2 begins with explicit byte 2 and strict 0/1 context
presence. Absence ends the packet immediately. Presence has a strict explicit
sky-kind byte (`0=SPACE_FALLBACK`, `1=PLANETARY`, `2=SINGULARITY`), optional body
presence and canonical ASCII ResourceLocation <=128 characters, distance unsigned
short 4..190, three phase ints 0..359,999 in EAST/UP/NORTH order, three signed
rate bytes -60..60 in that order, epoch long and sampled-server-logical-time long,
both nonnegative with epoch <= sample. SPACE_FALLBACK may retain a missing known
orbit-body ID; PLANETARY/SINGULARITY require the current valid server catalog body.
The server chooses SINGULARITY only from the accepted ADR-057 singularity profile,
never from name/gravity magnitude or a client decision. NONE on logout/Level/
region change removes the old context. No station UUID, owner or member list is
sent. Bound remains 256 bytes with exact ranges/canonical encoding/trailing-byte
rejection and client interpolation only. This integrates C18's black-hole sky
selection once, without granting new body discovery or altitude/orientation solar
penalties. Old celestial protocol-3 clients reject the protocol-4 host rather
than misdecode the new context. Celestial snapshot message bytes otherwise retain
their existing schema/budgets on both channels.

Flight index 0 appends a strict optional-pad flag; if present, UUID and observed
nonnegative pad epoch (25 bytes maximum including flag), only with a station
TravelTarget. Preserve existing action IDs, request UUID, canonical Entity ID and
TravelTarget encoding. No client supplies pad coordinates, bank contents or cost.
Flight index 1 appends the selected optional pad and a bounded typed resource view
with the selected quote's consumption vector only: at most two simultaneous banks, explicit stable
bank role IDs (`0=ABSTRACT`, `1=MONO`, `2=BIPROPELLANT_FUEL`, `3=OXIDIZER`,
`4=WORKING_FLUID`), content branch (`0=EMPTY`, `1=ABSTRACT`, `2=FLUID`), strict
optional canonical Fluid ID <=128 ASCII characters, nonnegative int amount/
capacity/required debit <=2,048,000 and frozen descriptor signature (32 bytes);
role/branch legality is checked and native Fluid tags are never transmitted. Existing
primary `requiredFuel` remains the abstract/primary display value, not an authority
for skipping oxidizer. An unavailable/over-capacity route returns its validation
status, never a wrapped/truncated debit. Retain all 160 existing quotes and 32
accessible stations; changing selection updates the two-bank detail, not 160
copies of it. The selected pad adds <=25 bytes, detail count adds one byte, and
each bank detail adds <=180 bytes (two enum bytes, strict optional Fluid field
<=131, three ints and 32-byte signature, conservatively rounded). New frame bound
is the actual old maximum plus 386 bytes, still <=32 KiB; codecs preflight this
bound before copy/allocation. Tests use all old maximum-length IDs/48-character
station names simultaneously with maximum detail. No legacy list is truncated
or count increased to fit the combined envelope.
Version-8 clients reject version 9. Do not alter rocket_visual chunk/hash budgets
because a resource snapshot revision changes; its cache continues to distinguish
the latest content hash within the existing bounded scene protocol.

All channels keep exact-version negotiation. Update the protocol fixture once
with both station-sky consumers, and test wrong-direction/discriminator/version,
maximal frames, canonical option flags/enums/VarInts and trailing bytes. These
are proposed values/table, not registered runtime packets.

### 11.2 Authority, content and verification

C2S classic intents carry menu/container identity,
action enum and bounded target/token/value, <=512 bytes, never arbitrary NBT,
coordinates, Fluid amount, thrust or final quote. Every handler checks connected
sender, main thread, alive/non-spectator/non-FakePlayer, same Level, distance <=8,
exact FULL-loaded BE, matching open menu, owner/station permission, observed
generation/state/revision, action/value range and rate. One mutation/player/10
ticks; natural confirmation/deployment replay is additionally idempotent.

Recipe DataGen must deliver each obtainable ID from reachable C15/C16 inputs:
advanced engines use titanium aluminide/iridium and motor tiers; nuclear core uses
the existing iridium progression (no nonexistent uranium acquisition gate); monitor/
beacon/controls use C16 tracking/circuit/UI parts; solar uses silicon/copper/glass.
The first Earth-Moon rocket remains obtainable without Moon ores. Exact shaped/
machine grids are frozen and tested in each implementation task, never claimed as
legacy-equivalent without source evidence. C16d graph reports include Fluid/FE,
body/discovery/route and research dependencies and every explicit exemption.

Each task's original code/assets and intended upstream imports are entered in
provenance BEFORE entering runtime roots. Existing asset allowlist handling and
`REVIEW` author findings are respected; NEW replacements do not close legacy
asset review rows automatically. No bitmap extracted from Minecraft/Forge or
LibVulpes is copied. Models/sounds may only reference legal vanilla assets.
C18d owns full OBJ/sounds presentation refresh; C17 delivers complete usable
original/approved models, GUI and feedback, not missing-texture placeholders.

Minimum tests, each with actual source/command/exit/log/JAR identity:

- A0: tier/core/mixed-kind arithmetic and overflow, per-role full/partial/zero
  fluid bounds, integer efficiency/remainders, no-fuel/gravity switches, table
  reload and frozen-plan signatures; all legacy-to-new codecs, future/depth/byte/
  unknown-native identity refusal; snapshot hash/materialization invariants;
  complete-vector disassembly consent/cargo distinction; external integrated
  component precedence and mode-mix order permutations; byte/visit queue fairness
  and old-hash-until-commit, worst-case pure/native reconstruction benchmarks;
  orientation modular arithmetic for very long elapsed times; station transition
  whitelist and near-budget migration; packet trailing/size/version bounds.
- A1: every machine/profile happy/failure/save-restore; item and Fluid conservation
  across loader simulation/full/incompatible/reentrant/stale capabilities; monitor
  authority/offline owner/redstone edges/replay; both fuel/oxidizer debit or neither;
  no forced chunks; full station actor matrix; cancelled writes/effects; stale pads,
  occupancy and non-square rocket footprints; crossed projectors/orphans/removal/
  atmosphere invalidation, source/field cross-chunk sealing and cancellation;
  actual finder HEAD workstation install/remove/restart/equipped/held capacity
  and body-private beacon cache; bay receipt fullness, decommission interlocks,
  register/decommission/consume acknowledgment conflicts and replay;
  solar night/occlusion/rain/station/reload/unformed and shared FE output limits.
- S1: packaged sidedness, representative old v1.7 rocket/fuel-loader/station/
  satellite world upgrade on a copy, two clean restarts, continue cargo/flights/
  controls/deployment afterward, raw readback of all resources and source backup
  unchanged. Existing unsupported-root preservation and chunk-save behavior tested
  through native serializers, not only standalone codec methods.
- S2: every admitted cargo/deployment escrow/register/consume/materialize cut,
  two competing endpoints, unresolved snapshot/transfer migration and failure/
  unknown write handling; complete propulsion-vector disposal and bay register ->
  decommission -> source-save/replay/transfer/receipt-retirement cuts.
  Enumerate any accepted D3 residual separately.
- Full `clean build`, `test`, `runData`, deterministic output comparison,
  `runGameTestServer`, strict provenance/resource/client-import/ledger validators,
  recipe graph; independent actual-diff review and critical regressions.
- V1: actual GPU sky phases/altitude, panels/light/field models, all menus/GUI
  scales/EN+zh and resource reload. V2: two real clients see authority rejections,
  monitor/warp notices, phase/context agreement and pad reservations. No simulated
  players or software rendering are marked V1/V2. Required G0-G9 remain open until
  version-wide evidence and human approval exist; ADR-018 campaign schedule remains.

Suggested independently reviewable implementation leaves: C17a-01 pure typed
propulsion/plans+migrations; C17a-02 Fluid fueling; C17a-03 mutable snapshot and
loader recovery; C17a-04 linker/monitor; C17b-01 checked orbital controls; C17b-02
warp screens/localization/notices; C17b-03 pads; C17b-04 light/field; C17c-01 beacon/
finder; C17c-02 bay; C17c-03 solar. No leaf closes another leaf by shared naming.

## 12. Exact primary ledger coverage

The `modern ID / contract` column lists proposed delivery, not delivered content.

| Ledger unit | Slice | Proposed modern ID / contract |
|---|---|---|
| `block:advbipropellantRocketmotor` | C17a | `advanced_bipropellant_rocket_motor` |
| `block:advRocketmotor` | C17a | `advanced_rocket_motor` |
| `block:bipropellantfueltank` | C17a | `bipropellant_fuel_tank` |
| `block:bipropellantrocketmotor` | C17a | `bipropellant_rocket_motor` |
| `block:monitoringStation` | C17a | `rocket_monitoring_station` |
| `block:nuclearcore` | C17a | `nuclear_core` |
| `block:nuclearfueltank` | C17a | `nuclear_fuel_tank` |
| `block:nuclearrocketmotor` | C17a | `nuclear_rocket_motor` |
| `block:oxidizerfueltank` | C17a | `oxidizer_fuel_tank` |
| `block_variant:loader/2` | C17a | `rocket_item_unloader` |
| `block_variant:loader/3` | C17a | `rocket_item_loader` |
| `block_variant:loader/4` | C17a | `rocket_fluid_unloader` |
| `block_variant:loader/5` | C17a | `rocket_fluid_loader` |
| `config:ROCKET.canBeFueledByHand` | C17a | hand fueling switch, section 3 |
| `config:ROCKET.fuelCapacityMultiplier` | C17a | captured capacity multiplier, section 3 |
| `config:ROCKET.gravityAffectsFuels` | C17a | Q gravity-term switch, section 3 |
| `config:ROCKET.nuclearCoreThrustRatio` | C17a | captured core capacity multiplier, section 3 |
| `config:ROCKET.rocketBipropellants` | C17a | role-specific Fluid fuel table |
| `config:ROCKET.rocketFuels` | C17a | role-specific Fluid fuel table |
| `config:ROCKET.rocketNuclearWorkingFluids` | C17a | role-specific Fluid fuel table |
| `config:ROCKET.rocketOxidizers` | C17a | role-specific Fluid fuel table |
| `config:ROCKET.rocketsRequireFuel` | C17a | debit-only fuel requirement switch |
| `config:ROCKET.thrustMultiplier` | C17a | captured thrust multiplier, section 3 |
| `libvulpes:java/itemLinker` | C17a | `linker`, original implementation |
| `block:altitudeController` | C17b | `station_altitude_controller`, distance not body |
| `block:circleLight` | C17b | `station_light` |
| `block:concrete` | C17b | vanilla concrete recipe references |
| `block:forceField` | C17b | technical `force_field` |
| `block:forceFieldProjector` | C17b | `force_field_projector` |
| `block:gravityController` | C17b | `station_gravity_controller` |
| `block:landingPad` | C17b | `landing_pad`, additional indexed target |
| `block:orientationController` | C17b | `station_orientation_controller`, D2 |
| `block:warpMonitor` | C17b | `warp_controller` + ADR-046 gaps |
| `block:beacon` | C17c | `planetary_beacon` |
| `block:solararray` | C17c | `solar_array` |
| `block:solararraypanel` | C17c | `solar_array_panel` |
| `block:solarGenerator` | C17c | `solar_generator` |
| `block:solarPanel` | C17c | `solar_panel` |
| `block_variant:loader/1` | C17c | `rocket_satellite_bay` |
| `config:ENERGY.solarGeneratorMultiplier` | C17c | common multiplier for both profiles |
| `item:beaconFinder` | C17c | `beacon_finder`, C18b helmet integration |

## 13. Approval and rollback boundaries

The independent reviewer checks this proposal against actual working-tree APIs,
accepted ADRs, pinned legacy facts, all 41 units and migrations. No conclusion or
fixed verdict is prescribed. The owner-selected D1-A, D1-disassembly-A and D3-A
product boundaries still require independent review and acceptance of their
schema/recovery contracts, including D3-A forced-stop recovery verification
before admitting its interactions. C17b orbital
implementation starts only after the owner-confirmed D2-A's checked-transition
and wire contract passes independent review and is accepted. Content cannot be silently
moved to DEFERRED/REJECTED; changing an ADR-062 disposition requires that ADR's
owner-approved revision and player impact. Other non-dependent pure test/design
work may continue.

Use per-profile disable switches for rollback without deleting IDs or resource
roots. A released typed snapshot, station record or resource coordinator cannot
be safely removed by restoring only the old JAR. Back up the whole world before
upgrade, drain pending resource movements and use the documented old-world backup
for downgrade. Candidate rollback and migration fixtures preserve originals.

## Source appendix (local factual references)

- Modern: `rocket/forge/RocketForgeMetrics.java`, `rocket/stats/RocketBlockMetrics.java`,
  `rocket/flight/RocketFuelState.java`, `rocket/model/RocketStructureSnapshot.java`,
  `rocket/entity/RocketEntity.java`, `rocket/forge/VanillaContainerRocketAdapter.java`,
  `rocket/fuel/FuelLoaderBlockEntity.java`, `rocket/server/RocketFlightService.java`;
  `station/model/StationState.java`, `StationLimits.java`,
  `station/service/StationGravityService.java`, `station/warp/StationWarpService.java`,
  `station/orbit/StationOrbitEnvironmentResolver.java`, `StationSkyContextService.java`,
  `celestial/network/StationSkyContextPacket.java`,
  `satellite/service/SatelliteManager.java`, `SatelliteKindLifecycle.java`.
- Legacy: `block/Block*RocketMotor.java`, `BlockNuclearCore.java`, `BlockFuelTank.java`,
  `api/ARConfiguration.java`, `tile/TileRocketAssemblingMachine.java`,
  `tile/infrastructure/TileRocketMonitoringStation.java`, `tile/TileSolarPanel.java`,
  `tile/multiblock/energy/TileSolarArray.java`, `tile/multiblock/TileBeacon.java`,
  `tile/station/TileStationAltitudeController.java`, `TileStationOrientationController.java`,
  `tile/TileForceFieldProjector.java`, `tile/hatch/TileSatelliteHatch.java`,
  `stations/SpaceStationObject.java`, `client/render/planet/RenderSpaceSky.java`,
  `client/ClientProxy.java`, `util/PlanetaryTravelHelper.java`,
  `tile/multiblock/TileSpaceElevator.java`, `item/ItemBeaconFinder.java`.
- Governance: mandatory AGENTS documents, UPSTREAM/NOTICE/source audit/provenance,
  accepted ADR-026/027/041/046/049/054/058/061/062/064, version coverage table and
  current ledger. Unapproved sibling LibVulpes and the protected development-docs
  directory were not inspected.

## Acceptance record

The [acceptance receipt](../work/v1.8.0-c17-contract/ACCEPTANCE.md) preserves
the immutable proposals, independent reviews, selected owner branches and
status-only integration diff. Section 13's exact schema/writer/recovery gates,
including forced-stop verification before D3-A interactions open, remain required.
