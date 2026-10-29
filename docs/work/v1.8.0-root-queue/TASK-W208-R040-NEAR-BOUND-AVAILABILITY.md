# TASK W208: R-040, sealed-operation availability near the journal's 64 MiB bound

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w208` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w208` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W208.md` (new; the experiment and cut design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W208/` |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` (SHA-256 `0667fe44…8f555e`), section 3, "W203-P4"; its write scope, tests and exclusions are part of this TASK |
| Background | R-040 in `docs/11-RISK-REGISTER.md`; ADR-074; `records/W129a.md` and `records/W129bc.md`; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

**Root decision R178.** Near the 64 MiB transaction-journal bound, the measured write wait (P50 and P95 of 84 and 244
ticks) can outlast a 100-tick native proof and the 200-tick request. DEADLINE and retry are safe, but whether a sealed
operation completes, retries, expires or stays retained there, and how long stop and drain take, are unmeasured.

## What to do

1. **Experiment and cut design first, committed before any native run:** a small-root baseline and a qualified
   near-limit root with a real retained sealed operation; a quiet writer and bounded concurrent traffic; the
   WRITE_PENDING, UNKNOWN and commit outcomes; C1 and C3 proof expiry and retry; a normal stop, a defined interrupted
   drain and two recovery starts. The harness gets an explicit external observation ceiling: reaching it is recorded as
   NOT_COMPLETED, never a product timeout, and never a reason to kill or reopen the live world without safe cleanup.
2. **Diagnostic fixtures and the runner**, test-side, each with a test that fails first.
3. **Measure natively** under the heavy-JVM lock, on a JAR built from your committed source: request-to-completion or
   hold time, write and publish latency, deadline retries, whole-tick work, GC, RSS, and stop and join duration. Verify
   that nothing takes effect before acknowledgement and that U, carriers and resources are conserved after restart.
4. An availability or performance defect is reported for a later scoped remedy, not fixed here.

W207 runs native cases under the same heavy-JVM lock; wait for it, never remove it.

## Verification

The fixture tests alone, failing first; the whole unit suite; the Python oracle tests; the relevant sealed batches.
Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent review of the design
and the results follows.

## Not in this task

Production writer, journal, sealed-executor, schema or budget changes; an M3 work queue; consent renewal; larger
100 or 200-tick bounds; a shutdown timeout; accepting long holds; a docs/17 reference or soak claim; ADRs, status files
and the ledger.
