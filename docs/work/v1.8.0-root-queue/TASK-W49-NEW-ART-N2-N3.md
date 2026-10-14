# TASK W49: NEW art for the class D asset rows (closure batches N2 and N3)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w49-new-art` (from the shared branch) |
| Branch | `codex/v1.8.0-w49-new-art` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W49.md` (new) |
| Input | `docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.md` sections 3 and 5 (batches N2 and N3), and the 18 class D rows of `AUDIT-01.csv` |
| Specification | ADR-066 section 7.3; ADR-061 (NEW art, screening, provenance); `docs/08-ASSET-LICENSE-AND-PROVENANCE.md` |

The 18 class D rows need NEW work.

**N2, 10 rows.**
- Eight AR 16x-set block faces: `laserbottom`, `laserfront`, `machineelectrolyzer`,
  `machineelectrolyzer_active`, `precisionassemblerfront`, `railgun`, `satelliteassembler`,
  `warpcore`.
- Two solar-panel item icons: `basicsolarpanel`, `advancedsolarpanel`.

**N3, 8 rows: the space suit as one set.**
- Item icons and overlays: `spaceboots` and its overlay, `spacechestplate` and its overlay,
  `spacehelmet_overlay`.
- Armour layers: `spacesuit_layer1`, its overlay, and `spacesuit_layer2_overlay`.

The AR 16x set stays a NEW replacement until authorship is resolved (ADR-061 section 4.9). Do not
import or trace any legacy file.

## What to do

1. **Before any drawing**, write a pre-authoring record for each row, following the existing
   `docs/provenance/*-preauthoring.md` files: the intent, the method and the references allowed.
   You may describe the legacy file's purpose. Do not copy its pixels.
2. **Draw the art in code.** Use a DataGen provider like the existing `datagen/V180*Art.java`
   (deterministic, fixed seed, `StrictMath`), and write it to the target paths AUDIT-01 names.
   - If a target already holds an older NEW or placeholder texture, say in the record what changes
     and why.
   - Keep each texture's size and animation metadata consistent with the model that uses it.
3. **The suit.** Give the space suit a project armour material and texture layers, replacing
   `ArmorMaterials.IRON`.
   - Keep the item IDs and every existing suit behaviour: oxygen, modules, protection values, and
     the equipment tests.
   - If the protection values are bound to `IRON`, keep the same numbers in the new material and
     say so.
4. **Screening.** Screen every new image with `tools/audit/screen_generated_art.py`, without
   weakening any threshold. A hit is a failure, not something to argue away.
5. **Provenance records** for every row, in the format earlier NEW batches use: target path, SHA-256,
   generator class and commit.
6. **Tests.**
   - The existing resource and provenance tests, extended to the new files.
   - Each model resolves its textures with no missing texture.

## Commands

- The whole unit suite once.
- `runData` under the lock, then `git diff --exit-code -- src/generated` after committing.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- Any import, the importer (W48), OBJ models.
- ADR-062 revisions, the ledger and status files (Root). Root moves the rows to terminal after review.
