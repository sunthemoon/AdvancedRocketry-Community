# TASK W147: finish the cargo benchmarks C-B and D-B on the combined tree (W144, W145 and W146 merged)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W144](TASK-W144-CARGO-C-B-D-B-BENCHMARKS.md),
W144's record (`records/W144.md`, with Root decisions R81 to R85 in QUEUE), and the records of W145 (the launch queue)
and W146 (cargo visual republication) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w147` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w147` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W147.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W147/` |

W144's harness is merged. Its native reference run stopped twice for causes now fixed: every governed flight was
discarded before ARMED capture (W145) and no updated-hash visual packet arrived (W146). W146's own reference run still
stopped as `STOP_MISSING_MEASUREMENT` because two fluid-unloader cycles did not retire; W146 predates W145.

## What to do

1. Run W144's native reference load (`scripts/run_v180_cargo_native_benchmark.py`) on your committed source: the eight
   flights, the device cycles, the mixed queue and the visual republication, with docs/17's memory and MSPT figures.
2. If fluid-unloader cycles still do not retire, find exactly why (which stage each stops at, the operation and ledger
   state, the device status) and stop and report the evidence before changing anything; a fixture or harness cause may
   be fixed with a test, a production cause goes to Root.
3. The restart measurement W144 did not reach: stop the server after the reference load and restart it twice, measuring
   recovery time and that every record, device and rocket matches.
4. Complete W144's verdict: every stated budget met, or the exact figure that misses (the stop rule applies; no budget,
   fixture size or cadence change).

## Verification

The harness's unit and Python tests; the benchmarks on committed source under the heavy-JVM lock with the atomic pattern
and the hand-off rule, releasing it in the `finally` of the same detached script that took it and deleting every server
copy. Report hardware, JVM flags, collector and fixture composition. Check C: and D: above 10 GiB and 6 GiB free memory
first. Keep the evidence under 100,000,000 bytes.

## Not in this task

The joint docs/17 soak, V1 and V2 client evidence, enabling admission, production changes without Root's decision,
status files and the ledger (Root).
