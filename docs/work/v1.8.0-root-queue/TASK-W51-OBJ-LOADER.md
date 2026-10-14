# TASK W51: the OBJ model path and the centrifuge reference import (closure batch K1)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w51-obj-loader` (from the shared branch) |
| Branch | `codex/v1.8.0-w51-obj-loader` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W51.md` (new) |
| Specification | ADR-066 section 7.1 (the OBJ bullet) and section 7.3; `docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.md` section 5, batch K1; `records/W48.md` (the importer); ADR-061 sections 4.4 to 4.9 |

## What to do

1. **The physical-client OBJ path** (the ledger unit `event:ClientProxy.modelBakeEvent`). Use Forge's
   OBJ model loader, in physical-client code only.
   - **Budgets.** At most 65,536 vertices per baked object, and 262,144 vertices or 16 MiB per cached
     model set. Fail malformed or oversized geometry before allocation.
   - **References.** Every material and texture reference resolves inside the mod's resources. A
     missing one falls back to an explicit NEW or project-owned texture, never the purple missing
     texture.
   - **Reload.** On a resource reload failure, keep the last good cache. On success, clear the old
     resources.
   - **Sources.** No runtime source path, no download, no URL.
   - **The fixture.** Each model fixture freezes orientation, scale, rendering and bounding volume,
     without changing collision or the multiblock match.
2. **Import the centrifuge reference pair.** Bring in `models/centrifuge.obj` and
   `textures/models/centrifuge.png` with `tools/import/import_v180_assets.py`:
   - write the plan `tools/import/v180-k1-plan.json` and the record
     `docs/provenance/v1.8.0-c18d-k1.json`;
   - run `generate`, then `verify`;
   - clone the pinned upstream as W48's record describes (`--filter=blob:none`; a shallow clone
     fails), and fetch the two pinned client JARs with `tools/audit/fetch_vanilla_clients.py`.

   Then render the centrifuge with it as the reference.
3. **Keep fallbacks.** If the import's derivation or history check reports anything other than
   CLEAR, stop and report. Do not appeal a HIT. Do not move rows or edit ADR-062.
4. **Tests.**
   - Unit tests for the budget and reference checks on the client path, using client-logic tests
     that need no running client.
   - The importer's `verify` in the record.
   - The existing resource, provenance and GameTest suites stay green.

   There is no V1 claim. Say what a real-GPU reviewer should look at.

## Commands

- The whole unit suite once.
- `runData` under the lock, if generated data changes; then `git diff --exit-code -- src/generated`.
- `runGameTestServer` under the lock, then the log checker.
- The importer's `generate` and `verify` with the exact arguments, in the record.
- Delete your generated `build/gametest/world` and any upstream clone you made outside `build/` at
  the end with a plain `rm -rf`. If the tool refuses, record the paths and Root deletes them.

## Not in this task

- The other K batches (K2 to K6), NEW art, exclusions.
- ADR-062, the ledger and status files (Root).
