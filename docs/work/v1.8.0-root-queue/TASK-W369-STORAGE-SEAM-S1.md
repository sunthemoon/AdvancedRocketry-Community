# TASK W369: X01 and X02 simulation, slice S1 (the gated file-operations seam and the recorder)

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w369`, branch `codex/v1.8.0-w369`, at the shared head |
| Decision | Root decision R461 (QUEUE row W369) |
| Design | [W368-DESIGN](records/W368-DESIGN.md), sections 0, 2, 6 and 7.1 row S1; Root's answers to its section 8 are in QUEUE row W368 (R461) |
| Record | `docs/work/v1.8.0-root-queue/records/W369.md` |
| Write scope | `persistence/migration/CheckedSavedDataFile.java`, `CheckedSavedDataPolicy.java` (the `forceDirectory` body only), new `persistence/migration/CheckedFileOps.java` and `ReleaseTestStorageTrace.java`, one installer call beside the existing release-test start-up hooks, new tests under `src/test/java/.../persistence/migration/`, and the record. |

## What to do

1. **The seam.**
   - Move today's file operations of `CheckedSavedDataFile.replace` and `atomicMove`, and the body of
     `CheckedSavedDataPolicy.forceDirectory`, into one `DIRECT` implementation of a package-private `CheckedFileOps`
     interface. The operations are open, write, force, close, staged read, move, directory force and delete.
   - Keep the order of operations, every exception and every byte exactly as today.
   - The seam's field holds `DIRECT` unless the gated installer replaces it.
2. **The recorder.** As section 2.2 describes:
   - one unbuffered `ops.jsonl` line per operation, in a trace directory outside the world;
   - blobs stored once per SHA-256;
   - a copy of the old target at each open;
   - the bounds of 4,096 operations and 256 MiB of blobs, with `ARCE_RELEASE_STORAGE_TRACE_INVALID` logged once,
     after which recording stops.

   The recorder never changes a write's outcome.
3. **Gating.** Section 6 applies exactly. Install only with all of these:
   - the release-test hooks property;
   - a dedicated server;
   - the open release-test gate;
   - `advancedrocketrycommunity.storageTrace=<directory>`.

   Restore `DIRECT` at server stop. Add no command, network message, configuration key or saved field.
4. **Tests.**
   - A unit test pins that the field is `DIRECT` with no property.
   - Another shows that `DIRECT` gives the same bytes and the same exceptions as the old code for a normal write, a
     failed readback and a throwing committer.
   - Unit tests cover the recorder's lines, its blobs, its bounds and its refusal marker.
   - Every existing `CheckedSavedDataFile` and writer test runs unchanged.
5. **A native smoke.** Record one existing cut, for example a bay D1 MOVED cut, with the property set, on a locally
   built JAR under the heavy-JVM lock. Show that the trace has the expected operations in order and that the cut's
   result is unchanged.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change a ceiling (AGENTS.md section 5). Production
  behaviour must be byte-identical without the property.
- Run the targeted tests, the full unit suite, and the full GameTest server with the unchanged log checker under the
  heavy-JVM lock. Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by another engine follows before the merge.
- The record gives what was done, the commits, the commands and results, the smoke's trace summary and anything not
  done. Its last line, and the last line of your answer, is the path of `records/W369.md`.
