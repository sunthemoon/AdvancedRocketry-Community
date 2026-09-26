# V120-MIG-03B2 handoff

Status: READY_FOR_REVIEW. No commits, runtime changes or central plan edits.

## Integration file list

Source worktree: `D:/GitHub/arce-v120-mig03b-rolling`. Paths are repository-relative.

Manual code/document files:

1. `scripts/run_v120_rolling_restart_smoke.py` — six-line metadata diff only;
   add `--tested-worktree`, nullable tested commit, base commit and dirty marker.
2. `scripts/v120_rolling_world_fixture.py` — new bounded extractor/verifier.
3. `tests/test_v120_rolling_world_fixture.py` — new 16-test suite.
4. `docs/work/v1.2.0-migration/ROLLING-WORLD-FIXTURE.md` — scoped report.
5. `docs/work/v1.2.0-migration/rolling-task/run_baseline.py` — task-local reproducible
   fresh-world helper using an existing Forge libraries tree; actual baseline,
   not synthesized metadata.
6. `docs/work/v1.2.0-migration/rolling-task/TASK.md` — worker record.
7. This `HANDOFF.md`.

Generated evidence files to retain byte-for-byte:

- `docs/work/v1.2.0-migration/fixtures/rolling/c.8.8.nbt.zlib`
- `docs/work/v1.2.0-migration/fixtures/rolling/manifest.json`
- `docs/work/v1.2.0-migration/rolling-restart/SHA256SUMS`
- `docs/work/v1.2.0-migration/rolling-restart/fixture-tests.txt`
- `docs/work/v1.2.0-migration/rolling-restart/baseline/summary.json`
- `docs/work/v1.2.0-migration/rolling-restart/baseline/first-start.txt`
- `docs/work/v1.2.0-migration/rolling-restart/baseline/restart.txt`
- `docs/work/v1.2.0-migration/rolling-restart/scenario/summary.json`
- `docs/work/v1.2.0-migration/rolling-restart/scenario/filtered-lifecycle.txt`
- All five `.txt` raw logs under `rolling-restart/full-logs/`, already enumerated by
  `SHA256SUMS`. Do not substitute logs from another run.

The generated `scenario/filtered-lifecycle.log` is redundant with `.txt`; it is
ignored by Git and not required for integration. Do not copy any `build/` runtime,
world, caches or root-shared file. The shared Precision reader is unchanged here.

## Verified commands/results

- Baseline helper: exit 0; actual first start and same-world restart, port 56062.
- Rolling S2 harness: exit 0; durable-save forced process exit 1, recovery and final
  restart exit 0. Progress 11/100 and 3780 FE preserved exactly; completion gives
  8 bars, 400 water, 2000 FE, revision 7 and one stable transaction marker.
- Fixture extraction + verification: exit 0; unchanged 6677-byte chunk, final
  region `e6f27d28197813de6ed5aaddb3f694310e6c1b532b3299fb3fd282578a97dc9d`.
- `python -B -m unittest tests.test_v120_rolling_world_fixture -v`: 16 passed.
- Combined new Rolling + old Precision suite: 22 passed.
- Five archived raw logs match summary hashes; actual baseline and scenario
  world/artifact identities match; 11 evidence files in `SHA256SUMS`.
- `git diff --check`: exit 0. No Gradle rebuild in this worker.

The candidate artifact is `6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a`.
No further use of the root build artifact is required. No server process remains.

## Limits and rollback

No historical Rolling region recovered, prior-version Rolling migration, arbitrary
crash cut, long load, multiplayer or GPU claim. All Required Gates remain open.
No production or save schema changed. Rollback removes the new helper/tests/fixture
and evidence, and reverts only the scoped harness metadata addition. Preserve the
older historical evidence and other workers' files.
