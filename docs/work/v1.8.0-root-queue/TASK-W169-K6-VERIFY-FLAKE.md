# TASK W169: the intermittent K6 import check in Repository governance

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w169` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w169` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W169.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W169/` |
| Failed logs | `D:/GitHub/ARCE-Task-Evidence/root-queue/ci-flakes/governance-3a8893a0-job-122185866189.log` and `governance-6fc6cd9f-job-122206261687.log` |

Repository governance failed twice on 2026-10-27 in the step "Verify the v1.8.0 K6 import batch byte for byte"
(`.github/workflows/`, ADR-061):

```text
python tools/import/import_v180_assets.py verify --upstream build/upstream/AdvancedRocketry \
  --plan tools/import/v180-k6-plan.json --record docs/provenance/v1.8.0-c18d-k6.json --vanilla ... --vanilla ...
[FAIL] provenance record does not reproduce byte for byte from the plan, upstream and targets
```

The two failing commits:
- `3a8893a0`: its rerun passed;
- `6fc6cd9f`: a documentation-only commit.

Other runs on the same source passed. So some input of `build()` or `v010.record_for()` is not deterministic: it
renders a different record from the same plan, upstream checkout, targets and vanilla jars.

## What to do

1. **Reproduce.** Run the same `verify` locally many times, varying the conditions:
   - `PYTHONHASHSEED`;
   - a fresh upstream clone and a fresh vanilla-jar download against cached ones;
   - Python 3.12, which CI uses, and the local Python.

   Record each run's result. The workflow's steps show how CI prepares the upstream checkout and the vanilla jars.
2. **Make a failure explain itself.** On a mismatch, print the first differing JSON path and both values, with a
   bounded size. Keep `[FAIL]` and the exit code.
3. **Fix the cause in the tool,** for example an unsorted iteration, a time value, history that depends on clone
   depth, or a race in parallel work. Add a regression test that fails before the fix.
   - The recorded provenance file, the plan and the imported targets must not change, unless you prove the committed
     record itself is wrong. Then stop and report.
4. **If the cause is in the workflow,** such as how it fetches the upstream or the jars, do not edit the workflow; it is
   Root's. Report the exact change.

## Verification

- The tool's Python tests.
- The verify run at least 20 times under the varied conditions above, all passing.
- `python -B scripts/validate_repository.py --require-approved-identity`.

No Gradle, GameTest or server is needed.

## Not in this task

The workflows, the K6 plan, the provenance record, imported assets, ADRs, status files and the ledger.
