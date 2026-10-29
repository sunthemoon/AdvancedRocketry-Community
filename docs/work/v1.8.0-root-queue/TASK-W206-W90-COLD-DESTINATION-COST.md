# TASK W206: W90's product-entry cold-destination cost and ticket lifecycle

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w206` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w206` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W206.md` (new; the measurement and fixture plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W206/` |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` (SHA-256 `0667fe44…8f555e`), section 3, "W203-P3"; its write scope, tests and exclusions are part of this TASK |
| Design | [records/W90-DESIGN.md](records/W90-DESIGN.md) and `records/W90B.md` sections 9 and 10; R22(6); [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

**Root decision R175.** The owner's 2026-10-26 fuel-residual acceptance named W83 and W90 as prerequisites. W90a and
W90b implement governed readiness and the destination warm-up, but its cold, border-crossing and maximum-margin
readiness and cost, ticket lifetime and release, and a warm-up process restart are unmeasured. W197 to W202 measured
ordinary I6, not this.

## What to do

1. **Plan first, committed before any native run:** the fixtures (cold with no other ticket, generated versus new
   terrain, border and 16-column ends, maximum margin), the real launch entry, what each measurement records, and the
   summary's work, callback and readiness-latency cohorts. Do not prewarm the measured end or add support tickets.
2. **Observer-only helpers and summaries**, test-side, each with a test that fails first.
3. **Measure natively** under the heavy-JVM lock, on a JAR built from your committed source: pre-F1 cold refusal, the
   server-selected columns, relaunch to READY, F2 handover, flight and F8 release; eight warm-ups and the ninth
   refused, the per-player cap unchanged, expiry, replacement and disconnect, and an actual process restart. Check
   ticket absence apart from chunk eviction. Report tick mean, P95, P99 and maximum, non-idle and callback cohorts,
   readiness latency, per-phase latency, RSS, GC and IO, and every loaded or ticketed column, against docs/17.
   Report a cold-generation failure as it is; do not extend the 600 or 400-tick lifetimes.
4. A production or persistence cause is not fixed here: describe it with its reproduction and stop for Root.

W204 runs native cuts under the same heavy-JVM lock; wait for the lock, never remove it.

## Verification

The helper and summary tests alone, failing first; the whole unit suite; the warm-up GameTest batches; the native
runs. Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent review of the
source pin, the oracle and the results follows.

## Not in this task

Warm-up admission or pad policy, caps, lifetimes, budgets, production changes, a reference-node or four-hour claim from
a short Windows run, ADRs, status files and the ledger.
