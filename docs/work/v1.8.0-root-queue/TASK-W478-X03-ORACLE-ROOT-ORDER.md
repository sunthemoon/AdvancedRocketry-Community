# TASK W478: make the X03 oracle read its managed roots in a fixed order

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w478`, branch `codex/v1.8.0-w478` from the shared branch |
| Decision | Root decision R697 (QUEUE row W478), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Write scope | `scripts/x03_repair_oracle.py`, `tests/test_x03_repair_oracle.py` (new tests only) and the record `docs/work/v1.8.0-root-queue/records/W478.md` |
| Machine | W477's night campaign holds this machine from 23:30 to 07:45. Start no JVM, Gradle task or Minecraft process, do not take the heavy-JVM lock, and run only the targeted tests below, not the full Python suite |

## What failed

Hosted Repository governance run 45641260247 on `bb29d683b` (a documents-only push) failed one test, which had
passed on the previous head:

```text
FAIL: test_measurement_reports_both_generation_failures_and_later_roots
  (test_x03_repair_oracle.BookkeepingClockTest)
AssertionError: "WRITER_GENERATION" does not match
  "OBJECT_CHANGED:data/advancedrocketrycommunity_satellite_missions.dat:/data/save_epoch"
```

Root's reading of the cause: `read()` in `scripts/x03_repair_oracle.py` builds `managed` from
`world.rglob('advancedrocketrycommunity_*.dat')` in directory order (line 263). The other two listings in the file
(lines 97 and 120) are already sorted. `compare_functional()` then visits `before['managed']` in that order
(line 482), and in strict mode it raises at the first failed check. On a filesystem that lists the satellite missions
file before the resource escrow ledger, the object change is raised before the writer-generation check, so the first
error depends on the runner's directory order.

## What to do

1. Confirm or correct Root's reading. Make every listing in `scripts/x03_repair_oracle.py` whose order decides a
   strict-mode first error or the order of recorded findings deterministic and independent of the filesystem (for
   example `sorted(...)`). Change nothing else in the oracle's checks, messages or allowances.
2. Add Linux-safe tests that show the order no longer depends on the listing. For example, patch the listing to
   return the files in reverse order and check that the strict error is still `WRITER_GENERATION` and that the
   managed roots come back in sorted order. Do not change, loosen or delete any existing test or assertion.
3. Check `scripts/run_v180_x03_repair.py` for the same kind of order dependence and list what you find in the
   record. Fix it only if it is in the write scope; otherwise describe it for Root.
4. Run:
   - `python -B -m unittest tests.test_x03_repair_oracle -v`
   - any other test module that imports `x03_repair_oracle` or `run_v180_x03_repair`
   - `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`
5. Record: the cause as you found it, the change, the new tests, the commands and results, and any order dependence
   left outside the write scope. Sealed X03 evidence stays as recorded; this change does not alter any recorded
   result.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- No change under `src/` (frozen at C6, R600).
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
