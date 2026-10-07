# CL18B-EQUIPMENT-01: test design draft 01

Date: 2026-10-07. Status: **planned cases only; none executed**. Derives from
[CONTRACT-01](CONTRACT-01.md) ("C§n" below). Levels: **A0** pure JUnit (codecs and decisions with
no Minecraft runtime, or with the repository's `MinecraftBootstrap` where NBT types are needed);
**A1** Forge GameTest with real server players where possible; **S1** packaged dedicated server
with restarts; **S2** forced-stop cuts; **V1/V2** real clients. A plain schema round trip never
counts as durability evidence.

## 1. Codecs and preservation (A0)

| ID | Input | Expected |
| --- | --- | --- |
| P01 | each tank tier created with multiplier 1..4 | `maximum = base × m`; `gas EMPTY`, `amount 0` |
| P02 | tank root round trip for EMPTY, OXYGEN, HYDROGEN at 0, 1, max−1, max | identical root |
| P03 | `amount > maximum`, negative amount, `gas EMPTY` with amount > 0, unknown gas string, wrong tag types, missing keys, extra key | unsupported: read gives no gas, write refused, original bytes unchanged |
| P04 | `schema_version` 2 | future: no gas, refused, preserved |
| P05 | saved maximum not in `{base × 1..4}` for that tier | unsupported, not clamped (C§4.3) |
| P06 | module root with 0, 1, 2, 3 entries on the right slot | accepted |
| P07 | wrong position for slot, duplicate position, two of one kind, two finders, two tanks with the same gas, entry stack count 2, unknown item ID, nested stack with oversized NBT | each unsupported; preserved |
| P08 | module root exactly 16,384 bytes / depth 16 / 256 nodes, and one over each | at the limit accepted; over refused before copying |
| P09 | armor stack with unrelated custom tags, enchantments, damage | every module operation preserves them byte for byte (compare whole tag minus the changed subtree) |
| P10 | built-in suit with no module root (pre-v1.8 item) | valid, no modules, oxygen unchanged |
| P11 | enchanted-chest root valid 0..2,000; 2,001; negative; future | valid; others unusable and preserved |

## 2. Gas conservation and query purity (A0 decisions, A1 server)

| ID | Case | Expected |
| --- | --- | --- |
| G01 | active 1,500, target 2,000, oxygen tank 8,000, debit due | one commit: tank 7,500, active 2,000 − debit; sum before = sum after + debit |
| G02 | active 1,999, tank 1 | `t = 1` |
| G03 | tank 0 | no refill; debit from active |
| G04 | no debit due this tick | no refill (refill happens only before a scheduled debit) |
| G05 | creative, spectator, breathable position | no debit, no refill |
| G06 | 1,000 HUD reads, tooltip renders, simulated calls | zero changes to any root |
| G07 | target lowered to 500 with active 1,800 | no refill; active 1,800 kept; debits continue |
| G08 | two oxygen tanks forced into a root by a fixture | root unsupported; no refill from either; nothing changes |
| G09 | live chest replaced between read and commit (A1: swap the chest in the same tick via a test hook) | commit refused; debit applied to the unrefilled balance per ADR-025 |
| G10 | hydrogen spend: 100 paid thrust ticks | tank −100 exactly; a tick with an empty tank applies no thrust |
| G11 | multiplier changed from 1 to 4 after a tank exists | that tank's maximum unchanged; a new tank gets 4× |
| G12 | conservation sweep: random sequences of refill, debit, spend, install, remove over 10,000 steps (seeded) | total gas = initial − debits − spends at every step |

## 3. Workstation (A1)

| ID | Case | Expected |
| --- | --- | --- |
| W01 | install each kind into its slot | module root updated; transfer slot emptied |
| W02 | install into a full slot | refused; both stacks unchanged |
| W03 | install a duplicate kind, a third HEAD module, a second finder, a second oxygen tank | refused unchanged |
| W04 | install into external-provider armor, enchanted third-party armor | refused unchanged |
| W05 | remove into an empty transfer slot; transfer slot occupied with inventory room; both full | first two move the module once; the last refuses unchanged |
| W06 | shift-click armor and modules in every direction | same results as the service, verified on detached copies; no duplication; no deletion |
| W07 | install while a D4 receipt is pending on the chest (fixture root) | refused unchanged |
| W08 | install with an unsupported module root on the armor | refused unchanged |
| W09 | break the workstation holding armor and a module | each dropped once |
| W10 | hopper, pipe and other automation next to the block | no capability; nothing moves |
| W11 | intent from a player 9 blocks away, in another Level, without the menu open, with a wrong container ID, unknown operation, position 8, a 65-byte payload, 11 intents in one second | each rejected without change or chunk load |
| W12 | tank gas, durability, enchantments and unknown tags on the module survive install then remove | byte-equal stack |

## 4. Motion and passive effects (A1, real server player)

| ID | Case | Expected |
| --- | --- | --- |
| M01 | toggle on, heartbeat held 40 ticks | upward velocity rises by `0.08 × force` per paid tick to the 0.6 cap |
| M02 | heartbeat stops | thrust ends within 5 ticks |
| M03 | 20 heartbeats in one second | 10 accepted |
| M04 | packet carrying a velocity or position field | rejected (strict decoding) |
| M05 | elytra flying, rocket seat, elevator transit, no hydrogen, unsupported tank, switch off | no thrust |
| M06 | same force on Earth and on the Moon | identical thrust increment; net motion differs only by gravity (C§7.1 constraint) |
| M07 | gravity stronger than thrust (test multiplier) | player still falls |
| M08 | HOVER without `hover_upgrade` | `CYCLE_MODE` refused |
| M09 | hover reference after Level change, logout, equipment change | reset |
| M10 | abilities after any sequence | `mayfly`/`flying` never set by the jetpack |
| M11 | bionic legs sprint / stop / unequip / switch off | modifier present only while sprinting and worn; removed the same tick; another mod's modifier untouched |
| M12 | padded boots fall from 20 blocks; with `lowGravityBootsOnly` on Earth and on the Moon | no fall damage; with the flag, damage on Earth, none on the Moon; other damage types unaffected |
| M13 | enchanted chest provider precedence | a registered external provider's item ignores the enchantment path; enchanted native chest debits its own root |

## 5. Summary, interop and client (A1, V1, V2)

| ID | Level | Case | Expected |
| --- | --- | --- | --- |
| I01 | A1 | finder installed at HEAD, worn | `beaconFinderEnabled` true; removed or unequipped: false within the next recomputation |
| I02 | A1 | finder held but not worn | summary false; C17 held mode handles it (one marker stream) |
| I03 | A1 | two HEAD positions occupied, then finder install | refused |
| I04 | A1 | restart with finder installed (S1) | summary true after login |
| I05 | A0 | summary encoding bounds | owner ≤ 64 bytes; tracking copy has no gas amounts; unknown enum value rejected |
| I06 | A1 | tracking player receives only the tracking copy | no gas amounts on the wire |
| C01 | V1 | key toggle, hold jump, release | thrust feels continuous; no client-side velocity sent |
| C02 | V1 | fog in a dense atmosphere with and without the anti-fog visor, setting on and off | modulation only with setting on and visor absent; vanilla blindness unaffected |
| C03 | V1 | earthbright visor in the Space Level and on the Moon | brighter only in Space; clears on reload and Level change |
| C04 | V2 | one client wearing a jetpack, another watching | watcher sees the attachment and active state; no gas values |

## 6. Durability (S1, S2; planned against Root's D4 work)

| ID | Level | Case | Expected |
| --- | --- | --- | --- |
| D01 | S1 | install, save, restart | module root intact on the worn and the stored armor |
| D02 | S1 | refill and debit for 600 ticks, restart | active and tank values equal the last saved state; total consistent |
| D03 | S2 | forced stop after a refill commit and before the player file save | after restart the stack is either before or after the commit, never a mix; total gas not increased |
| D04 | S2 | forced stop during a workstation install (BE saved, player not, and the reverse) | exactly one copy of the module exists |
| D05 | S2 | pad charging cut points (D4 protocol, when it exists) | conservation per ADR-066 §4 |
| D06 | S1 | unsupported and future roots in a fixture world | preserved through two restarts |

## 7. Not covered

Recipes, art, JEI, the gas charge pad itself, mining tools and the HUD panel layout belong to
other tasks. Performance: the motion carrier (C§7.1) adds at most 20 S2C packets per second per
flying player; a load case with 16 flying players is part of the S1 performance record.

## 8. Executed checks for this draft

None of the cases above has been executed. The only checks run for this draft are the static
text, hash and link checks recorded in [HANDOFF-01](HANDOFF-01.md).
