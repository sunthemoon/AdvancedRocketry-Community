# TASK W101: make the v1.3 native smoke family run on v1.8 (W92-R9)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) and `records/W92.md` (its W92-R9 note: "the v1.3 adapter
flight smoke is blocked by its own stale expectations") first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w101` (new; from the shared branch at `6d5920e6`) |
| Branch | `codex/v1.8.0-w101` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W101.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W101/` |

## The problem

Every `scripts/run_v130_*.py` native smoke expects the log line "API 1.7"; the v1.8 mod reports a later API version, so
each smoke fails before it tests anything. Behind the pin, the adapter flight smoke expects `code=SUCCESS` for a launch
that v1.8 answers `JOURNAL_PENDING` and completes after the journal acknowledgement (W11b2 and W91). W92's two changes
to that smoke (the fuel-discard wait and the asynchronous disassembly receipt) have therefore never run natively.

## What to do

1. **One source for the API version.** Find where the mod's adapter API version is defined and logged, and make the
   v1.3 smokes read the expected value from that one place (for example a constant the scripts share, checked by a test
   against the Java source), instead of each pinning a literal. Do not change the Java API version or any log line.
2. **The adapter flight smoke's launch protocol:** accept `JOURNAL_PENDING` for the launch request, then wait for the
   acknowledged result exactly as v1.8 reports it (find the line or command the other v1.8 native runners use), with a
   bounded wait. Keep every other check, including W92's fuel-discard wait and asynchronous receipt; a weaker check is
   not allowed.
3. **Run each v1.3 smoke natively once** on a jar built from your committed source (flight, adapter recovery,
   atmosphere boundary, component, environment, fuel, satellite payload, suit equipment) and record each result. A
   smoke that fails for another stale v1.3 expectation gets the smallest fix that keeps its check, in its own commit; a
   failure that looks like a product defect is a finding to report, not to adjust.
4. Run the smokes' Python tests and `python -B scripts/validate_repository.py --require-approved-identity`.

## Write scope

`scripts/run_v130_*.py`, their shared helper scripts, `tests/test_v130_*.py`, a new shared constant or helper if you add
one, and the record. No Java change, no log-line change, and no relaxed assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W97, W98, W100) also run tests; take the heavy-JVM lock for every native run and
release it between smokes when another worker waits. Check that C: and D: have more than 10 GiB free before each heavy
start. Delete each smoke's server copy afterwards; keep only logs, results and manifests. If the tool refuses your
cleanup, write the paths in the record and final answer at once.

Not in this task: smokes of other versions, ADRs, the risk register, status files and the ledger (Root).
