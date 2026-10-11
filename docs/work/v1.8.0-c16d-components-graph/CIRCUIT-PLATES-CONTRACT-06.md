# C16d precision circuit-plate acquisition

Task: C16d-CIRCUIT-PLATES-06. Status: PROPOSED, 2026-10-11. Root is sole
implementer and integrator. Base d6cdb6d8100c53bd3b70c85c555ecde0532854f0;
branch codex/v1.8.0-circuit-plates-acquisition; isolated checkout
D:/GitHub/arce-v180-circuit-plates-acquisition-20261011-23.

## Observable outcome and preserved scope

Basic and advanced circuit plates become ordinary stable items, obtainable by
their original precision-assembler producers on the existing v1.2 machine.
This advances the inputs needed by tracking/cutting/etching progression.
It is one producer leaf under accepted ADR-064 section 10, not acceptance of
the entire proposed CONTRACT-02 graph, nor proof of a complete survival graph.

Both eventual legacy producers are retained as requirements: the second route
is the C16c laser etcher (gold plate, redstone or redstone block, four wafers,
retained lens; two plates at 1200 ticks and 400/600 FE per tick). Its serializer,
physical hatches and machine are not yet installed. This leaf does not create
unknown-type recipe JSON or waive that producer; it remains an explicit C16c
dependency. The initial steel route remains the approved electric arc furnace,
not a cheaper crafting/smelting bypass. No stand-in wafer/circuit or v1.2 recipe
is retired or rebalanced before graph proof and the relevant machine delivery.

## Permanent IDs and exact precision semantics

Namespace advancedrocketrycommunity. Add basic_circuit_plate and
advanced_circuit_plate as plain Item instances (stack 64), with no payload,
fuel, tick, capability, custom root or packet. Keep all existing registry IDs.
Native ItemStack save/load preserves these new identities and counts. Existing
worlds may acquire them normally; no old inventory is rewritten. Older builds
cannot resolve the new items; no downgrade or 1.12.2 world conversion is promised.

Recipes use existing precision_assembling type, schema_version 1:
- precision_basic_circuit_plate: ordered forge:ingots/gold, forge:dusts/redstone,
  literal silicon_wafer, each count 1, produce one basic_circuit_plate.
- precision_advanced_circuit_plate: ordered forge:ingots/gold, literal
  minecraft:redstone_block, literal silicon_wafer, each count 1, produce one
  advanced_circuit_plate.
Both run 900 ticks at 100 FE per tick (90000 FE total). Channels 3/4 are empty;
only output channel 0 is populated. No lens is used by the precision route.
Absent legacy input/result counts are explicitly mapped to one in modern data,
not represented as executable evidence of the old loader's default.

Only the existing sole language writer adds Basic Circuit Plate / 基础电路板
and Advanced Circuit Plate / 高级电路板. Both enter the existing creative tab
once, but creative giving is not acquisition evidence. Precision machine recipes
have no vanilla crafting recipe-book unlock; do not fabricate a crafting recipe
or an advancement reward for them. Existing JEI precision category consumes the
loaded recipes unchanged. Real player/JEI, discovery, energy supply and Level
access qualification remain separate from fixture-supplied inputs and FE.

## Source and art

Use only verified original Advanced-Rocketry/AdvancedRocketry MIT recipe facts
at c5cd5af62fc07cd4e0d24f06a16033f181c47c04, prefix
src/main/resources/assets/advancedrocketry/recipes/:
- basiccircuitplate.json: 451 bytes,
  15fa951546181e68bb0221936939c35ee60d198ff1e73259b12e5dbc8f7db2e7.
- advcircuitplate.json: 426 bytes,
  190d3ea59b7f5d0795dd4c1421132286434c2740b2c45e4e498e7bb5bb92305e.
- basiccircuitplateetcher.json: 624 bytes,
  f61ed418606e19d1c16e2b5752d650f8c53c7cca05e90d9568fc86f430c1820f.
- advcircuitplateetcher.json: 598 bytes,
  665847a41e0af5b761a538e2efbc5e1696b6b80af47f02da435319c9be0361bb.

Fresh bindings are external in circuit-plates-acquisition-20261011-23,
plate-source-bindings-01.json, before the original evidence copies. All four
bodies match legacy-manifest; only facts, not raw legacy JSON/Java, enter code.
Do not read/copy/trace legacy or official/third-party bitmap authoring inputs.
Draw two NEW transparent 16x16 circuit substrates via V180MaterialArt: visibly
different layouts as well as distinct colouring, not recoloured board copies.
Only asset-plan order 242 (textures/items/*circuitplate.png) becomes REGENERATE
under accepted ADR-064 section 12. No other asset-plan row or ledger is changed.
Actual generated schema-2 NEW provenance binds hashes, source/base and committed
authoring SHA later; independent review is not human art approval.

## Exclusive Root write scope after independent review and adoption

Java root src/main/java/io/github/sunthemoon/advancedrocketrycommunity/:
- registry/ModItems.java and ModCreativeTabs.java: two item/tab additions only.
- datagen/BootstrapDataGenerators.java and V180LanguageProvider.java: one
  V180CircuitPlateData provider and its two language additions.
- new datagen/V180CircuitPlateData.java; new gametest/CircuitPlateGameTests.java.
Test root: new datagen/V180CircuitPlateDataTest.java only.
Generated src/generated/v1.8/resources: two item models, two NEW PNGs, two
precision recipes and the two additions in each existing language file.
Docs: this leaf's contract/adoption/implementation/qualification records,
dedicated provenance/preauthoring records, asset-plan order 242, CHANGELOG and
the exact leaf in COMPLETION-PLAN.md. CURRENT_VERSION.md only later qualification
and dated hosted metadata; no Gate or whole-C16d delivery status changes.

Forbidden: Main AGENTS.md/shared implementation log, existing assertions,
build/Gradle/CI, checker/expected logs, configs, networks, schema, shared writers,
machine adapters, other resources/IDs/asset-plan rows and content-ledger edits.
The task-specific implementation record substitutes for the unowned global log.

## Verification

Before authoring: independent actual contract/source/selector review and explicit
Root scoped adoption. A0 tests exact resource names/recipe fields/channel order,
counts/time/FE, bilingual labels/fresh values, both NEW drawings and side flags,
then emitted committed resource identity. A1 tests literal plain-item identities,
native NBT counts/no fuel, actual loaded tag members, both unique installed recipe
matches/results and missing/tagged/swapped/trailing channel refusal.

Two separate formed native-machine tests process one basic/advanced batch through
900 natural server ticks, with unchanged physical v1.2 ports. Test fixture inputs
and replenished direct FE are not a generator/survival acquisition witness.
Keep a bounded supplied-energy total of 90000 FE; verify exact input consumption,
one plain output, empty second output, zero remaining FE/progress and no second
output later. New tests use timeoutTicks 960 for the 900-tick authored recipes;
no existing timeout or assertion changes. No manual controller tick acceleration.

Root alone runs committed authoring seed DataGen, commits generated source, then
bounded clean build/test, two cached-idempotence DataGen/diff checks, complete
GameTest and unchanged strict log checker. Before sustained execution check disk
>=10 GiB and independently review fresh external helper copies/caller limits.
Bind original exits/complete finite streams/actual XML/resources/server log and
source SHA; retain failures. No sealed helper is executed, old cache deleted or
world reused. Actual source/generated/completed-result independent review follows.
Dedicated restart, current repository/Python-strict, raw hosted Linux, NEW human
art/V1/V2, full graph/energy/access, steel, etcher and shared hatches remain open.
v1.8 stays IN_PROGRESS / IMPLEMENTING; all G0-G9 remain open.
