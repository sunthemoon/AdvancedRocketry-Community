# ADR-054 — Endgame authority, protection, rate, energy, transit and audit framework

```yaml
status: PROPOSED
revision: 1
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
slices: [V170-SEC-01, V170-TRN-01]
development_dependency: ADR-021, ADR-040, ADR-041, ADR-043, ADR-044, ADR-045, ADR-049, ADR-050, ADR-051, ADR-053
used_by: [ADR-055, ADR-056, ADR-057, ADR-058, ADR-059]
```

## Context

v1.7 adds five systems that can break blocks, mint or move items, generate
energy, change other players' physics or teleport players across Levels (see the
[legacy audit](../work/v1.7.0-legacy-audit.md)). In the legacy game none of them
checked who operated it, several trusted client packets, two force-loaded chunks
at client coordinates, and cargo moved between two independently saved chunks in
one tick. The version document requires one shared, frozen contract for:
per-system switches; server-side validation of target, permission, load state and
budget; transactional energy and effects; TravelTarget/BodyContext for
cross-Level targets; audit events and operator diagnostics (§5), plus a
territory/permission extension point (§6.4).

Existing building blocks: station authority (`StationAccessService`, ADR-040/046),
the checked station write path and server-wide write spacing (ADR-041), the
satellite menu-intent rules (ADR-049 §10), and the cross-store delivery protocol
with a SavedData save epoch, chunk-save persistence signals and a total
reconciliation table (ADR-050 §2, ADR-051 §5–§11).

## Decision

### 1. Systems and switches

| System ID | ADR | COMMON switch | Default |
|---|---|---|---|
| `laser_drill` | ADR-055 | `endgame.laserDrill.enabled` | `true` |
| `laser_drill` physical mode | ADR-055 | `endgame.laserDrill.physicalMining` | **`false`** |
| `railgun` | ADR-056 | `endgame.railgun.enabled` | `true` |
| `black_hole_generator` | ADR-057 | `endgame.blackHoleGenerator.enabled` | `true` |
| `gravity_field` | ADR-058 | `endgame.gravityField.enabled` | `true` |
| `space_elevator` | ADR-059 | `endgame.spaceElevator.enabled` | `true` |

The switches are read at each use, so a config reload applies on the next tick.
A **disabled** system:

- keeps its blocks, items, recipes and saved state registered and loadable, so
  disabling never breaks world load and never removes content;
- starts no new operation (every intent returns `SYSTEM_DISABLED`) and produces
  no effect: no block break, no generation, no field, no launch, no ride, no bind;
- still runs **settlement and recovery**: transit registration, arrival, claim,
  reconciliation and receipt handling (§11), counter reconciliation (ADR-055),
  unbinding (ADR-059) and withdrawal from buffers. A player is never stranded
  and an escrowed payload is never frozen by a switch.

Every limit in this ADR and the system ADRs is a COMMON config value that can
only be lowered below its fixed maximum (the existing `CommonConfig.limit`
pattern), unless the text gives a range.

### 2. Device identity and ownership

Every endgame block entity root has `schema_version`, `device_id` and `owner_id`.

- `device_id` is a random UUID created on placement, or on the first load of a
  root without one. It is never carried in an item: removing a device drops the
  plain block and its **local buffers** (input, output, receive and drop
  buffers; energy is lost), and a new placement gets a new ID. Escrowed outbox
  entries, incoming payloads and receipts are not local buffers; §9.1 settles them.
- `owner_id` is the UUID of the placing entity when it is a connected
  `ServerPlayer` that is **not** a `FakePlayer`. Any other placement (automation,
  FakePlayers, `/setblock`) creates an **unowned** device, which is inert
  (`UNOWNED`) until an operator assigns an owner (§13).
- A root with an unknown schema, a missing or malformed ID, or a value outside
  its bounds is **quarantined**: the device is inert, its root is kept unchanged
  and re-saved byte-identical, and one `ARCE_ENDGAME_DEVICE_QUARANTINED` line is
  written. Buffers of a quarantined root are not dropped on break; breaking it is
  refused for non-operators.
- Endpoints (§9) additionally follow ADR-051 §5's persistence rule: an endpoint
  ID, outbox entry or receipt counts as **persisted** only when it is present in
  that endpoint's entry of a `ChunkDataEvent.Save` or `ChunkDataEvent.Load` tag
  for its chunk. Nothing else marks it persisted, and while anything is
  unpersisted the block entity calls `setChanged()`.

### 2.1 Structures

The laser drill, railgun, black-hole generator and elevator anchor are
**structure-only multiblocks**. Their shape is a `machine_patterns` file in the
ADR-016 format, matched by the kernel's bounded `MultiblockPatternValidator`
through a loaded-chunk world view; it never loads a chunk. Unlike the v1.2
machines they have **no port blocks, part bindings or process journal**: every
buffer lives in the controller block entity, and automation uses the
controller's own capabilities. The controller re-validates when a neighbouring
block changes inside its pattern box and every 200 ticks, at most 8 validations
per server tick across all endgame controllers (the rest wait, §7). An
unformed structure idles with `UNFORMED`, and one with an unloaded cell with
`STRUCTURE_UNLOADED`; nothing is lost or dropped by either state.

### 3. Authority

`EndgameAuthority.allowed(actor, device, action)` is a pure function over a
snapshot (actor UUID, operator flag, device owner, the station record at the
device position if any, the action). Actions: `VIEW`, `CONFIGURE` (settings,
targets, links, binds), `OPERATE` (start, stop, launch, ride), `WITHDRAW`.

| Actor | Device outside any station region | Device inside a committed station region |
|---|---|---|
| Operator (permission 2, connected non-fake player) | all | all |
| Device owner | all | all, while the owner still has station `BUILD` access (owner or member); otherwise `VIEW` |
| Station owner (not device owner) | — | all (station `MANAGE_STATION`) |
| Station member (not device owner) | — | `VIEW` |
| Anyone else | `VIEW` of public status only | none |

- A device in the Space Level outside every committed region, or in a blocked or
  quarantined station registry, is refused with `STATION_UNAVAILABLE` for every
  action except `WITHDRAW` by its owner or an operator. Systems that require a
  station (ADR-055, ADR-057) also refuse to operate there.
- ADR-058 narrows `CONFIGURE`/`OPERATE` inside stations to `MANAGE_STATION`,
  because a field overrides the station's own gravity.
- FakePlayers, command blocks, functions and the console never pass an intent.
  The automation path is redstone control, where a system allows it.
- `VIEW` of public status never includes a target, a coordinate, an owner name or
  another device's ID.

**Per-action overrides** (review R1-M3). These replace the table above for the
named actions only; `EndgameAuthority` evaluates them first.

| Action | Allowed for | Extra condition |
|---|---|---|
| Elevator `RIDE` and `SHIP`, at either endpoint of a pair | Station owner, station members, operators (station `VISIT`, the rocket rule) | The pair's anchor owner is the station's current owner or a current member, else `ANCHOR_OWNER_NOT_MEMBER` (operators exempt). An ownership transfer or a removed membership therefore stops traffic into the anchor owner's base without that owner's consent |
| Elevator `BIND` | Station owner or operator, at the terminal | The anchor is the actor's own; operators may pick any (ADR-059 §3) |
| Elevator `UNBIND` | Station owner, anchor owner, operators | From either endpoint's menu or the command, whatever the pair's validity (ADR-045) |
| `CONFIGURE` by a station owner of a device someone else owns | Station owner | Selection lists show the **device owner's** endpoints (below), not the station owner's |

### 4. Intents and network

- All player intents are fixed button IDs through vanilla `clickMenuButton`. No
  intent carries a payload, a coordinate, an index or an item. Selections
  (targets, pairs, products) are server-side menu state moved by previous/next
  intents, as in ADR-049 §10.
- Each intent is validated on the server thread: the menu's block entity is
  still the same device at the same position, the player is within 8 blocks and
  in the same Level, the chunk is loaded, §3 allows the action, the system is
  enabled, and the per-player rate allows it (state-changing intents 10 ticks,
  selection intents 2 ticks, config `endgame.intentIntervalTicks` ≥ 10 and
  `endgame.selectionIntervalTicks` ≥ 2). Refused intents change nothing and send
  one status line.
- **No new C2S message.** Menu data slots carry only 16-bit values and open
  data is sent once, so they cannot show a selection that changes with every
  previous/next intent (review R1-M10). As v1.6 did for the terminal (ADR-049
  §10), one new **S2C-only** channel `advancedrocketrycommunity:endgame`,
  protocol `1`, carries one message, the **device view**: sent when a device
  menu opens and on change, at most once per 5 ticks per player, ≤ 8 KiB and
  tested at that maximum. It holds the device status and codes, the current
  selection only (never the whole list: the selected endpoint's label, its
  position in the list and the list size), pair and transit counts, and buffer
  summaries. Endpoints have no player-set names; a **label** is generated from
  the kind, the first 8 hex digits of the ID and the body. Level and position
  are included only for viewers allowed to see them (§9). Numbers that change
  every tick (energy, progress) stay in data slots. A client without the channel
  cannot join, as with the other channels.
- Visuals use block-entity update tags (≤ 1 KiB, only render state: active
  flag, beam length, field radius and value, never a target coordinate of
  another Level). Existing channel versions (life support 1, celestial 3,
  rocket flight 8, rocket visual 1, satellite 1) are unchanged.
- Every status and refusal is shown as translated text (with its stable code),
  never by colour or icon alone, so it stays readable at any GUI scale and for
  colour-blind players.

### 5. Protection chain

Every world effect outside the device's own block passes this chain on the server
thread, **re-evaluated for every batch** (a batch is at most one tick of work for
one device). The first failure stops the operation with the given code; the
device keeps its state and reports the code.

1. **Loaded.** Every chunk the batch touches is present at `FULL` status
   (`getChunkNow`), else `TARGET_UNLOADED`. Nothing loads a chunk (§12).
2. **Bounds.** Inside the Level's world border and build height, else
   `TARGET_OUT_OF_BOUNDS`.
3. **Protected zones** (§6): no zone in that Level intersects the batch box
   unless the device owner is on that zone's allow list, else `TARGET_PROTECTED`.
4. **Stations.** In the Space Level, every touched position is inside a committed
   region where the device owner has `BUILD` access, else `TARGET_PROTECTED`.
5. **Spawn protection.** `MinecraftServer.isUnderSpawnProtection` for an
   owner-bound FakePlayer (profile `owner_id`, name `[ARCE]`, no permission
   level) is false for every position, else `TARGET_PROTECTED`.
6. **Extension event.** The public, cancellable
   `api.endgame.EndgameEffectEvent` (§5.1) is not cancelled, else
   `TARGET_PROTECTED`.
7. **Block breaks only.** For each block, a standard `BlockEvent.BreakEvent`
   posted with the owner-bound FakePlayer is not cancelled, so claim mods that
   protect breaking also protect against the laser, else `TARGET_PROTECTED`.

A protection refusal is audited once per device and code per 1,200 ticks (§13).
Effects that touch only the device's own block entity or a ledger (generation,
cargo escrow, claim) do not use the chain.

#### 5.1 Public API extension

API minor 1.7 → **1.8** (ADR-021) adds
`io.github.sunthemoon.advancedrocketrycommunity.api.endgame`:

- `EndgameEffect`: enum `BLOCK_BREAK`, `ENTITY_GRAVITY`, `TELEPORT`;
- `EndgameEffectEvent`: a cancellable Forge `Event` (posted for `BLOCK_BREAK` by
  ADR-055, for `ENTITY_GRAVITY` by ADR-058 and for `TELEPORT` at every elevator
  ride commit by ADR-059 §8), posted on
  `MinecraftForge.EVENT_BUS` on the server thread only, with `systemId()`
  (`ResourceLocation`), `effect()`, `ownerId()` (`UUID`), `level()`
  (`ResourceKey<Level>`), `min()` and `max()` (`BlockPos`, the batch box).

It exposes no internal type, no device object and no way to change the effect.
Listeners may only cancel. Existing API classes keep their bytes; the API
compatibility document, the public API guide and the compatibility test mod
gain the new types. This is the only public API change in v1.7.

### 6. Protected zones

Operators manage zones in the endgame root (§10): `/arce endgame zone add <name>
<from> <to> [<player>...]` in the operator's current Level, `zone remove <name>`,
`zone list`. A zone is an axis-aligned box of at most 4,096 × 4,096 blocks in
plan view (full height), a name of 1..32 characters `[a-z0-9_-]`, and an allow
list of at most 16 player UUIDs. At most 256 zones per server. Zones affect only
endgame effects (§5 step 3); they are not a general claim system. Zone changes are
barrier flushes (§10) and audited. Non-operators never see zone boxes.

### 7. Rates and budgets

- **Per player:** §4 intent spacing.
- **Per device:** each system ADR fixes the device cadence (operations per
  tick or per interval).
- **Per system per tick:** each system ADR fixes a server-wide work cap (blocks,
  launches, claims, rides, field lookups). Work beyond the cap waits for the next
  tick in a deterministic order (device ID order, round-robin from the last
  served device). **ID order** everywhere in v1.7 is the order of the canonical
  lowercase UUID strings (`String.compareTo`), not `UUID.compareTo`, which
  compares signed halves. No system pass ever loops over all devices of another system.
- **Counts:** active devices per owner and globally, endpoints, transit records,
  pairs and zones are capped (system ADRs and §10).
- **Ledger passes:** registration ≤ 32 per tick, arrival ≤ 64 per tick,
  reconciliation ≤ 64 records per tick across all endpoints (§11).

**Where work runs** (reviews R1-M8, R1-L4). Devices act from their block-entity
ticker, which vanilla runs only in block-ticking chunks, and draw on the
per-tick system budgets above. Ledger, index and reconciliation passes run in
one `ServerTickEvent` END handler. The gravity-field lookup runs in the existing
living-tick hook (ADR-058). Any read of a position outside the device's own
block goes through `getChunkNow` (or `hasChunkAt`) first and treats an absent
chunk as unavailable, because `Level.getBlockState` and `getBlockEntity` load
chunks. C13 measures all three places against total tick time, because Forge
records `tickTimes` before END handlers run (§7 budgets).

### 8. Energy

- Energy is Forge Energy through the block capability. Each device has its own
  bounded buffer in its block entity; `receiveEnergy` is effective only on the
  server thread, `simulate=true` changes nothing, and generators expose no
  `receiveEnergy`.
- **Same-store rule.** Wherever possible a payment and its effect are in one
  block entity and change in the same server tick, so one chunk save covers both:
  laser logical output (ADR-055), black-hole fuel and generation (ADR-057), field
  upkeep (ADR-058), cargo escrow (§11).
- **Cross-store rule.** When the effect is elsewhere, either the payment is
  escrowed with a record that is reconciled (cargo, §11), or both sides keep
  monotone counters and reconcile to the maximum with a recorded debt (ADR-055
  physical mode). A ride (ADR-059) is the single documented energy-only residual.
- **No refund with a kept effect.** Energy is refunded only when the effect is
  known not to have happened and can no longer happen (refused before escrow).
  Escrowed cargo energy is never refunded.
- No device mints energy except the black-hole generator, bounded by fuel and a
  per-tick cap (ADR-057).

### 9. Endpoints and targeting

An **endpoint** is a device that other devices can address: `laser_target`
(ADR-055), `railgun` (ADR-056), `elevator_anchor` and `elevator_terminal`
(ADR-059). Targeting is always endpoint selection, never a coordinate.

- **Index.** The endgame root keeps one record per endpoint: `endpoint_id`,
  `kind`, `owner_id`, Level key, block position, `state` (`ACTIVE` or
  `MISSING`), ≤ 256 bytes. At most 4,096 endpoints, 64 per owner.
- **Registration.** An endpoint is added when its ID is persisted (§2); until
  then it is `AWAITING_WORLD_SAVE` and cannot be selected. Registration is a
  flush-pending mutation (§10). Placement beyond the limits is allowed, but the
  endpoint stays `ENDPOINT_LIMIT` and inert.
- **Removal.** Removing an endpoint removes its record (§9.1). Breaking is
  refused for non-operators while it holds an outbox entry, an incoming payload,
  an unacknowledged receipt or an elevator pair (`ENDPOINT_BUSY`). An endpoint
  whose chunk is loaded without a
  block entity of that ID at the recorded position becomes `MISSING`; a block
  entity carrying a registered ID at another position is
  `ENDPOINT_POSITION_CONFLICT` and inert (operator copy tools such as `/clone`
  are outside the guarantee, as in ADR-051 §7).
- **Selection.** A selection list holds only `ACTIVE` endpoints of a compatible
  kind owned by the **device's owner**, filtered by the system's route rule and
  sorted in ID order (§7); whoever is allowed to `CONFIGURE` the device (§3)
  picks from it. Operators can select any endpoint. The device owner, the
  station owner where §3 allows `CONFIGURE`, and operators see the endpoint's
  Level, position and body; others see nothing.
- **Body context.** An endpoint's body is derived live, never stored, with the
  existing ADR-014 `BodyContextResolver.resolve(WorldLocation, catalog)` over one
  captured catalog: in the Space Level the station-region resolver gives the
  station's current orbit body (ADR-041); in another Level the Level's single
  candidate body. Its star system is the catalog's root for that body (ADR-043).
  No body means `BODY_UNAVAILABLE`. Cross-Level targets are therefore expressed
  as BodyContext plus an endpoint ID, as the version plan requires.

#### 9.1 Removal by any cause

Review R1-H3: a refused player break is not the only way a block entity goes.
Every endgame endpoint block is in `#minecraft:wither_immune` and
`#minecraft:dragon_immune`, has blast resistance 1,200 (TNT, creepers, beds
and withers cannot remove it), and cannot be pushed (it has a block entity).
Player breaks follow §9. Every other removal (an operator break, `/setblock`
and `/fill`, another mod's breaker or block mover) is handled in the block's
`onRemove` when the block changes to a different block, which chunk unloading
never triggers:

- **Local buffers** drop as items, as for any container.
- **Outbox entries never drop.** Entries with `seq ≤ dispatched_through[S]` are
  delivered by the ledger. Unregistered entries are destroyed with one
  `OUTBOX_LOST_ON_REMOVAL` line naming the payload hash: an audited loss, never a
  duplicate. Dropping them could duplicate, because dropped item entities are
  saved in the entity storage, separately from the block entity, and a crash
  can restore the endpoint with its entries.
- **Incoming payloads and receipts** are settled from the endpoint's live state,
  record by record, and the result is written with a **barrier flush** in the
  same tick: a record whose payload is still incoming here, or was never
  materialized here (no receipt), returns to `ARRIVED` with `paid_endpoint`
  cleared and waits as `DESTINATION_MISSING`; a record whose payload already
  moved to the receive buffer (receipt present, nothing incoming) becomes
  `CLAIMED` and acknowledged. An incoming payload whose record is already
  acknowledged or pruned is the endpoint's own content (its move was due after
  a crash, below) and drops with the local buffers. If the barrier flush fails,
  the records stay as they were and one `REMOVAL_SETTLEMENT_UNKNOWN` line asks
  an operator to redirect or purge them.
- The endpoint's index record is removed, its `dispatched_through` stays as a
  tombstone (§11), pairs naming it become invalid (unbinding stays possible,
  ADR-059), and a laser drill linked to it sees `LINK_LOST` (ADR-055).

A crash after the barrier flush but before the endpoint's chunk saved the
removal restores the endpoint with its incoming payload and receipt while the
record is `ARRIVED`; the destination row "ledger behind" re-claims it, so it is
paid once. The reference model checks removal at any point with up to two
crashes.

### 10. Endgame root

All cross-device state lives in one SavedData,
`advancedrocketrycommunity_endgame.dat` in the world's `data` directory, a new
entry of the managed-SavedData allowlist (review R1-M2):

- `ManagedSavedDataType.ENDGAME`: introduced in `v1.7.0`, current schema 1 and
  format epoch `v1.7.0-endgame` (its own `currentSchemaVersion` and `formatEpoch`
  cases; schema 1 is the only readable schema, so there is no legacy migration),
  required tags `endpoints`, `dispatched_through`, `transits`, `elevator_pairs`,
  `zones` (lists) and `save_epoch` (long), the 4 MiB bound below, and a semantic
  validator that runs the full root codec;
- written through the checked atomic file path: `AtomicSavedData` widens its
  constructor allowlist from the two discovery authorities to also accept
  `ENDGAME`, so ordinary saves, barrier flushes and the save epoch use the same
  replace-and-read-back path;

- root `schema_version` 1, `save_epoch`, and the sections `endpoints` (§9),
  `dispatched_through` (§11), `transits` (§11), `elevator_pairs` (ADR-059) and
  `zones` (§6);
- hard bound 4 MiB encoded; **growth admission**: endpoint registration, escrow
  admission (§11), binds and zone additions are refused while the encoded root is
  within 1 MiB of the bound; a registration of an already escrowed payload and
  every reconciliation step are admitted up to the bound itself, and beyond it
  wait (never drop) with `ROOT_FULL`;
- **write policy** (ADR-050 §2 semantics): elevator bind and unbind, zone changes
  and operator redirects and purges are **barrier flushes**; every other mutation
  marks the root flush-pending, and a coalesced flush runs at most once per 100
  ticks while one is pending; idle servers write nothing extra;
- **save epoch** E as in ADR-050 §2: a write puts E + 1 in the file and E
  advances when the write returns without error; records store the E of their
  creation, and "durable" means `save_epoch > record_epoch`;
- **load**: no file means a fresh root (v1.6 worlds need no migration). The
  pre-start validation (`WorldDataMigrationService`, ADR-050 §10) refuses to
  start the world, as for every managed file, when the root has a future schema
  (`FUTURE_SCHEMA`) or a malformed section, a duplicate key or a bound violation
  (`INVALID_SCHEMA`); the message names the file and nothing is overwritten. An
  operator who accepts losing endgame state moves the file away and restarts.
  Records that decode but fail a semantic check later (an item of a removed mod)
  are quarantined one by one (§11). A failed write keeps the root dirty, is
  retried by the next save, and logs once, as `AtomicSavedData` already does.
- **operational** means the root is loaded and the endgame service is installed
  for this server; before that (startup) and after `ServerStoppingEvent` every
  endgame action is refused and the station guards of ADR-059 §5 fail closed.

### 11. Transit ledger

Cargo (ADR-056 railgun, ADR-059 elevator) moves from a source endpoint S to a
destination endpoint D through a ledger record, never directly between two block
entities.

**Source state** (in S's root): `next_seq` (starts at 1) and an `outbox` of at
most 4 entries `{seq, destination, payload, paid_fe, system}`. A payload is at
most 4 stacks, each with an encoded item tag of at most 512 bytes; plain items
only otherwise.

**Record** (≤ 4 KiB): `source`, `seq`, `system`, `owner_id`, `destination`,
`payload`, `paid_fe`, `dispatch_epoch`, `arrive_at` (game time), `state`
(`IN_TRANSIT`, `ARRIVED`, `CLAIMED`, `QUARANTINED`), `paid_endpoint`,
`acknowledged`, `ack_epoch`, `redirected`. Identity is `(source, seq)`.
`dispatched_through[S]` is the highest registered `seq` of S and never
decreases; it is what keeps a pruned transfer from being registered again.
When S's endpoint record is removed (by any cause, §9, or by an operator purge)
`dispatched_through[S]` stays as a **tombstone** until S's absence is persisted:
a `ChunkDataEvent.Save` or `ChunkDataEvent.Load` tag of S's chunk whose
block-entity list holds no entry with S's ID at the recorded position, observed
at least 40 ticks earlier (the age rule of step 2). Until then a crash can
restore S with a stale outbox entry, and only the tombstone keeps it from
registering a delivered payload again (review R1-H1). Tombstones are at most
64 bytes each. One is removed only when its source's absence was observed at
least 6,000 ticks earlier **and** the table holds more than 8,192 tombstones,
oldest observation first; otherwise tombstones are kept, admitted up to the
root's hard bound. Residual: if the absence observation itself was a lost
asynchronous write and the server then crashed, S could return after its
tombstone was removed and register a delivered payload again, unaudited. The
6,000-tick age (five minutes of uninterrupted running, far beyond any `IOWorker`
backlog) and the pressure rule make that unreachable in practice; it is the
same lost-write class as the escrow residual below. Limits: 512 records globally, 32 per owner, counting live
outbox entries known to the server. A record whose payload no longer decodes
(for example an item of a removed mod) is `QUARANTINED` with its raw payload kept
byte-identical; it is never claimed, and only an operator purge removes it. A
purge of a record whose source still holds the entry also drops that entry
(`OUTBOX_STALE_DROPPED`), because the purge destroyed the payload.

**Dispatch.**

1. **Escrow.** In one server tick at S: the payload leaves S's input buffer and
   becomes outbox entry `seq = next_seq++`, and `paid_fe` leaves S's energy
   buffer. Requires §3 `OPERATE` (or the system's redstone path), the system
   enabled, the root operational with admission room, a selected `ACTIVE`
   destination passing the system's route rule, a free outbox slot, the limits
   and the energy.
2. **Registration.** When the entry is persisted and the persistence
   observation is at least 40 ticks old, and the entry is S's **lowest** outbox
   entry with `seq > dispatched_through[S]`, the ledger creates the record (`IN_TRANSIT`, `dispatch_epoch = E`,
   `arrive_at = now + travel`) and sets `dispatched_through[S] = seq`. If the
   destination has meanwhile become `MISSING` or was removed, the record is still
   created; it will wait as `DESTINATION_MISSING`. Normally
   `seq = dispatched_through[S] + 1`; a larger `seq` means the ledger lost
   registrations (an operator restored an older file), and the entry registers
   with a `SEQUENCE_GAP` audit line instead of waiting forever.
3. **Release.** S drops the outbox entry when its record is durable, or when
   `seq ≤ dispatched_through[S]` and the record is gone (it was delivered,
   acknowledged durably and pruned, or purged by an operator).

**Delivery.** At `arrive_at` the record becomes `ARRIVED` (due queue). D claims
it automatically during its reconciliation pass, or on a withdraw intent, when:
the record is durable, D's ID is persisted, D's receive buffer has room for the
whole payload beyond what its incoming payloads already reserve, and D has a
free receipt slot (≤ 64). In one tick the record becomes `CLAIMED` with
`paid_endpoint = D`, and D gains an unpersisted receipt `(source, seq)` and the
payload in its **incoming** area.

**Incoming gate** (answers finding F02 of the external v1.3–v1.6 deep-test report
of 2026-10-01, which reproduced a duplicate in ADR-051's terminal delivery after
a player withdrew a reward that the terminal's chunk had not yet saved). Incoming
payloads are not extractable by players, menus or automation, are not dropped
when the block is broken (breaking is refused while any exist, §9), and count
against the receive buffer's room. A payload moves from incoming into the
extractable receive buffer, in one tick inside D, and its record is
acknowledged in the same tick, only after a
`ChunkDataEvent.Save` or `ChunkDataEvent.Load` tag of D's chunk is observed to
contain it (§2's persistence rule) and that observation is at least 40 ticks old,
the same age rule as registration (review R1-M1). The reconciliation row that
acknowledges a persisted receipt uses the same aged observation. Until then a crash can only lose D's copy
together with its receipt, and the record rematerializes it into incoming; no
third store (a player, a hopper, another container) can already hold it. After
the move, the receive buffer is an ordinary container: a crash that saves a
player's file after a withdrawal but not D's chunk duplicates the withdrawn
items, exactly as for any vanilla chest. That container class is listed as a
residual below; v1.7 adds no window beyond it.

**Source reconciliation** (when S loads and before each escrow at S):

| Ledger | Outbox entry `seq` at S | Action |
|---|---|---|
| Not operational (§10) | any | Nothing; escrow refused |
| No record, `seq > dispatched_through[S]` | persisted, aged, lowest entry of S | Register (step 2); `SEQUENCE_GAP` if `seq > dispatched_through[S] + 1` |
| No record, `seq > dispatched_through[S]` | unpersisted or not aged | Wait |
| No record, `seq ≤ dispatched_through[S]` | present | Delivered and pruned (or purged): drop the entry, audit `OUTBOX_STALE_DROPPED` |
| Record present, not durable | present | Wait |
| Record present, durable | present | Drop the entry (step 3) |
| — | S's persisted `next_seq ≤ dispatched_through[S]` | S's chunk is older than the ledger: set `next_seq = dispatched_through[S] + 1`, refuse escrow for this tick, audit `SOURCE_ROLLBACK` |

**Destination reconciliation** is ADR-051 §7 with these substitutions: terminal →
D; mission → record; ACTIVE/READY → `IN_TRANSIT`/`ARRIVED`; bound terminal →
`destination`; rebind → operator redirect; the `CANCELLED` row does not exist
(transfers cannot be cancelled). Its rows: ledger behind (receipt present, record
not yet `CLAIMED`) sets `CLAIMED` with no items (`CLAIM_RECOVERED`, or
`REDIRECT_CONFLICT` with a barrier flush that redirects back to D); persisted
receipt acknowledges; missing receipt for an unacknowledged claim at D
rematerializes once into incoming (`REMATERIALIZED`, waiting while full); an
incoming payload whose record is already acknowledged or pruned means D's chunk
is behind its own move, and it moves again; a receipt is dropped
only after the acknowledgement is durable or when the record is absent; a claim
paid elsewhere keeps the receipt (`REDIRECT_DOUBLE_PAY`); a quarantined record
keeps it.

**Pruning.** A record is removed once its acknowledgement is durable, whether or
not S has already dropped its entry; the `dispatched_through` rule above makes a
late drop safe. `dispatched_through[S]` is removed only as a tombstone whose
source's absence is persisted (above). A
`source_released` flag was considered and dropped: the reference model shows it
changes no outcome, with or without a lost source write.

**Operator actions** (barrier flushes, audited with a SHA-256 prefix of the
payload): `transfer redirect <source> <seq> <endpoint>` for an `IN_TRANSIT` or
`ARRIVED` record to another endpoint of the same owner and system (ADR-051 §9
residual applies); `transfer purge <source> <seq>` removes a record and destroys
its payload.

**Crash cuts.**

| Cut | After restart | Outcome |
|---|---|---|
| Escrow not saved | Payload in S's input | Nothing happened |
| Escrow saved, not registered | Outbox entry, no record | Registered later; exactly once |
| Registered, ledger not flushed | Outbox entry, no record | Registered again with the same `(S, seq)` |
| Ledger flushed, release not saved | Outbox entry, durable record | Entry dropped; delivered once |
| Claim: D saved, ledger not | Receipt, record `ARRIVED` | `CLAIM_RECOVERED`; paid once |
| Endpoint removed by a non-player cause, removal not yet saved | Endpoint back with its entries, incoming payload and receipt | Registered entries delivered; the incoming claim, returned to `ARRIVED` by the barrier flush, is re-claimed once |
| Endpoint removed, removal saved | No endpoint | Registered entries delivered; unregistered entries lost and audited; incoming claims redirected (`DESTINATION_MISSING`) |
| Claim: ledger flushed, D not | `CLAIMED`, no receipt | Rematerialized once into incoming; nothing was withdrawable before the lost save |
| Claim persisted, payload moved and withdrawn, player file saved, D's chunk not saved again | D's incoming or buffer still holds the payload; the player holds it too | **Residual**, the ordinary container/player torn save of every vanilla chest; the model shows no other duplicate or loss path |
| S removed (and its tombstone kept), ledger flushed, S's chunk not saved | S restored with an entry `seq ≤ dispatched_through` | Dropped (`OUTBOX_STALE_DROPPED`); delivered once |
| Record pruned while S still holds the entry (S unloaded, its release not yet saved, or that save lost) | Entry with `seq ≤ dispatched_through`, no record | Dropped (`OUTBOX_STALE_DROPPED`); the payload was already delivered once |
| **Residual**: S's escrow save observed but its asynchronous file write lost, then the record durable | S's input still holds the payload, record exists | Duplicate of one payload, detected and audited as `SOURCE_ROLLBACK`; same class as a torn vanilla save. The 40-tick age rule narrows the window to an `IOWorker` backlog older than 2 s |
| **Residual**: operator redirect after a ledger rollback | ADR-051 §9 | Possible double delivery, audited |
| **Residual** (R1-M1): D's save of a claim observed and aged, but its asynchronous write lost; the move, acknowledgement and flush happened; then a crash | The ledger acknowledged a payload that D's chunk never stored | The payload is lost (ADR-051 §11 lists the same class) |
| **Residual** (R1-M1): as above, but the ledger's acknowledgement was not yet flushed and a player withdrew and saved the moved payload | The record is `CLAIMED`, D has no receipt | Rematerialized, so the payload exists twice; it needs a lost write that the 40-tick age did not outlast |

### 12. Chunk loading

v1.7 never forces a chunk, never creates a persistent ticket, never loads a chunk
synchronously, and never initialises a Level on demand. Every world effect needs
its chunks already `FULL` (§5 step 1); ledger paths need no chunk. The only
tickets an endgame action creates are the elevator ride-arrival tickets of
ADR-059 §8 (review R1-M4): one per pending ride, at most 64, at a server-derived
position, with a 300-tick lifespan and released at commit or cancellation;
vanilla adds its own short `POST_TELEPORT` ticket to the teleport itself. Tests
count tickets by type before and after every system and after every ride.

### 13. Audit and diagnostics

- **Lines.** Every state-changing intent, refusal code change, effect batch
  summary, ledger transition, reconciliation result, operator command and
  quarantine writes one `ARCE_ENDGAME` line of at most 512 bytes:
  `system action result device owner actor` plus bounded fields. Coordinates
  appear only in these server log lines and in operator command output.
- **Ring.** The last 512 lines are kept in memory (not persisted, cleared at
  stop) for `/arce endgame audit [system] [page]` (operator, 16 lines per page).
- **Operator commands** (permission 2, leaf-level `.requires`, ADR-045 lesson):
  `/arce endgame status` (switches, root state and size, active device counts,
  last-tick work per system, ride-arrival tickets held, at most 64); `audit`;
  `zone …` (§6);
  `device inspect <pos>`; `device owner <pos> <player>`; `endpoint list
  [<player>]`; `endpoint purge <id>` (removes the index record only, refused
  while referenced; the §11 tombstone stays until the absence is persisted);
  `transfer list|inspect|redirect|purge` (§11). Outputs are bounded to one page.
- **Players** see their own device status in its menu; nothing lists other
  players' devices, endpoints, zones or coordinates.

### 14. Migration, removal and rollback

- New content only: no existing block, item, registry or SavedData changes
  except the new root, the API minor and the hooks named in ADR-058 (gravity
  chain) and ADR-059 (warp and deletion guards). v1.6 worlds open unchanged.
- A v1.6 host loading a v1.7 world loses the new blocks (missing registry
  entries become air with Forge's usual warning) and ignores
  `advancedrocketrycommunity_endgame.dat`;
  escrowed and in-transit cargo is lost there. Downgrade is not supported, as for
  earlier versions.
- Disabling a system (§1) is the supported way to stop it.

### 15. Shared threat model

| Threat | Control |
|---|---|
| Grief by breaking or changing others' blocks or physics | §3 authority, §5 chain with zones, stations, spawn protection, API event and break events; physical laser default off |
| Duplication | Same-store rule; transit ledger with escrow, persistence signals, epochs, receipts and total reconciliation; endpoint break refusal while busy; residuals listed in §11 |
| Chunk loading | §12: none, tested |
| Packet spam / forged intents | No new C2S message; button IDs only; server-side selection; rate limits; refusals change nothing |
| Privilege escalation via automation | FakePlayers and command sources never pass intents; unowned devices inert |
| Unbounded work or data | §7 caps, §10 root bounds and admission, bounded menus and update tags |
| Information leak | Coordinates only to the owner and operators; public status carries no target |
| Disabled or broken config | §1 switches keep content loadable; settlement continues |

## Consequences

- One protection chain, one ledger and one audit format for all five systems.
- Players address targets they physically built, never coordinates.
- Claim and protection mods integrate through standard break events and one
  cancellable API event.
- Some effects wait for chunks that players or other mods keep loaded; v1.7 does
  not provide chunk loading.

## Verification

- A0: authority matrix over every actor, action and station case; protection
  chain order with each step failing; zone bounds and allow lists; limit
  admission; endgame root codec round trip, bounds, blocked load, epoch; transit
  ledger: every source and destination reconciliation row, every crash cut by
  fault injection, ordering enumeration (the reference vectors), pruning and
  `dispatched_through`; the incoming gate against a third store that withdraws
  (the reference vectors' delivery model, and the F02 withdrawal sequence as a
  regression); audit line bound.
- A1: intents refused for FakePlayers, distance, other Level, unloaded chunk and
  rate; API event cancellation stops a batch; protection chain against a spawn
  area and a zone; disabled switch keeps settlement running; zero tickets.
- S1/S2 (C13): forced stops around escrow, registration, release, claim and
  acknowledgement; after each restart every payload exists exactly once except
  the documented residuals.

## Rollback

Disable the systems by config. Removing the code leaves
`advancedrocketrycommunity_endgame.dat` unused;
escrowed cargo in it would be lost, so removal requires draining transfers first.
