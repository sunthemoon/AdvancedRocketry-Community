# TASK W466: map C6's named hosted GameTest rows to the per-shard results

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w466`, branch `codex/v1.8.0-w466` from the shared branch |
| Decision | Root decision R670 (QUEUE row W466), following [W455](records/W455.md) (R651) |
| Sources | [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) and [W455](records/W455.md): 89 named hosted GameTest rows are NOT_COVERED because the build-evidence ZIP W455 read has no per-method shard results. [scripts/ci_gametest_shards.py](../../../scripts/ci_gametest_shards.py), `scripts/ci_gametest/` (the observer that writes each shard's `result.json`) and the v1.8 workflow under `.github/workflows/` (read only), for what `executed`, `failed` and `complete` mean |
| Inputs (read only) | Root downloaded the four shard artifacts and the regression artifact of C6's hosted run 44770334930 to `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/hosted-shards-44770334930/` (`v180-shard-0` to `-3` and `v180-regression`, each `...-c8fa16e16be87554f26c402546733df6555d15e6-1.zip`). Each shard ZIP holds `result.json` (schema, shard, inventory, bundle_sha256, executed, failed, complete), `tested-commit.txt`, `gametest.log`, `inventory.json` and logs. Open the ZIPs read only, with `python -I`, and keep any extraction under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/hosted-shards-44770334930/extracted/` |
| Outputs | Tracked: the rows' coverage cells in `C6-SWITCH-REPORT.md`, and the record `docs/work/v1.8.0-root-queue/records/W466.md`. Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/hosted-shards-44770334930/SHA256SUMS.txt` for the five ZIPs and any extraction |
| Lock | No server runs and no lock. Run the validator only. |
| Not allowed | Changing any other row, figure, verdict or decision; marking a Gate, version or switch passed; counting a test as passed unless the observer's own semantics say so; changing the workflow, scripts or tests. |

## What to do

1. **Semantics and identity.** From the observer and workflow code, state exactly what `executed`, `failed` and
   `complete` mean (for example, whether `executed` lists only tests that passed), and confirm for each shard that
   `tested-commit.txt` is C6's commit, `complete` is true, `failed` is empty, and the four `bundle_sha256` values match
   the run's single runtime bundle. Record the five ZIPs' SHA-256 values.
2. **Map** each of the 89 named rows to its GameTest method: the shard, batch and name in `executed`, and the line in
   that shard's `gametest.log` that shows its result, if the log has one. A row whose method is absent, ambiguous or
   failed stays NOT_COVERED with the reason. Do not infer a pass from the aggregate count.
3. **Update** each mapped row's cell in `C6-SWITCH-REPORT.md` with the evidence (run, shard, batch, method, ZIP hash)
   and its new status as the report's own vocabulary allows, keeping the old text's reason as history where the report
   keeps history. Note any other row in the report that this evidence also covers, without changing it.
4. **Record** the semantics, the identity checks, the mapping table and anything left open, and run
   `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Complete sentences; no pass beyond what the evidence shows (AGENTS.md sections 5 and 10).
- Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
