# C18c-01: inventory tutorial advancements

Date: 2026-10-04. Status: IMPLEMENTED_UNVERIFIED; native delivery remains pending.
Integrator: Root. Implementation owner: delegated C18 worker after leaf review.
Baseline: `85afa4139f74dd71f6963596b0d498ac6a0ebcfc`.
Contract: accepted ADR-066 section 6.2 and ADR-061 stable identities/DataGen.

## Outcome and dependencies

Provide six visible tutorial milestones using already registered, obtainable
items. Their feedback does not unlock recipes or spend research/resources.
They are independent of new shared hatches, typed propulsion, ground survey
jobs, world-first event election and the unproven charge-pad durability branch.

| Stable advancement ID under `advancedrocketrycommunity:` | Required inventory |
|---|---|
| `classic/root` | `minecraft:crafting_table` |
| `classic/block_press` | `advancedrocketrycommunity:small_plate_press` |
| `classic/rolling` | `advancedrocketrycommunity:rolling_machine` |
| `classic/electrolysis` | `advancedrocketrycommunity:electrolyzer` |
| `classic/suited_up` | All four named built-in `space_suit_helmet`, `space_suit_chestplate`, `space_suit_leggings`, `space_suit_boots` items |
| `classic/warp_core` | `advancedrocketrycommunity:warp_core` |

Use the real server-authoritative vanilla inventory-changed trigger, with
explicit item identities. The suit criterion is the conjunction of four
distinct named items; partial suits and equivalent external API equipment do
not satisfy this inventory tutorial. It is not a test of usable oxygen or a
new equipment capability. `warp_core` means item acquisition, never a completed
warp or a world-first travel event.

Display parents are a bounded acyclic tree: root -> block_press -> rolling ->
electrolysis -> warp_core, with suited_up under root. These display relationships
are not gameplay prerequisites. Reuse existing registered item icons and
referenced vanilla advancement background; copy no external assets. Existing
advancements and recipe-unlock entries remain unchanged. No registry alias or
old-world inventory rewrite is introduced. Old saves have no completed criteria
for these new stable IDs; ordinary inventory events may earn them naturally.

Translated title/description keys use
`advancement.advancedrocketrycommunity.classic.<suffix>.title` and `.description`,
in both en_us and zh_cn. Text explains item acquisition, without claiming the
associated machine, suit oxygen, landing or warp operation succeeded.

## Implementation ownership

The delegated worker uses a separate D worktree and writes only the new
advancement DataGen provider, its small language helper, pure/JSON tests,
dedicated GameTests and its own task progress/handoff. It proposes the two
central provider/language registration lines for Root to integrate. Exact
filenames are assigned after contract review. Root exclusively owns central
registration, generated-resource integration, ledger/status and commits/pushes.
No code is written before the exact leaf inputs are independently reviewed.

## Acceptance and verification

- All six stable IDs, direct targets and display references are unique and
  resolve; generated criteria/requirements implement the exact inventory goals.
- DataGen produces deterministic JSON and both languages without rewriting
  earlier version resources or removing unrelated v1.8 labels.
- Test the actual inventory-change path, rather than console advancement grant:
  positive targets, wrong/missing items, partial and complete suits, replay,
  correctly scoped server state and no side effects/rewards/recipe locks.
- A short packaged-server exercise must drive those same inventory triggers,
  preserve vanilla advancement progress across a clean stop/restart and verify
  already-earned criteria remain earned without extra resources or awards.
  Model-only criteria parsing does not substitute for this behavioural evidence.
- Clean build/test/DataGen, generated diff and required GameTests pass; pin the
  tested commit/JAR and independently review the actual diff and key tests.
- Content-ledger changes apply only to these six whole advancement units after
  their implementation, verification and integration evidence are complete.
  Do not close composite playerTick, other advancements or any version Gate.

Real GPU/multiplayer and whole-version progression acceptance remain required,
scheduled under ADR-018; this leaf does not assert those results. No new NBT root,
resource writer, API/network protocol, first-event outbox, travel completion,
recipe cost, numeric balance, asset import or damage configuration is in scope.

New temporary helpers/logs/results use the project-parent
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/` and per-process D TEMP/TMP/TMPDIR.

The original reviewed task SHA-256 is
`769f4e5d78b99bff9ed3e348aa9db3a4d061f2020e8ed6074dded0afd20f6a9d`.
Only checkpoint metadata was added after that review. The frozen semantics,
implementation boundaries and runtime obligations are unchanged; see
[review disposition](REVIEW-DISPOSITION-01.md).

Root has committed/pushed the exact reviewed six source files, both central
lines, six generated advancements and twelve new keys in each language at
`a5abc34809891d6b10724ee60ab9b5e97f036bae`. Fresh complete JUnit/DataGen and
all 467 required GameTests pass, including three actual inventory-listener
tests. Packaged restart and ledger delivery remain open. See
[automatic verification](VERIFICATION-01.md) and
[source integration](SOURCE-INTEGRATION-01.md). The separately reviewed native
fixture draft has an unresolved ownership Medium and is not admitted unchanged.
