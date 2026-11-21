# TASK W437: K02 follow-up, the v1.0 forced-stop runners on C6's sealed disassembly

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w437`, branch `codex/v1.8.0-w437` from the shared branch |
| Decision | Root decision R612 (QUEUE row W437), following R611 on [W431](records/W431.md) |
| Problem | W431 found that both v1.0 forced-stop runners wait for the legacy line `ARCE_RELEASE_TEST_DISASSEMBLY entity=... logical=... code=... blocks=... rolled_back=...` (the regex `DISASSEMBLY_LOG` in [the v0.6 flight smoke](../../../scripts/run_v060_flight_server_smoke.py), used by [the flight runner](../../../scripts/run_v100_flight_forced_stop.py) through `disassemble_and_verify` and by [the transaction runner](../../../scripts/run_v100_transaction_forced_stop.py) directly). On C6 the release-test disassembly goes through the retained sealed executor: the command answers `DISASSEMBLY_PENDING` and later logs `ARCE_ROCKET_SEALED_DISASSEMBLY entity=... logical=... result=...` ([RocketDisassemblyRequest](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketDisassemblyRequest.java), `finishedRetained`, and [RocketDisassemblyService](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketDisassemblyService.java); read only). Result: flight 0 of 10 and transaction 8 of 25 complete |
| Inputs | W431's sealed K02 evidence under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` (read only) and its external launcher `package.py` as the pattern for a detached run with one lock per JVM and the R607 liveness check; C6's identity in QUEUE row "C6 run" (R581) |
| Outputs | Tracked: the two v1.0 runners, their two test modules, and the record `docs/work/v1.8.0-root-queue/records/W437.md`. Outside Git: new run folders under `.../fixtures/K02/` named with `R612`, with a `SHA256SUMS.txt`; W431's folders stay unchanged |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds after each release before the next. C6 attempt 2 and other workers share the lock. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to `scripts/run_v060_flight_server_smoke.py` or any other shared script, to a budget, a timeout or an existing assertion. Starting any client. Accepting a receipt by substring or by its absence. |

## Root's choices

1. **Receipt.** Each runner accepts exactly one of two receipt forms for the disassembly it requested: the legacy
   line as today, or the sealed line with the same entity and logical IDs and `result=SEALED`, after the command's
   `DISASSEMBLY_PENDING` answer. Any other sealed result is a failure. The sealed line has no block counts, so the
   success condition is the runner's existing check of the restored blocks and the expected chest and inventory
   contents, which must still pass unchanged; the durability line W431 saw (`phase=DONE`) is also required if C6
   always prints it on this path (read the code and say which). Keep the change local to the two runners, behind
   an explicit option such as `--sealed-disassembly-receipt`, with the old behaviour as the default.
2. **Unreachable checkpoints.** If C6's sealed path never reaches a legacy disassembly journal checkpoint the
   transaction runner sets (`DISASSEMBLY:RESTORING:n` and the rest), do not fake it: report each such case as
   NOT_REACHABLE_ON_C6 with the code path that shows it, and name the C6 runner or GameTest that covers forced stops
   of the sealed path, if one exists. Such a case is never counted as passed.
3. **One complete record.** Rerun the whole flight matrix (10 cases) and the whole transaction matrix (25 cases),
   each from a fresh C6 baseline in one run, with `--wait-ready-status` (W431), so each matrix has a single complete
   result. W431's partial runs stay as earlier evidence.

## What to do

1. Read the two C6 classes above and the runners; write the change and tests for both receipt forms, a wrong
   entity or logical ID, a non-SEALED result, a missing receipt, and the unchanged default (Linux-safe tests).
2. Run both matrices as in choice 3 and keep every log, summary and argument file.
3. Write the record: commits, commands, the per-case table (PASS, FAIL or NOT_REACHABLE_ON_C6 with reason), and what
   stays open for K02.
4. Run the targeted tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
