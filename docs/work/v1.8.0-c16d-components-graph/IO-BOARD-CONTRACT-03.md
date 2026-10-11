# Item IO board: two legacy producers

Status: PROPOSED, 2026-10-11. This is a narrow implementation leaf under
accepted ADR-064 sections 10 and 12, not acceptance of CONTRACT-02 or its graph.
Root implements and integrates in the separate io-board acquisition worktree,
based on 6c53eca90437c3ce26eacd5612c9cfcd5fde5c69. Main owner files are excluded.

## Product semantics and stable identities

Freeze advancedrocketrycommunity:item_io_board as an ordinary Item, stack 64,
no custom NBT, fuel registration, capability, ticking, packet or world state.
This spelling follows ADR-061's item IO role. No existing identity or inventory
changes. Native ItemStack saves retain its new ID. Older builds cannot resolve
the new Item; no downgrade migration is promised.

Restore both original producers for this Item (scoped G-OPEN-9 decision):

- item_io_board: ordinary shaped crafting, category misc, empty group,
  pattern rvr / dwd / dpd. r forge:dusts/redstone (2 slots), v
  forge:gems/quartz (1), d forge:dusts/gold (4), w minecraft:wooden_slabs
  (1), p forge:plates/iron (1). Output one item_io_board. Vanilla symmetry
  and full 3x3 dimensions remain; reject omitted/wrong inputs. Unlock by
  possessing forge:dusts/gold, named has_gold_dust, OR has_the_recipe;
  reward only this crafting recipe, parent minecraft:recipes/root.
- precision_item_io_board: existing precision_assembling recipe type, schema 1;
  ordered channels 0/1/2 consume one forge:dusts/redstone, one
  forge:plates/gold, one forge:plates/steel; channels 3/4 must be empty.
  Output channel 0 is one item_io_board, channel 1 stays empty.
  Duration 200 ticks at 10 FE/t (2,000 FE), no machine advancement added.
  Keep the installed kernel's bounded tag resolution, exact-slot matching,
  ambiguity refusal, signatures and transaction/journal behavior unchanged.

The crafting route uses existing unpowered small-press iron plate/gold dust,
wood slabs/redstone and Nether quartz. This does not prove the full survival
graph or a player's actual craft consumption. The precision producer uses the
existing formed v1.2 machine/ports, not classic hatches. Its clean-world steel
source remains blocked by C16b; registering or exercising it with test-given
steel is not a survival witness. Do not invent steel, alter stand-in recipes,
retire another producer or rebalance a controller.

## Verified behavior sources and NEW art

Original Advanced-Rocketry/AdvancedRocketry MIT commit
c5cd5af62fc07cd4e0d24f06a16033f181c47c04, prefix
src/main/resources/assets/advancedrocketry/recipes/:

- iocircuitboard.json: 731 bytes, SHA-256
  e48a9109867a831bfe5f577ae0b7f6083f55727698b17f4c7fba3f954a9d81dc.
- iocircuitboard_prec.json: 445 bytes, SHA-256
  617175a667c19d0c4ad2bd3e11c13b36a78247774e1a5ba1b1b17f7bf86bff9f.

Root downloaded these two exact pinned bodies and checked both existing manifest
identities before authoring. They are inert semantic references; no legacy JSON
or Java is copied into product source. Evidence:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/io-board-acquisition-20261011-06.

All code/data/art are newly authored community MIT. Draw an original compact
16x16 board with separated input/output connectors, traces and a central
package, using the existing deterministic V180MaterialArt encoder. Do not read,
copy or trace the legacy itemiocircuit bitmap, excluded/official/third-party
pixels. Change only asset-plan row 245 from IMPORT to REGENERATE, as required
by ADR-064 section 12; record actual NEW bytes with schema-2 provenance before
resource integration. Human originality/client readability remain open.

## Write scope

Root only, this worktree:
registry/ModItems.java and ModCreativeTabs.java (one entry each);
datagen/BootstrapDataGenerators.java and V180LanguageProvider.java (one provider
and bilingual additions); new datagen/V180IoBoardData.java;
new test datagen/V180IoBoardDataTest.java and gametest/IoBoardGameTests.java.
Generated v1.8: one model, one NEW PNG, two recipes, one crafting unlock and two
language additions. Dedicated provenance/preauthoring record, asset-plan row
245, this task's scoped records and one CHANGELOG development entry.
After qualification, Root may update only the relevant C16d leaf/current status
to the proved scope. No shared implementation-log, AGENTS, content-ledger,
build/Gradle/CI, log expectation, kernel/schema/network/hatch edit.

## Verification and boundaries

Independently review this actual contract before Root adoption/authoring; review
actual source/generated resources before integration. Source SHA must be fixed
for qualification. Root alone operates JVM/native commands, with disk >=10 GiB,
900-second command plus 10-second owned retirement, 2 MiB per output stream,
6 GiB owned Windows Job and two Gradle workers; Gradle heap 1 GiB, native heap
2 GiB/initial 256 MiB. Preserve all receipts, failures and source worlds.

A0 checks exact emitted recipes/selectors/counts/timing/output/unlock/model,
16x16 transparent NEW PNG validity/determinism, bilingual additions and provider
side boundaries. Loaded native tests check actual selector membership, ItemStack
ID/count round trips, crafting matches/lookup/assemble including invalid inputs,
precision availability and installed-catalog uniqueness, strict exact channels,
and a formed physical machine consuming one batch and exactly 2,000 FE.
No increased existing timeout/assertion relaxation or expected-error changes.
Test-given ingredients and native stack round trips are separately disclosed.

Run clean build, deterministic runData and clean scoped generated diff, full
GameTest and unchanged strict log reconciliation. Product/art/command/results
require independent reviews. Full steel bootstrap, all component producers,
graph/rebalance, hatches/machine families, dedicated restart, V1/V2, all G0-G9
and ADR-018 full-campaign requirements remain open; this leaf cannot close them.
