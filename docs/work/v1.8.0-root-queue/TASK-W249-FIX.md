# TASK W249-fix: the review's changes to import batch P1

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w249` (existing; branch `codex/v1.8.0-w249` at `92c62ae37`, which adds Root's W249-R5 move of the ADR-061 paragraph to `07a43c8d0`) |
| Findings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-review/REVIEW.md`, SHA-256 `019d7ecac5096e31c51df7d04f76b3eaf7b3931032affc4ef1b23cbcd95e9c27` |
| Task it answers | [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [W249-REVIEW](TASK-W249-REVIEW.md), Root decisions R271 and R276 |
| Record | Append a "W249-fix" section to `docs/work/v1.8.0-root-queue/records/W249.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-fix/` |

**Root decision R276**, under the owner's delegation:

1. **W249-R1 (High).** The provenance record hashes a CRLF working copy of the plan, so import verify fails on any
   clean checkout. Make the plan's working file LF (in this worktree `git ls-files --eol` shows `w/crlf`), rerun
   `generate`, and commit the record; only `plan_sha256` and `review.content_digest` may change. Verify again on a
   clean export of the commit, not in this worktree. Then make the importer refuse a plan or record that contains a
   CR byte, with a unit test that fails first, so this cannot recur. Append a correction to W249.md's verify row.
2. **W249-R2 (Medium), fixed in P1.** Add `.noOcclusion()` to the `rocket_motor` and `advanced_rocket_motor`
   registrations, as upstream's `isOpaqueCube` returned false. First check every server-side use that could depend on
   these blocks occluding, being solid or blocking light (rocket assembly, sealing and atmosphere checks, landing
   and placement rules) and report each; if any gameplay result would change, stop and report instead. Test that
   both blocks do not occlude and that light reaches a cell beneath one, and rerun the motors' GameTest batches.
3. **W249-R3.** State in the design note why the motors use `flip_v: false` while the formed hulls use `true` (the
   motors came through Forge's 1.12 blockstate OBJ loader, not LibVulpes'), citing the Forge 1.12.2 `OBJModel` source
   if you can reach it, or saying plainly that it is unverified. Keep texture orientation on the V1 list.
4. **W249-R4.** Record why the textures are named `*_classic.png`: upstream `textures/blocks/combustion.png` (asset-plan
   row 236) would land on the same stitched name under W246's rule.
5. **W249-R7.** Add the atlas-walk fixture for an OBJ `usemtl` material that the MTL does not define.
6. **W249-R9.** The resource auditor treats a top-level `mtl_override` (and any other `forge:obj` metadata key it
   does not walk) as metadata, with a test.

W249-R5 (the ADR paragraph) and W249-R6 (CI verifies every import record) are Root's; W249-R8 waits for P2 or X3.

## Verification

- The new tests failing first where they can; import verify on a clean export; the importer Python tests.
- The client and data unit tests and the whole unit suite; the motors' focused GameTests under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine rechecks.

## Not in this task

P2 to P4, the asset plan beyond W249's rows, status files and the ledger.
