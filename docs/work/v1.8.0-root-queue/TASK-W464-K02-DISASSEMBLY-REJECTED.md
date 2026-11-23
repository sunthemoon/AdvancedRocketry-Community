# TASK W464: why C6 rejects the sealed disassembly after a recovered landing

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w464`, branch `codex/v1.8.0-w464` from the shared branch |
| Decision | Root decision R665 (QUEUE row W464), following [W463](records/W463.md) and [W462](records/W462.md) |
| Sources | [W463](records/W463.md), section "Corrected flight matrix": in DESCENT and LANDED, after the settlement event, a preparation-only pending answer and a fresh exact-fuel request, C6 logs `ARCE_TRANSFER_RETIREMENT_PROVED ... slots=2`, then `ARCE_ROCKET_SEALED_DISASSEMBLY ... result=REJECTED` and `ARCE_DISASSEMBLY_DURABILITY_COMPLETE ... reason=REJECTED`; W463's counted cases sent no chunk-loading command, not even the runner's inherited origin `forceload`. [W462](records/W462.md), section "Diagnostic execution": with the inherited origin `forceload` still in place, a new request ten minutes later, after vanilla `forceload` of both departure margins and the Moon chunk, passed every check. The runner [run_v100_flight_forced_stop.py](../../../scripts/run_v100_flight_forced_stop.py) with its opt-in modes. C6's code under `src/main/` (read only), in particular `SealedDisassemblyCoordinator`, `RocketDisassemblyRequest`, `RocketDisassemblyService`, `RocketOrdinaryRetirement`, `RocketI6Entry`, `RocketI6Retirement` and `SealedDiagnostics`. The log line `ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED` in the same recovery, if it is related |
| Inputs (read only) | W463's evidence `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R663-W463/corrected/` (logs, summaries, launcher) and W462's `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W462/`; K02's sealed sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` (fresh copies only) |
| Outputs | Tracked: the record `docs/work/v1.8.0-root-queue/records/W464.md`, and a runner change with tests only if a diagnostic needs one, opt-in and default off. Outside Git: diagnostic runs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R665-W464/` |
| Lock | Server runs take the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, including across a launcher restart, no hold over two hours; detached launchers start with no console window. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to K02's sources or earlier evidence. Changing a runner's default behaviour or loosening any existing check. Changing W447's or W463's counted results: diagnostic runs count as nothing. |

## What to do

1. **The predicate.** From C6's code, find the exact condition that produces `result=REJECTED` right after the
   retirement proof, with file and line citations, and what it checks that the proof does not.
2. **State after the rejection.** From the code, and then in a diagnostic LANDED run under W463's conditions, record
   what remains after `REJECTED`: the rocket entity with its identity, fuel and payload, the terrain at the landing
   site, the transfer record, and whether anything is lost or created. Keep the server running and try one new
   request after 60 seconds, and one after five minutes, and record each answer.
3. **The difference from W462.** Repeat the diagnostic LANDED run once with only the runner's inherited origin
   `forceload` restored (as in W447 and W462) and nothing else changed, to see whether that one command decides
   between `REJECTED` and `SEALED`. Add any further single-variable run the code points to, at most two.
4. **Classify** the rejection as PRODUCT (a player can meet it, for example by disassembling a landed rocket at the
   destination after a server restart while the departure area is unloaded) or HARNESS, with the evidence. For
   PRODUCT, state the player impact (refused and safe to retry, or worse), propose one paragraph of known-issue text
   for players and a v1.9 follow-up, since `src/main` stays frozen at C6.
5. **Record** the findings, commands and hashes, and anything left open, and run
   `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
