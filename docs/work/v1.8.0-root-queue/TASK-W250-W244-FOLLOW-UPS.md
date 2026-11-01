# TASK W250: W244's follow-ups (the capsule's render box and the copied resource adapter)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w250` (new; branch `codex/v1.8.0-w250` from the shared head named in your prompt, which includes W244) |
| Findings | W244-R5 and W244-R7 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-recheck/RECHECK.md` (SHA-256 `92742d746c27ed62673220963ed7cfb05f3bb868ed90e903aac84f2ed0625ca2`) |
| Background | [TASK-W244-FIX](TASK-W244-FIX.md) (R263) and the W244 record, `docs/work/v1.8.0-root-queue/records/W244.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W250.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W250/` |

**Root decision R272**, under the owner's delegation:

1. **W244-R5 (Low).** Forge skips a block entity renderer whose render box is outside the view, and only the
   endpoint's renderer offers the capsule, so at a terminal the capsule above the platform is culled while in view
   (a rider looking ahead loses it). Add a fixed capsule box to `ElevatorEndpointBlockEntity.getRenderBoundingBox()`
   for both endpoint kinds, covering `CapsuleMesh.MODEL_BOUNDS` at any yaw, with no client class in common code. Pin
   it with a unit test that fails first. Keep the tether column and K4's existing render-box assertion.
2. **W244-R7 (Nit).** `CapsuleResources.source` repeats `ObjModelReloadListener`'s resource adapter and its diagnostic
   truncation line for line. Share one package-level helper instead, with no change to what either reads or logs, and
   state in `CapsuleMesh`'s Javadoc why the capsule reads its geometry itself (the reasons the recheck lists).
3. **The record.** `records/W244.md` says the capsule "is not dropped while its edge is in view"; append a dated
   correction note after that paragraph rather than rewriting it.

## Verification

- The new render-box test failing first, then passing; the client model, elevator and K4 projection unit tests and the
  whole unit suite.
- The focused K4 elevator GameTest batches under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

The capsule's look (R265's V1 items), other K batches, status files and the ledger.
