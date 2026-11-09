# TASK W325: the satellite bay's integrated evidence on candidate C1

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w325`, branch `codex/v1.8.0-w325`, created by Root from the shared head |
| Authority | [OWNER-DECISIONS-20261109](records/OWNER-DECISIONS-20261109.md): the owner's choice of A for the five loader variants, and Root decisions R386 and R387 |
| Candidate | C1: `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/candidate-c1/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`, SHA-256 `fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832`, the hosted build of run 42499122646 at `70dd5d43e` |
| Contract | [W27A-DESIGN](records/W27A-DESIGN.md) section 9 (B1 to B18 and the F2 to F5 matrices), [W127-PLAN](records/W127-PLAN.md), [W230-DESIGN](records/W230-DESIGN.md) (safe exit), W231 and W238 (16 pending operations per owner), D1a (`BAY_SOURCE_PROTECTED`) |
| Record | `docs/work/v1.8.0-root-queue/records/W325.md`; evidence under `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/bay/` |

## Goal

The owner wants to know, case by case, whether the satellite bay works on one candidate JAR: what passes, what fails,
what is not covered, and what work remains. Production bay admission stays off: never change
`BayDeploymentSession`'s production flag or any other production switch. Runs use only the existing gated release-test
path. This task produces evidence; it does not enable anything or accept any risk.

## What to do

1. **The matrix first.** Before any native run, commit `records/W325-MATRIX.md`: one row per B1 to B18 side and named
   variant of W27A-DESIGN section 9 (for example B3 with the source present and absent, online and offline), plus the
   safe-exit S3 cuts (W254, W257, W262), the released-flight cuts (W196), the neutral-source refusal and the per-owner
   cap. For each row give the runner and case ID that exercises it (`scripts/run_v180_bay_forced_stop.py`,
   `run_v180_bay_released_flight.py`, `run_v180_bay_exit_cuts.py`, or another existing runner), or NOT_COVERED with the
   reason. Map the F2 to F5, registry-transport and N2 matrices to the GameTests that cover them (they passed in run
   42499122646 at `70dd5d43e`), or NOT_COVERED.
2. **Run every mapped case on C1**, two restarts each, under the heavy-JVM lock (backslash lock path, as WORKER-RULES
   says; take it per runner invocation and release it between invocations, because W326 shares the lock). Copy C1 into
   each session as the runners do; never build or substitute another JAR. The runners require a clean worktree whose
   HEAD equals `--source-commit`: run from your committed branch head, and record with each run that `git diff --quiet
   70dd5d43e HEAD -- src/main src/generated build.gradle settings.gradle gradle.properties` succeeded.
3. **Close gaps where the harness allows.** Where a row is NOT_COVERED only because a runner lacks a case, add the
   case test-side (runner, oracle, fixture), with tests that fail without it, and run it. Do not change `src/main`.
4. **Failures.** If a harness or fixture defect fails a case, fix it test-side and rerun, keeping both attempts. If a
   case shows a production defect (a lost or duplicated satellite, package or rocket, a receipt in the wrong state, an
   unexpected ERROR), stop that family, keep the raw evidence, continue the independent families, and describe the
   defect precisely in the record. Root queues the fix; a fixed candidate reruns the whole matrix.
5. **Waiting times.** Where the runner's logs show when each D step started and ended (for example the D6 proof turns
   and the claim queue), record the observed ticks per case, so that R-043's estimates can be compared with
   measurements. Add no production instrumentation.

## Report

`records/W325.md` gives, for each matrix row: PASS, FAIL or NOT_COVERED; the case ID and attempt count; the evidence
path; and for every FAIL or NOT_COVERED, what remains and an estimate of the work (harness, design, owner decision, or
the final campaign's V1, V2 or reference node). Keep the evidence outside the repository with a `SHA256SUMS.txt`; delete
server copies after each run, as AGENTS.md section 9 says.

## Verification

The Python tests of every runner and oracle you change; `./gradlew test` if any test source changes;
`python -B scripts/validate_repository.py --require-approved-identity`. Check 10 GB free on C: and D: before native runs.

Commit on the branch; do not push. Root reviews, merges the test-side changes and reports to the owner.

## Not in this task

Any change to `src/main`; production admission; the four cargo kinds (W326); the fuel loader; the risk register and the
risk audit (W327); real clients, the reference node and the soak; the status file, the ledger and QUEUE.
