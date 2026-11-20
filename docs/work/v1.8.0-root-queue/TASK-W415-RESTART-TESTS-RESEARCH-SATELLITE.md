# TASK W415: restart GameTests for the research, satellite-builder, microwave-receiver and assembler devices

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w415`, branch `codex/v1.8.0-w415` from the shared branch |
| Decision | Root decision R564 (QUEUE row W415) |
| Sources | [MACHINE-CENSUS](../../releases/v1.8.0/MACHINE-CENSUS.md) findings F2, F4, F6 and F7 (each names the existing tests and what they do not cover); the version document's section 9 (each machine needs at least a happy, a failure and a restart test); the block entities, their saved forms and the existing GameTests the census cites |
| Record | `docs/work/v1.8.0-root-queue/records/W415.md` |
| Write scope | New GameTest methods (in the existing GameTest classes the census names, or a new class beside them in the same package) and any test-only fixture helpers they need; the record. No change to product behaviour, saved formats or existing tests. |
| Lock | Take the heavy-JVM lock (WORKER-RULES) for each GameTest server run. Candidate C5's native campaign also takes it between its families and waits for you; keep each run to the batches you add (`-Dforge.gameTestBatches` or the project's filter) and release the lock as soon as it ends. Never hold it while you edit. |

## What to add

For each device, a restart test in the current saved format that saves the block entity (and the domain state it
owns) to NBT or through a real chunk unload, rebuilds a fresh block entity from that data, and then checks that the
work continues exactly once:

1. **F2, `observatory` and `astrobody_data_processor`**: a paid survey and a paid analysis in progress, with the
   exact energy, progress, research data and credit before and after; the next step completes once and pays once.
2. **F6, `satellite_builder`**: a current-format inventory, energy and pending result restored into a fresh block
   entity, then one successful build with the exact energy, inputs consumed and one output, no duplicate.
3. **F7, `microwave_receiver`**: a current receiver identity, chip link and energy buffer restored, then bounded
   reception that delivers exactly once.
4. **F4, `rocket_assembler`**: its schema-1 report saved and restored into a fresh block entity (code, detail, time,
   statistics), and a future-root report retained unchanged, as the existing future-root tests do elsewhere.

Each test states in its name and a short comment which finding it closes. Keep the existing tests; where a test
injects a future or malformed root, it stays.

## Checks

- Run the new batches under the lock and record each run's command, duration, batch list and result. Then run the
  unit suite for the touched packages and `python -B scripts/validate_repository.py`, which must end with `0 failed`.
- A failing new test that shows a product defect is a finding: stop, record it with the evidence, and do not change
  product code in this task.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
