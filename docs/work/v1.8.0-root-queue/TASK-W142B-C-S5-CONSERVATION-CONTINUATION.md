# TASK W142B: continue C-S5 on W142's branch, starting with the rebuild-source conservation check

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W142](TASK-W142-CARGO-C-S4-C-S5-NATIVE-CUTS.md) and
the "C-S5" part of W142's record (`docs/work/v1.8.0-root-queue/records/W142.md` on the branch) first. You are a new
session continuing W142 after its session ended on a provider error; Root decisions R65 to R74 in QUEUE apply.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w142`, branch `codex/v1.8.0-w142` at `2087f6e0`; continue on that branch |
| Record | append a "W142B" section to `records/W142.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W142/` (keep earlier files unchanged) |

State: 29 cuts passed (all 20 of C-S4, and C-S5's N1, F1, F6, F8 and the three disassembly cuts). `FF_REBUILD_SOURCE`
has not passed. In its last run the fixture gave the source copy a new snapshot ID; afterwards the fluid total across
both rockets and the device read 10,000 mB instead of the 5,000 mB the cut started with. `2087f6e0` changes the
fixture to keep the source identity from the transfer record. Whether ordinary play can reach that state is open
(finding F4 in QUEUE); production cargo admission is off.

## What to do

1. **Conservation check for `FF_REBUILD_SOURCE`** on `2087f6e0`: run the cut with the preserved source identity through
   the production unload and the production REBUILD_SOURCE recovery, then two restarts. Record, for every rocket and
   device, its native fluid amount, and which production actions (launch, refuel, unload, disassemble, settle) each
   rocket accepts.
   - If the total stays at 5,000 mB and only one rocket accepts actions, the cut passes: continue with step 2.
   - If the total exceeds 5,000 mB, stop: write the exact steps, the code path that accepts the recovery and the
     protection it bypasses into the record, run nothing further, and deliver. Do not change production code or the
     oracle.
2. The remaining C-S5 cuts (REBUILD_DESTINATION, KEEP_SOURCE, stale PREPARED with both physical entities, disembarked
   reconnect, the two governed-record cases, the successor mutation between two flights, exact-source cancellation),
   under the same stop rule.

## Verification

Each changed test class alone, the whole unit suite, the runner's Python tests and one full GameTest with the unchanged
log checker on the final committed source; the cuts under the heavy-JVM lock with the atomic pattern and the hand-off
rule in WORKER-RULES, releasing it and deleting every server copy and `build/gametest/world` in the `finally` of the same
detached script that took it. Keep the evidence under 100 MB.

## Not in this task

Production changes, D-S (W143), status files and the ledger (Root).
