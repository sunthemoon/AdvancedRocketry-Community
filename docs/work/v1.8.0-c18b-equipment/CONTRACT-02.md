# CL18B-EQUIPMENT-02: equipment modules, successor contract draft 02

Date: 2026-10-08. Milestone: v1.8.0 / C18b. Status: **proposed, not frozen**; production stays
`DEPENDENCY_BLOCKED`. Author: Claude (delegated worker, owner-started interactive session).
Task: [TASK-02](TASK-02.md), read in the Root checkout at
`65dd821180f6c0304340fc51d8d1d11df6d29347` (SHA-256
`4864c6aea82a3e5c42ade6d5d7553a3739622e3344dcd27fa24e15f0be48a21b`). Worktree base
`a65dcbf68143ce63af3b2205b0c36af02eaae0e8`; Root source checkpoint
`f9f2d9d2c5eb0de2c9f5d28160ab7804fc57eaef`. Only HUD, client configuration and their tests changed
under `src/` between them; every source line cited here has the same bytes at both.

This draft replaces [CONTRACT-01](CONTRACT-01.md) as the proposal under review; draft 01 stays
unchanged. It implements nothing, accepts no numeric balance, no motion carrier, no fog curve, no
save veto and no R-021 extension, and grants no source task.

## 0. Inputs added since draft 01

| Input | Identity | Use here |
| --- | --- | --- |
| Independent review of draft 01 | [REVIEW-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-independent-20261008-01/REVIEW-01.md), SHA-256 `4d5b33f36d83b85eae58081f3573a4434715215198a042c822d0bc327400f4db` | seven Medium, two Low |
| Oxygen-reserve leaf proposal (immutable) | [PROPOSAL-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-oxygen-reserve-proposal-20261007/PROPOSAL-01.md), SHA-256 `95d6681bc338c915fe42dceb4b6727829ff703418124b5ea542fec6306f4fdc3` | §2 schema and bounds, §3.2 scheduled consumption, §3.3 workstation |
| Its section 3.1 successor | [PROPOSAL-02](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-oxygen-reserve-proposal-successor-20261007/PROPOSAL-02.md), SHA-256 `bfff23386d9b939b757733ad2a7b0763c39294f5a5242cac44ec67d526b0788f` | paired charge interaction (owner decision pending) and native-shape admission |
| Accepted technical wording | [OXYGEN-WITNESS-CLARIFICATION-01](../v1.8.0-c18-contract/OXYGEN-WITNESS-CLARIFICATION-01.md) | stale refusal limited to captured witnesses |
| C18 owner receipt | [OWNER-DECISIONS](../v1.8.0-c18-contract/OWNER-DECISIONS.md) | D1 accepted; D4 still needs technical proof |
| Current status | `docs/status/CURRENT_VERSION.md:489-498` at `65dd8211` | tier capacities/config and paired interaction await the owner; workstation save-veto extension unadmitted; API/HUD buffer 2,000 |

Draft 01 was written without the reserve proposal and invented different root names. This draft
aligns with the reserve proposal wherever both cover the same thing, so Root has one schema line to
review, and extends it only for the parts the reserve proposal leaves out (hydrogen, jetpack,
upgrades, visors, boots, enchantment, summary). Where the two still differ, §11 names the choice.

## 1. Finding dispositions

| Finding | Disposition | Where |
| --- | --- | --- |
| M1 workstation cross-store transfer | **Narrowed.** Module install and remove move items only between the workstation's own two slots (one block entity, one save domain); removal to the player inventory is withdrawn. Player-to-workstation moves are ordinary menu slot moves with vanilla container durability and no stronger claim. The invented "workstation transaction marker" is withdrawn. Insertion-time admission keeps the block entity's root emittable, so this design does not need the unadmitted save veto; if Root wants to hold arbitrary stacks, that veto becomes a blocking dependency. Future or corrupt workstation roots need a Root quarantine mechanism before the block is enabled. | §6 |
| M2 whole-armor copy and component fidelity | **Addressed by adopting** the reserve proposal §2.4 bounded composition (shallow outer map, deep copy of owned subtrees only, identity recheck) plus a component admission policy: exact native shape screen, refusal of components carrying `ForgeCaps`, a lossless save/read/save preflight, and all foreign callbacks before the final recheck. | §4.4, §4.5 |
| M3 refill cannot start from empty active | **Addressed.** The refill trigger is the scheduled debit boundary (old vacuum phase 19) under full admission, independent of the engine having lowered oxygen; the engine then runs on the virtual post-transfer balance without a phase reset. Active 0 with a charged reserve is paid; the same formula as reserve proposal §3.2. | §5.1 |
| M4 four-slot enchantment eligibility | **Addressed as a proposal** for the ADR-025 additive revision: per-slot live eligibility, unstackable count-one native armor in its native slot with level exactly 1, provider and built-in precedence, CHEST-only oxygen, acquisition exclusions. The ADR-025 revision and the C16c recipe stay dependencies. | §7 |
| M5 summary refresh | **Addressed.** A dirty-flag policy covering every authoritative transition, one recomputation per player per tick, tracking-start and invalidation rules, and a bounded periodic recompute. | §9.1 |
| M6 fog cannot tell supplied rooms | **Not resolvable from current inputs; narrowed.** The current snapshot has no supplied bit. Root adds an authoritative controlled-volume bit, or the owner accepts a narrower visual (§9.3). Breathability and client scans are never used to infer it. | §9.3 |
| M7 boots ignore area-field precedence | **Addressed.** Boots use the same resolved ARCE multiplier as `CelestialGravityController` (area field, then position override, then Level profile), read once at the landing tick, never the final attribute value. Ordering with the C18a fall scaler is stated. | §8.3 |
| L1 tank round-trip oracle | **Fixed.** Valid states and preserved invalid states are separated (§4.2); TEST-DESIGN-02 splits them. | §4.2 |
| L2 refill target called accepted | **Fixed.** Only the 2,000 API/HUD capacity and the D1 mechanism are accepted; every default, range and tier is a proposal. | §2, §10 |

## 2. What is accepted, what is Root's, what is the owner's

| Class | Content |
| --- | --- |
| Accepted (kept unchanged) | fixed external/API/HUD active capacity 2,000 on `arce_space_suit_oxygen` schema 1; D1 mechanism: one finite built-in auxiliary oxygen reserve, lossless one-commit transfer of at most the missing amount before a scheduled debit, never mirrored; eight module positions with two shared HEAD positions; C17 `beacon_finder` identity owned by C17c; ADR-025 provider precedence and the 2,000 external bound; gas conservation; queries, HUD and simulations never mutate; unrelated data preserved; no automation armor editor; no fake charging |
| Root technical dependencies | D4 pad durability or an approved alternative; module catalog and summary port in `model/api`; equipment network surface; workstation persistence and quarantine; component admission helper; ADR-025 additive revision; controlled-volume bit for fog; gravity query port; C18a fall ordering; C16c recipe type; C17c finder fixtures; motion carrier evidence |
| Owner product choices | tier capacities and multiplier range; refill target default; jetpack force, speed and boot numbers; emptied-tank gas switching; upgrade stack size; jetpack mode persistence; toggle and cycle inputs; fog and nausea curves; paired canister charge interaction (reserve proposal §3.1) |
| Author proposals in this draft | root layout of §4, admission rules of §4.5, refill ordering of §5, workstation narrowing of §6, enchantment eligibility of §7, boot rule of §8.3, refresh policy of §9.1, leaf split of §12 |

## 3. Unit mapping and module table

The twenty-unit mapping of draft 01 §2 is unchanged (the review confirmed exactly twenty rows, each
once, no tools and no C17 finder added). The slot table of draft 01 §3 is unchanged:

| Armor slot | Positions | Accepted kinds | Per-armor rule |
| --- | ---: | --- | --- |
| HEAD | `head_0`, `head_1` | `hover_upgrade`, `anti_fog_visor`, `earthbright_visor`, C17 `beacon_finder` | one of each kind |
| CHEST | `chest_jetpack` | `jetpack` | — |
| CHEST | `chest_oxygen`, `chest_hydrogen` | the four pressure tanks | the oxygen position holds only an EMPTY or OXYGEN tank, the hydrogen position only an EMPTY or HYDROGEN tank |
| LEGS | `legs_0`, `legs_1` | `flight_speed_upgrade`, `bionic_leg_upgrade` | one of each kind |
| FEET | `feet_0` | `padded_landing_boots` | — |

Change from draft 01: the two tank positions are named by gas (as reserve proposal §2.3 reserves
"the second pressure position" for hydrogen), which makes "one oxygen, one hydrogen" a position
rule instead of a cross-entry check. Only built-in `space_suit_*` pieces accept modules.

## 4. Owned payloads

### 4.1 Armor root

Aligned with reserve proposal §2.3: private `arce_classic_equipment` on built-in suit pieces, exact
`schema_version` INT 1, and optional position records. Draft 01's name `arce_suit_modules` is
withdrawn.

```text
arce_classic_equipment: { schema_version: INT 1,
  <position>: { item: STRING, count: BYTE 1, tag: COMPOUND (optional) } ... }
```

- Allowed position keys depend on the piece's slot (§3); any other key makes the root unsupported.
- Missing root: legacy suit, no modules; a read never writes an empty root.
- The reserve proposal's `oxygen_reserve` key is the same record as `chest_oxygen` here. **E2-OPEN-1
  (Root):** pick one schema-1 key set covering all positions before the first source leaf. Two
  different schema-1 shapes must never ship; extending later needs a version and migration.

### 4.2 Pressure tank root

Aligned with reserve proposal §2.2: `arce_pressure_tank` with exactly `schema_version` INT 1, `gas`
BYTE (0 EMPTY, 1 OXYGEN, 2 HYDROGEN), `stored_units` INT, `maximum_units` INT. Draft 01's string gas
field is withdrawn.

| State | Condition | Treatment |
| --- | --- | --- |
| uninitialized empty | root missing on a registered tank item | zero gas, no capacity authority; reads leave it missing |
| valid empty | gas 0, stored 0, maximum in the item's supported set | accepted; round trip identical |
| valid charged | gas 1 or 2, 1 ≤ stored ≤ maximum, maximum supported | accepted; round trip identical |
| unsupported | gas 0 with stored > 0; gas 1 or 2 with stored 0; stored < 0 or > maximum; maximum not in `{base × m}`; wrong tag type; missing or extra key | no gas; every write refused; bytes preserved |
| future | `schema_version` > 1 | as unsupported |

Full depletion sets gas to EMPTY and keeps `maximum_units` (reserve proposal §2.2). Whether an
emptied tank may then take the other gas is an owner choice (E-OPEN-3); ADR-066 forbids only a
nonempty tank switching gas.

### 4.3 Active oxygen and the enchanted chest

`arce_space_suit_oxygen` schema 1, 0..2,000, unchanged. The enchanted chest root
`arce_enchanted_suit_oxygen` is as draft 01 §4.5, governed by §7.

### 4.4 Bounded composition (reserve proposal §2.4, adopted)

- Owned trees on one armor stack (active root plus `arce_classic_equipment`) together: at most
  16,384 encoded native NBT bytes, depth 16, 256 tag nodes, checked after an exact native-shape screen
  (no foreign tag classes, null keys or children, cycles, mixed lists or non-faithful values).
- The armor stack's outer tag map: at most 256 keys and 16,384 total key-encoding bytes. Operations
  shallow-copy the outer map and deep-copy only the screened owned subtrees. Unrelated values are kept
  by reference without traversal and their key-to-reference identity is rechecked before
  publication.
- Over-budget outer framing refuses a **new module operation** unchanged. Baseline suit reads and
  scheduled debits keep working as today.
- Never an unrestricted whole-stack `ItemStack.copy()` of armor to prepare an update.

### 4.5 Component admission

A module or tank stack is admitted into a position record only when all hold, checked during
preparation:

1. registered stable ID of the position's kind; count 1;
2. its item tag passes the exact native-shape screen and the component ceiling (the same 16,384 /
   16 / 256, with the record's framing included in the armor ceiling of §4.4);
3. its native saved form contains no `ForgeCaps` key of any type: a component with attached
   serializable capabilities is refused, not flattened (the existing canister rule,
   `CanisterItemSafety.java:16-25`, is the precedent; that package-private helper is not reused);
4. **lossless preflight:** saving the stack, building the record, rebuilding a stack from the
   record and saving it again gives a byte-identical compound;
5. every foreign callback (native save, capability lookup) runs during preparation; after the final
   identity recheck only the prepared tags are swapped, with no further callback.

Removal reverses the record into a stack with the same preflight. A record that fails the preflight
on removal stays installed, the position reports `REPAIR_REQUIRED`, and nothing is dropped or
deleted.

## 5. Gas transfers

### 5.1 Scheduled refill (D1; replaces draft 01 §5.2)

Same rule as reserve proposal §3.2:

- **Boundary:** the existing service's scheduled debit tick, where the old vacuum phase is 19
  (`PlayerLifeSupportEngine.java:24-26`, interval 20).
- **Admission:** nonexempt player, complete suit (`COMPLETE_SUIT_PIECES`), base atmosphere not
  breathable and volume not `BREATHABLE` (PENDING keeps the engine's existing behaviour), current
  built-in chest, `equipment.classicEnabled`, supported active root, supported `chest_oxygen` record
  holding OXYGEN with stored > 0.
- **Transfer:** `x = min(max(0, T − A), R)` with active A, target T, reserve R; 0 ≤ x ≤ 2,000.
- If x > 0, the unchanged engine runs with virtual active `A + x` and the same phase; it debits
  exactly one. One detached commit publishes active `A + x − 1` and reserve `R − x` on the same armor
  stack; both owned snapshots, the worn reference and count, the enabled state and T are rechecked
  first.
- If x = 0, the existing active-only path runs; the module root is not written.
- A failed combined commit preserves both roots and applies the existing failed-debit decision
  (`PlayerLifeSupportService.java:94-99`: recount, evaluate with zero oxygen) at the original phase.
- Consequences: A = 0 with R > 0 is paid at the boundary (the draft-01 gap); A = 1 with T = 1 debits
  to 0 without transfer, and the next boundary transfers 1 and debits it. Between boundaries the
  engine still reports `OXYGEN_EMPTY` at A = 0 (no damage, no debit); the HUD can show the reserve
  from the summary.
- One operation never refills twice. Ordinary ticks, protected environments, partial suits, queries,
  tooltips, HUD and simulations never transfer.

### 5.2 Charging tanks

Pad charging waits for D4 or an owner-approved alternative. The paired canister charge of reserve
proposal §3.1 (successor PROPOSAL-02) is pending the owner's product decision and is not selected
here. Neither is assumed to cover the workstation. Until one is accepted, no tank can gain gas, so
reserve and jetpack features cannot be delivered.

### 5.3 Hydrogen spend

Each paid jetpack tick spends exactly one unit from `chest_hydrogen` in one detached commit before
the motion is applied; an empty tank or a refused commit means no thrust that tick. The commit order
relative to motion is part of the open motion contract (§8.1).

## 6. Workstation (narrowed)

- Block entity with two count-one slots: armor (0) and transfer (1); persisted root
  `arce_suit_workstation` schema 1 as in reserve proposal §3.3; no installed-gas mirror.
- **Install:** transfer-slot component into a free position of the armor in slot 0. **Remove:** a
  position's component into the **empty** transfer slot only; if slot 1 is occupied, refuse
  unchanged. Both slots are in this one block entity, so each operation changes one save domain.
- Player moves between the inventory and slots 0/1 (clicks, shift-click) are ordinary menu moves with
  vanilla container durability. This contract adds no marker and claims nothing stronger for them.
  Shift-click is planned against bounded detached copies and commits a complete slot plan or nothing.
- **Insertion admission:** a stack enters slot 0 or 1 only if it passes §4.4 and §4.5 limits and the
  whole `arce_suit_workstation` root would stay within 65,536 bytes, depth 16, 1,024 nodes (reserve
  proposal §3.3). Operations can only shrink or keep within those bounds. The block entity therefore
  never needs to omit a root at save time, and this design does not use the unadmitted save veto. If
  Root instead wants the workstation to hold any stack, the `GuardedChunkSaves` veto and its R-021
  scope become a blocking dependency, with object, chunk and Level impact, duration, log volume and
  recovery stated for Root's ADR and risk review.
- **Future or corrupt root read from disk:** no items materialize, the menu shows `REPAIR_REQUIRED`,
  nothing is dropped, and nothing is erased. Breaking, explosion or a mover would destroy such a
  block's retained bytes; Root must cover the block with a reviewed quarantine mechanism (as the C16a
  hatches use) before enabling it (E2-OPEN-2). Without one, the block is not enabled.
- **Ordinary destruction:** each supported held stack drops exactly once; there is no pending state
  because install and remove complete within one server operation.
- D4 interaction: if Root's D4 work adds any pending root to armor, module operations must refuse
  while it is present. D4's pad protocol does not cover the workstation.
- Intents and menu admission: as reserve proposal §3.3 (loaded block entity only, same Level,
  ≤ 8 blocks, fixed enum, ≤ 10 per second, ≤ 64 bytes, vanilla menu button route, no new channel for
  the workstation).

## 7. `space_breathing` eligibility (proposal for the ADR-025 additive revision)

- **Per live worn stack, per slot:** a piece counts toward the four-piece suit if it is a built-in
  suit piece of that slot (first), else claimed by a registered external provider mapping for that
  slot (second), else an **enchanted native piece**: an `ArmorItem` whose native slot is that slot,
  maximum stack size 1 and count 1, and `space_breathing` level exactly 1.
- **Precedence and faults:** a provider-claimed item is never evaluated by the enchantment path, even
  if its provider faults or its payload is unsupported; the fault leaves that piece uncounted. No
  item ID is registered dynamically; eligibility is never a static per-item registration.
- **Mixing:** any combination of built-in, external and enchanted pieces counts when each piece is
  eligible in its own slot (for example built-in helmet, enchanted iron chestplate, built-in legs and
  boots).
- **Oxygen:** only the CHEST owns oxygen: built-in chest its active root, external chest its provider
  payload, enchanted chest `arce_enchanted_suit_oxygen` (0..2,000, same commit rules). Enchanted
  HEAD, LEGS and FEET own nothing. Enchanted pieces accept no modules and no reserve.
- **Acquisition:** only the C16c chemical recipe applies level 1. Proposed enchantment flags: not
  discoverable, not tradeable, treasure-only, not allowed at the enchanting table, not allowed on
  books; so random-enchant loot functions and librarians never produce it. Anvil merging of two
  already enchanted pieces is vanilla behaviour and creates no new source.
- The ADR-025 revision and the C16c recipe type remain unresolved dependencies, not source
  permission. The current `SuitEquipmentService.countPieces` (`SuitEquipmentService.java:30-39`)
  counts only built-in and registered pieces.

## 8. Motion and passive effects

### 8.1 Jetpack (OPEN)

Draft 01's recommended carrier is withdrawn as a recommendation. The verified fact stands:
`CelestialGravityController` applies its multiplier as a transient `MULTIPLY_TOTAL`
`ENTITY_GRAVITY` modifier with a fixed ID (`CelestialGravityController.java:22, 92-124`), so a simple
additive gravity term would be scaled by it. Neither candidate (server delta-movement with motion
packets, or an attribute composition) is proven. Before any carrier is adopted, Root needs real
client and server evidence that freezes: paid-tick ordering (hydrogen commit, then motion), the
bounded hover correction, horizontal input authority, cap composition with gravity, heartbeat
expiry, and the packet budget. Intents stay as ADR-066 §5.2: `TOGGLE`, `CYCLE_MODE`, boolean
heartbeat, ≤ 10 per second, expiry after 5 ticks, never velocity or position. Vanilla `mayfly` and
`flying` are never set.

### 8.2 Bionic legs

As draft 01 §7.2: a transient `MOVEMENT_SPEED` modifier with a fixed ID while sprinting with the
upgrade worn and the switch on; removed the same tick otherwise; other modifiers untouched. The
+20 % value is a proposal.

### 8.3 Padded landing boots

- Cancel `LivingFallEvent` damage for a wearer of `feet_0 = padded_landing_boots`.
- With `equipment.lowGravityBootsOnly` true, cancel only when the **resolved ARCE multiplier** at the
  landing tick is below 1.0. It is resolved exactly as `CelestialGravityController.multiplierAt`
  does for players (`CelestialGravityController.java:67-78`): the player's area field, else the
  position override (stations), else the Level profile, else 1.0. A multiplier of exactly 1.0 is not
  low gravity.
- Source of the value: a Root-owned pure query port exposing that resolution (E2-OPEN-3).
  Alternative if Root prefers: read only the controller's own modifier (ID
  `6fef66cc-a721-4b58-9be5-c8b07831eb0f`) amount + 1, absent meaning 1.0. Never read the final
  `ENTITY_GRAVITY` value, which includes other mods' modifiers.
- Order: the C18a gravity fall scaler (not yet implemented; no `LivingFallEvent` handler exists in
  `src/main/java`) runs first; the boots handler runs after it at a lower priority and does not
  receive cancelled events. Other damage types are untouched; client fall distance is not faked.

## 9. Summary and client boundaries

### 9.1 Summary refresh policy

The summary record (draft 01 §8.1 fields) is recomputed from the live worn equipment:

- **Dirty marks**, set by the server code that causes them: equipment change event for any armor
  slot (workstation operations edit only unworn armor and need no mark of their own; wearing that
  armor later raises this mark); reserve transfer or debit commit;
  hydrogen spend commit; jetpack toggle, cycle, heartbeat start and heartbeat expiry; depletion to
  EMPTY; a root found unsupported during any read; `equipment.classicEnabled` or other COMMON
  equipment configuration reload (marks all online players, processed at most 64 players per tick);
  login, respawn (`Clone`) and Level change.
- **Recompute:** at most once per player per server tick, at the end of the tick, when dirty.
- **Safety recompute:** every 40 ticks per player in round-robin, so a root changed by an unknown
  writer is noticed within 2 seconds without an equipment-swap event.
- **Send:** owner copy only when it differs from the last copy sent to that owner; tracking copy
  (no gas values) when it differs, and always to a new tracker on `PlayerEvent.StartTracking`.
- **Invalidation:** on logout the last-sent memo is dropped; on Level change and respawn the next
  recompute sends a full copy.
- Bounds: owner copy ≤ 64 bytes; tracking copy carries jetpack installed/active and visor presence
  only.

### 9.2 Key mapping and input

As draft 01 §8.2; names and the `CYCLE_MODE` input remain proposals and owner choices.

### 9.3 Fog and nausea

Facts: `AtmosphereLevelService.breathabilityAt` returns `BREATHABLE` for ambient breathable air and
for a controlled volume alike (`AtmosphereLevelService.java:223-228`); the engine returns
`BREATHABLE_ENVIRONMENT` before it looks at the volume (`PlayerLifeSupportEngine.java:11-16`); the
status packet carries status, breathability, pieces and oxygen only
(`LifeSupportStatusPacket.java:28-34`). A dense outdoor position and a supplied room on a breathable
body therefore look the same to the client.

Two ways forward, for Root and the owner:

1. **Root dependency (proposed):** one authoritative `controlled` bit for the player's eye position,
   computed by the server from `controlledAt`, sent with the life-support status (a schema bump of that
   packet) or in the owner summary. Modulation then requires: setting on, no `anti_fog_visor`,
   synchronized body pressure known, status not PENDING, and `controlled` false.
2. **Narrower visual (owner choice):** modulate by ambient body pressure only, documented as also
   applying inside supplied rooms on such bodies.

Missing or PENDING data means no modulation. The curve and thresholds stay owner choices (E-OPEN-9).
The existing sky-profile fog stays; vanilla blindness and potion fog are never removed.

### 9.4 Earthbright visor

As draft 01 §8.4.

## 10. Configuration (all proposals except the accepted capacity)

| Path | Range | Proposed default | Status |
| --- | --- | --- | --- |
| `equipment.classicEnabled` | boolean | true | ADR-066 §2 switch |
| active refill target | int 1..2,000 | 2,000 | proposal; the reserve proposal names it `equipment.spaceSuitO2Buffer`, draft 01 `equipment.suitActiveOxygenMaximum` (E-OPEN-1) |
| tank capacity multiplier | int 1..4 | 1 | proposal; names as above (`equipment.suitTankCapacity` versus `equipment.pressureTankCapacityMultiplier`) |
| tier bases | 1,000 / 2,000 / 4,000 / 8,000 | — | ADR-066 proposal; owner choice |
| `equipment.jetpackForce` | double 0.05..4.0 | 1.3 | proposal; motion OPEN |
| `equipment.lowGravityBootsOnly` | boolean | false | proposal |
| `effects.atmosphericNausea` (CLIENT) | boolean | true | proposal |

Only the 2,000 API/HUD capacity is accepted. Lowering the target never erases a valid active
balance; lowering the multiplier never changes saved maxima.

## 11. Dependencies and remaining choices

| ID | Item | Owner | Blocks |
| --- | --- | --- | --- |
| D-1 | D4 pad durability or an approved alternative; paired canister charge decision | Root; owner | every tank fill, so reserve and jetpack delivery |
| D-2 | module catalog and summary port in `model/api`; equipment network surface | Root | workstation, summary, C17 finder |
| D-3 | workstation persistence and quarantine (§6) | Root | workstation enablement |
| D-4 | component admission helper (§4.5) | Root | install/remove |
| D-5 | ADR-025 additive revision; C16c recipe type | Root, review | `space_breathing` |
| D-6 | controlled-volume bit (§9.3) or the owner's narrower visual | Root; owner | fog |
| D-7 | gravity query port (§8.3); C18a fall scaler | Root | boots |
| D-8 | motion carrier evidence (§8.1) | Root | jetpack |
| D-9 | C17c finder fixtures; recipes and art | Root, asset review | delivery |
| E2-OPEN-1 | one schema-1 key set for `arce_classic_equipment` (§4.1) | Root | all module leaves |
| E2-OPEN-2 | quarantine mechanism for the workstation (§6) | Root | workstation |
| E2-OPEN-3 | gravity query port versus reading the controller's modifier | Root | boots |
| E-OPEN-1..10 | as draft 01 §10 (names, stack size, emptied tank, mode persistence, refill action, carrier, channel, cycle input, fog curve, numbers) | Root / owner | — |

## 12. Proposed implementation leaves (not permission)

Each needs its own TASK after freeze and the listed dependencies; write scopes are disjoint.

| Leaf | Depends on | Write scope (new files unless noted) |
| --- | --- | --- |
| L-a pure reserve transition (as the [readiness report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-oxygen-implementation-readiness-20261007/REPORT-01.md) suggests) | D1 only | `atmosphere/life/SuitReserveTransition.java` and its test |
| L-b tank and armor root codecs | E2-OPEN-1, owner tiers | `equipment/model/` codec classes and tests |
| L-c component admission helper | D-4 | `equipment/model/ComponentAdmission.java` and tests |
| L-d workstation block, menu, persistence | D-2, D-3, L-b, L-c | `equipment/workstation/` package; Root registers block, item, menu |
| L-e scheduled refill adapter | L-a, L-b, D-1 for any filled tank | changes inside the life-support service, Root-integrated |
| L-f summary and refresh | D-2 | `equipment/summary/` package; Root wires the channel |
| L-g boots and legs | D-7, L-b | `equipment/effects/` package |
| L-h jetpack | D-8, L-f | `equipment/motion/` package |
| L-i client visors, fog, key | L-f, D-6 | `client/equipment/` package |
| L-j `space_breathing` | D-5 | `equipment/enchant/` package and the ADR-025 revision |

## 13. Impact and Gate

No source, test, registry, configuration, network, save, asset, ADR, risk, ledger or status change.
No S1, S2, V1, V2 or content result. This draft passes no v1.8 G0-G9 Gate.
