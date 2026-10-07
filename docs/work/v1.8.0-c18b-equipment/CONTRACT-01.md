# CL18B-EQUIPMENT-01: equipment modules, contract draft 01

Date: 2026-10-07. Milestone: v1.8.0 / C18b. Status: **proposed, not frozen**; production
implementation remains `DEPENDENCY_BLOCKED`.
Author: Claude (delegated worker, interactive session). Task: [TASK-01](TASK-01.md), published by
Root at `90f257ff` (SHA-256 `d51f6b8eaf9423c4c1353dafce186ae460ca4ff9b93a1fc46eb618dc1a1c533e`).
Code basis: `a65dcbf68143ce63af3b2205b0c36af02eaae0e8`.

This draft maps the twenty assigned units, proposes a slot and module-kind table, the owned item
payloads, the detached-stack mutation rules, the configuration table, a server equipment summary
and the client effect and input boundaries. It implements nothing and approves no numeric balance.
Accepted ADR-066 text is cited as accepted; values ADR-066 marks **proposal** stay proposals here.
Items marked **OPEN** need Root input or an owner decision. [TEST-DESIGN-01](TEST-DESIGN-01.md)
derives its cases from this text.

Terms used below:

- **Built-in suit**: the four existing `space_suit_*` items (`SpaceSuitArmorItem`).
- **Active oxygen**: the existing chest balance, 0..2,000 units, at `arce_space_suit_oxygen`
  schema 1 (`atmosphere/content/SpaceSuitOxygen.java`); one unit protects for 20 vacuum ticks
  (ADR-025).
- **Auxiliary gas**: gas stored in an installed pressure tank.
- **Detached commit**: read a copy of the item's owned subtree, compute the new subtree on the
  copy, and replace the live subtree only if the live stack and its owned subtree are still
  exactly the ones read (the rule already used by `SuitEquipmentService.commit`).

## 1. Inputs

| Input | What it fixes |
| --- | --- |
| ADR-066 §2 | shared authority, menu/intent rules (real player, same Level, ≤ 8 blocks, loaded chunk; 10 intents/s; 64-byte intents), equipment roots ≤ 16 KiB / depth 16 / 256 nodes, quarantine of unsupported roots, `equipment.classicEnabled` switch |
| ADR-066 §4 | gas charge pad and the D4 durability gate (Root-owned) |
| ADR-066 §5.1 | workstation (one armor slot, one module transfer slot), fixed module layout, stable IDs, pressure-tank schema and proposed tiers, D1 (owner-confirmed): active 0..2,000 unchanged plus finite built-in auxiliary tank, lossless refill rules, config meanings of `spaceSuitO2Buffer` and `suitTankCapacity`, `space_breathing` rules |
| ADR-066 §5.2 | jetpack intents and motion bounds (proposals), leg and boot upgrades, visors, `atmosphericNausea` |
| ADR-066 §7.1, §8, §9 | rendering only from bounded S2C summaries; evidence table rows "Charge/equipment"; integration dependencies |
| ADR-025 | fixed item-to-slot provider mappings; detached owned NBT; external payload key `arce_suit_provider`; 2,000-unit bound; provider precedence |
| ADR-065 lines 778-808, 979-986 | `beacon_finder` shares the two HEAD accessory positions; eight-module total; immutable `beaconFinderEnabled` summary; C18 must freeze its own network surface |
| Ledger and assignment | 20 rows (§2), all `DEPENDENCY_BLOCKED` in the assignment CSV |
| Legacy, pinned `c5cd5af` | config defaults (`ARConfiguration.java:415-416, 441-442, 488`): `lowGravityBoots=false`, `jetPackForce=1.3`, `spaceSuitO2Buffer=30` (minutes), `suitTankCapacity=1.0` (0..unbounded float), `EnableAtmosphericNausea=true` |
| Current code | `SpaceSuitOxygen`, `SpaceSuitArmorItem`, `SuitEquipmentService`, `PlayerLifeSupportService`, `LifeSupportStatusPacket`, `CelestialGravityController` (transient `ENTITY_GRAVITY` modifier, `MULTIPLY_TOTAL`), `client/sky/ClientSkyEvents` (fog colour and distance from the sky profile), `CommonConfig` (`lifeSupport.classicDevicesEnabled`) |

The legacy `spaceSuitO2Buffer` default of 30 minutes equals 1,800 units at 20 ticks per unit; the
modern default 2,000 (33.3 minutes) is ADR-066's accepted mapping, not a legacy value.

## 2. Unit mapping

| Ledger unit | Modern target | Kind |
| --- | --- | --- |
| `block:suitWorkStation` | block, item and menu `advancedrocketrycommunity:suit_workstation` | device |
| `item:jetPack` | item `jetpack` | CHEST module |
| `item_variant:itemUpgrade/0` | item `hover_upgrade` | HEAD module |
| `item_variant:itemUpgrade/1` | item `flight_speed_upgrade` | LEGS module |
| `item_variant:itemUpgrade/2` | item `bionic_leg_upgrade` | LEGS module |
| `item_variant:itemUpgrade/3` | item `padded_landing_boots` | FEET module |
| `item_variant:itemUpgrade/4` | item `anti_fog_visor` | HEAD module |
| `item_variant:itemUpgrade/5` | item `earthbright_visor` | HEAD module |
| `item_variant:pressureTank/0` | item `low_pressure_tank` | CHEST tank |
| `item_variant:pressureTank/1` | item `pressure_tank` | CHEST tank |
| `item_variant:pressureTank/2` | item `high_pressure_tank` | CHEST tank |
| `item_variant:pressureTank/3` | item `super_high_pressure_tank` | CHEST tank |
| `enchantment:spacebreathing` | enchantment `space_breathing`, level 1 only | enchantment |
| `keybinding:toggleJetpack` | client key mapping `key.advancedrocketrycommunity.toggle_jetpack` in category `key.categories.advancedrocketrycommunity` | client input |
| `config:CATEGORY_GENERAL.jetPackForce` | COMMON `equipment.jetpackForce` | config (§6) |
| `config:CATEGORY_GENERAL.lowGravityBoots` | COMMON `equipment.lowGravityBootsOnly` | config (§6) |
| `config:CLIENT.EnableAtmosphericNausea` | CLIENT `effects.atmosphericNausea` | config (§6) |
| `config:OXYGEN.spaceSuitO2Buffer` | COMMON `equipment.suitActiveOxygenMaximum` | config (§6) |
| `config:OXYGEN.suitTankCapacity` | COMMON `equipment.pressureTankCapacityMultiplier` | config (§6) |
| `event:PlanetEventHandler.fogColor(RenderFogEvent)` | client fog-density modulation removable by `anti_fog_visor` | client effect (§8.3) |

The item IDs and the enchantment ID are ADR-066 §5.1's stable IDs. The key mapping name, the
category and the five configuration paths are **proposals** (**OPEN**, Root); the legacy default key
of `toggleJetpack` is in `client/KeyBindings.java:98`, which is not available locally (**OPEN**).
`beacon_finder` belongs to C17c and is not registered here.

## 3. Slot and module-kind table

Proposed shared table, frozen by Root in `model/api` (ADR-066 §5.1: "Freeze the shared bounded
module-kind table and summary port in model/api under root integration"):

| Armor slot | Positions | Accepted kinds | Per-armor rule |
| --- | ---: | --- | --- |
| HEAD | 2 (`head_0`, `head_1`) | `hover_upgrade`, `anti_fog_visor`, `earthbright_visor`, `beacon_finder` | at most one of each kind |
| CHEST | 1 (`chest_jetpack`) | `jetpack` | — |
| CHEST | 2 (`chest_tank_0`, `chest_tank_1`) | the four pressure tanks | at most one tank holding OXYGEN and one holding HYDROGEN (§4.3) |
| LEGS | 2 (`legs_0`, `legs_1`) | `flight_speed_upgrade`, `bionic_leg_upgrade` | at most one of each |
| FEET | 1 (`feet_0`) | `padded_landing_boots` | — |

- Eight positions in total; ADR-065 forbids a ninth or a third HEAD position.
- Every module position holds at most one item, count 1. Modules are unstackable items
  (stack size 1), so owned data never merges. **OPEN (owner):** whether data-free upgrades may stack
  in the inventory; installation still takes exactly one.
- Only built-in suit pieces accept modules. External-provider armor and enchanted third-party armor
  accept none (ADR-066 §5.1, ADR-065 line 786).
- Kind IDs are the item registry IDs. The table is data in code, not a data pack.

## 4. Owned payloads

All roots are strict: exact keys, exact tag types, known registry IDs, bounded sizes checked before
copying or `ItemStack` deserialization (ADR-066 §2). Unsupported, future or corrupt roots give no
gas and no effect, refuse writes and stay byte-preserved.

### 4.1 Armor modules: `arce_suit_modules`, schema 1 (built-in suit pieces only)

```text
{ schema_version: 1,
  modules: [ { position: "<position id>", stack: <ItemStack compound, count 1> } ... ] }
```

- At most 3 entries per armor piece (the CHEST maximum); whole root ≤ 16 KiB, depth 16, 256 nodes.
- Each entry's position must belong to the piece's armor slot and accept the stack's kind; no two
  entries share a position; per-armor rules of §3 hold.
- An absent root means no modules: old built-in suits stay valid (ADR-066 §5.1).
- The nested stacks keep their own owned roots (tank gas, durability, unrelated tags) unchanged.
- No other key of the armor stack is read or written by module code.

### 4.2 Active oxygen (unchanged)

`arce_space_suit_oxygen` schema 1, 0..2,000 (`SpaceSuitOxygen.java:11-74`). It is not mirrored
anywhere and its schema does not change. The configured active maximum (§6) only limits refills.

### 4.3 Pressure tank: `arce_pressure_tank`, schema 1 (ADR-066 §5.1)

```text
{ schema_version: 1, gas: "EMPTY" | "OXYGEN" | "HYDROGEN", amount: int, maximum: int }
```

- `0 ≤ amount ≤ maximum`; `gas == EMPTY` iff `amount == 0` (proposal: an emptied tank returns to
  EMPTY, so it may later hold the other gas; **OPEN**, owner, since ADR-066 says "nonempty tanks
  cannot switch gas" and is silent about emptied ones).
- `maximum` is snapshotted when the tank is created: tier base × multiplier. **Proposal** tier bases
  1,000 / 2,000 / 4,000 / 8,000 units; multiplier 1..4. Supported maxima are exactly
  `{tier base × m | m ∈ 1..4}` for the tank's own tier; any other saved maximum is unsupported
  (refused, preserved), never clamped.
- Empty tanks acquire gas from charge only (ADR-066 §5.1), that is from the C18a gas charge pad
  under the D4 protocol. No world fluid capability is exposed on an installed tank.
- Two installed tanks may not hold the same gas. Charging an EMPTY tank with gas G is refused if the
  other installed tank already holds G.

### 4.4 Jetpack and upgrades

The jetpack and the upgrades carry no owned gameplay data. Jetpack on/off and NORMAL/HOVER mode are
transient server session state, reset on logout, Level change and equipment change (ADR-066 §5.2:
"off by default", hover reference transient). **OPEN (owner):** whether the mode should persist on
the jetpack item.

### 4.5 Enchanted chest: `arce_enchanted_suit_oxygen`, schema 1

For a native `ArmorItem` chest that no provider claims, count 1, in its native slot and enchanted
with `space_breathing` level 1 (checked per live worn stack): strict root with oxygen 0..2,000, the
same detached debit/refill rules. Other owned NBT untouched. Eligibility is an ADR-025 additive
revision (ADR-066 §5.1); registered external providers keep precedence and their payloads are never
aliased. Enchanted chests accept no modules and no auxiliary tank.

## 5. Mutation rules

### 5.1 Workstation install and remove

- Menu-only operations; the block exposes no item, fluid or armor capability to automation.
- Install: detached copies of the armor stack and the transfer-slot stack; checks: armor is a built-in
  suit piece with a supported (or absent) module root; the module kind is accepted by a free
  position of that slot; per-armor rules; the module's own root is supported; no pending transaction
  (§5.4). Commit: write the new module root to the armor copy and clear the transfer slot copy, then
  replace both live slots only if both live stacks still equal the copies read. Any failure leaves
  both stacks unchanged.
- Remove: the player selects a position; the module moves to the transfer slot if it is empty,
  otherwise to the player's inventory; if neither has room the operation is refused (no drop, no
  destruction).
- Shift-click (`quickMoveStack`) routes through the same install/remove service on detached copies;
  `setChanged` alone is never the commit (ADR-066 §5.1).
- C2S intent: fixed operation enum `INSTALL | REMOVE`, a position index 0..7, the container ID;
  at most 64 bytes, 10 per second per player; menu validity rules of ADR-066 §2.
- Breaking the workstation drops the held armor and module once (two slots maximum). The
  workstation never stores a second copy of any gas.

### 5.2 Refill of active oxygen from an installed oxygen tank (D1)

- Trigger: only when the life-support tick is about to apply a scheduled oxygen debit
  (`PlayerLifeSupportService.applyTick`, the decision lowers oxygen), or an explicit valid refill.
  **OPEN (Root):** whether any explicit refill action exists beyond the existing canister use; this
  draft proposes none.
- Amount: `t = min(target − active, tankAmount, 2,000)`, where
  `target = min(2,000, equipment.suitActiveOxygenMaximum)`; if `t ≤ 0` nothing happens.
- One detached commit on the worn chest stack: tank amount − t, active + t, then the scheduled
  debit, all in the same stack's tag. Readback must equal the computed values. On any mismatch the
  commit is refused and the debit is applied to the unrefilled balance (ADR-025's failed-debit rule
  then applies).
- Queries, HUD reads, simulated calls and tooltips never refill or debit.
- Lowering `suitActiveOxygenMaximum` stops refills above the new target but never removes an
  existing valid active balance.

### 5.3 Hydrogen spend (jetpack)

- Each thrust or hover-correction tick spends exactly 1 unit from the installed HYDROGEN tank in one
  detached commit before the motion is applied (proposal). An empty tank or a refused commit means
  no thrust that tick.

### 5.4 Pending transactions and preservation

- While the chest (or any piece) holds an unreconciled D4 receipt, or the workstation-side transaction
  marker of a running install/remove exists, install, remove and charge are refused. The receipt root
  and its reconciliation belong to Root's D4 work; this contract only reads "pending: yes/no" from it.
- Unknown keys of the armor stack, enchantments, durability and the nested modules' own data are
  preserved by every operation; tests compare the full stack NBT except the changed subtree.
- A stack whose module root is unsupported keeps working as plain armor (its oxygen key still
  applies if valid) but gets no module effects and refuses module operations.

## 6. Configuration

| Path | Side | Type and range | Default | Meaning |
| --- | --- | --- | --- | --- |
| `equipment.suitActiveOxygenMaximum` | COMMON | int 1..2,000 | 2,000 | refill target for active oxygen (ADR-066 §5.1); never erases a stored balance |
| `equipment.pressureTankCapacityMultiplier` | COMMON | int 1..4 | 1 | snapshotted into each new tank's maximum (ADR-066 §5.1) |
| `equipment.jetpackForce` | COMMON | double 0.05..4.0 | 1.3 | thrust increment `0.08 × value` blocks/tick² (proposal, ADR-066 §5.2) |
| `equipment.lowGravityBootsOnly` | COMMON | boolean | false | padded boots cancel fall damage only where the ARCE gravity multiplier < 1 |
| `effects.atmosphericNausea` | CLIENT | boolean | true | enables the ARCE atmospheric fog and nausea modulation on this client only |
| `equipment.classicEnabled` | COMMON | boolean | true | ADR-066 §2 switch; off stops new work and effects, allows safe removal |

All are read per use, not cached across reloads. Legacy differences: `suitTankCapacity` was a float
from 0 upward and `spaceSuitO2Buffer` was in minutes; both ranges are redefined by ADR-066.
Numeric defaults other than those ADR-066 accepts are review inputs.

## 7. Server motion and passive effects

### 7.1 Jetpack (ADR-066 §5.2)

- Eligibility each tick: real survival or adventure player; correctly worn built-in chest with an
  installed jetpack and a supported HYDROGEN tank with gas; not elytra flying, not seated in a rocket,
  not in an elevator transit; `equipment.classicEnabled`; heartbeat received within the last 5 ticks.
- Intents: `TOGGLE`, `CYCLE_MODE` (HOVER only with `hover_upgrade` at HEAD) and a boolean
  `THRUST` heartbeat; at most 10 per second; no velocity, height or position in any packet.
- Motion: on each paid tick add `0.08 × jetpackForce` to vertical velocity, cap at 0.6 blocks/tick
  upward; horizontal assistance with `flight_speed_upgrade` 20 % of the current horizontal input
  direction, cap 0.8 (proposals). Hover holds the reference height captured at mode entry.
- **Technical constraint (OPEN, Root):** vanilla player movement is client-simulated. Two carriers:
  (a) the server changes the player's delta movement on each paid tick and marks it for a motion
  packet, costing at most 20 small S2C packets per second per flying player; (b) a transient
  `ENTITY_GRAVITY` modifier. Option (b) does not compose correctly with
  `CelestialGravityController`, which applies a `MULTIPLY_TOTAL` modifier: an additive jetpack term
  would be scaled by the planet multiplier, so thrust would depend on gravity. This draft recommends
  (a), states its packet cost, and requires a test that thrust is independent of the planet
  multiplier while net motion still loses to strong gravity (ADR-066: "it is not free flight").
- Never set vanilla `mayfly`/`flying` abilities and never accept client velocity.

### 7.2 Leg and boot upgrades

- `bionic_leg_upgrade`: a transient `MOVEMENT_SPEED` modifier (+20 %, proposal) with a fixed UUID,
  present only while sprinting with the upgrade correctly worn and the switch on; removed the same
  tick otherwise. Other modifiers untouched.
- `flight_speed_upgrade`: only scales jetpack horizontal assistance (§7.1).
- `padded_landing_boots`: cancel `LivingFallEvent` damage for the wearer (proposal), after any C18a
  gravity scaling of fall damage; with `lowGravityBootsOnly` only where the server's ARCE gravity
  multiplier for the player's Level or station is < 1. No other damage type is cancelled; client fall
  distance is not faked.

## 8. Summary port and client boundaries

### 8.1 Server equipment summary (Root-owned shared interface)

Immutable record computed on the server from the live worn equipment, recomputed on equipment
change, Level change, login and module operations, never from client data:

| Field | Type | Consumers |
| --- | --- | --- |
| `beaconFinderEnabled` | boolean | C17 finder marker stream (ADR-065) |
| `antiFogVisor`, `earthbrightVisor`, `hoverUpgrade` | boolean | client effects |
| `jetpackInstalled`, `jetpackActive`, `jetpackMode` | boolean, boolean, enum NORMAL/HOVER | HUD, rendering, audio |
| `flightSpeedUpgrade`, `bionicLegUpgrade`, `paddedBoots` | boolean | HUD |
| `oxygenTank`, `hydrogenTank` | optional `(amount, maximum)` ints | HUD |

- Owner copy: all fields, sent to the owning player on change, at most once per tick, ≤ 64 bytes.
- Tracking copy: only the visible fields needed for rendering (jetpack installed/active, visor
  presence), sent to tracking players on change; no gas amounts.
- **OPEN (Root):** carrier and channel. Proposal: a new channel
  `advancedrocketrycommunity:equipment`, protocol 1, discriminators 0 C2S intent (≤ 64 bytes),
  1 S2C owner summary, 2 S2C tracking summary, pinned in `network-protocols.txt`. ADR-065 forbids
  using `classic_controls` for C18 equipment messages.
- The C17 adapter reads only `beaconFinderEnabled` through the model/api port; neither adapter imports
  the other (no C17/C18 cycle).

### 8.2 Key mapping and input

- Registered on the physical client only; pressing it sends `TOGGLE`; a modifier or second key for
  `CYCLE_MODE` is **OPEN** (one key with sneak, or a second mapping).
- While the jump key is held with an active jetpack, the client sends the `THRUST` heartbeat at most
  10 times per second; releasing stops it; the server expires thrust after 5 ticks without heartbeat.

### 8.3 Fog and nausea (client)

- Input: the synchronized body pressure already on the client (`SkySelection` pressure from the
  celestial snapshot), the life-support snapshot's breathability for the player's position, the owner
  summary's `antiFogVisor`, and `effects.atmosphericNausea`.
- Proposal: in the body's ambient atmosphere with pressure above 1.0 atm, scale the terrain fog far
  plane by `1 / (1 + 0.5 × (p − 1))`, bounded to at least 25 % of the vanilla distance; outside
  0.5..2.0 atm add a bounded client-only view wobble. Unknown or missing pressure, a sealed or supplied
  position, the visor, or the setting off: no modulation (never treat unknown as safe air for gameplay,
  and never invent fog either). The curve and thresholds are **OPEN** (owner).
- The existing sky-profile fog in `ClientSkyEvents` stays; the modulation applies after it. Vanilla
  blindness and potion fog are never removed.

### 8.4 Earthbright visor (client)

Brightness lift rendered only while the owner summary says the visor is worn and the client Level is
the actual Space Level; no potion effect is applied or renewed; clears on resource reload, Level
change and summary invalidation.

## 9. Dependencies before atomic implementation

| Dependency | Owner | Blocks |
| --- | --- | --- |
| D4 pad charging protocol or an owner-approved alternative | Root, owner | filling tanks; pending-transaction reads |
| Module-kind table and summary port in model/api | Root | workstation, C17 finder integration |
| Equipment network surface (§8.1) | Root | intents, summaries |
| C16c chemical reactor recipe type | Root (C16c) | `space_breathing` application recipe |
| C17c `beacon_finder` | Root (C17c) | HEAD interoperability fixtures |
| ADR-025 additive revision for enchanted hosts | Root, review | §4.5 |
| C18a gravity-scaled fall damage | Root (C18a) | boot ordering |
| Recipes and art for all items and the block | Root, asset review | delivery |

Suggested atomic leaves once those exist, each with its own TASK: (1) pressure-tank item and schema
with pure codec tests; (2) module-kind table and armor module root codec; (3) workstation block,
menu and install/remove service; (4) refill and hydrogen spend in the life-support tick; (5) jetpack
motion and intents; (6) leg and boot effects; (7) summary and client effects (visors, fog, key);
(8) `space_breathing` with the ADR-025 revision.

## 10. Open items

| ID | Question | Owner |
| --- | --- | --- |
| E-OPEN-1 | Config paths and key mapping names; legacy default key | Root |
| E-OPEN-2 | Upgrade stack size | owner |
| E-OPEN-3 | Emptied tank returns to EMPTY (gas switch after empty) | owner |
| E-OPEN-4 | Jetpack mode persistence | owner |
| E-OPEN-5 | Explicit refill action beyond canisters | Root |
| E-OPEN-6 | Jetpack motion carrier (§7.1) and its packet budget | Root |
| E-OPEN-7 | Equipment channel and summary carrier | Root |
| E-OPEN-8 | `CYCLE_MODE` input | owner |
| E-OPEN-9 | Fog/nausea curve and thresholds | owner |
| E-OPEN-10 | Tier bases, multiplier, thrust, speed and boot numbers (all ADR proposals) | owner review |

This draft passes no build, native, restart, V1, V2 or v1.8 G0-G9 Gate.
