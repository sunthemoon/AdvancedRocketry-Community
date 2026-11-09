# TASK W326: the four cargo kinds' integrated evidence on candidate C1

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w326`, branch `codex/v1.8.0-w326`, created by Root from the shared head |
| Authority | [OWNER-DECISIONS-20261109](records/OWNER-DECISIONS-20261109.md): the owner's choice of A for the five loader variants, and Root decisions R386 and R388 |
| Candidate | C1: `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/candidate-c1/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`, SHA-256 `fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832`, the hosted build of run 42499122646 at `70dd5d43e` |
| Contract | [W11CD-DESIGN](records/W11CD-DESIGN.md) section 6 (C-S1 to C-S5 and D-S1 to D-S2, the changed-boundary and reuse mapping), the earlier runs [W138](TASK-W138-CARGO-C-S1-C-S3-NATIVE-CUTS.md), [W142](TASK-W142-CARGO-C-S4-C-S5-NATIVE-CUTS.md) and [W143](TASK-W143-CARGO-D-S1-D-S2-NATIVE-CUTS.md) and their records, W238 (16 pending operations per owner over loader, cargo and bay) |
| Record | `docs/work/v1.8.0-root-queue/records/W326.md`; evidence under `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/cargo/` |

## Goal

The owner wants to know, case by case and per cargo kind, whether rocket cargo works on one candidate JAR: what passes,
what fails, what is not covered, and what work remains. The four kinds are `FLUID_CARGO_LOAD`, `FLUID_CARGO_UNLOAD`,
`ITEM_CARGO_LOAD` and `ITEM_CARGO_UNLOAD`. Production cargo admission stays off: never change
`EscrowCargoSwitches.production` or any other production switch. Runs use only the existing gated test path. This task
produces evidence; it does not enable anything or accept any risk.

## What to do

1. **The matrix first.** Before any native run, commit `records/W326-MATRIX.md`: one row per cut of C-S1 to C-S5 (90
   fluid cuts) and D-S1 to D-S2 (30 item cuts), counted as W11CD-DESIGN section 6 counts them, with the kind or kinds
   each cut evidences. For each row give the runner and case ID that exercises it (`scripts/run_v180_cargo_cuts.py`,
   `run_v180_cargo_s4_s5_cuts.py`, `run_v180_item_cuts.py`, or another existing runner), or NOT_COVERED with the
   reason. Include the cases W138, W142 and W143 left failed, residual or NOT_COVERED (for example `FF_F6_AFTER_CARGO`
   and `FF_GOVERNED_TERMINAL`) and the per-owner cap over cargo. Map the C-E and D-E A1 cases to the GameTests that
   cover them (they passed in run 42499122646 at `70dd5d43e`), or NOT_COVERED.
2. **Run every mapped case on C1**, two restarts each, under the heavy-JVM lock (backslash lock path, as WORKER-RULES
   says; take it per runner invocation and release it between invocations, because W325 shares the lock). Copy C1 into
   each session as the runners do; never build or substitute another JAR. The runners require a clean worktree whose
   HEAD equals `--source-commit`: run from your committed branch head, and record with each run that `git diff --quiet
   70dd5d43e HEAD -- src/main src/generated build.gradle settings.gradle gradle.properties` succeeded.
3. **Close gaps where the harness allows.** Where a row is NOT_COVERED only because a runner lacks a case, add the
   case test-side (runner, oracle, fixture), with tests that fail without it, and run it. Do not change `src/main`.
4. **Failures.** If a harness or fixture defect fails a case, fix it test-side and rerun, keeping both attempts. If a
   case shows a production defect (a conservation break, a quantity or item that appears or vanishes, a receipt or
   ledger in the wrong state, an unexpected ERROR), stop that family, keep the raw evidence, continue the independent
   families, and describe the defect precisely in the record. Root queues the fix; a fixed candidate reruns the whole
   matrix.

## Report

`records/W326.md` gives, for each matrix row: PASS, FAIL or NOT_COVERED; the case ID and attempt count; the evidence
path; and for every FAIL or NOT_COVERED, what remains and an estimate of the work (harness, design, owner decision, or
the final campaign's V1, V2 or reference node). End with one summary table per kind. Keep the evidence outside the
repository with a `SHA256SUMS.txt`, at most 100 MB; delete server copies after each run, as AGENTS.md section 9 says.

## Verification

The Python tests of every runner and oracle you change; `./gradlew test` if any test source changes;
`python -B scripts/validate_repository.py --require-approved-identity`. Check 10 GB free on C: and D: before native runs.

Commit on the branch; do not push. Root reviews, merges the test-side changes and reports to the owner.

## Not in this task

Any change to `src/main`; production admission; the satellite bay (W325); the fuel loader; the risk register and the
risk audit (W327); real clients, the reference node, the soak and the cargo benchmarks (C-B, D-B); the status file,
the ledger and QUEUE.
