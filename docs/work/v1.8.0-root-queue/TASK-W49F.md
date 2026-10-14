# TASK W49F: write W49's model bindings as v1.8 copies, not in place

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w49f-bindings` (from the shared branch, plus Root's commit `16c1ed71` and this task) |
| Branch | `codex/v1.8.0-w49f-v18-bindings` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W49F.md` (new) |
| Source | The Repository governance run 38591893577: `test_validate_v080_release_evidence` fails because W49 (`714a480c`) changed the sealed `docs/provenance/v0.8.0-generated-resources.json` |

## What happened

To rebind its NEW art, W49's `V180ClosureModels` wrote 13 models **in place** into historical generated
roots (v0.2, v0.4, v0.8, v1.2, v1.5, v1.6 and v1.7), and rewrote three sealed provenance records
(v0.2.0, v0.4.0 and v0.8.0). Historical generated roots are immutable inputs. The correct pattern is
W52's: new v1.8 copies, plus a Root build exclusion of the old ones.

Root's commit `16c1ed71`:
- restores the 13 historical models and the three records byte for byte from `714a480c^`, so the
  v0.8.0 release evidence validates again;
- excludes those 13 historical copies (`supersededW49Models`) from `processResources` and
  `sourcesJar`.

## What to do

1. **Write the v1.8 copies.** Change `V180ClosureModels` so it writes the same 13 model JSONs (the
   same asset-relative paths and the same content as W49's bindings) into its own output,
   `src/generated/v1.8/resources`. It must never write into a sibling root. Remove the "in-place"
   design and its guard.
2. **Run `runData` under the lock.**
   - The 13 models appear under `src/generated/v1.8/resources/assets/advancedrocketrycommunity/models/`.
   - The historical roots and the three records stay exactly as Root restored them; check with
     `git diff --exit-code -- src/generated/v0.2 src/generated/v0.4 src/generated/v0.8 src/generated/v1.2 src/generated/v1.5 src/generated/v1.6 src/generated/v1.7 docs/provenance/v0.2.0-generated-resources.json docs/provenance/v0.4.0-generated-resources.json docs/provenance/v0.8.0-generated-resources.json`.
3. **Update W49's tests and provenance** (`V180ClosureResourcesTest`, `V180ClosureProvenanceTest`, the W49
   provenance records) to the v1.8 paths.
   - Keep every assertion's intent.
   - Add a test that no file under a historical generated root differs from its sealed provenance
     record. Reuse the existing validators where they fit.
4. **Check the jar.** Run `build` under the lock and check that each of the 13 models is packaged
   exactly once, as the v1.8 copy.
5. **Run the governance checks locally:**
   - `python -B scripts/validate_v080_release_evidence.py`, and the v0.2.0 and v0.4.0 release
     validators if they exist;
   - `python -B -m unittest discover -s tests`, using a long TEMP path as W50's record explains.

   All must pass.

## Commands

- The whole unit suite once.
- `runData` and `build` under the lock.
- `runGameTestServer` under the lock, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf` and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- New art, or any change to `build.gradle` or to Root's restored files.
- Status files and the ledger (Root).
