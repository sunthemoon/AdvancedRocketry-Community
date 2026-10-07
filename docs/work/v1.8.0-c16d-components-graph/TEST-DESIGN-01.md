# CL16D-COMPONENTS-GRAPH-01: test design draft 01

Date: 2026-10-07. Status: **planned cases only; none executed**. Derives from
[CONTRACT-01](CONTRACT-01.md); expected results follow the contract's rules, not an
implementation. Section numbers like "C§5.2" point into CONTRACT-01.

Verification levels (docs/17 §2): **A0** pure JUnit over synthetic or committed inputs;
**A1** Forge GameTest; **S1** packaged dedicated server; **V1/V2** real clients. The graph itself
is A0. Registry equality and in-world producers are A1.

## 1. Fixture conventions

- Synthetic graphs are small JSON documents in the test's own resources, not the real catalog.
  Every case states its roots, producers and expected result; it never reads the generated data.
- A synthetic producer is written `P: inputs [selectors] + facility + energy -> outputs`.
  A selector `{a|b}` is satisfied by either member. `fe>=N` needs a sustained N FE/t.
- Expected report fields are exact: the available set, each node's first route (producer ID and
  round), each unavailable node's classification (`missing_producer`, `blocked`,
  `hard_locked_cycle`) and its blockers, and the failure code of the run.
- Every case also runs with its producers, tags and files supplied in reversed order; the report
  bytes must be identical (C§5.2.3).

## 2. Synthetic positive and negative graphs (A0)

| ID | Graph | Expected |
| --- | --- | --- |
| G01 single recipe | root `r`; `P1: [r] -> a` | `a` available, first route `P1`, round 1 |
| G02 missing ingredient | root `r`; `P1: [r, x] -> a`; no producer of `x` | `a` blocked by `x`; `x` missing producer; run fails on both (if registered) |
| G03 tag alternative | roots `r1`; tag `#t = {m1, m2}`; `P0: [r1] -> m2`; `P1: [#t] -> a` | `a` available through `m2`; report names the chosen member `m2` |
| G04 empty tag | tag `#t = {}`; `P1: [#t] -> a` | `a` blocked by `#t (empty)` |
| G05 unknown tag | `P1: [#missing] -> a` | run fails with `unresolved_tag` before the fixpoint |
| G06 nested and cyclic tags | `#t1 = {#t2}`, `#t2 = {#t1}` | run fails with `tag_cycle`; depth 9 nesting fails with `tag_depth` |
| G07 external-only member | `#t = {othermod:x}`; `P1: [#t] -> a` | `a` blocked; external members are never roots (C§4.3.2) |
| G08 facility | root `r`; `P1: [r] + machine:M -> a`; `M` needs controller `c` and part `k`; `c` craftable, `k` missing | `a` blocked by `machine:M`, which is blocked by `k` |
| G09 missing FE | `P1: [r] + fe>=40 -> a`; no FE producer | `a` blocked by `fe>=40` |
| G10 FE from generator | root fuel `coal`; generator item craftable; `P1: [r] + fe>=400 -> a` | `a` available; report records 10 generators (`ceil(400/40)`) |
| G11 FE above machine intake | as G10, machine intake limit 300 FE/t | `a` blocked with `energy_above_intake` (C§4.5.3) |
| G12 buffer is not rate | FE source sustained 40 FE/t, buffer 1,000,000; `P1: fe>=41` | `a` blocked |
| G13 inaccessible body | item `o` only from a feature in Level `L`; no route to `L` | `o` blocked by `level:L` |
| G14 route but rocket parts missing | route to `L` exists; blueprint needs `motor`; `motor` missing | `level:L` blocked by `motor` |
| G15 discovery gate | `L` requires discovery; rocket available; data satellite path missing | `level:L` blocked by `discovery:L` |
| G16 discovery satisfied | as G15 with satellite, terminal, research fee path available | `level:L` available; first route shows the discovery producer |
| G17 optional cycle | root `r`; `P1: [r] -> a`; `P2: [b] -> a`; `P3: [a] -> b` | `a` round 1 via `P1`, `b` round 2 via `P3`; cycle `{a, b}` reported as informational |
| G18 hard cycle | `P2: [b] -> a`; `P3: [a] -> b`; no other producer | both unavailable, `hard_locked_cycle` component `{a, b}` with no external entry |
| G19 hard cycle with blocked entry | as G18 plus `P4: [r, x] -> a`, `x` missing | component `{a, b}`, external blocker `x` listed |
| G20 valid exemption | `fluid_block` `f` exempt, no producer | run passes; `f` listed under exemptions applied |
| G21 stale exemption | exempt `z` not in registered inventory | run fails `exemption_unknown_id` |
| G22 forbidden exemption | exempt `a` that is a recipe output | run fails `exemption_of_output` |
| G23 bad category | exempt `e` with category `equipment` | run fails `exemption_category` |
| G24 exempt but reachable | exempt `f`; a producer makes `f` | run passes with warning `exemption_unused` |
| G25 deterministic ties | two producers of `a` enabled in the same round, IDs `p_b` and `p_a` | first route `p_a`; reversed input order gives identical bytes |
| G26 limits | 16,385 nodes; a 65,537-byte recipe; 33 alternatives in a kernel selector | each fails with its named limit code; nothing is truncated |
| G27 unknown recipe type | effective file with `type: other:thing` | run fails `unknown_recipe_type` |
| G28 unknown loot condition | block loot entry with an unsupported condition | that entry is a blocker `unsupported_loot_condition`; the run does not count it as a producer |
| G29 duplicate path | two roots both containing `recipes/x.json` | run fails `duplicate_resource_path` unless the extractor input is the processed set (C§4.2.1) |
| G30 catalyst not consumed | etcher-style producer with a kept lens | lens is a prerequisite; output available once lens available; lens count unchanged in report |
| G31 chance output | centrifuge-style producer with weights `{n1: 1, n2: 0}` | `n1` available, `n2` not (weight 0) |
| G32 in-world press | press producer needs `press` item, `obsidian` root, `redstone_pulse` capability root | output available only with all three |
| G33 no-Tau variant | item `t` only from Level `tau_ceti_f`; C16 output `c` needs `t`; expected list contains `t` | `release` passes; `no_tau_ceti` fails because `c` is unavailable |
| G34 no-Tau expected list drift | `no_tau_ceti` makes `u` unavailable, list lacks `u` | fails `no_tau_unexpected`; a listed item that stays available fails `no_tau_stale` |
| G35 overworld-only diagnostic | `q` is a `nether` root needed for `a` | `release` passes; `overworld_only_roots` report lists `a` blocked by root class |
| G36 switch recorded | producer guarded by switch `s` default on | report lists `s=on`; a diagnostic run with `s=off` makes the guarded outputs blocked |
| G37 empty or missing resources | the resources property missing or the directory empty | run fails `no_effective_resources`, never "nothing to check" |

Cases G02, G04, G08-G09, G11-G15, G18-G19, G21-G23, G26-G29, G33-G34 and G37 are negative: they
must fail with the stated code. A synthetic negative that passes is a test failure.

## 3. Extractor cases over the real catalog (A0, planned)

These need Root's bindings (C§9) and the committed resource files.

| ID | Input | Expected |
| --- | --- | --- |
| R01 | processed resources of the candidate | exactly the recipe IDs in the effective set; the three superseded v1.2 copies are absent |
| R02 | each present recipe type | an adapter exists; field-by-field equality with the type's serializer for a sample of every type (counts, fluids, time, energy) |
| R03 | `registered-content.txt` | every item and fluid referenced by a recipe output or producer is in the inventory, or the run fails |
| R04 | roots file | every root is a vanilla ID; no project ID; every class value is allowed |
| R05 | exemptions file | all C§5.1 rules |
| R06 | tags | `forge:ingots/iron` resolves to the vanilla iron ingot from the classpath; `advancedrocketrycommunity:silicon_wafers` to `silicon_wafer`; an unresolved reference fails |

## 4. Current-data expectations (A0, planned, reported not asserted until delivery)

Under the base commit's data and root reading (a), the diagnostic report is expected to show
(C§6): available iron rods, copper coil, small plate press, iron plates, combustion generator, FE,
silicon wafer, basic circuit, machine casing, rolling machine, precision assembler and
electrolyzer; unavailable steel family, motors, pressurized tank, pump, the ten assigned
components and all C16b/C16c outputs. A difference from this list is investigated before any
assertion is written. The release assertion is enabled only with the C16d delivery
(ADR-061 §5.3: "introduced in C16d and must pass from then on"); until then the report is archived
and the ledger rows stay `PLANNED`.

## 5. Component mapping cases (A0 and A1, planned)

| ID | Level | Check |
| --- | --- | --- |
| M01 | A0 | the eight new IDs exist in `registered-content.txt` and in the generated language and model files |
| M02 | A0 | `forge:ingots/carbon` contains exactly `advancedrocketrycommunity:carbon_brick` from this mod |
| M03 | A0 | each new recipe equals the frozen legacy body under the C§3 mapping rules (counts, shape, time, energy) |
| M04 | A0 | precision recipes for the boards and the tracking circuit fit 5 inputs / 2 outputs |
| M05 | A0 | no COMMON key named like `makeMaterialsForOtherMods` exists; every kernel and classic machine recipe that consumes a material uses a tag (catalog scan) |
| M06 | A1 | a GameTest crafts each crafting component in a real crafting grid and runs one precision component recipe in a formed precision assembler |
| M07 | A1 | an item from another test-only mod in `forge:ingots/titanium` rolls in the rolling machine (tag interoperability that replaces the switch) |
| M08 | A1 | registry equality: live `ForgeRegistries.ITEMS`/`FLUIDS` in the namespace equal `registered-content.txt` both ways |
| M09 | A0 | ledger delivery record lists `item_variant:misc/1`, `material:Carbon` and `advancedrocketrycommunity:carbon_brick` as whole tokens |

## 6. Rebalance cases (planned, per accepted proposal only)

For any rebalance item that Root accepts (C§7):

- before/after recipe catalog diff lists exactly the changed IDs;
- the `release` graph still passes after the change;
- for RB-1, an A1 test starts a precision process on `precision_control_circuit`, removes the
  recipe through a data reload and checks progress and resources are kept and the menu reason is
  `recipe_missing` (ADR-064 §2.3, §14);
- no inventory item changes (ADR-061 §1.5).

## 7. Deliberately not covered here

- Quantity feasibility (stack counts, 27-casing patterns, fuel totals): playthrough checks
  under ADR-066 §6.3, not this graph.
- S1 restart, V1 JEI presentation and V2 multiplayer: they belong to the machine and component
  deliveries; the graph adds no persisted state or network message.
- Performance: the graph runs in the unit-test JVM within the C§5.5 limits; no MSPT budget applies.

## 8. Executed checks for this draft

None of the cases above has been executed. The only checks run for this draft are the static
text, hash and link checks recorded in [HANDOFF-01](HANDOFF-01.md).
