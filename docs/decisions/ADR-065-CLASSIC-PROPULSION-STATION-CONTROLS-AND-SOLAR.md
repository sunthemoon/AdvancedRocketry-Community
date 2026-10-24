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

### W22 implementation note: station notices and their data preservation

This note documents the section 5.3 implementation (W22 and W22F, 2026-10-13). It does not
change a decision or accept a risk. The risk is R-029. Nothing in it refuses a save or throws
from serialization.

**Who is told.** A start, commit or abort notice names the affected station. It goes to:
- the online owner and members;
- the acting player, while they still hold VISIT access, so a de-opped former actor is told
  nothing more;
- visitors currently inside the region.

A visitor without VISIT access gets the brief form: the station, the code and the target,
without the warp cost or the energy balance. These stay limited to VISIT holders, as
`/arce station warp status` already does. The countdown uses only the action bar.

**Departures from the section 5.3 text.**
- One station keeps up to 33 acknowledgements: the owner's and 32 members'. The byte and node
  budgets include the owner.
- The code keys are in the v1.8 language namespace (`advancedrocketrycommunity_v180`), not
  `advancedrocketrycommunity_v150`. `runData` now writes only `src/generated/v1.8`.

**Persisted identifiers.** Stored outcomes are validated on decode against these:
- the `StationManagementCode` names and their `success` flags;
- the notice kind keys;
- `StationLimits.MAX_NAME_LENGTH`;
- the warp cost classes.

They are persisted identifiers (AGENTS.md section 8). Renaming or removing one, or changing a
`success` flag, needs a notice schema bump with a migration. Otherwise every server whose store
holds that code refuses the whole notice root (below).

**Durability.** The notice store is saved by ordinary autosave, while the warp itself is a
checked, forced write. A crash after a committed warp and before the next autosave leaves the
previous summary, or an empty one. An offline member is then never told of that warp; the
`ARCE_STATION_WARP` audit line remains in the server log. The store is a summary, not an audit
feed, as section 5.3 says. If notice delivery throws inside the warp commit, the exception is
caught after the checked write. One WARN is logged (`ARCE_STATION_NOTICE_FAILED`), and both the
outcome and the audit line are kept.

**Notice root.**
- **Scope.** An `advancedrocketrycommunity_station_notices` root that reads as NBT but does not
  decode is kept by reference and written back unchanged. "Does not decode" means unknown,
  future, malformed, or over a section 5.3 bound. The affected unit is the whole server's
  offline member-notice store:
  - no outcome is recorded;
  - no login notice is shown;
  - no acknowledgement is recorded.

  Live notices continue as described above, and no stored summary is deleted.
- **Duration.** Until the file is repaired. A restart with the same file refuses it again.
- **Log volume.** One WARN (`ARCE_STATION_NOTICES_REFUSED`) per load.
- **Recovery.** Back up, then repair or remove `data/advancedrocketrycommunity_station_notices.dat`
  offline, and restart. Removal loses only the outcomes that no owner or member has seen yet.
- **Unreadable file.** A `.dat` that cannot be read as NBT at all makes vanilla
  `DimensionDataStorage` log an ERROR and start an empty store, as for the beacon index (R-028).
- **Revision exhaustion.** A summary at revision `Long.MAX_VALUE` keeps its last outcome and
  refuses later ones, with one WARN (`ARCE_STATION_NOTICE_NOT_KEPT`) per refusal. It cannot be
  reached in practice.

**Follow-ups from the review (closed 2026-10-15):**
- F8, a new member is shown the latest outcome from before they joined: Root accepted it as
  section 5.3's rule. An unacknowledged member sees the latest outcome once, and a new member is
  unacknowledged. The summary names no member.
- F9, default station names contain the creator's player name: W55 corrected the records
  (`deed8855`).
- F10, two tests could be tightened: W55 tightened them (`d2a846f1`).

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

### W04 implementation note: oversized native save protection

This note documents protection mechanics, not a changed decision or accepted
risk. W04 applies the existing `GuardedChunkSaves` mechanism to oversized field
and projector roots (risk R-022). The affected unit is the whole terrain chunk;
the Level capability retains denial across unload, and its existing 256-coordinate
saturation can refuse further terrain saves in that Level. The live raw Tag is
retained and ordinary removal is refused. The existing guard throws at the chunk
save event so ChunkMap reports refusal and restores the dirty flag; exceptions
from BE serialization alone are not used to control saves. This does not prevent
unload or promise durability between the cleanup file and terrain writer. Guard
logs have no retry-volume bound. Denial lasts through the Level lifecycle, with no
production online reset. Back up, stop and repair the native root offline before
restarting; restarting alone is not repair.

An oversized cleanup SavedData root is retained by reference and remains inert.
Its file save is refused without throwing, leaving the existing `.dat` file and
dirty flag intact; one diagnostic per loaded root reports that refusal. This
affects only that cleanup file, not other SavedData files. Recovery requires the
same backup/offline repair procedure. Native stop/restart and human risk acceptance
remain separate evidence and are not granted by this implementation note.

### W59 implementation note: empty reservations of unowned projectors

This note documents reservation mechanics, not a changed decision. Since W04F every readable
projector in a loaded Level reserves one cleanup-root entry, unowned ones included. An unowned
projector's entry uses the all-zero owner UUID as an internal sentinel and has zero claims; it grants
no ownership or effect, and binding by a connected player replaces it in place without another slot.
When NBT replaces a live projector's identity (an operator's item `BlockEntityTag`, a structure
template placed into a live Level, `/setblock` or `/data`), the projector releases its previous entry
only if that entry is exactly its own non-orphan, zero-claim sentinel entry, and then reserves the
loaded identity under the ordinary admission rules. A refused root, a full root without that entry,
and a loaded identity whose key holds another entry or a tombstone refuse as before. A bound, claimed
or foreign entry is never released. Chunk loads from disk are unchanged. No save refusal is added
(W59; W04F review N5).

### W13 implementation note: landing-pad save protection

This implementation note does not change a decision or accept risk R-023.
Unsupported bounded pad-index and block-entity roots remain verbatim and disable
pad operations. An oversized index retains its original Tag reference, refuses
only its independent `advancedrocketrycommunity_landing_pads.dat` write without
throwing, keeps the dirty flag, and emits at most one warning per loaded instance.
It never replaces the existing file with an empty index.

An oversized `landing_pad` block-entity root retains its original reference and
uses the existing `GuardedChunkSaves` terrain-chunk denial. This survives unload
for the Level lifetime; the existing 256-coordinate saturation can affect all
terrain saves in that Level. No new exception-based BE save control is added.
The existing EventBus/ChunkMap save-retry diagnostics have no cumulative log
limit. Unknown bounded roots do not deny unrelated terrain saves. Recovery is
to stop gameplay, back up the whole world, inspect and repair the pad index and
corresponding BE identities offline on a copy, then verify a clean save and two
restarts. Restart alone cannot repair either root; resource/flight journals must
not be reset to clear a pad. Native forced-stop and operator acceptance remain
separate verification, not supplied by an in-memory round trip.

**Known limitation (W13F review N1, Root decision 2026-10-15).** The station-wide occupancy
search visits only loaded, accessible entity sections and never loads a chunk. A rocket parked
where the entities are unloaded or still loading is not found, so an arrival can be placed over
it. v1.8 records this limitation. Two fixes are reconsidered with W11b3's preparation contract:
requiring loaded entities in every arrival chunk (at most 16, without loading), and a bounded
index of parked rocket regions (new persisted state, which needs a design note and a review).
A refusal now logs one INFO line, `ARCE_PAD_OCCUPANCY_REFUSED`, naming the blocking rocket
(W13F review N3, W55).

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

### W18 implementation note: beacon data preservation

This note documents protection mechanics, not a changed decision or accepted
risk (risk R-028). Neither mechanism refuses a save or throws from serialization.

- **Index root.** A `advancedrocketrycommunity_planetary_beacons` SavedData root
  that reads as NBT but does not decode (unknown, future, malformed or over a
  section 7 bound) is kept by reference and written back unchanged. The affected
  unit is the whole server's beacon index, across every Level: no beacon
  registers, updates or removes its record, and every finder receives an empty
  snapshot. None of its records is deleted. It lasts until the file is repaired,
  because a restart with the same file refuses it again; one WARN is logged per
  load. Recovery is an offline repair of the `.dat` file from a backup and a
  restart. Loaded beacons then re-register on their next validation. A beacon
  removed while the index was unavailable leaves its old record, which its owner
  sees as a last-known marker. Once the index works again, the owner's finder
  removes that record the next time its position is loaded and holds no such
  beacon (the W18F rule below).
- **Unreadable index file.** The preservation above covers only a root that
  reads as NBT. When the `.dat` file itself cannot be read, for example a
  truncated gzip, vanilla `DimensionDataStorage` logs an ERROR and starts an
  empty index, and the first registration overwrites the file. This is a
  project-wide vanilla residual: no ARCE SavedData guards against it. The loss is
  soft, because each beacon re-registers once its chunk is loaded.
- **Block-entity root.** An `arce_planetary_beacon` root that does not decode is
  kept and written back unchanged. The affected unit is that one beacon: it stays
  inactive and unregistered and logs nothing, so an operator finds such a beacon
  only by using it, which replies with the invalid-data status. The beacon holds
  no item, fluid or energy. Ordinary removal is therefore allowed and drops the
  plain block; it discards only the unreadable identity and name. A record that
  the beacon wrote before its root became unreadable stays in the index with its
  last active flag, so its owner can see a last-known marker. While the beacon
  stands, nothing proves that record stale. Once the beacon is removed, the
  W18F rule below removes the record. Recovery is an offline repair of the chunk
  data or replacing the beacon. Since W18F, item NBT reaches this root only
  from a creative operator (`onlyOpCanSetNbt`).
- **Stale records (W18F).** A record is removed only by its own beacon's
  removal, or when its owner's finder snapshot finds its own position loaded
  and holding no beacon or another readable beacon. The snapshot checks at most
  the viewer's 32 records, reads only loaded chunks, never loads or tickets a
  chunk, and logs one INFO line per removal. A record whose position stays
  unloaded, or whose owner never uses a finder, is kept.

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

### W27b implementation note: the bay block (deployment disabled)

This note documents mechanics, not a changed decision; the design is `records/W27A-DESIGN.md` section 6,
and the risk is R-038. W27b (merge `c2aa5319`) adds `advancedrocketrycommunity:rocket_satellite_bay`: a
one-slot block whose menu and item capability share one handler, a schema-1 root (owner, then at most
one of `package` or `consumed_op`; the package bounded at 4,096 bytes, depth 16, 256 nodes, `Count` 1),
the package identity decoder, and the host rocket adapter `satellite_bay_v1` inside the existing
128-payload budget. Deployment, receipts and admission are absent until W27c1 to W27e.

A valid bay drops its package exactly once and an empty bay on a normal break or explosion; there is
no `BlockEntityTag` carrier and no owner restoration on placement. A raw, unknown, future or quarantined
root, or an unavailable package, is guarded: it refuses player break, leaves the explosion list, drops
nothing, and is saved verbatim. A bounded native preflight decides the save (review finding W27B-R1):
a root the terrain writer and reader keep losslessly, or one too large to examine within the work
budget, is saved verbatim without any denial; only an observed incompatibility (over the native depth,
a string too long for modified UTF-8, a list of mixed or end tags, a non-canonical NaN) uses the
existing `GuardedChunkSaves` denial. Each load into a guarded state and each refused live replacement
writes one audit WARN. The independent review is `D:/GitHub/ARCE-Task-Evidence/root-queue/W27B-review/REVIEW.md`
(SHA-256 `8ea41a4c29a117f9095acbad62e3e743dbadf31b4b99341e645f6b9d43da1e53`).

### W27c1 implementation note: bay deployment receipts and the shared admission authority (deployment disabled)

This note documents mechanics, not a changed decision; the design is `records/W27A-DESIGN.md` sections 2
and 5 and the W27c1 row of section 9, and the risk is R-039. Receipts live in their own schema-1 managed
root, `BAY_RECEIPTS` (`data/advancedrocketrycommunity_bay_receipts.dat`, epoch `v1.8.0-bay-receipts`): a
4 MiB section of at most 4,096 receipts, 128 per owner and 1 KiB each. An existing file is loaded with the
load-only lookup and indexed at `ServerStartingEvent`, before the first tick; a future or invalid root
refuses whole-world startup and keeps its bytes, as RESOURCE_ESCROW does. The checked writer keeps the
W11b1 behaviour (one captured generation and one successor, exact acknowledgements, bounded readback),
with encoding, digest, staging, force, readback and replacement off the server thread, and its generation
digest is the shared `CanonicalNbtDigest`. A definite failure before replacement cancels the attempt; only an
unknown outcome is retried as the exact candidate every 200 ticks; definite and unknown failures both count
towards the three that make the store unavailable. Root's R37 decisions on the independent review (W27C1F,
2026-10-20) add: an unavailable store with no write in flight writes one generation with no new change at
most every 200 ticks, and its exact commit makes the store available again; each op and each satellite has
at most one change in flight, and a second one answers BUSY until the first is acknowledged, so a definite
failure restores every change exactly; an `Error` on the IO thread of this writer or of the escrow ledger
writer posts an unknown outcome, so neither a tick nor a stop drain waits for ever.

Root records the contract delta from W11B3-DESIGN section 2 (2026-10-20): the one
`EscrowAdmissionAuthority` belongs to `EscrowRuntime`, not to a coordinator, and each store attaches to the
delegate that the runtime registered (B3-U, W93). W27c1 registers the BAY consumer as NOT_LOADED together
with PROPULSION when the runtime creates the authority (at `ServerStartingEvent`, or earlier when a ledger
binds while the Levels prepare), so the authority never exists without BAY, and loads it in the
`ServerStartingEvent` handler; until both consumers are loaded, `tryReserve` answers UNAVAILABLE for both and
`tryRetain` answers UNAVAILABLE. W27c1 also adds the non-charging queries `checkReserve` and `checkRetain`,
with the same results; they reserve nothing, no consumer may act on a check alone, and the bay store refuses
a charge that answers differently from its check. The frozen `tryReserve` and `tryRetain` results and the no-release rule of W27A
section 2 are unchanged: the pending union (64 per server, 8 per rocket) counts unique durable, queued,
captured and unknown operations of both stores, including REPAIR and released receipts; the bay alone
also holds the retained union (4,096, 128 per owner, 1 KiB each), which includes REJECTED receipts.

A bay refuses a package while a receipt blocks it (moved from W27b). The audited operator command
`/arce bay receipt retire-rejected <satellite>` is the only online receipt deletion (W27A section 5, N5).
It writes an `outcome=REQUESTED` audit line when the removal is queued and a second line with the final
outcome (R37), so an outcome that never resolves still leaves the operator's action audited.
Nothing in production creates a receipt: the deployment, the registry sequence, the launch and
decommission fences and the transfer hook are W27c2 to W27d, and admission stays disabled until W27e.

### W27c3 implementation note: the rocket transaction journal's checked policy (W123, merged `b9e4e755`)

This note records mechanics; it changes no decision and accepts no risk. The risk is R-040. W123-review
(APPROVE-WITH-CHANGES, SHA-256 `93e025ae…daef`) asked Root to record the protection below before merge.

- **Checked writes.** `ROCKET_TRANSACTIONS` adopts W11b2's checked policy and exact acknowledgement (mutation
  sequence, NC1 root digest, transaction-to-record digests) on a checked-write core now shared with the transfer
  journal, whose per-attempt results are unchanged. Writes stay synchronous at the journal's existing save cadence.
  A definite failure keeps the old disk proof and dirty RAM; an ambiguous replacement, readback or directory force
  is UNKNOWN until a successful checked write. The acknowledgement is session-local; loading never acknowledges.
- **B14 for W27d's D0.** A bounded absence query (at most 256 records) refuses a remaining assembly or disassembly
  record for the rocket as `ASSEMBLY_JOURNAL_PENDING`, even after a successful write of it; with no record, a stale
  acknowledgement asks for one checked write and a failed or unknown one answers `ASSEMBLY_JOURNAL_UNPROVED`.
- **An unreadable file.** An existing journal file that cannot be read no longer becomes vanilla's empty fallback
  root: the journal is not operational for the session, assembly and disassembly are refused, every position counts
  as claimed, and the file is never overwritten. Recovery is offline whole-world repair, then a restart.
- No saved format changed; maximum-size synchronous write cost is not yet measured. This is a development
  implementation note, not owner acceptance or a Gate result.

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

### W17 implementation note: solar array save protection

This note documents protection mechanics, not a changed decision or accepted
risk. W17 applies the existing `GuardedChunkSaves` mechanism, through
`SolarProtection`, to the array controller's `arce_solar_array` root (risk
R-021, solar array supplement). Only a root that is present and fails the
4,096-byte, depth-16 or 1,024-node preflight triggers it; a bounded unknown or
future root is saved verbatim, stops generation, output and ordinary removal,
and refuses nothing. The affected unit is the whole terrain chunk; the Level
capability retains denial across unload, and its existing 256-coordinate
saturation can refuse further terrain saves in that Level. The live raw Tag is
retained by reference and ordinary removal is refused. The existing guard throws
at the chunk save event so ChunkMap reports refusal and restores the dirty flag;
exceptions from BE serialization are not used to control saves. Guard logs have
no retry-volume bound. Denial lasts through the Level lifecycle, with no
production online reset. Back up, stop and repair the root offline before
restarting; restarting alone is not repair. The 2026-10-06 single-generator owner
decision did not cover arrays; this note relies on section 2 above and grants no
risk acceptance, native stop/restart evidence or Gate result.

*Additions from the W17 review (W17F, 2026-10-13; the decision is unchanged).*
- **Layout exception.** The code-defined 3-by-22 solar array is a scoped
  exception to ADR-016's 16-cell axis bound. It reuses the unchanged kernel
  matchers, transform, footprint index and dirty queue, with 66 inspected
  cells and the existing controller, cell-reference and per-tick limits.
- **One collector per panel.** A panel counts for only one formed array. A
  panel that another formed array already counts makes the second array a
  CONFLICT, as a shared plug does.
- **Operator-only item NBT.** The array controller and the single solar
  generator accept item NBT only from operators. This closes the creative path
  by which a non-operator could reach the guard above. It does not remove the
  administrative and corrupt-data risk.

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


### W11b1 implementation note: the D3-A escrow core (admission disabled)

This note records the section 10 coordinator core as implemented. It does not change a
decision or accept a risk. The risks are R-031 and R-032.

**Sources.**
- **Design:** `records/W11A-DESIGN.md`, round 2. Its independent design reviews returned
  REDESIGN, then APPROVE-WITH-CHANGES (C1 to C6).
- **Implementation:** W11b1 and continuation 01. The actual-diff review (SHA-256 `e3d1fd91…`)
  returned MERGE-WITH-FOLLOWUP.
- **Fixes:** W11b1F fixed all eleven review findings.

**What exists.** The following are implemented:
- the server-scoped coordinator;
- the bounded codecs;
- a per-type checked writer policy;
- one immutable ledger writer with exact generation acknowledgements;
- the barrier's own store with force-then-stable-readback;
- the stable Anvil reader with an endpoint-record size probe;
- fair reconciliation with persisted three-failure STALLED;
- joint same-slot proofs and per-endpoint prefix retirement;
- test-only endpoints.

**Production admission is disabled.** No production block, item or command can create a
record. Ledger creation is lazy, so a production world has no ledger file.

**Startup scope (R-031).**
- **Refusal.** `RESOURCE_ESCROW` is a schema-1 managed root. An existing future or invalid
  root refuses the whole world's startup and keeps its raw bytes. There is no online deletion,
  fallback or per-file restoration. The refusal persists across restarts until an offline
  whole-world repair succeeds.
- **Logging.** One bounded startup diagnostic, with its stack, is logged per failed startup.
  Repeated launches have no lifetime log cap. The startup hint names the escrow ledger only in
  worlds that have one.
- **Recovery.**
  1. Stop the server.
  2. Back up all Levels' terrain, entities and POI, the global data, journals and ledger, the
     player data and the metadata, together with hashes.
  3. Repair only a copy, or restore one consistent complete backup.
  4. Keep the malformed originals.
  5. Validate the copy natively: check conservation, run two save-and-restart cycles, and
     take fresh endpoint proofs.

  Never delete or restore the escrow root alone.

**Residual outside S2 (R-032).** A vanilla writer failure that the mod cannot observe is
outside the S2 claim, as round 2 of the design states. Examples:
- a failed region-cache eviction close;
- an external-file move failure;
- a later write that corrupts a chunk after its confirmation.

The native cuts E1 and E3 are diagnostics, not PASS: there is no Mixin and no extra seam to
inject those failures. Direct writes by other mods or tools bypass the save monitor. S2
proves stopped-process ordering after forced writes. It does not cover hardware or OS crashes,
power loss, or a rollback of one world file.

**Measured on this machine, not on the docs/17 reference machine.**
- Saturated 256-region terrain and entity caches.
- Both `sync-chunk-writes` settings.
- The main-thread cost is a lower bound.
- No reference-load or soak result exists yet.

**Open items.**
- *(Closed by W29, 2026-10-13: the reader now checks the endpoint's slot, not the whole file; two consecutive native selections passed 24/24 with no `BARRIER_UNSTABLE`.)* The native E2 recovery case failed once with `BARRIER_UNSTABLE`. The reader's stability
  check compares the whole region file's size and modification time, so a vanilla save of
  another chunk in the same region during startup makes the readback unstable. In production
  this is a retryable stall. Track it until the check is per slot, or until repeated clean runs
  close it.
- The endpoint chunk and Level save-veto scope is registered by W11b2, when real endpoints
  exist. *(Registered with W11b2 part 1, 2026-10-14: R-035.)*

### W11b2 part 1 implementation note: D3-A rocket integration (admission disabled)

This note records W11b2 part 1 as merged together with its fixes. It does not change a decision
or accept a risk. The risks are R-031, R-032 and R-035.

**Sources.**
- **Design:** `records/W11B2-DESIGN.md`. Its independent review returned APPROVE-WITH-CHANGES
  (R1 to R6, SHA-256 `8ec318a3…`).
- **Implementation:** W11b2 up to continuation 05 (`0638009f..3c47fa07`), recorded in
  `records/W11B2.md`.
- **Reviews and fixes.** The actual-diff review returned BLOCK (SHA-256 `8c1da511…`, F1 to F10).
  W11B2-P1F fixed F1, F2, F5, F6 and F7 (`records/W11B2-P1F.md`). Root's review of the fixes
  returned MERGE-WITH-FOLLOWUP (SHA-256 `8266bf6f…`, N1 and N2).

**What exists.**
- **The transfer journal.** Every write is a checked write on an off-thread writer: stage, force,
  decoded readback, atomic move, then a forced parent directory. Windows has no portable
  directory force. An acknowledgement is published on the server thread, for one exact snapshot.
  - Launch, phase, cleanup, migration, replacement and reconnect effects run only after their
    generation is acknowledged.
  - Failed or unknown writes keep authority and dirty state and retry every 200 ticks.
  - Any `Throwable` on the writer thread publishes UNKNOWN.
  - A normal stop drains, then writes the final RAM state. A crash writes nothing.
- **Rocket entity schema 3.** It adds the escrow section. It is neutral and omits `ledger_id` only
  at epoch 0 with no markers. Schemas 1 and 2 still load. An invalid section quarantines the
  wrapper verbatim.
- **Legacy scalar fuel.** Rockets saved before native propulsion keep playing. Resource
  comparisons use capacity, amount and debits when either side is scalar.
- **The section 8.1 hook.**
  - Ledger-first fence classification runs before any ticket.
  - Re-evaluation is bounded and loaded-only.
  - Final guards apply at every entry in the design.
  - The bay recovery gate is inert while no production caller installs it.
- **Governed readiness (W90; W90a merged `e80069c6`, W90b merged `fcd549c9`).** The bullets below record
  Root decisions R21 and R22 as implemented.
- **Flight tickets (W90, Root decision R21 item 1).** This is an explicit change from two origin tickets per transfer.
  - After a checked ACTIVE classification, and never while the record is held, the governed dispatch refreshes one
    `arce_rocket_flight` region ticket (distance 2, keyed by transfer) on each column of each server-recorded end's
    checked discovery-margin column set: at most 16 per end, 32 per transfer and 2,048 at 64 transfers.
  - The F2 callback's origin tickets remain a named exception, and legacy flights keep their two origin tickets.
  - F8 removes the origin ticket and the same set in each end's Level. Without a refresh, the tickets expire at the
    first purge with age greater than 400 ticks. Tickets are not persisted.
  - Ordinary reconciliation, pruning, held repair, the HOLD and CLEANUP routes and the legacy fences add none.
  - **Exception: retirement before the first escrow fill** (W130, Root decision R52's D2 (b2), W126-R1; merged
    `3bce8373` on 2026-10-22, recorded by Root under W130-R5). When a loader attempts a fill on a landed rocket that
    still has its COMMITTED transfer record T, the retirement places the existing ordinary flight ticket on the old
    launch site's origin column and refreshes the destination's, so W98's on-disk proof can remove T before the fill.
    It starts on demand only: at most four starts per tick and one attempt per rocket per 200 ticks, never at startup
    by itself, released on success or by the 400-tick expiry, without resetting W98's backoff. W130F (merged
    `6d1230f0`, R59, closing W130-R1) checks the three holds that no ticket can end (a discovery region over its limit,
    a Level with saving off, unsupported entity storage) before any ticket; one demand allows at most three ticket
    steps, after which the entry ends with T kept (`DEMAND_SPENT`) until a new demand; a loader only queues its
    demand, and the coordinator's tick starts the attempt. Unreachable while production admission is off.
- **Checked column set (R21 item 2).**
  - One server-free, overflow-checked helper defines the columns for readiness, governed pad admission, F1's source,
    refresh and release.
  - New governed admission requires at most 16 columns, including the origin column. The 16-chunk limit is applied to
    the margin and not raised.
  - A recorded governed end that fails the helper becomes executor hold `DISCOVERY_MARGIN`: no margin ticket, no
    further refresh, one WARN per flight and reason, and offline repair. Its scope is one transfer.
- **Governed admission budget (R21 item 3).**
  - For governed launches, surface, station and named-pad selection count margin columns before terrain access and
    refuse with `chunk_budget`, and F1's source refuses with `LAUNCH_CHUNK_BUDGET`.
  - Legacy selection is unchanged.
- **Destination warm-up (W90b, Root decisions R21 item 4 and R22 amendments 1 to 4).**
  - Immediately before F1's begin, after every existing check and the server's pad selection, and only when F1's
    read-only preconditions hold and a slot is free (a global cap and a per-initiating-player limit, each counting
    WARMING, READY and BOUND; admin and release-test launches count only globally), a governed launch gets one plain
    `arce_rocket_warmup` region ticket (distance 2, keyed by the logical rocket, added once and never renewed) on each
    column of the selected destination's checked set, and a RAM reservation of that exact region.
  - A ready destination calls begin at once and is BOUND on F1's acceptance; an unready one answers
    `DESTINATION_LOADING` and the player launches again with a fresh request ID. Without a slot, a ready destination
    launches without a warm-up and an unready one answers `DESTINATION_LOADING_LIMIT`.
  - A repeated request for the same destination and source geometry revalidates the kept candidate first and keeps its
    region, tickets and deadline. The starting request ID is refused while the warm-up lives.
  - Releases: the handover right after T's first ACTIVE refresh that ticketed columns; F1's refusal or the launch's
    `discarded` callback, which may follow `journal.put` and releases only the warm-up; replacement; disconnect and
    source removal for WARMING and READY only; expiry at the captured lifetime; and the runtime clear.
  - Operator settings can only lower the ceilings of 8 warm-ups, 2 per player and 600 ticks, and apply to the next
    allocation. The flight-ticket bound stays `MAX_LANDING_CHUNKS`.
  - It is an explicit exception to "only ACTIVE adds tickets", and F1 and F2 keep every check and proof. It does not
    change "Pads do not force chunks or take independent flight tickets": the warm-up ticket is a non-forced,
    non-persistent launch-preflight ticket keyed by the logical rocket, never held by a pad.
- **The shared admission authority:** 64 per server and 8 per rocket. It lives inside a
  coordinator that has a ledger. W27c1 lifts it to server lifetime.
- **The interlocks.**
  - Mount, teleport, spreadplayers, dimension travel, `kill` and live `/data` consult only ledger
    protection: markers, a bound section, records naming the rocket, logical locks and
    tombstones.
  - A dismount is never cancelled.
  - Board, leave and refuelling wait only for the rocket's own record version to be
    acknowledged.
  - A blocked journal refuses launch and resource mutation, as before.

**Production admission is disabled.** A ledger can be created only by the GameTest server or the
release-test hooks. Settlement (O1 is on the W11b2 branch; O2 to O4 and the O-veto are in progress)
and credited-world reconciliation are not part of part 1.

**Behaviour every world sees.**
- **Pending results.** Launch and cancel answer JOURNAL_PENDING and report the result later.
  `/arce rocket recover` answers RETRY_LATER first.
- **The baseline write.** The first start rewrites an existing journal once, as a checked
  baseline.
- **Downgrade.** After schema-3 saves, an older jar quarantines every rocket verbatim, so a
  downgrade needs a whole-world backup.
- **Paused transfers (F3).** Superseded by the P1-F3 and P1-N2 note below (W91): a flight now
  waits only for its own record's snapshot. The text it replaces said that while any journal write
  was unacknowledged no live transfer advanced, pausing every flight for 2 to 3 ticks per advance
  at the measured writer p95 of about 125 ms, and for about 117 ticks at the largest legal journal.

**Open before W11b3 or any admission:**
- F3: closed by W91 (`264c4e30`, with W91R `843ea4dc`); see the P1-F3 and P1-N2 note below.
- F8: the landed reservation is released before a disassembly that can still fail.
- F9: a failing respawn rewrites the journal on each recovery attempt.
- N1: a section bound to the current ledger is never idle. Restore "another ledger, or no
  ledger, is not idle", and add a test.
- N2: closed by W91 (the fail-back reserves its continuation before it changes anything); its
  native refused-reservation and named-pad cuts are still to run (the W96 leaf N3-C).
- P1-N3 (found by W91K's native cuts, 2026-10-18; it predates W91): an ordinary fail-back changes
  the source in RAM and then removes the record, with no entity save between, so a crash after the
  removal is durable and before the source chunk saves leaves the rocket in its saved in-flight image
  with no record. The design W96 is frozen with its review's amendments (Root decision R34); W98
  implements the first leaf.

This is a development implementation note, not owner acceptance or a Gate result.

### W11b2 part 2 implementation note: settlement, reconciliation and governed flights (admission disabled)

This note records W11b2 part 2 as merged together with its fixes. It does not change a decision
or accept a risk. The risks are R-031, R-032 and R-035.

**Sources.**
- **Design:** `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`) and its independent review.
- **Root decisions:** R1 to R20 (with R19A), in `D:/GitHub/ARCE-Task-Evidence/root-queue/` and summarized in
  the W11b2 records; each continuation's TASK names the ones it applies.
- **Implementation:** W11b2 continuations 06 to 54 and W89, recorded in `records/W11B2.md`,
  `records/W11B2-F7F8.md`, `records/W11B2-C41.md`, `records/W11B2-C46.md` to `records/W11B2-C54.md`
  and `records/W89.md`.
- **Reviews.** The settlement, C09, C11 and C0 to C8 reviews; the final review part A
  (settlement, receipts, capacity, operator; MERGE-WITH-FOLLOWUP, SHA-256 `4d9d8273…`) and part B
  (governed flight path; FIX-FIRST, SHA-256 `60581547…`); and the two reviews of their fixes
  (the journal-lease fix review: FIX-FIRST, SHA-256 `d1d513c2…`, LR1 and LR2 fixed by continuation 49;
  the flight fix review: MERGE-WITH-FOLLOWUP, SHA-256 `5a418f13…`, N1, N3, N4 and N7 fixed by
  continuation 50).

**What exists.**
- **Settlement O1 to O4** of a frozen decision: O1 freezes the intent; O2 takes joint native
  proofs of every endpoint; O3 installs the receipts (tombstones) with the survivors; the journal
  records naming the settled members are removed (O3J); O4 removes the settled records. Every
  step needs its exact acknowledgement; a released survivor's markers are pruned later by the
  bounded pruning cursor, never by the probe.
- **Operator commands** (`/arce escrow status`, `inspect`, `retry`, settlement preview and
  confirm) with console provenance; a retry accelerates only retryable reasons and has one
  200-tick cooldown per settlement intent.
- **Ordinary reconciliation C0 to C8** for `FLUID_PROPULSION_FILL` with fixture sources only:
  `proved=true` comes only from C7; each step's permit is one-use.
- **The governed flight path F0 to F8**: an ARMED entry checkpoints the exact source envelope E
  before launch (F1); a schema-3 PREPARED record (F2); bound source phases with one-use permits
  (F3); a fixed destination identity (F4); the passenger and source moves (F5); the joint proofs
  of the survivor (F6); the HANDED_OFF or CANCELLED outcome with the exact successor-lock release
  (F7, R12); and cleanup that prunes the ARMED entry (F8). A pre-F2 orphan is cancelled only
  after it proves the unchanged E and checked no-T.
- **Rider normalization (R18).** Every governed comparison of a native rocket envelope from F1 to
  F8 uses the envelope with its physical rider list normalized to the empty list, after checking
  the live riders as a duplicate-free subset of the assigned passengers. The manifest and every
  other field are compared exactly. Uncrewed digests did not change.
- **Per-rocket journal lease (R17 item 3).** F1, F7, F8, the pre-F2 cancellation, settlement O3
  and O4 and reconciliation C2 to C7 hold a lease on logical rocket L only; another rocket's
  journal change does not revoke it. A coalesced flight batch is captured again without a request
  whose guard refuses.
- **Writer accounting (R17 item 4).** A guard refusal before replacement is its own outcome
  (`GUARD_REFUSED`) and never counts towards writer unavailability; autosave runs while the
  writer is unavailable.

**Production admission is disabled.** A ledger can be created only by the GameTest server or the
release-test hooks, so production worlds see none of the holds below.

**Formats (unreleased, M2).** Escrow root schema 2, receipt schema 3, governed transfer record
schema 3 with envelope 1, and journal root 3 (W44). Their definitions changed in place during development with no migration chain. A
development escrow root written before a format change is refused at whole-world startup with
its raw file preserved and one ERROR per start; worlds without a ledger are unaffected. R18 left
uncrewed ARMED digests unchanged; a crewed development arm written before R18 no longer matches
and stays held (whole-world repair or regeneration). Older jars refuse and preserve governed
records.

**Capacity.**
- 64 permanent receipts per world lifetime, every choice counted; at most 64 receipt-backed
  READY heads; 64 ARMED or terminal flight entries, counting terminal and UNKNOWN entries.
- Concurrent fill capacity is min(byte capacity, 63 − permanent receipts) (part A F1): at 63
  receipts no fill is admitted until offline repair. Pinned byte capacities (R5 to R9): an empty
  root admits 62 unguarded or 58 guarded fills with no floors, and 60 or 56 with 29 floors.
- A launch refused by ARMED capacity answers TRANSFER_JOURNAL_BLOCKED, releases its pad and logs
  nothing.

**Refusals, holds and quarantines (AGENTS.md section 3.3).** None rewrites or discards a saved
file. Each refuses an action or holds resources.

- **Pre-change development root (M2).** Scope: the whole world, only when the ledger file exists.
  Every start is refused until repair; the raw file is unchanged. Log: one ERROR per start, without
  a stack. Recovery: whole-world repair, or regeneration of a disposable fixture world.
- **Baseline readback (M4).** Before start, a root that does not re-read identically refuses
  whole-world startup, as above. A late bind failure makes escrow unavailable for the session:
  every rocket in that world is non-idle (no refuelling, launch, mounting or native resource
  change), and settlement, pruning and reconciliation stop. Log: one WARN
  `ARCE_ESCROW_BASELINE_UNAVAILABLE`. Duration: until a stopped-server repair and restart.
- **Settlement O1 to O4.** Scope: every endpoint of the frozen component, which covers rocket L
  (refuelling, launch, mounting, disassembly and external movement) and its fixture sources.
  Duration: from the O1 acknowledgement until O4, with no online rescind; indefinite under a veto,
  a frozen-observation mismatch, an unloaded endpoint or a missing proof, retried every 200 ticks.
  Log: INFO `ARCE_ESCROW_O1` once, INFO `ARCE_ESCROW_SETTLEMENT_HELD` once per reason change per
  intent and after each restart, INFO `ARCE_ESCROW_O4`. While O3J's journal removal is
  unacknowledged, part 1's journal pause applies. Recovery: automatic for retryable reasons (the
  operator may accelerate them, once per intent every 200 ticks); a mismatch, a veto or an invalid
  identity needs whole-world repair.
- **Permanent receipts.** Exhaustion refuses every settlement choice and preview
  (`RECEIPTS_FULL`) and, through the capacity rule above, every fill. A missing or abandoned logical
  rocket, any physical copy of a settled L other than its READY head's, an exact retired transfer
  and a missing block-entity identity stay locked for the world's lifetime. Recovery: whole-world
  repair.
- **Survivor markers awaiting pruning.** The released group's survivor and fixture source stay
  non-idle until the bounded cursor can remove the leading prefix; a wait behind an earlier group
  or live work is not sticky, a frontier that never matches holds indefinitely. Log: one WARN
  `ARCE_ESCROW_PRUNING_REFUSED` per change of (group, endpoint, reason), and one INFO
  `ARCE_ESCROW_PRUNING_CLEARED` when a warned endpoint later prunes; `/arce escrow inspect` shows
  `pruning=<state>`. Recovery: automatic, otherwise whole-world repair.
- **Ordinary reconciliation.** The C3 freeze holds the endpoints from C3 to L2. A C7 write with an
  UNKNOWN outcome keeps the candidate and the locks. A C0 to C2 OTHER_STATE is sticky REPAIR: the
  source debit stays held for the whole component until settlement or offline repair, with one
  WARN per state change. A stale destination prefix is a silent wait until the rocket's first
  tick-hook prune. A component above eight members, or a 65th Level service, is BARRIER_BOUNDS:
  STALLED after three failures with one WARN, retried every 1,200 ticks.
- **ARMED authority, F1 to F8.** L is not idle: it cannot launch again, be mounted externally, be
  disassembled or killed, or be teleported, spread or moved to another dimension, and its
  resources stay locked until the exact F7 acknowledgement (R12). Healthy duration: seconds to
  minutes, with no log.
- **F1 preparation.** Any refusal discards the launch, releases the tentative pad and backs off by
  100, 400 and 1,200 ticks, with one INFO `ARCE_ESCROW_SUCCESSOR_HOLD` per transition. Another
  rocket's journal change no longer refuses it. A full F2 callback queue no longer discards a
  written PREPARED launch; F3 starts its countdown.
- **Pre-F2 orphan (ARMED without T).** Held until the cancellation proves the unchanged E (R18) and
  checked no-T; it then writes CANCELLED, and F8 prunes the arm. INFO hold lines once per
  transition. Recovery: automatic, otherwise whole-world repair.
- **Governed classification holds.** Loud reasons (`GOVERNED_LEDGER_MISSING`,
  `_LEDGER_UNAVAILABLE`, `_LEDGER_MISMATCH`, `_ARM_MISSING`, `_ARM_MISMATCH`, `_ESCROW_PENDING`,
  `_RETIRED_AUTHORITY`, `_EXACT_TRANSFER_FLOOR`, `_READY_REQUIRED`, `_READY_MISMATCH`,
  `_TERMINAL_PHASE_MISMATCH`, `_RECORD_REQUIRED`, `_ACTION_REQUIRED`) log one
  `ARCE_TRANSFER_FENCED ... repair=offline-whole-world` WARN per reason change per transfer and are
  re-evaluated on the 100, 400 and 1,200-tick schedule; silent reasons (R10, R11, R14:
  `GOVERNED_JOURNAL_UNACKNOWLEDGED`, `_LEDGER_PENDING`, `_RUNTIME_STOPPED`) log nothing. Scope: one
  record and its logical rocket. ARM and READY mismatches are permanent and need whole-world
  repair.
- **Executor holds inside an ACTIVE route (R17 items 5 and 6).** Motion that the rocket's own tick
  zeroes is transient and sets no sticky flag. A non-transient refusal (a persistent position
  change at rest, a mismatched envelope, a native endpoint failure) logs one WARN
  `ARCE_GOVERNED_EXECUTOR_HOLD transfer=<T> reason=<reason>` per flight and reason, only while the
  journal still holds that flight as ACTIVE, and stops refreshing the flight tickets, so their
  chunks unload. Duration: indefinite. Recovery: whole-world repair.
- **F6 passenger wait.** The survivor's resources, ARMED, T, the pad and both tickets (two loaded
  chunks) are held only while an assigned passenger is offline (R17 item 2). An online assigned
  passenger at rest reaches F5's checked move; CANCELLED accepts an online passenger aboard or
  unmounted in the source's Level. Duration: unbounded while the passenger stays offline; W80's
  admin recovery is the follow-up. Log: none for the wait.
- **F7 or F8 UNKNOWN.** The writer keeps the exact candidate, so every other rocket's F1, F7, F8
  and pre-F2 write waits behind it (world-wide escrow-writer serialization, not a Level save veto),
  retried every 200 ticks. Log: one INFO per unknown episode and one on resolution.
- **Writer unavailable.** Three unresolved real write outcomes (a guard refusal does not count,
  R17 item 4) refuse new admissions, flight writes and settlements and fence governed transfers
  with WARN lines. A dirty autosave still runs and a COMMITTED generation restores availability.
  Log: one WARN on entry, one INFO on recovery.
- **Terminal entries awaiting F8.** The slot stays charged and L cannot relaunch. Blocked reasons
  `GOVERNED_CLEANUP_AUTHORITY_CONFLICT`, `_JOURNAL_CONFLICT` and `_PAD_UNAVAILABLE` log one WARN per
  reason change.
- **Schema-3 record refusal.** A governed record that fails envelope decode, has an unknown field
  or fails digest validation blocks the whole transfer journal under the existing managed-root
  policy, so every transfer in the world stops. Log: the existing journal ERROR. Recovery:
  whole-world repair.

**Accepted interval (R13).** A motion change between a capture and its off-thread replacement is
seen only at the next owner-thread observation. The write records the proved disk state, and such
a change cannot duplicate or lose the rocket.

**Pending pads.** A governed launch's selected destination stays reserved in memory from F1
admission until its F2 publication callback returns or the launch is refused or discarded, and is
kept while the ARMED write is UNKNOWN (continuation 53). Pad selection for every launch, governed
or not, excludes these regions as well as the journal's; when all pads are taken the request gets
the existing `LANDING_PAD_UNAVAILABLE`. The set is bounded by the 64 pending launches and is not
saved: a pre-F2 arm without T is cancelled after a restart (R19, R19A).

**Journal leases.** F1, F6, F7, F8, the pre-F2 cancellation, settlement O3 and O4 and
reconciliation C2 to C7 lease only logical rocket L's checked state, so ordinary and governed work
completes under unrelated journal activity. Another record that names the same T or serial still
refuses the lease, checked against the acknowledged journal as well as RAM.

**Known limit.** A governed return requested by the owner exists only in memory: after a restart,
a cancelled countdown whose return was not yet applied resumes forward dispatch. Nothing is
duplicated or lost; the flight does not do what the owner asked.

**Recovery.** Retryable IO resumes by itself. A frozen mismatch, missing or invalid authority, a
pre-change format or receipt exhaustion needs stopped-server whole-world-copy repair: back up
every Level's regions, entities and POI, the global data, the journals, the ledger, player data
and metadata with hashes; repair only the copy and keep the original; validate natively; do two
save and restart cycles; then replace the whole stopped world. Restoring one file, including a
migration backup, is not safe, and a restart alone is not a repair.

**Limits.** There is no reconstruction of a missing credited source (W27f), and R-032's
exclusions remain.

**Accepted deviation (part A F6).** Receipt schema 3 does not name the predecessor READY head
(design 2 section 1 says O3 names it). This has no safety effect: L is locked from O1 to O4, and
O4 reads the previous head from the root it replaces.

**Benchmark.** Continuation 54's native run on `20304245` ran continuation 45's eight-flight
reference load on the Windows 11 host (12 logical CPUs; the JVM limited to 4 processors and 4 GiB;
not the Debian reference node): two connected players, 16 active oxygen vents, ten stations, 100
satellite tasks, a 2,048-block assembly, eight concurrent governed flights through the player
entry (all eight landed as checked idle survivors, six of them through F3's countdown fallback),
a world-first DEFER held for 20 ticks over 77 dispatcher visits (R16 item 3(c)), one ordinary C0
to C8 retirement, settlement O1 to O4 with pruning, seven periodic saves and one restart. The
runner reported full coverage. Over 1,400 measured ticks the main thread's mean was 4.17 ms, P95
12.04 ms and P99 39.43 ms (budgets 25, 50 and 100 ms); the largest non-save tick was 156 ms
(budget 500 ms) and the peak RSS 1.78 GiB (5.5 GiB); the 20 ticks containing the DEFER averaged
7.18 ms. From the F1 request to the checked idle landed survivor took 20.6 s on average and 26.4 s
at most. Its first attempt completed the same workload but its process monitor reported an
incomplete JVM inventory, so the runner rejected it before the restart; it is retained, not used.
These short-run numbers are development evidence, not a soak, a reference-node measurement or a
Gate result.

**Open before W11b3 or any admission:**
- Part 1's F3 and N2 are fixed by W91 (the P1-F3 and P1-N2 note below), F8 by W92 (the P1-F8
  note below), F9 by W77 and N1 by part 2. P1-N3 (part 1 open item above) and N2's remaining
  native cuts remain.
- Signed per-fluid accounting.
- Governed readiness and cold destinations (W90): fixed by W90a (`e80069c6`, one ticket per
  checked margin column) and W90b (`fcd549c9`, the bounded destination warm-up); see the W90
  bullets in the part 1 note. The measured cold, border-crossing and largest-margin costs and
  readiness times remain before admission (R22 answer 6).
- A launch-boundary refusal at the exact moment of a return proof callback holds that return
  until a restart (flight fix review residual).
- N6: the arrival executor runs twice per COMMITTED visit (cost only; no state effect).
- Arrival entity readiness (W13-N1): fixed by W83b (`ff5e5dc1`, Root decision R32). An arrival waits at its
  PREPARED wait until every column of the destination's checked column set has its entity sections
  loaded; the check only reads. To end that wait, every flight's destination ticket covers that set (at
  most 16 columns, an explicit change from one origin ticket for legacy flights, 1,024 at 64 transfers);
  a destination whose set is not admissible keeps its origin ticket. The recovery rebuild path's missing
  occupancy guard and a rocket whose entity lies outside the checked set remain residuals.

This is a development implementation note, not owner acceptance or a Gate result.

### P1-F8 implementation note: landed disassembly keeps its claim (W92, Design M of W85 revision 3)

Implemented by W92 (commits `2a28c604` to `df25825a`, record `records/W92.md`, merged at the W92 merge) and reviewed independently (W92-review, MERGE-WITH-FOLLOWUP, SHA-256 `4c8b9c2d…`). Root's decisions on the review: M2 also rechecks the v0.5 journal's operability and pending recovery before restoring (W92-R2), and a non-native schema-1 record may be disassembled with an identical write-back (W92-R4); both, with the review's other low findings, follow in W92F. The diagnostic native cuts K6 and K11 move to the S2 campaign.

Disassembly of a landed arrival keeps the existing order: the checked removal of its transfer record
T is acknowledged before any block is restored, so the transfer journal never rebuilds the rocket
over restored blocks. This is not a promise that a stale native entity cannot reappear. No record,
key, schema or root is added, and older jars read the journal unchanged. A request first runs the
restoration's no-mutation checks; a predictable failure answers its code and keeps T. Otherwise the
service reserves a journal continuation, removes T, keeps T's destination region and journal slot
reserved for new flights in a session-only pending entry (at most 64), and answers
DISASSEMBLY_PENDING. Repeated requests answer DISASSEMBLY_PENDING after the actor, identity, range
and permission checks, and zero-fuel arrivals take the same path. On the removal's exact
acknowledgement, and at most 200 ticks after the request, the continuation sets a one-way
restoration latch and restores the rocket. The identical T is written back only when restoration
never started (a late acknowledgement, a failed precondition or a discarded continuation) or after a
certified complete rollback. Once mutation is uncertain (ROLLBACK_FAILED, or an exception or `Error`
after the latch), nothing writes T back, and the v0.5 recovery stays the only owner of its record. A
written-back T keeps the pending entry and the rocket's interlocks until its write is acknowledged,
with no time limit; no timeout releases unacknowledged state. Residuals (R-035): ROLLBACK_FAILED or
an uncertain mutation, a refused write-back, and a process stop while disk lacks T leave T released.
That disk gap is about one tick or one checked write when uncontended, and unbounded while a
successor write is late or failing or after an UNKNOWN outcome. The spot is then protected only by
the loaded-rocket occupancy check and, once merged, W83's footprint readiness, which does not see an
unloaded rocket whose entity lies outside the footprint. A process stop after the removal reaches
disk loses an arrival whose entity section was never saved. A write-back that keeps failing locks
the rocket and, while F3 was open, paused every flight until storage was repaired (superseded:
since W91 it holds only that rocket). After a successful
restoration, vanilla saves terrain chunks and entity sections independently, so a stop can leave a
stale entity together with saved blocks, lose the unsaved chunks' blocks of a never-saved arrival,
or, when the entity's removal was saved while a footprint chunk was inside its 10-second save
cooldown, lose those blocks of an arrival that had been saved. These belong to the existing v0.5
disassembly class. Only ROLLBACK_FAILED is detected, and a restart makes the losses and duplicates
permanent. Repair needs a known-consistent whole-world backup from before the operation. This is a
development implementation note, not owner acceptance or a Gate result.

### P1-F3 and P1-N2 implementation note: per-record journal scheduling (W91, design W86)

This note records W91 (merged `264c4e30`, review W91-review MERGE-WITH-FOLLOWUP, follow-ups W91R
`843ea4dc`) and its native evidence (W91N and W91N2 `0d6b839a`, W91K `93eb3ad2`). It changes no
decision status and accepts no risk. The risk is R-035.

- **The rule (Root decision R23).** The transfer journal keeps one file, one writer and one exact
  acknowledgement. Each record slot, and the Moon-landing outbox, counts as acknowledged when the
  acknowledged snapshot was captured at or after the slot's last change in RAM. A rocket is
  actionable when its slot is acknowledged and it has no queued continuation and no held
  reservation. Live flights, recovery, reconnection and governed flight permits check that the
  rocket is actionable instead of a clean journal. A generation already in flight when a change is
  made does not acknowledge it.
- **Continuations (P1-N2).** Every ordinary journal mutation first reserves its continuation, one
  per logical rocket; a refused reservation changes nothing. The exceptions are W11b2's F2 put,
  settlement's O3J removal, the Moon-landing delivery, a write-back at a stop, and test fixtures,
  each under its own frozen or whole-journal rule. Only W85's M2 (the landed removal's
  continuation) runs while another record's write has failed (W91R).
- **Failures.** UNKNOWN, a blocked or unreadable journal, and the time before a session's first
  acknowledgement still pause every transfer; a legacy launch before that first acknowledgement is
  admitted and counts down only after it (W91R). A definite failure stops only steps that need a
  new write, and queued continuations wait for the retry.
- **Whole-journal consumers (Root decision R31).** Escrow settlement, floor collection, the escrow
  audit, governed F1 admission, pre-F2 cancellation and the test hooks still need a clean journal
  with no queued continuation, and F1 admission, pre-F2 cancellation and settlement also need no
  journal mutation during their asynchronous barrier and ledger write. Under eight-flight
  staggered native traffic they completed in 907 ms, 986 ms and 2,988 ms (W91N2), but no journal
  mutation fell inside those windows; under continuous traffic their wait has no bound.
- **Measured (W91N, development evidence, not a reference-node result).** Five native sessions of
  eight concurrent flights, staggered, simultaneous and with one record's write held, had no
  missed actionable step, no step beyond the three-write bound, and mean, P95 and P99 tick times
  within 25, 50 and 100 ms; each session had one tick over 500 ms at the first admission, recorded
  for the reference campaign. `RocketJournalBenchmark` reran unchanged.
- **Crash cuts (W91K).** The F3 held-successor, F3 UNKNOWN and governed exact-source return cuts
  passed with two restarts each. The ordinary fail-back cuts after the removal's move and after an
  UNKNOWN removal found P1-N3 (above).
- **W92's landed disassembly** is on the per-record API (W91H): its request, removal and write-back
  reserve before their mutation, and a later committed generation captured after T's removal
  acknowledges that removal (Root decision R28).

No saved format changes. This is a development implementation note, not owner acceptance or a
Gate result.

### P1-N3 leaf N3-A1 implementation note: ordinary source returns keep T until the survivor is proved (W98, design W96)

This note records W98 and its review follow-ups W98F (design W96 frozen by Root decision R34 with the review
amendments W96-R1 to W96-R5; independent review W98-review APPROVE-WITH-CHANGES, SHA-256 `493187a7…042b`). It
changes no decision status and accepts no risk. The risk is R-035.

- **The order.** An ordinary source return (I1 fail-back, I2 source recovery, I3 last reconnect) keeps the actual
  transfer record T until the survivor's origin-column entity slot, and the slot of each discarded copy, are
  stored, forced and read back; only then is T removed, and the call site's effects run on that acknowledgement.
  W91K's two P1-N3 cuts now leave the rocket FUELED at its origin on both restarts, and three new cuts before the
  store, the force and the removal keep T on disk until the first restart proves the rocket.
- **Bounds.** The journal reservation is held for one proof attempt of at most 100 ticks and released on any wait
  (W96-R1, W98-R5); at most 64 pending returns; an ordinary sub-quota of 8 captures and 4 entity captures per Level
  per batch in the shared barrier, with a silent checkpoint sink, and ordinary captures take the tick's capture only
  after governed dispatch and cleanup (W98-R4). A save-off Level holds its returns, checked before each capture.
- **W96-R2** was a real defect: a reconnect during a live source phase removed a live flight's record. Retirement on
  reconnect now requires a settled record whose source is stationary at its origin.
- **Fallback (R5(c), W98-R11).** Only the exact vanilla entity storage supports the own-store proof; elsewhere T is
  removed after the in-memory return, as before W98, with one WARN per Level.
- **Root decision R35, corrected.** R35 item 1 chose that fallback for a world whose escrow ledger is present but not
  operational. W98F showed that such a world already fences every rocket (`EscrowCoordinator.logicalLocked`), as
  before W98, so no fallback is needed and none was built.
- **N3-A2 (W116, merged `81c987f9`; W116-review APPROVE-WITH-CHANGES, SHA-256 `d032498c…de9e`).** Each proof
  attempt refreshes the existing flight ticket on every census column (W98-R3; an off-origin I3 discarded slot
  still gets none, and the 400-tick expiry bounds a ticket after its last refresh). An ordinary store or force
  failure in a mixed barrier batch no longer fails the governed attempts or counts in their `ioFailures`; a force
  failure on a worker that also carries a governed capture still fails it (W98-R10). The coordinator's guards,
  escrow rule and test pause moved out, with state-machine and contention coverage (W98-R10, R12). Root decision
  R42 keeps recovery waiting for an offline crew after a countdown cancellation killed before its proof: a source
  that recovery already returned, or a stale COUNTDOWN image, cannot be told from one that never left (W98-R6).
- **Open:** W98-R1 (an ordinary capture invalidates a governed proof of the same entity slot, before governed
  admission opens; W116 observed it on every run), W98-R6's offline-crew hold (R42; a durable cancellation intent
  is reconsidered with N3-B), N3-B (I6, I7) and the player checkpoint adapter.
- **N3-B design (W148, reviewed; Root decision R87, 2026-10-23; not yet implemented).** I6 (landed disassembly:
  the pre-retirement gap and a second, independent terrain transaction) and I7 (a replacement that loses the old
  end's exclusion authority) take N3-A1's pre-retirement proof, with R52 and R59's on-demand ticketing extended to
  them inside the unchanged shared caps. Root's answers to W148 section 7: (1) the terrain dependency is accepted and
  no pre-M1-only player path ships; (2) the demand ticketing extends to I6 and I7, including the non-destructive
  cold-disassembly preparation and a fresh confirmation; (3) a destination with unsupported entity storage refuses
  instead of R34's R5(c) unproved fallback, with the scoped pending-operation and footprint exclusions; (4) the
  existing-format U, checkpoint and recovery scope is approved, including all ordinary no-T disassembly. No saved
  format, schema, bound, lease, budget, timeout or protocol changes. The holds and their recovery are in R-035.
  Leaves N3-B1 to N3-B4 follow W148 section 7; N3-B2 needs an independent persistence review before integration.
- **N3-B2 isolation (W154 design revisions 1 and 2, `records/W154-DESIGN.md`; review W154-review BLOCK, SHA-256
  `6facedc6…`, and its re-review, SHA-256 `c67bfebd…`; Root decisions R91 and R93 to R96, 2026-10-24; not yet
  implemented).** W152 showed that vanilla container access (hoppers, the item capability, menus, breaking) bypasses
  the region locks while U is retained, so a rollback could restore items that had already left. Sealed staging closes
  that window. While U is retained, every member of the footprint is an inert carrier C: no inventory, capability,
  menu, ticker, drops or item form; unbreakable, and immune to explosions and pistons. C holds the member's original
  state, its adapter ID and the adapter payload that U holds.
  - **Order (C0 to C5):** U LOCKED is acknowledged, then C is staged and proved, then RESTORED is acknowledged (the
    terrain is elected), then D is retired and proved, then U is removed with W123's checked acknowledgement. Each C
    is unsealed after that.
  - **Why no added residual:** after unsealing, C and the published container are two representations of one member
    in one terrain slot. A later transfer out of the container has the torn-save behaviour of every vanilla chest
    (ADR-054), not the disassembly's two-store transfer, so section 10's D3-A rule against an added residual holds.
  - **Unsealing (R95, R96):** every adapter restores into a target that is not yet installed, while C stays in the
    world. The target is verified by recapture and published in one step. A failed restore discards the target (it is
    marked removed, and a built-in target is also emptied) and holds C.
  - **Provider contract (method signatures unchanged):** restore receives a server-bound target before it is
    installed and must work from the target and the payload. A provider that needs an installed host is held, and
    nothing is lost.
  - **Trust boundary:** code that keeps a raw block-entity reference from `AttachCapabilitiesEvent` and uses it after
    the block entity is removed is outside the protection, as it already is for every vanilla container instance a
    chunk unload leaves behind. R-035 discloses this boundary.
  - **Saved format (R93):** one new block and block entity, the carrier, with a schema-1 root within U's per-member
    bounds and ADR-061/064 preservation; it is never discarded or replaced with air. Journal schema 2 gains the record
    type `SEALED_DISASSEMBLY`, so existing `DISASSEMBLY` records keep their current recovery. An older jar blocks the
    journal root; downgrade follows section 13.
  - **Limits:** no bound, lease, budget, timeout or protocol change.
  - **Stop condition:** W152's first commit probes the publication sequence, including an attachment listener that
    keeps the raw target, and stops if no one-step publication exists without a Mixin.

No saved format changes except the N3-B2 carrier and the `SEALED_DISASSEMBLY` record type that R93 authorizes above.
This is a development implementation note, not owner acceptance or a Gate result.

### W11c and W11d implementation note: rocket cargo scope and protections (design W11CD, admission disabled)

This note records the cargo design (W11CD, frozen at `49f7e08b`) and its first leaves: C-I, the
structure digest and the grouped recognition predicate (W99, merged `fac40f53`), and C-K, the escrow
core's fluid cargo kinds (W100 with W100F, merged `c3f80822`). It states the protection scope before
leaf C-P implements the tank payload host, as AGENTS section 3.3 requires. R-035 registers the same
scope (`93659768`). It changes no decision status and accepts no risk.

- **What cargo is.** W11c adds `rocket_fluid_loader` and `rocket_fluid_unloader`, the
  `pressurized_tank_v1` payload host and the cargo projection `ROCKET_COMPOSITE_V1`; W11d adds
  `rocket_item_loader` and `rocket_item_unloader` for vanilla chest and barrel payloads. Each device
  holds one 16,000 mB Fluid or 4 slots of at most 64 items and 4 KiB per stack, and moves at most
  1,000 mB or one stack per 20 ticks into or out of one payload the server chooses deterministically,
  never a propulsion bank. A cargo operation publishes a whole new snapshot with the same snapshot ID
  through the shared fair queue and the runtime-owned authority, with Root-R6's headroom and Root-R8's
  charging. Disassembly restores the latest cargo exactly once; rocket epochs and transfer floors stop
  an older snapshot from recreating unloaded cargo. One live cargo operation per rocket, no chains, no
  mixing of fuel and cargo or of load and unload.
- **Protections and their scope (AGENTS section 3.3).**
  - *One device:* an invalid, future, quarantined or unregistered-content device root is kept
    unchanged and the device is disabled. Player removal is refused before loot, explosions skip it,
    extraction is refused and nothing drops. This lasts until the cause is resolved (the missing mod
    restored and verified at the next load, or a supporting jar or offline repair for future or
    invalid data); a restart alone does not clear it. An escrow lock lasts separately, until a proved
    reconciliation or an operator settlement releases it.
  - *One terrain chunk:* only an observed native incompatibility vetoes that chunk's terrain save,
    through `GuardedChunkSaves`. A raw root that can be written and read back losslessly, or that
    exceeds the bounded native check budget, is saved unchanged; runtime oversize alone never refuses
    a save.
  - *One Level:* after the inherited saturation bound of 256 refused coordinates, that ServerLevel's
    later terrain saves are vetoed until the Level closes. Entities, players and global stores still
    save separately; this is not an atomic world pause, and closing the Level ends the veto without
    clearing the incompatible data. No new Level-wide entity-save veto is added.
  - *Entity records and barriers:* unsupported tank or container payloads follow the existing rocket
    entity quarantine and retention rules. If cargo makes a rocket's native entity record exceed the
    supported inline sector read, cut-E2 keeps its operation and endpoints locked and refuses
    retirement until a valid proof or an offline repair; unobservable native failures stay diagnostic
    residuals, not new guarantees.
  - *Logging:* one bounded audit line per state, reason or backoff transition, never per tick.
    Repeated transitions and the inherited native or Forge save errors have no lifetime log cap.
  - *Recovery:* stop the server and keep the originals and hashes of every Level's terrain, entity
    and POI files, the global journal, ledger and data, player data and metadata; repair only a
    whole-world copy, verify conservation and fresh proofs with two saves and restarts, then replace
    the world as a whole. Never restore a single store, substitute a cleaned root or treat a restart
    as repair.
- **Item kinds (D-K, W110 with W110F, merged `0b11269f`).** The item load and unload kinds, the
  `CONTAINER` unit, the bounded lossless stack codec (4,096 bytes, depth 16, 512 nodes, `ForgeCaps`
  refused) and the signed four-slot buffer; item settlements record quantity 0. Under Root decision
  R39, decoding a durable item record is structural only (ID syntax, count, bounds, `ForgeCaps`, the
  exact field set and a string `SkullOwner` on a player head), so removing an item's mod does not
  refuse the world; the registry, native maximum and round-trip checks run at admission, O2 and
  readback, where a failure holds only that operation for REPAIR (C-E and D-E wire them). Item
  operations require the loader as source and the rocket as destination, or the reverse for an
  unload. An older jar refuses a ledger with item records as invalid data, keeping the bytes (R-035).
- **Fluid payloads (R39 for fluids, W115, merged `f8ddfc25`).** A durable fuel fill or fluid cargo
  record decodes by structure only (`EscrowFluidCodec.validateStructure`: the bounds, the exact
  field set, the ID syntax, an `Amount` of 1 to 1,000 and a compound `Tag`), so removing or renaming
  a Fluid does not refuse the world. The registry, non-empty and round-trip checks
  (`EscrowOperation.nativeFluid()`) run at admission, the fill's C2 permit and both projections; a
  fill that fails is held alone as REPAIR with the existing `OTHER_STATE` reason and one
  `ARCE_ESCROW_FLUID_UNAVAILABLE` WARN. Cargo payload objects stay native; C-E calls the check before
  its C2, O2 and readback act. No saved format, hash or bound changed (R-035).
- **Receipt charges (R40 and R41, W114 with W114F, merged `4153204e`).** A new settlement decision
  takes an actor name of 1 to 16 characters in ASCII 33 to 126, the 1.20.1 login rule; a player whose
  profile name does not conform settles from the server console, and stored decisions keep their
  128-byte decode bound. Admission and the shared preview charge one rocket marker for the rocket a
  cargo member names and five for any other rocket. Every guarded receipt then fits in 2,048 bytes
  on every registered Level (worst at Tau Ceti g: 1,557 bytes for cargo, 1,833 for fuel). No saved
  format, ceiling or decode bound changed (R-035).
- **Preparation (C-P part 2, W112 with W112F, merged `16b368f6`).** A cargo change is reconstructed
  in RAM by one session-owned ticket at a time, within one reconstruction, 16 MiB and 16,384 visits
  per server tick, with byte-identical hashes. While a ticket exists, the rocket's refuel,
  disassembly and launch are refused; only an escrow-idle rocket can begin one. The ticket ends on
  publication, on any change that invalidates it (unload, movement, a new snapshot), or after 40
  server ticks without progress or unpublished once READY, with one INFO line
  (`ARCE_CARGO_PREPARATION_CLOSED`). Nothing is saved; the prior snapshot stays authoritative.
- **Item cargo (D-P, W118 with W118F, merged `1e53314c`).** An item move changes exactly one slot of
  a 27-slot chest, trapped chest or barrel payload (each half of a vanilla double chest counts as one)
  and one slot of the device's four-slot buffer, keeps `CustomName` and `Lock`, and refuses a
  `LootTable` (`UNRESOLVED_LOOT`) and other `ChestBlockEntity` subclasses (`NO_CARGO_STORAGE`). A load
  merges into a container already holding the same item and tags before using an empty slot; an
  unload skips a stack that fails the stack checks. Item preparation uses C-P part 2's tickets and
  the unchanged 16 MiB budget, with a first-step reservation sized for the largest legal container.
- **Item devices (D-A, W120 with W120F, merged `42887305`).** The rocket item loader and unloader
  have a closed `arce_rocket_item_cargo` root (four slots under section 5.3's bounds, a 15-node
  reserve so a filled device can always be bound), an insert-only or extract-only item handler, an
  owner-only menu whose device-slot clicks the server decides and writes through the device first,
  a buffer that spills once and C-A's protection; admission is unwired.
- **Admission path (C-E1, W117 with W117F, merged `cd5cd60d`).** A fluid cargo operation is admitted
  up to an acknowledged PREPARED record behind one switch per cargo kind (all off in production):
  refusals are typed (STALE, ROCKET_LIMIT, ESCROW_FULL, CARGO_TOO_LARGE); the first cargo ledger is
  created through the checked baseline; one fair resource queue serves the fuel loader and the cargo
  devices; RAM-only turn tokens follow loaded devices, and a device yields its own unused token.
- **Reconciliation (C-E2, W124 with W124F, merged `c1b5eab0`).** A cargo device endpoint adapter and a
  one-use device permit; classification by the composite signature, so an AFTER rocket is never
  classified BEFORE; a BEFORE rocket rolled forward through the budgeted replay, verified against its
  frozen digests before either endpoint publishes; off-thread typed readback; R39's native check
  before either side acts. Named (transfer-guarded) cargo stays PREPARED until the F1 fix is extended
  to cargo.
- **Admission.** Cargo admission has its own per-kind switches and stays off until the cargo S2
  evidence, the part-1 closures and the benchmarks pass and the Root or owner decision is recorded
  with the source and the JAR. R-031, R-032 and the Required Gates are unchanged.
- **Packaging of the tank tag (C-P).** One resource set holds every version's generated root, so a
  v1.8 `rocket_movable` file at the sealed v0.5 path cannot merge with it at build time. As for the
  v1.8 tool tags (ADR-063) and the v1.8 gas table (ADR-064 section 5), the v1.8 copy is complete:
  every sealed v0.5 value in its order, then `pressurized_tank`, without `replace: true`. The build
  excludes the v0.5 copy from the runtime resources and the sources JAR; the v0.5 provider and
  output stay byte-identical with their provenance. A test fails if a v0.5 value is missing from
  the v1.8 copy or the packaged JAR holds anything else.

No saved format of a published world changes: the escrow root is unpublished under R-035. This is a
development implementation note, not owner acceptance or a Gate result.

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

### R92 implementation note: the monitoring station on `classic_controls` protocol 2 (Root, 2026-10-24)

Section 4.3 needs a menu intent and a view that the table above does not assign, and W153 stopped at that boundary.
Root decision R92 (QUEUE, TASK-W153) assigns them inside this ADR's accepted surface, as the suit_motion 2 bump of
TASK-W06F did. It changes no accepted behaviour or bound.

- The monitor is a fifth `ClassicControlKind`, `ROCKET_MONITOR`, wire byte 4, on the existing index 0 intent and
  index 1 view. The table keeps its three messages, indices and directions.
- `classic_controls` moves from protocol 1 to 2 with exact-version negotiation, so a protocol-1 peer cannot join
  instead of disagreeing about kind 4. Kinds 0 to 3 keep byte-identical layouts.
- The bounds stay at 512 bytes per intent and 8 KiB per view. The view carries the kind's exact scalar values, a
  status code name and a quote token: no coordinates, inventories, native tags or another owner's rocket position.
  Banks appear as the stable bank role IDs of the flight plan above, with amount, capacity and required debit.
- The intent names the open menu by container ID. The launch action carries the displayed quote's token, so a stale
  view cannot launch. The server creates the fresh request ID and uses the shared flight path. A destination is an
  index into the bounded accessible catalog the menu shows, checked again by the server.
- The malformed-kind tests move their unknown sample to the first unassigned byte (5). They and the protocol pin test
  gain the version-2 cases; no assertion is removed.

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
