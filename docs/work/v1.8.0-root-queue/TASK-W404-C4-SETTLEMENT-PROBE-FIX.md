# TASK W404: fix the fluid-identity check that failed C4's cargo-C-S4, and sweep the probes for the same mistake

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w404`, branch `codex/v1.8.0-w404` from the shared branch |
| Decision | Root decision R539 (QUEUE row W404) |
| Evidence | Outside Git: `D:/GitHub/ARCE-Task-Evidence/root-queue/C4/campaign/cargo-C-S4-attempt1/` (`runner.log`, `native/results.json`, `native/FS_BIND_BEFORE-1.log`, `native/FS_BIND_BEFORE-1-native/crash-reports/`). Read only; copy what you need to a Temp folder. |
| Record | `docs/work/v1.8.0-root-queue/records/W404.md` |
| Write scope | The gated test probes under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/` whose names end in `Cuts.java` or `Probe.java` (test support compiled into the JAR, active only under their test properties), new or changed unit tests under `src/test/java/`, the record. No other product class, no script, no runner, no oracle. |
| Not allowed | A full GameTest run, `runData`, a full build or any native server: the heavy-JVM lock belongs to C4's native runs. Compiling and running targeted unit tests is allowed (`JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`). |

## What failed

Candidate C4's family `cargo-C-S4` failed at its third case, `FS_BIND_BEFORE` (scenario NEUTRAL, unload): the server
threw "The released device stayed locked or broke conservation" from `EscrowCargoSettlementCuts.released`. W373B
(`5e7e6cf87`) added `water(device) == (scenario != Scenario.SUBSTITUTE)` to that check. In an unload that is not
applied, the device keeps its starting 0 mB, an empty tank has no fluid, so `water(device)` is false and the check
throws. The amounts balanced: `requireRocket` (5,000 mB) and `requireDevice` (0 mB) passed just before it in the same
method. W373B ran only its new `FS_AC_` cases; the unload cases where the device stays empty never ran this check
until C4. The family runner then skipped `cargo-C-S4-LOAD` and `cargo-C-S5`.

## What to do

1. Fix the check so that it states the right expectation for every case of the class: when the device holds fluid,
   its identity is water except in SUBSTITUTE; when it holds none, it has no fluid identity (say how you test that).
   Keep every other conjunct, including conservation and the lock. Put the expectation in a small static helper and
   cover it with a unit test for every scenario and both directions (empty and non-empty device).
2. **Sweep.** List every assertion that W373B (`5e7e6cf87`, `206dd6d3b`), W373C and W391 added or changed in shared
   code paths of the probes in the write scope, and for each one name the existing cases (of any family in the
   integrated runner's selection) that execute it, from the case tables and the runner's family list. Mark each
   assertion as checked by a case that passed in W373B, W373C, W391 or C4's passed families, or as not yet executed by
   any passed case. For each not yet executed one, decide whether it holds for those cases; fix the ones that do not,
   with a unit test where the logic allows it. Do not weaken a check that is right; the goal is a correct expectation,
   not a looser one (AGENTS.md section 5).
3. Run the new and changed unit tests and the escrow unit tests (`./gradlew test --tests` with the escrow and
   resourceescrow test classes), and `python -B scripts/validate_repository.py`, which must end with `0 failed`.
4. Record the cause, the fix, the sweep table, the commands and results, and the native families Root must rerun
   under the lock before the next candidate: at least `cargo-C-S4`, `cargo-C-S4-LOAD` and `cargo-C-S5`, plus any
   family the sweep touches.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
