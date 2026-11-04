# TASK W298: the bay deployment matrix GameTest's missing published destination

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w298`, branch `codex/v1.8.0-w298`, created by Root from the shared head |
| Decision | Root decision R336 (QUEUE row W298) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W285/ci-41870324437-shard3-attempt2.log` (hosted run 41870324437, attempt 2, shard 3) |
| Earlier work | W170's matrix fix (`26ed25928`); W270 to W273 and W283 (records in this folder) for the patterns behind recent fixture fixes |
| Record | `docs/work/v1.8.0-root-queue/records/W298.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W298/` |

## The failure

`rocket/server/BayDeploymentMatrixGameTests.aRefusedCheckpointRequestRejectsEveryBayWithNoReceipt` failed once with
`The published destination is missing from the loaded fixture` (the assertion near lines 244 and 276) on hosted CI,
at code (W285 on top of `e8bbbc7f7`) that does not touch bays and that passed this test in other runs.

## What to do

1. Read the test and its fixture, find which "published destination" it expects to be loaded and when, and what the
   log shows around the failure (entity and chunk loading lines, `ARCE_*` lines, timing).
2. Reproduce it: run its batch repeatedly with and without load (state how you made the load), and force the suspected
   interleaving deterministically if you can (as W272 and W283 did).
3. If it is a fixture assumption (for example, waiting for one chunk or entity while the product publishes into
   another, or stopping before an asynchronous load completes), fix the fixture at its cause with the product's own
   readiness predicates, keeping every assertion: no raised timeouts or wait bounds, no skipped tests, no batch moves,
   no expected-log manifest changes. If it is a product defect (a published destination that is really missing), stop
   and report it with a failing test.

## Verification

The reproduction failing before and passing after (if reproduced); the batch 20 times in a row under load with a search
for undeclared WARN or ERROR lines; the bay GameTest classes; the whole unit suite; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`. Record the number of loaded runs if it does not reproduce.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Other intermittent entries, bay production behaviour unless a failing test shows a defect, the expected-log manifest,
the ledger, the status file and QUEUE.
