# TASK W251: why the double-chest GameTest fails in local unfiltered runs with W242 (W242-R2)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w251` (new; branch `codex/v1.8.0-w251` from the shared head named in your prompt) |
| Finding | W242-R2 and section 5 of `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-review/REVIEW.md`, SHA-256 `bfcc2e8e10c1c46cc2e07462a63572f34facff8f0b1b89b52f06f1bd75bf603c` |
| Evidence | W242's three unfiltered local runs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W242/` and `W242-fix/` (`full`, `r264-full`, `r264-final2-full`); W247's passing full run in `W247/full-v2/` |
| Record | `docs/work/v1.8.0-root-queue/records/W251.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W251/` |

`doublechestboundaryandlazylootstaysealedundertickingautomation` (batch `w152_special_containers`) times out at
PREPARE with BARRIER_VETO in all three local unfiltered runs that contain W242's batches, and
`akilledrocketleaksitsrejectedreceiptuntiltheoperatorretiresit` then fails on the chest's leftover disassembly record.
Both pass on base, in W247's full run, in focused runs and in hosted shards. The review's leading explanation is the
chest test's position: two of W242's single-test batches move it about 42 blocks along its row in the noise world.

**Root decision R274 (W251 part):**

1. **Diagnose.** Log the exception that `EscrowBarrier.captureReason` maps to BARRIER_VETO, as a permanent diagnostic.
2. **Experiment.** On a throwaway commit that is never merged, add two no-op tests on the `rocket_test` template in
   batches named `bay_exit_climb` and `bay_exit_first_tick` (same buckets and widths, no W242 code), and run one
   unfiltered GameTest under the heavy-JVM lock. Confirm from the log that the chest test lands where it did in W242's
   runs.
3. **If it fails the same way**, the cause is position: fix the W152 fixture on the test side so its proofs do not
   depend on where it lands (for example, settle or clear the natural terrain, fluids and mobs its proofs see), with
   every assertion and timeout unchanged, and rerun the experiment to show it passes. Make the operator test fail on
   its own refusal code rather than on a foreign record (for example, by checking that no other test's disassembly
   record is present first), without changing the retirement guard.
4. **If it passes**, the cause is in one of W242's two cases: stop, report, and propose the bisection on W242's
   branch rather than changing anything.

## Verification

- The experiment's unfiltered runs with the log checker, and focused runs of the two batches.
- The whole unit suite if any main or test class changes, and the repository validator.

Commit on the branch (the experiment commit stays off it); do not push. Root pushes for a sharded run and reviews.

## Not in this task

W242's own code and tests, the retirement guard, the escrow barrier's rules, status files and the ledger.
