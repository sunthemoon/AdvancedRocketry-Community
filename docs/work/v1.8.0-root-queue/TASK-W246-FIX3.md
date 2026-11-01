# TASK W246-fix3: the atlas test's expected model set (W246-R5)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w246` (existing; branch `codex/v1.8.0-w246` at `bc39b1347`) |
| Finding | W246-R5 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-recheck/RECHECK.md` (SHA-256 `d89e73d89dc13d0191d664bae6198f5166b8d60075f4241d9d81d09930b74ce9`), with its probe scripts and logs in the same folder |
| Task it answers | [TASK-W246-FIX2](TASK-W246-FIX2.md) and [TASK-W246-RECHECK](TASK-W246-RECHECK.md), Root decisions R268 and R270 |
| Record | Append a "W246-fix3" section to `docs/work/v1.8.0-root-queue/records/W246.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix3/` |

The recheck closed W246-R1, R3 and R4. `PackagedBlockAtlasCoverageTest` checks only the models it finds, so a packaged
tree missing one model, or holding a single model, still passes (W246-R5, Medium). `RegisteredContentResourcesTest`
requires an item model for every registered item and a blockstate for every block, but nothing requires the models
that blockstates and item models name to exist.

**Root decision R270**, under the owner's delegation:

1. **Derive the expected set from references.** Before checking textures, the atlas test collects every model that a
   packaged blockstate of the mod names (variants and multipart, every `model` value), every item model that
   `RegisteredContentResourcesTest` requires (reuse its registration capture or its helper rather than a second
   registry walk), and every parent in those models' chains. Each one in the mod's namespace must be packaged; each
   one in another namespace must resolve on the test classpath. Every packaged model, reachable or not, keeps its
   texture check. Name any deliberate exception in the test with a reason; do not add one without need.
2. **Negative coverage.** Run the same check on small fixture trees in a temporary folder: one missing model named by
   a blockstate, a missing parent and a proper subset must each fail; a complete fixture passes. Keep the existing
   empty-input, `filter` and unknown-source failures.
3. Keep every existing assertion of the atlas test and of `V180FlatTextureImportTest`. Report anything the new
   check finds across all models; if it finds a real missing model, stop and report it rather than changing content.

## Verification

- The new negative tests failing on `bc39b1347`'s test, where they can, then passing; the recheck's two partial probes
  (`probe-missing-one`, `probe-one-model`) now failing.
- The client and data unit tests, the whole unit suite and the repository validator.

Commit on the branch; do not push. Root pushes, and a fresh session of the other engine rechecks.

## Not in this task

The texture move, the provenance records, other batches, status files, the ledger and the asset plan.
