# TASK W225-fix5: apply the host-lag ceiling per GameTest server run in the sharded aggregate

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w225` (W225's; continue on its branch) |
| Branch | `codex/v1.8.0-w225`, now at `82fd1d900` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W225.md`: append "R236 host-lag ceiling per server run" |
| Hosted run | Fifth sharded run 41223816244 on `82fd1d900`: the build (12.6 minutes) and all four shards passed (13.3 to 18.5 minutes, 1,557 of 1,557 tests, every shard's log check PASS). The aggregate job failed on one rule only: `[FAIL] union: whole-suite warning ceiling server-overloaded: 11 > 8`. Its log is `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/job-124611136632.log`; the shard logs are `job-124606718741.log` (shard 0, 5 notices), `job-124606718685.log` (shard 1, 3), `job-124606718795.log` (shard 2, 1) and `job-124606718751.log` (shard 3, 2), every notice in the first minutes of its run |

**Root decision R236.** [ADR-072](../../decisions/ADR-072-V180-GAMETEST-LOG-EXPECTATIONS.md)'s implementation note
defines the `server-overloaded` ceiling per run: "a run above eight still fails", and its notices come from the server
start and the heavy batches right after it. A sharded regression has four server runs, so summing their notices
against one run's ceiling is stricter than ADR-072. It fails runs whose tests and per-shard log checks all pass.

1. In `scripts/ci_gametest_shards.py union`, check the `server-overloaded` ceiling per shard log (each shard is one
   GameTest server run, at most 8), exactly as the single-job regression checked its one log. Report the
   whole-suite total as information. Every other aggregate rule is unchanged: the union, failure propagation, the
   per-shard log check and the other expectations.
2. Tests:
   - a shard with 9 notices fails;
   - four shards with 8 each pass, and the total is reported;
   - the fifth run's 5, 3, 1 and 2 pass;
   - every other union rule still fails as before.
   Make them fail first on `82fd1d900`.
3. Draft, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W225/W225-adr072-draft.md`, the exact ADR-072 note that Root
   adds. It states:
   - the per-run application in sharded runs, with this run's counts;
   - that the ceiling remains a diagnostic count, not a performance budget;
   - that a notice outside a run's first minutes is still reviewed as a possible regression;
   - the AGENTS section 5 exception fields: responsible, reason, expiry at the v1.8 release acceptance review
     together with ADR-072, and recovery;
   - the link to the owner's pending D14-log question (8 or 5 per run).
   Do not edit ADR-072.

## Verification

- The changed tests.
- All Python suites the workflow runs.
- `union` over the fifth run's four shard logs, which now passes.

Root pushes; a sixth sharded run qualifies the result.

## Not in this task

The workflow, the partition, other ceilings or expectations, status files and the ledger.
