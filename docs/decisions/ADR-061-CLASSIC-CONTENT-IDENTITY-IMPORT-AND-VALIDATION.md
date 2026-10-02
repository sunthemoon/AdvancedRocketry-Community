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
  resources reference 36 distinct LibVulpes resource locations, and some of
  those LibVulpes files came from an unmerged third-party pull request;
- legacy file names say nothing about origin: the Moon turf and ferric sand
  are recolours of the vanilla grass top, the plate press faces a filtered
  vanilla piston, and several GUI sheets the vanilla container frames, while
  two model JSON files are byte-identical to vanilla models (§4.8); five
  sound events point to one silent placeholder, and the sounds and the
  JPEG planet images carry no embedded attribution;
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
   any existing ID. The placeholders the version document (§8, §10) means are
   formalised under their existing IDs:
   - fourteen `DevelopmentComponentItem`s whose tooltip says they have no
     machine behaviour yet (`silicon_wafer`, `basic_circuit`,
     `advanced_circuit`, `data_storage_unit`, `satellite_chassis`,
     `satellite_solar_module`, `advanced_solar_panel`, `satellite_battery`,
     `large_satellite_battery`, `satellite_cargo_hold`, `survey_scanner_module`,
     `solar_transmitter_module`, `asteroid_drill_module`, `gas_intake_module`)
     lose that tooltip and get their classic recipes in C16d;
   - the community-authored placeholder block models of the v1.2 machines and
     the other blocks built from casing and vanilla textures get their final
     models in C18d;
   - the C19 upgrade check loads a v1.7 world that holds every one of these
     IDs and confirms that items, block states and block entities survive
     unchanged.
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
   `fans/…`, `boules/…`); dilithium crystals use `forge:gems/dilithium`.
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
5. Every classic machine profile and every new world feature has a server
   switch. A disabled machine keeps its blocks, block entities and stored
   resources but does not process; a disabled feature stops generating in new
   chunks. This is how an unstable machine is taken out of play without a
   migration (version document §17).

### 4. Asset import pipeline

1. **Allowed sources.**
   - `UPSTREAM_AR_MIT`: `Advanced-Rocketry/AdvancedRocketry`, branch `1.12`,
     commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, MIT,
     `Copyright (c) 2017`.
   - `NEW`: community-authored files under the repository's MIT license.
   - `GENERATED`: DataGen output.
   No other source is allowed. Advanced Rocketry Reworked, ARLib, Advanced
   Rocketry 3, other forks, released JARs and texture packs stay excluded.

   **LibVulpes is not an approved source in v1.8.** Its branch `1.12` at
   `c2ca79dc18625c9e63a191a795f1f07d078f29f0` carries an MIT `LICENSE`
   (`Copyright (c) 2017`, SHA-256
   `1f9978a442976337a86ea9ea5a0c97ae6b1bdab56d9661d05938f163b8e55be3`, added on
   2017-10-01), but the files Advanced Rocketry relies on do not have a clear
   enough chain for a category approval:
   - commit `984d67474a50494840aba551eb3baa434a24cf2f` (2020-05-04) took the
     casing, hatch, battery and plug textures "from the unmerged Cl1ff PR",
     that is, from a contribution that was never merged;
   - its ingot and nugget templates match the vanilla gold ingot and nugget
     silhouettes on 98.8 % of pixels;
   - the tree bundles a non-MIT third-party JAR, so the root `LICENSE` is not a
     per-file statement.
   The material products, ores, casings, hatches, coils, motors, the linker
   and batteries therefore use `NEW` art drawn for this project (or the
   project's existing textures). Two Advanced Rocketry files are byte-identical
   to LibVulpes files and are `REVIEW` for the same reason:
   `textures/blocks/machinegeneric.png` (the `984d6747` casing) and
   `models/item/models/motor.obj`. A later revision may approve individual
   LibVulpes files only with a per-file manifest (path, git blob and SHA-256 at
   a pinned commit, last commit and author, the §4.8 check and an authorship
   finding) recorded in `docs/provenance/` before any copy; the maintainer's
   acceptance would approve that file list, not a category.
2. **Asset plan.** [`v1.8.0-asset-plan.csv`](../work/v1.8.0-asset-plan.csv)
   decides every legacy asset by its first matching rule:
   - `IMPORT`: candidate for its batch;
   - `REVIEW`: quarantined until an origin review is recorded (sounds,
     planet images in formats the derivation check cannot read, files that
     share a vanilla file name, and `SUSPECT` derivation verdicts);
   - `REGENERATE`: reference only; DataGen writes the modern file;
   - `EXCLUDE`: never imported;
   - `IMPORTED`: already recorded (v0.1.0).
   Each rule names its owning ledger units; an `IMPORT` or `REVIEW` rule cannot
   own a `DEFERRED` or `REJECTED` unit. The importable assets and their ceiling
   (`IMPORT` or `REVIEW`) are listed in
   [`v1.8.0-asset-import-allowlist.txt`](../work/v1.8.0-asset-import-allowlist.txt),
   whose SHA-256 ADR-062 pins. A batch may tighten a rule (import fewer files,
   move a file to `EXCLUDE`) in its own commit; adding a file to the allowlist
   or raising its ceiling needs an ADR-062 revision. A `REVIEW` file becomes
   `IMPORT` only through a recorded origin finding in
   `docs/provenance/v1.8.0-origin-findings.json` (decision `CLEARED`, reviewer,
   date, basis); a finding with decision `EXCLUDED` forces `EXCLUDE`.
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
   - any file in the tree has the SHA-256 of a legacy-manifest asset and no
     provenance record names it;
   - a file added under `src/main/resources/` after the v1.8 baseline is
     neither inside a DataGen output root nor recorded with a matching target
     hash (files that predate the baseline keep their earlier records);
   - a record's target is missing or its hash differs.
   The JAR audit fails on any `QUARANTINED` or `REJECTED` target and on any
   packaged file with a legacy-manifest hash that has no record.
8. **Vanilla derivation check.** A file name says nothing about origin: the
   legacy Moon turf and ferric sand are recolours of the vanilla grass top
   with a different name. `tools/audit/vanilla_derivation.py` compares every
   legacy asset with the assets of the vanilla 1.12.2 and 1.20.1 client JARs
   (read locally, never copied; their SHA-256 values are in the results) by
   file hash and, for PNG images of the same size or an integer scale of 2
   or 4, by byte-equal pixels outside the vanilla image's dominant colour
   (`overlap`), opaque-mask intersection over union (`iou`) and luminance
   rank correlation (`rank`). Verdicts: `HIT` when the hash is equal,
   `overlap` ≥ 0.30, or `iou` ≥ 0.90 with `rank` ≥ 0.90; `SUSPECT` when
   `overlap` ≥ 0.10, or `iou` ≥ 0.85 with `rank` ≥ 0.75; `UNSUPPORTED` for
   formats it cannot decode; otherwise `CLEAR`. A `HIT` is never imported or
   reviewed into the tree (docs/08 §7); `SUSPECT` and `UNSUPPORTED` files are
   `REVIEW` at most and import only after a `CLEARED` origin finding. The
   results are committed in
   [`v1.8.0-vanilla-derivation.json`](../work/v1.8.0-vanilla-derivation.json);
   the validator refuses any `IMPORT` whose verdict is not `CLEAR`. The
   importer re-runs the check for each entry it writes (for derived pixels,
   on the transformed file too) and records the verdict in the entry; a
   derivative of an `EXCLUDE` or `REVIEW` file inherits that handling.
9. **History.** Each record entry lists the upstream commits that touched
   its source file. A history entry that brings in material from an issue,
   an unmerged pull request or another project (for example the plate
   press textures added from issue #1527, whose author describes them as a
   filtered vanilla piston) puts the file under `REVIEW`.

### 5. Content validation and the recipe graph

1. DataGen writes models, blockstates, recipes, tags, loot tables, language
   files, sound definitions and advancements; `runData` followed by
   `git diff --exit-code` stays empty.
2. The resource validator checks: model → texture, blockstate → model,
   sound definition → OGG, language key → registered object, recipe → registered
   item or tag, no case collision, unique registry IDs.
3. A **recipe graph** tool proves that every registered item is obtainable
   from a new survival world. Its nodes are items, fluids and three kinds of
   prerequisite:
   - **energy:** a powered recipe needs a reachable Forge Energy source (the
     C16a combustion generator first);
   - **Level access:** world generation, loot and surface resources of a body
     count only once a rocket that can reach it is buildable, with its fuel,
     route and discovery requirement (ADR-035, ADR-037), so an ore found only
     on the Moon cannot be an input of the first Moon rocket;
   - **research and discovery:** satellite missions, mission rewards and
     recipes behind research count only once their satellite, terminal and
     research gates are reachable.
   The roots are vanilla survival items obtainable in the Overworld. A cycle is
   allowed only when every member also has an acyclic route. Items that need no
   route (technical blocks, fluid blocks, the force field block, the unlit
   torch, creative-only content) are listed in a committed exemption file, each
   with a reason, and reviewed with the batch; nothing is skipped silently. The
   tool writes a report (reachable items, the access and energy path of each,
   unreachable items, cycles) and fails on any unreachable item or hard-locked
   cycle. It is introduced in C16d and must pass from then on.
4. The [content ledger](../work/v1.8.0-content-ledger.csv) moves rows from
   `PLANNED` to `IMPLEMENTED` (or to an ADR-backed disposition) in the batch
   that delivers them, with the delivered modern IDs and the batch's evidence
   file in the `evidence` column. A row cannot be `IMPLEMENTED` or
   `REDESIGNED` with plan `v1.8.0` without that file; work v1.8 still has to
   build stays `PLANNED`, including redesigns.
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

- Material, casing, hatch, coil and motor art is drawn new instead of taken
  from LibVulpes.
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
