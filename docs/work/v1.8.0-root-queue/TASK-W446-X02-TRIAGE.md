# TASK W446: triage C6's X02 storage-fault results and rerun the harness cases

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w446`, branch `codex/v1.8.0-w446` from the shared branch |
| Decision | Root decision R629 (QUEUE row W446; the C6 run row records X02 attempt 3) |
| Sources | [W379](records/W379.md) (the classification rules: `SEEN_HELD`, `CAUGHT_LATER`, `NOT_CAUGHT`), [W368-DESIGN](records/W368-DESIGN.md), [C5-SWITCH-REPORT](records/C5-SWITCH-REPORT.md) section 13 and [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) (the X02 rows and the residuals R-035 names), `scripts/run_v180_storage_faults.py` and the runners and injector it calls; C6's code under `src/main/` (read only) |
| Inputs | C6's X02 S5 attempt 3, run 2026-11-22 20:24 to 2026-11-23 01:36: `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/x02-s5-attempt3/` (`MANIFEST.json`, `results.jsonl`, `SUMMARY.md`, `cases/`; read only). Attempt 2 (`x02-s5-attempt2/`) was cut by a power-off and counts for nothing |
| Outputs | Tracked: fixes under `scripts/` with tests under `tests/` where a cause is the harness, and the record `docs/work/v1.8.0-root-queue/records/W446.md`. Outside Git: the rerun under `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/x02-s5-triage/` |
| Lock | Native runs take the heavy-JVM lock as the runner already does (per case, at least 90 seconds between holds); detached launchers start with no console window (WORKER-RULES). Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to the attempt-3 evidence. Loosening a classification rule, an oracle, an assertion or a timeout to turn a case into `SEEN_HELD`. Editing a case's verdict by hand. |

## Attempt 3 in brief

72 cases: 44 `SEEN_HELD`, 10 `CAUGHT_LATER`, 10 `NOT_CAUGHT`, 4 `INCOMPLETE`, 4 `NOT_APPLICABLE`; report status
`INCOMPLETE` because of the four.

- `INCOMPLETE` (4): `RESOURCE_ESCROW-ZEROED_TAIL`, `RESOURCE_ESCROW-TRUNCATED`, `ROCKET_TRANSFERS-ZEROED_TAIL` and
  `ROCKET_TRANSFERS-TRUNCATED`, each `The damaged copy was not started twice: EOFError: Compressed file ended before
  the end-of-stream marker was reached` (the same faults on the satellite store and bay receipts completed).
- `NOT_CAUGHT` (10): `RESOURCE_ESCROW` `MOVE_ERROR-1`, `MOVE_DENIED-1`, `READ_ERROR-1` and `READ_CORRUPT-1` on
  `FC_LOAD_L1_REPLACED` (`Server exited with code 0 before 'ARCE_RELEASE_CARGO_PAUSED ...'`); `RESOURCE_ESCROW-STALE`
  on `FC_LOAD_L2_REPLACED` (8 undeclared ERROR or FATAL lines and the oracle failed); `BAY_RECEIPTS-ZEROED_TAIL` and
  `BIT_FLIP` on `B3_MOVED` (12 undeclared ERROR or FATAL lines and the existing runner failed);
  `ROCKET_TRANSFERS-WRITE_ERROR-4` on `FS_O3J_AFTER` (timed out waiting for the cut marker); and
  `SATELLITE_MISSIONS` `READ_ERROR-1` and `READ_CORRUPT-1` on `R3` (the write after faulted write 2 missing or
  different, nothing reported; recorded by the report as a residual).

## What to do

1. **Classify the cause of each of the 14 cases** from its logs, the runner and C6's code, as one of: harness (the
   injector, the damaged-copy step, the runner or its oracle is wrong for this fault), product (C6 mishandles the
   fault: data lost or changed without a refusal, a crash, a refusal that is not declared, a store that cannot be
   recovered by the supported route), or residual (a fault W368 and R-035 already declare unobservable). Quote the
   lines that decide it. Do not guess: if the evidence cannot decide, say what run would.
2. **Fix the harness causes** (for example the `EOFError` in preparing a damaged compressed copy) in the scripts,
   with tests, without weakening any rule in step 1's sense.
3. **Rerun** with the runner's `--case` option, on the C6 JAR and the same template and pins as attempt 3, every
   case whose cause was the harness, into `x02-s5-triage/`, and report each new class. For a product cause, write a
   native reproduction (the case's command line and the decisive lines) for Root; do not work around it.
4. **Record** a table of the 14 cases (cause, evidence, fix commit, rerun class) and the attempt-3 totals with the
   rerun applied, and run `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
