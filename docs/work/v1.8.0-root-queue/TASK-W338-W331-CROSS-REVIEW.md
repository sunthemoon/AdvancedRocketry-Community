# TASK W338: read-only cross-engine review of W331's gated bay fixtures and runners

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change no checkout.

| Item | Value |
|---|---|
| Checkout | `D:/GitHub/arce-v180-w338`, detached at the shared head, created by Root. W331 merged there as `f7595b001` (its own commits `f9930d950..1888fb9b3`, based on `4a6cc4fc1`). |
| Decision | Root decision R409 (QUEUE row W338). AGENTS.md section 11 prefers that a different engine review an implementation; W331 was implemented by a Claude session and reviewed by Root, also Claude. |
| Read first | In the checkout: [TASK-W331](TASK-W331-BAY-NATIVE-GAPS.md), [W331-DESIGN](records/W331-DESIGN.md), [W331](records/W331.md), and W27A's B6, B7 and B8 rows in [W27A-DESIGN](records/W27A-DESIGN.md) (around line 894) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W338/REVIEW.md`; logs and scratch files under `D:/GitHub/ARCE-Task-Evidence/root-queue/W338/` |

## Why

C2's full rerun (R386) will use W331's fixtures and runner changes. Root reviewed the diff, but by the same engine as
the implementer. This review checks that the fixtures cannot reach a production server, that the runners check the
candidate correctly, and that the corrected oracles and Root's B6.5 decision follow the design.

## Check

1. **The gate.** `BayGapReleaseTestCommands` and the new `trio`, `survey` and `offline` fixture branches are
   registered only when the release-test property is set, require permission 2, and call
   `EscrowTestEndpoints.requireEnabled` before reading or writing anything. No production path calls them. Does
   `BayGapReleaseTestGateTest` fail if a command skips the gate? Is the new cut value `B15_D1` reachable only through
   the gated cut probe?
2. **Writes by the fixtures.** `stale-assembly` writes an ASSEMBLY record in a dimension that never loads, and
   `decommission` flushes the satellite registry. Can either leave state that a later case on the same template
   misreads, or that changes a saved form? Is the stale record bounded?
3. **The candidate checks.** The forced-stop, released-flight and exit runners and `bay_integrated_evidence.py pin`
   require `--jar-sha256` and `--candidate-commit`, check both before Java starts, and run W325's production diff
   against that commit. Can any path start Java with a mismatched JAR or a production diff?
4. **The oracle corrections.** W331 changed four of its own oracles after smokes (D0's reason on each bay; B15.5's single
   write; B6.5; B7.3). For each, is the new expectation what the design requires, or was an oracle relaxed to fit the
   observed behaviour? Name any that should fail but now passes.
5. **B6.5 (Root decision R407).** Root recorded B6.5 as reached for its safety property: with the destination absent
   after retirement, production keeps the consumed source stationary (KEEP_SOURCE), and the rebuild from the consumed
   transfer record is not natively reachable without deleting a forced source, which W27A's B7 row forbids. Check this
   reading against W27A's B6, B7 and B8 rows and the recovery code. Is there a legitimate native path to the rebuild?
6. **The withdrawn fixture and NOT_COVERED rows.** The epoch-only `protected` variant was removed. Confirm that nothing
   still reaches it, and that each of the 8 NOT_COVERED reasons is accurate.

Give a verdict, APPROVED or CHANGES REQUIRED, with findings ranked by severity, each with a file and line, a concrete
failure scenario and a fix.

## Run

- `python -B -m unittest discover -s tests -p "test_bay*.py"` and the bay runner tests
  (`test_run_v180_bay_forced_stop.py`, `test_run_v180_bay_released_flight.py`, `test_run_v180_bay_exit_cuts.py`).
- `gradlew.bat --offline test --tests "*BayGapReleaseTestGateTest" --tests "*BayDeploymentCuts*"` under the heavy-JVM
  lock.
- Any probe you need, in your own scratch directory under the output folder, never in the checkout. No native server
  run is required.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Take the lock per step (backslash lock path, as WORKER-RULES says) and release only your own token; other workers share
it. Before you finish, delete your own scratch trees and confirm with `git status` that the checkout is unchanged.

## Not in this task

Editing, committing or pushing anything; W332, W336 and W337; production behaviour beyond what W331's fixtures touch;
native runs.
