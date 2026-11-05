# TASK W302: the sealed world-protection GameTest depends on where it lands

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w302`, branch `codex/v1.8.0-w302`, created by Root from the shared head |
| Decision | Root decision R342 (QUEUE row W302) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W298/ci/job-127021694986.log` (failing shard 3 of run 41958063402, W298's integration commit `a73e3c5ee`) and `job-127009523120.log` (the same test passing in shard 3 of the shared run 41954303601 at `624d4de2e`) |
| Earlier work | [W251](records/W251.md): the same mechanism in `SealedSpecialHostAutomationGameTests` (ambient noise-world water refilled a cell the native terrain projection needs to be air, so PREPARE kept vetoing until the timeout); fixed there with `isolateAutomationFixture` |
| Record | `docs/work/v1.8.0-root-queue/records/W302.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W302/` |

## The failure

W298 adds one GameTest to an existing batch, which moves the grid positions of later tests. In W298's branch run,
`SealedWorldProtectionGameTests.retainedCarrierSurvivesNativeBreakPistonExplosionFluidFireAndSupportUpdates` (batch
`w152_world_protection`) timed out at its 400 ticks. Its first PREPARE was vetoed with `Captured endpoint
identity/frontier mismatch` (`ARCE_DISASSEMBLY_DURABILITY_HELD ... reason=BARRIER_VETO`), which is the designed veto by
the bedrock fence: in the passing shared run the same line appears and `ARCE_DISASSEMBLY_DURABILITY_COMPLETE` follows
about 270 ticks later. In the failing run the retry never completed, the test's `SEALED_DISASSEMBLY/RESTORED` record
stayed behind, and `BayDeploymentLockGameTests.aKilledRocketLeaksItsRejectedReceiptUntilTheOperatorRetiresIt` then
failed on that foreign record, as W251 made it do.

The test itself explodes at the carrier (age 10), sets fire (age 20), places water north of it (age 30) and clears
the fence (age 60). At a new position the explosion can open the fixture to ambient terrain fluids, which W251 found
can refill a cell the native projection requires.

## What to do

1. **Reproduce.** Confirm the cause at the failing position: run the full unfiltered GameTest set (or the smallest
   selection that reproduces the same placement) on the shared head with W298's commit `2379beb3a` cherry-picked on a
   throwaway commit that is never merged, under the heavy-JVM lock, and record what blocks the second proof (W251's
   diagnostic logs the original exception at INFO). Use a read-only look at that run's region or a position probe on
   the throwaway commit if needed.
2. **Fix the fixture, not the product.** Isolate this test's cells from ambient terrain, as W251 did, in a way that
   survives the test's own explosion, fire and water (glass may not survive the explosion; choose the enclosure so that
   only the test's own effects reach the native proof columns). Keep every assertion, the 400-tick timeout, the fence
   and retry design and the explosion, fire and water steps unchanged.
3. **Check the siblings.** List the other GameTests that rely on native terrain capture of cells a nearby ambient
   fluid could refill (start with the `w152_*` batches and `SealedUnsealGameTests`) and say in the record which ones
   are already isolated; isolate any that share this exposure the same way, or say why not.
4. If the cause is not ambient terrain, stop and report it with the evidence; if it is a product defect, report it
   with a failing test.

## Verification

The reproduction failing before and passing after on the same placement; the `w152_*` batches and
`bay_deployment_lock` focused; the whole unit suite if any main or test class changes; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges; W298 merges after this.

## Not in this task

W298's change, the sealed disassembly and escrow barrier rules, the retirement guard, the expected-log manifest, the
ledger, the status file and QUEUE.
