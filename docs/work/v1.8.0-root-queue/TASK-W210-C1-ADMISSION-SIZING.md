# TASK W210: the synchronous C1 admission sizing near the journal bound

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w210` (new; from the shared head named in your prompt, which contains W208) |
| Branch | `codex/v1.8.0-w210` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W210.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W210/` |
| Finding | W208-R2 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W208-review/REVIEW.md` (SHA-256 `b0e5cabb…76929e951`), with its "Costs and C1 attribution" section |
| Code | under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`: `rocket/persistence/RocketTransactionSavedData.java` (`canAppend`, lines 117 to 140), called from `rocket/server/SealedJournalEntry.java` (`retain`, lines 47 to 54) and bracketed in `rocket/server/SealedDisassemblyWorld.java` (line 107). The measured interval also includes `journalFor(...).write(...)`; attribute both |
| Harness | W208's native availability harness, now on the shared head: `scripts/run_w208_availability.ps1` and `.py`, `scripts/w208_summary.py`, `W208NativeAvailability`, `W208JournalFixture`; `records/W208.md` |
| Background | `records/W202.md` (the journal root sized once in the codec's own limit check, for I6 write-back); R-040 and R-035 in `docs/11-RISK-REGISTER.md`; ADR-074; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

**Root decision R183.** W208 measured the first real C1 retain near the 64 MiB journal bound at 302 to 485 ms on the
server thread (the interrupted control's P99 315.868 ms, over docs/17's 100 ms), against 0.915 ms for a small root.
W208's review traced it to `canAppend`: before retaining one U it encodes and measures every resident entry plus the
candidate, and each snapshot encode serializes its NBT again for sizing. This is a production change in a persistence
path.

## What to do

1. **Design note first, appended to the record and committed before code** (one page): where the time goes (confirm
   W208-R2 with a profile or timing of the encoder and measurer); the remedy (for example a size kept per immutable
   entry and invalidated on every mutation path, or the root size W202 already computes); why admission and refusal
   stay exactly the same, including after a reload, a mutation, a failed or UNKNOWN write and a replaced transaction;
   what happens to a candidate near the byte or count limit; memory cost. No format, schema or bound change.
2. **Fix it** with unit tests that fail first: the remedied result equals the full re-encode for fresh and reloaded
   roots, after each mutation path, for candidates just under, at and over the byte bound and at the count limit,
   for a rejected candidate and a retry, and for a maximum-size candidate.
3. **Measure before and after** on W208's harness under the heavy-JVM lock, on JARs built from committed source: the
   C1 retain time, CPU, allocation and GC, whole ticks (mean, P95, P99, maximum) and completion latency, for the
   small and near-bound roots, with at least the quiet near-bound case and its two recoveries. Report against docs/17
   honestly; do not claim a reference-node or soak result.

## Verification

The new tests alone, failing first; the whole unit suite; the sealed and journal GameTest batches three times under
the heavy-JVM lock; the native before and after runs. Root's hosted run on the pushed branch stands in for a complete
local GameTest. An independent read-only review of the design and the fix follows before merge.

## Not in this task

The 100 and 200-tick bounds, the 64 MiB bound, the writer's capture and publication, the journal format, other
callers' costs, budgets, timeouts, ADRs, status files and the ledger.
