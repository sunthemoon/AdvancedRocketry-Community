# TASK W249: import batch P1, the ordinary rocket motor models

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w249` (new; branch `codex/v1.8.0-w249` from W246's approved and tested head `17bdece9b`, merged with the shared head named in your prompt; Root merges W246 into the shared branch by that SHA) |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55`: batch P1, sections 4 and 5, rules 291 and 320 |
| Earlier batches | W246 (flat textures, the stitched-folder rule and `PackagedBlockAtlasCoverageTest`), K2 to K4 and W244 (formed OBJ hulls through `ClassicObjModelRenderer`, a different route) |
| Record | `docs/work/v1.8.0-root-queue/records/W249.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W249/` |

**Root decision R271**, under the owner's delegation:

1. **Scope.** `rocket_motor` and `advanced_rocket_motor` and their block items, from the mono and advanced mono
   OBJ, MTL and texture triples that W245 matched (rules 291 and 320). The bipropellant, nuclear and tank meshes (P2
   to P4), the saw block, the laser gun and every other file under rule 291 stay out.
2. **Route.** An ordinary baked block model through Forge's `forge:obj` loader, so placed blocks, block items and
   assembled rockets (`RocketEntityRenderer` draws restored block states with `renderSingleBlock`) all show it. No
   block entity, formed flag, renderer, packet, persisted field or new block state for art. The block item reuses the
   block model with display transforms. Transcribe the legacy placement transform from upstream's blockstate rather
   than fitting by eye, and keep the mesh within the block's space or record why not.
3. **Textures and materials.** Forge's OBJ loader takes MTL textures from the block atlas, so each texture goes in
   `textures/block/` (W246's rule), named `<name>_classic.png` where retained NEW art holds the name. Rewrite the
   MTL references to the mod's namespace and lower case, and record every rewrite as W245 asks.
4. **No runtime fallback.** A broken resource pack shows Forge's missing model, as vanilla does. The bundled files
   are held correct by tests instead: the OBJ parses with the project's existing check, and the atlas test covers
   the materials.
5. **The atlas test** (this also settles W246-R6). Extend `PackagedBlockAtlasCoverageTest` so a `forge:obj` model is
   followed to its OBJ, its `mtllib` and every texture the MTL names, each of which must be stitched. Any other
   `loader`, and any `overrides`, must fail the test until it is modelled. Add fixtures for both.
6. **A short design note first**, committed before the code: the model JSON, the transform and its upstream source,
   the item display transforms, the atlas handling, and what the record will ask V1 to judge.

As in W246: the importer's plan, the provenance record (PENDING_HUMAN_REVIEW), import verify and the provenance
validator with the CI build's arguments; drafts (not edits) for the asset plan, the ledger and any ADR-061 or
`.gitattributes` change, for Root to apply. Replaced NEW art is listed with a proposed disposition, never deleted.

## Verification

- The atlas test's new fixtures failing first, then passing; the OBJ check, the client and data unit tests and the
  whole unit suite; runData twice with identical output.
- The importer, content-ledger and provenance Python tests, import verify and the provenance validator.
- Focused GameTests for the motors' existing batches (assembly and flight that place these blocks), under the
  heavy-JVM lock; no full GameTest run is needed.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

P2 to P4, S1, P0, H1, U1, fuel or bay behaviour, sounds (D5), status files and the ledger beyond the drafts.
