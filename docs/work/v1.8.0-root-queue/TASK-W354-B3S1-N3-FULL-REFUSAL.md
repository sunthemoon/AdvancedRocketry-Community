# TASK W354: why the fuel loader's N3_FULL refusal no longer reaches the queue

Root: Claude. Date: 2026-11-11. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w354`, branch `codex/v1.8.0-w354`, created by Root from the shared head |
| Decision | Root decision R426 (QUEUE row W354) |
| Evidence to read | `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/campaign/cargo-B3-S1-REUSE-attempt1/` (outside Git): `runner.log`, `native/results.json` and `native/LF_M_N3_FULL/` |
| Earlier pass | [W27C1](records/W27C1.md), section "W95's native cuts (R7)": the case passed on `1f674080` |
| Record | `docs/work/v1.8.0-root-queue/records/W354.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W354/` |

## Why

Candidate C2's campaign family `cargo-B3-S1-REUSE` (the fuel loader's reused S1 cuts, runner
`scripts/run_v180_loader_cuts.py`) passed `LF_M_N0`, `LF_M_N2_DEFINITE` and `LF_M_N2_UNKNOWN`, then failed
`LF_M_N3_FULL` at its first start. The gated probe threw:

```text
java.lang.IllegalArgumentException: Refusal did not visit the queue
    at resourceescrow.EscrowTags.require(EscrowTags.java:15)
    at resourceescrow.EscrowLoaderCutProbe.refuse(EscrowLoaderCutProbe.java:419)
    at resourceescrow.EscrowLoaderCutProbe.tick(EscrowLoaderCutProbe.java:302)
```

The probe substitutes a BAY index holding 64 pending BAY operations (`EscrowLimits.OPERATIONS`), then requires
`loader().attempt()` to answer `READY` (the attempt is queued) and the queue visit to refuse with `ESCROW_FULL`. On
C2 the attempt answered something else. The same case passed on `1f674080` (W27C1), logging
`ARCE_LOADER_MATRIX_REFUSED cut=LF_M_N3_FULL status=ESCROW_FULL bay=TEST_INDEX pending=64`. The family did not
run at C1. The remaining cases `LF_M_N3_NOT_LOADED`, `LF_M_N3_PREFLIGHT` and `LF_M_N3_ELIGIBILITY` did not run.

The cause is not known. It may be a production change to the fuel loader's admission since `1f674080` (for
example the bay's shared pending pool, W329's protective answers while the ledger is NOT_LOADED, or W332's
release-test load-failure seam), or a fixture that no longer sets up what it means to. Either is possible.

## What to do

1. **Find what the attempt answered and why.** Reproduce the case on the C2 JAR
   (`D:/GitHub/ARCE-Task-Evidence/root-queue/C2/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`,
   SHA-256 `1eea72ac442ea181806940c1288698abd7840fbfa0bce2b17f415550bff8ca57`) and read the code path from
   `FluidFuelLoaderBlockEntity.attempt` to the status it returned. A scratch JAR with extra logging is fine for
   diagnosis only; keep it out of your commits. Name the commit that changed the answer (read `git log` on the
   path, and bisect with scratch builds if needed).
2. **Classify it, with the evidence.**
   - If production now refuses at the attempt on purpose, show where that behavior was decided (the commit, its
     task record and any ADR or design note). Then correct the probe or the runner's expectation to the decided
     behavior. The case must still prove what it was written for: the full shared pool refuses this loader with
     the unchanged resource envelopes and reservations, and nothing is written.
   - If production changed by mistake (a regression), **stop**: do not change production code. Write what you
     found in your record, with the reproduction, and finish. Root decides the next step, because a production
     change needs a design note and a separate review.
3. **Check the rest of the family.** After any probe correction, run all seven `cargo-B3-S1-REUSE` cases
   (`LF_M_N0` to `LF_M_N3_ELIGIBILITY`) natively with the forced stop and both restarts, with
   `scripts/run_v180_cargo_integrated.py <template server> --family B3-S1-REUSE` and the other arguments from the
   C2 family's `invocation.json`. Use a JAR built from your commit if you changed Java, or the C2 JAR from a
   detached scratch checkout at `ade1538c3` with only your runner files copied in if you changed Python only;
   say which you did.

## Verification

- `./gradlew compileJava compileTestJava` and the targeted unit tests for anything you change; the Python tests
  for any runner you change, and `python -B -m unittest discover -s tests` once before you commit if you change
  Python (the escrow probe inventory test checks case tokens).
- The native run above: all seven cases must pass with unchanged oracles, unless step 2 found a regression.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- **The heavy-JVM lock.** Root's C2 campaign driver is running and takes the lock once per family, with a cooldown
  between families; it waits when the lock is held. Take the lock the normal way for each JAR build and native run
  (backslash lock path, as WORKER-RULES says) and release it after each step. Never remove, rename or edit a lock
  you do not own. No full GameTest or full build while the campaign runs.
- Check C: and D: for 10 GB free before each heavy step. Delete server copies, scratch JARs and scratch worktrees
  after the run; keep logs, results and manifests.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; the families `cargo-B3-S4-REUSE` and `cargo-B3-S5-REUSE` (Root reruns them); the C2 worktree and
its evidence; the status file, QUEUE and the risk register.
