# TASK W379F: slice S4 follow-up, Root's answers to W379's two questions

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w379`, branch `codex/v1.8.0-w379`, at Root's integration merge `4d12570d3` (W379's slice S4 plus the shared head, which now holds the merged S1 seam) |
| Decision | Root decision R486 (QUEUE row W379) |
| Design | [W368-DESIGN](records/W368-DESIGN.md), sections 3, 4 and 5; W379's record `docs/work/v1.8.0-root-queue/records/W379.md` on this branch, sections "Two questions that slice S5 needs answered" and "Known risks" |
| Record | Add a section "W379F follow-up (R486)" to `records/W379.md`; do not rewrite W379's sections |
| Write scope | `persistence/migration/ReleaseTestStorageFault.java` and its unit tests, `scripts/run_v180_storage_faults.py` and `tests/test_run_v180_storage_faults.py`, and the record. Nothing else; no existing runner, oracle or production writer changes. |

## Root's answers (R486)

1. **Declared hold markers, faulted start only.**
   - The runner's manifest declares, before any case runs, a per-store list of hold markers. The list has exactly
     two entries:
     - `ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED` for the transfer journal;
     - `ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED` for the satellite registry.
   - An ERROR line that carries a declared marker for the faulted store is accepted only in the faulted start, and
     only after an `ARCE_RELEASE_STORAGE_FAULT injected` line for that store. The classification evidence keeps every
     accepted line.
   - The runner's own check ("Native run logged ERROR or FATAL") is not edited. The X02 runner evaluates the start
     itself, with the markers filtered, and records both results.
   - Any other ERROR or FATAL line in the faulted start, and every ERROR or FATAL line in either restart, keeps its
     current effect.
   - `NEEDS_REVIEW` stays for any case that still has an unaccepted line.
2. **An optional skip field.**
   - The property becomes `<store>:<fault>:<count>[:<skip>]`. The skip is 0 to 48 and defaults to 0.
   - The three-field form keeps its exact current meaning, and its unit tests stay unchanged.
   - The injector passes the store's first `skip` writes to `DIRECT` before it counts the faulted writes. Its log
     lines report the skip, and the `attempt` lines still cover at most the store's first 64 writes.
   - Use it in the default campaign where a first-write fault cannot reach the cut. At least the registry case gets
     the skip that passes the runner's baseline request, chosen from the recorded write sequence, with the reason in
     the manifest.
3. **Cases that cannot complete.**
   - The plan marks a case `NOT_APPLICABLE` before the run, with its reason, when it cannot complete by construction.
     `BAY_RECEIPTS:WRITE_ERROR:4` is one: with one rocket, no second receipt write follows.
   - Where a count-4 case can reach the writer's retry limit (a store that retries the same generation), keep it.
   - The campaign's status rule stays: `NOT_RUN`, `INCOMPLETE` and `NEEDS_REVIEW` keep it `INCOMPLETE`.
   - A declared `NOT_APPLICABLE` is listed in `SUMMARY.md` as uncovered scope.
4. **The hook in `CheckedSavedDataPolicy.read` stays as W379 made it.** Root accepts it. Do not widen it.

## Native smokes (bounded)

Run these on a locally built JAR under the heavy-JVM lock, with the 90-second gap, and delete server copies.

- The journal case (`FS_O3J_AFTER`, `WRITE_ERROR`, 1) again, and the registry case with its skip. Report each class.
- One live smoke each of `DISK_FULL_OPEN`, `MOVE_DENIED`, `MOVE_AFTER`, `DELETE_ERROR` and `READ_CORRUPT`. Use the
  bay receipts, or another store where the native case reaches the fault.
- One offline smoke on the ledger or the journal (any kind other than `TRUNCATED`).
- At most ten cases in total. Do not run the full campaign: that is slice S5, on the candidate.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Production behaviour stays
  byte-identical without the property.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Run the unit tests of the injector and the new Python suite, plus
  the neighbouring runner suites W379 ran.
- A full GameTest run is not needed. The hosted regression of the integrated branch covers it.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only Codex review of the whole S4 slice follows.
- The last line of your answer is the path of `records/W379.md`.
