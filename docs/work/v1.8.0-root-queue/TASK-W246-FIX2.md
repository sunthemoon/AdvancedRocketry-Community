# TASK W246-fix2: stitch the imported textures (W246-R1 to R4)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w246` (existing; branch `codex/v1.8.0-w246` at `e495440bb`, whose sharded run 41318121110 passed) |
| Findings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-review/REVIEW.md`, SHA-256 `91b384f34f209c88594e7673931704bb4ff6c188b1f4a4cec33a59325b4ab274`, with its `atlas_coverage.py` |
| Task it answers | [TASK-W246](TASK-W246-FLAT-TEXTURES.md) and [TASK-W246-FIX](TASK-W246-FIX.md), Root decisions R262 and R267 |
| Record | Append a "W246-fix2" section to `docs/work/v1.8.0-root-queue/records/W246.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix2/` |

**Root decision R268**, under the owner's delegation:

- **W246-R1 (High).** Move the sixteen imported textures from `textures/blocks/` and `textures/items/` into the
  singular `textures/block/` and `textures/item/` folders that the 1.20.1 block atlas stitches, as ADR-061 section 4
  and the K6 batch already do. Where the singular name is already taken by retained NEW art (`jackhammer`, `jetpack`,
  `thermite`, and any other the move finds), name the import `<name>_classic.png`; do not touch the NEW art. Update the
  import plan, provenance records (W246's import record and the R267 superseding record, through new dated records if
  either is sealed), the providers, the generated models and the tests. Import verify must pass again.
- **W246-R2 (Medium).** Add an automated atlas-coverage unit test, based on the review's `atlas_coverage.py`: every
  texture any generated or imported block or item model references must lie in a folder the block atlas stitches (or
  in an atlas source the repository declares). It must fail on `e495440bb` and pass after the move. Run it across all
  models, not only W246's, and report anything else it finds.
- **W246-R3 and R4 (Low).** The VERIFICATION summary stops calling itself an unapplied draft and names the R267
  record; the seven provider Javadocs stop calling the retained art a fallback. Give the VERIFICATION change to Root
  as a draft.

## Verification

- The new atlas test failing first, then passing; the importer, content-ledger and provenance Python tests, import
  verify and the provenance validator with the CI build's arguments.
- runData twice with identical output, the client and data unit tests and the whole unit suite.
- The repository validator.

Commit on the branch; do not push. Root pushes (dispatching the run if the push is docs-only), and a fresh session of
the other engine rechecks.

## Not in this task

Other batches, status files, the ledger and the asset plan beyond the paths this move changes.
