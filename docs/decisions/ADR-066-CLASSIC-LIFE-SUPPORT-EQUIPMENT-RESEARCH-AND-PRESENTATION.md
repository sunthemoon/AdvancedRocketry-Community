# ADR-066 - Classic life support, equipment, research and presentation (C18)

```yaml
status: ACCEPTED
revision: 3
date: 2026-10-03
owner: sunthemoon
deciders: [sunthemoon]
target_version: v1.8.0
slices: [C18a, C18b, C18c, C18d]
accepted_at: 2026-10-03
acceptance_basis: owner confirmed D1/D2/D3/D5; independently reviewed revision 3 has no unresolved Critical/High/Medium; conditional authorization permits this contract while its explicit leaf feasibility and runtime admission gates remain required
owner_confirmed_decisions: [D1, D2, D3, D5]
development_dependency: [ADR-024, ADR-025, ADR-034, ADR-037, ADR-049, ADR-050, ADR-051, ADR-052, ADR-054, ADR-057, ADR-058, ADR-059, ADR-061, ADR-062, ADR-063, ADR-064, C17-contract]
revisits: [ADR-025, ADR-051, ADR-057, ADR-059]
supersedes: ""
```

This is an accepted conditional contract, not an implementation result or a Gate
PASS. Numeric choices labelled **proposal** remain leaf review inputs, not
claims about legacy values. Its companion coverage CSV enumerates the exact 104
currently PLANNED C18 ledger units: C18a 28, C18b 23, C18c 19, C18d 34. Asset
coverage is separately expanded with the ledger's ordered first-match rules.
No existing PLANNED, DEFERRED or REJECTED disposition changes through this text.
Revision 2 addressed independent REVIEW-01 (H1, M1-M4). Revision 3 records the
subsequent owner choices: D2 independent ground-owned server survey jobs without
a survey-satellite prerequisite, and D3 preserved world-first Moon/warp milestones
with recorded winning participants. D1's existing 2,000-unit API/HUD buffer with
finite built-in reserves and D5's finite savings/nonzero minimum consumption were
already confirmed. Acceptance does not freeze proposed numeric balance or prove
D4's unprovided durability mechanism. No technical/numeric behavior changes
from revision 2 through this decision-only revision. Independent review and the
integrator acceptance are recorded in the linked acceptance receipt below.

## 1. Sources and boundaries

The pinned Advanced Rocketry MIT commit is
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`. Read-only facts are checked against
`legacy-manifest/java-files.csv`, `legacy-manifest/assets.csv`,
`docs/work/v1.8.0-content-audit.md`, and the approved archive's `upstream/ar/`
tree. `source-files.json` records the exact read files and matching hashes.
LibVulpes is an interface/behaviour dependency, not an approved source: no
LibVulpes implementation, numerical table, GUI, icon, texture or sound is copied.
No Minecraft, Mojang, Microsoft or Forge bitmap/model/sound is copied.

Important legacy facts, not modern requirements:

- Scrubbers damage a carbon cartridge, and two active scrubbers reduce the
  vent's oxygen consumption expression to zero (`TileCO2Scrubber`,
  `TileOxygenVent`). This proposal deliberately retains a finite oxygen debit.
- `TileGasChargePad` stores 16,000 mB of oxygen or hydrogen, fills chest oxygen
  and modular hydrogen tanks, and uses no FE per operation. A refill crosses
  independently saved player and chunk stores; single-tick code alone does not
  prove crash atomicity.
- `ItemPressureTank` doubles its constructor capacity by metadata tier.
  `ItemJetpack` uses hydrogen, accelerates vertically, and reads hover/speed
  modules from helmet inventory, while `ItemUpgrade` permits speed modules on
  legs. The new slot table below removes that inconsistency.
- Observatory shape is five 5 x 5 layers, and its sky observation takes 1,000
  ticks; the processor is two 2 x 3 layers with three old data buses. Three data
  kinds are already replaced by research points under ADR-062; no data-bus
  network returns here.
- `beer` is seat-item use on TNT, not brewing. Legacy Moon and warp custom
  achievements run from rocket/warp authority; two first-event achievements
  use world-global booleans. They are not inventory guesses.
- Existing torch/fire/sleep/spawn/fall/underwater/space-return behaviours are
  described in the content audit section 3.11. The legacy safety return
  selected the *farthest* station; that is not an accepted destination policy.

All new registry IDs use `advancedrocketrycommunity`; C18 translations belong
in `advancedrocketrycommunity_v180` language output, with full keys naming the
actual content namespace. Existing IDs, metadata meanings, player inventories,
SavedData keys and public API binary signatures remain intact unless a precise
migration below says otherwise. There is no direct 1.12.2 world conversion.

## 2. Shared authority, persistence, limits and failure

Use small domain decisions, separate codecs, Forge adapters and physical-client
renderers. Common/server must never reference `net.minecraft.client.*`.
Use the existing atmosphere manager, boundary catalog, suit service, machine
kernel, station services, protection chain and mission registry rather than
parallel truth stores. No static player/world/BlockEntity collection is added.

Every device capability and menu operation revalidates logical-server thread,
non-reentrancy, exact loaded BlockEntity identity, operation state and retained
capability generation. Simulated capability calls are pure. Menus require a
connected real nonspectating player, same Level, at most eight blocks, loaded
chunk and the expected container/type. Generic C2S intents carry fixed operation
enums, container identity and bounded selection IDs, never final resources/NBT.
Defaults: ten menu intents per second per player; 64-byte ordinary intents.
Reject unknown/truncated/trailing fields without mutation or chunk loading.

New device roots are `arce_<registry_id>` with schema 1: at most 64 KiB,
depth 16 and 1,024 nodes, at most eight ItemStack slots, 16,000 mB per fluid
bank and 40,000 FE per bank. More restrictive item or inherited service limits
win. C18 equipment roots are at most 16 KiB/depth 16/256 nodes. Preflight all
owned trees before copying, callback invocation or ItemStack deserialization.
Strict exact field types/keys, known registry IDs, item stack/count maxima and
resource bounds are required. Unsupported/corrupt roots grant no resources and
preserve the original owned tree; ordinary break/explosion/automation/movers
must not destroy quarantined resources. Oversized roots use the reviewed C16
chunk-save veto with dirty retry, not a BlockEntity exception that vanilla may
swallow and omit. Document offline backup/repair rather than erasing data.

Transient caches/queues clear on Level unload/server stop/logout as appropriate.
No offline world/device ticks. Restart tests preserve balances, phase and
inventory, not just successful startup. No generic callback to a foreign tank
is claimed as atomic. Cross player/chunk refills require section 4's protocol.

New COMMON switches are additive: `lifeSupport.classicDevicesEnabled`,
`equipment.classicEnabled`, `research.groundSurveysEnabled` default true.
Disable prevents new work/debit/output but permits safe withdrawal, repair and
completion/reconciliation of already durable transactions. Definitions and IDs
remain registered. No scope is silently deferred by a switch.

## 3. C18a - devices, tools and environment

### 3.1 Scrubber and instruments

- `co2_scrubber`, `carbon_scrubber_cartridge`: one unstackable cartridge slot,
  schema 1 with integer remaining uses, **proposal** 32,766 uses. Associate with
  at most one elected supplied vent by the existing sealed-volume identity,
  never by scanning arbitrary neighbouring rooms. A vent samples at most its
  six adjacent loaded cells; one active scrubber gives one oxygen-debit skip
  every two scheduled debits, two give at most three skips every four. This
  finite-savings policy is owner-confirmed D5; the exact 1/2 and 3/4 schedules
  remain proposed balancing values for scoped contract review. The
  fourth debit remains mandatory. Spending one cartridge use per skipped debit
  and committing phase/use together is required. With no charged cartridge the
  ordinary vent continues its old debit. `scrubberRequiresCartridge=false`
  exempts cartridge use, not oxygen or energy use. Scrubber progress/phase and
  the vent's oxygen debit must have one persistence owner: move scrubber credit
  and cartridge bank into the elected vent root with a generation-scoped facade
  (ADR-019 pattern); breaking/unbinding rules are the C16 controller-bank rules.
  This requires vent schema 1 -> 2, preserving the actual schema-1 oxygen, FE,
  canister counts and oxygen phase exactly. Current HEAD supports schema 1;
  schema 2 is new here, not a pre-existing input whose future meaning is guessed.
  Old vents with no scrubber fields retain baseline supply, and unsupported
  future roots remain preserved/unusable until their explicit migration exists.
  Never save a duplicate cartridge/credit in both chunks. Cartridge refresh is
  a special lossless C16 chemical recipe, not a plain-item output that erases
  arbitrary NBT or creates a second cartridge.
- `atmosphere_detector`: schema-1 selection enum `BREATHABLE`, `VACUUM`,
  `NON_BREATHABLE_ATMOSPHERE`, `HIGH_PRESSURE`, `HOT`, `COLD`. This replaces
  the not-yet-implemented proposal name LOW_OXYGEN: AtmosphereDefinition has
  no oxygen fraction. The reader label means nonbreathable atmosphere, not a
  measured concentration. The already merged tiers follow ADR-062; no
  separate super-high-pressure state or new composition model returns.
  Observe each actual cell through the authoritative atmosphere/BodyContext
  services. For a known supplied sealed cell, the effective classification is
  BREATHABLE=true and all other modes=false (ADR-034 climate-controlled room),
  irrespective of the exterior's climate. Otherwise use the known ambient
  profile's finite pressure/temperature and actual cell breathability below;
  equipment protection does not change the cell. Unknown/PENDING/unloaded or
  unsupported profile cells match no mode and retain UNAVAILABLE/PENDING reason.
  Surface metadata comes from the actual mapped surface's definition; in Space
  (including station regions) use the actual Space Level's ambient definition,
  never the orbited body's surface atmosphere. BodyContext ORBIT names an orbit,
  not air supplied by that planet, and the public API's absent orbit-atmosphere
  payload must not be filled from orbitBody. A supplied room overrides only
  when the manager has a valid known supplied cell; it does not make an unknown
  scan/context valid. Analyzer output separates ambient profile values from
  actual cell breathability/control, so its localized display cannot imply
  surface air exists in orbit.

  | Mode | Exact predicate for a known unsupplied cell |
  |---|---|
  | BREATHABLE | authoritative cell breathability is true |
  | VACUUM | pressure == 0 and cell is not breathable |
  | NON_BREATHABLE_ATMOSPHERE | pressure > 0 and cell is not breathable |
  | HIGH_PRESSURE | pressure > 2 |
  | HOT | temperatureKelvin > 330 |
  | COLD | temperatureKelvin < 240 |

  Thermal/pressure modes may overlap with each other and with breathability;
  240/330 K and pressure 2 are not adverse predicates. Hazard-damage opt-in
  switches do not alter this measurement table. Never infer oxygen concentration
  or effective room climate from a profile name. Every ten ticks inspect only
  the six immediate loaded, non-solid neighbouring cells, signal 15 if any
  matches the selected mode, otherwise 0. Unknown cells are skipped, not treated
  as a positive hazard; if none is known the reason is UNAVAILABLE and signal
  remains 0. Notify redstone only on state change. Fixtures cover every boundary,
  overlapping modes, exterior versus supplied-room classification and unknowns.
- `atmosphere_analyzer`: item-use reads the user's eye cell and authoritative
  surface/BodyContext profile; show localized breathability, temperature,
  pressure and body label with unavailable/pending codes. No target coordinates
  are sent by the client, and no item-held state determines breathable air.
- `seal_detector`: reach at most six blocks; report the actual state-based
  boundary from ADR-024 (`SEALED`, `OPEN`, `UNAVAILABLE`) and whether a supplied
  scanned volume covers the queried adjacent cell. Checking one block does not
  certify a whole room. It triggers no scan/force-load or dynamic capability.

### 3.2 Seals and lighting

- `airlock_door` block/item: an ordinary two-block, iron-like powered door
  with stable lower/upper/open/hinge/powered state; both halves seal only when
  closed. One failed/unloaded half is OPEN/UNAVAILABLE. Vanilla placement,
  neighbour, survival/support, redstone and removal rules apply; toggling
  invalidates volumes synchronously through ADR-024, never grants old-scan air.
  This is one door, not an automatic two-door cycling controller.
- `pipe_seal`: a full sealing cube with six direct capability forwarding faces
  for item/fluid/FE to the opposite immediately adjacent loaded block. A
  transaction obtains one target only; opposite `pipe_seal` refuses forwarding
  and a per-instance guard stops callback loops. Cache no live foreign
  capability beyond its lifecycle, never own a duplicate inventory, and never
  force-load a target. Closed cube seals regardless of a pipe/capability's
  existence. No general pipe network is introduced.
- `thermite` item/common dust tag and `thermite_torch` standing/wall blocks:
  **proposal** shapeless aluminum dust + iron dust -> one thermite; stick +
  thermite -> four torches. Vacuum-compatible light level 14, original art,
  no heat damage/world explosions. `unlit_torch` standing/wall blocks emit
  light 0 and have no survival recipe. Orientation/support stays in BlockState,
  but a small BlockEntity root `arce_unlit_torch` schema 1 also preserves the
  exact original torch-pair ID, block ID and item ID; mounting alone cannot
  distinguish two source torches. Limit that owned root to 2 KiB/depth 8/64
  nodes, each ID 128 UTF-8 bytes, exact known fields/types. It owns no inventory.
- Server tags `combustion_torches` (vanilla standing/wall torches by default)
  and `combustion_fire_starters` (flint and steel, fire charge, blaze rod,
  blaze powder) replace `addtorch` and `torchBlocks`. On placement or a
  subscribed atmosphere invalidation, registered compatible torches in a
  noncombustible eye/adjacent cell become orientation-preserving unlit torches.
  A reviewed data catalog `torch_pairs` schema 1 bounds supported pairs to 64
  definitions, each at most 4 KiB: exact standing block, wall block and source
  item IDs, no duplicate block mapping. The permanent vanilla torch pair is
  built in. Additional pairs must be registered TorchBlock/WallTorchBlock-like
  shapes with the supported orientation properties, no BlockEntity or other
  arbitrary state to lose, and a verified shared drop/place item. A tag alone
  is not permission to deserialize a foreign block or invent its drop identity.
  Unsupported tagged torch blocks refuse placement instead of guessing their
  orientation/data. Thermite torches are never in this tag. Existing-world
  torches are reconsidered on relevant block/volume events, not an unbounded
  Level scan. A fair pending torch queue is capped at 8,192 positions/Level,
  256 checks/tick; overload is observable and fails new placements closed.
  No arbitrary block removal by a client request. `dropExtinguishedTorches`
  default false: true removes once and drops one original torch; false keeps
  one unlit block dropping that exact saved source item when broken. Relighting
  restores the pair's exact standing/wall block in the saved orientation, not
  a vanilla substitute. Preserve wall support. Removed/unknown catalog identity,
  future/corrupt root or incompatible replacement properties quarantine the
  original owned tree: refuse ordinary break, relight, movers and explosions
  until the matching pack/backup or an explicit offline repair restores it.
  Never convert a missing item to air. Placement/extinguish/relight/drop changes
  use the protection policy and applicable cancellable Forge block-change
  events; cancellation keeps original block/item and emits no drops. Freeze the
  environmental-change adapter under ADR-054 before runtime, without an
  operator FakePlayer bypass. Two distinct compatible source pairs with the
  same orientation must round-trip independently through save/restart/break and
  relight, with cancellation and removed-pair fixtures.
  Relighting with a validated fire starter requires combustion air and normal
  permissions. Suppress fire-starter right-click at the actual adjacent loaded
  cell when noncombustible/PENDING; do not consume the held item/durability.
  Existing arbitrary fires/lava/lightning are not globally simulated/extinguished.

### 3.3 Actors, gravity, sleep and safety

- A non-player `LivingEntity` reuses the atmosphere/exposure pure decisions,
  with no suit oxygen unless supported real equipment is defined explicitly.
  A server entity-type tag `atmosphere_bypass` exempts these atmospheric
  effects, not gravity, fire, drowning or unrelated damage. Initial tag is
  empty; modpacks can nominate their actual vacuum-adapted types. Apply at most
  one exposure attempt per 20 entity ticks, using ADR-034 priority/opt-in and
  no double suffocation/heat attempt. Use entity lifecycle fields or bounded
  service state; never scan all entities/chunks as a separate pass.
- Natural/spawner spawn checks for tagged bypass entities pass this policy;
  otherwise deny at an unavailable/PENDING/nonbreathable spawn eye cell or
  an opted-in hostile profile unprotected by a supplied sealed room. Ordinary
  biome spawn tables still choose candidates; no additional global spawner.
  Explicit command/spawn-egg exceptions must be tested and documented, not
  represented as natural-spawn acceptance.
- Extend `CelestialGravityController` to all living entities through Forge's
  gravity attribute: players retain field -> station position -> Level order;
  non-players use Level gravity only, because area fields/station overrides on
  non-players remain deferred by ADR-062/058. Missing gravity attributes are
  skipped, not coremodded. Apply one transient UUID modifier, clear at multiplier
  1, preserve other mods' modifiers. Fall event uses the same resolved ARCE
  multiplier once: distance * multiplier, finite/clamped to existing body
  limits before vanilla damage; padded boots rule follows section 5. Never
  multiply by the complete attribute again (double application).
- `vacuumDamage`: COMMON finite 0..20 health points per scheduled second,
  **proposal** default preserve the present engine's damage, not legacy's 1.
  Zero disables vacuum damage only, not consumption, breathability or exposure.
- Sleep denies at the sleeping player's actual unbreathable/PENDING cell on
  non-Overworld mapped surface/Space; `forcePlanetRespawn` is a respawn option,
  not permission to sleep without air. Existing Overworld bed rules remain.
  `allowPlanetRespawn` default false, `forcePlanetRespawn` default false:
  validate a real saved bed/anchor, destination profile and safe loaded arrival;
  if allow is true, accept only breathable destination unless force is true.
  Force bypasses air only, never solid/liquid hazards, station permission,
  missing body or unloaded unsafe arrival. Fallback is the existing safe
  Overworld respawn, not an arbitrary player-provided coordinate.
- Full recognized equipped suits with a successful oxygen debit may restore
  vanilla underwater air. The debit is once per 20 submerged ticks and shares
  the existing oxygen cadence; vacuum + underwater must not debit twice.
  Empty/unsupported suit grants no underwater breathability. Native water
  breathing effects and aquatic mob behaviour stay vanilla.
- Space safety return: only an actual connected player outside all committed
  station regions and not in a rocket transfer/elevator ride transaction is
  eligible. After 100 consecutive ticks adrift, try the nearest *authorized*
  station safe arrival using VISIT authority, not the legacy farthest policy.
  The present registry only has occupied-cell findAt and a name-sorted 32-entry
  access list; neither is a nearest query. Add a small lifecycle-owned reverse
  owner/member -> station-ID index as part of this slice: at most 4,096 stations
  with 32 members each, 131,072 member links plus one owner link/station. Update
  it only after committed create/team/remove changes; rebuild from bounded
  registry records at startup, not on every player query. Operators use the
  bounded all-station ID index. Do not use a 32-name prefix as the candidate set.
  Nearest means squared horizontal distance from the player's captured Space
  position to station safe arrival, ties by station UUID; it considers every
  authorized candidate, but only already loaded safe arrivals. No new station
  ticket/force load, no outsider access. A resumable fair query has one job/player,
  at most 64 jobs, 32 candidate inspections/job/pass, 128 inspections and 256
  loaded-cell safety probes/server tick globally. Retain a stable index cursor
  and best candidate until the entire bounded candidate set is examined; do not
  return the first safe prefix or run a 4,096-record synchronous retry. Existing
  safe-arrival checks must be interruptible inside the probe budget; an unsafe
  or unloaded destination is skipped without world access that loads chunks.
  Index/access generation changes invalidate the selection; revalidate VISIT,
  body/profile, loaded safety, same Level and still-adrift eligibility at commit.
  Distance is measured from the captured query-start position; ordinary drifting
  does not restart every tick, but entering a committed region cancels the job.
  If the best
  destination changed/unloaded, resume a fresh bounded pass; if a complete pass
  finds none, use safe Overworld return. Queue delay after 100 ticks is observable,
  not an instantaneous-nearest claim. Unknown/blocked registry cannot authorize
  a station or be treated as an empty successful query.
  Rate at most one attempt/player/200 ticks. Unknown/blocked station registry
  cannot authorize a station. Clear phase on Level change/logout/vehicle/return.
  Coordinate selection, protection/TELEPORT event and transfer lifecycle use
  existing server travel services; announce destination/refusal in translations.

## 4. Gas charging and finite player resources

`gas_charge_pad` stores one oxygen or hydrogen bank up to 16,000 mB, accepts
the corresponding C16 canister/fluid routes, and charges a real player standing
on its platform (1 x 1 x 2 AABB) at most 100 units per operation/20 ticks.
Choose one eligible player per pad pass in UUID order, and globally at most
32 pad attempts/tick with a fair resumable queue. No FE is required (legacy
behaviour); redstone/config pauses. Oxygen conversion remains C16's canister
mapping, not an invented 1,000x exchange. Output is an actual worn, supported
chest's active/installed oxygen reserve, or hydrogen tank; no arbitrary
ItemStack capability callback. Exact item/gas identities and room revalidate.

The existing single-tick whole-canister refill is not promoted to an S2 claim
for a pad. **Proposed crash-safe protocol**: pad debit/outbox at source ->
durable chunk Save/Load observation -> worn chest credit/receipt -> player
file save observation -> source acknowledgment. A UUID/revision transaction,
owner UUID, selected gas and amount are immutable. Source retains at most eight
outboxes, equipment at most eight receipts; full sections stop charging. The
player action is blocked while pending/unreconciled. Only the original saved
player/pad identity may reconcile. A receipt ahead of source recovers source
debit without re-credit; an outbox ahead of receipt credits once after source
durability. No source-only delete/retry window may remint gas. No carrying,
replacing, workstation extraction or ordinary destruction of pending equipment
or source is allowed until reconciliation; no chunk is force-loaded for it.
Inventory identity changed -> hold/quarantine and operator inspection, not
write a different stack. Disconnected players wait for login. Receipts cannot
be pruned until acknowledgment is durable at source. Unsupported player roots
retain data and refuse writes. Source/player pre-/post-save crash cuts must
prove conservation; a save hook that only calls ItemStack.save is insufficient.

**Feasibility gate**: the exact Forge 47.4.10 player-file save observation,
durability ordering and raw-data preservation mechanism must be demonstrated
and independently reviewed before implementation. If this cannot be established
without an approved lifecycle/AT exception, bring the owner a revised protocol
(e.g. finite canister exchange on the pad then player-local canister refill),
with changed interaction and conservation claims. Do not ship automatic pad
charging while describing it as crash atomic without evidence.

## 5. C18b - equipment and equipment schema

### 5.1 Slots, capacities, workstation and enchantment

`suit_workstation` has exactly one armor slot and one module transfer slot;
module layout is fixed: HEAD two shared visor/hover/beacon-finder positions,
CHEST one jetpack and
two pressure-tank positions, LEGS two speed/bionic positions, FEET one padded
position. No upgrade stacks; at most one of each upgrade type, one oxygen and
one hydrogen tank. Workstation menu only, no automatable armor-edit capability.
Insertion/extraction moves the actual component's bounded owned NBT; tank gas,
durability, unknown unrelated armor tags/enchantments are preserved. Incompatible
slots/full player inventory/pending transaction/invalid root reject without
changing either stack. Vanilla shift-click is tested against detached copies;
commit through an authoritative install/remove method, not setChanged alone.
One armor item schema owns both installed modules and gas; there is no second
persisted mirror in the workstation. Breaking workstation drops the sole held
armor/module once. Old built-in suit IDs/data remain valid without modules.

C17's stable `beacon_finder` occupies one of those existing two HEAD slots;
at most one finder, no new slot and no duplicate C18 registration. Choosing
finder/hover/two visor types means not all effects fit simultaneously. Publish
only immutable server equipment-summary `beaconFinderEnabled` to the C17 finder
adapter, not nested finder NBT or a client claim. C17 owns waypoint policy and
held-item/finder selection authority. Freeze the shared bounded module-kind
table and summary port in model/api under root integration; the two adapters
consume it without importing one another or forming a C17/C18 dependency cycle.
Full-slot rejection, install/remove, equipped versus held detection, restart and
summary removal on unequip are required interoperability fixtures.

Stable IDs: `low_pressure_tank`, `pressure_tank`, `high_pressure_tank`,
`super_high_pressure_tank`, `jetpack`, `hover_upgrade`, `flight_speed_upgrade`,
`bionic_leg_upgrade`, `padded_landing_boots`, `anti_fog_visor`,
`earthbright_visor`, `jackhammer`, `basic_laser_gun`, `space_breathing` enchantment.
Pressure tank schema 1: selected gas `EMPTY|OXYGEN|HYDROGEN`, integer stored
amount, snapshotted maximum; **proposal** tiers 1,000/2,000/4,000/8,000 units.
Empty tanks acquire gas from charge only; nonempty tanks cannot switch gas.
No world fluid capability is exposed directly on equipped nested components.

**Owner-confirmed decision D1**: retain ADR-025's providers/HUD/active oxygen
0..2,000 and built-in data on its original key; add a built-in-only finite
auxiliary tank schema. The generalized provider-capacity/API alternative was
not chosen and is outside this implementation. Replenishment is a lossless
one-item commit moving at most the exact missing active amount from the single
installed oxygen reserve, with source debit and active credit in the same
detached ItemStack owned payload (existing active key plus new module root,
neither mirrored into the other). Refill runs only before an authoritative
scheduled oxygen debit or an explicit valid refill, at most one bounded
2,000-unit transfer/stack/operation; simulated/query/HUD calls never mutate it.
Active <= 2,000, auxiliary <= its saved supported maximum;
never mirror auxiliary gas in the active balance or bypass a scheduled debit.
Under the proposed tier table, default maximum combined oxygen is 10,000
(2,000 active + one 8,000 tank); two oxygen tanks are forbidden. The proposed
capacity multiplier permits an absolute 32,000/tank and 34,000 combined oxygen,
still finite; this equipment-specific saved capacity is not a device fluid-bank
extension or an external provider allowance. `spaceSuitO2Buffer` becomes
built-in active maximum in
units 1..2,000, default 2,000; `suitTankCapacity` becomes auxiliary tier
multiplier 1..4, default 1, snapshotted per created tank. Lowering configuration
does not discard previously stored gas: existing maxima remain snapshot values,
new auxiliary fills stop at the supported saved maximum. Lowering the active
target stops refill above that target but does not erase a valid pre-existing
0..2,000 balance. Refuse unsupported saved maxima rather than clamping away gas.
Numeric tier/config choices require scoped contract review; D1 confirms the
mechanism, not a generalized API migration or every proposed balance value.

`space_breathing` level 1 is applied only by the C16 chemical recipe, not
enchanting tables/books or a generic unrestricted enchanting loot. It seals
correctly worn armor across four slots but does not provide infinite oxygen or
climate/pressure/solar protection. Only CHEST owns finite oxygen, at most 2,000
under D1's selected active-buffer policy; use the existing detached-owned-payload
commit principles. Existing registered external providers still use ADR-025 and take
precedence; enchantment cannot override/alias their owned root. Proposed private
host eligibility extension: count an otherwise unclaimed native ArmorItem only
when unstackable/count 1, in its native slot, and actually enchanted at level 1;
eligibility is checked for each live worn stack, not an unconditional static
item-to-slot registration that would seal all unenchanted armour of that ID.
The enchanted chest owns only `arce_enchanted_suit_oxygen`, strict schema 1 with
oxygen 0..2,000 and the same bounded atomic detached commit/debit/refill checks.
Other owned item NBT is untouched. No module/jetpack compatibility with arbitrary
enchanted third-party armour is promised; those need their explicit extension.
Review this eligibility extension as an ADR-025 additive revision; the public
function signatures and external 2,000-unit bound do not change. Dynamically
registering providers per enchant or claiming already mapped items is prohibited.
Built-in oxygen root is not rewritten. Enchanted unsupported/corrupt chest data
is unusable/preserved.

### 5.2 Motion and sensory upgrades

- Jetpack off by default. Toggle key submits `TOGGLE` or `CYCLE_MODE`; held
  thrust submits a boolean heartbeat, never velocity/height. Rate 10 packets/s,
  server intent expires after five ticks without heartbeat. NORMAL/HOVER
  mode is an enum; HOVER requires `hover_upgrade` installed at HEAD. Require
  real player, valid correctly worn built-in equipment, live same-server state,
  no elytra flight/rocket seat/elevator transit, and finite hydrogen. **Proposal**
  spend 1 unit before each thrust/hover correction; thrust increment
  `0.08 * jetPackForce`, force config 0.05..4, default 1.3, vertical velocity
  cap 0.6 blocks/tick and horizontal cap 0.8. Hover reference height is
  transient and resets on mode/Level/logout/equipment change. Never toggle
  vanilla creative flight flags or apply client-calculated velocity. Gravity
  above available thrust can overcome the jetpack; it is not free flight.
- `flight_speed_upgrade` at LEGS gives **proposal** 20% horizontal thrust
  assistance while actively paid jetpacking, using server motion bounds;
  `bionic_leg_upgrade` at LEGS gives a transient +20% movement-speed modifier
  while sprinting, removed immediately when unequipped/disabled. No reflective
  mutation of player capabilities; preserve other mods' attribute modifiers.
- `padded_landing_boots`: **proposal** cancel fall damage while worn correctly;
  `lowGravityBoots=true` narrows this to ARCE multiplier < 1, default false.
  Do not fake client fall distance or cancel every damage type.
- `anti_fog_visor`: removes only ARCE atmospheric fog/nausea visuals, never
  vanilla blindness/potion fog or server hazards. `atmosphericNausea` CLIENT
  setting default true controls only this modulation. Fog uses synchronized
  bounded density/profile, never extrapolates unknown profiles as safe air.
- `earthbright_visor`: physical-client brightness/night-vision-like rendering
  in the actual Space Level only; no potion effect renewal that survives
  removal or interferes with real night vision. Both visor effects clear on
  resource reload/world change/equipment snapshot invalidation.

### 5.3 Mining tools

`jackhammer` is a vanilla-authoritative single-block pickaxe-like tool,
**proposal** mining speed 50 on its mineable tag, diamond-equivalent required
tier, durability 1,024, repair by titanium rods. No blanket canHarvest=true,
AOE block pass, protected breaking bypass or tile-NBT copying.

`basic_laser_gun` item-use or fixed empty laser intent traces from the server
player's current eye/look, at most 50 blocks, checking loaded chunks before
each ray segment. No unloaded segment is queried via a loading world API.
**Proposal** one held target at a time, 20 valid continuous-use ticks to break
one eligible block, cooldown five ticks, durability 512/one use per break.
Reset on target/state/item/range/Level/player changes or use-end; cap tracked
users at actual connected players and clear on logout. Zero-hardness blocks
can break after one tick; negative/unbreakable/unsupported block entities
refuse. Run ADR-054 protection, Forge BreakEvent, actual tool harvest/loot and
station BUILD policy before breaking through the ordinary server player path;
event cancellation means no damage or mutation. Entity-hit branch attempts
one point of damage with vanilla interaction/line-of-sight/PvP/protection
checks, no mining at a hidden block behind that entity. Do not replay rewards
after cooldown; packets contain no target position/entity/damage/drop list.

## 6. C18c - ground research and advancements

### 6.1 Observatory and processor

Both use ADR-016 bounded data patterns/four rotations, not copied legacy classes.
`observatory`: five 5 x 5 layers, own controller, casing, C16 motor, tower and
lens/glass cells; `astrobody_data_processor`: two 2 x 3 layers, controller,
slabs, casing and kernel power/input/output equivalents. Freeze the exact
cell map as reviewed `machine_patterns` fixtures before formation code.
Only controller banks persist (ADR-019/C16); no imported data bus requirement.
Per-Level matching/revalidation and loaded-cell budgets are inherited, not
a new synchronous scan. Menus show missing cell/sky/energy/owner/data status.

**Owner-confirmed decision D2 / selected extension**: a ground survey is an additive
owned registry job, not a fake launched satellite and not an unbounded item
chip bearing minted yields. Extend the existing satellite registry schema
3 -> 4 while preserving every satellite, mission, instance and research
account's identities and resource values. Instance schema 1 -> 2 changes only
the source representation as specified below; saying its serialized records
are unchanged would be incorrect. A new `ground_surveys` section owns job
identity, owner,
source device UUID, source Level/position, source system, definition/table
fingerprints, seed, bounded snapshotted candidate/yield, timestamps, phase and
analysis-credit state. Ground surveys count inside the existing 16 live
instances/owner and 2,048 total instance limits, not beside a second allowance.
At most one live job/controller, 16 jobs/owner and 2,048 jobs/global, each
4 KiB; source directory indexes lifecycle-bound and bounded. Ground-job section
budget is **proposal** 1 MiB and remains inside the existing 16 MiB whole-root
preflight; whichever record/byte/node bound is reached first refuses admission
before mutation. It does not raise any existing satellite section limit or
silently drop old records. At most 32 paid advances and 64 queue inspections
per server tick, fair across Levels; excess controllers show QUEUED. Progress
mutations use the existing coalesced flush budget, not one file save per tick.

**ADR-051 source amendment under selected D2:** replace the mandatory ambiguous
instance `sourceMission` with a bounded tagged origin, `SURVEY_MISSION` or
`GROUND_SURVEY`, plus exactly one UUID. Instance schema 2 serializes exact
`origin_kind`/`origin_id`; there is no nullable UUID, invented MissionState or
placeholder satellite. Schema-1 instances migrate their original source UUID
to SURVEY_MISSION and preserve instance/owner/system/type IDs, seed, versions,
fingerprints, full yields, timestamps, state, expiry and allocatedMission.
The registry 1/2 -> 3 existing migration runs before 3 -> 4; already-current
registry/instance inputs are a no-op. Snapshot all current per-record budgets
before admission: the new tagged envelope still fits each 4-KiB instance bound
and the root's existing byte/node/section bounds, or start is refused. Unknown
origin kinds/types and future schemas are raw-preserved, not downcast.

The revised pure invariant plan checks origin and mining allocation separately:

- PENDING/SURVEY_MISSION requires the existing unfinished SURVEY MissionState,
  its exact payload backlink and matching owner/system; every survey payload
  ID in turn requires that exact pending mission-origin instance. Do not relax
  existing orphan/satellite/mission checks to permit ground jobs.
- PENDING/GROUND_SURVEY requires one ACTIVE job whose UUID equals origin_id,
  whose instanceId equals this exact instance UUID, and whose owner/system and
  snapshotted source definitions agree. Every ACTIVE ground job must in turn
  resolve its exact PENDING ground-origin instance. A duplicate source/backlink,
  wrong owner/system/kind, missing job or mismatched ID quarantines affected
  raw records without payment, deletion or global acceptance of broken roots.
- Completing one ground job atomically changes instance to AVAILABLE and job
  to READY with its single analysis credit. READY/PROCESSED jobs retain the
  immutable origin/backlink while the corresponding instance is retained.
  For AVAILABLE/ALLOCATED/retained DEPLETED or EXPIRED ground instances the
  matching READY/PROCESSED job must exist; ALLOCATED additionally retains the
  existing one-to-one unfinished mining mission/allocatedMission checks.
- Mission-origin AVAILABLE/ALLOCATED/terminal records keep their exact original
  origin UUID even if the finished source survey was pruned under existing
  retention. Do not newly quarantine these valid old records merely because
  a terminal survey mission is absent. Mining allocation remains independently
  checked regardless of either origin kind.
- Cancel ACTIVE only by one checked registry mutation removing its exact
  matching PENDING instance and setting its job CANCELLED with no resource
  award/refund. Preserve a bounded cancellation tombstone at least the existing
  1,200 logical-tick retention; READY/PROCESSED origin jobs cannot be pruned
  before their retained instance and all allocation/claim references are gone.
  Instance terminal retention otherwise remains ADR-051. Prune deterministic
  bounded records, never delete a live source to make room for new work.

Required fixtures include legacy PENDING/AVAILABLE/ALLOCATED and terminal
schema-1 migrations, ground PENDING save/restart, exact backlink permutations,
duplicate/wrong-owner/wrong-system/wrong-kind origins, pruning/cancel/replay and
old source-survey-pruned AVAILABLE/ALLOCATED records. D2's branch is owner-selected;
the precise ADR-051 amendment and its implementation still require their scoped
review and migration evidence. This is not model code or runtime admission.

On authorized START at a formed loaded observatory, derive the actual surface
system (or permitted station's orbit system), current asteroid candidates and
seed. Reserve one PENDING instance and its job in one registry mutation with
the existing persistence barrier; registry root failure means no start.
**Proposal** advance 1,000 paid loaded ticks at 20 FE/t, while night/no rain
and the single loaded lens column has sky visibility, or while in an actual
permitted station orbit. Missing body/table/sky/unloaded cell/full power pauses;
no offline scan, physical asteroid/forced chunk/dimension allocation. Progress
belongs to the registry job; consuming controller FE has the existing machine
FE non-crash-atomic limitation, not an item/fluid duplication claim.
At completion, one registry mutation sets instance AVAILABLE with ADR-051 TTL
and sets its analysis available. The user's logical asteroid instance becomes
visible through the existing terminal/mission interface. Completion/reload
must never duplicate an instance, even after controller copy/rebind or job
lookup retry. Breaking the device does not refund paid FE or duplicate an
instance; owner cancellation removes only PENDING and follows existing
instance transitions once AVAILABLE/ALLOCATED.

The processor selects the owner's completed unprocessed survey job through a
bounded server-held list, not a client-provided yield or research number.
Its analysis subphase is WAITING/RUNNING/CREDITED within the READY/PROCESSED
job, with one claimed processor UUID/Level/position and bounded paid progress;
no second processor can advance/claim it. Job lifecycle remains ACTIVE, READY,
PROCESSED, CANCELLED or QUARANTINED, so running analysis does not make a live
ground-origin instance look orphaned. Rebind/claim changes require an explicit
owner-authorized checked mutation and cannot reset paid work into new credits.
**Proposal** 200 loaded ticks/20 FE/t, then credit 120 owner research points
and mark that job PROCESSED in the *same* registry mutation. Account overflow
pauses without credit/state loss. Replaying the selection returns ALREADY_CLAIMED.
No physical certificate consumption crosses chunk and SavedData files; analysis
is a registry record, matching ADR-062's replacement of three data kinds.
This does not discover a celestial body or bypass ADR-037 discovery cost;
discovery remains the data-satellite workflow. Analysis on a ground-survey job
does not change yield of an already allocated asteroid. Jobs' claim tombstones
remain while their instance/reference is live; only deterministic registry
retention after all references are gone can delete them under the origin rules
above. Controller copied from NBT at a different position cannot claim an
existing job; same UUID collision
quarantines instead of stealing ownership. Schema-3 input migrates instances
losslessly and initializes no ground jobs; future/malformed input refuses and
preserves the whole registry. Old schema-3
host rejects schema 4, so downgrade restores pre-upgrade backup/matched build.

The unselected D2 alternative is a registry-authoritative survey service requiring
an owned survey satellite at the observatory. The owner chose independent ground
jobs without that prerequisite. Switching to the satellite-required branch would
change the selected availability/tech path and require a new owner decision;
it is not an implementation fallback. Observatory and processor remain in scope.
D2's precise ADR-051 revision/extension review remains an implementation input.

### 6.2 Advancement identities and triggers

All stable IDs below are under `advancedrocketrycommunity:classic/`; DataGen
builds a visible tree with translated title/description and already owned icons.
Advancements are tutorial feedback, not additional research costs or recipe
locks. Existing root/advancements remain registered; no inventory is rewritten.
No console/grant statement is accepted as the behavioural test for a trigger.

| Legacy advancement | Stable suffix | Server-authoritative trigger |
|---|---|---|
| root | root | obtain a vanilla crafting table |
| blockpresser | block_press | obtain the C15 small plate press |
| rollin | rolling | obtain existing rolling machine |
| electrifying | electrolysis | obtain existing electrolyzer |
| feeltheheat | arc_furnace | obtain C16 electric arc furnace |
| spindoctor | lathe | obtain C16 lathe |
| crystalline | crystallizer | obtain C16 crystallizer |
| dilithium | dilithium | obtain the exact C16 dilithium crystal |
| holographic | formed_multiblock | first actual formed classic multiblock, not a removed projector |
| suitedup | suited_up | acquire all four correctly named built-in suit pieces (matching old inventory goal) |
| warp | warp_core | obtain existing warp core |
| beer | seat_on_tnt | valid seat-item right-click on TNT; no explosion or item consumption created by trigger |
| moonlanding | moon_landing | committed rocket descent/landing on Moon with player aboard |
| onesmallstep | one_small_step | participant in first committed world Moon rocket landing |
| wenttothemoon | moon_surface_visit | actual player's first Moon surface arrival; no deferred lander proximity |
| givingitallshesgot | station_warp | player present in authorized station at committed warp |
| flightofpheonix | first_station_warp | participant in first committed world station warp |

World-first flags need versioned world state, not static booleans. **Owner-confirmed
decision D3:** retain world-first Moon/warp semantics with recorded winning
participants and the following commit-coupled receipts. Per-player first events
were not selected; changing to them would require a new owner decision and
published player-impact revision. Other advancements are unaffected.

**Selected world-first design, implementation still gated:** add strict bounded schema-1
`advancedrocketrycommunity_classic_progression` SavedData containing two event
receipts. Each records kind, immutable source transaction UUID, source identity,
occurred flag and participant UUIDs: at most 4,096 participants/receipt and
256 KiB/depth 16/16,384 nodes for the whole root; existing lower passenger/server
bounds still win (rocket passengers remain <= 16). This is an acknowledgment and
login-grant store, not a second authority allowed to elect a different winner.
A post-travel callback alone is insufficient because its SavedData may not
be durable when the travel source already is. Each source therefore owns one
durably retained first-event slot, committed with the actual event:

- Station warp: a bounded `classic_first_warp` source outbox in the station
  registry root, not in the 8-KiB individual StationState. Include its immutable
  event/participant record in the *same* CheckedSavedDataFile candidate,
  fsync/replace and publish as checkedRelocation's orbit and FE debit. Compare
  the first-event slot as empty inside that checked update; no later warp can
  win while it contains an unacknowledged or acknowledged event. Join C17's
  station registry 4 -> 5 migration once, not an unrelated second root bump.
- Moon rocket landing: one `classic_first_moon_landing` source outbox in the
  rocket-transfer journal root, not only a landed Entity/player callback.
  Extend C17's journal 2 -> 3 migration once with a checked landing-completion
  authority: validate safe actual descent completion and snapshot its terminal
  rocket/transfer identity and participants, then persist that completion and
  the empty-slot election in one checked source replacement before publishing
  LANDED/advancement notifications. Recovery projects an already committed
  terminal completion to the unique authoritative rocket if its chunk state
  lags. A journal COMMITTED transfer phase is not itself a Moon landing and
  cannot trigger the award. A failed source write leaves descent completion
  pending/repair-required, not a successful landing with a forgotten receipt.
  Never discard/supersede the only completion authority before the terminal
  entity state is reconciled and the source first-event marker is durable.

Each source outbox is schema 1, at most 128 KiB/depth 16/8,192 nodes, inside its
existing whole-root byte/node budget (station 4 MiB; journal uses its current
fixed RocketFlightLimits bound). It does not raise those budgets. Reserve
headroom before accepting an eligible first-event request or taking resources;
refuse over-budget participant/root state visibly, not partial awards. Capture
the actual real connected passengers aboard the unique authoritative rocket at
the checked Moon completion, or actual real connected players within the
authorized committed station region with live VISIT permission at checked warp
completion. Login recovery uses that immutable set, not later joiners or all
members. Preflight request
maximums and recheck the exact completion set; an empty set still records that
the first committed event occurred, without inventing recipients.

The source journal's exact checked writer and its coordination with ordinary
SavedData/chunk saves must be frozen and independently reviewed before runtime.
setDirty(), ChunkDataEvent.Save or ItemStack.save is not a durability barrier.
Ordinary saves must serialize the latest acknowledged source authority and may
not overwrite a checked first-event commit with stale memory. Station checked
write failure/outcome-unknown semantics remain unchanged; journal outcome
unknown similarly blocks conflicting completion/election until durable reload.
This proposes that adapter work explicitly; it does not claim current journal
put/remove methods already provide it.

The progression receipt likewise uses a reviewed checked SavedData write/reload
barrier for its source acknowledgment, not an in-memory setDirty observation.
Its new managed type/key and strict schema participate in the existing migration
catalog under root ownership, without changing external environment APIs.
Delivery is source durable event -> durable matching progression receipt ->
source acknowledgment. Until acknowledgment is durable, keep the complete source
event and its participants plus an immutable participant-set fingerprint; no
timeout/entry pruning can erase the recovery information. After acknowledgment,
keep the immutable occurred/transaction
marker forever and permit participant payload compaction only because the
progression receipt is itself durable. Missing/mismatched/quarantined progression
data holds source events and awards for repair, never elects another first event.
Grants at login are idempotent while the player's advancement file lags.
Canceled/failed travel before source completion does not occupy the winner slot;
an outcome-unknown write is reconciled from its actual durable source, never
blindly retried as a new transaction. Both write orders and cuts after source
commit/before progression flush and after progression commit/before source ack
must prove the same winner/participants after restart. These are required S2
fixtures, not yet executed evidence.

Historical initialization uses only trustworthy durable committed Moon *rocket
landing* or station-warp source markers. A surface visit, inventory, current
orbit or console grant is not a historical first-event record. A trusted marker
without participants initializes occurred=true without retroactive awards;
worlds with no trustworthy marker await a real event. Old schema-2 journal and
schema-4 station roots initialize no fabricated source event, preserving their
existing recovery/balances; migrate settled old transfers under C17's old
checksum-first rules. Future/invalid source/receipt roots are raw-preserved and
unusable until repair. D3's product branch is owner-confirmed; this durability
contract/writer review still precedes implementation. Native writer/restart proof
is not provided by the owner decision receipt.

### 6.3 Reachability and player guide

Extend the C16d recipe graph, not an item-only count. Ground-survey analysis
edges require reachable observatory, power, relevant surface/station access
and processor; discovery retains its data satellite/access/fee prerequisites.
Generate a report with minimal reachable route for every registered survival
item, required fluids/energy/body access/research, cycles and audited technical
exemptions. `unlit_torch`/effect-only IDs are explicit exemptions; equipment,
ground research machines and all ordinary recipe outputs are not exemptions.
New-world fresh-overworld playthrough must obtain life support, a Moon rocket,
surveys, industrial circuits and endgame machines without admin/other-mod FE.
Client JEI presents the actual graph recipes with supported NBT operations and
failure prerequisites; with JEI absent all recipes and progression still work.
Player guide explains actual steps, not internal ADR/review task descriptions.

## 7. C18d - presentation, assets, language and configuration

### 7.1 Runtime presentation contracts

- Register real SoundEvents for all ten planned ledger IDs: `air_hiss_loop`
  (supplied vent), `basic_laser_gun`, `combustion_rocket`, `electric_shock_small`,
  `gravity_controller`, `laser_drill`, `lathe`, `machine_large`, `railgun_bang`,
  `rolling_machine`. Non-silent legacy OGG is eligible only after per-file
  authorship finding; absence of a Vorbis author comment is not a clearance.
  `lathe`/`rolling_machine` may share one approved OGG without duplicating
  their event IDs. Five rejected silent placeholders remain rejected; do not
  count dummy.ogg or a silent NEW replacement as functional audio delivery.
  Actual loop start/stop/pause follows bounded S2C active state, clears on
  unload/config/resource reload, and runs only in client distance range 32.
  At most 32 simultaneous ARCE loops/client, prioritizing nearest sources;
  never send a packet every audio frame. NEW synthesis/recording is allowed
  with pre-authoring provenance, an audible waveform check and manual listen.
- Use approved OBJ/MTL through Forge model loader only in physical-client
  code. A resource-reference audit resolves every material and texture;
  fallback NEW/project-owned texture is explicit, not a purple missing texture.
  Each model fixture freezes orientation, scale, rendering and bounding volume
  without changing collision or machine match. Budget **proposal** at most
  65,536 vertices per baked object and 262,144/16 MiB per cached model set;
  fail malformed oversized geometry/resources before allocation, preserve
  last-good cache on resource reload failure, clear prior resources on success.
  No runtime source path/file download or arbitrary untrusted URL is accepted.
- Black-hole sky is a bounded per-station/sky-profile physical-client feature,
  selected by server-approved body/station context: the committed station's
  live orbit body must have a currently valid ADR-057 singularity profile.
  A server-generated fixed sky-kind enum in the existing station snapshot
  supplies this distinction. Coordinate one station sky payload schema 1 -> 2
  containing this C18 sky-kind and C17's distance/phases/rates, and one
  `celestial_snapshot` channel protocol 3 -> 4 in the frozen integration.
  C17 separately owns `rocket_flight` channel 8 -> 9; C18 does not bump it again.
  Validate fixed known sky-kind, bounded orbit fields and unknown/trailing data
  in the same payload; no independently versioned parallel sky packet. The
  client does not infer it from a body name, gravity or fuel/output value.
  Do not add an inferred
  black-hole star flag to the celestial model without its own ADR. Use one
  dark disc plus bounded accretion/ring mesh at most 512 vertices, eight
  particles/tick maximum and no block/entity attraction or server FE changes.
  It supplements ADR-057's controller effect, clears on context change and
  respects the station sky switch. Unknown context uses existing fallback.
- Elevator capsule is a client-only effect ID `elevator_capsule`, not a new
  persistent ride Entity. Draw against existing endpoint/pair/countdown S2C
  lifecycle, at most one effect per rider and 64 visible endpoints; lease at
  most 300 ticks, clear on cancel/commit/disconnect/Level change/unload. No fake
  passenger/collision/player authority, no second teleport or reward. The
  server ride remains ADR-059; the visual cannot claim gradual physical
  passenger travel where the actual ride is a countdown then committed teleport.
- Equipment component rendering uses only bounded immutable S2C module/gas
  summaries (no arbitrary equipped NBT). Render actual jetpack/tank/visor
  mounts with NEW or approved geometry, at most eight attachments/player and
  tracked-player vanilla distance culling. No negative armour-body scale or
  orphan cached player object across logout.
- Rocket HUD shows only existing synchronized flight/fuel/route/status data;
  life-support/H2/environment/suit panels use authoritative snapshots. HUD
  cannot predict remaining gas as a server resource. Values widened from short
  menus use reviewed split/full-width wire representation, not signed truncation.

### 7.2 Client configuration mapping

The existing electric-mushroom flash setting remains unchanged. Add CLIENT-only
settings: `effects.advancedVisuals=true`; `sky.overworldOverride=true`,
`sky.planetOverride=true`, `sky.stationOverride=true`. False delegates visual
sky rendering to the appropriate vanilla/fallback renderer, not to another
body's environment; no physics/discovery/atmosphere value changes. Advanced
visuals suppress expensive accents/capsule particles but retain essential
status/arrival feedback and bounded base capsule rendering.

For each panel `environment` (legacy atmBar), `hydrogen` (hydrogenBar),
`oxygen` (oxygenBar), `suit` (suitPanel), add four settings under
`hud.<panel>.{x,y,anchorX,anchorY}`: signed pixel offsets -4,096..4,096 and
anchors START/CENTER/END enums, default placements chosen to avoid vanilla HUD.
These map all 16 old position/mode keys, not just one aggregate HUD toggle.
Clamp final rectangle to the visible scaled screen with four-pixel margin;
very small screens use compact non-overlapping layout rather than truncate
critical numbers/buttons. GUI scale/resolution/resource reload tests exercise
both axes/all anchors/offset limits. Client settings are never persisted into
server SavedData, transmitted C2S as environment truth or used by FE/oxygen.

### 7.3 Per-asset closure

The companion `asset-coverage.csv` enumerates the actual first-match C18d
files, not only broad patterns. Every such row must have a terminal evidence
record: approved import+source/target hash+history+transformation+review; a
recorded excluded origin with NEW functional replacement and original art
record; or the already approved EXCLUDE disposition of a non-goal. A REVIEW
row is not finished by placing an unrelated placeholder in runtime. Moving
REVIEW -> EXCLUDE or changing the importable list needs ADR-062 revision/pin
and matching origin evidence; this draft does not pre-clear any file.
All other batches' unfinished REVIEW/assets join the C18d integration audit;
the final C19 closure must cover them too, not hide them under a different plan.

Use the ADR-061 import tools/allowlist, vanilla 1.12.2 and 1.20.1 screening,
inheritance/history findings, author confirmations and transformation checks.
Screen NEW generated assets too. No weakening thresholds or changing a HIT
to CLEAR merely because the artist says original. Unsupported JPEG/OGG/OBJ
needs the corresponding origin/format check, not PNG detector acceptance.
The AR 16x/Cl1ff set stays NEW replacement until authorship is actually resolved.
Public reader documentation describes known appearance differences honestly.
English/Chinese all registered objects, menus, reasons, status/HUD, tooltips,
death/notice messages, config descriptions, advancements and guide are complete;
other legacy translations are reviewed per-file/authorship or supplied NEW by
a credited translator. Invalid/missing translation falls back to working
English without changing the server reason token or machine behaviour.

## 8. Verification and evidence

Before runtime, freeze/review each sub-contract, exact patterns, slot tables,
schema migrations, refill ordering, authority and owner decisions. Separate
implementation from independent actual-diff review. Record no-open-Critical/
High/Medium scoped findings, real commands/hashes and remaining Gate limits.

Minimum behavioural evidence (individual cases, not one mega-test):

| Area | A0/A1 success and failure | S1/S2 and player/visual evidence |
|---|---|---|
| Scrubber | finite fourth debit, empty/charged cartridge, one/dual support, stale bindings, reentrant ports, phase preservation | cross-chunk vent/scrubber unload/restart and removal with exact inventory/gas counts |
| Detector/seals/torches | exact mode truth table/240/330/2 boundaries and overlap, supplied versus ambient, six loaded probes, unknown/PENDING, door halves/redstone, loops/revoke, two source-pair torch identities/drop once | original-world upgrade; same-orientation custom-pair save/restart/break/relight; removed/future pair quarantine; canceled world changes; queue exhaustion/no force-load |
| Environment | player vs mob/bypass, spawn types, exposure priority, living gravity/no double fall, finite underwater gas; nearest outside 32-name prefix, negative grids, authority removal | dimension/context/logout/reset, safe sleep/respawn; many adrift players with global query/probe budgets and generation changes; no tickets or permission bypass |
| Charge/equipment | whole resource conservation, detached install/partial quick-move, all gas tiers/root types/config reductions, ability expiry, enchantment mixed-provider precedence | source and player saves in both orders and crashes at every phase; supported future/oversized roots preserved |
| Tools | canceled break/PvP, item/target change, loaded ray boundary/rate/replay, actual loot/harvest | dedicated two-player contested target; no loot duplication/fake-player bypass |
| Ground research | limits/tables/seed snapshot, one instance/owner credit/processor claim, overflow/cancel/replay; tagged origin and both-direction wrong-owner/kind/system/source/orphan checks | registry 1/2/3 -> 4 and instance 1 -> 2 migration; ground PENDING restart; retained AVAILABLE/ALLOCATED/terminal records and pruned legacy source; crash/rebind/copy/raw-preserve cases |
| Advancements | all 17 real triggers, failed/canceled travel not rewarded, serialized source first-event election/immutable participants/late login | cut after source travel commit before progression flush; after progression before source ack; entity/source/advancement save orders; source prune/supersede; historical initialization and unknown write outcome |
| Presentation | resource graph/provenance; NEW screening; enum/offset/wire bounds; caches clear | actual GPU V1, two-client V2; sound listen, GUI scale, reload, attachment/sky/capsule correctness |
| Progression | no unreachable ordinary item/hard cycle, energy/access/research prerequisites | complete new-survival tech route, JEI present/absent, actual report/video |

Per implemented slice run Java 17 `clean build`, `test`, `runData`, generated
byte reproducibility, `runGameTestServer`, strict repository/provenance/resource
validators and targeted packaged persistence checks. C16d graph must remain
passing. Source/JAR hashes bind the tests; failures, skipped/unexecuted checks
and changed hypotheses remain in evidence. C19 runs accepted ledger closure
with zero PLANNED and separately records every asset's terminal handling.
S1 does not imply S2, V0 does not imply V1/V2, and a draft never satisfies G0-G9.
Full campaign remains scheduled under ADR-018; no existing Gate is checked here.

## 9. Decision status and integration dependencies

1. **D1 OWNER-CONFIRMED**: keep the active 2,000-oxygen API/HUD with finite
   built-in auxiliary reserve and bounded replenishment. Generalized provider
   capacity/API migration was not selected. Numeric tier/config limits and
   schema/migration details still require scoped contract review.
2. **D2 OWNER-CONFIRMED**: independent ground-owned server survey jobs without
   a survey-satellite prerequisite. The satellite-required alternative was not
   selected; explicitly revise/extend ADR-051 and verify schema-4 migration.
3. **D3 OWNER-CONFIRMED**: preserve world-first Moon/warp milestones with
   recorded winning participants, not per-player first events. Old-world
   initialization and crash-grant receipts still need review/native evidence.
4. **D4** prove pad/player save durability protocol or approve an alternate
   interaction with a stated non-crash-atomic limitation. No phantom atomicity.
5. **D5 OWNER-CONFIRMED**: finite scrubber savings with nonzero minimum
   consumption. Zero consumption with two scrubbers was not selected. The
   proposed fourth-debit schedule, cartridge balance and vent migration still
   require scoped contract review; confirmation does not mark runtime complete.
6. Exact pattern cells, recipe ingredients/JEI serializer forms, NEW art design
   and per-file origin outcomes are implementation inputs to freeze and review,
   not a permission to reduce any C18 unit.

C18a needs C15a steel fan and C16d user interface/carbon brick, C16 fluid/tag
conversion, existing vent migration, bounded station access query and protection
authority. C18b additionally needs C16c special chemical recipes and C17's
beacon_finder identity/shared equipment summary, without adapter cycles. C18c
needs C16d circuitry/recipe graph, reviewed ADR-050/051 origin invariants and
C17 station/warp plus rocket-journal checked completion/outbox integration.
C18d needs every implemented batch's final IDs/state snapshots plus the C17
client contract; it must not invent a renderer packet schema contrary to that
contract. Freeze station sky payload schema 2/celestial_snapshot channel 4 and
retain C17's single rocket_flight channel 9 change; the pre-change payload schema
is 1 and the two channel protocols are 3/8 as specified in their source contracts.
Central registration/network/
build/status files belong to root.

No commit/tag/push, Gate pass, runtime completion or asset license clearance is
authorized by contract acceptance alone. The integrator accepts the independently
reviewed final contract under the maintainer's conditional authorization with no
unresolved Critical/High/Medium. D1/D2/D3/D5 are owner-confirmed. D4 remains an unproven
feasibility/possible-interaction decision, and an interaction-changing alternative
still requires owner confirmation rather than an implicit fallback approval.

## Acceptance record

The [acceptance receipt](../work/v1.8.0-c18-contract/ACCEPTANCE.md) binds the
immutable revision-3 proposal, independent review, owner choices and the
status-only integration diff. All explicit feasibility, exact leaf-input,
writer/migration and native recovery requirements in this ADR remain required.
