# TASK W463: K02 flight matrix with the settled and prepared disassembly protocol

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w463`, branch `codex/v1.8.0-w463` from the shared branch |
| Decision | Root decision R663 (QUEUE row W463), following [W462](records/W462.md) and [W447](records/W447.md) |
| Sources | [W462](records/W462.md), sections "Recovery cleanup investigation" and "Player impact and proposed follow-up" (DESCENT: the disassembly command is refused with `REGION_BUSY` before the transfer settles, and a refused command is not queued; LANDED: the first command at a cold departure site only prepares, and a fresh authorized command is needed once preparation is ready); [W447](records/W447.md), section "Flight results"; the runner [run_v100_flight_forced_stop.py](../../../scripts/run_v100_flight_forced_stop.py) with its opt-in modes and `scripts/run_v060_flight_server_smoke.py`; C6's code under `src/main/` (read only), in particular `RocketDisassemblyService`, `RocketI6Entry`, `RocketI6Retirement`, `RocketOrdinaryRetirement`, `RocketDestinationCompletion` and `RocketTransferService` |
| Inputs (read only) | W447's and W462's flight evidence under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R631-W447/flight/` and `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W462/`; K02's sealed sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` (fresh copies only) |
| Outputs | Tracked: the runner change with its tests, and the record `docs/work/v1.8.0-root-queue/records/W463.md`. Outside Git: new runs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R663-W463/` |
| Lock | Server runs take the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, including across a launcher restart, no hold over two hours; detached launchers start with no console window. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to K02's sources or earlier evidence. Changing the runner's default behaviour or loosening any existing check. Raising the 30-second pause budget or the 45-second disassembly budget. Loading chunks with commands a player does not have (`forceload` and the like) in a counted case. Counting the three NOT_REACHABLE_ON_C6 checkpoints as anything else. |

## What to do

1. **Protocol mode.** Add an opt-in mode to the flight runner, default off, that disassembles the way a player can:
   wait for the same transfer's settled or retained event (match its exact line and transfer identity) before the
   first disassembly command; if the answer is the preparation-only pending answer, issue a fresh authorized
   command with the exact reported fuel consent once preparation is ready (or at a fixed interval, each a new
   request, if C6 prints no readiness line); then require the identified SEALED and DONE receipts, entity absence,
   every restored block and the exact inventory, as now. The whole sequence stays within the existing 45-second
   budget counted from the first disassembly command. Any refusal other than the two named answers still fails.
   Test the mode with Linux-safe line-backed fakes, including the unchanged default, a refusal that never clears,
   a preparation that never becomes ready, and a second SEALED receipt.
2. **Rerun the flight matrix**, all ten cases on fresh baselines with `--wait-ready-status`, both earlier opt-in modes
   and the new one, and report each case. DESTINATION_SPAWNED, PASSENGERS_TRANSFERRED and SOURCE_REMOVED are
   expected to stay FAIL as NOT_REACHABLE_ON_C6 under W462; report what they actually do. A case that fails stays
   failed with its reason.
3. **Record** the protocol, the tables, the time each disassembly took from the first command, the commands and
   hashes, and anything left open, and run `python -B scripts/validate_repository.py`; its `Summary:` line must show
   `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
