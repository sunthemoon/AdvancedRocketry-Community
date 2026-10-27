# TASK W168: the ground survey's whole-root preflight (W129 Q6)

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md), section 2.4 of
[records/W129-DESIGN.md](records/W129-DESIGN.md), item 13 of
[ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) and docs/17 section 4 first. You are a
new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w168` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w168` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W168.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168/` |

W129 moved the satellite registry's writes off the server thread (merged at `a03fdd048`), but one encoding stays on
it. `GroundSurveys` (around lines 367-397) encodes and round-trips the whole registry root on the server thread in three
cases:
- every ground-survey START;
- every research-account creation through a ground mutation;
- every change while the section reservations cannot bound the root (a restored root above its section budgets).

At the 16 MiB bound, W128 measured a whole-root encode at hundreds of milliseconds, far over docs/17's P95 of 50 ms
and P99 of 100 ms for one tick of server-thread work.

## What to do

1. **Measure first.** Add a standalone benchmark under `src/test`, following the pattern of W128's
   `SatelliteRegistryBenchmark`. Run it with Xmx4G under the heavy-JVM lock, at least 50 samples each, reporting P50,
   P95, P99 and the maximum:
   - a START on a root at the byte bound;
   - an account creation on that root;
   - one ordinary change on a restored root whose reservations exceed the bound.
2. **A fast path that changes no answer.** For START and account creation, first try the reservation proof the
   other changes already use, with the new record's own reservation and, if a new account is created,
   `RecordSizer.ACCOUNT_BYTES` added. Encode only when that proof fails.
   - Because the reservations bound the encoding, this changes no result: a change refused today is still refused,
     and one accepted today is still accepted.
   - Prove that with tests at the boundary: reservations exactly at the bound, one byte over, and a restored
     over-budget root.
3. **Measure again**, and report how often the slow path can still run.
   - **If it still misses docs/17 in some case,** stop and report options with numbers. Do not choose one. Examples:
     refuse with STORAGE_BUDGET whenever the reservations cannot prove the fit (a behaviour change, which needs Root's
     decision and a risk note), or a per-tick allowance for preflights.
   - Do not reduce any bound.

## Rules and verification

Nothing may change:
- the registry's saved format, schema or bounds;
- any section budget, quota, timeout or protocol;
- which requests are refused, except as step 3 reports for Root's decision.

Verification:
- the new tests alone, failing first where they pin a fix;
- the `satellite` packages, then the whole unit suite;
- one complete GameTest with the unchanged log checker, under the lock.

Check C: and D: above 10 GiB and 6 GiB of free memory before each heavy run. A fresh read-only review follows
before merge.

## Not in this task

The registry writer (W129), the bay slices, ADRs, the risk register, status files and the ledger (Root).
