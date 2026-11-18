# TASK W380: X03 slice X03-B, the native repair family and its oracle

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w380`, branch `codex/v1.8.0-w380`, at the shared head (X03-A and X03-V merged) |
| Decision | Root decision R485 (QUEUE row W380) |
| Design | [W367-DESIGN](records/W367-DESIGN.md) revision 2, sections 3, 5 and 6 row X03-B; Root's answers in QUEUE rows W367, W367F and W376 (R463, R473, R474) |
| Record | `docs/work/v1.8.0-root-queue/records/W380.md` |
| Write scope | `scripts/run_v180_x03_repair.py` (completed from X03-V's skeleton), its tests, the independent oracle the design names, a new campaign family in `scripts/run_v180_integrated_campaign.py`, the probe inventory if its counts change, and the record. No production code. The C-S5 assertions stay intact. |

## What to do

1. **The runner.** Implement section 5:
   - the fenced seed from `FF_REBUILD_SOURCE`;
   - the qualifying start;
   - the hash-verified backup;
   - `plan` and `apply` of the repair tool (`scripts/repair_v180_rebuild_source.py`) on a copy;
   - two verification cycles with X03-V's cases (`FF_X03_OBSERVE`, `FF_X03_UNLOAD`, `FF_X03_LAUNCH`);
   - the whole-world replacement.
2. **The independent oracle.** It checks:
   - conservation (5,000, 4,000 and 1,000 mB);
   - no duplicated rocket or tank;
   - the FLOOR_CONFLICT cleared and asserted by the family itself;
   - a real ordinary unload and launch afterwards;
   - every file not on the reasoned list unchanged.
   After the launch it reads the landed rocket from the files, as W376 noted.
3. **The lists.** Derive the runtime file list from two dry runs, with a reason for each entry, and give it in the record
   for Root's review.
4. **The refusal cases, at least three,** including:
   - the FF_KEEP_SOURCE world, refused by the geometric predicate (R474);
   - a world whose root is not current;
   - an uninterpreted authority row.
5. **Both negative controls:**
   - the rule-chosen tamper target, refusing with `NO_TAMPER_TARGET` if none exists;
   - the semantic comparison of the rocket and the unloader.
6. **A campaign family.** Add one that runs the whole case with the candidate pins, as the other families do. Update
   the campaign's family count and its tests.
7. **Native runs.** Run on a locally built JAR under the heavy-JVM lock, with the full sequence. The ledger is root 2
   until activation; the root-3 case is X03-C after activation. Keep evidence under
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W380/` and delete server copies after each run.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change an existing oracle (AGENTS.md section 5).
- Run the Python tests you touched. Dispatching governance is Root's job; run the full Python suites of the runners you
  change.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives the sequence, the oracle, the reasoned file list, each refusal and control result, the native
  results, the commands and the evidence paths. Its last line, and the last line of your answer, is the path of
  `records/W380.md`.
