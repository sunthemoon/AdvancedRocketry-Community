# CL18B-EQUIPMENT-02: test design draft 02

Date: 2026-10-08. Status: **planned cases only; none executed**. Derives from
[CONTRACT-02](CONTRACT-02.md) ("C§n"); replaces [TEST-DESIGN-01](TEST-DESIGN-01.md) as the proposal,
which stays unchanged.

Levels: **A0** pure JUnit (codecs, the pure reserve transition, decisions; NBT through the
repository's `MinecraftBootstrap` where needed); **A1** GameTest with real server players where
possible; **S1** packaged dedicated server with restarts; **S2** forced-stop cuts; **V1/V2** real
clients. A schema round trip is never durability evidence. Expected values were computed by hand
from C§4-C§9; tier numbers are the ADR-066 proposal values and change if the owner changes them.

## 1. Codecs and preservation (A0)

| ID | Input | Expected |
| --- | --- | --- |
| P01 | `low_pressure_tank` root: gas 0, stored 0, maximum 1,000 | valid empty; round trip byte-identical |
| P02 | gas 1 (OXYGEN) and gas 2 (HYDROGEN), each at stored 1, 999, 1,000, maximum 1,000 | valid charged; round trip byte-identical |
| P03 | tank item without the root | uninitialized empty: zero gas; reading writes nothing |
| P04 | each of: gas 0 stored 5; gas 1 stored 0; stored −1; stored 1,001 with maximum 1,000; maximum 1,500; `gas` as STRING; `stored_units` missing; an extra key | unsupported: no gas, every write refused, bytes unchanged |
| P05 | `schema_version` 2 | future: as P04 |
| P06 | supported maxima | low {1,000; 2,000; 3,000; 4,000}, pressure {2,000; 4,000; 6,000; 8,000}, high {4,000; 8,000; 12,000; 16,000}, super high {8,000; 16,000; 24,000; 32,000}; any other value unsupported |
| P07 | chest root with `chest_oxygen` only; with all three chest positions; helmet with `head_0` and `head_1` | valid |
| P08 | chest root with a `legs_0` key; an unknown key; a record with count 2; an unknown item ID; a HYDROGEN tank in `chest_oxygen`; a record whose stack saves with `ForgeCaps` | unsupported; preserved; no module effect |
| P09 | owned trees at exactly 16,384 bytes, depth 16, 256 nodes; then one over each | accepted; refused before any copy |
| P10 | armor outer tag with 256 keys and 16,384 key bytes; then 257 keys | module operation accepted; then refused unchanged while a scheduled debit on that armor still lowers active oxygen by 1 |
| P11 | small owned root plus one unrelated key holding a 1 MiB nested compound | module operation succeeds; the unrelated value is the same object reference and byte-equal afterwards; it was not traversed (test hook counts visits: 0) |
| P12 | unrelated value replaced by a test hook between preparation and publication | publication refused; both slots unchanged |
| P13 | components: plain tank; tank with `ForgeCaps`; a component whose save/read/save differs (fixture) | admitted; refused; refused |
| P14 | installed record whose rebuilt stack saves differently (fixture) on removal | stays installed; position `REPAIR_REQUIRED`; nothing dropped |
| P15 | enchanted chest root 0, 2,000; 2,001; −1; schema 2 | valid; others unusable and preserved |
| P16 | built-in suit without `arce_classic_equipment` | valid legacy suit; no modules; reading writes nothing |

## 2. Scheduled refill and gas conservation (A0 transition; A1 service)

Boundary means the tick whose old vacuum phase is 19. T = 2,000 unless stated.

| ID | State before the tick | Expected after |
| --- | --- | --- |
| G01 | boundary; A 1,500; R 8,000 | x 500; active 1,999; reserve 7,500; total 9,500 → 9,499 |
| G02 | boundary; A 0; R 8,000 | x 2,000; active 1,999; reserve 6,000; status `SUIT_OXYGEN`; no damage |
| G03 | boundary; A 0; tank EMPTY | no transfer; `OXYGEN_EMPTY`; damage 2.0 |
| G04 | T 1; boundary 1 with A 1, R 100; boundary 2 | boundary 1: x 0, active 0, reserve 100, no damage; boundary 2: x 1, active 0, reserve 99, no damage |
| G05 | non-boundary tick; A 0; R 100 | no transfer; no damage; `OXYGEN_EMPTY` |
| G06 | boundary; 3 suit pieces; R 100 | no transfer; existing `PARTIAL_SUIT` damage |
| G07 | boundary; unsupported active root | no transfer; existing failed path; module root bytes unchanged |
| G08 | creative and spectator | `EXEMPT`; no transfer |
| G09 | base atmosphere breathable; breathable volume | no transfer; phase 0 |
| G10 | boundary; volume PENDING; complete suit; A 0; R 50 | x 50; active 49; reserve 0 and gas EMPTY, maximum unchanged |
| G11 | boundary; chest replaced by a test hook between prepare and commit | both roots unchanged; decision with zero oxygen: damage 2.0; phase not reset |
| G12 | 1,000 HUD reads, tooltip renders, simulations | zero byte changes anywhere |
| G13 | boundary; T 500; A 1,800 | x 0; active 1,799 |
| G14 | seeded random sequence of 10,000 boundaries, non-boundaries, protected ticks and hydrogen spends | at every step: active + reserve + hydrogen = initial − oxygen debits − hydrogen spends; never negative; never above maxima |
| G15 | `equipment.classicEnabled` false; boundary; A 0; R 100 | no transfer; existing active-only path (damage 2.0) |

## 3. Workstation (A1; S2 where noted)

| ID | Case | Expected |
| --- | --- | --- |
| W01 | install each kind into its position | record written; transfer slot empty |
| W02 | install into an occupied position | refused; both slots unchanged |
| W03 | second finder; third HEAD module; duplicate kind | refused unchanged |
| W04 | HYDROGEN tank into `chest_oxygen`; OXYGEN tank into `chest_hydrogen`; EMPTY tank into either | refused; refused; accepted |
| W05 | remove with transfer slot empty; with it occupied | moved once; refused unchanged (no inventory fallback) |
| W06 | shift-click with full destination; with room | nothing changes; complete plan committed |
| W07 | insert an armor stack whose workstation root would exceed 65,536 bytes | refused at insertion; stack stays with the player |
| W08 | external-provider armor or enchanted armor in slot 0, then install | install refused unchanged |
| W09 | break with supported stacks | each drops once |
| W10 | hopper, pipe and other automation adjacent | no capability; nothing moves |
| W11 | intent at 9 blocks, other Level, closed menu, wrong container ID, unknown operation, position 8, 65-byte payload, 11 intents in one second | each rejected; no change; no chunk load |
| W12 | tank gas and unrelated module tags through install then remove | byte-equal stack |
| W13 | (S2) forced stop after an install commits in memory and before the chunk saves | after restart the block entity is wholly before or wholly after; exactly one copy of the module |
| W14 | workstation root `schema_version` 2 in a fixture world | no items materialize; `REPAIR_REQUIRED`; nothing dropped or erased; the block stays disabled until E2-OPEN-2 is met |

## 4. Enchantment eligibility (A0 decisions; A1)

| ID | Worn set | Expected pieces / oxygen owner |
| --- | --- | --- |
| E01 | built-in helmet, legs, boots; unenchanted iron chestplate | 3; none |
| E02 | as E01 with the chestplate enchanted level 1 | 4; `arce_enchanted_suit_oxygen` on the chestplate |
| E03 | chestplate at level 2 (fixture) | 3 |
| E04 | enchanted chestplate in the HEAD slot (command) | not counted in HEAD; 3 |
| E05 | four enchanted native pieces | 4; only the chestplate's root is ever read or written |
| E06 | a provider-claimed chestplate with the enchantment | provider path only; with the provider faulting, the piece is not counted and the enchantment is not used |
| E07 | enchanted chestplate in the workstation, install a module | refused |
| E08 | enchanting-table offers, librarian trades and `enchant_randomly` loot over 10,000 seeded rolls | `space_breathing` never produced |

## 5. Motion and passive effects (A1)

Carrier-dependent thrust cases wait for the motion contract (C§8.1).

| ID | Case | Expected |
| --- | --- | --- |
| M01 | heartbeat packet with any extra field | rejected (strict decoding) |
| M02 | 20 heartbeats in one second | 10 accepted |
| M03 | elytra flying, rocket seat, elevator transit, EMPTY hydrogen, unsupported tank, switch off | no paid tick, no hydrogen spent |
| M04 | `CYCLE_MODE` to HOVER without `hover_upgrade` | refused |
| M05 | any sequence | `mayfly` and `flying` never set by the jetpack |
| B01 | boots; flag off; 20-block fall on Earth | no fall damage |
| B02 | flag on; Earth (resolved 1.0); Moon (resolved < 1) | damage; none |
| B03 | flag on; area field 0.5 on Earth | none (field precedence) |
| B04 | flag on; area field 1.5 on the Moon | damage |
| B05 | flag on; station position override below 1 | none |
| B06 | flag on; Earth; another mod's ×0.5 `ENTITY_GRAVITY` modifier | damage (foreign modifier not used) |
| B07 | boots and a non-fall damage source | damage unchanged |
| B08 | a stand-in scaler at normal priority cancels the event | the boots handler is not called |
| L01 | bionic legs sprint, stop, unequip, switch off | modifier only while sprinting and worn; removed the same tick; foreign modifiers untouched |

## 6. Summary (A0 encoding; A1)

| ID | Case | Expected |
| --- | --- | --- |
| S01 | finder installed in a helmet, helmet then worn | `beaconFinderEnabled` true in the summary sent after that tick |
| S02 | same worn chest, boundary transfer | owner copy shows the new reserve amount that tick |
| S03 | jetpack toggle accepted; heartbeat stops | `jetpackActive` true; false within 6 ticks |
| S04 | `classicEnabled` set false by config reload with 200 players online | every summary updated within 4 ticks (64 per tick) |
| S05 | root changed by a test writer with no event | noticed within 40 ticks |
| S06 | new tracking player | receives the tracking copy on start tracking; it has no gas fields |
| S07 | logout and login | full owner copy after login |
| S08 | 10 dirty marks in one tick | one recompute |
| S09 | owner copy encoding | at most 64 bytes; unknown enum value rejected on decode |

## 7. Fog and client (V1 unless noted)

| ID | Case | Expected |
| --- | --- | --- |
| F01 | with the controlled bit (C§9.3 option 1): dense breathable outdoor position, supplied room on the same body | modulation outdoors only |
| F02 | with option 2 | both modulated (documented limitation) |
| F03 | PENDING status; missing pressure | no modulation |
| F04 | visor worn; setting off | no modulation |
| F05 | vanilla blindness | unaffected |
| F06 | room supply ends | modulation starts after the status update |
| C01 | earthbright visor in the Space Level and on the Moon | brighter only in Space; clears on reload and Level change |
| C02 | V2: one client wearing a jetpack, another watching | watcher sees installed and active; no gas values |

## 8. Durability (S1, S2)

| ID | Level | Case | Expected |
| --- | --- | --- | --- |
| D01 | S1 | modules on worn armor and armor held in the workstation; restart | roots intact |
| D02 | S1 | 600 ticks of boundaries with transfers; restart | values equal the last saved state; conservation holds |
| D03 | S2 | forced stop after a transfer commit and before the player file save | after restart the chest is before or after the commit, never mixed; total gas not increased |
| D04 | S2 | W13 | as W13 |
| D05 | S2 | pad charging cuts | when D4 exists |
| D06 | S1 | unsupported and future roots in a fixture world | preserved through two restarts |

## 9. Not covered and not executed

Recipes, art, JEI, the charge pad, tools and the HUD layout belong to other tasks. None of the cases
above has been executed. The only checks run for this draft are the static text, hash and link
checks recorded in [HANDOFF-02](HANDOFF-02.md).
