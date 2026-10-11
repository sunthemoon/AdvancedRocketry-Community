# C16d ordinary carbon brick and user-interface acquisition

Status: PROPOSED, revised 2026-10-11 after independent review. This is one implementation leaf under accepted
ADR-064 section 10, not adoption of the entire proposed CONTRACT-02 graph.
Root is the sole implementer/integrator. Base source/record commit:
5267f77cab1f60f9655031efd5291238ab0f5f8c, separate clean worktree
D:/GitHub/arce-v180-c16d-components-20261011-01, branch
codex/v1.8.0-component-acquisition.

## Observable outcome and non-goals

Two ordinary materials have stable item identities and real loaded crafting
recipes, unlock data, bilingual names and complete item models/textures.
They can be crafted without a classic hatch, a private helper, a development
tooltip or a new machine. Existing circuits, wafers, recipes and their stand-in
status do not change. This leaf does not deliver the complete recipe graph,
machine families, energy/Level access witnesses, an IMPORT batch, a survival
video, JEI, dedicated restart, V1/V2, matrix/ledger completion or a Gate.

## Exact item and recipe semantics

Namespace: advancedrocketrycommunity. New persistent registry IDs are
carbon_brick and user_interface; each is a plain Item, stack size 64, with no
custom payload, ticking, capability, new packet, world state or schema.
No existing ID is removed/remapped; normal ItemStack save/load preserves both
new IDs. Existing worlds can acquire them through the new recipes; no inventory
rewrite or old circuit retirement occurs. An older build cannot resolve these
new items; this ordinary new-content limitation is documented, not silent
compatibility or a downgrade migration promise.

- carbon_brick: shapeless six separate minecraft:charcoal ingredients produce
  one carbon_brick. Coal, five/seven ingredients and a single large stack are
  not six recipe slots. It belongs to forge:ingots/carbon, with replace=false.
  Root's G-OPEN-3 choice for this leaf is no new fuel registration/burn behavior:
  it is a material ingot, not an energy multiplication feature. Test the clean
  built-in runtime fuel result; other mods' fuel-event overrides are not denied.
- user_interface: shaped two-row pattern lrl / fgf; l forge:dyes/lime,
  r forge:dusts/redstone, f forge:dusts/glowstone, g minecraft:glass_pane.
  Output one user_interface; preserve vanilla shifted/mirrored matching and
  reject wrong dyes, glass blocks, omitted ingredients or extra occupied slots.
  The removed legacy blank top row is formatting normalization, not a changed
  ingredient count or a new three-by-three constraint.
- Recipe IDs equal item IDs. Recipe groups are empty; category misc. Carbon's
  ordinary unlock is possession of charcoal; UI's is possession of glowstone
  dust. Requirements are vanilla OR of the ingredient-specific criterion
  (has_charcoal / has_glowstone_dust) and has_the_recipe,
  reward only the corresponding recipe, parent minecraft:recipes/root.
  Criteria/unlock alone do not claim the player has crafted the output.
- Add both to the existing creative tab, but use loaded crafting and ordinary
  vanilla ingredients as acquisition evidence, never creative giving as the
  claimed survival route. Nether/witch/portal root classifications belong to
  the later graph witness; this recipe does not restrict ingredient provenance
  or require a new discovery flag.

T-OPEN-1 is resolved for these three Forge selectors only by actual selected
build-classpath tag-member checks and installed runtime tag membership tests.
Do not assert tag existence from a name or assume the entire tag/graph question
is closed. G-OPEN-9 is immaterial to these two single-producer legacy items;
all alternate machine/circuit producer decisions remain open.

## Sources and art boundary

Behavior source is original Advanced-Rocketry/AdvancedRocketry, MIT, commit
c5cd5af62fc07cd4e0d24f06a16033f181c47c04. Verify actual selected source bytes
against legacy-manifest before using them:
- recipes/charcoalbrick.json: 586 bytes, SHA-256
  28ec6e6ce7aaf732dc166765795844a56aefdaa85acb409670fbf1b693bb62d2.
- recipes/userinterface.json: 580 bytes, SHA-256
  dc07f355dc95072ba3a5d57fde2948a7dd6209ca69dd3277c30a3c6beb64e599.
Both paths have prefix src/main/resources/assets/advancedrocketry/.
The earlier draft's charcoal body hash differs from the actual manifest;
do not reuse it as a measured source identity. Recipes and modern JSON/code
are newly authored from these facts, not copied legacy JSON.

carbon_brick.png is a NEW original 16x16 drawing in V180CraftingComponentData,
using only the existing V180MaterialArt PNG encoder. Do not inspect/copy/trace
the excluded legacy carbonbrick bitmap or official/third-party art as authoring
input. Draw a compact compressed dark block with a distinct silhouette, not a
recolored vanilla ingot. Final bytes and actual originality/asset review follow.

user_interface.png is also a NEW original 16x16 drawing in the same provider.
Draw a compact control panel with distinct display, controls and connectors;
do not copy/trace the quarantined upstream bitmap or official/third-party art.
Accepted ADR-064 section 12 requires every C16 texture to be NEW. The initial
IMPORT proposal conflicted with that decision and lacked the schema-2,
reproducible importer and asset-plan consistency required by ADR-061. It is
withdrawn, not accepted as an exception or silently counted as imported.
Synchronize only asset-plan row 247 (textures/items/userinterface.png) to
REGENERATE under ADR-064 section 12. No structured UPSTREAM_AR_MIT import
record or product bitmap copy is created. The selected quarantine bytes remain
external historical source-check evidence and are not drawing inputs. Preserve
carbonbrick's EXCLUDE rule and all other asset-plan rows. Actual generated NEW
bytes/hashes, originality screen and independent asset review remain required.

## Write scope after independent review and explicit Root adoption

Root only, in this worktree:
- registry/ModItems.java and ModCreativeTabs.java: exactly two new item/tab entries.
- datagen/BootstrapDataGenerators.java and V180LanguageProvider.java: one provider
  and two translations; one new datagen/V180CraftingComponentData.java.
- generated v1.8: exactly two item models, two NEW component PNGs, two recipes,
  two recipe-unlock advancements, forge:ingots/carbon tag and the two language
  file additions through the existing sole writer.
- new datagen/V180CraftingComponentDataTest.java and
  gametest/CraftingComponentGameTests.java, with no existing assertion changes.
- docs/provenance/v1.8.0-c16d-acquisition-preauthoring.md and later the actual
  dedicated NEW-resource hash record; asset-plan row 247 only; this task's contract/implementation/
  verification records; precise C16d leaf addition to COMPLETION-PLAN.md and
  CHANGELOG development entry only. Shared implementation log and AGENTS are
  not owned; use the task-specific implementation log in this directory.

No build/Gradle/CI, log-expectation, config, network, schema, machine adapter,
shared save writer, other recipe/resource, other asset-plan row or content-ledger edit.
Discovering a required additional file is a scope proposal before editing it.

## Verification and evidence

Before authoring: independent actual contract/source-input/selector review and
scoped adoption. Before a native run: committed source SHA, disk >=10 GiB,
Root-owned process/Job custody, exact command/cohort and finite limits.
Root is sole JVM/Gradle/native operator; workers are static-only unless assigned
a separately bounded actual verification scope. Use a fresh external evidence
packet, never alter old sealed evidence or worlds.

Tests must inspect actual emitted JSON/counts/tag/unlock/model/PNG references
and immutable source identities, and exercise loaded native shaped/shapeless
matches/assemble, wrong/excess/shifted inputs, exact tag membership and default
fuel behavior. No bypass asserts, expanded timeouts or fake roots. Use normal
recipes/vanilla matching; qualify actual craft consumption separately if the
fixture only invokes assemble. Persistence tests should distinguish native
ItemStack NBT round trip from full dedicated-server restart.

Run bounded clean build/test, runData twice with deterministic scoped diff,
native GameTest and unchanged strict log reconciliation as applicable. Preserve
all failures, actual counts, skipped/unexecuted checks and command SHA. Full
version/asset/server/visual Gates remain separate and open; this leaf cannot
approve the final graph or change ADR-018's full-campaign trigger.
