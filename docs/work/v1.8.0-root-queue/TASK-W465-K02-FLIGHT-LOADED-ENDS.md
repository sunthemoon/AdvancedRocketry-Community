# TASK W465: K02 flight matrix with the rocket's chunk kept loaded at both ends

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w465`, branch `codex/v1.8.0-w465` from the shared branch |
| Decision | Root decision R666 (QUEUE row W465), following [W464](records/W464.md), [W463](records/W463.md) and [W462](records/W462.md) |
| Sources | [W464](records/W464.md): C6 rejects the unretained sealed disassembly when the rocket's destination chunk unloads after the transfer retires and releases its flight tickets, which happens on an empty server but not with a requesting player within eight blocks; the rocket stays intact, and with only the Moon landing chunk forced the request passes every check. [W463](records/W463.md) for the counted matrix and its launcher (the baseline already persists a forced source-origin chunk), and the runner [run_v100_flight_forced_stop.py](../../../scripts/run_v100_flight_forced_stop.py) with its opt-in modes |
| Inputs (read only) | W463's and W464's evidence under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R663-W463/` and `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R665-W464/`; K02's sealed sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` (fresh copies only) |
| Outputs | Tracked: the record `docs/work/v1.8.0-root-queue/records/W465.md`, and a runner change with tests only if the contract needs one, opt-in and default off. Outside Git: new runs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R666-W465/` |
| Lock | Server runs take the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, including across a launcher restart, no hold over two hours; detached launchers start with no console window. Give the lock token this task's own name. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to K02's sources or earlier evidence. Changing the runner's default behaviour or loosening any existing check. Raising the 30-second pause budget or the 45-second disassembly budget. Any chunk-loading command other than the one contract below, and any command sent after a failure to rescue a case. Changing W447's or W463's counted results. |

## The contract (Root decision R666)

A player who asks for a disassembly must stay within eight blocks of the rocket, so the chunk under the rocket stays
loaded while the request runs. On this unmanned server, the stand-in is one forced chunk under the rocket at each
end, set up in the uncounted baseline before any case starts: the existing persisted source-origin chunk and, new,
the Moon chunk that contains the landing site. Nothing else is loaded by command, and nothing is added during a case.

## What to do

1. Add the Moon landing chunk to the baseline as the contract says, and record the exact command and the readback that
   shows both forced chunks before the first case. If the runner needs a change for this, make it an opt-in flag,
   default off, with Linux-safe tests.
2. Rerun all ten flight cases on fresh copies of that baseline with `--wait-ready-status`,
   `--sealed-disassembly-receipt`, `--journalled-launch-receipt` and `--settled-disassembly-protocol`, and report each
   case with W463's columns. The three NOT_REACHABLE_ON_C6 checkpoints are expected to stay FAIL; report what they
   actually do. A case that fails stays failed with its reason.
3. **Record** the contract, the tables, the time each disassembly took, the commands and hashes, and anything left
   open, and run `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
