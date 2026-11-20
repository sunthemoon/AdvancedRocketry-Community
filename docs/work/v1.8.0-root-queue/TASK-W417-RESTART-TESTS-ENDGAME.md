# TASK W417: restart GameTests for the orbital laser drill, the laser target and the black hole generator

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w417`, branch `codex/v1.8.0-w417` from the shared branch |
| Decision | Root decision R568 (QUEUE row W417), following R564 |
| Sources | [MACHINE-CENSUS](../../releases/v1.8.0/MACHINE-CENSUS.md) findings F9, F10 and F11 (each names the existing tests and what they do not cover); the version document's section 9; the endgame block entities, their services and saved forms; the existing GameTests the census cites; W416's helper `RestartTestSupport` is on branch `codex/v1.8.0-w416` and not yet merged, so do not depend on it |
| Record | `docs/work/v1.8.0-root-queue/records/W417.md` |
| Write scope | New GameTest classes or methods in the endgame GameTest package and test-only helpers of your own; the record. No change to product behaviour, saved formats or existing tests. |
| Lock | The heavy-JVM lock is held by candidate C5's native pre-run until about 21:00 and then freed. Compile and run unit tests without it. If the lock is free (no `owner.txt`, or one that is yours), take it per WORKER-RULES and run only your new batches; if it is held, do not wait more than ten minutes: record the batches as not yet run, with their names, for Root to run. |

## What to add

1. **F9, `orbital_laser_drill`**: restore a real current-format job (logical mode, and physical mode where the
   default-off admission allows it under the test property the existing tests use) with its persisted service and
   endpoint state, and prove that the next logical draw or physical layer is neither repeated nor lost: exact seed,
   index, energy, output and link.
2. **F10, `laser_target`**: restore an active target's block entity together with its live endpoint record, with the
   exact layer and buffer; keep the existing retired-copy refusal test.
3. **F11, `black_hole_generator`**: a mid-burn restart in the current format with the exact fuel, remaining ticks, burn
   rate and energy, then continuation; and a full-buffer continuation case.

Each test states in its name and a short comment which finding it closes, and uses a batch name starting with
`w417_`. Keep the existing tests.

## Checks

- Compile, run the unit suite for the touched packages and `python -B scripts/validate_repository.py` (it must end with
  `0 failed`); run the new GameTest batches if the lock is free as described above, and record each run.
- A failing new test that shows a product defect is a finding: stop, record it with the evidence, and do not change
  product code in this task.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
