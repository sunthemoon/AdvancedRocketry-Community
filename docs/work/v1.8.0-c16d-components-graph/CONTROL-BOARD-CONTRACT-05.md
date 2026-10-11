# Control board acquisition leaf

2026-10-11. Task C16d-CONTROL-BOARD-05, Root implementation and integration.
Isolated branch codex/v1.8.0-control-board-acquisition, worktree
D:/GitHub/arce-v180-control-board-acquisition-20261011-20, base
b3ad9020a19045c072fc3223f9e04697e314058b. Accepted ADR-060 revision 2 and
ADR-064 revision 5 sections 10 and 12 authorize this component, not release.
Independent actual contract review and scoped Root adoption precede authoring.

## Observable outcome

Register advancedrocketrycommunity:control_board as one plain Item, stack limit
64, with no custom NBT/capability, fuel, ticking, packets or world-state root.
Display Control Circuit Board / 控制电路板 and add one creative entry.
No existing ID, Item IO/Liquid IO board, stand-in circuit or recipe is removed,
renamed or rebalanced. In particular precision_control_circuit is unchanged.

Retain both pinned original producers for ic metadata 3:

- Crafting ID control_board: shaped rvr/dwd/dpd. r is forge:dusts/redstone x2,
  v forge:gems/quartz x1, d forge:dusts/copper x4, w minecraft:wooden_slabs x1,
  p forge:plates/iron x1; output one board. Unlock with copper dust or the
  already unlocked crafting recipe; reward only this crafting recipe.
- Precision ID precision_control_board: existing precision_assembling schema
  1, ordered channels 0/1/2 redstone dust, copper plate, steel plate each one;
  channels 3/4 empty; output channel 0 one board, channel 1 empty; 200 ticks
  at 10 FE/t = 2000 FE. No new machine advancement or discovery lock.

The original precision JSON omits output count. Modern count one is an explicit
mapping decision, not proof of the old loader's runtime default. Preserve tag
resolution bounds, exact-slot matching, ambiguity refusal, signatures and the
existing process/journal transaction implementation.

Crafting has mined redstone, Nether quartz, wooden slabs and existing unpowered
small-press copper dust/iron plates as dependencies. Precision uses existing
formed v1.2 ports and copper/steel plates. Initial steel, controllers, physical
classic hatches and clean-world full graph/access/discovery remain separate
open dependencies. Fixtures supply materials/FE; lookup and assembly do not
prove actual player crafting consumption. No substitute producer is added.

## Provenance and art

Root acquired exact original Advanced-Rocketry/AdvancedRocketry MIT recipe
bodies at c5cd5af62fc07cd4e0d24f06a16033f181c47c04 before authoring:

- src/main/resources/assets/advancedrocketry/recipes/controlcircuitboard.json:
  733 bytes, SHA-256 6a234e9cae6ff502d6e243e2013389e34c9d75c3ea7589c12277d73ac40ad3ee.
- src/main/resources/assets/advancedrocketry/recipes/controlcircuitboard_prec.json:
  447 bytes, SHA-256 699b80267a894d60574fc9a54a383ce75470e4440c597129dea8b075a6ddc59f.

External inert references and bindings are in
D:/GitHub/ARCE-Task-Evidence/v1.8.0/control-board-acquisition-20261011-20.
New community MIT code/data express those semantic facts; no copied legacy
JSON, Java or bitmap enters product source. NEW 16x16 art uses an original
clock/control-centered PCB grid and green tint through V180MaterialArt.png.
No legacy/official/excluded/third-party bitmap is read or traced. Only asset-plan
row ID 244 changes IMPORT to REGENERATE. All seven generated hashes and
schema-2 authoring identities are recorded before resource integration; human
originality/readability approval remains pending.

## Exclusive write scope and non-goals

Root alone writes in the isolated tree: registry/ModItems.java, ModCreativeTabs.java
(one entry each); datagen/BootstrapDataGenerators.java, V180LanguageProvider.java
(one provider and bilingual additions); new datagen/V180ControlBoardData.java,
test datagen/V180ControlBoardDataTest.java and gametest/ControlBoardGameTests.java.
Generated v1.8 resources: one model, NEW PNG, two recipes, one crafting unlock,
two shared-language additions. Dedicated provenance/preauthoring, this leaf's
contract/adoption/implementation/qualification records, asset-plan row 244 and
one stable player-facing CHANGELOG entry. After qualification Root may update
only current-source/CI associations and this leaf in the existing status/plan.

No protected AGENTS/global implementation-log bodies, content-ledger delivery,
build/CI, log expectations, existing board sources/resources, kernel/schema,
network, shared physical hatch writers, other worktrees/worlds/foreign processes.
This is not acceptance of other proposed C16d contracts, mappings, migration,
rebalance, initial steel or all-component delivery.

## Verification

A0: exact scoped IDs/files/shape/selectors/quantities, precision channel order,
time/FE/result, copper-dust-or-recipe unlock, bilingual names/fresh JSON,
deterministic transparent 16x16 NEW PNG distinct from both existing boards,
side flags and committed generated-byte/name agreement.
A1: installed tag memberships (dust versus plate/ingot), plain ItemStack round
trips/no fuel, valid crafting with wooden alternatives and invalid/missing
positions, unique installed precision matching, empty trailing channels and
tagged-input refusal, and one formed v1.2 batch consuming its three inputs and
2000 FE without repeated output. Dedicated control_board batch.

Root alone runs a fresh initial seed runData against a committed authoring SHA,
then fixes the committed generated source SHA before clean build, two cached
idempotent runData checks/whole-tree diff, full runGameTestServer and unchanged
strict log checker. No assertions, timeouts or expected-error rules are relaxed.
Bind original exits, complete bounded streams, actual XML/resources/server log;
retain failures. Independent actual source/generated and completed-result
reviews are required before integration.

Copy reviewed helpers into this new unsealed packet, inspect changed literals
and semantics before execution; never execute helpers in sealed evidence.
Execution envelope: disk >=10 GiB, two Gradle workers, 1 GiB Gradle heap,
native Xms256 MiB/Xmx2 GiB, owned Windows Job 6 GiB, original command deadline
900 seconds plus 10 seconds owned retirement, each stream <=2 MiB. Refuse clean
build/native if a native world/log already exists; bind the server log after
owned retirement before checking. This is not peak-memory/soak/natural-child-exit
or independent JVM replay proof. No cache deletion or reuse of an old world.
Dedicated restart, current Python/repository-strict, raw hosted Linux, steel/
full graph, human art, V1/V2, storage-CME cause and all G0-G9 remain open.
