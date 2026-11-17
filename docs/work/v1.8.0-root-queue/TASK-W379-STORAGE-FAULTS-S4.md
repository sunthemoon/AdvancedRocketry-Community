# TASK W379: X01 and X02 simulation, slice S4 (the fault injector and the X02 runner)

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w379`, branch `codex/v1.8.0-w379`, at W369's fixed head `0d8210e50` (slice S1 with its review fixes, under re-review) |
| Decision | Root decision R482 (QUEUE row W379) |
| Design | [W368-DESIGN](records/W368-DESIGN.md), sections 4, 5, 6 and 7.1 row S4; Root's answers in QUEUE row W368 (R461) |
| Record | `docs/work/v1.8.0-root-queue/records/W379.md` |
| Write scope | New `persistence/migration/ReleaseTestStorageFault.java`, its installer hook beside the recorder's, the `Reader` wrapper the design names, new `scripts/run_v180_storage_faults.py` and `tests/test_run_v180_storage_faults.py`, an exact entry in `scripts/gametest_expected_log.json` only if you add a GameTest, unit tests under `src/test/java/.../persistence/migration/`, and the record. |

## What to do

1. **The injector.** Implement section 4.
   - Inject each fault at the `CheckedFileOps` seam: an I/O error on write, a write that reports success but is lost,
     a failed flush, a failed move, a failed close, a full disk, and a corrupted read.
   - It is controlled by `advancedrocketrycommunity.storageFault=<store>:<fault>:<count>`.
   - Gate it exactly as section 6 does for the recorder, and restore `DIRECT` at server stop.
   - `DIRECT` and the recorder must not change.
2. **The X02 runner.** For the four stores (the ledger, bay receipts, the transfer journal and the satellite registry),
   run each fault against a native case with the existing runners, then restart twice with storage healthy.
   - Classify each outcome as seen and held, caught later, or not caught, from the logs and the existing oracles.
   - Keep each classification's evidence.
   - Never relax an oracle; an outcome of the third kind is recorded as a residual.
3. **Tests.**
   - Unit tests for each fault's injection and gating, and for the restore.
   - Unit tests for the runner's classification on synthetic results.
   - Every existing test runs unchanged.
4. **One native smoke per fault kind** on a locally built JAR under the heavy-JVM lock, to show that the injection
   happens where the design says. The full X02 run is slice S5, on the candidate.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Production behaviour must be
  byte-identical without the property.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Heavy runs go under the lock, and server copies are deleted after
  each run.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by another engine follows before the merge.
- The record gives what was done, the commits, the commands and results, and the smoke outcomes per fault. Its last
  line, and the last line of your answer, is the path of `records/W379.md`.
