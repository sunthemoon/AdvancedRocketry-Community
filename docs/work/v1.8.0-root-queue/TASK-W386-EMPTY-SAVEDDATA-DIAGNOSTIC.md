# TASK W386: an empty managed SavedData file is refused as invalid, not oversized

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w386`, branch `codex/v1.8.0-w386`, at the shared head |
| Decision | Root decision R506 (QUEUE row W386) |
| Prior work | [W384's record](records/W384.md): the X02 `ZERO_LENGTH` case on the satellite registry was refused at both starts with `ARCE-BETA-2002`, so the runner, which detects only the declared `ARCE-BETA-2000` line, classed it `NOT_CAUGHT` |
| Record | `docs/work/v1.8.0-root-queue/records/W386.md` |
| Write scope | `persistence/migration/BoundedSavedDataIo.java`, its unit tests and `WorldDataMigrationServiceTest`, and the record. No other production file, no runner, no oracle, no log manifest unless a GameTest proves an expected line changed. |

## The defect

`BoundedSavedDataIo.read` refuses a file whose size is 0 in the same branch as a file above the type's compressed
limit:

```java
if (compressedBytes <= 0L || compressedBytes > type.maxCompressedBytes()) {
    throw oversized(type, "compressed file size is " + compressedBytes + " bytes");
}
```

So an empty file, which is what a torn or zero-length write leaves, reports `OVERSIZED_DATA` (`ARCE-BETA-2002`). The
startup is blocked and the file is untouched, which is right, but an operator recovering the world is told the data is
too large.

## What to do

1. Refuse a size of 0 with `INVALID_SCHEMA` (`ARCE-BETA-2000`) and a detail that says the file is empty. Keep a size
   above the limit as `OVERSIZED_DATA`. Nothing else changes: the refusal still blocks startup, still writes no byte,
   and still precedes any backup or migration.
2. Check the staging path in `WorldDataMigrationService.stageMigrations`, which passes `OVERSIZED_DATA` through and
   wraps every other refusal as `STAGING_FAILED`: say in the record what an empty staged file now reports, and pin it
   with a test.
3. Add unit tests: an empty file for each managed type gives `INVALID_SCHEMA` with the empty-file detail and leaves
   the file's bytes unchanged; the existing oversized tests stay unchanged and pass.
4. Search the GameTests, the log manifest and the Python runners and oracles for an expectation of `ARCE-BETA-2002` on
   an empty file. List each one in the record. Change none outside the write scope; if one exists, stop and report it.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). No existing assertion changes.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Run the touched classes' unit tests and the full unit suite once.
  Use the heavy-JVM lock with the 90-second gap for one full GameTest run with the log checker.
- Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W386/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The last line of the record, and the last line of your answer, is the path of `records/W386.md`.
