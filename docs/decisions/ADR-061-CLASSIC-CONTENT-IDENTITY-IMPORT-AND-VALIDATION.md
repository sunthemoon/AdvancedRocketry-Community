# ADR-061 — Classic content identity, import and validation

```yaml
status: PROPOSED
revision: 1
date: 2026-10-02
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.8.0
development_dependency: ADR-016, ADR-019, ADR-021, ADR-026, ADR-027, ADR-031, ADR-054, ADR-060
used_by: [ADR-062]
supersedes: ""
```

## Context

v1.8 restores most of the classic content: more than 200 legacy blocks, items,
variants, materials, fluids, biomes and features are planned (ADR-062), and the
asset plan names 350 legacy files as import candidates. Until now the project
imported 10 upstream files (v0.1.0) and drew every other texture from its own
three casing textures or from vanilla resource locations. The version document
(§5) asks for frozen contracts on stable IDs, per-batch source manifests, the
v1.2 machine framework, DataGen and validator checks, and a technology tree with
no unreachable node or hard-locking cycle. The legacy audit
([content audit](../work/v1.8.0-content-audit.md)) found the facts this contract
must handle:

- the legacy game registers metadata variants (seven loader blocks, six crystal
  colours, six circuits, six upgrades, four pressure tanks) under one ID each;
- legacy materials (titanium, steel, aluminum, …) and their product textures,
  hatches and casings come from **LibVulpes**, not from Advanced Rocketry; AR
  resources reference 36 distinct LibVulpes resource locations;
- some legacy files are presumed copies of vanilla files (one model JSON is
  byte-identical; iron armor layers, lava, the font and the sun share vanilla
  names); six sound events point to one silent placeholder; the sounds and the
  photographic planet images carry no embedded attribution;
- 1.12 blockstates, models, recipes and advancements use formats that 1.20.1
  cannot load; they are reference data, not importable files.

## Decision

### 1. Registry identity

1. Every new block, item, fluid, entity, biome, feature, sound event, enchantment
   and menu uses the namespace `advancedrocketrycommunity` and a stable
   lower-case `snake_case` path. Legacy names, legacy casing and the legacy
   namespace are never registered.
2. **Flattening.** Each legacy metadata variant becomes its own ID (for example
   `tracking_circuit`, `item_io_board`, `violet_crystal_block`). No item or block
   encodes its kind in NBT or damage.
3. **Existing IDs keep their identity.** A classic tier that already has a
   modern ID (for example `rocket_motor`, `rocket_fuel_tank`, `basic_circuit`)
   keeps it; new tiers get new IDs. v1.8 adds IDs and does not rename or remove
   any existing ID.
4. **Permanence.** From the first commit that registers an ID, renaming it needs
   a `MissingMappingsEvent` remap and an ADR revision; removing it needs a scan
   of the representative worlds and a migration (version document §8). An ID
   never becomes a different thing.
5. **Recipes are not inventories.** Changing a recipe changes how new items are
   made; it never rewrites items players already hold. Every recipe change is
   listed in the batch's release notes.
6. **Missing data.** A data pack that references a missing v1.8 ID fails with
   the existing aggregated reload error that names the ID; nothing is silently
   deleted.

### 2. Materials and tags

1. Every material product is tagged with the Forge common convention
   (`forge:ingots/titanium`, `forge:plates/steel`, `forge:rods/titanium`,
   `forge:dusts/…`, `forge:nuggets/…`, `forge:gears/…`, `forge:storage_blocks/…`,
   `forge:ores/…`, `forge:raw_materials/…`), plus `advancedrocketrycommunity:`
   tags for the products Forge does not name (`sheets/…`, `coils/…`,
   `boules/…`).
2. Recipes consume tags, not item IDs, wherever a material is meant. Other
   mods' titanium therefore works in our machines, and our products work in
   theirs. This replaces the legacy `makeMaterialsForOtherMods` switch.
3. Vanilla materials stay vanilla: copper, iron and gold ingots and ores, basalt
   and concrete are not re-registered.
4. Ores follow 1.18+ conventions: stone and deepslate variants where the ore
   generates in the Overworld, a raw item, and smelting plus blasting recipes.

### 3. Machines

1. Classic processing machines run on the v1.2 kernel (ADR-016): a
   `ProcessDefinition` per recipe, a resource snapshot, the transaction executor,
   process ports and the bounded multiblock pattern with the lifecycle
   coordinator. The C16 batch ADR freezes one shared machine family (profile,
   port layout, menu, recipe serializer) so that each classic machine is a
   profile, not a new adapter.
2. A machine that cannot use the kernel needs its own exception ADR before it
   is implemented.
3. Every machine has at least a happy-path, a failure and a restart GameTest, a
   save round-trip test, JEI registration through the client compat package,
   and startup with and without JEI.
4. Every new C2S intent follows the existing intent policy (sender, distance,
   state, loaded chunk, rate limit, bounded payload), and every new channel is
   pinned in `network-protocols.txt` with its version.

### 4. Asset import pipeline

1. **Allowed sources.**
   - `UPSTREAM_AR_MIT`: `Advanced-Rocketry/AdvancedRocketry`, branch `1.12`,
     commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, MIT,
     `Copyright (c) 2017`.
   - `THIRD_PARTY_APPROVED` (LibVulpes): `Advanced-Rocketry/libVulpes`, branch
     `1.12`, commit `c2ca79dc18625c9e63a191a795f1f07d078f29f0`, whose root
     `LICENSE` is MIT with `Copyright (c) 2017` (SHA-256
     `1f9978a442976337a86ea9ea5a0c97ae6b1bdab56d9661d05938f163b8e55be3`, added
     on 2017-10-01 as "more permissive licence"). Allowed scope: only files that
     Advanced Rocketry 1.12 references (material product textures, machine
     casing and structure textures, hatches, coils and the dilithium ore). The
     first batch that imports one adds the license copy to `docs/licenses/` and
     an entry to `THIRD-PARTY-NOTICES.md`. Acceptance of this ADR is the
     approval of this source and scope; the status stays per file.
   - `NEW`: community-authored files under the repository's MIT license.
   - `GENERATED`: DataGen output.
   No other source is allowed. Advanced Rocketry Reworked, ARLib, Advanced
   Rocketry 3, other forks, released JARs and texture packs stay excluded.
2. **Asset plan.** [`v1.8.0-asset-plan.csv`](../work/v1.8.0-asset-plan.csv)
   decides every legacy asset by its first matching rule:
   - `IMPORT`: candidate for its batch;
   - `REVIEW`: quarantined until an origin review is recorded (sounds,
     photographic planet images, files sharing a vanilla name);
   - `REGENERATE`: reference only; DataGen writes the modern file;
   - `EXCLUDE`: never imported;
   - `IMPORTED`: already recorded (v0.1.0).
   A batch may tighten a rule (import fewer files, move a file to `EXCLUDE`) by
   editing the plan in its own commit; loosening `EXCLUDE` or `REVIEW` needs an
   ADR-062 revision.
3. **Records.** Each batch writes `docs/provenance/v1.8.0-<batch>.json` with a
   schema-2 record before the files enter the tree. Schema 2 keeps every
   schema-1 entry field (target path, status, source repository, branch,
   commit, path, source and target SHA-256, license, copyright notice,
   transformation list), allows several sources per batch and up to 512
   entries, and keeps the batch `review` block. A `NEW` entry names the
   repository as its source and the authoring commit's parent as its base.
4. **Reproducible conversion.** A shared importer under `tools/import/`
   generates and verifies each batch from the pinned upstream trees: `generate`
   writes the files and the record, `verify` recomputes both and fails on any
   difference. Allowed transformations are byte copies, renames (namespace,
   lower case, singular directories, flattened names), OBJ/MTL reference
   rewrites and `.lang` key extraction. A file whose pixels are changed
   (recolouring, cropping, palette edits) is still derived from its source: it
   keeps the source's status and notice, the importer makes the change by a
   script, and the record lists the change as a transformation. Only art drawn
   from scratch is `NEW`. Runtime tinting of byte-copied greyscale templates is
   preferred to recoloured copies.
5. **Review.** Records enter with review status `PENDING_HUMAN_REVIEW`. The
   maintainer's visual and license review sets `APPROVED`; G0 needs every
   record approved. `REVIEW` assets enter only after a recorded origin finding;
   otherwise the batch uses a `NEW` replacement.
6. **Placeholders.** Content without an approved asset uses a model that
   references the project's own textures or vanilla resource locations (the
   established pattern), never a copied vanilla file and never a missing
   texture.
7. **Enforcement.** A repository check fails when:
   - any file in the tree has the SHA-256 of a legacy-manifest asset (or of an
     imported LibVulpes file) and no provenance record names it;
   - a file added under `src/main/resources/` after the v1.8 baseline is
     neither inside a DataGen output root nor recorded with a matching target
     hash (files that predate the baseline keep their earlier records);
   - a record's target is missing or its hash differs.
   The JAR audit fails on any `QUARANTINED` or `REJECTED` target and on any
   packaged file with a legacy-manifest hash that has no record.

### 5. Content validation and the recipe graph

1. DataGen writes models, blockstates, recipes, tags, loot tables, language
   files, sound definitions and advancements; `runData` followed by
   `git diff --exit-code` stays empty.
2. The resource validator checks: model → texture, blockstate → model,
   sound definition → OGG, language key → registered object, recipe → registered
   item or tag, no case collision, unique registry IDs.
3. A **recipe graph** tool reads the generated recipes, loot tables, world
   generation and mission tables and proves that every registered item is
   obtainable from vanilla survival roots. A cycle is allowed only when every
   member also has an acyclic route. The tool writes a report (reachable items,
   unreachable items, cycles) and fails on any unreachable item or hard-locked
   cycle. It is introduced in C16d and must pass from then on.
4. The [content ledger](../work/v1.8.0-content-ledger.csv) moves rows from
   `PLANNED` to `IMPLEMENTED` (or to an ADR-backed disposition) in the batch
   that delivers them, with the delivered modern IDs.
   `scripts/validate_v180_content_ledger.py` checks every row; C19 runs it with
   `--require-accepted` and requires zero `PLANNED` rows.

### 6. Budgets

- Idle machines do no per-tick scanning; work is driven by events and the
  kernel's bounded revalidation.
- Pumps, force-field projectors and world-changing tools have per-tick and
  per-operation limits and use the ADR-054 §5 protection chain.
- New world generation runs only in new chunks; every feature has bounded size
  and count per chunk.

### 7. Rollback

Each batch keeps its registrations, data and assets in its own content package,
DataGen output root and provenance record, so an unreleased batch can be
reverted as a whole. After a release, removing a batch needs the §1.4 migration.

## Alternatives

### A. Copy the whole legacy asset tree first, sort it out later

Rejected by the version document (§4) and by AGENTS.md §3.2: unrecorded files
would enter the tree, presumed vanilla copies would ship, and the review would
not be per batch.

### B. Draw everything new, import nothing

Removes the license work but loses the classic look the version promises, and
costs far more art time than reviewing MIT files.

### C. Re-implement LibVulpes materials under a `libvulpes` namespace

Rejected: the project does not create a LibVulpes copy (AGENTS.md §3.1), and a
foreign namespace would collide with a real LibVulpes port.

## Consequences

### Positive

- Every file in the JAR has a source, a hash and a review state.
- Materials interoperate with other mods through common tags.
- Machines share one tested kernel path.

### Negative

- LibVulpes becomes a recorded third-party source that the maintainer must
  approve.
- The asset review becomes a maintainer task per batch.

## Validation

- `scripts/validate_v180_content_ledger.py` and its unit tests (C14);
- the import `verify` mode and the repository provenance check (from C15a);
- the recipe graph report (from C16d);
- per-machine GameTests and the full Gradle suite in every batch.

## Revisit when

- a needed asset exists only in a source this ADR does not name;
- the maintainer's review rejects an imported file;
- a machine cannot fit the kernel.

## Review history

- Revision 1: proposed with the C14 audit.
