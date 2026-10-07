# CL16D-COMPONENTS-GRAPH-02: test design draft 02

Date: 2026-10-08. Status: **planned cases only; none executed**. Derives from
[CONTRACT-02](CONTRACT-02.md) ("C§n"). Replaces [TEST-DESIGN-01](TEST-DESIGN-01.md) as the
proposal; draft 01 stays unchanged.

Conventions:

- Level **A0**: plain JUnit on synthetic inputs (no Minecraft data) or on the effective resources;
  **A1**: GameTest. Synthetic fixtures are small JSON trees written by the test, not repository
  data. Their expected values below were derived by hand from the C§7 rules, independently of any
  implementation.
- Node and producer IDs use the canonical forms of C§7.1. In synthetic fixtures `r`, `a`, `b`, …
  stand for `item:t:r`, `item:t:a`, …; producers are `recipe:t:<name>`. All synthetic items are
  required unless a case says otherwise. Ungated roots are round 0.
- "work" means the `work_units` counter: +1 per node taken from a round list, +1 per
  (producer, slot) occurrence visited, +1 per producer applied, +1 per blocker edge.
- Every case runs twice, the second time with all input files and all selector members in reverse
  order; both reports must be byte-identical (C§7.1 rule 4).

## 1. Fixpoint, ordering and route (A0, synthetic)

| ID | Fixture | Expected |
| --- | --- | --- |
| G01 | root r; `pa`: [r] → a; `pb`: [a] → b | a round 1 via `pa`; b round 2 via `pb`, slot 0 member a, `member_round` 1; `rounds` 2; work 3 pops (r, a, b) + 2 visits + 2 applications = 7 |
| G02 | as G01 but producer IDs `recipe:t:aaa` → b and `recipe:t:zzz` → a | b still round 2 (synchronous rounds: no in-round chaining) |
| G03 | roots m1, m2; `p`: [{m2, m1}] → x | x round 1; slot member `item:t:m1` (both round 0, smaller ID) |
| G04 | root r; `c1`: [r] → c1; `c2`: [c1] → c2; `recipe:t:a`: [c2] → x; `recipe:t:z`: [r] → x | x round 1 credited `recipe:t:z`; `route_metric` "height"; `route_size` of x = 1 |
| G05 | root r; `recipe:t:b`: [r] → x; `recipe:t:a`: [r] → x | x credited `recipe:t:a` |
| G06 | root r; `p`: [r] → {x, y} | x and y round 1, both credited `p` |
| G07 | root r; `p1`: [r] → m1; `p2`: [m1] → m2; `q`: [{m2, m1}] → x | slot member m1 (round 1 beats round 2); x round 2 |
| G08 | `p`: [e] → x; e has no producer and is not required | x `blocked`, producers [`p`: unsatisfied slot 0 members [e]]; no `hard_lock`; `scc_components` 2, none cyclic |
| G09 | `p`: [x] → x | x `hard_lock`, members [x], edge x -`p`/0-> x |
| G10 | `pa`: [b] → a; `pb`: [a] → b | one `hard_lock` with members [a, b] |
| G11 | `p1`: [e] → x (e no producer, not required); `p2`: [x, r] → x; root r | x `blocked` with note `cyclic_but_entry_blocked`; entries [`p1`] with outside blockers [e]; no `hard_lock` |
| G12 | root r; `p1`: [r] → a; `p2`: [b] → a; `pb`: [a] → b | a round 1, b round 2; `available_cycles` [[a, b]]; no failure |
| G13 | G10 plus `pd`: [a] → d | a, b `hard_lock`; d `blocked` (slot members [a]) |
| G14 | `pn`: [{m, e}] → n; `pm`: [n] → m; e no producer, not required | `hard_lock` [m, n] (the slot has a member inside the component) |
| G15 | `p`: [r, `cap:fe:t:port`] → x; root r; no FE producer | x `blocked`, unsatisfied slot members [`cap:fe:t:port`] |
| G16 | G01 with 2,000 extra unrelated roots | a and b reports unchanged; `rounds` 2 |

## 2. Energy (A0 synthetic unless noted)

Shared fixture: root `fuel`; producer row `code:t:gen` (combustion, 40 FE/t) needs [`fuel`, placed
`gen` item]; receiver `t:port`, capacity 20,000, receive limit 1,000; witness row (`code:t:gen`,
`t:port`, free faces 5); machine recipe `m` with `processing_time` T and `energy_per_tick` e.

| ID | Variation | Expected |
| --- | --- | --- |
| E01 | T 900, e 50 | output available; throughput `total_fe` 45,000, `ticks_one_generator` 1,125, `generators_full_speed` 2, `exceeds_witness_faces` false |
| E02 | T 100, e 41 | available (pauses allowed); `total_fe` 4,100, `ticks_one_generator` 103, `generators_full_speed` 2 |
| E03 | e 20,001 | output unavailable; diagnostic `energy_debit_exceeds_capacity` subject `t:port` |
| E04 | witness row removed | `cap:fe:t:port` has no producer; output `blocked` by it |
| E05 | e 400, T 1,200, free faces 5 | available; `generators_full_speed` 10, `exceeds_witness_faces` true, `ticks_one_generator` 12,000 |
| E06 | witness receiver is another combustion generator | run fails `witness_invalid` (the generator never pushes into one) |
| E07 | pump row: buffer 10,000, debit 100 | pump output available; with a fixture debit 10,001 it is unavailable (`energy_debit_exceeds_capacity`) |
| E08 | terminal power slot OR {`minecraft:redstone`, `cap:fe:t:terminal`}; only redstone root, no generator | slot satisfied by `item:minecraft:redstone` |
| E09 | warp balance capacity 10,000,000, interstellar debit 8,000,000 | warp producer enabled; with a fixture capacity 5,000,000 it is unavailable |
| E10 | a classic-type recipe while the plug binding is unfrozen | run fails `binding_unfrozen` (named, before any report) |
| E11 | (A1) one combustion generator next to the energy port of a formed precision assembler, fuel inserted | stored energy rises; this is the witness observation for the real row |
| E12 | (A1) a precision recipe at 50 FE/t with one generator | the machine alternates `WAITING_ENERGY` and `RUNNING` and completes with progress kept; output count 1 |

## 3. Contexts, Nether, stations and warp (A0 synthetic unless noted)

Shared catalog fixture: bodies `t:earth` (surface = Overworld, no discovery), `t:moon` (discovery
not required), `t:far` (other system, orbitable, landable, discovery required); routes
`t:earth_moon` (surface earth ↔ orbit moon) and `t:far_surface_orbit`.

| ID | Fixture | Expected |
| --- | --- | --- |
| X01 | kit available; no warp core | `cap:station:t:earth` available (via `code:station_create:t:earth`); `cap:station:t:far` unavailable; a producer bound to `cap:station:t:far` is `blocked`; one bound to `cap:station:t:earth` is available |
| X02 | X01 plus warp core, `cap:discovered:t:far`, warp balance witness | `cap:station:t:far` available via `code:warp:t:earth->t:far`; then `cap:surface:t:far` via the `t:far_surface_orbit` route |
| X03 | rocket route to `cap:orbit:t:moon` only | `cap:station:t:moon` unavailable (a quote never creates a station) |
| X04 | `t:far` route present, no data mission | `cap:surface:t:far` unavailable, blocker `cap:discovered:t:far` |
| X05 | Overworld ore feature, Moon ore feature | Overworld ore round 0 via feature; Moon ore only after `cap:surface:t:moon` |
| X06 | roots: obsidian, flint and steel; Nether root quartz gated; recipe wafer [quartz, redstone] | `cap:access:minecraft:the_nether` round 1; quartz round 2; wafer round 3 |
| X07 | as X06 but without flint and steel; fire charge as a Nether-gated root | `hard_lock` [`cap:access:minecraft:the_nether`, `item:minecraft:fire_charge`] |
| X08 | variant `overworld_without_nether` on X06 | quartz and wafer unavailable; report only (variant does not fail the run) |
| X09 | a Space-Level producer declared "anywhere in Space" with OR slot over every orbit and station context | available once any one context is available |
| X10 | (A1) player uses the deployment kit on the Moon surface Level | the new station's `orbitBody` is the Moon (observation for the station-creation row) |

## 4. Discovery and research (A0 synthetic; A1 observations)

Shared fixture: terminal, chassis, solar module, data payload and blank chip available; definition
yield 120, cost 100, `allowed_targets` [`t:moon`, `t:far`].

| ID | Fixture | Expected |
| --- | --- | --- |
| D01 | no prior research node at all | `cap:discovered:t:far` and `cap:research` available in the same round as `code:data_mission:t:far` |
| D02 | target `t:other` not in `allowed_targets`, discovery required | no `code:data_mission:t:other` producer exists; a route needing it is `blocked` |
| D03 | `cap:discovered:t:far` available, no rocket parts | `cap:surface:t:far` unavailable (discovery is not access) |
| D04 | `t:moon` with `discovery_required` false | the Moon route needs no discovery slot |
| D05 | power by redstone only | discovery available (E08) |
| D06 | (A1) first data mission for an owner with an empty account | claim succeeds; balance 20 (`120 - 100`) |
| D07 | a `ground_survey` producer row before C18c adds its adapter | run fails `adapter_missing` |

## 5. Universes, exemptions and real-data extraction

| ID | Level | Case | Expected |
| --- | --- | --- | --- |
| U01 | A0 | real `precision_control_circuit.json` | producer `recipe:advancedrocketrycommunity:precision_control_circuit`; slots `forge:ingots/iron` ×2, `forge:dusts/redstone` ×2, formed precision assembler, `cap:fe:<precision energy port>`; outputs `item:advancedrocketrycommunity:advanced_circuit` ×1 and `item:minecraft:redstone_torch` ×2; energy 40, time 20 |
| U02 | A0 | real `precision_guidance_module.json` | output `item:minecraft:comparator`; it appears in `external_references` and is not required |
| U03 | A0 | a recipe input `minecraft:not_an_item` absent from `external-references.txt` | fails `unknown_reference` |
| U04 | A0 | `external-references.txt` with one extra or one missing line | fails (byte comparison) |
| U05 | A1 | `registered-content.txt` against Forge registries | equal in both directions; a fixture with one extra and one missing line fails naming both |
| U06 | A1 | every line of `external-references.txt` | registered |
| N01 | A0 | exemption for an ID output by a recipe | fails `exemption_has_producer` |
| N02 | A0 | exemption for an ID output only by a producer-table row | fails `exemption_has_producer` |
| N03 | A0 | exemption for an unregistered ID | fails `exemption_unknown` |
| N04 | A0 | exemption without `decision` | fails `exemption_incomplete` |
| N05 | A0 | valid `unlit_torch` exemption | listed in `exemptions_applied`; not in `unavailable` |
| P01 | A0 | producer-table row without an `observation` | fails `row_unobserved` |
| P02 | A0 | a 33-member tag in a precision recipe | recipe marked `disabled_by_tag_size`; it is not a producer; the run continues |

## 6. Limits and work (A0 synthetic)

| ID | Case | Expected |
| --- | --- | --- |
| L01 | 4,096 recipe files; 4,097 | accepted; `input_limit:recipes:files` |
| L02 | recipe file of 65,536 bytes; 65,537 | accepted; `input_limit:recipes:bytes` (rejected from the listing, before parsing) |
| L03 | recipe JSON depth 16; 17 | accepted; `input_limit:recipes:depth` |
| L04 | tag with 4,096 resolved members; 4,097 | accepted; `input_limit:tags:members` |
| L05 | tag nesting depth 8; 9; a two-tag cycle | accepted; `input_limit:tags:depth`; `tag_cycle` |
| L06 | worldgen file of 262,144 bytes; 262,145 | accepted; `input_limit:worldgen:bytes` |
| L07 | loot table depth 32; 33 | accepted; `input_limit:loot:depth` |
| L08 | committed graph file of 1,048,576 bytes; 1,048,577 | accepted; `input_limit:graph_files:bytes` |
| L09 | aggregate input of 64 MiB; one more byte | accepted; `input_limit:aggregate:bytes` |
| L10 | producer with 64 slots; 65 | accepted; `limit:slots` |
| L11 | chain of 10,000 producers from one root | last node round 10,000; work exactly 30,001 (10,001 pops + 10,000 visits + 10,000 applications) |
| L12 | 16,000 producers each [e] → x_i, e without producer (16,001 nodes, inside the node limit) | 16,000 `blocked`; blocker edges 16,000; work 16,000; no `hard_lock` |
| L13 | a fixture whose work would be 8,388,609 | stops with `limit:work`; no report presented as a result |
| L14 | 1,048,577 slot-member occurrences | `limit:occurrences` before allocation |

## 7. Component mapping from the legacy bodies (A0)

Expected values are the C§2.2 translation of each body, written here independently from the body
text in the Root leaf.

| ID | Body | Expected modern recipe |
| --- | --- | --- |
| M01 | `trackingcircuit.json` | `precision_assembling`; inputs `forge:dusts/redstone` ×1, `minecraft:ender_eye` ×1, `basic_circuit_plate` ×1; output `tracking_circuit` ×1; `processing_time` 900; `energy_per_tick` 50 |
| M02 | `controlcircuitboard.json` | shaped `rvr`, `dwd`, `dpd`; r `forge:dusts/redstone`, v `forge:gems/quartz`, d `forge:dusts/copper`, w `minecraft:wooden_slabs`, p `forge:plates/iron`; output `control_board` ×1 |
| M03 | `iocircuitboard.json`, `liquidiocircuitboard.json` | as M02 with d `forge:dusts/gold` / `forge:gems/lapis`; outputs `item_io_board` / `liquid_io_board` |
| M04 | the three `_prec` board bodies | precision; inputs redstone ×1, (`forge:plates/copper` / `forge:plates/gold` / `forge:gems/lapis`) ×1, `forge:plates/steel` ×1; 200 ticks; 10 FE/t |
| M05 | `basiccircuitplate.json`, `advcircuitplate.json` | precision; `forge:ingots/gold`, (`forge:dusts/redstone` / `minecraft:redstone_block`), `silicon_wafers` tag, each ×1; output plate ×1; 900 ticks; 100 FE/t |
| M06 | the two etcher plate bodies | recorded for C16c: lens catalyst, `forge:plates/gold` ×1, redstone dust / redstone block ×1, `silicon_wafers` ×4; output 2; 1,200 ticks at 400 / 600 FE/t |
| M07 | `userinterface.json` | shaped `lrl`, `fgf` (blank top row dropped); output `user_interface` ×1 |
| M08 | `charcoalbrick.json` | shapeless, six `minecraft:charcoal` (not `minecraft:coals`); output `carbon_brick` ×1; `carbon_brick` absent from the furnace fuel map |
| M09 | ledger tokens | ten unit IDs map to eight modern IDs; `item_variant:misc/1` and `material:Carbon` both to `carbon_brick`; the configuration unit to none |
| M10 | every body used | its SHA-256 equals the `legacy-manifest/recipes.csv` row and the Root provenance entry |
| M11 | every translated precision recipe | at most 5 inputs and 2 outputs |
| M12 | a body with an unmapped reference (fixture) | translation stops with `mapping_open:<reference>` instead of guessing |

## 8. Real-data expectations (A0 on the effective resources; not executed)

| ID | Moment | Expected |
| --- | --- | --- |
| R01 | today (no C16d recipes) | `release` fails; the eight new IDs are absent from `U_req` until registered; steel-dependent items are `blocked` |
| R02 | after C16d recipes, before C16b/C16c | boards by crafting, plates by precision, tracking circuit, user interface and carbon brick available; `rank(silicon_wafer) > rank(cap:access:minecraft:the_nether)`; precision board producers `blocked` by `forge:plates/steel` |
| R03 | any | `external_references` equals the committed file and contains `minecraft:comparator` and `minecraft:redstone_torch` |
| R04 | any | `code:data_mission:<body>` producers exist exactly for the bodies in the effective v1.8 `data_satellite.json` with `discovery_required` true |
| R05 | after C16b/C16c | `release` passes or names every `hard_lock`; `no_tau_ceti` matches `no-tau-unavailable.txt` |
| R06 | two runs on the same inputs | byte-identical JSON and Markdown |
| R07 | `overworld_without_nether` | report lists quartz, ender eye, glowstone dust and their dependants as unavailable |

## 9. Rebalance cases (A0 and A1)

| ID | Case | Expected |
| --- | --- | --- |
| B01 | RB-1 applied in a fixture | the classic advanced-circuit route exists in `release`; a process saved on `precision_control_circuit` loads paused with `recipe_missing`, progress and resources kept (A1) |
| B02 | RB-2 to RB-4 | stand-ins stay; the graph lists both routes for wafer and circuits |
| B03 | RB-7 | delivered v1.2 machine recipes unchanged byte for byte |
| B04 | RB-8 | both producers per component present; removing the crafting board producers in a fixture makes `control_board` unavailable before steel |

## 10. Not covered and not executed

Quantity feasibility (stack counts, 27 casings), a fresh survival playthrough, JEI present/absent,
native and restart runs, V1/V2. No case above has been executed. The only checks run for this
draft are the static text, hash and link checks recorded in [HANDOFF-02](HANDOFF-02.md).
