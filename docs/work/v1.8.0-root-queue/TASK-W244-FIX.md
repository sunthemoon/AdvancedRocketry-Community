# TASK W244-fix: the capsule's look, and the importer and capsule follow-ups

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w244` (existing; branch `codex/v1.8.0-w244` at `5d0799199`, which adds Root's ADR-061 blank line, W244-R3, to the reviewed `96e4925c9`) |
| Findings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-review/REVIEW.md`, SHA-256 `1756fd9853507e0d1953729ab7d4b76077e5ce066f6535ca5a6e1215471f5edd`: W244-R1 (Medium), W244-R2 and W244-R4 (Low) |
| Task it answers | [TASK-W244](TASK-W244-K5-AND-ETCHER.md), Root decisions R259 and R263 |
| Record | Append a "W244-fix" section to `docs/work/v1.8.0-root-queue/records/W244.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-fix/` |

**Root decision R263**, under the owner's delegation, for W244-R1:

- Replace W244's new cage UV layout. While at least one ride is pending at an endpoint, draw one stationary capsule at
  that endpoint: the legacy `Capsule` group of the already-imported `spaceelevator.obj` (K4), with the capsule
  texture through the group's own UVs, placed by the legacy capsule renderer's transform at upstream `c5cd5af6`
  (transcribe it and pin it in a test). Riders inside it stay visible as the legacy capsule allowed; say how.
- One capsule per endpoint, however many riders are pending, so the drawn geometry is bounded by the existing
  endpoint limits. The group's vertices already count in K4's catalog budget; say whether the total changes.
- Missing or unreadable texture or model: the existing line cage, as before.
- No new field, entity, packet, ride action or persisted state. Write a short design note in the record before the
  code.

## Steps

1. **W244-R1** as R263 says, with a test that the drawn capsule's sampled texels are opaque where the legacy mesh is
   solid (the review's check), and the transform test.
2. **W244-R2.** When the identical-candidate rename appears only against a merge's second parent, record the
   candidates in `rename_candidates` too, so ADR-061's "every candidate is listed" holds. Failing first, with the
   review's reproduction as the test case.
3. **W244-R4.** Check the capsule texture's availability once per resource reload, not every frame, and fall back to
   the line cage when it is unreadable as well as missing.
4. Update the VERIFICATION summary's capsule paragraph as a draft for Root.

## Verification

- The new tests failing first where they check a behaviour change, then passing.
- The importer and content-ledger Python tests, import verify, the client model and capsule unit tests, and the whole
  unit suite.
- The elevator capsule and model GameTest batches in a focused run under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and the W244 reviewer's findings are rechecked by a
fresh session of the other engine.

## Not in this task

The flat textures (W246), other model batches, status files and the ledger.
