# TASK W71: the section 15 release-input collector (W62 slice S8)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w71-release-collector` (from the shared branch) |
| Branch | `codex/v1.8.0-w71-release-collector` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W71.md` (new) |
| Specification | `records/W62.md` section 6 (the `docs/releases/v1.8.0/` layout) and section 7, row S8 |

## What to do

Write `scripts/collect_v180_release_inputs.py` and `tests/test_collect_v180_release_inputs.py`.

1. **Collect.** The script copies these files into a staging folder in W62 section 6's layout and
   writes a SHA-256 manifest. It does not write into `docs/releases/`; the final campaign does that.
   - the `build/reports/*.md` graph and coverage reports that the unit tests write, among them the
     recipe-graph and reachability reports, `machine-recipe-material-tags.md` and
     `registered-content-resources.md`;
   - `validate_v180_content_ledger.py --closure` as `content/ledger-summary.json`;
   - `validate_v090_resources.py --output` as `assets/resource-audit.json`;
   - the provenance index and JSON from `validate_v180_provenance.py`, when given a jar.
2. **Derive** two reports:
   - `content/porting-matrix-counts.md`: the ledger's dispositions per system or batch, with the
     PORTING_MATRIX row status beside each;
   - `content/progression-report.md`: a basic-to-endgame route through the witness routes that the
     recipe-graph tests compute. Name each stage and its machine, and mark any stage the graph cannot
     reach.
3. **Refuse to produce a quietly empty or partial package.** Exit non-zero and name what is missing
   when a required input does not exist.

   The ledger summary is the one exception. Its `--closure` run is expected to fail until the ledger
   closes, so record its exit code and output instead of failing.
4. **Unit tests** with fixture reports: a missing input, a hash mismatch, a ledger closure failure
   that is recorded and does not crash, and a deterministic manifest.
5. **One real run** after `gradlew test` in your worktree, with the staging output under
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W71/` (under 100 MB), and an inventory.

## Commands

- The new Python tests (`python -B -m unittest tests.test_collect_v180_release_inputs` from the
  repository root, with `TEMP` and `TMP` set to `C:/Users/Administrator/AppData/Local/Temp`).
- `gradlew test` once, to produce the reports for the real run.
- `git diff --check`.

## Not in this task

- Writing into `docs/releases/`, workflows, status files, the ledger and ADRs.
