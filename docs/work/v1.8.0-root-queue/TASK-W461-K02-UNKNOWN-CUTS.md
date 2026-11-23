# TASK W461: rerun K02's eight UNKNOWN sealed cuts on C6 with the exact UNKNOWN log expectation

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w461`, branch `codex/v1.8.0-w461` from the shared branch |
| Decision | Root decision R662 (QUEUE row W461), following [W447](records/W447.md) |
| Sources | [W447](records/W447.md), section "Sealed C0-C5 and recovery results": rows 012, 013, 044, 045, 066, 067, 084 and 088 fail only the native runner's rejection of every ERROR line, on the intended `ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN` line; [W198](records/W198.md), section "Failure and tranche accounting", for the narrowly scoped UNKNOWN expectation that commit `2422da0f7` added to [run_w198_terrain.py](../../../scripts/run_w198_terrain.py); [W129a](records/W129a.md), known risk "The `_UNKNOWN` W152 cuts"; [run_w152_native.py](../../../scripts/run_w152_native.py), `scripts/w198_terrain.py`, `tests/test_w198_terrain.py` and `tests/test_w152_native_reader.py`; C6's code under `src/main/` (read only) |
| Inputs (read only) | W447's evidence `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R631-W447/`: `native/plan.json`, the per-case arguments, `run.py` and `resume_native.py` (the Forge server template and Java path), and the pinned diagnostic JAR `artifacts/advancedrocketry-community-1.20.1-1.8.0-dev-w152-native.jar` (SHA-256 `59978207226863290e38d199ca6222e86960ad2def6073a3c44954fb59f8ed77`, see `diagnostic-artifact.json`) |
| Outputs | Tracked: any runner change with its tests, and the record `docs/work/v1.8.0-root-queue/records/W461.md`. Outside Git: new runs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W461/` |
| Lock | Native runs take the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, no hold over two hours; detached launchers start with no console window. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600), to K02's sources or to W447's evidence. Changing a runner's default behaviour or loosening any existing check. Accepting any ERROR line other than exactly the one expected UNKNOWN line on an explicitly selected UNKNOWN cut. Using a JAR other than W447's pinned artifact (verify its hash first) unless that artifact lacks a class the chosen path needs; then build from C6's commit with the existing init script and record why. Relabelling W447's eight results: they stay failed attempts, and the new runs are separate rows. |

## What to do

1. **Choose the oracle.** Compare `run_w198_terrain.py` (its fixture, row plan and checks) with `run_w152_native.py`
   as W447 ran it. If the W198 runner drives the same fixture and cut plan and the pinned C6 JAR has what it needs,
   use it unchanged for the eight rows. Otherwise add an opt-in flag to `run_w152_native.py`, default off, that
   applies W198's expectation exactly: exactly one fully matched
   `ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN sequence=N acknowledged=...` ERROR and exactly one
   matching `C1_WRITE_UNKNOWN`, `C3_WRITE_UNKNOWN` or `C5_WRITE_UNKNOWN` observation, and only when the selected
   initial or recovery cut is that UNKNOWN cut. Any other ERROR, any FATAL, a missing or repeated line, trailing
   text, or the same line on another cut still fails. Test the flag with Linux-safe line-backed fakes, including
   the unchanged default.
2. **Rerun** rows 012, 013, 044, 045, 066, 067, 084 and 088 on fresh copies, with recovery and both stable
   restarts, and report each row with the same columns as W447. A row that still fails stays failed with its
   reason, and missing later stages are not invented.
3. **Control.** Rerun rows 010 and 068 through the same path with the flag on, and confirm that they still pass.
4. **Record** the choice and its reason, the tables, commands and hashes, and anything left open, and run
   `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
