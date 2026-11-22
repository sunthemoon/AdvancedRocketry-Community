# TASK W447: K02 follow-up, C6's journalled launch and the sealed disassembly cuts

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w447`, branch `codex/v1.8.0-w447` from the shared branch |
| Decision | Root decision R631 (QUEUE row W447), following [W437](records/W437.md) |
| Sources | [W437](records/W437.md) (flight 2 PASS and 8 FAIL on `code=JOURNAL_PENDING`; transaction 13 PASS and 12 `NOT_REACHABLE_ON_C6`; the source path of the retained disassembly); [W431](records/W431.md) (K02's sources); the runners [flight](../../../scripts/run_v100_flight_forced_stop.py) and [transaction](../../../scripts/run_v100_transaction_forced_stop.py) with W437's opt-in receipt mode; the sealed native runner [run_w152_native.py](../../../scripts/run_w152_native.py) and `scripts/w152_native.init.gradle`; C6's launch and journal code (`RocketFlightService`, `RocketOrdinaryLaunch`, `RocketFlightRequestCode`, `RocketTransactionSavedData`) and the sealed disassembly code (`SealedDisassemblyCoordinator`, `SealedDiagnostics`) under `src/main/` (read only) |
| Inputs | K02's sealed sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` (read only; fresh copies only) and W437's evidence under `.../fixtures/K02/R612-W437-restart-20261122/` (read only) |
| Outputs | Tracked: the runner changes with tests, and the record `docs/work/v1.8.0-root-queue/records/W447.md`. Outside Git: new runs under `.../fixtures/K02/R631-W447/` |
| Lock | Native runs and the diagnostic build take the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, no hold over two hours; detached launchers start with no console window. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to K02's sources. Changing the default behaviour of either runner, loosening any existing check, or counting `JOURNAL_PENDING` alone as a launch. Building the diagnostic JAR from anything but C6's commit with the existing init script. |

## What to do

1. **Journalled launch.** Read C6's code for what follows `code=JOURNAL_PENDING` on an accepted launch (the journal
   commit and the line or readback that confirms the flight started). Add an opt-in mode to the flight runner, in
   the style of W437's `--sealed-disassembly-receipt`, that accepts `JOURNAL_PENDING` only when that confirmation
   for the same rocket follows within the existing launch budget, and otherwise fails as before. Test it with
   line-backed fakes (wrong rocket, missing confirmation, refusal after pending, unchanged default).
2. **Rerun the flight matrix** (all ten cases, fresh baselines, `--wait-ready-status`, both opt-in modes) and report
   each case. A case that still fails stays failed with its reason.
3. **Sealed disassembly cuts.** The C6 campaign has no family that force-stops a sealed disassembly, so W437's 12
   unreachable legacy checkpoints have no C6 counterpart yet. Build the diagnostic JAR from C6's commit with
   `scripts/w152_native.init.gradle` (record its hash and that production sources equal C6), then run
   `run_w152_native.py`'s C0 to C5 cuts, its recovery cuts and two stable restarts on fresh copies, and report each
   cut. Map W437's 12 legacy rows to these cuts in the record; a legacy row with no matching cut stays
   `NOT_REACHABLE_ON_C6` with that reason.
4. **Record** the tables, commands, hashes and anything left open, and run
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
