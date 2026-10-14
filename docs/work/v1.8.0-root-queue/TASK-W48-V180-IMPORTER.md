# TASK W48: the v1.8 asset importer (closure batch K0)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w48-importer` (from the shared branch) |
| Branch | `codex/v1.8.0-w48-importer` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W48.md` (new) |
| Specification | `docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.md` section 5, batch K0; ADR-061 sections 4.4 to 4.9; ADR-066 sections 7.1 and 7.3; `docs/08-ASSET-LICENSE-AND-PROVENANCE.md` |

Every import batch in AUDIT-01 (K1 to K6, and X2's `.lang` rows) needs a v1.8 importer. Only the v0.1.0
importer exists. This task writes the tool and its tests. **It imports nothing.**

## What to build

`tools/import/import_v180_assets.py`, modelled on the existing `import_v010_assets.py`.

- **Subcommands.**
  - `generate --upstream <tree> --plan <plan.json> --record <record.json>`;
  - `verify` with the same arguments.
- **Records.** Schema-2 provenance records with:
  - several sources;
  - up to 512 entries per record;
  - each entry's source and target SHA-256, transformation and upstream history.
- **Transformations.**
  - a byte copy;
  - a rename;
  - an OBJ and MTL reference rewrite;
  - `.lang` key extraction to the modern keys a plan names.
- **Checks during `generate` and `verify`, for each entry:**
  - the derivation check (`tools/audit/vanilla_derivation.py`);
  - the upstream history (ADR-061 section 4.9), from a pinned clone at the commit `UPSTREAM.md` names;
  - the OBJ and MTL format and origin check from ADR-066 section 7.3, which:
    - parses the files;
    - counts vertices and faces against the section 7.1 budgets (65,536 vertices for each baked
      object; 262,144 vertices or 16 MiB for each cached model set);
    - requires every material and texture reference to resolve inside the plan;
    - rejects any external or absolute path.
- **Failures.** A failing check fails the whole run, with no partial output. `verify` reproduces
  `generate` byte for byte.
- **The plan.** `tools/import/v180-<batch>-plan.json`: the source path, the target path, the
  transformation, the owning ledger units and the asset-coverage row.

## Tests (unit tests only)

- Every transformation, on small fixtures that are not upstream art. Make fixtures with code, or use
  repository-owned NEW files.
- Every check failing on purpose:
  - a hash mismatch;
  - a derivation hit;
  - an oversized OBJ;
  - a dangling material;
  - an absolute or external reference;
  - more than 512 entries;
  - a `.lang` key the plan does not map.
- `generate` followed by `verify` round-trips.
- A record from `import_v010_assets.py` still verifies with its own tool. Nothing about v0.1.0 changes.

## Commands

- The new unit tests, plus the existing Python tests for `tools/` and `scripts/`, run with the
  repository's test command.
- No Gradle or Minecraft run is needed. If you run one, say why.

## Not in this task

- Any import, any plan for a real batch, any change to runtime assets or provenance records.
- ADR-062 revisions, the ledger and status files (Root).
