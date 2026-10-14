# W47 AUDIT-01: C18d per-asset closure audit

Task: [TASK W47](../v1.8.0-root-queue/TASK-W47-ASSET-CLOSURE-AUDIT.md). Date: 2026-10-14.
Implementer: a headless Claude session. Branch `codex/v1.8.0-w47-asset-audit`, based on the shared
branch at `16109683`. Root reviews and merges.

This is a read-only audit. It changes no runtime asset, ledger row, provenance record, ADR or
status file, imports nothing, and moves no REVIEW row to CLEAR or EXCLUDE. Every row below is a
proposal for Root.

| File | Content |
|---|---|
| [`AUDIT-01.csv`](AUDIT-01.csv) | The 155 input rows in input order, with `class`, `runtime_target`, `runtime_sha256`, `evidence` and `missing` |
| [`check_audit_01.py`](check_audit_01.py) | The read-only check of this audit (section 8) |
| This file | Counts, method, closure batches, owner questions and the run record |

Input: [`asset-coverage.csv`](../v1.8.0-c18-contract/proposals/revision-03/asset-coverage.csv) of the
accepted ADR-066 revision 3: 155 rows, 77 REVIEW, 77 IMPORT and 1 REGENERATE.

## 1. Result

| Class | Meaning | Rows | IMPORT | REVIEW | REGENERATE |
|---|---|---:|---:|---:|---:|
| A | terminal now | 0 | 0 | 0 | 0 |
| B | import ready | 38 | 38 | 0 | 0 |
| C | NEW replacement exists, record missing | 11 | 0 | 10 | 1 |
| D | needs NEW work | 18 | 0 | 18 | 0 |
| E | not used by runtime, a candidate for EXCLUDE | 81 | 39 | 42 | 0 |
| F | needs the owner or an author | 7 | 0 | 7 | 0 |
| Total | | 155 | 77 | 77 | 1 |

No row is terminal under ADR-066 section 7.3, for four reasons:

- **Nothing is imported.** No file under `src/` has the SHA-256 of any of the 155 sources, and no
  record in `docs/provenance/` imports one.
- **No origin finding covers them.** `docs/provenance/v1.8.0-origin-findings.json` holds one finding
  (`textures/gui/buttons/tabtemplate.png`, EXCLUDED), which is not one of these rows. There is no
  CLEARED finding at all.
- **The only NEW replacements are unreviewed.** The ten W16 sounds and their `sounds.json` are
  recorded in `docs/provenance/v1.8.0-c18d-presentation.json` with review status
  `PENDING_HUMAN_REVIEW`.
- **No row is EXCLUDE.** Every row is still IMPORT, REVIEW or REGENERATE in the asset plan.

Two constraints shape the closure:

- **There is no v1.8 importer.** ADR-061 section 4.4 requires a shared importer under `tools/import/`
  with `generate` and `verify`. Only `tools/import/import_v010_assets.py` exists, and it is
  v0.1.0-only: it writes schema 1, at most 64 entries, only to `src/main/resources` and
  `src/generated/resources`, and only the v0.1.0 record. Every class B row waits for it.
- **C19 fails on any REVIEW plan row.** `python -B scripts/validate_v180_content_ledger.py --closure`
  fails while any asset is still handled as REVIEW. So all 77 REVIEW rows must leave REVIEW: to IMPORT
  through a CLEARED origin finding, or to EXCLUDE through an EXCLUDED finding or an ADR-062 revision.
  Eight plan rules each cover several of these files (section 3).

## 2. Method

1. **Inputs and pins.** I read the input CSV, the current asset plan (first-match rules) and the
   import allowlist. The allowlist's SHA-256 (`fc5ebb60...`) equals the ADR-062 pin. I also read the
   derivation results, whose SHA-256 (`97aaee71...`) equals the ADR-061 pin, and the origin findings,
   the content ledger and every file in `docs/provenance/`.
2. **Source identity.** Every `source_sha256` equals `legacy-manifest/assets.csv`. Every
   `source_sha256` also equals the file in the pinned upstream tree: the C14 extraction of
   `ar-c5cd5af.zip` in the local Temp folder. I read that tree and hashed it; I copied nothing from it.
3. **Imported copies.** I hashed every file under `src/` against the 155 source hashes and found no
   match. I then searched every `docs/provenance/` file for the 155 source paths and hashes. No hash
   matched. The only path matches were modern targets that share a legacy name (`sounds.json` and
   `sounds/lathe.ogg`), in the C18d presentation and sound pre-authoring records and the v0.1.0
   generated-resources record.
4. **What the runtime ships.** For each row I followed the owning ledger unit to its modern ID. I then
   read the shipped blockstate, block or item model, sound definitions and registration. I searched the
   runtime Java for every texture binding. Outside block and item models it binds only
   `textures/entity/suit_components.png`, the vanilla beacon beam and a vanilla advancement background.
   I also searched for an OBJ loader (none), for registered particle types (none; the runtime uses
   vanilla `ParticleTypes` only) and for textured sky rendering (none). For rows without an owning
   unit, and to tell live legacy files from dead ones, I read the legacy consumer in the upstream Java:
   the renderers, `ClientProxy`, `TextureResources` and `ItemSpaceArmor`.
5. **Classification.** Each row gets exactly one class:
   - **A:** the full ADR-066 section 7.3 record exists.
   - **B:** the row is IMPORT, the allowlist ceiling is IMPORT, and the derivation verdict is CLEAR with
     no inherited review. It also has a runtime consumer, either today or as an accepted, open plan. A
     plan counts when it is a PLANNED ledger unit or the ADR-062 C18d scope ("OBJ models", "the visual
     refresh of existing blocks"), for an owner that exists today. Examples are a modern formed
     structure that the legacy OBJ depicted, or an item that now shows a vanilla placeholder.
   - **C:** a NEW replacement ships, but its excluded-origin record or review is incomplete.
   - **D:** the row is REVIEW and has no replacement. The delivered owner shows a placeholder where
     the legacy file was, or would show the file after a model-only change.
   - **E:** nothing in the runtime uses the file, and no accepted open plan would. Either the slot is
     gone (a redesigned, deferred or rejected owner, a single-block redesign, a state that does not
     exist), the legacy file had no consumer even in 1.12.2, or the runtime draws the feature from code.
   - **F:** closing the row needs provenance that only the owner or an author can supply.
6. **Rule numbers.** The input's `rule_order` predates commit `53523bcf`. That commit inserted plan
   row 200 (`recipes/jackhammer.json`, IMPORTED) and renumbered the rows after it. So input rules 201
   and later are one lower than the current plan. Each `evidence` cell cites both numbers; the
   pattern, handling and owning units are unchanged.

Each `evidence` cell starts with the current plan rule, the allowlist ceiling and the derivation
verdict (with the count of related legacy files). It then confirms that no `src/` file carries the
source hash, names the legacy consumer and states what the runtime ships. Every shipped file is cited
as `path@commit`, where the commit is the one that added the file.

## 3. Rows by class

**B, import ready (38).** These are 16 legacy machine OBJ models, 15 of their model textures, the
elevator capsule texture and 6 item textures:

- OBJ models and their textures: astrobody data processor, beacon (OBJ only), black hole generator,
  centrifuge, chemical reactor, crystalliser, cutting machine, lathe, observatory, orbital laser drill,
  precision assembler, precision laser etcher, railgun, rolling machine, solar array and space elevator.
  Each modern owner exists today with a JSON cube placeholder and has a machine pattern; the solar array
  is a controller-owned multiblock. The consumer is still to be built: the C18d OBJ loader (ledger
  `event:ClientProxy.modelBakeEvent(ModelBakeEvent)`, PLANNED).
- The beacon's model texture `textures/models/beacon.png` is REVIEW, outside these rows. The beacon OBJ
  therefore needs that file cleared, or a NEW texture drawn on its UV layout.
- `textures/models/spaceelevatorcapsule.png` is for the PLANNED capsule effect
  (`entity:ARSpaceElevatorCapsule`). The legacy capsule also used `models/spaceelevator.obj` as its mesh.
- Item textures that would replace vanilla placeholder parents: `basiclens` (laser lens), `satellite`
  (satellite package), `satelliteidchip` (satellite control chip), `opticalsensor` (data satellite
  package), `microwavetransmitter` (solar transmitter module) and `oremapper` (survey scanner module).

**C, NEW replacement exists, record missing (11).** The ten sounds and `sounds.json`:

- The NEW synthesized files ship and are registered (W16, commit `c09a1e34`), and their record target
  hashes match.
- Still missing: the recorded excluded origin of each legacy OGG, the maintainer's APPROVED review, and
  the manual listen required by ADR-066 section 7.1.
- The ten `sound_event:*` ledger rows are still PLANNED.

**D, needs NEW work (18).** Each of these modern owners exists but shows a placeholder:

- Eight 16x-set block faces: the laser drill bottom and front, the electrolyzer front and active front,
  the precision assembler front, the railgun, the satellite assembler and the warp core.
- Two 16x-set satellite solar panel icons.
- Eight space-suit textures: three armour layers and five item icons. The worn suit uses
  `ArmorMaterials.IRON`, and the item models use vanilla iron parents. The rest of the legacy set is
  EXCLUDE because it derives from vanilla: `spacesuit_layer2.png`, and the helmet and leggings icons
  `spacehelmet.png`, `spaceleggings.png`, `space_helmet.png` and `space_leggings.png`. So the suit is
  drawn NEW as one set. The legacy suit was dyeable leather whose overlays were its untinted layers, so
  a NEW set may merge each overlay into its base.

**E, not used by runtime (81).** Nothing uses these files, for the reasons below:

- **Drawn from code: the sky and the laser beam (46).** The 45 sky rows are all 33 REVIEW planet
  images, the 5 IMPORT planet images (`asteroid_a/b/c`, `asteroidleo`, `spoopy`), the 4 IMPORT
  `textures/env/*` files, the REVIEW `env/sun.png`, and the IMPORT `models/atmosphere.obj` and
  `models/atmosphere.mtl`. The planetary sky is six fixed meshes with no textures, and the black-hole
  sky and accretion disc are code geometry. The legacy `atmosphere` model also served the redesigned
  holographic selector. The 46th row is the IMPORT `textures/entity/flare.png`: the modern laser beam
  is the vanilla beacon beam.
- **Particles (6).** The runtime spawns vanilla particles only.
- **Single-block redesigns (6).** The OBJ and model texture of the area gravity controller, the
  electrolyser and the warp core. Their modern owners are single blocks with no formed structure.
- **Deferred or redesigned owners (9).** `diamondmetal`, `fan` and `metalplate` served only the
  deferred terraformer (like `tubes.png`, already EXCLUDE). `girder` and `grid` drew the assembly-bounds
  frame that ADR-062 section 2 removed. `lens1` belonged to a lens block that is now an item.
  `vacuumlaserfront` belonged to a block redesigned as casing plus lens. `eyecandy/earth` served only
  terraforming GUIs. `precisionassemblerfront_active` has no active state on the modern block.
- **Owners delivered without the file (2).** `rockethud`: the W16 HUD is drawn from primitives.
  `spinnything`: the delivered analyzer and warp screen draw no eye candy.
- **Dead even in 1.12.2 (10).** `satelliteprimaryfunction0..5`, `satellitepowersource0/1`,
  `items/space_boots` and `items/space_chestplate` had no legacy reference.
- **Folded variants (2).** `compositionsensor` and `massdetector`: their variants fold into the data
  satellite package, which shows one icon.

**F, needs the owner or an author (7).** The seven legacy translations: `de_DE`, `es_ES`, `fi_FI`,
`fr_FR`, `pt_br`, `ru_RU` and `ua_UA`.

The plan rules that cover several of these files are: `lang/*.lang` (7, F), `sounds/*.ogg` (10, C),
`textures/planets/*leo.jpg` (12, E), `textures/particle/*` (6, E), `textures/planets/*` (5, E),
`textures/env/*` (4, E) and `models/atmosphere.*` (2, E). One rule mixes classes:
`textures/items/satellite*.png` (8 files: 2 B, 6 E) must be split before its rows can close separately.
The AR 16x set accounts for 10 class D rows and 21 class E rows.

## 4. Choices where the inputs left room

- **"Needed by the runtime".** I read this as a current consumer, or an accepted, open plan for one
  (section 2, step 5). I did not count a wish that the runtime might someday use a file. This puts the
  whole sky set in E, because ADR-066 section 7.1 specifies the black-hole sky as code geometry. If Root
  or the owner wants textured bodies, the 33 REVIEW planet images become D, and the 5 IMPORT planet
  images become B with a new consumer.
- **One icon for the data satellite package.** It shows one icon, so I assigned it the variant-0
  `opticalsensor.png` (B). `compositionsensor.png` and `massdetector.png` are E. Swapping is a one-row
  change for Root.
- **Single-block owners.** I put the OBJ of a modern single block in E rather than B. Keeping one would
  need a scale fixture over one block, and for the gravity controller a NEW texture, since its model
  texture is REVIEW.
- **`laserbottom.png` is D.** The modern `orientable` model has no bottom face, but adding one is a
  model-only change. `precisionassemblerfront_active.png` is E, because adding an active face needs a
  new block state.
- **"Recorded excluded origin".** I read this as an origin decision recorded against the legacy file.
  That is an EXCLUDED finding, plus the ADR-062 revision that ADR-066 section 7.3 requires for moving
  REVIEW to EXCLUDE. A statement in the NEW record that the legacy file was not used is not enough.
- **The REGENERATE row.** ADR-066 section 7.3 names no terminal form for REGENERATE. I put
  `sounds.json` in C, pending review of its DataGen record, and left the closure form for Root to confirm.

## 5. Proposed closure batches

Each batch is sized for one task. Batches that import or draw art run `runData` once under the
machine lock, then the GameTest server once (WORKER-RULES). They also need the maintainer's review of
their record (ADR-061 section 4.5) before the rows are terminal.

| Batch | Rows | Work | Tools | Depends on |
|---|---:|---|---|---|
| K0 importer | 0 | Write the ADR-061 section 4.4 importer `tools/import/import_v180_assets.py`, with `generate`/`verify`, schema-2 records, several sources and up to 512 entries. It supports byte copy, rename, OBJ/MTL reference rewrite and `.lang` key extraction. It re-runs the derivation check per entry and records each entry's upstream history (section 4.9) from a pinned clone. Add the OBJ/MTL format and origin check that ADR-066 section 7.3 requires: parse, vertex and face counts against the section 7.1 budgets, and references that resolve with no external path. Unit tests only; no import. | Python `unittest`; `tools/audit/vanilla_derivation.py` | none |
| K1 OBJ loader | 2 | Build the physical-client OBJ path (ledger `event:ClientProxy.modelBakeEvent`), with the section 7.1 fixture, budgets, last-good cache and explicit fallback. Use it to import the centrifuge OBJ and texture as the reference pair. | K0 `generate`/`verify`; `runData`; `runGameTestServer` | K0 |
| K2 C16 machines | 10 | Import the OBJ and texture pairs of the chemical reactor, crystalliser, cutting machine, lathe and precision laser etcher. | as K1 | K1 |
| K3 research, beacon, solar | 7 | Import the astrobody data processor, observatory and solar array pairs, and the beacon OBJ. The beacon needs its REVIEW texture resolved or a NEW one. | as K1; `tools/audit/screen_generated_art.py` if NEW | K1 |
| K4 endgame structures | 12 | Import the precision assembler, rolling machine, railgun, orbital laser drill, black hole generator and space elevator pairs. | as K1 | K1 |
| K5 capsule | 1 | Build the PLANNED client capsule effect and import its texture. It uses the K4 elevator mesh. | as K1 | K4 |
| K6 item icons | 6 | Import the six item textures and point their item models at them. Split plan rule `textures/items/satellite*.png` first (Root). | K0 `generate`/`verify`; `runData` | K0 |
| N1 sound closure | 11 | Owner: listen to the ten sounds and approve the C18d record. Reviewer: one EXCLUDED origin finding per legacy OGG. Root: the ADR-062 revision for `sounds/*.ogg`, the ten ledger rows, and confirm the `sounds.json` form. | `scripts/validate_v180_content_ledger.py --require-accepted`; `scripts/validate_repository.py` | none |
| N2 NEW faces and icons | 10 | Draw NEW art (a DataGen provider like `datagen/V180*Art.java`) for the eight 16x-set block faces and two solar panel icons, with a pre-authoring record and a provenance record. | `tools/audit/screen_generated_art.py`; `runData`; resource tests | none |
| N3 NEW space suit | 8 | Draw NEW suit icons and armour layers as one set, and replace `ArmorMaterials.IRON` with a project armour texture. | as N2 | none |
| X1 exclusions | 81 | Root and owner: one ADR-062 revision that moves the class E rows to EXCLUDE as non-goals, with this audit's no-consumer evidence. It splits the satellite rule, re-pins the allowlist and states the appearance differences in the reader documentation. | `scripts/validate_v180_content_ledger.py`; the allowlist pin | owner acceptance |
| X2 final moves | 25 | After N2, N3 and the owner's answers: record the excluded origins of the D rows and move them to EXCLUDE. Then import or exclude the F rows (through K0 with `.lang` key extraction, or a NEW credited translation). | as X1; K0 for `.lang` | N2, N3, section 6 |

The K batches import with
`python -B tools/import/import_v180_assets.py generate --upstream <upstream/ar at c5cd5af> --plan tools/import/v180-<batch>-plan.json --record docs/provenance/v1.8.0-c18d-<batch>.json`,
then `verify` with the same arguments. This is the interface K0 is to provide, mirroring
`import_v010_assets.py`; it does not exist yet. The batches then run:

- `python tools/audit/vanilla_derivation.py --upstream <tree> --vanilla 1.12.2=<jar> --vanilla 1.20.1=<jar> --check`;
- `python -B scripts/validate_v180_content_ledger.py --require-accepted`;
- `python -B scripts/validate_repository.py`.

The order that frees the most rows first is X1 and N1 (92 rows, records only), then K0, then K6, K1
to K5 and N2 to N3 in parallel, and X2 last.

## 6. Owner questions (class F)

1. **The seven legacy translations** (`de_DE`, `es_ES`, `fi_FI`, `fr_FR`, `pt_br`, `ru_RU` and `ua_UA`).
   Which do you want?
   - (a) Import the legacy text after a per-file contributor finding.
   - (b) New translations by credited translators.
   - (c) English and Chinese only. Then the seven rows go to EXCLUDE through an ADR-062 revision.

   The legacy keys use 1.12.2 names, so (a) yields only the keys that map to a modern key; the rest
   fall back to English.
2. **If (a): what is enough provenance?** No record here names the translators. Do you accept the
   upstream repository's MIT licence as covering translations its maintainers merged? Or do you
   require each translator's own confirmation? Either way, Root needs the per-file upstream history,
   which needs network access.

Related decisions that come from other classes, not from F:

3. **The AR 16x set** (ADR-061 section 4.9). Will anyone ask the merger or Cl1ff about authorship? A
   finding would let the 10 class D 16x rows be imported instead of drawn NEW, and would change how the
   21 class E 16x rows are excluded. Without it, they stay NEW and EXCLUDE.
4. **Textured planets.** Is an untextured, code-drawn sky acceptable as the v1.8 look? That decides
   whether the 45 sky rows stay E (section 4).
5. **The manual listen** of the ten NEW sounds, and the APPROVED review of the C18d presentation
   record (N1).

## 7. Observations outside the 155 rows

- **Stale language targets.** In `docs/provenance/v1.8.0-c18d-presentation.json`, the targets
  `advancedrocketrycommunity_v180/lang/en_us.json` and `zh_cn.json` no longer match their recorded
  `target_sha256`; later batches rewrote both files. ADR-061 section 4.7 treats a record whose target
  hash differs as a failure. Root may want a correction entry. The record's ten sound targets,
  `sounds.json` and `suit_components.png` match.
- **Earlier NEW art is recorded as prose.** The NEW art of the earlier v1.8 batches is recorded in
  prose provenance files (`*-new-resources.md`, the v1.8 development metadata) and in batch evidence,
  not in per-file schema-2 records like C18d's. ADR-066 section 7.3 brings other batches' unfinished
  assets into the C18d integration audit. This audit does not cover them.
- **Unreviewed NEW faces.** The NEW face textures of the C16b, C16c and C18c machines carry open art
  and provenance reviews, according to their verification records.

## 8. Record of this run

Commit: one commit on `codex/v1.8.0-w47-asset-audit` adds the three files in this folder. Its SHA is
in the final answer. Files changed: `docs/work/v1.8.0-c18d-asset-closure/` only (`AUDIT-01.csv`,
`AUDIT-01.md` and `check_audit_01.py`).

Commands and results:

- `python -B docs/work/v1.8.0-c18d-asset-closure/check_audit_01.py --upstream <C14 upstream/ar tree>`
  passed:

  ```text
  [PASS] 155 rows, each input row once and in order; classes: A 0, B 38, C 11, D 18, E 81, F 7
  [PASS] runtime and source SHA-256 values match the files on disk and the upstream tree
  [PASS] 90 path@commit citations name the adding commit; plan, allowlist, derivation and record facts match
  ```

  Without `--upstream` it passes the same way and checks the source hashes against
  `legacy-manifest/assets.csv` only. It takes about 6 seconds.
- A negative test (a scratch script outside the repository) mutated this folder's CSV and report one
  fault at a time, ran the check, then restored both files byte for byte. The seven faults were a
  changed runtime hash, a duplicated row, two swapped rows, a wrong citation commit, a misstated
  derivation verdict, class B on a REVIEW row and a wrong count in section 1. Each run exited 1 with
  the matching `[FAIL]` line.
- `git diff --check` and `git diff --cached --check` printed nothing.
- No Gradle run: the task changes no code or generated resource.

What the check script verifies:

- **Rows.** The header; each input row exactly once, in order, with its input columns unchanged.
- **Hashes.** Each `runtime_sha256` against its file; each `source_sha256` against the legacy
  manifest and, with `--upstream`, the upstream tree; and that no `src/` file carries a source hash.
- **Pins.** The allowlist and derivation-results pins.
- **Citations.** That each `path@commit` names the commit that added the file, and that the file
  still exists.
- **Facts.** The cited plan rule, allowlist ceiling and derivation verdict; for B rows, the
  inheritance rule; for C rows, a NEW record entry with the runtime hash.
- **Counts.** The class counts in section 1.

Not done: no import, NEW art, origin finding, ADR-062 revision, ledger or status change (sections 5
and 6). Other batches' assets were not audited (section 7).

Known risks:

- Class E rests on reading "needed" as an existing or accepted consumer. If the owner wants more art,
  rows move to B or D (section 4).
- The CLEAR verdicts of the OBJ, MTL, OGG and `.lang` rows are hash comparisons only. ADR-066 section
  7.3 still requires their own origin and format check, which K0 must add before K1.
- The upstream tree used here is a local Temp extraction, not a committed input. The check script
  verifies the source hashes against the committed legacy manifest on every run.
