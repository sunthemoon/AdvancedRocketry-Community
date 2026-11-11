# TASK W352: the C-S3 R1 fixture's flight plan fuel

Root: Claude. Date: 2026-11-11. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w352`, branch `codex/v1.8.0-w352`, created by Root from the shared head |
| Decision | Root decision R424 (QUEUE row W352) |
| Evidence to read | `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/campaign/cargo-C-S3-attempt1/` (outside Git): `runner.log`, `native/results.json` and `native/FC_LOAD_R1_255-0.log` |
| Record | `docs/work/v1.8.0-root-queue/records/W352.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W352/` |

## Why

Candidate C2's campaign family `cargo-C-S3` failed at its first case, `FC_LOAD_R1_255`. At the first start the
server crashed in the gated release-test fixture, before any production cargo step:

```text
java.lang.IllegalStateException: Only a fueled rocket may accept a flight plan
    at rocket.flight.RocketFlightData.withPlan(RocketFlightData.java:289)
    at resourceescrow.EscrowCargoTransferFixtures.record(EscrowCargoTransferFixtures.java:55)
    at resourceescrow.EscrowCargoTransferFixtures.record(EscrowCargoTransferFixtures.java:36)
    at resourceescrow.EscrowCargoTransferFixtures.record(EscrowCargoTransferFixtures.java:32)
    at resourceescrow.EscrowCargoRecoveryProbe.place(EscrowCargoRecoveryProbe.java:56)
```

The same case passed on candidate C1 (`70dd5d43e`; W326's record, row N045). Since then W330's commit `7753cfd78`
("retain native propulsion in cargo transfer fixtures") changed the four-argument
`EscrowCargoTransferFixtures.record` to build its plan from the rocket's own fuel instead of a synthetic
1,000 mB water bank, so that W330's new GameTests keep their rocket's native propulsion. The C-S3 R1 branch of
`EscrowCargoRecoveryProbe.place` calls that method on the probe's rocket, which has no fuel at that point, so
`withPlan` now refuses. W330 did not run the C-S3 family natively. The R2 branch of the same probe fuels its rocket
before its call; the other native callers use `landedRecord`, which W330 did not change.

## What to do

1. **Restore C1's fixture state for the native R1 branch only.** The R1 branch (`R1_255` and `R1_256`) must again
   build its destination record, and the flight data it gives the rocket, exactly as at C1: a plan and arrival
   built from `RocketFuelState.empty(recorded propulsion)` with a 1,000 mB `minecraft:water` bank, as the method
   did before `7753cfd78`. For example, give `EscrowCargoTransferFixtures` a package-private overload that takes
   the fuel, and pass C1's synthetic fuel from the R1 branch. Keep the GameTest callers' behavior unchanged, and
   keep every method gated by `EscrowTestEndpoints.requireEnabled`.
2. **Check the other native callers.** List every caller of `EscrowCargoTransferFixtures.record` and
   `landedRecord` outside GameTest classes, and say for each whether its rocket has the fuel the call needs at that
   point (read the code and, where a native run exists, its log). Change only a caller that would refuse.
3. **A guard.** Add a unit test or a GameTest that would have caught this: the R1 branch's record must build for a
   rocket that has no fuel, and its resulting flight data must match C1's synthetic fuel. If a direct test is not
   practical, say why and rely on the native run below.

## Verification

- `./gradlew compileJava compileTestJava` and the targeted unit tests you touch, plus `EscrowCargoHarnessTest`.
- **Native:** all ten C-S3 cases with the forced stop and both restarts, on a JAR built from your commit:
  `scripts/run_v180_cargo_integrated.py <template server> --family C-S3` with your JAR, its SHA-256, your commit as
  both source and candidate commit, and the template server
  `D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-forced-setup-05/native-05/server` (see the C2 family's
  `invocation.json` for the full argument list). All ten must pass with the unchanged oracles.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- **The heavy-JVM lock.** Root's C2 campaign driver is running and takes the lock once per family, with a cooldown
  between families; it waits for the lock when it is held. Take the lock the normal way for your JAR build and your
  native run (backslash lock path, as WORKER-RULES says), one step at a time, and release it after each step. Never
  remove, rename or edit a lock you do not own, and do not run a full GameTest or a full build while the campaign
  runs; the hosted run after the merge covers them.
- Check C: and D: for 10 GB free before each heavy step. Delete your server copies after the run; keep logs,
  results and manifests.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; the cargo oracles, timeouts and runner arguments; W330's GameTests and their callers' behavior;
the C2 worktree and its evidence; the status file, QUEUE and the risk register.
