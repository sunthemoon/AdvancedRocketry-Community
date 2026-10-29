# TASK W202: the I6 write-back tick still over docs/17 after W200

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w202` (new; from the shared head named in your prompt, which includes W200 and W201) |
| Branch | `codex/v1.8.0-w202` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W202.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W202/` |
| Background | `docs/work/v1.8.0-root-queue/records/W197.md`, `records/W199.md` and `records/W200.md`; the W200 review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W200-review/REVIEW.md`, findings W200-R1 and the section after "The submitted audit reproduced both summaries exactly"; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

After W200, the worst proof tick is 166.67 ms (tick 1306), and five proof ticks exceed 100 ms. JFR samples put the
time in journal continuation, landed `writeBack`, `validateWriteBack` / `validateInsert` and NBT byte sizing, with a
GC pause overlapping. The review names two candidates: `RocketJournalInsertionBudget` sizes the returned root again
after `RocketTransferJournalCodec` has sized it for its limit check, and `RocketNbtSize` materializes the bytes in a
`ByteArrayOutputStream` only to count them.

## What to do

1. **Design note first.** Say how each sizing is removed or made cheaper while keeping exact serialization,
   modified-UTF behaviour, every threshold and every rejection. Reusing a byte count is allowed only for the exact
   root that was counted.
2. **Implement**, each change in its own commit with a test that fails first. Include boundary tests: a root exactly
   at a limit, one byte over, and strings whose modified-UTF length differs from their UTF-8 length; the old and new
   sizing must agree on all of them.
3. **Fix the measurement gap (W200-R1).** The W197 summary's work cohort leaves out ticks whose only step samples are
   `releaseTickets` or write-back callbacks. Add the write-back callbacks to the instrumentation or the cohort, keep
   the old summaries unchanged, and report both views.
4. **Measure** with W197's harness through the same command entry point, before and after, under the heavy-JVM lock,
   and compare with docs/17 (a 100 ms P99, mean and P95 as written there).

If the sizing changes are not enough, describe a per-tick write-back allowance in the record for Root, without
implementing it. It would need its own review.

No budget, proof bound, callback maximum, request age or timeout changes. Do not move live-world mutation off the
server thread.

## Verification

- the new tests alone, failing first; the whole unit suite;
- the I6 and sealed batches three times; W197's measurement before and after.

W198 runs native cases under the same heavy-JVM lock; wait for the lock, never remove it. Root's hosted run on the
pushed branch stands in for a complete local GameTest. An independent review follows.

## Not in this task

The admission path (W200), the terrain matrix (W198), ADRs, the risk register, status files and the ledger.
